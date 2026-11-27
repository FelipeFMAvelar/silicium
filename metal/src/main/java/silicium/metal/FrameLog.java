package silicium.metal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Measurement only (-Dsilicium.own.int.frameLog=true): one row per submitted frame, written to framelog.csv in the working directory
 * when the game exits. Per frame: when it ended (epoch ms), its wall time (submit to submit), the render thread's wait time per site
 * (WaitStats' sites: in-flight limit, fence, nextDrawable, pacer, drain), time in timed regions of the render thread (client tick,
 * frame extract, frame render, vanilla's section scheduling, translucent resort scheduling, our terrain's per-frame event apply),
 * the render thread's CPU time and allocation, GC collections and their time, our terrain's events applied (publish, resort,
 * release, clear), and the submit index (GpuTimes' key, for the GPU span with -Dsilicium.metal.gpuTimes). Render thread only; fixed
 * arrays, nothing allocated per frame.
 */
public final class FrameLog {
	public static final boolean ON = Boolean.getBoolean("silicium.own.int.frameLog");
	public static final int TICK = 0, EXTRACT = 1, RENDER = 2, COMPILE = 3, RESORT_SCHED = 4, OWN_BEGIN = 5, TEXTURES = 6;
	private static final String[] REGIONS = {"tickUs", "extractUs", "renderUs", "compileUs", "resortSchedUs", "ownBeginUs", "texTickUs"};
	public static final int PUBLISH = 0, RESORT = 1, RELEASE = 2, CLEAR = 3;
	private static final String[] EVENTS = {"publish", "resort", "release", "clear"};
	private static final String[] WAITS = {"inflightUs", "fenceUs", "drawableUs", "paceUs", "drainUs"};
	/** Backend operations recorded this frame: texture writes and their pixels, buffer writes and their KB, buffer copies and KB, texture copies, render passes. */
	public static final int TEX_WRITES = 0, TEX_PX = 1, BUF_WRITES = 2, BUF_KB = 3, BUF_COPIES = 4, COPY_KB = 5, TEX_COPIES = 6, PASSES = 7, CMD_BUFS = 8, PRE_BUFS = 9, BLIT_ENCS = 10, PIPELINES = 11;
	private static final String[] OPS = {"texWrites", "texPx", "bufWrites", "bufKb", "bufCopies", "copyKb", "texCopies", "passes", "cmdBufs", "preBufs", "blitEncs", "pipelines"};
	private static final int CAP = 1 << 19;
	private static final int ENC_CAP = 1 << 20;
	private static final boolean LAZY = AutoConfig.lazyProfilingArrays();

	/** Big per-frame arrays, MXBean lookups and the encoder/tick-pass maps: never created in a default or probe-only run. */
	private static final class Heavy {
		final long[] endMs = new long[CAP];
		final int[] wallUs = new int[CAP], cpuUs = new int[CAP], allocKb = new int[CAP];
		final int[] gcCount = new int[CAP], gcMs = new int[CAP], submit = new int[CAP];
		final int[][] waitUs = new int[5][CAP], regionUs = new int[7][CAP], events = new int[4][CAP];
		final int[][] ops = new int[12][CAP];
		final java.util.Map<String, long[]> tickPasses = new java.util.TreeMap<>();
		final java.lang.management.ThreadMXBean THREADS = EAGER_THREADS != null ? EAGER_THREADS : ManagementFactory.getThreadMXBean();
		final List<GarbageCollectorMXBean> GCS = EAGER_GCS != null ? EAGER_GCS : ManagementFactory.getGarbageCollectorMXBeans();
		final int[] encSubmit = new int[ENC_CAP];
		final short[] encGroup = new short[ENC_CAP], encLabel = new short[ENC_CAP];
		final double[] encOrigin = new double[ENC_CAP];
		final float[][] encT = new float[4][ENC_CAP];
		final java.util.Map<String, Integer> LABELS = new java.util.HashMap<>();
		final List<String> LABEL_LIST = new java.util.ArrayList<>();
	}

	/** Fallback only: keep the old eager MXBean lookups when the lazy-holder gate is off. */
	private static final java.lang.management.ThreadMXBean EAGER_THREADS = LAZY ? null : ManagementFactory.getThreadMXBean();
	private static final List<GarbageCollectorMXBean> EAGER_GCS = LAZY ? null : ManagementFactory.getGarbageCollectorMXBeans();

	private static volatile Heavy heavy;
	static {
		if (ON) heavy = new Heavy();
		if (ON) {
			Runtime.getRuntime().addShutdownHook(new Thread(FrameLog::write, "silicium-framelog"));
			System.out.println("silicium-framelog: on (framelog.csv at exit)");
		}
	}

	private static Heavy heavy() {
		Heavy h = heavy;
		if (h == null) {
			synchronized (FrameLog.class) {
				h = heavy;
				if (h == null) heavy = h = new Heavy();
			}
		}
		return h;
	}

	/** Small scratch state: a few dozen bytes even in default runs, and the timed-region hooks call these unconditionally. */
	private static final long[] curOps = new long[12];
	private static final long[] curWait = new long[5], curRegion = new long[7], regionStart = new long[7];
	private static final int[] curEvents = new int[4];
	private static int n;
	private static long lastNs, lastCpu = -1, lastAlloc = -1, lastGcCount = -1, lastGcMs;
	private static Thread renderThread;
	private static long encRows;

	private FrameLog() {
	}

	static void waited(int site, long ns) {
		curWait[site] += ns;
	}

	/** Start of a timed region on the render thread (nesting of the same region is not supported: the outer one counts). */
	public static void begin(int region) {
		if (regionStart[region] == 0) regionStart[region] = System.nanoTime();
	}

	public static void end(int region) {
		long s = regionStart[region];
		if (s == 0) return;
		curRegion[region] += System.nanoTime() - s;
		regionStart[region] = 0;
	}

	public static void event(int kind) {
		curEvents[kind]++;
	}

	static void op(int kind, long amount) {
		curOps[kind] += amount;
	}

	/** Inside the client tick or the texture manager's tick (their timers running). */
	static boolean inTick() {
		return regionStart[TICK] != 0 || regionStart[TEXTURES] != 0;
	}

	/** A render pass made during the client tick: counted by what it draws into (pass label, target, mip, size, render area). */
	static void tickPass(String key, long pixels) {
		long[] v = heavy().tickPasses.computeIfAbsent(key, k -> new long[2]);
		v[0]++;
		v[1] += pixels;
	}

	/** End of a submit (from WaitStats.frame). */
	static void frame(long submitIndex) {
		if (!ON) return; // probe-only runs read ON from MetalEncoder: no MXBean lookups, no big arrays
		Heavy h = heavy();
		long now = System.nanoTime();
		if (renderThread == null) renderThread = Thread.currentThread();
		long cpu = h.THREADS.getCurrentThreadCpuTime();
		long alloc = h.THREADS instanceof com.sun.management.ThreadMXBean t ? t.getCurrentThreadAllocatedBytes() : 0;
		long gcc = 0, gct = 0;
		for (int i = 0, k = h.GCS.size(); i < k; i++) {
			GarbageCollectorMXBean g = h.GCS.get(i);
			gcc += Math.max(0, g.getCollectionCount());
			gct += Math.max(0, g.getCollectionTime());
		}
		if (lastNs != 0 && n < CAP) {
			int i = n++;
			h.endMs[i] = System.currentTimeMillis();
			h.wallUs[i] = (int) Math.min(Integer.MAX_VALUE, (now - lastNs) / 1000);
			h.cpuUs[i] = lastCpu < 0 ? 0 : (int) ((cpu - lastCpu) / 1000);
			h.allocKb[i] = lastAlloc < 0 ? 0 : (int) ((alloc - lastAlloc) >> 10);
			h.gcCount[i] = lastGcCount < 0 ? 0 : (int) (gcc - lastGcCount);
			h.gcMs[i] = lastGcCount < 0 ? 0 : (int) (gct - lastGcMs);
			h.submit[i] = (int) submitIndex;
			for (int s = 0; s < 5; s++) h.waitUs[s][i] = (int) (curWait[s] / 1000);
			for (int r = 0; r < 7; r++) h.regionUs[r][i] = (int) (curRegion[r] / 1000);
			for (int e = 0; e < 4; e++) h.events[e][i] = curEvents[e];
			try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
				long c = stack.ncalloc(4, 3, 4);
				Native.encCounters(c);
				for (int k = 0; k < 3; k++) curOps[CMD_BUFS + k] += org.lwjgl.system.MemoryUtil.memGetInt(c + k * 4L);
			}
			for (int o = 0; o < 12; o++) h.ops[o][i] = (int) Math.min(Integer.MAX_VALUE, curOps[o]);
		}
		java.util.Arrays.fill(curWait, 0);
		java.util.Arrays.fill(curRegion, 0);
		java.util.Arrays.fill(curEvents, 0);
		java.util.Arrays.fill(curOps, 0);
		lastNs = now;
		lastCpu = cpu;
		lastAlloc = alloc;
		lastGcCount = gcc;
		lastGcMs = gct;
	}

	// the encoder log (-Dsilicium.own.int.encLog): a ring of the last ENC_CAP encoders; per submit its origin (first sample, us since calibration)
	/** One profiled submit's encoders: 4 samples each (vertex start/end, fragment start/end; compute: start/end in the first two). */
	static void encoders(long ctx, long submit, long samples, int count, String[] labels) {
		if (!ON) return;
		Heavy h = heavy();
		try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
			long out = stack.nmalloc(8, Math.max(1, count) * 8);
			Native.profileReadAbs(ctx, samples, count, out);
			double origin = Double.MAX_VALUE;
			for (int i = 0; i < count; i++) {
				double v = org.lwjgl.system.MemoryUtil.memGetDouble(out + i * 8L);
				if (v >= 0) origin = Math.min(origin, v);
			}
			if (origin == Double.MAX_VALUE) return;
			for (int g = 0; g < count / 4; g++) {
				int row = (int) (encRows++ % ENC_CAP);
				h.encSubmit[row] = (int) submit;
				h.encGroup[row] = (short) g;
				String label = g < labels.length && labels[g] != null ? labels[g] : "compute";
				h.encLabel[row] = (short) (int) h.LABELS.computeIfAbsent(label, k -> {
					h.LABEL_LIST.add(k);
					return h.LABEL_LIST.size() - 1;
				});
				h.encOrigin[row] = origin;
				for (int k = 0; k < 4; k++) {
					double v = org.lwjgl.system.MemoryUtil.memGetDouble(out + (g * 4L + k) * 8);
					h.encT[k][row] = v < 0 ? -1 : (float) (v - origin);
				}
			}
		}
	}

	private static void writeEncoders(Heavy h) {
		if (encRows == 0) return;
		Path out = Path.of(System.getProperty("user.dir"), "enclog.csv");
		try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.US_ASCII)) {
			w.write("submit,group,label,originUs,vsUs,veUs,fsUs,feUs\n");
			long first = Math.max(0, encRows - ENC_CAP);
			StringBuilder b = new StringBuilder(160);
			for (long r = first; r < encRows; r++) {
				int i = (int) (r % ENC_CAP);
				b.setLength(0);
				b.append(h.encSubmit[i]).append(',').append(h.encGroup[i]).append(',').append(h.LABEL_LIST.get(h.encLabel[i]).replace(',', ';')).append(',')
					.append(String.format("%.1f", h.encOrigin[i]));
				for (int k = 0; k < 4; k++) b.append(',').append(String.format("%.1f", h.encT[k][i]));
				w.write(b.append('\n').toString());
			}
		} catch (IOException e) {
			// measurement only
		}
	}

	private static void write() {
		Heavy h = heavy();
		writeEncoders(h);
		Path out = Path.of(System.getProperty("user.dir"), "framelog.csv");
		try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.US_ASCII)) {
			StringBuilder header = new StringBuilder("endMs,submit,wallUs,cpuUs,allocKb,gcCount,gcMs");
			for (String s : WAITS) header.append(',').append(s);
			for (String s : REGIONS) header.append(',').append(s);
			for (String s : EVENTS) header.append(',').append(s);
			for (String s : OPS) header.append(',').append(s);
			w.write(header.append('\n').toString());
			StringBuilder b = new StringBuilder(256);
			for (int i = 0; i < n; i++) {
				b.setLength(0);
				b.append(h.endMs[i]).append(',').append(h.submit[i]).append(',').append(h.wallUs[i]).append(',').append(h.cpuUs[i]).append(',').append(h.allocKb[i])
					.append(',').append(h.gcCount[i]).append(',').append(h.gcMs[i]);
				for (int s = 0; s < 5; s++) b.append(',').append(h.waitUs[s][i]);
				for (int r = 0; r < 7; r++) b.append(',').append(h.regionUs[r][i]);
				for (int e = 0; e < 4; e++) b.append(',').append(h.events[e][i]);
				for (int o = 0; o < 12; o++) b.append(',').append(h.ops[o][i]);
				w.write(b.append('\n').toString());
			}
		} catch (IOException e) {
			System.out.println("silicium-framelog: can't write " + out + ": " + e);
		}
		if (!h.tickPasses.isEmpty()) {
			// (to a file: the game's stdout is gone by the time shutdown hooks run)
			StringBuilder b = new StringBuilder("render passes made in the client tick (count, target pixels each, key):\n");
			h.tickPasses.forEach((k, v) -> b.append(v[0]).append("x ").append(v[1] / Math.max(1, v[0])).append(" px  ").append(k).append('\n'));
			try {
				Files.writeString(Path.of(System.getProperty("user.dir"), "framelog-passes.txt"), b, StandardCharsets.UTF_8);
			} catch (IOException e) {
				// measurement only
			}
		}
	}
}
