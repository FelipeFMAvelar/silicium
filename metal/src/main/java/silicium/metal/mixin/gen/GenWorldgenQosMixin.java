package silicium.metal.mixin.gen;

import silicium.metal.lod.LodGenQos;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * -Dsilicium.gen.worldgenQos=CLASS: a thread filling a chunk from the noise puts itself in that QoS class (once), unless
 * Qos.self("worker") already claimed that thread - then one owner wins and this call is a no-op.
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class GenWorldgenQosMixin {
	@Inject(method = "doFill", at = @At("HEAD"))
	private void silicium$qos(CallbackInfo ci) {
		LodGenQos.once(LodGenQos.WORLDGEN, "chunk generation");
	}
}
