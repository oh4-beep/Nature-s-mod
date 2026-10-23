package com.naturesmod.disaster;

/**
 * How the ground shakes in an earthquake, wherever and whenever: shared by the server, which knocks
 * things down with it, and the clients, which shake the view and rumble. A block stands for 20 metres
 * here, so that a strong quake is felt for a few thousand blocks: the first waves (P) run 15 blocks a
 * tick and jolt the ground lightly, the second (S) 8.75 blocks a tick and shake it hard, and far from
 * the epicentre the shaking comes later, weaker and drawn out.
 */
public final class Seismic {
	public static final double P_SPEED = 15.0;
	public static final double S_SPEED = 8.75;

	private Seismic() {
	}

	/** The strongest ground acceleration, in g, `distance` blocks from the epicentre of a quake of magnitude `m`. */
	public static double pga(double m, double distance) {
		return peak(m) / Math.pow(1.0 + distance / reach(m), 1.6);
	}

	/** At the epicentre. */
	static double peak(double m) {
		return 0.12 * Math.pow(1.85, m - 5.0);
	}

	/** How quickly it falls off. */
	static double reach(double m) {
		return 35.0 * Math.pow(1.6, m - 5.0);
	}

	/** How far out the ground is felt to shake at all (0.01 g). */
	public static double feltRadius(double m) {
		return reach(m) * (Math.pow(peak(m) / 0.01, 1.0 / 1.6) - 1.0);
	}

	/** How long the strong shaking lasts near the epicentre, in ticks. */
	public static int shakingTicks(double m) {
		return (int) Math.min(1600.0, 20.0 * 6.0 * Math.pow(1.9, m - 5.0));
	}

	/** How hard the ground shakes, as a fraction of its strongest, `t` ticks after the rupture and `distance` blocks away. */
	public static double envelope(double t, double distance, double m) {
		double tp = distance / P_SPEED;
		double ts = distance / S_SPEED;
		if (t < tp) {
			return 0.0;
		}
		if (t < ts) {
			return 0.3 * Math.min(1.0, (t - tp) / 4.0);
		}
		double since = t - ts;
		double lasting = shakingTicks(m) * Math.min(2.0, 1.0 + distance / 600.0);
		double rise = Math.min(1.0, since / 8.0);
		double decay = since > lasting ? Math.exp(-(since - lasting) / (0.35 * lasting)) : 1.0;
		return rise * decay;
	}

	/** Shaking (in g) at a point right now. */
	public static double shaking(double m, double distance, double t) {
		return pga(m, distance) * envelope(t, distance, m);
	}

	/** The word for how it feels, after the Modified Mercalli scale. */
	public static String feeling(double g) {
		if (g < 0.02) {
			return "weak";
		} else if (g < 0.05) {
			return "light";
		} else if (g < 0.1) {
			return "moderate";
		} else if (g < 0.2) {
			return "strong";
		} else if (g < 0.35) {
			return "very_strong";
		} else if (g < 0.65) {
			return "severe";
		} else if (g < 1.2) {
			return "violent";
		}
		return "extreme";
	}
}
