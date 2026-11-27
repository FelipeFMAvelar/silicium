package silicium.metal.mixin.ownmesh;

import silicium.metal.own.OwnQuadHolder;
import silicium.metal.own.OwnQuads;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** -Dsilicium.own.mesh=true: a compile's own quads, from our mesher to the compiled mesh. */
@Mixin(SectionCompiler.Results.class)
abstract class OwnMeshResultsMixin implements OwnQuadHolder {
	@Unique
	private @Nullable OwnQuads silicium$quads;

	@Override
	public @Nullable OwnQuads silicium$quads() {
		return this.silicium$quads;
	}

	@Override
	public void silicium$setQuads(@Nullable OwnQuads quads) {
		this.silicium$quads = quads;
	}
}
