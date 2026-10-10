package dev.anaterra.worldgen;

import dev.anaterra.terrain.TerrainGenerator;
import dev.anaterra.terrain.TerrainSettings;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * One terrain generator per world seed, plus a small per-thread cache of finished columns: the surface, the
 * preliminary surface, the six climate values and the river water all ask for the same column, and a column is
 * expensive.
 * <p>
 * The terrain settings are global: the server loads them from the world folder before the world is created
 * ({@link SettingsStore}) and every generator made after that uses them.
 */
public final class TerrainCache {
	private TerrainCache() {}

	private static final ConcurrentHashMap<Long, TerrainGenerator> GENERATORS = new ConcurrentHashMap<>();
	private static volatile TerrainSettings settings = new TerrainSettings();
	private static volatile TerrainGenerator active;

	/** Sets the settings for generators made from now on and forgets the old generators. */
	public static void useSettings(TerrainSettings s) {
		settings = s;
		GENERATORS.clear();
		active = null;
		resetStats();
	}

	public static TerrainSettings settings() { return settings; }

	/** The generator of the world that is currently running, or null if no AnaTerra world has been set up. */
	public static TerrainGenerator active() { return active; }

	/**
	 * Density functions don't receive the world seed directly. Every AnaTerra function carries a vanilla noise
	 * ("seed_noise") that the game seeds from the world seed when it wires up the noise router; sampling that noise
	 * at a few fixed points gives a number that is unique per world seed.
	 */
	public static TerrainGenerator forNoise(DensityFunction.NoiseHolder holder) {
		NormalNoise n = holder.noise();
		long seed = 0L;
		if (n != null) {
			seed = Double.doubleToLongBits(n.getValue(0.1234, 0.0, 0.5678));
			seed = seed * 31 + Double.doubleToLongBits(n.getValue(1234.5, 0.0, -987.6));
			seed = seed * 31 + Double.doubleToLongBits(n.getValue(-4321.25, 0.0, 2468.75));
		}
		TerrainGenerator gen = GENERATORS.computeIfAbsent(seed, s -> new TerrainGenerator(settings, s));
		if (n != null) active = gen;   // only the seeded copy belongs to a real world
		return gen;
	}

	private static final int SIZE = 4096;   // power of two

	private static final class Slots {
		final long[] keys = new long[SIZE];
		final TerrainGenerator[] owners = new TerrainGenerator[SIZE];
		final TerrainGenerator.Column[] cols = new TerrainGenerator.Column[SIZE];
	}

	private static final ThreadLocal<Slots> CACHE = ThreadLocal.withInitial(Slots::new);

	// Numbers for /anaterra perf
	private static final LongAdder LOOKUPS = new LongAdder(), COMPUTED = new LongAdder(), NANOS = new LongAdder();
	private static volatile long statsSince = System.nanoTime();

	public static TerrainGenerator.Column column(TerrainGenerator gen, int x, int z) {
		long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
		int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 52) & (SIZE - 1);
		Slots c = CACHE.get();
		LOOKUPS.increment();
		if (c.owners[slot] == gen && c.keys[slot] == key) return c.cols[slot];
		long t0 = System.nanoTime();
		TerrainGenerator.Column col = gen.column(x, z);
		NANOS.add(System.nanoTime() - t0);
		COMPUTED.increment();
		c.keys[slot] = key; c.owners[slot] = gen; c.cols[slot] = col;
		return col;
	}

	public static void resetStats() {
		LOOKUPS.reset(); COMPUTED.reset(); NANOS.reset();
		statsSince = System.nanoTime();
	}

	/** lookups, columns computed, total nanoseconds spent computing, nanoseconds since the last reset */
	public static long[] stats() {
		return new long[] { LOOKUPS.sum(), COMPUTED.sum(), NANOS.sum(), System.nanoTime() - statsSince };
	}
}
