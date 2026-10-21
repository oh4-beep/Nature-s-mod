package com.naturesmod.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.naturesmod.NaturesMod;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Decides which biomes get which Nature's Mod features. */
public final class ModBiomeModifications {
	private static final List<String> TEMPERATE_FORESTS = List.of("forest", "flower_forest", "birch_forest", "old_growth_birch_forest",
			"dark_forest", "dappled_forest");
	private static final List<String> TAIGAS = List.of("taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga");
	private static final List<String> SNOWY = List.of("snowy_taiga", "snowy_plains", "ice_spikes", "snowy_slopes", "frozen_peaks",
			"jagged_peaks", "grove");
	private static final List<String> HIGHLANDS = List.of("meadow", "stony_peaks", "cherry_grove", "windswept_hills", "windswept_forest",
			"windswept_gravelly_hills", "windswept_savanna", "savanna_plateau");
	private static final List<String> JUNGLES = List.of("jungle", "sparse_jungle", "bamboo_jungle");
	private static final List<String> SAVANNAS = List.of("savanna", "savanna_plateau", "windswept_savanna");
	private static final List<String> PLAINS = List.of("plains", "sunflower_plains");
	private static final List<String> BADLANDS = List.of("badlands", "wooded_badlands", "eroded_badlands");
	private static final List<String> SWAMPS = List.of("swamp", "mangrove_swamp");

	private ModBiomeModifications() {
	}

	public static void register() {
		// Water: waterfalls off every cliff, cascading pools down hillsides, lakes high in the mountains.
		add("cliff_waterfall", Decoration.LAKES, HIGHLANDS, SNOWY, TAIGAS, JUNGLES, BADLANDS);
		add("cliff_waterfall_sparse", Decoration.LAKES, TEMPERATE_FORESTS, PLAINS, SAVANNAS, List.of("desert"));
		add("cascade_pools", Decoration.LAKES, TEMPERATE_FORESTS, TAIGAS, JUNGLES, PLAINS, SAVANNAS,
				List.of("meadow", "cherry_grove", "windswept_hills", "windswept_forest"));
		add("alpine_tarn", Decoration.LAKES, HIGHLANDS, SNOWY, TAIGAS);

		// Terrain: dramatic cave mouths and sinkholes.
		add("cave_mouth", Decoration.RAW_GENERATION, TEMPERATE_FORESTS, TAIGAS, SNOWY, HIGHLANDS, JUNGLES, SAVANNAS, PLAINS, BADLANDS,
				List.of("desert", "pale_garden"));
		add("cenote", Decoration.RAW_GENERATION, JUNGLES, SAVANNAS);
		add("sinkhole", Decoration.RAW_GENERATION, PLAINS, TEMPERATE_FORESTS, TAIGAS);

		// Rocks.
		add("boulder_mossy", Decoration.LOCAL_MODIFICATIONS, TEMPERATE_FORESTS, TAIGAS, JUNGLES, List.of("pale_garden"));
		add("boulder_stone", Decoration.LOCAL_MODIFICATIONS, PLAINS, SAVANNAS, List.of("windswept_hills", "windswept_gravelly_hills", "stony_peaks"));
		add("boulder_granite", Decoration.LOCAL_MODIFICATIONS, List.of("meadow", "cherry_grove", "windswept_forest"));
		add("boulder_snowy", Decoration.LOCAL_MODIFICATIONS, SNOWY);
		add("boulder_badlands", Decoration.LOCAL_MODIFICATIONS, BADLANDS, List.of("desert"));

		// Landmark rock formations.
		add("badlands_arch", Decoration.LOCAL_MODIFICATIONS, BADLANDS);
		add("badlands_hoodoos", Decoration.LOCAL_MODIFICATIONS, BADLANDS);
		add("desert_arch", Decoration.LOCAL_MODIFICATIONS, List.of("desert"));
		add("mountain_arch", Decoration.LOCAL_MODIFICATIONS, List.of("meadow", "windswept_hills", "windswept_gravelly_hills", "stony_peaks",
				"savanna_plateau"));
		add("sea_arch", Decoration.LOCAL_MODIFICATIONS, List.of("beach", "stony_shore"));
		add("sea_stack", Decoration.LOCAL_MODIFICATIONS, List.of("ocean", "lukewarm_ocean", "warm_ocean", "cold_ocean", "beach", "stony_shore"));

		// Giant trees grow first so the ordinary forest fills in around them.
		add("ancient_oak", Decoration.LOCAL_MODIFICATIONS, List.of("forest", "flower_forest"));
		add("ancient_oak_rare", Decoration.LOCAL_MODIFICATIONS, PLAINS, List.of("meadow", "windswept_forest"));
		add("ancient_dark_oak", Decoration.LOCAL_MODIFICATIONS, List.of("dark_forest"));
		add("autumn_giant", Decoration.LOCAL_MODIFICATIONS, List.of("dappled_forest"));
		add("autumn_giant_rare", Decoration.LOCAL_MODIFICATIONS, List.of("forest", "flower_forest", "birch_forest"));
		add("giant_birch", Decoration.LOCAL_MODIFICATIONS, List.of("birch_forest", "old_growth_birch_forest"));
		add("redwood", Decoration.LOCAL_MODIFICATIONS, List.of("old_growth_spruce_taiga", "old_growth_pine_taiga"));
		add("redwood_rare", Decoration.LOCAL_MODIFICATIONS, List.of("taiga", "snowy_taiga", "grove"));
		add("weeping_willow", Decoration.LOCAL_MODIFICATIONS, List.of("swamp"));
		add("mangrove_willow", Decoration.LOCAL_MODIFICATIONS, List.of("mangrove_swamp"));
		add("weeping_willow_waterside", Decoration.LOCAL_MODIFICATIONS, PLAINS, List.of("forest", "flower_forest", "birch_forest", "meadow"));
		add("baobab", Decoration.LOCAL_MODIFICATIONS, SAVANNAS);
		add("spruce_snag", Decoration.LOCAL_MODIFICATIONS, TAIGAS, List.of("snowy_taiga", "windswept_forest", "grove"));
		add("oak_snag", Decoration.LOCAL_MODIFICATIONS, List.of("forest", "dark_forest", "wooded_badlands", "swamp"));
		add("acacia_snag", Decoration.LOCAL_MODIFICATIONS, SAVANNAS);

		// Forest floor: fallen giants, leaf litter, ferns, bushes and fireflies.
		add("fallen_oak_log", Decoration.VEGETAL_DECORATION, List.of("forest", "flower_forest", "plains", "meadow"));
		add("fallen_birch_log", Decoration.VEGETAL_DECORATION, List.of("birch_forest", "old_growth_birch_forest"));
		add("fallen_spruce_log", Decoration.VEGETAL_DECORATION, TAIGAS, List.of("snowy_taiga", "grove", "windswept_forest"));
		add("fallen_dark_oak_log", Decoration.VEGETAL_DECORATION, List.of("dark_forest", "pale_garden"));
		add("fallen_jungle_log", Decoration.VEGETAL_DECORATION, JUNGLES);
		add("fallen_poplar_log", Decoration.VEGETAL_DECORATION, List.of("dappled_forest"));

		add("cliff_vines_lush", Decoration.VEGETAL_DECORATION, JUNGLES, SWAMPS, List.of("dark_forest"));
		add("cliff_vines_sparse", Decoration.VEGETAL_DECORATION, List.of("forest", "flower_forest", "birch_forest", "old_growth_birch_forest",
				"dappled_forest", "cherry_grove", "windswept_forest"));
		add("forest_leaf_litter", Decoration.VEGETAL_DECORATION, List.of("forest", "flower_forest", "birch_forest",
				"old_growth_birch_forest", "windswept_forest"));
		add("forest_bush", Decoration.VEGETAL_DECORATION, TEMPERATE_FORESTS, TAIGAS, PLAINS);
		add("forest_large_fern", Decoration.VEGETAL_DECORATION, TAIGAS, JUNGLES, List.of("dark_forest", "forest"));
		add("waterside_fireflies", Decoration.VEGETAL_DECORATION, TEMPERATE_FORESTS, TAIGAS, PLAINS, JUNGLES,
				List.of("meadow", "cherry_grove"));
	}

	@SafeVarargs
	private static void add(String placedFeature, Decoration step, List<String>... biomeGroups) {
		BiomeModifications.addFeature(biomes(biomeGroups), step, ResourceKey.create(Registries.PLACED_FEATURE, NaturesMod.id(placedFeature)));
	}

	@SafeVarargs
	private static Predicate<BiomeSelectionContext> biomes(List<String>... groups) {
		Set<String> names = new LinkedHashSet<>();
		for (List<String> group : groups) {
			names.addAll(group);
		}
		List<ResourceKey<Biome>> keys = new ArrayList<>();
		for (String name : names) {
			keys.add(ResourceKey.create(Registries.BIOME, Identifier.withDefaultNamespace(name)));
		}
		return BiomeSelectors.includeByKey(keys);
	}
}
