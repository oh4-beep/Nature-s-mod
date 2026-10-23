package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterSounds;

import net.minecraft.core.particles.ParticleTypes;

/**
 * Draws a wildfire: columns of smoke rising from its hottest spots and leaning away downwind into a
 * plume seen from far off, embers whirling up out of the flames, and downwind of it the air brown
 * with smoke. Its roar comes from the nearest of its fires.
 */
final class FireFx {
	private FireFx() {
	}

	static void tick(ClientDisaster d) {
		double windSpeed = d.extra(0);
		int burning = (int) d.extra(1);
		int spots = (d.extra.length - 2) / 3;
		double drift = windSpeed * 0.012;
		double nearest = Double.MAX_VALUE;
		double nx = 0.0;
		double ny = 0.0;
		double nz = 0.0;
		for (int i = 0; i < spots; i++) {
			double sx = d.extra(2 + i * 3);
			double sy = d.extra(3 + i * 3);
			double sz = d.extra(4 + i * 3);
			double distance = Fx.horizontal(sx, sz);
			if (distance < nearest) {
				nearest = distance;
				nx = sx;
				ny = sy;
				nz = sz;
			}
			int smoke = Fx.budget(2, distance, 800.0);
			for (int k = 0; k < smoke; k++) {
				float tone = (float) Fx.range(0.24, 0.36);
				Fx.cloud(sx + Fx.range(-2.0, 2.0), sy + Fx.range(1.0, 3.0), sz + Fx.range(-2.0, 2.0))
						.velocity(d.dirX * drift, Fx.range(0.2, 0.38), d.dirZ * drift).drag(0.99).size((float) Fx.range(4.0, 7.0))
						.color(tone, tone * 0.92F, tone * 0.84F).alpha(0.7F).life(110 + Fx.random.nextInt(80)).grow(2.5F).fade(0.08F, 0.5F).spawn();
			}
			if (distance < 96.0) {
				int embers = Fx.round(2 * Fx.density);
				for (int k = 0; k < embers; k++) {
					Fx.spark(sx + Fx.range(-2.5, 2.5), sy + Fx.range(0.5, 3.0), sz + Fx.range(-2.5, 2.5))
							.velocity(d.dirX * drift * 2 + Fx.range(-0.05, 0.05), Fx.range(0.08, 0.25), d.dirZ * drift * 2 + Fx.range(-0.05, 0.05))
							.drag(0.98).size((float) Fx.range(0.08, 0.2)).color(1.0F, (float) Fx.range(0.4, 0.65), 0.1F).glow().alpha(1.0F)
							.life(20 + Fx.random.nextInt(25)).spawn();
				}
			}
			if (distance < 30.0) {
				Fx.level.addParticle(ParticleTypes.LARGE_SMOKE, sx + Fx.range(-1.5, 1.5), sy + 1.0, sz + Fx.range(-1.5, 1.5), 0.0, 0.05, 0.0);
			}
		}
		if (nearest < 150.0) {
			float volume = (float) (Math.min(1.0, burning / 40.0 + 0.3) * d.strength);
			DisasterAudio.loop("fire" + d.id, DisasterSounds.FIRE_ROAR, true, volume, nx, ny + 1.0, nz);
		}
		// Downwind of the fire the smoke hangs in the air.
		if (spots > 0 && nearest < 240.0) {
			double ex = Fx.eye.x - nx;
			double ez = Fx.eye.z - nz;
			double along = ex * d.dirX + ez * d.dirZ;
			double across = Math.abs(-ex * d.dirZ + ez * d.dirX);
			if (along > -12.0 && across < 30.0 + Math.max(0.0, along) * 0.35) {
				float thick = (float) (0.7 * (1.0 - nearest / 240.0) * Math.min(1.0, burning / 30.0 + 0.2));
				DisasterClient.fog(thick, 0.52F, 0.42F, 0.32F, 55.0F);
			}
		}
	}
}
