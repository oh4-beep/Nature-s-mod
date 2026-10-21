package com.naturesmod.nether;

import java.util.List;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.NetherBiomes;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import com.naturesmod.NaturesMod;

/**
 * The Nether overhaul's biomes. The Nether's terrain itself (twice as tall, with vast caverns,
 * floating islands and colossal pillars above the classic Nether) is data: see
 * tools/worldgen/nether.py.
 */
public final class NetherOverhaul {
	private static final List<String> VANILLA = List.of("nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas");

	private NetherOverhaul() {
	}

	public static void register() {
		// Down in the classic Nether, between the crimson forests and the soul sand valleys.
		NetherBiomes.addNetherBiome(biome("ember_wastes"), Climate.parameters(0.65F, -0.35F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		// Up in the great caverns ("depth" rises with height in the Nether's noise router).
		NetherBiomes.addNetherBiome(biome("glowstone_grotto"), Climate.parameters(Climate.Parameter.span(-1.0F, 0.15F),
				Climate.Parameter.span(-1.0F, 1.0F), Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
				Climate.Parameter.span(0.55F, 1.6F), Climate.Parameter.point(0.0F), 0.0F));
		NetherBiomes.addNetherBiome(biome("obsidian_spires"), Climate.parameters(Climate.Parameter.span(0.15F, 1.0F),
				Climate.Parameter.span(-1.0F, 1.0F), Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
				Climate.Parameter.span(0.55F, 1.6F), Climate.Parameter.point(0.0F), 0.0F));

		// Lavafalls, hanging cages and geysers in the vanilla biomes too.
		add("lavafall_sparse", VANILLA);
		add("hanging_cage_rare", List.of("nether_wastes", "soul_sand_valley", "basalt_deltas"));
		add("geyser_field_sparse", List.of("basalt_deltas", "nether_wastes"));
	}

	private static ResourceKey<Biome> biome(String name) {
		return ResourceKey.create(Registries.BIOME, NaturesMod.id(name));
	}

	private static void add(String feature, List<String> biomes) {
		List<ResourceKey<Biome>> keys = biomes.stream().map(name -> ResourceKey.create(Registries.BIOME, Identifier.withDefaultNamespace(name))).toList();
		ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, NaturesMod.id("nether/" + feature));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(keys), Decoration.UNDERGROUND_DECORATION, key);
	}
}
