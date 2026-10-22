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
import static com.naturesmod.worldgen.deep.LandParts.wool;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The second wave of great dungeons: a dragon's bones, a castle in the sky, a volcano, a coven, a
 * barbarian hall, a sun temple, a jungle palace, a glacier vault, a wizard's tower, a bunker, a
 * cathedral and a city of ruins. Keepers last, as always.
 */
final class LandHard2 {
	private LandHard2() {
	}

	private static BlockState leaves(Block leaves) {
		return s(leaves).setValue(BlockStateProperties.PERSISTENT, true);
	}

	private static void ladder(DeepBuilder b, int x, int z, int y0, int y1, Direction facing) {
		for (int y = y0; y <= y1; y++) {
			b.set(x, y, z, facing(Blocks.LADDER, facing));
		}
	}

	/** The bones of a dragon the size of a hill, its hoard still under its ribs, and the dead who came for it. */
	static void dragonBones(DeepBuilder b) {
		site(b, 26.5, 30);
		// The spine, curving along the ground, and ribs arching over the hoard.
		for (int k = -20; k <= 22; k++) {
			int z = (int) Math.round(Math.sin(k * 0.12) * 4);
			int y = 6 - Math.abs(k) / 5;
			b.set(k, y, z, axis(Blocks.BONE_BLOCK, Direction.Axis.X));
			b.set(k, y - 1, z, axis(Blocks.BONE_BLOCK, Direction.Axis.X));
			if (k % 3 == 0 && Math.abs(k) < 14) {
				for (int side = -1; side <= 1; side += 2) {
					b.line(k, y, z, k, y + 2, z + side * 6, s(Blocks.BONE_BLOCK));
					b.line(k, y + 2, z + side * 6, k, 0, z + side * 9, s(Blocks.BONE_BLOCK));
				}
			}
		}
		// The skull at the west end, big enough to walk into.
		b.ellipsoid(-23, 4, 0, 3.0, 4.0, 4.0, s(Blocks.BONE_BLOCK), true, -3);
		b.clear(-23, 1, -1, -23, 3, 1);
		b.line(-23, 7, -2, -23, 11, -5, s(Blocks.CALCITE));
		b.line(-23, 7, 2, -23, 11, 5, s(Blocks.CALCITE));
		// The hoard.
		b.hoard(0, 1, 0, 5.0, s(Blocks.GOLD_BLOCK), s(Blocks.RAW_GOLD_BLOCK), s(Blocks.GILDED_BLACKSTONE), s(Blocks.GILDED_BLACKSTONE),
				s(Blocks.GILDED_BLACKSTONE), s(Blocks.GILDED_BLACKSTONE));
		b.chest(-6, 1, 0, Direction.EAST, "dragon");
		b.chest(6, 1, 0, Direction.WEST, "treasure");
		b.chest(0, 1, 7, Direction.NORTH, "dragon");
		b.spawner(-10, 0, 5, EntityTypes.SKELETON);
		b.spawner(10, 0, -5, EntityTypes.SKELETON);
		for (int[] p : new int[][] {{-14, -10}, {14, 10}, {-14, 10}, {14, -10}}) {
			b.pike(p[0], p[1], 1, 2, Blocks.OAK_FENCE);
		}
		b.keeper(-8, 1, -8, EntityTypes.WITHER_SKELETON);
		b.keeper(8, 1, 8, EntityTypes.WITHER_SKELETON);
		b.keeper(0, 1, -10, EntityTypes.SKELETON);
	}

	/** A castle on a floating island high above the land, reached by a stair that winds up a stone pillar. */
	static void skyCastle(DeepBuilder b) {
		site(b, 12.5, 12);
		int base = 40;
		// The island, its rock hanging below it.
		b.ellipsoid(0, base, 0, 16.0, 9.0, 16.0, b.mix(s(Blocks.STONE), s(Blocks.ANDESITE), s(Blocks.DIRT)), false, -9);
		b.disc(0, base, 0, 15.5, s(Blocks.GRASS_BLOCK));
		b.ellipsoid(0, base + 1, 0, 16.0, 9.0, 16.0, (x, y, z) -> b.open(), false, 0);
		// The pillar up from the ground, with a spiral of stairs round it.
		b.cylinder(0, 0, 3.0, 1, base - 9, s(Blocks.STONE_BRICKS), false);
		for (int y = 1; y < base; y++) {
			double angle = y * 0.45;
			int x = (int) Math.round(Math.cos(angle) * 4);
			int z = (int) Math.round(Math.sin(angle) * 4);
			b.set(x, y, z, s(Blocks.STONE_BRICKS));
			b.clear(x, y + 1, z, x, y + 3, z);
		}
		b.cylinder(0, 0, 4.0, base - 9, base, b.open(), false);
		b.cylinder(0, 0, 2.0, base - 9, base - 1, s(Blocks.STONE_BRICKS), false);
		// The castle on top.
		DeepBuilder.Paint stone = b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS));
		b.walls(-9, base + 1, -9, 9, base + 7, 9, stone);
		b.fill(-8, base + 1, -8, 8, base + 7, 8, b.open());
		b.fill(-9, base + 8, -9, 9, base + 8, 9, s(Blocks.STONE_BRICKS));
		b.fill(-8, base + 8, -8, 8, base + 8, 8, slab(Blocks.STONE_BRICK_SLAB, false));
		for (int[] c : new int[][] {{-9, -9}, {9, -9}, {-9, 9}, {9, 9}}) {
			b.cylinder(c[0], c[1], 2.5, base + 1, base + 13, s(Blocks.STONE_BRICKS), false);
			b.disc(c[0], base + 14, c[1], 3.0, s(vanilla("blue_terracotta")));
		}
		b.clear(-1, base + 1, -9, 1, base + 3, -9);
		b.fill(-6, base + 1, 6, 6, base + 1, 6, carpet("blue"));
		b.set(0, base + 1, 7, stairs(Blocks.QUARTZ_STAIRS, Direction.SOUTH, false));
		b.chest(-2, base + 1, 7, Direction.NORTH, "treasure");
		b.chest(2, base + 1, 7, Direction.NORTH, "tower");
		b.chest(-7, base + 1, -7, Direction.EAST, "fortress");
		for (int x = -6; x <= 6; x += 6) {
			b.set(x, base + 7, 0, lantern(false, true));
		}
		b.keeper(0, base + 1, 4, EntityTypes.EVOKER);
		b.keeper(-4, base + 1, 0, EntityTypes.VINDICATOR);
		b.keeper(4, base + 1, 0, EntityTypes.VINDICATOR);
	}

	/** A volcano: a cone of black rock with a lava lake in its crater, blazes on the wind and riches in the vent. */
	static void volcano(DeepBuilder b) {
		site(b, 26.5, 40);
		for (int y = 1; y <= 26; y++) {
			double r = 24.0 - y * 0.75;
			b.disc(0, y, 0, r, b.mix(s(Blocks.BASALT), s(Blocks.BLACKSTONE), s(Blocks.TUFF), s(Blocks.BLACKSTONE), s(Blocks.MAGMA_BLOCK)));
		}
		b.cylinder(0, 0, 6.0, 18, 27, b.open(), false);
		b.disc(0, 17, 0, 6.0, s(Blocks.LAVA));
		b.disc(0, 16, 0, 6.5, s(Blocks.BASALT));
		// A tunnel into the heart, and the vault beside the vent.
		b.clear(-1, 1, -24, 1, 3, -8);
		b.fill(-6, 0, -7, 6, 6, 3, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
		b.fill(-5, 1, -6, 5, 5, 2, b.open());
		b.clear(-1, 1, -8, 1, 3, -7);
		b.chest(-4, 1, 1, Direction.SOUTH, "blaze");
		b.chest(4, 1, 1, Direction.SOUTH, "treasure");
		b.set(0, 1, 2, Blocks.LAVA_CAULDRON);
		b.spawner(-4, 1, -4, EntityTypes.MAGMA_CUBE);
		b.spawner(0, 27, 8, EntityTypes.BLAZE);
		b.keeper(2, 1, -3, EntityTypes.BLAZE);
		b.keeper(-2, 1, -2, EntityTypes.MAGMA_CUBE);
	}

	/** A witches' coven: crooked huts round a great cauldron, and the witches at their brewing. */
	static void witchCoven(DeepBuilder b) {
		site(b, 24.5, 18);
		b.disc(0, 0, 0, 22.0, b.mix(s(Blocks.MUD), s(Blocks.PACKED_MUD), s(Blocks.PODZOL)));
		b.cylinder(0, 0, 3.5, 1, 2, s(Blocks.COBBLED_DEEPSLATE), false);
		b.disc(0, 2, 0, 2.5, s(Blocks.WATER));
		b.disc(0, 1, 0, 2.5, s(Blocks.MAGMA_BLOCK));
		for (int i = 0; i < 6; i++) {
			double angle = i * Math.PI / 3 + 0.3;
			int x = (int) Math.round(Math.cos(angle) * 14);
			int z = (int) Math.round(Math.sin(angle) * 14);
			LandParts.house(b, x - 3, z - 3, x + 3, z + 3, 4, DeepBuilder.p(s(Blocks.DARK_OAK_PLANKS)), log(Blocks.DARK_OAK_LOG),
					s(Blocks.SPRUCE_PLANKS));
			for (int k = 0; k <= 3; k++) {
				b.fill(x - 3 + k, 5 + k, z - 3 + k, x + 3 - k, 5 + k, z + 3 - k, s(vanilla("purple_terracotta")));
			}
			int dx = (int) Math.signum(-x);
			int dz = (int) Math.signum(-z);
			b.clear(x + dx * 3, 1, z + dz * 3, x + dx * 3, 2, z + dz * 3);
			b.set(x, 1, z, Blocks.BREWING_STAND);
			b.chest(x + 2, 1, z + 2, Direction.WEST, i % 2 == 0 ? "witch" : "swamp");
			b.set(x, 4, z, lantern(true, true));
		}
		for (int i = 0; i < 6; i++) {
			double angle = i * Math.PI / 3 + 0.3;
			b.keeper((int) Math.round(Math.cos(angle) * 14) - 1, 1, (int) Math.round(Math.sin(angle) * 14) - 1, EntityTypes.WITCH);
		}
		b.keeper(0, 3, 5, EntityTypes.CAT);
	}

	/** A barbarian stronghold: a palisade round a great log hall, its warband feasting inside. */
	static void barbarianHall(DeepBuilder b) {
		site(b, -26, -26, 26, 26, 0, 22);
		LandParts.palisade(b, -22, -22, 22, 22, 6, Blocks.SPRUCE_LOG);
		LandParts.cottage(b, -12, -6, 12, 14, 8, DeepBuilder.p(log(Blocks.SPRUCE_LOG)), log(Blocks.DARK_OAK_LOG), s(Blocks.SPRUCE_PLANKS),
				Blocks.DARK_OAK_STAIRS, s(Blocks.DARK_OAK_PLANKS));
		b.fill(-1, 1, -2, 1, 1, 10, slab(Blocks.SPRUCE_SLAB, true));
		b.fill(-1, 1, 11, 1, 1, 12, s(Blocks.CAMPFIRE));
		for (int z = -1; z <= 9; z += 2) {
			LandParts.seat(b, -2, z, Blocks.SPRUCE_STAIRS, Direction.WEST);
			LandParts.seat(b, 2, z, Blocks.SPRUCE_STAIRS, Direction.EAST);
		}
		b.chest(-11, 1, 13, Direction.EAST, "fortress");
		b.chest(11, 1, 13, Direction.WEST, "treasure");
		b.chest(-11, 1, -5, Direction.EAST, "bandit");
		b.stand(6, 1, 12, Direction.WEST, new ItemStack(Items.IRON_HELMET), new ItemStack(Items.CHAINMAIL_CHESTPLATE),
				new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.IRON_AXE));
		for (int[] t : new int[][] {{-18, -18}, {18, -18}, {-18, 18}, {18, 18}}) {
			b.set(t[0], 1, t[1], s(Blocks.CAMPFIRE));
		}
		b.keeper(-4, 1, 4, EntityTypes.VINDICATOR);
		b.keeper(4, 1, 6, EntityTypes.VINDICATOR);
		b.keeper(0, 1, -10, EntityTypes.PILLAGER);
		b.keeper(-15, 1, 0, EntityTypes.WOLF);
		b.keeper(15, 1, 0, EntityTypes.WOLF);
	}

	/** A temple of the sun: a stepped pyramid of gold-trimmed sandstone, the sun's altar at its peak. */
	static void sunTemple(DeepBuilder b) {
		site(b, -26, -26, 26, 26, 0, 30);
		DeepBuilder.Paint stone = b.worn(s(Blocks.CUT_SANDSTONE), s(Blocks.SANDSTONE), s(Blocks.SMOOTH_SANDSTONE));
		for (int t = 0; t < 6; t++) {
			int w = 21 - t * 4;
			b.fill(-w, 1 + t * 3, -w, w, 3 + t * 3, w, stone);
			b.walls(-w, 3 + t * 3, -w, w, 3 + t * 3, w, s(vanilla("orange_terracotta")));
		}
		for (int k = 0; k < 18; k++) {
			b.fill(-2, k + 1, -22 + k, 2, k + 1, -22 + k, stairs(Blocks.SANDSTONE_STAIRS, Direction.SOUTH, false));
			b.clear(-2, k + 2, -22 + k, 2, k + 4, -22 + k);
		}
		b.fill(-1, 19, -1, 1, 19, 1, s(Blocks.GOLD_BLOCK));
		b.set(0, 20, 0, Blocks.BEACON);
		// The inner sanctum, inside the lowest step.
		b.fill(-15, 1, -15, 15, 2, 15, b.open());
		b.clear(19, 1, -1, 21, 2, 1);
		for (int x = -12; x <= 12; x += 6) {
			for (int z = -12; z <= 12; z += 6) {
				b.pillar(x, z, 1, 2, s(Blocks.CHISELED_SANDSTONE));
			}
		}
		b.chest(-14, 1, 14, Direction.EAST, "pyramid");
		b.chest(14, 1, 14, Direction.WEST, "temple");
		b.chest(0, 1, 14, Direction.NORTH, "treasure");
		b.spawner(-8, 1, 8, EntityTypes.HUSK);
		b.spawner(8, 1, -8, EntityTypes.HUSK);
		b.keeper(0, 20, 2, EntityTypes.EVOKER);
		b.keeper(-3, 1, 0, EntityTypes.HUSK);
	}

	/** A palace in the jungle: terraces, a throne room, gardens gone wild, and the guardians of its gold. */
	static void junglePalace(DeepBuilder b) {
		site(b, -26, -26, 26, 26, 0, 30);
		DeepBuilder.Paint stone = b.worn(s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS));
		b.fill(-20, 1, -20, 20, 2, 20, stone);
		b.fill(-14, 3, -14, 14, 4, 14, stone);
		b.walls(-10, 5, -10, 10, 12, 10, stone);
		b.fill(-9, 5, -9, 9, 12, 9, b.open());
		b.fill(-10, 13, -10, 10, 13, 10, s(Blocks.MOSSY_STONE_BRICKS));
		for (int k = 0; k < 4; k++) {
			b.fill(-2, k + 1, -21 + k, 2, k + 1, -21 + k, stairs(Blocks.MOSSY_STONE_BRICK_STAIRS, Direction.SOUTH, false));
		}
		b.clear(-1, 5, -10, 1, 8, -10);
		b.fill(-1, 5, -9, 1, 5, 7, carpet("red"));
		b.fill(-1, 5, 8, 1, 6, 8, s(Blocks.GOLD_BLOCK));
		b.set(0, 7, 8, s(Blocks.EMERALD_BLOCK));
		b.chest(-8, 5, 8, Direction.EAST, "ziggurat");
		b.chest(8, 5, 8, Direction.WEST, "treasure");
		b.chest(-8, 5, -8, Direction.EAST, "jungle");
		for (int x = -6; x <= 6; x += 6) {
			b.set(x, 12, 0, lantern(false, true));
		}
		for (int i = 0; i < 60; i++) {
			int x = b.layout.nextInt(41) - 20;
			int z = b.layout.nextInt(41) - 20;
			if (Math.abs(x) <= 14 && Math.abs(z) <= 14 || Math.abs(x) <= 2) {
				continue;
			}
			b.set(x, 3, z, leaves(Blocks.JUNGLE_LEAVES));
		}
		b.spawner(-8, 5, 0, EntityTypes.CAVE_SPIDER);
		b.keeper(0, 5, 4, "komodo_dragon");
		b.keeper(-12, 5, 12, "orangutan");
		b.keeper(12, 5, -12, "cobra");
		b.keeper(4, 5, -4, EntityTypes.VINDICATOR);
	}

	/** A vault cut into a glacier: halls of blue ice, frozen guards, and the treasure locked in the cold. */
	static void glacierVault(DeepBuilder b) {
		site(b, 25.5, 30);
		b.ellipsoid(0, 0, 0, 24.0, 18.0, 22.0, b.mix(s(Blocks.PACKED_ICE), s(Blocks.PACKED_ICE), s(Blocks.BLUE_ICE), s(Blocks.SNOW_BLOCK)), false, 0);
		b.clear(-2, 1, -23, 2, 5, -8);
		b.fill(-12, 1, -8, 12, 9, 12, b.open());
		b.fill(-12, 0, -8, 12, 0, 12, s(Blocks.POLISHED_DIORITE));
		for (int x = -9; x <= 9; x += 6) {
			for (int z = -4; z <= 10; z += 7) {
				b.pillar(x, z, 1, 9, s(Blocks.BLUE_ICE));
			}
		}
		b.spawner(-6, 1, 6, EntityTypes.STRAY);
		b.spawner(6, 1, 6, EntityTypes.STRAY);
		b.chest(-11, 1, 11, Direction.EAST, "frost");
		b.chest(11, 1, 11, Direction.WEST, "frost");
		b.chest(0, 1, 11, Direction.NORTH, "treasure");
		for (int x = -8; x <= 8; x += 8) {
			b.stand(x, 1, 2, Direction.NORTH, new ItemStack(Items.CHAINMAIL_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
					new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.BOW));
		}
		b.keeper(0, 1, 6, EntityTypes.EVOKER);
		b.keeper(-3, 1, 0, EntityTypes.STRAY);
		b.keeper(3, 1, 0, EntityTypes.STRAY);
		b.keeper(0, 1, -20, "walrus");
	}

	/** A wizard's tower: a spire of purple stone, a library, a lab, an observatory, and the wizard at the top. */
	static void wizardTower(DeepBuilder b) {
		site(b, 14.5, 60);
		DeepBuilder.Paint stone = b.worn(s(Blocks.STONE_BRICKS), s(Blocks.POLISHED_ANDESITE));
		b.cylinder(0, 0, 6.5, 1, 48, stone, true);
		for (int y = 12; y <= 36; y += 12) {
			b.disc(0, y, 0, 5.5, s(Blocks.DARK_OAK_PLANKS));
		}
		b.disc(0, 48, 0, 6.5, s(Blocks.DARK_OAK_PLANKS));
		for (int k = 0; k <= 10; k++) {
			b.disc(0, 49 + k, 0, 7.5 - k * 0.7, s(vanilla("purple_terracotta")));
		}
		b.set(0, 60, 0, s(Blocks.GOLD_BLOCK));
		b.clear(0, 1, -6, 0, 3, -6);
		for (int floor = 0; floor <= 36; floor += 12) {
			int side = floor % 24 == 0 ? 1 : -1;
			ladder(b, side * 5, 0, floor + 1, floor + 12, side > 0 ? Direction.WEST : Direction.EAST);
			b.set(0, floor + 11, 0, lantern(true, true));
		}
		for (int z = -3; z <= 3; z++) {
			b.fill(-5, 13, z, -5, 15, z, Blocks.BOOKSHELF);
		}
		b.set(0, 13, 0, Blocks.ENCHANTING_TABLE);
		b.chest(3, 13, 3, Direction.WEST, "library");
		b.set(-2, 25, -2, Blocks.BREWING_STAND);
		b.set(2, 25, -2, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		b.chest(-3, 25, 3, Direction.EAST, "witch");
		b.chest(3, 37, 3, Direction.WEST, "observatory");
		b.chest(0, 49, 3, Direction.NORTH, "treasure");
		b.spawner(0, 1, 3, EntityTypes.SKELETON);
		b.keeper(0, 49, -2, EntityTypes.EVOKER);
		b.keeper(2, 37, 0, EntityTypes.WITCH);
	}

	/** A buried bunker: a hatch in the grass, corridors of concrete, stores, a control room and what still guards it. */
	static void bunker(DeepBuilder b) {
		site(b, 12.5, 6);
		// The main hall and its rooms.
		b.fill(-20, -22, -4, 20, -16, 4, s(Blocks.GRAY_CONCRETE));
		b.fill(-19, -21, -3, 19, -17, 3, b.open());
		// The shaft down from the hatch, its ladder on the south wall.
		b.fill(-1, -16, -1, 1, 1, 1, s(Blocks.GRAY_CONCRETE));
		b.clear(0, -16, 0, 0, 1, 0);
		ladder(b, 0, 0, -21, 1, Direction.NORTH);
		b.set(0, -21, 1, s(Blocks.GRAY_CONCRETE));
		b.set(0, -20, 1, s(Blocks.GRAY_CONCRETE));
		b.set(0, -19, 1, s(Blocks.GRAY_CONCRETE));
		b.set(0, -18, 1, s(Blocks.GRAY_CONCRETE));
		b.set(0, -17, 1, s(Blocks.GRAY_CONCRETE));
		for (int x = -16; x <= 16; x += 8) {
			b.set(x, -17, 0, lantern(false, true));
			b.fill(x - 3, -22, 5, x + 3, -16, 12, s(Blocks.LIGHT_GRAY_CONCRETE));
			b.fill(x - 2, -21, 5, x + 2, -17, 11, b.open());
			b.clear(x, -21, 4, x, -20, 4);
		}
		b.chest(-16, -21, 10, Direction.SOUTH, "deep_mine");
		b.chest(-8, -21, 10, Direction.SOUTH, "fortress");
		b.chest(0, -21, 10, Direction.SOUTH, "treasure");
		b.fill(6, -21, 10, 10, -20, 10, Blocks.BARREL);
		b.barrel(8, -19, 10, Direction.UP, "mine");
		b.fill(14, -21, 11, 18, -21, 11, s(Blocks.TNT));
		b.spawner(16, -21, 8, EntityTypes.ZOMBIE);
		b.keeper(-10, -21, 0, EntityTypes.ZOMBIE);
		b.keeper(10, -21, 0, EntityTypes.SKELETON);
	}

	/** A cathedral in ruins: a nave of pillars and coloured glass, a crypt below, the bells fallen silent. */
	static void cathedral(DeepBuilder b) {
		site(b, -26, -26, 26, 26, 0, 40);
		DeepBuilder.Paint stone = b.ruin(b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS)), 0.04);
		b.walls(-10, 1, -22, 10, 18, 22, stone);
		b.fill(-9, 1, -21, 9, 18, 21, b.open());
		b.fill(-10, 0, -22, 10, 0, 22, s(Blocks.POLISHED_ANDESITE));
		for (int z = -18; z <= 18; z += 6) {
			b.pillar(-6, z, 1, 18, s(Blocks.STONE_BRICKS));
			b.pillar(6, z, 1, 18, s(Blocks.STONE_BRICKS));
			b.fill(-10, 6, z, -10, 14, z, s(vanilla("purple_stained_glass")));
			b.fill(10, 6, z, 10, 14, z, s(vanilla("blue_stained_glass")));
		}
		for (int k = 0; k <= 10; k++) {
			b.fill(-10 + k, 19 + k, -22, 10 - k, 19 + k, 22, s(Blocks.DEEPSLATE_TILES));
			b.fill(-9 + k, 19 + k, -21, 9 - k, 19 + k, 21, b.open());
		}
		b.clear(-2, 1, -22, 2, 6, -22);
		b.fill(-3, 1, 17, 3, 1, 20, s(Blocks.CHISELED_STONE_BRICKS));
		b.set(0, 2, 19, Blocks.LECTERN);
		for (int z = -15; z <= 12; z += 3) {
			b.fill(-4, 1, z, -2, 1, z, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
			b.fill(2, 1, z, 4, 1, z, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
		}
		b.chest(-8, 1, 20, Direction.EAST, "library");
		b.chest(8, 1, 20, Direction.WEST, "crypt");
		// The crypt below the altar.
		LandParts.stairway(b, -1, 1, 0, 8, 1, 8, DeepBuilder.p(s(Blocks.DEEPSLATE_BRICKS)), Blocks.DEEPSLATE_BRICK_STAIRS);
		b.fill(-8, -8, 16, 8, -2, 22, s(Blocks.DEEPSLATE_BRICKS));
		b.fill(-7, -7, 17, 7, -3, 21, b.open());
		b.clear(-1, -7, 16, 1, -5, 16);
		b.spawner(0, -7, 19, EntityTypes.SKELETON);
		b.chest(-6, -7, 21, Direction.EAST, "treasure");
		b.chest(6, -7, 21, Direction.WEST, "crypt");
		b.keeper(0, 1, 0, EntityTypes.VINDICATOR);
		b.keeper(-3, 1, 10, EntityTypes.SKELETON);
	}

	/** The ruins of a city: broken streets, roofless houses, a fallen tower, and the things that live there now. */
	static void ruinedCity(DeepBuilder b) {
		site(b, -26, -26, 26, 26, 0, 30);
		b.fill(-24, 0, -24, 24, 0, 24, b.mix(s(Blocks.COBBLESTONE), s(Blocks.GRAVEL), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.STONE_BRICKS)));
		DeepBuilder.Paint wall = b.ruin(b.worn(s(Blocks.STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS)), 0.2);
		int n = 0;
		for (int bx = -20; bx <= 12; bx += 16) {
			for (int bz = -20; bz <= 12; bz += 16) {
				LandParts.house(b, bx, bz, bx + 8, bz + 8, 4 + n % 3 * 2, wall, s(Blocks.STONE_BRICKS), s(Blocks.SPRUCE_PLANKS));
				b.chest(bx + 1, 1, bz + 7, Direction.EAST, n % 2 == 0 ? "common" : "library");
				n++;
			}
		}
		b.cylinder(0, 0, 4.5, 1, 22, wall, true);
		b.disc(0, 11, 0, 3.5, s(Blocks.SPRUCE_PLANKS));
		ladder(b, 3, 0, 1, 11, Direction.WEST);
		b.pillar(4, 0, 1, 11, s(Blocks.STONE_BRICKS));
		b.chest(-2, 12, 2, Direction.EAST, "treasure");
		b.spawner(0, 1, -2, EntityTypes.ZOMBIE);
		b.keeper(-12, 1, 0, EntityTypes.ZOMBIE);
		b.keeper(12, 1, 0, "raccoon");
		b.keeper(0, 1, 12, EntityTypes.SKELETON);
		b.keeper(0, 1, -12, EntityTypes.PILLAGER);
	}
}
