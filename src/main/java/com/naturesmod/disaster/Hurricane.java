package com.naturesmod.disaster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Placeholder until the hurricane is written. */
public final class Hurricane extends Disaster {
	Hurricane(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.HURRICANE, level, at, intensity, natural, 20);
	}

	/** Sets it on a track that passes near a place. */
	public void aim(Vec3 target) {
	}

	@Override
	protected void tick() {
	}

	@Override
	public float radius() {
		return 1.0F;
	}
}
