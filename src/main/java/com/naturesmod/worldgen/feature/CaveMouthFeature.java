package com.naturesmod.worldgen.feature;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.WaterWorks;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A huge arched cave mouth opening into a hillside: mossy floor near the light, vines and glow
 * berries hanging from the arch, dripstone deeper inside and sometimes a spring flowing out.
 */
public record CaveMouthFeature(int minLength, int maxLength) implements Feature {
	public static final MapCodec<CaveMouthFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(6, 40).optionalFieldOf("min_length", 12).forGetter(CaveMouthFeature::minLength),
			Codec.intRange(6, 40).optionalFieldOf("max_length", 24).forGetter(CaveMouthFeature::maxLength)
	).apply(instance, CaveMouthFeature::new));

	private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
	private static final double STEP = 0.5;

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	private record Slice(double x, double z, double t, double floor, double halfWidth, double height, double perpX, double perpZ) {
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 1);
		int x0 = origin.getX();
		int z0 = origin.getZ();
		int ground = WorldGenUtil.groundY(level, x0, z0);
		BlockPos start = new BlockPos(x0, ground, z0);
		if (ground < level.getSeaLevel() + 2 || !level.getFluidState(start.above()).isEmpty()
				|| !WorldGenUtil.isTerrain(level.getBlockState(start)) || !WorldGenUtil.isStructureFree(level, start)) {
			return false;
		}

		// Find a hillside rising in front of us.
		int[] best = null;
		int bestRise = 0;
		for (int[] d : DIRECTIONS) {
			double len = Math.sqrt(d[0] * d[0] + d[1] * d[1]);
			int nearX = x0 + (int) Math.round(d[0] / len * 6);
			int nearZ = z0 + (int) Math.round(d[1] / len * 6);
			int farX = x0 + (int) Math.round(d[0] / len * 10);
			int farZ = z0 + (int) Math.round(d[1] / len * 10);
			if (!window.contains(nearX, nearZ) || !window.contains(farX, farZ)) {
				continue;
			}
			int riseNear = WorldGenUtil.groundY(level, nearX, nearZ) - ground;
			int riseFar = WorldGenUtil.groundY(level, farX, farZ) - ground;
			if (riseNear >= 4 && riseFar >= 8 && riseFar > bestRise) {
				bestRise = riseFar;
				best = d;
			}
		}
		if (best == null) {
			return false;
		}

		boolean cold = WorldGenUtil.isCold(level, start);
		boolean stream = !cold && random.nextFloat() < 0.35f;
		double yaw = Math.atan2(best[1], best[0]) + (random.nextDouble() - 0.5) * 0.5;
		double mouthWidth = 7 + random.nextDouble() * 5;
		double endWidth = 3.5 + random.nextDouble() * 1.5;
		double mouthHeight = Mth.clamp(bestRise * 0.9, 7, 14) + random.nextDouble() * 2;
		double endHeight = 4 + random.nextDouble() * 2;
		double slope = stream ? -(0.12 + random.nextDouble() * 0.1) : 0.25 + random.nextDouble() * 0.35;
		int length = minLength + random.nextInt(Math.max(1, maxLength - minLength + 1));

		WorldGenUtil.Wobble widthNoise = new WorldGenUtil.Wobble(random, 3, 0.35);
		WorldGenUtil.Wobble heightNoise = new WorldGenUtil.Wobble(random, 3, 0.3);
		WorldGenUtil.Wobble wander = new WorldGenUtil.Wobble(random, 2, 0.12);

		// Trace the tunnel's centre line, stopping before it would leave the writable window.
		List<Slice> slices = new ArrayList<>();
		double x = x0 + 0.5 - Math.cos(yaw) * 1.5;
		double z = z0 + 0.5 - Math.sin(yaw) * 1.5;
		for (double t = -1.5; t <= length; t += STEP) {
			double f = Mth.clamp(t / length, 0, 1);
			double angle = yaw + 0.5 * wander.at(t) * f;
			double halfWidth = Mth.lerp(f, mouthWidth, endWidth) * (1 + 0.15 * widthNoise.at(t)) * 0.5;
			double height = Mth.lerp(f, mouthHeight, endHeight) * (1 + 0.12 * heightNoise.at(t));
			double floor = ground + 1 - slope * Math.max(t, 0);
			int margin = (int) Math.ceil(halfWidth) + 2;
			if (!window.containsRadius(Mth.floor(x), Mth.floor(z), margin)) {
				break;
			}
			slices.add(new Slice(x, z, t, floor, halfWidth, height, -Math.sin(angle), Math.cos(angle)));
			x += Math.cos(angle) * STEP;
			z += Math.sin(angle) * STEP;
		}
		double reached = slices.isEmpty() ? 0 : slices.get(slices.size() - 1).t();
		if (reached < minLength * 0.75) {
			return false;
		}

		Long2DoubleMap carved = new Long2DoubleOpenHashMap();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minY = level.getMinY() + 6;
		for (Slice slice : slices) {
			double depthFraction = Mth.clamp(slice.t() / reached, 0, 1);
			for (double u = -slice.halfWidth(); u <= slice.halfWidth(); u += 0.5) {
				double archTop = slice.height() * Math.sqrt(Math.max(0, 1 - (u * u) / (slice.halfWidth() * slice.halfWidth())));
				for (double v = 0; v <= archTop; v += 0.5) {
					int bx = Mth.floor(slice.x() + slice.perpX() * u);
					int by = Mth.floor(slice.floor() + v);
					int bz = Mth.floor(slice.z() + slice.perpZ() * u);
					long key = BlockPos.asLong(bx, by, bz);
					if (by < minY || carved.containsKey(key)) {
						continue;
					}
					pos.set(bx, by, bz);
					BlockState state = level.getBlockState(pos);
					if (!state.getFluidState().isEmpty() || state.is(BlockTags.FEATURES_CANNOT_REPLACE) || state.is(Blocks.BEDROCK)) {
						continue;
					}
					if (!state.isAir()) {
						if (slice.t() > 1 && WorldGenUtil.hasLiquidNeighbour(level, pos)) {
							WaterWorks.set(level, pos, Blocks.STONE.defaultBlockState());
							continue;
						}
						WaterWorks.set(level, pos, depthFraction < 0.5 ? WaterWorks.AIR : Blocks.CAVE_AIR.defaultBlockState());
					}
					carved.put(key, depthFraction);
				}
			}
		}
		if (carved.size() < 60) {
			return false;
		}

		decorate(level, random, carved, cold);
		if (stream) {
			stream(level, window, random, slices);
		}
		FeatureStats.placed("cave_mouth");
		return true;
	}

	private static void decorate(WorldGenLevel level, RandomSource random, Long2DoubleMap carved, boolean cold) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos other = new BlockPos.MutableBlockPos();
		for (Long2DoubleMap.Entry entry : carved.long2DoubleEntrySet()) {
			long key = entry.getLongKey();
			double f = entry.getDoubleValue();
			int x = BlockPos.getX(key);
			int y = BlockPos.getY(key);
			int z = BlockPos.getZ(key);
			pos.set(x, y, z);
			if (!level.getBlockState(pos).isAir()) {
				continue;
			}

			// Floor.
			other.set(x, y - 1, z);
			BlockState floor = level.getBlockState(other);
			if (!carved.containsKey(other.asLong()) && WorldGenUtil.isTerrain(floor)) {
				if (f < 0.45 && !cold) {
					int roll = random.nextInt(10);
					if (roll < 4) {
						WaterWorks.set(level, other, Blocks.MOSS_BLOCK.defaultBlockState());
					} else if (roll < 6) {
						WaterWorks.set(level, other, Blocks.COARSE_DIRT.defaultBlockState());
					} else if (roll < 7) {
						WaterWorks.set(level, other, Blocks.GRAVEL.defaultBlockState());
					}
					BlockState ground = level.getBlockState(other);
					if (ground.is(Blocks.MOSS_BLOCK) || ground.is(BlockTags.SUBSTRATE_OVERWORLD)) {
						int plant = random.nextInt(12);
						if (plant < 3) {
							WaterWorks.set(level, pos, Blocks.MOSS_CARPET.defaultBlockState());
						} else if (plant < 4) {
							WaterWorks.set(level, pos, Blocks.FERN.defaultBlockState());
						} else if (plant < 5) {
							WaterWorks.set(level, pos, Blocks.SHORT_GRASS.defaultBlockState());
						}
					}
				} else if (f >= 0.45) {
					int roll = random.nextInt(14);
					if (roll < 2) {
						WaterWorks.set(level, other, Blocks.GRAVEL.defaultBlockState());
					} else if (roll < 3) {
						WaterWorks.set(level, other, Blocks.COBBLESTONE.defaultBlockState());
					} else if (roll < 4 && f > 0.6) {
						dripstone(level, pos, Direction.UP, 1 + random.nextInt(2), carved);
					}
				}
				continue;
			}

			// Ceiling.
			other.set(x, y + 1, z);
			BlockState ceiling = level.getBlockState(other);
			if (!carved.containsKey(other.asLong()) && WorldGenUtil.isTerrain(ceiling)) {
				if (f < 0.5 && !cold) {
					int roll = random.nextInt(14);
					if (roll < 2) {
						caveVines(level, random, pos, 1 + random.nextInt(6), carved);
					} else if (roll < 3) {
						WaterWorks.set(level, pos, Blocks.HANGING_ROOTS.defaultBlockState());
					}
				} else if (f >= 0.5) {
					int roll = random.nextInt(14);
					if (roll < 1) {
						dripstone(level, pos, Direction.DOWN, 1 + random.nextInt(3), carved);
					} else if (roll < 3) {
						WaterWorks.set(level, pos, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true));
					}
				}
				continue;
			}

			// Walls.
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				other.set(x + direction.getStepX(), y, z + direction.getStepZ());
				if (carved.containsKey(other.asLong()) || !WorldGenUtil.isTerrain(level.getBlockState(other))) {
					continue;
				}
				if (f < 0.3 && !cold && random.nextInt(6) == 0) {
					hangingVine(level, random, pos, direction, 1 + random.nextInt(5), carved);
				} else if (f > 0.4 && random.nextInt(16) == 0) {
					WaterWorks.set(level, pos, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(direction), true));
				}
				break;
			}
		}
	}

	private static void caveVines(WorldGenLevel level, RandomSource random, BlockPos top, int length, Long2DoubleMap carved) {
		BlockPos.MutableBlockPos pos = top.mutable();
		int placed = 0;
		for (int i = 0; i < length; i++) {
			if (!carved.containsKey(pos.asLong()) || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isAir()) {
				break;
			}
			placed++;
			pos.move(Direction.DOWN);
		}
		pos.set(top);
		for (int i = 0; i < placed; i++) {
			boolean last = i == placed - 1;
			BlockState vine = (last ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState()
					.setValue(BlockStateProperties.BERRIES, random.nextInt(5) == 0);
			if (last) {
				vine = vine.setValue(BlockStateProperties.AGE_25, 25);
			}
			WaterWorks.set(level, pos, vine);
			pos.move(Direction.DOWN);
		}
	}

	/** Vines growing on a wall face, hanging down a few blocks. */
	static void hangingVine(WorldGenLevel level, RandomSource random, BlockPos start, Direction towardWall, int length,
			Long2DoubleMap carved) {
		BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(towardWall), true);
		BlockPos.MutableBlockPos pos = start.mutable();
		for (int i = 0; i < length; i++) {
			if ((carved != null && !carved.containsKey(pos.asLong())) || !level.getBlockState(pos).isAir()) {
				break;
			}
			WaterWorks.set(level, pos, vine);
			pos.move(Direction.DOWN);
		}
	}

	/** A stalactite (growing DOWN from the ceiling) or stalagmite (growing UP from the floor). */
	private static void dripstone(WorldGenLevel level, BlockPos start, Direction growth, int length, Long2DoubleMap carved) {
		BlockPos.MutableBlockPos pos = start.mutable();
		int room = 0;
		for (int i = 0; i < length; i++) {
			if (!carved.containsKey(pos.asLong()) || !level.getBlockState(pos).isAir()) {
				break;
			}
			room++;
			pos.move(growth);
		}
		if (room == 0) {
			return;
		}
		pos.set(start);
		for (int i = 0; i < room; i++) {
			int fromTip = room - 1 - i;
			String thickness = fromTip == 0 ? "tip" : fromTip == 1 ? "frustum" : i == 0 ? "base" : "middle";
			BlockState dripstone = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(BlockStateProperties.VERTICAL_DIRECTION, growth);
			WaterWorks.set(level, pos, WorldGenUtil.withProperty(dripstone, "thickness", thickness));
			pos.move(growth);
		}
	}

	/** A spring at the back of the cave whose stream steps down the floor and out of the mouth. */
	private static void stream(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, List<Slice> slices) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Slice deepest = slices.get(slices.size() - 1);
		int lastX = Integer.MIN_VALUE;
		int lastZ = Integer.MIN_VALUE;
		for (int i = slices.size() - 1; i >= 0; i--) {
			Slice slice = slices.get(i);
			if (slice.t() < 0) {
				break;
			}
			int x = Mth.floor(slice.x());
			int z = Mth.floor(slice.z());
			if (x == lastX && z == lastZ) {
				continue;
			}
			lastX = x;
			lastZ = z;
			int y = Mth.floor(slice.floor()) - 1;
			pos.set(x, y, z);
			if (!WorldGenUtil.isTerrain(level.getBlockState(pos))) {
				continue;
			}
			WaterWorks.set(level, pos, WaterWorks.WATER);
			WaterWorks.set(level, pos.move(Direction.DOWN), Blocks.GRAVEL.defaultBlockState());
			pos.move(Direction.UP);
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos side = pos.relative(direction);
				if (level.getBlockState(side).isAir() && level.getBlockState(side.below()).isAir()) {
					WaterWorks.set(level, side.below(), Blocks.STONE.defaultBlockState());
				}
			}
			WaterWorks.scheduleFluidTick(level, pos);
		}
		// A spring pool at the back of the cave.
		int springX = Mth.floor(deepest.x());
		int springZ = Mth.floor(deepest.z());
		WaterWorks.pool(level, window, random, springX, springZ, 1.6, 1.6, Mth.floor(deepest.floor()) - 1, 2, false);
		// A pool outside the mouth to catch the stream.
		Slice mouth = slices.get(0);
		int poolX = Mth.floor(mouth.x());
		int poolZ = Mth.floor(mouth.z());
		int surface = WorldGenUtil.surfaceY(level, poolX, poolZ);
		if (level.getFluidState(pos.set(poolX, surface, poolZ)).isEmpty() && window.containsRadius(poolX, poolZ, 4)) {
			WaterWorks.pool(level, window, random, poolX, poolZ, 2.2, 2.0, Math.min(surface, Mth.floor(mouth.floor()) - 2), 2, false);
		}
	}
}
