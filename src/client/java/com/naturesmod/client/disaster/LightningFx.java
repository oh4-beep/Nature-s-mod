package com.naturesmod.client.disaster;

import java.util.HashMap;
import java.util.Map;

import com.naturesmod.disaster.DisasterSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a lightning storm. Under it the air is dark and close, a ragged cloud base hanging low overhead and
 * the gusts of its outflow howling; lightning crawls along the underside of the cloud, more of it in a burst,
 * lighting the cloud from within, and its thunder rolls in after (the strikes to the ground are the game's own
 * bolts, with their own flash and thunder). From outside, a dark wall of cloud along its edge. Where a strike
 * is about to come down the air crackles, sparks creeping up from the ground; ball lightning is a fizzing,
 * glowing orb throwing off sparks, and when it zaps something an arc leaps to it with a crack.
 *
 * <p>Most of the gloom is fog. The particles are few: some big, slow clouds, short-lived strokes of light a
 * few times a second at the most, and a handful about each orb and each charge, none of them past the
 * distance they would be seen at.
 */
final class LightningFx {
	/** Where the orbs begin in the storm's numbers, and how many numbers each orb and each charge takes (see LightningStorm.extra). */
	private static final int HEADER = 5;
	private static final int ORB_FLOATS = 8;
	private static final int CHARGE_FLOATS = 4;
	private static final int MAX_ORBS = 3;
	private static final int MAX_CHARGES = 3;
	/** The colour of the storm's air (dimmed with the daylight by the fog itself): a dark slate. */
	private static final float RED = 0.2F;
	private static final float GREEN = 0.22F;
	private static final float BLUE = 0.28F;
	/** The zap last drawn of each orb, by storm and orb: each zap is drawn and heard once. */
	private static final Map<Long, Integer> ZAPS = new HashMap<>();

	private LightningFx() {
	}

	static void tick(ClientDisaster d) {
		double radius = Math.max(1.0, d.radius);
		double base = d.extra.length >= HEADER ? d.extra(0) : d.y + 30.0;
		double visibility = d.extra(1) > 0.0F ? Math.max(24.0, Math.min(400.0, d.extra(1))) : 100.0;
		double burst = Math.max(0.0, Math.min(4.0, d.extra(2)));
		float rank = Math.max(1.0F, Math.min(5.0F, d.intensity));
		double distance = Fx.horizontal(d.x, d.z);
		if (distance < radius) {
			float inside = (float) (Math.min(1.0, (radius - distance) / 50.0) * d.strength);
			if (inside > 0.0F) {
				inside(d, inside, base, visibility, burst);
			}
		} else if (distance < radius + 400.0) {
			wall(d, distance, radius, base);
		}
		if (d.strength > 0.05F && distance < radius + 400.0) {
			flicker(d, radius, base, burst, rank, distance);
		}
		int orbs = Math.max(0, Math.min(MAX_ORBS, (int) d.extra(3)));
		for (int k = 0; k < orbs; k++) {
			orb(d, k, HEADER + k * ORB_FLOATS);
		}
		int charges = Math.max(0, Math.min(MAX_CHARGES, (int) d.extra(4)));
		for (int k = 0; k < charges; k++) {
			charge(d, HEADER + orbs * ORB_FLOATS + k * CHARGE_FLOATS);
		}
	}

	/** Under the storm: its dark air, its gusts, and the low cloud overhead (less of all of it under a roof, none down a cave). */
	private static void inside(ClientDisaster d, float inside, double base, double visibility, double burst) {
		int bx = (int) Math.floor(Fx.eye.x);
		int bz = (int) Math.floor(Fx.eye.z);
		int roof = Fx.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		double over = roof - Fx.eye.y;
		float open;
		if (over <= 0.0 || over < 24.0 && Fx.level.getBlockState(new BlockPos(bx, roof - 1, bz)).is(BlockTags.LEAVES)) {
			open = 1.0F;
		} else if (over < 12.0) {
			// Under a roof: the gloom is still out of the windows.
			open = 0.6F;
		} else {
			open = 0.15F;
		}
		DisasterClient.fog(0.8F * inside * open, RED, GREEN, BLUE, (float) visibility);
		float gusts = (float) (0.25 + 0.12 * Math.min(2.5, burst)) * inside * (open >= 1.0F ? 1.0F : open * 0.6F);
		DisasterAudio.loop("lightning" + d.id, DisasterSounds.WIND_HOWL, false, Math.min(0.8F, gusts), 0, 0, 0);
		if (open < 1.0F) {
			return;
		}
		// The ragged cloud base hanging low over the land: a few big, dark, slow billows about the viewer.
		int scud = Fx.round(0.6 * inside * Fx.density);
		for (int i = 0; i < scud; i++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = Fx.range(10.0, 90.0);
			float shade = (float) Fx.range(0.16, 0.26);
			Fx.cloud(Fx.eye.x + Math.cos(angle) * r, base + Fx.range(-3.0, 4.0), Fx.eye.z + Math.sin(angle) * r)
					.velocity(d.dirX * 0.06, 0.0, d.dirZ * 0.06).drag(1.0).size((float) Fx.range(12.0, 22.0)).color(shade, shade * 1.04F, shade * 1.15F)
					.alpha(0.75F).life(140 + Fx.random.nextInt(80)).fade(0.25F, 0.35F).spin((float) Fx.range(-0.004, 0.004)).spawn();
		}
	}

	/** The storm seen from outside: a dark wall of cloud along its edge nearest the viewer, towering up from its base. */
	private static void wall(ClientDisaster d, double distance, double radius, double base) {
		// Building up as the storm does, and breaking up as it dies away.
		int count = Fx.round(Fx.budget(3, distance - radius, 450.0) * Math.min(1.0F, d.strength * 1.5F));
		double toward = Math.atan2(Fx.eye.z - d.z, Fx.eye.x - d.x);
		for (int i = 0; i < count; i++) {
			double angle = toward + Fx.random.nextGaussian() * 0.35;
			double r = radius * Fx.range(0.85, 1.0);
			float shade = (float) Fx.range(0.2, 0.32);
			Fx.cloud(d.x + Math.cos(angle) * r, base + Math.pow(Fx.random.nextDouble(), 1.5) * 45.0, d.z + Math.sin(angle) * r)
					.velocity(d.dirX * 0.04, 0.0, d.dirZ * 0.04).drag(1.0).size((float) Fx.range(16.0, 28.0)).color(shade, shade * 1.03F, shade * 1.12F)
					.alpha(0.8F).life(90 + Fx.random.nextInt(60)).fade(0.25F, 0.35F).spawn();
		}
	}

	/** Lightning crawling through the cloud now and then, more often in a burst: over the viewer or near it, or anywhere in the storm. */
	private static void flicker(ClientDisaster d, double radius, double base, double burst, float rank, double distance) {
		double chance = (0.008 + 0.026 * burst) * (0.4 + 0.12 * rank) * d.strength;
		if (Fx.random.nextDouble() >= chance) {
			return;
		}
		double angle = Fx.random.nextDouble() * Math.PI * 2.0;
		double sx;
		double sz;
		if (distance < radius && Fx.random.nextInt(3) > 0) {
			double r = Fx.range(0.0, 110.0);
			sx = Fx.eye.x + Math.cos(angle) * r;
			sz = Fx.eye.z + Math.sin(angle) * r;
		} else {
			double r = radius * Math.sqrt(Fx.random.nextDouble());
			sx = d.x + Math.cos(angle) * r;
			sz = d.z + Math.sin(angle) * r;
		}
		crawl(sx, base + Fx.range(0.0, 8.0), sz);
	}

	/** A discharge crawling along the underside of the cloud: a jagged, branching line of light, a glow over the cloud about it, its thunder after. */
	private static void crawl(double sx, double sy, double sz) {
		double distance = Fx.distance(sx, sy, sz);
		int budget = Fx.budget(18, distance, 700.0);
		if (budget < 3) {
			return;
		}
		int segments = Math.min(budget - 1, 5 + Fx.random.nextInt(8));
		double angle = Fx.random.nextDouble() * Math.PI * 2.0;
		double px = sx;
		double py = sy;
		double pz = sz;
		for (int i = 0; i < segments; i++) {
			angle += Fx.random.nextGaussian() * 0.6;
			double length = Fx.range(4.0, 9.0);
			double nx = px + Math.cos(angle) * length;
			double ny = py + Fx.range(-1.5, 1.5);
			double nz = pz + Math.sin(angle) * length;
			stroke(px, py, pz, nx, ny, nz, 1.0F);
			if (Fx.random.nextInt(4) == 0) {
				// A branch forking off to one side.
				double fork = angle + (Fx.random.nextBoolean() ? 0.9 : -0.9) + Fx.random.nextGaussian() * 0.3;
				double flen = Fx.range(3.0, 6.0);
				stroke(nx, ny, nz, nx + Math.cos(fork) * flen, ny + Fx.range(-1.5, 1.0), nz + Math.sin(fork) * flen, 0.7F);
			}
			px = nx;
			py = ny;
			pz = nz;
		}
		// The cloud lit from within about it.
		Fx.spark((sx + px) * 0.5, sy + 3.0, (sz + pz) * 0.5).size(Fx.apparent(22.0F, distance, 0.06)).color(0.72F, 0.74F, 0.92F).glow().alpha(0.35F)
				.life(5).fade(0.0F, 0.8F).spawn();
		if (Fx.random.nextInt(5) < 3) {
			DisasterAudio.distant(SoundEvents.LIGHTNING_BOLT_THUNDER, sx, sy, sz, 12.0F, (float) Fx.range(0.5, 0.8));
		}
	}

	/** One stroke of a lightning channel, from one point to the next. */
	private static void stroke(double ax, double ay, double az, double bx, double by, double bz, float bright) {
		Vec3 along = new Vec3(bx - ax, by - ay, bz - az);
		double length = along.length();
		if (!(length > 0.01)) {
			return;
		}
		Fx.streak((ax + bx) * 0.5, (ay + by) * 0.5, (az + bz) * 0.5).along(along).size((float) (length * 0.55)).color(0.86F, 0.88F, 1.0F).glow()
				.alpha(bright).life(3 + Fx.random.nextInt(3)).fade(0.0F, 0.6F).spawn();
	}

	// --- ball lightning -----------------------------------------------------------------------

	/** A ball of lightning: a white-hot core flickering in a violet-blue glow, throwing off sparks and tendrils, fizzing and crackling. */
	private static void orb(ClientDisaster d, int index, int i) {
		double x = d.extra(i);
		double y = d.extra(i + 1);
		double z = d.extra(i + 2);
		float energy = Math.max(0.0F, Math.min(1.0F, d.extra(i + 3)));
		double distance = Fx.distance(x, y, z);
		zap(d, index, (int) d.extra(i + 4), x, y, z, d.extra(i + 5), d.extra(i + 6), d.extra(i + 7), distance);
		if (distance > 160.0 || energy <= 0.0F) {
			return;
		}
		float flicker = (float) Fx.range(0.8, 1.15);
		Fx.spark(x, y, z).size(Fx.apparent(0.32F * energy * flicker, distance, 0.004)).color(0.9F, 0.94F, 1.0F).glow().alpha(1.0F).life(3)
				.fade(0.0F, 0.5F).spawn();
		Fx.spark(x, y, z).size(Fx.apparent(0.9F * energy * flicker, distance, 0.008)).color(0.5F, 0.58F, 1.0F).glow().alpha(0.4F).life(3)
				.fade(0.0F, 0.6F).spawn();
		int sparks = Fx.budget(3, distance, 80.0);
		for (int k = 0; k < sparks; k++) {
			Fx.spark(x + Fx.range(-0.2, 0.2), y + Fx.range(-0.2, 0.2), z + Fx.range(-0.2, 0.2))
					.velocity(Fx.range(-0.14, 0.14), Fx.range(-0.08, 0.14), Fx.range(-0.14, 0.14)).drag(0.85).fall(0.006)
					.size((float) Fx.range(0.05, 0.1)).color(0.7F, 0.8F, 1.0F).glow().alpha(1.0F).life(4 + Fx.random.nextInt(6)).fade(0.0F, 0.5F).spawn();
		}
		if (distance < 48.0 && Fx.random.nextInt(3) == 0) {
			// A tendril of discharge flicking out of it.
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double rise = Fx.range(-0.6, 0.6);
			double length = Fx.range(0.5, 1.2) * energy;
			stroke(x, y, z, x + Math.cos(angle) * length, y + rise * length, z + Math.sin(angle) * length, 1.0F);
		}
		if (distance < 28.0 && Fx.random.nextInt(9) == 0) {
			Fx.level.playLocalSound(x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.WEATHER, 1.0F, (float) Fx.range(1.5, 2.0), false);
		}
	}

	/** An orb's zap, the first time it is seen: an arc leaping to what it struck, a burst of sparks there and a sharp crack. */
	private static void zap(ClientDisaster d, int index, int zapAge, double x, double y, double z, double tx, double ty, double tz, double distance) {
		if (zapAge <= 0) {
			return;
		}
		long key = (long) d.id << 3 | index;
		Integer seen = ZAPS.get(key);
		if (seen != null && seen == zapAge) {
			return;
		}
		if (ZAPS.size() > 64) {
			ZAPS.clear();
		}
		ZAPS.put(key, zapAge);
		// Only one just made, not one from before this client heard of the orb.
		if (Math.abs(d.age - zapAge) > 10 || distance > 96.0) {
			return;
		}
		double px = x;
		double py = y;
		double pz = z;
		for (int s = 1; s <= 4; s++) {
			double t = s / 4.0;
			double jitter = s == 4 ? 0.0 : 0.35;
			double nx = x + (tx - x) * t + Fx.range(-jitter, jitter);
			double ny = y + (ty - y) * t + Fx.range(-jitter, jitter);
			double nz = z + (tz - z) * t + Fx.range(-jitter, jitter);
			stroke(px, py, pz, nx, ny, nz, 1.0F);
			px = nx;
			py = ny;
			pz = nz;
		}
		Fx.spark(tx, ty, tz).size(1.2F).color(0.75F, 0.8F, 1.0F).glow().alpha(0.7F).life(3).fade(0.0F, 0.7F).spawn();
		int sparks = Fx.round(6 * Fx.density);
		for (int k = 0; k < sparks; k++) {
			Fx.spark(tx, ty, tz).velocity(Fx.range(-0.25, 0.25), Fx.range(-0.1, 0.3), Fx.range(-0.25, 0.25)).drag(0.85).fall(0.02)
					.size((float) Fx.range(0.06, 0.12)).color(0.75F, 0.82F, 1.0F).glow().alpha(1.0F).life(5 + Fx.random.nextInt(6)).fade(0.0F, 0.5F).spawn();
		}
		Fx.level.playLocalSound(tx, ty, tz, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 0.7F, (float) Fx.range(1.5, 1.9), false);
	}

	// --- a strike about to come down ----------------------------------------------------------

	/** Where a strike is about to come down: the air crackling, sparks creeping up from the ground and off whoever stands there, faster as it nears. */
	private static void charge(ClientDisaster d, int i) {
		double x = d.extra(i);
		double y = d.extra(i + 1);
		double z = d.extra(i + 2);
		double left = d.extra(i + 3);
		double distance = Fx.distance(x, y, z);
		if (distance > 64.0) {
			return;
		}
		float urgency = (float) Math.max(0.0, Math.min(1.0, 1.0 - left / 40.0));
		int sparks = Fx.round((1.5 + 3.5 * urgency) * Fx.density);
		for (int k = 0; k < sparks; k++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = Fx.range(0.0, 1.6);
			Fx.spark(x + Math.cos(angle) * r, y + Fx.range(0.0, 2.2), z + Math.sin(angle) * r)
					.velocity(Fx.range(-0.02, 0.02), Fx.range(0.02, 0.09), Fx.range(-0.02, 0.02)).drag(0.9).size((float) Fx.range(0.05, 0.11))
					.color(0.72F, 0.78F, 1.0F).glow().alpha(1.0F).life(4 + Fx.random.nextInt(6)).fade(0.0F, 0.5F).spawn();
		}
		if (Fx.random.nextDouble() < 0.15 + 0.35 * urgency) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = Fx.range(0.2, 1.4);
			double sx = x + Math.cos(angle) * r;
			double sz = z + Math.sin(angle) * r;
			double sy = y + Fx.range(0.0, 1.8);
			stroke(sx, sy, sz, sx + Fx.range(-0.4, 0.4), sy + Fx.range(0.2, 0.6), sz + Fx.range(-0.4, 0.4), 0.9F);
		}
		if (distance < 32.0 && Fx.random.nextInt(urgency > 0.5F ? 3 : 6) == 0) {
			Fx.level.playLocalSound(x, y + 1.0, z, SoundEvents.FIRE_AMBIENT, SoundSource.WEATHER, 1.2F, (float) Fx.range(1.7, 2.0), false);
		}
	}
}
