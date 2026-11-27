// Shared native backend types and helpers (mcmetal.m, mcown.m, mclod.m, mccap.m, rec.m).
#import <Metal/Metal.h>

#ifdef __OBJC__
#include <stdio.h>
#include <string.h>
// FNV-1a 64 of "silicium-metallib-v1|" + the exact MSL source bytes (the same key build.gradle and MetalPipeline.java use).
static inline uint64_t mc_source_hash(NSString *source) {
	static const unsigned char salt[] = "silicium-metallib-v1|";
	const unsigned char *s = (const unsigned char *) [source UTF8String];
	size_t n = s ? strlen((const char *) s) : 0;
	uint64_t h = 1469598103934665603ULL;
	for (size_t i = 0; i < sizeof(salt) - 1; i++) h = (h ^ salt[i]) * 1099511628211ULL;
	for (size_t i = 0; i < n; i++) h = (h ^ s[i]) * 1099511628211ULL;
	return h;
}

// Precompiled .metallib paths registered from Java (silicium AutoConfig.precompiledShaders). One shared registry:
// defined in mcmetal.m, because mc_compile_library is called from several translation units of this dylib.
extern NSMutableDictionary<NSNumber *, NSString *> *mcPrecompiledPaths;

// Register a precompiled library path under a source hash (defined in mcmetal.m; Java gates this on AutoConfig).
void mc_precompiled_add(uint64_t hash, const char *path);

// Compile `source` with the newest MSL this SDK/OS supports: on macOS 26+ try MSL 4.0 first and use it when compilation
// succeeds; otherwise (older OS, or a compile failure) fall back to MSL 3.1. `mathMode` is kept from the caller so the
// backend's fast-math choice is unchanged. When a precompiled library has been registered for this exact source it is
// loaded first with newLibraryWithURL:; any failure (missing file, wrong MSL version, older OS) falls through to the
// original source path. Returns a library owned by the caller (or nil with *error set).
// MTLLanguageVersion4_0 / MTLGPUFamilyMetal4 are NS_ENUM constants, so `defined()` alone can't see them; the SDK-version
// check keeps this building against SDKs that predate Metal 4 while @available() guards the runtime call.
static inline id<MTLLibrary> mc_compile_library(id<MTLDevice> device, NSString *source, MTLMathMode mathMode, const char *what,
	NSError **error) {
	if (mcPrecompiledPaths) {
		NSString *path = [mcPrecompiledPaths objectForKey:@(mc_source_hash(source))];
		if (path) {
			NSError *pe = nil;
			id<MTLLibrary> pre = [device newLibraryWithURL:[NSURL fileURLWithPath:path] error:&pe];
			if (pre) {
				static int said;
				if (!said) { said = 1; printf("[silicium] precompiled shaders: loaded .metallib from resources\n"); fflush(stdout); }
				return pre;
			}
			static int warned;
			if (!warned) {
				warned = 1;
				fprintf(stderr, "[silicium] precompiled shader load failed (%s), compiling from source\n",
					pe ? pe.description.UTF8String : "unknown error");
			}
		}
	}
	MTLCompileOptions *o = [[MTLCompileOptions new] autorelease];
	o.mathMode = mathMode;
#if defined(MTLLanguageVersion4_0) || __MAC_OS_X_VERSION_MAX_ALLOWED >= 260000
	if (@available(macOS 26.0, *)) {
		static int used4, fellBack;
		o.languageVersion = MTLLanguageVersion4_0;
		NSError *e4 = nil;
		id<MTLLibrary> lib4 = [device newLibraryWithSource:source options:o error:&e4];
		if (lib4) {
			if (!used4) {
				used4 = 1;
				printf("[silicium] %s: compiled with MSL 4.0\n", what);
				fflush(stdout);
			}
			return lib4;
		}
		if (!fellBack) {
			fellBack = 1;
			printf("[silicium] %s: MSL 4.0 compilation failed (%s), falling back to MSL 3.1\n", what,
				e4 ? e4.description.UTF8String : "unknown error");
			fflush(stdout);
		}
	}
#endif
	o.languageVersion = MTLLanguageVersion3_1;
	return [device newLibraryWithSource:source options:o error:error];
}
#endif  // __OBJC__

typedef struct {
	id<MTLDevice> device;
	id<MTLCommandQueue> queue;
	id<MTLLibrary> builtins;
	id<MTLRenderPipelineState> present;  // flips the frame into the drawable (same row order as GL/Vulkan)
	id<MTLSamplerState> presentSampler;
	NSMutableDictionary<NSNumber *, id<MTLRenderPipelineState>> *clearPipelines;  // keyed by attachment formats
	id<MTLDepthStencilState> depthWrite, depthKeep;
	id<MTLBuffer> fanIndices;  // {0, k+1, k+2} per triangle: Metal has no fans, so they're drawn as indexed triangle lists
} Ctx;

#define MAX_COLORS 8

typedef struct {
	Ctx *ctx;
	id<MTLCommandBuffer> cmd;
	id<MTLBlitCommandEncoder> blit;
	// Stays open after mc_render_end: if the next pass draws to the same attachments without clearing, it continues in this
	// encoder, which on a tile GPU saves a full store + load of every attachment. Anything else encoded ends it first.
	id<MTLRenderCommandEncoder> render;
	id<MTLTexture> colors[MAX_COLORS];  // attachments of `render`, compared by identity only
	id<MTLTexture> depth;
	int colorCount;
	int width, height;  // of `render`'s viewport
	// Attachments whose contents died while `render` was open (the game cleared them): bit i = color i, bit 8 = depth.
	// Their store actions are decided at endEncoding, so dead ones are never written back to memory.
	unsigned dead;
	id<MTLBuffer> indexBuffer;
	MTLIndexType indexType;
	int indexSize;
	MTLPrimitiveType primitive;
	id<MTLCounterSampleBuffer> samples;  // set only while profiling: 4 timestamps per render encoder (vertex start/end, fragment start/end)
	int sampleCount;
	id sampleEnc[512];  // the encoder log (-Dsilicium.own.int.encLog): each profiled group's encoder (retained until mc_profile_labels)
	char sampleKind[512];
	char sampleInfo[512][48];  // render groups: target size, load and store actions
	int renderGroup;
	// Terrain culling only: a command buffer committed right before the frame's `cmd` (neither is enqueued earlier, so the
	// queue runs pre first), so work recorded into it mid-frame (geometry uploads, culling) still runs on the GPU before
	// anything in `cmd`. Opened by the frame's first use (mc_pre), so a frame without any has no second command buffer.
	// Nothing else orders them, on purpose: every buffer of pre's that `cmd` uses is bound or declared with useResource, so
	// hazard tracking orders those accesses, and a queue reports completions in order, so cmd's completion (what fences and
	// frame pacing wait on) implies pre's. An event between them would also wait for all older work on the queue, i.e. the
	// previous frame, and frames would stop overlapping.
	id<MTLCommandBuffer> pre;
	id<MTLBlitCommandEncoder> preBlit;
	id<MTLComputeCommandEncoder> preCompute;
	unsigned renderSerial;  // bumped for every render encoder opened (the native shading pipeline tells encoders apart by it)
	// opt-in (-Dsilicium.cpu.pass): mc_render_begin's pass descriptor, kept and reset instead of made per pass; the color
	// attachments it may still name (cpuPassColors), and whether profiling put a sample buffer on it.
	MTLRenderPassDescriptor *cpuPassDesc;
	int cpuPassColors, cpuPassSampled;
	// mc_render_resume's pass descriptor, kept and reset instead of made on every split (render thread only).
	MTLRenderPassDescriptor *resumePass;
	// opt-in (-Dsilicium.cpu.cmdAhead): the next frame's command buffer, made on a background queue right after a commit;
	// cmd() takes it (waiting for it if it isn't made yet) instead of making one on the render thread.
	id<MTLCommandBuffer> cpuNext;
	dispatch_semaphore_t cpuNextReady;
	int cpuNextPending;
	// (mc_tl_restart) the open render encoder's depth was cleared at open (to tlDepthClearValue), and
	// whether anything in it wrote depth since (a pipeline whose depth state writes, an in-place depth clear)
	int tlDepthCleared, tlDepthWritten;
	float tlDepthClearValue;
} Enc;

id<MTLCommandBuffer> mc_pre(Enc *enc);
id<MTLComputeCommandEncoder> mc_pre_compute(Enc *enc);
// A compute encoder on command buffer c; while a submit is profiled (-Dsilicium.metal.trace) its start and end are sampled into the
// "vertex" slots of a group of 4, so it shows in the per-encoder GPU profile as an encoder with no fragment stage.
id<MTLComputeCommandEncoder> mc_profiled_compute(Enc *enc, id<MTLCommandBuffer> c, MTLDispatchType type);
id<MTLBlitCommandEncoder> mc_profiled_blit(Enc *enc, id<MTLCommandBuffer> c);
// The profile group of the last compute encoder mc_profiled_compute made (-1: not profiled).
int mc_last_compute_group(void);
void mc_pre_end_encoders(Enc *enc);
id<MTLComputeCommandEncoder> mc_render_suspend(Enc *enc);
id<MTLBlitCommandEncoder> mc_blit(Enc *enc);  // the frame's blit encoder, ending any open render encoder (shaderpack runtime)
void mc_set_rp_log(int on);
id<MTLCommandBuffer> mc_frame_cmd(Enc *enc);  // the frame's command buffer with no encoder open (native shading pipeline)
void mc_render_resume(Enc *enc, id<MTLComputeCommandEncoder> compute);
// 1 when MSL 4 is usable here (macOS 26+, and the device reports the Metal 4 family when a context is given), else 0.
// NULL context returns OS availability only.
int mc_supports_metal4(Ctx *ctx);
// Debug groups in mco_draw: on by default (the current behavior); off when called with 0.
void mc_set_debug_groups(int on);
