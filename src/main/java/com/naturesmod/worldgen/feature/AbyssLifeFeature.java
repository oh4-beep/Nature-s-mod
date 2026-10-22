package com.naturesmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.naturesmod.NaturesMod;
import com.naturesmod.block.GiantClamBlock;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Fills the Abyss: meadows of sea grass, glowmoss, anemones and coral over the sea floor; rocks,
 * balls of coral, whale skeletons and lost cargo drifting glowing through the black water; and the
 * litter of a drowned people on the floor: broken columns, amphorae, anchors and lamp posts.
 */
public record AbyssLifeFeature(Style style) implements Feature {
	public static final MapCodec<AbyssLifeFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Style.CODEC.fieldOf("style").forGetter(AbyssLifeFeature::style)
	).apply(instance, AbyssLifeFeature::new));

	private static final Block[] CORALS = {Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL, Blocks.FIRE_CORAL, Blocks.HORN_CORAL};
	private static final Block[] CORAL_FANS = {Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN, Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN,
			Blocks.HORN_CORAL_FAN};
	private static final Block[] CORAL_WALL_FANS = {Blocks.TUBE_CORAL_WALL_FAN, Blocks.BRAIN_CORAL_WALL_FAN, Blocks.BUBBLE_CORAL_WALL_FAN,
			Blocks.FIRE_CORAL_WALL_FAN, Blocks.HORN_CORAL_WALL_FAN};
	private static final Block[] CORAL_BLOCKS = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK,
			Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK};
	private static final Block[] DEAD_CORALS = {Blocks.DEAD_TUBE_CORAL, Blocks.DEAD_BRAIN_CORAL, Blocks.DEAD_BUBBLE_CORAL,
			Blocks.DEAD_FIRE_CORAL, Blocks.DEAD_HORN_CORAL};
	private static final Block[] DEAD_CORAL_FANS = {Blocks.DEAD_TUBE_CORAL_FAN, Blocks.DEAD_BRAIN_CORAL_FAN, Blocks.DEAD_BUBBLE_CORAL_FAN,
			Blocks.DEAD_FIRE_CORAL_FAN, Blocks.DEAD_HORN_CORAL_FAN};
	private static final Block[] LIGHTS = {Blocks.SEA_LANTERN, Blocks.PEARLESCENT_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.OCHRE_FROGLIGHT,
			Blocks.SHROOMLIGHT};

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (origin.getY() >= level.getSeaLevel() - 3) {
			return false;
		}
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 1);
		boolean placed = switch (style) {
			case MEADOW -> meadow(level, window, random, origin);
			case DRIFT -> drift(level, window, random, origin);
			case RELIC -> relic(level, window, random, origin);
		};
		if (placed) {
			FeatureStats.placed("abyss_" + style.getSerializedName(), origin);
		}
		return placed;
	}

	// --- helpers -------------------------------------------------------------------------

	/** Sets a block, keeping it waterlogged when it replaces water. */
	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
			state = state.setValue(BlockStateProperties.WATERLOGGED, WorldGenUtil.isWater(level.getBlockState(pos)));
		}
		level.setBlock(pos, state, 2);
	}

	/** Still, open water: a water source block. */
	private static boolean source(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(Blocks.WATER) && state.getFluidState().isSource();
	}

	/** Ground a plant can stand on. */
	private static boolean sturdy(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getFluidState().isEmpty() && !state.is(Blocks.MAGMA_BLOCK) && state.isFaceSturdy(level, pos, Direction.UP);
	}

	private static boolean solid(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && state.getFluidState().isEmpty();
	}

	private static Block pick(RandomSource random, Block... blocks) {
		return blocks[random.nextInt(blocks.length)];
	}

	private static ResourceKey<LootTable> loot(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, NaturesMod.id("chests/abyss/" + name));
	}

	private static void container(WorldGenLevel level, BlockPos pos, BlockState state, RandomSource random, String table) {
		set(level, pos, state);
		RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot(table));
	}

	/** Y of the top of the ground in a column (the motion-blocking floor, which may be a ledge). */
	private static int ground(WorldGenLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
	}

	/** True when the water around a point is open for some distance in every direction. */
	private static boolean openWater(WorldGenLevel level, BlockPos center, int reach) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int d = (int) Math.round(reach * 0.7);
		int[][] probes = {{0, 0, 0}, {reach, 0, 0}, {-reach, 0, 0}, {0, reach, 0}, {0, -reach, 0}, {0, 0, reach}, {0, 0, -reach},
				{d, d, d}, {-d, d, -d}, {d, -d, -d}, {-d, -d, d}, {0, -reach * 2, 0}};
		for (int[] probe : probes) {
			pos.setWithOffset(center, probe[0], probe[1], probe[2]);
			if (!WorldGenUtil.isWater(level.getBlockState(pos))) {
				return false;
			}
		}
		return true;
	}

	// --- meadows -------------------------------------------------------------------------

	/** What covers the floor in each part of the Abyss; repeated entries are more common. */
	private enum Plant {
		SEAGRASS, TALL_SEAGRASS, KELP, PICKLES, GLOWMOSS, RED_ANEMONE, PURPLE_ANEMONE, GREEN_ANEMONE, PURPLE_FAN, ORANGE_FAN, TUBE_WORMS,
		BLUE_STARFISH, ORANGE_STARFISH, SHELLS, URCHIN, CLAM, CORAL, CORAL_FAN, DEAD_CORAL, DEAD_CORAL_FAN, BONE, SKULL, CRYSTAL, SEA_PEN
	}

	private enum Meadow {
		PLAINS(Plant.SEAGRASS, Plant.SEAGRASS, Plant.SEAGRASS, Plant.TALL_SEAGRASS, Plant.TALL_SEAGRASS, Plant.GLOWMOSS, Plant.GLOWMOSS,
				Plant.GLOWMOSS, Plant.PURPLE_ANEMONE, Plant.GREEN_ANEMONE, Plant.TUBE_WORMS, Plant.TUBE_WORMS, Plant.BLUE_STARFISH,
				Plant.BLUE_STARFISH, Plant.SHELLS, Plant.URCHIN, Plant.URCHIN, Plant.PICKLES, Plant.PICKLES, Plant.SEA_PEN, Plant.DEAD_CORAL_FAN,
				Plant.CRYSTAL),
		REEF(Plant.CORAL, Plant.CORAL, Plant.CORAL, Plant.CORAL_FAN, Plant.CORAL_FAN, Plant.CORAL_FAN, Plant.GLOWMOSS, Plant.GLOWMOSS,
				Plant.GLOWMOSS, Plant.PICKLES, Plant.PICKLES, Plant.PICKLES, Plant.PURPLE_ANEMONE, Plant.PURPLE_ANEMONE, Plant.RED_ANEMONE,
				Plant.RED_ANEMONE, Plant.GREEN_ANEMONE, Plant.PURPLE_FAN, Plant.PURPLE_FAN, Plant.ORANGE_FAN, Plant.ORANGE_FAN,
				Plant.ORANGE_STARFISH, Plant.CLAM, Plant.SEAGRASS, Plant.SEAGRASS, Plant.TALL_SEAGRASS),
		VENTS(Plant.TUBE_WORMS, Plant.TUBE_WORMS, Plant.TUBE_WORMS, Plant.TUBE_WORMS, Plant.TUBE_WORMS, Plant.TUBE_WORMS, Plant.URCHIN,
				Plant.URCHIN, Plant.GLOWMOSS, Plant.CRYSTAL, Plant.CRYSTAL, Plant.DEAD_CORAL, Plant.DEAD_CORAL, Plant.SHELLS),
		BONES(Plant.BONE, Plant.BONE, Plant.BONE, Plant.SKULL, Plant.DEAD_CORAL, Plant.DEAD_CORAL, Plant.DEAD_CORAL, Plant.DEAD_CORAL_FAN,
				Plant.DEAD_CORAL_FAN, Plant.DEAD_CORAL_FAN, Plant.GLOWMOSS, Plant.GLOWMOSS, Plant.GLOWMOSS, Plant.BLUE_STARFISH, Plant.URCHIN,
				Plant.SEAGRASS, Plant.SEAGRASS, Plant.PICKLES),
		TWILIGHT(Plant.SEAGRASS, Plant.SEAGRASS, Plant.SEAGRASS, Plant.SEAGRASS, Plant.SEAGRASS, Plant.TALL_SEAGRASS, Plant.TALL_SEAGRASS,
				Plant.TALL_SEAGRASS, Plant.TALL_SEAGRASS, Plant.KELP, Plant.KELP, Plant.KELP, Plant.PICKLES, Plant.PICKLES, Plant.GREEN_ANEMONE,
				Plant.GREEN_ANEMONE, Plant.ORANGE_STARFISH, Plant.BLUE_STARFISH, Plant.URCHIN, Plant.URCHIN, Plant.CORAL_FAN, Plant.ORANGE_FAN,
				Plant.GLOWMOSS),
		MIDNIGHT(Plant.GLOWMOSS, Plant.GLOWMOSS, Plant.GLOWMOSS, Plant.GLOWMOSS, Plant.TUBE_WORMS, Plant.TUBE_WORMS, Plant.PURPLE_ANEMONE,
				Plant.PURPLE_ANEMONE, Plant.PICKLES, Plant.PICKLES, Plant.CRYSTAL, Plant.SEA_PEN, Plant.DEAD_CORAL_FAN, Plant.DEAD_CORAL_FAN);

		final Plant[] plants;

		Meadow(Plant... plants) {
			this.plants = plants;
		}

		static Meadow at(WorldGenLevel level, BlockPos pos) {
			String biome = level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
			return switch (biome) {
				case "glowing_reef" -> REEF;
				case "hydrothermal_vents" -> VENTS;
				case "bone_fields" -> BONES;
				case "twilight_waters" -> TWILIGHT;
				case "midnight_zone" -> MIDNIGHT;
				default -> PLAINS;
			};
		}
	}

	/**
	 * Covers the floor of a chunk with patches of life, each biome with its own mix. The patches
	 * are thick in the middle and thin out at their edges, with bare ground between them.
	 */
	private boolean meadow(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int baseX = origin.getX() & ~15;
		int baseZ = origin.getZ() & ~15;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int placed = 0;
		int patches = 3 + random.nextInt(4);
		for (int p = 0; p < patches; p++) {
			int cx = baseX + random.nextInt(16);
			int cz = baseZ + random.nextInt(16);
			int radius = 2 + random.nextInt(4);
			float density = 0.45F + random.nextFloat() * 0.4F;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					int d2 = dx * dx + dz * dz;
					if (d2 > radius * radius + 1 || random.nextFloat() > density * (1.0F - 0.5F * d2 / (radius * radius + 1.0F))) {
						continue;
					}
					int x = cx + dx;
					int z = cz + dz;
					if (!window.contains(x, z)) {
						continue;
					}
					pos.set(x, ground(level, x, z) + 1, z);
					if (pos.getY() >= level.getSeaLevel() - 3 || !source(level, pos) || !sturdy(level, pos.below())) {
						continue;
					}
					Meadow meadow = Meadow.at(level, pos);
					if (grow(level, pos, random, meadow.plants[random.nextInt(meadow.plants.length)])) {
						placed++;
					}
				}
			}
		}
		return placed > 0;
	}

	/** Grows one plant (or puts one thing) on the floor at a spot of open water. */
	private static boolean grow(WorldGenLevel level, BlockPos.MutableBlockPos pos, RandomSource random, Plant plant) {
		BlockPos at = pos.immutable();
		switch (plant) {
			case SEAGRASS -> set(level, at, Blocks.SEAGRASS.defaultBlockState());
			case TALL_SEAGRASS -> {
				if (!source(level, at.above())) {
					set(level, at, Blocks.SEAGRASS.defaultBlockState());
				} else {
					BlockState lower = Blocks.TALL_SEAGRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
					level.setBlock(at, lower, 2);
					level.setBlock(at.above(), lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), 2);
				}
			}
			case KELP -> {
				int height = 2 + random.nextInt(9);
				int grown = 0;
				while (grown < height && source(level, at.above(grown)) && source(level, at.above(grown + 1))) {
					grown++;
				}
				if (grown == 0) {
					return false;
				}
				for (int i = 0; i < grown - 1; i++) {
					level.setBlock(at.above(i), Blocks.KELP_PLANT.defaultBlockState(), 2);
				}
				level.setBlock(at.above(grown - 1), Blocks.KELP.defaultBlockState().setValue(BlockStateProperties.AGE_25, 20 + random.nextInt(5)), 2);
			}
			case PICKLES -> set(level, at, Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 1 + random.nextInt(4)));
			case GLOWMOSS -> set(level, at, ModBlocks.ABYSSAL_GLOWMOSS.defaultBlockState());
			case RED_ANEMONE -> set(level, at, ModBlocks.RED_SEA_ANEMONE.defaultBlockState());
			case PURPLE_ANEMONE -> set(level, at, ModBlocks.PURPLE_SEA_ANEMONE.defaultBlockState());
			case GREEN_ANEMONE -> set(level, at, ModBlocks.GREEN_SEA_ANEMONE.defaultBlockState());
			case PURPLE_FAN -> set(level, at, ModBlocks.PURPLE_SEA_FAN.defaultBlockState());
			case ORANGE_FAN -> set(level, at, ModBlocks.ORANGE_SEA_FAN.defaultBlockState());
			case TUBE_WORMS -> set(level, at, ModBlocks.TUBE_WORMS.defaultBlockState());
			case BLUE_STARFISH -> set(level, at, ModBlocks.BLUE_STARFISH.defaultBlockState());
			case ORANGE_STARFISH -> set(level, at, ModBlocks.ORANGE_STARFISH.defaultBlockState());
			case SHELLS -> set(level, at, ModBlocks.SEASHELLS.defaultBlockState());
			case URCHIN -> set(level, at, ModBlocks.SEA_URCHIN.defaultBlockState());
			case CLAM -> set(level, at, ModBlocks.GIANT_CLAM.defaultBlockState().setValue(GiantClamBlock.HAS_PEARL, random.nextInt(4) == 0));
			case CORAL -> set(level, at, pick(random, CORALS).defaultBlockState());
			case CORAL_FAN -> set(level, at, pick(random, CORAL_FANS).defaultBlockState());
			case DEAD_CORAL -> set(level, at, pick(random, DEAD_CORALS).defaultBlockState());
			case DEAD_CORAL_FAN -> set(level, at, pick(random, DEAD_CORAL_FANS).defaultBlockState());
			case BONE -> {
				Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
				BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
				set(level, at, bone);
				BlockPos next = at.relative(axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH);
				if (random.nextBoolean() && source(level, next) && sturdy(level, next.below())) {
					set(level, next, bone);
				}
			}
			case SKULL -> set(level, at, (random.nextInt(10) == 0 ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL).defaultBlockState()
					.setValue(SkullBlock.ROTATION, random.nextInt(16)));
			case CRYSTAL -> set(level, at, (random.nextBoolean() ? Blocks.AMETHYST_CLUSTER : Blocks.LARGE_AMETHYST_BUD).defaultBlockState()
					.setValue(BlockStateProperties.FACING, Direction.UP));
			case SEA_PEN -> set(level, at, Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		}
		return true;
	}

	// --- things drifting in the water ----------------------------------------------------

	/**
	 * Something drifting in the open water between the floor and the surface: a floating rock
	 * grown over with glowing life, a ball of living coral around a glowing heart, the skeleton of
	 * a whale still sinking to the bottom, or lost cargo hung with lanterns.
	 */
	private boolean drift(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 13) || !openWater(level, origin, 9)) {
			return false;
		}
		int roll = random.nextInt(20);
		if (roll < 9) {
			driftRock(level, random, origin);
		} else if (roll < 14) {
			coralBall(level, random, origin);
		} else if (roll < 17) {
			sinkingWhale(level, random, origin);
		} else {
			lostCargo(level, random, origin);
		}
		return true;
	}

	private static void driftRock(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int rx = 2 + random.nextInt(3);
		int rz = 2 + random.nextInt(3);
		int up = 1 + random.nextInt(2);
		int down = up + 1 + random.nextInt(3);
		Block[][] kinds = {{Blocks.TUFF, Blocks.COBBLED_DEEPSLATE}, {Blocks.DEEPSLATE, Blocks.TUFF}, {Blocks.CALCITE, Blocks.TUFF},
				{Blocks.BLACKSTONE, Blocks.BASALT}, {Blocks.PRISMARINE, Blocks.DARK_PRISMARINE}};
		Block[] rock = kinds[random.nextInt(kinds.length)];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		double wobble = random.nextDouble() * 6.28;
		for (int dx = -rx; dx <= rx; dx++) {
			for (int dz = -rz; dz <= rz; dz++) {
				double flat = (double) dx * dx / (rx * rx) + (double) dz * dz / (rz * rz);
				if (flat > 1.0) {
					continue;
				}
				// A flat-topped rock that hangs down to a ragged point beneath, like an iceberg.
				double edge = 1.0 - flat;
				int top = (int) Math.round(up * Math.sqrt(edge) + 0.4 * Math.sin(dx * 1.3 + dz * 0.7 + wobble));
				int bottom = -(int) Math.round(down * edge * edge + random.nextInt(2));
				for (int dy = bottom; dy <= top; dy++) {
					pos.setWithOffset(origin, dx, dy, dz);
					BlockState state = (random.nextInt(4) == 0 ? rock[1] : rock[0]).defaultBlockState();
					if (state.hasProperty(RotatedPillarBlock.AXIS)) {
						state = state.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
					}
					level.setBlock(pos, state, 2);
				}
				// Life on the top, glowing things beneath.
				pos.setWithOffset(origin, dx, top + 1, dz);
				if (random.nextInt(3) != 0 && source(level, pos)) {
					grow(level, pos, random, random.nextInt(3) == 0 ? Plant.PICKLES : random.nextBoolean() ? Plant.GLOWMOSS
							: random.nextBoolean() ? Plant.TUBE_WORMS : Plant.PURPLE_ANEMONE);
				}
			}
		}
		// A lantern or two, hung on chains from the bottom of the rock by divers who passed by.
		int chains = random.nextInt(3);
		for (int c = 0; c < chains; c++) {
			int x = origin.getX() + random.nextInt(3) - 1;
			int z = origin.getZ() + random.nextInt(3) - 1;
			pos.set(x, origin.getY(), z);
			while (solid(level, pos) && pos.getY() > origin.getY() - down - 3) {
				pos.move(Direction.DOWN);
			}
			hang(level, pos, 2 + random.nextInt(6), random);
		}
		if (chains == 0) {
			// Or glowing drips of froglight beneath it.
			pos.set(origin.getX(), origin.getY(), origin.getZ());
			while (solid(level, pos) && pos.getY() > origin.getY() - down - 3) {
				pos.move(Direction.DOWN);
			}
			if (source(level, pos)) {
				level.setBlock(pos, pick(random, LIGHTS).defaultBlockState(), 2);
			}
		}
	}

	/** A chain hanging down from a point, a lantern at its end. */
	private static void hang(WorldGenLevel level, BlockPos.MutableBlockPos pos, int length, RandomSource random) {
		BlockState chain = Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		for (int i = 0; i < length; i++) {
			if (!source(level, pos) || !source(level, pos.below())) {
				return;
			}
			set(level, pos, chain);
			pos.move(Direction.DOWN);
		}
		if (source(level, pos)) {
			Block lantern = random.nextInt(3) == 0 ? Blocks.SOUL_LANTERN : Blocks.LANTERN;
			set(level, pos, lantern.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
		}
	}

	private static void coralBall(WorldGenLevel level, RandomSource random, BlockPos origin) {
		double radius = 1.6 + random.nextDouble() * 1.4;
		int r = (int) Math.ceil(radius);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Block light = pick(random, LIGHTS);
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy * 1.3 + dz * dz);
					if (d > radius) {
						continue;
					}
					pos.setWithOffset(origin, dx, dy, dz);
					boolean core = d < radius * 0.45 || random.nextInt(9) == 0;
					level.setBlock(pos, (core ? light : pick(random, CORAL_BLOCKS)).defaultBlockState(), 2);
				}
			}
		}
		// Fans on its sides, coral and pickles on its top.
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dy = -r - 1; dy <= r + 1; dy++) {
				for (int dz = -r - 1; dz <= r + 1; dz++) {
					pos.setWithOffset(origin, dx, dy, dz);
					if (!source(level, pos) || random.nextInt(3) == 0) {
						continue;
					}
					if (solid(level, pos.below())) {
						BlockPos at = pos.immutable();
						set(level, at, random.nextInt(3) == 0 ? Blocks.SEA_PICKLE.defaultBlockState()
								.setValue(BlockStateProperties.PICKLES, 1 + random.nextInt(4)) : pick(random, random.nextBoolean() ? CORALS : CORAL_FANS)
								.defaultBlockState());
						continue;
					}
					for (Direction side : Direction.Plane.HORIZONTAL) {
						if (solid(level, pos.relative(side.getOpposite()))) {
							set(level, pos.immutable(), pick(random, CORAL_WALL_FANS).defaultBlockState().setValue(BaseCoralWallFanBlock.FACING, side));
							break;
						}
					}
				}
			}
		}
	}

	/** The skeleton of a whale, belly down, on its long way to the floor. */
	private static void sinkingWhale(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int length = 11 + random.nextInt(8);
		boolean alongX = random.nextBoolean();
		Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
		BlockState spine = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
		BlockState rib = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		BlockState across = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.Z : Direction.Axis.X);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int half = length / 2;
		for (int i = -half; i <= half; i++) {
			int dy = (int) Math.round(Math.sin(i * 0.3) * 0.7);
			pos.setWithOffset(origin, alongX ? i : 0, dy, alongX ? 0 : i);
			level.setBlock(pos, spine, 2);
			double along = (i + half) / (double) length;
			if (along > 0.2 && along < 0.7 && Math.floorMod(i, 2) == 0) {
				double size = Math.sin(Math.PI * (along - 0.2) / 0.5);
				int width = 2 + (int) Math.round(size * 3);
				int height = 3 + (int) Math.round(size * 4);
				for (int side = -1; side <= 1; side += 2) {
					for (int k = 0; k <= height; k++) {
						double t = k / (double) height;
						int out = (int) Math.round(width * Math.sin(Math.PI * 0.85 * t) * side);
						pos.setWithOffset(origin, (alongX ? i : out), dy - k, (alongX ? out : i));
						if (WorldGenUtil.isWater(level.getBlockState(pos))) {
							level.setBlock(pos, t < 0.25 || t > 0.8 ? across : rib, 2);
						}
					}
				}
			}
		}
		// The great skull at the front, and a few glowing scavengers' lights on the bones.
		int head = half + 1;
		for (int dx = 0; dx <= 2; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (Math.abs(dy) + Math.abs(dz) == 2 && dx == 2) {
						continue;
					}
					pos.setWithOffset(origin, alongX ? head + dx : dz, dy, alongX ? dz : head + dx);
					level.setBlock(pos, Blocks.BONE_BLOCK.defaultBlockState(), 2);
				}
			}
		}
		for (int i = 0; i < 4; i++) {
			int along = random.nextInt(length) - half;
			pos.setWithOffset(origin, alongX ? along : 0, 1, alongX ? 0 : along);
			if (source(level, pos) && solid(level, pos.below())) {
				set(level, pos.immutable(), random.nextBoolean() ? ModBlocks.ABYSSAL_GLOWMOSS.defaultBlockState()
						: Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 1 + random.nextInt(3)));
			}
		}
	}

	/** A raft of lost cargo, still hung with the lanterns that lit it, sinking slowly. */
	private static void lostCargo(WorldGenLevel level, RandomSource random, BlockPos origin) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Block planks = random.nextBoolean() ? Blocks.SPRUCE_PLANKS : Blocks.DARK_OAK_PLANKS;
		Block fence = planks == Blocks.SPRUCE_PLANKS ? Blocks.SPRUCE_FENCE : Blocks.DARK_OAK_FENCE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 && Math.abs(dz) == 2 || random.nextInt(6) == 0) {
					continue;
				}
				pos.setWithOffset(origin, dx, 0, dz);
				level.setBlock(pos, planks.defaultBlockState(), 2);
			}
		}
		int crates = 1 + random.nextInt(3);
		for (int i = 0; i < crates; i++) {
			int dx = random.nextInt(3) - 1;
			int dz = random.nextInt(3) - 1;
			pos.setWithOffset(origin, dx, 1, dz);
			if (!solid(level, pos.below()) || !source(level, pos)) {
				continue;
			}
			BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
			if (i == 0) {
				container(level, pos.immutable(), barrel, random, "drift_cargo");
			} else {
				level.setBlock(pos, random.nextBoolean() ? barrel : Blocks.HAY_BLOCK.defaultBlockState(), 2);
			}
		}
		// A lantern on a post at one corner, and chains with lanterns hanging underneath.
		pos.setWithOffset(origin, 1, 1, 1);
		if (source(level, pos)) {
			set(level, pos.immutable(), fence.defaultBlockState());
			pos.move(Direction.UP);
			if (source(level, pos)) {
				set(level, pos.immutable(), Blocks.LANTERN.defaultBlockState());
			}
		}
		for (int c = 0; c < 2; c++) {
			pos.setWithOffset(origin, c == 0 ? -1 : 1, -1, c == 0 ? 1 : -1);
			hang(level, pos, 2 + random.nextInt(4), random);
		}
	}

	// --- relics of the drowned people ------------------------------------------------------

	/** One of the little ruins the drowned people left on the floor. */
	private boolean relic(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 6)) {
			return false;
		}
		int floor = ground(level, origin.getX(), origin.getZ());
		BlockPos base = new BlockPos(origin.getX(), floor, origin.getZ());
		if (!source(level, base.above()) || !source(level, base.above(3))) {
			return false;
		}
		Block[][] stones = {
				{Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL},
				{Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICK_SLAB, Blocks.PRISMARINE_WALL},
				{Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICK_SLAB,
						Blocks.DEEPSLATE_BRICK_WALL},
				{Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_SLAB, Blocks.STONE_BRICK_WALL},
				{Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF_BRICKS, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICK_SLAB, Blocks.TUFF_BRICK_WALL},
		};
		Block[] stone = stones[random.nextInt(stones.length)];
		switch (random.nextInt(7)) {
			case 0 -> column(level, random, base, stone);
			case 1 -> toppledColumn(level, random, base, stone);
			case 2 -> amphorae(level, random, base);
			case 3 -> anchor(level, random, base);
			case 4 -> lampPost(level, random, base, stone);
			case 5 -> stoneHead(level, random, base, stone);
			default -> cargo(level, random, base);
		}
		return true;
	}

	private static BlockState upright(Block block) {
		BlockState state = block.defaultBlockState();
		return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y) : state;
	}

	private static void column(WorldGenLevel level, RandomSource random, BlockPos base, Block[] stone) {
		int columns = 1 + random.nextInt(3);
		for (int c = 0; c < columns; c++) {
			BlockPos foot = c == 0 ? base : base.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
			BlockPos.MutableBlockPos pos = foot.mutable();
			level.setBlock(pos, upright(stone[0]), 2);
			int height = 2 + random.nextInt(5);
			for (int i = 1; i <= height; i++) {
				pos.move(Direction.UP);
				if (!WorldGenUtil.isWater(level.getBlockState(pos))) {
					break;
				}
				level.setBlock(pos, upright(i == height && random.nextBoolean() ? stone[2] : random.nextInt(4) == 0 ? stone[1] : stone[0]), 2);
			}
			pos.move(Direction.UP);
			if (source(level, pos) && random.nextBoolean()) {
				set(level, pos.immutable(), stone[3].defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
			}
		}
	}

	private static void toppledColumn(WorldGenLevel level, RandomSource random, BlockPos base, Block[] stone) {
		Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		int length = 3 + random.nextInt(5);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < length; i++) {
			if (random.nextInt(6) == 0) {
				continue;
			}
			pos.set(base.relative(along, i)).move(Direction.UP);
			int floor = pos.getY();
			while (!solid(level, pos.below()) && pos.getY() > floor - 3) {
				pos.move(Direction.DOWN);
			}
			BlockState state = (random.nextInt(4) == 0 ? stone[1] : stone[0]).defaultBlockState();
			if (state.hasProperty(RotatedPillarBlock.AXIS)) {
				state = state.setValue(RotatedPillarBlock.AXIS, along.getAxis());
			}
			level.setBlock(pos, state, 2);
		}
		// The capital that broke off, lying at the end.
		pos.set(base.relative(along, length)).move(Direction.UP);
		if (source(level, pos)) {
			level.setBlock(pos, stone[2].defaultBlockState(), 2);
		}
	}

	private static void amphorae(WorldGenLevel level, RandomSource random, BlockPos base) {
		int pots = 2 + random.nextInt(4);
		for (int i = 0; i < pots; i++) {
			BlockPos at = base.offset(random.nextInt(5) - 2, 1, random.nextInt(5) - 2);
			if (!source(level, at) || !sturdy(level, at.below())) {
				continue;
			}
			BlockState pot = Blocks.DECORATED_POT.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random))
					.setValue(BlockStateProperties.CRACKED, random.nextInt(3) == 0);
			if (i < 2) {
				container(level, at, pot, random, "amphora");
			} else {
				set(level, at, pot);
			}
		}
	}

	private static void anchor(WorldGenLevel level, RandomSource random, BlockPos base) {
		Direction arms = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
		BlockState bars = Blocks.IRON_BARS.defaultBlockState();
		// The arms half sunk in the ooze, the shank standing up, the ring at its top.
		for (int i = -2; i <= 2; i++) {
			pos.set(base.relative(arms, i));
			level.setBlock(pos, iron, 2);
			if (Math.abs(i) == 2) {
				pos.move(Direction.UP);
				level.setBlock(pos, iron, 2);
			}
		}
		pos.set(base);
		int shank = 3 + random.nextInt(2);
		for (int i = 1; i <= shank; i++) {
			pos.move(Direction.UP);
			level.setBlock(pos, i == shank ? iron : bars, 2);
		}
		for (int i = -1; i <= 1; i += 2) {
			BlockPos stock = pos.relative(arms, i).below();
			if (WorldGenUtil.isWater(level.getBlockState(stock))) {
				level.setBlock(stock, iron, 2);
			}
		}
		// Its chain trails away over the floor.
		Direction trail = arms.getClockWise();
		BlockState chain = Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, trail.getAxis());
		for (int i = 1; i < 6 + random.nextInt(8); i++) {
			pos.set(base.relative(trail, i)).move(Direction.UP);
			int floor = pos.getY();
			while (!solid(level, pos.below()) && pos.getY() > floor - 3) {
				pos.move(Direction.DOWN);
			}
			if (source(level, pos)) {
				set(level, pos.immutable(), chain);
			}
		}
	}

	private static void lampPost(WorldGenLevel level, RandomSource random, BlockPos base, Block[] stone) {
		BlockPos.MutableBlockPos pos = base.mutable();
		level.setBlock(pos, upright(stone[0]), 2);
		int height = 2 + random.nextInt(3);
		for (int i = 0; i < height; i++) {
			pos.move(Direction.UP);
			if (!source(level, pos)) {
				return;
			}
			set(level, pos.immutable(), stone[4].defaultBlockState());
		}
		pos.move(Direction.UP);
		if (source(level, pos)) {
			Block light = switch (random.nextInt(4)) {
				case 0 -> Blocks.SOUL_LANTERN;
				case 1 -> Blocks.SEA_LANTERN;
				default -> Blocks.LANTERN;
			};
			set(level, pos.immutable(), light.defaultBlockState());
		}
	}

	private static void stoneHead(WorldGenLevel level, RandomSource random, BlockPos base, Block[] stone) {
		Direction face = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		Direction right = face.getClockWise();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int sink = random.nextInt(2);
		for (int a = -1; a <= 1; a++) {
			for (int dy = 0; dy <= 2; dy++) {
				for (int depth = 0; depth <= 2; depth++) {
					pos.set(base.relative(right, a).relative(face, -depth)).move(Direction.UP, dy + 1 - sink);
					BlockState state = random.nextInt(5) == 0 ? stone[1].defaultBlockState() : upright(stone[0]);
					if (depth == 0 && dy == 1 && a != 0) {
						state = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
					} else if (depth == 0 && dy == 0 && a == 0 && sink == 0) {
						state = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
					}
					level.setBlock(pos, state, 2);
				}
			}
		}
		pos.set(base.relative(face, 1)).move(Direction.UP);
		if (source(level, pos) && sturdy(level, pos.below())) {
			set(level, pos.immutable(), Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 2 + random.nextInt(3)));
		}
		pos.set(base.relative(face, -1)).move(Direction.UP, 4 - sink);
		if (source(level, pos)) {
			set(level, pos.immutable(), stone[3].defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
		}
	}

	private static void cargo(WorldGenLevel level, RandomSource random, BlockPos base) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int crates = 2 + random.nextInt(3);
		for (int i = 0; i < crates; i++) {
			pos.set(base.offset(random.nextInt(5) - 2, 1, random.nextInt(5) - 2));
			if (!source(level, pos) || !sturdy(level, pos.below())) {
				continue;
			}
			BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING,
					random.nextBoolean() ? Direction.UP : Direction.Plane.HORIZONTAL.getRandomDirection(random));
			if (i == 0) {
				container(level, pos.immutable(), barrel, random, "cargo");
			} else {
				level.setBlock(pos, barrel, 2);
				if (random.nextInt(3) == 0 && source(level, pos.above())) {
					level.setBlock(pos.above(), barrel, 2);
				}
			}
		}
		pos.set(base.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random), 3)).move(Direction.UP);
		if (source(level, pos) && sturdy(level, pos.below())) {
			set(level, pos.immutable(), Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HALF, Half.BOTTOM)
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
		}
	}

	public enum Style implements StringRepresentable {
		MEADOW("meadow"),
		DRIFT("drift"),
		RELIC("relic");

		public static final Codec<Style> CODEC = StringRepresentable.fromEnum(Style::values);
		private final String name;

		Style(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}
}
