package com.naturesmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.naturesmod.block.GiantClamBlock;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.block.SeaPlantBlock;
import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A patch of sea floor life: anemones, sea fans, starfish, urchins, giant clams and shells on the
 * reefs and sea beds, shells washed up on the beaches, and glowmoss and tube worms in the Abyss.
 */
public record SeaGardenFeature(Style style) implements Feature {
	public static final MapCodec<SeaGardenFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Style.CODEC.fieldOf("style").forGetter(SeaGardenFeature::style)
	).apply(instance, SeaGardenFeature::new));

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 1);
		Block[] palette = style.palette();
		int count = style.minCount + random.nextInt(style.maxCount - style.minCount + 1);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int placed = 0;
		for (int i = 0; i < count; i++) {
			int x = origin.getX() + random.nextInt(style.radius * 2 + 1) - style.radius;
			int z = origin.getZ() + random.nextInt(style.radius * 2 + 1) - style.radius;
			if (!window.contains(x, z)) {
				continue;
			}
			BlockPos spot = ground(level, pos, x, origin.getY() + 4, z, style == Style.BEACH);
			if (spot == null) {
				continue;
			}
			Block block = palette[random.nextInt(palette.length)];
			BlockState state = block.defaultBlockState().setValue(SeaPlantBlock.WATERLOGGED, WorldGenUtil.isWater(level.getBlockState(spot)));
			if (block instanceof GiantClamBlock) {
				state = state.setValue(GiantClamBlock.HAS_PEARL, random.nextInt(3) == 0);
			}
			level.setBlock(spot, state, 2);
			placed++;
		}
		if (placed > 0) {
			FeatureStats.placed("sea_garden_" + style.getSerializedName(), origin);
		}
		return placed > 0;
	}

	/**
	 * The first free spot right on top of solid ground below a position: in the water, or on a
	 * beach in the open air.
	 */
	private static BlockPos ground(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, boolean dry) {
		pos.set(x, y, z);
		for (int i = 0; i < 14 && pos.getY() > level.getMinY() + 1; i++) {
			BlockState here = level.getBlockState(pos);
			BlockPos below = pos.below();
			BlockState under = level.getBlockState(below);
			boolean free = dry ? here.isAir() : WorldGenUtil.isWater(here) && here.getFluidState().isSource();
			if (free && !under.isAir() && under.getFluidState().isEmpty() && under.isFaceSturdy(level, below, Direction.UP)) {
				return pos.immutable();
			}
			pos.move(Direction.DOWN);
		}
		return null;
	}

	public enum Style implements StringRepresentable {
		REEF("reef", 8, 16, 6),
		TEMPERATE("temperate", 3, 7, 5),
		COLD("cold", 3, 6, 5),
		BEACH("beach", 2, 5, 4),
		ABYSS_REEF("abyss_reef", 6, 12, 5),
		ABYSS_VENTS("abyss_vents", 6, 14, 4),
		ABYSS_FLOOR("abyss_floor", 2, 5, 5);

		public static final Codec<Style> CODEC = StringRepresentable.fromEnum(Style::values);
		private final String name;
		final int minCount;
		final int maxCount;
		final int radius;

		Style(String name, int minCount, int maxCount, int radius) {
			this.name = name;
			this.minCount = minCount;
			this.maxCount = maxCount;
			this.radius = radius;
		}

		/** What grows in this kind of place; repeated entries are more common. */
		Block[] palette() {
			return switch (this) {
				case REEF -> new Block[] {ModBlocks.RED_SEA_ANEMONE, ModBlocks.RED_SEA_ANEMONE, ModBlocks.PURPLE_SEA_ANEMONE,
						ModBlocks.PURPLE_SEA_ANEMONE, ModBlocks.GREEN_SEA_ANEMONE, ModBlocks.PURPLE_SEA_FAN, ModBlocks.PURPLE_SEA_FAN,
						ModBlocks.ORANGE_SEA_FAN, ModBlocks.ORANGE_SEA_FAN, ModBlocks.ORANGE_STARFISH, ModBlocks.BLUE_STARFISH,
						ModBlocks.SEA_URCHIN, ModBlocks.GIANT_CLAM, ModBlocks.SEASHELLS};
				case TEMPERATE -> new Block[] {ModBlocks.GREEN_SEA_ANEMONE, ModBlocks.GREEN_SEA_ANEMONE, ModBlocks.RED_SEA_ANEMONE,
						ModBlocks.ORANGE_STARFISH, ModBlocks.ORANGE_STARFISH, ModBlocks.ORANGE_STARFISH, ModBlocks.SEA_URCHIN,
						ModBlocks.SEA_URCHIN, ModBlocks.SEASHELLS, ModBlocks.SEASHELLS};
				case COLD -> new Block[] {ModBlocks.GREEN_SEA_ANEMONE, ModBlocks.GREEN_SEA_ANEMONE, ModBlocks.BLUE_STARFISH,
						ModBlocks.BLUE_STARFISH, ModBlocks.SEA_URCHIN, ModBlocks.SEA_URCHIN, ModBlocks.SEA_URCHIN};
				case BEACH -> new Block[] {ModBlocks.SEASHELLS, ModBlocks.SEASHELLS, ModBlocks.SEASHELLS, ModBlocks.ORANGE_STARFISH};
				case ABYSS_REEF -> new Block[] {ModBlocks.ABYSSAL_GLOWMOSS, ModBlocks.ABYSSAL_GLOWMOSS, ModBlocks.ABYSSAL_GLOWMOSS,
						ModBlocks.PURPLE_SEA_ANEMONE, ModBlocks.PURPLE_SEA_ANEMONE, ModBlocks.GREEN_SEA_ANEMONE, ModBlocks.GIANT_CLAM};
				case ABYSS_VENTS -> new Block[] {ModBlocks.TUBE_WORMS, ModBlocks.TUBE_WORMS, ModBlocks.TUBE_WORMS, ModBlocks.TUBE_WORMS,
						ModBlocks.SEA_URCHIN};
				case ABYSS_FLOOR -> new Block[] {ModBlocks.BLUE_STARFISH, ModBlocks.BLUE_STARFISH, ModBlocks.SEA_URCHIN,
						ModBlocks.ABYSSAL_GLOWMOSS, ModBlocks.ABYSSAL_GLOWMOSS, ModBlocks.SEASHELLS};
			};
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}
}
