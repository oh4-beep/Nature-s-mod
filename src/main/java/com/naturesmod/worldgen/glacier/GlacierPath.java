package com.naturesmod.worldgen.glacier;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The course of a glacier: a tongue of ice flowing from a basin high on a peak down the valley
 * below it. Each point carries the height of the ice surface on the centre line and the half
 * width of the ice there. The surface never rises downstream.
 */
public final class GlacierPath {
	static final double STEP = 3.0;
	private static final int MAX_STEPS = 64;

	final int[] xs;
	final int[] zs;
	final int[] surfaces;
	/** Half width of the ice, times ten. */
	final int[] widths;
	/** Centre and water level of the meltwater lake in front of the snout. */
	final int lakeX;
	final int lakeZ;
	final int lakeLevel;

	GlacierPath(int[] xs, int[] zs, int[] surfaces, int[] widths, int lakeX, int lakeZ, int lakeLevel) {
		this.xs = xs;
		this.zs = zs;
		this.surfaces = surfaces;
		this.widths = widths;
		this.lakeX = lakeX;
		this.lakeZ = lakeZ;
		this.lakeLevel = lakeLevel;
	}

	public int size() {
		return xs.length;
	}

	public double width(int i) {
		return widths[i] / 10.0;
	}

	/** Radius of the meltwater lake. */
	public int lakeRadius() {
		return (int) Math.round(width(size() - 1) * 0.9 + 2);
	}

	/** Terrain height lookup: y of the topmost solid block. */
	@FunctionalInterface
	public interface HeightSampler {
		int height(int x, int z);
	}

	/**
	 * Follows the land downhill from a high basin. Returns null if the ice would not make a
	 * worthwhile glacier.
	 */
	public static GlacierPath trace(HeightSampler heights, int startX, int startZ, int startHeight, int snout, RandomSource random) {
		double x = startX + 0.5;
		double z = startZ + 0.5;
		double heading = 0;
		int lowest = Integer.MAX_VALUE;
		for (int i = 0; i < 12; i++) {
			double angle = Math.PI * 2 * i / 12;
			int h = heights.height(Mth.floor(x + Math.cos(angle) * 14), Mth.floor(z + Math.sin(angle) * 14));
			if (h < lowest) {
				lowest = h;
				heading = angle;
			}
		}
		if (startHeight - lowest < 6) {
			return null; // a broad flat summit: the ice has nowhere to flow
		}

		List<double[]> points = new ArrayList<>();
		List<Integer> terrain = new ArrayList<>();
		points.add(new double[] {x, z});
		terrain.add(startHeight);
		double phase = random.nextDouble() * Math.PI * 2;
		double sway = 0.05 + random.nextDouble() * 0.06;
		for (int i = 1; i <= MAX_STEPS; i++) {
			double bestScore = Double.MAX_VALUE;
			double bestHeading = heading;
			for (double turn : new double[] {-0.6, -0.3, 0.0, 0.3, 0.6}) {
				double candidate = heading + turn;
				int h = heights.height(Mth.floor(x + Math.cos(candidate) * STEP * 3), Mth.floor(z + Math.sin(candidate) * STEP * 3));
				double score = h + Math.abs(turn) * 3.0;
				if (score < bestScore) {
					bestScore = score;
					bestHeading = candidate;
				}
			}
			// Ice is heavy and slow: it swings through bends much more lazily than a river.
			heading = Mth.lerp(0.35, heading, bestHeading) + Math.sin(i * 0.17 + phase) * sway;
			x += Math.cos(heading) * STEP;
			z += Math.sin(heading) * STEP;
			int h = heights.height(Mth.floor(x), Mth.floor(z));
			if (h > terrain.get(terrain.size() - 1) + 3) {
				break; // a ridge in the way: the glacier ends here
			}
			points.add(new double[] {x, z});
			terrain.add(h);
			if (h < snout) {
				break; // melted out
			}
		}

		int count = points.size();
		if (count < 12 || terrain.get(0) - terrain.get(count - 1) < 24) {
			return null;
		}
		int[] xs = new int[count];
		int[] zs = new int[count];
		int[] surfaces = new int[count];
		int[] widths = new int[count];
		double maxWidth = 9 + random.nextDouble() * 5;
		int previous = Integer.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			xs[i] = Mth.floor(points.get(i)[0]);
			zs[i] = Mth.floor(points.get(i)[1]);
			// The ice smooths over the bumps in its bed.
			double bed = 0;
			int samples = 0;
			for (int j = Math.max(0, i - 2); j <= Math.min(count - 1, i + 2); j++) {
				bed += terrain.get(j);
				samples++;
			}
			bed /= samples;
			double f = i / (double) (count - 1);
			// Thin where it clings to the summit and at the melting snout, thickest in between.
			double thickness = 3 + 9 * Math.pow(Math.sin(Math.PI * f), 0.6);
			int surface = (int) Math.round(Math.max(bed, terrain.get(i)) + thickness);
			surfaces[i] = previous = Math.min(previous, surface);
			// Wide in the basin up top, a narrower tongue below, rounded off at the snout.
			widths[i] = (int) Math.round((5 + (maxWidth - 5) * Math.pow(1 - f, 1.4)) * 10);
		}

		// Meltwater collects in a lake just beyond the snout, held back by the end moraine.
		int last = count - 1;
		double dx = xs[last] - xs[last - 1];
		double dz = zs[last] - zs[last - 1];
		double length = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
		double reach = widths[last] / 10.0 + 3;
		int lakeX = Mth.floor(xs[last] + 0.5 + dx / length * reach);
		int lakeZ = Mth.floor(zs[last] + 0.5 + dz / length * reach);
		int rim = Integer.MAX_VALUE;
		int radius = (int) Math.round(widths[last] / 10.0 * 0.9 + 2);
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * 2 * i / 8;
			rim = Math.min(rim, heights.height(Mth.floor(lakeX + Math.cos(angle) * (radius + 1)), Mth.floor(lakeZ + Math.sin(angle) * (radius + 1))));
		}
		int lakeLevel = Math.min(rim, surfaces[last] - 2);
		return new GlacierPath(xs, zs, surfaces, widths, lakeX, lakeZ, lakeLevel);
	}

	/** Result of locating the glacier segment closest to a column. */
	static final class Nearest {
		int segment = -1;
		double t;
		double distance = Double.MAX_VALUE;
		/** Distance travelled down the glacier to the closest point, in blocks. */
		double along;
	}

	/** Finds the segment of the glacier closest to (x, z). */
	void nearest(double x, double z, Nearest out) {
		out.segment = -1;
		out.distance = Double.MAX_VALUE;
		double travelled = 0;
		for (int i = 0; i < xs.length - 1; i++) {
			double ax = xs[i] + 0.5;
			double az = zs[i] + 0.5;
			double bx = xs[i + 1] + 0.5;
			double bz = zs[i + 1] + 0.5;
			double dx = bx - ax;
			double dz = bz - az;
			double len2 = dx * dx + dz * dz;
			double len = Math.sqrt(len2);
			double reach = Math.max(width(i), width(i + 1)) + 8;
			if (x >= Math.min(ax, bx) - reach && x <= Math.max(ax, bx) + reach && z >= Math.min(az, bz) - reach && z <= Math.max(az, bz) + reach) {
				double t = len2 == 0 ? 0 : Mth.clamp(((x - ax) * dx + (z - az) * dz) / len2, 0, 1);
				double px = ax + dx * t - x;
				double pz = az + dz * t - z;
				double d = Math.sqrt(px * px + pz * pz);
				if (d < out.distance) {
					out.distance = d;
					out.segment = i;
					out.t = t;
					out.along = travelled + t * len;
				}
			}
			travelled += len;
		}
	}
}
