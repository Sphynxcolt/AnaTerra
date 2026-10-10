package dev.anaterra.mixin.client;

import dev.anaterra.AnaTerra;
import dev.anaterra.client.AnaTerraSettingsScreen;
import dev.anaterra.worldgen.SettingsStore;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the AnaTerra world type a working "Customize" button in the world creation screen (vanilla only has
 * editors for Superflat and Single Biome).
 */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
	@Unique
	private static final ResourceKey<WorldPreset> ANATERRA = ResourceKey.create(Registries.WORLD_PRESET, AnaTerra.id("anaterra"));

	/** A new world creation screen starts from the default settings, not from the last one. */
	@Inject(method = "<init>", at = @At("RETURN"))
	private void anaterra$freshSettings(CallbackInfo ci) {
		SettingsStore.pending = null;
	}

	@Inject(method = "getPresetEditor", at = @At("RETURN"), cancellable = true)
	private void anaterra$editor(CallbackInfoReturnable<PresetEditor> cir) {
		if (cir.getReturnValue() != null) return;
		Holder<WorldPreset> preset = ((WorldCreationUiState) (Object) this).getWorldType().preset();
		if (preset != null && preset.is(ANATERRA)) {
			cir.setReturnValue((parent, context) -> new AnaTerraSettingsScreen(parent));
		}
	}
}
