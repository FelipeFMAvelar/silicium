package silicium.metal.mixin.own;

import java.util.Map;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** -Dsilicium.own.int.lists: vanilla's per-layer uber buffers. */
@Mixin(SectionRenderDispatcher.class)
public interface OwnIntDispatcherAccess {
	@Accessor("chunkUberBuffers")
	Map<?, ?> silicium$uberBuffers();
}
