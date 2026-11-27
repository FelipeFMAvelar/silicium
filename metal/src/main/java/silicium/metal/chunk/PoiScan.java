package silicium.metal.chunk;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import silicium.metal.AutoConfig;
import silicium.metal.mixin.chunk.PoiSectionAccessor;
import silicium.metal.mixin.chunk.SectionStorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;

/**
 * D1 (-Dsilicium.chunk.poi=true): PoiManager.findAllClosestFirstWithType as plain loops, for AcquirePoi, which consumes the stream
 * at once (limit 5, filter, collect). Vanilla's pipeline: chunks of ChunkPos.rangeClosed (x fastest, then z) around the centre,
 * sections bottom to top (getOrLoad: may load POI data from disk), each section's byType entries whose type passes, each record
 * in its set that passes the occupancy test, then the square, the radius and the caller's filter (AcquirePoi's retry cache, which
 * has side effects), then a stable sort by distance. sorted() pulls every upstream element before the first result, so running
 * the same steps here, in the same order, once, gives the same list and the same side effects in the same order. List.sort is
 * the same stable merge sort with the same comparator.
 */
public final class PoiScan {
	private PoiScan() { }

	/**
	 * Per-PoiManager set of section keys whose {@code getOrLoad} returned {@code Optional.empty()} (see {@link #emptyCache}).
	 * Weak keys so a discarded manager does not pin its cache. Access is synchronized because POI scans (and the writes that
	 * must invalidate them) can land on different server threads.
	 */
	private static final Map<PoiManager, LongOpenHashSet> EMPTY_SECTIONS = Collections.synchronizedMap(new WeakHashMap<>());

	/**
	 * A cached empty result is only a proof of "still empty" if every POI write invalidates the entry. This task's file scope
	 * cannot add the required {@code PoiManager.add/remove/release/setDirty} mixin (it would need a new mixin class and an edit
	 * to the mixin config), so no write hook exists yet. The cache therefore fails closed until a write hook explicitly arms it
	 * by calling {@link #markInvalidationWired()}; until then every scan still calls {@code getOrLoad} as before.
	 */
	private static volatile boolean INVALIDATION_WIRED = false;

	/** Config gate, read once from the single source of truth (AutoConfig). */
	private static final boolean CONFIGURED = AutoConfig.poiEmptyCache();

	/** True only when both the config enables the cache and its invalidation is known to be wired. */
	private static boolean cacheEnabled() {
		return CONFIGURED && INVALIDATION_WIRED;
	}

	/**
	 * Called from the POI write path (outside this file's scope) once it is guaranteed that every add/remove/release invalidates
	 * the affected section through {@link #invalidate} / {@link #invalidateAll}. Until it is called the cache is inert.
	 */
	public static void markInvalidationWired() {
		INVALIDATION_WIRED = true;
	}

	/** Forget that a section was empty; call on every POI write into {@code key}. */
	public static void invalidate(PoiManager poi, long key) {
		if (poi == null) return;
		synchronized (EMPTY_SECTIONS) {
			LongOpenHashSet set = EMPTY_SECTIONS.get(poi);
			if (set != null) set.remove(key);
		}
	}

	/** Forget all empty sections of a manager (e.g. after a bulk POI reload / world unload). */
	public static void invalidateAll(PoiManager poi) {
		if (poi == null) return;
		synchronized (EMPTY_SECTIONS) {
			EMPTY_SECTIONS.remove(poi);
		}
	}

	private static LongOpenHashSet emptyCache(PoiManager poi) {
		synchronized (EMPTY_SECTIONS) {
			return EMPTY_SECTIONS.computeIfAbsent(poi, k -> new LongOpenHashSet());
		}
	}

	/**
	 * poi=verify: vanilla's stream runs for real with the caller's filter wrapped to record every call and its answer; then the
	 * loops run with a filter that replays those answers and checks it is asked about the same positions in the same order;
	 * the two sorted lists must be equal. Vanilla's result is what the game gets.
	 */
	public static Stream<Pair<Holder<PoiType>, BlockPos>> verify(
		PoiManager poi, Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter, BlockPos center, int radius, PoiManager.Occupancy occupancy
	) {
		List<BlockPos> asked = new ArrayList<>();
		List<Boolean> answers = new ArrayList<>();
		List<Pair<Holder<PoiType>, BlockPos>> vanilla = poi.findAllClosestFirstWithType(predicate, pos -> {
			boolean r = filter.test(pos);
			asked.add(pos.immutable());
			answers.add(r);
			return r;
		}, center, radius, occupancy).toList();
		int[] k = {0};
		boolean[] sameCalls = {true};
		List<Pair<Holder<PoiType>, BlockPos>> mine = closestFirstWithType(poi, predicate, pos -> {
			int i = k[0]++;
			if (i >= asked.size() || !asked.get(i).equals(pos)) {
				sameCalls[0] = false;
				return false;
			}
			return answers.get(i);
		}, center, radius, occupancy, false).toList(); // verify must replay vanilla's call order with no cache skips
		boolean same = sameCalls[0] && k[0] == asked.size() && vanilla.equals(mine);
		ChunkOpt.count(same ? "poi.verify.same" : "poi.verify.MISMATCH");
		ChunkOpt.count("poi.verify.filterCalls", asked.size());
		ChunkOpt.count("poi.verify.results", vanilla.size());
		return vanilla.stream();
	}

	public static Stream<Pair<Holder<PoiType>, BlockPos>> closestFirstWithType(
		PoiManager poi, Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter, BlockPos center, int radius, PoiManager.Occupancy occupancy
	) {
		boolean useCache = cacheEnabled();
		if (ChunkOpt.STATS && CONFIGURED && !INVALIDATION_WIRED) ChunkOpt.count("poi.emptyCache.failClosed");
		return closestFirstWithType(poi, predicate, filter, center, radius, occupancy, useCache);
	}

	/**
	 * The loops, with section emptiness caching only when {@code useCache} is true. Chunk order (z, then x), section order
	 * (bottom to top), type/record iteration, the square/radius tests and the caller's side-effecting filter are untouched: a
	 * cached empty section is skipped <em>before</em> its (empty, side-effect-free) {@code getOrLoad} only, so nothing else in
	 * the pipeline observes a different call order.
	 */
	@SuppressWarnings("unchecked")
	private static Stream<Pair<Holder<PoiType>, BlockPos>> closestFirstWithType(
		PoiManager poi, Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter, BlockPos center, int radius,
		PoiManager.Occupancy occupancy, boolean useCache
	) {
		LongOpenHashSet cache = useCache ? emptyCache(poi) : null;
		SectionStorageAccess storage = (SectionStorageAccess) poi;
		LevelHeightAccessor height = storage.silicium$levelHeightAccessor();
		int minY = height.getMinSectionY(), maxY = height.getMaxSectionY();
		int chunkRadius = Math.floorDiv(radius, 16) + 1;
		ChunkPos c = ChunkPos.containing(center);
		long radiusSqr = (long) radius * radius;
		Predicate<? super PoiRecord> occupied = occupancy.getTest();
		List<Pair<Holder<PoiType>, BlockPos>> out = new ArrayList<>();
		for (int z = c.z() - chunkRadius; z <= c.z() + chunkRadius; z++) {
			for (int x = c.x() - chunkRadius; x <= c.x() + chunkRadius; x++) {
				for (int sy = minY; sy <= maxY; sy++) {
					long key = SectionPos.asLong(x, sy, z);
					if (cache != null && cache.contains(key)) {
						if (ChunkOpt.STATS) ChunkOpt.count("poi.emptyCache.hit");
						continue;
					}
					if (cache != null && ChunkOpt.STATS) ChunkOpt.count("poi.emptyCache.miss");
					Optional<?> section = storage.silicium$getOrLoad(key);
					if (section.isEmpty()) {
						if (cache != null) cache.add(key);
						continue;
					}
					for (Map.Entry<Holder<PoiType>, Set<PoiRecord>> e : ((PoiSectionAccessor) section.get()).silicium$byType().entrySet()) {
						if (!predicate.test(e.getKey())) continue;
						for (PoiRecord r : e.getValue()) {
							if (!occupied.test(r)) continue;
							BlockPos pos = r.getPos();
							if (Math.abs(pos.getX() - center.getX()) > radius || Math.abs(pos.getZ() - center.getZ()) > radius) continue;
							if (pos.distSqr(center) > radiusSqr) continue;
							if (!filter.test(pos)) continue;
							out.add(Pair.of(r.getPoiType(), pos));
						}
					}
				}
			}
		}
		out.sort(Comparator.comparingDouble(p -> p.getSecond().distSqr(center)));
		if (ChunkOpt.STATS && !ChunkOpt.POI_VERIFY) ChunkOpt.count("poi.scans");
		return out.stream();
	}
}
