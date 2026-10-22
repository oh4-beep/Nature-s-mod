package com.naturesmod.worldgen.deep;

import static com.naturesmod.worldgen.deep.DeepBuilder.WATER;
import static com.naturesmod.worldgen.deep.DeepBuilder.axis;
import static com.naturesmod.worldgen.deep.DeepBuilder.facing;
import static com.naturesmod.worldgen.deep.DeepBuilder.head;
import static com.naturesmod.worldgen.deep.DeepBuilder.joined;
import static com.naturesmod.worldgen.deep.DeepBuilder.lantern;
import static com.naturesmod.worldgen.deep.DeepBuilder.lit;
import static com.naturesmod.worldgen.deep.DeepBuilder.pickles;
import static com.naturesmod.worldgen.deep.DeepBuilder.s;
import static com.naturesmod.worldgen.deep.DeepBuilder.slab;
import static com.naturesmod.worldgen.deep.DeepBuilder.stairs;
import static com.naturesmod.worldgen.deep.DeepBuilder.vanilla;
import static com.naturesmod.worldgen.deep.DeepBuilder.weapon;

import com.naturesmod.block.ModBlocks;
import com.naturesmod.item.AbyssWeapon;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The wonders of the deep: rare, vast, and the richest places in the world. */
final class Wonders {
	private static final Block[] STAINED_GLASS = {vanilla("blue_stained_glass"), vanilla("cyan_stained_glass"), vanilla("purple_stained_glass"),
			vanilla("light_blue_stained_glass"), vanilla("magenta_stained_glass"), vanilla("lime_stained_glass")};

	private Wonders() {
	}

	private static AbyssWeapon.Kind anyWeapon(DeepBuilder b) {
		AbyssWeapon.Kind[] kinds = AbyssWeapon.Kind.values();
		return kinds[b.layout.nextInt(kinds.length)];
	}

	private static BlockState coral(DeepBuilder b, int x, int y, int z) {
		return b.pick(x, y, z, 31, s(Blocks.TUBE_CORAL_BLOCK), s(Blocks.BRAIN_CORAL_BLOCK), s(Blocks.BUBBLE_CORAL_BLOCK), s(Blocks.FIRE_CORAL_BLOCK),
				s(Blocks.HORN_CORAL_BLOCK));
	}

	private static BlockState dripstone(boolean down) {
		return s(Blocks.POINTED_DRIPSTONE).setValue(BlockStateProperties.VERTICAL_DIRECTION, down ? Direction.DOWN : Direction.UP);
	}

	// --- the Drowned Cathedral -----------------------------------------------------------------

	/**
	 * A cathedral as big as any on land: a nave of grey stone under a steep roof, windows of
	 * coloured glass, two towers at the front, and above the altar at the far end a pair of wings.
	 */
	static void cathedral(DeepBuilder b) {
		DeepBuilder.Paint stone = b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS),
				s(Blocks.DEEPSLATE_BRICKS));
		b.pad(-16, -24, 16, 19, b.mix(s(Blocks.GRAVEL), s(Blocks.STONE_BRICKS), s(Blocks.GRAVEL)), s(Blocks.COBBLESTONE), 40);
		b.walls(-8, 1, -16, 8, 14, 15, stone);
		b.fill(-7, 0, -15, 7, 0, 14, (x, y, z) -> (x + z) % 2 == 0 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.DEEPSLATE_TILES));
		// The steep roof, closed at both gables.
		for (int x = -9; x <= 9; x++) {
			int y = 15 + (9 - Math.abs(x));
			BlockState tile = x == 0 ? s(Blocks.DEEPSLATE_TILES) : stairs(Blocks.DEEPSLATE_TILE_STAIRS, x < 0 ? Direction.EAST : Direction.WEST, false);
			b.fill(x, y, -17, x, y, 16, tile);
			if (Math.abs(x) <= 8) {
				b.fill(x, 15, -16, x, y - 1, -16, stone);
				b.fill(x, 15, 15, x, y - 1, 15, stone);
			}
		}
		// Buttresses.
		for (int z = -14; z <= 13; z += 4) {
			for (int side = -1; side <= 1; side += 2) {
				b.fill(side * 9, 1, z, side * 10, 8, z, stone);
				b.fill(side * 9, 9, z, side * 9, 12, z, stone);
			}
		}
		// Tall windows of coloured glass.
		int pane = 0;
		for (int z = -12; z <= 11; z += 4) {
			Block glass = STAINED_GLASS[pane++ % STAINED_GLASS.length];
			for (int side = -1; side <= 1; side += 2) {
				b.fill(side * 8, 4, z - 1, side * 8, 11, z + 1, glass);
				b.set(side * 8, 12, z, glass);
			}
		}
		// The great door and the rose window over it.
		b.water(-2, 1, -16, 2, 6, -16);
		b.water(-1, 7, -16, 1, 7, -16);
		for (int x = -4; x <= 4; x++) {
			for (int y = 8; y <= 16; y++) {
				double d = Math.sqrt(x * x + (y - 12) * (y - 12));
				if (d <= 3.6) {
					b.set(x, y, -16, d < 1.0 ? s(Blocks.SEA_LANTERN) : s(STAINED_GLASS[(int) (Math.atan2(y - 12, x) * 3 + 10) % STAINED_GLASS.length]));
				}
			}
		}
		// Two towers with spires.
		for (int side = -1; side <= 1; side += 2) {
			int tx = side * 11;
			b.fill(tx - 2, 1, -21, tx + 2, 28, -17, stone);
			b.water(tx - 1, 1, -20, tx + 1, 27, -18);
			for (int y = 6; y <= 24; y += 6) {
				b.set(tx, y, -21, Blocks.SEA_LANTERN);
				b.set(tx + side * 2, y, -19, Blocks.SEA_LANTERN);
			}
			for (int level = 0; level < 9; level++) {
				int half = Math.max(0, 2 - level / 3);
				b.fill(tx - half, 29 + level, -19 - half, tx + half, 29 + level, -19 + half, s(Blocks.DEEPSLATE_TILES));
			}
			b.set(tx, 38, -19, Blocks.SEA_LANTERN);
			b.set(tx, 39, -19, vanilla("lightning_rod"));
		}
		// Pillars down the nave and pews between them.
		for (int z = -12; z <= 10; z += 4) {
			for (int side = -1; side <= 1; side += 2) {
				b.pillar(side * 5, z, 1, 14, s(Blocks.POLISHED_DEEPSLATE));
				b.set(side * 5, 15, z, Blocks.CHISELED_DEEPSLATE);
			}
			b.pillar(0, z, 16, 23, axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			b.set(0, 15, z, lantern(false, true));
		}
		for (int z = -11; z <= 6; z += 2) {
			for (int side = -1; side <= 1; side += 2) {
				b.fill(side * 2, 1, z, side * 4, 1, z, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
			}
		}
		// The altar.
		b.fill(-5, 1, 10, 5, 1, 14, Blocks.POLISHED_DEEPSLATE);
		b.fill(-3, 2, 12, 3, 2, 14, Blocks.POLISHED_DEEPSLATE);
		b.fill(-1, 3, 13, 1, 3, 13, Blocks.GOLD_BLOCK);
		b.set(-1, 4, 13, vanilla("candle"));
		b.set(1, 4, 13, vanilla("candle"));
		b.chest(0, 4, 13, Direction.NORTH, "legendary");
		b.frame(0, 9, 14, Direction.NORTH, Items.ELYTRA);
		b.set(-1, 9, 14, Blocks.SEA_LANTERN);
		b.set(1, 9, 14, Blocks.SEA_LANTERN);
		b.set(0, 10, 14, Blocks.GOLD_BLOCK);
		b.set(0, 8, 14, Blocks.GOLD_BLOCK);
		b.chest(-4, 2, 13, Direction.EAST, "vault");
		b.chest(4, 2, 13, Direction.WEST, "library");
		b.spawner(-3, 0, 4, EntityTypes.DROWNED);
		b.spawner(3, 0, -6, EntityTypes.DROWNED);
	}

	// --- the Ziggurat ------------------------------------------------------------------------

	/**
	 * A stepped temple of dark prismarine, a stair up its face, and at its summit a beacon whose
	 * beam climbs the whole height of the sea.
	 */
	static void ziggurat(DeepBuilder b) {
		b.pad(-21, -24, 21, 21, b.mix(s(Blocks.PRISMARINE), s(Blocks.GRAVEL)), s(Blocks.COBBLED_DEEPSLATE), 24);
		int[] halves = {18, 14, 10, 6};
		for (int i = 0; i < halves.length; i++) {
			int half = halves[i];
			int y0 = 1 + i * 4;
			b.fill(-half, y0, -half, half, y0 + 3, half, (x, y, z) -> Math.abs(x) == half || Math.abs(z) == half
					? (y % 4 == 0 ? s(Blocks.PRISMARINE_BRICKS) : s(Blocks.DARK_PRISMARINE)) : s(Blocks.DARK_PRISMARINE));
			for (int[] c : new int[][] {{-half, -half}, {half, -half}, {-half, half}, {half, half}}) {
				b.set(c[0], y0 + 4, c[1], Blocks.SEA_LANTERN);
			}
		}
		// The stair up the front.
		for (int y = 1; y <= 16; y++) {
			int z = -19 + y;
			b.fill(-2, y, z, 2, y, z, stairs(Blocks.PRISMARINE_BRICK_STAIRS, Direction.SOUTH, false));
			b.fill(-2, y + 1, z, 2, y + 4, z, WATER);
			b.set(-3, y, z, Blocks.PRISMARINE_BRICKS);
			b.set(3, y, z, Blocks.PRISMARINE_BRICKS);
		}
		// The summit: pillars round a beacon on a base of gold.
		b.fill(-1, 16, -1, 1, 16, 1, Blocks.GOLD_BLOCK);
		b.set(0, 17, 0, Blocks.BEACON);
		for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
			b.pillar(c[0], c[1], 17, 21, s(Blocks.PRISMARINE_BRICKS));
			b.set(c[0], 22, c[1], Blocks.SEA_LANTERN);
		}
		// The treasure chamber inside, reached by a passage from the foot of the stair.
		b.water(-1, 1, 6, 1, 3, 18);
		b.water(-6, 1, -6, 6, 5, 6);
		b.fill(-6, 0, -6, 6, 0, 6, (x, y, z) -> (x + z) % 2 == 0 ? s(Blocks.PRISMARINE_BRICKS) : s(Blocks.DARK_PRISMARINE));
		for (int x = -4; x <= 4; x += 4) {
			b.set(x, 6, -4, Blocks.SEA_LANTERN);
			b.set(x, 6, 4, Blocks.SEA_LANTERN);
		}
		b.hoard(0, 1, 3, 2.6, s(Blocks.GOLD_BLOCK), s(Blocks.GOLD_BLOCK), s(Blocks.EMERALD_BLOCK));
		b.chest(-5, 1, 5, Direction.EAST, "legendary");
		b.chest(5, 1, 5, Direction.WEST, "legendary");
		b.chest(-5, 1, -5, Direction.EAST, "treasury");
		b.spawner(-5, 1, 0, EntityTypes.GUARDIAN);
		b.spawner(5, 1, 0, EntityTypes.GUARDIAN);
	}

	// --- the Citadel -------------------------------------------------------------------------

	/** A walled citadel: towers at the corners, a gatehouse, and a keep with treasure at the top. */
	static void citadel(DeepBuilder b) {
		DeepBuilder.Paint wall = b.worn(s(Blocks.DEEPSLATE_TILES), s(Blocks.DEEPSLATE_BRICKS), s(Blocks.CRACKED_DEEPSLATE_TILES),
				s(Blocks.POLISHED_DEEPSLATE));
		b.pad(-24, -24, 24, 24, b.mix(s(Blocks.COBBLED_DEEPSLATE), s(Blocks.GRAVEL), s(Blocks.DEEPSLATE_TILES)), s(Blocks.COBBLED_DEEPSLATE), 22);
		// The curtain wall.
		for (int x = -20; x <= 20; x++) {
			for (int z = -20; z <= 20; z++) {
				int m = Math.max(Math.abs(x), Math.abs(z));
				if (m < 19 || !b.column(x, z)) {
					continue;
				}
				b.pillar(x, z, 1, 10, wall);
				if (m == 20 && (x + z) % 2 == 0) {
					b.set(x, 11, z, wall.at(x, 11, z));
				}
				if (m == 19 && (x + z) % 6 == 0) {
					b.set(x, 8, z, Blocks.SEA_LANTERN);
				}
			}
		}
		// The corner towers.
		for (int[] c : new int[][] {{-20, -20}, {20, -20}, {-20, 20}, {20, 20}}) {
			b.cylinder(c[0], c[1], 3.6, 1, 15, wall, false);
			b.cylinder(c[0], c[1], 3.6, 16, 16, wall, true);
			b.set(c[0], 16, c[1], Blocks.SEA_LANTERN);
		}
		// The gatehouse.
		b.water(-2, 1, -20, 2, 6, -19);
		for (int side = -1; side <= 1; side += 2) {
			b.cylinder(side * 4, -20, 2.6, 1, 13, wall, false);
			b.set(side * 4, 14, -20, Blocks.SEA_LANTERN);
		}
		b.fill(-2, 5, -20, 2, 6, -20, joined(Blocks.IRON_BARS, true));
		// The keep: three floors, each open to the one above.
		b.shell(-7, 0, -4, 7, 19, 10, wall);
		b.water(-1, 1, -4, 1, 4, -4);
		for (int y : new int[] {7, 13}) {
			b.fill(-6, y, -3, 6, y, 9, Blocks.POLISHED_DEEPSLATE);
			b.water(-6 + (y == 7 ? 0 : 10), y, 7, -4 + (y == 7 ? 0 : 10), y, 9);
		}
		for (int y : new int[] {5, 11, 17}) {
			for (int x = -4; x <= 4; x += 8) {
				b.set(x, y + 1, 3, lantern(false, true));
			}
		}
		for (int y = 4; y <= 16; y += 6) {
			for (int x = -7; x <= 7; x += 14) {
				b.set(x, y, 3, Blocks.SEA_LANTERN);
			}
			b.set(0, y, 10, Blocks.SEA_LANTERN);
		}
		// Ground floor: the guard room.
		b.chest(-6, 1, 9, Direction.EAST, "armory");
		b.stand(4, 1, 8, Direction.NORTH, new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
				new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.DIAMOND_SWORD));
		// Middle floor: the lord's chamber.
		b.chest(6, 8, 9, Direction.WEST, "vault");
		b.bed(-5, 8, -2, Direction.SOUTH, vanilla("red_bed"));
		// Top floor: the treasure.
		b.hoard(0, 14, 5, 2.5, s(Blocks.GOLD_BLOCK), s(Blocks.GOLD_BLOCK), s(Blocks.DIAMOND_BLOCK), s(Blocks.GOLD_BLOCK));
		b.chest(-6, 14, 9, Direction.EAST, "legendary");
		b.chest(6, 14, -3, Direction.WEST, "legendary");
		b.frame(0, 16, 9, Direction.NORTH, weapon(anyWeapon(b)));
		b.fill(-7, 20, -4, 7, 20, 10, (x, y, z) -> (x + z) % 2 == 0 && (Math.abs(x) == 7 || z == -4 || z == 10) ? wall.at(x, y, z) : WATER);
		b.spawner(-10, 0, 0, EntityTypes.DROWNED);
		b.spawner(10, 0, -10, EntityTypes.DROWNED);
	}

	// --- the Colossus ------------------------------------------------------------------------

	/** A drowned king of stone, forty blocks tall, trident raised; his tomb is in his pedestal. */
	static void colossus(DeepBuilder b) {
		DeepBuilder.Paint stone = b.worn(s(Blocks.POLISHED_ANDESITE), s(Blocks.ANDESITE), s(Blocks.STONE), s(Blocks.MOSSY_COBBLESTONE));
		b.padRound(0, 0, 16.5, b.mix(s(Blocks.GRAVEL), s(Blocks.STONE_BRICKS)), s(Blocks.COBBLESTONE), 46);
		// The pedestal, with a tomb inside.
		b.fill(-7, 0, -6, 7, 3, 6, Blocks.POLISHED_DEEPSLATE);
		b.walls(-7, 3, -6, 7, 3, 6, s(Blocks.GOLD_BLOCK));
		b.water(-5, 1, -4, 5, 2, 4);
		b.water(-1, 1, 5, 1, 2, 6);
		for (int x = -4; x <= 4; x += 4) {
			b.set(x, 2, -5, Blocks.SEA_LANTERN);
		}
		b.chest(-4, 1, 4, Direction.EAST, "legendary");
		b.chest(4, 1, 4, Direction.WEST, "legendary");
		b.hoard(0, 1, -2, 2.3, s(Blocks.GOLD_BLOCK), s(Blocks.RAW_GOLD_BLOCK), s(Blocks.GOLD_BLOCK));
		// Legs, body and arms.
		b.fill(-4, 4, -2, -2, 14, 1, stone);
		b.fill(2, 4, -2, 4, 14, 1, stone);
		b.fill(-5, 4, -3, -1, 5, 2, stone);
		b.fill(1, 4, -3, 5, 5, 2, stone);
		b.fill(-5, 15, -3, 5, 25, 2, stone);
		b.fill(-4, 19, -4, 4, 23, -4, s(Blocks.CHISELED_STONE_BRICKS));
		b.fill(-5, 15, -4, 5, 16, -4, s(Blocks.GOLD_BLOCK));
		b.fill(-8, 14, -1, -6, 25, 1, stone);
		b.fill(6, 24, -1, 8, 26, 1, stone);
		b.fill(7, 27, -1, 8, 33, 1, stone);
		// The trident.
		b.pillar(9, 0, 18, 42, s(Blocks.PRISMARINE_BRICKS));
		b.fill(9, 37, -2, 9, 37, 2, Blocks.PRISMARINE_BRICKS);
		b.pillar(9, -2, 38, 41, s(Blocks.PRISMARINE_BRICKS));
		b.pillar(9, 2, 38, 41, s(Blocks.PRISMARINE_BRICKS));
		b.set(9, 43, 0, Blocks.GOLD_BLOCK);
		b.set(9, 42, -2, Blocks.GOLD_BLOCK);
		b.set(9, 42, 2, Blocks.GOLD_BLOCK);
		// The head, its eyes alight, and the crown.
		b.ellipsoid(0, 29, 0, 3.6, 3.8, 3.4, stone, false, -3);
		b.set(-1, 30, -3, Blocks.SEA_LANTERN);
		b.set(1, 30, -3, Blocks.SEA_LANTERN);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) == 3) {
					b.set(x, 33, z, Blocks.GOLD_BLOCK);
					if ((x + z) % 2 == 0) {
						b.set(x, 34, z, Blocks.GOLD_BLOCK);
					}
				}
			}
		}
		b.set(0, 34, -3, Blocks.DIAMOND_BLOCK);
		for (int[] c : new int[][] {{-12, -10}, {12, -10}, {-12, 10}, {12, 10}}) {
			b.lampPost(c[0], c[1], 1, 4, s(Blocks.STONE_BRICK_WALL), s(Blocks.SEA_LANTERN));
		}
	}

	// --- the Drowned City --------------------------------------------------------------------

	/** A quarter of a drowned city: a square with a fountain, lamp-lit streets and houses all round. */
	static void drownedCity(DeepBuilder b) {
		b.pad(-24, -24, 24, 24, b.mix(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS), s(Blocks.GRAVEL)),
				s(Blocks.COBBLESTONE), 16);
		// The fountain in the square.
		b.cylinder(0, 0, 4.0, 1, 1, s(Blocks.PRISMARINE_BRICKS), true);
		b.cylinder(0, 0, 1.5, 1, 4, s(Blocks.PRISMARINE_BRICKS), false);
		b.set(0, 5, 0, Blocks.SEA_LANTERN);
		b.set(0, 6, 0, Blocks.PRISMARINE_BRICKS);
		b.set(0, 7, 0, Blocks.SEA_LANTERN);
		// Streets out of the square, lit along both sides.
		for (int t = 6; t <= 22; t += 4) {
			for (int[] c : new int[][] {{3, t}, {-3, t}, {3, -t}, {-3, -t}, {t, 3}, {t, -3}, {-t, 3}, {-t, -3}}) {
				b.lampPost(c[0], c[1], 1, 3, s(Blocks.DARK_OAK_FENCE), lantern(false, false));
			}
		}
		// A house in each quarter, each of its own stone.
		BlockState[][] styles = {
				{s(Blocks.STONE_BRICKS), s(Blocks.SPRUCE_PLANKS), s(Blocks.DARK_OAK_PLANKS)},
				{s(Blocks.CUT_SANDSTONE), s(Blocks.SMOOTH_SANDSTONE), s(vanilla("orange_terracotta"))},
				{s(Blocks.MUD_BRICKS), s(Blocks.PACKED_MUD), s(Blocks.MANGROVE_PLANKS)},
				{s(Blocks.TUFF_BRICKS), s(Blocks.POLISHED_TUFF), s(Blocks.DEEPSLATE_TILES)}};
		int[][] corners = {{-14, -14}, {14, -14}, {-14, 14}, {14, 14}};
		for (int i = 0; i < 4; i++) {
			house(b, corners[i][0], corners[i][1], styles[i], i);
		}
		// A market stall and a well in the square.
		b.fill(6, 1, -8, 9, 1, -8, Blocks.SPRUCE_PLANKS);
		b.fill(6, 4, -9, 9, 4, -7, vanilla("red_wool"));
		b.pillar(6, -7, 1, 3, s(Blocks.SPRUCE_FENCE));
		b.pillar(9, -7, 1, 3, s(Blocks.SPRUCE_FENCE));
		b.barrel(7, 2, -8, Direction.SOUTH, "ruin");
		b.frame(8, 1, -7, Direction.SOUTH, Items.EMERALD);
		b.walls(-9, 1, 6, -7, 1, 8, s(Blocks.MOSSY_COBBLESTONE));
		b.water(-8, -6, 7, -8, 1, 7);
		b.set(-8, -7, 7, Blocks.GOLD_BLOCK);
	}

	/** One house of the drowned city, its door facing the square. */
	private static void house(DeepBuilder b, int hx, int hz, BlockState[] style, int index) {
		int dx = -Integer.signum(hx);
		int dz = -Integer.signum(hz);
		b.shell(hx - 4, 0, hz - 4, hx + 4, 7, hz + 4, b.worn(style[0], style[1]));
		b.fill(hx - 3, 0, hz - 3, hx + 3, 0, hz + 3, style[2]);
		for (int k = 0; k <= 5; k++) {
			b.fill(hx - 5 + k, 8 + k, hz - 5 + k, hx + 5 - k, 8 + k, hz + 5 - k, k == 5 ? s(Blocks.SEA_LANTERN) : style[2]);
		}
		// The door towards the square, a window on each side.
		b.water(hx + dx * 4, 1, hz, hx + dx * 4, 2, hz);
		b.water(hx, 1, hz + dz * 4, hx, 2, hz + dz * 4);
		b.set(hx - dx * 4, 3, hz, joined(Blocks.GLASS_PANE, false));
		b.set(hx, 3, hz - dz * 4, joined(Blocks.GLASS_PANE, true));
		b.set(hx, 6, hz, lantern(false, true));
		b.chest(hx - dx * 3, 1, hz - dz * 3, Direction.NORTH, index == 3 ? "vault" : "ruin");
		b.bed(hx + dx * 2, 1, hz - dz * 3, dz > 0 ? Direction.SOUTH : Direction.NORTH, vanilla("white_bed"));
		b.set(hx - dx * 3, 1, hz + dz * 2, Blocks.CRAFTING_TABLE);
		b.set(hx - dx * 3, 1, hz, Blocks.BOOKSHELF);
	}

	// --- the Titan's Skull -------------------------------------------------------------------

	/** The skull of a titan, half sunk in the floor, its eyes lit from within, a hoard in its jaws. */
	static void titanSkull(DeepBuilder b) {
		b.padRound(0, 0, 18.5, b.mix(s(Blocks.GRAVEL), s(Blocks.CALCITE), s(Blocks.GRAVEL)), s(Blocks.CALCITE), 30);
		DeepBuilder.Paint bone = (x, y, z) -> b.chance(x, y, z, 21, 0.25) ? s(Blocks.CALCITE) : b.chance(x, y, z, 22, 0.12)
				? s(Blocks.DRIPSTONE_BLOCK) : s(Blocks.BONE_BLOCK);
		b.ellipsoid(0, 3, 0, 14.0, 15.0, 13.0, bone, true, -4);
		// The eye sockets, lit from within.
		for (int side = -1; side <= 1; side += 2) {
			b.sphere(side * 6, 11, -10, 3.4, WATER, false, -4);
			b.sphere(side * 6, 11, -6, 1.6, s(Blocks.SHROOMLIGHT), false, -2);
		}
		// The nose.
		for (int y = 4; y <= 7; y++) {
			int half = (7 - y) / 2;
			b.fill(-half, y, -14, half, y, -10, WATER);
		}
		// The jaws, open at the ground, with teeth above and below.
		b.water(-8, 1, -14, 8, 3, -8);
		for (int x = -7; x <= 7; x += 2) {
			b.set(x, 3, -13, dripstone(true));
			b.set(x + 1, 1, -13, dripstone(false));
		}
		// The hoard inside.
		b.hoard(0, 1, 3, 4.5, s(Blocks.GOLD_BLOCK), s(Blocks.GOLD_BLOCK), s(Blocks.RAW_GOLD_BLOCK), s(Blocks.GOLD_BLOCK), s(Blocks.DIAMOND_BLOCK));
		b.chest(-6, 1, 4, Direction.EAST, "legendary");
		b.chest(6, 1, 4, Direction.WEST, "legendary");
		b.chest(0, 1, 9, Direction.NORTH, "heads");
		for (int[] c : new int[][] {{-8, 0}, {8, 0}, {-4, 9}, {4, 9}}) {
			b.set(c[0], 1, c[1], b.head(c[0], 1, c[1]));
			b.set(c[0], 6, c[1], Blocks.SEA_LANTERN);
		}
		b.spawner(0, 0, -3, "bone_shark");
	}

	// --- the Great Vault ---------------------------------------------------------------------

	/**
	 * The vault of a drowned empire: a round door in a stone face, a stair down, and a hall under
	 * the sea floor heaped with gold, diamond and netherite, wings, weapons and a table of the deep.
	 */
	static void greatVault(DeepBuilder b) {
		BlockState plate = s(Blocks.POLISHED_DEEPSLATE);
		DeepBuilder.Paint tiles = b.mix(s(Blocks.DEEPSLATE_TILES), plate, s(Blocks.DEEPSLATE_TILES));
		b.pad(-14, -18, 14, 18, b.mix(s(Blocks.DEEPSLATE_TILES), s(Blocks.GRAVEL)), s(Blocks.COBBLED_DEEPSLATE), 16);
		// The face, with its great round door rolled half aside.
		b.fill(-10, 1, -12, 10, 12, -8, tiles);
		for (int x = -5; x <= 5; x++) {
			for (int y = 1; y <= 11; y++) {
				double d = Math.sqrt(x * x + (y - 6) * (y - 6));
				if (d <= 5.0) {
					b.set(x, y, -12, d > 4.0 ? s(Blocks.GOLD_BLOCK) : x > 1 ? s(Blocks.IRON_BLOCK) : WATER);
					if (d <= 4.0) {
						b.set(x, y, -11, WATER);
						b.set(x, y, -10, WATER);
					}
				}
			}
		}
		b.fill(2, 3, -13, 2, 9, -13, Blocks.IRON_BLOCK);
		b.set(3, 6, -13, Blocks.GOLD_BLOCK);
		b.water(-3, 1, -9, 3, 6, -8);
		for (int x = -9; x <= 9; x += 6) {
			b.set(x, 11, -12, Blocks.SEA_LANTERN);
		}
		// The hall below.
		b.shell(-12, -16, -7, 12, -2, 16, tiles);
		b.water(-11, -15, -6, 11, -3, 15);
		for (int x = -9; x <= 9; x += 6) {
			for (int z = -4; z <= 14; z += 6) {
				b.set(x, -2, z, Blocks.SEA_LANTERN);
			}
		}
		// The stair down.
		for (int k = 0; k <= 14; k++) {
			int z = -8 + k;
			b.water(-2, -k, z, 2, -k + 4, z);
			b.fill(-2, -k - 1, z, 2, -k - 1, z, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.SOUTH, false));
		}
		b.fill(-11, -16, -6, 11, -16, 15, (x, y, z) -> x % 4 == 0 && z % 4 == 0 ? s(Blocks.GOLD_BLOCK)
				: (x + z) % 2 == 0 ? s(Blocks.POLISHED_DEEPSLATE) : s(Blocks.DEEPSLATE_TILES));
		// The strongrooms down both sides, behind bars.
		for (int side = -1; side <= 1; side += 2) {
			for (int z = 0; z <= 10; z += 5) {
				b.fill(side * 6, -15, z - 2, side * 6, -12, z + 2, joined(Blocks.IRON_BARS, false));
				b.water(side * 6, -15, z, side * 6, -14, z);
				b.hoard(side * 9, -15, z, 2.2, s(Blocks.GOLD_BLOCK), s(Blocks.RAW_GOLD_BLOCK), s(Blocks.EMERALD_BLOCK), s(Blocks.GOLD_BLOCK));
				b.chest(side * 10, -15, z + 2, side < 0 ? Direction.EAST : Direction.WEST, z == 5 ? "legendary" : "treasury");
			}
		}
		// The treasures of the empire at the end of the hall.
		b.fill(-5, -15, 12, 5, -15, 15, plate);
		b.set(0, -14, 13, ModBlocks.ABYSSAL_ENCHANTING_TABLE);
		b.set(-3, -14, 14, Blocks.NETHERITE_BLOCK);
		b.set(3, -14, 14, Blocks.NETHERITE_BLOCK);
		b.set(-4, -14, 14, Blocks.DIAMOND_BLOCK);
		b.set(4, -14, 14, Blocks.DIAMOND_BLOCK);
		b.frame(0, -11, 15, Direction.NORTH, Items.ELYTRA);
		b.frame(-2, -11, 15, Direction.NORTH, weapon(anyWeapon(b)));
		b.frame(2, -11, 15, Direction.NORTH, Items.TOTEM_OF_UNDYING);
		b.set(0, -10, 15, Blocks.SEA_LANTERN);
		b.chest(-5, -14, 15, Direction.NORTH, "legendary");
		b.chest(5, -14, 15, Direction.NORTH, "legendary");
		b.spawner(0, -16, 4, EntityTypes.DROWNED);
		b.spawner(-3, -16, 10, "pale_lurker");
	}

	// --- the Temple of the Deep --------------------------------------------------------------

	/**
	 * The great temple of the drowned people, ringed with columns, its sanctum guarded and lit by
	 * a living conduit whose power reaches every diver nearby.
	 */
	static void templeOfTheDeep(DeepBuilder b) {
		b.pad(-18, -18, 18, 18, b.mix(s(Blocks.PRISMARINE), s(Blocks.GRAVEL)), s(Blocks.COBBLED_DEEPSLATE), 26);
		b.fill(-16, 1, -16, 16, 1, 16, (x, y, z) -> (Math.abs(x) + Math.abs(z)) % 4 == 0 ? s(Blocks.DARK_PRISMARINE) : s(Blocks.PRISMARINE_BRICKS));
		// The colonnade.
		for (int t = -15; t <= 15; t += 5) {
			for (int[] c : new int[][] {{t, -15}, {t, 15}, {-15, t}, {15, t}}) {
				b.pillar(c[0], c[1], 2, 10, s(Blocks.DARK_PRISMARINE));
				b.set(c[0], 11, c[1], Blocks.SEA_LANTERN);
			}
		}
		b.walls(-15, 12, -15, 15, 12, 15, s(Blocks.PRISMARINE_BRICKS));
		// The temple, its roof in steps.
		b.shell(-9, 1, -9, 9, 14, 9, DeepBuilder.banded(s(Blocks.PRISMARINE_BRICKS), s(Blocks.DARK_PRISMARINE), 4));
		for (int k = 0; k <= 5; k++) {
			int half = 9 - k;
			b.walls(-half, 15 + k, -half, half, 15 + k, half, s(Blocks.PRISMARINE_BRICKS));
			for (int[] c : new int[][] {{-half, -half}, {half, -half}, {-half, half}, {half, half}}) {
				b.set(c[0], 15 + k, c[1], Blocks.SEA_LANTERN);
			}
		}
		b.fill(-3, 21, -3, 3, 21, 3, Blocks.DARK_PRISMARINE);
		b.set(0, 22, 0, Blocks.SEA_LANTERN);
		for (Direction door : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			int x = door.getStepX() * 9;
			int z = door.getStepZ() * 9;
			b.water(x == 0 ? -1 : x, 2, z == 0 ? -1 : z, x == 0 ? 1 : x, 5, z == 0 ? 1 : z);
		}
		for (int y = 4; y <= 12; y += 4) {
			for (int t = -6; t <= 6; t += 6) {
				b.set(t, y, -9, Blocks.SEA_LANTERN);
				b.set(t, y, 9, Blocks.SEA_LANTERN);
			}
		}
		// The sanctum: a conduit in its frame above, a table of the deep and the temple treasure below.
		b.conduit(0, 9, 0);
		b.fill(-2, 2, 3, 2, 2, 6, Blocks.DARK_PRISMARINE);
		b.set(0, 3, 4, ModBlocks.ABYSSAL_ENCHANTING_TABLE);
		b.chest(-5, 2, 6, Direction.EAST, "legendary");
		b.chest(5, 2, 6, Direction.WEST, "legendary");
		b.chest(-5, 2, -6, Direction.EAST, "vault");
		b.set(5, 2, -6, Blocks.GOLD_BLOCK);
		b.set(5, 3, -6, Blocks.GOLD_BLOCK);
		b.spawner(-12, 1, 0, EntityTypes.GUARDIAN);
		b.spawner(12, 1, 0, EntityTypes.GUARDIAN);
	}

	// --- the Leviathan Graveyard -------------------------------------------------------------

	/** Where the great beasts of the sea come to die: their bones, and what they swallowed. */
	static void leviathanGraveyard(DeepBuilder b) {
		b.padRound(0, 0, 24.5, b.mix(s(Blocks.GRAVEL), s(Blocks.CALCITE), s(Blocks.GRAVEL), s(Blocks.BONE_BLOCK)), s(Blocks.CALCITE), 24);
		skeleton(b, -10, -12, 0.35, 22, 7);
		skeleton(b, 8, 4, 2.2, 26, 9);
		skeleton(b, -12, 13, -1.1, 16, 5);
		// What they swallowed.
		b.chest(-9, 1, -4, Direction.EAST, "legendary");
		b.chest(4, 1, 1, Direction.WEST, "vault");
		b.chest(-12, 1, 12, Direction.NORTH, "treasury");
		for (int i = 0; i < 14; i++) {
			int x = b.layout.nextInt(41) - 20;
			int z = b.layout.nextInt(41) - 20;
			b.set(x, 1, z, i % 3 == 0 ? b.head(x, 1, z) : i % 3 == 1 ? s(Blocks.GOLD_BLOCK) : s(Blocks.BONE_BLOCK));
		}
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			b.lampPost((int) Math.round(Math.cos(a) * 20), (int) Math.round(Math.sin(a) * 20), 1, 3, s(Blocks.BONE_BLOCK), lantern(true, false));
		}
		b.keeper(0, 8, 0, "bonewarden");
	}

	/** The bones of one great beast: a spine, a cage of ribs and a skull. */
	private static void skeleton(DeepBuilder b, int x0, int z0, double angle, int length, int height) {
		double ax = Math.cos(angle);
		double az = Math.sin(angle);
		BlockState bone = s(Blocks.BONE_BLOCK);
		for (int t = 0; t <= length; t++) {
			double f = t / (double) length;
			int y = 1 + (int) Math.round(height * Math.sin(Math.PI * Math.min(1.0, f * 1.2)) * 0.9);
			int x = x0 + (int) Math.round(ax * (t - length / 2.0));
			int z = z0 + (int) Math.round(az * (t - length / 2.0));
			b.set(x, Math.max(1, y), z, bone);
			// Ribs either side of the middle of the spine, arching down to the ground.
			if (t % 2 == 0 && f > 0.2 && f < 0.75) {
				for (int side = -1; side <= 1; side += 2) {
					for (int k = 1; k <= 8; k++) {
						double a = Math.PI / 2 * k / 8;
						double out = Math.sin(a) * (height * 0.8);
						int rx = x + (int) Math.round(-az * side * out);
						int rz = z + (int) Math.round(ax * side * out);
						int ry = Math.max(1, y - (int) Math.round((1.0 - Math.cos(a)) * (y - 1)));
						b.set(rx, ry, rz, bone);
					}
				}
			}
		}
		// The skull at the head end.
		int hx = x0 + (int) Math.round(ax * (length / 2.0 + 2));
		int hz = z0 + (int) Math.round(az * (length / 2.0 + 2));
		b.ellipsoid(hx, 2, hz, 2.6, 2.4, 2.6, (x, y, z) -> b.chance(x, y, z, 23, 0.3) ? s(Blocks.CALCITE) : s(Blocks.BONE_BLOCK), true, -1);
		b.set(hx, 3, hz, lantern(true, false));
	}

	// --- the Abyssal Gate --------------------------------------------------------------------

	/** A gate of obsidian taller than anything in the sea, leading nowhere. Something waits in it. */
	static void abyssalGate(DeepBuilder b) {
		DeepBuilder.Paint obsidian = (x, y, z) -> y % 7 == 0 ? s(Blocks.GILDED_BLACKSTONE) : b.chance(x, y, z, 24, 0.3)
				? s(Blocks.CRYING_OBSIDIAN) : s(Blocks.OBSIDIAN);
		b.pad(-18, -26, 18, 12, b.mix(s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.BLACKSTONE), s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)),
				s(Blocks.BLACKSTONE), 34);
		// The pillars and the lintel.
		for (int side = -1; side <= 1; side += 2) {
			b.fill(side * 9, 1, -2, side * 13, 26, 2, obsidian);
			// An alcove in each pillar with a chest.
			b.water(side * 11, 1, -2, side * 11, 3, -1);
			b.chest(side * 11, 1, -1, Direction.NORTH, "legendary");
			b.set(side * 11, 4, -2, Blocks.SHROOMLIGHT);
		}
		b.fill(-13, 27, -2, 13, 30, 2, obsidian);
		for (int k = 0; k <= 4; k++) {
			b.fill(-8 + k * 2, 31 + k, -1, 8 - k * 2, 31 + k, 1, obsidian);
		}
		b.set(0, 36, 0, Blocks.SEA_LANTERN);
		// Chains with soul lanterns hanging in the gateway, and the eye above them.
		for (int x = -6; x <= 6; x += 3) {
			int length = 6 + (int) (b.rand(x, 0, 0, 25) * 8);
			b.pillar(x, 0, 26 - length, 26, axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			b.set(x, 25 - length, 0, lantern(true, true));
		}
		b.sphere(0, 21, 0, 2.6, s(Blocks.CRYING_OBSIDIAN), false, -3);
		b.set(0, 21, -2, Blocks.SHROOMLIGHT);
		b.set(0, 21, -3, Blocks.SHROOMLIGHT);
		// The road up to it, lined with heads on pikes.
		for (int z = -24; z <= -4; z += 3) {
			b.pike(-3, z, 1, 3, Blocks.POLISHED_BLACKSTONE_WALL);
			b.pike(3, z, 1, 3, Blocks.POLISHED_BLACKSTONE_WALL);
		}
		for (int z = -22; z <= -6; z += 8) {
			b.set(-3, 0, z + 1, Blocks.SHROOMLIGHT);
			b.set(3, 0, z + 1, Blocks.SHROOMLIGHT);
		}
		b.hoard(0, 1, 6, 3.2, s(Blocks.GOLD_BLOCK), s(Blocks.RAW_GOLD_BLOCK), s(Blocks.ANCIENT_DEBRIS), s(Blocks.GOLD_BLOCK));
		b.keeper(0, 10, -6, "hollow_king");
	}

	// --- the Coral Tower ---------------------------------------------------------------------

	/** A tower of living coral rising forty blocks, a treasure on every floor, wings at the top. */
	static void coralTower(DeepBuilder b) {
		b.padRound(0, 0, 14.5, b.mix(s(Blocks.SAND), s(Blocks.SAND), s(Blocks.GRAVEL)), s(Blocks.SANDSTONE), 44);
		DeepBuilder.Paint wall = (x, y, z) -> y % 6 == 0 ? s(Blocks.PRISMARINE_BRICKS) : coral(b, x, y, z);
		b.cylinder(0, 0, 6.0, 1, 35, wall, true);
		b.water(-1, 1, -6, 1, 3, -5);
		for (int level = 0; level < 6; level++) {
			int y = level * 6;
			if (level > 0) {
				b.disc(0, y, 0, 5.2, s(Blocks.PRISMARINE_BRICKS));
				// A way up to the next floor, on alternating sides.
				int hx = level % 2 == 0 ? 3 : -3;
				b.fill(hx - 1, y, -1, hx + 1, y, 1, WATER);
			}
			if (level < 5) {
				b.set(0, y + 5, 0, lantern(false, true));
			}
			for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
				if (level > 0) {
					b.set(d.getStepX() * 6, y + 3, d.getStepZ() * 6, Blocks.SEA_LANTERN);
				}
			}
			if (level > 0 && level < 5) {
				b.chest(level % 2 == 0 ? -4 : 4, y + 1, 2, level % 2 == 0 ? Direction.EAST : Direction.WEST, "vault");
			}
		}
		// The crown: a glass dome, and the wings on a golden pillar under it.
		b.sphere(0, 36, 0, 6.0, (x, y, z) -> b.chance(x, y, z, 26, 0.12) ? s(Blocks.SEA_LANTERN) : s(Blocks.GLASS), true, 0);
		b.pillar(0, 0, 31, 32, s(Blocks.GOLD_BLOCK));
		b.frame(0, 32, -1, Direction.NORTH, Items.ELYTRA);
		b.chest(0, 31, 3, Direction.NORTH, "legendary");
		b.set(0, 42, 0, Blocks.SEA_LANTERN);
		// Branches of coral and glowing pickles round the base.
		for (int i = 0; i < 30; i++) {
			double a = b.layout.nextDouble() * Math.PI * 2;
			double r = 7.0 + b.layout.nextDouble() * 6.5;
			int x = (int) Math.round(Math.cos(a) * r);
			int z = (int) Math.round(Math.sin(a) * r);
			int h = 1 + b.layout.nextInt(4);
			b.pillar(x, z, 1, h, coral(b, x, 0, z));
			b.set(x, h + 1, z, b.pick(x, h, z, 27, pickles(4), s(Blocks.TUBE_CORAL), s(Blocks.FIRE_CORAL), s(Blocks.BRAIN_CORAL_FAN)));
		}
	}

	// --- the Sea Dragon's Nest ---------------------------------------------------------------

	/** The nest of something enormous: a ring of mud, bone and wreckage round its eggs and its gold. */
	static void seaDragonNest(DeepBuilder b) {
		b.padRound(0, 0, 18.5, b.mix(s(Blocks.MUD), s(Blocks.GRAVEL)), s(Blocks.PACKED_MUD), 16);
		DeepBuilder.Paint nest = b.mix(s(Blocks.MUD), s(Blocks.PACKED_MUD), s(Blocks.BONE_BLOCK), s(Blocks.DRIED_KELP_BLOCK),
				axis(Blocks.DARK_OAK_LOG, Direction.Axis.X), axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z), s(Blocks.MUD));
		for (int x = -16; x <= 16; x++) {
			for (int z = -16; z <= 16; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r < 7.0 || r > 16.0 || !b.column(x, z)) {
					continue;
				}
				int h = (int) Math.round(7.0 * Math.sin(Math.PI * (r - 7.0) / 9.0) + b.rand(x, 0, z, 28) * 1.5);
				if (h > 0) {
					b.pillar(x, z, 1, h, nest);
				}
			}
		}
		// The hoard, and the eggs lying on it.
		b.hoard(0, 1, 0, 6.0, s(Blocks.GOLD_BLOCK), s(Blocks.RAW_GOLD_BLOCK), s(Blocks.GOLD_BLOCK), s(Blocks.EMERALD_BLOCK), s(Blocks.GOLD_BLOCK));
		int[][] eggs = {{-2, -2}, {3, -1}, {0, 3}, {-3, 2}, {2, 3}};
		for (int[] egg : eggs) {
			b.ellipsoid(egg[0], 5, egg[1], 1.6, 2.3, 1.6, (x, y, z) -> b.chance(x, y, z, 29, 0.2) ? s(Blocks.CALCITE)
					: b.chance(x, y, z, 30, 0.08) ? s(Blocks.SEA_LANTERN) : s(ModBlocks.PEARL_BLOCK), false, -2);
		}
		b.chest(-5, 1, -4, Direction.EAST, "legendary");
		b.chest(5, 1, 4, Direction.WEST, "legendary");
		b.chest(0, 1, -6, Direction.SOUTH, "treasury");
		// What the dragon brought home: bones and the heads of its prey round the rim.
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6;
			int x = (int) Math.round(Math.cos(a) * 17.5);
			int z = (int) Math.round(Math.sin(a) * 17.5);
			if (i % 2 == 0) {
				b.pike(x, z, 1, 2, Blocks.BONE_BLOCK);
			} else {
				b.lampPost(x, z, 1, 2, s(Blocks.BONE_BLOCK), s(Blocks.SEA_LANTERN));
			}
		}
		b.keeper(0, 12, 0, "twin_terror");
	}

	// --- the Mining Outpost ------------------------------------------------------------------

	/** A drowned mine: an open pit stepping down into rock veined with diamond and ancient debris. */
	static void miningOutpost(DeepBuilder b) {
		b.padRound(0, 0, 24.0, b.mix(s(Blocks.GRAVEL), s(Blocks.COBBLED_DEEPSLATE), s(Blocks.GRAVEL)), s(Blocks.COBBLED_DEEPSLATE), 22);
		// The pit, in terraces, its walls and floors rich with ore.
		for (int x = -14; x <= 14; x++) {
			for (int z = -14; z <= 14; z++) {
				if (!b.column(x, z)) {
					continue;
				}
				int depth = pitDepth(x, z);
				if (depth == 0) {
					continue;
				}
				for (int y = -depth + 1; y <= 0; y++) {
					b.set(x, y, z, WATER);
				}
				// The floor of this terrace, and its face where the next terrace drops away.
				int deepest = Math.max(Math.max(pitDepth(x + 1, z), pitDepth(x - 1, z)), Math.max(pitDepth(x, z + 1), pitDepth(x, z - 1)));
				for (int y = Math.min(-depth, -deepest + 1); y <= -depth; y++) {
					b.set(x, y, z, ore(b, x, y, z));
				}
			}
		}
		for (int x = -14; x <= 14; x++) {
			for (int z = -14; z <= 14; z++) {
				if (pitDepth(x, z) == 0 && b.column(x, z)) {
					int deepest = Math.max(Math.max(pitDepth(x + 1, z), pitDepth(x - 1, z)), Math.max(pitDepth(x, z + 1), pitDepth(x, z - 1)));
					for (int y = -deepest + 1; y <= 0; y++) {
						b.set(x, y, z, ore(b, x, y, z));
					}
				}
			}
		}
		// The headframe and its crane over the pit.
		for (int[] c : new int[][] {{12, -12}, {15, -12}, {12, -15}, {15, -15}}) {
			b.pillar(c[0], c[1], 1, 16, axis(Blocks.SPRUCE_LOG, Direction.Axis.Y));
		}
		for (int y = 6; y <= 16; y += 5) {
			b.fill(12, y, -15, 15, y, -12, slab(Blocks.SPRUCE_SLAB, false));
		}
		b.line(13, 17, -13, 3, 17, -3, axis(Blocks.SPRUCE_LOG, Direction.Axis.X));
		b.pillar(3, -3, -6, 16, axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
		b.set(3, -7, -3, Blocks.CAULDRON);
		b.set(14, 17, -14, Blocks.SEA_LANTERN);
		// Two shacks, the ore that came out, and the lamps round the rim.
		shack(b, -17, 7);
		shack(b, 16, 9);
		b.hoard(-16, 1, -10, 2.5, s(Blocks.RAW_IRON_BLOCK), s(Blocks.RAW_GOLD_BLOCK), s(Blocks.COAL_BLOCK), s(vanilla("raw_copper_block")));
		b.hoard(10, 1, 19, 2.0, s(Blocks.RAW_GOLD_BLOCK), s(Blocks.DIAMOND_BLOCK), s(Blocks.RAW_IRON_BLOCK), s(Blocks.ANCIENT_DEBRIS));
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + 0.3;
			b.lampPost((int) Math.round(Math.cos(a) * 16), (int) Math.round(Math.sin(a) * 16), 1, 3, s(Blocks.SPRUCE_FENCE), lantern(false, false));
		}
		b.fill(-20, 1, -4, -19, 2, -3, Blocks.TNT);
		b.chest(0, -17, 0, Direction.NORTH, "mine");
		b.spawner(2, -18, 2, "pale_lurker");
	}

	/** How deep the pit is at a column: four terraces, deepest in the middle. */
	private static int pitDepth(int x, int z) {
		double r = Math.sqrt(x * x + z * z);
		return r < 4.0 ? 18 : r < 7.0 ? 14 : r < 10.0 ? 9 : r < 13.0 ? 4 : 0;
	}

	private static BlockState ore(DeepBuilder b, int x, int y, int z) {
		double r = b.rand(x, y, z, 32);
		return r < 0.07 ? s(Blocks.ANCIENT_DEBRIS) : r < 0.16 ? s(Blocks.DEEPSLATE_DIAMOND_ORE) : r < 0.25 ? s(Blocks.DEEPSLATE_GOLD_ORE)
				: r < 0.28 ? s(Blocks.DEEPSLATE_EMERALD_ORE) : r < 0.38 ? s(Blocks.DEEPSLATE_IRON_ORE) : r < 0.42 ? s(Blocks.DEEPSLATE_LAPIS_ORE)
						: r < 0.7 ? s(Blocks.DEEPSLATE) : s(Blocks.TUFF);
	}

	private static void shack(DeepBuilder b, int sx, int sz) {
		b.shell(sx - 3, 0, sz - 3, sx + 3, 5, sz + 3, b.worn(s(Blocks.SPRUCE_PLANKS), s(Blocks.STRIPPED_SPRUCE_LOG)));
		b.fill(sx - 4, 6, sz - 4, sx + 4, 6, sz + 4, slab(Blocks.SPRUCE_SLAB, false));
		b.water(sx, 1, sz - 3, sx, 2, sz - 3);
		b.water(sx, 1, sz + 3, sx, 2, sz + 3);
		b.barrel(sx - 2, 1, sz + 2, Direction.UP, "mine");
		b.barrel(sx + 2, 1, sz + 2, Direction.UP, "ruin");
		b.chest(sx - 2, 1, sz - 2, Direction.EAST, "mine");
		b.set(sx, 4, sz, lantern(false, true));
	}

	// --- the Laboratory ----------------------------------------------------------------------

	/** A research station of glass domes joined by tunnels, where someone studied the deep, and its table. */
	static void laboratory(DeepBuilder b) {
		b.padRound(0, 0, 21.5, b.mix(s(Blocks.SAND), s(Blocks.GRAVEL)), s(Blocks.SANDSTONE), 14);
		int[][] domes = {{0, -11}, {-11, 8}, {11, 8}};
		// Glass tunnels between the domes; their insides are cut once the domes are up.
		for (int i = 0; i < 3; i++) {
			tunnel(b, domes[i], domes[(i + 1) % 3], false);
		}
		for (int[] d : domes) {
			b.cylinder(d[0], d[1], 6.5, 0, 1, s(Blocks.IRON_BLOCK), false);
			b.sphere(d[0], 1, d[1], 6.5, (x, y, z) -> b.chance(x, y, z, 33, 0.08) ? lit(vanilla("waxed_copper_bulb")) : s(Blocks.GLASS), true, 1);
			b.sphere(d[0], 1, d[1], 5.5, WATER, false, 1);
			b.disc(d[0], 1, d[1], 5.5, s(vanilla("white_concrete")));
			b.set(d[0], 7, d[1], Blocks.SEA_LANTERN);
		}
		for (int i = 0; i < 3; i++) {
			tunnel(b, domes[i], domes[(i + 1) % 3], true);
		}
		// The way in.
		b.water(0, 2, -17, 0, 3, -17);
		// North dome: the potions laboratory.
		int[] n = domes[0];
		b.set(n[0] - 3, 2, n[1], Blocks.BREWING_STAND);
		b.set(n[0] + 3, 2, n[1], Blocks.BREWING_STAND);
		b.set(n[0] - 2, 2, n[1] + 2, Blocks.WATER_CAULDRON);
		b.set(n[0] + 2, 2, n[1] + 2, Blocks.WATER_CAULDRON);
		b.chest(n[0] - 1, 2, n[1] - 4, Direction.SOUTH, "lab");
		b.chest(n[0] + 1, 2, n[1] - 4, Direction.SOUTH, "lab");
		b.fill(n[0] - 4, 2, n[1] - 2, n[0] - 4, 3, n[1] - 1, Blocks.BOOKSHELF);
		// West dome: the specimen tank.
		int[] w = domes[1];
		b.fill(w[0] - 1, 2, w[1] - 1, w[0] + 1, 6, w[1] + 1, Blocks.GLASS);
		b.water(w[0], 3, w[1], w[0], 5, w[1]);
		b.spawner(w[0], 2, w[1], "rotfin");
		b.chest(w[0] - 3, 2, w[1] + 3, Direction.EAST, "lab");
		b.set(w[0] + 3, 2, w[1] + 3, facing(Blocks.LECTERN, Direction.WEST));
		// East dome: the table of the deep they were trying to understand.
		int[] e = domes[2];
		b.fill(e[0] - 1, 2, e[1] - 1, e[0] + 1, 2, e[1] + 1, Blocks.POLISHED_DEEPSLATE);
		b.set(e[0], 3, e[1], ModBlocks.ABYSSAL_ENCHANTING_TABLE);
		for (int dx = -3; dx <= 3; dx += 6) {
			b.fill(e[0] + dx, 2, e[1] - 2, e[0] + dx, 3, e[1] + 2, Blocks.BOOKSHELF);
		}
		b.chest(e[0], 2, e[1] + 4, Direction.NORTH, "legendary");
	}

	/** A glass tunnel from one dome to another: its shell, or the water inside it. */
	private static void tunnel(DeepBuilder b, int[] from, int[] to, boolean inside) {
		int steps = (int) Math.ceil(Math.sqrt((to[0] - from[0]) * (to[0] - from[0]) + (to[1] - from[1]) * (to[1] - from[1])));
		for (int t = 0; t <= steps; t++) {
			int x = from[0] + Math.round((to[0] - from[0]) * t / (float) steps);
			int z = from[1] + Math.round((to[1] - from[1]) * t / (float) steps);
			double toFrom = Math.sqrt((x - from[0]) * (x - from[0]) + (z - from[1]) * (z - from[1]));
			double toTo = Math.sqrt((x - to[0]) * (x - to[0]) + (z - to[1]) * (z - to[1]));
			if (inside) {
				if (toFrom > 4.5 && toTo > 4.5) {
					b.fill(x - 1, 2, z - 1, x + 1, 4, z + 1, WATER);
				} else if (toFrom > 3.0 && toTo > 3.0) {
					b.fill(x, 2, z, x, 3, z, WATER);
				}
			} else if (toFrom > 5.5 && toTo > 5.5) {
				b.fill(x - 2, 1, z - 2, x + 2, 5, z + 2, s(Blocks.GLASS));
				b.fill(x - 2, 1, z - 2, x + 2, 1, z + 2, s(Blocks.IRON_BLOCK));
			}
		}
	}

	// --- the Royal Tomb ----------------------------------------------------------------------

	/** The tomb of the last king of the drowned people, on its stepped mound, his treasure about him. */
	static void royalTomb(DeepBuilder b) {
		BlockState bricks = s(Blocks.POLISHED_BLACKSTONE_BRICKS);
		DeepBuilder.Paint wall = (x, y, z) -> y % 5 == 0 ? s(Blocks.GILDED_BLACKSTONE) : b.chance(x, y, z, 34, 0.15)
				? s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : bricks;
		b.pad(-19, -22, 19, 19, b.mix(s(Blocks.BLACKSTONE), s(Blocks.GRAVEL)), s(Blocks.BLACKSTONE), 34);
		// The stepped mound.
		int[] halves = {16, 13, 10};
		for (int i = 0; i < 3; i++) {
			int half = halves[i];
			b.fill(-half, 1 + i * 2, -half, half, 2 + i * 2, half, s(Blocks.POLISHED_BLACKSTONE));
			b.walls(-half, 2 + i * 2, -half, half, 2 + i * 2, half, s(Blocks.GILDED_BLACKSTONE));
		}
		for (int y = 1; y <= 6; y++) {
			int z = -23 + y * 2;
			if (y > 1) {
				b.fill(-2, 1, z, 2, y - 1, z + 1, bricks);
			}
			b.fill(-2, y, z, 2, y, z + 1, stairs(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Direction.SOUTH, false));
			b.fill(-2, y + 1, z, 2, y + 4, z + 1, WATER);
		}
		// The tomb itself.
		b.shell(-9, 6, -9, 9, 18, 9, wall);
		b.water(-2, 7, -9, 2, 12, -9);
		for (int x = -8; x <= 8; x += 4) {
			if (Math.abs(x) > 2) {
				b.pillar(x, -10, 7, 16, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
				b.set(x, 7, -10, Blocks.GILDED_BLACKSTONE);
			}
		}
		b.fill(-9, 17, -11, 9, 17, -10, bricks);
		for (int k = 0; k <= 7; k++) {
			b.fill(-10 + k, 19 + k, -10 + k, 10 - k, 19 + k, 10 - k, k % 3 == 2 ? s(Blocks.GILDED_BLACKSTONE) : s(Blocks.BLACKSTONE));
		}
		b.set(0, 27, 0, Blocks.GOLD_BLOCK);
		b.set(0, 28, 0, Blocks.SEA_LANTERN);
		// Inside: the golden sarcophagus, the crowned skull, the treasure.
		b.fill(-1, 7, -2, 1, 8, 2, Blocks.GOLD_BLOCK);
		b.set(0, 9, -1, Blocks.WITHER_SKELETON_SKULL);
		for (int[] c : new int[][] {{-1, -2}, {1, -2}, {-1, 0}, {1, 0}}) {
			b.set(c[0], 9, c[1], Blocks.GOLD_BLOCK);
		}
		for (int[] c : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
			b.pillar(c[0], c[1], 7, 17, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
			b.set(c[0], 12, c[1] + (c[1] < 0 ? 1 : -1), Blocks.SEA_LANTERN);
		}
		b.chest(-7, 7, 8, Direction.EAST, "legendary");
		b.chest(7, 7, 8, Direction.WEST, "legendary");
		b.chest(-7, 7, -8, Direction.EAST, "tomb");
		b.chest(7, 7, -8, Direction.WEST, "tomb");
		b.frame(-2, 11, 8, Direction.NORTH, Items.TOTEM_OF_UNDYING);
		b.frame(2, 11, 8, Direction.NORTH, weapon(anyWeapon(b)));
		b.pillar(0, 8, 7, 10, s(Blocks.GOLD_BLOCK));
		b.set(0, 11, 8, Blocks.WITHER_SKELETON_SKULL);
		for (int x = -4; x <= 4; x += 8) {
			b.set(x, 17, 0, lantern(true, true));
		}
		b.spawner(-5, 6, 0, EntityTypes.DROWNED);
		b.spawner(5, 6, 0, EntityTypes.DROWNED);
	}
}
