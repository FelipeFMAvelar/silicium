// silicium item 1: GPU-encoded indirect command buffer for the plain classed solid-terrain draws.
//
// This kernel encodes exactly one draw command into one ICB slot. The ICB is created with inheritPipelineState=YES and
// inheritBuffers=YES, so the vertex/fragment pipeline and every buffer/texture binding are the ones the existing
// mco_draw path sets on the parent render encoder immediately before executeCommandsInBuffer:. Only fixed-function
// state is set on the GPU (depth, cull, winding, fill, bias), which is what makes this a GPU-driven draw; the geometry,
// index buffer, instance count and draw order are the same as the CPU indirect draw it replaces.
//
// Requires MSL 4 / macOS 26+ (metal::command_buffer, metal::render_command). Creation is feature-detected in mco_new;
// any failure leaves w->icbReady=0 and mco_draw takes the proven CPU path.

#include <metal_stdlib>
using namespace metal;

struct ICBBind {
	command_buffer icb [[id(0)]];
	depth_stencil_state depth [[id(1)]];
};

// The first 20 bytes of the cull's per-list arguments (MTLDrawIndexedPrimitivesIndirectArguments).
struct DrawArgs {
	uint indexCount;
	uint instanceCount;
	uint firstIndex;
	int baseVertex;
	uint baseInstance;
};

kernel void own_icb_encode(uint tid [[thread_position_in_grid]],
	device ICBBind *bind [[buffer(0)]],
	constant DrawArgs *args [[buffer(1)]],
	constant uint2 *spec [[buffer(2)]],       // x: ICB slot, y: depth bias units
	device ushort *indices [[buffer(3)]]) {
	(void) tid;
	render_command cmd(bind->icb, spec->x);
	cmd.reset();
	// Device state set on the GPU (the ICB descriptor has the matching inherit* flags set to NO).
	cmd.set_depth_stencil_state(bind->depth);
	cmd.set_cull_mode(cull_mode::back);
	cmd.set_front_facing_winding(winding::clockwise);
	cmd.set_triangle_fill_mode(triangle_fill_mode::fill);
	cmd.set_depth_bias(spec->y != 0 ? 1.0f : 0.0f, 0.0f, 0.0f);
	// Same geometry as [r drawIndexedPrimitives:... indirectBuffer:args offset:...]: firstIndex folds into the pointer.
	cmd.draw_indexed_primitives(primitive_type::triangle, args->indexCount, indices + args->firstIndex,
		args->instanceCount, args->baseVertex, args->baseInstance);
}
