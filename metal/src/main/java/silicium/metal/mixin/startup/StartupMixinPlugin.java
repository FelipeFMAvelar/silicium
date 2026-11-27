package silicium.metal.mixin.startup;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Startup mixins follow AutoConfig by default; every {@code -Dsilicium.startup.*} key is only an internal override. */
public final class StartupMixinPlugin implements IMixinConfigPlugin {
	static {
		silicium.metal.Defaults.apply(); // before any flag is read or AutoConfig is touched
	}

	@Override public void onLoad(String mixinPackage) { }
	@Override public String getRefMapperConfig() { return null; }
	@Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		// Never load silicium.metal.Startup here: it references game classes, which must not be loaded before their own mixins apply.
		// AutoConfig depends only on Hardware (system calls), so it is safe at this stage and is the source of the hardware decisions.
		if (mixinClassName.endsWith("NarratorMixin")) return "lazy".equals(System.getProperty("silicium.startup.narrator"));
		if (mixinClassName.endsWith("CrashPreloadMixin")) return crashPreload();
		if (mixinClassName.endsWith("BlockCacheMixin") || mixinClassName.endsWith("BootstrapCacheMixin")) return blockCaches();
		if (mixinClassName.endsWith("FadeMixin")) return System.getProperty("silicium.startup.fadeMs") != null;
		return false;
	}

	/** The effective crash-preload decision: an explicit override wins, otherwise AutoConfig decides. */
	private static boolean crashPreload() {
		String override = System.getProperty("silicium.startup.crashPreload");
		if (override != null) return !override.isEmpty() && !"false".equalsIgnoreCase(override);
		return silicium.metal.AutoConfig.asyncCrashPreload();
	}

	/** Both cache mixins must agree: they defer in the registry loop and finish it from Bootstrap. An explicit value
	 * (e.g. =digest for the A/B comparison) keeps the old "apply whenever set" behaviour; AutoConfig supplies the default. */
	private static boolean blockCaches() {
		String override = System.getProperty("silicium.startup.blockCache");
		if (override != null && !override.isEmpty()) return true;
		return silicium.metal.AutoConfig.parallelBlockCaches();
	}
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
	@Override public List<String> getMixins() { return null; }
	@Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
	@Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
