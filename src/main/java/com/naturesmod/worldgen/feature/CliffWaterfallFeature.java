package com.naturesmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.WaterWorks;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A stream that runs to the edge of a cliff and pours over it as a waterfall into a plunge pool.
 * In freezing places the whole fall is frozen solid instead.
 */
public record CliffWaterfallFeature(int minDrop, int maxChannel) implements Feature {
	public static final MapCodec<CliffWaterfallFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(3, 128).optionalFieldOf("min_drop", 7).forGetter(CliffWaterfallFeature::minDrop),
			Codec.intRange(1, 10).optionalFieldOf("max_channel", 5).forGetter(CliffWaterfallFeature::maxChannel)
	).apply(instance, CliffWaterfallFeature::new));

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 4);
		int x0 = origin.getX();
		int z0 = origin.getZ();
		int top = WorldGenUtil.groundY(level, x0, z0);
		if (top < level.getSeaLevel() + 4) {
			return false;
		}
		BlockPos lip = new BlockPos(x0, top, z0);
		if (!WorldGenUtil.isTerrain(level.getBlockState(lip)) || !level.getFluidState(lip.above()).isEmpty()
				|| !WorldGenUtil.isStructureFree(level, lip)) {
			return false;
		}

		Direction out = null;
		int bestDrop = 0;
		for (Direction direction : Direction.Plane.HORIZONTAL.shuffledCopy(random)) {
			int nx = x0 + direction.getStepX();
			int nz = z0 + direction.getStepZ();
			int drop = top - landingY(level, nx, top, nz);
			if (drop >= minDrop && drop > bestDrop) {
				bestDrop = drop;
				out = direction;
			}
		}
		if (out == null) {
			return false;
		}

		Direction side = out.getClockWise();
		int width = 1;
		BlockPos lip2 = lip.relative(side);
		if (random.nextInt(3) == 0 && top - landingY(level, lip2.getX() + out.getStepX(), top, lip2.getZ() + out.getStepZ()) >= minDrop
				&& WorldGenUtil.groundY(level, lip2.getX(), lip2.getZ()) == top) {
			width = 2;
		}

		boolean frozen = WorldGenUtil.isCold(level, lip);
		boolean warm = WorldGenUtil.isWarm(level, lip);
		int channelLength = 2 + random.nextInt(Math.max(1, maxChannel - 1));

		for (int lane = 0; lane < width; lane++) {
			BlockPos laneLip = lip.relative(side, lane);
			carveChannel(level, window, random, laneLip, out, side, top, channelLength, frozen, warm);
			pourOver(level, window, random, laneLip, out, side, top, frozen, warm);
		}
		FeatureStats.placed(frozen ? "icefall" : "cliff_waterfall");
		return true;
	}

	/** Y of the first block that would stop water falling down the column at (x, z), starting at y. */
	static int landingY(WorldGenLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
		int minY = level.getMinY();
		while (y > minY) {
			BlockState state = level.getBlockState(pos.setY(y));
			if (!state.isAir() && !WorldGenUtil.isClearable(state) || !state.getFluidState().isEmpty()) {
				return y;
			}
			y--;
		}
		return minY;
	}

	/** Cuts a little stream bed leading back from the lip of the cliff. */
	private static void carveChannel(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos lip,
			Direction out, Direction side, int top, int length, boolean frozen, boolean warm) {
		BlockState water = frozen ? Blocks.ICE.defaultBlockState() : WaterWorks.WATER;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < length; i++) {
			int x = lip.getX() - out.getStepX() * i;
			int z = lip.getZ() - out.getStepZ() * i;
			if (!window.containsRadius(x, z, 1)) {
				break;
			}
			int ground = WorldGenUtil.groundY(level, x, z);
			if (ground < top - 1 || ground > top + 4) {
				break;
			}
			// Cut a gully through any ground rising above the stream.
			for (int y = top + 1; y <= ground + 2; y++) {
				BlockState state = level.getBlockState(pos.set(x, y, z));
				if (WorldGenUtil.isTerrain(state) || WorldGenUtil.isClearable(state)) {
					WaterWorks.set(level, pos, WaterWorks.AIR);
				}
			}
			BlockState bed = level.getBlockState(pos.set(x, top - 1, z));
			if (!WaterWorks.holdsWater(bed) || random.nextInt(2) == 0) {
				WaterWorks.set(level, pos, WaterWorks.bed(random, warm));
			}
			WaterWorks.set(level, pos.set(x, top, z), water);
			for (int sign = -1; sign <= 1; sign += 2) {
				pos.set(x + side.getStepX() * sign, top, z + side.getStepZ() * sign);
				BlockState bank = level.getBlockState(pos);
				if (!WaterWorks.holdsWater(bank) && bank.getFluidState().isEmpty()) {
					WaterWorks.set(level, pos, WaterWorks.rim(random));
				}
			}
		}
		if (!frozen) {
			WaterWorks.scheduleFluidTick(level, lip);
		}
	}

	/** Places the falling curtain of water (or ice) and the plunge pool at its foot. */
	private static void pourOver(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos lip,
			Direction out, Direction side, int top, boolean frozen, boolean warm) {
		BlockPos curtainTop = lip.relative(out);
		if (!window.contains(curtainTop)) {
			return;
		}
		int landing = landingY(level, curtainTop.getX(), top, curtainTop.getZ());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean intoWater = !level.getFluidState(pos.set(curtainTop.getX(), landing, curtainTop.getZ())).isEmpty();

		for (int y = top; y > landing; y--) {
			pos.set(curtainTop.getX(), y, curtainTop.getZ());
			BlockState curtain;
			if (frozen) {
				int roll = random.nextInt(10);
				curtain = roll == 0 ? Blocks.BLUE_ICE.defaultBlockState()
						: roll < 6 ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.ICE.defaultBlockState();
			} else {
				curtain = y == top ? WaterWorks.flowing(7, false) : WaterWorks.flowing(8, true);
			}
			WaterWorks.set(level, pos, curtain);

			// The rock behind and beside a waterfall is wet and mossy.
			mossify(level, random, pos.set(lip.getX(), y, lip.getZ()), 0.45f);
			mossify(level, random, pos.set(curtainTop.getX() + side.getStepX(), y, curtainTop.getZ() + side.getStepZ()), 0.25f);
			mossify(level, random, pos.set(curtainTop.getX() - side.getStepX(), y, curtainTop.getZ() - side.getStepZ()), 0.25f);
		}
		if (!frozen) {
			WaterWorks.scheduleFluidTick(level, curtainTop.atY(top));
		}

		if (intoWater) {
			return;
		}
		int fall = top - landing;
		double radius = fall >= 20 ? 3.2 : fall >= 12 ? 2.6 : 2.0;
		if (frozen) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					int x = curtainTop.getX() + dx;
					int z = curtainTop.getZ() + dz;
					if (dx * dx + dz * dz > 5 || !window.contains(x, z)) {
						continue;
					}
					int ground = WorldGenUtil.groundY(level, x, z);
					if (Math.abs(ground - landing) <= 1) {
						WaterWorks.set(level, pos.set(x, ground, z), Blocks.PACKED_ICE.defaultBlockState());
					}
				}
			}
			return;
		}
		WaterWorks.pool(level, window, random, curtainTop.getX(), curtainTop.getZ(), radius, radius * (0.8 + random.nextDouble() * 0.4),
				landing, fall >= 12 ? 4 : 3, warm);
	}

	private static void mossify(WorldGenLevel level, RandomSource random, BlockPos pos, float chance) {
		if (random.nextFloat() >= chance) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.COBBLESTONE)) {
			WaterWorks.set(level, pos, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
		}
	}
}
