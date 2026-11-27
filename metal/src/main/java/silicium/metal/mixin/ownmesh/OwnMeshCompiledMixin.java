package silicium.metal.mixin.ownmesh;

import silicium.metal.own.OwnQuadHolder;
import silicium.metal.own.OwnQuads;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * -Dsilicium.own.mesh=true: the compiled mesh holds its compile's own quads until the upload hook (OwnRenderSectionMixin) has put
 * each own layer into the arena, on the same worker, in the same task.
 */
@Mixin(CompiledSectionMesh.class)
abstract class OwnMeshCompiledMixin implements OwnQuadHolder {
	@Unique
	private @Nullable OwnQuads silicium$quads;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void silicium$ownQuads(TranslucencyPointOfView translucencyPointOfView, SectionCompiler.Results results, long compileTaskStartTimeNs, CallbackInfo ci) {
		this.silicium$quads = ((OwnQuadHolder) (Object) results).silicium$quads();
	}

	@Override
	public @Nullable OwnQuads silicium$quads() {
		return this.silicium$quads;
	}

	@Override
	public void silicium$setQuads(@Nullable OwnQuads quads) {
		this.silicium$quads = quads;
	}
}
