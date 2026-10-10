package dev.anaterra.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anaterra.terrain.TerrainGenerator;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code anaterra:climate} — one of vanilla's six biome parameters, written from AnaTerra's own layers so vanilla's
 * biome picker places biomes on the terrain we actually generate (rivers in our river channels, deep ocean past our
 * shelf, peaks on our ranges, and so on).
 */
public final class ClimateDensityFunction implements DensityFunction {
	public enum Parameter implements StringRepresentable {
		CONTINENTALNESS("continentalness"), EROSION("erosion"), WEIRDNESS("weirdness"),
		TEMPERATURE("temperature"), HUMIDITY("humidity"), DEPTH("depth");

		public static final Codec<Parameter> CODEC = StringRepresentable.fromEnum(Parameter::values);
		private final String name;
		Parameter(String name) { this.name = name; }
		@Override public String getSerializedName() { return name; }
	}

	public static final MapCodec<ClimateDensityFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		DensityFunction.NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(ClimateDensityFunction::seedNoise),
		Parameter.CODEC.fieldOf("parameter").forGetter(ClimateDensityFunction::parameter)
	).apply(i, ClimateDensityFunction::new));
	public static final KeyDispatchDataCodec<ClimateDensityFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

	private final DensityFunction.NoiseHolder seedNoise;
	private final Parameter parameter;
	private final TerrainGenerator generator;

	public ClimateDensityFunction(DensityFunction.NoiseHolder seedNoise, Parameter parameter) {
		this.seedNoise = seedNoise;
		this.parameter = parameter;
		this.generator = TerrainCache.forNoise(seedNoise);
	}

	@Override
	public double compute(FunctionContext context) {
		TerrainGenerator.Column c = TerrainCache.column(generator, context.blockX(), context.blockZ());
		return switch (parameter) {
			case CONTINENTALNESS -> continentalness(c);
			case EROSION -> erosion(c);
			case WEIRDNESS -> weirdness(c);
			case TEMPERATURE -> c.T;
			case HUMIDITY -> c.H;
			// vanilla: 0 at the surface, +1 about every 128 blocks further down
			case DEPTH -> (c.height + generator.settings().seaLevel - context.blockY()) / 128.0;
		};
	}

	/** Where a column sits relative to the sea. Decides the continentalness band, so biomes follow the real shoreline. */
	public enum Zone { DEEP_OCEAN, OCEAN, SHALLOWS, SHORE, CLIFF_SHORE, INLAND, ISLAND }

	public static Zone zone(TerrainGenerator.Column c) {
		double h = c.height;              // relative to sea level; the top block is above water when h > 0
		if (h > 0 && c.C < -0.14) return Zone.ISLAND;
		if (h <= 0) {
			if (c.inRiver()) return Zone.INLAND;            // river beds below sea level near the coast
			if (h > -4 && c.C > -0.1) return Zone.SHALLOWS; // sandy shallows in front of beaches, no ocean structures
			return h < -50 ? Zone.DEEP_OCEAN : Zone.OCEAN;
		}
		if (c.C < 0.06 && !c.inRiver()) {
			if (h < 4 && c.m < 0.3) return Zone.SHORE;
			if (h < 10 && c.m >= 0.3) return Zone.CLIFF_SHORE;
		}
		return Zone.INLAND;
	}

	/**
	 * Vanilla: deep ocean below -0.455, ocean below -0.19, coast to -0.11, then inland; mushroom fields below -1.05.
	 * The band comes from the actual height, the value inside the band from C, so the ocean biomes sit exactly on water
	 * and the beaches exactly on the shoreline.
	 */
	public static double continentalness(TerrainGenerator.Column c) {
		double h = c.height;
		return switch (zone(c)) {
			case ISLAND -> -1.1;
			case DEEP_OCEAN -> Math.max(-1.0, -0.47 - (-h - 50) / 1500.0);
			case OCEAN -> -0.2 - 0.25 * Math.min(1.0, -h / 50.0);
			case SHALLOWS, SHORE, CLIFF_SHORE -> -0.15;
			case INLAND -> Math.max(-0.10, Math.min(1.0, -0.11 + Math.max(c.C, 0.0) * 1.2));
		};
	}

	/** Vanilla: low erosion = mountains and peaks, high = flat land; swamps need the top band. */
	public static double erosion(TerrainGenerator.Column c) {
		Zone z = zone(c);
		if (z == Zone.SHORE || z == Zone.SHALLOWS) return Math.max(0.1, 0.45 + (-0.85 - 0.45) * c.m);   // beach, not stony shore
		if (z == Zone.CLIFF_SHORE) return -0.6;                                                         // stony shore
		if (c.swamp > 0.5) return 0.8;
		return 0.45 + (-0.85 - 0.45) * c.m;
	}

	/**
	 * Vanilla: weirdness near 0 is a valley, which is where it puts rivers. Only our river channels get it.
	 * Along the shore it is kept in vanilla's "low" slice, the one that has beaches on every kind of coast.
	 */
	public static double weirdness(TerrainGenerator.Column c) {
		if (c.inRiver()) return 0.0;
		double w = c.W * 1.2;
		Zone z = zone(c);
		if (z == Zone.SHORE || z == Zone.SHALLOWS || z == Zone.CLIFF_SHORE) return w < 0 ? -0.15 : 0.15;
		double a = Math.max(Math.abs(w), 0.08);
		return Math.min(1.0, w < 0 ? -a : a);
	}

	public DensityFunction.NoiseHolder seedNoise() { return seedNoise; }
	public Parameter parameter() { return parameter; }

	@Override
	public void fillArray(double[] array, ContextProvider provider) {
		provider.fillAllDirectly(array, this);
	}

	@Override
	public DensityFunction mapChildren(Visitor visitor) {
		return new ClimateDensityFunction(visitor.visitNoise(seedNoise), parameter);
	}

	@Override
	public double minValue() { return parameter == Parameter.DEPTH ? -32.0 : -2.0; }

	@Override
	public double maxValue() { return parameter == Parameter.DEPTH ? 32.0 : 2.0; }

	@Override
	public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
