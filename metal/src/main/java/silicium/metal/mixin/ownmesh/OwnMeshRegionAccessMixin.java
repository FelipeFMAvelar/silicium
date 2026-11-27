package silicium.metal.mixin.ownmesh;

import silicium.metal.own.OwnRegionAccess;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** -Dsilicium.own.mesh.prefill: the region's section copies, for OwnRegion's unpack. */
@Mixin(RenderSectionRegion.class)
abstract class OwnMeshRegionAccessMixin implements OwnRegionAccess {
	@Shadow
	@Final
	private SectionCopy[] sections;

	@org.spongepowered.asm.mixin.Unique
	private silicium.metal.own.@org.jspecify.annotations.Nullable OwnRegion silicium$own;

	@Override
	public silicium.metal.own.@org.jspecify.annotations.Nullable OwnRegion silicium$own() {
		return this.silicium$own;
	}

	@Override
	public void silicium$own(silicium.metal.own.@org.jspecify.annotations.Nullable OwnRegion own) {
		this.silicium$own = own;
	}

	@Override
	public SectionCopy[] silicium$sections() {
		return this.sections;
	}
}
