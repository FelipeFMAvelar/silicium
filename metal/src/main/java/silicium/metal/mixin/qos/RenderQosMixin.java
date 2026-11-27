package silicium.metal.mixin.qos;

import silicium.metal.Qos;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dsilicium.qos.render: the render thread's class, set as the game loop starts. */
@Mixin(Minecraft.class)
abstract class RenderQosMixin {
	@Inject(method = "run", at = @At("HEAD"))
	private void silicium$qos(CallbackInfo ci) {
		Qos.self("render");
	}
}
