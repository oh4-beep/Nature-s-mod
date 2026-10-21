package com.naturesmod.worldgen;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** Helpers for placing still and falling water during world generation without leaks. */
public final class WaterWorks {
	public static final BlockState WATER = Blocks.WATER.defaultBlockState();
	public static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private WaterWorks() {
	}

	/** Blocks that hold water back. */
	public static boolean holdsWater(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !WorldGenUtil.isClearable(state);
	}

	public static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS);
	}

	/** A flowing (non-source) water block, e.g. the curtain of a waterfall. */
	public static BlockState flowing(int amount, boolean falling) {
		return Fluids.WATER.getFlowing(amount, falling).createLegacyBlock();
	}

	/** Makes the fluid at the position recalculate as soon as the chunk is loaded. */
	public static void scheduleFluidTick(WorldGenLevel level, BlockPos pos) {
		FluidState fluid = level.getFluidState(pos);
		if (!fluid.isEmpty()) {
			level.scheduleTick(pos, fluid.getType(), 0);
		}
	}

	/** Bed material for a pool or stream. */
	public static BlockState bed(RandomSource random, boolean warm) {
		int roll = random.nextInt(10);
		if (roll < 4) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		if (roll < 6) {
			return Blocks.CLAY.defaultBlockState();
		}
		if (roll < 8) {
			return warm ? Blocks.SAND.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
		}
		return Blocks.STONE.defaultBlockState();
	}

	/** Natural-looking stone used to build up the rim of a pool where the ground is too low. */
	public static BlockState rim(RandomSource random) {
		int roll = random.nextInt(10);
		if (roll < 4) {
			return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
		}
		if (roll < 6) {
			return Blocks.COBBLESTONE.defaultBlockState();
		}
		if (roll < 8) {
			return Blocks.ANDESITE.defaultBlockState();
		}
		return Blocks.STONE.defaultBlockState();
	}

	/**
	 * Digs and fills a round pool whose surface sits at {@code surfaceY}. Returns the set of water
	 * columns (packed x/z as BlockPos longs at y=0) so callers can decorate it.
	 */
	public static LongSet pool(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, int cx, int cz,
			double radiusX, double radiusZ, int surfaceY, int maxDepth, boolean warm) {
		LongSet cells = new LongOpenHashSet();
		int rx = (int) Math.ceil(radiusX);
		int rz = (int) Math.ceil(radiusZ);
		WorldGenUtil.Wobble wobble = new WorldGenUtil.Wobble(random, 3, 1.0);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int[][] depths = new int[rx * 2 + 1][rz * 2 + 1];

		for (int dx = -rx; dx <= rx; dx++) {
			for (int dz = -rz; dz <= rz; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!window.containsRadius(x, z, 1)) {
					continue;
				}
				double angle = Math.atan2(dz, dx);
				double edge = 1.0 + 0.18 * wobble.at(angle);
				double d = Math.sqrt((dx * dx) / (radiusX * radiusX) + (dz * dz) / (radiusZ * radiusZ)) / edge;
				if (d > 1.0) {
					continue;
				}
				int depth = 1 + (int) Math.round((1.0 - d * d) * (maxDepth - 1));
				depths[dx + rx][dz + rz] = depth;
				cells.add(BlockPos.asLong(x, 0, z));
			}
		}
		if (cells.isEmpty()) {
			return cells;
		}

		for (long packed : cells) {
			int x = BlockPos.getX(packed);
			int z = BlockPos.getZ(packed);
			int depth = depths[x - cx + rx][z - cz + rz];
			for (int j = 0; j < depth; j++) {
				set(level, pos.set(x, surfaceY - j, z), WATER);
			}
			BlockState below = level.getBlockState(pos.set(x, surfaceY - depth, z));
			if (!holdsWater(below) || random.nextInt(3) == 0) {
				set(level, pos, bed(random, warm));
			}
			// Open the pool to the sky.
			for (int y = surfaceY + 1; y <= surfaceY + 3; y++) {
				BlockState above = level.getBlockState(pos.set(x, y, z));
				if (above.isAir()) {
					break;
				}
				if (WorldGenUtil.isClearable(above) || WorldGenUtil.isTerrain(above)) {
					set(level, pos, AIR);
				}
			}
		}

		seal(level, window, random, cells, surfaceY, depths, cx - rx, cz - rz);
		return cells;
	}

	/** Builds a rim wherever a pool's water would otherwise leak out sideways. */
	private static void seal(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, LongSet cells, int surfaceY,
			int[][] depths, int offsetX, int offsetZ) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (long packed : cells) {
			int x = BlockPos.getX(packed);
			int z = BlockPos.getZ(packed);
			int depth = depths[x - offsetX][z - offsetZ];
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				int nx = x + direction.getStepX();
				int nz = z + direction.getStepZ();
				if (cells.contains(BlockPos.asLong(nx, 0, nz))) {
					continue;
				}
				for (int j = 0; j < depth; j++) {
					BlockState neighbour = level.getBlockState(pos.set(nx, surfaceY - j, nz));
					if (!holdsWater(neighbour) && neighbour.getFluidState().isEmpty()) {
						set(level, pos, rim(random));
					}
				}
			}
		}
	}
}
