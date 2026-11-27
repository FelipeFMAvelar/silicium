package silicium.metal.mixin.cpu;

import silicium.metal.cpu.Ab;
import silicium.metal.cpu.Cpu;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** -Dsilicium.cpu.model=ab: times FeatureRenderDispatcher.prepareFrame (where models are built) with the lever alternately off/on. */
@Mixin(FeatureRenderDispatcher.class)
abstract class ModelAbMixin {
	@Unique private static final Ab silicium$ab = new Ab("model.prepareFrame");

	@Inject(method = "prepareFrame", at = @At("HEAD"))
	private void silicium$begin(CallbackInfoReturnable<?> cir) {
		if (Cpu.MODEL_AB) Cpu.modelActive = silicium$ab.begin();
	}

	@Inject(method = "prepareFrame", at = @At("RETURN"))
	private void silicium$end(CallbackInfoReturnable<?> cir) {
		if (Cpu.MODEL_AB) silicium$ab.end();
	}
}
