package silicium.metal.mixin.own;

import silicium.metal.FrameLog;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dsilicium.own.int.frameLog): the client tick's time in the frame it runs in. */
@Mixin(Minecraft.class)
public class FrameLogMinecraftMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void silicium$frameLogTickHead(CallbackInfo ci) {
		FrameLog.begin(FrameLog.TICK);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void silicium$frameLogTickReturn(CallbackInfo ci) {
		FrameLog.end(FrameLog.TICK);
	}
}
