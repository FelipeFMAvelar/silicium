package silicium.metal.mixin.chunk;

import silicium.metal.chunk.PoiScan;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Arms PoiScan's empty-section cache. {@code PoiManager.setDirty(long)} is the single hook through which every POI
 * write (add/remove/release/refresh) marks its section dirty, so invalidating there keeps the cached "empty" answers
 * correct. The mixin is applied only with the existing chunk.poi switch; no new user setting.
 */
@Mixin(PoiManager.class)
abstract class PoiWriteMixin {
	@Inject(method = "setDirty", at = @At("HEAD"))
	private void silicium$poiInvalidate(long key, CallbackInfo ci) {
		PoiScan.markInvalidationWired();
		PoiScan.invalidate((PoiManager) (Object) this, key);
	}
}
