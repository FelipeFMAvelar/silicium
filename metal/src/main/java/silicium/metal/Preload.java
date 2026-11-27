package silicium.metal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Loads, on background threads, the classes Minecraft is about to load one by one on its main thread during boot and
 * world load, in the order it loads them, so reading, Mixin-transforming and defining them overlaps the main thread's
 * own work instead of stalling it. Loading only (no static initializers run), so nothing about the game changes: the
 * same classes end up loaded, only sooner and off the main thread. The list (resources/silicium/metal/preload.txt.gz)
 * comes from a -Xlog:class+load run of the shipping stack (tools/preloadlist.py); names that don't exist in some
 * other mod set are skipped. -Dsilicium.preload=false turns it off; the thread count comes from AutoConfig unless
 * -Dsilicium.preload.threads=N overrides it. Both are internal knobs, not user settings.
 */
public final class Preload implements PreLaunchEntrypoint {
	static {
		silicium.metal.Defaults.apply(); // before any flag is read
	}

	private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("silicium.preload", "true"));
	private static final int THREADS = Integer.getInteger("silicium.preload.threads", AutoConfig.preloadThreads());

	@Override
	public void onPreLaunch() {
		PlatformCheck.run();
		if (!ENABLED) return;
		List<String> names = new ArrayList<>();
		try (InputStream in = Preload.class.getResourceAsStream("/silicium/metal/preload.txt.gz")) {
			if (in == null) return;
			BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
			for (String line; (line = r.readLine()) != null; ) if (!line.isEmpty()) names.add(line);
		} catch (IOException e) {
			return;
		}
		ClassLoader loader = Preload.class.getClassLoader();
		AtomicInteger next = new AtomicInteger();
		for (int t = 0; t < THREADS; t++) {
			Thread thread = new Thread(() -> {
				for (int i; (i = next.getAndIncrement()) < names.size(); ) {
					String name = names.get(i);
					// Cheap resource check first: names from other mod sets (e.g. Sodium's 502 net.caffeinemc classes,
					// declared as breaks) are guaranteed absent and would otherwise throw with a stack trace each.
					// Class.forName still runs in the same index order for every name that is present.
					if (loader.getResource(name.replace('.', '/') + ".class") == null) continue;
					try {
						Class.forName(name, false, loader);
					} catch (Throwable absent) {
						// In the jar but not loadable in this environment: the game wouldn't load it either.
					}
				}
			}, "silicium class preload #" + t);
			thread.setDaemon(true);
			thread.start();
		}
	}
}
