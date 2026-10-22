package com.naturesmod.worldgen.deep;

import static com.naturesmod.worldgen.deep.DeepBuilder.axis;
import static com.naturesmod.worldgen.deep.DeepBuilder.facing;
import static com.naturesmod.worldgen.deep.DeepBuilder.head;
import static com.naturesmod.worldgen.deep.DeepBuilder.joined;
import static com.naturesmod.worldgen.deep.DeepBuilder.lantern;
import static com.naturesmod.worldgen.deep.DeepBuilder.s;
import static com.naturesmod.worldgen.deep.DeepBuilder.slab;
import static com.naturesmod.worldgen.deep.DeepBuilder.stairs;
import static com.naturesmod.worldgen.deep.DeepBuilder.vanilla;
import static com.naturesmod.worldgen.deep.LandParts.carpet;
import static com.naturesmod.worldgen.deep.LandParts.log;
import static com.naturesmod.worldgen.deep.LandParts.site;
import static com.naturesmod.worldgen.deep.LandParts.vine;
import static com.naturesmod.worldgen.deep.LandParts.wool;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The harder halls of the Overworld: hideouts, tombs and temples with keepers, spawners and traps,
 * and riches to match. Spawner rooms are kept dark, or the spawners would never wake.
 */
final class LandMedium {
	private LandMedium() {
	}

	private static BlockState leaves(Block leaves) {
		return s(leaves).setValue(BlockStateProperties.PERSISTENT, true);
	}

	/** Cobwebs in the corners of a room. */
	private static void webs(DeepBuilder b, int x0, int y, int z0, int x1, int z1) {
		for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			if (b.chance(c[0], y, c[1], 81, 0.7)) {
				b.set(c[0], y, c[1], Blocks.COBWEB);
			}
		}
	}

	/** A market stall: four posts under an awning of cloth, a counter along its north side. */
	private static void stall(DeepBuilder b, int x, int z, String colour) {
		for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			b.pillar(x + c[0], z + c[1], 1, 3, s(Blocks.OAK_FENCE));
		}
		b.fill(x - 1, 4, z - 1, x + 1, 4, z + 1, wool(colour));
		b.fill(x - 1, 1, z - 1, x + 1, 1, z - 1, slab(Blocks.SMOOTH_SANDSTONE_SLAB, true));
	}

	// --- the woods ----------------------------------------------------------------------------------

	/** A bandits' camp behind a palisade: tents, a cabin, a lookout, a practice dummy, and the bandits themselves. */
	static void banditHideout(DeepBuilder b) {
		site(b, -15, -15, 15, 15, 0, 14);
		LandParts.palisade(b, -12, -12, 12, 12, 5, Blocks.SPRUCE_LOG);
		LandParts.tent(b, -8, -9, -4, wool("gray"));
		LandParts.tent(b, 8, -9, -4, wool("black"));
		LandParts.cottage(b, -10, 3, -3, 9, 4, DeepBuilder.p(s(Blocks.SPRUCE_PLANKS)), log(Blocks.SPRUCE_LOG), s(Blocks.SPRUCE_PLANKS),
				Blocks.SPRUCE_STAIRS, s(Blocks.SPRUCE_PLANKS));
		b.chest(-9, 1, 8, Direction.EAST, "bandit");
		b.bed(-4, 1, 7, Direction.SOUTH, vanilla("black_bed"));
		b.barrel(-9, 1, 4, Direction.UP, "bandit");
		// The lookout in the corner, boarded up to a platform with a ladder inside.
		b.walls(7, 1, 7, 10, 7, 10, s(Blocks.SPRUCE_PLANKS));
		for (int[] c : new int[][] {{7, 7}, {10, 7}, {7, 10}, {10, 10}}) {
			b.pillar(c[0], c[1], 1, 9, log(Blocks.SPRUCE_LOG));
		}
		b.clear(8, 1, 7, 8, 2, 7);
		b.fill(7, 8, 7, 10, 8, 10, s(Blocks.SPRUCE_PLANKS));
		LandParts.railing(b, 7, 9, 7, 10, 9, 10, Blocks.SPRUCE_FENCE);
		for (int y = 1; y <= 8; y++) {
			b.set(9, y, 8, facing(Blocks.LADDER, Direction.WEST));
		}
		b.chest(8, 9, 9, Direction.EAST, "bandit");
		// The yard: a fire, a dummy to practise on, the loot of the last raid.
		b.set(0, 1, 0, s(Blocks.CAMPFIRE));
		b.set(4, 1, 3, Blocks.OAK_FENCE);
		b.set(4, 2, 3, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
		b.set(4, 3, 3, facing(Blocks.CARVED_PUMPKIN, Direction.NORTH));
		b.fill(2, 1, -2, 3, 2, -1, Blocks.BARREL);
		b.stand(-2, 1, 3, Direction.NORTH, new ItemStack(Items.IRON_HELMET), new ItemStack(Items.CHAINMAIL_CHESTPLATE), ItemStack.EMPTY,
				ItemStack.EMPTY, new ItemStack(Items.IRON_AXE));
		b.keeper(-6, 1, -2, EntityTypes.PILLAGER);
		b.keeper(6, 1, -1, EntityTypes.PILLAGER);
		b.keeper(9, 9, 9, EntityTypes.PILLAGER);
		b.keeper(0, 1, 5, EntityTypes.VINDICATOR);
	}

	/** A crooked witch's tower of dark stone in the bog, a cauldron bubbling at the top and the witch beside it. */
	static void witchTower(DeepBuilder b) {
		site(b, 9.5, 30);
		DeepBuilder.Paint stone = b.worn(s(Blocks.COBBLED_DEEPSLATE), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.DEEPSLATE_BRICKS));
		LandParts.tower(b, 0, 0, 4.5, 18, 6, stone, s(Blocks.DARK_OAK_PLANKS));
		// A pointed roof of purple.
		for (int k = 0; k <= 6; k++) {
			b.disc(0, 19 + k, 0, 5.5 - k * 0.85, k % 2 == 0 ? s(vanilla("purple_terracotta")) : s(vanilla("magenta_terracotta")));
		}
		b.set(0, 26, 0, vanilla("lightning_rod"));
		for (int y = 3; y <= 15; y += 6) {
			b.set(-4, y, 0, joined(Blocks.IRON_BARS, false));
			b.set(0, y + 1, 4, joined(Blocks.IRON_BARS, true));
		}
		// The ground floor: brewing; the middle: a bed and shelves; the top: the cauldron over its fire.
		b.set(-2, 1, -1, Blocks.BREWING_STAND);
		b.set(-2, 1, 1, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		b.chest(-2, 1, 2, Direction.EAST, "witch");
		b.bed(-2, 7, -1, Direction.SOUTH, vanilla("purple_bed"));
		b.fill(1, 7, 2, 2, 8, 2, Blocks.BOOKSHELF);
		b.set(0, 12, 0, s(Blocks.CAMPFIRE));
		b.set(0, 13, 0, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		b.chest(-2, 13, 1, Direction.EAST, "witch");
		b.set(1, 18, 1, lantern(true, true));
		webs(b, -2, 17, -2, 2, 2);
		b.keeper(1, 13, -1, EntityTypes.WITCH);
	}

	/** A graveyard behind iron railings, its chapel fallen in, and in the mausoleum something that keeps raising the dead. */
	static void hauntedGraveyard(DeepBuilder b) {
		site(b, -15, -15, 15, 15, 0, 12);
		LandParts.railing(b, -14, 1, -14, 14, 1, 14, Blocks.IRON_BARS);
		b.clear(-1, 1, -14, 1, 1, -14);
		DeepBuilder.Paint grave = b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS));
		for (int x = -11; x <= 11; x += 4) {
			for (int z = -10; z <= 2; z += 4) {
				if (Math.abs(x) < 3) {
					continue;
				}
				b.set(x, 1, z, grave);
				b.set(x, 2, z, b.chance(x, 2, z, 82, 0.5) ? s(Blocks.STONE_BRICK_WALL) : s(Blocks.MOSSY_STONE_BRICK_WALL));
				b.fill(x, 0, z + 1, x, 0, z + 2, s(Blocks.COARSE_DIRT));
				if (b.chance(x, 0, z, 83, 0.25)) {
					// An open grave, its coffin still in it.
					b.clear(x, 0, z + 1, x, 0, z + 2);
					b.chest(x, -1, z + 1, Direction.NORTH, "crypt");
				}
			}
		}
		// The ruined chapel, its bell still hanging over the door.
		LandParts.house(b, -6, 5, 6, 13, 6, b.ruin(grave, 0.1), s(Blocks.CHISELED_STONE_BRICKS), s(Blocks.STONE_BRICKS));
		b.clear(0, 1, 5, 0, 3, 5);
		b.pillar(-1, 5, 7, 9, s(Blocks.STONE_BRICKS));
		b.pillar(1, 5, 7, 9, s(Blocks.STONE_BRICKS));
		b.fill(-1, 10, 5, 1, 10, 5, slab(Blocks.STONE_BRICK_SLAB, false));
		b.set(0, 9, 5, s(Blocks.BELL).setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING));
		b.set(0, 1, 12, Blocks.LECTERN);
		b.chest(-5, 1, 12, Direction.EAST, "crypt");
		// The mausoleum, and what keeps the dead walking: dark inside, its door turned from the sun.
		b.fill(9, 0, 6, 13, 0, 12, s(Blocks.POLISHED_ANDESITE));
		b.walls(9, 1, 6, 13, 4, 12, grave);
		b.fill(9, 5, 6, 13, 5, 12, slab(Blocks.STONE_BRICK_SLAB, false));
		b.clear(11, 1, 6, 11, 2, 6);
		b.spawner(11, 1, 10, EntityTypes.ZOMBIE);
		b.chest(12, 1, 11, Direction.WEST, "crypt");
		for (int[] c : new int[][] {{-13, -13}, {13, -13}, {-13, 13}, {13, 13}}) {
			b.pillar(c[0], c[1], 1, 2, s(Blocks.DARK_OAK_FENCE));
			b.set(c[0], 3, c[1], lantern(true, false));
		}
	}

	/** A lumber mill: a long open shed over the saw, piles of logs, a waterwheel turning in its race. */
	static void lumberMill(DeepBuilder b) {
		site(b, -15, -14, 15, 14, 0, 12);
		for (int x = -10; x <= 10; x += 5) {
			b.pillar(x, -4, 1, 5, log(Blocks.SPRUCE_LOG));
			b.pillar(x, 4, 1, 5, log(Blocks.SPRUCE_LOG));
		}
		LandParts.roof(b, -10, -4, 10, 4, 6, Blocks.SPRUCE_STAIRS, s(Blocks.SPRUCE_PLANKS));
		b.fill(-10, 0, -4, 10, 0, 4, s(Blocks.SPRUCE_PLANKS));
		b.set(0, 1, 0, facing(Blocks.STONECUTTER, Direction.EAST));
		b.fill(-3, 1, 0, -1, 1, 0, axis(Blocks.OAK_LOG, Direction.Axis.X));
		b.fill(1, 1, 0, 3, 1, 0, slab(Blocks.OAK_SLAB, false));
		b.set(-8, 1, 3, Blocks.CRAFTING_TABLE);
		b.chest(8, 1, 3, Direction.WEST, "cabin");
		b.barrel(8, 1, -3, Direction.UP, "common");
		b.frame(-5, 2, 3, Direction.NORTH, Items.IRON_AXE);
		// Log piles.
		for (int[] pile : new int[][] {{-12, -10}, {-6, -10}, {2, -10}}) {
			b.fill(pile[0], 1, pile[1], pile[0] + 3, 2, pile[1] + 2, axis(Blocks.OAK_LOG, Direction.Axis.Z));
			b.fill(pile[0] + 1, 3, pile[1], pile[0] + 2, 3, pile[1] + 2, axis(Blocks.SPRUCE_LOG, Direction.Axis.Z));
		}
		b.fill(7, 1, -10, 10, 1, -8, axis(Blocks.STRIPPED_OAK_LOG, Direction.Axis.X));
		// The race and its wheel.
		b.fill(12, -1, -2, 14, 0, 10, DeepBuilder.WATER);
		b.fill(12, -2, -2, 14, -2, 10, s(Blocks.STONE));
		for (int k = -3; k <= 3; k++) {
			b.set(13, 3 + k, 6, s(Blocks.SPRUCE_PLANKS));
			b.set(13, 3, 6 + k, s(Blocks.SPRUCE_PLANKS));
		}
		b.set(13, 3, 6, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
		b.fill(11, 3, 6, 12, 3, 6, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
		b.pillar(11, 6, 1, 2, log(Blocks.DARK_OAK_LOG));
	}

	// --- the desert --------------------------------------------------------------------------------

	/** A desert bazaar: stalls under coloured awnings round a fountain, a trader and his llama, camels resting. */
	static void desertBazaar(DeepBuilder b) {
		site(b, -16, -16, 16, 16, 0, 10);
		b.fill(-14, 0, -14, 14, 0, 14, b.mix(s(Blocks.SMOOTH_SANDSTONE), s(Blocks.SAND), s(Blocks.SANDSTONE)));
		String[] colours = {"red", "orange", "yellow", "blue", "cyan", "purple"};
		int[][] stalls = {{-9, -9}, {0, -10}, {9, -9}, {-9, 9}, {0, 10}, {9, 9}};
		for (int i = 0; i < stalls.length; i++) {
			stall(b, stalls[i][0], stalls[i][1], colours[i]);
		}
		b.chest(-9, 1, -8, Direction.SOUTH, "market");
		b.chest(9, 1, 10, Direction.NORTH, "market");
		b.barrel(0, 1, -9, Direction.UP, "market");
		b.set(-10, 1, 10, Blocks.MELON);
		b.set(-8, 1, 10, Blocks.PUMPKIN);
		b.set(8, 1, -8, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
		// The fountain in the middle.
		b.cylinder(0, 0, 3.5, 0, 1, s(Blocks.CUT_SANDSTONE), true);
		b.disc(0, 0, 0, 2.5, DeepBuilder.WATER);
		b.pillar(0, 0, 1, 2, s(Blocks.CHISELED_SANDSTONE));
		b.set(0, 3, 0, s(Blocks.SMOOTH_SANDSTONE_SLAB));
		b.keeper(-4, 1, 4, EntityTypes.WANDERING_TRADER);
		b.keeper(-5, 1, 6, EntityTypes.LLAMA);
		b.keeper(5, 1, -5, EntityTypes.CAMEL);
		b.keeper(6, 1, 4, EntityTypes.CAMEL);
	}

	/** A sandstone tomb: a small temple above, stairs down to a burial hall kept by husks, a trap at its door. */
	static void sandstoneTomb(DeepBuilder b) {
		site(b, 12.5, 10);
		BlockState cut = s(Blocks.CUT_SANDSTONE);
		// The temple above.
		b.fill(-5, 0, -5, 5, 0, 5, s(Blocks.SMOOTH_SANDSTONE));
		b.walls(-5, 1, -5, 5, 5, 5, cut);
		b.fill(-5, 6, -5, 5, 6, 5, s(Blocks.SMOOTH_SANDSTONE_SLAB));
		b.clear(-1, 1, -5, 1, 3, -5);
		for (int x = -4; x <= 4; x += 8) {
			b.pillar(x, -6, 1, 5, s(Blocks.CHISELED_SANDSTONE));
		}
		// The stairs down, and the burial hall at their foot, with a trap just inside its door.
		LandParts.stairway(b, -1, 1, -3, 8, DeepBuilder.p(cut), Blocks.SANDSTONE_STAIRS);
		b.fill(-6, -9, 5, 6, -9, 15, cut);
		b.walls(-6, -8, 5, 6, -5, 15, cut);
		b.fill(-6, -4, 5, 6, -4, 15, cut);
		b.clear(-5, -8, 6, 5, -5, 14);
		b.clear(-1, -8, 5, 1, -6, 5);
		LandParts.tntTrap(b, 0, -8, 7, cut);
		b.fill(-1, -8, 12, 1, -8, 13, s(Blocks.CHISELED_SANDSTONE));
		b.set(0, -7, 12, head(Blocks.SKELETON_SKULL, 0));
		b.chest(-1, -8, 14, Direction.NORTH, "tomb");
		b.chest(1, -8, 14, Direction.NORTH, "tomb");
		b.spawner(-4, -8, 10, EntityTypes.HUSK);
		b.spawner(4, -8, 10, EntityTypes.HUSK);
		for (int z = 7; z <= 13; z += 3) {
			b.set(-6, -6, z, s(vanilla("orange_glazed_terracotta")));
			b.set(6, -6, z, s(vanilla("blue_glazed_terracotta")));
		}
	}

	/** An outlaws' saloon: a bar, a piano, tables and rooms upstairs, and the outlaws drinking in it. */
	static void outlawSaloon(DeepBuilder b) {
		site(b, -16, -13, 16, 13, 0, 16);
		DeepBuilder.Paint planks = DeepBuilder.p(s(Blocks.SPRUCE_PLANKS));
		LandParts.house(b, -10, -6, 10, 8, 10, planks, log(Blocks.DARK_OAK_LOG), s(Blocks.DARK_OAK_PLANKS));
		b.fill(-9, 5, -5, 9, 5, 7, s(Blocks.SPRUCE_PLANKS));
		b.fill(-10, 11, -6, 10, 11, 8, slab(Blocks.DARK_OAK_SLAB, false));
		// A false front over the door, a balcony on posts, swinging doors.
		b.fill(-10, 11, -6, 10, 13, -6, planks);
		b.fill(-10, 5, -9, 10, 5, -7, s(Blocks.SPRUCE_PLANKS));
		LandParts.railing(b, -10, 6, -9, 10, 6, -7, Blocks.SPRUCE_FENCE);
		b.clear(-9, 6, -7, 9, 6, -7);
		b.clear(-1, 6, -6, 1, 7, -6);
		for (int x = -8; x <= 8; x += 16) {
			b.pillar(x, -9, 1, 4, log(Blocks.DARK_OAK_LOG));
		}
		b.set(0, 1, -6, facing(Blocks.SPRUCE_FENCE_GATE, Direction.NORTH));
		b.clear(0, 2, -6, 0, 2, -6);
		// The bar.
		b.fill(-8, 1, 4, 2, 1, 4, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, true));
		b.fill(-8, 1, 7, 2, 2, 7, Blocks.BARREL);
		for (int x = -7; x <= 1; x += 2) {
			b.set(x, 2, 4, Blocks.BREWING_STAND);
		}
		b.chest(-9, 1, 6, Direction.EAST, "saloon");
		// The piano and the tables.
		b.fill(6, 1, 6, 8, 1, 6, Blocks.NOTE_BLOCK);
		b.set(7, 1, 5, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
		for (int[] t : new int[][] {{-5, -2}, {0, -2}, {5, -2}}) {
			LandParts.table(b, t[0], t[1], Blocks.DARK_OAK_FENCE, carpet("green"));
			LandParts.seat(b, t[0] - 1, t[1], Blocks.SPRUCE_STAIRS, Direction.WEST);
			LandParts.seat(b, t[0] + 1, t[1], Blocks.SPRUCE_STAIRS, Direction.EAST);
		}
		// Stairs up along the east wall, and the rooms.
		for (int k = 0; k <= 4; k++) {
			b.set(9, 1 + k, 3 - k, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
		}
		b.clear(9, 5, 0, 9, 5, 3);
		b.bed(-8, 6, 1, Direction.SOUTH, vanilla("red_bed"));
		b.bed(-4, 6, 1, Direction.SOUTH, vanilla("red_bed"));
		b.chest(-9, 6, 7, Direction.EAST, "saloon");
		b.set(0, 10, 2, lantern(false, true));
		b.set(0, 4, 0, lantern(false, true));
		b.keeper(-3, 1, 1, EntityTypes.PILLAGER);
		b.keeper(4, 1, 1, EntityTypes.PILLAGER);
		b.keeper(-6, 6, 4, EntityTypes.VINDICATOR);
		b.keeper(12, 1, -10, EntityTypes.HORSE);
	}

	// --- the jungle -----------------------------------------------------------------------------------

	/** A temple swallowed by the jungle: four steps of mossy stone under vines and leaves, spiders in its heart. */
	static void overgrownTemple(DeepBuilder b) {
		site(b, -15, -15, 15, 15, 0, 16);
		DeepBuilder.Paint stone = b.worn(s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.CRACKED_STONE_BRICKS));
		b.fill(-12, 1, -12, 12, 3, 12, stone);
		b.fill(-9, 4, -9, 9, 6, 9, stone);
		b.fill(-6, 7, -6, 6, 9, 6, stone);
		b.fill(-3, 10, -3, 3, 12, 3, s(Blocks.CHISELED_STONE_BRICKS));
		// The hall inside the lowest step, entered from the east, and the shrine on top.
		b.clear(-9, 1, -9, 9, 3, 9);
		b.clear(10, 1, -1, 12, 2, 1);
		for (int x = -6; x <= 6; x += 6) {
			for (int z = -6; z <= 6; z += 6) {
				b.pillar(x, z, 1, 3, s(Blocks.CHISELED_STONE_BRICKS));
			}
		}
		b.spawner(0, 1, 4, EntityTypes.SPIDER);
		b.chest(-8, 1, 8, Direction.EAST, "temple");
		b.chest(8, 1, 8, Direction.WEST, "temple");
		webs(b, -8, 3, -8, 8, 8);
		b.clear(-2, 10, -2, 2, 11, 2);
		b.clear(0, 10, -3, 0, 11, -3);
		b.chest(0, 10, 1, Direction.NORTH, "jungle");
		// The great stairs up the north face.
		for (int k = 0; k < 9; k++) {
			b.fill(-2, k + 1, -13 + k, 2, k + 1, -13 + k, stairs(Blocks.MOSSY_STONE_BRICK_STAIRS, Direction.SOUTH, false));
			b.clear(-2, k + 2, -13 + k, 2, k + 4, -13 + k);
		}
		// The jungle taking it back.
		for (int i = 0; i < 40; i++) {
			int x = b.layout.nextInt(27) - 13;
			int z = b.layout.nextInt(27) - 13;
			if (Math.abs(x) <= 2 && z < -3) {
				continue;
			}
			int y = Math.abs(x) <= 3 && Math.abs(z) <= 3 ? 13 : Math.abs(x) <= 6 && Math.abs(z) <= 6 ? 10
					: Math.abs(x) <= 9 && Math.abs(z) <= 9 ? 7 : 4;
			b.set(x, y, z, leaves(Blocks.JUNGLE_LEAVES));
		}
		for (int y = 1; y <= 3; y++) {
			for (int x = -11; x <= 11; x += 4) {
				if (Math.abs(x) > 2) {
					b.set(x, y, -13, vine(Direction.SOUTH));
				}
			}
		}
	}

	/** A ruined temple taken over by monkeys, with a gorilla that minds it; their treasure is up on the pillars. */
	static void monkeyTemple(DeepBuilder b) {
		site(b, -14, -14, 14, 14, 0, 16);
		DeepBuilder.Paint stone = b.ruin(b.worn(s(Blocks.MOSSY_COBBLESTONE), s(Blocks.COBBLESTONE), s(Blocks.MOSSY_STONE_BRICKS)), 0.15);
		b.fill(-12, 0, -12, 12, 0, 12, b.mix(s(Blocks.MOSSY_COBBLESTONE), s(Blocks.GRASS_BLOCK), s(Blocks.COBBLESTONE)));
		b.walls(-12, 1, -12, 12, 4, 12, stone);
		b.clear(-2, 1, -12, 2, 3, -12);
		int[][] pillars = {{-8, -8}, {8, -8}, {-8, 8}, {8, 8}, {0, 6}};
		for (int[] p : pillars) {
			b.pillar(p[0], p[1], 1, 9, s(Blocks.MOSSY_STONE_BRICKS));
			b.set(p[0], 10, p[1], s(Blocks.CHISELED_STONE_BRICKS));
		}
		b.chest(-8, 11, -8, Direction.NORTH, "jungle");
		b.chest(8, 11, 8, Direction.NORTH, "temple");
		// The idol: a golden face on a stone stand, with emerald eyes.
		b.fill(-1, 1, 9, 1, 2, 11, s(Blocks.MOSSY_STONE_BRICKS));
		b.fill(0, 3, 10, 0, 5, 10, s(Blocks.GOLD_BLOCK));
		b.set(-1, 4, 10, s(Blocks.CHISELED_STONE_BRICKS));
		b.set(1, 4, 10, s(Blocks.CHISELED_STONE_BRICKS));
		b.set(-1, 4, 9, s(Blocks.EMERALD_BLOCK));
		b.set(1, 4, 9, s(Blocks.EMERALD_BLOCK));
		b.set(0, 1, 7, Blocks.MELON);
		b.pillar(3, 4, 1, 4, log(Blocks.JUNGLE_LOG));
		BlockState cocoa = s(Blocks.COCOA).setValue(BlockStateProperties.AGE_2, 2);
		b.set(3, 2, 3, cocoa.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		b.set(2, 3, 4, cocoa.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		for (int i = 0; i < 6; i++) {
			b.keeper(-6 + i * 2, 1, -3 + (i % 2) * 4, "monkey");
		}
		b.keeper(0, 1, 2, "gorilla");
	}

	// --- the cold north --------------------------------------------------------------------------------

	/** A rangers' outpost: a tall boarded watchtower and a barracks, a target range and the rangers' gear. */
	static void rangerOutpost(DeepBuilder b) {
		site(b, -15, -15, 15, 15, 0, 20);
		b.walls(-11, 1, -11, -7, 14, -7, s(Blocks.SPRUCE_PLANKS));
		for (int[] c : new int[][] {{-11, -11}, {-7, -11}, {-11, -7}, {-7, -7}}) {
			b.pillar(c[0], c[1], 1, 18, log(Blocks.SPRUCE_LOG));
		}
		b.clear(-9, 1, -7, -9, 2, -7);
		for (int y = 5; y <= 13; y += 4) {
			b.set(-11, y, -9, joined(Blocks.GLASS_PANE, false));
			b.set(-9, y, -11, joined(Blocks.GLASS_PANE, true));
		}
		b.fill(-11, 15, -11, -7, 15, -7, s(Blocks.SPRUCE_PLANKS));
		LandParts.railing(b, -11, 16, -11, -7, 16, -7, Blocks.SPRUCE_FENCE);
		b.fill(-12, 19, -12, -6, 19, -6, slab(Blocks.SPRUCE_SLAB, false));
		for (int y = 1; y <= 15; y++) {
			b.set(-8, y, -9, facing(Blocks.LADDER, Direction.WEST));
		}
		b.chest(-10, 16, -10, Direction.EAST, "ranger");
		// The barracks.
		LandParts.cottage(b, 1, 2, 12, 10, 4, DeepBuilder.p(s(Blocks.SPRUCE_PLANKS)), log(Blocks.STRIPPED_SPRUCE_LOG), s(Blocks.SPRUCE_PLANKS),
				Blocks.SPRUCE_STAIRS, s(Blocks.SPRUCE_PLANKS));
		for (int x = 2; x <= 10; x += 4) {
			b.bed(x, 1, 7, Direction.SOUTH, vanilla("green_bed"));
		}
		b.chest(11, 1, 5, Direction.WEST, "ranger");
		b.stand(11, 1, 3, Direction.WEST, new ItemStack(Items.CHAINMAIL_HELMET), new ItemStack(Items.CHAINMAIL_CHESTPLATE),
				new ItemStack(Items.CHAINMAIL_LEGGINGS), new ItemStack(Items.CHAINMAIL_BOOTS), new ItemStack(Items.CROSSBOW));
		b.set(4, 1, 9, lantern(false, false));
		// The target range.
		for (int x = -4; x <= 4; x += 4) {
			b.set(x, 1, 12, Blocks.TARGET);
			b.set(x, 2, 12, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
		}
		b.set(0, 1, -2, s(Blocks.CAMPFIRE));
		b.keeper(-2, 1, 0, EntityTypes.WOLF);
	}

	/** The bones of a herd of mammoths in the snow, tusks curling out of the drifts, and strays rising from the ice cave beneath. */
	static void mammothGraveyard(DeepBuilder b) {
		site(b, 15.5, 12);
		for (int[] m : new int[][] {{-7, -5, 0}, {6, 4, 1}, {-2, 9, 1}}) {
			int x0 = m[0];
			int z0 = m[1];
			boolean alongX = m[2] == 1;
			// A spine and arching ribs.
			for (int k = -4; k <= 4; k++) {
				int x = alongX ? x0 + k : x0;
				int z = alongX ? z0 : z0 + k;
				b.set(x, 4 - Math.abs(k) / 3, z, s(Blocks.BONE_BLOCK));
				if (k % 2 == 0 && Math.abs(k) < 4) {
					for (int side = -1; side <= 1; side += 2) {
						int rx = alongX ? x : x + side * 2;
						int rz = alongX ? z + side * 2 : z;
						b.pillar(rx, rz, 1, 3, axis(Blocks.BONE_BLOCK, Direction.Axis.Y));
					}
				}
			}
			// The skull, and its tusks.
			int hx = alongX ? x0 - 6 : x0;
			int hz = alongX ? z0 : z0 - 6;
			b.fill(hx - 1, 1, hz - 1, hx + 1, 3, hz + 1, s(Blocks.BONE_BLOCK));
			for (int side = -1; side <= 1; side += 2) {
				int tx = alongX ? hx - 2 : hx + side * 2;
				int tz = alongX ? hz + side * 2 : hz - 2;
				b.line(tx, 1, tz, alongX ? tx - 3 : tx + side, 4, alongX ? tz + side : tz - 3, s(Blocks.CALCITE));
			}
		}
		for (int i = 0; i < 6; i++) {
			int x = b.layout.nextInt(25) - 12;
			int z = b.layout.nextInt(25) - 12;
			b.pillar(x, z, 1, 2 + b.layout.nextInt(4), s(Blocks.PACKED_ICE));
		}
		// The ice cave below, dark enough for the dead to rise, and the way down into it.
		b.fill(-4, -7, -4, 4, -2, 4, s(Blocks.PACKED_ICE));
		b.clear(-3, -6, -3, 3, -3, 3);
		for (int k = 0; k <= 4; k++) {
			b.fill(0, -1 - k, -8 + k, 1, -1 - k, -8 + k, s(Blocks.SNOW_BLOCK));
			b.clear(0, -k, -8 + k, 1, -k + 2, -8 + k);
		}
		b.spawner(0, -6, 2, EntityTypes.STRAY);
		b.chest(-3, -6, 3, Direction.EAST, "explorer");
		b.chest(3, -6, 3, Direction.WEST, "common");
		b.set(-3, -6, -3, head(Blocks.SKELETON_SKULL, 6));
	}

	// --- the mountains ----------------------------------------------------------------------------

	/** A stone observatory on a mountainside, its glass dome and its telescope pointed at the stars. */
	static void mountainObservatory(DeepBuilder b) {
		site(b, 11.5, 30);
		DeepBuilder.Paint stone = b.worn(s(Blocks.STONE_BRICKS), s(Blocks.POLISHED_ANDESITE), s(Blocks.STONE_BRICKS));
		b.cylinder(0, 0, 6.5, 1, 14, stone, true);
		b.disc(0, 0, 0, 5.5, s(Blocks.POLISHED_ANDESITE));
		b.disc(0, 7, 0, 5.5, s(Blocks.SPRUCE_PLANKS));
		b.disc(0, 14, 0, 5.5, s(Blocks.SPRUCE_PLANKS));
		b.sphere(0, 15, 0, 7.0, (x, y, z) -> b.chance(x, y, z, 84, 0.15) ? s(Blocks.STONE_BRICKS) : s(Blocks.GLASS), true, 0);
		b.clear(0, 1, -6, 0, 3, -6);
		for (int y = 1; y <= 7; y++) {
			b.set(5, y, 0, facing(Blocks.LADDER, Direction.WEST));
		}
		for (int y = 8; y <= 14; y++) {
			b.set(-5, y, 0, facing(Blocks.LADDER, Direction.EAST));
		}
		// The telescope, poking out through a slot in the dome.
		b.line(-2, 15, 0, 4, 20, 0, s(vanilla("waxed_copper_block")));
		b.clear(5, 19, -1, 7, 21, 1);
		b.set(5, 20, 0, s(vanilla("waxed_copper_block")));
		b.set(-3, 1, 2, Blocks.CARTOGRAPHY_TABLE);
		b.chest(-4, 1, -2, Direction.EAST, "observatory");
		b.chest(2, 15, 3, Direction.NORTH, "observatory");
		b.frame(0, 2, 5, Direction.NORTH, Items.SPYGLASS);
		b.bed(2, 8, 1, Direction.SOUTH, vanilla("blue_bed"));
		b.set(0, 13, 0, lantern(false, true));
		b.set(0, 6, 0, lantern(false, true));
	}

	/** A dwarven forge carved down into the rock: a great hall of deepslate, lava running through it, anvils and riches. */
	static void dwarvenForge(DeepBuilder b) {
		site(b, 10.5, 8);
		DeepBuilder.Paint stone = b.worn(s(Blocks.DEEPSLATE_BRICKS), s(Blocks.DEEPSLATE_TILES), s(Blocks.CRACKED_DEEPSLATE_BRICKS));
		// The gate in the ground, and the long stairs behind it.
		b.fill(-3, 0, -9, 3, 5, -7, stone);
		b.clear(-1, 1, -9, 1, 3, -7);
		b.fill(-2, 6, -9, 2, 6, -7, s(Blocks.POLISHED_DEEPSLATE));
		b.set(-2, 7, -8, lantern(false, false));
		b.set(2, 7, -8, lantern(false, false));
		LandParts.stairway(b, -1, 1, -6, 8, stone, Blocks.DEEPSLATE_BRICK_STAIRS);
		// The hall.
		b.fill(-8, -9, 2, 8, -9, 16, s(Blocks.POLISHED_DEEPSLATE));
		b.walls(-8, -8, 2, 8, -3, 16, stone);
		b.fill(-8, -2, 2, 8, -2, 16, stone);
		b.clear(-7, -8, 3, 7, -3, 15);
		b.clear(-1, -8, 2, 1, -6, 2);
		b.fill(-1, -9, 6, 1, -9, 13, s(Blocks.LAVA));
		LandParts.railing(b, -2, -8, 6, 2, -8, 13, Blocks.IRON_BARS);
		for (int z = 5; z <= 13; z += 4) {
			b.set(-6, -8, z, facing(Blocks.ANVIL, Direction.EAST));
			b.set(6, -8, z, s(Blocks.BLAST_FURNACE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
					.setValue(BlockStateProperties.LIT, true));
		}
		for (int x = -6; x <= 6; x += 4) {
			b.set(x, -3, 9, lantern(false, true));
		}
		b.chest(-7, -8, 15, Direction.EAST, "forge");
		b.chest(7, -8, 15, Direction.WEST, "forge");
		b.set(0, -8, 15, s(Blocks.GOLD_BLOCK));
		b.stand(-4, -8, 15, Direction.NORTH, new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
				new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.IRON_AXE));
		b.keeper(4, -8, 10, EntityTypes.VINDICATOR);
		b.keeper(-4, -8, 6, EntityTypes.VINDICATOR);
	}

	// --- the plains and the savanna -----------------------------------------------------------------

	/** A working farm: a barn, a farmhouse, fields of four crops, a pen of animals and a well. */
	static void farmstead(DeepBuilder b) {
		site(b, -16, -16, 16, 16, 0, 14);
		LandParts.cottage(b, -14, -14, -4, -6, 5, DeepBuilder.p(s(vanilla("red_terracotta"))), log(Blocks.DARK_OAK_LOG), s(Blocks.SPRUCE_PLANKS),
				Blocks.DARK_OAK_STAIRS, s(Blocks.DARK_OAK_PLANKS));
		b.clear(-10, 1, -14, -8, 4, -14);
		b.fill(-13, 1, -12, -11, 2, -8, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
		b.barrel(-5, 1, -7, Direction.UP, "farm");
		LandParts.cottage(b, 4, -14, 13, -7, 4, DeepBuilder.p(s(Blocks.OAK_PLANKS)), log(Blocks.OAK_LOG), s(Blocks.OAK_PLANKS),
				Blocks.OAK_STAIRS, s(Blocks.OAK_PLANKS));
		b.bed(5, 1, -10, Direction.SOUTH, vanilla("yellow_bed"));
		b.chest(12, 1, -8, Direction.WEST, "farm");
		b.set(12, 1, -12, facing(Blocks.FURNACE, Direction.WEST));
		LandParts.field(b, -14, 2, -9, 14, Blocks.WHEAT);
		LandParts.field(b, -7, 2, -2, 14, Blocks.CARROTS);
		LandParts.field(b, 0, 8, 5, 14, Blocks.POTATOES);
		LandParts.field(b, 7, 8, 12, 14, Blocks.BEETROOTS);
		// The pen.
		LandParts.railing(b, 2, 1, -3, 13, 1, 5, Blocks.OAK_FENCE);
		b.set(7, 1, -3, facing(Blocks.OAK_FENCE_GATE, Direction.NORTH));
		b.keeper(5, 1, 0, EntityTypes.COW);
		b.keeper(8, 1, 2, EntityTypes.SHEEP);
		b.keeper(10, 1, -1, EntityTypes.CHICKEN);
		b.keeper(4, 1, 3, EntityTypes.PIG);
		// The well.
		b.cylinder(-1, -3, 1.5, 0, 1, s(Blocks.COBBLESTONE), true);
		b.fill(-1, -3, -3, -1, 0, -3, DeepBuilder.WATER);
	}

	/** A mill among the fields: a stone base, a timber tower and four great sails of white cloth. */
	static void windmill(DeepBuilder b) {
		site(b, 12.5, 26);
		b.cylinder(0, 0, 4.5, 1, 7, b.worn(s(Blocks.COBBLESTONE), s(Blocks.MOSSY_COBBLESTONE)), true);
		b.cylinder(0, 0, 4.0, 8, 15, DeepBuilder.p(s(Blocks.OAK_PLANKS)), true);
		b.disc(0, 0, 0, 3.5, s(Blocks.OAK_PLANKS));
		b.disc(0, 8, 0, 3.0, s(Blocks.SPRUCE_PLANKS));
		for (int k = 0; k <= 4; k++) {
			b.disc(0, 16 + k, 0, 4.5 - k, s(Blocks.DARK_OAK_PLANKS));
		}
		b.clear(0, 1, -4, 0, 2, -4);
		for (int y = 1; y <= 8; y++) {
			b.set(3, y, 0, facing(Blocks.LADDER, Direction.WEST));
		}
		// The sails, turned to the north, round their hub.
		b.set(0, 12, -5, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
		for (int k = 1; k <= 7; k++) {
			b.set(0, 12 + k, -6, Blocks.OAK_FENCE);
			b.set(0, 12 - k, -6, Blocks.OAK_FENCE);
			b.set(k, 12, -6, joined(Blocks.OAK_FENCE, true));
			b.set(-k, 12, -6, joined(Blocks.OAK_FENCE, true));
			if (k >= 2) {
				b.set(1, 12 + k, -6, wool("white"));
				b.set(-1, 12 - k, -6, wool("white"));
				b.set(k, 11, -6, wool("white"));
				b.set(-k, 13, -6, wool("white"));
			}
		}
		b.set(0, 12, -6, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
		b.set(-2, 1, 1, s(Blocks.GRINDSTONE).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
		b.barrel(-2, 1, -1, Direction.UP, "farm");
		b.barrel(2, 9, 1, Direction.UP, "farm");
		b.fill(1, 1, 2, 2, 1, 2, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
		b.chest(-2, 9, -1, Direction.EAST, "farm");
		b.set(0, 15, 0, lantern(false, true));
	}

	/** A safari camp: tents, a map table, crates of supplies and a corral of zebras and a giraffe. Bring a saddle. */
	static void safariCamp(DeepBuilder b) {
		site(b, -16, -16, 16, 16, 0, 12);
		LandParts.tent(b, -10, -12, -6, wool("white"));
		LandParts.tent(b, -4, -12, -6, wool("lime"));
		LandParts.tent(b, 2, -12, -6, wool("white"));
		b.chest(-10, 1, -7, Direction.NORTH, "safari");
		b.chest(2, 1, -7, Direction.NORTH, "safari");
		b.set(-4, 1, -7, Blocks.CARTOGRAPHY_TABLE);
		b.fill(7, 1, -12, 9, 1, -10, Blocks.BARREL);
		b.barrel(8, 2, -11, Direction.UP, "safari");
		b.set(-6, 1, -2, s(Blocks.CAMPFIRE));
		// The corral.
		LandParts.railing(b, -12, 1, 2, 12, 1, 14, Blocks.ACACIA_FENCE);
		b.set(0, 1, 2, facing(Blocks.ACACIA_FENCE_GATE, Direction.NORTH));
		b.fill(-11, 1, 13, -9, 1, 13, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
		b.set(10, 1, 12, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		b.keeper(-6, 1, 7, "zebra");
		b.keeper(-2, 1, 10, "zebra");
		b.keeper(6, 1, 8, "giraffe");
	}

	/** A den in a rocky outcrop: bones at its mouth, the remains of the last fool to go in, and its owners at home. */
	static void beastDen(DeepBuilder b) {
		site(b, 14.5, 4);
		DeepBuilder.Paint rock = b.mix(s(Blocks.STONE), s(Blocks.ANDESITE), s(Blocks.COBBLESTONE), s(Blocks.MOSSY_COBBLESTONE));
		b.ellipsoid(0, 0, 2, 10.0, 7.0, 9.0, rock, false, 0);
		b.ellipsoid(0, 1, 3, 6.5, 4.0, 5.5, (x, y, z) -> b.open(), false, 0);
		b.clear(-2, 1, -8, 2, 3, -2);
		b.fill(-6, 0, -2, 6, 0, 8, b.mix(s(Blocks.COARSE_DIRT), s(Blocks.GRAVEL), s(Blocks.DIRT)));
		for (int i = 0; i < 8; i++) {
			int x = b.layout.nextInt(11) - 5;
			int z = b.layout.nextInt(9) - 1;
			b.set(x, 1, z, b.pick(x, 1, z, 85, s(Blocks.BONE_BLOCK), head(Blocks.SKELETON_SKULL, 3), s(Blocks.BONE_BLOCK)));
		}
		b.stand(3, 1, 6, Direction.WEST, new ItemStack(Items.CHAINMAIL_HELMET), new ItemStack(Items.LEATHER_CHESTPLATE), ItemStack.EMPTY,
				ItemStack.EMPTY, new ItemStack(Items.STONE_SWORD));
		b.chest(-4, 1, 6, Direction.EAST, "den");
		b.set(0, 5, 3, lantern(false, true));
		switch (b.climate()) {
			case SAVANNA -> {
				b.keeper(-2, 1, 4, "lion");
				b.keeper(2, 1, 3, "lion");
			}
			case TAIGA, SNOWY -> {
				b.keeper(0, 1, 4, "grizzly_bear");
				b.keeper(-3, 1, 2, EntityTypes.WOLF);
			}
			case JUNGLE -> b.keeper(0, 1, 4, "jaguar");
			default -> b.keeper(0, 1, 4, "grizzly_bear");
		}
	}

	// --- anywhere ---------------------------------------------------------------------------------------

	/** A library fallen into ruin, its shelves still full, cobwebs everywhere and an enchanting table in the back. */
	static void forgottenLibrary(DeepBuilder b) {
		site(b, -15, -13, 15, 13, 0, 14);
		DeepBuilder.Paint wall = b.ruin(b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS)), 0.06);
		LandParts.house(b, -12, -9, 12, 9, 8, wall, s(Blocks.CHISELED_STONE_BRICKS), s(Blocks.OAK_PLANKS));
		b.fill(-12, 9, -9, 12, 9, 9, b.ruin(DeepBuilder.p(s(Blocks.DARK_OAK_PLANKS)), 0.15));
		b.clear(-1, 1, -9, 1, 4, -9);
		for (int z : new int[] {-6, -3, 0, 3}) {
			for (int side = -1; side <= 1; side += 2) {
				b.fill(side * 3, 1, z, side * 10, 3, z, Blocks.BOOKSHELF);
				b.set(side * 2, 1, z, facing(Blocks.CHISELED_BOOKSHELF, side < 0 ? Direction.EAST : Direction.WEST));
			}
		}
		// The enchanting table in its alcove of shelves.
		b.set(0, 1, 6, Blocks.ENCHANTING_TABLE);
		for (int y = 1; y <= 2; y++) {
			b.fill(-2, y, 8, 2, y, 8, Blocks.BOOKSHELF);
			b.fill(-2, y, 5, -2, y, 7, Blocks.BOOKSHELF);
			b.fill(2, y, 5, 2, y, 7, Blocks.BOOKSHELF);
		}
		b.set(-5, 1, 7, facing(Blocks.LECTERN, Direction.EAST));
		b.chest(-11, 1, 8, Direction.EAST, "library");
		b.chest(11, 1, -8, Direction.WEST, "library");
		for (int x = -9; x <= 9; x += 6) {
			b.set(x, 9, 0, s(Blocks.DARK_OAK_PLANKS));
			b.set(x, 8, 0, lantern(false, true));
			webs(b, x - 2, 7, -8, x + 2, 8);
		}
	}
}
