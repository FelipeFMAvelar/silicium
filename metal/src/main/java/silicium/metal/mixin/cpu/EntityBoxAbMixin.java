package silicium.metal.mixin.cpu;

import silicium.metal.cpu.Ab;
import silicium.metal.cpu.EntityBox;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dsilicium.cpu.entityBox=ab: times the frame's entity pass with the box reuse alternately off and on (see Ab). */
@Mixin(LevelExtractor.class)
abstract class EntityBoxAbMixin {
	@Unique private static final Ab silicium$ab = new Ab("entityBox.extractVisibleEntities");

	@Inject(method = "extractVisibleEntities", at = @At("HEAD"))
	private void silicium$begin(CallbackInfo ci) {
		if (EntityBox.AB) EntityBox.active = silicium$ab.begin();
	}

	@Inject(method = "extractVisibleEntities", at = @At("RETURN"))
	private void silicium$end(CallbackInfo ci) {
		if (EntityBox.AB) silicium$ab.end();
	}
}
