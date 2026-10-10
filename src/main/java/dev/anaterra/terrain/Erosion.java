/*
 * Phacelle Noise and Advanced Terrain Erosion Filter
 * Copyright (c) 2025 Rune Skovbo Johansen.
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Ported to Java for AnaTerra from the previewer's GLSL port (seeded integer hash, parameters as an object).
 */
package dev.anaterra.terrain;

import static dev.anaterra.terrain.Noise.*;

public final class Erosion {
	private Erosion() {}

	/** Erosion filter parameters (see the previewer's Erosion section). */
	public static final class Params {
		public double scale, strength, gully, detail, cell, norm, gain, lac;
		/** Gain used from octave {@code fineFrom} on, so the small gullies can be made stronger without touching the big shapes. */
		public double fineGain = -1; public int fineFrom = 4;
		public int oct;
		public double roundRidge, roundCrease, roundInput, roundOct;   // rounding.xyzw
		public double onsetIn, onsetOct, onsetRidgeIn, onsetRidgeOct;  // onset.xyzw
		public double assumedSlope, assumedWeight;
		public int salt;
	}

	/**
	 * Phacelle noise: a stripe pattern along {@code normDir}. Writes (cos, sin) of the phase into out[0..1] and the
	 * side direction scaled by frequency into out[2..3].
	 */
	public static void phacelle(double px, double pz, double ndx, double ndz, double freq, double offset,
	                            double normalization, int salt, double[] out) {
		double sdx = -ndz * freq * 6.28318531, sdz = ndx * freq * 6.28318531;
		offset *= 6.28318531;
		double ipx = Math.floor(px), ipz = Math.floor(pz);
		double fx = px - ipx, fz = pz - ipz;
		int cx = (int) ipx, cz = (int) ipz;
		double phx = 0, phz = 0, wsum = 0;
		for (int i = -1; i <= 2; i++) {
			for (int j = -1; j <= 2; j++) {
				long h = Noise.hashRaw(cx + i, cz + j, salt);
				double vx = fx - i - Noise.hx(h) * 0.5, vz = fz - j - Noise.hy(h) * 0.5;
				double w = Math.max(0.0, Math.exp(-(vx * vx + vz * vz) * 2.0) - 0.01111);
				wsum += w;
				double wi = vx * sdx + vz * sdz + offset;
				phx += Math.cos(wi) * w; phz += Math.sin(wi) * w;
			}
		}
		double ix = phx / Math.max(wsum, 1e-10), iz = phz / Math.max(wsum, 1e-10);
		double mag = Math.max(1.0 - normalization, Math.sqrt(ix * ix + iz * iz));
		out[0] = ix / mag; out[1] = iz / mag; out[2] = sdx; out[3] = sdz;
	}

	static double easeOut(double t) { double v = 1.0 - clamp(t, 0.0, 1.0); return 1.0 - v * v; }
	static double smoothStart(double t, double s) { return t >= s ? t - 0.5 * s : 0.5 * t * t / s; }
	static double powInv(double t, double p) { return 1.0 - Math.pow(1.0 - clamp(t, 0.0, 1.0), p); }

	/**
	 * The erosion filter. {@code hs} = (height, dh/dx, dh/dz) in noise units. Writes the change to hs into
	 * out[0..2], the total magnitude into out[3] and the ridge map (-1 creases .. +1 ridges) into out[4].
	 */
	public static void filter(double px, double pz, double h0, double hdx, double hdz, double fadeTarget, Params P, double[] out) {
		double strength = P.strength * P.scale;
		fadeTarget = clamp(fadeTarget, -1.0, 1.0);
		double hsx = h0, hsy = hdx, hsz = hdz;
		double freq = 1.0 / (P.scale * P.cell);
		double slopeLength = Math.max(Math.sqrt(hdx * hdx + hdz * hdz), 1e-10);
		double magnitude = 0.0, roundingMult = 1.0;
		double roundingForInput = mix(P.roundCrease, P.roundRidge, clamp(fadeTarget + 0.5, 0.0, 1.0)) * P.roundInput;
		double combiMask = easeOut(smoothStart(slopeLength * P.onsetIn, roundingForInput * P.onsetIn));
		double rmMask = easeOut(slopeLength * P.onsetRidgeIn);
		double rmFade = fadeTarget;
		double gsx = mix(hdx, hdx / slopeLength * P.assumedSlope, P.assumedWeight);
		double gsz = mix(hdz, hdz / slopeLength * P.assumedSlope, P.assumedWeight);
		double[] ph = new double[4];
		for (int i = 0; i < P.oct; i++) {
			double gl = Math.sqrt(gsx * gsx + gsz * gsz);
			double ndx = gl > 1e-10 ? gsx / gl : 0.0, ndz = gl > 1e-10 ? gsz / gl : 0.0;
			phacelle(px * freq, pz * freq, ndx, ndz, P.cell, 0.25, P.norm, P.salt * 0x27D4EB2D + i * 0x165667B1 + 1, ph);
			ph[2] *= -freq; ph[3] *= -freq;
			double sloping = Math.abs(ph[1]);
			double sg = Math.signum(ph[1]);
			gsx += sg * ph[2] * strength * P.gully;
			gsz += sg * ph[3] * strength * P.gully;
			double g0 = ph[0], g1 = ph[1] * ph[2], g2 = ph[1] * ph[3];
			double f0 = mix(fadeTarget, g0 * P.gully, combiMask);
			double f1 = mix(0.0, g1 * P.gully, combiMask);
			double f2 = mix(0.0, g2 * P.gully, combiMask);
			hsx += f0 * strength; hsy += f1 * strength; hsz += f2 * strength;
			magnitude += strength;
			fadeTarget = f0;
			double rfo = mix(P.roundCrease, P.roundRidge, clamp(ph[0] + 0.5, 0.0, 1.0)) * roundingMult;
			double newMask = easeOut(smoothStart(sloping * P.onsetOct, rfo * P.onsetOct));
			combiMask = powInv(combiMask, P.detail) * newMask;
			rmFade = mix(rmFade, g0, rmMask);
			rmMask *= easeOut(sloping * P.onsetRidgeOct);
			strength *= (P.fineGain >= 0 && i + 1 >= P.fineFrom) ? P.fineGain : P.gain; freq *= P.lac; roundingMult *= P.roundOct;
		}
		out[0] = hsx - h0; out[1] = hsy - hdx; out[2] = hsz - hdz; out[3] = magnitude;
		out[4] = rmFade * (1.0 - rmMask);
	}
}
