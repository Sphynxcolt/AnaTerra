/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.anaterra.terrain;

import static dev.anaterra.terrain.Noise.*;

/**
 * AnaTerra's terrain, column by column. A line-by-line port of the previewer's height shader (v15):
 * continents and sea floor, tectonic plates (ranges, trenches, ridges, island arcs), landforms (badlands,
 * canyons, savanna plateaus, dunes, swamps), the erosion filter, and crease rivers.
 * <p>
 * Pure function of (seed, x, z): no state between columns, safe to call from any thread.
 */
public final class TerrainGenerator {
	private final TerrainSettings s;
	private final int seed;                 // the previewer's uSeed: 32 bits of the world seed
	private final Erosion.Params mainErosion, canyonErosion;

	public TerrainGenerator(TerrainSettings settings, long worldSeed) {
		this.s = settings;
		this.seed = (int) (worldSeed ^ (worldSeed >>> 32));
		mainErosion = new Erosion.Params();
		mainErosion.scale = s.gullyB / TerrainSettings.UNIT; mainErosion.gully = s.gully; mainErosion.detail = s.detail;
		mainErosion.cell = s.cell; mainErosion.norm = s.norm; mainErosion.gain = s.gain; mainErosion.lac = s.lac; mainErosion.oct = s.octaves;
		mainErosion.roundRidge = s.rRidge; mainErosion.roundCrease = s.rCrease; mainErosion.roundInput = s.rInput; mainErosion.roundOct = s.rOct;
		mainErosion.onsetIn = s.onIn; mainErosion.onsetOct = s.onOct; mainErosion.onsetRidgeIn = 2.8; mainErosion.onsetRidgeOct = 1.5;
		mainErosion.fineGain = s.fineGain; mainErosion.assumedSlope = s.aSlope; mainErosion.assumedWeight = s.aWeight; mainErosion.salt = seed;
		canyonErosion = new Erosion.Params();
		canyonErosion.scale = s.canyonSize / TerrainSettings.UNIT; canyonErosion.strength = 0.55; canyonErosion.gully = 0.85;
		canyonErosion.detail = 2.2; canyonErosion.cell = 0.8; canyonErosion.norm = 0.6; canyonErosion.gain = 0.55; canyonErosion.lac = 2.0;
		canyonErosion.oct = 4; canyonErosion.roundRidge = 0.0; canyonErosion.roundCrease = 0.7; canyonErosion.roundInput = 0.1;
		canyonErosion.roundOct = 2.0; canyonErosion.onsetIn = 1.25; canyonErosion.onsetOct = 1.25; canyonErosion.onsetRidgeIn = 2.8;
		canyonErosion.onsetRidgeOct = 1.5; canyonErosion.assumedSlope = 0.7; canyonErosion.assumedWeight = 1.0; canyonErosion.salt = seed + 777;
	}

	public TerrainSettings settings() { return s; }

	/** Everything known about one column. Heights are relative to sea level. */
	public static final class Column {
		public double height;          // final terrain height (sea level = 0)
		public double C, m, mf, T, H, W; // continentalness, mountain mask, peak shape, temperature, humidity, weirdness
		public double hot, warm, badlands, eroded, wooded, dunes, swamp, lift;
		public double riverEdge = 1e9, riverSurf = -1e9, riverW;   // channel: edge < 0 inside; surf = water level
		public boolean inRiver() { return riverEdge < 0 && riverSurf > 0 && height < riverSurf; }
	}

	// ---------------------------------------------------------------------------------------------
	// Continentalness -> base elevation (vanilla's offset spline in spirit)
	private static final double[] KC = {-1.0, -0.55, -0.35, -0.20, -0.09, -0.03, 0.02, 0.25, 0.50, 1.0};
	private static final double[] KY = {-150.0, -115.0, -50.0, -32.0, -12.0, -3.0, 4.0, 16.0, 32.0, 58.0};

	static double elev(double c) {
		if (c <= KC[0]) return KY[0];
		for (int i = 1; i < KC.length; i++) {
			if (c < KC[i]) {
				double t = (c - KC[i - 1]) / (KC[i] - KC[i - 1]);
				t = t * t * (3.0 - 2.0 * t);
				return mix(KY[i - 1], KY[i], t);
			}
		}
		return KY[KY.length - 1];
	}

	private static final class Info {
		double C, m, mf, T, H, W, hot, warm, badlands, eroded, wooded, dunes, swamp, drop, lift;
	}

	// ---------------------------------------------------------------------------------------------
	// Tectonic plates
	private static final class Plate {
		double tec, conv, db = 1e9, ang, trench, ridge, rift, arc;
	}

	private double plateCenterX(int cx, int cz) { return (cx + 0.5 + hash2x(cx, cz, seed + 9001) * 0.38) * s.plate; }
	private double plateCenterZ(int cx, int cz) { return (cz + 0.5 + hash2y(cx, cz, seed + 9001) * 0.38) * s.plate; }

	/** xy: velocity, z: turning speed. */
	private void plateMotion(int cx, int cz, double[] out) {
		long h = Noise.hashRaw(cx, cz, seed + 9101);
		double ang = Noise.hx(h) * 3.14159265, spd = 0.55 + 0.45 * Noise.hy(h);
		out[0] = Math.cos(ang) * spd; out[1] = Math.sin(ang) * spd;
		out[2] = hash2x(cx, cz, seed + 9201) * s.plateSpin * 1.6;
	}

	private Plate plates(double bx, double bz, boolean ocean) {
		double wsc = s.plate * 0.8;
		double qx = bx + s.plateWarp * s.plate * 0.3 * fbm(bx / wsc, bz / wsc, 3, 0.5, seed + 31);
		double qz = bz + s.plateWarp * s.plate * 0.3 * fbm(bx / wsc + 5.2, bz / wsc + 1.3, 3, 0.5, seed + 32);
		int cix = (int) Math.floor(qx / s.plate), ciz = (int) Math.floor(qz / s.plate);
		double best = 1e20; int cax = cix, caz = ciz;
		for (int j = 0; j < 3; j++) for (int i = 0; i < 3; i++) {
			int cx = cix + i - 1, cz = ciz + j - 1;
			double px = plateCenterX(cx, cz), pz = plateCenterZ(cx, cz);
			double d = (qx - px) * (qx - px) + (qz - pz) * (qz - pz);
			if (d < best) { best = d; cax = cx; caz = cz; }
		}
		final int N = 5, h = 2, nn = 25;
		double[] PX = new double[nn], PZ = new double[nn], PD = new double[nn];
		for (int k = 0; k < nn; k++) {
			int cx = cax + (k % N) - h, cz = caz + (k / N) - h;
			PX[k] = plateCenterX(cx, cz); PZ[k] = plateCenterZ(cx, cz);
			PD[k] = Math.hypot(qx - PX[k], qz - PZ[k]);
		}
		double[] ma = new double[3], mA = new double[3], mB = new double[3];
		plateMotion(cax, caz, ma);
		Plate P = new Plate();
		P.ang = fract(Math.atan2(ma[1], ma[0]) / 6.2831853 + 0.5);
		double lim = Math.sqrt(best) + 6.0 * s.plateW, soft = 0.35 * s.plateW;
		for (int a = 0; a < nn; a++) {
			if (PD[a] > lim) continue;
			for (int c = a + 1; c < nn; c++) {
				if (PD[c] > lim) continue;
				double pAx = PX[a], pAz = PZ[a], pBx = PX[c], pBz = PZ[c];
				double nl = Math.hypot(pBx - pAx, pBz - pAz);
				double nx = (pBx - pAx) / nl, nz = (pBz - pAz) / nl;
				double sd = (0.5 * (pAx + pBx) - qx) * nx + (0.5 * (pAz + pBz) - qz) * nz;   // signed distance to the bisector
				double d = Math.abs(sd);
				if (d > 3.0 * s.plateW) continue;
				double fx = qx + nx * sd, fz = qz + nz * sd;
				double da = Math.hypot(fx - pAx, fz - pAz), margin = 1e9;
				for (int k = 0; k < nn; k++) {
					if (k == a || k == c) continue;
					margin = Math.min(margin, Math.hypot(fx - PX[k], fz - PZ[k]) - da);
					if (margin < -soft) break;
				}
				double wgt = smoothstep(-soft, soft, margin);
				if (wgt <= 0.0) continue;
				int cAx = cax + (a % N) - h, cAz = caz + (a / N) - h, cBx = cax + (c % N) - h, cBz = caz + (c / N) - h;
				plateMotion(cAx, cAz, mA); plateMotion(cBx, cBz, mB);
				double rAx = (fx - pAx) / s.plate, rAz = (fz - pAz) / s.plate, rBx = (fx - pBx) / s.plate, rBz = (fz - pBz) / s.plate;
				double vAx = mA[0] - mA[2] * rAz, vAz = mA[1] + mA[2] * rAx;
				double vBx = mB[0] - mB[2] * rBz, vBz = mB[1] + mB[2] * rBx;
				double conv = ((vAx - vBx) * nx + (vAz - vBz) * nz) / 1.2;
				double vsc = s.plate * 0.2;
				double vr = fbm(fx / vsc, fz / vsc, 3, 0.5, seed + 33);
				double hMod = Math.min(1.0, mix(1.0, 0.45 + 0.7 * smoothstep(-0.55, 0.35, vr), s.ridgeVar));
				double wMod = 1.0 + 0.35 * vr * s.ridgeVar;
				double push = smoothstep(0.05, 0.75, conv + s.plateBias);
				double dd = d / (s.plateW * wMod);
				double t = push * hMod * wgt * Math.exp(-dd * dd);
				P.tec = Math.max(P.tec, t);
				if (d < P.db && wgt > 0.3) { P.db = d; P.conv = conv; }
				if (!ocean) continue;
				// pulling apart: mid-ocean ridge with a rift
				double pull = smoothstep(0.05, 0.75, -conv - s.plateBias);
				double r1 = d / (s.plateW * 2.2), r2 = d / (s.plateW * 0.12);
				P.ridge = Math.max(P.ridge, pull * wgt * Math.exp(-r1 * r1));
				P.rift = Math.max(P.rift, pull * wgt * Math.exp(-r2 * r2));
				// colliding: trench on the diving plate's side, island arc behind it on the other side
				boolean aDives = hash2x(cAx, cAz, seed + 9301) > hash2x(cBx, cBz, seed + 9301);
				double sx = aDives ? sd : -sd;
				double wsc2 = s.plateW * 1.6;
				double wig = Noise.noised(fx / wsc2, fz / wsc2, seed + 35, null) * 1.6;
				double sT = sx - (0.35 + 0.35 * vr + 0.35 * wig) * s.plateW;
				double wT = smoothstep(-soft, 4.0 * s.plateW, margin);
				double deep = mix(0.5, 1.15, smoothstep(-0.6, 0.6, vr));
				double dead = hash2x(cAx + cBx * 7, cAz + cBz * 7, seed + 9401) > 0.72 ? 0.3 * smoothstep(0.2, 0.7, vr) : 0.0;
				P.trench = Math.max(P.trench, push * wT * (deep + dead) * Math.exp(-Math.pow(Math.abs(sT) / (s.plateW * (0.24 + 0.1 * wig)), 2.4)));
				double sa = (sx + 1.4 * s.plateW) / (s.plateW * 0.45);
				P.arc = Math.max(P.arc, push * wT * Math.exp(-sa * sa));
			}
		}
		return P;
	}

	// ---------------------------------------------------------------------------------------------
	// Landforms

	/** Canyon field for Eroded Badlands: 0 (plateau) .. 1 (canyon floor). */
	private double canyonCut(double bx, double bz, double amount) {
		double syn = s.canyonSize * 4.0;
		double[] n = new double[3];
		fbmd(bx / syn, bz / syn, 3, 0.5, seed + 4242, n);
		double nl = Math.hypot(n[1], n[2]);
		double dx = nl > 1e-10 ? n[1] / nl : 0, dz = nl > 1e-10 ? n[2] / nl : 0;
		double[] out = new double[5];
		Erosion.filter(bx / TerrainSettings.UNIT, bz / TerrainSettings.UNIT, n[0] * 0.05, dx * 0.8, dz * 0.8,
			clamp(n[0] / 0.6, -1.0, 1.0), canyonErosion, out);
		double g = out[0] / Math.max(out[3], 1e-6);
		double t = mix(-1.6, mix(-0.55, 0.55, s.canyonWidth), amount);
		double soft = mix(0.32, 0.025, s.cliff);
		double cut = 1.0 - smoothstep(t - soft, t + soft, g);
		double steps = Math.max(1.0, s.mesaH / Math.max(s.terrace, 1.0));
		if (s.terrace > 0.5) { double q = cut * steps; cut = (Math.floor(q) + smoothstep(0.3, 0.7, fract(q))) / steps; }
		return cut;
	}

	private double duneProfile(double bx, double bz) {
		double a = s.wind * 0.0174533;
		double wdx = Math.cos(a), wdz = Math.sin(a), pdx = -wdz, pdz = wdx;
		double ws = s.duneL * 4.0;
		double warp = fbm(bx / ws, bz / ws, 2, 0.5, seed + 515) * 1.3;
		double u = (bx * wdx + bz * wdz) / s.duneL + warp;
		double crest = 0.55 + 0.45 * fbm((bx * pdx + bz * pdz) / (s.duneL * 2.5), u * 0.15, 2, 0.5, seed + 616);
		double sv = fract(u);
		double prof = sv < 0.78 ? Math.pow(sv / 0.78, 1.4) : Math.pow(1.0 - (sv - 0.78) / 0.22, 2.0);
		return prof * clamp(crest, 0.0, 1.0);
	}

	// ---------------------------------------------------------------------------------------------
	// Base height (before erosion and rivers)
	private double baseY(double bx, double bz, boolean withCut, Info I) {
		double qx = bx / (s.contSize * 0.6), qz = bz / (s.contSize * 0.6);
		double wx = fbm(qx, qz, 3, 0.5, seed + 101), wz = fbm(qx + 31.7, qz + 11.3, 3, 0.5, seed + 202);
		double C = fbm((bx + wx * s.contSize * 0.3) / s.contSize, (bz + wz * s.contSize * 0.3) / s.contSize, 6, 0.5, seed + 11) * 1.6 + s.land;
		double y = elev(C);
		if (C < 0.02) {                                                // sea floor: shelf, slope, abyssal plain, seamounts
			double t = -C;
			double floorY = -3.0 - 37.0 * smoothstep(0.03, 0.10, t) - (0.85 * s.oceanDepth - 40.0) * smoothstep(0.10, 0.30, t)
				- 0.15 * s.oceanDepth * smoothstep(0.30, 0.9, t);
			floorY += smoothstep(0.55, 0.95, fbm(bx / 3500.0, bz / 3500.0, 3, 0.5, seed + 61)) * 0.6 * s.oceanDepth * smoothstep(0.2, 0.4, t);
			y = mix(floorY, y, smoothstep(-0.03, 0.02, C));
		}
		double land = smoothstep(0.0, 0.14, C);
		double seafloor = smoothstep(-8.0, -24.0, y) * 0.35;
		double hills = fbm(bx / s.hillSize, bz / s.hillSize, 3, 0.5, seed + 33);
		double mmNoise = fbm(bx / s.mtnSize, bz / s.mtnSize, 3, 0.5, seed + 55);
		double mm = mmNoise + s.mtnCover + C * 0.25;
		Plate PL = null;
		if (s.tect > 0.001) {
			PL = plates(bx, bz, C < 0.02);
			double mmT = PL.tec * 1.1 - 0.2 + s.mtnCover * 0.6 + mmNoise * 0.14 + C * 0.25;
			mm = mix(mm, mmT, s.tect);
		}
		double m = smoothstep(-0.1, 0.45, mm) * smoothstep(-0.12, 0.15, C);
		double sea = (1.0 - smoothstep(-0.06, 0.02, C)) * s.tect;
		if (PL != null && sea > 0.001 && PL.trench + PL.ridge + PL.rift + PL.arc > 0.002) {
			double rough = 1.0 + 0.22 * fbm(bx / 160.0, bz / 160.0, 4, 0.6, seed + 63);
			double yT = -s.trenchDepth * PL.trench * rough;
			y += Math.min(0.0, yT - y) * smoothstep(0.05, 0.35, PL.trench) * sea;
			y += sea * (PL.ridge * 0.45 * s.oceanDepth - PL.rift * 70.0);
			double cs = s.plateW * 0.35;
			double cone = smoothstep(0.1, 0.8, fbm(bx / cs, bz / cs, 3, 0.5, seed + 62));
			y += sea * PL.arc * (0.3 * s.oceanDepth + cone * (0.9 * s.oceanDepth + 90.0));
		}
		double mf = fbm(bx / s.peak, bz / s.peak, 3, 0.28, seed + 77);
		double shape = Math.pow(clamp(mf * 0.5 + 0.55, 0.0, 1.0), 1.5);
		double T = clamp(fbm(bx / (s.contSize * 0.9), bz / (s.contSize * 0.9), 3, 0.5, seed + 99) * 1.3, -1.0, 1.0);
		double H = clamp(fbm(bx / (s.contSize * 0.7), bz / (s.contSize * 0.7), 3, 0.5, seed + 123) * 1.3, -1.0, 1.0);
		double W = fbm(bx / (s.contSize * 0.22), bz / (s.contSize * 0.22), 3, 0.5, seed + 321);
		double mtnTerm = (shape * 0.82 + 0.18) * s.mtnH * m * m * (3.0 - 2.0 * m);

		I.C = C; I.m = m; I.mf = mf; I.T = T; I.H = H; I.W = W;
		I.hot = 0; I.warm = 0; I.badlands = 0; I.eroded = 0; I.wooded = 0; I.dunes = 0; I.swamp = 0; I.drop = 0; I.lift = 0;

		if (!s.landforms) { y += hills * s.hillH * Math.max(land, seafloor) + mtnTerm; I.lift = mtnTerm; return y; }

		double hot = smoothstep(0.5, 0.6, T);
		double warm = smoothstep(0.15, 0.25, T) * (1.0 - hot);
		I.hot = hot; I.warm = warm;

		double inland = smoothstep(0.03, 0.42, C);
		double bad = hot * smoothstep(0.15, 0.4, m) * smoothstep(0.03, 0.30, C);
		double eroded = smoothstep(0.05, -0.05, W) * smoothstep(0.15, 0.0, H);
		double wooded = smoothstep(0.2, 0.32, H);
		double mesa = s.mesaH * bad * mix(0.55, 1.0, Math.max(eroded, wooded));
		double canyonAmt = mix(0.3, 1.0, eroded) * (1.0 - wooded);
		if (withCut && bad > 0.01 && canyonAmt > 0.01) I.drop = mesa * canyonCut(bx, bz, canyonAmt);
		I.badlands = bad; I.eroded = eroded; I.wooded = wooded;

		double plateau = (s.platH * smoothstep(0.12, 0.32, m) * (0.85 + 0.15 * smoothstep(0.2, 0.6, shape))
			+ smoothstep(0.72, 1.0, shape) * s.mtnH * 0.55 * m) * inland;
		double rough = mix(mtnTerm, plateau, warm) * (1.0 - hot) + mtnTerm * 0.25 * hot * (1.0 - bad);

		double dunes = hot * (1.0 - smoothstep(0.1, 0.25, m)) * smoothstep(0.03, 0.12, C)
			* smoothstep(-0.25, 0.15, fbm(bx / 2200.0, bz / 2200.0, 2, 0.5, seed + 717));
		I.dunes = dunes;

		double hillAmt = hills * s.hillH * Math.max(land, seafloor) * mix(1.0, 0.4, dunes) * mix(1.0, 1.6, bad * (1.0 - eroded));
		y += hillAmt + rough + mesa + (dunes > 0 ? dunes * s.duneH * duneProfile(bx, bz) : 0.0);
		I.lift = rough + mesa;

		double sw = (1.0 - smoothstep(0.05, 0.25, m)) * smoothstep(0.0, 0.06, C) * (1.0 - smoothstep(0.32, 0.45, C))
			* smoothstep(0.12, 0.48, fbm(bx / 2600.0, bz / 2600.0, 3, 0.5, seed + 818) + s.swamp)
			* smoothstep(-0.12, 0.08, H) * smoothstep(-0.45, -0.3, T) * (1.0 - dunes);
		if (sw > 0) y = mix(y, 0.35 + fbm(bx / 55.0, bz / 55.0, 2, 0.5, seed + 919) * 2.8, sw);
		I.drop *= 1.0 - sw; I.lift *= 1.0 - sw;
		I.swamp = sw;
		return y;
	}

	// ---------------------------------------------------------------------------------------------
	// Rivers from creases
	private double ladder(double E) {
		double k = Math.floor(E / s.rivStep);
		double bk = (k + (fract(k * 0.618034 + 0.31) - 0.5) * 0.6) * s.rivStep;
		if (E < bk) { k -= 1.0; bk = (k + (fract(k * 0.618034 + 0.31) - 0.5) * 0.6) * s.rivStep; }
		return Math.floor(bk);
	}

	/** Writes edge, surf, width, floor into r[0..3]. */
	private void rivers(double bx, double bz, double yS, double gx, double gz, double gSx, double gSz, double m, double H, double[] r) {
		r[0] = 1e9; r[1] = -1e9; r[2] = 0; r[3] = 1e9;
		if (!s.rivers) return;
		double slope = Math.hypot(gx, gz);
		double gl = Math.hypot(gSx, gSz);
		double nSx = gl > 1e-10 ? gSx / gl : 0, nSz = gl > 1e-10 ? gSz / gl : 0;
		double bend = smoothstep(0.02, 0.25, slope);
		double dxr = nSx * 0.04 + gx * bend, dzr = nSz * 0.04 + gz * bend;
		double dl = Math.hypot(dxr, dzr);
		double dirx = dl > 1e-10 ? dxr / dl : 0, dirz = dl > 1e-10 ? dzr / dl : 0;
		double steep = 1.0 - smoothstep(0.3, 0.75, slope);
		double landF = smoothstep(-6.0, 3.0, yS);
		double inc = 1.0 + 14.0 * m;
		double flood0 = mix(2.5, 0.4, m), vslope = mix(0.18, 1.0, m);
		double[] ph = new double[4];
		for (int L = 0; L < 2; L++) {
			double S = L == 0 ? s.rivSpace : s.rivSpace / 3.0;
			int salt = seed * 0x2C1B3C6D + L * 0x297A2D39 + 4111;
			Erosion.phacelle(bx / (S * 0.7), bz / (S * 0.7), dirx, dirz, 0.7, 0.25, 1.0, salt, ph);
			double d = (3.14159265 - Math.acos(clamp(ph[0], -1.0, 1.0))) * S / 6.2831853;
			double mask = fbm(bx / (S * 2.2), bz / (S * 2.2), 2, 0.5, salt + 7) * 0.9 + 0.35 * H + (s.rivDensity - 0.5) * 1.2
				+ (L == 0 ? 0.1 - 0.3 * m : 0.15 * m);
			double w = smoothstep(0.0, 0.35, mask) * steep * landF * (L == 0 ? s.rivW * mix(1.0, 0.45, m) : Math.min(18.0, 0.3 * s.rivW));
			double lvl = yS - inc - 0.04 * w, surf = ladder(lvl);
			double e = d - 0.5 * w, flood = w * flood0;
			double vy = Math.min(lvl + 1.5 + Math.max(0.0, e - flood) * vslope, surf + 1.0 + Math.max(e, 0.0) * 0.5)
				+ (1.0 - smoothstep(1.0, 5.0, w)) * 400.0;
			r[3] = Math.min(r[3], vy);
			if (w > 1.0 && e < r[0]) { r[0] = e; r[1] = surf; r[2] = w; }
		}
	}

	// ---------------------------------------------------------------------------------------------
	/** The full column: base height at three points (for the slope), erosion, rivers, canyons. */
	public Column column(double bx, double bz) {
		final double E = 4.0;
		Info info = new Info(), tmp = new Info();
		double y0 = baseY(bx, bz, true, info);
		double s0 = elev(info.C) + info.m * s.mtnH * 0.6;
		double yX = baseY(bx + E, bz, false, tmp);
		double sX = elev(tmp.C) + tmp.m * s.mtnH * 0.6;
		double yZ = baseY(bx, bz + E, false, tmp);
		double sZ = elev(tmp.C) + tmp.m * s.mtnH * 0.6;
		double gx = (yX - y0) / E, gz = (yZ - y0) / E;

		double y = y0, yS = y0;
		if (s.erode) {
			double m = info.m;
			double fade = clamp(((info.mf * 0.5 + 0.55) * 2.0 - 1.0) / 0.6, -1.0, 1.0) * m * (1.0 - info.hot);
			double underwater = smoothstep(-60.0, -5.0, y0);
			double strength = s.strength * mix(s.lowEro, 1.0, m) * underwater
				* (1.0 - info.dunes) * (1.0 - info.swamp) * mix(1.0, 0.3, info.badlands)
				* mix(1.0, 0.45, info.warm * (1.0 - smoothstep(0.65, 0.85, info.mf * 0.5 + 0.55)));
			double hs0 = y0 / TerrainSettings.UNIT;
			double offset = mix(s.hOff, -fade, s.hOffFade);
			if (strength > 0) {
				double[] out = new double[5];
				filterWith(bx / TerrainSettings.UNIT, bz / TerrainSettings.UNIT, hs0, gx, gz, fade, strength, out);
				y = (hs0 + out[0] + offset * out[3]) * TerrainSettings.UNIT;
				yS = (hs0 + offset * out[3]) * TerrainSettings.UNIT;
			}
		}

		if (s.rock && s.rockH > 0) {
			double slope = Math.sqrt(gx * gx + gz * gz);
			double mask = smoothstep(0.08, 0.5, info.m)
				* mix(1.0, smoothstep(0.12, 0.9, slope), s.rockSlope)
				* smoothstep(6.0, 40.0, y)
				* (1.0 - info.dunes) * (1.0 - info.swamp) * (1.0 - 0.6 * info.badlands);
			if (mask > 0.001) y += s.rockH * mix(0.5, 1.0, info.m) * mask * rock(bx, bz);
		}

		double[] rv = new double[4];
		rivers(bx, bz, yS, gx, gz, (sX - s0) / E, (sZ - s0) / E, info.m * (1.0 - info.badlands), info.H, rv);
		if (rv[3] < 1e8) {
			double hk = 6.0, hh = clamp(0.5 + 0.5 * (rv[3] - y) / hk, 0.0, 1.0);
			y = Math.max(mix(rv[3], y, hh) - hk * hh * (1.0 - hh), Math.min(y, rv[3]) - 0.5);
			if (rv[0] < 0.0) {
				double r = clamp((rv[0] + 0.5 * rv[2]) / (0.5 * rv[2]), 0.0, 1.0);
				y = Math.min(y, rv[1] - Math.max(1.0, (1.5 + 0.08 * rv[2]) * (1.0 - r * r)));
			}
		}
		y -= info.drop * smoothstep(0.0, 40.0, rv[0]);

		Column col = new Column();
		col.height = y;
		col.C = info.C; col.m = info.m; col.mf = info.mf; col.T = info.T; col.H = info.H; col.W = info.W;
		col.hot = info.hot; col.warm = info.warm; col.badlands = info.badlands; col.eroded = info.eroded; col.wooded = info.wooded;
		col.dunes = info.dunes; col.swamp = info.swamp; col.lift = info.lift;
		col.riverEdge = rv[0]; col.riverSurf = rv[1]; col.riverW = rv[2];
		return col;
	}

	/**
	 * Rock detail: domain-warped ridged multifractal, roughly -1..1. Sharp crests where the ridges meet, rounded gullies
	 * between them; each octave only shows on the crests of the one before, so the crags cluster like real outcrops.
	 */
	private double rock(double bx, double bz) {
		double px = bx / s.rockSize, pz = bz / s.rockSize;
		px += Noise.fbm(px * 0.45 + 3.7, pz * 0.45 - 1.3, 2, 0.5, seed + 901) * 0.35;
		pz += Noise.fbm(px * 0.45 - 5.1, pz * 0.45 + 2.9, 2, 0.5, seed + 902) * 0.35;
		double sharp = 1.0 + 2.0 * s.rockSharp;
		double sum = 0, amp = 1, norm = 0, prev = 1;
		for (int i = 0; i < s.rockOct; i++) {
			double n = Noise.noised(px, pz, seed + 1201 + i * 0x9E37, null);
			double r = clamp(1.0 - Math.abs(n) * 1.6, 0.0, 1.0);
			r = Math.pow(r, sharp);
			double w = clamp(prev * 1.5, 0.0, 1.0);
			sum += r * w * amp;
			norm += amp;
			prev = r;
			amp *= 0.5;
			px = px * 2.03 + 11.7; pz = pz * 2.03 - 6.3;
		}
		return (sum / norm) * 2.6 - 1.25;
	}

	/** Runs the main erosion filter with a per-column strength without mutating shared state. */
	private void filterWith(double px, double pz, double h0, double gx, double gz, double fade, double strength, double[] out) {
		Erosion.Params p = new Erosion.Params();
		Erosion.Params q = mainErosion;
		p.scale = q.scale; p.strength = strength; p.gully = q.gully; p.detail = q.detail; p.cell = q.cell; p.norm = q.norm;
		p.gain = q.gain; p.lac = q.lac; p.oct = q.oct; p.roundRidge = q.roundRidge; p.roundCrease = q.roundCrease;
		p.roundInput = q.roundInput; p.roundOct = q.roundOct; p.onsetIn = q.onsetIn; p.onsetOct = q.onsetOct;
		p.onsetRidgeIn = q.onsetRidgeIn; p.onsetRidgeOct = q.onsetRidgeOct; p.assumedSlope = q.assumedSlope;
		p.assumedWeight = q.assumedWeight; p.salt = q.salt;
		p.fineGain = q.fineGain; p.fineFrom = q.fineFrom;
		Erosion.filter(px, pz, h0, gx, gz, fade, p, out);
	}
}
