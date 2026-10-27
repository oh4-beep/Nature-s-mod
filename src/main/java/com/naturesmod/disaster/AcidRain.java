package com.naturesmod.disaster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Placeholder until the acid_rain is written. */
public final class AcidRain extends Disaster {
	AcidRain(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.ACID_RAIN, level, at, intensity, natural, 20);
	}

	@Override
	protected void tick() {
	}

	@Override
	public float radius() {
		return 1.0F;
	}
}
