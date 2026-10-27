package com.naturesmod.disaster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Placeholder until the tsunami is written. */
public final class Tsunami extends Disaster {
	Tsunami(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.TSUNAMI, level, at, intensity, natural, 20);
	}

	@Override
	protected void tick() {
	}

	@Override
	public float radius() {
		return 1.0F;
	}
}
