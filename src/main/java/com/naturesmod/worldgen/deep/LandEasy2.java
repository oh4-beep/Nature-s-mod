package com.naturesmod.worldgen.deep;

import static com.naturesmod.worldgen.deep.DeepBuilder.axis;
import static com.naturesmod.worldgen.deep.DeepBuilder.facing;
import static com.naturesmod.worldgen.deep.DeepBuilder.head;
import static com.naturesmod.worldgen.deep.DeepBuilder.lantern;
import static com.naturesmod.worldgen.deep.DeepBuilder.s;
import static com.naturesmod.worldgen.deep.DeepBuilder.slab;
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

/** The second wave of easy finds: huts, shrines, a fallen star, fossils and odd corners of the wild. */
final class LandEasy2 {
	private LandEasy2() {
	}

	private static BlockState leaves(Block leaves) {
		return s(leaves).setValue(BlockStateProperties.PERSISTENT, true);
	}

	/** A small hut: four walls, a door in the north wall, a flat roof of slabs. */
	private static void hut(DeepBuilder b, int x0, int z0, int x1, int z1, int height, BlockState wall, Block roof) {
		LandParts.house(b, x0, z0, x1, z1, height, DeepBuilder.p(wall), log(Blocks.OAK_LOG), s(Blocks.OAK_PLANKS));
		b.fill(x0 - 1, height + 1, z0 - 1, x1 + 1, height + 1, z1 + 1, slab(roof, false));
		b.clear((x0 + x1) / 2, 1, z0, (x0 + x1) / 2, 2, z0);
	}

	/** A fisher's hut by the water, nets drying and the catch in barrels. */
	static void fishingHut(DeepBuilder b) {
		site(b, 8.5, 8);
		hut(b, -3, -2, 2, 3, 3, s(Blocks.SPRUCE_PLANKS), Blocks.SPRUCE_SLAB);
		b.fill(-1, 0, -8, 0, 0, -3, s(Blocks.SPRUCE_PLANKS));
		b.pillar(-2, -8, 1, 2, s(Blocks.SPRUCE_FENCE));
		b.pillar(1, -8, 1, 2, s(Blocks.SPRUCE_FENCE));
		b.set(-2, 3, -8, lantern(false, false));
		b.chest(1, 1, 2, Direction.WEST, "camp");
		b.barrel(-2, 1, 2, Direction.UP, "common");
		b.set(-2, 1, 0, Blocks.CAULDRON);
		b.fill(4, 1, -1, 4, 2, 1, s(Blocks.COBWEB));
		b.set(4, 0, 2, s(Blocks.WATER));
	}

	/** A shepherd's hut on the hillside and a pen of sheep. */
	static void shepherdsHut(DeepBuilder b) {
		site(b, 9.5, 8);
		hut(b, -8, -3, -4, 1, 3, s(Blocks.COBBLESTONE), Blocks.COBBLESTONE_SLAB);
		b.bed(-7, 1, -1, Direction.SOUTH, vanilla("white_bed"));
		b.chest(-5, 1, 0, Direction.WEST, "farm");
		LandParts.railing(b, 0, 1, -4, 7, 1, 4, Blocks.OAK_FENCE);
		b.set(3, 1, -4, facing(Blocks.OAK_FENCE_GATE, Direction.NORTH));
		b.set(6, 1, 3, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
		b.keeper(2, 1, 0, EntityTypes.SHEEP);
		b.keeper(4, 1, 2, EntityTypes.SHEEP);
		b.keeper(5, 1, -2, EntityTypes.SHEEP);
	}

	/** A fallen star: a crater, its glassy rim, and the hot rock that hit the ground. */
	static void fallenMeteor(DeepBuilder b) {
		site(b, 9.5, 8);
		b.ellipsoid(0, 0, 0, 7.0, 4.0, 7.0, (x, y, z) -> b.open(), false, -4);
		b.ellipsoid(0, -1, 0, 7.5, 4.0, 7.5, b.mix(s(Blocks.BLACKSTONE), s(Blocks.TUFF), s(Blocks.COARSE_DIRT), s(Blocks.OBSIDIAN)), true, -5);
		b.sphere(0, -2, 0, 2.2, b.mix(s(Blocks.MAGMA_BLOCK), s(Blocks.OBSIDIAN), s(Blocks.CRYING_OBSIDIAN), s(Blocks.RAW_IRON_BLOCK)), false, -3);
		b.chest(3, -2, 0, Direction.WEST, "meteor");
	}

	/** A ring of giant mushrooms round a mossy clearing. */
	static void mushroomRing(DeepBuilder b) {
		site(b, 9.5, 14);
		for (int i = 0; i < 6; i++) {
			double angle = i * Math.PI / 3;
			int x = (int) Math.round(Math.cos(angle) * 6);
			int z = (int) Math.round(Math.sin(angle) * 6);
			int h = 4 + i % 3;
			b.pillar(x, z, 1, h, s(Blocks.MUSHROOM_STEM));
			b.disc(x, h + 1, z, 2.3, s(i % 2 == 0 ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK));
		}
		b.disc(0, 0, 0, 4.5, s(Blocks.MOSS_BLOCK));
		b.chest(0, 1, 0, Direction.NORTH, "swamp");
		b.set(1, 1, 1, Blocks.RED_MUSHROOM);
		b.set(-1, 1, -1, Blocks.BROWN_MUSHROOM);
		b.set(1, 0, 1, Blocks.MYCELIUM);
		b.set(-1, 0, -1, Blocks.MYCELIUM);
	}

	/** A roadside shrine: a little roofed altar with candles and an offering box. */
	static void roadsideShrine(DeepBuilder b) {
		site(b, 6.5, 8);
		b.fill(-2, 0, -2, 2, 0, 2, s(Blocks.STONE_BRICKS));
		b.pillar(-2, 2, 1, 3, s(Blocks.STONE_BRICK_WALL));
		b.pillar(2, 2, 1, 3, s(Blocks.STONE_BRICK_WALL));
		b.fill(-2, 4, 1, 2, 4, 2, slab(Blocks.STONE_BRICK_SLAB, false));
		b.set(0, 1, 2, s(Blocks.CHISELED_STONE_BRICKS));
		b.set(0, 2, 2, s(vanilla("white_candle")).setValue(BlockStateProperties.CANDLES, 3).setValue(BlockStateProperties.LIT, true));
		b.chest(0, 1, 1, Direction.NORTH, "wishing");
		b.set(-1, 1, -1, Blocks.POPPY);
		b.set(1, 1, -1, Blocks.DANDELION);
	}

	/** A hermit's cave dug into a mound: a bed, a fire, a lifetime of odds and ends. */
	static void hermitCave(DeepBuilder b) {
		site(b, 9.5, 4);
		b.ellipsoid(0, 0, 1, 7.0, 5.0, 6.0, b.mix(s(Blocks.STONE), s(Blocks.COBBLESTONE), s(Blocks.ANDESITE)), false, 0);
		b.ellipsoid(0, 1, 1, 4.5, 3.0, 4.0, (x, y, z) -> b.open(), false, 0);
		b.clear(0, 1, -6, 0, 2, -3);
		b.bed(-3, 1, 2, Direction.SOUTH, vanilla("brown_bed"));
		b.set(2, 1, 3, s(Blocks.CAMPFIRE).setValue(BlockStateProperties.LIT, false));
		b.chest(3, 1, 0, Direction.WEST, "explorer");
		b.set(-2, 1, -1, Blocks.CRAFTING_TABLE);
		b.set(0, 4, 1, lantern(false, true));
	}

	/** A garden of cacti behind a sandstone wall, and a water trough. */
	static void cactusGarden(DeepBuilder b) {
		site(b, 9.5, 6);
		b.walls(-7, 1, -7, 7, 1, 7, s(Blocks.SANDSTONE_WALL));
		b.clear(0, 1, -7, 0, 1, -7);
		for (int x = -5; x <= 5; x += 3) {
			for (int z = -5; z <= 5; z += 3) {
				if (Math.abs(x) + Math.abs(z) > 2) {
					b.set(x, 0, z, Blocks.SAND);
					b.pillar(x, z, 1, 1 + (int) (b.rand(x, 0, z, 120) * 3), s(Blocks.CACTUS));
				}
			}
		}
		b.fill(-1, 1, 0, 1, 1, 0, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		b.barrel(0, 1, 1, Direction.UP, "desert");
	}

	/** Toppled pillars of a sandstone temple half under the dunes. */
	static void sandRuins(DeepBuilder b) {
		site(b, 9.5, 8);
		DeepBuilder.Paint stone = b.worn(s(Blocks.CUT_SANDSTONE), s(Blocks.SANDSTONE), s(Blocks.CHISELED_SANDSTONE));
		b.fill(-6, 0, -6, 6, 0, 6, b.mix(s(Blocks.SMOOTH_SANDSTONE), s(Blocks.SAND), s(Blocks.SAND)));
		for (int[] p : new int[][] {{-5, -5, 5}, {5, -5, 2}, {-5, 5, 3}, {5, 5, 6}}) {
			b.pillar(p[0], p[1], 1, p[2], stone);
		}
		b.fill(-3, 1, 0, 2, 1, 0, s(Blocks.SMOOTH_SANDSTONE));
		b.chest(0, 1, 2, Direction.NORTH, "desert");
		b.set(-1, 1, 3, head(Blocks.SKELETON_SKULL, 8));
		b.keeper(3, 1, -2, "gazelle");
	}

	/** A lookout platform high in a jungle tree. */
	static void treePlatform(DeepBuilder b) {
		site(b, 8.5, 22);
		b.pillar(0, 0, 1, 14, log(Blocks.JUNGLE_LOG));
		b.disc(0, 10, 0, 4.5, s(Blocks.JUNGLE_PLANKS));
		b.set(0, 10, 0, log(Blocks.JUNGLE_LOG));
		b.ellipsoid(0, 16, 0, 5.0, 3.0, 5.0, (x, y, z) -> leaves(Blocks.JUNGLE_LEAVES), false, -2);
		b.pillar(0, 0, 14, 16, log(Blocks.JUNGLE_LOG));
		for (int y = 1; y <= 10; y++) {
			b.set(0, y, -1, facing(Blocks.LADDER, Direction.NORTH));
		}
		b.chest(2, 11, 2, Direction.WEST, "jungle");
		b.set(-2, 11, 2, lantern(false, false));
		b.keeper(-2, 11, -2, "lemur");
	}

	/** An igloo village: two snow domes and a fire between them. */
	static void igloos(DeepBuilder b) {
		site(b, 9.5, 8);
		for (int side = -1; side <= 1; side += 2) {
			int x = side * 4;
			b.sphere(x, 0, 0, 3.5, s(Blocks.SNOW_BLOCK), true, 1);
			b.clear(x, 1, -3, x, 2, -3);
			b.set(x + side, 1, 1, Blocks.FURNACE);
		}
		b.chest(-4, 1, 1, Direction.EAST, "explorer");
		b.bed(4, 1, 0, Direction.SOUTH, vanilla("light_blue_bed"));
		b.set(0, 1, -5, s(Blocks.CAMPFIRE));
		b.keeper(0, 1, 4, "reindeer");
	}

	/** An ice fisher's camp: a hole in the ice, a tent, a sled. */
	static void iceFishingCamp(DeepBuilder b) {
		site(b, 8.5, 8);
		b.disc(0, 0, 0, 6.5, s(Blocks.PACKED_ICE));
		b.fill(-1, -1, -1, 0, 0, 0, s(Blocks.WATER));
		LandParts.tent(b, 4, -2, 2, wool("blue"));
		b.chest(4, 1, 1, Direction.NORTH, "explorer");
		b.fill(-5, 1, 2, -4, 1, 4, slab(Blocks.SPRUCE_SLAB, false));
		b.barrel(-4, 2, 3, Direction.UP, "camp");
		b.keeper(-3, 1, -3, "otter");
	}

	/** A prospector's camp: a sluice, a tent and a mule's worth of gear. */
	static void prospectorCamp(DeepBuilder b) {
		site(b, 9.5, 8);
		LandParts.tent(b, -4, -3, 2, wool("brown"));
		b.chest(-4, 1, 1, Direction.NORTH, "mine");
		b.fill(2, 1, -4, 2, 1, 3, s(Blocks.SPRUCE_PLANKS));
		b.fill(3, 1, -4, 3, 1, 3, s(Blocks.SPRUCE_SLAB));
		b.set(3, 2, 3, Blocks.IRON_ORE);
		b.set(5, 1, 0, Blocks.ANVIL);
		b.barrel(5, 1, -2, Direction.UP, "mine");
		b.set(0, 1, 4, s(Blocks.CAMPFIRE));
		b.keeper(6, 1, 4, EntityTypes.DONKEY);
	}

	/** A great baobab with a hollow trunk, and something stored inside. */
	static void baobab(DeepBuilder b) {
		site(b, 9.5, 20);
		b.cylinder(0, 0, 2.5, 1, 10, log(Blocks.ACACIA_LOG), true);
		b.fill(-1, 1, -2, 1, 2, -2, b.open());
		for (int i = 0; i < 5; i++) {
			double angle = i * Math.PI * 2 / 5;
			int x = (int) Math.round(Math.cos(angle) * 5);
			int z = (int) Math.round(Math.sin(angle) * 5);
			b.line(0, 10, 0, x, 13, z, log(Blocks.ACACIA_LOG));
			b.disc(x, 14, z, 2.5, leaves(Blocks.ACACIA_LEAVES));
		}
		b.chest(0, 1, 1, Direction.NORTH, "savanna");
		b.keeper(5, 1, 5, "warthog");
	}

	/** A hag's hut on stilts over the bog, with a black cat on the step. */
	static void stiltHut(DeepBuilder b) {
		site(b, 8.5, 14);
		for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			b.pillar(c[0], c[1], 1, 3, log(Blocks.DARK_OAK_LOG));
		}
		b.fill(-3, 4, -3, 3, 4, 3, s(Blocks.DARK_OAK_PLANKS));
		b.walls(-2, 5, -2, 2, 7, 2, s(Blocks.SPRUCE_PLANKS));
		b.fill(-3, 8, -3, 3, 8, 3, slab(Blocks.DARK_OAK_SLAB, false));
		b.clear(0, 5, -2, 0, 6, -2);
		b.pillar(0, -3, 1, 3, s(Blocks.DARK_OAK_PLANKS));
		for (int y = 1; y <= 4; y++) {
			b.set(0, y, -4, facing(Blocks.LADDER, Direction.NORTH));
		}
		b.set(-1, 5, 1, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		b.chest(1, 5, 1, Direction.WEST, "witch");
		b.keeper(-1, 5, -1, EntityTypes.CAT);
	}

	/** A white gazebo in a flower meadow. */
	static void gazebo(DeepBuilder b) {
		site(b, 8.5, 10);
		b.disc(0, 0, 0, 3.5, s(Blocks.SMOOTH_QUARTZ));
		for (int[] c : new int[][] {{-3, 0}, {3, 0}, {0, -3}, {0, 3}, {-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			if (c[1] != -3) {
				b.pillar(c[0], c[1], 1, 3, s(Blocks.QUARTZ_PILLAR));
			}
		}
		b.disc(0, 4, 0, 3.5, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
		b.set(0, 5, 0, s(Blocks.QUARTZ_BLOCK));
		b.chest(0, 1, 1, Direction.NORTH, "farm");
		b.set(0, 3, 0, lantern(false, true));
		Block[] flowers = {Blocks.POPPY, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.OXEYE_DAISY};
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				if (x * x + z * z > 20 && x * x + z * z < 50 && b.chance(x, 1, z, 121, 0.4)) {
					b.set(x, 1, z, b.pick(x, 1, z, 122, flowers));
				}
			}
		}
		b.keeper(5, 1, -5, "peacock");
	}

	/** Wagons drawn up in a circle against the night, their fire long cold. */
	static void wagonCircle(DeepBuilder b) {
		site(b, 9.5, 8);
		for (int i = 0; i < 4; i++) {
			double angle = i * Math.PI / 2 + Math.PI / 4;
			int x = (int) Math.round(Math.cos(angle) * 6);
			int z = (int) Math.round(Math.sin(angle) * 6);
			b.fill(x - 1, 1, z - 1, x + 1, 1, z + 1, s(Blocks.SPRUCE_PLANKS));
			b.fill(x - 1, 2, z - 1, x + 1, 2, z + 1, wool(i % 2 == 0 ? "white" : "light_gray"));
		}
		b.set(0, 1, 0, s(Blocks.CAMPFIRE).setValue(BlockStateProperties.LIT, false));
		b.chest(4, 3, 4, Direction.NORTH, "camp");
		b.barrel(-4, 3, -4, Direction.UP, "common");
		b.keeper(0, 1, -3, EntityTypes.HORSE);
	}

	/** An archery range: targets at the end of the lanes, a rack of bows. */
	static void archeryRange(DeepBuilder b) {
		site(b, -9, -9, 9, 9, 0, 6);
		for (int x = -6; x <= 6; x += 4) {
			b.set(x, 1, 7, Blocks.TARGET);
			b.set(x, 2, 7, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
			b.set(x, 0, -6, s(Blocks.STONE_BRICKS));
		}
		b.fill(-7, 1, -8, 7, 1, -8, s(Blocks.SPRUCE_FENCE));
		b.stand(-8, 1, -6, Direction.SOUTH, new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.LEATHER_CHESTPLATE), ItemStack.EMPTY,
				ItemStack.EMPTY, new ItemStack(Items.BOW));
		b.chest(8, 1, -6, Direction.WEST, "ranger");
	}

	/** A trapper's log lodge, hides drying on racks. */
	static void trapperLodge(DeepBuilder b) {
		site(b, 9.5, 10);
		LandParts.cottage(b, -4, -3, 4, 3, 3, DeepBuilder.p(log(Blocks.SPRUCE_LOG)), log(Blocks.STRIPPED_SPRUCE_LOG), s(Blocks.SPRUCE_PLANKS),
				Blocks.SPRUCE_STAIRS, s(Blocks.SPRUCE_PLANKS));
		b.chest(3, 1, 2, Direction.WEST, "cabin");
		b.bed(-3, 1, 1, Direction.SOUTH, vanilla("brown_bed"));
		b.set(-3, 1, -2, s(Blocks.SMOKER).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		b.fill(6, 1, -2, 6, 2, 2, s(Blocks.SPRUCE_FENCE));
		b.fill(6, 3, -2, 6, 3, 2, carpet("brown"));
		b.keeper(-6, 1, 5, "wolverine");
	}

	/** The bones of something enormous, weathering out of the ground. */
	static void fossil(DeepBuilder b) {
		site(b, 9.5, 8);
		for (int k = -7; k <= 7; k++) {
			b.set(k, 1 + (7 - Math.abs(k)) / 3, 0, axis(Blocks.BONE_BLOCK, Direction.Axis.X));
			if (k % 2 == 0 && Math.abs(k) < 6) {
				for (int side = -1; side <= 1; side += 2) {
					b.line(k, 3, side, k, 1, side * 3, s(Blocks.BONE_BLOCK));
				}
			}
		}
		b.fill(-9, 1, -1, -8, 3, 1, s(Blocks.BONE_BLOCK));
		b.set(-9, 2, -2, s(Blocks.CALCITE));
		b.chest(4, 1, 4, Direction.NORTH, "explorer");
		b.set(3, 1, 4, s(Blocks.SUSPICIOUS_SAND));
	}

	/** A fairy ring: a circle of mushrooms and flowers round a glowing stone. */
	static void fairyRing(DeepBuilder b) {
		site(b, 8.5, 6);
		for (int i = 0; i < 16; i++) {
			double angle = i * Math.PI / 8;
			int x = (int) Math.round(Math.cos(angle) * 5);
			int z = (int) Math.round(Math.sin(angle) * 5);
			b.set(x, 0, z, Blocks.PODZOL);
			b.set(x, 1, z, i % 2 == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM);
		}
		b.set(0, 0, 0, Blocks.GLOWSTONE);
		b.chest(0, 1, 1, Direction.NORTH, "wishing");
		b.set(0, 1, -1, Blocks.LILY_OF_THE_VALLEY);
		b.keeper(2, 1, -2, "hedgehog");
	}
}
