package silicium.metal.mixin.chunk;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * D2/D5 (-Dsilicium.chunk.chunkGet=true): every block or fluid read and write of a chunk (LevelChunk/ProtoChunk getBlockState,
 * getFluidState, setBlockState...) maps the block Y to a section index through getSectionIndex -> getMinSectionY -> getMinY ->
 * the height accessor (a Level: dimensionType() -> Holder.value() -> minY). That chain is constant for a chunk (only ChunkAccess
 * defines getMinY, from its final levelHeightAccessor), so it is evaluated once when the chunk is constructed; getMinY itself (and
 * the getMinSectionY / getSectionIndexFromSectionY defaults derived from it) then returns the cached min section shifted by 4.
 * Calls made during construction take the original chain.
 */
@Mixin(ChunkAccess.class)
abstract class ChunkAccessSectionMixin implements LevelHeightAccessor {
	@Shadow @Final protected LevelHeightAccessor levelHeightAccessor;
	@Unique private int silicium$minSectionY;
	@Unique private boolean silicium$minSet;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void silicium$cacheMinSection(CallbackInfo ci) {
		this.silicium$minSectionY = SectionPos.blockToSectionCoord(this.levelHeightAccessor.getMinY());
		this.silicium$minSet = true;
	}

	@Override
	public int getSectionIndex(int blockY) {
		return SectionPos.blockToSectionCoord(blockY) - (this.silicium$minSet ? this.silicium$minSectionY : this.getMinSectionY());
	}

	@Override
	public int getMinY() {
		return this.silicium$minSet ? this.silicium$minSectionY << 4 : this.levelHeightAccessor.getMinY();
	}
}
