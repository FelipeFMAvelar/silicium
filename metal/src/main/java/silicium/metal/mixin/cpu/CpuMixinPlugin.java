package silicium.metal.mixin.cpu;

import java.util.List;
import java.util.Set;
import silicium.metal.cpu.Cpu;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** These mixins apply only when a -Dsilicium.cpu.* property turns their lever on, so by default the game is unchanged. */
public final class CpuMixinPlugin implements IMixinConfigPlugin {
	static {
		silicium.metal.Defaults.apply(); // before any flag is read
	}

	@Override public void onLoad(String mixinPackage) { }
	@Override public String getRefMapperConfig() { return null; }
	@Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
		if (name.startsWith("AbFrame")) return Cpu.AB_SHOTS || Cpu.PASS_AB || Cpu.LEASH_AB || Cpu.MODEL_AB || silicium.metal.cpu.EntityBox.AB;
		if (name.startsWith("Leash")) return Cpu.LEASH;
		if (name.startsWith("Model")) return Cpu.MODEL;
		if (name.startsWith("EntityBox") || name.startsWith("EntityRendererBox")) return silicium.metal.cpu.EntityBox.ON;
		if (name.startsWith("Loading")) return Cpu.LOADING_FPS > 0;
		return false;
	}
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
	@Override public List<String> getMixins() { return null; }
	@Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
	@Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
