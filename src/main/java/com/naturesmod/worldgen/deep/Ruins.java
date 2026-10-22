package com.naturesmod.worldgen.deep;

import static com.naturesmod.worldgen.deep.DeepBuilder.WATER;
import static com.naturesmod.worldgen.deep.DeepBuilder.axis;
import static com.naturesmod.worldgen.deep.DeepBuilder.facing;
import static com.naturesmod.worldgen.deep.DeepBuilder.head;
import static com.naturesmod.worldgen.deep.DeepBuilder.joined;
import static com.naturesmod.worldgen.deep.DeepBuilder.lantern;
import static com.naturesmod.worldgen.deep.DeepBuilder.pickles;
import static com.naturesmod.worldgen.deep.DeepBuilder.s;
import static com.naturesmod.worldgen.deep.DeepBuilder.slab;
import static com.naturesmod.worldgen.deep.DeepBuilder.stairs;

import com.naturesmod.block.ModBlocks;

import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The small ruins strewn all over the floor of the Abyss. */
final class Ruins {
	static final Item[] DISCS = {Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS, Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_FAR,
			Items.MUSIC_DISC_MALL, Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_STRAD, Items.MUSIC_DISC_WARD,
			Items.MUSIC_DISC_11, Items.MUSIC_DISC_WAIT, Items.MUSIC_DISC_PIGSTEP, Items.MUSIC_DISC_OTHERSIDE, Items.MUSIC_DISC_5,
			Items.MUSIC_DISC_RELIC, Items.MUSIC_DISC_CREATOR, Items.MUSIC_DISC_PRECIPICE};

	private Ruins() {
	}

	private static BlockState coral(DeepBuilder b, int x, int y, int z) {
		return b.pick(x, y, z, 31, s(Blocks.TUBE_CORAL_BLOCK), s(Blocks.BRAIN_CORAL_BLOCK), s(Blocks.BUBBLE_CORAL_BLOCK), s(Blocks.FIRE_CORAL_BLOCK),
				s(Blocks.HORN_CORAL_BLOCK));
	}

	/** Eight columns round an altar, some standing, some fallen. */
	static void columnCircle(DeepBuilder b) {
		b.padRound(0, 0, 7.5, (x, y, z) -> {
			double r = Math.sqrt(x * x + z * z);
			return Math.abs(r - 4.0) < 0.6 ? s(Blocks.DARK_PRISMARINE) : r <= 1.5 ? s(Blocks.PRISMARINE_BRICKS)
					: b.chance(x, y, z, 1, 0.12) ? s(Blocks.GRAVEL) : s(Blocks.PRISMARINE);
		}, s(Blocks.COBBLED_DEEPSLATE), 12);
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4;
			int x = (int) Math.round(Math.cos(angle) * 6);
			int z = (int) Math.round(Math.sin(angle) * 6);
			int height = 3 + b.layout.nextInt(6);
			boolean fallen = b.layout.nextInt(4) == 0;
			if (fallen) {
				// Toppled along the ring, broken in two.
				b.set(x, 1, z, Blocks.DARK_PRISMARINE);
				for (int t = 1; t <= height; t++) {
					int fx = (int) Math.round(Math.cos(angle) * 6 - Math.sin(angle) * t);
					int fz = (int) Math.round(Math.sin(angle) * 6 + Math.cos(angle) * t);
					if (t != height / 2) {
						b.set(fx, 1, fz, Blocks.PRISMARINE_BRICKS);
					}
				}
				continue;
			}
			b.set(x, 1, z, Blocks.DARK_PRISMARINE);
			b.pillar(x, z, 2, height, s(Blocks.PRISMARINE_BRICKS));
			b.set(x, height + 1, z, height >= 6 ? s(Blocks.SEA_LANTERN) : s(Blocks.DARK_PRISMARINE));
		}
		b.fill(-1, 1, -1, 1, 1, 1, Blocks.DARK_PRISMARINE);
		b.set(0, 1, 0, Blocks.SEA_LANTERN);
		b.chest(0, 2, 0, Direction.NORTH, "ruin");
		for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			b.set(c[0], 2, c[1], pickles(4));
		}
	}

	/** A colossal statue lies on its back where it fell, still wearing its golden crown. */
	static void fallenStatue(DeepBuilder b) {
		DeepBuilder.Paint stone = b.worn(s(Blocks.POLISHED_ANDESITE), s(Blocks.ANDESITE), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.STONE));
		b.pad(-7, -9, 7, 9, b.mix(s(Blocks.GRAVEL), s(Blocks.SAND), s(Blocks.GRAVEL)), s(Blocks.COBBLED_DEEPSLATE), 8);
		// The head, face up, eyes still glowing.
		b.sphere(0, 2, -6, 2.6, stone, false, -1);
		b.set(-1, 4, -7, Blocks.SEA_LANTERN);
		b.set(1, 4, -7, Blocks.SEA_LANTERN);
		b.set(0, 5, -6, Blocks.POLISHED_ANDESITE);
		for (int x = -2; x <= 2; x++) {
			b.set(x, 3, -4 + (Math.abs(x) == 2 ? -1 : 0), (x + 2) % 2 == 0 ? s(Blocks.GOLD_BLOCK) : s(Blocks.RAW_GOLD_BLOCK));
		}
		b.set(0, 4, -4, Blocks.GOLD_BLOCK);
		// Shoulders and chest.
		b.fill(-3, 1, -3, 3, 3, 2, stone);
		b.fill(-2, 4, -2, 2, 4, 1, stone);
		// The arms lie at its sides, the right hand open over its treasure.
		b.line(-4, 2, -2, -5, 1, 4, stone);
		b.line(-4, 1, -2, -5, 1, 3, stone);
		b.line(4, 2, -2, 5, 1, 4, stone);
		b.line(4, 1, -2, 5, 1, 3, stone);
		b.fill(4, 1, 5, 6, 1, 6, stone);
		b.chest(5, 2, 5, Direction.WEST, "ruin");
		// The legs, one broken off at the knee.
		b.fill(-2, 1, 3, -1, 2, 9, stone);
		b.fill(1, 1, 3, 2, 2, 6, stone);
		b.fill(2, 1, 8, 3, 2, 9, stone);
		b.set(-6, 1, -6, lantern(false, false));
		b.set(6, 1, -5, lantern(false, false));
	}

	/** A heap of gold left where a treasure ship spilled it, picked over by no one. */
	static void treasurePile(DeepBuilder b) {
		b.padRound(0, 0, 7.5, b.mix(s(Blocks.SAND), s(Blocks.SAND), s(Blocks.GRAVEL)), s(Blocks.SANDSTONE), 10);
		b.chest(-5, 1, 1, Direction.WEST, "treasury");
		b.chest(4, 1, -4, Direction.EAST, "ruin");
		b.hoard(0, 1, 0, 4.2, s(Blocks.GOLD_BLOCK), s(Blocks.GOLD_BLOCK), s(Blocks.RAW_GOLD_BLOCK), s(Blocks.GOLD_BLOCK), s(Blocks.RAW_GOLD_BLOCK),
				s(Blocks.DEEPSLATE_GOLD_ORE));
		int top = 1;
		while (top < 6 && b.solid(0, top, 0)) {
			top++;
		}
		b.set(0, top, 0, Blocks.DIAMOND_BLOCK);
		b.set(0, top + 1, 0, b.head(0, top + 1, 0));
		for (int[] post : new int[][] {{-6, -3}, {6, 3}, {-2, 6}, {3, -6}}) {
			b.lampPost(post[0], post[1], 1, 2, s(Blocks.SPRUCE_FENCE), lantern(false, false));
		}
		for (int[] skull : new int[][] {{-4, -4}, {5, 0}, {-1, 5}}) {
			b.set(skull[0], 1, skull[1], b.head(skull[0], 1, skull[1]));
		}
	}

	/** A little pavilion of white stone under a dome of living coral. */
	static void coralGazebo(DeepBuilder b) {
		b.padRound(0, 0, 6.5, (x, y, z) -> Math.max(Math.abs(x), Math.abs(z)) <= 3 ? s(Blocks.SMOOTH_QUARTZ) : s(Blocks.SAND),
				s(Blocks.SANDSTONE), 12);
		for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			b.pillar(c[0], c[1], 1, 4, axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
			b.set(c[0], 5, c[1], Blocks.CHISELED_QUARTZ_BLOCK);
		}
		b.fill(-3, 5, -3, 3, 5, 3, Blocks.SMOOTH_QUARTZ);
		b.sphere(0, 5, 0, 4.4, (x, y, z) -> b.chance(x, y, z, 2, 0.1) ? s(Blocks.SEA_LANTERN) : coral(b, x, y, z), true, 1);
		b.fill(-2, 5, -2, 2, 5, 2, WATER);
		b.set(0, 8, 0, Blocks.SEA_LANTERN);
		b.set(0, 1, 0, Blocks.CHISELED_QUARTZ_BLOCK);
		b.chest(0, 2, 0, Direction.NORTH, "ruin");
		for (int i = 0; i < 12; i++) {
			int x = b.layout.nextInt(13) - 6;
			int z = b.layout.nextInt(13) - 6;
			if (Math.max(Math.abs(x), Math.abs(z)) > 3) {
				b.set(x, 1, z, b.pick(x, 1, z, 3, s(Blocks.TUBE_CORAL), s(Blocks.FIRE_CORAL), s(Blocks.HORN_CORAL), pickles(3),
						s(ModBlocks.RED_SEA_ANEMONE), s(ModBlocks.ORANGE_STARFISH)));
			}
		}
	}

	/** A village well, its shaft running deep into the sea floor with something at the bottom. */
	static void sunkenWell(DeepBuilder b) {
		b.pad(-5, -5, 5, 5, b.worn(s(Blocks.COBBLESTONE), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.GRAVEL)), s(Blocks.COBBLESTONE), 9);
		// The shaft.
		b.fill(-2, -12, -2, 2, 0, 2, b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS)));
		b.water(-1, -11, -1, 1, 0, 1);
		b.fill(-1, -12, -1, 1, -12, 1, Blocks.GRAVEL);
		b.set(-1, -11, -1, Blocks.SEA_LANTERN);
		b.chest(0, -11, 1, Direction.NORTH, "ruin");
		b.set(1, -11, -1, b.head(1, -11, -1));
		// The wall round its mouth and the roof over it.
		b.walls(-2, 1, -2, 2, 1, 2, b.worn(s(Blocks.MOSSY_COBBLESTONE), s(Blocks.COBBLESTONE)));
		for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			b.pillar(c[0], c[1], 2, 4, s(Blocks.OAK_FENCE));
		}
		b.fill(-2, 5, -2, 2, 5, 2, slab(Blocks.DARK_OAK_SLAB, false));
		b.fill(-1, 5, -1, 1, 5, 1, Blocks.DARK_OAK_PLANKS);
		b.set(0, 4, 0, axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
		b.set(0, 3, 0, lantern(false, true));
		b.set(3, 1, -3, Blocks.WATER_CAULDRON);
	}

	/** A black shrine whose shelves hold the heads of everything that came too close. */
	static void skullShrine(DeepBuilder b) {
		BlockState bricks = s(Blocks.POLISHED_BLACKSTONE_BRICKS);
		b.pad(-5, -4, 5, 4, b.worn(s(Blocks.POLISHED_BLACKSTONE), s(Blocks.BLACKSTONE), s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)),
				s(Blocks.BLACKSTONE), 10);
		b.fill(-4, 1, 3, 4, 6, 3, bricks);
		b.fill(-4, 7, 3, 4, 7, 3, s(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB));
		b.set(0, 7, 3, Blocks.GILDED_BLACKSTONE);
		b.set(0, 8, 3, Blocks.WITHER_SKELETON_SKULL);
		for (int side = -1; side <= 1; side += 2) {
			b.fill(side * 4, 1, -1, side * 4, 5, 2, bricks);
			b.set(side * 4, 6, -1, lantern(true, false));
		}
		// Two shelves of heads.
		for (int x = -3; x <= 3; x++) {
			b.set(x, 1, 2, Blocks.POLISHED_BLACKSTONE);
			b.set(x, 2, 2, head(b.pick(x, 2, 2, 4, Blocks.SKELETON_SKULL, Blocks.ZOMBIE_HEAD, Blocks.CREEPER_HEAD, Blocks.PIGLIN_HEAD,
					Blocks.WITHER_SKELETON_SKULL, Blocks.PLAYER_HEAD), 8));
			b.set(x, 3, 2, Blocks.GILDED_BLACKSTONE);
			b.set(x, 4, 2, head(x == 0 && b.chance(0, 4, 2, 5, 0.35) ? Blocks.DRAGON_HEAD : b.pick(x, 4, 2, 6, Blocks.SKELETON_SKULL,
					Blocks.ZOMBIE_HEAD, Blocks.CREEPER_HEAD, Blocks.PIGLIN_HEAD, Blocks.WITHER_SKELETON_SKULL), 8));
		}
		b.chest(0, 1, -1, Direction.NORTH, "heads");
		for (int x : new int[] {-2, 2}) {
			b.set(x, 1, -2, lantern(true, false));
		}
	}

	/** A hermit's cell: a bed, a desk and a shelf of books nobody has read for a thousand years. */
	static void monkCell(DeepBuilder b) {
		DeepBuilder.Paint wall = b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS));
		b.pad(-5, -5, 5, 5, b.mix(s(Blocks.GRAVEL), s(Blocks.COBBLESTONE), s(Blocks.GRAVEL)), s(Blocks.COBBLESTONE), 9);
		b.shell(-3, 0, -3, 3, 5, 3, wall);
		b.fill(-2, 0, -2, 2, 0, 2, Blocks.SPRUCE_PLANKS);
		// The roof steps up to a ridge.
		for (int x = -4; x <= 4; x++) {
			int y = 6 + (4 - Math.abs(x)) / 2;
			b.fill(x, 6, -4, x, y - 1, 4, s(Blocks.STONE_BRICKS));
			b.fill(x, y, -4, x, y, 4, x == 0 ? s(Blocks.STONE_BRICKS) : stairs(Blocks.STONE_BRICK_STAIRS, x < 0 ? Direction.EAST : Direction.WEST, false));
		}
		b.fill(-2, 5, -2, 2, 5, 2, WATER);
		b.water(0, 1, -3, 0, 2, -3);
		b.set(-3, 2, 0, joined(Blocks.GLASS_PANE, false));
		b.set(3, 2, 0, joined(Blocks.GLASS_PANE, false));
		b.bed(-2, 1, 0, Direction.SOUTH, Blocks.BROWN_BED);
		b.fill(0, 1, 2, 2, 3, 2, Blocks.BOOKSHELF);
		b.set(2, 1, 0, facing(Blocks.LECTERN, Direction.WEST));
		b.barrel(2, 1, -2, Direction.UP, "library");
		b.set(-2, 1, -2, Blocks.CANDLE);
		b.set(0, 4, 0, lantern(false, true));
	}

	/** Mine rails running nowhere, and the ore spilled from the cart that ran off them. */
	static void lostCart(DeepBuilder b) {
		b.pad(-4, -8, 4, 8, b.mix(s(Blocks.GRAVEL), s(Blocks.GRAVEL), s(Blocks.COBBLED_DEEPSLATE)), s(Blocks.COBBLED_DEEPSLATE), 8);
		for (int z = -8; z <= 8; z++) {
			if (!b.crumbled(0, 1, z, 0.12)) {
				b.set(0, 1, z, Blocks.RAIL);
			}
		}
		for (int z : new int[] {-6, 0, 6}) {
			b.pillar(-2, z, 1, 3, s(Blocks.OAK_FENCE));
			b.pillar(2, z, 1, 3, s(Blocks.OAK_FENCE));
			b.fill(-2, 4, z, 2, 4, z, Blocks.OAK_PLANKS);
			b.set(0, 3, z, lantern(false, true));
		}
		// The spill.
		b.hoard(3, 1, 2, 2.4, s(Blocks.RAW_IRON_BLOCK), s(Blocks.RAW_GOLD_BLOCK), s(Blocks.COAL_BLOCK), s(Blocks.RAW_IRON_BLOCK),
				s(Blocks.DEEPSLATE_DIAMOND_ORE), s(Blocks.RAW_COPPER_BLOCK));
		b.set(-3, 1, 3, Blocks.RAW_GOLD_BLOCK);
		b.set(-3, 1, 4, Blocks.DEEPSLATE_DIAMOND_ORE);
		b.chest(-3, 1, -3, Direction.EAST, "mine");
		b.set(3, 1, -4, Blocks.TNT);
		b.set(3, 2, -4, Blocks.TNT);
	}

	/** A ship's anchor as tall as a house, driven into the sea floor, its chain rising into the dark. */
	static void anchorSite(DeepBuilder b) {
		BlockState iron = s(Blocks.IRON_BLOCK);
		b.padRound(0, 0, 6.5, b.mix(s(Blocks.GRAVEL), s(Blocks.SAND), s(Blocks.MUD)), s(Blocks.COBBLED_DEEPSLATE), 22);
		b.pillar(0, 0, -2, 10, iron);
		b.fill(-3, 9, 0, 3, 9, 0, iron);
		b.set(-3, 10, 0, Blocks.ANVIL);
		b.set(3, 10, 0, Blocks.ANVIL);
		// The ring, and the chain going up and up.
		b.fill(-1, 11, 0, 1, 11, 0, joined(Blocks.IRON_BARS, true));
		b.set(0, 12, 0, joined(Blocks.IRON_BARS, true));
		b.pillar(0, 0, 13, 22, axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
		// The flukes, half buried.
		for (int side = -1; side <= 1; side += 2) {
			b.line(0, 0, 0, side * 3, 1, 0, iron);
			b.line(side * 3, 1, 0, side * 4, 3, 0, iron);
			b.set(side * 4, 4, 0, facing(Blocks.ANVIL, Direction.NORTH));
		}
		// Wreckage round it.
		b.barrel(4, 1, 3, Direction.UP, "ship");
		b.barrel(5, 1, 3, Direction.NORTH, "ruin");
		b.barrel(4, 2, 3, Direction.UP, "ship");
		b.fill(-5, 1, 2, -3, 1, 2, slab(Blocks.DARK_OAK_SLAB, false));
		b.set(-4, 1, 4, Blocks.DARK_OAK_PLANKS);
		b.lampPost(-4, -4, 1, 2, s(Blocks.DARK_OAK_FENCE), lantern(false, false));
	}

	/** A diving bell that came down and never went back up. */
	static void divingBell(DeepBuilder b) {
		b.padRound(0, 0, 6.5, b.mix(s(Blocks.SAND), s(Blocks.GRAVEL)), s(Blocks.SANDSTONE), 14);
		b.sphere(0, 0, 0, 4.5, (x, y, z) -> y == 2 && (x == 0 || z == 0) ? s(Blocks.GLASS)
				: b.chance(x, y, z, 7, 0.3) ? s(Blocks.WEATHERED_COPPER) : s(Blocks.OXIDIZED_COPPER), true, 0);
		b.disc(0, 0, 0, 4.2, s(Blocks.OXIDIZED_CUT_COPPER));
		b.water(-1, 1, -4, 1, 2, -4);
		b.set(0, 3, 0, lantern(false, true));
		b.pillar(0, 0, 5, 13, axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
		b.chest(2, 1, 1, Direction.WEST, "ship");
		// The last diver.
		b.set(-2, 1, 1, Blocks.SKELETON_SKULL);
		b.set(-2, 1, 2, axis(Blocks.BONE_BLOCK, Direction.Axis.Z));
		b.set(1, 1, 2, Blocks.LANTERN);
		b.frame(0, 1, 3, Direction.NORTH, Items.COMPASS);
	}

	/** An altar of bone under a vault of giant ribs. */
	static void boneAltar(DeepBuilder b) {
		b.padRound(0, 0, 7.5, b.mix(s(Blocks.BONE_BLOCK), s(Blocks.CALCITE), s(Blocks.GRAVEL), s(Blocks.CALCITE)), s(Blocks.CALCITE), 10);
		for (int z : new int[] {-4, -1, 2, 5}) {
			for (int t = 0; t <= 16; t++) {
				double a = Math.PI * t / 16;
				int x = (int) Math.round(Math.cos(a) * 5.0);
				int y = 1 + (int) Math.round(Math.sin(a) * 6.5);
				b.set(x, y, z, axis(Blocks.BONE_BLOCK, Direction.Axis.Y));
			}
			b.set(0, 6, z, lantern(true, true));
		}
		// The spine along the top.
		b.fill(0, 8, -5, 0, 8, 6, axis(Blocks.BONE_BLOCK, Direction.Axis.Z));
		b.fill(-1, 1, -1, 1, 1, 1, Blocks.BONE_BLOCK);
		b.set(0, 2, 0, Blocks.WITHER_SKELETON_SKULL);
		b.set(-1, 2, -1, Blocks.CANDLE);
		b.set(1, 2, -1, Blocks.CANDLE);
		b.chest(0, 1, 2, Direction.NORTH, "tomb");
		for (int[] p : new int[][] {{-6, -4}, {6, -4}, {-6, 4}, {6, 4}}) {
			b.pike(p[0], p[1], 1, 2, Blocks.BONE_BLOCK);
		}
	}

	/** A black monolith, older than anything else down here, with netherite in the rock at its feet. */
	static void obsidianMonolith(DeepBuilder b) {
		b.padRound(0, 0, 7.5, b.mix(s(Blocks.BASALT), s(Blocks.BLACKSTONE), s(Blocks.SMOOTH_BASALT)), s(Blocks.BLACKSTONE), 20);
		b.fill(-2, 1, -2, 2, 1, 2, Blocks.POLISHED_BLACKSTONE);
		for (int y = 2; y <= 17; y++) {
			int half = y <= 13 ? 1 : 0;
			b.fill(-half, y, -half, half, y, half, (x, yy, z) -> b.chance(x, yy, z, 8, 0.25) ? s(Blocks.CRYING_OBSIDIAN) : s(Blocks.OBSIDIAN));
		}
		for (int y = 4; y <= 12; y += 4) {
			b.set(0, y, -1, Blocks.SHROOMLIGHT);
			b.set(0, y, 1, Blocks.SHROOMLIGHT);
		}
		b.set(0, 18, 0, Blocks.END_ROD);
		b.chest(0, 2, -1, Direction.NORTH, "ruin");
		// Ancient debris breaking the surface all round.
		for (int i = 0; i < 9; i++) {
			double a = b.layout.nextDouble() * Math.PI * 2;
			double r = 3.5 + b.layout.nextDouble() * 3.5;
			int x = (int) Math.round(Math.cos(a) * r);
			int z = (int) Math.round(Math.sin(a) * r);
			b.set(x, 0, z, Blocks.ANCIENT_DEBRIS);
			if (b.layout.nextInt(3) == 0) {
				b.set(x, 1, z, Blocks.ANCIENT_DEBRIS);
			}
		}
	}

	/** A geode the size of a room, split open, glittering with amethyst and diamonds. */
	static void crystalGeode(DeepBuilder b) {
		b.sphere(0, 0, 0, 6.4, s(Blocks.SMOOTH_BASALT), false, -7);
		b.sphere(0, 0, 0, 5.4, s(Blocks.CALCITE), false, -6);
		b.sphere(0, 0, 0, 4.4, (x, y, z) -> b.chance(x, y, z, 9, 0.12) ? s(Blocks.DEEPSLATE_DIAMOND_ORE) : s(Blocks.AMETHYST_BLOCK), false, -5);
		b.sphere(0, 0, 0, 3.4, WATER, false, -4);
		// The crack across the top.
		for (int x = -7; x <= 7; x++) {
			for (int z = -2; z <= 1; z++) {
				int zz = z + (int) Math.round(Math.sin(x * 0.6) * 1.5);
				for (int y = 1; y <= 7; y++) {
					if (y >= 3 - Math.abs(z) / 2 || Math.abs(x) <= 3) {
						b.set(x, y, zz, WATER);
					}
				}
			}
		}
		// Crystals on the floor inside, on the lowest water of each column.
		for (int[] c : new int[][] {{-2, -2}, {2, -1}, {-1, 2}, {1, 1}, {-2, 1}, {2, 2}, {0, -2}}) {
			int y = -(int) Math.floor(Math.sqrt(3.4 * 3.4 - c[0] * c[0] - c[1] * c[1]));
			b.set(c[0], y, c[1], c[0] == 0 ? pickles(4) : facing(Blocks.AMETHYST_CLUSTER, Direction.UP));
		}
		b.chest(0, -3, 0, Direction.NORTH, "ruin");
	}

	/** A sarcophagus on its platform, lit by four lamps that have burned for centuries. */
	static void tomb(DeepBuilder b) {
		b.pad(-5, -6, 5, 6, b.mix(s(Blocks.GRAVEL), s(Blocks.SAND)), s(Blocks.COBBLESTONE), 9);
		b.fill(-3, 1, -4, 3, 1, 4, b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS)));
		b.fill(-1, 2, -2, 1, 3, 2, Blocks.POLISHED_ANDESITE);
		b.fill(-1, 4, -2, 1, 4, 2, slab(Blocks.ANDESITE_SLAB, false));
		b.set(0, 2, -3, Blocks.CHISELED_STONE_BRICKS);
		b.set(0, 4, 0, Blocks.SKELETON_SKULL);
		b.chest(0, 2, 3, Direction.NORTH, "tomb");
		for (int[] c : new int[][] {{-3, -4}, {3, -4}, {-3, 4}, {3, 4}}) {
			b.lampPost(c[0], c[1], 2, 2, s(Blocks.STONE_BRICK_WALL), lantern(false, false));
		}
		b.set(-2, 2, -4, b.head(-2, 2, -4));
		b.set(2, 2, 3, b.head(2, 2, 3));
	}

	/** A great stone arch, the gate of a city that is no longer there. */
	static void ruinedArch(DeepBuilder b) {
		DeepBuilder.Paint stone = b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS));
		b.pad(-8, -4, 8, 4, b.mix(s(Blocks.GRAVEL), s(Blocks.STONE_BRICKS), s(Blocks.GRAVEL)), s(Blocks.COBBLESTONE), 15);
		for (int side = -1; side <= 1; side += 2) {
			b.fill(side * 6, 1, -1, side * 5, 8, 1, stone);
			b.set(side * 5, 1, -2, Blocks.CHISELED_STONE_BRICKS);
		}
		for (int t = 0; t <= 20; t++) {
			double a = Math.PI * t / 20;
			int x = (int) Math.round(Math.cos(a) * 5.5);
			int y = 8 + (int) Math.round(Math.sin(a) * 4.0);
			b.fill(x, y, -1, x, y + 1, 1, b.ruin(stone, 0.06));
		}
		b.fill(0, 12, -1, 0, 13, 1, Blocks.GOLD_BLOCK);
		b.set(0, 11, 0, axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
		b.set(0, 10, 0, lantern(false, true));
		b.chest(0, 1, 0, Direction.NORTH, "ruin");
		// Fallen blocks.
		for (int i = 0; i < 10; i++) {
			int x = b.layout.nextInt(17) - 8;
			int z = b.layout.nextInt(9) - 4;
			if (Math.abs(x) <= 6 && Math.abs(z) <= 1) {
				continue;
			}
			b.set(x, 1, z, stone);
		}
	}

	/** A bed of giant clams around one the size of a boat, with a pearl as big as a man inside. */
	static void pearlBed(DeepBuilder b) {
		b.padRound(0, 0, 8.5, b.mix(s(Blocks.SAND), s(Blocks.SAND), s(Blocks.GRAVEL)), s(Blocks.SANDSTONE), 10);
		// The great clam: a bowl below and its lid thrown open behind.
		b.ellipsoid(0, 1, 0, 4.5, 2.0, 3.5, b.mix(s(Blocks.CALCITE), s(Blocks.DIORITE), s(Blocks.CALCITE)), false, -2);
		b.ellipsoid(0, 2, 0, 3.6, 2.0, 2.6, (x, y, z) -> WATER, false, -1);
		for (int x = -4; x <= 4; x++) {
			for (int y = 2; y <= 7; y++) {
				double f = (x * x) / 20.25 + ((y - 2) * (y - 2)) / 30.0;
				if (f <= 1.0 && f >= 0.55) {
					b.set(x, y, 3, Blocks.CALCITE);
				}
			}
		}
		b.fill(-1, 2, -1, 0, 3, 0, ModBlocks.PEARL_BLOCK);
		b.chest(2, 2, -1, Direction.WEST, "ruin");
		b.set(-2, 2, 1, pickles(4));
		for (int i = 0; i < 16; i++) {
			double a = b.layout.nextDouble() * Math.PI * 2;
			double r = 5.5 + b.layout.nextDouble() * 2.5;
			int x = (int) Math.round(Math.cos(a) * r);
			int z = (int) Math.round(Math.sin(a) * r);
			b.set(x, 1, z, b.pick(x, 1, z, 10, s(ModBlocks.GIANT_CLAM), s(ModBlocks.GIANT_CLAM), s(ModBlocks.SEASHELLS), s(ModBlocks.BLUE_STARFISH),
					pickles(2), s(ModBlocks.PEARL_BLOCK)));
		}
	}

	/** A lighthouse that guided ships to a coast that sank: the tower stands, its lamp lies in the sand. */
	static void lighthouseRuin(DeepBuilder b) {
		b.padRound(0, 0, 9.0, b.mix(s(Blocks.SAND), s(Blocks.GRAVEL)), s(Blocks.SANDSTONE), 10);
		DeepBuilder.Paint stripes = (x, y, z) -> Math.floorMod(y + z, 6) < 3 ? s(Blocks.WHITE_TERRACOTTA) : s(Blocks.RED_TERRACOTTA);
		b.cylinder(0, -5, 3.5, 0, 7, b.ruin(stripes, 0.04), true);
		b.disc(0, 0, -5, 2.6, s(Blocks.STONE_BRICKS));
		b.water(0, 1, -8, 0, 2, -8);
		b.chest(0, 1, -5, Direction.NORTH, "ship");
		b.set(1, 1, -4, lantern(false, false));
		// The fallen top, lying along the sand.
		for (int z = -1; z <= 5; z++) {
			for (int x = -3; x <= 3; x++) {
				for (int y = 0; y <= 5; y++) {
					double d = Math.sqrt(x * x + (y - 2.5) * (y - 2.5));
					if (d <= 3.0 && d > 2.0) {
						b.set(x, y + 1, z, stripes.at(x, z, y));
					}
				}
			}
		}
		b.sphere(0, 3, 7, 2.6, s(Blocks.GLASS), true, -2);
		b.set(0, 3, 7, Blocks.GLOWSTONE);
		b.set(0, 4, 7, Blocks.SEA_LANTERN);
		b.set(0, 2, 7, Blocks.SEA_LANTERN);
	}

	/** A giant stone face looking out of the sea floor, eyes alight, a treasure in its mouth. */
	static void stoneHead(DeepBuilder b) {
		DeepBuilder.Paint stone = b.worn(s(Blocks.ANDESITE), s(Blocks.POLISHED_ANDESITE), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.TUFF));
		b.padRound(0, 0, 7.5, b.mix(s(Blocks.GRAVEL), s(Blocks.MOSS_BLOCK), s(Blocks.GRAVEL)), s(Blocks.COBBLESTONE), 16);
		b.fill(-4, 1, -2, 4, 10, 3, stone);
		b.fill(-5, 7, -3, 5, 8, -3, stone);
		b.fill(-4, 11, -2, 4, 12, 2, stone);
		b.fill(-1, 3, -4, 1, 7, -3, stone);
		b.set(0, 3, -5, s(Blocks.ANDESITE));
		// Eyes.
		for (int side = -1; side <= 1; side += 2) {
			b.fill(side * 2, 6, -3, side * 3, 6, -3, Blocks.SHROOMLIGHT);
			b.set(side * 2, 6, -2, Blocks.SHROOMLIGHT);
		}
		// The open mouth with the treasure inside.
		b.water(-2, 1, -3, 2, 2, 0);
		b.chest(0, 1, 0, Direction.NORTH, "ruin");
		b.set(-2, 1, 0, Blocks.GOLD_BLOCK);
		b.set(2, 1, 0, Blocks.GOLD_BLOCK);
		b.set(0, 13, 0, Blocks.MOSS_BLOCK);
		b.set(-3, 13, 0, Blocks.MOSS_CARPET);
	}

	/** The camp of an explorer who never came back: a tent, a cold fire pit, a gramophone. */
	static void lostCamp(DeepBuilder b) {
		b.padRound(0, 0, 7.5, b.mix(s(Blocks.SAND), s(Blocks.GRAVEL), s(Blocks.SAND)), s(Blocks.SANDSTONE), 8);
		// The tent.
		for (int z = -3; z <= 2; z++) {
			b.set(-2, 1, z, Blocks.BROWN_WOOL);
			b.set(2, 1, z, Blocks.BROWN_WOOL);
			b.set(-1, 2, z, Blocks.BROWN_WOOL);
			b.set(1, 2, z, Blocks.BROWN_WOOL);
			b.set(0, 3, z, Blocks.BROWN_WOOL);
		}
		b.bed(0, 1, -2, Direction.SOUTH, Blocks.RED_BED);
		b.set(0, 2, -1, lantern(false, true));
		// The fire pit.
		for (int[] c : new int[][] {{3, -5}, {5, -5}, {4, -6}, {4, -4}}) {
			b.set(c[0], 1, c[1], Blocks.COBBLESTONE);
		}
		b.set(4, 0, -5, Blocks.MAGMA_BLOCK);
		// Supplies, and the jukebox with a record still beside it.
		b.barrel(-4, 1, 2, Direction.UP, "ruin");
		b.barrel(-4, 1, 3, Direction.NORTH, "ruin");
		b.chest(-5, 1, 2, Direction.EAST, "discs");
		b.set(4, 1, 1, Blocks.JUKEBOX);
		b.frame(4, 1, 0, Direction.NORTH, DISCS[b.layout.nextInt(DISCS.length)]);
		b.lampPost(3, 3, 1, 2, s(Blocks.SPRUCE_FENCE), lantern(false, false));
		b.set(-3, 1, -4, Blocks.CRAFTING_TABLE);
	}

	/** A crack in the sea floor glittering with ancient debris and diamond, where a miner died rich. */
	static void debrisVein(DeepBuilder b) {
		b.pad(-6, -9, 6, 9, b.mix(s(Blocks.BASALT), s(Blocks.SMOOTH_BASALT), s(Blocks.GRAVEL)), s(Blocks.BASALT), 8);
		for (int z = -8; z <= 8; z++) {
			int center = (int) Math.round(Math.sin(z * 0.45) * 1.5);
			int width = 1 + (int) Math.round(1.2 + Math.sin(z * 0.9) * 0.8);
			int depth = 3 + (int) Math.round(2.0 * Math.cos(z * 0.3));
			for (int x = center - width - 1; x <= center + width + 1; x++) {
				for (int y = -depth - 1; y <= 0; y++) {
					boolean inside = Math.abs(x - center) <= width && y >= -depth;
					if (inside) {
						b.set(x, y, z, WATER);
					} else {
						double r = b.rand(x, y, z, 11);
						b.set(x, y, z, r < 0.16 ? s(Blocks.ANCIENT_DEBRIS) : r < 0.28 ? s(Blocks.DEEPSLATE_DIAMOND_ORE)
								: r < 0.38 ? s(Blocks.DEEPSLATE_GOLD_ORE) : r < 0.46 ? s(Blocks.MAGMA_BLOCK) : s(Blocks.BLACKSTONE));
					}
				}
			}
		}
		b.chest(4, 1, 6, Direction.WEST, "mine");
		b.set(4, 1, 5, Blocks.SKELETON_SKULL);
		b.set(5, 1, 5, axis(Blocks.BONE_BLOCK, Direction.Axis.X));
		for (int[] c : new int[][] {{-4, -6}, {4, -2}, {-4, 3}}) {
			b.lampPost(c[0], c[1], 1, 1, s(Blocks.POLISHED_BLACKSTONE_WALL), lantern(true, false));
		}
	}

}
