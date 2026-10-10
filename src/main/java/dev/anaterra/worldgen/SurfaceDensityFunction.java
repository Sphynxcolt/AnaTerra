package dev.anaterra.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anaterra.terrain.TerrainGenerator;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code anaterra:surface} — the absolute Y of the terrain surface in this column (2D: ignores the block's Y).
 * Used for the preliminary surface and, through {@code minecraft:cache_2d}, for the final density.
 */
public final class SurfaceDensityFunction implements DensityFunction {
	public static final MapCodec<SurfaceDensityFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		DensityFunction.NoiseHolder.CODEC.fieldOf("seed_noise").forGetter(SurfaceDensityFunction::seedNoise)
	).apply(i, SurfaceDensityFunction::new));
	public static final KeyDispatchDataCodec<SurfaceDensityFunction> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

	private final DensityFunction.NoiseHolder seedNoise;
	private final TerrainGenerator generator;

	public SurfaceDensityFunction(DensityFunction.NoiseHolder seedNoise) {
		this.seedNoise = seedNoise;
		this.generator = TerrainCache.forNoise(seedNoise);
	}

	@Override
	public double compute(FunctionContext context) {
		TerrainGenerator.Column col = TerrainCache.column(generator, context.blockX(), context.blockZ());
		return col.height + generator.settings().seaLevel;
	}

	public DensityFunction.NoiseHolder seedNoise() { return seedNoise; }

	@Override
	public void fillArray(double[] array, ContextProvider provider) {
		provider.fillAllDirectly(array, this);
	}

	@Override
	public DensityFunction mapChildren(Visitor visitor) {
		// This is where the game hands us the world-seeded noise.
		return new SurfaceDensityFunction(visitor.visitNoise(seedNoise));
	}

	@Override
	public double minValue() { return -2048.0; }

	@Override
	public double maxValue() { return 2048.0; }

	@Override
	public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
