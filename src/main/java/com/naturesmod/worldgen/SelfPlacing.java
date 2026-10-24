package com.naturesmod.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * The settings of a Nature's Mod feature, which also place it: each feature is one such record, and {@link ModFeatures}
 * gives every kind a feature type that hands the placing over to the settings.
 */
public interface SelfPlacing extends FeatureConfiguration {
	boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin);
}
