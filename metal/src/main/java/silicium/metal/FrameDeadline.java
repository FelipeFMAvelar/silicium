package silicium.metal;

/**
 * A continuous schedule for the frame cap: each deadline is the previous one plus a period, instead of "now plus a period".
 * Late timer wakeups then no longer accumulate into a permanently late schedule. Recovers from hitches, cap changes and
 * long idle periods by starting a new schedule at the current time.
 */
public final class FrameDeadline {
	private long deadline;
	private long interval;

	public long next(long now, long lastFrame, int framesPerSecond) {
		if (framesPerSecond <= 0) {
			this.deadline = 0;
			this.interval = 0;
			return now;
		}
		long period = Math.max(1, 1_000_000_000L / framesPerSecond);
		if (period != this.interval || this.deadline == 0 || now < lastFrame || now - this.deadline > period * 4) {
			this.deadline = lastFrame > 0 && now >= lastFrame && now - lastFrame <= period * 4 ? lastFrame + period : now;
			this.interval = period;
		} else {
			this.deadline += period;
		}
		if (now - this.deadline >= period) this.deadline = now;
		return this.deadline;
	}
}
