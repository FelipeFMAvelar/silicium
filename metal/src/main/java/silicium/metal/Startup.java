package silicium.metal;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Startup changes that cost no fps, only launch latency. The paths are chosen by {@link AutoConfig}; the
 * {@code -Dsilicium.*} keys are internal development / A/B overrides, not user settings.
 * <ul>
 * <li>The warm-up crash report (its hardware probe runs an external command) is built on a daemon thread by default;
 * the memory reserve is still allocated first, on the main thread. {@code -Dsilicium.startup.crashPreload=false} turns it
 * back off in an emergency.</li>
 * <li>Every block state's cache (shapes, light, sturdy faces) is built in parallel by default after the registry loop,
 * which keeps adding the states to the id map in order. {@code -Dsilicium.startup.blockCache=digest} / {@code =parallel-digest}
 * are one-off A/B tools that compute sequentially / in parallel and print a digest; no verification runs on a normal
 * launch. Any other explicit value forces the sequential path.</li>
 * <li>{@code -Dsilicium.startup.narrator=lazy}: the macOS speech channel is created on the narrator's first
 * use instead of in Minecraft's constructor.</li>
 * <li>{@code -Dsilicium.startup.fadeMs=N}: the loading overlay's fade-out takes N ms instead of 1000 (fade-in N/2 instead of 500);
 * a visible UI change, not a game-logic one.</li>
 * </ul>
 */
public final class Startup {
	/** Effective value: the AutoConfig default unless an explicit (emergency) override is present. */
	public static final String CRASH_PRELOAD = System.getProperty("silicium.startup.crashPreload", AutoConfig.asyncCrashPreload() ? "async" : "");
	public static final String BLOCK_CACHE = System.getProperty("silicium.startup.blockCache", "");
	public static final float FADE_MS = Float.parseFloat(System.getProperty("silicium.startup.fadeMs", "-1"));

	private static final List<BlockState> PENDING = new ArrayList<>();

	private Startup() {
	}

	/** The effective async crash-preload decision, mirroring the mixin plugin's condition (which cannot load this class). */
	public static boolean asyncCrashPreload() {
		return !CRASH_PRELOAD.isEmpty() && !"false".equalsIgnoreCase(CRASH_PRELOAD);
	}

	/** The cache path used by both the registry loop and {@link #finishBlockCaches()}. */
	public static boolean parallelBlockCache() {
		if (!BLOCK_CACHE.isEmpty()) return BLOCK_CACHE.startsWith("parallel");
		return AutoConfig.parallelBlockCaches();
	}

	/** Collects a state whose cache the registry loop would have built now. */
	public static void deferInitCache(BlockState state) {
		PENDING.add(state);
	}

	/** At the end of the registry loop: build the deferred caches the one chosen way, before Blocks' init returns. */
	public static void finishBlockCaches() {
		long t0 = System.nanoTime();
		boolean parallel = parallelBlockCache();
		boolean digest = BLOCK_CACHE.endsWith("digest");
		int states = PENDING.size();

		if (parallel) {
			ForkJoinPool pool = new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
			try {
				pool.submit(() -> PENDING.parallelStream().forEach(BlockState::initCache)).join();
			} finally {
				pool.shutdown();
			}
		} else {
			PENDING.forEach(BlockState::initCache);
		}

		// Timing covers the cache work only, as before; the digest is a separate, opt-in A/B measurement.
		long built = System.nanoTime();
		if (digest) {
			long h = digest();
			System.out.printf("[silicium] block caches %s, %d states, %.1f ms, digest %016x%n", parallel ? "parallel" : "sequential",
				states, (built - t0) / 1e6, h);
		} else {
			System.out.printf("[silicium] block caches %s, %d states, %.1f ms%n", parallel ? "parallel" : "sequential",
				states, (built - t0) / 1e6);
		}
		PENDING.clear();
	}

	/** Every field initCache sets, for every state in id order: a value digest (shapes by their boxes, arrays element-wise). */
	private static long digest() {
		long h = 1125899906842597L;
		try {
			Class<?> base = BlockState.class.getSuperclass();
			List<Field> fields = new ArrayList<>();
			for (Field f : base.getDeclaredFields()) {
				// only what initCache sets: the non-final instance fields (the final ones come from the constructor)
				if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers())) {
					f.setAccessible(true);
					fields.add(f);
				}
			}
			String dump = System.getProperty("silicium.startup.blockCacheDump");
			java.io.PrintWriter out = dump == null ? null : new java.io.PrintWriter(java.nio.file.Files.newBufferedWriter(java.nio.file.Path.of(dump)));
			int i = 0;
			for (BlockState s : net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY) {
				for (Field f : fields) {
					long v = value(f.get(s), 0);
					h = mix(h, v);
					if (out != null) out.printf("%d %s %s %016x%n", i, s, f.getName(), v);
				}
				i++;
			}
			if (out != null) out.close();
			// registry order (numeric ids) of blocks and items: Items' initialisation can move to a cache worker
			for (var b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) h = mix(h, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).hashCode());
			for (var it : net.minecraft.core.registries.BuiltInRegistries.ITEM) h = mix(h, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(it).hashCode());
		} catch (ReflectiveOperationException | java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		return h;
	}

	private static long value(Object v, int depth) throws IllegalAccessException {
		if (v == null) return 0;
		if (v instanceof VoxelShape shape) return shape.toAabbs().hashCode();
		if (v.getClass().isArray()) {
			long h = 7;
			for (int i = 0; i < Array.getLength(v); i++) h = mix(h, value(Array.get(v, i), depth + 1));
			return h;
		}
		if (v instanceof Enum<?> e) return e.name().hashCode();
		if (v instanceof Number || v instanceof Boolean || v instanceof String) return v.hashCode();
		if (v.getClass().getName().endsWith("$Cache") && depth == 0) {
			long h = 11;
			for (Field f : v.getClass().getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers())) continue;
				f.setAccessible(true);
				h = mix(h, value(f.get(v), depth + 1));
			}
			return h;
		}
		if (v instanceof net.minecraft.world.level.material.FluidState fs) return fs.toString().hashCode();
		throw new IllegalStateException("block cache digest: unhandled value type " + v.getClass().getName());
	}

	private static long mix(long h, long v) {
		h ^= v + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
		return h * 0xff51afd7ed558ccdL;
	}
}
