package silicium.metal.mixin.own;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Our own near terrain's mixins apply only with -Dsilicium.own=true and without Sodium (which replaces the same renderer): without
 * the switch the game is byte-for-byte what it is without them. Reads the property itself so nothing of silicium.metal.own loads
 * before the game does.
 */
public final class OwnMixinPlugin implements IMixinConfigPlugin {
	static {
		silicium.metal.Defaults.apply(); // before any flag is read
	}

	private static final boolean ENABLED = Boolean.getBoolean("silicium.own") && !FabricLoader.getInstance().isModLoaded("sodium");

	@Override
	public void onLoad(String mixinPackage) {
		if (Boolean.getBoolean("silicium.own") && !ENABLED) System.out.println("silicium-own: Sodium is loaded, our near terrain stays off");
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.endsWith(".OwnAnimFlushMixin") || mixinClassName.endsWith(".OwnAnimFlushFrameMixin")) return ENABLED && Boolean.getBoolean("silicium.own.int.animOnePass");
		if (mixinClassName.endsWith(".OwnAtlasWriteMixin")) return ENABLED && Boolean.getBoolean("silicium.own.int.atlasWrite");
		if (mixinClassName.endsWith(".OwnAnimCopyMixin")) return ENABLED && (Boolean.getBoolean("silicium.own.int.animCopy") || Integer.getInteger("silicium.own.int.animHash", 0) > 0 || Integer.getInteger("silicium.own.int.animVerify", 0) > 0 || Integer.getInteger("silicium.own.int.animVerifyCopy", 0) > 0 || Boolean.getBoolean("silicium.own.int.animOnePass"));
		if (mixinClassName.endsWith(".FrameLogNoAnimMixin")) return ENABLED && Boolean.getBoolean("silicium.own.int.noAnim");  // (measurement only, not exact)
		if (mixinClassName.contains(".FrameLog")) return ENABLED && Boolean.getBoolean("silicium.own.int.frameLog");  // (measurement only)
		return ENABLED;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
