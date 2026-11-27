package silicium.metal.mixin.qos;

import silicium.metal.Qos;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dsilicium.qos.server: the integrated server thread's class, set from the thread as it starts. */
@Mixin(MinecraftServer.class)
abstract class ServerQosMixin {
	@Inject(method = "runServer", at = @At("HEAD"))
	private void silicium$qos(CallbackInfo ci) {
		Qos.self("server");
	}
}
