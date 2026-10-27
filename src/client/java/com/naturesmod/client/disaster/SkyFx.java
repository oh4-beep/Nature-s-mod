package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterSounds;
import com.naturesmod.disaster.MeteorShower;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * A meteor shower's sky, and what it leaves on the ground. The shooting stars: brief streaks high
 * overhead, all running from the shower's radiant on parallel paths (so they seem to fan out from one
 * point of the sky), a bright head and a fading trail, and now and then a bright one, longer and
 * slower. Best seen at night; by day only the brightest show. The fireballs that come down among them
 * are the server's (see {@link DisasterEvents}). Then its fresh craters: smoke rising from their hot
 * floors as they cool, and close by sparks and the glow of the molten rock and the crackle of the fires
 * in them; the dust of a strike hanging in the air round it for a while; and over a great one a column
 * of dust boiling up into a mushroom cloud, seen from far off.
 *
 * <p>Everything goes through the particle budget, few and big; the sparks and the glow only close by.
 */
final class SkyFx {
	/** Fresh craters drawn at once, whatever the server says. */
	private static final int MAX_CRATERS = 8;

	private SkyFx() {
	}

	static void tick(ClientDisaster d) {
		stars(d);
		int told = (int) Math.max(0.0, Math.min(MAX_CRATERS, finite(d.extra(3), 0.0)));
		told = Math.min(told, Math.max(0, (d.extra.length - MeteorShower.CRATERS_AT) / MeteorShower.CRATER_FLOATS));
		for (int i = 0; i < told; i++) {
			int at = MeteorShower.CRATERS_AT + i * MeteorShower.CRATER_FLOATS;
			double x = d.extra(at);
			double y = d.extra(at + 1);
			double z = d.extra(at + 2);
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
				continue;
			}
			double radius = Math.max(1.0, Math.min(40.0, finite(d.extra(at + 3), 4.0)));
			double since = Math.max(0.0, finite(d.extra(at + 4), 0.0));
			crater(d, i, x, y, z, radius, since);
		}
	}

	private static void stars(ClientDisaster d) {
		Vec3 radiant = new Vec3(d.extra(0), d.extra(1), d.extra(2));
		double length = radiant.lengthSqr();
		if (!(length > 0.5) || !Double.isFinite(length)) {
			return;
		}
		radiant = radiant.normalize();
		float night = Fx.level.isDarkOutside() ? 1.0F : 0.2F;
		double rate = d.intensity * 0.11 * night * d.strength * Fx.density;
		int count = Fx.round(rate);
		for (int i = 0; i < count; i++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double distance = Fx.range(40.0, 260.0);
			// Now and then a bright one, lower, longer and slower: a fireball that burns up high.
			boolean bright = Fx.random.nextInt(14) == 0;
			Vec3 start = new Vec3(Fx.eye.x + Math.cos(angle) * distance, Fx.eye.y + (bright ? Fx.range(90.0, 170.0) : Fx.range(110.0, 200.0)),
					Fx.eye.z + Math.sin(angle) * distance);
			double trail = bright ? Fx.range(90.0, 160.0) : Fx.range(30.0, 90.0);
			int ticks = bright ? 14 + Fx.random.nextInt(8) : 6 + Fx.random.nextInt(8);
			boolean green = Fx.random.nextInt(4) == 0;
			float size = (float) (bright ? Fx.range(1.6, 2.6) : Fx.range(0.5, 1.2));
			DisasterEvents.streak(start, radiant.scale(trail / ticks), ticks, size, green);
		}
	}

	/** A fresh crater, `since` ticks after it struck. */
	private static void crater(ClientDisaster d, int index, double x, double y, double z, double radius, double since) {
		double life = MeteorShower.SMOULDER_TICKS;
		if (since >= life) {
			return;
		}
		double heat = 1.0 - since / life;
		double depth = radius * 0.6 + 1.0;
		double floor = y - 1.0 - depth * 0.8;
		double distance = Fx.distance(x, floor, z);
		// Smoke rising from the hot floor, thinning as it cools.
		int smoke = share(0.8 * heat * (0.5 + radius / 16.0), distance, 900.0);
		for (int k = 0; k < smoke; k++) {
			float tone = (float) Fx.range(0.2, 0.32);
			Fx.cloud(x + Fx.range(-radius, radius) * 0.4, floor + Fx.range(0.5, 2.0), z + Fx.range(-radius, radius) * 0.4)
					.velocity(Fx.range(-0.02, 0.02), Fx.range(0.12, 0.24) * (0.6 + 0.4 * heat), Fx.range(-0.02, 0.02)).drag(0.99)
					.size((float) (Fx.range(2.5, 4.5) * (0.7 + radius / 20.0))).color(tone, tone * 0.95F, tone * 0.9F).alpha((float) (0.45 + 0.3 * heat))
					.life(120 + Fx.random.nextInt(80)).grow(2.5F).fade(0.08F, 0.5F).spawn();
		}
		if (distance < 72.0 && Fx.spawned < Fx.cap) {
			// Sparks off the molten rock, and its glow.
			int sparks = Fx.round(1.4 * heat * heat * Fx.density);
			for (int k = 0; k < sparks; k++) {
				Fx.spark(x + Fx.range(-radius, radius) * 0.45, floor + Fx.range(0.2, 1.0), z + Fx.range(-radius, radius) * 0.45)
						.velocity(Fx.range(-0.03, 0.03), Fx.range(0.05, 0.16), Fx.range(-0.03, 0.03)).drag(0.97).size((float) Fx.range(0.08, 0.2))
						.color(1.0F, (float) Fx.range(0.35, 0.6), 0.1F).glow().alpha(1.0F).life(18 + Fx.random.nextInt(20)).spawn();
			}
			if (heat > 0.4 && Fx.random.nextInt(3) == 0) {
				Fx.cloud(x + Fx.range(-radius, radius) * 0.3, floor + 0.6, z + Fx.range(-radius, radius) * 0.3).size((float) (radius * 0.35))
						.color(1.0F, 0.38F, 0.1F).glow().alpha((float) (0.22 * heat)).life(10).fade(0.3F, 0.5F).spawn();
			}
			if (heat > 0.5 && Fx.random.nextInt(8) == 0) {
				Fx.level.addParticle(ParticleTypes.LAVA, x + Fx.range(-radius, radius) * 0.3, floor + 0.5, z + Fx.range(-radius, radius) * 0.3, 0.0, 0.0,
						0.0);
			}
		}
		// The crackle of the fires in it.
		if (distance < 40.0 && heat > 0.3) {
			DisasterAudio.loop("crater" + d.id + "." + index, DisasterSounds.FIRE_ROAR, true, (float) (0.4 * heat * (1.0 - distance / 40.0)), x,
					floor + 1.0, z);
		}
		if (since < 300.0) {
			dust(x, y, z, radius, since);
		}
		if (radius >= 14.0 && since < 600.0) {
			mushroom(x, y, z, radius, since);
		}
	}

	/** For the first quarter of a minute the dust of a strike hangs in the air round it, settling. */
	private static void dust(double x, double y, double z, double radius, double since) {
		double fresh = 1.0 - since / 300.0;
		double reach = radius * 5.0 + 20.0;
		double h = Fx.horizontal(x, z);
		if (h > reach || Math.abs(Fx.eye.y - y) > reach) {
			return;
		}
		double near = 1.0 - h / reach;
		DisasterClient.fog((float) (0.6 * fresh * near), 0.5F, 0.44F, 0.37F, (float) (24.0 + 80.0 * (1.0 - fresh * near)));
		int motes = Fx.spawned < Fx.cap ? Fx.round(2.5 * fresh * near * Fx.density) : 0;
		for (int k = 0; k < motes; k++) {
			float tone = (float) Fx.range(0.42, 0.55);
			Fx.cloud(Fx.eye.x + Fx.range(-14.0, 14.0), Fx.eye.y + Fx.range(-3.0, 5.0), Fx.eye.z + Fx.range(-14.0, 14.0))
					.velocity(Fx.range(-0.03, 0.03), Fx.range(-0.03, 0.0), Fx.range(-0.03, 0.03)).size((float) Fx.range(2.0, 3.5))
					.color(tone, tone * 0.92F, tone * 0.82F).alpha(0.3F).life(50 + Fx.random.nextInt(30)).fade(0.3F, 0.4F).spawn();
		}
	}

	/** Over a great strike, a column of dust boiling up and spreading at the top into a mushroom cloud. */
	private static void mushroom(double x, double y, double z, double radius, double since) {
		double fresh = 1.0 - since / 600.0;
		double height = Math.min(120.0, 25.0 + since * 0.35);
		double distance = Fx.distance(x, y + height * 0.5, z);
		int count = share(2.4 * fresh, distance, 2000.0);
		double scale = radius / 20.0;
		for (int k = 0; k < count; k++) {
			float tone = (float) Fx.range(0.36, 0.5);
			if (Fx.random.nextInt(5) < 3) {
				// The stem.
				double t = Fx.random.nextDouble();
				Fx.cloud(x + Fx.range(-radius, radius) * 0.25, y + 2.0 + t * height, z + Fx.range(-radius, radius) * 0.25)
						.velocity(Fx.range(-0.02, 0.02), Fx.range(0.08, 0.2), Fx.range(-0.02, 0.02)).drag(0.99)
						.size((float) (Fx.range(6.0, 10.0) * scale + 2.0)).color(tone, tone * 0.9F, tone * 0.8F).alpha(0.7F)
						.life(160 + Fx.random.nextInt(80)).grow(1.6F).fade(0.06F, 0.5F).spawn();
			} else {
				// The cap, rolling outward.
				double angle = Fx.random.nextDouble() * Math.PI * 2.0;
				double out = Fx.range(0.0, radius * 1.4 + since * 0.03);
				double speed = Fx.range(0.04, 0.1);
				Fx.cloud(x + Math.cos(angle) * out, y + height + Fx.range(-6.0, 6.0), z + Math.sin(angle) * out)
						.velocity(Math.cos(angle) * speed, Fx.range(0.0, 0.06), Math.sin(angle) * speed).drag(0.99)
						.size((float) (Fx.range(9.0, 15.0) * scale + 3.0)).color(tone, tone * 0.9F, tone * 0.8F).alpha(0.7F)
						.life(200 + Fx.random.nextInt(100)).grow(1.8F).fade(0.06F, 0.5F).spawn();
			}
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
