package com.naturesmod.creature;

import java.util.List;

import net.minecraft.world.entity.MobCategory;

/**
 * Everything that sets one creature species apart from another: the sea creatures and the creatures
 * of the End. The table of species is generated from tools/creatures/species.py into
 * {@link CreatureSpecies}.
 *
 * @param plan      body plan of the model: fish, deep, long, shark, ray, jelly, squid, seahorse, beetle, golem,
 *                  stalker or hopper
 * @param category  spawn category: water_ambient, water_creature, ambient, creature or monster
 * @param scale     size of the model relative to its body plan
 * @param depth     where it spawns in the water column: surface, floor, deep or any
 * @param sound     sound set: fish, squid or leviathan
 * @param segments  number of body segments of long (eel-like) bodies
 * @param habitats  biomes it spawns in
 * @param effect    what its bite does to its victim besides the damage: darkness, wither, nausea, poison,
 *                  blindness, levitation, or nothing
 * @param medium    what it lives in: water (the sea creatures), air (the End's flyers) or land (the End's walkers)
 */
public record SpeciesData(String id, String plan, Behavior behavior, String category, float scale, float width, float height,
		double health, double attack, double speed, int groupMin, int groupMax, int weight, boolean bucket, boolean glow,
		boolean translucent, String depth, String sound, int segments, List<String> habitats, String effect, String medium) {

	public MobCategory mobCategory() {
		return switch (category) {
			case "water_creature" -> MobCategory.WATER_CREATURE;
			case "monster" -> MobCategory.MONSTER;
			case "creature" -> MobCategory.CREATURE;
			case "ambient" -> MobCategory.AMBIENT;
			default -> MobCategory.WATER_AMBIENT;
		};
	}

	/** A creature of the sea (the Overworld's and the Abyss's), as opposed to one of the End's. */
	public boolean inWater() {
		return "water".equals(medium);
	}

	public boolean flies() {
		return "air".equals(medium);
	}

	public boolean walks() {
		return "land".equals(medium);
	}

	public boolean isJelly() {
		return "jelly".equals(plan);
	}

	public boolean schools() {
		return behavior == Behavior.SCHOOL || behavior == Behavior.PACK;
	}

	/** Venomous to the touch: jellyfish and lionfish. */
	public boolean stings() {
		return behavior == Behavior.DRIFTER && attack > 0;
	}

	/** Which kind of entity carries this species. */
	public Kind kind() {
		if (flies()) {
			return Kind.FLYER;
		}
		if (walks()) {
			return Kind.WALKER;
		}
		if (behavior == Behavior.BOSS) {
			// Every boss, whatever its shape, is a big smooth swimmer with a boss bar.
			return Kind.LARGE;
		}
		if (isJelly()) {
			return Kind.JELLY;
		}
		if (schools()) {
			return Kind.SCHOOL;
		}
		return bucket ? Kind.SMALL : Kind.LARGE;
	}

	public enum Kind {
		/** Little fish that can be caught in a bucket. */
		SMALL,
		/** Schooling fish. */
		SCHOOL,
		/** Big, smooth swimmers: sharks, eels, rays, squid and the deep-sea hunters. */
		LARGE,
		/** Pulsing, drifting jellyfish. */
		JELLY,
		/** The End's creatures of the open air, which swim through it as fish swim through water. */
		FLYER,
		/** The End's creatures that walk its lands. */
		WALKER
	}
}
