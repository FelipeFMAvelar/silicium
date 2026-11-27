package silicium.metal;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * -Dsilicium.cpu.texelCache=true: texel-buffer views (MTLTextures over a buffer range) kept across frames. MetalRenderPass
 * keeps them per pass object, so a uniform bound every frame (Sodium's u_SectionTimeInfo) made a new driver texture each frame.
 * Keyed by the MetalBuffer, the MTLBuffer it currently sits on (rename() moves it), offset, length and format, so a hit is a view
 * over exactly the same storage; a view no submit has used for 3 submits is released (by then no frame in flight uses it).
 */
final class TexelViews {
	static final boolean ON = Boolean.getBoolean("silicium.cpu.texelCache");
	/** silicium's allocation-free lookup decision (AutoConfig): reuse a mutable lookup key instead of allocating one per bind. */
	static final boolean ALLOC_FREE = silicium.metal.AutoConfig.allocationFreeTexelLookup();
	/** Per-frame switch for the same-run picture check (AbShots); always true otherwise. */
	static volatile boolean active = true;
	private static final int KEEP_SUBMITS = 3;

	/**
	 * Mutable so one instance can be reused for lookups. The stored keys are separate, never-mutated instances; the lookup key is
	 * never inserted into the map (it changes on the next get()).
	 */
	private static final class Key {
		MetalBuffer buffer;
		long memory, offset, length;
		int format;

		Key(MetalBuffer buffer, long memory, long offset, long length, int format) {
			this.set(buffer, memory, offset, length, format);
		}

		void set(MetalBuffer buffer, long memory, long offset, long length, int format) {
			this.buffer = buffer;
			this.memory = memory;
			this.offset = offset;
			this.length = length;
			this.format = format;
		}

		@Override
		public int hashCode() {
			int h = this.buffer.hashCode();
			h = 31 * h + Long.hashCode(this.memory);
			h = 31 * h + Long.hashCode(this.offset);
			h = 31 * h + Long.hashCode(this.length);
			return 31 * h + this.format;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Key k)) return false;
			return this.buffer == k.buffer && this.memory == k.memory && this.offset == k.offset && this.length == k.length && this.format == k.format;
		}
	}

	private static final Key LOOKUP = new Key(null, 0, 0, 0, 0);

	private static final class View {
		final long handle;
		long lastSubmit;

		View(long handle) {
			this.handle = handle;
		}
	}

	private static final Map<Key, View> views = new HashMap<>();
	private static long lastSweep = -1;
	static long created, hits;

	private TexelViews() {
	}

	static long get(MetalEncoder encoder, GpuBufferSlice slice, int pixelFormat, int blockSize) {
		MetalBuffer buffer = encoder.use(slice.buffer());
		long submit = buffer.lastUse; // use() just stamped it with the current submit index
		if (submit != lastSweep) {
			lastSweep = submit;
			for (Iterator<View> it = views.values().iterator(); it.hasNext(); ) {
				View v = it.next();
				if (submit - v.lastSubmit > KEEP_SUBMITS) {
					encoder.releaseLater(v.handle);
					it.remove();
				}
			}
		}
		long memory = buffer.handle, offset = slice.offset(), length = slice.length();
		Key key;
		if (ALLOC_FREE) {
			// (never stored: rename() may change buffer.handle, which is part of the key, so a stale view is never hit)
			LOOKUP.set(buffer, memory, offset, length, pixelFormat);
			key = LOOKUP;
		} else {
			key = new Key(buffer, memory, offset, length, pixelFormat);
		}
		View v = views.get(key);
		if (v == null) {
			v = new View(Native.textureBuffer(encoder.ctx, memory, pixelFormat, offset, length, blockSize));
			// the lookup key is reused; store a snapshot that stays valid after the next get()
			views.put(ALLOC_FREE ? new Key(buffer, memory, offset, length, pixelFormat) : key, v);
			created++;
		} else {
			hits++;
		}
		v.lastSubmit = submit;
		return v.handle;
	}
}
