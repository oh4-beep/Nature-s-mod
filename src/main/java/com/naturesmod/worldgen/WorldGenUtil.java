package com.naturesmod.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.material.FluidState;

/**
 * Shared helpers for Nature's Mod world features.
 *
 * <p>While a chunk is being decorated a feature may only touch the 3x3 chunk area around it,
 * so every feature works inside a {@link Window} and never reads or writes outside of it.
 */
public final class WorldGenUtil {
	private WorldGenUtil() {
	}

	/** The horizontal area a feature is allowed to read and write. */
	public record Window(int minX, int minZ, int maxX, int maxZ) {
		public static Window around(BlockPos origin, int margin) {
			int cx = origin.getX() >> 4;
			int cz = origin.getZ() >> 4;
			return new Window(((cx - 1) << 4) + margin, ((cz - 1) << 4) + margin,
					((cx + 2) << 4) - 1 - margin, ((cz + 2) << 4) - 1 - margin);
		}

		public boolean contains(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}

		public boolean contains(BlockPos pos) {
			return contains(pos.getX(), pos.getZ());
		}

		/** True when the whole disc of the given radius around (x, z) is inside the window. */
		public boolean containsRadius(int x, int z, int radius) {
			return contains(x - radius, z - radius) && contains(x + radius, z + radius);
		}
	}

	/** Natural ground that features may dig into or build on. */
	public static boolean isTerrain(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(BlockTags.SUBSTRATE_OVERWORLD)
				|| state.is(BlockTags.SAND)
				|| state.is(BlockTags.TERRACOTTA)
				|| state.is(Blocks.GRAVEL)
				|| state.is(Blocks.CLAY)
				|| state.is(Blocks.SANDSTONE)
				|| state.is(Blocks.RED_SANDSTONE)
				|| state.is(Blocks.CALCITE)
				|| state.is(Blocks.SNOW_BLOCK)
				|| state.is(Blocks.POWDER_SNOW)
				|| state.is(Blocks.PACKED_ICE)
				|| state.is(Blocks.BLUE_ICE)
				|| state.is(Blocks.DRIPSTONE_BLOCK)
				|| state.is(Blocks.COBBLESTONE)
				|| state.is(Blocks.MOSSY_COBBLESTONE)
				|| state.is(Blocks.SMOOTH_BASALT);
	}

	/** Loose ground blocks that look natural as a river or pool bed. */
	public static boolean isSoftGround(BlockState state) {
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY);
	}

	/** Things lying on the ground (plants, snow layers, leaf litter...) that can be cleared freely. */
	public static boolean isClearable(BlockState state) {
		if (state.isAir()) {
			return true;
		}
		if (!state.getFluidState().isEmpty()) {
			return false;
		}
		return state.canBeReplaced()
				|| state.is(BlockTags.FLOWERS)
				|| state.is(BlockTags.SAPLINGS)
				|| state.is(Blocks.SWEET_BERRY_BUSH)
				|| state.is(Blocks.BROWN_MUSHROOM)
				|| state.is(Blocks.RED_MUSHROOM)
				|| state.is(Blocks.MOSS_CARPET)
				|| state.is(Blocks.FIREFLY_BUSH)
				|| state.is(Blocks.CACTUS)
				|| state.is(Blocks.SUGAR_CANE)
				|| state.is(Blocks.PUMPKIN)
				|| state.is(Blocks.MELON);
	}

	/** Blocks belonging to trees or plants (skipped when looking for the ground). */
	public static boolean isVegetation(BlockState state) {
		return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.BEE_NEST)
				|| state.is(Blocks.SHELF_MUSHROOM) || state.is(Blocks.MUSHROOM_STEM)
				|| state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
				|| state.is(Blocks.BAMBOO) || state.is(Blocks.COCOA);
	}

	public static boolean isWater(BlockState state) {
		return state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
	}

	public static boolean isStructureFree(WorldGenLevel level, BlockPos pos) {
		for (Structure structure : level.getChunk(pos).getAllReferences().keySet()) {
			if (structure.step() == GenerationStep.Decoration.SURFACE_STRUCTURES) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Y of the highest natural ground block in the column, ignoring trees, plants and snow layers.
	 * Water is treated as "not ground", so for a lake this returns the lake bed.
	 */
	public static int groundY(WorldGenLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
		int minY = level.getMinY();
		while (y > minY) {
			BlockState state = level.getBlockState(pos.setY(y));
			if (!state.isAir() && state.getFluidState().isEmpty() && !isClearable(state) && !isVegetation(state)) {
				return y;
			}
			y--;
		}
		return minY;
	}

	/** Y of the highest block that is either ground or a liquid surface. */
	public static int surfaceY(WorldGenLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
		int minY = level.getMinY();
		while (y > minY) {
			BlockState state = level.getBlockState(pos.setY(y));
			if (!state.getFluidState().isEmpty()) {
				return y;
			}
			if (!state.isAir() && !isClearable(state) && !isVegetation(state)) {
				return y;
			}
			y--;
		}
		return minY;
	}

	public static boolean hasLiquidNeighbour(WorldGenLevel level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			FluidState fluid = level.getFluidState(pos.relative(direction));
			if (!fluid.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	public static boolean isCold(WorldGenLevel level, BlockPos pos) {
		Biome biome = level.getBiome(pos).value();
		return biome.coldEnoughToSnow(pos, level.getSeaLevel());
	}

	public static boolean isWarm(WorldGenLevel level, BlockPos pos) {
		return level.getBiome(pos).value().getBaseTemperature() >= 0.7f;
	}

	/**
	 * Sets a block state property by name, leaving the state untouched if the block has no such
	 * property. Handy for properties whose Java constants moved between Minecraft versions.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static BlockState withProperty(BlockState state, String name, String value) {
		Property property = state.getBlock().getStateDefinition().getProperty(name);
		if (property == null) {
			return state;
		}
		java.util.Optional parsed = property.getValue(value);
		return parsed.isPresent() ? state.setValue(property, (Comparable) parsed.get()) : state;
	}

	public static <T> T pick(RandomSource random, java.util.List<T> list) {
		return list.get(random.nextInt(list.size()));
	}

	/** Smooth pseudo-noise in [-1, 1] built from a few sines, for organic outlines. */
	public static final class Wobble {
		private final double[] phases;
		private final double[] freqs;

		public Wobble(RandomSource random, int terms, double baseFreq) {
			phases = new double[terms];
			freqs = new double[terms];
			for (int i = 0; i < terms; i++) {
				phases[i] = random.nextDouble() * Math.PI * 2;
				freqs[i] = baseFreq * (1 + i) * (0.8 + random.nextDouble() * 0.4);
			}
		}

		public double at(double t) {
			double sum = 0;
			double norm = 0;
			for (int i = 0; i < phases.length; i++) {
				double weight = 1.0 / (1 + i);
				sum += Math.sin(t * freqs[i] + phases[i]) * weight;
				norm += weight;
			}
			return sum / norm;
		}

		public double at(double a, double b) {
			return (at(a) + at(b * 1.37 + 11.0)) * 0.5;
		}
	}
}
