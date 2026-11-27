package silicium.metal.mixin.own;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Our mesher hands solid and cutout to our renderer as placeholder section buffers (the real quads live in our arena).
 * Improved Transparency's depth-bounds pass draws those vanilla buffers to bound the transmittance integral, so with our
 * renderer it sees no opaque geometry and culls every translucent fragment (water disappears). The classic path has no
 * such pass and our renderer draws translucent terrain itself, so with the own renderer enabled we report OIT as off.
 */
@Mixin(GameRenderer.class)
abstract class OwnOitCompatMixin {
	@Inject(method = "useImprovedTransparency", at = @At("HEAD"), cancellable = true)
	private void silicium$classicTransparencyWithOwn(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}
}
