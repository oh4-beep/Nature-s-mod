package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterSounds;
import com.naturesmod.disaster.Vortex;

/**
 * Draws a tornado. Above it the storm's base hangs low and dark and turns slowly, lowest round the
 * funnel (the wall cloud). The condensation funnel lowers out of it as the tornado forms, spinning
 * and climbing, wider aloft than at the ground (a wedge for the violent ones, a thin twisting rope
 * as it dies, its top lagging behind its foot). Where it touches down a swirling cloud of dust and
 * flying debris churns round its base (or spray, over water). Its roar carries far.
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
		double core = d.extra(0);
		double cloudBase = d.extra(1);
		double rating = d.extra(2);
		double wind = d.extra(3);
		double formation = d.extra(4);
		boolean water = d.extra(7) > 0.5F;
		double fade = d.extra(8);
		double depth = Math.max(20.0, cloudBase - d.y);
		double distance = Fx.horizontal(d.x, d.z);
		boolean down = formation >= 0.6 && wind > 18.0;
		sound(d, distance, core, wind, down);
		if (distance < core * 2.5 && down) {
			// Inside the debris cloud.
			float thick = (float) (0.85 * (1.0 - distance / (core * 2.5)));
			DisasterClient.fog(thick, water ? 0.8F : 0.45F, water ? 0.84F : 0.39F, water ? 0.88F : 0.32F, 10.0F);
		}
		int budget = Fx.budget(110, distance, 1200.0);
		if (budget <= 0) {
			return;
		}
		DisasterParticle.Axis axis = axis(d);
		boolean rope = fade < 0.6;
		double peak = wind * Vortex.WIND_SCALE;
		// The funnel, lowering out of the cloud as the tornado forms: condensation spinning up its surface,
		// pale grey above, darkened near the ground by the dust and debris drawn into it.
		double lowered = Math.min(1.0, formation * 1.25);
		double bottom = cloudBase - depth * lowered;
		int funnel = (int) (budget * 0.55);
		for (int i = 0; i < funnel; i++) {
			double f = Math.pow(Fx.random.nextDouble(), 0.8);
			double h = bottom + f * (cloudBase - bottom);
			double up = (h - d.y) / depth;
			double surface = Vortex.funnel(up, core, rating, rope);
			double r = surface * Fx.range(0.82, 1.02);
			double angular = -Math.max(0.05, Math.min(0.5, (peak * 1.4 + 0.25) / Math.max(r, 1.0)));
			float shade = (float) (up < 0.15 && down ? 0.42 + up * 1.2 : 0.6 + 0.16 * up) + (float) Fx.range(-0.04, 0.04);
			DisasterParticle p = Fx.cloud(d.x, h, d.z);
			p.vortex(axis, Fx.random.nextDouble() * Math.PI * 2.0, r, angular, Fx.range(0.08, 0.2), 0.0)
					.size((float) (Math.max(1.4, surface * 0.55) * Fx.range(0.75, 1.15))).gray(shade).alpha(0.9F).life(24 + Fx.random.nextInt(24))
					.fade(0.18F, 0.4F).spin((float) Fx.range(-0.1, 0.1)).spawn();
		}
		if (down) {
			// The swirl of dust and debris (or spray) round its foot.
			int swirl = (int) (budget * 0.25);
			for (int i = 0; i < swirl; i++) {
				double h = d.y + Math.pow(Fx.random.nextDouble(), 2.0) * (6.0 + rating * 3.0);
				double r = core * Fx.range(0.9, 2.5);
				double angular = -Math.max(0.04, Math.min(0.35, (peak + 0.2) / Math.max(r, 1.0)));
				DisasterParticle p = Fx.cloud(d.x, h, d.z);
				if (water) {
					p.color(0.86F, 0.89F, 0.92F);
				} else {
					float brown = (float) Fx.range(0.36, 0.5);
					p.color(brown, brown * 0.84F, brown * 0.66F);
				}
				p.vortex(axis, Fx.random.nextDouble() * Math.PI * 2.0, r, angular, Fx.range(0.04, 0.2), Fx.range(0.0, 0.05))
						.size((float) Fx.range(1.6, 4.0)).alpha(0.78F).life(20 + Fx.random.nextInt(28)).grow(0.6F).fade(0.1F, 0.45F).spawn();
			}
			if (!water) {
				int chunks = (int) (budget * 0.12);
				for (int i = 0; i < chunks; i++) {
					double r = core * Fx.range(0.3, 1.2);
					DisasterParticle p = Fx.debris(d.x, d.y + Fx.range(0.0, 4.0), d.z);
					float tone = (float) Fx.range(0.2, 0.5);
					p.vortex(axis, Fx.random.nextDouble() * Math.PI * 2.0, r, -Math.min(0.5, (peak * 1.6 + 0.3) / Math.max(r, 1.0)),
							Fx.range(0.15, 0.5), Fx.range(0.04, 0.16)).size((float) Fx.range(0.15, 0.5)).color(tone, tone * 0.82F, tone * 0.62F)
							.alpha(1.0F).life(40 + Fx.random.nextInt(40)).fade(0.05F, 0.2F).spin((float) Fx.range(-0.4, 0.4)).spawn();
				}
			}
		}
		// The wall cloud and the mesocyclone it hangs from, turning slowly.
		int storm = Math.max(1, (int) (budget * 0.08));
		double reach = core * 9.0 + 40.0;
		for (int i = 0; i < storm; i++) {
			double r = reach * Math.sqrt(Fx.random.nextDouble());
			double lower = Math.max(0.0, 1.0 - r / (core * 4.0 + 15.0)) * 14.0;
			double h = cloudBase + Fx.range(-2.0, 8.0) - lower;
			DisasterParticle p = Fx.cloud(d.x, h, d.z);
			p.vortex(axis, Fx.random.nextDouble() * Math.PI * 2.0, r, -(0.004 + 0.012 * (1.0 - r / reach)), 0.0, 0.0)
					.size((float) Fx.range(10.0, 22.0)).gray((float) Fx.range(0.26, 0.36)).alpha(0.8F).life(140 + Fx.random.nextInt(80))
					.fade(0.25F, 0.35F).spawn();
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
