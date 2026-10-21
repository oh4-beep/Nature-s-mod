package com.naturesmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.naturesmod.NaturesMod;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Landmarks of the Nether: lavafalls pouring from cliffs and cavern roofs, fields of erupting
 * geysers, jagged ember spikes, giant glowstone chandeliers, weeping obsidian spires, and iron
 * cages dangling on chains over the abyss.
 */
public record NetherFeature(Style style) implements Feature {
	public static final MapCodec<NetherFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Style.CODEC.fieldOf("style").forGetter(NetherFeature::style)
	).apply(instance, NetherFeature::new));

	private static final ResourceKey<LootTable> CAGE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, NaturesMod.id("chests/nether_cage"));

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin).isAir()) {
			return false;
		}
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 1);
		boolean placed = switch (style) {
			case LAVAFALL -> lavafall(level, window, random, origin);
			case GEYSER_FIELD -> geyserField(level, window, random, origin);
			case EMBER_SPIKES -> emberSpikes(level, window, random, origin);
			case GLOWSTONE_CHANDELIER -> chandelier(level, window, random, origin);
			case OBSIDIAN_SPIRE -> obsidianSpire(level, window, random, origin);
			case HANGING_CAGE -> hangingCage(level, window, random, origin);
		};
		if (placed) {
			FeatureStats.placed("nether_" + style.getSerializedName(), origin);
		}
		return placed;
	}

	// --- helpers -------------------------------------------------------------------------

	private static boolean isRock(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && (state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.NYLIUM)
				|| state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.GRAVEL) || state.is(Blocks.MAGMA_BLOCK)
				|| state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.GILDED_BLACKSTONE) || state.is(Blocks.CRYING_OBSIDIAN)
				|| state.is(Blocks.NETHER_GOLD_ORE) || state.is(Blocks.NETHER_QUARTZ_ORE));
	}

	/** The first rock straight up or down from a position, or Integer.MIN_VALUE if none within reach. */
	private static int scan(WorldGenLevel level, BlockPos origin, Direction direction, int reach) {
		BlockPos.MutableBlockPos pos = origin.mutable();
		for (int i = 0; i < reach; i++) {
			pos.move(direction);
			if (pos.getY() <= level.getMinY() + 1 || pos.getY() >= level.getMaxY() - 1) {
				return Integer.MIN_VALUE;
			}
			BlockState state = level.getBlockState(pos);
			if (!state.isAir()) {
				return isRock(state) ? pos.getY() : Integer.MIN_VALUE;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, 2);
	}

	/** Places a block only where there is open air. */
	private static void fill(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (level.getBlockState(pos).isAir()) {
			level.setBlock(pos, state, 2);
		}
	}

	// --- lavafalls -----------------------------------------------------------------------

	private boolean lavafall(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		// Under the roof of a cavern, or out of the face of a cliff.
		if (random.nextBoolean()) {
			int roof = scan(level, origin, Direction.UP, 48);
			int floor = scan(level, origin, Direction.DOWN, 160);
			if (roof == Integer.MIN_VALUE || roof - Math.max(floor, level.getMinY()) < 18) {
				return false;
			}
			BlockPos source = new BlockPos(origin.getX(), roof, origin.getZ());
			return pour(level, window, source, null);
		}
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockPos.MutableBlockPos pos = origin.mutable();
		for (int i = 0; i < 12; i++) {
			pos.move(facing);
			if (!window.contains(pos)) {
				return false;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (!isRock(state)) {
				return false;
			}
			// pos is the wall: the lava comes out towards the open side, over a long drop.
			BlockPos open = pos.relative(facing.getOpposite());
			int floor = scan(level, open, Direction.DOWN, 160);
			if (open.getY() - Math.max(floor, level.getMinY()) < 14) {
				return false;
			}
			return pour(level, window, pos.immutable(), facing.getOpposite());
		}
		return false;
	}

	/** Replaces a rock block with a lava source, sealing it so the lava only escapes one way. */
	private static boolean pour(WorldGenLevel level, WorldGenUtil.Window window, BlockPos source, Direction open) {
		if (!window.containsRadius(source.getX(), source.getZ(), 2)) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			if (direction == open || direction == Direction.DOWN && open == null) {
				continue;
			}
			BlockPos side = source.relative(direction);
			if (!isRock(level.getBlockState(side))) {
				set(level, side, Blocks.BASALT.defaultBlockState());
			}
		}
		set(level, source, Blocks.LAVA.defaultBlockState());
		level.scheduleTick(source, Fluids.LAVA, 0);
		return true;
	}

	// --- geysers -------------------------------------------------------------------------

	private boolean geyserField(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 6)) {
			return false;
		}
		int ground = scan(level, origin, Direction.DOWN, 24);
		if (ground == Integer.MIN_VALUE || ground <= level.getSeaLevel()) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState geyser = ModBlocks.NETHER_GEYSER.defaultBlockState();
		int vents = 2 + random.nextInt(4);
		int made = 0;
		for (int i = 0; i < vents * 3 && made < vents; i++) {
			int x = origin.getX() + random.nextInt(11) - 5;
			int z = origin.getZ() + random.nextInt(11) - 5;
			int y = scan(level, new BlockPos(x, ground + 4, z), Direction.DOWN, 10);
			if (y == Integer.MIN_VALUE || !level.getBlockState(pos.set(x, y + 1, z)).isAir() || !level.getBlockState(pos.set(x, y + 2, z)).isAir()) {
				continue;
			}
			set(level, pos.set(x, y, z), geyser);
			// A cone of basalt and seething magma around each vent.
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					int d2 = dx * dx + dz * dz;
					if (d2 == 0 || d2 > 5) {
						continue;
					}
					int gx = x + dx;
					int gz = z + dz;
					int gy = scan(level, new BlockPos(gx, y + 3, gz), Direction.DOWN, 8);
					if (gy == Integer.MIN_VALUE) {
						continue;
					}
					if (d2 <= 2) {
						set(level, pos.set(gx, gy, gz), (random.nextInt(3) == 0 ? Blocks.MAGMA_BLOCK : Blocks.BASALT).defaultBlockState());
						if (random.nextInt(3) == 0) {
							fill(level, pos.set(gx, gy + 1, gz), Blocks.BASALT.defaultBlockState());
						}
					} else if (random.nextInt(2) == 0) {
						set(level, pos.set(gx, gy, gz), Blocks.MAGMA_BLOCK.defaultBlockState());
					}
				}
			}
			made++;
		}
		return made > 0;
	}

	// --- ember spikes --------------------------------------------------------------------

	private boolean emberSpikes(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 5)) {
			return false;
		}
		int ground = scan(level, origin, Direction.DOWN, 24);
		if (ground == Integer.MIN_VALUE || ground <= level.getSeaLevel()) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int spikes = 1 + random.nextInt(3);
		for (int s = 0; s < spikes; s++) {
			int x = origin.getX() + random.nextInt(7) - 3;
			int z = origin.getZ() + random.nextInt(7) - 3;
			int base = scan(level, new BlockPos(x, ground + 6, z), Direction.DOWN, 14);
			if (base == Integer.MIN_VALUE) {
				continue;
			}
			int height = 4 + random.nextInt(12);
			double radius = 1.2 + random.nextDouble() * 1.4;
			double leanX = (random.nextDouble() - 0.5) * 0.5;
			double leanZ = (random.nextDouble() - 0.5) * 0.5;
			for (int h = 0; h <= height; h++) {
				double r = radius * (1.0 - h / (double) (height + 1));
				int cx = Mth.floor(x + 0.5 + leanX * h);
				int cz = Mth.floor(z + 0.5 + leanZ * h);
				int ir = (int) Math.ceil(r);
				for (int dx = -ir; dx <= ir; dx++) {
					for (int dz = -ir; dz <= ir; dz++) {
						if (dx * dx + dz * dz > r * r + 0.2) {
							continue;
						}
						pos.set(cx + dx, base + 1 + h, cz + dz);
						if (!window.contains(pos)) {
							continue;
						}
						Block rock = random.nextInt(9) == 0 ? Blocks.MAGMA_BLOCK : random.nextInt(3) == 0 ? Blocks.BASALT : Blocks.BLACKSTONE;
						BlockState state = rock.defaultBlockState();
						if (state.hasProperty(RotatedPillarBlock.AXIS)) {
							state = state.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
						}
						fill(level, pos, state);
					}
				}
			}
		}
		return true;
	}

	// --- glowstone chandeliers -----------------------------------------------------------

	private boolean chandelier(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 6)) {
			return false;
		}
		int roof = scan(level, origin, Direction.UP, 64);
		if (roof == Integer.MIN_VALUE) {
			return false;
		}
		int floor = scan(level, new BlockPos(origin.getX(), roof - 1, origin.getZ()), Direction.DOWN, 200);
		int drop = roof - Math.max(floor, level.getMinY());
		if (drop < 20) {
			return false;
		}
		// A stalactite of rock, thick at the roof.
		int length = 4 + random.nextInt(Math.min(18, drop / 3));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int x = origin.getX();
		int z = origin.getZ();
		for (int h = 0; h < length; h++) {
			double r = 2.2 * (1.0 - h / (double) length) + 0.6;
			int ir = (int) Math.ceil(r);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					if (dx * dx + dz * dz <= r * r) {
						fill(level, pos.set(x + dx, roof - 1 - h, z + dz), (h % 5 == 3 ? Blocks.BLACKSTONE : Blocks.NETHERRACK).defaultBlockState());
					}
				}
			}
		}
		// The chandelier: a great ball of glowstone with shroomlight and dangling arms.
		int cy = roof - 1 - length - 2;
		double radius = 2.0 + random.nextDouble() * 2.0;
		int ir = (int) Math.ceil(radius) + 1;
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dy = -ir; dy <= ir; dy++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy * 1.3 + dz * dz) + random.nextDouble() * 0.8;
					if (d > radius) {
						continue;
					}
					pos.set(x + dx, cy + dy, z + dz);
					if (window.contains(pos)) {
						fill(level, pos, (random.nextInt(7) == 0 ? Blocks.SHROOMLIGHT : Blocks.GLOWSTONE).defaultBlockState());
					}
				}
			}
		}
		int arms = 4 + random.nextInt(4);
		for (int a = 0; a < arms; a++) {
			int ax = x + random.nextInt(ir * 2 + 1) - ir;
			int az = z + random.nextInt(ir * 2 + 1) - ir;
			int armLength = 2 + random.nextInt(6);
			for (int h = 0; h < armLength; h++) {
				pos.set(ax, cy - (int) radius + 1 - h, az);
				if (window.contains(pos)) {
					fill(level, pos, Blocks.GLOWSTONE.defaultBlockState());
				}
			}
		}
		return true;
	}

	// --- obsidian spires -----------------------------------------------------------------

	private boolean obsidianSpire(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 6)) {
			return false;
		}
		int ground = scan(level, origin, Direction.DOWN, 40);
		if (ground == Integer.MIN_VALUE || ground <= level.getSeaLevel()) {
			return false;
		}
		int roof = scan(level, new BlockPos(origin.getX(), ground + 1, origin.getZ()), Direction.UP, 120);
		int room = roof == Integer.MIN_VALUE ? 60 : roof - ground - 3;
		if (room < 10) {
			return false;
		}
		int height = Math.min(room, 12 + random.nextInt(34));
		double base = 2.2 + random.nextDouble() * 1.6;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		double leanX = (random.nextDouble() - 0.5) * 0.12;
		double leanZ = (random.nextDouble() - 0.5) * 0.12;
		int x = origin.getX();
		int z = origin.getZ();
		for (int h = -2; h <= height; h++) {
			double f = Math.max(0, h) / (double) height;
			double r = base * Math.pow(1.0 - f, 0.8) + 0.4;
			double cx = x + 0.5 + leanX * h;
			double cz = z + 0.5 + leanZ * h;
			int ir = (int) Math.ceil(r);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					int bx = Mth.floor(cx) + dx;
					int bz = Mth.floor(cz) + dz;
					double d = Math.sqrt(Mth.square(bx + 0.5 - cx) + Mth.square(bz + 0.5 - cz));
					if (d > r) {
						continue;
					}
					pos.set(bx, ground + h, bz);
					if (!window.contains(pos)) {
						continue;
					}
					int roll = random.nextInt(100);
					Block block = roll < 18 ? Blocks.CRYING_OBSIDIAN : roll < 70 ? Blocks.OBSIDIAN : Blocks.BLACKSTONE;
					set(level, pos, block.defaultBlockState());
				}
			}
		}
		// Soul fire burns on the tip.
		int tipX = Mth.floor(x + 0.5 + leanX * height);
		int tipZ = Mth.floor(z + 0.5 + leanZ * height);
		pos.set(tipX, ground + height + 1, tipZ);
		if (window.contains(pos) && level.getBlockState(pos).isAir()) {
			set(level, pos.move(Direction.DOWN), Blocks.SOUL_SAND.defaultBlockState());
			set(level, pos.move(Direction.UP), Blocks.SOUL_FIRE.defaultBlockState());
		}
		return true;
	}

	// --- hanging cages -------------------------------------------------------------------

	private boolean hangingCage(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 3)) {
			return false;
		}
		int roof = scan(level, origin, Direction.UP, 40);
		if (roof == Integer.MIN_VALUE) {
			return false;
		}
		int chain = 3 + random.nextInt(9);
		int top = roof - 1 - chain;
		int floor = scan(level, new BlockPos(origin.getX(), top, origin.getZ()), Direction.DOWN, 64);
		if (top - Math.max(floor, level.getMinY()) < 10) {
			return false;
		}
		int x = origin.getX();
		int z = origin.getZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// The cage must hang in open air.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = -4; dy <= 0; dy++) {
					if (!level.getBlockState(pos.set(x + dx, top + dy, z + dz)).isAir()) {
						return false;
					}
				}
			}
		}
		BlockState chainState = Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		for (int h = 0; h < chain; h++) {
			set(level, pos.set(x, roof - 1 - h, z), chainState);
		}
		BlockState plate = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				set(level, pos.set(x + dx, top, z + dz), plate);
				set(level, pos.set(x + dx, top - 4, z + dz), plate);
				if (dx == 0 && dz == 0) {
					continue;
				}
				// Iron bars all round, joined to their neighbours in the ring.
				BlockState bars = Blocks.IRON_BARS.defaultBlockState()
						.setValue(BlockStateProperties.NORTH, dz >= 0 && !(dx == 0 && dz == 1))
						.setValue(BlockStateProperties.SOUTH, dz <= 0 && !(dx == 0 && dz == -1))
						.setValue(BlockStateProperties.WEST, dx >= 0 && !(dz == 0 && dx == 1))
						.setValue(BlockStateProperties.EAST, dx <= 0 && !(dz == 0 && dx == -1));
				for (int dy = -3; dy <= -1; dy++) {
					set(level, pos.set(x + dx, top + dy, z + dz), bars);
				}
			}
		}
		// Whoever was locked in here never got out, but their belongings are still here.
		int skull = top - 3;
		if (random.nextInt(2) == 0) {
			set(level, pos.set(x, top - 3, z), Blocks.CHEST.defaultBlockState());
			if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
				chest.setLootTable(CAGE_LOOT, random.nextLong());
			}
			skull = top - 2;
		}
		set(level, pos.set(x, skull, z), Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, random.nextInt(16)));
		set(level, pos.set(x, top - 5, z), Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
		return true;
	}

	public enum Style implements StringRepresentable {
		LAVAFALL("lavafall"),
		GEYSER_FIELD("geyser_field"),
		EMBER_SPIKES("ember_spikes"),
		GLOWSTONE_CHANDELIER("glowstone_chandelier"),
		OBSIDIAN_SPIRE("obsidian_spire"),
		HANGING_CAGE("hanging_cage");

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
