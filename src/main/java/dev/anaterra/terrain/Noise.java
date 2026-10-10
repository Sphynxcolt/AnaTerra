/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package dev.anaterra.terrain;

/**
 * Integer hash and gradient noise, identical to the previewer's shader.
 * Unsigned 32-bit arithmetic is done on Java ints (same bits); shifts are logical (>>>).
 */
public final class Noise {
	private Noise() {}

	private static final double INV_U32 = 2.0 / 4294967295.0;

	/** pcg2d on two 32-bit lanes; returns x in the low and y in the high half. */
	static long pcg2d(int x, int y) {
		x = x * 1664525 + 1013904223;
		y = y * 1664525 + 1013904223;
		x += y * 1664525; y += x * 1664525;
		x ^= x >>> 16; y ^= y >>> 16;
		x += y * 1664525; y += x * 1664525;
		x ^= x >>> 16; y ^= y >>> 16;
		return (x & 0xFFFFFFFFL) | ((long) y << 32);
	}

	static long hashRaw(int cx, int cz, int salt) {
		return pcg2d(cx ^ (salt * 0x9E3779B9), cz ^ (salt * 0x85EBCA6B + 0x632BE5AB));
	}

	/** First component of hash2(), in [-1, 1]. */
	static double hx(long h) { return (h & 0xFFFFFFFFL) * INV_U32 - 1.0; }
	/** Second component of hash2(), in [-1, 1]. */
	static double hy(long h) { return (h >>> 32) * INV_U32 - 1.0; }

	public static double hash2x(int cx, int cz, int salt) { return hx(hashRaw(cx, cz, salt)); }
	public static double hash2y(int cx, int cz, int salt) { return hy(hashRaw(cx, cz, salt)); }

	/**
	 * Gradient noise with analytic derivatives (after Inigo Quilez). Returns the value; if {@code d} is not null,
	 * writes the derivative into d[0], d[1].
	 */
	public static double noised(double px, double pz, int salt, double[] d) {
		double ix = Math.floor(px), iz = Math.floor(pz);
		double fx = px - ix, fz = pz - iz;
		int cx = (int) ix, cz = (int) iz;
		double ux = fx * fx * fx * (fx * (fx * 6.0 - 15.0) + 10.0);
		double uz = fz * fz * fz * (fz * (fz * 6.0 - 15.0) + 10.0);
		long ha = hashRaw(cx, cz, salt), hb = hashRaw(cx + 1, cz, salt), hc = hashRaw(cx, cz + 1, salt), hd = hashRaw(cx + 1, cz + 1, salt);
		double gax = hx(ha), gaz = hy(ha), gbx = hx(hb), gbz = hy(hb), gcx = hx(hc), gcz = hy(hc), gdx = hx(hd), gdz = hy(hd);
		double va = gax * fx + gaz * fz;
		double vb = gbx * (fx - 1.0) + gbz * fz;
		double vc = gcx * fx + gcz * (fz - 1.0);
		double vd = gdx * (fx - 1.0) + gdz * (fz - 1.0);
		double k = va - vb - vc + vd;
		if (d != null) {
			double dux = 30.0 * fx * fx * (fx * (fx - 2.0) + 1.0);
			double duz = 30.0 * fz * fz * (fz * (fz - 2.0) + 1.0);
			d[0] = gax + ux * (gbx - gax) + uz * (gcx - gax) + ux * uz * (gax - gbx - gcx + gdx) + dux * (uz * k + vb - va);
			d[1] = gaz + ux * (gbz - gaz) + uz * (gcz - gaz) + ux * uz * (gaz - gbz - gcz + gdz) + duz * (ux * k + vc - va);
		}
		return va + ux * (vb - va) + uz * (vc - va) + ux * uz * k;
	}

	public static double fbm(double px, double pz, int oct, double gain, int salt) {
		double sum = 0, amp = 1, norm = 0;
		for (int i = 0; i < oct; i++) {
			sum += noised(px, pz, salt + i * 0x9E37, null) * amp;
			norm += amp; amp *= gain;
			px = px * 2.03 + 17.31; pz = pz * 2.03 - 9.17;
		}
		return sum / norm * 1.8;
	}

	/** fbm with gradient: out[0] value, out[1..2] derivative in units of p. */
	public static void fbmd(double px, double pz, int oct, double gain, int salt, double[] out) {
		double s0 = 0, s1 = 0, s2 = 0, amp = 1, norm = 0, sc = 1;
		double[] d = new double[2];
		for (int i = 0; i < oct; i++) {
			double v = noised(px, pz, salt + i * 0x9E37, d);
			s0 += v * amp; s1 += d[0] * sc * amp; s2 += d[1] * sc * amp;
			norm += amp; amp *= gain; sc *= 2.03;
			px = px * 2.03 + 17.31; pz = pz * 2.03 - 9.17;
		}
		out[0] = s0 / norm * 1.8; out[1] = s1 / norm * 1.8; out[2] = s2 / norm * 1.8;
	}

	// GLSL helpers
	public static double clamp(double x, double a, double b) { return x < a ? a : (x > b ? b : x); }
	public static double mix(double a, double b, double t) { return a + (b - a) * t; }
	public static double smoothstep(double e0, double e1, double x) {
		double t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
	public static double fract(double x) { return x - Math.floor(x); }
}
