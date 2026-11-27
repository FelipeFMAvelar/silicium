package silicium.metal.mixin.own;

import silicium.metal.FrameLog;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measurement only (-Dsilicium.own.int.frameLog): the frame's extract and render times. */
@Mixin(GameRenderer.class)
public class FrameLogGameRendererMixin {
	@Inject(method = "extract", at = @At("HEAD"))
	private void silicium$frameLogExtractHead(DeltaTracker deltaTracker, boolean advance, CallbackInfo ci) {
		FrameLog.begin(FrameLog.EXTRACT);
	}

	@Inject(method = "extract", at = @At("RETURN"))
	private void silicium$frameLogExtractReturn(DeltaTracker deltaTracker, boolean advance, CallbackInfo ci) {
		FrameLog.end(FrameLog.EXTRACT);
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void silicium$frameLogRenderHead(CallbackInfo ci) {
		FrameLog.begin(FrameLog.RENDER);
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void silicium$frameLogRenderReturn(CallbackInfo ci) {
		FrameLog.end(FrameLog.RENDER);
	}
}
