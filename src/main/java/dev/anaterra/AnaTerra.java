package dev.anaterra;

import dev.anaterra.debug.AnaTerraCommands;
import dev.anaterra.worldgen.ClimateDensityFunction;
import dev.anaterra.worldgen.SettingsStore;
import dev.anaterra.worldgen.SurfaceDensityFunction;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnaTerra implements ModInitializer {
	public static final String MOD_ID = "anaterra";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("surface"), SurfaceDensityFunction.DATA_CODEC);
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("climate"), ClimateDensityFunction.DATA_CODEC);
		SettingsStore.register();
		AnaTerraCommands.register();
		LOGGER.info("AnaTerra ready");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
