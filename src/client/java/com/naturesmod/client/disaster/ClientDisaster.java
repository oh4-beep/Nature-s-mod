package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterNet;
import com.naturesmod.disaster.DisasterType;

/**
 * A disaster as this client knows it: what the server last said of it, moved on smoothly between
 * the server's updates.
 */
final class ClientDisaster {
	final int id;
	final DisasterType type;
	double x;
	double y;
	double z;
	private double targetX;
	private double targetY;
	private double targetZ;
	float radius;
	float intensity;
	float strength;
	float dirX;
	float dirZ;
	int age;
	int duration;
	float[] extra = new float[0];
	/** Gone from the server's list: the particles following it let go. */
	boolean removed;

	ClientDisaster(DisasterNet.State state) {
		this.id = state.id();
		this.type = state.kind();
		update(state);
		x = targetX;
		y = targetY;
		z = targetZ;
	}

	void update(DisasterNet.State state) {
		targetX = state.x();
		targetY = state.y();
		targetZ = state.z();
		radius = state.radius();
		intensity = state.intensity();
		strength = state.strength();
		dirX = state.dirX();
		dirZ = state.dirZ();
		age = state.age();
		duration = state.duration();
		extra = state.extra();
	}

	void tick() {
		x += (targetX - x) * 0.3;
		y += (targetY - y) * 0.3;
		z += (targetZ - z) * 0.3;
		age++;
	}

	float extra(int i) {
		return i < extra.length ? extra[i] : 0.0F;
	}
}
