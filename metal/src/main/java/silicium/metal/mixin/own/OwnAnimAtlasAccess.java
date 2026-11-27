package silicium.metal.mixin.own;

import java.util.List;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** -Dsilicium.own.int.animCopy: the atlas's sprites, animation states, size and mip count. */
@Mixin(TextureAtlas.class)
public interface OwnAnimAtlasAccess {
	@Accessor("sprites")
	List<TextureAtlasSprite> silicium$sprites();

	@Accessor("animatedTexturesStates")
	List<SpriteContents.AnimationState> silicium$animationStates();

	@Accessor("maxMipLevel")
	int silicium$maxMipLevel();

	@Accessor("width")
	int silicium$width();

	@Accessor("height")
	int silicium$height();

	/** One-pass animation's fallback: vanilla's own upload of this tick's animation frames. */
	@org.spongepowered.asm.mixin.gen.Invoker("uploadAnimationFrames")
	void silicium$uploadAnimationFrames();
}
