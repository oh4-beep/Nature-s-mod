package com.naturesmod.worldgen.river;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The course of a mountain river: a polyline of points, each with the water level and channel
 * width at that point. Water levels never rise downstream; where they drop the river forms a
 * cascade or a waterfall.
 */
public final class RiverPath {
	/** Distance between consecutive points, in blocks. */
	static final double STEP = 3.0;
	private static final int MAX_STEPS = 140;
	/** How deep the river may cut through a ridge in its way before it gives up and pools. */
	private static final int MAX_CUT = 12;

	final int[] xs;
	final int[] zs;
	final int[] levels;
	/** Channel half-width times ten. */
	final int[] widths;
	/** True when the river ends in a lake instead of reaching the sea. */
	final boolean lakeEnd;

	RiverPath(int[] xs, int[] zs, int[] levels, int[] widths, boolean lakeEnd) {
		this.xs = xs;
		this.zs = zs;
		this.levels = levels;
		this.widths = widths;
		this.lakeEnd = lakeEnd;
	}

	public int size() {
		return xs.length;
	}

	public double width(int i) {
		return widths[i] / 10.0;
	}

	public int lakeRadius() {
		return lakeEnd ? (int) Math.round(width(size() - 1) + 4) : 0;
	}

	/** Terrain height lookup: y of the topmost solid block. */
	@FunctionalInterface
	public interface HeightSampler {
		int height(int x, int z);
	}

	/**
	 * Follows the land downhill from a mountain spring. Returns null if the result would not make
	 * a worthwhile river.
	 */
	public static RiverPath trace(HeightSampler heights, int startX, int startZ, int sourceHeight, int seaLevel, RandomSource random) {
		double x = startX + 0.5;
		double z = startZ + 0.5;
		double heading = 0;
		int lowest = Integer.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * 2 * i / 8;
			int h = heights.height(Mth.floor(x + Math.cos(angle) * 10), Mth.floor(z + Math.sin(angle) * 10));
			if (h < lowest) {
				lowest = h;
				heading = angle;
			}
		}
		if (sourceHeight - lowest < 3) {
			return null; // a flat summit: no obvious way down
		}

		List<int[]> points = new ArrayList<>();
		int level = sourceHeight - 1;
		points.add(new int[] {Mth.floor(x), Mth.floor(z), level, 14});
		double phase = random.nextDouble() * Math.PI * 2;
		double wiggle = 0.08 + random.nextDouble() * 0.1;
		boolean reachedSea = false;
		int stalls = 0;

		for (int i = 1; i <= MAX_STEPS; i++) {
			double bestScore = Double.MAX_VALUE;
			double bestHeading = heading;
			for (double turn : new double[] {-0.5, 0.0, 0.5}) {
				double candidate = heading + turn;
				int h = heights.height(Mth.floor(x + Math.cos(candidate) * STEP * 2.5), Mth.floor(z + Math.sin(candidate) * STEP * 2.5));
				double score = h + Math.abs(turn) * 2.0;
				if (score < bestScore) {
					bestScore = score;
					bestHeading = candidate;
				}
			}
			heading = Mth.lerp(0.6, heading, bestHeading) + Math.sin(i * 0.23 + phase) * wiggle;
			x += Math.cos(heading) * STEP;
			z += Math.sin(heading) * STEP;
			int terrain = heights.height(Mth.floor(x), Mth.floor(z));
			int width = (int) Math.round(Math.min(5.5, 1.4 + i * 0.035) * 10);
			if (terrain <= seaLevel) {
				points.add(new int[] {Mth.floor(x), Mth.floor(z), seaLevel, width});
				reachedSea = true;
				break;
			}
			int next = Math.min(level, terrain - 1);
			if (terrain - 1 - next > MAX_CUT) {
				if (++stalls > 2) {
					break;
				}
				continue;
			}
			stalls = 0;
			level = Math.max(next, seaLevel);
			points.add(new int[] {Mth.floor(x), Mth.floor(z), level, width});
		}

		int count = points.size();
		if (count < 14 || points.get(0)[2] - points.get(count - 1)[2] < 14) {
			return null;
		}
		int[] xs = new int[count];
		int[] zs = new int[count];
		int[] levels = new int[count];
		int[] widths = new int[count];
		for (int i = 0; i < count; i++) {
			int[] p = points.get(i);
			xs[i] = p[0];
			zs[i] = p[1];
			levels[i] = p[2];
			widths[i] = p[3];
		}
		return new RiverPath(xs, zs, levels, widths, !reachedSea);
	}

	/** Result of locating the river segment closest to a column. */
	static final class Nearest {
		int segment = -1;
		double t;
		double distance = Double.MAX_VALUE;
	}

	/** Finds the segment of the river closest to (x, z). */
	void nearest(double x, double z, Nearest out) {
		out.segment = -1;
		out.distance = Double.MAX_VALUE;
		for (int i = 0; i < xs.length - 1; i++) {
			double ax = xs[i] + 0.5;
			double az = zs[i] + 0.5;
			double bx = xs[i + 1] + 0.5;
			double bz = zs[i + 1] + 0.5;
			double reach = Math.max(width(i), width(i + 1)) + 8;
			if (x < Math.min(ax, bx) - reach || x > Math.max(ax, bx) + reach || z < Math.min(az, bz) - reach || z > Math.max(az, bz) + reach) {
				continue;
			}
			double dx = bx - ax;
			double dz = bz - az;
			double len2 = dx * dx + dz * dz;
			double t = len2 == 0 ? 0 : Mth.clamp(((x - ax) * dx + (z - az) * dz) / len2, 0, 1);
			double px = ax + dx * t - x;
			double pz = az + dz * t - z;
			double d = Math.sqrt(px * px + pz * pz);
			if (d < out.distance) {
				out.distance = d;
				out.segment = i;
				out.t = t;
			}
		}
	}
}
