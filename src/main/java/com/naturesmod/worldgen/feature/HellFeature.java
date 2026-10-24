package com.naturesmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.naturesmod.NaturesMod;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.SelfPlacing;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The marks of Hell on the Nether: fields of skulls on pikes, the burnt-out husks of trees, flesh
 * swelling out of the rock, the bones of fallen titans, smouldering brimstone, ember crystals and
 * little ruined shrines where braziers still burn.
 */
public record HellFeature(Style style) implements SelfPlacing {
	public static final MapCodec<HellFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Style.CODEC.fieldOf("style").forGetter(HellFeature::style)
	).apply(instance, HellFeature::new));

	private static final ResourceKey<LootTable> SHRINE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, NaturesMod.id("chests/hell_shrine"));
	private static final int NONE = Integer.MIN_VALUE;

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin).isAir()) {
			return false;
		}
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 1);
		boolean placed = switch (style) {
			case SKULL_PIKES -> skullPikes(level, window, random, origin);
			case CHARRED_TREE -> charredTree(level, window, random, origin);
			case FLESH_GROWTH -> fleshGrowth(level, window, random, origin);
			case TITAN_BONES -> titanBones(level, window, random, origin);
			case BRIMSTONE_DEPOSIT -> brimstoneDeposit(level, window, random, origin);
			case EMBER_CRYSTALS -> emberCrystals(level, window, random, origin);
			case HELL_SHRINE -> hellShrine(level, window, random, origin);
			case BONE_SPIKES -> boneSpikes(level, window, random, origin);
		};
		if (placed) {
			FeatureStats.placed("hell_" + style.getSerializedName(), origin);
		}
		return placed;
	}

	// --- helpers -------------------------------------------------------------------------

	private static boolean isGround(WorldGenLevel level, BlockPos pos, BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && state.isFaceSturdy(level, pos, Direction.UP);
	}

	/** The rock the Nether is made of, which flesh and brimstone may take over. */
	private static boolean isRockMass(BlockState state) {
		return state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.NYLIUM) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)
				|| state.is(Blocks.GRAVEL) || state.is(Blocks.MAGMA_BLOCK) || state.is(ModBlocks.ASH_BLOCK) || state.is(ModBlocks.FLESH_BLOCK);
	}

	/** Y of the ground below a position, through open air only; NONE if there is none within reach. */
	private static int floorBelow(WorldGenLevel level, int x, int y, int z, int reach) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
		for (int i = 0; i < reach && pos.getY() > level.getMinY() + 1; i++) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir()) {
				return isGround(level, pos, state) && i > 0 ? pos.getY() : NONE;
			}
			pos.move(Direction.DOWN);
		}
		return NONE;
	}

	/** Y of the rock above a position, through open air only; NONE if there is none within reach. */
	private static int roofAbove(WorldGenLevel level, int x, int y, int z, int reach) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
		for (int i = 0; i < reach && pos.getY() < level.getMaxY() - 1; i++) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir()) {
				return isRockMass(state) ? pos.getY() : NONE;
			}
			pos.move(Direction.UP);
		}
		return NONE;
	}

	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, 2);
	}

	/** Places a block only where there is open air. */
	private static boolean fill(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockState(pos).isAir()) {
			level.setBlock(pos, state, 2);
			return true;
		}
		return false;
	}

	private static BlockState skull(RandomSource random, int witherChance) {
		Block skull = random.nextInt(witherChance) == 0 ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL;
		return skull.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16));
	}

	private static BlockState log(Direction.Axis axis) {
		return ModBlocks.CHARRED_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	private static BlockState bone(Direction.Axis axis) {
		return Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	// --- skulls on pikes -----------------------------------------------------------------

	/** A field of pikes, each one crowned with a skull; now and then someone is still on one. */
	private boolean skullPikes(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 6)) {
			return false;
		}
		int ground = floorBelow(level, origin.getX(), origin.getY(), origin.getZ(), 24);
		if (ground == NONE || ground <= level.getSeaLevel()) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int pikes = 3 + random.nextInt(6);
		int made = 0;
		for (int i = 0; i < pikes * 3 && made < pikes; i++) {
			int x = origin.getX() + random.nextInt(11) - 5;
			int z = origin.getZ() + random.nextInt(11) - 5;
			int y = floorBelow(level, x, ground + 5, z, 12);
			int height = 2 + random.nextInt(3);
			if (y == NONE || !open(level, pos, x, y + 1, z, height + 2)) {
				continue;
			}
			int roll = random.nextInt(5);
			BlockState shaft = roll == 0 ? bone(Direction.Axis.Y) : roll < 3 ? Blocks.NETHER_BRICK_FENCE.defaultBlockState()
					: Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();
			for (int h = 1; h <= height; h++) {
				set(level, pos.set(x, y + h, z), shaft);
			}
			set(level, pos.set(x, y + height + 1, z), skull(random, 4));
			made++;
			if (made == 1 && random.nextInt(4) == 0) {
				impale(level, random, x, y + height + 1, z);
			}
		}
		// Soul fire smoulders among the pikes.
		for (int i = 0; i < 3 && made > 0; i++) {
			int x = origin.getX() + random.nextInt(9) - 4;
			int z = origin.getZ() + random.nextInt(9) - 4;
			int y = floorBelow(level, x, ground + 4, z, 10);
			if (y != NONE && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
				set(level, pos.set(x, y, z), Blocks.SOUL_SOIL.defaultBlockState());
				set(level, pos.set(x, y + 1, z), Blocks.SOUL_FIRE.defaultBlockState());
			}
		}
		return made > 0;
	}

	private static boolean open(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int height) {
		for (int h = 0; h < height; h++) {
			if (!level.getBlockState(pos.set(x, y + h, z)).isAir()) {
				return false;
			}
		}
		return true;
	}

	/** What is left of someone who was impaled on a pike: bones in rusted mail. */
	private static void impale(WorldGenLevel level, RandomSource random, int x, int y, int z) {
		set(level, new BlockPos(x, y, z), Blocks.NETHER_BRICK_FENCE.defaultBlockState());
		ArmorStand body = new ArmorStand(level.getLevel(), x + 0.5, y - 0.6, z + 0.5);
		float yaw = random.nextFloat() * 360.0F;
		body.setYRot(yaw);
		body.setYBodyRot(yaw);
		body.setNoBasePlate(true);
		body.setNoGravity(true);
		body.setShowArms(true);
		body.setHeadPose(new Rotations(40.0F, 0.0F, random.nextFloat() * 40.0F - 20.0F));
		body.setLeftArmPose(new Rotations(10.0F, 0.0F, -40.0F));
		body.setRightArmPose(new Rotations(-10.0F, 0.0F, 50.0F));
		body.setItemSlot(EquipmentSlot.HEAD, new ItemStack(random.nextInt(3) == 0 ? Items.WITHER_SKELETON_SKULL : Items.SKELETON_SKULL));
		body.setItemSlot(EquipmentSlot.CHEST, new ItemStack(random.nextBoolean() ? Items.CHAINMAIL_CHESTPLATE : Items.GOLDEN_CHESTPLATE));
		level.addFreshEntity(body);
	}

	// --- charred trees -------------------------------------------------------------------

	/** The burnt-out husk of a tree from a world that is long gone, embers still glowing in it. */
	private boolean charredTree(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 5)) {
			return false;
		}
		int x = origin.getX();
		int z = origin.getZ();
		int ground = floorBelow(level, x, origin.getY(), z, 24);
		if (ground == NONE || ground <= level.getSeaLevel()) {
			return false;
		}
		int roof = roofAbove(level, x, ground + 1, z, 40);
		int room = roof == NONE ? 30 : roof - ground - 2;
		if (room < 6) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int height = Math.min(room - 1, 4 + random.nextInt(7));
		for (int h = 1; h <= height; h++) {
			set(level, pos.set(x, ground + h, z), h > 1 && h < height && random.nextInt(8) == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState()
					: log(Direction.Axis.Y));
		}
		// Roots clawing at the ground.
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (random.nextInt(3) != 0) {
				pos.set(x, ground + 1, z).move(side);
				if (level.getBlockState(pos).isAir() && isGround(level, pos.below(), level.getBlockState(pos.below()))) {
					set(level, pos, log(side.getAxis()));
				}
			}
		}
		// A few bare, crooked branches.
		int branches = 2 + random.nextInt(3);
		for (int b = 0; b < branches; b++) {
			Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			int bx = x;
			int by = ground + Math.max(2, height / 2 + random.nextInt(Math.max(1, height / 2)));
			int bz = z;
			int length = 1 + random.nextInt(3);
			for (int i = 0; i < length; i++) {
				bx += side.getStepX();
				bz += side.getStepZ();
				boolean rise = random.nextInt(3) == 0;
				if (rise) {
					by++;
				}
				pos.set(bx, by, bz);
				if (!window.contains(pos) || !fill(level, pos, rise ? log(Direction.Axis.Y) : log(side.getAxis()))) {
					break;
				}
			}
			// Something dangles from the end of a branch now and then.
			if (random.nextInt(4) == 0 && level.getBlockState(pos.set(bx, by - 1, bz)).isAir()) {
				int chain = 1 + random.nextInt(3);
				for (int c = 1; c <= chain; c++) {
					fill(level, pos.set(bx, by - c, bz), Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
				}
				fill(level, pos.set(bx, by - chain - 1, bz), random.nextBoolean()
						? Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true) : skull(random, 6));
			}
		}
		// Ash heaps at the foot of the tree.
		for (int i = 0; i < 6; i++) {
			int ax = x + random.nextInt(7) - 3;
			int az = z + random.nextInt(7) - 3;
			int ay = floorBelow(level, ax, ground + 3, az, 8);
			if (ay != NONE && isRockMass(level.getBlockState(pos.set(ax, ay, az)))) {
				set(level, pos, ModBlocks.ASH_BLOCK.defaultBlockState());
			}
		}
		return true;
	}

	// --- flesh growths -------------------------------------------------------------------

	/** Flesh swelling out of a cave wall or roof, blistered with glowing boils, dripping vines. */
	private boolean fleshGrowth(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 6)) {
			return false;
		}
		// Find the rock to grow from: the roof first, else a wall.
		Direction toward = random.nextInt(3) == 0 ? Direction.Plane.HORIZONTAL.getRandomDirection(random) : Direction.UP;
		BlockPos.MutableBlockPos pos = origin.mutable();
		BlockPos contact = null;
		for (int i = 0; i < 16; i++) {
			pos.move(toward);
			if (!window.contains(pos) || pos.getY() >= level.getMaxY() - 2) {
				return false;
			}
			BlockState state = level.getBlockState(pos);
			if (!state.isAir()) {
				if (!isRockMass(state)) {
					return false;
				}
				contact = pos.immutable();
				break;
			}
		}
		if (contact == null) {
			return false;
		}
		double radius = 2.5 + random.nextDouble() * 2.5;
		int ir = (int) Math.ceil(radius);
		BlockState flesh = ModBlocks.FLESH_BLOCK.defaultBlockState();
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dy = -ir; dy <= ir; dy++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz) + random.nextDouble() * 0.9;
					if (d > radius) {
						continue;
					}
					pos.set(contact.getX() + dx, contact.getY() + dy, contact.getZ() + dz);
					if (!window.contains(pos)) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (isRockMass(state)) {
						set(level, pos, d < radius - 1.5 && random.nextInt(9) == 0 ? Blocks.SHROOMLIGHT.defaultBlockState() : flesh);
					} else if (state.isAir() && d < radius - 1.2 && touchesFlesh(level, pos)) {
						set(level, pos, random.nextInt(14) == 0 ? Blocks.SHROOMLIGHT.defaultBlockState() : flesh);
					}
				}
			}
		}
		// Vines of blood drip from the underside.
		int vines = 2 + random.nextInt(5);
		for (int v = 0; v < vines; v++) {
			int vx = contact.getX() + random.nextInt(ir * 2 + 1) - ir;
			int vz = contact.getZ() + random.nextInt(ir * 2 + 1) - ir;
			int vy = contact.getY();
			pos.set(vx, vy, vz);
			if (!window.contains(pos)) {
				continue;
			}
			// Down to the bottom of the growth.
			while (pos.getY() > contact.getY() - ir - 2 && !level.getBlockState(pos).isAir()) {
				pos.move(Direction.DOWN);
			}
			if (!level.getBlockState(pos.above()).is(ModBlocks.FLESH_BLOCK) || !level.getBlockState(pos).isAir()) {
				continue;
			}
			int length = 2 + random.nextInt(6);
			int placed = 0;
			for (int i = 0; i < length && level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isAir(); i++) {
				set(level, pos, (i == length - 1 ? Blocks.WEEPING_VINES : Blocks.WEEPING_VINES_PLANT).defaultBlockState());
				pos.move(Direction.DOWN);
				placed++;
			}
			if (placed > 0 && !level.getBlockState(pos.above()).is(Blocks.WEEPING_VINES)) {
				set(level, pos.move(Direction.UP), Blocks.WEEPING_VINES.defaultBlockState());
			}
		}
		return true;
	}

	private static boolean touchesFlesh(WorldGenLevel level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (level.getBlockState(pos.relative(direction)).is(ModBlocks.FLESH_BLOCK)) {
				return true;
			}
		}
		return false;
	}

	// --- titan bones ---------------------------------------------------------------------

	/** The skeleton of something enormous that died here: a spine, ribs arching high, a skull. */
	private boolean titanBones(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 14)) {
			return false;
		}
		int ground = floorBelow(level, origin.getX(), origin.getY(), origin.getZ(), 24);
		if (ground == NONE || ground <= level.getSeaLevel()) {
			return false;
		}
		Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		Direction across = along.getClockWise();
		int length = 12 + random.nextInt(9);
		int sx = origin.getX() - along.getStepX() * length / 2;
		int sz = origin.getZ() - along.getStepZ() * length / 2;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int spineY = ground + 1;
		int placed = 0;
		for (int i = 0; i < length; i++) {
			int x = sx + along.getStepX() * i;
			int z = sz + along.getStepZ() * i;
			int y = floorBelow(level, x, spineY + 4, z, 10);
			if (y != NONE && Math.abs(y + 1 - spineY) <= 2) {
				spineY = y + 1;
			}
			if (fill(level, pos.set(x, spineY, z), bone(along.getAxis()))) {
				placed++;
			}
			// Ribs: a pair every third vertebra, arching up and out; they shrink towards the tail.
			if (i % 3 == 1 && i < length - 2) {
				double size = 1.0 - i / (double) length * 0.6;
				int reach = (int) Math.round(5 * size) + 1;
				int rise = (int) Math.round(7 * size) + 2;
				for (int s = -1; s <= 1; s += 2) {
					for (int t = 0; t <= 12; t++) {
						double a = Math.PI * t / 12.0;
						int ox = (int) Math.round(Math.sin(a * 0.5) * reach);
						int oy = (int) Math.round(Math.sin(a) * rise);
						pos.set(x + across.getStepX() * ox * s, spineY + oy, z + across.getStepZ() * ox * s);
						if (window.contains(pos) && fill(level, pos, bone(t > 3 && t < 9 ? across.getAxis() : Direction.Axis.Y))) {
							placed++;
						}
					}
				}
			}
		}
		// The skull, at the head end, its empty sockets lit from within.
		int hx = sx - along.getStepX() * 3;
		int hz = sz - along.getStepZ() * 3;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 0; dy <= 3; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (Math.abs(dx) + Math.abs(dz) + (dy == 3 ? 1 : 0) > 3) {
						continue;
					}
					pos.set(hx + dx, spineY + dy, hz + dz);
					if (window.contains(pos)) {
						fill(level, pos, Blocks.BONE_BLOCK.defaultBlockState());
					}
				}
			}
		}
		for (int s = -1; s <= 1; s += 2) {
			pos.set(hx - along.getStepX() * 2 + across.getStepX() * s, spineY + 2, hz - along.getStepZ() * 2 + across.getStepZ() * s);
			if (window.contains(pos)) {
				set(level, pos, Blocks.SHROOMLIGHT.defaultBlockState());
			}
		}
		return placed > 4;
	}

	// --- brimstone -----------------------------------------------------------------------

	/** A smouldering bed of brimstone, eternal fires dancing on it and ember crystals around it. */
	private boolean brimstoneDeposit(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 6)) {
			return false;
		}
		int ground = floorBelow(level, origin.getX(), origin.getY(), origin.getZ(), 24);
		if (ground == NONE || ground <= level.getSeaLevel()) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		double radius = 2.5 + random.nextDouble() * 3.0;
		int ir = (int) Math.ceil(radius);
		BlockState brimstone = ModBlocks.BRIMSTONE.defaultBlockState();
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dz = -ir; dz <= ir; dz++) {
				for (int dy = -3; dy <= 1; dy++) {
					double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 3.0) + random.nextDouble();
					if (d > radius) {
						continue;
					}
					pos.set(origin.getX() + dx, ground + dy, origin.getZ() + dz);
					BlockState state = level.getBlockState(pos);
					if (isRockMass(state) || dy == 1 && state.isAir() && d < radius - 1.5 && random.nextInt(3) == 0
							&& isRockMass(level.getBlockState(pos.below()))) {
						set(level, pos, brimstone);
					}
				}
			}
		}
		// Fire on the exposed brimstone never goes out; crystals grow at the edges.
		for (int i = 0; i < 10; i++) {
			int x = origin.getX() + random.nextInt(ir * 2 + 3) - ir - 1;
			int z = origin.getZ() + random.nextInt(ir * 2 + 3) - ir - 1;
			int y = floorBelow(level, x, ground + 3, z, 8);
			if (y == NONE || !level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
				continue;
			}
			if (level.getBlockState(pos.set(x, y, z)).is(ModBlocks.BRIMSTONE)) {
				if (random.nextInt(3) == 0) {
					set(level, pos.set(x, y + 1, z), Blocks.FIRE.defaultBlockState());
				}
			} else if (random.nextInt(2) == 0) {
				set(level, pos.set(x, y + 1, z), crystal(Direction.UP));
			}
		}
		return true;
	}

	private static BlockState crystal(Direction facing) {
		return ModBlocks.EMBER_CRYSTAL.defaultBlockState().setValue(AmethystClusterBlock.FACING, facing);
	}

	// --- ember crystals ------------------------------------------------------------------

	/** A cluster of ember crystals growing out of scorched rock, on a floor, wall or roof. */
	private boolean emberCrystals(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 5)) {
			return false;
		}
		Direction[] directions = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		Direction toward = directions[random.nextInt(directions.length)];
		BlockPos.MutableBlockPos pos = origin.mutable();
		BlockPos surface = null;
		for (int i = 0; i < 12; i++) {
			pos.move(toward);
			if (!window.contains(pos)) {
				return false;
			}
			BlockState state = level.getBlockState(pos);
			if (!state.isAir()) {
				if (!isRockMass(state)) {
					return false;
				}
				surface = pos.immutable();
				break;
			}
		}
		if (surface == null) {
			return false;
		}
		Direction facing = toward.getOpposite();
		int made = 0;
		for (int i = 0; i < 24; i++) {
			int dx = random.nextInt(5) - 2;
			int dy = random.nextInt(5) - 2;
			int dz = random.nextInt(5) - 2;
			BlockPos rock = surface.offset(facing.getAxis() == Direction.Axis.X ? 0 : dx, facing.getAxis() == Direction.Axis.Y ? 0 : dy,
					facing.getAxis() == Direction.Axis.Z ? 0 : dz);
			if (!window.contains(rock) || !isRockMass(level.getBlockState(rock))) {
				continue;
			}
			BlockPos spot = rock.relative(facing);
			if (!level.getBlockState(spot).isAir()) {
				continue;
			}
			set(level, rock, ModBlocks.SCORCHED_NETHERRACK.defaultBlockState());
			if (random.nextInt(3) != 0) {
				set(level, spot, crystal(facing));
				made++;
			}
		}
		return made > 0;
	}

	// --- little shrines ------------------------------------------------------------------

	/** A small ruined shrine of scorched brick where a brazier still burns among the skulls. */
	private boolean hellShrine(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 5)) {
			return false;
		}
		int x = origin.getX();
		int z = origin.getZ();
		int ground = floorBelow(level, x, origin.getY(), z, 24);
		if (ground == NONE || ground <= level.getSeaLevel()) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// It needs a fairly level floor and a little headroom.
		for (int dx = -2; dx <= 2; dx += 2) {
			for (int dz = -2; dz <= 2; dz += 2) {
				int y = floorBelow(level, x + dx, ground + 3, z + dz, 8);
				if (y == NONE || Math.abs(y - ground) > 2 || !open(level, pos, x + dx, ground + 1, z + dz, 4)) {
					return false;
				}
			}
		}
		boolean soul = random.nextInt(3) == 0;
		BlockState brick = soul ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : ModBlocks.SCORCHED_BRICKS.defaultBlockState();
		BlockState cracked = soul ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.NETHER_BRICKS.defaultBlockState();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				// The platform, on a footing that reaches the floor.
				for (int y = ground; y > ground - 4; y--) {
					BlockState state = level.getBlockState(pos.set(x + dx, y, z + dz));
					if (y < ground && isGround(level, pos, state)) {
						break;
					}
					set(level, pos, random.nextInt(5) == 0 ? cracked : brick);
				}
				for (int h = 1; h <= 4; h++) {
					pos.set(x + dx, ground + h, z + dz);
					if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).is(Blocks.FIRE)) {
						set(level, pos, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		// Four corner pillars, broken to different heights, skulls on the tall ones.
		for (int[] corner : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			int height = 1 + random.nextInt(4);
			for (int h = 1; h <= height; h++) {
				set(level, pos.set(x + corner[0], ground + h, z + corner[1]), random.nextInt(4) == 0 ? cracked : brick);
			}
			if (height >= 2 && random.nextBoolean()) {
				set(level, pos.set(x + corner[0], ground + height + 1, z + corner[1]), skull(random, 3));
			}
		}
		set(level, pos.set(x, ground + 1, z), (soul ? ModBlocks.SOUL_BRAZIER : ModBlocks.HELLFIRE_BRAZIER).defaultBlockState());
		if (random.nextInt(3) == 0) {
			Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			pos.set(x + side.getStepX(), ground + 1, z + side.getStepZ());
			set(level, pos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, side));
			if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
				chest.setLootTable(SHRINE_LOOT, random.nextLong());
			}
		}
		for (int i = 0; i < 3; i++) {
			int sx = x + random.nextInt(5) - 2;
			int sz = z + random.nextInt(5) - 2;
			fill(level, pos.set(sx, ground + 1, sz), skull(random, 5));
		}
		return true;
	}

	// --- bone spikes ---------------------------------------------------------------------

	/** A patch of the ground bristling with sharpened bones. */
	private boolean boneSpikes(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 5)) {
			return false;
		}
		int ground = floorBelow(level, origin.getX(), origin.getY(), origin.getZ(), 24);
		if (ground == NONE || ground <= level.getSeaLevel()) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState spikes = ModBlocks.BONE_SPIKES.defaultBlockState();
		int made = 0;
		for (int i = 0; i < 20; i++) {
			int x = origin.getX() + random.nextInt(9) - 4;
			int z = origin.getZ() + random.nextInt(9) - 4;
			int y = floorBelow(level, x, ground + 3, z, 8);
			if (y == NONE || !level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
				continue;
			}
			set(level, pos, random.nextInt(9) == 0 ? skull(random, 6) : random.nextInt(7) == 0 ? bone(Direction.Axis.Y) : spikes);
			made++;
		}
		return made > 0;
	}

	public enum Style implements StringRepresentable {
		SKULL_PIKES("skull_pikes"),
		CHARRED_TREE("charred_tree"),
		FLESH_GROWTH("flesh_growth"),
		TITAN_BONES("titan_bones"),
		BRIMSTONE_DEPOSIT("brimstone_deposit"),
		EMBER_CRYSTALS("ember_crystals"),
		HELL_SHRINE("hell_shrine"),
		BONE_SPIKES("bone_spikes");

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
