package com.naturesmod.disaster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Placeholder until the avalanche is written. */
public final class Avalanche extends Disaster {
	Avalanche(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.AVALANCHE, level, at, intensity, natural, 20);
	}

	@Override
	protected void tick() {
	}

	@Override
	public float radius() {
		return 1.0F;
	}
}
