package silicium.metal.mixin.edge;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import silicium.metal.EdgeCap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
abstract class FramerateCapMixin {
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void silicium$edgeCap(CallbackInfoReturnable<Integer> cir) {
		int limit = cir.getReturnValueI(), capped = EdgeCap.limit(limit);
		if (capped != limit) cir.setReturnValue(capped);
	}
}
