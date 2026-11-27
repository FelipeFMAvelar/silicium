package silicium.metal.mixin.own;

import silicium.metal.own.AnimCopy;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dsilicium.own.int.animCopy: the atlas's animation draws through AnimCopy (a small target and copies) instead of passes over whole mips. */
@Mixin(TextureAtlas.class)
public class OwnAnimCopyMixin {
	@Inject(method = "uploadAnimationFrames", at = @At("HEAD"), cancellable = true)
	private void silicium$animCopy(CallbackInfo ci) {
		if (silicium.metal.own.AnimOnePass.upload((TextureAtlas) (Object) this) || !silicium.metal.own.AnimOnePass.ON && AnimCopy.upload((TextureAtlas) (Object) this)) ci.cancel();
	}

	@Inject(method = "uploadAnimationFrames", at = @At("RETURN"))
	private void silicium$animVerify(CallbackInfo ci) {
		AnimCopy.afterVanilla((TextureAtlas) (Object) this);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void silicium$animHash(CallbackInfo ci) {
		AnimCopy.ticked((TextureAtlas) (Object) this);
	}
}
