package silicium.metal.own;

import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jspecify.annotations.Nullable;

/** -Dsilicium.own.mesh.prefill: accessors mixed into RenderSectionRegion (and Copy into SectionCopy). */
public interface OwnRegionAccess {
	SectionCopy[] silicium$sections();

	/** -Dsilicium.own.mesh.frapiRegion: the compile cache serving this region while our compile runs on it, else null. */
	@Nullable OwnRegion silicium$own();

	void silicium$own(@Nullable OwnRegion own);

	interface Copy {
		@Nullable PalettedContainer<BlockState> silicium$section();

		boolean silicium$debug();
	}
}
