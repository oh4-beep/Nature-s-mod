package com.naturesmod.disaster;

import com.naturesmod.NaturesMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * The particles the disasters are drawn with: billowing cloud (funnels, plumes, dust walls, smoke),
 * debris, glowing sparks (meteors, embers, lava) and blown snow. The clients spawn them themselves.
 */
public final class DisasterParticles {
	public static final SimpleParticleType CLOUD = register("cloud");
	public static final SimpleParticleType DEBRIS = register("debris");
	public static final SimpleParticleType SPARK = register("spark");
	public static final SimpleParticleType FLAKE = register("flake");

	private DisasterParticles() {
	}

	public static void register() {
	}

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, NaturesMod.id(name), FabricParticleTypes.simple(true));
	}
}
