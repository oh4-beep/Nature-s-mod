package com.naturesmod.disaster;

import com.naturesmod.NaturesMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/** How the disasters hurt (defined in data/naturesmod/damage_type, written by tools/assets/disasters.py). */
public final class DisasterDamage {
	public static final ResourceKey<DamageType> TORNADO = key("tornado");
	public static final ResourceKey<DamageType> EARTHQUAKE = key("earthquake");
	public static final ResourceKey<DamageType> METEOR = key("meteor");
	public static final ResourceKey<DamageType> PYROCLASTIC = key("pyroclastic");

	private DisasterDamage() {
	}

	private static ResourceKey<DamageType> key(String name) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, NaturesMod.id(name));
	}
}
