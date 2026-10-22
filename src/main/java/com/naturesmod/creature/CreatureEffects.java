package com.naturesmod.creature;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** What the bite of the mutated things of the deep does besides the damage. */
final class CreatureEffects {
	private CreatureEffects() {
	}

	static void bite(Entity attacker, SpeciesData species, Entity target) {
		if (!(target instanceof LivingEntity victim)) {
			return;
		}
		switch (species.effect()) {
			case "darkness" -> victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0), attacker);
			case "wither" -> victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1), attacker);
			case "nausea" -> {
				victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0), attacker);
				victim.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1), attacker);
			}
			case "poison" -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1), attacker);
			case "blindness" -> victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0), attacker);
			case "levitation" -> victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 50, 1), attacker);
			case "crush" -> {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), attacker);
				victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1), attacker);
			}
			default -> {
			}
		}
	}
}
