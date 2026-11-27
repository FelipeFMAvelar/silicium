package silicium.metal.mixin.gen;

import silicium.metal.lod.GenServerNoise;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A NoiseChunk's samplers (its fill, aquifer and surface) through LodNativeNoise's copies. Applied by GenMixinPlugin on
 * hosts where AutoConfig.serverNativeNoise() is true, or when -Dsilicium.gen.nativeNoise is set explicitly; the backend is
 * LodNativeNoise.SERVER_BACKEND. Copies are behaviour-identical and -Dsilicium.lod.nativeNoise.verify=N verifies them too.
 */
@Mixin(NoiseChunk.class)
abstract class GenNoiseChunkMixin {
	@Redirect(method = "<init>", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/levelgen/RandomState;samplersWithContext(Lnet/minecraft/world/level/levelgen/densityfunction/SamplerContext;)Lnet/minecraft/world/level/levelgen/densityfunction/DensitySamplerSet;"))
	private DensitySamplerSet silicium$nativeNoise(RandomState random, SamplerContext ctx) {
		return GenServerNoise.wrap(random.samplersWithContext(ctx), random);
	}
}
