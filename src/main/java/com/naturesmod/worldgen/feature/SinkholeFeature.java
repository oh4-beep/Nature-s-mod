package com.naturesmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.WaterWorks;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A deep collapsed sinkhole. With {@code water} it becomes a cenote: a narrow opening that widens
 * into a cavern with a turquoise pool at the bottom and vines trailing all the way down.
 */
public record SinkholeFeature(boolean water, int minDepth, int maxDepth) implements Feature {
	public static final MapCodec<SinkholeFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("water", true).forGetter(SinkholeFeature::water),
			Codec.intRange(6, 60).optionalFieldOf("min_depth", 14).forGetter(SinkholeFeature::minDepth),
			Codec.intRange(6, 60).optionalFieldOf("max_depth", 30).forGetter(SinkholeFeature::maxDepth)
	).apply(instance, SinkholeFeature::new));

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 1);
		int x0 = origin.getX();
		int z0 = origin.getZ();
		int ground = WorldGenUtil.groundY(level, x0, z0);
		BlockPos start = new BlockPos(x0, ground, z0);
		if (!level.getFluidState(start.above()).isEmpty() || !WorldGenUtil.isTerrain(level.getBlockState(start))
				|| !WorldGenUtil.isStructureFree(level, start)) {
			return false;
		}
		double radius = 3 + random.nextDouble() * 3.5;
		int depth = minDepth + random.nextInt(Math.max(1, maxDepth - minDepth + 1));
		int bottom = ground - depth;
		if (bottom < level.getMinY() + 12 || !window.containsRadius(x0, z0, (int) Math.ceil(radius * 1.5) + 2)) {
			return false;
		}
		// Keep the rim fairly level so the hole doesn't cut a hillside open.
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			int y = WorldGenUtil.groundY(level, x0 + direction.getStepX() * (int) radius, z0 + direction.getStepZ() * (int) radius);
			if (Math.abs(y - ground) > 4) {
				return false;
			}
		}

		WorldGenUtil.Wobble wobble = new WorldGenUtil.Wobble(random, 3, 1.0);
		WorldGenUtil.Wobble layers = new WorldGenUtil.Wobble(random, 2, 0.2);
		int waterTop = water ? bottom + 3 + random.nextInt(4) : Integer.MIN_VALUE;
		LongSet shaft = new LongOpenHashSet();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int maxR = (int) Math.ceil(radius * 1.5) + 1;

		for (int y = ground + 2; y >= bottom; y--) {
			double fromTop = (ground - y) / (double) depth;
			// Narrow throat, then a wide undercut cavern towards the bottom.
			double r = radius * (fromTop < 0.25 ? 1.0 : 1.0 + 0.45 * Math.sin(Math.min(1.0, (fromTop - 0.25) / 0.6) * Math.PI / 2));
			r *= 1 + 0.08 * layers.at(y);
			for (int dx = -maxR; dx <= maxR; dx++) {
				for (int dz = -maxR; dz <= maxR; dz++) {
					double angle = Math.atan2(dz, dx);
					double edge = r * (1 + 0.15 * wobble.at(angle + y * 0.05));
					if (dx * dx + dz * dz > edge * edge) {
						continue;
					}
					pos.set(x0 + dx, y, z0 + dz);
					BlockState state = level.getBlockState(pos);
					if (state.is(BlockTags.FEATURES_CANNOT_REPLACE) || state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty()) {
						continue;
					}
					if (y <= waterTop) {
						WaterWorks.set(level, pos, WaterWorks.WATER);
					} else if (!state.isAir()) {
						WaterWorks.set(level, pos, y > ground - 3 ? WaterWorks.AIR : Blocks.CAVE_AIR.defaultBlockState());
					}
					shaft.add(pos.asLong());
				}
			}
		}

		// Seal the cenote so the pool cannot drain into neighbouring caves.
		for (long packed : shaft) {
			int y = BlockPos.getY(packed);
			if (y > waterTop) {
				continue;
			}
			for (Direction direction : Direction.values()) {
				if (direction == Direction.UP) {
					continue;
				}
				pos.set(BlockPos.getX(packed) + direction.getStepX(), y + direction.getStepY(), BlockPos.getZ(packed) + direction.getStepZ());
				if (shaft.contains(pos.asLong())) {
					continue;
				}
				if (!WaterWorks.holdsWater(level.getBlockState(pos))) {
					WaterWorks.set(level, pos, direction == Direction.DOWN ? Blocks.CLAY.defaultBlockState() : Blocks.STONE.defaultBlockState());
				}
			}
		}

		decorate(level, random, shaft, ground, waterTop, WorldGenUtil.isWarm(level, start));
		FeatureStats.placed(water ? "cenote" : "sinkhole");
		return true;
	}

	private static void decorate(WorldGenLevel level, RandomSource random, LongSet shaft, int ground, int waterTop, boolean warm) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (long packed : shaft) {
			int x = BlockPos.getX(packed);
			int y = BlockPos.getY(packed);
			int z = BlockPos.getZ(packed);
			if (y <= waterTop) {
				if (y == waterTop && warm && random.nextInt(9) == 0 && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
					WaterWorks.set(level, pos, Blocks.LILY_PAD.defaultBlockState());
				}
				continue;
			}
			// Curtains of vines down the walls, starting near the rim.
			if (y >= ground - 3 && y <= ground && random.nextInt(3) == 0) {
				for (Direction direction : Direction.Plane.HORIZONTAL) {
					pos.set(x + direction.getStepX(), y, z + direction.getStepZ());
					if (!shaft.contains(pos.asLong()) && WorldGenUtil.isTerrain(level.getBlockState(pos))) {
						int length = 4 + random.nextInt(Math.max(1, (ground - waterTop) - 4));
						vineCurtain(level, new BlockPos(x, y, z), direction, length, waterTop);
						break;
					}
				}
			}
			// Hanging roots and glow berries under the undercut ceiling.
			pos.set(x, y + 1, z);
			if (!shaft.contains(pos.asLong()) && y < ground - 3 && WorldGenUtil.isTerrain(level.getBlockState(pos))
					&& level.getBlockState(pos.set(x, y, z)).isAir()) {
				int roll = random.nextInt(10);
				if (roll == 0) {
					WaterWorks.set(level, pos, Blocks.HANGING_ROOTS.defaultBlockState());
				} else if (roll == 1 && level.getBlockState(pos.set(x, y - 1, z)).isAir()) {
					WaterWorks.set(level, pos.set(x, y, z), Blocks.CAVE_VINES.defaultBlockState()
							.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BERRIES, random.nextBoolean()));
				}
			}
			// Moss on ledges.
			pos.set(x, y - 1, z);
			if (!shaft.contains(pos.asLong()) && WorldGenUtil.isTerrain(level.getBlockState(pos)) && random.nextInt(3) == 0) {
				WaterWorks.set(level, pos, Blocks.MOSS_BLOCK.defaultBlockState());
				if (random.nextInt(3) == 0) {
					WaterWorks.set(level, pos.set(x, y, z), Blocks.MOSS_CARPET.defaultBlockState());
				}
			}
		}
	}

	private static void vineCurtain(WorldGenLevel level, BlockPos start, Direction towardWall, int length, int waterTop) {
		BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(towardWall), true);
		BlockPos.MutableBlockPos pos = start.mutable();
		// Vines may hang free below the end of the wall: each one is held up by the vine above it.
		for (int i = 0; i < length && pos.getY() > waterTop + 1; i++) {
			if (!level.getBlockState(pos).isAir()) {
				break;
			}
			WaterWorks.set(level, pos, vine);
			pos.move(Direction.DOWN);
		}
	}
}
