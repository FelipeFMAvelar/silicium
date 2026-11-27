package silicium.metal.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import silicium.metal.AutoConfig;
import net.minecraft.world.level.chunk.DataLayer;

/**
 * C3: the section map of a published light snapshot (LightSnapshots): lookups go to the delta, then to the shared base. A
 * snapshot's map is only ever read through get/containsKey (DataLayerStorageMap.getLayer/hasLayer with the cache disabled), so
 * everything else is refused rather than answered wrongly.
 */
public final class LayeredLightMap extends Long2ObjectOpenHashMap<DataLayer> {
	private static final boolean DELTA_FLAG = AutoConfig.chunkLightDeltaFlag();
	public Long2ObjectOpenHashMap<DataLayer> base, delta;
	/** Set once when the snapshot is published; delta is never mutated afterwards, so an empty delta can be skipped. */
	public boolean deltaEmpty;

	public LayeredLightMap() {
		super(0);
	}

	@Override
	public DataLayer get(long k) {
		if (!DELTA_FLAG || !this.deltaEmpty) {
			DataLayer d = this.delta.get(k);
			if (d != null) return d == LightSnapshots.REMOVED ? null : d;
		}
		return this.base.get(k);
	}

	@Override
	public boolean containsKey(long k) {
		return this.get(k) != null;
	}

	@Override
	public DataLayer put(long k, DataLayer v) {
		throw new UnsupportedOperationException("light snapshot is read-only");
	}

	@Override
	public DataLayer remove(long k) {
		throw new UnsupportedOperationException("light snapshot is read-only");
	}

	@Override
	public int size() {
		throw new UnsupportedOperationException("light snapshot has no size");
	}

	@Override
	public LayeredLightMap clone() {
		throw new UnsupportedOperationException("light snapshot is not copied");
	}
}
