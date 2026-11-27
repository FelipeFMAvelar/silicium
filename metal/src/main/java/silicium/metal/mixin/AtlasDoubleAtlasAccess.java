package silicium.metal.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.List;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** -Dsilicium.metal.atlasDouble: the atlas's sprites, animation states, per-mip views (the animation passes' targets) and size. */
@Mixin(TextureAtlas.class)
public interface AtlasDoubleAtlasAccess {
	@Accessor("sprites")
	List<TextureAtlasSprite> silicium$adSprites();

	@Accessor("animatedTexturesStates")
	List<SpriteContents.AnimationState> silicium$adStates();

	@Accessor("mipViews")
	GpuTextureView[] silicium$adMipViews();

	@Accessor("mipViews")
	void silicium$adSetMipViews(GpuTextureView[] views);

	@Accessor("maxMipLevel")
	int silicium$adMaxMip();

	@Accessor("width")
	int silicium$adWidth();

	@Accessor("height")
	int silicium$adHeight();
}
