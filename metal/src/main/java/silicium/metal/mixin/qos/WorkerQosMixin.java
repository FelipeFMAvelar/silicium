package silicium.metal.mixin.qos;

import silicium.metal.Qos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * -Dsilicium.qos.worker: Minecraft's Worker-Main pool threads (Util.makeExecutor's ForkJoinWorkerThread subclass). Qos.self
 * claims the thread, so GenWorldgenQosMixin's LodGenQos.once becomes a no-op on these same worldgen threads.
 */
@Mixin(targets = "net.minecraft.util.Util$2")
abstract class WorkerQosMixin {
	@Inject(method = "onStart", at = @At("HEAD"))
	private void silicium$qos(CallbackInfo ci) {
		Qos.self("worker");
	}
}
