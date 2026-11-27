package silicium.metal;

import java.nio.charset.StandardCharsets;

/** Hardware probes for the chip generation, GPU core count and memory size. Each is read at most once. */
public final class Hardware {
	private static int gpuCores = -2;
	private static String chipName;
	private static long memBytes = -2;

	private Hardware() {
	}

	/**
	 * The Apple Silicon generation from {@code machdep.cpu.brand_string} (e.g. "Apple M5"), or "" if unreadable.
	 * Unlike GPU core count this distinguishes an M5 from an older 10-core M4: they have different shader
	 * capabilities and different performance tradeoffs even with the same number of GPU cores.
	 */
	public static synchronized String chipName() {
		if (chipName == null) chipName = run("/usr/sbin/sysctl", "-n", "machdep.cpu.brand_string").trim();
		return chipName;
	}

	/** The GPU's core count from the IORegistry (AGXAccelerator "gpu-core-count"), -1 if unreadable. */
	public static synchronized int gpuCores() {
		if (gpuCores == -2) {
			String s = run("/usr/sbin/ioreg", "-rd1", "-c", "AGXAccelerator");
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"gpu-core-count\"\\s*=\\s*(\\d+)").matcher(s);
			gpuCores = m.find() ? Integer.parseInt(m.group(1)) : -1;
		}
		return gpuCores;
	}

	/** Physical memory in bytes, or -1 if unreadable. */
	public static synchronized long memBytes() {
		if (memBytes == -2) {
			try {
				memBytes = Long.parseLong(run("/usr/sbin/sysctl", "-n", "hw.memsize").trim());
			} catch (NumberFormatException e) {
				memBytes = -1;
			}
		}
		return memBytes;
	}

	private static String run(String... cmd) {
		try {
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null"))).start();
			byte[] b = p.getInputStream().readAllBytes();
			p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
			return new String(b, StandardCharsets.UTF_8);
		} catch (Exception e) {
			return "";
		}
	}
}
