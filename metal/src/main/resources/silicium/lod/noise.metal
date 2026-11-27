// silicium research item 2 (GPU LOD stack-volume noise) — DEFERRED, NO KERNEL SHIPPED.
//
// This file is deliberately empty of executable MSL. The reference mcn_stack_volume in lodnoise.c is bit-exact with
// Minecraft's Java `double` arithmetic: wrapd() uses `v - floor(v / 33554432.0 + 0.5) * 33554432.0`, layer_get() computes
// `X = wrapd(x) + ox` and `fyd = Y - floor(Y)` in double, and fudgey() divides in double. Metal Shading Language (all
// versions, including 4.0/4.1 on the macOS 27.2 SDK) does not support `double` at all:
//
//   $ xcrun metal -std=metal4.0 -c /tmp/dbl.metal -o /tmp/dbl.air
//   /tmp/dbl.metal:3:48: error: 'double' is not supported in Metal
//
// Emulating those operations with float/float2 would change the floor boundaries, the fraction bits and the gradient
// weights for a large fraction of coordinates, so a GPU kernel here cannot be proven bit-exact against the existing
// volume. The TODO explicitly requires deferring rather than shipping a wrong kernel, and AutoConfig.gpuLodNoise() is
// false, so the native dispatch in mclod.m and the Java binding in LodNative.java are scaffolding that always fail
// closed to mcn_stack_volume on the CPU. This resource is kept (and compiled to an empty library) so the build.gradle
// shader pipeline and the resource layout are already in place if fp64 ever becomes available.
//
// Required before enabling:
//  1. A Metal language/runtime with true IEEE-754 binary64 (or a formally verified double emulation with the exact
//     rounding of Java's StrictMath floor and division).
//  2. A whole-tile comparison (LodGenBench verify=100, LodNativeNoise.verify) over the same seed/volume space.
//  3. Flip AutoConfig.gpuLodNoise() to true only after (2) passes on the M5 in this workspace.
