package com.naturesmod.client.disaster;

import com.naturesmod.NaturesConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** What every disaster's effects need from one client tick to the next: the view, a random source and a particle budget. */
final class Fx {
	/** No more disaster particles than this in one tick, all disasters together (before a slow game thins them, see {@link #quality}). */
	private static final int MAX_PER_TICK = 260;
	/**
	 * How much of the full show the game's speed allows: 1 at a smooth frame rate, falling to a fifth on a computer
	 * that struggles, so that a disaster never makes the game unplayable.
	 */
	static float quality = 1.0F;
	/** No more particles than this this tick, all disasters together, nor anything spawned past twice this. */
	static int cap = MAX_PER_TICK;

	static Minecraft mc;
	static ClientLevel level;
	static Vec3 eye = Vec3.ZERO;
	static RandomSource random = RandomSource.create();
	static float density = 1.0F;
	/** The density the player's own settings ask for, before a slow game thins it. */
	static float settingDensity = 1.0F;
	static int spawned;
	/** How far off a particle may be drawn where it is: past this the fog would hide it (see {@link DisasterParticle}). */
	static double viewLimit = 96.0;

	private Fx() {
	}

	static void begin(Minecraft minecraft) {
		mc = minecraft;
		level = minecraft.level;
		eye = minecraft.player.getEyePosition();
		random = level.getRandom();
		density = Math.max(0.0F, NaturesConfig.get().disasterParticles);
		// The game's own particle setting too: all, decreased or minimal.
		int setting = minecraft.options.particles().get().ordinal();
		density *= setting == 0 ? 1.0F : setting == 1 ? 0.5F : 0.2F;
		settingDensity = density;
		if (NaturesConfig.get().adaptiveDisasterEffects) {
			// Under 50 frames a second the particles thin out, to a fifth at 15. Thinning fast and filling back in slowly,
			// so that the thinning doesn't itself set the frame rate swinging.
			int fps = minecraft.getFps();
			float want = fps >= 50 ? 1.0F : fps <= 15 ? 0.2F : 0.2F + 0.8F * (fps - 15) / 35.0F;
			quality += (want - quality) * (want < quality ? 0.08F : 0.01F);
		} else {
			quality = 1.0F;
		}
		density *= quality;
		cap = Math.max(16, Math.round(MAX_PER_TICK * quality));
		spawned = 0;
		viewLimit = Math.max(40.0, minecraft.options.getEffectiveRenderDistance() * 16.0 * 0.6);
	}

	/** Whether the disasters' particle textures have loaded (the particle calls below may be relied on once they have). */
	static boolean ready() {
		return DisasterParticle.cloudSprites != null && DisasterParticle.debrisSprites != null && DisasterParticle.sparkSprites != null
				&& DisasterParticle.flakeSprites != null && DisasterParticle.streakSprites != null;
	}

	/** How many particles a disaster may spend this tick: `base` close by, fewer the further off (none past `far`). */
	static int budget(int base, double distance, double far) {
		if (distance > far || spawned >= cap) {
			return 0;
		}
		double near = 1.0 - distance / far;
		return Math.min(cap - spawned, round(base * density * (0.25 + 0.75 * near)));
	}

	/** An expected count as a whole number: 2.3 is 2 seven times in ten and 3 the rest. */
	static int round(double expected) {
		int whole = (int) expected;
		return whole + (random.nextDouble() < expected - whole ? 1 : 0);
	}

	static double horizontal(double x, double z) {
		double dx = x - eye.x;
		double dz = z - eye.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	static double distance(double x, double y, double z) {
		return Math.sqrt(eye.distanceToSqr(x, y, z));
	}

	static double range(double lo, double hi) {
		return lo + random.nextDouble() * (hi - lo);
	}

	static DisasterParticle cloud(double x, double y, double z) {
		return DisasterParticle.of(DisasterParticle.cloudSprites, x, y, z);
	}

	static DisasterParticle debris(double x, double y, double z) {
		return DisasterParticle.of(DisasterParticle.debrisSprites, x, y, z);
	}

	static DisasterParticle spark(double x, double y, double z) {
		return DisasterParticle.of(DisasterParticle.sparkSprites, x, y, z);
	}

	static DisasterParticle flake(double x, double y, double z) {
		return DisasterParticle.of(DisasterParticle.flakeSprites, x, y, z);
	}

	static DisasterParticle streak(double x, double y, double z) {
		return DisasterParticle.of(DisasterParticle.streakSprites, x, y, z);
	}

	/** A size that looks at least `angle` radians across from here, at a distance. */
	static float apparent(float size, double distance, double angle) {
		return (float) Math.max(size, distance * angle);
	}
}
