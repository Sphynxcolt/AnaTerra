package dev.anaterra.terrain;

/**
 * All terrain parameters, with the defaults of the browser previewer (v15).
 * Units are blocks unless noted. Heights are relative to sea level.
 * <p>
 * Plain Java with no Minecraft dependencies, so the terrain code can be tested outside the game.
 */
public final class TerrainSettings {
	// World
	public int seaLevel = 63;

	// Continents
	public double contSize = 22000, land = -0.04, hillH = 22, hillSize = 650;

	// Oceans
	public double oceanDepth = 350, trenchDepth = 900;

	// Mountains (noise belts; mostly replaced by tectonics)
	public double mtnSize = 7000, mtnCover = -0.05, mtnH = 520, peak = 1500, lowEro = 0.12;

	// Tectonics
	public double tect = 0.9, plate = 26000, plateW = 1800, plateWarp = 0.55, plateSpin = 0.5, plateBias = 0.1, ridgeVar = 0.6;

	// Landforms
	public boolean landforms = true;
	public double mesaH = 110, canyonSize = 520, canyonWidth = 0.45, cliff = 0.55, terrace = 9, platH = 95;
	public double duneH = 14, duneL = 95, wind = 70, swamp = 0;

	// Rivers
	public boolean rivers = true;
	public double rivSpace = 3200, rivDensity = 0.55, rivW = 48, rivStep = 4;

	// Erosion
	public boolean erode = true;
	public double gullyB = 600, strength = 0.22, gully = 0.5, detail = 1.5, gain = 0.5, lac = 2.0;
	public int octaves = 7;
	/** Gain of the erosion octaves after the 4th (the gullies smaller than ~50 blocks). 0.5 = the previewer's look. */
	public double fineGain = 0.7;
	public double rRidge = 0.1, rCrease = 0.0, cell = 0.7, norm = 0.5, aSlope = 0.7, aWeight = 1.0;
	public double rInput = 0.1, rOct = 2.0, onIn = 1.25, onOct = 1.25, hOff = -0.65, hOffFade = 0.9;

	// Rock detail: small ridged crags and gullies on steep mountain slopes, below the erosion filter's finest octave
	public boolean rock = true;
	public double rockH = 20, rockSize = 90, rockSlope = 0.5, rockSharp = 0.6;
	public int rockOct = 5;

	/** Blocks per erosion noise unit (fixed in the previewer). */
	public static final double UNIT = 4096;
}
