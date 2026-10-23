package com.naturesmod.worldgen.wild;

import java.util.List;

import com.naturesmod.NaturesMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * The wild Overworld: thirteen new biomes grown out of vanilla's, by {@link WildBiomeSource}. The
 * world presets name the source (see tools/worldgen/wild.py); the biomes themselves, their surface,
 * features and creatures are data.
 */
public final class WildOverworld {
	/** Every new biome of the Overworld: eleven on the surface, then two deep in its caves. */
	public static final List<String> BIOMES = List.of("lavender_fields", "golden_prairie", "maple_woods", "glowshroom_forest",
			"tropical_shore", "salt_flats", "volcanic_peaks", "bayou", "tundra", "petrified_forest", "heather_moors", "crystal_caverns",
			"fungal_grotto");

	private WildOverworld() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.BIOME_SOURCE, NaturesMod.id("wild"), WildBiomeSource.CODEC);
	}

	public static ResourceKey<Biome> biome(String name) {
		return ResourceKey.create(Registries.BIOME, NaturesMod.id(name));
	}
}
