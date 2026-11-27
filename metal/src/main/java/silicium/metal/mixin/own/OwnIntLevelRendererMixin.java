package silicium.metal.mixin.own;

import silicium.metal.own.OwnSeam;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * -Dsilicium.own.int.seam with -Dsilicium.lod.fade=instant: the sections vanilla still draws itself (translucent terrain, unless
 * -Dsilicium.own.translucent) appear without the fog fade-in while far terrain draws, as ours do (OwnSeam.fadeMs).
 */
@Mixin(LevelRenderer.class)
abstract class OwnIntLevelRendererMixin {
	@ModifyVariable(method = "extractSectionDrawGroups", at = @At("STORE"), ordinal = 0)
	private long silicium$ownFade(long fadeDuration) {
		return OwnSeam.fadeMs(fadeDuration);
	}
}
