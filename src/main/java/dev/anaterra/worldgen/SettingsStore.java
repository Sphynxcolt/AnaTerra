package dev.anaterra.worldgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.anaterra.AnaTerra;
import dev.anaterra.terrain.TerrainSettings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Keeps each world's terrain settings in {@code <world folder>/anaterra.json}.
 * <ul>
 *   <li>Server starting: if the world has the file, its settings are used. Otherwise the settings picked in the
 *       world creation screen are used (or the defaults).</li>
 *   <li>Server started: if the world turned out to be an AnaTerra world and had no file yet, the file is written,
 *       so the world keeps its settings from now on.</li>
 * </ul>
 * On a dedicated server, edit {@code anaterra.json} before the first start (or delete the world's region files after
 * changing it) to try other settings.
 */
public final class SettingsStore {
	private SettingsStore() {}

	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final String FILE = "anaterra.json";

	/** Settings picked in the world creation screen, waiting for the world to be created. Null = defaults. */
	public static volatile TerrainSettings pending;

	private static boolean needsWrite;

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(SettingsStore::onStarting);
		ServerLifecycleEvents.SERVER_STARTED.register(SettingsStore::onStarted);
	}

	public static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(FILE);
	}

	private static void onStarting(MinecraftServer server) {
		Path f = file(server);
		TerrainSettings s = null;
		if (Files.exists(f)) {
			try {
				s = GSON.fromJson(Files.readString(f), TerrainSettings.class);
				AnaTerra.LOGGER.info("AnaTerra settings loaded from {}", f);
			} catch (Exception e) {
				AnaTerra.LOGGER.error("Could not read {}, using the defaults", f, e);
			}
			needsWrite = false;
		} else {
			s = pending;
			needsWrite = true;
		}
		pending = null;
		TerrainCache.useSettings(s != null ? s : new TerrainSettings());
	}

	private static void onStarted(MinecraftServer server) {
		if (!needsWrite || TerrainCache.active() == null) return;   // not an AnaTerra world
		needsWrite = false;
		save(server);
	}

	public static void save(MinecraftServer server) {
		Path f = file(server);
		try {
			Files.writeString(f, GSON.toJson(TerrainCache.settings()));
		} catch (IOException e) {
			AnaTerra.LOGGER.error("Could not write {}", f, e);
		}
	}

	public static TerrainSettings copy(TerrainSettings s) {
		return GSON.fromJson(GSON.toJson(s), TerrainSettings.class);
	}
}
