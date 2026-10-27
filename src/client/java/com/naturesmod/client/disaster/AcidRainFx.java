package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Draws acid rain. Under it the air hangs a sickly yellow-green, thicker the stronger the storm and in its heavier
 * showers; the rain comes down tinted the same, slanting a little on its wind, and where it lands it fizzes: little
 * puffs of acrid smoke off the ground, the leaves and the water about the viewer, a spatter thrown up, and the hiss
 * of it all round. From outside, a yellowish curtain of rain hangs along its edge and the air is already tinged.
 * Under the crowns of trees some of it drips through; under a roof only its hiss on the roof, down a cave nothing.
 *
 * <p>Most of it is fog. The particles are few and about the eye only, thinned with the rest on a slow computer, and
 * none under a roof; the puffs look at a handful of columns a tick.
 */
final class AcidRainFx {
	/** The colour of the air under it (dimmed with the daylight by the fog itself). */
	private static final float RED = 0.64F;
	private static final float GREEN = 0.7F;
	private static final float BLUE = 0.34F;
	/** How far beyond its edge it is seen and heard at all. */
	private static final double FAR = 220.0;

	private AcidRainFx() {
	}

	static void tick(ClientDisaster d) {
		double radius = Math.max(1.0, d.radius);
		double downpour = d.extra(0) > 0.0F ? Math.min(1.0, d.extra(0)) : 0.7;
		double slant = d.extra(1) > 0.0F ? Math.min(0.6, d.extra(1)) : 0.2;
		double distance = Fx.horizontal(d.x, d.z);
		if (distance < radius) {
			float inside = (float) (Math.min(1.0, (radius - distance) / 40.0) * d.strength);
			if (inside > 0.0F) {
				inside(d, inside, downpour, slant);
			}
		} else if (distance < radius + FAR) {
			approach(d, distance, radius);
		}
	}

	private static void inside(ClientDisaster d, float inside, double downpour, double slant) {
		// What is over the viewer's head: open sky, the crowns of trees, a roof, or the rock over a cave.
		int bx = (int) Math.floor(Fx.eye.x);
		int bz = (int) Math.floor(Fx.eye.z);
		int roof = Fx.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		double over = roof - Fx.eye.y;
		float air;
		float rain;
		float sound;
		if (over <= 0.0) {
			air = 1.0F;
			rain = 1.0F;
			sound = 1.0F;
		} else if (over < 24.0 && Fx.level.getBlockState(new BlockPos(bx, roof - 1, bz)).is(BlockTags.LEAVES)) {
			air = 0.85F;
			rain = 0.35F;
			sound = 0.85F;
		} else if (over < 10.0) {
			// Its hiss on the roof.
			air = 0.3F;
			rain = 0.0F;
			sound = 0.5F;
		} else {
			air = 0.0F;
			rain = 0.0F;
			sound = 0.1F;
		}
		double level = Math.max(1.0, Math.min(5.0, d.intensity));
		// The heavier showers close it in; between them it opens a little.
		float seeing = (float) ((150.0 - 20.0 * level) * (1.3 - 0.3 * downpour));
		float thick = (float) ((0.3 + 0.1 * level) * (0.75 + 0.25 * downpour)) * inside;
		if (air > 0.0F) {
			DisasterClient.fog(thick * air, RED, GREEN, BLUE, seeing / air);
		}
		float hiss = (float) (0.45 + 0.55 * downpour) * inside * sound;
		DisasterAudio.loop("acidrain" + d.id, DisasterSounds.ACID_RAIN_HISS, false, Math.min(1.0F, hiss), 0, 0, 0);
		if (rain <= 0.0F) {
			return;
		}
		double amount = inside * rain * downpour;
		fall(d, amount, slant, level);
		if (over > 0.0 || Fx.eye.y - roof < 12.0) {
			fizz(amount, level);
		}
	}

	/** The rain coming down about the eye, tinted and slanting a little on its wind: spawned above and upwind, so that it crosses the view. */
	private static void fall(ClientDisaster d, double amount, double slant, double level) {
		Vec3 path = new Vec3(d.dirX * slant, -1.0, d.dirZ * slant);
		float length = (float) path.length();
		int streaks = share(Fx.round((3.0 + 3.0 * level) * amount * Fx.density));
		for (int i = 0; i < streaks; i++) {
			double v = Fx.range(0.85, 1.15);
			int life = 6 + Fx.random.nextInt(4);
			double back = life * 0.5 * v;
			Fx.streak(Fx.eye.x - path.x * back + Fx.range(-6.0, 6.0), Fx.eye.y - path.y * back + Fx.range(-3.0, 3.0),
					Fx.eye.z - path.z * back + Fx.range(-6.0, 6.0)).along(path).velocity(path.x * v, path.y * v, path.z * v).drag(1.0)
					.size((float) Math.max(0.5, length * Fx.range(0.55, 0.8))).color(0.74F, 0.84F, 0.36F).alpha(0.45F).life(life).fade(0.15F, 0.3F)
					.spawn();
		}
		// Veils of the dirty rain sweeping past in the heavier showers.
		int veils = share(Fx.round(1.5 * amount * Fx.density));
		for (int i = 0; i < veils; i++) {
			Fx.cloud(Fx.eye.x - d.dirX * 10.0 + Fx.range(-12.0, 12.0), Fx.eye.y + Fx.range(-2.0, 5.0), Fx.eye.z - d.dirZ * 10.0 + Fx.range(-12.0, 12.0))
					.velocity(d.dirX * slant * 0.6, -0.04, d.dirZ * slant * 0.6).drag(1.0).size((float) Fx.range(4.0, 7.0)).color(0.6F, 0.65F, 0.36F)
					.alpha(0.2F).life(30 + Fx.random.nextInt(15)).grow(0.5F).fade(0.25F, 0.4F).spawn();
		}
	}

	/**
	 * Where the rain lands about the viewer it fizzes: little puffs of acrid smoke off whatever it lands on (the
	 * ground, the crowns of trees, the water), now and then with a spatter of droplets thrown up.
	 */
	private static void fizz(double amount, double level) {
		int puffs = share(Fx.round((1.0 + 0.6 * level) * amount * Fx.density));
		for (int i = 0; i < puffs; i++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = Fx.range(2.0, 14.0);
			double px = Fx.eye.x + Math.cos(angle) * r;
			double pz = Fx.eye.z + Math.sin(angle) * r;
			int top = Fx.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(px), (int) Math.floor(pz));
			if (top <= Fx.level.getMinY() + 1 || top < Fx.eye.y - 14.0 || top > Fx.eye.y + 16.0) {
				continue;
			}
			float tone = (float) Fx.range(0.62, 0.74);
			Fx.cloud(px, top + 0.1, pz).velocity(Fx.range(-0.01, 0.01), Fx.range(0.02, 0.05), Fx.range(-0.01, 0.01)).drag(0.96)
					.size((float) Fx.range(0.35, 0.7)).color(tone, tone + 0.06F, tone * 0.6F).alpha(0.5F).life(14 + Fx.random.nextInt(12)).grow(1.4F)
					.fade(0.1F, 0.5F).spawn();
			if (Fx.random.nextInt(2) == 0) {
				Fx.flake(px, top + 0.05, pz).velocity(Fx.range(-0.04, 0.04), Fx.range(0.06, 0.12), Fx.range(-0.04, 0.04)).drag(0.98).fall(0.03)
						.size((float) Fx.range(0.04, 0.07)).color(0.82F, 0.9F, 0.4F).alpha(0.9F).life(6 + Fx.random.nextInt(5)).fade(0.05F, 0.3F).spawn();
			}
		}
	}

	/** The rain seen coming: a yellowish curtain hanging along the stretch of its edge facing the viewer, the air already tinged, a faint hiss. */
	private static void approach(ClientDisaster d, double distance, double radius) {
		double gap = distance - radius;
		float near = (float) Math.max(0.0, 1.0 - gap / FAR) * d.strength;
		if (!(near > 0.0F)) {
			return;
		}
		DisasterClient.fog(0.25F * near * near, RED, GREEN, BLUE, 140.0F);
		DisasterAudio.loop("acidrain" + d.id, DisasterSounds.ACID_RAIN_HISS, false, 0.25F * near * near, 0, 0, 0);
		int count = Fx.budget(3, gap, FAR);
		if (count <= 0) {
			return;
		}
		double toward = Math.atan2(Fx.eye.z - d.z, Fx.eye.x - d.x);
		double base = Math.min(d.y, Fx.eye.y);
		for (int i = 0; i < count; i++) {
			double angle = toward + Fx.random.nextGaussian() * 0.25;
			double r = radius * Fx.range(0.94, 1.0);
			Fx.cloud(d.x + Math.cos(angle) * r, base + Fx.range(0.0, 50.0), d.z + Math.sin(angle) * r).velocity(d.dirX * 0.03, -0.02, d.dirZ * 0.03)
					.drag(1.0).size((float) Fx.range(10.0, 18.0)).color(0.58F, 0.62F, 0.4F).alpha(0.45F).life(70 + Fx.random.nextInt(40))
					.fade(0.25F, 0.35F).spawn();
		}
	}

	/** No more than the particles left to spend this tick, all disasters together. */
	private static int share(int count) {
		return Math.max(0, Math.min(count, Fx.cap - Fx.spawned));
	}
}
