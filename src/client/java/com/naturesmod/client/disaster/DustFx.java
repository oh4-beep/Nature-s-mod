package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterSounds;
import com.naturesmod.disaster.Sandstorm;

import net.minecraft.core.BlockPos;

/**
 * Draws a sandstorm, a haboob. Seen from ahead it is a wall of dust two hundred blocks high stretching
 * across the land from one side to the other and rolling toward you, its top boiling up in towering
 * heads, its foot a churning skirt of sand racing out before it; the nearer it comes the browner and
 * dimmer the sky, and the louder its roar. From the side it is the same wall running away, from
 * behind a lower haze going off. Inside it the air is thick and dark, the sun gone, the world a few
 * blocks wide: sand streams past in the gusts and sheets along the ground, and the air clears a little
 * in the lulls. Dust devils spin across the ground before it.
 *
 * <p>Few particles, and big ones: the wall is a handful of billows a tick, each tens of blocks across,
 * drawn only along the stretch of it facing the viewer and fewer the further off it is; the sand flying
 * past is drawn close about the viewer only, and not at all under a roof.
 */
final class DustFx {
	/** How far off the wall is drawn at all, and how far off it starts to darken the sky. */
	private static final double FAR = 1600.0;
	private static final double DIMMING = 800.0;
	/** Where the dust devils' numbers start (see {@link Sandstorm#extra()}), and how many each has. */
	private static final int DEVILS = 9;
	private static final int DEVIL_FLOATS = 6;
	private static final int MAX_DEVILS = 8;

	private DustFx() {
	}

	static void tick(ClientDisaster d) {
		double wx = d.dirX;
		double wz = d.dirZ;
		if (!(wx * wx + wz * wz > 0.25)) {
			return;
		}
		double front = d.extra(0);
		double body = Math.max(1.0, d.extra(1));
		double width = Math.max(1.0, d.extra(2));
		double visibility = Math.max(3.0, d.extra(3));
		double speed = d.extra(4);
		double ground = d.extra(5);
		double height = Math.max(40.0, d.extra(6));
		double wind = d.extra(8);
		float[] tone = tone((int) d.extra(7));
		double ex = Fx.eye.x - d.x;
		double ez = Fx.eye.z - d.z;
		double along = ex * wx + ez * wz;
		double across = -ex * wz + ez * wx;
		double depth = Sandstorm.dustDepth(along, across, front, body, width);
		if (depth > 0.0) {
			// The air thickens over the first few blocks past the wall, not the thirty the wind takes to build: walking into
			// it the sky's brown gives way straight to the dark, with no clearing between.
			inside(d, (float) (Math.min(1.0, depth * 3.0) * d.strength), tone, visibility, wind, Sandstorm.gust(d.age, along), wx, wz);
		} else {
			outside(d, along, across, front, body, width, height, ground, speed, tone, wx, wz);
		}
		devils(d, tone, wx, wz);
	}

	/** The colour of the dust: tan over sand, rust over red sand, a dull brown over green land. */
	private static float[] tone(int tint) {
		return switch (tint) {
			case 1 -> new float[] {0.78F, 0.47F, 0.29F};
			case 2 -> new float[] {0.57F, 0.49F, 0.39F};
			default -> new float[] {0.8F, 0.63F, 0.42F};
		};
	}

	private static DisasterParticle tinted(DisasterParticle p, float[] tone, float shade) {
		return p.color(Math.min(1.0F, tone[0] * shade), Math.min(1.0F, tone[1] * shade), Math.min(1.0F, tone[2] * shade));
	}

	/** Whether there is a roof over the viewer: indoors the dust only seeps in, and nothing flies past. */
	private static boolean roofed() {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int bx = (int) Math.floor(Fx.eye.x);
		int by = (int) Math.floor(Fx.eye.y);
		int bz = (int) Math.floor(Fx.eye.z);
		for (int dy = 1; dy <= 20; dy++) {
			pos.set(bx, by + dy, bz);
			if (!Fx.level.getBlockState(pos).getCollisionShape(Fx.level, pos).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** In the thick of it: a few blocks of brown air, darker the worse the storm, the sand streaming past in the gusts. */
	private static void inside(ClientDisaster d, float in, float[] tone, double visibility, double wind, double gust, double wx, double wz) {
		boolean roofed = roofed();
		float dark = (float) (1.0 - 0.07 * d.intensity);
		float see = (float) (visibility * (1.2 - 0.35 * gust));
		if (roofed) {
			DisasterClient.fog(0.4F * in, tone[0] * dark, tone[1] * dark, tone[2] * dark, see * 4.0F);
		} else {
			DisasterClient.fog(0.97F * in, tone[0] * dark, tone[1] * dark, tone[2] * dark, see);
		}
		DisasterAudio.loop("sand" + d.id, DisasterSounds.SANDSTORM_WIND, false, in * (roofed ? 0.4F : (float) (0.7 + 0.3 * gust)), 0, 0, 0);
		DisasterAudio.loop("sandgust" + d.id, DisasterSounds.WIND_HOWL, false, roofed ? 0.0F : (float) (in * gust * 0.55), 0, 0, 0);
		if (roofed || in < 0.02F) {
			return;
		}
		double blow = wind * (0.6 + 0.8 * gust);
		// Billows of dust rolling past: few and not too big, as they pass right over the eye (the fog does the thickening).
		int billows = Fx.round(8 * in * Fx.density * (0.5 + 0.5 * gust));
		for (int i = 0; i < billows; i++) {
			double x = Fx.eye.x - wx * 10.0 + Fx.range(-11.0, 11.0);
			double z = Fx.eye.z - wz * 10.0 + Fx.range(-11.0, 11.0);
			double rush = blow * Fx.range(1.6, 2.4);
			tinted(Fx.cloud(x, Fx.eye.y + Fx.range(-2.0, 5.0), z), tone, (float) Fx.range(0.85, 1.02) * dark)
					.velocity(wx * rush, Fx.range(-0.02, 0.03), wz * rush).drag(1.0).size((float) Fx.range(1.8, 3.5)).alpha(0.3F)
					.life(25 + Fx.random.nextInt(15)).spin((float) Fx.range(-0.03, 0.03)).spawn();
		}
		// Grains of sand flying past, close about the viewer.
		int grains = Fx.round(26 * in * Fx.density * (0.3 + 0.7 * gust));
		for (int i = 0; i < grains; i++) {
			double rush = blow * Fx.range(2.5, 4.0);
			tinted(Fx.debris(Fx.eye.x - wx * 5.0 + Fx.range(-5.0, 5.0), Fx.eye.y + Fx.range(-1.5, 2.5), Fx.eye.z - wz * 5.0 + Fx.range(-5.0, 5.0)), tone,
					(float) Fx.range(0.9, 1.1)).velocity(wx * rush, Fx.range(-0.03, 0.03), wz * rush).drag(1.0).size((float) Fx.range(0.03, 0.07))
					.alpha(0.9F).life(8 + Fx.random.nextInt(8)).fade(0.1F, 0.3F).spawn();
		}
		// Sheets of sand skimming the ground in the gusts.
		int sheets = Fx.round(5 * in * Fx.density * gust);
		for (int i = 0; i < sheets; i++) {
			double rush = blow * Fx.range(2.5, 3.5);
			tinted(Fx.cloud(Fx.eye.x - wx * 8.0 + Fx.range(-7.0, 7.0), Fx.eye.y - 1.5 + Fx.range(-0.2, 0.4), Fx.eye.z - wz * 8.0 + Fx.range(-7.0, 7.0)),
					tone, (float) Fx.range(0.95, 1.1)).velocity(wx * rush, 0.0, wz * rush).drag(1.0).size((float) Fx.range(1.2, 2.4)).alpha(0.45F)
					.life(14 + Fx.random.nextInt(8)).spawn();
		}
	}

	/**
	 * Out of it: the nearest stretch of it seen as a wall, tallest along its front, lower and thinner at its back; the
	 * nearer it is the browner and dimmer the sky, and the louder its roar.
	 */
	private static void outside(ClientDisaster d, double along, double across, double front, double body, double width, double height, double ground,
			double speed, float[] tone, double wx, double wz) {
		double tail = front - body;
		double half = width / 2.0;
		double nearAlong = Math.max(tail, Math.min(front, along));
		double nearAcross = Math.max(-half, Math.min(half, across));
		double gap = Math.sqrt((along - nearAlong) * (along - nearAlong) + (across - nearAcross) * (across - nearAcross));
		if (!(gap < FAR)) {
			return;
		}
		float dark = (float) (1.0 - 0.07 * d.intensity);
		float fade = 0.3F + 0.7F * d.strength;
		if (gap < DIMMING) {
			// The sky goes brown and dim as it comes on (the fog reaching no nearer than the wall, which stays in sight).
			double near = 1.0 - gap / DIMMING;
			float dim = dark * 0.85F;
			DisasterClient.fog((float) (0.6 * near * near) * fade, tone[0] * dim, tone[1] * dim, tone[2] * dim, (float) Math.max(60.0, gap * 1.3 + 40.0));
			DisasterAudio.loop("sand" + d.id, DisasterSounds.SANDSTORM_WIND, false, (float) (0.85 * Math.pow(near, 1.6)) * fade, 0, 0, 0);
		}
		double base = Math.min(ground, Fx.eye.y - 12.0);
		int count = Fx.budget(5, gap, FAR);
		if (along > front) {
			// Ahead of it: its front, coming on.
			wall(d, count, gap, true, front, -1.0, nearAcross, -half, half, height, base, speed, tone, dark, 0.88F * fade, wx, wz);
			skirt(d, gap, front, nearAcross, half, Math.min(ground, Fx.eye.y - 1.6), speed, tone, dark, fade, wx, wz);
		} else if (along < tail) {
			// Behind it: the back of the dust going away, lower and thinner.
			wall(d, (count + 1) / 2, gap, true, tail, 1.0, nearAcross, -half, half, height * 0.45, base, speed * 0.3, tone, dark, 0.5F * fade, wx, wz);
		} else {
			// Beside it: its flank.
			double side = across > 0.0 ? half : -half;
			wall(d, count, gap, false, side, across > 0.0 ? -1.0 : 1.0, nearAlong, tail, front, height * 0.85, base, speed, tone, dark, 0.8F * fade, wx,
					wz);
		}
	}

	/**
	 * A stretch of the wall: big billows of dust about the part of an edge nearest the viewer, spread the wider the
	 * further off it is. Along the front or the back (`crossing`), the edge is at `fixed` along the wind and runs across
	 * it; along a flank, it is at `fixed` across the wind and runs along it. `inward` is the way into the dust.
	 */
	private static void wall(ClientDisaster d, int count, double gap, boolean crossing, double fixed, double inward, double centre, double lo, double hi,
			double height, double base, double speed, float[] tone, float dark, float alpha, double wx, double wz) {
		double spread = 0.6 * gap + 50.0;
		// Bigger the further off: each looks much the same size from where it is seen.
		double size = Math.max(10.0, Math.min(48.0, gap * 0.1 + 8.0)) * Math.max(0.7, height / 200.0);
		for (int i = 0; i < count; i++) {
			double s = centre + Fx.random.nextGaussian() * spread;
			if (s < lo || s > hi) {
				continue;
			}
			double edge = fixed + inward * Fx.random.nextDouble() * 16.0;
			double a = crossing ? edge : s;
			double c = crossing ? s : edge;
			// Its top is lumpy along its length: towering heads of dust and lower stretches between them.
			double top = height * (0.8 + 0.12 * Math.sin(s * 0.011 + d.id) + 0.08 * Math.sin(s * 0.037 + 2.0));
			double f = Math.pow(Fx.random.nextDouble(), 0.8);
			double x = d.x + wx * a - wz * c;
			double z = d.z + wz * a + wx * c;
			// Darker at its foot, the sun on the heads of it.
			float shade = (float) (Fx.range(0.72, 0.9) * (0.8 + 0.3 * f)) * dark;
			double roll = speed * (0.95 + 0.15 * f);
			tinted(Fx.cloud(x, base + f * top, z), tone, shade).velocity(wx * roll, Fx.range(0.0, 0.05), wz * roll).drag(1.0)
					.size((float) (size * Fx.range(0.75, 1.25) * (0.8 + 0.5 * f))).alpha(alpha).life(70 + Fx.random.nextInt(40)).grow(0.35F)
					.fade(0.2F, 0.35F).spin((float) Fx.range(-0.01, 0.01)).spawn();
		}
	}

	/** The foot of the wall close up: sand boiling up off the ground and racing out ahead of it. */
	private static void skirt(ClientDisaster d, double gap, double front, double centre, double half, double floor, double speed, float[] tone, float dark,
			float alpha, double wx, double wz) {
		int count = Fx.budget(3, gap, 220.0);
		for (int i = 0; i < count; i++) {
			double c = centre + Fx.random.nextGaussian() * (0.5 * gap + 30.0);
			if (c < -half || c > half) {
				continue;
			}
			double a = front + Fx.range(-4.0, 20.0);
			double x = d.x + wx * a - wz * c;
			double z = d.z + wz * a + wx * c;
			tinted(Fx.cloud(x, floor + Fx.range(0.0, 8.0), z), tone, (float) Fx.range(0.75, 0.9) * dark)
					.velocity(wx * speed * 1.3, Fx.range(0.0, 0.04), wz * speed * 1.3).drag(0.99).size((float) Fx.range(6.0, 14.0)).alpha(0.75F * alpha)
					.life(40 + Fx.random.nextInt(25)).grow(0.6F).fade(0.15F, 0.4F).spawn();
		}
	}

	/** Where a dust devil's numbers start among the storm's, or -1 once it is gone. */
	private static int slot(ClientDisaster d, float id) {
		int count = Math.max(0, Math.min(MAX_DEVILS, (int) d.extra(DEVILS)));
		for (int i = 0; i < count; i++) {
			int k = DEVILS + 1 + i * DEVIL_FLOATS;
			if (Math.abs(d.extra(k) - id) < 0.5F) {
				return k;
			}
		}
		return -1;
	}

	/** The axis of a dust devil, followed by its id as the server moves it: swaying, and leaning with the wind as it goes up. */
	private static DisasterParticle.Axis devilAxis(ClientDisaster d, float id, double x, double y, double z, double core, double wx, double wz) {
		return new DisasterParticle.Axis() {
			private double footX = x;
			private double footY = y;
			private double footZ = z;

			private void find() {
				int k = slot(d, id);
				if (k >= 0) {
					footX = d.extra(k + 1);
					footY = d.extra(k + 2);
					footZ = d.extra(k + 3);
				}
			}

			@Override
			public boolean alive() {
				return !d.removed && slot(d, id) >= 0;
			}

			@Override
			public double x(double height) {
				find();
				double up = Math.max(0.0, height - footY);
				return footX + wx * up * 0.12 + Math.sin(up * 0.09 + d.age * 0.05) * core * 0.35;
			}

			@Override
			public double z(double height) {
				find();
				double up = Math.max(0.0, height - footY);
				return footZ + wz * up * 0.12 + Math.cos(up * 0.07 + d.age * 0.04) * core * 0.35;
			}
		};
	}

	/** The dust devils ahead of the storm: slender whirling columns of dust flaring out toward the top, sand skittering round their feet. */
	private static void devils(ClientDisaster d, float[] tone, double wx, double wz) {
		int count = Math.max(0, Math.min(MAX_DEVILS, (int) d.extra(DEVILS)));
		for (int i = 0; i < count; i++) {
			int k = DEVILS + 1 + i * DEVIL_FLOATS;
			float id = d.extra(k);
			double x = d.extra(k + 1);
			double y = d.extra(k + 2);
			double z = d.extra(k + 3);
			double height = d.extra(k + 4);
			double core = Math.max(1.0, d.extra(k + 5));
			if (height < 3.0) {
				continue;
			}
			double distance = Fx.horizontal(x, z);
			int spin = Fx.budget(6, distance, 500.0);
			if (spin <= 0) {
				continue;
			}
			DisasterParticle.Axis axis = devilAxis(d, id, x, y, z, core, wx, wz);
			for (int n = 0; n < spin; n++) {
				double f = Math.pow(Fx.random.nextDouble(), 1.3);
				double r = core * (0.45 + 1.5 * f) * Fx.range(0.8, 1.1);
				double angular = -Math.min(0.5, 0.55 / Math.max(1.0, r));
				tinted(Fx.cloud(x, y + f * height, z), tone, (float) Fx.range(0.78, 0.95))
						.vortex(axis, Fx.random.nextDouble() * Math.PI * 2.0, r, angular, Fx.range(0.1, 0.22), 0.01)
						.size((float) (Fx.range(1.3, 2.4) * (1.0 + 1.6 * f))).alpha((float) (0.7 - 0.3 * f)).life(28 + Fx.random.nextInt(18))
						.fade(0.15F, 0.4F).spawn();
			}
			if (distance < 120.0) {
				int grains = Fx.round(3 * Fx.density);
				for (int n = 0; n < grains; n++) {
					tinted(Fx.debris(x, y + Fx.range(0.0, 1.5), z), tone, (float) Fx.range(0.9, 1.05))
							.vortex(axis, Fx.random.nextDouble() * Math.PI * 2.0, core * Fx.range(0.8, 1.8), -0.3, Fx.range(0.02, 0.08), 0.02)
							.size((float) Fx.range(0.05, 0.1)).alpha(0.9F).life(16 + Fx.random.nextInt(10)).spawn();
				}
			}
		}
	}
}
