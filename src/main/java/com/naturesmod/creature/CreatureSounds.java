package com.naturesmod.creature;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Vanilla sounds that suit each kind of sea creature. */
final class CreatureSounds {
	private CreatureSounds() {
	}

	private static boolean big(SpeciesData species) {
		return species.scale() >= 1.5F;
	}

	static SoundEvent ambient(SpeciesData species) {
		return switch (species.sound()) {
			case "squid" -> SoundEvents.SQUID_AMBIENT;
			case "leviathan" -> SoundEvents.ELDER_GUARDIAN_AMBIENT;
			default -> big(species) ? null : SoundEvents.COD_AMBIENT;
		};
	}

	static SoundEvent hurt(SpeciesData species) {
		return switch (species.sound()) {
			case "squid" -> SoundEvents.SQUID_HURT;
			case "leviathan" -> SoundEvents.ELDER_GUARDIAN_HURT;
			default -> big(species) ? SoundEvents.GUARDIAN_HURT : SoundEvents.COD_HURT;
		};
	}

	static SoundEvent death(SpeciesData species) {
		return switch (species.sound()) {
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
