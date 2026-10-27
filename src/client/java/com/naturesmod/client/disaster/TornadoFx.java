package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterSounds;
import com.naturesmod.disaster.Vortex;

/**
 * Draws a tornado. Above it the storm's base hangs low and dark and turns slowly, lowest round the
 * funnel (the wall cloud). The condensation funnel lowers out of it as the tornado forms, spinning
 * and climbing, wider aloft than at the ground: a slender cone for the weak ones, for the violent ones
 * (EF4, EF5) a broad dark wedge, near as wide as it is tall, that can be seen from far across the land;
 * dying, a thin twisting rope, its top lagging behind its foot. Where it touches down a cloud of dust
 * and debris churns round its base (or spray, over water), towering over the treetops round the violent
 * ones, with wreckage whirling in it. Near it the ground trembles; inside it the air is thick and
 * brown, and pieces of what it has torn up fly past. Its roar carries far.
 *
 * <p>Few particles, and big ones: the funnel and the debris cloud are made of some dozens of billows a
 * tick, each many blocks across, and the specks of wreckage are only drawn close by.
 */
final class TornadoFx {
	private TornadoFx() {
	}

	/** Where the funnel's axis is at a height: its foot, plus the tilt of the funnel and a slow meander. */
	static double axisX(ClientDisaster d, double height) {
		double up = Math.max(0.0, height - d.y);
		return d.x + d.extra(5) * up + Math.sin(up * 0.07 + d.age * 0.03) * d.extra(0) * 0.22;
	}

	static double axisZ(ClientDisaster d, double height) {
		double up = Math.max(0.0, height - d.y);
		return d.z + d.extra(6) * up + Math.cos(up * 0.05 + d.age * 0.025) * d.extra(0) * 0.22;
	}

	private static DisasterParticle.Axis axis(ClientDisaster d) {
		return new DisasterParticle.Axis() {
			@Override
			public boolean alive() {
				return !d.removed;
			}

			@Override
			public double x(double height) {
				return axisX(d, height);
			}

			@Override
			public double z(double height) {
				return axisZ(d, height);
			}
		};
	}

	static void tick(ClientDisaster d) {
		double core = Math.max(1.0, d.extra(0));
		double cloudBase = d.extra(1);
		double rating = Math.max(0.0, Math.min(5.0, d.extra(2)));
		double wind = d.extra(3);
		double formation = d.extra(4);
		boolean water = d.extra(7) > 0.5F;
		double fade = d.extra(8);
		double depth = Math.max(20.0, cloudBase - d.y);
		double distance = Fx.horizontal(d.x, d.z);
		boolean down = formation >= 0.6 && wind > 18.0;
		boolean rope = fade < 0.6;
		// How violent it looks: 0 up to EF3, 0.5 for an EF4, 1 for an EF5 (and none of it once it ropes out).
		double violent = rope ? 0.0 : Math.max(0.0, Math.min(1.0, (rating - 3.0) / 2.0));
		// How high its debris cloud churns.
		double cloudTop = 6.0 + rating * 3.0 + violent * 8.0;
		double peak = wind * Vortex.WIND_SCALE;
		sound(d, distance, core, wind, down);
		near(d, distance, core, rating, cloudTop, peak, water, down);
		hail(d);
		int budget = Fx.budget(70 + (int) (rating * 8.0), distance, 1500.0);
		if (budget <= 0) {
			return;
		}
		DisasterParticle.Axis axis = axis(d);
		// Specks of wreckage are lost from further off: their share goes to the funnel.
		boolean close = distance < 220.0 && down && !water;
		funnel(d, axis, (int) (budget * (close ? 0.42 : 0.52)), core, cloudBase, depth, rating, formation, peak, rope, down, violent);
		if (down) {
			foot(d, axis, (int) (budget * 0.3), core, peak, cloudTop, water, violent);
		}
		if (close) {
			wreckage(d, axis, (int) (budget * 0.1), core, peak, cloudTop, violent);
		}
		storm(d, axis, Math.max(1, (int) (budget * 0.1)), core, cloudBase, violent);
	}

	/**
	 * The funnel, lowering out of the cloud as the tornado forms: condensation spinning up its surface, pale grey
	 * above in the weak ones, dark all the way up in the violent ones, and near the ground the colour of the dust
	 * and debris drawn into it.
	 */
	private static void funnel(ClientDisaster d, DisasterParticle.Axis axis, int count, double core, double cloudBase, double depth, double rating,
			double formation, double peak, boolean rope, boolean down, double violent) {
		double lowered = Math.min(1.0, formation * 1.25);
		double bottom = cloudBase - depth * lowered;
		// Darker than the sky behind it, or it can't be told from the cloud: the violent ones near black, the weak ones a
		// paler grey rope.
		double tone = 0.42 - 0.2 * violent;
		// Billows small against the funnel, many of them, on its surface: they draw its outline, a crisp cone (or wedge)
		// spinning fast, where big ones would only make a column of smoke.
		double billow = 0.3 + 0.08 * violent;
		double dusty = 0.15 + 0.15 * violent;
		for (int i = 0; i < count; i++) {
			double f = Fx.random.nextDouble();
			double h = bottom + f * (cloudBase - bottom);
			double up = (h - d.y) / depth;
			double surface = Vortex.funnel(up, core, rating, rope);
			double r = surface * Fx.range(0.86, 1.0);
			double angular = -Math.max(0.04, Math.min(0.5, (peak * 1.4 + 0.25) / Math.max(r, 1.0)));
			float shade = (float) (tone - 0.05 * up + Fx.range(-0.03, 0.03));
			DisasterParticle p = Fx.cloud(d.x, h, d.z);
			if (down && up < dusty) {
				float dust = shade * 0.8F;
				p.color(dust, dust * 0.9F, dust * 0.78F);
			} else {
				p.gray(shade);
			}
			p.vortex(axis, Fx.random.nextDouble() * Math.PI * 2.0, r, angular, Fx.range(0.08, 0.2), 0.0)
					.size((float) (Math.max(1.2, surface * billow) * Fx.range(0.8, 1.15))).alpha(1.0F).life(26 + Fx.random.nextInt(22))
					.fade(0.18F, 0.4F).spin((float) Fx.range(-0.1, 0.1)).spawn();
		}
	}

	/** The cloud of dust and debris (or spray) churning round its foot: for the violent ones a boiling skirt tall as the trees and more. */
	private static void foot(ClientDisaster d, DisasterParticle.Axis axis, int count, double core, double peak, double cloudTop, boolean water,
			double violent) {
		for (int i = 0; i < count; i++) {
			double up = Math.pow(Fx.random.nextDouble(), 1.7);
			double h = d.y + up * cloudTop;
			double r = core * Fx.range(0.7, 1.6 + 0.6 * violent) * (0.8 + 0.4 * up);
			double angular = -Math.max(0.03, Math.min(0.3, (peak * 0.9 + 0.2) / Math.max(r, 1.0)));
			DisasterParticle p = Fx.cloud(d.x, h, d.z);
			if (water) {
				p.color(0.84F, 0.87F, 0.9F);
			} else {
				float brown = (float) (Fx.range(0.24, 0.36) - 0.06 * violent);
				p.color(brown, brown * 0.86F, brown * 0.7F);
			}
			p.vortex(axis, Fx.random.nextDouble() * Math.PI * 2.0, r, angular, Fx.range(0.04, 0.16), Fx.range(0.0, 0.06))
					.size((float) (Math.max(2.5, core * Fx.range(0.28, 0.5)) * (1.0 + 0.4 * violent))).alpha(0.7F).life(24 + Fx.random.nextInt(20))
					.grow(0.7F).fade(0.15F, 0.5F).spin((float) Fx.range(-0.06, 0.06)).spawn();
		}
	}

	/** Wreckage whirling round the core and up it: boards, branches, clods; the violent ones loft it high. */
	private static void wreckage(ClientDisaster d, DisasterParticle.Axis axis, int count, double core, double peak, double cloudTop, double violent) {
		for (int i = 0; i < count; i++) {
			double r = core * Fx.range(0.4, 1.3);
			double h = d.y + Math.pow(Fx.random.nextDouble(), 1.5) * cloudTop * (1.0 + violent);
			float tone = (float) Fx.range(0.18, 0.45);
			Fx.debris(d.x, h, d.z).vortex(axis, Fx.random.nextDouble() * Math.PI * 2.0, r, -Math.min(0.5, (peak * 1.6 + 0.3) / Math.max(r, 1.0)),
					Fx.range(0.1, 0.45), Fx.range(0.02, 0.12)).size((float) (Fx.range(0.15, 0.5) * (1.0 + violent)))
					.color(tone, tone * 0.82F, tone * 0.62F).alpha(1.0F).life(30 + Fx.random.nextInt(30)).fade(0.05F, 0.2F)
					.spin((float) Fx.range(-0.4, 0.4)).spawn();
		}
	}

	/** The wall cloud and the mesocyclone it hangs from, turning slowly: over the violent ones a vast, dark lowering. */
	private static void storm(ClientDisaster d, DisasterParticle.Axis axis, int count, double core, double cloudBase, double violent) {
		// A wide, flat, dark deck, the funnel hanging from it, lowered a little right round the funnel: not a cap on it.
		double reach = core * 10.0 + 70.0;
		for (int i = 0; i < count; i++) {
			double r = reach * Math.sqrt(Fx.random.nextDouble());
			double lower = Math.max(0.0, 1.0 - r / (core * 3.0 + 12.0)) * (8.0 + 5.0 * violent);
			double h = cloudBase + Fx.range(0.0, 5.0) - lower;
			Fx.cloud(d.x, h, d.z).vortex(axis, Fx.random.nextDouble() * Math.PI * 2.0, r, -(0.004 + 0.012 * (1.0 - r / reach)), 0.0, 0.0)
					.size((float) (Fx.range(12.0, 24.0) * (1.0 + 0.3 * violent))).gray((float) (Fx.range(0.22, 0.3) - 0.06 * violent)).alpha(0.85F)
					.life(120 + Fx.random.nextInt(60)).fade(0.25F, 0.35F).spawn();
		}
	}

	/**
	 * Close to it and in it: the ground trembling, the air thick with its dust (or, carried up above the dust, grey
	 * with the cloud of the funnel all round), and pieces of what it has torn up flying past.
	 */
	private static void near(ClientDisaster d, double distance, double core, double rating, double cloudTop, double peak, boolean water,
			boolean down) {
		if (!down) {
			return;
		}
		double rumble = core * 4.0 + 20.0;
		if (distance < rumble) {
			CameraShake.shake((1.0 - distance / rumble) * (0.01 + rating * 0.008) * d.strength);
		}
		double inside = core * 2.2;
		if (distance >= inside) {
			return;
		}
		float thick = (float) (1.0 - distance / inside);
		double above = Fx.eye.y - d.y;
		if (above > cloudTop) {
			DisasterClient.fog(0.6F * thick, 0.42F, 0.42F, 0.44F, 22.0F);
		} else {
			DisasterClient.fog(0.85F * thick, water ? 0.8F : 0.4F, water ? 0.84F : 0.35F, water ? 0.88F : 0.29F, 10.0F);
		}
		if (above > 3.0 && distance < core * 1.3) {
			// Off the ground in it: carried.
			CameraShake.shake(0.06 + rating * 0.006);
		}
		if (water) {
			return;
		}
		double ax = axisX(d, Fx.eye.y);
		double az = axisZ(d, Fx.eye.y);
		double vr = Math.max(1.0, Math.sqrt((Fx.eye.x - ax) * (Fx.eye.x - ax) + (Fx.eye.z - az) * (Fx.eye.z - az)));
		double angle = Math.atan2(Fx.eye.z - az, Fx.eye.x - ax);
		double angular = -Math.max(0.04, Math.min(0.45, (peak + 0.2) / vr));
		DisasterParticle.Axis axis = axis(d);
		int count = Fx.round(10 * thick * Fx.density);
		for (int i = 0; i < count; i++) {
			// Coming round from upwind of the viewer, to fly past.
			float tone = (float) Fx.range(0.2, 0.45);
			Fx.debris(d.x, Fx.eye.y + Fx.range(-3.0, 3.0), d.z)
					.vortex(axis, angle + Fx.range(0.3, 1.2), Math.max(0.5, vr + Fx.range(-3.0, 3.0)), angular, Fx.range(-0.05, 0.15), 0.0)
					.size((float) Fx.range(0.12, 0.4)).color(tone, tone * 0.82F, tone * 0.62F).alpha(1.0F).life(16 + Fx.random.nextInt(12))
					.fade(0.05F, 0.2F).spin((float) Fx.range(-0.5, 0.5)).spawn();
		}
	}

	/** Under the storm's hail core: stones pelting down and bouncing, the clatter of them, the air grey with it. */
	private static void hail(ClientDisaster d) {
		double reach = d.extra(11);
		if (reach <= 0.0) {
			return;
		}
		double distance = Fx.horizontal(d.extra(9), d.extra(10));
		float inside = (float) Math.max(0.0, Math.min(1.0, (reach + 15.0 - distance) / 30.0));
		if (inside <= 0.0F) {
			return;
		}
		DisasterAudio.loop("hail" + d.id, DisasterSounds.HAIL, false, inside * 0.9F, 0, 0, 0);
		DisasterClient.fog(0.3F * inside, 0.72F, 0.75F, 0.8F, 70.0F);
		double stone = d.extra(12);
		int count = Fx.round(28 * inside * Fx.density);
		for (int i = 0; i < count; i++) {
			// Not right in front of the eyes: a ring about the viewer.
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = Fx.range(2.5, 12.0);
			Fx.flake(Fx.eye.x + Math.cos(angle) * r, Fx.eye.y + Fx.range(6.0, 14.0), Fx.eye.z + Math.sin(angle) * r)
					.velocity(d.dirX * 0.12 + Fx.range(-0.03, 0.03), -Fx.range(0.8, 1.1), d.dirZ * 0.12 + Fx.range(-0.03, 0.03)).drag(1.0).fall(0.05)
					.size((float) (stone * Fx.range(0.3, 0.7))).color(0.88F, 0.91F, 0.96F).alpha(1.0F).bounce().life(45).fade(0.0F, 0.1F).spawn();
		}
	}

	private static void sound(ClientDisaster d, double distance, double core, double wind, boolean down) {
		float roar = (float) (d.strength * (0.35 + 0.65 * Math.min(1.0, wind / 60.0)));
		DisasterAudio.loop("tornado" + d.id, DisasterSounds.TORNADO_ROAR, true, down ? roar : roar * 0.4F, d.x, d.y + 4.0, d.z);
		double close = core * 5.0 + 40.0;
		if (distance < close) {
			DisasterAudio.loop("tornado_wind" + d.id, DisasterSounds.WIND_HOWL, false, (float) ((1.0 - distance / close) * d.strength), 0, 0, 0);
		}
	}
}
