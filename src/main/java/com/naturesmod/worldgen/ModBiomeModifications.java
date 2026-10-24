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
			"dark_forest", "naturesmod:maple_woods", "naturesmod:glowshroom_forest");
	private static final List<String> TAIGAS = List.of("taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga");
	private static final List<String> SNOWY = List.of("snowy_taiga", "snowy_plains", "ice_spikes", "snowy_slopes", "frozen_peaks",
			"jagged_peaks", "grove", "naturesmod:tundra");
	private static final List<String> HIGHLANDS = List.of("meadow", "stony_peaks", "cherry_grove", "windswept_hills", "windswept_forest",
			"windswept_gravelly_hills", "windswept_savanna", "savanna_plateau", "naturesmod:heather_moors");
	private static final List<String> JUNGLES = List.of("jungle", "sparse_jungle", "bamboo_jungle");
	private static final List<String> SAVANNAS = List.of("savanna", "savanna_plateau", "windswept_savanna");
	private static final List<String> PLAINS = List.of("plains", "sunflower_plains", "naturesmod:lavender_fields", "naturesmod:golden_prairie");
	private static final List<String> BADLANDS = List.of("badlands", "wooded_badlands", "eroded_badlands", "naturesmod:petrified_forest");
	private static final List<String> SWAMPS = List.of("swamp", "mangrove_swamp", "naturesmod:bayou");
	private static final List<String> BEACHES = List.of("beach", "naturesmod:tropical_shore");
	private static final List<String> WARM_SEAS = List.of("warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean");
	private static final List<String> COLD_SEAS = List.of("cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean");

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
				List.of("desert", "pale_garden", "naturesmod:volcanic_peaks"));
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
		add("sea_arch", Decoration.LOCAL_MODIFICATIONS, BEACHES, List.of("stony_shore"));
		add("sea_stack", Decoration.LOCAL_MODIFICATIONS, List.of("ocean", "lukewarm_ocean", "warm_ocean", "cold_ocean", "stony_shore"), BEACHES);

		// Giant trees grow first so the ordinary forest fills in around them.
		add("ancient_oak", Decoration.LOCAL_MODIFICATIONS, List.of("forest", "flower_forest"));
		add("ancient_oak_rare", Decoration.LOCAL_MODIFICATIONS, PLAINS, List.of("meadow", "windswept_forest"));
		add("ancient_dark_oak", Decoration.LOCAL_MODIFICATIONS, List.of("dark_forest"));
		// This version has no autumn leaves of its own: the giants turn red only under the Maple Woods' autumn sky.
		add("autumn_giant", Decoration.LOCAL_MODIFICATIONS, List.of("naturesmod:maple_woods"));
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

		add("cliff_vines_lush", Decoration.VEGETAL_DECORATION, JUNGLES, SWAMPS, List.of("dark_forest"));
		add("cliff_vines_sparse", Decoration.VEGETAL_DECORATION, List.of("forest", "flower_forest", "birch_forest", "old_growth_birch_forest",
				"cherry_grove", "windswept_forest"));
		add("forest_leaf_litter", Decoration.VEGETAL_DECORATION, List.of("forest", "flower_forest", "birch_forest",
				"old_growth_birch_forest", "windswept_forest"));
		add("forest_bush", Decoration.VEGETAL_DECORATION, TEMPERATE_FORESTS, TAIGAS, PLAINS);
		add("forest_large_fern", Decoration.VEGETAL_DECORATION, TAIGAS, JUNGLES, List.of("dark_forest", "forest"));
		add("waterside_fireflies", Decoration.VEGETAL_DECORATION, TEMPERATE_FORESTS, TAIGAS, PLAINS, JUNGLES,
				List.of("meadow", "cherry_grove"));

		// The wild Overworld's wonders, in vanilla's lands as well as its own: fallen stars, hot springs, berries,
		// cattails by the water, glowcaps under dark canopies and geodes of new crystal deep down everywhere.
		add("wild/meteor_crater", Decoration.LOCAL_MODIFICATIONS, List.of("plains", "sunflower_plains", "savanna", "desert", "badlands",
				"snowy_plains", "taiga", "meadow", "naturesmod:golden_prairie", "naturesmod:lavender_fields", "naturesmod:salt_flats",
				"naturesmod:tundra", "naturesmod:heather_moors", "naturesmod:petrified_forest", "naturesmod:volcanic_peaks"));
		add("wild/hot_spring", Decoration.LAKES, List.of("meadow", "windswept_hills", "windswept_gravelly_hills", "windswept_forest", "taiga",
				"old_growth_pine_taiga", "old_growth_spruce_taiga", "cherry_grove", "stony_peaks", "naturesmod:heather_moors",
				"naturesmod:volcanic_peaks", "naturesmod:tundra"));
		add("wild/blueberries", Decoration.VEGETAL_DECORATION, TAIGAS, List.of("snowy_taiga", "grove", "windswept_forest", "naturesmod:heather_moors"));
		add("wild/riverside_cattails", Decoration.VEGETAL_DECORATION, PLAINS, SWAMPS, List.of("river", "forest", "flower_forest", "birch_forest",
				"naturesmod:maple_woods"));
		add("wild/glowcaps_sparse", Decoration.VEGETAL_DECORATION, List.of("dark_forest", "pale_garden", "old_growth_spruce_taiga"));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), Decoration.LOCAL_MODIFICATIONS, placed("wild/rare_azure_geode"));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), Decoration.LOCAL_MODIFICATIONS, placed("wild/rare_citrine_geode"));

		// The sea: reefs and sea beds full of life, shells on the beaches, and more kelp, seagrass and coral.
		add("ocean/reef_garden", Decoration.VEGETAL_DECORATION, List.of("warm_ocean"));
		add("ocean/reef_garden_sparse", Decoration.VEGETAL_DECORATION, List.of("lukewarm_ocean", "deep_lukewarm_ocean"));
		add("ocean/temperate_garden", Decoration.VEGETAL_DECORATION, List.of("ocean", "deep_ocean"));
		add("ocean/cold_garden", Decoration.VEGETAL_DECORATION, COLD_SEAS);
		add("ocean/beach_shells", Decoration.VEGETAL_DECORATION, BEACHES, List.of("snowy_beach"));
		add("ocean/extra_coral", Decoration.VEGETAL_DECORATION, List.of("warm_ocean", "lukewarm_ocean"));
		add("ocean/extra_kelp", Decoration.VEGETAL_DECORATION, List.of("ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean",
				"lukewarm_ocean", "deep_lukewarm_ocean"));
		add("ocean/extra_seagrass", Decoration.VEGETAL_DECORATION, WARM_SEAS, List.of("ocean", "deep_ocean"), COLD_SEAS);
		add("ocean/extra_sea_pickles", Decoration.VEGETAL_DECORATION, WARM_SEAS);
	}

	@SafeVarargs
	private static void add(String placedFeature, Decoration step, List<String>... biomeGroups) {
		BiomeModifications.addFeature(biomes(biomeGroups), step, placed(placedFeature));
	}

	private static ResourceKey<PlacedFeature> placed(String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, NaturesMod.id(name));
	}

	@SafeVarargs
	private static Predicate<BiomeSelectionContext> biomes(List<String>... groups) {
		Set<String> names = new LinkedHashSet<>();
		for (List<String> group : groups) {
			names.addAll(group);
		}
		List<ResourceKey<Biome>> keys = new ArrayList<>();
		for (String name : names) {
			keys.add(ResourceKey.create(Registries.BIOME, name.contains(":")
					? Identifier.fromNamespaceAndPath(name.substring(0, name.indexOf(':')), name.substring(name.indexOf(':') + 1))
					: Identifier.withDefaultNamespace(name)));
		}
		return BiomeSelectors.includeByKey(keys);
	}
}
