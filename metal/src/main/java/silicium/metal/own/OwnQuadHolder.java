package silicium.metal.own;

import org.jspecify.annotations.Nullable;

/** -Dsilicium.own.mesh: mixed into SectionCompiler.Results and CompiledSectionMesh, carries a compile's OwnQuads to the upload hook. */
public interface OwnQuadHolder {
	@Nullable OwnQuads silicium$quads();

	void silicium$setQuads(@Nullable OwnQuads quads);
}
