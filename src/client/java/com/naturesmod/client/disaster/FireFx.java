package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterSounds;
import com.naturesmod.disaster.Wildfire;

import net.minecraft.core.particles.ParticleTypes;

/**
 * Draws a wildfire: flames licking up out of the burning close by and embers whirling out of them, now
 * and then one lofted high and carried far downwind; smoke rising all along the front, leaning away
 * downwind; over the whole fire a great plume, climbing and bending downwind, seen from far off; and
 * downwind of it the air brown with smoke, and glowing orange in the thick of the flames. Its roar
 * comes from the nearest of its fires.
 *
 * <p>The server sends the burning nearest each player and a sample of all of it. Everything here goes
 * through the particle budget, few and big: the smoke is shared out among the spots, the flames and the
 * embers are drawn only close by.
 */
final class FireFx {
	private FireFx() {
	}

	static void tick(ClientDisaster d) {
		double windSpeed = Math.max(0.0, Math.min(60.0, finite(d.extra(0), 6.0)));
		int burning = (int) Math.max(0.0, Math.min(100000.0, finite(d.extra(1), 0.0)));
		double cx = finite(d.extra(2), d.x);
		double cy = finite(d.extra(3), d.y);
		double cz = finite(d.extra(4), d.z);
		double spread = Math.max(1.0, Math.min(400.0, finite(d.extra(5), 4.0)));
		int spots = (int) Math.max(0.0, Math.min(256.0, finite(d.extra(6), 0.0)));
		spots = Math.min(spots, Math.max(0, (d.extra.length - Wildfire.SPOTS_AT) / 3));
		if (burning <= 0 || spots == 0) {
			return;
		}
		double windX = d.dirX;
		double windZ = d.dirZ;
		double windLength = Math.sqrt(windX * windX + windZ * windZ);
		if (!(windLength > 0.01)) {
			windX = 1.0;
			windZ = 0.0;
		} else {
			windX /= windLength;
			windZ /= windLength;
		}
		double drift = windSpeed * 0.012;
		plume(cx, cy, cz, spread, burning, windX, windZ, drift, d.strength);
		double nearest = Double.MAX_VALUE;
		double nx = 0.0;
		double ny = 0.0;
		double nz = 0.0;
		// Some seven billows of smoke a tick near the fire, shared out among its spots.
		double smokeEach = 7.0 / spots;
		for (int i = 0; i < spots; i++) {
			int at = Wildfire.SPOTS_AT + i * 3;
			double sx = d.extra(at);
			double sy = d.extra(at + 1);
			double sz = d.extra(at + 2);
			if (!Double.isFinite(sx) || !Double.isFinite(sy) || !Double.isFinite(sz)) {
				continue;
			}
			double distance = Fx.horizontal(sx, sz);
			if (distance < nearest) {
				nearest = distance;
				nx = sx;
				ny = sy;
				nz = sz;
			}
			smoke(sx, sy, sz, distance, smokeEach, windX, windZ, drift);
			if (distance < 56.0) {
				flames(sx, sy, sz, windX, windZ, drift);
			}
			if (distance < 96.0) {
				embers(sx, sy, sz, windX, windZ, drift, windSpeed);
			}
			if (distance < 24.0 && Fx.random.nextInt(3) == 0) {
				Fx.level.addParticle(ParticleTypes.LARGE_SMOKE, sx + Fx.range(-1.5, 1.5), sy + 1.0, sz + Fx.range(-1.5, 1.5), 0.0, 0.05, 0.0);
			}
		}
		if (nearest < 150.0) {
			float volume = (float) (Math.min(1.0, burning / 40.0 + 0.3) * d.strength);
			DisasterAudio.loop("fire" + d.id, DisasterSounds.FIRE_ROAR, true, volume, nx, ny + 1.0, nz);
		}
		air(cx, cz, spread, burning, nearest, windX, windZ, d.strength);
	}

	/** The plume over the whole fire: big billows climbing from all over it and bending away downwind, seen from far off. */
	private static void plume(double cx, double cy, double cz, double spread, int burning, double windX, double windZ, double drift, float strength) {
		double distance = Fx.distance(cx, cy + 40.0, cz);
		int count = share((0.35 + Math.min(1.0, burning / 300.0)) * strength, distance, 1600.0);
		double scale = 0.6 + Math.min(1.0, spread / 50.0) * 0.8;
		for (int k = 0; k < count; k++) {
			float tone = (float) Fx.range(0.26, 0.38);
			Fx.cloud(cx + Fx.range(-spread, spread) * 0.6, cy + Fx.range(4.0, 14.0), cz + Fx.range(-spread, spread) * 0.6)
					.velocity(windX * drift, Fx.range(0.28, 0.45), windZ * drift).drag(0.995).size((float) (Fx.range(8.0, 13.0) * scale))
					.color(tone, tone * 0.93F, tone * 0.85F).alpha(0.6F).life(200 + Fx.random.nextInt(80)).grow(2.2F).fade(0.06F, 0.5F).spawn();
		}
	}

	private static void smoke(double sx, double sy, double sz, double distance, double expected, double windX, double windZ, double drift) {
		int smoke = share(expected, distance, 900.0);
		for (int k = 0; k < smoke; k++) {
			float tone = (float) Fx.range(0.24, 0.36);
			Fx.cloud(sx + Fx.range(-2.0, 2.0), sy + Fx.range(1.0, 3.0), sz + Fx.range(-2.0, 2.0))
					.velocity(windX * drift, Fx.range(0.18, 0.34), windZ * drift).drag(0.99).size((float) Fx.range(4.0, 7.0))
					.color(tone, tone * 0.92F, tone * 0.84F).alpha(0.65F).life(100 + Fx.random.nextInt(70)).grow(2.5F).fade(0.08F, 0.5F).spawn();
		}
	}

	/** Flames licking up out of the burning, leaning with the wind. */
	private static void flames(double sx, double sy, double sz, double windX, double windZ, double drift) {
		int flames = Fx.spawned < Fx.cap ? Fx.round(1.3 * Fx.density) : 0;
		for (int k = 0; k < flames; k++) {
			Fx.cloud(sx + Fx.range(-1.4, 1.4), sy + Fx.range(0.3, 1.8), sz + Fx.range(-1.4, 1.4))
					.velocity(windX * drift * 0.5 + Fx.range(-0.02, 0.02), Fx.range(0.05, 0.14), windZ * drift * 0.5 + Fx.range(-0.02, 0.02)).drag(0.96)
					.size((float) Fx.range(1.0, 2.2)).color(1.0F, (float) Fx.range(0.42, 0.72), (float) Fx.range(0.06, 0.18)).glow().alpha(0.85F)
					.life(9 + Fx.random.nextInt(9)).grow(0.5F).fade(0.15F, 0.6F).spawn();
		}
	}

	/** Embers whirling up out of the flames; now and then one lofted high and carried far downwind, as the ones that start the spot fires are. */
	private static void embers(double sx, double sy, double sz, double windX, double windZ, double drift, double windSpeed) {
		int embers = Fx.spawned < Fx.cap ? Fx.round(Fx.density) : 0;
		for (int k = 0; k < embers; k++) {
			Fx.spark(sx + Fx.range(-2.5, 2.5), sy + Fx.range(0.5, 3.0), sz + Fx.range(-2.5, 2.5))
					.velocity(windX * drift * 2.0 + Fx.range(-0.05, 0.05), Fx.range(0.08, 0.25), windZ * drift * 2.0 + Fx.range(-0.05, 0.05)).drag(0.98)
					.size((float) Fx.range(0.08, 0.2)).color(1.0F, (float) Fx.range(0.4, 0.65), 0.1F).glow().alpha(1.0F).life(20 + Fx.random.nextInt(25))
					.spawn();
		}
		if (Fx.random.nextInt(10) == 0 && Fx.spawned < Fx.cap) {
			double carry = windSpeed * 0.02;
			Fx.spark(sx, sy + 2.0, sz).velocity(windX * carry, Fx.range(0.3, 0.5), windZ * carry).drag(0.985).fall(0.006)
					.size((float) Fx.range(0.15, 0.25)).color(1.0F, 0.55F, 0.12F).glow().alpha(1.0F).life(70 + Fx.random.nextInt(40)).fade(0.05F, 0.4F)
					.spawn();
		}
	}

	/** Downwind of the fire the smoke hangs in the air, brown; in the thick of the flames it glows orange. */
	private static void air(double cx, double cz, double spread, int burning, double nearest, double windX, double windZ, float strength) {
		double ex = Fx.eye.x - cx;
		double ez = Fx.eye.z - cz;
		double along = ex * windX + ez * windZ;
		double across = Math.abs(-ex * windZ + ez * windX);
		double width = spread + 20.0 + Math.max(0.0, along) * 0.35;
		if (along > -spread - 10.0 && along < 600.0 && across < width) {
			float thick = (float) (0.7 * (1.0 - Math.max(0.0, along) / 600.0) * (1.0 - across / width) * Math.min(1.0, burning / 30.0 + 0.2) * strength);
			DisasterClient.fog(thick, 0.52F, 0.42F, 0.32F, 50.0F);
		}
		if (nearest < 12.0) {
			DisasterClient.fog((float) (0.55 * (1.0 - nearest / 12.0) * strength), 0.78F, 0.45F, 0.22F, 22.0F);
		}
	}

	/** How many particles an effect may spend this tick: `expected` close by (a fraction is a chance), fewer the further off, none past `far`. */
	private static int share(double expected, double distance, double far) {
		if (!(expected > 0.0) || distance > far || Fx.spawned >= Fx.cap) {
			return 0;
		}
		double near = 1.0 - distance / far;
		return Math.min(Fx.cap - Fx.spawned, Fx.round(expected * Fx.density * (0.25 + 0.75 * near)));
	}

	private static double finite(double value, double fallback) {
		return Double.isFinite(value) ? value : fallback;
	}
}
