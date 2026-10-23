package com.naturesmod.worldgen;

import com.mojang.serialization.MapCodec;

import com.naturesmod.NaturesMod;
import com.naturesmod.worldgen.feature.AbyssFeature;
import com.naturesmod.worldgen.feature.AbyssLifeFeature;
import com.naturesmod.worldgen.feature.AlpineTarnFeature;
import com.naturesmod.worldgen.feature.BoulderFeature;
import com.naturesmod.worldgen.feature.CascadePoolsFeature;
import com.naturesmod.worldgen.feature.CaveMouthFeature;
import com.naturesmod.worldgen.feature.CliffVinesFeature;
import com.naturesmod.worldgen.feature.CliffWaterfallFeature;
import com.naturesmod.worldgen.feature.EndFeature;
import com.naturesmod.worldgen.feature.FallenLogFeature;
import com.naturesmod.worldgen.feature.HellFeature;
import com.naturesmod.worldgen.feature.NetherFeature;
import com.naturesmod.worldgen.feature.RockFormationFeature;
import com.naturesmod.worldgen.feature.SeaGardenFeature;
import com.naturesmod.worldgen.feature.SinkholeFeature;
import com.naturesmod.worldgen.feature.WildFeature;
import com.naturesmod.worldgen.tree.GiantTreeFeature;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Registers the Nature's Mod feature types; the features themselves are data-driven JSON. */
public final class ModFeatures {
	private ModFeatures() {
	}

	public static void register() {
		register("cliff_waterfall", CliffWaterfallFeature.CODEC);
		register("cascade_pools", CascadePoolsFeature.CODEC);
		register("alpine_tarn", AlpineTarnFeature.CODEC);
		register("cave_mouth", CaveMouthFeature.CODEC);
		register("sinkhole", SinkholeFeature.CODEC);
		register("boulder", BoulderFeature.CODEC);
		register("fallen_log", FallenLogFeature.CODEC);
		register("giant_tree", GiantTreeFeature.CODEC);
		register("rock_formation", RockFormationFeature.CODEC);
		register("cliff_vines", CliffVinesFeature.CODEC);
		register("abyss_feature", AbyssFeature.CODEC);
		register("abyss_life", AbyssLifeFeature.CODEC);
		register("nether_feature", NetherFeature.CODEC);
		register("hell_feature", HellFeature.CODEC);
		register("sea_garden", SeaGardenFeature.CODEC);
		register("end_feature", EndFeature.CODEC);
		register("wild_feature", WildFeature.CODEC);
	}

	private static void register(String name, MapCodec<? extends Feature> codec) {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, NaturesMod.id(name), codec);
	}
}
