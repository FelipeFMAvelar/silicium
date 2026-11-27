package silicium.metal.own;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.system.MemoryUtil;

/**
 * -Dsilicium.own.mesh.exactPos=true (with -Dsilicium.own.compact): exact vertex positions in the 16-byte compact vertex. A coordinate on
 * the 1/2048-block grid in [-8, 22) keeps its grid code (c / 2048 - 8); any other coordinate (vanilla's snow-layer inner face at
 * 0.002 / 16, random block offsets of plants, fluid heights, ...) gets a code >= 61440 indexing a table of its exact float, kept in
 * the arena's first 256 units (4096 floats; the arena is bound wherever positions are decoded, and growth copies it along). The
 * table only grows (one entry per distinct coordinate, section-relative); a full table falls back to the nearest grid code
 * (counted and logged: then that coordinate is as before, rounded). Every producer of compact positions encodes through here.
 */
final class OwnPosTable {
	static final boolean ON = Boolean.getBoolean("silicium.own.mesh.exactPos") && Boolean.getBoolean("silicium.own.compact");
	static final int BASE = 61440, SIZE = 4096, UNITS = SIZE * 4 / 64;

	// Open-addressed int(bits of p) -> code table, linear probing, 8192 slots (power of two, <= 0.5 load with SIZE entries), so
	// CODES lookups are primitive and never box. 0 in CODES means empty; stored codes are BASE+i > 0, so the sentinel is unambiguous.
	// KEYS is written before CODES.set publishes the slot (volatile), so a lock-free reader that sees the code also sees its key;
	// inserts stay serialized on VALUES just as before, and readers re-check inside the lock (double-check) before adding.
	private static final int TABLE_BITS = 13, TABLE_SIZE = 1 << TABLE_BITS, TABLE_MASK = TABLE_SIZE - 1;
	private static final int[] KEYS = new int[TABLE_SIZE];
	private static final AtomicIntegerArray CODES = new AtomicIntegerArray(TABLE_SIZE);
	private static final float[] VALUES = new float[SIZE];
	private static volatile int count;
	private static final AtomicLong OVERFLOW = new AtomicLong();
	private static long logAt, syncLogAt;

	private OwnPosTable() {
	}

	private static int hash(int bits) {
		int h = bits * 0x9E3779B1;
		h ^= h >>> 16;
		return h & TABLE_MASK;
	}

	/** The cached code of the coordinate's bit pattern, or -1 if absent. Safe to call outside the lock. */
	private static int lookup(int bits) {
		int j = hash(bits);
		for (int probe = 0; probe < TABLE_SIZE; probe++) {
			int c = CODES.get(j);
			if (c == 0) return -1;
			if (KEYS[j] == bits) return c;
			j = (j + 1) & TABLE_MASK;
		}
		return -1;
	}

	/** Insert (only from inside synchronized(VALUES), and only when lookup already said absent). */
	private static void insert(int bits, int code) {
		int j = hash(bits);
		for (int probe = 0; probe < TABLE_SIZE; probe++) {
			if (CODES.get(j) == 0) {
				KEYS[j] = bits;
				CODES.set(j, code);  // publish after the key is visible
				return;
			}
			j = (j + 1) & TABLE_MASK;
		}
		throw new IllegalStateException("silicium-own mesh exactPos: code table full at " + TABLE_SIZE + " slots");
	}

	/** The compact code of section-relative coordinate p. */
	static int encode(float p) {
		int g = Math.max(0, Math.min(65535, Math.round((p + 8f) * 2048f)));
		if (g < BASE && g / 2048f - 8f == p) return g;
		int bits = Float.floatToRawIntBits(p);
		int c = lookup(bits);
		if (c >= 0) return c;
		synchronized (VALUES) {
			c = lookup(bits);
			if (c >= 0) return c;
			int i = count;
			if (i >= SIZE) {
				long n = OVERFLOW.incrementAndGet();
				long now = System.nanoTime();
				if (now > logAt) {
					logAt = now + 5_000_000_000L;
					System.out.println("silicium-own mesh exactPos: table full (" + SIZE + "), " + n + " coordinates rounded to the grid");
				}
				return Math.min(g, BASE - 1);
			}
			VALUES[i] = p;
			count = i + 1;  // (volatile: the value is visible before the count)
			insert(bits, BASE + i);
			return BASE + i;
		}
	}

	/** The coordinate of code c (CPU side, as the shader decodes it). */
	static float decode(int c) {
		return c >= BASE && ON ? VALUES[c - BASE] : c / 2048f - 8f;
	}

	/**
	 * Copies entries [from, count) into the arena at address a (its first UNITS units) and returns the new count; the render
	 * thread calls it after applying the frame's publish events (each event was queued after the entries its quads use).
	 */
	static int sync(long a, int from) {
		int n = count;
		if (n != from) {
			long now = System.nanoTime();
			if (now > syncLogAt) {
				syncLogAt = now + 5_000_000_000L;
				System.out.println("silicium-own mesh exactPos: table " + n + " of " + SIZE + " entries, " + OVERFLOW.get() + " rounded");
			}
		}
		for (int i = from; i < n; i++) MemoryUtil.memPutFloat(a + 4L * i, VALUES[i]);
		return n;
	}

	static int size() {
		return count;
	}
}
