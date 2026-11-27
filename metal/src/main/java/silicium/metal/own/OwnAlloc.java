package silicium.metal.own;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import silicium.metal.AutoConfig;

/**
 * Best-fit range allocator over [0, capacity) in units (quads, records): free ranges by start (for merging neighbours) and by
 * size then start (best fit). Not thread-safe: callers lock.
 *
 * <p>The measured path keeps both orderings in sorted primitive long arrays (size&lt;&lt;32|start and start&lt;&lt;32|size), with
 * binary search and arraycopy. The old TreeMap/TreeSet implementation stays for hardware we cannot test; it is selected by
 * {@link AutoConfig#primitiveMeshAllocator()} and produces exactly the same best fit and merge results.
 */
final class OwnAlloc {
	private static final boolean PRIMITIVE = AutoConfig.primitiveMeshAllocator();

	private final TreeMap<Integer, Integer> byStart = PRIMITIVE ? null : new TreeMap<>();
	private final TreeSet<Long> bySize = PRIMITIVE ? null : new TreeSet<>();
	private long[] bySizePrim = new long[0];
	private long[] byStartPrim = new long[0];
	private int ranges;
	private int capacity;
	private long used;

	OwnAlloc(int capacity) {
		this.capacity = capacity;
		this.addFree(0, capacity);
	}

	int capacity() {
		return this.capacity;
	}

	long used() {
		return this.used;
	}

	/** Start of a free range of n units, or -1 when none is big enough. */
	int alloc(int n) {
		if (PRIMITIVE) return this.primAlloc(n);
		Long fit = this.bySize.ceiling((long) n << 32);
		if (fit == null) return -1;
		int start = (int) (long) fit, size = (int) (fit >>> 32);
		this.removeFree(start, size);
		if (size > n) this.addFree(start + n, size - n);
		this.used += n;
		return start;
	}

	void free(int start, int n) {
		if (n <= 0) return;
		this.used -= n;
		if (PRIMITIVE) {
			this.primFree(start, n);
			return;
		}
		Map.Entry<Integer, Integer> before = this.byStart.floorEntry(start - 1);
		if (before != null && before.getKey() + before.getValue() == start) {
			this.removeFree(before.getKey(), before.getValue());
			start = before.getKey();
			n += before.getValue();
		}
		Integer after = this.byStart.get(start + n);
		if (after != null) {
			this.removeFree(start + n, after);
			n += after;
		}
		this.addFree(start, n);
	}

	/** Capacity grows to newCapacity; the new tail is free. */
	void grow(int newCapacity) {
		if (newCapacity <= this.capacity) return;
		int old = this.capacity;
		this.capacity = newCapacity;
		this.used += newCapacity - old;  // free() below takes it back out
		this.free(old, newCapacity - old);
	}

	private void addFree(int start, int size) {
		if (PRIMITIVE) {
			this.addPrim(start, size);
			return;
		}
		this.byStart.put(start, size);
		this.bySize.add((long) size << 32 | start);
	}

	private void removeFree(int start, int size) {
		if (PRIMITIVE) {
			this.removePrimByStart(this.lowerBoundStart(start), size);
			return;
		}
		this.byStart.remove(start);
		this.bySize.remove((long) size << 32 | start);
	}

	/* ---- primitive best-fit state: the same two orderings, packed into longs ---- */

	private static long sizeKey(int start, int size) {
		return (long) size << 32 | start;
	}

	private static long startKey(int start, int size) {
		return (long) start << 32 | size;
	}

	private int lowerBound(long[] a, long key) {
		int lo = 0, hi = this.ranges;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (a[mid] < key) lo = mid + 1;
			else hi = mid;
		}
		return lo;
	}

	private int lowerBoundStart(int start) {
		int at = this.lowerBound(this.byStartPrim, (long) start << 32);
		return at < this.ranges && (int) (this.byStartPrim[at] >>> 32) == start ? at : this.ranges;
	}

	private void addPrim(int start, int size) {
		long sk = sizeKey(start, size), pk = startKey(start, size);
		int si = this.lowerBound(this.bySizePrim, sk), pi = this.lowerBound(this.byStartPrim, pk);
		if (this.ranges == this.bySizePrim.length) {
			int cap = Math.max(4, this.ranges << 1);
			this.bySizePrim = Arrays.copyOf(this.bySizePrim, cap);
			this.byStartPrim = Arrays.copyOf(this.byStartPrim, cap);
		}
		System.arraycopy(this.bySizePrim, si, this.bySizePrim, si + 1, this.ranges - si);
		System.arraycopy(this.byStartPrim, pi, this.byStartPrim, pi + 1, this.ranges - pi);
		this.bySizePrim[si] = sk;
		this.byStartPrim[pi] = pk;
		this.ranges++;
	}

	/** Removes the range at a known byStart index; both parallel arrays stay sorted. */
	private void removePrimByStart(int pi, int size) {
		long pk = this.byStartPrim[pi];
		int start = (int) (pk >>> 32);
		long sk = sizeKey(start, size);
		int si = this.lowerBound(this.bySizePrim, sk);
		System.arraycopy(this.bySizePrim, si + 1, this.bySizePrim, si, this.ranges - si - 1);
		System.arraycopy(this.byStartPrim, pi + 1, this.byStartPrim, pi, this.ranges - pi - 1);
		this.ranges--;
	}

	private void removePrimBySize(int si) {
		long sk = this.bySizePrim[si];
		int start = (int) sk, size = (int) (sk >>> 32);
		int pi = this.lowerBoundStart(start);
		System.arraycopy(this.bySizePrim, si + 1, this.bySizePrim, si, this.ranges - si - 1);
		System.arraycopy(this.byStartPrim, pi + 1, this.byStartPrim, pi, this.ranges - pi - 1);
		this.ranges--;
	}

	private int primAlloc(int n) {
		int si = this.lowerBound(this.bySizePrim, (long) n << 32);
		if (si == this.ranges) return -1;
		long fit = this.bySizePrim[si];
		int start = (int) fit, size = (int) (fit >>> 32);
		this.removePrimBySize(si);
		if (size > n) this.addPrim(start + n, size - n);
		this.used += n;
		return start;
	}

	private void primFree(int start, int n) {
		int before = this.lowerBound(this.byStartPrim, (long) start << 32) - 1;
		if (before >= 0) {
			long pk = this.byStartPrim[before];
			int bs = (int) (pk >>> 32), bsz = (int) pk;
			if (bs + bsz == start) {
				this.removePrimByStart(before, bsz);
				start = bs;
				n += bsz;
			}
		}
		int after = this.lowerBoundStart(start + n);
		if (after < this.ranges) {
			int sz = (int) this.byStartPrim[after];
			this.removePrimByStart(after, sz);
			n += sz;
		}
		this.addPrim(start, n);
	}
}
