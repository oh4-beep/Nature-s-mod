package com.naturesmod.disaster;

import net.minecraft.world.phys.Vec3;

/**
 * The wind of a tornado or a dust devil: a modified Rankine vortex. Inside the core the air turns like
 * a solid wheel, fastest at the core's edge; outside it the wind falls off with distance. Near the
 * ground air streams in toward the core and rises up it; aloft it spins out again. The rotation is
 * cyclonic, counterclockwise seen from above. Shared by the server, which throws things about with it,
 * and the clients, which draw the funnel turning the same way.
 */
public final class Vortex {
	/** Metres a second to blocks a tick, scaled down so that a tornado's winds stay playable. */
	public static final double WIND_SCALE = 0.012;

	private Vortex() {
	}

	/**
	 * The air's velocity in blocks a tick at (dx, dz) from the axis, `height` blocks above the ground,
	 * for a vortex whose core has radius `core`, peak tangential wind `peak` (blocks a tick) and which
	 * reaches up `depth` blocks to the cloud.
	 */
	public static Vec3 air(double dx, double dz, double height, double core, double peak, double depth) {
		double r = Math.sqrt(dx * dx + dz * dz) + 1.0E-6;
		double tangential = r < core ? peak * r / core : peak * Math.pow(core / r, 0.6);
		// Inflow hugging the ground, outflow aloft.
		double inflowLayer = Math.max(0.0, 1.0 - height / (2.5 * core + 8.0));
		double radial = -0.45 * tangential * inflowLayer * Math.min(1.0, r / (0.7 * core));
		double aloft = clamp((height - depth * 0.6) / (depth * 0.4), 0.0, 1.0);
		radial += 0.3 * tangential * aloft;
		double q = r / (1.2 * core);
		double updraft = peak * 0.55 * Math.exp(-q * q) * (1.0 - aloft);
		double tx = dz / r;
		double tz = -dx / r;
		double rx = dx / r;
		double rz = dz / r;
		return new Vec3(tx * tangential + rx * radial, updraft, tz * tangential + rz * radial);
	}

	/**
	 * The radius of the visible condensation funnel at a fraction `f` of the way up from the ground to the
	 * cloud (0 to 1): a cone, narrow at the ground and flaring into the cloud above, the weak ones slender,
	 * the violent ones broad wedges, nearly as wide at the ground as aloft; dying, a thin rope.
	 */
	public static double funnel(double f, double core, double rating, boolean rope) {
		f = clamp(f, 0.0, 1.0);
		rating = clamp(rating, 0.0, 5.0);
		if (rope) {
			return core * (0.18 + 0.3 * f * f);
		}
		double foot = 0.3 + 0.1 * rating;
		double top = 1.0 + 0.16 * rating;
		double flare = 2.4 - 0.16 * rating;
		return core * (foot + (top - foot) * Math.pow(f, flare));
	}

	static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}
}
