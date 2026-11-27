package silicium.metal.mixin.gen;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * The generation experiments' mixins apply only with their flags: -Dsilicium.gen.worldgenQos (GenWorldgenQosMixin),
 * -Dsilicium.gen.nativeNoise (GenNoiseChunkMixin). Native noise is auto-enabled on the hosts picked by
 * AutoConfig.serverNativeNoise(); an explicit property (including "none"/"plain") still wins. Without them the game is
 * byte for byte what it is without these mixins. Reads the properties itself, so nothing of silicium.metal.lod loads before
 * the game does.
 */
public final class GenMixinPlugin implements IMixinConfigPlugin {
	static {
		silicium.metal.Defaults.apply(); // before any flag is read
	}

	private static final boolean WORLDGEN_QOS = System.getProperty("silicium.gen.worldgenQos") != null;
	private static final String GEN_NATIVE = effectiveNativeNoise();
	private static final boolean NATIVE_NOISE = !GEN_NATIVE.isBlank();

	/**
	 * A blank/absent -Dsilicium.gen.nativeNoise is not a choice: AutoConfig decides NEON vs off. The effective value is
	 * written back to the property so LodNativeNoise.SERVER_BACKEND reads exactly the same decision (an explicit value
	 * is never touched, so "none"/"plain"/"off" remain an override).
	 */
	private static String effectiveNativeNoise() {
		String v = System.getProperty("silicium.gen.nativeNoise", "");
		if (v.isBlank() && silicium.metal.AutoConfig.serverNativeNoise()) {
			v = "neon";
			System.setProperty("silicium.gen.nativeNoise", v);
		}
		return v;
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.endsWith(".GenWorldgenQosMixin")) return WORLDGEN_QOS;
		if (mixinClassName.endsWith(".GenNoiseChunkMixin")) return NATIVE_NOISE;
		return false;
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
