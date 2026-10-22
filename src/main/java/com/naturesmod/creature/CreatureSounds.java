package com.naturesmod.creature;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Vanilla sounds that suit each kind of creature, of the sea, of the End and of the Overworld's lands and skies. */
final class CreatureSounds {
	private CreatureSounds() {
	}

	private static boolean big(SpeciesData species) {
		return species.scale() >= 1.5F;
	}

	static SoundEvent ambient(SpeciesData species) {
		return switch (species.sound()) {
			case "phantom" -> SoundEvents.PHANTOM_AMBIENT;
			case "wyrm" -> SoundEvents.ENDER_DRAGON_AMBIENT;
			// The Overworld's beasts and birds.
			case "llama" -> SoundEvents.LLAMA_AMBIENT;
			case "horse" -> SoundEvents.HORSE_AMBIENT;
			case "big_beast" -> SoundEvents.RAVAGER_AMBIENT;
			case "big_cat" -> SoundEvents.POLAR_BEAR_WARNING;
			case "fox" -> SoundEvents.FOX_AMBIENT;
			case "bird" -> SoundEvents.PARROT_AMBIENT;
			case "monkey" -> SoundEvents.PANDA_AMBIENT;
			case "bear" -> SoundEvents.POLAR_BEAR_AMBIENT;
			case "reptile", "penguin" -> SoundEvents.TURTLE_AMBIENT_LAND;
			case "capybara" -> SoundEvents.ARMADILLO_AMBIENT;
			case "goat" -> SoundEvents.GOAT_AMBIENT;
			case "hoglin" -> SoundEvents.HOGLIN_AMBIENT;
			case "snake" -> SoundEvents.SILVERFISH_AMBIENT;
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
			case "wyrm" -> SoundEvents.ENDER_DRAGON_HURT;
			case "llama" -> SoundEvents.LLAMA_HURT;
			case "horse" -> SoundEvents.HORSE_HURT;
			case "big_beast" -> SoundEvents.RAVAGER_HURT;
			case "big_cat" -> SoundEvents.OCELOT_HURT;
			case "fox" -> SoundEvents.FOX_HURT;
			case "bird" -> SoundEvents.PARROT_HURT;
			case "monkey" -> SoundEvents.PANDA_HURT;
			case "bear" -> SoundEvents.POLAR_BEAR_HURT;
			case "reptile", "penguin" -> SoundEvents.TURTLE_HURT;
			case "capybara" -> SoundEvents.ARMADILLO_HURT;
			case "goat" -> SoundEvents.GOAT_HURT;
			case "hoglin" -> SoundEvents.HOGLIN_HURT;
			case "snake" -> SoundEvents.SILVERFISH_HURT;
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
			case "wyrm" -> SoundEvents.ENDER_DRAGON_DEATH;
			case "llama" -> SoundEvents.LLAMA_DEATH;
			case "horse" -> SoundEvents.HORSE_DEATH;
			case "big_beast" -> SoundEvents.RAVAGER_DEATH;
			case "big_cat" -> SoundEvents.OCELOT_DEATH;
			case "fox" -> SoundEvents.FOX_DEATH;
			case "bird" -> SoundEvents.PARROT_DEATH;
			case "monkey" -> SoundEvents.PANDA_DEATH;
			case "bear" -> SoundEvents.POLAR_BEAR_DEATH;
			case "reptile", "penguin" -> SoundEvents.TURTLE_DEATH;
			case "capybara" -> SoundEvents.ARMADILLO_DEATH;
			case "goat" -> SoundEvents.GOAT_DEATH;
			case "hoglin" -> SoundEvents.HOGLIN_DEATH;
			case "snake" -> SoundEvents.SILVERFISH_DEATH;
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
