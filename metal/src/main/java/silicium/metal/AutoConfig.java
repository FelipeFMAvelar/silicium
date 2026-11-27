package silicium.metal;

/**
 * silicium's single source of truth for hardware-dependent decisions.
 *
 * <p>There is no configuration: {@link Defaults} turns every finished optimization on and this class chooses the
 * hardware-dependent values. Values are computed once, before any renderer or mixin reads them. Paths a chip cannot
 * run or that measure slower on it fail closed to the measured behaviour for that chip.
 */
public final class AutoConfig {
	private static final String CHIP = Hardware.chipName();
	private static final int CORES = Hardware.gpuCores();
	private static final boolean M5 = CHIP.contains("M5");
	private static final boolean APPLE_SILICON = CHIP.startsWith("Apple ");
	private static final boolean SMALL = !APPLE_SILICON || CORES > 0 && CORES < 6 || ramGb() > 0 && ramGb() <= 8;

	private AutoConfig() {
	}

	/** Chip string, e.g. "Apple M5". */
	public static String chip() {
		return CHIP;
	}

	public static boolean isM5() {
		return M5;
	}

	public static int gpuCores() {
		return CORES;
	}

	public static boolean smallTier() {
		return SMALL;
	}

	/** The edge cap exists to leave CPU for worldgen; 0 disables it. M5 has enough headroom to run uncapped in flight. */
	public static int edgeCapRefreshMultiplier() {
		return M5 ? 0 : 2;
	}

	private static int ramGb() {
		long bytes = Hardware.memBytes();
		return bytes > 0 ? (int) (bytes >> 30) : -1;
	}

	/* ---- startup ---- */

	public static boolean asyncCrashPreload() {
		return true;  // the existing early-crash test covers it; the hardware probe is pure startup latency
	}

	public static boolean parallelBlockCaches() {
		return true;  // guarded at runtime by Startup's digest comparison
	}

	public static int preloadThreads() {
		return Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
	}

	/* ---- world generation / noise ---- */

	public static boolean serverNativeNoise() {
		return M5 || CORES >= 10;  // bit-exact by construction; verified in the A/B harness before default
	}

	public static boolean nativeNoiseStats() {
		return false;  // counters are measurement-only; keep them off in production
	}

	public static boolean vectorNoise() {
		return false;  // requires JVM flags that the launcher does not pass; NEON wins until measured otherwise
	}

	public static boolean xColumnNoise() {
		return M5;  // only after the size histogram shows small-sy volumes matter
	}

	/* ---- chunk I/O / server tick ---- */

	public static boolean poiEmptyCache() {
		return true;
	}

	public static boolean chunkLightDeltaFlag() {
		return true;
	}

	/* ---- textures / entities / render list ---- */

	public static boolean keepAtlasCompression() {
		String override = System.getProperty("silicium.internal.atlasCompression");  // internal A/B kill switch, not a user setting
		return override != null ? Boolean.parseBoolean(override) : M5 || CORES >= 10;
	}

	public static boolean ownEntityBoxProducer() {
		return true;
	}

	public static boolean allocationFreeTexelLookup() {
		return true;
	}

	public static boolean cachedUberEmptiness() {
		return true;
	}

	public static boolean batchedUploads() {
		return true;
	}

	/* ---- memory / lighting ---- */

	public static boolean lazyTraceArguments() {
		return true;
	}

	public static boolean lazyTransientBuffers() {
		return true;
	}

	public static int lightMapBuckets() {
		return M5 || CORES >= 10 ? 1024 : 256;
	}

	public static boolean pooledAfterFrameLists() {
		return true;
	}

	public static boolean primitiveMeshAllocator() {
		return M5 || CORES >= 10;
	}

	public static int arenaReserveDepth() {
		return M5 ? 2 : 1;
	}

	public static long arenaReserveBytes() {
		return M5 ? 320L << 20 : 256L << 20;
	}

	public static boolean lazyProfilingArrays() {
		return true;
	}

	/* ---- architecture (feature-gated) ---- */

	public static boolean gpuDrivenDrawing() {
		// Metal rejects an ICB pipeline whose fragment function is present ("Fragment shader cannot be used with
		// indirect command buffers", observed on the M5). The native path already fails closed to CPU draws, but
		// enabling it only produces PSO error spam, so keep it off until a fragment-free variant is implemented.
		String override = System.getProperty("silicium.internal.gpuDriven");
		return override != null && Boolean.parseBoolean(override);
	}

	public static boolean gpuLodNoise() {
		return false;  // research item: enable only after strict-IEEE kernel passes whole-tile exactness
	}

	public static boolean precompiledShaders() {
		return true;  // .metallib with source fallback
	}
}
