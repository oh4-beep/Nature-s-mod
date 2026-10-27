package com.naturesmod.client.disaster;

import java.util.HashSet;
import java.util.Set;

import com.naturesmod.disaster.Avalanche;
import com.naturesmod.disaster.DisasterSounds;

import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Draws an avalanche. First the crack of the slab breaking away, heard as far off as it carries (once
 * its sound has got here) and felt close by, and snow dust puffing up along the fracture; then its
 * front racing down the mountain: a billowing wall of white powder as wide as the flow, rolling out
 * ahead of it and boiling up the higher the faster it goes, lumps of snow flung out of its churning
 * foot, and behind it the cloud lifting off the track and hanging in the air; its roar, and close by
 * the ground trembling. Caught in it the world goes white a few blocks off and the snow drives past.
 * Once it stops its cloud sinks over the debris and drifts off, thinning away. A landslide is the same
 * in dust and grit (brown over earth, grey over rock, tan over sand, rust over clay), lower and heavier.
 *
 * <p>Few particles, and big ones, all through the budget: the front and its cloud are a few dozen
 * billows a tick at most, fewer the further off it is; the lumps are drawn only close by, and the snow
 * driving past only about a viewer inside it.
 */
final class AvalancheFx {
	/** How far off it is drawn at all. */
	private static final double FAR = 900.0;
	/** The avalanches whose crack this client has heard (themselves, not their numbers, which start again with a new server). */
	private static final Set<ClientDisaster> HEARD = new HashSet<>();

	private AvalancheFx() {
	}

	static void tick(ClientDisaster d) {
		int kind = Math.max(0, Math.min(4, (int) finite(d.extra(0), Avalanche.SNOW)));
		int phase = Math.max(0, Math.min(2, (int) finite(d.extra(1), Avalanche.CRACKING)));
		double speed = Math.max(0.0, Math.min(3.0, finite(d.extra(2), 0.0)));
		double half = Math.max(2.0, Math.min(40.0, finite(d.extra(3), 8.0)));
		double body = Math.max(4.0, Math.min(60.0, finite(d.extra(4), 14.0)));
		boolean told = d.extra.length > 7;
		double rx = told ? finite(d.extra(5), d.x) : d.x;
		double ry = told ? finite(d.extra(6), d.y) : d.y;
		double rz = told ? finite(d.extra(7), d.z) : d.z;
		double since = finite(d.extra(8), -1.0);
		double cloud = Math.max(2.0, Math.min(40.0, finite(d.extra(9), 8.0)));
		int grade = Math.max(1, Math.min(5, (int) Math.round(finite(d.extra(10), 3.0))));
		double ux = d.dirX;
		double uz = d.dirZ;
		double length = Math.sqrt(ux * ux + uz * uz);
		if (length > 0.01) {
			ux /= length;
			uz /= length;
		} else {
			ux = 1.0;
			uz = 0.0;
		}
		float[] tone = tone(kind);
		crack(d, kind, phase, rx, ry, rz, half, ux, uz, tone);
		// Its front a little ahead of where it was last heard of: the server's word comes every other tick, and this
		// client eases toward it.
		double lead = phase == Avalanche.RUNNING ? speed * 2.5 : 0.0;
		double fx = d.x + ux * lead;
		double fz = d.z + uz * lead;
		double fy = d.y;
		double distance = Fx.horizontal(fx, fz);
		if (phase == Avalanche.RUNNING) {
			roar(d, kind, fx, fy, fz, speed, distance, grade);
			front(kind, fx, fy, fz, ux, uz, half, body, speed, cloud, grade, tone, distance);
			inside(d, kind, fx, fy, fz, ux, uz, half, body, speed, cloud, tone);
		} else if (phase == Avalanche.SETTLING) {
			settle(d, fx, fy, fz, ux, uz, half, since, cloud, grade, tone, distance);
		}
		if (HEARD.size() > 16) {
			HEARD.removeIf(known -> DisasterClient.get(known.id) != known);
		}
	}

	private static double finite(double value, double otherwise) {
		return Double.isFinite(value) ? value : otherwise;
	}

	/** The colour of its cloud: white snow; for a landslide brown earth, grey rock, tan sand or rust-red clay. */
	private static float[] tone(int kind) {
		return switch (kind) {
			case Avalanche.EARTH -> new float[] {0.5F, 0.41F, 0.31F};
			case Avalanche.ROCK -> new float[] {0.56F, 0.54F, 0.51F};
			case Avalanche.SAND -> new float[] {0.82F, 0.71F, 0.52F};
			case Avalanche.CLAY -> new float[] {0.7F, 0.45F, 0.32F};
			default -> new float[] {0.95F, 0.97F, 1.0F};
		};
	}

	private static DisasterParticle tinted(DisasterParticle p, float[] tone, float shade) {
		return p.color(Math.min(1.0F, tone[0] * shade), Math.min(1.0F, tone[1] * shade), Math.min(1.0F, tone[2] * shade));
	}

	/** The height of the ground at (x, z) as this client has it, where that is near where the flow is (otherwise, there). */
	private static double floor(double x, double z, double near) {
		int top = Fx.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
		return top > Fx.level.getMinY() + 1 && Math.abs(top - near) < 12.0 ? top : near;
	}

	/**
	 * The slab breaking away: its crack heard once, when the sound of it gets here, as far off as it carries, and a jolt
	 * felt close by; while it is breaking, dust puffing up along the fracture across the slope.
	 */
	private static void crack(ClientDisaster d, int kind, int phase, double rx, double ry, double rz, double half, double ux, double uz,
			float[] tone) {
		double distance = Fx.distance(rx, ry, rz);
		if (d.age < 240 && !HEARD.contains(d) && d.age >= distance / 17.15) {
			HEARD.add(d);
			float loud = (float) Math.max(0.0, 1.0 - distance / 700.0);
			if (loud > 0.02F) {
				// A sharp crack for a slab of snow, a deep one for the ground giving way.
				float pitch = (float) (kind == Avalanche.SNOW ? Fx.range(1.4, 1.6) : Fx.range(0.8, 0.95));
				DisasterAudio.around(DisasterSounds.QUAKE_JOLT, loud, pitch);
			}
			if (distance < 120.0) {
				CameraShake.jolt(0.15 * (1.0 - distance / 120.0));
			}
		}
		if (phase != Avalanche.CRACKING) {
			return;
		}
		int count = Fx.budget(8, distance, 600.0);
		for (int i = 0; i < count; i++) {
			double side = Fx.range(-half, half);
			double px = rx - uz * side;
			double pz = rz + ux * side;
			tinted(Fx.cloud(px, floor(px, pz, ry) + Fx.range(0.0, 1.0), pz), tone, (float) Fx.range(0.9, 1.0))
					.velocity(ux * 0.04, Fx.range(0.03, 0.09), uz * 0.04).drag(0.97).size((float) Fx.range(1.2, 2.6)).alpha(0.7F)
					.life(40 + Fx.random.nextInt(30)).grow(1.6F).fade(0.1F, 0.5F).spawn();
		}
	}

	/** Its roar, from its front, and close by the ground trembling under it (more under a landslide). */
	private static void roar(ClientDisaster d, int kind, double fx, double fy, double fz, double speed, double distance, int grade) {
		double pace = Math.min(1.0, speed / 1.2);
		float loud = (float) ((0.35 + 0.65 * pace) * Math.max(0.0, 1.0 - distance / 700.0));
		DisasterAudio.loop("avalanche" + d.id, DisasterSounds.AVALANCHE_ROAR, true, loud, fx, fy + 2.0, fz);
		double reach = 50.0 + 12.0 * grade;
		if (distance < reach) {
			double near = 1.0 - distance / reach;
			CameraShake.shake(near * near * (0.025 + 0.012 * grade) * (0.4 + 0.6 * pace) * (kind == Avalanche.SNOW ? 1.0 : 1.4));
			DisasterAudio.loop("avalancherumble" + d.id, DisasterSounds.QUAKE_RUMBLE, false, (float) (near * 0.6 * (0.4 + 0.6 * pace)), 0, 0, 0);
		}
	}

	/**
	 * The running front: a wall of billows rolling out a little ahead of it across its width and boiling up, the cloud
	 * behind it lifting off the track and hanging there, and close by lumps flung out of its foot.
	 */
	private static void front(int kind, double fx, double fy, double fz, double ux, double uz, double half, double body, double speed, double cloud,
			int grade, float[] tone, double distance) {
		boolean snow = kind == Avalanche.SNOW;
		double pace = Math.min(1.0, speed / 1.2);
		int budget = Fx.round(Fx.budget(16 + 5 * grade, distance, FAR) * (0.4 + 0.6 * pace));
		int wall = (int) Math.ceil(budget * 0.65);
		for (int i = 0; i < wall; i++) {
			double side = Fx.range(-half, half);
			double ahead = Fx.range(-2.0, 3.0);
			double px = fx + ux * ahead - uz * side;
			double pz = fz + uz * ahead + ux * side;
			double rise = Math.pow(Fx.random.nextDouble(), 1.7) * cloud * 0.5;
			double roll = speed * Fx.range(0.7, 1.05);
			tinted(Fx.cloud(px, floor(px, pz, fy) + rise, pz), tone, (float) Fx.range(0.86, 1.0))
					.velocity(ux * roll, Fx.range(0.02, 0.08) + 0.06 * pace, uz * roll).drag(0.965).size((float) Fx.range(3.0, 5.5 + grade))
					.alpha(snow ? 0.9F : 0.85F).life(35 + Fx.random.nextInt(35)).grow(1.8F).fade(0.05F, 0.5F).spin((float) Fx.range(-0.03, 0.03))
					.spawn();
		}
		int trail = budget - wall;
		for (int i = 0; i < trail; i++) {
			double back = Math.pow(Fx.random.nextDouble(), 0.8) * body * 1.5;
			double side = Math.max(-half, Math.min(half, Fx.random.nextGaussian() * half * 0.5));
			double px = fx - ux * back - uz * side;
			double pz = fz - uz * back + ux * side;
			// Behind the front is uphill.
			double ground = floor(px, pz, fy + back * 0.3);
			tinted(Fx.cloud(px, ground + Fx.range(cloud * 0.2, cloud), pz), tone, (float) Fx.range(0.82, 0.97))
					.velocity(ux * speed * 0.2, Fx.range(0.03, 0.1), uz * speed * 0.2).drag(0.985).size((float) Fx.range(6.0, 9.0 + 2.0 * grade))
					.alpha(snow ? 0.7F : 0.6F).life(70 + Fx.random.nextInt(50)).grow(2.0F).fade(0.1F, 0.55F).spawn();
		}
		int lumps = Fx.budget(snow ? 5 + grade : 7 + grade, distance, 96.0);
		for (int i = 0; i < lumps; i++) {
			double side = Fx.range(-half, half);
			double px = fx - uz * side;
			double pz = fz + ux * side;
			double fling = speed * Fx.range(0.8, 1.3);
			float shade = (float) (snow ? Fx.range(0.9, 1.0) : Fx.range(0.6, 0.85));
			tinted(Fx.debris(px, floor(px, pz, fy) + Fx.range(0.2, 1.5), pz), tone, shade)
					.velocity(ux * fling + Fx.range(-0.1, 0.1), Fx.range(0.15, 0.45), uz * fling + Fx.range(-0.1, 0.1)).fall(0.04).drag(0.98)
					.size((float) Fx.range(0.15, 0.4)).alpha(1.0F).life(25 + Fx.random.nextInt(20)).spin((float) Fx.range(-0.4, 0.4)).spawn();
		}
	}

	/**
	 * Caught in its flow, or in the cloud just behind its front: the air white (or brown) and thick, nothing to be seen a
	 * few blocks off, the snow (or grit) driving past and the ground heaving.
	 */
	private static void inside(ClientDisaster d, int kind, double fx, double fy, double fz, double ux, double uz, double half, double body,
			double speed, double cloud, float[] tone) {
		double ex = Fx.eye.x - fx;
		double ez = Fx.eye.z - fz;
		double along = ex * ux + ez * uz;
		double across = -ex * uz + ez * ux;
		double above = Fx.eye.y - fy;
		if (along > 4.0 || along < -body * 2.5 || Math.abs(across) > half + 4.0 || above > cloud + 6.0 + Math.max(0.0, -along) || above < -12.0) {
			return;
		}
		double thick = along > -body ? 1.0 : 1.0 - (-along - body) / (body * 1.5);
		thick *= Math.min(1.0, (half + 4.0 - Math.abs(across)) / 4.0);
		thick *= (0.4 + 0.6 * Math.min(1.0, speed / 1.2)) * Math.max(0.0F, Math.min(1.0F, d.strength));
		float amount = (float) Math.max(0.0, Math.min(1.0, thick));
		if (amount < 0.02F) {
			return;
		}
		if (along > -body) {
			CameraShake.shake(0.08 * amount);
		}
		// Under a roof (or buried) the air only thickens a little, and nothing drives past.
		int roof = Fx.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(Fx.eye.x), (int) Math.floor(Fx.eye.z));
		if (roof > Fx.eye.y + 0.5) {
			DisasterClient.fog(0.4F * amount, tone[0] * 0.95F, tone[1] * 0.95F, tone[2] * 0.95F, (float) (12.0 + 40.0 * (1.0 - amount)));
			return;
		}
		DisasterClient.fog(0.97F * amount, tone[0] * 0.95F, tone[1] * 0.95F, tone[2] * 0.95F, (float) (3.0 + 30.0 * (1.0 - amount)));
		boolean snow = kind == Avalanche.SNOW;
		int count = Fx.round(16 * amount * Fx.density);
		for (int i = 0; i < count; i++) {
			double rush = speed * Fx.range(1.0, 1.6) + 0.2;
			double px = Fx.eye.x - ux * 4.0 + Fx.range(-5.0, 5.0);
			double py = Fx.eye.y + Fx.range(-1.5, 2.5);
			double pz = Fx.eye.z - uz * 4.0 + Fx.range(-5.0, 5.0);
			DisasterParticle p = snow ? Fx.flake(px, py, pz) : Fx.debris(px, py, pz);
			tinted(p, tone, (float) Fx.range(0.9, 1.05)).velocity(ux * rush, Fx.range(-0.05, 0.08), uz * rush).drag(1.0)
					.size((float) (snow ? Fx.range(0.08, 0.16) : Fx.range(0.04, 0.09))).alpha(0.9F).life(10 + Fx.random.nextInt(8)).spawn();
		}
	}

	/** Come to rest: its cloud sinking over the debris and drifting off, thinning away, and its roar dying. */
	private static void settle(ClientDisaster d, double fx, double fy, double fz, double ux, double uz, double half, double since, double cloud,
			int grade, float[] tone, double distance) {
		double fade = Math.max(0.0, 1.0 - Math.max(0.0, since) / 80.0);
		if (fade <= 0.0) {
			return;
		}
		DisasterAudio.loop("avalanche" + d.id, DisasterSounds.AVALANCHE_ROAR, true, (float) (0.4 * fade * Math.max(0.0, 1.0 - distance / 700.0)), fx,
				fy + 2.0, fz);
		int count = Fx.round(Fx.budget(8 + 2 * grade, distance, 700.0) * fade);
		for (int i = 0; i < count; i++) {
			double a = Fx.random.nextGaussian() * half * 0.5;
			double c = Fx.random.nextGaussian() * half * 0.6;
			double px = fx + ux * a - uz * c;
			double pz = fz + uz * a + ux * c;
			tinted(Fx.cloud(px, floor(px, pz, fy) + Fx.range(0.5, Math.max(1.0, cloud * 0.6)), pz), tone, (float) Fx.range(0.85, 1.0))
					.velocity(ux * 0.04 + Fx.range(-0.02, 0.02), Fx.range(-0.015, 0.02), uz * 0.04 + Fx.range(-0.02, 0.02)).drag(0.99)
					.size((float) Fx.range(5.0, 9.0 + grade)).alpha((float) (0.25 + 0.4 * fade)).life(80 + Fx.random.nextInt(60)).grow(1.2F)
					.fade(0.15F, 0.6F).spawn();
		}
		if (distance < half + 8.0 && Math.abs(Fx.eye.y - fy) < cloud + 6.0) {
			DisasterClient.fog((float) (0.6 * fade), tone[0], tone[1], tone[2], (float) (8.0 + 50.0 * (1.0 - fade)));
		}
	}
}
