package silicium.metal.lod;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;
import java.util.Locale;
import silicium.metal.Qos;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Thread QoS classes for the contention experiments (far-terrain workers vs the server's chunk generation on the same cores):
 * -Dsilicium.lod.qos=CLASS for the far terrain's generation workers (each sets its own class on its first tile, LodNoise),
 * -Dsilicium.gen.worldgenQos=CLASS for the integrated server's chunk generation threads (each on its first chunk fill, mixin
 * GenWorldgenQosMixin). CLASS: background (E-cores only on Apple silicon), utility, default, initiated, interactive. A thread
 * may only set its own class (libSystem's pthread_set_qos_class_self_np). Unset: nothing changes. A thread Qos already
 * assigned a class through -Dsilicium.qos.worker keeps it: once() never overwrites it (one owner per thread).
 */
public final class LodGenQos {
	private LodGenQos() {
	}

	static final int LOD = parse(System.getProperty("silicium.lod.qos"));
	public static final int WORLDGEN = parse(System.getProperty("silicium.gen.worldgenQos"));
	private static MethodHandle setSelf;
	private static final ThreadLocal<Boolean> DONE = ThreadLocal.withInitial(() -> Boolean.FALSE);

	static int parse(String name) {
		if (name == null) return 0;
		return switch (name.toLowerCase(Locale.ROOT)) {
			case "background" -> 0x09;
			case "utility" -> 0x11;
			case "default" -> 0x15;
			case "initiated", "user-initiated" -> 0x19;
			case "interactive", "user-interactive" -> 0x21;
			default -> 0;
		};
	}

	/**
	 * The calling thread into class cls once (0: nothing). A thread that Qos.self("worker") has already put in its
	 * class keeps that class: the two internal knobs stay independent but never overwrite each other. Which role is
	 * configured (and therefore which one claims a thread) comes from the built-in defaults / AutoConfig.
	 */
	public static void once(int cls, String role) {
		if (cls == 0 || DONE.get() || Qos.claimed()) return;
		DONE.set(Boolean.TRUE);
		try {
			synchronized (LodGenQos.class) {
				if (setSelf == null) {
					Linker linker = Linker.nativeLinker();
					setSelf = linker.downcallHandle(linker.defaultLookup().find("pthread_set_qos_class_self_np").orElseThrow(),
						FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));
				}
			}
			int rc = (int) setSelf.invokeExact(cls, 0);
			System.out.println("silicium-lod: QoS 0x" + Integer.toHexString(cls) + " for " + role + " (" + Thread.currentThread().getName() + "): " + rc);
		} catch (Throwable t) {
			System.out.println("silicium-lod: QoS for " + role + ": " + t);
		}
	}
}
