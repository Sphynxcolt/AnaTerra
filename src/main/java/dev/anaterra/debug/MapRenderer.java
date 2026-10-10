package dev.anaterra.debug;

import dev.anaterra.terrain.TerrainGenerator;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Top-down maps straight from the terrain generator, without generating any chunks. Height maps use the same
 * colouring idea as the previewer (sea depth in blues, land by height, hill shading), so the two can be compared.
 * Biome maps ask vanilla's biome source, i.e. exactly what the game will place.
 */
final class MapRenderer {
	private MapRenderer() {}

	static String render(MinecraftServer server, ServerLevel level, TerrainGenerator gen, int cx, int cz, int size, int step, boolean biomes) throws Exception {
		long t0 = System.nanoTime();
		int sea = gen.settings().seaLevel;
		int x0 = cx - size * step / 2, z0 = cz - size * step / 2;
		double[] h = new double[size * size];
		boolean[] river = new boolean[size * size];
		String[] biome = biomes ? new String[size * size] : null;
		BiomeSource source = biomes ? level.getChunkSource().getGenerator().getBiomeSource() : null;
		Climate.Sampler sampler = biomes ? level.getChunkSource().randomState().sampler() : null;

		IntStream.range(0, size).parallel().forEach(j -> {
			for (int i = 0; i < size; i++) {
				int x = x0 + i * step, z = z0 + j * step;
				TerrainGenerator.Column c = gen.column(x, z);
				h[j * size + i] = c.height;
				river[j * size + i] = c.inRiver();
				if (biomes) {
					int y = (int) Math.floor(Math.max(c.height, c.inRiver() ? c.riverSurf : c.height)) + sea;
					Holder<Biome> b = source.getNoiseBiome(x >> 2, y >> 2, z >> 2, sampler);
					biome[j * size + i] = b.getRegisteredName();
				}
			}
		});

		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		Map<String, Integer> counts = new HashMap<>();
		for (int j = 0; j < size; j++) {
			for (int i = 0; i < size; i++) {
				int k = j * size + i;
				double dx = (h[j * size + Math.min(i + 1, size - 1)] - h[j * size + Math.max(i - 1, 0)]) / (2.0 * step);
				double dz = (h[Math.min(j + 1, size - 1) * size + i] - h[Math.max(j - 1, 0) * size + i]) / (2.0 * step);
				double shade = hillshade(dx, dz);
				int rgb;
				if (biomes) {
					counts.merge(biome[k], 1, Integer::sum);
					rgb = shadeColor(biomeColor(biome[k]), h[k] > 0 ? shade : 1.0);
				} else if (river[k]) {
					rgb = 0x3C78E6;
				} else if (h[k] <= 0) {
					rgb = waterColor(-h[k]);
				} else {
					rgb = shadeColor(landColor(h[k]), shade);
				}
				img.setRGB(i, j, rgb);
			}
		}
		// mark the player
		for (int d = -4; d <= 4; d++) {
			int m = size / 2;
			img.setRGB(Math.clamp(m + d, 0, size - 1), m, 0xFF2020);
			img.setRGB(m, Math.clamp(m + d, 0, size - 1), 0xFF2020);
		}

		Path dir = server.getWorldPath(LevelResource.ROOT).resolve("anaterra-maps");
		Files.createDirectories(dir);
		Path file = dir.resolve(String.format(Locale.ROOT, "%s_%d_%d_%dbpp.png", biomes ? "biomes" : "height", cx, cz, step));
		ImageIO.write(img, "png", file.toFile());

		double secs = (System.nanoTime() - t0) / 1e9;
		StringBuilder msg = new StringBuilder(String.format(Locale.ROOT, "AnaTerra: map saved in %.1f s to %s", secs, file.toAbsolutePath()));
		if (biomes) {
			int total = size * size;
			String top = counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(12)
				.map(e -> String.format(Locale.ROOT, "%s %.1f%%", e.getKey().replace("minecraft:", ""), 100.0 * e.getValue() / total))
				.collect(Collectors.joining(", "));
			msg.append("\nMost common: ").append(top);
		}
		return msg.toString();
	}

	private static double hillshade(double dx, double dz) {
		// light from the north-west, 45° up
		double nx = -dx, ny = 1.0, nz = -dz;
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		double lx = -0.5, ly = 0.7071, lz = -0.5;
		return Math.max(0.0, (nx * lx + ny * ly + nz * lz) / len) / 0.7071;
	}

	private static int shadeColor(int rgb, double shade) {
		double f = 0.45 + 0.6 * shade;
		int r = (int) Math.min(255, ((rgb >> 16) & 255) * f);
		int g = (int) Math.min(255, ((rgb >> 8) & 255) * f);
		int b = (int) Math.min(255, (rgb & 255) * f);
		return (r << 16) | (g << 8) | b;
	}

	private static int lerp(int a, int b, double t) {
		t = Math.clamp(t, 0.0, 1.0);
		int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static int waterColor(double depth) {
		if (depth < 300) return lerp(0x5AAADC, 0x0F2A6E, depth / 300.0);
		return lerp(0x0F2A6E, 0x050A28, (depth - 300) / 1000.0);
	}

	private static int landColor(double h) {
		if (h < 4) return lerp(0xDCCD96, 0x5A9646, h / 4.0);
		if (h < 150) return lerp(0x5A9646, 0x8CA050, (h - 4) / 146.0);
		if (h < 500) return lerp(0x8CA050, 0x8C6E50, (h - 150) / 350.0);
		if (h < 900) return lerp(0x8C6E50, 0x969696, (h - 500) / 400.0);
		return lerp(0x969696, 0xFFFFFF, (h - 900) / 400.0);
	}

	private static final Map<String, Integer> BIOME_COLORS = new ConcurrentHashMap<>(Map.ofEntries(
		Map.entry("minecraft:ocean", 0x2850B4), Map.entry("minecraft:deep_ocean", 0x14286E),
		Map.entry("minecraft:warm_ocean", 0x28A0C8), Map.entry("minecraft:lukewarm_ocean", 0x2878C8),
		Map.entry("minecraft:deep_lukewarm_ocean", 0x1E5096), Map.entry("minecraft:cold_ocean", 0x324696),
		Map.entry("minecraft:deep_cold_ocean", 0x1E2864), Map.entry("minecraft:frozen_ocean", 0x8CA0DC),
		Map.entry("minecraft:deep_frozen_ocean", 0x5064A0), Map.entry("minecraft:river", 0x3C78E6),
		Map.entry("minecraft:frozen_river", 0xA0B4F0), Map.entry("minecraft:beach", 0xF0DCA0),
		Map.entry("minecraft:snowy_beach", 0xF0F0E6), Map.entry("minecraft:stony_shore", 0x8C8C8C),
		Map.entry("minecraft:desert", 0xF0D282), Map.entry("minecraft:plains", 0x8CBE5A),
		Map.entry("minecraft:sunflower_plains", 0xB4D25A), Map.entry("minecraft:forest", 0x3C8232),
		Map.entry("minecraft:birch_forest", 0x64A050), Map.entry("minecraft:dark_forest", 0x28501E),
		Map.entry("minecraft:flower_forest", 0x78AA46), Map.entry("minecraft:taiga", 0x3C6E50),
		Map.entry("minecraft:snowy_taiga", 0x96B4AA), Map.entry("minecraft:snowy_plains", 0xE6F0F0),
		Map.entry("minecraft:savanna", 0xBEB45A), Map.entry("minecraft:savanna_plateau", 0xA0963C),
		Map.entry("minecraft:badlands", 0xD2643C), Map.entry("minecraft:eroded_badlands", 0xE67846),
		Map.entry("minecraft:wooded_badlands", 0xB4643C), Map.entry("minecraft:jungle", 0x288C1E),
		Map.entry("minecraft:swamp", 0x506E46), Map.entry("minecraft:mangrove_swamp", 0x3C6432),
		Map.entry("minecraft:mushroom_fields", 0xC878C8), Map.entry("minecraft:meadow", 0x9CC86E),
		Map.entry("minecraft:grove", 0xC8DCD2), Map.entry("minecraft:snowy_slopes", 0xF0F5FA),
		Map.entry("minecraft:jagged_peaks", 0xDCE6F0), Map.entry("minecraft:frozen_peaks", 0xC8DCFA),
		Map.entry("minecraft:stony_peaks", 0xA0A0A0), Map.entry("minecraft:windswept_hills", 0x78966E),
		Map.entry("minecraft:cherry_grove", 0xF0AAC8)));

	private static int biomeColor(String id) {
		return BIOME_COLORS.computeIfAbsent(id, s -> {
			float hue = (s.hashCode() & 0xFFFF) / 65536f;
			return java.awt.Color.HSBtoRGB(hue, 0.5f, 0.85f) & 0xFFFFFF;
		});
	}
}
