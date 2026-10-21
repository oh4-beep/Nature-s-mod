package com.naturesmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.naturesmod.NaturesMod;
import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Decorations of the Abyss's sea floor: black smokers venting into the dark, bubbling soul
 * vents, glowing reef trees, the skeletons of whales that sank to the bottom, and nodules of
 * dark rock scattered over the ooze.
 */
public record AbyssFeature(Style style) implements Feature {
	public static final MapCodec<AbyssFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Style.CODEC.fieldOf("style").forGetter(AbyssFeature::style)
	).apply(instance, AbyssFeature::new));

	private static final Block[] FROGLIGHTS = {Blocks.PEARLESCENT_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.OCHRE_FROGLIGHT};
	private static final Block[] CORAL_BLOCKS = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK,
			Blocks.HORN_CORAL_BLOCK, Blocks.PRISMARINE};
	private static final Block[] WALL_FANS = {Blocks.TUBE_CORAL_WALL_FAN, Blocks.BRAIN_CORAL_WALL_FAN, Blocks.BUBBLE_CORAL_WALL_FAN,
			Blocks.HORN_CORAL_WALL_FAN, Blocks.FIRE_CORAL_WALL_FAN};

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!WorldGenUtil.isWater(level.getBlockState(origin)) || origin.getY() >= level.getSeaLevel() - 2) {
			return false;
		}
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 1);
		boolean placed = switch (style) {
			case BLACK_SMOKER -> blackSmoker(level, window, random, origin);
			case SOUL_VENT -> soulVent(level, window, random, origin);
			case GLOW_REEF -> glowReef(level, window, random, origin);
			case NODULES -> nodules(level, window, random, origin);
			case WHALE_FALL -> whaleFall(level, window, random, origin);
			case GIANT_KELP -> giantKelp(level, window, random, origin);
			case HANGING_GLOW -> hangingGlow(level, window, random, origin);
			case BONE_PILE -> bonePile(level, window, random, origin);
			case DIVER_REMAINS -> diverRemains(level, window, random, origin);
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

	private static boolean replaceable(BlockState state) {
		return WorldGenUtil.isWater(state) || state.isAir() || state.canBeReplaced();
	}

	private static boolean isSolid(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.CORAL_PLANTS);
	}

	/** The top of the solid ground below a position in the water. */
	private static int floor(WorldGenLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
		for (int i = 0; i < 48 && pos.getY() > level.getMinY(); i++) {
			pos.move(Direction.DOWN);
			if (isSolid(level, pos)) {
				return pos.getY();
			}
		}
		return y - 1;
	}

	/** Fills water above the ground with an upward bubble column. */
	private static void bubbles(WorldGenLevel level, BlockPos.MutableBlockPos pos, int height) {
		BlockState column = Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, false);
		for (int i = 0; i < height; i++) {
			pos.move(Direction.UP);
			if (!level.getBlockState(pos).is(Blocks.WATER) || !level.getBlockState(pos.above()).is(Blocks.WATER)) {
				return;
			}
			level.setBlock(pos, column, 2);
		}
	}

	// --- black smoker --------------------------------------------------------------------

	private boolean blackSmoker(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 6)) {
			return false;
		}
		int ground = floor(level, origin.getX(), origin.getY() + 1, origin.getZ());
		int height = 6 + random.nextInt(18);
		double base = 2.0 + random.nextDouble() * 1.2;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState basalt = Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		double leanX = (random.nextDouble() - 0.5) * 0.12;
		double leanZ = (random.nextDouble() - 0.5) * 0.12;
		for (int dy = -2; dy <= height; dy++) {
			double f = Math.max(0, dy) / (double) height;
			double radius = base * (1.0 - 0.55 * f) + (random.nextDouble() - 0.5) * 0.4;
			double cx = origin.getX() + 0.5 + leanX * dy;
			double cz = origin.getZ() + 0.5 + leanZ * dy;
			int ir = (int) Math.ceil(radius) + 1;
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					int x = Mth.floor(cx) + dx;
					int z = Mth.floor(cz) + dz;
					double d = Math.sqrt(Mth.square(x + 0.5 - cx) + Mth.square(z + 0.5 - cz));
					if (d > radius) {
						continue;
					}
					pos.set(x, ground + dy, z);
					if (d < 0.8 && dy > 0) {
						set(level, pos, Blocks.WATER.defaultBlockState());
						continue;
					}
					// Mineral crust: blackstone and basalt with veins of gold and rings of magma near the mouth.
					BlockState state;
					int roll = random.nextInt(100);
					if (f > 0.75 && roll < 30) {
						state = Blocks.MAGMA_BLOCK.defaultBlockState();
					} else if (roll < 4) {
						state = Blocks.GILDED_BLACKSTONE.defaultBlockState();
					} else if (roll < 45) {
						state = basalt;
					} else if (roll < 55) {
						state = Blocks.SMOOTH_BASALT.defaultBlockState();
					} else {
						state = Blocks.BLACKSTONE.defaultBlockState();
					}
					set(level, pos, state);
				}
			}
		}
		// Hot water belches out of the chimney.
		pos.set(Mth.floor(origin.getX() + 0.5 + leanX), ground, Mth.floor(origin.getZ() + 0.5 + leanZ));
		set(level, pos, Blocks.SOUL_SAND.defaultBlockState());
		bubbles(level, pos, height + 10 + random.nextInt(25));
		// Tube worms and scalding magma around the base.
		for (int i = 0; i < 14; i++) {
			int x = origin.getX() + random.nextInt(11) - 5;
			int z = origin.getZ() + random.nextInt(11) - 5;
			int y = floor(level, x, origin.getY() + 6, z);
			pos.set(x, y + 1, z);
			if (!WorldGenUtil.isWater(level.getBlockState(pos))) {
				continue;
			}
			if (random.nextInt(4) == 0) {
				set(level, pos.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
			} else {
				Block worm = random.nextInt(3) == 0 ? Blocks.TUBE_CORAL : Blocks.FIRE_CORAL;
				set(level, pos, worm.defaultBlockState());
			}
		}
		return true;
	}

	// --- soul vent -----------------------------------------------------------------------

	private boolean soulVent(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 5)) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int radius = 2 + random.nextInt(2);
		for (int dx = -radius - 1; dx <= radius + 1; dx++) {
			for (int dz = -radius - 1; dz <= radius + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz) + random.nextDouble() * 0.6;
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				int ground = floor(level, x, origin.getY() + 3, z);
				pos.set(x, ground, z);
				if (d <= radius) {
					set(level, pos, (random.nextBoolean() ? Blocks.SOUL_SAND : Blocks.SOUL_SOIL).defaultBlockState());
					if (level.getBlockState(pos).is(Blocks.SOUL_SAND)) {
						bubbles(level, pos, 12 + random.nextInt(30));
					}
				} else if (d <= radius + 1.2) {
					set(level, pos, (random.nextInt(3) == 0 ? Blocks.MAGMA_BLOCK : Blocks.BLACKSTONE).defaultBlockState());
				}
			}
		}
		return true;
	}

	// --- glowing reef --------------------------------------------------------------------

	private boolean glowReef(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 9)) {
			return false;
		}
		int trees = 1 + random.nextInt(3);
		for (int t = 0; t < trees; t++) {
			int x = origin.getX() + random.nextInt(9) - 4;
			int z = origin.getZ() + random.nextInt(9) - 4;
			int ground = floor(level, x, origin.getY() + 4, z);
			glowTree(level, window, random, new BlockPos(x, ground + 1, z));
		}
		// Sea pickles glowing among the branches.
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 6; i++) {
			int x = origin.getX() + random.nextInt(9) - 4;
			int z = origin.getZ() + random.nextInt(9) - 4;
			pos.set(x, floor(level, x, origin.getY() + 8, z) + 1, z);
			if (WorldGenUtil.isWater(level.getBlockState(pos))) {
				set(level, pos, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + random.nextInt(4)));
			}
		}
		return true;
	}

	/** A branching tree of coral and prismarine whose tips glow like lamps. */
	private static void glowTree(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos base) {
		Block body = CORAL_BLOCKS[random.nextInt(CORAL_BLOCKS.length)];
		Block light = random.nextInt(4) == 0 ? Blocks.SEA_LANTERN : FROGLIGHTS[random.nextInt(FROGLIGHTS.length)];
		Block fan = WALL_FANS[random.nextInt(WALL_FANS.length)];
		int trunk = 3 + random.nextInt(6);
		BlockPos.MutableBlockPos pos = base.mutable();
		for (int i = 0; i < trunk; i++) {
			if (!replaceable(level.getBlockState(pos))) {
				return;
			}
			set(level, pos, body.defaultBlockState());
			pos.move(Direction.UP);
		}
		int branches = 2 + random.nextInt(4);
		BlockPos top = pos.below().immutable();
		for (int b = 0; b < branches; b++) {
			pos.set(top);
			Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			int length = 2 + random.nextInt(4);
			for (int i = 0; i < length; i++) {
				pos.move(random.nextInt(3) == 0 ? Direction.UP : dir);
				if (!window.contains(pos) || !replaceable(level.getBlockState(pos))) {
					break;
				}
				set(level, pos, (i == length - 1 ? light : body).defaultBlockState());
				// Coral fans fringe the branches.
				if (random.nextInt(3) == 0) {
					Direction side = dir.getClockWise();
					BlockPos fanPos = pos.relative(side);
					if (window.contains(fanPos) && WorldGenUtil.isWater(level.getBlockState(fanPos))) {
						set(level, fanPos, fan.defaultBlockState().setValue(BaseCoralWallFanBlock.FACING, side));
					}
				}
			}
		}
		set(level, top.above(), light.defaultBlockState());
	}

	// --- nodules -------------------------------------------------------------------------

	private boolean nodules(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 6)) {
			return false;
		}
		Block[] rocks = {Blocks.BLACKSTONE, Blocks.BASALT, Blocks.TUFF, Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_BLACKSTONE};
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int count = 1 + random.nextInt(4);
		for (int n = 0; n < count; n++) {
			int cx = origin.getX() + random.nextInt(9) - 4;
			int cz = origin.getZ() + random.nextInt(9) - 4;
			int ground = floor(level, cx, origin.getY() + 3, cz);
			double radius = 0.8 + random.nextDouble() * 0.9;
			Block rock = rocks[random.nextInt(rocks.length)];
			int ir = (int) Math.ceil(radius);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dy = -1; dy <= ir; dy++) {
					for (int dz = -ir; dz <= ir; dz++) {
						if (dx * dx + dy * dy * 1.6 + dz * dz > radius * radius + 0.3) {
							continue;
						}
						pos.set(cx + dx, ground + dy, cz + dz);
						if (replaceable(level.getBlockState(pos)) || dy <= 0) {
							BlockState state = rock.defaultBlockState();
							if (state.hasProperty(RotatedPillarBlock.AXIS)) {
								state = state.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
							}
							set(level, pos, state);
						}
					}
				}
			}
		}
		return true;
	}

	// --- whale fall ----------------------------------------------------------------------

	private boolean whaleFall(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int length = 16 + random.nextInt(14);
		if (!window.containsRadius(origin.getX(), origin.getZ(), length / 2 + 7)) {
			return false;
		}
		boolean alongX = random.nextBoolean();
		Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
		BlockState spine = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
		BlockState rib = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		BlockState across = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.Z : Direction.Axis.X);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int half = length / 2;
		// The spine sags into the ooze, rising and dipping along its length.
		int sink = random.nextInt(2);
		for (int i = -half; i <= half; i++) {
			int x = origin.getX() + (alongX ? i : 0);
			int z = origin.getZ() + (alongX ? 0 : i);
			int ground = floor(level, x, origin.getY() + 6, z);
			int y = ground + 1 - sink + (int) Math.round(Math.sin(i * 0.35) * 0.6);
			pos.set(x, y, z);
			set(level, pos, spine);
			// Ribs arch up and inwards from the middle of the body.
			double along = (i + half) / (double) length;
			if (along > 0.25 && along < 0.75 && Math.floorMod(i, 2) == 0 && random.nextInt(6) != 0) {
				double size = Math.sin(Math.PI * (along - 0.25) / 0.5);
				int width = 3 + (int) Math.round(size * 3);
				int height = 4 + (int) Math.round(size * 5);
				boolean broken = random.nextInt(4) == 0;
				for (int side = -1; side <= 1; side += 2) {
					for (int k = 0; k <= height; k++) {
						double t = k / (double) height;
						double out = width * Math.sin(Math.PI * 0.85 * t);
						if (broken && side > 0 && t > 0.5) {
							break;
						}
						int ox = alongX ? 0 : (int) Math.round(out * side);
						int oz = alongX ? (int) Math.round(out * side) : 0;
						pos.set(x + ox, y + k, z + oz);
						if (window.contains(pos) && replaceable(level.getBlockState(pos))) {
							set(level, pos, t < 0.25 || t > 0.8 ? across : rib);
						}
					}
				}
			}
		}
		// The skull: a heavy block of bone at the head end, with hollow eye sockets.
		int headX = origin.getX() + (alongX ? -half - 2 : 0);
		int headZ = origin.getZ() + (alongX ? 0 : -half - 2);
		int headY = floor(level, headX, origin.getY() + 6, headZ) + 1 - sink;
		for (int a = -2; a <= 2; a++) {
			for (int b = -2; b <= 2; b++) {
				for (int h = 0; h <= 3; h++) {
					if (Math.abs(a) == 2 && Math.abs(b) == 2 || h == 3 && (Math.abs(a) == 2 || Math.abs(b) == 2)) {
						continue;
					}
					int x = headX + (alongX ? a : b);
					int z = headZ + (alongX ? b : a);
					pos.set(x, headY + h, z);
					boolean socket = h == 2 && Math.abs(b) == 1 && a == -2;
					if (window.contains(pos)) {
						set(level, pos, socket ? Blocks.WATER.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState());
					}
				}
			}
		}
		// Life gathers on the carcass.
		for (int i = 0; i < 8; i++) {
			int x = origin.getX() + random.nextInt(length) - half;
			int z = origin.getZ() + random.nextInt(9) - 4;
			if (!alongX) {
				int swap = x - origin.getX();
				x = origin.getX() + (z - origin.getZ());
				z = origin.getZ() + swap;
			}
			pos.set(x, floor(level, x, origin.getY() + 10, z) + 1, z);
			if (window.contains(pos) && WorldGenUtil.isWater(level.getBlockState(pos))) {
				set(level, pos, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + random.nextInt(3)));
			}
		}
		return true;
	}

	// --- giant kelp ----------------------------------------------------------------------

	/** A stand of kelp far taller than any in the Overworld, swaying on a seamount summit. */
	private boolean giantKelp(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 4)) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int strands = 3 + random.nextInt(6);
		boolean placed = false;
		for (int n = 0; n < strands; n++) {
			int x = origin.getX() + random.nextInt(7) - 3;
			int z = origin.getZ() + random.nextInt(7) - 3;
			int ground = floor(level, x, origin.getY() + 4, z);
			pos.set(x, ground, z);
			if (!isSolid(level, pos)) {
				continue;
			}
			int height = 14 + random.nextInt(30);
			int top = ground;
			for (int h = 1; h <= height; h++) {
				pos.set(x, ground + h, z);
				if (!level.getBlockState(pos).is(Blocks.WATER) || !level.getBlockState(pos.above()).is(Blocks.WATER)
						|| pos.getY() >= level.getSeaLevel() - 3) {
					break;
				}
				top = ground + h;
			}
			if (top - ground < 4) {
				continue;
			}
			for (int y = ground + 1; y < top; y++) {
				level.setBlock(pos.set(x, y, z), Blocks.KELP_PLANT.defaultBlockState(), 2);
			}
			level.setBlock(pos.set(x, top, z), Blocks.KELP.defaultBlockState().setValue(BlockStateProperties.AGE_25, 25), 2);
			placed = true;
		}
		return placed;
	}

	// --- hanging glow --------------------------------------------------------------------

	/**
	 * Bioluminescent growths dripping from the underside of a drifting island or an overhang:
	 * beacons in the black water that a diver can see from far away.
	 */
	private boolean hangingGlow(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 3)) {
			return false;
		}
		BlockPos.MutableBlockPos pos = origin.mutable();
		int ceiling = Integer.MIN_VALUE;
		for (int i = 0; i < 12; i++) {
			pos.move(Direction.UP);
			if (isSolid(level, pos)) {
				ceiling = pos.getY();
				break;
			}
			if (!WorldGenUtil.isWater(level.getBlockState(pos))) {
				return false;
			}
		}
		if (ceiling == Integer.MIN_VALUE || !WorldGenUtil.isTerrain(level.getBlockState(pos)) && !level.getBlockState(pos).is(Blocks.DEEPSLATE)
				&& !level.getBlockState(pos).is(Blocks.COBBLED_DEEPSLATE) && !level.getBlockState(pos).is(Blocks.TUFF)) {
			return false;
		}
		Block glow = random.nextInt(3) == 0 ? Blocks.SHROOMLIGHT : FROGLIGHTS[random.nextInt(FROGLIGHTS.length)];
		int drips = 1 + random.nextInt(4);
		for (int d = 0; d < drips; d++) {
			int x = origin.getX() + random.nextInt(5) - 2;
			int z = origin.getZ() + random.nextInt(5) - 2;
			int length = 1 + random.nextInt(d == 0 ? 4 : 2);
			// Find the ceiling right above this drip.
			int y = ceiling + 2;
			pos.set(x, y, z);
			int found = Integer.MIN_VALUE;
			for (int i = 0; i < 6; i++) {
				pos.move(Direction.DOWN);
				if (WorldGenUtil.isWater(level.getBlockState(pos)) && isSolid(level, pos.above())) {
					found = pos.getY();
					break;
				}
			}
			if (found == Integer.MIN_VALUE) {
				continue;
			}
			for (int k = 0; k < length; k++) {
				pos.set(x, found - k, z);
				if (!WorldGenUtil.isWater(level.getBlockState(pos))) {
					break;
				}
				level.setBlock(pos, glow.defaultBlockState(), 2);
			}
		}
		return true;
	}

	// --- bone piles ------------------------------------------------------------------------

	/** Bones and skulls heaped on the sea floor: whatever came down here did not leave. */
	private boolean bonePile(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 4)) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int bones = 3 + random.nextInt(6);
		for (int i = 0; i < bones; i++) {
			int x = origin.getX() + random.nextInt(7) - 3;
			int z = origin.getZ() + random.nextInt(7) - 3;
			int ground = floor(level, x, origin.getY() + 3, z);
			pos.set(x, ground + 1, z);
			if (!WorldGenUtil.isWater(level.getBlockState(pos))) {
				continue;
			}
			Direction.Axis axis = Direction.Axis.values()[random.nextInt(3)];
			set(level, pos, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
			if (random.nextInt(3) == 0 && WorldGenUtil.isWater(level.getBlockState(pos.above()))) {
				set(level, pos.move(Direction.UP), Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
			}
		}
		int skulls = 1 + random.nextInt(4);
		for (int i = 0; i < skulls; i++) {
			int x = origin.getX() + random.nextInt(7) - 3;
			int z = origin.getZ() + random.nextInt(7) - 3;
			int ground = floor(level, x, origin.getY() + 4, z);
			pos.set(x, ground + 1, z);
			if (!WorldGenUtil.isWater(level.getBlockState(pos))) {
				continue;
			}
			Block skull = random.nextInt(14) == 0 ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL;
			level.setBlock(pos, skull.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16)), 2);
		}
		return true;
	}

	// --- the remains of a diver -----------------------------------------------------------

	/**
	 * A diver who never made it back: a skeleton in rusted gear slumped against a chest of what
	 * they carried, a lantern gone dark beside them.
	 */
	private boolean diverRemains(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 3)) {
			return false;
		}
		int ground = floor(level, origin.getX(), origin.getY() + 3, origin.getZ());
		BlockPos seat = new BlockPos(origin.getX(), ground + 1, origin.getZ());
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockPos chestPos = seat.relative(facing.getOpposite());
		if (!WorldGenUtil.isWater(level.getBlockState(seat)) || !WorldGenUtil.isWater(level.getBlockState(chestPos))
				|| !isSolid(level, chestPos.below())) {
			return false;
		}
		ArmorStand body = new ArmorStand(level.getLevel(), seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5);
		body.setYRot(facing.toYRot());
		body.setYBodyRot(facing.toYRot());
		body.setNoBasePlate(true);
		body.setShowArms(true);
		body.setHeadPose(new Rotations(32.0F, 0.0F, random.nextFloat() * 30.0F - 15.0F));
		body.setLeftArmPose(new Rotations(-12.0F, 0.0F, -12.0F));
		body.setRightArmPose(new Rotations(-20.0F, 0.0F, 14.0F));
		body.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SKELETON_SKULL));
		body.setItemSlot(EquipmentSlot.CHEST, new ItemStack(random.nextBoolean() ? Items.CHAINMAIL_CHESTPLATE : Items.IRON_CHESTPLATE));
		if (random.nextBoolean()) {
			body.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
		}
		if (random.nextInt(3) == 0) {
			body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		}
		level.addFreshEntity(body);
		level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing)
				.setValue(BlockStateProperties.WATERLOGGED, true), 2);
		if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
			chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, NaturesMod.id("chests/diver_remains")), random.nextLong());
		}
		BlockPos lantern = seat.relative(facing.getClockWise());
		if (WorldGenUtil.isWater(level.getBlockState(lantern)) && isSolid(level, lantern.below())) {
			level.setBlock(lantern, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 2);
		}
		BlockPos skull = seat.relative(facing.getCounterClockWise());
		if (WorldGenUtil.isWater(level.getBlockState(skull)) && isSolid(level, skull.below()) && random.nextBoolean()) {
			level.setBlock(skull, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16)), 2);
		}
		return true;
	}

	public enum Style implements StringRepresentable {
		BLACK_SMOKER("black_smoker"),
		SOUL_VENT("soul_vent"),
		GLOW_REEF("glow_reef"),
		NODULES("nodules"),
		WHALE_FALL("whale_fall"),
		GIANT_KELP("giant_kelp"),
		HANGING_GLOW("hanging_glow"),
		BONE_PILE("bone_pile"),
		DIVER_REMAINS("diver_remains");

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
