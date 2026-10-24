package com.naturesmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.SelfPlacing;
import com.naturesmod.worldgen.WaterWorks;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * The long trunk of a tree that toppled years ago, lying next to its broken stump, slowly being
 * reclaimed by moss, mushrooms and leaf litter.
 */
public record FallenLogFeature(BlockState log, int minLength, int maxLength, float thickChance) implements SelfPlacing {
	public static final MapCodec<FallenLogFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BlockState.CODEC.fieldOf("log").forGetter(FallenLogFeature::log),
			Codec.intRange(3, 24).optionalFieldOf("min_length", 6).forGetter(FallenLogFeature::minLength),
			Codec.intRange(3, 24).optionalFieldOf("max_length", 12).forGetter(FallenLogFeature::maxLength),
			Codec.floatRange(0, 1).optionalFieldOf("thick_chance", 0.25f).forGetter(FallenLogFeature::thickChance)
	).apply(instance, FallenLogFeature::new));

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 1);
		int ground = WorldGenUtil.groundY(level, origin.getX(), origin.getZ());
		BlockPos stump = new BlockPos(origin.getX(), ground + 1, origin.getZ());
		if (!level.getBlockState(stump.below()).is(BlockTags.SUPPORTS_VEGETATION) || !level.getFluidState(stump).isEmpty()) {
			return false;
		}
		Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		Direction side = direction.getClockWise();
		boolean thick = random.nextFloat() < thickChance;
		int length = minLength + random.nextInt(Math.max(1, maxLength - minLength + 1));
		if (thick) {
			length += 3;
		}

		// Work out how far the trunk can lie along the ground before something gets in the way.
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int gap = 1 + random.nextInt(2);
		int fits = 0;
		for (int i = 0; i < length; i++) {
			int x = stump.getX() + direction.getStepX() * (i + gap);
			int z = stump.getZ() + direction.getStepZ() * (i + gap);
			if (!window.containsRadius(x, z, 1)) {
				break;
			}
			int g = WorldGenUtil.groundY(level, x, z);
			if (g > ground || g < ground - 1 || !free(level, pos.set(x, ground + 1, z))
					|| thick && (!free(level, pos.set(x + side.getStepX(), ground + 1, z + side.getStepZ()))
					|| !free(level, pos.set(x, ground + 2, z)) || !free(level, pos.set(x + side.getStepX(), ground + 2, z + side.getStepZ())))) {
				break;
			}
			fits++;
		}
		if (fits < Math.max(3, minLength / 2)) {
			return false;
		}

		BlockState stumpLog = log.hasProperty(BlockStateProperties.AXIS) ? log.setValue(BlockStateProperties.AXIS, Direction.Axis.Y) : log;
		BlockState lying = log.hasProperty(BlockStateProperties.AXIS) ? log.setValue(BlockStateProperties.AXIS, direction.getAxis()) : log;

		// The jagged stump left standing.
		int stumpHeight = 1 + random.nextInt(thick ? 3 : 2);
		for (int y = 0; y < stumpHeight; y++) {
			if (free(level, pos.set(stump.getX(), stump.getY() + y, stump.getZ()))) {
				WaterWorks.set(level, pos, stumpLog);
			}
			if (thick && free(level, pos.set(stump.getX() + side.getStepX(), stump.getY() + y, stump.getZ() + side.getStepZ()))
					&& y < stumpHeight - 1) {
				WaterWorks.set(level, pos, stumpLog);
			}
		}

		for (int i = 0; i < fits; i++) {
			int x = stump.getX() + direction.getStepX() * (i + gap);
			int z = stump.getZ() + direction.getStepZ() * (i + gap);
			placeLog(level, random, pos.set(x, ground + 1, z), lying, direction);
			if (thick) {
				placeLog(level, random, pos.set(x + side.getStepX(), ground + 1, z + side.getStepZ()), lying, direction);
				placeLog(level, random, pos.set(x, ground + 2, z), lying, direction);
				placeLog(level, random, pos.set(x + side.getStepX(), ground + 2, z + side.getStepZ()), lying, direction);
			}
		}
		// Leaf litter scattered around.
		for (int i = 0; i < 10; i++) {
			int x = stump.getX() + direction.getStepX() * random.nextInt(fits + gap) + random.nextInt(5) - 2;
			int z = stump.getZ() + direction.getStepZ() * random.nextInt(fits + gap) + random.nextInt(5) - 2;
			if (!window.contains(x, z)) {
				continue;
			}
			int g = WorldGenUtil.groundY(level, x, z);
			pos.set(x, g + 1, z);
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(BlockTags.GRASS_BLOCKS)) {
				WaterWorks.set(level, pos, leafLitter(random));
			}
		}
		FeatureStats.placed("fallen_log");
		return true;
	}

	public static BlockState leafLitter(RandomSource random) {
		BlockState litter = WorldGenUtil.withProperty(Blocks.LEAF_LITTER.defaultBlockState(), "facing",
				Direction.Plane.HORIZONTAL.getRandomDirection(random).getSerializedName());
		return WorldGenUtil.withProperty(litter, "segment_amount", Integer.toString(1 + random.nextInt(4)));
	}

	private static boolean free(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || WorldGenUtil.isClearable(state);
	}

	private static void placeLog(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, BlockState lying, Direction direction) {
		WaterWorks.set(level, pos, lying);
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		// Moss and mushrooms on top.
		if (level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
			int roll = random.nextInt(10);
			if (roll < 4) {
				WaterWorks.set(level, pos, Blocks.MOSS_CARPET.defaultBlockState());
			} else if (roll == 4) {
				WaterWorks.set(level, pos, (random.nextBoolean() ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState());
			}
		}
		pos.set(x, y, z);
	}
}
