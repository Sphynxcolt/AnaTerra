package dev.anaterra.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.anaterra.AnaTerra;
import dev.anaterra.terrain.TerrainGenerator;
import dev.anaterra.terrain.TerrainSettings;
import dev.anaterra.worldgen.ClimateDensityFunction;
import dev.anaterra.worldgen.SettingsStore;
import dev.anaterra.worldgen.TerrainCache;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Debug commands. All of them only read, so they are open to every player.
 * <pre>
 * /anaterra probe                        what the generator thinks about the column you stand in
 * /anaterra perf [reset]                 how fast columns are being generated
 * /anaterra settings                     the settings of this world (only the ones that differ from the defaults)
 * /anaterra map [size] [blocksPerPixel]  shaded height map around you, saved as a PNG in the world folder
 * /anaterra biomemap [size] [blocksPerPixel]  the same area coloured by biome
 * </pre>
 */
public final class AnaTerraCommands {
	private AnaTerraCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
			literal("anaterra")
				.then(literal("probe").executes(AnaTerraCommands::probe))
				.then(literal("perf").executes(AnaTerraCommands::perf)
					.then(literal("reset").executes(c -> { TerrainCache.resetStats(); say(c.getSource(), "AnaTerra: perf counters reset"); return 1; })))
				.then(literal("settings").executes(AnaTerraCommands::settings))
				.then(mapCommand("map", false))
				.then(mapCommand("biomemap", true))
		));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mapCommand(String name, boolean biomes) {
		return literal(name).executes(c -> map(c, 512, 4, biomes))
			.then(argument("size", IntegerArgumentType.integer(64, 2048)).executes(c -> map(c, IntegerArgumentType.getInteger(c, "size"), 4, biomes))
				.then(argument("blocksPerPixel", IntegerArgumentType.integer(1, 64)).executes(c ->
					map(c, IntegerArgumentType.getInteger(c, "size"), IntegerArgumentType.getInteger(c, "blocksPerPixel"), biomes))));
	}

	static void say(CommandSourceStack src, String text) {
		src.sendSuccess(() -> Component.literal(text), false);
	}

	private static TerrainGenerator generator(CommandSourceStack src) {
		TerrainGenerator gen = TerrainCache.active();
		if (gen == null) src.sendFailure(Component.literal("AnaTerra: this world doesn't use AnaTerra terrain"));
		return gen;
	}

	private static int probe(CommandContext<CommandSourceStack> c) {
		CommandSourceStack src = c.getSource();
		TerrainGenerator gen = generator(src);
		if (gen == null) return 0;
		Vec3 p = src.getPosition();
		int x = Mth.floor(p.x), y = Mth.floor(p.y), z = Mth.floor(p.z);
		TerrainGenerator.Column col = gen.column(x, z);
		int sea = gen.settings().seaLevel;
		String biome = src.getLevel().getBiome(BlockPos.containing(p)).getRegisteredName();

		say(src, String.format(Locale.ROOT, "§6AnaTerra probe§r at %d %d %d", x, y, z));
		say(src, String.format(Locale.ROOT, "Surface y %.1f (%+.1f from sea level), zone %s",
			col.height + sea, col.height, ClimateDensityFunction.zone(col)));
		say(src, String.format(Locale.ROOT, "Layers: C %.3f  mountains %.2f  peaks %.2f  temp %.2f  humid %.2f  weird %.2f  uplift %.2f",
			col.C, col.m, col.mf, col.T, col.H, col.W, col.lift));
		say(src, String.format(Locale.ROOT, "Landforms: hot %.2f  warm %.2f  badlands %.2f  eroded %.2f  wooded %.2f  dunes %.2f  swamp %.2f",
			col.hot, col.warm, col.badlands, col.eroded, col.wooded, col.dunes, col.swamp));
		if (col.riverEdge < 1e8)
			say(src, String.format(Locale.ROOT, "River: %s (edge %.1f), water y %.1f, width %.0f",
				col.riverEdge < 0 ? "in the channel" : "nearby", col.riverEdge, col.riverSurf + sea, col.riverW));
		else
			say(src, "River: none here");
		double depth = (col.height + sea - y) / 128.0;
		say(src, String.format(Locale.ROOT, "Climate given to vanilla: cont %.3f  erosion %.3f  weird %.3f  temp %.2f  humid %.2f  depth %.2f",
			ClimateDensityFunction.continentalness(col), ClimateDensityFunction.erosion(col), ClimateDensityFunction.weirdness(col),
			col.T, col.H, depth));
		say(src, "Biome here: §a" + biome);
		return 1;
	}

	private static int perf(CommandContext<CommandSourceStack> c) {
		long[] s = TerrainCache.stats();
		double seconds = s[3] / 1e9;
		double avgUs = s[1] > 0 ? s[2] / 1e3 / s[1] : 0;
		double hit = s[0] > 0 ? 100.0 * (s[0] - s[1]) / s[0] : 0;
		CommandSourceStack src = c.getSource();
		say(src, String.format(Locale.ROOT, "§6AnaTerra perf§r over the last %.0f s:", seconds));
		say(src, String.format(Locale.ROOT, "%,d columns generated (%.0f per second), %.1f µs each on average", s[1], s[1] / Math.max(seconds, 1e-9), avgUs));
		say(src, String.format(Locale.ROOT, "%,d column lookups, %.1f%% answered from the cache", s[0], hit));
		say(src, String.format(Locale.ROOT, "CPU time in AnaTerra: %.1f s (across all worker threads)", s[2] / 1e9));
		return 1;
	}

	private static int settings(CommandContext<CommandSourceStack> c) {
		CommandSourceStack src = c.getSource();
		TerrainSettings now = TerrainCache.settings(), def = new TerrainSettings();
		StringBuilder sb = new StringBuilder();
		int n = 0;
		for (Field f : TerrainSettings.class.getFields()) {
			if (Modifier.isStatic(f.getModifiers())) continue;
			try {
				Object a = f.get(now), b = f.get(def);
				if (!a.equals(b)) { sb.append(n++ > 0 ? ", " : "").append(f.getName()).append(" = ").append(a); }
			} catch (IllegalAccessException ignored) {}
		}
		say(src, "§6AnaTerra settings§r (" + SettingsStore.file(src.getServer()) + ")");
		say(src, n == 0 ? "All defaults" : "Changed: " + sb);
		return 1;
	}

	private static int map(CommandContext<CommandSourceStack> c, int size, int step, boolean biomes) {
		CommandSourceStack src = c.getSource();
		TerrainGenerator gen = generator(src);
		if (gen == null) return 0;
		Vec3 p = src.getPosition();
		int cx = Mth.floor(p.x), cz = Mth.floor(p.z);
		MinecraftServer server = src.getServer();
		say(src, String.format(Locale.ROOT, "AnaTerra: drawing %s, %d × %d blocks around %d %d…",
			biomes ? "biome map" : "height map", size * step, size * step, cx, cz));
		CompletableFuture.runAsync(() -> {
			try {
				String result = MapRenderer.render(server, src.getLevel(), gen, cx, cz, size, step, biomes);
				server.execute(() -> say(src, result));
			} catch (Throwable t) {
				AnaTerra.LOGGER.error("AnaTerra map failed", t);
				server.execute(() -> src.sendFailure(Component.literal("AnaTerra: map failed: " + t)));
			}
		});
		return 1;
	}
}
