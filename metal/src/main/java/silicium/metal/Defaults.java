package silicium.metal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The built-in defaults: every finished optimization is on, with no config file and no profile selection.
 * A command-line {@code -D} flag is still honoured when one is set explicitly. Hardware limits and Metal
 * capability checks stay where the features use them, so a path that a chip cannot run or that measures
 * slower on it is still chosen by {@link AutoConfig} / the native layer, not by the user.
 *
 * This runs first in every mixin config plugin and in the preLaunch entrypoint, before any feature reads
 * a flag; the first call does the work, later calls return at once.
 */
public final class Defaults {
	private static boolean applied;

	/** The effective feature set: all measured, exact-vs-vanilla wins; experimental far terrain stays opt-in. */
	private static final String[] FLAGS = {
		"silicium.alloc.lightMap=true",
		"silicium.chunk.chunkGet=true",
		"silicium.chunk.poi=true",
		"silicium.chunk.saveSkip=true",
		"silicium.chunk.ticks=true",
		"silicium.cpu.cmdAhead=true",
		"silicium.cpu.fence=true",
		"silicium.cpu.model=true",
		"silicium.cpu.texelCache=true",
		"silicium.edgeCap=true",
		"silicium.lod.fade=instant",
		"silicium.lod.meshShader=true",
		"silicium.lod.nativeNoise=neon",
		"silicium.lod.prio=screen",
		"silicium.lod.publish=gpu",
		"silicium.lod.render=mesh",
		"silicium.lod.tg=128",
		"silicium.lod.yield=missing:0",
		"silicium.metal.lazyClears=true",
		"silicium.metal.paceAdapt=true",
		"silicium.metal.presentQueue=acquire",
		"silicium.own=true",
		"silicium.own.arena.preSize=true",
		"silicium.own.arena.preSizeMaxCores=9",
		"silicium.own.compact=true",
		"silicium.own.debugGroups=false",
		"silicium.own.frag.a2Exact=true",
		"silicium.own.frag.a2ExactMaxCores=8",
		"silicium.own.frag.box=true",
		"silicium.own.frag.fine=3",
		"silicium.own.frag.half=true",
		"silicium.own.frag.lean=true",
		"silicium.own.frag.msub=true",
		"silicium.own.frag.pipe=true",
		"silicium.own.frag.pyrSkip=1",
		"silicium.own.frag.sizeQuads=16",
		"silicium.own.frag.sizes=true",
		"silicium.own.frag.splitNear=216",
		"silicium.own.frag.testFast=true",
		"silicium.own.frag.testGather=true",
		"silicium.own.frag.testLate=true",
		"silicium.own.frag.tieClose=true",
		"silicium.own.frag.uocc=true",
		"silicium.own.frag.uoccMaxMp=2.0",
		"silicium.own.frag.uoccMaxMpCores=8",
		"silicium.own.frag.vGroup=2",
		"silicium.own.frag.vGroupMinCores=8",
		"silicium.own.hizGather=true",
		"silicium.own.hizTop=true",
		"silicium.own.int.animMinCores=8",
		"silicium.own.int.animOnePass=true",
		"silicium.own.int.atlasWrite=true",
		"silicium.own.int.compileRegionDrop=true",
		"silicium.own.int.cullR=true",
		"silicium.own.int.cullW=8",
		"silicium.own.int.lifeFix=true",
		"silicium.own.int.lists=true",
		"silicium.own.int.preEarly=true",
		"silicium.own.int.seam=true",
		"silicium.own.int.visible=true",
		"silicium.own.mesh=true",
		"silicium.own.mesh.ao=true",
		"silicium.own.mesh.bucketClass=suffix",
		"silicium.own.mesh.cell=8",
		"silicium.own.mesh.exactPos=true",
		"silicium.own.mesh.frapiRegion=true",
		"silicium.own.mesh.order=cell",
		"silicium.own.mesh.prefill=true",
		"silicium.own.mesh.qrec=true",
		"silicium.own.mesh.region=true",
		"silicium.own.mesh.subbox=true",
		"silicium.own.mesh.tcull=true",
		"silicium.own.mesh.tfrustum=true",
		"silicium.own.mesh.thalf=true",
		"silicium.own.mesh.tint=true",
		"silicium.own.mesh.tsorted=true",
		"silicium.own.run=32",
		"silicium.own.tl.a1Split=0",
		"silicium.own.tl.a1SplitMaxCores=7",
		"silicium.own.tl.a1SplitMinCores=8",
		"silicium.own.translucent=true",
		"silicium.own.vignetteNoop=true",
		"silicium.startup.mslCache=true",
		"silicium.startup.narrator=lazy",
	};

	private Defaults() {
	}

	public static synchronized void apply() {
		if (applied) return;
		applied = true;
		for (String flag : FLAGS) {
			int eq = flag.indexOf('=');
			String key = flag.substring(0, eq);
			if (System.getProperty(key) == null) System.setProperty(key, flag.substring(eq + 1));
		}
		distantHorizonsOnOpenGl();
	}

	/**
	 * Distant Horizons draws through the Metal backend with its default Blaze3D renderer (DhRenderApiMixin), next to our own
	 * terrain. Its OpenGL renderer can't: DH picks that one when its config says renderingEngine = "OPEN_GL" (or when Iris
	 * is loaded, which needs Sodium and so never loads with this build), and on the Metal backend it then stops at startup
	 * ("API doesn't match"). Then the Metal backend switches to OpenGL. Without DH nothing here runs.
	 */
	private static void distantHorizonsOnOpenGl() {
		if (System.getProperty("silicium.metal") != null) return;
		String why;
		try {
			net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
			if (!loader.isModLoaded("distanthorizons")) return;
			why = loader.isModLoaded("iris") ? "with Iris" : dhOpenGlEngine() ? "set to its OpenGL renderer" : null;
		} catch (Throwable t) {
			return;
		}
		if (why == null) return;
		System.setProperty("silicium.metal", "false");
		System.out.println("[silicium] Distant Horizons " + why + " needs OpenGL, using OpenGL; silicium's other optimizations stay on");
	}

	/** config/DistantHorizons.toml says renderingEngine = "OPEN_GL" (its default is AUTO: Blaze3D on 26.x). */
	private static boolean dhOpenGlEngine() {
		Path toml = gameDir().resolve("config").resolve("DistantHorizons.toml");
		if (!Files.isRegularFile(toml)) return false;
		try {
			for (String line : Files.readAllLines(toml, StandardCharsets.UTF_8)) {
				String t = line.strip();
				if (t.startsWith("renderingEngine")) return t.replace(" ", "").startsWith("renderingEngine=\"OPEN_GL\"");
			}
		} catch (IOException | RuntimeException e) {
			System.out.println("[silicium] can't read " + toml + ": " + e);
		}
		return false;
	}

	private static Path gameDir() {
		try {
			return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
		} catch (Throwable t) {
			return Path.of("").toAbsolutePath();
		}
	}
}
