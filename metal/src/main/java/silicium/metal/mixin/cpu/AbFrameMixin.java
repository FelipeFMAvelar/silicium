package silicium.metal.mixin.cpu;

import silicium.metal.cpu.Ab;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Any -Dsilicium.cpu.*=ab: counts frames for Ab's per-frame arms. */
@Mixin(GameRenderer.class)
abstract class AbFrameMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void silicium$frame(CallbackInfo ci) {
		Ab.frame++;
		if (silicium.metal.cpu.Cpu.AB_SHOTS) silicium.metal.cpu.AbShots.frameStart();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void silicium$frameEnd(CallbackInfo ci) {
		if (silicium.metal.cpu.Cpu.AB_SHOTS) silicium.metal.cpu.AbShots.frameEnd();
	}
}
