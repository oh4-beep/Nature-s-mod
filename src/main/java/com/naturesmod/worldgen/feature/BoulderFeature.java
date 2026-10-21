package com.naturesmod.worldgen.feature;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.WaterWorks;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** A weathered boulder made of a few fused, partly buried lumps of rock, often wearing a cap of moss. */
public record BoulderFeature(List<BlockState> materials, float mossChance, float minRadius, float maxRadius) implements Feature {
	public static final MapCodec<BoulderFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ExtraCodecs.nonEmptyList(BlockState.CODEC.listOf()).fieldOf("materials").forGetter(BoulderFeature::materials),
			Codec.floatRange(0, 1).optionalFieldOf("moss_chance", 0.0f).forGetter(BoulderFeature::mossChance),
			Codec.floatRange(1, 6).optionalFieldOf("min_radius", 1.5f).forGetter(BoulderFeature::minRadius),
			Codec.floatRange(1, 6).optionalFieldOf("max_radius", 3.2f).forGetter(BoulderFeature::maxRadius)
	).apply(instance, BoulderFeature::new));

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 0);
		int ground = WorldGenUtil.groundY(level, origin.getX(), origin.getZ());
		BlockPos base = new BlockPos(origin.getX(), ground, origin.getZ());
		if (!WorldGenUtil.isTerrain(level.getBlockState(base)) || !level.getFluidState(base.above()).isEmpty()) {
			return false;
		}

		int lumps = 1 + random.nextInt(3);
		double[][] blobs = new double[lumps][6];
		double reach = 0;
		for (int i = 0; i < lumps; i++) {
			double r = minRadius + random.nextDouble() * Math.max(0, maxRadius - minRadius);
			if (i > 0) {
				r *= 0.6 + random.nextDouble() * 0.3;
			}
			double ox = i == 0 ? 0 : (random.nextDouble() - 0.5) * r * 2;
			double oz = i == 0 ? 0 : (random.nextDouble() - 0.5) * r * 2;
			blobs[i] = new double[] {ox, r * (0.1 + random.nextDouble() * 0.3), oz, r, r * (0.65 + random.nextDouble() * 0.25),
					r * (0.8 + random.nextDouble() * 0.3)};
			reach = Math.max(reach, Math.abs(ox) + r + 1);
		}
		int span = (int) Math.ceil(reach);
		if (!window.containsRadius(origin.getX(), origin.getZ(), span)) {
			return false;
		}

		WorldGenUtil.Wobble wobble = new WorldGenUtil.Wobble(random, 3, 1.3);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int placed = 0;
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				for (int dy = -2; dy <= span; dy++) {
					if (!inside(blobs, dx, dy, dz, wobble)) {
						continue;
					}
					pos.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
					BlockState state = level.getBlockState(pos);
					if (!state.isAir() && !WorldGenUtil.isClearable(state) && !WorldGenUtil.isTerrain(state)) {
						continue;
					}
					// Patches of a secondary material, like veins and lichen-darkened faces.
					double patch = wobble.at(dx * 0.7 + dy * 0.3, dz * 0.7 - dy * 0.2);
					int index = materials.size() == 1 ? 0 : Math.floorMod((int) Math.floor((patch + 1) * materials.size() * 0.75 + random.nextInt(2)), materials.size());
					WaterWorks.set(level, pos, materials.get(index));
					placed++;
				}
			}
		}
		if (placed == 0) {
			return false;
		}

		if (mossChance > 0) {
			for (int dx = -span; dx <= span; dx++) {
				for (int dz = -span; dz <= span; dz++) {
					for (int dy = span; dy >= -1; dy--) {
						if (!inside(blobs, dx, dy, dz, wobble)) {
							continue;
						}
						pos.set(base.getX() + dx, base.getY() + dy + 1, base.getZ() + dz);
						if (level.getBlockState(pos).isAir() && random.nextFloat() < mossChance) {
							WaterWorks.set(level, pos, Blocks.MOSS_CARPET.defaultBlockState());
						}
						break;
					}
				}
			}
		}
		FeatureStats.placed("boulder");
		return true;
	}

	private static boolean inside(double[][] blobs, int dx, int dy, int dz, WorldGenUtil.Wobble wobble) {
		double rough = 1 + 0.12 * wobble.at(dx * 0.9 + dz * 0.4, dy * 1.1 - dz * 0.3);
		for (double[] blob : blobs) {
			double nx = (dx - blob[0]) / blob[3];
			double ny = (dy - blob[1]) / blob[4];
			double nz = (dz - blob[2]) / blob[5];
			if (nx * nx + ny * ny + nz * nz <= rough * rough) {
				return true;
			}
		}
		return false;
	}
}
