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

/**
 * A staircase of natural pools stepping down a hillside, each spilling into the next one below:
 * a little mountain stream with its own terraces and cascades.
 */
public record CascadePoolsFeature(int minPools, int maxPools) implements Feature {
	public static final MapCodec<CascadePoolsFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(2, 12).optionalFieldOf("min_pools", 3).forGetter(CascadePoolsFeature::minPools),
			Codec.intRange(2, 12).optionalFieldOf("max_pools", 6).forGetter(CascadePoolsFeature::maxPools)
	).apply(instance, CascadePoolsFeature::new));

	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 2);
		int x0 = origin.getX();
		int z0 = origin.getZ();
		int top = WorldGenUtil.groundY(level, x0, z0);
		BlockPos start = new BlockPos(x0, top, z0);
		if (top < level.getSeaLevel() + 3 || WorldGenUtil.isAlpine(top) || !level.getFluidState(start.above()).isEmpty()
				|| !WorldGenUtil.isTerrain(level.getBlockState(start)) || !WorldGenUtil.isStructureFree(level, start)) {
			return false;
		}

		// Head down the steepest slope.
		int[] best = null;
		int bestDrop = 0;
		for (int[] d : DIRECTIONS) {
			double len = Math.sqrt(d[0] * d[0] + d[1] * d[1]);
			int x = x0 + (int) Math.round(d[0] / len * 12);
			int z = z0 + (int) Math.round(d[1] / len * 12);
			if (!window.contains(x, z)) {
				continue;
			}
			int drop = top - WorldGenUtil.surfaceY(level, x, z);
			if (drop >= 4 && drop <= 28 && drop > bestDrop) {
				bestDrop = drop;
				best = d;
			}
		}
		if (best == null) {
			return false;
		}
		double len = Math.sqrt(best[0] * best[0] + best[1] * best[1]);
		double dirX = best[0] / len;
		double dirZ = best[1] / len;

		boolean warm = WorldGenUtil.isWarm(level, start);
		boolean cold = WorldGenUtil.isCold(level, start);
		int pools = minPools + random.nextInt(Math.max(1, maxPools - minPools + 1));
		double cx = x0;
		double cz = z0;
		int previousLevel = top;
		int placed = 0;
		int[] previousSpill = null;

		for (int i = 0; i < pools; i++) {
			double radius = 1.6 + random.nextDouble() * 1.6;
			if (i > 0) {
				double step = radius + 2.5 + random.nextDouble() * 2.0;
				cx += dirX * step + (random.nextDouble() - 0.5) * 2.0;
				cz += dirZ * step + (random.nextDouble() - 0.5) * 2.0;
			}
			int px = (int) Math.round(cx);
			int pz = (int) Math.round(cz);
			int r = (int) Math.ceil(radius) + 2;
			if (!window.containsRadius(px, pz, r)) {
				break;
			}
			int ground = WorldGenUtil.surfaceY(level, px, pz);
			if (!level.getFluidState(new BlockPos(px, ground, pz)).isEmpty()) {
				break; // reached a lake or river: the cascade simply ends in it
			}
			int surface = Math.min(ground - 1, previousLevel - (i == 0 ? 1 : 1 + random.nextInt(2)));
			if (previousLevel - surface > 9 || ground - surface > 3) {
				break;
			}
			LongSet cells = WaterWorks.pool(level, window, random, px, pz, radius, radius * (0.7 + random.nextDouble() * 0.5),
					surface, 2 + random.nextInt(2), warm);
			if (cells.isEmpty()) {
				break;
			}
			decorate(level, random, cells, surface, warm, cold);
			if (previousSpill != null) {
				spill(level, window, previousSpill[0], previousSpill[1], previousLevel, px, pz, surface);
			}
			// The spill point of this pool: the cell furthest downhill.
			long spill = 0;
			double bestProjection = -Double.MAX_VALUE;
			for (long packed : cells) {
				double projection = (BlockPos.getX(packed) - px) * dirX + (BlockPos.getZ(packed) - pz) * dirZ;
				if (projection > bestProjection) {
					bestProjection = projection;
					spill = packed;
				}
			}
			previousSpill = new int[] {BlockPos.getX(spill), BlockPos.getZ(spill)};
			previousLevel = surface;
			placed++;
		}

		if (placed < 2) {
			return placed > 0;
		}
		FeatureStats.placed("cascade_pools");
		return true;
	}

	/**
	 * Carves a notch from the spill point of the upper pool towards the lower pool so the water
	 * overflows and tumbles down into it.
	 */
	private static void spill(WorldGenLevel level, WorldGenUtil.Window window, int fromX, int fromZ, int fromLevel, int toX, int toZ,
			int toLevel) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		double dx = toX - fromX;
		double dz = toZ - fromZ;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 1) {
			return;
		}
		int steps = (int) Math.ceil(distance);
		int lastX = fromX;
		int lastZ = fromZ;
		for (int s = 1; s <= steps; s++) {
			int x = (int) Math.round(fromX + dx * s / steps);
			int z = (int) Math.round(fromZ + dz * s / steps);
			if (!window.containsRadius(x, z, 1)) {
				break;
			}
			if (!level.getFluidState(pos.set(x, toLevel, z)).isEmpty()) {
				break; // reached the lower pool
			}
			int ground = WorldGenUtil.groundY(level, x, z);
			for (int y = fromLevel; y <= Math.max(ground + 1, fromLevel); y++) {
				BlockState state = level.getBlockState(pos.set(x, y, z));
				if (WorldGenUtil.isTerrain(state) || WorldGenUtil.isClearable(state)) {
					WaterWorks.set(level, pos, WaterWorks.AIR);
				}
			}
			lastX = x;
			lastZ = z;
			if (ground < fromLevel) {
				// The slope falls away here: the water can tumble down on its own.
				break;
			}
		}
		// Let the upper pool overflow into the notch.
		WaterWorks.scheduleFluidTick(level, pos.set(fromX, fromLevel, fromZ));
		if (lastX != fromX || lastZ != fromZ) {
			WaterWorks.scheduleFluidTick(level, pos.set(lastX, fromLevel, lastZ));
		}
	}

	private static void decorate(WorldGenLevel level, RandomSource random, LongSet cells, int surface, boolean warm, boolean cold) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (long packed : cells) {
			int x = BlockPos.getX(packed);
			int z = BlockPos.getZ(packed);
			if (!cold && random.nextInt(warm ? 7 : 14) == 0 && level.getBlockState(pos.set(x, surface + 1, z)).isAir()) {
				WaterWorks.set(level, pos, Blocks.LILY_PAD.defaultBlockState());
			}
			// Moss creeping over the rim stones.
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				pos.set(x + direction.getStepX(), surface, z + direction.getStepZ());
				BlockState rim = level.getBlockState(pos);
				if (rim.is(Blocks.MOSSY_COBBLESTONE) || rim.is(Blocks.COBBLESTONE) || rim.is(Blocks.ANDESITE) || rim.is(Blocks.STONE)) {
					if (random.nextInt(3) == 0 && level.getBlockState(pos.move(Direction.UP)).isAir()) {
						WaterWorks.set(level, pos, Blocks.MOSS_CARPET.defaultBlockState());
					}
				}
			}
		}
	}
}
