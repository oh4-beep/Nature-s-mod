package com.naturesmod.client.disaster;

import com.naturesmod.NaturesConfig;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Shakes the view: the ground shaking under the player in an earthquake (as hard as the ground
 * accelerates, in g), or the jolt of a nearby blast. A tremor barely stirs it; a great quake throws it
 * about, up to some eleven degrees, and roughly: quicker, jerkier motion laid over the sway. The shake
 * is laid over the player's own look and taken off again the next tick, so it never drifts the aim.
 */
final class CameraShake {
	/** The most the view is turned, in degrees, however hard it shakes. */
	private static final double MAX_DEGREES = 11.0;

	private static double shaking;
	private static double jolt;
	private static float lastYaw;
	private static float lastPitch;

	private CameraShake() {
	}

	/** The ground is shaking this hard right now (asked for every tick it lasts). */
	static void shake(double g) {
		if (g > 0.0) {
			shaking = Math.max(shaking, g);
		}
	}

	/** A single jolt, dying away by itself. */
	static void jolt(double g) {
		if (g > 0.0) {
			jolt = Math.max(jolt, g);
		}
	}

	static double current() {
		return shaking + jolt;
	}

	static void tick(LocalPlayer player) {
		double a = Math.min(1.6, shaking + jolt);
		jolt *= 0.86;
		shaking = 0.0;
		float yaw = 0.0F;
		float pitch = 0.0F;
		if (a > 0.004 && NaturesConfig.get().cameraShake) {
			double t = player.tickCount;
			// Gentle for a light shaking (a quarter of a degree at 0.02 g, a degree and a half at 0.1 g), hard for a violent one.
			double degrees = Math.min(MAX_DEGREES, 17.0 * Math.pow(a, 1.1));
			double rough = Math.min(1.0, a * 1.2);
			yaw = (float) (degrees * (0.5 * Math.sin(t * 0.93) + 0.35 * Math.sin(t * 1.91 + 1.7) + 0.15 * rough * Math.sin(t * 3.7 + 0.3)));
			pitch = (float) (degrees * 0.75 * (0.5 * Math.sin(t * 1.17 + 0.5) + 0.35 * Math.sin(t * 2.23) + 0.15 * rough * Math.sin(t * 4.1 + 2.1)));
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
