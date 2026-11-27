package silicium.metal;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.api.device.DeviceFeatures;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.DeviceLimits;
import com.mojang.renderpearl.api.device.DeviceType;
import com.mojang.renderpearl.api.device.HintsAndWorkarounds;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.backend.api.GpuSurfaceBackend;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

final class MetalDevice implements GpuDeviceBackend {
	/**
	 * Sodium grows its terrain geometry in arenas of up to 256 MiB, created the moment a region no longer fits (flight 5.34 s:
	 * 179 MB). A fresh buffer's memory is found and wired in the submission of the first command buffer using it, the whole
	 * buffer at once, and when free memory is short the kernel compresses other memory first: 36-45 ms of one frame on the mini
	 * with a 4 GiB heap. So one arena of this size is kept provisioned
	 * ahead of need, off the render thread (Native.poolAdd), and handed to the next arena request it can hold, with its full
	 * size: Sodium sizes the arena from buffer.size(). Costs its size in memory while it waits; ARENA_RESERVE_DEPTH of them are
	 * kept ahead of need (the native pool also caps equal-size buffers at two). 0 turns it off.
	 */
	private static final long ARENA_RESERVE = Long.getLong("silicium.metal.arenaReserve", AutoConfig.arenaReserveBytes());
	/** How many provisioned arenas may wait in the pool; explicit -Dsilicium.metal.arenaReserveDepth=N still wins. */
	private static final int ARENA_RESERVE_DEPTH = Integer.getInteger("silicium.metal.arenaReserveDepth", AutoConfig.arenaReserveDepth());
	/** Wiring costs about 0.03 ms per MiB with memory free and 0.2-0.25 ms per MiB when short: only big arenas stall a frame. */
	private static final long ARENA_RESERVE_MIN = 64L << 20;
	/** Provisioned arenas currently waiting in the pool (0..ARENA_RESERVE_DEPTH). */
	private int arenaReserved;
	private final long ctx;
	private final MetalEncoder encoder;
	private final DeviceInfo info;

	MetalDevice() throws BackendCreationException {
		// Forward AutoConfig's hardware decisions and register precompiled libraries before any native shader is
		// compiled. Both hooks fail closed when the dylib or the resources are missing.
		MetalPipeline.ensureSiliciumHooks();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			long name = stack.nmalloc(1, 256), err = stack.nmalloc(1, 1024);
			this.ctx = Native.create(name, 256, err, 1024);
			if (this.ctx == 0) throw new BackendCreationException("Metal unavailable: " + MemoryUtil.memUTF8(err), BackendCreationException.Reason.PLATFORM_ERROR);
			this.info = new DeviceInfo(
				MemoryUtil.memUTF8(name), "Apple", (Native.supportsMetal4(this.ctx) ? "Metal 4 (silicium)" : "Metal 3 (silicium)"), true, "Metal", 1.0F,
				new DeviceLimits(16, 256, 16384, Native.maxBufferLength(this.ctx), Integer.MAX_VALUE, 8, Integer.MAX_VALUE),
				// Every multi-draw form is a native loop of Metal draws, so all of them are "supported".
				new DeviceFeatures(true, true, true, true, true, true, true, true),
				Set.of(),
				new HintsAndWorkarounds(false, false, true, false),
				DeviceType.INTEGRATED);
		}
		this.encoder = new MetalEncoder(this.ctx);
		// Debug group labels are for GPU captures; they are off in the steady frame.
		Native.setDebugGroups(!"false".equals(System.getProperty("silicium.own.debugGroups")));
		// -Dsilicium.metal.cbdiag=MS: stall diagnostics on stderr (big buffer allocations, slow command buffers); see mcmetal.m.
		String diag = System.getProperty("silicium.metal.cbdiag");
		if (diag != null) Native.diagEnable(Double.parseDouble(diag));
		// -Dsilicium.metal.gpuCount[=PATH]: measurement only, per render encoder and debug group: samples passing depth (visibility
		// counts), draws and primitives per pipeline, one JSON line each to PATH (true: gpucount.jsonl in the game dir); see mcmetal.m.
		// -Dsilicium.metal.gpuCount.every=N: only every Nth frame (the frames between run as without the flag).
		// -Dsilicium.metal.gpuCount.groups=false: one visibility slot per encoder (no new slot at debug groups).
		if (MetalPipeline.GPU_COUNT) Native.gpuCountEnable(MetalPipeline.GPU_COUNT_PATH, Integer.getInteger("silicium.metal.gpuCount.every", 1),
			!"false".equals(System.getProperty("silicium.metal.gpuCount.groups")));
		if (silicium.metal.cpu.Cpu.PASS || silicium.metal.cpu.Cpu.CMD_AHEAD) Native.cpuFlags(silicium.metal.cpu.Cpu.nativeFlags(silicium.metal.cpu.Cpu.PASS)); // opt-in: see mc_cpu_flags
	}

	/** Queues an ARENA_RESERVE arena for provisioning.
	 * Native mc_pool_add fails closed (0) when the pool is full or already holds two buffers of this size. */
	private boolean reserveArena(int delayMs) {
		return Native.poolAdd(this.ctx, ARENA_RESERVE, delayMs);
	}

	@Override
	public GpuSurfaceBackend createSurface(long windowHandle, BooleanSupplier isIconified) {
		return new MetalSurface(this.ctx, this.encoder, windowHandle);
	}

	@Override
	public CommandEncoderBackend createCommandEncoder() {
		return this.encoder;
	}

	@Override
	public GpuSampler createSampler(AddressMode u, AddressMode v, FilterMode min, FilterMode mag, int maxAnisotropy, OptionalDouble maxLod) {
		return MetalSampler.create(this.ctx, u, v, min, mag, maxAnisotropy, maxLod);
	}

	/**
	 * -Dsilicium.metal.residentAnim: sprite animation frame textures kept in a residency set the queue carries. Each frame is a small texture
	 * drawn into its atlas only on the ticks that need it; between those uses its pages go idle and the system compresses them, and the
	 * first pass of the next tick that samples them waits while they come back (on the base chips ~0.4-0.6 ms of GPU in the blocks atlas's
	 * mip-0 animation pass, the same draws ~10 us on the mips after it). Resident, they stay wired. Pure residency: nothing drawn changes.
	 */
	private static long residentBytes, residentCount;

	@Override
	public GpuTexture createTexture(@Nullable String label, @GpuTexture.Usage int usage, GpuFormat format, int width, int height, int layers, int mips) {
		// MTLTextureUsage: ShaderRead 1, RenderTarget 4. Copies need no usage bit in Metal.
		int mtlUsage = ((usage & GpuTexture.USAGE_TEXTURE_BINDING) != 0 ? 1 : 0) | ((usage & GpuTexture.USAGE_RENDER_ATTACHMENT) != 0 ? 4 : 0);
		if (silicium.metal.own.AnimCopy.creatingAnimatedAtlas) mtlUsage |= 2;  // ShaderWrite (-Dsilicium.own.int.atlasWrite)
		boolean cube = (usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0;
		long handle = Native.textureNew(this.ctx, MetalConst.pixelFormat(format), width, height, layers, mips, Math.max(1, mtlUsage), cube ? 1 : 0);
		if (handle == 0) throw new IllegalStateException("Couldn't create " + width + "x" + height + " " + format + " texture " + label);
		MetalTexture texture = new MetalTexture(this.encoder, handle, usage, label == null ? "" : label, format, width, height, layers, mips);
		texture.shaderWrite = (mtlUsage & 2) != 0;
		if (ResidentAnim.creating && Native.textureResident(this.ctx, handle, 1)) {
			texture.resident = true;
			long bytes = 0;
			for (int m = 0; m < mips; m++) bytes += (long) Math.max(1, width >> m) * Math.max(1, height >> m) * format.blockSize() * Math.max(1, layers);
			texture.residentBytes = bytes;
			residentBytes += bytes;
			if (++residentCount % 100 == 0) System.out.println("silicium-metal: " + residentCount + " animation frame textures resident, " + residentBytes / 1024 + " KB");
		}
		return texture;
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture texture, int baseMip, int mips) {
		return new MetalTexture.View(this.encoder, (MetalTexture) texture, baseMip, mips);
	}

	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> label, @GpuBuffer.Usage int usage, long size) {
		MetalEvents.Operation event = MetalEvents.begin("allocate", -1, size);
		// Sodium's ArenaAggregator names every geometry and index arena this way.
		boolean arena = label != null && "Arena buffer".equals(label.get());
		MetalBuffer buffer;
		try {
			long handle = 0, actual = size;
			if (arena && ARENA_RESERVE > 0 && size >= ARENA_RESERVE_MIN) {
				// At most twice what was asked for: Sodium fills an arena over time, a much bigger one would mostly sit empty.
				if (size <= ARENA_RESERVE && size * 2 >= ARENA_RESERVE) handle = Native.poolTake(size, ARENA_RESERVE);
				boolean took = handle != 0;
				if (took) {
					actual = Native.bufferLength(handle);
					if (this.arenaReserved > 0) this.arenaReserved--;
				}
				// Top up to depth, off the render thread; the next one a second later when this frame just took a ready arena.
				while (this.arenaReserved < ARENA_RESERVE_DEPTH) {
					if (!this.reserveArena(took ? 1000 : 0)) break;
					this.arenaReserved++;
				}
			}
			buffer = new MetalBuffer(this.encoder, handle != 0 ? handle : Native.bufferNew(this.ctx, size), usage, actual);
		} finally {
			MetalEvents.end(event);
		}
		return buffer;
	}

	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> label, @GpuBuffer.Usage int usage, ByteBuffer data) {
		// A brand-new buffer isn't referenced by any command yet, so the data can go straight into its memory.
		MetalBuffer buffer = new MetalBuffer(this.encoder, Native.bufferNew(this.ctx, data.remaining()), usage, data.remaining());
		MemoryUtil.memCopy(MemoryUtil.memAddress(data), buffer.address, data.remaining());
		return buffer;
	}

	@Override
	public List<String> getLastDebugMessages() {
		return List.of();
	}

	@Override
	public boolean isDebuggingEnabled() {
		return false;
	}

	@Override
	public BackendRenderPipeline.Pending compilePipeline(BackendRenderPipeline.CreateInfo info) {
		// Called on a worker thread by the frontend; Metal pipeline creation is thread-safe, so compile right here.
		MetalPipeline pipeline = MetalPipeline.compile(this.encoder, info);
		return () -> pipeline;
	}

	@Override
	public void close() {
		this.encoder.waitIdle();
	}

	@Override
	public GpuQueryPool createTimestampQueryPool(int size) {
		return new GpuQueryPool() {
			@Override
			public int size() {
				return size;
			}

			@Override
			public OptionalLong getValue(int index) {
				return OptionalLong.empty();
			}

			@Override
			public OptionalLong[] getValues(int index, int count) {
				OptionalLong[] values = new OptionalLong[count];
				java.util.Arrays.fill(values, OptionalLong.empty());
				return values;
			}

			@Override
			public void close() {
			}
		};
	}

	@Override
	public long getTimestampCalibrationOffset() {
		return 0;
	}

	@Override
	public DeviceInfo getDeviceInfo() {
		return this.info;
	}

	static void residentReleased(MetalTexture t) {
		residentBytes -= t.residentBytes;
		residentCount--;
	}

	/** The animation frame textures kept resident so far (count, bytes). */
	public static long[] residentAnimation() {
		return new long[] {residentCount, residentBytes};
	}
}
