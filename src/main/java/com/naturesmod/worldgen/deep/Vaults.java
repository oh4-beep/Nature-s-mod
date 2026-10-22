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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The vaults of the deep: drowned buildings and wrecks, each one full of riches. */
final class Vaults {
	private Vaults() {
	}

	private static AbyssWeapon.Kind anyWeapon(DeepBuilder b) {
		AbyssWeapon.Kind[] kinds = AbyssWeapon.Kind.values();
		return kinds[b.layout.nextInt(kinds.length)];
	}

	private static BlockState coral(DeepBuilder b, int x, int y, int z) {
		return b.pick(x, y, z, 31, s(Blocks.TUBE_CORAL_BLOCK), s(Blocks.BRAIN_CORAL_BLOCK), s(Blocks.BUBBLE_CORAL_BLOCK), s(Blocks.FIRE_CORAL_BLOCK),
				s(Blocks.HORN_CORAL_BLOCK));
	}

	/** A two-storey house of stone and timber, its family long gone, its strongbox still upstairs. */
	static void drownedHouse(DeepBuilder b) {
		DeepBuilder.Paint stone = b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS));
		b.pad(-8, -8, 8, 8, b.mix(s(Blocks.GRAVEL), s(Blocks.COARSE_DIRT), s(Blocks.GRAVEL)), s(Blocks.COBBLESTONE), 16);
		// Walls: stone below, timber above, logs at the corners.
		b.shell(-5, 0, -4, 5, 9, 4, stone);
		b.walls(-5, 5, -4, 5, 8, 4, s(Blocks.SPRUCE_PLANKS));
		for (int[] c : new int[][] {{-5, -4}, {5, -4}, {-5, 4}, {5, 4}}) {
			b.pillar(c[0], c[1], 1, 9, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
		}
		b.fill(-4, 0, -3, 4, 0, 3, Blocks.DARK_OAK_PLANKS);
		b.fill(-4, 5, -3, 4, 5, 3, Blocks.SPRUCE_PLANKS);
		b.water(3, 5, 1, 4, 5, 2);
		// The roof.
		for (int z = -5; z <= 5; z++) {
			int y = 10 + (5 - Math.abs(z)) / 2 * 1;
			for (int x = -6; x <= 6; x++) {
				b.fill(x, 10, z, x, y - 1, z, s(Blocks.SPRUCE_PLANKS));
				b.set(x, y, z, z == 0 ? s(Blocks.DARK_OAK_PLANKS) : stairs(Blocks.DARK_OAK_STAIRS, z < 0 ? Direction.SOUTH : Direction.NORTH, false));
			}
		}
		b.fill(-4, 9, -3, 4, 9, 3, WATER);
		b.fill(-4, 10, -3, 4, 11, 3, WATER);
		// Door and windows.
		b.water(0, 1, -4, 0, 2, -4);
		for (int x : new int[] {-3, 3}) {
			b.set(x, 2, -4, joined(Blocks.GLASS_PANE, true));
			b.set(x, 7, -4, joined(Blocks.GLASS_PANE, true));
			b.set(x, 7, 4, joined(Blocks.GLASS_PANE, true));
		}
		b.set(-5, 7, 0, joined(Blocks.GLASS_PANE, false));
		b.set(5, 2, 0, joined(Blocks.GLASS_PANE, false));
		// Downstairs: a table and chairs, a hearth, shelves.
		b.set(-2, 1, 1, Blocks.SPRUCE_FENCE);
		b.set(-2, 2, 1, slab(Blocks.SPRUCE_SLAB, false));
		b.set(-3, 1, 1, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
		b.set(-1, 1, 1, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
		b.set(-4, 1, 3, facing(Blocks.FURNACE, Direction.SOUTH));
		b.set(-3, 1, 3, Blocks.CRAFTING_TABLE);
		b.fill(0, 1, 3, 1, 3, 3, Blocks.BOOKSHELF);
		b.barrel(4, 1, -3, Direction.UP, "ruin");
		b.set(0, 4, 0, lantern(false, true));
		// The ladder up.
		for (int y = 1; y <= 5; y++) {
			b.set(4, y, 2, facing(Blocks.LADDER, Direction.WEST));
		}
		// Upstairs: beds and the strongbox.
		b.bed(-3, 6, -2, Direction.SOUTH, vanilla("blue_bed"));
		b.bed(-1, 6, -2, Direction.SOUTH, vanilla("blue_bed"));
		b.chest(-4, 6, 2, Direction.EAST, "vault");
		b.set(0, 6, 3, Blocks.BOOKSHELF);
		b.set(1, 11, 0, lantern(false, true));
		b.frame(-4, 7, 3, Direction.NORTH, Items.CLOCK);
	}

	/** A hall of books in the dark, and at its far end an Abyssal Enchanting Table. */
	static void ancientLibrary(DeepBuilder b) {
		DeepBuilder.Paint wall = b.worn(s(Blocks.DEEPSLATE_BRICKS), s(Blocks.CRACKED_DEEPSLATE_BRICKS), s(Blocks.DEEPSLATE_TILES));
		b.pad(-10, -9, 10, 9, b.mix(s(Blocks.COBBLED_DEEPSLATE), s(Blocks.GRAVEL)), s(Blocks.COBBLED_DEEPSLATE), 14);
		b.shell(-8, 0, -6, 8, 9, 7, wall);
		b.fill(-7, 0, -5, 7, 0, 6, Blocks.POLISHED_DEEPSLATE);
		b.fill(-1, 0, -5, 1, 0, 6, vanilla("red_wool"));
		// Pillars along the walls, and lamps in the roof.
		for (int z = -5; z <= 6; z += 3) {
			b.pillar(-8, z, 1, 8, s(Blocks.CHISELED_DEEPSLATE));
			b.pillar(8, z, 1, 8, s(Blocks.CHISELED_DEEPSLATE));
		}
		for (int x = -6; x <= 6; x += 4) {
			for (int z = -4; z <= 5; z += 3) {
				b.set(x, 9, z, Blocks.SEA_LANTERN);
			}
		}
		b.water(-1, 1, -6, 1, 3, -6);
		b.set(0, 4, -6, Blocks.CHISELED_DEEPSLATE);
		for (int x = -2; x <= 2; x += 4) {
			b.pillar(x, -6, 1, 5, s(Blocks.CHISELED_DEEPSLATE));
			b.set(x, 6, -6, Blocks.SEA_LANTERN);
			b.lampPost(x * 2, -9, 1, 3, s(Blocks.DEEPSLATE_BRICK_WALL), lantern(true, false));
		}
		// Tall windows down both sides, lit from within.
		for (int z = -4; z <= 5; z += 3) {
			b.fill(-8, 3, z, -8, 6, z, joined(Blocks.GLASS_PANE, false));
			b.fill(8, 3, z, 8, 6, z, joined(Blocks.GLASS_PANE, false));
		}
		// Rows of shelves either side of the aisle.
		for (int side = -1; side <= 1; side += 2) {
			for (int z = -4; z <= 3; z += 2) {
				b.fill(side * 3, 1, z, side * 7, 3, z, Blocks.BOOKSHELF);
			}
			b.set(side * 3, 1, 5, facing(Blocks.LECTERN, side < 0 ? Direction.EAST : Direction.WEST));
			b.set(side * 5, 4, 0, lantern(false, true));
		}
		// The table on its dais.
		b.fill(-2, 1, 4, 2, 1, 6, Blocks.POLISHED_DEEPSLATE);
		b.set(0, 2, 5, ModBlocks.ABYSSAL_ENCHANTING_TABLE);
		b.fill(-2, 2, 6, 2, 4, 6, Blocks.BOOKSHELF);
		b.set(-2, 2, 4, Blocks.AMETHYST_BLOCK);
		b.set(2, 2, 4, Blocks.AMETHYST_BLOCK);
		b.set(-2, 3, 4, facing(Blocks.AMETHYST_CLUSTER, Direction.UP));
		b.set(2, 3, 4, facing(Blocks.AMETHYST_CLUSTER, Direction.UP));
		b.chest(-7, 1, 6, Direction.EAST, "library");
		b.chest(7, 1, 6, Direction.WEST, "library");
		b.barrel(-7, 1, -5, Direction.UP, "library");
		b.barrel(7, 1, -5, Direction.UP, "ruin");
	}

	/** The armory of a drowned army: racks of weapons, suits of armor, and one blade of the deep. */
	static void armory(DeepBuilder b) {
		DeepBuilder.Paint wall = b.worn(s(Blocks.POLISHED_DEEPSLATE), s(Blocks.DEEPSLATE_BRICKS), s(Blocks.CRACKED_DEEPSLATE_BRICKS));
		b.pad(-9, -9, 9, 9, b.mix(s(Blocks.GRAVEL), s(Blocks.COBBLED_DEEPSLATE)), s(Blocks.COBBLED_DEEPSLATE), 12);
		b.shell(-7, 0, -6, 7, 7, 6, wall);
		b.fill(-6, 0, -5, 6, 0, 5, Blocks.STONE_BRICKS);
		for (int x = -7; x <= 7; x += 2) {
			b.set(x, 8, -6, Blocks.DEEPSLATE_BRICK_WALL);
			b.set(x, 8, 6, Blocks.DEEPSLATE_BRICK_WALL);
		}
		b.water(-1, 1, -6, 1, 3, -6);
		for (int x = -4; x <= 4; x += 4) {
			b.set(x, 6, 0, lantern(false, true));
		}
		// Weapons racked along both walls.
		ItemStack[] rack = {new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.TRIDENT), new ItemStack(Items.CROSSBOW),
				new ItemStack(Items.DIAMOND_AXE), new ItemStack(Items.BOW), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.MACE)};
		int i = 0;
		for (int z = -4; z <= 4; z += 2) {
			b.frame(-6, 3, z, Direction.EAST, rack[i++ % rack.length].copy());
			b.frame(6, 3, z, Direction.WEST, rack[i++ % rack.length].copy());
		}
		// The blade of the deep, alone on the end wall.
		b.set(0, 1, 5, Blocks.GOLD_BLOCK);
		b.frame(0, 3, 5, Direction.NORTH, weapon(anyWeapon(b)));
		b.set(-1, 3, 5, Blocks.SEA_LANTERN);
		b.set(1, 3, 5, Blocks.SEA_LANTERN);
		// Suits of armor.
		b.stand(-4, 1, 4, Direction.NORTH, new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
				new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS));
		b.stand(4, 1, 4, Direction.NORTH, new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
				new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS));
		b.stand(-4, 1, -3, Direction.SOUTH, new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.GOLDEN_CHESTPLATE),
				new ItemStack(Items.GOLDEN_LEGGINGS), new ItemStack(Items.GOLDEN_BOOTS));
		b.chest(-6, 1, 0, Direction.EAST, "armory");
		b.chest(6, 1, 0, Direction.WEST, "armory");
		b.set(4, 1, -4, facing(Blocks.ANVIL, Direction.EAST));
		b.set(3, 1, -4, Blocks.GRINDSTONE);
		b.set(5, 1, -4, Blocks.SMITHING_TABLE);
	}

	/** A strongroom of the old kings, its gate rusted open: gold to the ceiling, and netherite. */
	static void treasury(DeepBuilder b) {
		BlockState plate = s(Blocks.POLISHED_DEEPSLATE);
		b.pad(-9, -9, 9, 9, b.mix(s(Blocks.DEEPSLATE_TILES), s(Blocks.COBBLED_DEEPSLATE)), s(Blocks.COBBLED_DEEPSLATE), 12);
		b.shell(-6, 0, -6, 6, 7, 6, b.mix(plate, s(Blocks.DEEPSLATE_TILES), plate));
		b.shell(-5, 0, -5, 5, 6, 5, s(Blocks.IRON_BLOCK));
		b.fill(-4, 0, -4, 4, 0, 4, Blocks.POLISHED_DEEPSLATE);
		// The gate.
		b.water(-1, 1, -6, 1, 3, -5);
		b.fill(-1, 1, -6, 1, 3, -6, joined(Blocks.IRON_BARS, true));
		b.water(0, 1, -6, 0, 2, -6);
		b.set(-2, 4, -7, Blocks.SEA_LANTERN);
		b.set(2, 4, -7, Blocks.SEA_LANTERN);
		b.set(0, 4, -6, Blocks.GOLD_BLOCK);
		for (int x = -3; x <= 3; x += 3) {
			for (int z = -3; z <= 3; z += 3) {
				b.set(x, 6, z, Blocks.SEA_LANTERN);
			}
		}
		// Gold along the walls, gems in the corners, netherite in the middle.
		for (int z = -4; z <= 4; z++) {
			b.pillar(-4, z, 1, 1 + (int) (b.rand(-4, 0, z, 12) * 3), s(Blocks.GOLD_BLOCK));
			b.pillar(4, z, 1, 1 + (int) (b.rand(4, 0, z, 12) * 3), s(Blocks.GOLD_BLOCK));
		}
		b.set(-3, 1, 4, Blocks.EMERALD_BLOCK);
		b.set(-3, 2, 4, Blocks.EMERALD_BLOCK);
		b.set(3, 1, 4, Blocks.DIAMOND_BLOCK);
		b.set(3, 1, 3, Blocks.EMERALD_BLOCK);
		b.set(0, 1, 1, Blocks.POLISHED_DEEPSLATE);
		b.set(0, 2, 1, Blocks.NETHERITE_BLOCK);
		b.set(0, 1, 2, Blocks.DIAMOND_BLOCK);
		b.chest(-2, 1, 4, Direction.NORTH, "treasury");
		b.chest(2, 1, 4, Direction.NORTH, "treasury");
		b.chest(0, 1, 4, Direction.NORTH, "vault");
		b.spawner(0, 0, -8, "pale_lurker");
	}

	/** An open-air theatre: rows of seats, a stage, and the records of a thousand songs on the wall. */
	static void musicHall(DeepBuilder b) {
		b.pad(-12, -12, 12, 9, b.mix(s(Blocks.SAND), s(Blocks.GRAVEL)), s(Blocks.SANDSTONE), 12);
		// The stage.
		b.fill(-7, 0, 3, 7, 1, 7, Blocks.SMOOTH_QUARTZ);
		b.fill(-7, 1, 8, 7, 9, 8, Blocks.QUARTZ_BRICKS);
		b.fill(-7, 10, 8, 7, 10, 8, Blocks.CHISELED_QUARTZ_BLOCK);
		for (int x = -6; x <= 6; x += 3) {
			b.set(x, 1, 3, b.pick(x, 1, 3, 13, s(Blocks.OCHRE_FROGLIGHT), s(Blocks.VERDANT_FROGLIGHT), s(Blocks.PEARLESCENT_FROGLIGHT)));
		}
		// The records, hung in a gallery on the back wall.
		int d = b.layout.nextInt(Ruins.DISCS.length);
		for (int x = -5; x <= 5; x += 2) {
			for (int y = 4; y <= 7; y += 3) {
				b.frame(x, y, 7, Direction.NORTH, Ruins.DISCS[d++ % Ruins.DISCS.length]);
			}
		}
		b.set(-3, 2, 6, Blocks.JUKEBOX);
		b.set(3, 2, 6, Blocks.JUKEBOX);
		b.fill(-1, 2, 6, 1, 2, 6, Blocks.NOTE_BLOCK);
		b.set(0, 2, 4, Blocks.BELL);
		b.chest(-6, 2, 6, Direction.SOUTH, "discs");
		b.chest(6, 2, 6, Direction.SOUTH, "discs");
		// Rows of seats facing the stage, rising behind each other.
		for (int x = -11; x <= 11; x++) {
			for (int z = -11; z <= 0; z++) {
				double r = Math.sqrt(x * x + (z - 4) * (z - 4));
				int row = (int) ((r - 7.0) / 1.0);
				if (r < 7.0 || row > 4 || z > 0) {
					continue;
				}
				if (row > 0) {
					b.fill(x, 1, z, x, row, z, Blocks.STONE_BRICKS);
				}
				b.set(x, row + 1, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
			}
		}
		for (int side = -1; side <= 1; side += 2) {
			b.lampPost(side * 9, 7, 1, 4, s(Blocks.QUARTZ_PILLAR), s(Blocks.SEA_LANTERN));
		}
	}

	/** A gallery of heads: every kind of skull ever taken, each on its own plinth. */
	static void skullMuseum(DeepBuilder b) {
		b.pad(-10, -9, 10, 9, b.mix(s(Blocks.SMOOTH_STONE), s(Blocks.GRAVEL)), s(Blocks.STONE), 12);
		b.shell(-8, 0, -6, 8, 7, 6, b.worn(s(Blocks.QUARTZ_BRICKS), s(Blocks.SMOOTH_QUARTZ)));
		b.fill(-7, 7, -5, 7, 7, 5, Blocks.GLASS);
		b.fill(-7, 0, -5, 7, 0, 5, (x, y, z) -> (x + z) % 2 == 0 ? s(Blocks.POLISHED_ANDESITE) : s(Blocks.POLISHED_DIORITE));
		b.water(-1, 1, -6, 1, 3, -6);
		for (int x = -6; x <= 6; x += 4) {
			b.set(x, 6, -3, Blocks.SEA_LANTERN);
			b.set(x, 6, 3, Blocks.SEA_LANTERN);
		}
		// The plinths.
		Block[] heads = {Blocks.SKELETON_SKULL, Blocks.WITHER_SKELETON_SKULL, Blocks.ZOMBIE_HEAD,
				Blocks.CREEPER_HEAD, Blocks.PIGLIN_HEAD, Blocks.PLAYER_HEAD};
		int i = 0;
		for (int x : new int[] {-6, -3, 3, 6}) {
			for (int z : new int[] {-3, 0, 3}) {
				b.set(x, 1, z, Blocks.POLISHED_BLACKSTONE);
				b.set(x, 2, z, head(heads[i++ % heads.length], x < 0 ? 12 : 4));
			}
		}
		// The prize: a dragon's head in a glass case.
		b.fill(-1, 1, 3, 1, 3, 5, Blocks.GLASS);
		b.set(0, 1, 4, Blocks.GOLD_BLOCK);
		b.set(0, 2, 4, head(Blocks.DRAGON_HEAD, 8));
		b.water(0, 2, 3, 0, 2, 3);
		b.chest(-7, 1, 5, Direction.EAST, "heads");
		b.chest(7, 1, 5, Direction.WEST, "heads");
		b.frame(-7, 3, 0, Direction.EAST, Items.SKELETON_SKULL);
		b.frame(7, 3, 0, Direction.WEST, Items.WITHER_SKELETON_SKULL);
	}

	/** A ship of the End that sank into the wrong sea, with its wings still hanging in the cabin. */
	static void elytraShip(DeepBuilder b) {
		DeepBuilder.Paint purpur = b.worn(s(Blocks.PURPUR_BLOCK), s(Blocks.PURPUR_BLOCK), s(Blocks.END_STONE_BRICKS));
		b.pad(-8, -15, 8, 15, b.mix(s(Blocks.SAND), s(Blocks.GRAVEL)), s(Blocks.SANDSTONE), 20);
		for (int z = -14; z <= 13; z++) {
			int half = z < -8 ? Math.max(1, 4 + (z + 8) / 2) : z > 9 ? 3 : 4;
			int keel = z < -10 ? 2 : 0;
			for (int x = -half; x <= half; x++) {
				boolean side = Math.abs(x) == half;
				for (int y = keel; y <= 5; y++) {
					if (side || y == keel) {
						b.set(x, y, z, purpur.at(x, y, z));
					} else {
						b.set(x, y, z, WATER);
					}
				}
				b.set(x, 5, z, side ? s(Blocks.PURPUR_PILLAR) : slab(Blocks.PURPUR_SLAB, true));
				if (side && z % 3 == 0) {
					b.set(x, 6, z, Blocks.END_ROD);
				}
			}
		}
		// A hatch down into the hold.
		b.fill(-1, 5, -4, 1, 5, -2, WATER);
		b.chest(0, 1, -3, Direction.SOUTH, "ship");
		b.chest(-2, 1, 3, Direction.EAST, "ship");
		// The mast and its sails.
		b.pillar(0, -2, 6, 20, axis(Blocks.PURPUR_PILLAR, Direction.Axis.Y));
		for (int y = 9; y <= 18; y++) {
			int width = y > 15 ? 2 : 4;
			for (int x = -width; x <= width; x++) {
				if (x != 0 && !b.crumbled(x, y, -1, 0.08)) {
					b.set(x, y, -1, vanilla("magenta_wool"));
				}
			}
		}
		b.set(0, 21, -2, Blocks.END_ROD);
		// The stern cabin, where the wings hang.
		b.shell(-3, 5, 6, 3, 10, 12, b.worn(s(Blocks.END_STONE_BRICKS), s(Blocks.PURPUR_BLOCK)));
		b.water(0, 6, 6, 0, 7, 6);
		b.set(-3, 8, 9, joined(vanilla("magenta_stained_glass_pane"), false));
		b.set(3, 8, 9, joined(vanilla("magenta_stained_glass_pane"), false));
		b.frame(0, 8, 11, Direction.NORTH, Items.ELYTRA);
		b.set(-1, 8, 11, Blocks.END_ROD);
		b.set(1, 8, 11, Blocks.END_ROD);
		b.chest(-2, 6, 11, Direction.EAST, "ship");
		b.set(2, 6, 11, Blocks.BREWING_STAND);
		b.set(0, 9, 9, lantern(false, true));
		// The dragon's head on the prow.
		b.set(0, 6, -13, head(Blocks.DRAGON_HEAD, 8));
	}

	/** A smithy of the deep, its forges cold: anvils, blast furnaces, and netherite in the ash. */
	static void forge(DeepBuilder b) {
		b.pad(-9, -9, 9, 9, b.mix(s(Blocks.BLACKSTONE), s(Blocks.BASALT), s(Blocks.GRAVEL)), s(Blocks.BLACKSTONE), 18);
		DeepBuilder.Paint bricks = b.worn(s(Blocks.BRICKS), s(Blocks.MUD_BRICKS), s(Blocks.POLISHED_BLACKSTONE_BRICKS));
		b.shell(-7, 0, -5, 7, 7, 6, bricks);
		b.fill(-6, 0, -4, 6, 0, 5, Blocks.POLISHED_BLACKSTONE);
		// An open front between pillars.
		b.water(-5, 1, -5, 5, 5, -5);
		for (int x = -6; x <= 6; x += 4) {
			b.pillar(x, -5, 1, 6, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
		}
		// The chimney.
		b.fill(4, 7, 3, 6, 15, 5, bricks);
		b.water(5, 8, 4, 5, 15, 4);
		b.set(5, 15, 4, Blocks.MAGMA_BLOCK);
		// Forges along the back, the hearth glowing.
		for (int x = -5; x <= 1; x += 2) {
			b.set(x, 1, 5, facing(Blocks.BLAST_FURNACE, Direction.NORTH).setValue(BlockStateProperties.LIT, true));
		}
		b.fill(3, 0, 3, 5, 0, 5, Blocks.MAGMA_BLOCK);
		b.fill(3, 1, 4, 5, 1, 5, Blocks.MAGMA_BLOCK);
		b.set(-2, 1, 1, facing(Blocks.ANVIL, Direction.EAST));
		b.set(1, 1, 1, facing(Blocks.ANVIL, Direction.EAST));
		b.set(-5, 1, -2, Blocks.SMITHING_TABLE);
		b.set(-5, 1, 0, Blocks.GRINDSTONE);
		b.set(-5, 1, 1, Blocks.WATER_CAULDRON);
		// A pile of ancient debris waiting to be smelted.
		b.hoard(4, 1, -1, 1.8, s(Blocks.ANCIENT_DEBRIS), s(Blocks.ANCIENT_DEBRIS), s(Blocks.BLACKSTONE));
		b.chest(-6, 1, 3, Direction.EAST, "forge");
		b.chest(-6, 1, 4, Direction.EAST, "forge");
		b.frame(0, 4, 5, Direction.NORTH, Items.NETHERITE_INGOT);
		for (int x = -4; x <= 4; x += 4) {
			b.set(x, 6, 1, lantern(false, true));
		}
	}

	/** A round sanctum under a crystal dome, built about an Abyssal Enchanting Table. */
	static void enchantingSanctum(DeepBuilder b) {
		b.padRound(0, 0, 11.5, b.mix(s(Blocks.DEEPSLATE_TILES), s(Blocks.POLISHED_DEEPSLATE)), s(Blocks.COBBLED_DEEPSLATE), 20);
		b.disc(0, 0, 0, 7.5, (x, y, z) -> {
			double r = Math.sqrt(x * x + z * z);
			return Math.abs(r - 5.0) < 0.5 || Math.abs(r - 2.0) < 0.5 ? s(Blocks.AMETHYST_BLOCK) : s(Blocks.POLISHED_DEEPSLATE);
		});
		b.cylinder(0, 0, 8.0, 1, 6, b.worn(s(Blocks.CALCITE), s(Blocks.POLISHED_DEEPSLATE)), true);
		b.sphere(0, 6, 0, 8.0, (x, y, z) -> b.chance(x, y, z, 14, 0.15) ? s(Blocks.AMETHYST_BLOCK) : s(vanilla("purple_stained_glass")), true, 1);
		b.water(-1, 1, -8, 1, 3, -7);
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + Math.PI / 8;
			int x = (int) Math.round(Math.cos(a) * 6.5);
			int z = (int) Math.round(Math.sin(a) * 6.5);
			b.pillar(x, z, 1, 5, s(Blocks.POLISHED_DEEPSLATE));
			b.set(x, 6, z, Blocks.END_ROD);
		}
		// The ring of books and the table in the middle of it.
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) == 3 && !(z == -3 && Math.abs(x) <= 1)) {
					b.pillar(x, z, 1, 2, s(Blocks.BOOKSHELF));
				}
			}
		}
		b.fill(-1, 1, -1, 1, 1, 1, Blocks.POLISHED_DEEPSLATE);
		b.set(0, 2, 0, ModBlocks.ABYSSAL_ENCHANTING_TABLE);
		b.pillar(0, 0, 10, 13, axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
		b.set(0, 9, 0, lantern(true, true));
		b.chest(-5, 1, 3, Direction.EAST, "library");
		b.chest(5, 1, 3, Direction.WEST, "library");
		for (int[] c : new int[][] {{-5, -3}, {5, -3}}) {
			b.set(c[0], 1, c[1], Blocks.AMETHYST_BLOCK);
			b.set(c[0], 2, c[1], facing(Blocks.AMETHYST_CLUSTER, Direction.UP));
		}
	}

	/** The hall of a drowned king: a carpet to a golden throne, and the royal treasure behind it. */
	static void throneRoom(DeepBuilder b) {
		b.pad(-9, -12, 9, 12, b.mix(s(Blocks.PRISMARINE), s(Blocks.GRAVEL)), s(Blocks.COBBLED_DEEPSLATE), 16);
		b.shell(-6, 0, -10, 6, 11, 10, b.worn(s(Blocks.PRISMARINE_BRICKS), s(Blocks.PRISMARINE)));
		b.fill(-5, 0, -9, 5, 0, 9, Blocks.DARK_PRISMARINE);
		b.fill(-1, 0, -9, 1, 0, 6, vanilla("red_wool"));
		b.water(-1, 1, -10, 1, 4, -10);
		// A lit portico before the door, and windows between the pillars.
		for (int x = -3; x <= 3; x += 6) {
			b.pillar(x, -11, 1, 7, s(Blocks.PRISMARINE_BRICKS));
			b.set(x, 8, -11, Blocks.SEA_LANTERN);
		}
		b.fill(-3, 8, -12, 3, 8, -11, slab(Blocks.DARK_PRISMARINE_SLAB, false));
		b.fill(-2, 0, -12, 2, 0, -11, Blocks.DARK_PRISMARINE);
		for (int z = -6; z <= 6; z += 4) {
			b.fill(-6, 4, z, -6, 8, z, joined(Blocks.GLASS_PANE, false));
			b.fill(6, 4, z, 6, 8, z, joined(Blocks.GLASS_PANE, false));
		}
		for (int z = -8; z <= 8; z += 4) {
			b.pillar(-6, z, 1, 10, s(Blocks.DARK_PRISMARINE));
			b.pillar(6, z, 1, 10, s(Blocks.DARK_PRISMARINE));
			b.set(-5, 7, z, Blocks.SEA_LANTERN);
			b.set(5, 7, z, Blocks.SEA_LANTERN);
		}
		for (int z = -6; z <= 6; z += 6) {
			b.set(0, 11, z, Blocks.SEA_LANTERN);
			b.set(0, 10, z, axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
			b.set(0, 9, z, lantern(false, true));
		}
		// The dais and the throne.
		b.fill(-3, 1, 6, 3, 1, 9, Blocks.DARK_PRISMARINE);
		b.fill(-2, 2, 7, 2, 2, 9, Blocks.PRISMARINE_BRICKS);
		b.fill(-3, 1, 5, 3, 1, 5, stairs(Blocks.DARK_PRISMARINE_STAIRS, Direction.SOUTH, false));
		b.set(0, 3, 8, stairs(Blocks.QUARTZ_STAIRS, Direction.SOUTH, false));
		b.pillar(0, 9, 3, 6, s(Blocks.GOLD_BLOCK));
		b.set(-1, 3, 8, Blocks.GOLD_BLOCK);
		b.set(1, 3, 8, Blocks.GOLD_BLOCK);
		b.set(-1, 6, 9, Blocks.GOLD_BLOCK);
		b.set(1, 6, 9, Blocks.GOLD_BLOCK);
		b.set(0, 7, 9, Blocks.SEA_LANTERN);
		b.chest(-4, 1, 9, Direction.EAST, "vault");
		b.chest(4, 1, 9, Direction.WEST, "treasury");
		// The king's guard still stands.
		for (int side = -1; side <= 1; side += 2) {
			b.stand(side * 3, 1, 3, Direction.NORTH, new ItemStack(Items.GOLDEN_HELMET), new ItemStack(Items.GOLDEN_CHESTPLATE),
					new ItemStack(Items.GOLDEN_LEGGINGS), new ItemStack(Items.GOLDEN_BOOTS), new ItemStack(Items.TRIDENT));
		}
		b.spawner(0, 0, -4, EntityTypes.DROWNED);
	}

	/** A treasure galleon on the sea floor, masts broken, sails in rags, its hold still full. */
	static void galleon(DeepBuilder b) {
		DeepBuilder.Paint hull = b.worn(s(Blocks.DARK_OAK_PLANKS), s(Blocks.SPRUCE_PLANKS), s(Blocks.DARK_OAK_PLANKS));
		b.pad(-8, -16, 8, 16, b.mix(s(Blocks.SAND), s(Blocks.GRAVEL)), s(Blocks.SANDSTONE), 24);
		for (int z = -15; z <= 14; z++) {
			int half = z < -9 ? Math.max(1, 4 + (z + 9) / 2) : 4;
			int deck = z > 8 ? 8 : 5;
			for (int x = -half; x <= half; x++) {
				boolean side = Math.abs(x) == half || z == 14;
				for (int y = 0; y <= deck; y++) {
					if (side || y == 0) {
						b.set(x, y, z, b.crumbled(x, y, z, 0.05) ? WATER : hull.at(x, y, z));
					} else if (y == 5 || y == deck) {
						b.set(x, y, z, b.crumbled(x, y, z, 0.07) ? WATER : s(Blocks.SPRUCE_PLANKS));
					} else {
						b.set(x, y, z, WATER);
					}
				}
				if (side) {
					b.set(x, deck + 1, z, Blocks.DARK_OAK_FENCE);
				}
			}
			// Cannon ports.
			if (z > -8 && z < 8 && z % 3 == 0) {
				b.set(-4, 3, z, WATER);
				b.set(4, 3, z, WATER);
				b.set(-3, 3, z, Blocks.ANVIL);
				b.set(3, 3, z, Blocks.ANVIL);
			}
		}
		// The captain's cabin under the high stern.
		b.fill(-3, 6, 9, 3, 7, 13, WATER);
		b.chest(0, 6, 13, Direction.NORTH, "vault");
		b.chest(-3, 6, 12, Direction.EAST, "ship");
		b.set(2, 6, 12, facing(Blocks.LECTERN, Direction.WEST));
		b.set(0, 7, 11, lantern(false, true));
		b.frame(1, 7, 13, Direction.NORTH, Items.MAP);
		// The hold.
		b.barrel(-3, 1, -4, Direction.UP, "ship");
		b.barrel(-3, 1, -3, Direction.UP, "ruin");
		b.barrel(3, 1, 2, Direction.UP, "ship");
		b.chest(0, 1, 0, Direction.SOUTH, "treasury");
		b.set(0, 4, 0, lantern(false, true));
		b.fill(-1, 5, 3, 1, 5, 4, WATER);
		// Masts, one snapped.
		for (int[] mast : new int[][] {{-5, 22}, {4, 15}}) {
			b.pillar(0, mast[0], 6, mast[1], axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
			for (int y = 10; y <= mast[1] - 2; y += 6) {
				b.fill(-4, y, mast[0], 4, y, mast[0], axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
				for (int yy = y - 4; yy < y; yy++) {
					for (int x = -4; x <= 4; x++) {
						if (x != 0 && !b.crumbled(x, yy, mast[0], 0.3)) {
							b.set(x, yy, mast[0] + 1, vanilla("white_wool"));
						}
					}
				}
			}
		}
		b.set(0, 6, -15, Blocks.PRISMARINE);
		b.set(0, 7, -16, Blocks.PRISMARINE_BRICKS);
	}

	/** A submarine of riveted copper, still lit inside after all these years. */
	static void submarine(DeepBuilder b) {
		b.pad(-7, -15, 7, 15, b.mix(s(Blocks.SAND), s(Blocks.GRAVEL)), s(Blocks.SANDSTONE), 16);
		DeepBuilder.Paint copper = (x, y, z) -> b.chance(x, y, z, 15, 0.2) ? s(vanilla("weathered_cut_copper")) : s(vanilla("oxidized_cut_copper"));
		for (int z = -12; z <= 12; z++) {
			double radius = Math.abs(z) <= 8 ? 3.5 : 3.5 * Math.sqrt(1.0 - Math.pow((Math.abs(z) - 8) / 4.5, 2));
			for (int x = -4; x <= 4; x++) {
				for (int y = 0; y <= 8; y++) {
					double d = Math.sqrt(x * x + (y - 4) * (y - 4));
					if (d <= radius && d > radius - 1.0) {
						boolean porthole = y == 4 && Math.abs(x) >= 3 && z % 4 == 0 && Math.abs(z) <= 8;
						b.set(x, y, z, porthole ? s(Blocks.GLASS) : copper.at(x, y, z));
					} else if (d <= radius - 1.0) {
						b.set(x, y, z, WATER);
					}
				}
			}
		}
		b.fill(-1, 2, -8, 1, 2, 8, vanilla("oxidized_copper_grate"));
		// The conning tower and its periscope.
		b.fill(-1, 7, -3, 1, 11, 2, copper);
		b.water(0, 7, -2, 0, 11, 1);
		b.pillar(0, -2, 12, 14, s(Blocks.IRON_BARS));
		b.set(0, 15, -2, Blocks.IRON_BLOCK);
		// The propeller.
		b.fill(-2, 4, 13, 2, 4, 13, Blocks.IRON_BARS);
		b.fill(0, 2, 13, 0, 6, 13, Blocks.IRON_BARS);
		// Inside: lights, bunks, stores.
		for (int z = -6; z <= 6; z += 4) {
			b.set(0, 7, z, lit(vanilla("waxed_copper_bulb")));
		}
		b.bed(-2, 3, -6, Direction.SOUTH, vanilla("gray_bed"));
		b.bed(2, 3, -6, Direction.SOUTH, vanilla("gray_bed"));
		b.chest(-2, 3, 4, Direction.EAST, "ship");
		b.chest(2, 3, 4, Direction.WEST, "mine");
		b.barrel(0, 3, 8, Direction.UP, "ruin");
		b.set(0, 3, 0, facing(Blocks.LECTERN, Direction.NORTH));
	}

	/** A small mausoleum above, and below it a crypt where the drowned still keep watch. */
	static void crypt(DeepBuilder b) {
		DeepBuilder.Paint stone = b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS));
		b.pad(-9, -12, 9, 11, b.mix(s(Blocks.GRAVEL), s(Blocks.COARSE_DIRT)), s(Blocks.COBBLESTONE), 10);
		// The mausoleum over the stair.
		b.shell(-3, 0, -10, 3, 6, -4, stone);
		b.water(0, 1, -10, 0, 3, -10);
		b.fill(-3, 7, -10, 3, 7, -4, slab(Blocks.STONE_BRICK_SLAB, false));
		b.set(0, 7, -7, Blocks.CHISELED_STONE_BRICKS);
		b.set(-2, 1, -9, lantern(true, false));
		b.set(2, 1, -9, lantern(true, false));
		// The chamber below.
		b.fill(-8, -12, -3, 8, -1, 10, stone);
		b.water(-7, -11, -2, 7, -3, 9);
		// The stair down.
		for (int k = 0; k <= 9; k++) {
			b.water(-1, -k, -8 + k, 1, -k + 3, -8 + k);
			b.fill(-1, -k - 1, -8 + k, 1, -k - 1, -8 + k, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
		}
		b.fill(-7, -12, -2, 7, -12, 9, b.mix(s(Blocks.STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS)));
		// Sarcophagi along both walls.
		for (int side = -1; side <= 1; side += 2) {
			for (int z = 0; z <= 7; z += 4) {
				b.fill(side * 5, -11, z, side * 6, -11, z + 2, Blocks.POLISHED_ANDESITE);
				b.fill(side * 5, -10, z, side * 6, -10, z + 2, slab(Blocks.ANDESITE_SLAB, false));
				b.set(side * 5, -9, z + 1, b.head(side * 5, -9, z + 1));
			}
			b.set(side * 3, -3, 4, lantern(true, true));
		}
		b.set(0, -11, 9, Blocks.CHISELED_STONE_BRICKS);
		b.chest(-1, -11, 9, Direction.NORTH, "tomb");
		b.chest(1, -11, 9, Direction.NORTH, "tomb");
		b.set(0, -10, 9, Blocks.WITHER_SKELETON_SKULL);
		b.spawner(0, -11, 4, EntityTypes.DROWNED);
		b.set(0, -3, 0, lantern(true, true));
	}

	/** A stepped pyramid with gold on its summit and a treasure chamber in its heart. */
	static void pyramid(DeepBuilder b) {
		b.pad(-14, -14, 14, 14, b.mix(s(Blocks.SAND), s(Blocks.SANDSTONE)), s(Blocks.SANDSTONE), 20);
		for (int level = 0; level < 6; level++) {
			int half = 12 - level * 2;
			int y0 = 1 + level * 2;
			b.fill(-half, y0, -half, half, y0 + 1, half, (x, y, z) -> y % 4 == 0 && (Math.abs(x) == half || Math.abs(z) == half)
					? s(Blocks.CHISELED_SANDSTONE) : b.chance(x, y, z, 16, 0.2) ? s(Blocks.CUT_SANDSTONE) : s(Blocks.SANDSTONE));
		}
		// The summit shrine.
		b.fill(-1, 13, -1, 1, 13, 1, Blocks.GOLD_BLOCK);
		b.set(0, 14, 0, Blocks.SEA_LANTERN);
		for (int[] c : new int[][] {{-12, -12}, {12, -12}, {-12, 12}, {12, 12}}) {
			b.set(c[0], 3, c[1], Blocks.SEA_LANTERN);
		}
		// The passage and the chamber.
		b.water(-1, 1, -12, 1, 3, -4);
		for (int z = -11; z <= -5; z += 3) {
			b.set(-2, 2, z, Blocks.SEA_LANTERN);
			b.set(2, 2, z, Blocks.SEA_LANTERN);
		}
		b.water(-4, 1, -4, 4, 6, 4);
		b.fill(-4, 0, -4, 4, 0, 4, (x, y, z) -> (x + z) % 2 == 0 ? s(Blocks.CUT_SANDSTONE) : s(vanilla("orange_terracotta")));
		b.set(0, 7, 0, Blocks.SEA_LANTERN);
		b.hoard(0, 1, 2, 2.2, s(Blocks.GOLD_BLOCK), s(Blocks.RAW_GOLD_BLOCK), s(Blocks.GOLD_BLOCK));
		b.chest(-3, 1, 3, Direction.EAST, "treasury");
		b.chest(3, 1, 3, Direction.WEST, "vault");
		b.set(-3, 1, -3, Blocks.SKELETON_SKULL);
		b.set(3, 1, -3, b.head(3, 1, -3));
		b.spawner(0, 1, -2, "hollow_eye");
	}

	/** A palace grown from living coral, with pearl and gold in its halls. */
	static void coralPalace(DeepBuilder b) {
		b.pad(-11, -11, 11, 11, b.mix(s(Blocks.SAND), s(Blocks.SAND), s(Blocks.GRAVEL)), s(Blocks.SANDSTONE), 22);
		DeepBuilder.Paint coral = (x, y, z) -> coral(b, x, y, z);
		b.shell(-7, 0, -5, 7, 9, 6, coral);
		b.fill(-6, 0, -4, 6, 0, 5, Blocks.PRISMARINE_BRICKS);
		b.walls(-7, 9, -5, 7, 9, 6, s(Blocks.DARK_PRISMARINE));
		b.water(-1, 1, -5, 1, 4, -5);
		for (int x = -5; x <= 5; x += 5) {
			b.set(x, 4, -5, Blocks.SEA_LANTERN);
			b.set(x, 4, 6, Blocks.SEA_LANTERN);
		}
		// Spires and domes.
		for (int[] spire : new int[][] {{-7, -5, 16}, {7, -5, 16}, {0, 6, 20}}) {
			b.cylinder(spire[0], spire[1], 1.6, 1, spire[2], coral, false);
			b.set(spire[0], spire[2] + 1, spire[1], Blocks.SEA_LANTERN);
			b.set(spire[0], spire[2] + 2, spire[1], Blocks.GOLD_BLOCK);
		}
		b.sphere(0, 9, 0, 4.5, coral, true, 1);
		b.set(0, 13, 0, Blocks.SEA_LANTERN);
		b.fill(-3, 9, -3, 3, 9, 3, WATER);
		// The court within: pearl, gold and the treasure.
		b.fill(-1, 1, 3, 1, 2, 4, ModBlocks.PEARL_BLOCK);
		b.set(0, 3, 4, Blocks.GOLD_BLOCK);
		b.chest(-5, 1, 5, Direction.EAST, "vault");
		b.chest(5, 1, 5, Direction.WEST, "treasury");
		for (int[] c : new int[][] {{-5, -3}, {5, -3}, {-5, 2}, {5, 2}}) {
			b.pillar(c[0], c[1], 1, 8, s(Blocks.GOLD_BLOCK));
		}
		for (int i = 0; i < 20; i++) {
			int x = b.layout.nextInt(21) - 10;
			int z = b.layout.nextInt(21) - 10;
			if (Math.abs(x) > 8 || Math.abs(z) > 7) {
				b.set(x, 1, z, b.pick(x, 1, z, 17, s(Blocks.BRAIN_CORAL), s(Blocks.TUBE_CORAL_FAN), s(Blocks.FIRE_CORAL), pickles(4),
						s(ModBlocks.PURPLE_SEA_FAN)));
			}
		}
	}

	/** A tower under a glass dome, with a great telescope still pointed up at a sky it cannot see. */
	static void observatory(DeepBuilder b) {
		b.padRound(0, 0, 11.5, b.mix(s(Blocks.STONE), s(Blocks.GRAVEL)), s(Blocks.STONE), 22);
		b.cylinder(0, 0, 7.5, 1, 7, b.worn(s(Blocks.POLISHED_ANDESITE), s(Blocks.STONE_BRICKS), s(Blocks.ANDESITE)), true);
		b.disc(0, 0, 0, 7.0, (x, y, z) -> Math.abs(Math.sqrt(x * x + z * z) - 4.0) < 0.5 ? s(Blocks.LAPIS_BLOCK) : s(Blocks.POLISHED_ANDESITE));
		b.disc(0, 8, 0, 7.5, s(Blocks.POLISHED_ANDESITE));
		b.disc(0, 8, 0, 6.5, WATER);
		b.sphere(0, 8, 0, 7.5, s(Blocks.GLASS), true, 1);
		b.water(-1, 1, -8, 1, 3, -7);
		// The telescope on its mount.
		b.fill(-1, 1, -1, 1, 1, 1, Blocks.POLISHED_DEEPSLATE);
		b.pillar(0, 0, 2, 4, s(vanilla("lightning_rod")));
		b.line(-2, 4, -2, 3, 11, 3, s(vanilla("copper_block")));
		b.line(-2, 5, -2, 3, 12, 3, s(vanilla("waxed_copper_block")));
		b.set(3, 13, 3, Blocks.GLASS);
		// Star charts and instruments round the wall.
		b.frame(-6, 3, 0, Direction.EAST, Items.COMPASS);
		b.frame(6, 3, 0, Direction.WEST, Items.CLOCK);
		b.frame(0, 3, 6, Direction.NORTH, Items.SPYGLASS);
		b.frame(-4, 3, 5, Direction.NORTH, Items.RECOVERY_COMPASS);
		b.set(4, 1, 4, Blocks.CARTOGRAPHY_TABLE);
		b.set(-4, 1, 4, facing(Blocks.LECTERN, Direction.SOUTH));
		b.chest(5, 1, -3, Direction.WEST, "library");
		b.chest(-5, 1, -3, Direction.EAST, "vault");
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3;
			b.set((int) Math.round(Math.cos(a) * 6), 6, (int) Math.round(Math.sin(a) * 6), Blocks.SEA_LANTERN);
		}
	}

	/** A prison of the deep: cells behind iron bars, and the warden's strongbox at the end. */
	static void prison(DeepBuilder b) {
		DeepBuilder.Paint wall = b.worn(s(Blocks.POLISHED_DEEPSLATE), s(Blocks.DEEPSLATE_BRICKS), s(Blocks.CRACKED_DEEPSLATE_BRICKS));
		b.pad(-11, -9, 11, 9, b.mix(s(Blocks.COBBLED_DEEPSLATE), s(Blocks.GRAVEL)), s(Blocks.COBBLED_DEEPSLATE), 10);
		b.shell(-9, 0, -6, 9, 6, 6, wall);
		b.water(-9, 1, -1, -9, 3, 1);
		for (int x = -7; x <= 7; x += 4) {
			b.set(x, 5, 0, lantern(true, true));
		}
		// Cells either side of the corridor.
		for (int side = -1; side <= 1; side += 2) {
			for (int x = -8; x <= 4; x += 4) {
				b.fill(x, 1, side * 2, x + 3, 5, side * 2, joined(Blocks.IRON_BARS, true));
				b.fill(x + 3, 1, side * 2, x + 3, 5, side * 5, wall);
				b.water(x + 1, 1, side * 2, x + 1, 2, side * 2);
				// What is left of the prisoner.
				int cx = x + 1;
				int cz = side * 4;
				if (b.chance(cx, 1, cz, 18, 0.7)) {
					b.set(cx, 1, cz, Blocks.SKELETON_SKULL);
					b.set(cx + 1, 1, cz, axis(Blocks.BONE_BLOCK, Direction.Axis.X));
				}
			}
		}
		b.spawner(-6, 1, 4, EntityTypes.DROWNED);
		// The warden's desk.
		b.set(7, 1, 0, Blocks.POLISHED_DEEPSLATE);
		b.set(7, 2, 0, vanilla("candle"));
		b.chest(8, 1, 0, Direction.WEST, "vault");
		b.chest(8, 1, -1, Direction.WEST, "armory");
		b.frame(8, 3, 1, Direction.WEST, Items.TRIPWIRE_HOOK);
	}

	/** An arena ringed with tiers of seats; the champion's prize still waits on the podium. */
	static void arena(DeepBuilder b) {
		b.padRound(0, 0, 15.5, b.mix(s(Blocks.SAND), s(Blocks.SANDSTONE)), s(Blocks.SANDSTONE), 14);
		DeepBuilder.Paint stone = b.worn(s(Blocks.CUT_SANDSTONE), s(Blocks.SANDSTONE), s(Blocks.SMOOTH_SANDSTONE));
		// The outer wall, with arches.
		for (int x = -15; x <= 15; x++) {
			for (int z = -15; z <= 15; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r > 15.0 || !b.column(x, z)) {
					continue;
				}
				if (r > 13.0) {
					double a = Math.toDegrees(Math.atan2(z, x));
					boolean arch = Math.floorMod((int) Math.round(a), 30) < 6;
					for (int y = 1; y <= 9; y++) {
						b.set(x, y, z, arch && y <= 4 ? WATER : stone.at(x, y, z));
					}
				} else if (r > 9.0) {
					int tier = (int) (r - 9.0);
					b.fill(x, 1, z, x, 1 + tier * 2, z, stone);
					b.set(x, 2 + tier * 2, z, s(Blocks.SMOOTH_SANDSTONE_SLAB));
				}
			}
		}
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6 + Math.PI / 12;
			b.set((int) Math.round(Math.cos(a) * 14), 10, (int) Math.round(Math.sin(a) * 14), Blocks.SEA_LANTERN);
		}
		// The podium and its prize.
		b.fill(-2, 1, -2, 2, 1, 2, Blocks.POLISHED_BLACKSTONE_BRICKS);
		b.fill(-1, 2, -1, 1, 2, 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
		b.set(0, 3, 0, Blocks.GOLD_BLOCK);
		b.set(0, 4, 0, Blocks.GOLD_BLOCK);
		b.frame(0, 4, -1, Direction.NORTH, weapon(anyWeapon(b)));
		b.set(0, 5, 0, Blocks.SEA_LANTERN);
		b.chest(0, 2, 2, Direction.SOUTH, "armory");
		b.chest(-2, 2, 0, Direction.WEST, "vault");
		// The beasts.
		b.spawner(6, 0, 6, "bone_shark");
		b.spawner(-6, 0, -6, "rotfin");
	}

	/** The bones of a sea dragon, coiled where it died, its hoard still under its ribs. */
	static void dragonSkeleton(DeepBuilder b) {
		b.pad(-12, -16, 12, 16, b.mix(s(Blocks.GRAVEL), s(Blocks.CALCITE), s(Blocks.GRAVEL)), s(Blocks.CALCITE), 16);
		// The hoard first, so the ribs close over it.
		b.hoard(0, 1, 0, 4.5, s(Blocks.GOLD_BLOCK), s(Blocks.GOLD_BLOCK), s(Blocks.RAW_GOLD_BLOCK), s(Blocks.EMERALD_BLOCK), s(Blocks.GOLD_BLOCK));
		b.chest(-5, 1, -2, Direction.WEST, "treasury");
		b.chest(5, 1, 2, Direction.EAST, "vault");
		// The spine, snaking from skull to tail.
		int prevX = 0;
		for (int z = -12; z <= 15; z++) {
			int x = (int) Math.round(Math.sin(z / 5.0) * 4.0);
			int y = z < 8 ? 9 - Math.max(0, Math.abs(z) - 6) / 3 : Math.max(1, 9 - (z - 4) / 2);
			b.line(prevX, y, z - 1, x, y, z, axis(Blocks.BONE_BLOCK, Direction.Axis.Z));
			prevX = x;
			// Ribs either side, curving down to the sand.
			if (z % 2 == 0 && z > -8 && z < 9) {
				for (int side = -1; side <= 1; side += 2) {
					for (int t = 1; t <= 8; t++) {
						double a = Math.PI / 2 * t / 8;
						int rx = x + side * (int) Math.round(Math.sin(a) * 6.5);
						int ry = y - (int) Math.round((1.0 - Math.cos(a)) * (y - 1));
						b.set(rx, ry, z, Blocks.BONE_BLOCK);
					}
				}
			}
		}
		// The skull.
		b.fill(-3, 4, -16, 3, 8, -12, Blocks.BONE_BLOCK);
		b.fill(-2, 5, -15, 2, 7, -13, WATER);
		b.fill(-2, 5, -16, 2, 5, -16, WATER);
		for (int side = -1; side <= 1; side += 2) {
			b.set(side * 2, 7, -16, WATER);
			b.set(side * 2, 7, -15, Blocks.SOUL_LANTERN);
		}
		b.set(0, 9, -14, head(Blocks.DRAGON_HEAD, 8));
		for (int[] c : new int[][] {{-9, 6}, {9, -6}}) {
			b.lampPost(c[0], c[1], 1, 3, s(Blocks.BONE_BLOCK), lantern(true, false));
		}
	}

	/** A drowned market square: stalls under faded awnings, their wares still laid out. */
	static void sunkenMarket(DeepBuilder b) {
		b.pad(-14, -14, 14, 14, b.mix(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.GRAVEL)), s(Blocks.COBBLESTONE), 10);
		// A fountain in the middle.
		b.cylinder(0, 0, 3.0, 1, 1, s(Blocks.PRISMARINE_BRICKS), true);
		b.set(0, 0, 0, Blocks.SOUL_SAND);
		b.pillar(0, 0, 1, 4, s(Blocks.BUBBLE_COLUMN).setValue(BlockStateProperties.DRAG, false));
		b.set(0, 5, 0, WATER);
		b.set(2, 1, 2, Blocks.SEA_LANTERN);
		b.set(-2, 1, -2, Blocks.SEA_LANTERN);
		// Stalls round the square, each with its awning and its wares.
		Block[] awnings = {vanilla("red_wool"), vanilla("yellow_wool"), vanilla("blue_wool"), vanilla("green_wool"),
				vanilla("orange_wool"), vanilla("purple_wool")};
		Item[] wares = {Items.EMERALD, Items.DIAMOND, Items.GOLD_INGOT, Items.HEART_OF_THE_SEA, Items.NAUTILUS_SHELL,
				Items.ENCHANTED_BOOK, Items.GOLDEN_APPLE, Items.AMETHYST_SHARD};
		int[][] stalls = {{-9, -9}, {0, -10}, {9, -9}, {-9, 9}, {0, 10}, {9, 9}};
		for (int i = 0; i < stalls.length; i++) {
			int x = stalls[i][0];
			int z = stalls[i][1];
			Direction front = z < 0 ? Direction.SOUTH : Direction.NORTH;
			int back = z < 0 ? -1 : 1;
			b.fill(x - 2, 1, z, x + 2, 1, z, Blocks.SPRUCE_PLANKS);
			b.fill(x - 2, 1, z + back, x + 2, 3, z + back, Blocks.SPRUCE_PLANKS);
			for (int dx = -2; dx <= 2; dx += 4) {
				b.pillar(x + dx, z - back, 1, 3, s(Blocks.SPRUCE_FENCE));
			}
			b.fill(x - 2, 4, z - back, x + 2, 4, z + back, awnings[i]);
			b.frame(x - 1, 2, z, front, wares[(i * 2) % wares.length]);
			b.frame(x + 1, 2, z, front, wares[(i * 2 + 1) % wares.length]);
			if (i % 2 == 0) {
				b.barrel(x, 2, z, front, "ruin");
			} else {
				b.chest(x, 2, z, front, "vault");
			}
		}
		for (int[] c : new int[][] {{-6, 0}, {6, 0}, {0, -5}, {0, 5}}) {
			b.lampPost(c[0], c[1], 1, 3, s(Blocks.DARK_OAK_FENCE), lantern(false, false));
		}
	}
}
