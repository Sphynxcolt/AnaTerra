package dev.anaterra.client;

import dev.anaterra.terrain.TerrainSettings;
import dev.anaterra.worldgen.SettingsStore;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The "Customize" screen for the AnaTerra world type. A first, simple version: the most useful terrain settings as
 * sliders, on a few pages. The values go into the new world's {@code anaterra.json} when it is created.
 */
public class AnaTerraSettingsScreen extends Screen {
	private final Screen parent;
	private final TerrainSettings edit;
	private int page = 0;

	public AnaTerraSettingsScreen(Screen parent) {
		super(Component.literal("AnaTerra terrain"));
		this.parent = parent;
		TerrainSettings start = SettingsStore.pending;
		this.edit = SettingsStore.copy(start != null ? start : new TerrainSettings());
	}

	// ---- what can be edited -------------------------------------------------------------------------------------------

	private record Num(String label, String help, double min, double max, double step, Supplier<Double> get, Consumer<Double> set) {}
	private record Toggle(String label, String help, Supplier<Boolean> get, Consumer<Boolean> set) {}
	private record Page(String name, List<Object> entries) {}

	private List<Page> pages() {
		TerrainSettings s = edit;
		return List.of(
			new Page("Continents & oceans", List.of(
				new Num("Continent size", "Typical size of continents and oceans, in blocks", 4000, 60000, 500, () -> s.contSize, v -> s.contSize = v),
				new Num("Land amount", "More land (higher) or more ocean (lower)", -0.5, 0.5, 0.01, () -> s.land, v -> s.land = v),
				new Num("Hill height", "Height of the rolling hills on flat land", 0, 80, 1, () -> s.hillH, v -> s.hillH = v),
				new Num("Hill size", "Spacing of the rolling hills, in blocks", 100, 2000, 10, () -> s.hillSize, v -> s.hillSize = v),
				new Num("Ocean depth", "Depth of the open ocean floor below sea level", 50, 1100, 10, () -> s.oceanDepth, v -> s.oceanDepth = v),
				new Num("Trench depth", "Extra depth of the trenches at plate edges", 0, 1250, 10, () -> s.trenchDepth, v -> s.trenchDepth = v))),
			new Page("Mountains & plates", List.of(
				new Num("Mountain height", "Height of the mountain belts (the world top is y 1023)", 0, 900, 10, () -> s.mtnH, v -> s.mtnH = v),
				new Num("Peak spacing", "Size of the pattern that picks sharp peaks vs. rounded ones, in blocks", 300, 5000, 50, () -> s.peak, v -> s.peak = v),
				new Num("Mountain cover", "How much land is mountainous", -0.6, 0.6, 0.01, () -> s.mtnCover, v -> s.mtnCover = v),
				new Num("Tectonics", "How strongly plate edges raise ranges and cut trenches", 0, 1.5, 0.05, () -> s.tect, v -> s.tect = v),
				new Num("Plate size", "Typical size of a tectonic plate, in blocks", 8000, 60000, 500, () -> s.plate, v -> s.plate = v),
				new Num("Range width", "Width of the ranges along plate edges, in blocks", 400, 5000, 50, () -> s.plateW, v -> s.plateW = v),
				new Num("Ridge variety", "How much the height changes along a range", 0, 1, 0.05, () -> s.ridgeVar, v -> s.ridgeVar = v))),
			new Page("Landforms", List.of(
				new Toggle("Landforms", "Badlands canyons, plateaus, dunes", () -> s.landforms, v -> s.landforms = v),
				new Num("Mesa height", "Height of the badlands mesas", 0, 300, 5, () -> s.mesaH, v -> s.mesaH = v),
				new Num("Canyon size", "Spacing of the badlands canyons", 150, 1500, 10, () -> s.canyonSize, v -> s.canyonSize = v),
				new Num("Canyon width", "How wide the canyons are", 0.1, 0.9, 0.01, () -> s.canyonWidth, v -> s.canyonWidth = v),
				new Num("Plateau height", "Height of the savanna plateaus", 0, 250, 5, () -> s.platH, v -> s.platH = v),
				new Num("Dune height", "Height of the desert dunes", 0, 40, 1, () -> s.duneH, v -> s.duneH = v),
				new Num("Swamps", "Extra swamp land", 0, 1, 0.05, () -> s.swamp, v -> s.swamp = v))),
			new Page("Rivers", List.of(
				new Toggle("Rivers", "Carved rivers with water", () -> s.rivers, v -> s.rivers = v),
				new Num("River spacing", "Distance between rivers, in blocks", 800, 8000, 50, () -> s.rivSpace, v -> s.rivSpace = v),
				new Num("River amount", "How many of the possible rivers appear", 0, 1, 0.05, () -> s.rivDensity, v -> s.rivDensity = v),
				new Num("River width", "Width of big rivers, in blocks", 8, 128, 1, () -> s.rivW, v -> s.rivW = v),
				new Num("River steps", "Height of the steps (small falls) along rivers", 1, 12, 1, () -> s.rivStep, v -> s.rivStep = v))),
			new Page("Erosion", List.of(
				new Toggle("Erosion", "The erosion filter (gullies and ridges)", () -> s.erode, v -> s.erode = v),
				new Num("Strength", "Depth of the gullies and height of the ridges", 0, 0.6, 0.01, () -> s.strength, v -> s.strength = v),
				new Num("Gully size", "Size of the largest gullies, in blocks", 150, 2000, 10, () -> s.gullyB, v -> s.gullyB = v),
				new Num("Octaves", "Layers of smaller and smaller gullies. Each layer is half the size of the one before. "
					+ "More layers = finer detail, slightly slower", 1, 9, 1, () -> (double) s.octaves, v -> s.octaves = (int) Math.round(v)),
				new Num("Fine gullies", "Strength of the small gullies (layers 5 and up) compared with the big ones. "
					+ "0.5 = the previewer's look, higher = rougher mountains up close", 0.3, 0.9, 0.01, () -> s.fineGain, v -> s.fineGain = v),
				new Num("Layer strength", "How strong each layer is compared with the one before (all layers)", 0.3, 0.7, 0.01, () -> s.gain, v -> s.gain = v),
				new Num("Detail", "How far the small layers reach into gentle slopes", 0.5, 4, 0.05, () -> s.detail, v -> s.detail = v),
				new Num("Gully shape", "Low = shallow ripples, high = deep V-shaped gullies", 0, 1, 0.01, () -> s.gully, v -> s.gully = v),
				new Num("Ridge rounding", "Rounds off the sharp ridge crests", 0, 1, 0.01, () -> s.rRidge, v -> s.rRidge = v),
				new Num("Valley rounding", "Rounds off the bottoms of the gullies", 0, 1, 0.01, () -> s.rCrease, v -> s.rCrease = v),
				new Num("Lowland erosion", "Erosion strength on flat land, compared with mountains", 0, 1, 0.01, () -> s.lowEro, v -> s.lowEro = v),
				new Num("Cell size", "Spacing of gullies compared with their size: low = dense, high = sparse", 0.4, 1.2, 0.01, () -> s.cell, v -> s.cell = v))),
			new Page("Rock detail", List.of(
				new Toggle("Rock detail", "Small crags and ribs on steep mountain slopes, finer than the erosion", () -> s.rock, v -> s.rock = v),
				new Num("Rock height", "Height of the crags, in blocks", 0, 60, 1, () -> s.rockH, v -> s.rockH = v),
				new Num("Rock size", "Size of the largest crags, in blocks", 20, 400, 5, () -> s.rockSize, v -> s.rockSize = v),
				new Num("Rock layers", "Layers of smaller crags", 1, 7, 1, () -> (double) s.rockOct, v -> s.rockOct = (int) Math.round(v)),
				new Num("Sharpness", "Low = rounded knolls, high = knife-edge ribs", 0, 1, 0.01, () -> s.rockSharp, v -> s.rockSharp = v),
				new Num("Only on steep slopes", "0 = everywhere in the mountains, 1 = only on steep slopes", 0, 1, 0.01, () -> s.rockSlope, v -> s.rockSlope = v)))
		);
	}

	// ---- layout -------------------------------------------------------------------------------------------------------

	@Override
	protected void init() {
		List<Page> all = pages();
		page = Math.floorMod(page, all.size());
		Page p = all.get(page);

		StringWidget title = new StringWidget(width, 12, Component.literal("AnaTerra terrain — " + p.name()
			+ "  (" + (page + 1) + "/" + all.size() + ")"), font);
		title.setPosition(0, 12);
		addRenderableWidget(title);

		int colW = 200, gap = 10, rowH = 24;
		int left = width / 2 - colW - gap / 2, right = width / 2 + gap / 2;
		int rows = (p.entries().size() + 1) / 2;
		int top = Math.max(32, (height - 60 - rows * rowH) / 2);
		for (int i = 0; i < p.entries().size(); i++) {
			int x = i < rows ? left : right;
			int y = top + (i % rows) * rowH;
			Object e = p.entries().get(i);
			if (e instanceof Num n) addRenderableWidget(slider(n, x, y, colW));
			else if (e instanceof Toggle t) addRenderableWidget(toggle(t, x, y, colW));
		}

		StringWidget hint = new StringWidget(width, 12,
			Component.literal("Saved into the world when you create it. Use /anaterra probe, map and perf in game."), font);
		hint.setPosition(0, height - 52);
		addRenderableWidget(hint);

		int by = height - 32;
		addRenderableWidget(Button.builder(Component.literal("< Back"), b -> { page--; rebuildWidgets(); })
			.bounds(width / 2 - 205, by, 60, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Next >"), b -> { page++; rebuildWidgets(); })
			.bounds(width / 2 - 140, by, 60, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset all"), b -> { resetAll(); rebuildWidgets(); })
			.bounds(width / 2 - 75, by, 70, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
			.bounds(width / 2 + 5, by, 95, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> { SettingsStore.pending = SettingsStore.copy(edit); onClose(); })
			.bounds(width / 2 + 105, by, 100, 20).build());
	}

	private void resetAll() {
		TerrainSettings d = new TerrainSettings();
		for (var f : TerrainSettings.class.getFields()) {
			if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
			try { f.set(edit, f.get(d)); } catch (IllegalAccessException ignored) {}
		}
	}

	private AbstractSliderButton slider(Num n, int x, int y, int w) {
		double start = (n.get().get() - n.min()) / (n.max() - n.min());
		AbstractSliderButton s = new AbstractSliderButton(x, y, w, 20, Component.empty(), Math.clamp(start, 0.0, 1.0)) {
			{ updateMessage(); }

			private double current() {
				double v = n.min() + value * (n.max() - n.min());
				return Math.round(v / n.step()) * n.step();
			}

			@Override
			protected void updateMessage() {
				double v = current();
				String txt = n.step() >= 1 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.2f", v);
				setMessage(Component.literal(n.label() + ": " + txt));
			}

			@Override
			protected void applyValue() {
				n.set().accept(current());
			}
		};
		s.setTooltip(Tooltip.create(Component.literal(n.help())));
		return s;
	}

	private Button toggle(Toggle t, int x, int y, int w) {
		Button b = Button.builder(Component.literal(t.label() + ": " + (t.get().get() ? "ON" : "OFF")), btn -> {
			t.set().accept(!t.get().get());
			btn.setMessage(Component.literal(t.label() + ": " + (t.get().get() ? "ON" : "OFF")));
		}).bounds(x, y, w, 20).build();
		b.setTooltip(Tooltip.create(Component.literal(t.help())));
		return b;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
