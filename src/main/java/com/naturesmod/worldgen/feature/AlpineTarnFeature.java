package com.naturesmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.LongSet;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.WaterWorks;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** A clear mountain lake cradled in a high hollow, ringed with gravel and boulders. */
public record AlpineTarnFeature(int minY, int maxRadius) implements Feature {
	public static final MapCodec<AlpineTarnFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.INT.optionalFieldOf("min_y", 110).forGetter(AlpineTarnFeature::minY),
			Codec.intRange(3, 12).optionalFieldOf("max_radius", 9).forGetter(AlpineTarnFeature::maxRadius)
	).apply(instance, AlpineTarnFeature::new));

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 1);
		int x0 = origin.getX();
		int z0 = origin.getZ();
		int center = WorldGenUtil.groundY(level, x0, z0);
		BlockPos start = new BlockPos(x0, center, z0);
		if (center < minY || !level.getFluidState(start.above()).isEmpty() || !WorldGenUtil.isStructureFree(level, start)) {
			return false;
		}

		double radiusX = 4 + random.nextDouble() * (maxRadius - 4);
		double radiusZ = Math.max(3, radiusX * (0.6 + random.nextDouble() * 0.4));
		int reach = (int) Math.ceil(Math.max(radiusX, radiusZ)) + 3;
		if (!window.containsRadius(x0, z0, reach)) {
			radiusX = Math.min(radiusX, 5);
			radiusZ = Math.min(radiusZ, 4);
			reach = 8;
			if (!window.containsRadius(x0, z0, reach)) {
				return false;
			}
		}

		// The lake fills the hollow up to the lowest point of its rim.
		int rimMin = Integer.MAX_VALUE;
		int rimMax = Integer.MIN_VALUE;
		for (int i = 0; i < 16; i++) {
			double angle = Math.PI * 2 * i / 16;
			int x = x0 + (int) Math.round(Math.cos(angle) * (radiusX + 1));
			int z = z0 + (int) Math.round(Math.sin(angle) * (radiusZ + 1));
			int y = WorldGenUtil.surfaceY(level, x, z);
			if (!level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
				return false;
			}
			rimMin = Math.min(rimMin, y);
			rimMax = Math.max(rimMax, y);
		}
		if (rimMax - rimMin > 7) {
			return false; // a slope, not a hollow
		}
		int surface = Math.min(rimMin, center) - 1;
		if (center - surface > 4) {
			return false;
		}

		boolean cold = WorldGenUtil.isCold(level, start);
		LongSet cells = WaterWorks.pool(level, window, random, x0, z0, radiusX, radiusZ, surface, 3 + random.nextInt(4), false);
		if (cells.size() < 12) {
			return false;
		}
		shore(level, window, random, cells, surface, cold);
		FeatureStats.placed("alpine_tarn");
		return true;
	}

	/** Gravel and stone beaches with the odd boulder around the water. */
	private static void shore(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, LongSet cells, int surface, boolean cold) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (long packed : cells) {
			int x = BlockPos.getX(packed);
			int z = BlockPos.getZ(packed);
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				for (int step = 1; step <= 2; step++) {
					int sx = x + direction.getStepX() * step;
					int sz = z + direction.getStepZ() * step;
					if (cells.contains(BlockPos.asLong(sx, 0, sz)) || !window.contains(sx, sz)) {
						continue;
					}
					int ground = WorldGenUtil.groundY(level, sx, sz);
					if (ground < surface || ground > surface + 2) {
						continue;
					}
					BlockState top = level.getBlockState(pos.set(sx, ground, sz));
					if (!WorldGenUtil.isTerrain(top)) {
						continue;
					}
					int roll = random.nextInt(10);
					BlockState beach = roll < 5 ? Blocks.GRAVEL.defaultBlockState()
							: roll < 7 ? Blocks.STONE.defaultBlockState()
							: roll < 9 ? (cold ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState())
							: Blocks.ANDESITE.defaultBlockState();
					WaterWorks.set(level, pos, beach);
					BlockState above = level.getBlockState(pos.move(Direction.UP));
					if (!above.isAir() && WorldGenUtil.isClearable(above)) {
						WaterWorks.set(level, pos, WaterWorks.AIR);
					}
					if (random.nextInt(60) == 0) {
						smallRock(level, random, pos.immutable());
					}
				}
			}
		}
	}

	private static void smallRock(WorldGenLevel level, RandomSource random, BlockPos base) {
		BlockState stone = random.nextBoolean() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		WaterWorks.set(level, base, stone);
		if (random.nextBoolean() && level.getBlockState(base.above()).isAir()) {
			WaterWorks.set(level, base.above(), Blocks.ANDESITE.defaultBlockState());
		}
	}
}
