package silicium.metal.mixin;

import silicium.metal.FrameDeadline;
import silicium.metal.FrameWait;
import net.minecraft.client.FramerateLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's frame cap parks the render thread with parkNanos, then spins until the frame's deadline. On macOS that park is
 * a coalescable timer that wakes up to a quarter or more of the wait late (a 60 fps cap ran at 51 fps with Sodium), and the
 * spin burns a core for nothing. One wait on a timer that fires on time, no spin, and a continuous deadline so late
 * wakeups don't shift every following frame. Applies to every cap: the fps option, menus, AFK and minimized.
 */
@Mixin(FramerateLimiter.class)
abstract class FramerateLimiterMixin {
	@Shadow
	private static long lastFrameTime;
	@Unique
	private static final FrameDeadline silicium$deadlines = new FrameDeadline();

	@Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
	private static void silicium$preciseWait(int framerateLimit, CallbackInfo ci) {
		if (!FrameWait.ENABLED) return;
		FrameWait.sleepUntil(silicium$deadlines.next(System.nanoTime(), lastFrameTime, framerateLimit));
		lastFrameTime = System.nanoTime();
		ci.cancel();
	}
}
