package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterSounds;

import net.minecraft.core.BlockPos;

/**
 * Draws a blizzard: a whiteout inside it, the snow driving sideways in the gale, the world gone to a
 * few blocks of grey-white; from outside, a wall of white rolling toward you. Only where it is cold
 * enough to snow: elsewhere the same storm is just a hard rain.
 */
final class SnowFx {
	private SnowFx() {
	}

	static void tick(ClientDisaster d) {
		double visibility = d.extra(0);
		double windSpeed = d.extra(1);
		double distance = Fx.horizontal(d.x, d.z);
		BlockPos at = BlockPos.containing(Fx.eye);
		if (!Fx.level.getBiome(at).value().coldEnoughToSnow(at, Fx.level.getSeaLevel())) {
			return;
		}
		if (distance < d.radius) {
			float inside = (float) (Math.min(1.0, (d.radius - distance) / 40.0) * d.strength);
			DisasterClient.fog(0.96F * inside, 0.84F, 0.87F, 0.92F, (float) visibility);
			DisasterAudio.loop("blizzard" + d.id, DisasterSounds.BLIZZARD_WIND, false, inside, 0, 0, 0);
			int flakes = Fx.round(90 * inside * Fx.density);
			for (int i = 0; i < flakes; i++) {
				double gust = windSpeed * Fx.range(10.0, 20.0);
				Fx.flake(Fx.eye.x - d.dirX * 8.0 + Fx.range(-12.0, 12.0), Fx.eye.y + Fx.range(-3.0, 8.0), Fx.eye.z - d.dirZ * 8.0 + Fx.range(-12.0, 12.0))
						.velocity(d.dirX * gust + Fx.range(-0.05, 0.05), -Fx.range(0.04, 0.15), d.dirZ * gust + Fx.range(-0.05, 0.05)).drag(1.0)
						.size((float) Fx.range(0.06, 0.16)).color(0.96F, 0.97F, 1.0F).alpha(0.95F).life(15 + Fx.random.nextInt(15)).fade(0.1F, 0.2F).spawn();
			}
			int puffs = Fx.round(5 * inside * Fx.density);
			for (int i = 0; i < puffs; i++) {
				Fx.cloud(Fx.eye.x - d.dirX * 10.0 + Fx.range(-10.0, 10.0), Fx.eye.y + Fx.range(-2.0, 5.0), Fx.eye.z - d.dirZ * 10.0 + Fx.range(-10.0, 10.0))
						.velocity(d.dirX * windSpeed * 8.0, 0.0, d.dirZ * windSpeed * 8.0).drag(1.0).size((float) Fx.range(2.0, 4.0)).gray(0.95F)
						.alpha(0.25F).life(30).spawn();
			}
		} else if (distance < d.radius + 260.0) {
			// The storm seen coming: a wall of white along its edge.
			double ux = (Fx.eye.x - d.x) / distance;
			double uz = (Fx.eye.z - d.z) / distance;
			int count = Fx.budget(14, distance - d.radius, 300.0);
			for (int i = 0; i < count; i++) {
				double angle = Math.atan2(uz, ux) + Fx.random.nextGaussian() * 0.25;
				double x = d.x + Math.cos(angle) * d.radius;
				double z = d.z + Math.sin(angle) * d.radius;
				Fx.cloud(x, d.y + Fx.range(0.0, 60.0), z).velocity(d.dirX * windSpeed * 4.0, 0.0, d.dirZ * windSpeed * 4.0).drag(1.0)
						.size((float) Fx.range(10.0, 20.0)).color(0.86F, 0.88F, 0.92F).alpha(0.75F).life(80 + Fx.random.nextInt(60)).spawn();
			}
		}
	}
}
