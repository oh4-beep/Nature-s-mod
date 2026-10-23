package com.naturesmod.client.disaster;

import com.naturesmod.NaturesConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** What every disaster's effects need from one client tick to the next: the view, a random source and a particle budget. */
final class Fx {
	/** No more disaster particles than this in one tick, all disasters together. */
	private static final int MAX_PER_TICK = 260;

	static Minecraft mc;
	static ClientLevel level;
	static Vec3 eye = Vec3.ZERO;
	static RandomSource random = RandomSource.create();
	static float density = 1.0F;
	static int spawned;

	private Fx() {
	}

	static void begin(Minecraft minecraft) {
		mc = minecraft;
		level = minecraft.level;
		eye = minecraft.player.getEyePosition();
		random = level.getRandom();
		density = Math.max(0.0F, NaturesConfig.get().disasterParticles);
		spawned = 0;
	}

	/** Whether the disasters' particle textures have loaded (the particle calls below may be relied on once they have). */
	static boolean ready() {
		return DisasterParticle.cloudSprites != null && DisasterParticle.debrisSprites != null && DisasterParticle.sparkSprites != null
				&& DisasterParticle.flakeSprites != null;
	}

	/** How many particles a disaster may spend this tick: `base` close by, fewer the further off (none past `far`). */
	static int budget(int base, double distance, double far) {
		if (distance > far || spawned >= MAX_PER_TICK) {
			return 0;
		}
		double near = 1.0 - distance / far;
		return Math.min(MAX_PER_TICK - spawned, round(base * density * (0.25 + 0.75 * near)));
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
}
