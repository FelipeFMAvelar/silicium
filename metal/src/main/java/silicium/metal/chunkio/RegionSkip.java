package silicium.metal.chunkio;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.UTFDataFormatException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * C2: is a chunk write a no-op? Vanilla re-saves every chunk it loads (SerializableChunkData.read and the loading pyramid's LIGHT
 * step call setLightCorrect/setAllStarts/setAllReferences, which mark the chunk unsaved), so most writes during play put back the
 * bytes the region file already holds with a new LastUpdate stamp. The game never reads LastUpdate back (SerializableChunkData
 * parses it into a record field that only write() uses), nor the region header's per-chunk timestamps.
 *
 * <p>The check runs where the write would run (RegionFileStorage.write, on the storage's one IO thread, after every earlier
 * write of this chunk has reached the file): the new tag is serialized exactly as vanilla serializes it for the compressor, the
 * stored stream is decompressed with the region file's own reader, and the two byte strings are compared. They count as equal
 * only if they are byte-identical outside the 8-byte value of the root compound's LastUpdate long (whose offset is captured
 * while writing the fresh tag, and found structurally in the stored tag — never by searching bytes). Anything else (a missing
 * or unreadable stored chunk, a different length, a different key order, any parse doubt) means "write as vanilla does".
 * Equal bytes decode to equal NBT, so a skipped write cannot change what
 * any later load sees.
 */
public final class RegionSkip {
	private RegionSkip() { }

	public enum Result { IDENTICAL, LAST_UPDATE_ONLY, DIFFERENT, NO_STORED }

	/** Reusable buffers per IO thread (a storage's writes are serialized on one consecutive executor). */
	private static final ThreadLocal<Buffers> BUFFERS = ThreadLocal.withInitial(Buffers::new);

	public static final class Buffers {
		final FastDataOutput fresh = new FastDataOutput(1 << 17);
		/** Offset of the root LastUpdate value in {@link #fresh}, captured during the one serialization pass (-1 if absent). */
		int lastUpdateOffset = -1;
		byte[] stored = new byte[1 << 17];
		public int storedLength;
		final byte[] chunk = new byte[1 << 14];
	}

	/**
	 * Serialize {@code value} exactly as {@code NbtIo.write(value, out)} does for the region stream (no compression), and capture
	 * the byte offset of the root LastUpdate long's value during that single write.
	 *
	 * <p>Keeping vanilla's key order is essential: CompoundTag stores its keys in a HashMap and writes them in that iteration
	 * order, so the stored bytes are in that same order. Moving LastUpdate to the front of the fresh stream would reorder the
	 * two byte strings relative to each other and break the byte-for-byte proof; the offset is therefore captured in place
	 * instead, with no second walk of the finished bytes (the old {@code lastUpdateOffset(fresh)} scan).
	 *
	 * <p>The writer mirrors {@code NbtIo.writeUnnamedTagWithFallback}: root type, empty root name, each named entry in the
	 * tag's own key order, final End tag, with the same modified-UTF-8 fallback on an oversized name.
	 */
	public static Buffers serialize(CompoundTag value) throws IOException {
		Buffers b = BUFFERS.get();
		b.fresh.reset();
		b.lastUpdateOffset = -1;
		DataOutput out = new NbtFallbackOutput(b.fresh);
		out.writeByte(value.getId());
		out.writeUTF("");
		for (String key : value.keySet()) {
			Tag tag = value.get(key);
			int id = tag.getId();
			out.writeByte(id);
			if (id == 0) continue; // vanilla writeNamedTag writes no name/value for an End entry
			out.writeUTF(key);
			if (id == 4 && LAST_UPDATE_KEY.equals(key)) b.lastUpdateOffset = b.fresh.length();
			tag.write(out);
		}
		out.writeByte(0);
		return b;
	}

	/** The part of {@code NbtIo.StringFallbackDataOutput} that matters here: delegate everything, fall back to "" on a too-long UTF name. */
	private static final class NbtFallbackOutput implements DataOutput {
		private final DataOutput delegate;
		NbtFallbackOutput(DataOutput delegate) { this.delegate = delegate; }
		@Override public void write(int b) throws IOException { delegate.write(b); }
		@Override public void write(byte[] b) throws IOException { delegate.write(b); }
		@Override public void write(byte[] b, int off, int len) throws IOException { delegate.write(b, off, len); }
		@Override public void writeBoolean(boolean v) throws IOException { delegate.writeBoolean(v); }
		@Override public void writeByte(int v) throws IOException { delegate.writeByte(v); }
		@Override public void writeShort(int v) throws IOException { delegate.writeShort(v); }
		@Override public void writeChar(int v) throws IOException { delegate.writeChar(v); }
		@Override public void writeInt(int v) throws IOException { delegate.writeInt(v); }
		@Override public void writeLong(long v) throws IOException { delegate.writeLong(v); }
		@Override public void writeFloat(float v) throws IOException { delegate.writeFloat(v); }
		@Override public void writeDouble(double v) throws IOException { delegate.writeDouble(v); }
		@Override public void writeBytes(String s) throws IOException { delegate.writeBytes(s); }
		@Override public void writeChars(String s) throws IOException { delegate.writeChars(s); }
		@Override public void writeUTF(String s) throws IOException {
			try {
				delegate.writeUTF(s);
			} catch (UTFDataFormatException e) {
				delegate.writeUTF("");
			}
		}
	}

	/**
	 * Compare the decompressed stored stream with the serialized new tag while reading it, stopping at the first difference
	 * outside the LastUpdate value. The fresh bytes' root LastUpdate offset was captured during serialization; stored bytes
	 * before it are equal to the fresh ones whenever the scan gets there, so the same offset is the stored root's value too.
	 */
	public static Result compareStream(Buffers b, InputStream in) throws IOException {
		byte[] fresh = b.fresh.array();
		int lf = b.fresh.length(), pc = b.lastUpdateOffset, pos = 0;
		byte[] chunk = b.chunk;
		boolean lastUpdateDiffers = false;
		b.storedLength = 0;
		while (true) {
			int n = in.read(chunk, 0, chunk.length);
			if (n < 0) break;
			if (n == 0) continue;
			if (pos + n > lf) return Result.DIFFERENT;
			int m = Arrays.mismatch(chunk, 0, n, fresh, pos, pos + n);
			while (m >= 0) {
				int at = pos + m;
				if (pc < 0 || at < pc || at >= pc + 8) return Result.DIFFERENT;
				lastUpdateDiffers = true;
				int resume = Math.min(pc + 8 - pos, n);
				int m2 = Arrays.mismatch(chunk, resume, n, fresh, pos + resume, pos + n);
				m = m2 < 0 ? -1 : resume + m2;
			}
			pos += n;
			b.storedLength = pos;
		}
		if (pos != lf) return Result.DIFFERENT;
		return lastUpdateDiffers ? Result.LAST_UPDATE_ONLY : Result.IDENTICAL;
	}

	/** Read the whole decompressed stored stream into the thread's buffer. */
	public static void readStored(Buffers b, InputStream in) throws IOException {
		int n = 0;
		while (true) {
			if (n == b.stored.length) b.stored = Arrays.copyOf(b.stored, b.stored.length * 2);
			int r = in.read(b.stored, n, b.stored.length - n);
			if (r < 0) break;
			n += r;
		}
		b.storedLength = n;
	}

	public static Result compare(Buffers b) {
		byte[] a = b.stored, c = b.fresh.array();
		int la = b.storedLength, lc = b.fresh.length();
		int m = Arrays.mismatch(a, 0, la, c, 0, lc);
		if (m < 0) return Result.IDENTICAL;
		if (la != lc) return Result.DIFFERENT;
		int pa = lastUpdateOffset(a, la), pc = b.lastUpdateOffset;
		if (pa < 0 || pa != pc || m < pa || m >= pa + 8) return Result.DIFFERENT;
		return Arrays.equals(a, pa + 8, la, c, pc + 8, lc) ? Result.LAST_UPDATE_ONLY : Result.DIFFERENT;
	}

	private static final String LAST_UPDATE_KEY = "LastUpdate";
	private static final byte[] LAST_UPDATE = LAST_UPDATE_KEY.getBytes(java.nio.charset.StandardCharsets.US_ASCII);

	/** Offset of the 8 value bytes of the root compound's "LastUpdate" long, or -1 if absent or the bytes don't parse.
	 * Only used by the probe's comparison of the <em>stored</em> stream; the fresh offset is captured during serialization. */
	static int lastUpdateOffset(byte[] b, int len) {
		try {
			int p = 0;
			if (len < 3 || b[p++] != 10) return -1;
			p += 2 + u16(b, p, len);
			while (p < len) {
				int type = b[p++] & 0xFF;
				if (type == 0) return -1;
				int nameLength = u16(b, p, len);
				p += 2;
				boolean match = type == 4 && nameLength == LAST_UPDATE.length && p + nameLength <= len
					&& Arrays.equals(b, p, p + nameLength, LAST_UPDATE, 0, nameLength);
				p += nameLength;
				if (match) return p + 8 <= len ? p : -1;
				p = skip(b, p, len, type, 0);
				if (p < 0) return -1;
			}
			return -1;
		} catch (IndexOutOfBoundsException e) {
			return -1;
		}
	}

	private static int skip(byte[] b, int p, int len, int type, int depth) {
		if (depth > 512) return -1;
		long q;
		switch (type) {
			case 1 -> q = p + 1L;
			case 2 -> q = p + 2L;
			case 3, 5 -> q = p + 4L;
			case 4, 6 -> q = p + 8L;
			case 7 -> { int n = i32(b, p, len); q = n < 0 ? -1 : p + 4L + n; }
			case 8 -> q = p + 2L + u16(b, p, len);
			case 9 -> {
				int elementType = b[p] & 0xFF;
				int n = i32(b, p + 1, len);
				if (n < 0 || (elementType == 0 && n > 0)) return -1;
				p += 5;
				for (int i = 0; i < n; i++) {
					p = skip(b, p, len, elementType, depth + 1);
					if (p < 0) return -1;
				}
				q = p;
			}
			case 10 -> {
				while (true) {
					if (p >= len) return -1;
					int t = b[p++] & 0xFF;
					if (t == 0) break;
					p += 2 + u16(b, p, len);
					p = skip(b, p, len, t, depth + 1);
					if (p < 0) return -1;
				}
				q = p;
			}
			case 11 -> { int n = i32(b, p, len); q = n < 0 ? -1 : p + 4L + 4L * n; }
			case 12 -> { int n = i32(b, p, len); q = n < 0 ? -1 : p + 4L + 8L * n; }
			default -> q = -1;
		}
		return q < 0 || q > len ? -1 : (int) q;
	}

	private static int u16(byte[] b, int p, int len) {
		if (p + 2 > len) throw new IndexOutOfBoundsException();
		return (b[p] & 0xFF) << 8 | b[p + 1] & 0xFF;
	}

	private static int i32(byte[] b, int p, int len) {
		if (p + 4 > len) throw new IndexOutOfBoundsException();
		return b[p] << 24 | (b[p + 1] & 0xFF) << 16 | (b[p + 2] & 0xFF) << 8 | b[p + 3] & 0xFF;
	}

	/** Probe only: which root keys (and section sub-keys) differ between the stored and the new tag. */
	public static Set<String> diffKeys(Buffers b, CompoundTag fresh) {
		Set<String> out = new TreeSet<>();
		try {
			CompoundTag stored = NbtIo.read(new DataInputStream(new ByteArrayInputStream(b.stored, 0, b.storedLength)));
			Set<String> keys = new TreeSet<>(stored.keySet());
			keys.addAll(fresh.keySet());
			for (String k : keys) {
				Tag x = stored.get(k), y = fresh.get(k);
				if (java.util.Objects.equals(x, y)) continue;
				if (k.equals("sections") && x instanceof ListTag lx && y instanceof ListTag ly && lx.size() == ly.size()) {
					for (int i = 0; i < lx.size(); i++) {
						if (lx.get(i) instanceof CompoundTag sx && ly.get(i) instanceof CompoundTag sy) {
							Set<String> sk = new TreeSet<>(sx.keySet());
							sk.addAll(sy.keySet());
							for (String s : sk) if (!java.util.Objects.equals(sx.get(s), sy.get(s))) out.add("sections." + s);
						} else {
							out.add("sections.?");
						}
					}
				} else {
					out.add(k);
				}
			}
			if (out.isEmpty()) out.add("(order only)");
		} catch (IOException | RuntimeException e) {
			out.add("(unparsable)");
		}
		return out;
	}
}
