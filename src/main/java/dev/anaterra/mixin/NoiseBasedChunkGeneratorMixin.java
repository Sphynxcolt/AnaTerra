package dev.anaterra.mixin;

import dev.anaterra.terrain.TerrainGenerator;
import dev.anaterra.worldgen.TerrainCache;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Decides which fluid fills each open space below its water line. AnaTerra worlds have vanilla's aquifers switched
 * off, so this is the only source of water:
 * <ul>
 *   <li>Lava only just above the world bottom (vanilla fills every open space below y -54 with lava, which would put
 *       lava into our deep oceans and trenches).</li>
 *   <li>Sea water up to sea level, everywhere.</li>
 *   <li>River water up to the river's own water level, inside our river channels above sea level.</li>
 * </ul>
 * Vanilla worlds (bottom at -64 or higher) are left untouched.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {
	@Inject(method = "createFluidPicker", at = @At("HEAD"), cancellable = true)
	private static void anaterra$fluids(NoiseGeneratorSettings settings, CallbackInfoReturnable<Aquifer.FluidPicker> cir) {
		int minY = settings.noiseSettings().minY();
		if (minY >= -64) return;
		int lavaBelow = minY + 10;
		int sea = settings.seaLevel();
		Aquifer.FluidStatus lava = new Aquifer.FluidStatus(lavaBelow, Blocks.LAVA.defaultBlockState());
		Aquifer.FluidStatus water = new Aquifer.FluidStatus(sea, settings.defaultFluid());
		cir.setReturnValue((x, y, z) -> {
			if (y < lavaBelow) return lava;
			if (y < sea) return water;
			TerrainGenerator gen = TerrainCache.active();
			if (gen == null) return water;
			TerrainGenerator.Column c = TerrainCache.column(gen, x, z);
			if (c.riverEdge < 0.0 && c.riverSurf > 0.0) {
				// water in blocks below the river's surface; the channel floor is at least a block below it
				int level = sea + (int) Math.floor(c.riverSurf + 0.5);
				if (y < level) return new Aquifer.FluidStatus(level, settings.defaultFluid());
			}
			return water;   // above sea level: air
		});
	}
}
