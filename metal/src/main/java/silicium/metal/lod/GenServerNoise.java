package silicium.metal.lod;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;

/**
 * The server-side door of LodNativeNoise (-Dsilicium.gen.nativeNoise, NEON by default where AutoConfig picks it):
 * GenNoiseChunkMixin passes the sampler set a NoiseChunk takes from its RandomState through here, so the integrated
 * server's chunk generation evaluates the same copied noise leaves as the far terrain. Off: the same set is returned.
 *
 * <p>RandomState keeps one sampler set per SamplerContext and the game builds a NoiseChunk per chunk, so the wrapper
 * (and its Bound objects, cached in LodNativeNoise) is cached here per owner and set instead of being reallocated for
 * every NoiseChunk. The identity and contents of every Bound are unchanged.
 */
public final class GenServerNoise {
	private GenServerNoise() {
	}

	/** Owner -> its sampler sets -> the wrapped set. Weak owner keys: a dimension takes its wrappers with it. */
	private static final Map<Object, Map<DensitySamplerSet, DensitySamplerSet>> WRAPPED = new WeakHashMap<>();

	public static DensitySamplerSet wrap(DensitySamplerSet set, Object randomState) {
		synchronized (WRAPPED) {
			Map<DensitySamplerSet, DensitySamplerSet> bySet = WRAPPED.computeIfAbsent(randomState, o -> new IdentityHashMap<>());
			DensitySamplerSet w = bySet.get(set);
			if (w == null) {
				w = LodNativeNoise.wrapServer(set, randomState);
				bySet.put(set, w);
			}
			return w;
		}
	}
}
