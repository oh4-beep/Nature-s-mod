package com.naturesmod.worldgen.deep;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/** The kind of land a structure of the Overworld stands in, so each one is built where it belongs. */
public enum Climate {
	PLAINS, FOREST, TAIGA, SNOWY, DESERT, JUNGLE, SAVANNA, SWAMP, MOUNTAIN, BADLANDS;

	/** The climate of a biome, read from its tags. */
	public static Climate of(Holder<Biome> biome) {
		if (biome.is(Biomes.DESERT)) {
			return DESERT;
		}
		if (biome.is(BiomeTags.IS_BADLANDS)) {
			return BADLANDS;
		}
		if (biome.is(BiomeTags.IS_JUNGLE)) {
			return JUNGLE;
		}
		if (biome.is(BiomeTags.IS_SAVANNA)) {
			return SAVANNA;
		}
		if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
			return SWAMP;
		}
		if (biome.is(BiomeTags.HAS_IGLOO) || biome.is(Biomes.ICE_SPIKES) || biome.is(Biomes.FROZEN_PEAKS)) {
			return SNOWY;
		}
		if (biome.is(BiomeTags.IS_MOUNTAIN)) {
			return MOUNTAIN;
		}
		if (biome.is(BiomeTags.IS_TAIGA)) {
			return TAIGA;
		}
		if (biome.is(BiomeTags.IS_FOREST)) {
			return FOREST;
		}
		return PLAINS;
	}

	/** A climate by its saved name; plains for one that is not known. */
	public static Climate byName(String name) {
		for (Climate climate : values()) {
			if (climate.name().equals(name)) {
				return climate;
			}
		}
		return PLAINS;
	}
}
