package com.naturesmod.creature;

import java.util.List;

import net.minecraft.world.entity.MobCategory;

/**
 * Everything that sets one sea creature species apart from another. The table of species is
 * generated from tools/creatures/species.py into {@link CreatureSpecies}.
 *
 * @param plan      body plan of the model: fish, deep, long, shark, ray, jelly, squid or seahorse
 * @param category  spawn category: water_ambient or water_creature
 * @param scale     size of the model relative to its body plan
 * @param depth     where it spawns in the water column: surface, floor, deep or any
 * @param sound     sound set: fish, squid or leviathan
 * @param segments  number of body segments of long (eel-like) bodies
 * @param habitats  biomes it spawns in
 * @param effect    what its bite does to its victim besides the damage: darkness, wither, nausea, poison,
 *                  blindness, or nothing
 */
public record SpeciesData(String id, String plan, Behavior behavior, String category, float scale, float width, float height,
		double health, double attack, double speed, int groupMin, int groupMax, int weight, boolean bucket, boolean glow,
		boolean translucent, String depth, String sound, int segments, List<String> habitats, String effect) {

	public MobCategory mobCategory() {
		return "water_creature".equals(category) ? MobCategory.WATER_CREATURE : MobCategory.WATER_AMBIENT;
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
		JELLY
	}
}
