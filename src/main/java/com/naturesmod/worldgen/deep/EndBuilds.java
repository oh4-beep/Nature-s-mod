package com.naturesmod.worldgen.deep;

import static com.naturesmod.worldgen.deep.DeepBuilder.axis;
import static com.naturesmod.worldgen.deep.DeepBuilder.facing;
import static com.naturesmod.worldgen.deep.DeepBuilder.head;
import static com.naturesmod.worldgen.deep.DeepBuilder.joined;
import static com.naturesmod.worldgen.deep.DeepBuilder.lantern;
import static com.naturesmod.worldgen.deep.DeepBuilder.lit;
import static com.naturesmod.worldgen.deep.DeepBuilder.s;
import static com.naturesmod.worldgen.deep.DeepBuilder.slab;
import static com.naturesmod.worldgen.deep.DeepBuilder.stairs;
import static com.naturesmod.worldgen.deep.DeepBuilder.vanilla;

import com.naturesmod.block.ModBlocks;
import com.naturesmod.item.ModItems;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The structures of the new End, left by the star-folk who lived out here before the void
 * swallowed them: shrines, obelisks and nests out on the continents, their libraries, temples,
 * forges and towers, and the three great wonders, where the Star Altar waits. They are drawn like
 * the treasures of the deep (see {@link DeepBuilder}), in the open air.
 */
final class EndBuilds {
	private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private EndBuilds() {
	}

	// --- blocks --------------------------------------------------------------------------------

	private static BlockState rod(Direction facing) {
		return s(Blocks.END_ROD).setValue(BlockStateProperties.FACING, facing);
	}

	private static BlockState candles(int count) {
		return s(vanilla("purple_candle")).setValue(BlockStateProperties.CANDLES, count).setValue(BlockStateProperties.LIT, true);
	}

	private static BlockState pane(boolean alongX) {
		return joined(vanilla("magenta_stained_glass_pane"), alongX);
	}

	private static BlockState chain() {
		return axis(Blocks.IRON_CHAIN, Direction.Axis.Y);
	}

	private static BlockState pillar() {
		return axis(Blocks.PURPUR_PILLAR, Direction.Axis.Y);
	}

	private static BlockState cluster(Direction facing) {
		return s(ModBlocks.VOID_CRYSTAL_CLUSTER).setValue(BlockStateProperties.FACING, facing);
	}

	private static BlockState portalFrame(Direction facing, boolean eye) {
		return s(Blocks.END_PORTAL_FRAME).setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.EYE, eye);
	}

	/** End stone bricks, with a plain block of end stone here and there. */
	private static DeepBuilder.Paint endBricks(DeepBuilder b) {
		return b.worn(s(Blocks.END_STONE_BRICKS), s(Blocks.END_STONE), s(Blocks.END_STONE_BRICKS));
	}

	private static DeepBuilder.Paint purpur(DeepBuilder b) {
		return b.worn(s(Blocks.PURPUR_BLOCK), s(Blocks.PURPUR_BLOCK), pillar());
	}

	private static DeepBuilder.Paint voidBricks(DeepBuilder b) {
		return b.worn(s(ModBlocks.VOIDSTONE_BRICKS), s(ModBlocks.POLISHED_VOIDSTONE), s(ModBlocks.VOIDSTONE));
	}

	/** The ground round a structure: end stone with moss in it. */
	private static DeepBuilder.Paint ground(DeepBuilder b) {
		return b.mix(s(Blocks.END_STONE), s(Blocks.END_STONE), s(ModBlocks.END_MOSS));
	}

	private static int ringX(int i, int count, double radius) {
		return (int) Math.round(Math.cos(i * Math.PI * 2.0 / count) * radius);
	}

	private static int ringZ(int i, int count, double radius) {
		return (int) Math.round(Math.sin(i * Math.PI * 2.0 / count) * radius);
	}

	/** A little chorus tree: a trunk of chorus log under a crown of glowing bulbs, hung with void vines. */
	private static void chorusTree(DeepBuilder b, int x, int z, int y0, int height) {
		b.pillar(x, z, y0, y0 + height - 1, axis(ModBlocks.CHORUS_LOG, Direction.Axis.Y));
		int top = y0 + height;
		b.set(x, top, z, ModBlocks.CHORUS_BULB);
		for (Direction side : SIDES) {
			b.set(x + side.getStepX(), top - 1, z + side.getStepZ(), ModBlocks.CHORUS_BULB);
			b.set(x + side.getStepX(), top - 2, z + side.getStepZ(), ModBlocks.VOID_VINES);
		}
	}

	// --- outposts ------------------------------------------------------------------------------

	/** A ring of purpur pillars round an altar, where the endermen still leave their offerings. */
	static void enderShrine(DeepBuilder b) {
		b.padRound(0, 0, 7.5, ground(b), s(Blocks.END_STONE), 12);
		b.disc(0, 0, 0, 6.5, endBricks(b));
		b.disc(0, 0, 0, 3.5, s(ModBlocks.POLISHED_VOIDSTONE));
		int[] heights = new int[8];
		for (int i = 0; i < 8; i++) {
			heights[i] = b.layout.nextInt(4) == 0 ? 1 + b.layout.nextInt(3) : 5;
		}
		// Lintels between the pillars still standing...
		for (int i = 0; i < 8; i++) {
			int next = (i + 1) % 8;
			if (heights[i] == 5 && heights[next] == 5) {
				b.line(ringX(i, 8, 5.5), 6, ringZ(i, 8, 5.5), ringX(next, 8, 5.5), 6, ringZ(next, 8, 5.5), slab(Blocks.PURPUR_SLAB, false));
			}
		}
		// ...and the pillars, each crowned with a rod, or broken off with its top lying beside it.
		for (int i = 0; i < 8; i++) {
			int x = ringX(i, 8, 5.5);
			int z = ringZ(i, 8, 5.5);
			b.pillar(x, z, 1, heights[i], pillar());
			if (heights[i] == 5) {
				b.set(x, 6, z, Blocks.PURPUR_BLOCK);
				b.set(x, 7, z, rod(Direction.UP));
			} else {
				int ox = Math.abs(x) >= Math.abs(z) ? -Integer.signum(x) : 0;
				int oz = ox == 0 ? -Integer.signum(z) : 0;
				int length = 4 - heights[i];
				b.line(x + ox, 1, z + oz, x + ox * length, 1, z + oz * length, axis(Blocks.PURPUR_PILLAR, ox != 0 ? Direction.Axis.X : Direction.Axis.Z));
			}
		}
		// The altar, with its candles and a dragon's head.
		b.fill(-1, 1, -1, 1, 1, 1, s(ModBlocks.POLISHED_VOIDSTONE));
		b.set(0, 2, 0, Blocks.CRYING_OBSIDIAN);
		b.set(0, 3, 0, head(Blocks.DRAGON_HEAD, b.layout.nextInt(16)));
		b.set(-1, 2, -1, candles(3));
		b.set(1, 2, -1, candles(2));
		b.set(-1, 2, 1, candles(4));
		b.set(1, 2, 1, candles(1));
		for (Direction side : new Direction[] {Direction.EAST, Direction.WEST, Direction.SOUTH}) {
			b.set(side.getStepX() * 2, 1, side.getStepZ() * 2, portalFrame(side.getOpposite(), b.layout.nextInt(3) == 0));
		}
		b.chest(0, 1, -2, Direction.NORTH, "outpost");
		if (b.layout.nextBoolean()) {
			b.keeper(3, 1, 3, EntityTypes.ENDERMAN);
		}
	}

	/** A needle of obsidian crowned with void crystal; an offering waits in a niche at its foot. */
	static void obsidianObelisk(DeepBuilder b) {
		DeepBuilder.Paint obsidian = (x, y, z) -> b.chance(x, y, z, 11, 0.12) ? s(Blocks.CRYING_OBSIDIAN) : s(Blocks.OBSIDIAN);
		b.padRound(0, 0, 6.5, ground(b), s(Blocks.END_STONE), 22);
		b.disc(0, 0, 0, 5.5, voidBricks(b));
		// The plinth and the steps up to it on every side.
		b.fill(-3, 1, -3, 3, 1, 3, s(ModBlocks.POLISHED_VOIDSTONE));
		for (int i = -3; i <= 3; i++) {
			b.set(i, 1, -4, stairs(Blocks.END_STONE_BRICK_STAIRS, Direction.SOUTH, false));
			b.set(i, 1, 4, stairs(Blocks.END_STONE_BRICK_STAIRS, Direction.NORTH, false));
			b.set(-4, 1, i, stairs(Blocks.END_STONE_BRICK_STAIRS, Direction.EAST, false));
			b.set(4, 1, i, stairs(Blocks.END_STONE_BRICK_STAIRS, Direction.WEST, false));
		}
		// The needle: broad at its foot, then a single column up to its crown.
		b.fill(-1, 2, -1, 1, 10, 1, obsidian);
		b.pillar(0, 0, 11, 18, obsidian);
		b.set(0, 19, 0, ModBlocks.VOID_CRYSTAL);
		b.set(0, 20, 0, rod(Direction.UP));
		for (Direction side : SIDES) {
			b.set(side.getStepX(), 19, side.getStepZ(), cluster(side));
			b.set(side.getStepX(), 11, side.getStepZ(), stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, side.getOpposite(), false));
		}
		// The niche, lit by a rod either side.
		b.clear(0, 2, -1, 0, 3, -1);
		b.chest(0, 2, -1, Direction.NORTH, "outpost");
		b.set(-2, 2, -2, rod(Direction.UP));
		b.set(2, 2, -2, rod(Direction.UP));
		b.set(-3, 2, 3, candles(2));
		b.set(3, 2, -3, candles(3));
	}

	/**
	 * A well with no water at the top: it drops into a hidden chamber, onto a pool that breaks the
	 * fall, and an updraft in the corner of the chamber carries you back up again.
	 */
	static void voidWell(DeepBuilder b) {
		b.padRound(0, 0, 6.5, ground(b), s(Blocks.END_STONE), 8);
		b.disc(0, 0, 0, 5.5, endBricks(b));
		// The chamber below, with the pool in the middle of its floor.
		b.fill(-4, -16, -4, 4, -10, 4, voidBricks(b));
		b.clear(-3, -14, -3, 3, -11, 3);
		b.fill(-1, -15, -1, 1, -15, 1, s(Blocks.WATER));
		for (Direction side : SIDES) {
			b.set(side.getStepX() * 4, -12, side.getStepZ() * 4, ModBlocks.VOID_CRYSTAL);
		}
		// The shaft, lined with voidstone, and the rim of the well round its mouth.
		b.cylinder(0, 0, 2.5, -10, 0, voidBricks(b), true);
		b.clear(-1, -14, -1, 1, 4, 1);
		b.cylinder(0, 0, 2.5, 1, 1, s(ModBlocks.VOIDSTONE_BRICKS), true);
		b.pillar(-2, 0, 2, 4, pillar());
		b.pillar(2, 0, 2, 4, pillar());
		b.fill(-2, 5, 0, 2, 5, 0, slab(Blocks.PURPUR_SLAB, false));
		b.set(0, 4, 0, lantern(true, true));
		// The way back up: an updraft in the corner of the chamber, under a shaft of its own.
		b.set(3, -15, 3, ModBlocks.UPDRAFT_VENT);
		b.clear(3, -14, 3, 3, 0, 3);
		b.set(4, 1, 3, rod(Direction.UP));
		b.set(3, 1, 4, rod(Direction.UP));
		// What the well keeps.
		b.chest(-3, -14, 0, Direction.EAST, "outpost");
		b.set(-3, -14, -2, candles(4));
		b.set(-3, -14, 2, candles(3));
		b.set(0, -11, -3, rod(Direction.DOWN));
	}

	/** Spikes of void crystal leaning out round a pedestal, glittering over a floor of stardust. */
	static void crystalShrine(DeepBuilder b) {
		b.padRound(0, 0, 7.5, b.mix(s(ModBlocks.STARDUST), s(Blocks.END_STONE), s(ModBlocks.STARDUST)), s(Blocks.END_STONE), 12);
		b.disc(0, 0, 0, 2.5, s(ModBlocks.POLISHED_VOIDSTONE));
		for (int i = 0; i < 7; i++) {
			double angle = i * Math.PI * 2.0 / 7.0 + b.layout.nextDouble() * 0.4;
			double lean = 0.2 + b.layout.nextDouble() * 0.3;
			int height = 4 + b.layout.nextInt(4);
			int x0 = (int) Math.round(Math.cos(angle) * 5.0);
			int z0 = (int) Math.round(Math.sin(angle) * 5.0);
			int x1 = (int) Math.round(Math.cos(angle) * (5.0 + height * lean));
			int z1 = (int) Math.round(Math.sin(angle) * (5.0 + height * lean));
			b.line(x0, 0, z0, x1, height, z1, s(ModBlocks.VOID_CRYSTAL));
			b.line(x0, 0, z0 + 1, x1, height - 2, z1, s(ModBlocks.VOID_CRYSTAL));
			b.set(x1, height + 1, z1, cluster(Direction.UP));
		}
		// The pedestal, with a starmetal heart.
		b.set(0, 1, 0, ModBlocks.POLISHED_VOIDSTONE);
		b.set(0, 2, 0, ModBlocks.STARMETAL_BLOCK);
		b.set(0, 3, 0, cluster(Direction.UP));
		for (Direction side : SIDES) {
			b.set(side.getStepX() * 2, 1, side.getStepZ() * 2, ModBlocks.VOID_CRYSTAL);
			b.set(side.getStepX() * 2, 2, side.getStepZ() * 2, cluster(Direction.UP));
		}
		b.chest(1, 1, -1, Direction.NORTH, "outpost");
		if (b.layout.nextBoolean()) {
			b.keeper(-3, 1, 2, "crystal_golem");
		}
	}

	private static int craterDepth(double d, double radius) {
		return (int) Math.round(4.0 * (1.0 - d * d / (radius * radius)));
	}

	/** The crater of a fallen star, its heart of meteorite still shot through with starmetal. */
	static void fallenStar(DeepBuilder b) {
		double radius = 8.0;
		for (int x = -10; x <= 10; x++) {
			for (int z = -10; z <= 10; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d <= radius) {
					int depth = craterDepth(d, radius);
					b.clear(x, 1 - depth, z, x, 10, z);
					b.set(x, -depth, z, d < 3.5 ? s(ModBlocks.METEORITE) : b.chance(x, 0, z, 3, 0.7) ? s(ModBlocks.STARDUST) : s(Blocks.END_STONE));
				} else if (d <= 9.6) {
					// The rim the impact threw up round it.
					b.foundation(x, d <= 8.8 ? 2 : 1, z, b.chance(x, 1, z, 4, 0.5) ? s(ModBlocks.STARDUST) : s(Blocks.END_STONE));
				}
			}
		}
		// The star itself, half buried, with rays of light still shooting out of it.
		b.sphere(0, -3, 0, 2.3, (x, y, z) -> b.chance(x, y, z, 5, 0.3) ? s(ModBlocks.STARMETAL_ORE) : s(ModBlocks.METEORITE), false, -3);
		b.set(0, -1, 0, ModBlocks.STARMETAL_BLOCK);
		b.set(0, 0, 0, rod(Direction.UP));
		b.set(0, 1, 0, rod(Direction.UP));
		b.set(1, -1, 1, rod(Direction.UP));
		b.set(-1, -1, -1, rod(Direction.UP));
		// Shards of it thrown out over the crater.
		for (int i = 0; i < 7; i++) {
			int x = b.layout.nextInt(13) - 6;
			int z = b.layout.nextInt(13) - 6;
			boolean crystal = b.layout.nextBoolean();
			if (x * x + z * z < 12) {
				continue;
			}
			int floor = -craterDepth(Math.sqrt(x * x + z * z), radius);
			b.set(x, floor + 1, z, crystal ? s(ModBlocks.VOID_CRYSTAL) : s(ModBlocks.METEORITE));
			if (crystal) {
				b.set(x, floor + 2, z, cluster(Direction.UP));
			}
		}
		b.chest(3, 1 - craterDepth(3.0, radius), 0, Direction.WEST, "star");
	}

	/** A little purpur tower where shulkers have nested among the things some traveller left behind. */
	static void shulkerNest(DeepBuilder b) {
		b.pad(-6, -6, 6, 6, ground(b), s(Blocks.END_STONE), 14);
		b.shell(-3, 0, -3, 3, 10, 3, purpur(b));
		b.fill(-2, 0, -2, 2, 0, 2, s(Blocks.PURPUR_BLOCK));
		b.fill(-2, 5, -2, 2, 5, 2, s(Blocks.PURPUR_BLOCK));
		b.clear(0, 1, -3, 0, 2, -3);
		// The ladder up through the floor.
		for (int y = 1; y <= 6; y++) {
			b.set(2, y, 2, facing(Blocks.LADDER, Direction.WEST));
		}
		// Corner pillars, a roof of slabs and rods.
		for (int[] corner : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			b.pillar(corner[0], corner[1], 1, 10, pillar());
			b.set(corner[0], 11, corner[1], rod(Direction.UP));
		}
		b.fill(-2, 11, -2, 2, 11, 2, slab(Blocks.PURPUR_SLAB, false));
		b.set(-3, 3, 0, pane(false));
		b.set(3, 3, 0, pane(false));
		b.set(-3, 7, 0, pane(false));
		b.set(3, 7, 0, pane(false));
		b.set(0, 7, -3, pane(true));
		b.set(0, 7, 3, pane(true));
		// What was left behind, and who keeps it now.
		b.chest(-2, 6, 2, Direction.EAST, "nest");
		b.shulkerBox(-2, 1, 2, Direction.UP, "nest");
		b.set(-2, 1, -2, facing(Blocks.ENDER_CHEST, Direction.EAST));
		b.set(1, 6, -2, Blocks.BREWING_STAND);
		b.set(0, 4, 0, rod(Direction.DOWN));
		b.set(0, 9, 0, lantern(true, true));
		b.keeper(0, 6, 0, EntityTypes.SHULKER);
		b.keeper(-1, 1, 0, EntityTypes.SHULKER);
	}

	// --- halls ---------------------------------------------------------------------------------

	/** A hall of books under purpur vaults, with a gallery above and an enchanting nook at its end. */
	static void endLibrary(DeepBuilder b) {
		DeepBuilder.Paint wall = purpur(b);
		b.pad(-13, -11, 13, 11, endBricks(b), s(Blocks.END_STONE), 16);
		b.shell(-11, 0, -9, 11, 12, 9, wall);
		b.fill(-10, 0, -8, 10, 0, 8, (x, y, z) -> ((x + z) & 1) == 0 ? s(ModBlocks.POLISHED_VOIDSTONE) : s(Blocks.PURPUR_BLOCK));
		b.fill(-1, 1, -8, 1, 1, 3, s(vanilla("purple_carpet")));
		// The door, between two pillars.
		b.clear(-1, 1, -9, 1, 4, -9);
		for (int x = -2; x <= 2; x += 4) {
			b.pillar(x, -10, 1, 5, pillar());
			b.set(x, 6, -10, rod(Direction.UP));
		}
		// Pillars down the long walls, and tall windows between them.
		for (int x : new int[] {-9, -6, -3, 3, 6, 9}) {
			b.pillar(x, -9, 1, 11, pillar());
			b.pillar(x, 9, 1, 11, pillar());
		}
		for (int x : new int[] {-8, -7, -5, -4, 4, 5, 7, 8}) {
			b.fill(x, 3, -9, x, 7, -9, pane(true));
			b.fill(x, 3, 9, x, 7, 9, pane(true));
		}
		// A skylight of stained glass.
		b.fill(-6, 12, -2, 6, 12, 2, s(vanilla("magenta_stained_glass")));
		// Rows of shelves either side of the aisle, with chiseled shelves at their ends.
		for (int z : new int[] {-6, -3, 0, 3}) {
			for (int side = -1; side <= 1; side += 2) {
				b.fill(side * 4, 1, z, side * 9, 3, z, Blocks.BOOKSHELF);
				b.fill(side * 3, 1, z, side * 3, 3, z, facing(Blocks.CHISELED_BOOKSHELF, side < 0 ? Direction.EAST : Direction.WEST));
			}
		}
		// The enchanting nook at the far end.
		b.set(0, 1, 6, Blocks.ENCHANTING_TABLE);
		for (int y = 1; y <= 2; y++) {
			b.fill(-2, y, 8, 2, y, 8, Blocks.BOOKSHELF);
			b.fill(-2, y, 5, -2, y, 7, Blocks.BOOKSHELF);
			b.fill(2, y, 5, 2, y, 7, Blocks.BOOKSHELF);
		}
		b.set(-4, 1, 6, facing(Blocks.LECTERN, Direction.EAST));
		b.set(4, 1, 6, facing(Blocks.LECTERN, Direction.WEST));
		// A gallery along both ends of the hall, reached by ladders.
		for (int side = -1; side <= 1; side += 2) {
			b.fill(side * 8, 6, -8, side * 10, 6, 8, slab(Blocks.PURPUR_SLAB, true));
			for (int z = -8; z <= 8; z += 2) {
				b.set(side * 7, 6, z, s(Blocks.PURPUR_BLOCK));
				b.set(side * 7, 7, z, rod(Direction.UP));
			}
			for (int y = 1; y <= 7; y++) {
				b.set(side * 10, y, 0, facing(Blocks.LADDER, side < 0 ? Direction.EAST : Direction.WEST));
			}
			b.fill(side * 10, 7, -7, side * 10, 9, -5, Blocks.BOOKSHELF);
			b.fill(side * 10, 7, 5, side * 10, 9, 7, Blocks.BOOKSHELF);
		}
		b.chest(-9, 7, 6, Direction.EAST, "library");
		b.chest(9, 7, -6, Direction.WEST, "library");
		b.chest(0, 1, 4, Direction.NORTH, "library");
		b.barrel(-10, 1, -8, Direction.UP, "hall");
		// Lights: rods hanging from the vault, and a crystal chandelier over the aisle.
		for (int x = -8; x <= 8; x += 4) {
			for (int z = -6; z <= 6; z += 6) {
				b.set(x, 11, z, rod(Direction.DOWN));
			}
		}
		b.pillar(0, 0, 9, 11, chain());
		b.set(0, 8, 0, ModBlocks.VOID_CRYSTAL);
		for (Direction side : SIDES) {
			b.set(side.getStepX(), 8, side.getStepZ(), rod(side));
		}
		b.set(0, 7, 0, cluster(Direction.DOWN));
	}

	/** A round tower of end stone under a dome of glass, where the star-folk watched their sky. */
	static void starObservatory(DeepBuilder b) {
		b.padRound(0, 0, 10.5, ground(b), s(Blocks.END_STONE), 34);
		b.disc(0, 0, 0, 8.5, endBricks(b));
		b.cylinder(0, 0, 6.5, 1, 20, endBricks(b), true);
		b.disc(0, 0, 0, 5.5, s(ModBlocks.POLISHED_VOIDSTONE));
		b.disc(0, 10, 0, 5.5, s(Blocks.PURPUR_BLOCK));
		b.disc(0, 20, 0, 5.5, s(Blocks.PURPUR_BLOCK));
		b.cylinder(0, 0, 7.0, 10, 10, s(Blocks.PURPUR_BLOCK), true);
		b.cylinder(0, 0, 7.0, 20, 20, s(Blocks.PURPUR_BLOCK), true);
		// The dome, mostly glass.
		b.sphere(0, 21, 0, 7.0, (x, y, z) -> b.chance(x, y, z, 21, 0.18) ? s(Blocks.PURPUR_BLOCK) : s(vanilla("purple_stained_glass")), true, 0);
		// The door and the ladders up through the floors.
		b.clear(0, 1, -6, 0, 3, -6);
		b.set(0, 0, -7, stairs(Blocks.END_STONE_BRICK_STAIRS, Direction.SOUTH, false));
		for (int y = 1; y <= 10; y++) {
			b.set(5, y, 0, facing(Blocks.LADDER, Direction.WEST));
		}
		for (int y = 11; y <= 20; y++) {
			b.set(-5, y, 0, facing(Blocks.LADDER, Direction.EAST));
		}
		// Windows all the way up.
		for (int y : new int[] {3, 4, 13, 14}) {
			b.set(-6, y, -2, pane(false));
			b.set(6, y, 2, pane(false));
			b.set(0, y, 6, pane(true));
		}
		// The ground floor: charts and instruments.
		b.set(-3, 1, 3, Blocks.CARTOGRAPHY_TABLE);
		b.set(-2, 1, 4, facing(Blocks.LECTERN, Direction.NORTH));
		b.set(3, 1, 3, Blocks.FLETCHING_TABLE);
		b.chest(-4, 1, -2, Direction.EAST, "observatory");
		b.frame(0, 2, 5, Direction.NORTH, Items.COMPASS);
		b.frame(1, 2, 5, Direction.NORTH, Items.CLOCK);
		b.frame(-1, 2, 5, Direction.NORTH, Items.SPYGLASS);
		b.set(0, 9, 0, lantern(true, true));
		// The middle floor: where the watchers slept.
		b.bed(-3, 11, 1, Direction.SOUTH, vanilla("purple_bed"));
		b.set(2, 11, 3, Blocks.BOOKSHELF);
		b.set(3, 11, 2, Blocks.BOOKSHELF);
		b.barrel(3, 11, -3, Direction.UP, "observatory");
		b.set(0, 19, 0, lantern(true, true));
		// The top: the telescope, looking out through a slot in the dome.
		b.clear(4, 25, -1, 7, 27, 1);
		b.line(-2, 21, 0, 5, 26, 0, s(vanilla("waxed_cut_copper")));
		b.set(-3, 21, 0, Blocks.POLISHED_BLACKSTONE);
		b.set(-2, 21, 1, facing(Blocks.LECTERN, Direction.WEST));
		b.chest(2, 21, 3, Direction.NORTH, "observatory");
		b.set(-4, 21, -3, candles(3));
		b.set(4, 21, -3, candles(4));
	}

	/** A temple of black voidstone on a raised platform, where two crystals guard an altar. */
	static void voidTemple(DeepBuilder b) {
		DeepBuilder.Paint stone = voidBricks(b);
		DeepBuilder.Paint obsidian = (x, y, z) -> y == 3 || y == 12 ? s(Blocks.CRYING_OBSIDIAN) : s(Blocks.OBSIDIAN);
		b.pad(-15, -14, 15, 14, stone, s(Blocks.END_STONE), 30);
		// The platform, with its steps at the front.
		b.fill(-12, 1, -10, 12, 2, 11, s(ModBlocks.POLISHED_VOIDSTONE));
		for (int x = -4; x <= 4; x++) {
			b.set(x, 1, -12, stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.SOUTH, false));
			b.set(x, 1, -11, s(ModBlocks.POLISHED_VOIDSTONE));
			b.set(x, 2, -11, stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.SOUTH, false));
		}
		// The colonnade.
		for (int x : new int[] {-11, -7, -3, 3, 7, 11}) {
			b.pillar(x, -9, 3, 12, obsidian);
			b.pillar(x, 10, 3, 12, obsidian);
		}
		for (int z : new int[] {-5, -1, 3, 7}) {
			b.pillar(-11, z, 3, 12, obsidian);
			b.pillar(11, z, 3, 12, obsidian);
		}
		// The roof, stepping up to a crystal at its peak.
		b.fill(-12, 13, -10, 12, 13, 11, stone);
		for (int k = 0; k < 4; k++) {
			b.fill(-10 + k * 3, 14 + k, -8 + k * 3, 10 - k * 3, 14 + k, 9 - k * 3, stone);
		}
		b.set(0, 18, 0, ModBlocks.VOID_CRYSTAL);
		b.set(0, 19, 0, rod(Direction.UP));
		// The cella.
		b.walls(-6, 3, -4, 6, 12, 8, stone);
		b.clear(-1, 3, -4, 1, 6, -4);
		b.fill(-3, 3, 5, 3, 3, 7, s(ModBlocks.POLISHED_VOIDSTONE));
		b.set(0, 4, 6, Blocks.CRYING_OBSIDIAN);
		b.set(0, 5, 6, head(Blocks.DRAGON_HEAD, 8));
		b.set(-1, 4, 6, candles(4));
		b.set(1, 4, 6, candles(4));
		for (int x = -4; x <= 4; x += 8) {
			b.set(x, 3, 6, Blocks.OBSIDIAN);
			b.crystal(x, 4, 6);
		}
		// A crystal chandelier.
		b.pillar(0, 1, 10, 12, chain());
		b.set(0, 9, 1, ModBlocks.VOID_CRYSTAL);
		b.set(0, 8, 1, cluster(Direction.DOWN));
		for (Direction side : SIDES) {
			b.set(side.getStepX(), 9, 1 + side.getStepZ(), rod(side));
		}
		b.chest(-5, 3, 6, Direction.EAST, "temple");
		b.chest(5, 3, 6, Direction.WEST, "temple");
		// Its guardians.
		b.spawner(0, 2, 2, "void_stalker");
		b.keeper(-3, 3, 2, "void_stalker");
		b.keeper(3, 3, 2, "void_stalker");
	}

	/** How far the bow of the wrecked skyship has ploughed into the ground. */
	private static int shipSink(int z) {
		return (z + 13) / 9;
	}

	/** The half-width of the skyship's hull along its length. */
	private static int shipHalf(int z) {
		if (z <= -13) {
			return 3;
		}
		return z <= 6 ? 4 : Math.max(0, 4 - (z - 5) / 2);
	}

	/** A skyship of the star-folk that fell out of the sky and ploughed into the ground, its elytra still aboard. */
	static void skyshipWreck(DeepBuilder b) {
		b.pad(-9, -15, 9, 16, ground(b), s(Blocks.END_STONE), 18);
		DeepBuilder.Paint hull = b.ruin(purpur(b), 0.05);
		for (int z = -13; z <= 13; z++) {
			int half = shipHalf(z);
			int sink = shipSink(z);
			int deck = 2 - sink;
			for (int x = -half; x <= half; x++) {
				int bottom = -2 + Math.abs(x) / 2 - sink;
				if (Math.abs(x) == half || z == -13 || z == 13) {
					b.fill(x, bottom, z, x, deck, z, hull);
					b.set(x, deck + 1, z, slab(Blocks.PURPUR_SLAB, false));
				} else {
					b.set(x, bottom, z, Blocks.END_STONE_BRICKS);
					b.clear(x, bottom + 1, z, x, deck - 1, z);
					b.set(x, deck, z, b.crumbled(x, deck, z, 0.05) ? b.open() : s(Blocks.PURPUR_BLOCK));
				}
			}
		}
		// A hatch down into the hold, where the cargo still is.
		b.clear(-1, 2 - shipSink(2), 2, 0, 2 - shipSink(2), 3);
		b.chest(0, -1 - shipSink(4), 4, Direction.SOUTH, "ship");
		b.barrel(-1, -1 - shipSink(-2), -2, Direction.UP, "ship");
		b.set(1, -1 - shipSink(0), 0, rod(Direction.UP));
		// The dragon's head on the bow, now nosing the ground.
		b.set(0, 3 - shipSink(13), 14, facing(Blocks.DRAGON_WALL_HEAD, Direction.SOUTH));
		// The stump of the mast, and the rest of it lying in the dust beside the ship with its sail.
		b.pillar(0, 0, 3 - shipSink(0), 6 - shipSink(0), pillar());
		b.line(7, 1, -6, 7, 1, 8, axis(Blocks.PURPUR_PILLAR, Direction.Axis.Z));
		for (int z = -4; z <= 6; z++) {
			if (b.chance(8, 1, z, 7, 0.7)) {
				b.set(8, 1, z, s(vanilla("magenta_wool")));
			}
			if (b.chance(6, 1, z, 7, 0.3)) {
				b.set(6, 1, z, s(vanilla("magenta_carpet")));
			}
		}
		// The captain's cabin at the stern, with the elytra still hung on its wall.
		int deck = 2 - shipSink(-12);
		b.shell(-3, deck, -13, 3, deck + 5, -8, purpur(b));
		b.fill(-2, deck + 6, -12, 2, deck + 6, -9, slab(Blocks.PURPUR_SLAB, false));
		b.clear(0, deck + 1, -8, 0, deck + 2, -8);
		b.set(-3, deck + 3, -10, pane(false));
		b.set(3, deck + 3, -10, pane(false));
		b.frame(0, deck + 3, -12, Direction.SOUTH, Items.ELYTRA);
		b.chest(-2, deck + 1, -12, Direction.SOUTH, "ship");
		b.set(2, deck + 1, -12, Blocks.BREWING_STAND);
		b.set(2, deck + 1, -9, facing(Blocks.ENDER_CHEST, Direction.WEST));
		b.set(0, deck + 4, -10, rod(Direction.DOWN));
		for (int x = -3; x <= 3; x += 6) {
			b.set(x, deck + 6, -13, rod(Direction.UP));
			b.set(x, deck + 6, -8, rod(Direction.UP));
		}
		// Its crew are long gone; shulkers have moved in.
		b.keeper(0, 3 - shipSink(-4), -4, EntityTypes.SHULKER);
		b.keeper(1, -2 - shipSink(-6) + 1, -6, EntityTypes.SHULKER);
	}

	/** The forge where the star-folk worked their starmetal, its fire still burning under the chimney. */
	static void starmetalForge(DeepBuilder b) {
		DeepBuilder.Paint wall = DeepBuilder.banded(s(ModBlocks.VOIDSTONE_BRICKS), s(ModBlocks.POLISHED_VOIDSTONE), 4);
		b.pad(-14, -12, 14, 12, voidBricks(b), s(Blocks.END_STONE), 24);
		b.shell(-11, 0, -9, 11, 10, 9, wall);
		b.fill(-10, 0, -8, 10, 0, 8, s(ModBlocks.POLISHED_VOIDSTONE));
		b.clear(-2, 1, -9, 2, 5, -9);
		for (int x = -3; x <= 3; x += 6) {
			b.pillar(x, -10, 1, 7, s(ModBlocks.POLISHED_VOIDSTONE));
			b.set(x, 8, -10, ModBlocks.STARMETAL_BLOCK);
		}
		// The hearth: a pool of lava behind bars, under a hood and a chimney through the roof.
		b.fill(-1, 0, 0, 1, 0, 2, s(Blocks.LAVA));
		for (int x = -2; x <= 2; x++) {
			b.set(x, 1, -1, joined(Blocks.IRON_BARS, true));
			b.set(x, 1, 3, joined(Blocks.IRON_BARS, true));
		}
		for (int z = 0; z <= 2; z++) {
			b.set(-2, 1, z, joined(Blocks.IRON_BARS, false));
			b.set(2, 1, z, joined(Blocks.IRON_BARS, false));
		}
		b.walls(-2, 6, -1, 2, 9, 3, wall);
		b.walls(-1, 10, 0, 1, 17, 2, wall);
		b.clear(0, 6, 1, 0, 17, 1);
		b.set(0, 18, 1, lit(Blocks.CAMPFIRE));
		// Along the walls: blast furnaces, anvils, smithing tables and the ore waiting its turn.
		for (int z = -6; z <= -2; z += 2) {
			b.set(-10, 1, z, lit(Blocks.BLAST_FURNACE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		}
		b.set(-6, 1, 7, facing(Blocks.ANVIL, Direction.EAST));
		b.set(-4, 1, 7, facing(Blocks.CHIPPED_ANVIL, Direction.EAST));
		b.set(10, 1, -6, Blocks.SMITHING_TABLE);
		b.set(10, 1, -4, Blocks.SMITHING_TABLE);
		b.set(8, 1, 7, Blocks.LAVA_CAULDRON);
		b.set(9, 1, 7, Blocks.LAVA_CAULDRON);
		b.hoard(7, 1, -6, 2.2, s(ModBlocks.METEORITE), s(ModBlocks.STARMETAL_ORE), s(ModBlocks.STARDUST));
		b.set(-9, 1, 7, ModBlocks.STARMETAL_BLOCK);
		b.set(-9, 2, 7, ModBlocks.STARMETAL_BLOCK);
		b.chest(10, 1, 2, Direction.WEST, "forge");
		b.barrel(-10, 1, 2, Direction.UP, "forge");
		b.barrel(-10, 1, 3, Direction.UP, "hall");
		// Lights in the rafters.
		for (int x = -8; x <= 8; x += 4) {
			b.set(x, 9, -5, lantern(true, true));
			b.set(x, 9, 6, lantern(true, true));
		}
		b.keeper(5, 1, 3, "crystal_golem");
	}

	/** A slender tower with an updraft in its heart: step onto the vents, and the wind carries you to the top. */
	static void gravityTower(DeepBuilder b) {
		b.padRound(0, 0, 9.5, ground(b), s(Blocks.END_STONE), 54);
		// A ring of bounce blooms round the foot of the tower, for the way back down.
		b.disc(0, 0, 0, 8.5, s(ModBlocks.BOUNCE_BLOOM));
		b.disc(0, 0, 0, 5.5, endBricks(b));
		b.cylinder(0, 0, 4.5, 1, 44, (x, y, z) -> y % 6 == 0 ? s(ModBlocks.VOIDSTONE_BRICKS)
				: b.chance(x, y, z, 23, 0.05) ? s(ModBlocks.VOID_CRYSTAL) : s(Blocks.PURPUR_BLOCK), true);
		b.fill(-1, 0, -1, 1, 0, 1, s(ModBlocks.UPDRAFT_VENT));
		b.clear(0, 1, -4, 0, 3, -4);
		for (int x = -1; x <= 1; x += 2) {
			b.set(x * 2, 1, -5, rod(Direction.UP));
		}
		b.set(-3, 1, 0, rod(Direction.UP));
		b.set(3, 1, 0, rod(Direction.UP));
		// The room at the top: a floor with the updraft open through its middle, windows all round.
		b.disc(0, 38, 0, 6.5, s(Blocks.PURPUR_BLOCK));
		b.clear(-1, 38, -1, 1, 38, 1);
		b.clear(0, 39, 4, 0, 40, 4);
		for (int y = 40; y <= 41; y++) {
			b.set(-4, y, 0, pane(false));
			b.set(4, y, 0, pane(false));
			b.set(0, y, -4, pane(true));
		}
		b.cylinder(0, 0, 6.5, 39, 39, rod(Direction.UP), true);
		// The roof: a cone of purpur with a crystal at its point.
		b.disc(0, 44, 0, 4.5, s(ModBlocks.VOIDSTONE_BRICKS));
		for (int k = 0; k <= 5; k++) {
			b.disc(0, 45 + k, 0, 4.5 - k * 0.8, s(Blocks.PURPUR_BLOCK));
		}
		b.set(0, 51, 0, ModBlocks.VOID_CRYSTAL);
		b.set(0, 52, 0, rod(Direction.UP));
		b.chest(-2, 39, 2, Direction.EAST, "tower");
		b.chest(2, 39, -2, Direction.WEST, "tower");
		b.set(2, 43, 2, lantern(true, true));
		b.set(-2, 43, -2, lantern(true, true));
	}

	// --- citadels ------------------------------------------------------------------------------

	/**
	 * The citadel of the star-folk: walls and corner towers round a garden, and in the open court at
	 * the heart of its keep, between four crystals, the Star Altar, waiting for its four fragments.
	 */
	static void starCitadel(DeepBuilder b) {
		DeepBuilder.Paint stone = endBricks(b);
		b.pad(-26, -26, 26, 26, b.mix(s(Blocks.END_STONE_BRICKS), s(Blocks.END_STONE), s(Blocks.END_STONE)), s(Blocks.END_STONE), 40);
		// The garden between the walls and the keep.
		for (int x = -21; x <= 21; x++) {
			for (int z = -21; z <= 21; z++) {
				if (Math.abs(x) <= 13 && Math.abs(z) <= 13 || Math.abs(x) <= 1 && z < 0) {
					continue;
				}
				b.set(x, 0, z, ModBlocks.END_MOSS);
				double r = b.rand(x, 1, z, 51);
				if (r < 0.2) {
					b.set(x, 1, z, ModBlocks.ENDER_GRASS);
				} else if (r < 0.26) {
					b.set(x, 1, z, ModBlocks.VOID_BLOOM);
				}
			}
		}
		b.fill(-1, 0, -21, 1, 0, -13, s(ModBlocks.POLISHED_VOIDSTONE));
		for (int[] tree : new int[][] {{-17, -17}, {17, -17}, {-17, 17}, {17, 17}, {-17, 0}, {17, 0}, {0, 17}}) {
			chorusTree(b, tree[0], tree[1], 1, 4 + b.layout.nextInt(3));
		}
		// The curtain wall, two blocks thick and crenellated, with its gate.
		b.walls(-23, 1, -23, 23, 8, 23, stone);
		b.walls(-22, 1, -22, 22, 8, 22, stone);
		for (int i = -23; i <= 23; i += 2) {
			b.set(i, 9, -23, stone);
			b.set(i, 9, 23, stone);
			b.set(-23, 9, i, stone);
			b.set(23, 9, i, stone);
		}
		b.clear(-2, 1, -23, 2, 5, -22);
		for (int x = -3; x <= 3; x += 6) {
			b.pillar(x, -24, 1, 7, pillar());
			b.set(x, 8, -24, rod(Direction.UP));
		}
		b.fill(-2, 6, -24, 2, 6, -24, s(Blocks.PURPUR_BLOCK));
		// A tower at every corner.
		for (int[] corner : new int[][] {{-23, -23}, {23, -23}, {-23, 23}, {23, 23}}) {
			b.cylinder(corner[0], corner[1], 3.5, 1, 14, stone, false);
			b.disc(corner[0], 15, corner[1], 3.5, s(Blocks.PURPUR_BLOCK));
			b.cylinder(corner[0], corner[1], 3.5, 16, 16, slab(Blocks.PURPUR_SLAB, false), true);
			b.set(corner[0], 16, corner[1], ModBlocks.VOID_CRYSTAL);
			b.set(corner[0], 17, corner[1], rod(Direction.UP));
		}
		// The keep: high walls round an open court, a roof of purpur round its edges.
		b.walls(-12, 1, -12, 12, 15, 12, purpur(b));
		b.fill(-11, 0, -11, 11, 0, 11, (x, y, z) -> ((x + z) & 1) == 0 ? s(ModBlocks.POLISHED_VOIDSTONE) : s(Blocks.PURPUR_BLOCK));
		b.fill(-12, 16, -12, 12, 16, 12, s(Blocks.PURPUR_BLOCK));
		b.disc(0, 16, 0, 8.5, b.open());
		for (int i = 0; i < 12; i++) {
			int x = ringX(i, 12, 9.5);
			int z = ringZ(i, 12, 9.5);
			b.pillar(x, z, 1, 15, pillar());
		}
		b.clear(-2, 1, -12, 2, 6, -12);
		for (int x = -12; x <= 12; x += 24) {
			for (int z = -6; z <= 6; z += 6) {
				b.fill(x, 4, z - 1, x, 9, z + 1, s(vanilla("magenta_stained_glass")));
			}
		}
		// The Star Altar on its dais, between four crystals.
		b.fill(-4, 1, -4, 4, 1, 4, s(ModBlocks.POLISHED_VOIDSTONE));
		b.fill(-2, 2, -2, 2, 2, 2, s(ModBlocks.POLISHED_VOIDSTONE));
		b.set(0, 3, 0, ModBlocks.STAR_ALTAR);
		for (int[] corner : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
			b.pillar(corner[0], corner[1], 1, 6, s(Blocks.OBSIDIAN));
			b.crystal(corner[0], 7, corner[1]);
		}
		for (Direction side : SIDES) {
			b.set(side.getStepX() * 3, 2, side.getStepZ() * 3, candles(4));
		}
		b.frame(0, 4, 11, Direction.NORTH, ModItems.STAR_FRAGMENT);
		// The treasuries along the sides of the keep.
		b.chest(-11, 1, 0, Direction.EAST, "citadel");
		b.chest(11, 1, 0, Direction.WEST, "citadel");
		b.barrel(-11, 1, 8, Direction.UP, "hall");
		b.barrel(11, 1, -8, Direction.UP, "hall");
		b.chest(0, 1, 11, Direction.NORTH, "observatory");
		// Lights round the keep and on the wall.
		for (int i = 0; i < 12; i++) {
			b.set(ringX(i, 12, 9.5), 17, ringZ(i, 12, 9.5), rod(Direction.UP));
		}
		for (int i = -20; i <= 20; i += 8) {
			b.set(i, 9, -23, lantern(true, false));
			b.set(i, 9, 23, lantern(true, false));
		}
		// Those who keep it still.
		b.keeper(-15, 1, -15, "void_stalker");
		b.keeper(15, 1, 15, "void_stalker");
		b.keeper(15, 1, -15, EntityTypes.ENDERMAN);
		b.keeper(-15, 1, 15, EntityTypes.ENDERMAN);
		b.keeper(4, 1, -15, "crystal_golem");
	}

	/** A colossal statue of an enderman gazing out over the void; its treasure lies sealed in the plinth. */
	static void enderColossus(DeepBuilder b) {
		DeepBuilder.Paint body = (x, y, z) -> b.chance(x, y, z, 31, 0.15) ? s(ModBlocks.VOIDSTONE) : s(Blocks.OBSIDIAN);
		b.padRound(0, 0, 16.5, ground(b), s(Blocks.END_STONE), 60);
		b.disc(0, 0, 0, 14.5, endBricks(b));
		// A ring of broken obsidian teeth round it.
		for (int i = 0; i < 10; i++) {
			int x = ringX(i, 10, 13.0);
			int z = ringZ(i, 10, 13.0);
			int height = 2 + b.layout.nextInt(5);
			b.pillar(x, z, 1, height, s(Blocks.OBSIDIAN));
			b.set(x, height + 1, z, Blocks.CRYING_OBSIDIAN);
		}
		// The plinth, and the chamber inside it.
		b.fill(-10, 1, -8, 10, 1, 8, s(ModBlocks.POLISHED_VOIDSTONE));
		b.fill(-9, 2, -7, 9, 6, 7, voidBricks(b));
		b.clear(-7, 3, -5, 7, 5, 5);
		b.clear(-1, 3, -7, 1, 4, -6);
		b.chest(-6, 3, 4, Direction.EAST, "colossus");
		b.chest(6, 3, 4, Direction.WEST, "colossus");
		b.barrel(0, 3, 5, Direction.UP, "hall");
		b.hoard(-3, 3, 0, 1.8, s(Blocks.CRYING_OBSIDIAN), s(ModBlocks.VOID_CRYSTAL), s(Blocks.OBSIDIAN));
		b.hoard(3, 3, 0, 1.8, s(Blocks.CRYING_OBSIDIAN), s(ModBlocks.VOID_CRYSTAL), s(Blocks.OBSIDIAN));
		for (int x = -6; x <= 6; x += 4) {
			b.set(x, 5, -3, rod(Direction.DOWN));
			b.set(x, 5, 3, rod(Direction.DOWN));
		}
		b.spawner(0, 2, 0, "void_stalker");
		// The enderman: long legs, long arms hanging, and a head with burning eyes.
		for (int x = -3; x <= 3; x += 5) {
			b.fill(x, 7, -1, x + 1, 26, 0, body);
		}
		b.fill(-4, 27, -2, 4, 38, 1, body);
		for (int x = -6; x <= 5; x += 11) {
			b.fill(x, 17, -1, x + 1, 38, 0, body);
		}
		b.fill(-4, 39, -4, 4, 47, 4, body);
		for (int side = -1; side <= 1; side += 2) {
			b.set(side * 1, 42, -4, Blocks.CRYING_OBSIDIAN);
			b.set(side * 2, 42, -4, Blocks.PEARLESCENT_FROGLIGHT);
			b.set(side * 3, 42, -4, Blocks.CRYING_OBSIDIAN);
		}
		b.keeper(-8, 1, -10, EntityTypes.ENDERMAN);
		b.keeper(8, 1, -10, EntityTypes.ENDERMAN);
	}

	/** The palace of the star-folk's last queen: a throne hall of black voidstone between two crystal-crowned towers. */
	static void voidPalace(DeepBuilder b) {
		DeepBuilder.Paint wall = DeepBuilder.banded(s(ModBlocks.VOIDSTONE_BRICKS), s(ModBlocks.POLISHED_VOIDSTONE), 5);
		b.pad(-26, -24, 26, 24, s(ModBlocks.POLISHED_VOIDSTONE), s(Blocks.END_STONE), 46);
		// The throne hall.
		b.shell(-13, 0, -10, 13, 16, 14, wall);
		b.fill(-12, 0, -9, 12, 0, 13, (x, y, z) -> ((x + z) & 1) == 0 ? s(ModBlocks.POLISHED_VOIDSTONE) : s(Blocks.OBSIDIAN));
		b.fill(-1, 1, -9, 1, 1, 8, s(vanilla("purple_carpet")));
		b.clear(-2, 1, -10, 2, 7, -10);
		for (int x = -3; x <= 3; x += 6) {
			b.pillar(x, -11, 1, 9, s(Blocks.OBSIDIAN));
			b.set(x, 10, -11, ModBlocks.VOID_CRYSTAL);
		}
		// Two rows of obsidian columns down the hall, and tall windows of purple glass.
		for (int z = -6; z <= 10; z += 4) {
			for (int side = -1; side <= 1; side += 2) {
				b.pillar(side * 7, z, 1, 14, s(Blocks.OBSIDIAN));
				b.set(side * 7, 15, z, Blocks.CRYING_OBSIDIAN);
				b.fill(side * 13, 4, z + 2, side * 13, 11, z + 2, pane(false));
			}
		}
		// The throne on its dais at the far end, a dragon's head above it.
		b.fill(-5, 1, 9, 5, 1, 13, s(ModBlocks.POLISHED_VOIDSTONE));
		b.fill(-3, 2, 10, 3, 2, 13, s(ModBlocks.POLISHED_VOIDSTONE));
		b.set(0, 3, 12, stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.SOUTH, false));
		b.set(-1, 3, 12, slab(Blocks.POLISHED_BLACKSTONE_SLAB, false));
		b.set(1, 3, 12, slab(Blocks.POLISHED_BLACKSTONE_SLAB, false));
		b.fill(0, 3, 13, 0, 5, 13, s(ModBlocks.STARMETAL_BLOCK));
		b.set(0, 6, 13, cluster(Direction.UP));
		b.set(0, 9, 13, facing(Blocks.DRAGON_WALL_HEAD, Direction.NORTH));
		for (int x = -3; x <= 3; x += 6) {
			b.set(x, 3, 11, candles(4));
			b.pillar(x, 13, 3, 8, s(Blocks.CRYING_OBSIDIAN));
		}
		// Crystal chandeliers down the aisle.
		for (int z = -4; z <= 8; z += 6) {
			b.pillar(0, z, 12, 15, chain());
			b.set(0, 11, z, ModBlocks.VOID_CRYSTAL);
			b.set(0, 10, z, cluster(Direction.DOWN));
			for (Direction side : SIDES) {
				b.set(side.getStepX(), 11, z + side.getStepZ(), rod(side));
			}
		}
		// The treasure of the queen, in the corners behind the columns.
		b.chest(-12, 1, 12, Direction.EAST, "palace");
		b.chest(12, 1, 12, Direction.WEST, "palace");
		b.barrel(-12, 1, -8, Direction.UP, "hall");
		b.barrel(12, 1, -8, Direction.UP, "hall");
		b.spawner(0, 0, 4, "void_stalker");
		// The towers, crowned with void crystal, each with a chest at the top.
		for (int side = -1; side <= 1; side += 2) {
			int tx = side * 20;
			b.cylinder(tx, -2, 4.5, 1, 26, wall, true);
			b.disc(tx, 0, -2, 3.5, s(ModBlocks.POLISHED_VOIDSTONE));
			b.disc(tx, 27, -2, 4.5, s(ModBlocks.POLISHED_VOIDSTONE));
			b.sphere(tx, 29, -2, 2.5, s(ModBlocks.VOID_CRYSTAL), false, -2);
			b.set(tx, 32, -2, cluster(Direction.UP));
			b.clear(tx, 1, -6, tx, 3, -6);
			for (int y = 1; y <= 26; y++) {
				b.set(tx - side * 3, y, -2, facing(Blocks.LADDER, side < 0 ? Direction.WEST : Direction.EAST));
			}
			b.clear(tx - side * 3, 27, -2, tx - side * 3, 27, -2);
			b.disc(tx, 22, -2, 3.5, s(ModBlocks.POLISHED_VOIDSTONE));
			b.set(tx - side * 3, 22, -2, facing(Blocks.LADDER, side < 0 ? Direction.WEST : Direction.EAST));
			b.chest(tx + side, 23, 0, Direction.NORTH, "palace");
			b.set(tx, 26, -2, lantern(true, true));
		}
		// The garden before the doors: still pools, chorus trees, and a fountain of light.
		for (int side = -1; side <= 1; side += 2) {
			b.fill(side * 4, 0, -20, side * 10, 0, -14, s(Blocks.WATER));
			chorusTree(b, side * 14, -18, 1, 5);
			chorusTree(b, side * 14, -13, 1, 4);
		}
		b.pillar(0, -17, 1, 4, s(ModBlocks.VOID_CRYSTAL));
		for (Direction side : SIDES) {
			b.set(side.getStepX(), 4, -17 + side.getStepZ(), rod(side));
		}
		b.set(0, 5, -17, rod(Direction.UP));
		// Its guardians.
		b.keeper(-4, 1, 0, "void_stalker");
		b.keeper(4, 1, 0, "void_stalker");
		b.keeper(-4, 1, -13, "crystal_golem");
		b.keeper(4, 1, -13, "crystal_golem");
		b.keeper(-16, 1, -20, EntityTypes.ENDERMAN);
	}
}
