package silicium.metal.mixin.own;

import java.util.List;
import java.util.Map;
import silicium.metal.own.OwnTerrain;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * -Dsilicium.own.int.lists=true: vanilla's per-frame section draw lists (LevelRenderer.extractSectionDrawGroups, every visible section x
 * every layer: its draw, then its slice in vanilla's uber buffers) are skipped when vanilla's uber buffers hold no geometry at all,
 * which is the case while our near terrain takes every layer (nothing of ours is ever uploaded there). Exact: with every uber buffer
 * empty, getRenderSectionSlice returns null for every section and layer, so vanilla's walk adds no draw, no section info and no
 * index count; the skip returns the same 0 with the same empty lists. Any vanilla-held layer (improved transparency, a layer outside
 * the compact range, the frame after a toggle) makes a buffer non-empty and the walk runs as before.
 *
 * <p>silicium (AutoConfig.cachedUberEmptiness): the lock + map scan is replaced by OwnTerrain's volatile uberBuffersEmpty, maintained
 * where geometry is routed into/out of the uber buffers. A release forces one verifying scan before the cached true is trusted
 * again, and a generation counter prevents a scan from committing true over an add racing it. Verify mode still scans every frame
 * and compares the answer with the cached one.
 */
@Mixin(LevelRenderer.class)
abstract class OwnIntListsMixin {
	@Unique
	private static final String SILICIUM$MODE = System.getProperty("silicium.own.int.lists", "false");
	@Unique
	private static final boolean SILICIUM$ON = "true".equals(SILICIUM$MODE), SILICIUM$VERIFY = "verify".equals(SILICIUM$MODE);
	@Unique
	private static final boolean SILICIUM$CACHED = silicium.metal.AutoConfig.cachedUberEmptiness();
	@Unique
	private boolean silicium$wouldSkip;
	@Unique
	private long silicium$checked, silicium$bad, silicium$cacheBad, silicium$skipped, silicium$lastLog;

	@Inject(method = "extractSectionDrawGroups", at = @At("HEAD"), cancellable = true)
	private void silicium$skipVanillaLists(boolean respectTranslucentOrder, List<?> sectionInfos, Map<?, ?> drawGroups, CallbackInfoReturnable<Integer> cir) {
		this.silicium$wouldSkip = false;
		if (!SILICIUM$ON && !SILICIUM$VERIFY || OwnTerrain.get() == null) return;
		SectionRenderDispatcher d = ((LevelRenderer) (Object) this).sectionRenderDispatcher();
		if (d == null) return;
		boolean cached = OwnTerrain.uberBuffersEmpty;
		boolean empty;
		if (SILICIUM$CACHED && !SILICIUM$VERIFY && cached) {
			empty = true;  // known empty, no lock, no scan
		} else {
			long seq = OwnTerrain.uberSeq();
			d.lock();
			try {
				empty = true;
				for (Object buffers : ((OwnIntDispatcherAccess) d).silicium$uberBuffers().values()) {
					if (!((OwnIntUberAccess) ((OwnIntUberBuffersAccess) buffers).silicium$vertexBuffer()).silicium$allocations().isEmpty()) {
						empty = false;
						break;
					}
				}
			} finally {
				d.unlock();
			}
			if (SILICIUM$CACHED) {
				if (SILICIUM$VERIFY && cached != empty && seq == OwnTerrain.uberSeq()) this.silicium$cacheBad++;
				OwnTerrain.uberVerified(empty, seq);
			}
		}
		this.silicium$wouldSkip = empty;
		if (SILICIUM$ON) cir.setReturnValue(0);
	}

	/** A reload/level reset drops every section's vanilla uber allocation: re-verify once before trusting the cached answer. */
	@Inject(method = "invalidateCompiledGeometry", at = @At("RETURN"))
	private void silicium$uberReloaded(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.client.Options options,
		net.minecraft.client.Camera camera, net.minecraft.client.color.block.BlockColors colors, CallbackInfo ci) {
		if (SILICIUM$CACHED) OwnTerrain.uberInvalidate();
	}

	@Inject(method = "resetLevelRenderData", at = @At("RETURN"))
	private void silicium$uberLevelReset(CallbackInfo ci) {
		if (SILICIUM$CACHED) OwnTerrain.uberInvalidate();
	}

	/** -Dsilicium.own.int.lists=verify: vanilla's walk runs; whenever the skip would have applied, it must have produced nothing. */
	@Inject(method = "extractSectionDrawGroups", at = @At("RETURN"))
	private void silicium$verifyVanillaLists(boolean respectTranslucentOrder, List<?> sectionInfos, Map<?, ?> drawGroups, CallbackInfoReturnable<Integer> cir) {
		if (!SILICIUM$VERIFY) return;
		this.silicium$checked++;
		if (this.silicium$wouldSkip) {
			this.silicium$skipped++;
			boolean empty = cir.getReturnValueI() == 0 && sectionInfos.isEmpty();
			for (Object l : drawGroups.values()) empty &= ((List<?>) l).isEmpty();
			if (!empty) this.silicium$bad++;
		}
		long now = System.nanoTime();
		if (now - this.silicium$lastLog > 5_000_000_000L) {
			System.out.println("silicium-own lists verify: " + this.silicium$checked + " frames, skip would apply in " + this.silicium$skipped
				+ ", mismatches " + this.silicium$bad + ", cache mismatches " + this.silicium$cacheBad);
			this.silicium$lastLog = now;
		}
	}
}
