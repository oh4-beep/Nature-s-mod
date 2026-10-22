package com.naturesmod.creature;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Vanilla sounds that suit each kind of creature, of the sea and of the End. */
final class CreatureSounds {
	private CreatureSounds() {
	}

	private static boolean big(SpeciesData species) {
		return species.scale() >= 1.5F;
	}

	static SoundEvent ambient(SpeciesData species) {
		return switch (species.sound()) {
			case "phantom" -> SoundEvents.PHANTOM_AMBIENT;
			case "skyray" -> SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
			case "ender" -> SoundEvents.ENDERMAN_AMBIENT;
			case "beetle" -> SoundEvents.SILVERFISH_AMBIENT;
			case "golem" -> SoundEvents.AMETHYST_BLOCK_CHIME;
			case "critter" -> SoundEvents.RABBIT_AMBIENT;
			case "squid" -> SoundEvents.SQUID_AMBIENT;
			case "leviathan" -> SoundEvents.ELDER_GUARDIAN_AMBIENT;
			default -> big(species) ? null : SoundEvents.COD_AMBIENT;
		};
	}

	static SoundEvent hurt(SpeciesData species) {
		return switch (species.sound()) {
			case "phantom", "skyray" -> SoundEvents.PHANTOM_HURT;
			case "ender" -> SoundEvents.ENDERMAN_HURT;
			case "beetle" -> SoundEvents.SILVERFISH_HURT;
			case "golem" -> SoundEvents.IRON_GOLEM_HURT;
			case "critter" -> SoundEvents.RABBIT_HURT;
			case "squid" -> SoundEvents.SQUID_HURT;
			case "leviathan" -> SoundEvents.ELDER_GUARDIAN_HURT;
			default -> big(species) ? SoundEvents.GUARDIAN_HURT : SoundEvents.COD_HURT;
		};
	}

	static SoundEvent death(SpeciesData species) {
		return switch (species.sound()) {
			case "phantom", "skyray" -> SoundEvents.PHANTOM_DEATH;
			case "ender" -> SoundEvents.ENDERMAN_DEATH;
			case "beetle" -> SoundEvents.SILVERFISH_DEATH;
			case "golem" -> SoundEvents.IRON_GOLEM_DEATH;
			case "critter" -> SoundEvents.RABBIT_DEATH;
			case "squid" -> SoundEvents.SQUID_DEATH;
			case "leviathan" -> SoundEvents.ELDER_GUARDIAN_DEATH;
			default -> big(species) ? SoundEvents.GUARDIAN_DEATH : SoundEvents.COD_DEATH;
		};
	}

	static SoundEvent flop(SpeciesData species) {
		return switch (species.sound()) {
			case "leviathan" -> SoundEvents.ELDER_GUARDIAN_FLOP;
			default -> big(species) ? SoundEvents.GUARDIAN_FLOP : SoundEvents.COD_FLOP;
		};
	}
}
