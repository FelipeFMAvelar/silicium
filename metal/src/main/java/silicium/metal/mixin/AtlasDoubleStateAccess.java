package silicium.metal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** -Dsilicium.metal.atlasDouble: an animation's dirty flag (set for a sprite the spare copy lacks, so vanilla redraws its current frame there). */
@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$AnimationState")
public interface AtlasDoubleStateAccess {
	@Accessor("isDirty")
	boolean silicium$adDirty();

	@Accessor("isDirty")
	void silicium$adSetDirty(boolean dirty);
}
