package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterSounds;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;

/**
 * Draws a sandstorm. Seen from ahead it is a wall of dust towering over the desert and rolling toward
 * you; inside it the air is thick and tan-brown, the sun gone, sand streaming past on the wind. Dust
 * devils spin across the ground before it.
 */
final class DustFx {
	private DustFx() {
	}

	static void tick(ClientDisaster d) {
		double front = d.extra(0);
		double body = d.extra(1);
		double width = d.extra(2);
		double visibility = d.extra(3);
		double speed = d.extra(4);
		double ground = d.extra(5);
		double wx = d.dirX;
		double wz = d.dirZ;
		double ex = Fx.eye.x - d.x;
		double ez = Fx.eye.z - d.z;
		double along = ex * wx + ez * wz;
		double across = -ex * wz + ez * wx;
		boolean within = along < front && along > front - body && Math.abs(across) < width / 2.0;
		if (within) {
			double edge = Math.min(Math.min(front - along, along - (front - body)), width / 2.0 - Math.abs(across));
			float inside = (float) (Math.min(1.0, edge / 40.0) * d.strength);
			inside(d, inside, visibility, speed, wx, wz);
		} else if (along > front && along - front < 900.0 && Math.abs(across) < width / 2.0 + 200.0) {
			wall(d, front, along - front, across, width, ground, speed, wx, wz);
		}
		devils(d, ground);
	}

	private static void inside(ClientDisaster d, float inside, double visibility, double speed, double wx, double wz) {
		DisasterClient.fog(0.95F * inside, 0.74F, 0.57F, 0.38F, (float) visibility);
		DisasterAudio.loop("sand" + d.id, DisasterSounds.SANDSTORM_WIND, false, inside, 0, 0, 0);
		int streaks = Fx.round(40 * inside * Fx.density);
		for (int i = 0; i < streaks; i++) {
			double x = Fx.eye.x - wx * 10.0 + Fx.range(-12.0, 12.0);
			double z = Fx.eye.z - wz * 10.0 + Fx.range(-12.0, 12.0);
			double y = Fx.eye.y + Fx.range(-2.0, 4.0);
			double gust = speed * Fx.range(1.6, 3.0);
			float tone = (float) Fx.range(0.66, 0.8);
			Fx.cloud(x, y, z).velocity(wx * gust, Fx.range(-0.02, 0.03), wz * gust).drag(1.0).size((float) Fx.range(0.5, 1.4))
					.color(tone, tone * 0.78F, tone * 0.55F).alpha(0.35F).life(20 + Fx.random.nextInt(20)).spawn();
		}
		int grains = Fx.round(6 * inside * Fx.density);
		for (int i = 0; i < grains; i++) {
			Fx.level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), Fx.eye.x + Fx.range(-6.0, 6.0),
					Fx.eye.y + Fx.range(-1.0, 3.0), Fx.eye.z + Fx.range(-6.0, 6.0), 0.0, 0.0, 0.0);
		}
	}

	/** The wall of the haboob, coming on: drawn along the stretch of its front nearest the player. */
	private static void wall(ClientDisaster d, double front, double gap, double across, double width, double ground, double speed, double wx,
			double wz) {
		float near = (float) Math.max(0.0, 1.0 - gap / 900.0);
		DisasterAudio.loop("sandwall" + d.id, DisasterSounds.WIND_HOWL, true, Math.min(1.0F, near * 1.2F),
				d.x + wx * front - wz * across, ground + 10.0, d.z + wz * front + wx * across);
		int count = Fx.budget(26, gap, 1000.0);
		double height = 100.0 + d.intensity * 12.0;
		for (int i = 0; i < count; i++) {
			double side = Math.max(-width / 2.0, Math.min(width / 2.0, across + Fx.random.nextGaussian() * 140.0));
			double depth = front - Fx.random.nextDouble() * 14.0;
			double up = Math.pow(Fx.random.nextDouble(), 0.7) * height;
			double x = d.x + wx * depth - wz * side;
			double z = d.z + wz * depth + wx * side;
			float tone = (float) Fx.range(0.6, 0.72);
			Fx.cloud(x, ground + up, z).velocity(wx * speed, Fx.range(0.02, 0.08), wz * speed).drag(1.0)
					.size((float) (Fx.range(9.0, 16.0) + up * 0.08)).color(tone, tone * 0.78F, tone * 0.55F).alpha(0.92F)
					.life(70 + Fx.random.nextInt(50)).grow(0.8F).spin((float) Fx.range(-0.02, 0.02)).spawn();
		}
	}

	/** The dust devils ahead of the storm. */
	private static void devils(ClientDisaster d, double ground) {
		int count = (int) d.extra(6);
		for (int i = 0; i < count; i++) {
			int index = i;
			double dx = d.extra(7 + i * 3);
			double dz = d.extra(8 + i * 3);
			double height = d.extra(9 + i * 3);
			double distance = Fx.horizontal(dx, dz);
			int spin = Fx.budget(7, distance, 400.0);
			DisasterParticle.Axis axis = new DisasterParticle.Axis() {
				@Override
				public boolean alive() {
					return !d.removed && (int) d.extra(6) > index;
				}

				@Override
				public double x(double h) {
					return d.extra(7 + index * 3);
				}

				@Override
				public double z(double h) {
					return d.extra(8 + index * 3);
				}
			};
			for (int k = 0; k < spin; k++) {
				double up = Fx.random.nextDouble() * height;
				double r = 1.0 + up / height * 2.8;
				float tone = (float) Fx.range(0.6, 0.72);
				Fx.cloud(dx, ground + up, dz).vortex(axis, Fx.random.nextDouble() * Math.PI * 2.0, r, -0.28, Fx.range(0.1, 0.2), 0.01)
						.size((float) Fx.range(0.8, 2.0)).color(tone, tone * 0.8F, tone * 0.58F).alpha(0.6F).life(30 + Fx.random.nextInt(20)).spawn();
			}
		}
	}
}
