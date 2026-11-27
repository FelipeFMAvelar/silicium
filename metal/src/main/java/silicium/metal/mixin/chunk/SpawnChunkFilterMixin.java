package silicium.metal.mixin.chunk;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * D3 (-Dsilicium.chunk.ticks=true): collectSpawningChunks asks the public anyPlayerCloseEnoughForSpawning (the cached
 * DistanceManager.hasPlayersNearby answer) instead of running the exact per-player distance loop for every candidate chunk.
 * Exact: TriState TRUE/FALSE short-circuit to the same answer the loop would give; DEFAULT falls back to that same loop.
 * hasPlayersNearby only drains the natural-spawn chunk tracker (no ticket/chunk-holder side effects).
 */
@Mixin(ChunkMap.class)
abstract class SpawnChunkFilterMixin {
	@Redirect(method = "collectSpawningChunks", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ChunkMap;anyPlayerCloseEnoughForSpawningInternal(Lnet/minecraft/world/level/ChunkPos;)Z"))
	private boolean silicium$cachedSpawnFilter(ChunkMap map, ChunkPos pos) {
		return map.anyPlayerCloseEnoughForSpawning(pos);
	}
}
