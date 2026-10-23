package com.naturesmod.client.disaster;

import com.naturesmod.NaturesConfig;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Shakes the view: the ground shaking under the player in an earthquake (as hard as the ground
 * accelerates, in g), or the jolt of a nearby blast. The shake is laid over the player's own look and
 * taken off again the next tick, so it never drifts the aim.
 */
final class CameraShake {
	private static double shaking;
	private static double jolt;
	private static float lastYaw;
	private static float lastPitch;

	private CameraShake() {
	}

	/** The ground is shaking this hard right now (asked for every tick it lasts). */
	static void shake(double g) {
		shaking = Math.max(shaking, g);
	}

	/** A single jolt, dying away by itself. */
	static void jolt(double g) {
		jolt = Math.max(jolt, g);
	}

	static double current() {
		return shaking + jolt;
	}

	static void tick(LocalPlayer player) {
		double a = Math.min(1.0, shaking + jolt);
		jolt *= 0.86;
		shaking = 0.0;
		float yaw = 0.0F;
		float pitch = 0.0F;
		if (a > 0.004 && NaturesConfig.get().cameraShake) {
			double t = player.tickCount;
			double degrees = Math.min(7.0, 16.0 * a);
			yaw = (float) (degrees * (0.55 * Math.sin(t * 0.93) + 0.45 * Math.sin(t * 1.91 + 1.7)));
			pitch = (float) (degrees * 0.75 * (0.55 * Math.sin(t * 1.17 + 0.5) + 0.45 * Math.sin(t * 2.23)));
		}
		player.setYRot(player.getYRot() - lastYaw + yaw);
		player.setXRot(Mth.clamp(player.getXRot() - lastPitch + pitch, -90.0F, 90.0F));
		lastYaw = yaw;
		lastPitch = pitch;
	}

	static void reset() {
		shaking = 0.0;
		jolt = 0.0;
		lastYaw = 0.0F;
		lastPitch = 0.0F;
	}
}
