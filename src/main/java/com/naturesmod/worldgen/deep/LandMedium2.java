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
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The second wave of harder halls: coves, labs, camps, nests, a monastery and a barrow. */
final class LandMedium2 {
	private LandMedium2() {
	}

	private static BlockState leaves(Block leaves) {
		return s(leaves).setValue(BlockStateProperties.PERSISTENT, true);
	}

	/** A smugglers' cave in a rocky hill: barrels of contraband and the smugglers guarding them. */
	static void smugglersCove(DeepBuilder b) {
		site(b, 15.5, 4);
		b.ellipsoid(0, 0, 3, 12.0, 8.0, 11.0, b.mix(s(Blocks.STONE), s(Blocks.ANDESITE), s(Blocks.COBBLESTONE)), false, 0);
		b.ellipsoid(0, 1, 3, 8.0, 5.0, 7.0, (x, y, z) -> b.open(), false, 0);
		b.clear(-1, 1, -9, 1, 3, -3);
		b.fill(-6, 1, 6, -3, 2, 8, Blocks.BARREL);
		b.barrel(-5, 3, 7, Direction.UP, "bandit");
		b.chest(5, 1, 8, Direction.WEST, "bandit");
		b.chest(6, 1, 2, Direction.WEST, "saloon");
		b.set(0, 1, 4, s(Blocks.CAMPFIRE));
		b.set(0, 6, 3, lantern(false, true));
		b.keeper(-2, 1, 2, EntityTypes.PILLAGER);
		b.keeper(3, 1, 5, EntityTypes.VINDICATOR);
	}

	/** An alchemist's tower: brewing on every floor, and the alchemist's prize at the top. */
	static void alchemistTower(DeepBuilder b) {
		site(b, 9.5, 28);
		LandParts.tower(b, 0, 0, 4.5, 18, 6, b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS)), s(Blocks.SPRUCE_PLANKS));
		for (int k = 0; k <= 5; k++) {
			b.disc(0, 19 + k, 0, 5.0 - k, s(vanilla("cyan_terracotta")));
		}
		for (int floor = 0; floor <= 12; floor += 6) {
			b.set(-2, floor + 1, -1, Blocks.BREWING_STAND);
			b.set(-2, floor + 1, 1, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			b.set(0, floor == 12 ? 18 : floor + 5, 0, lantern(true, true));
		}
		b.chest(-2, 1, 2, Direction.EAST, "witch");
		b.chest(-2, 13, 2, Direction.EAST, "library");
		b.fill(1, 7, 2, 2, 8, 2, Blocks.BOOKSHELF);
		b.keeper(1, 13, -1, EntityTypes.WITCH);
	}

	/** Knights encamped: pavilions in their colours, a jousting rail, armour on its stands. */
	static void knightsCamp(DeepBuilder b) {
		site(b, -16, -16, 16, 16, 0, 10);
		String[] colours = {"red", "blue", "yellow"};
		for (int i = 0; i < 3; i++) {
			LandParts.tent(b, -10 + i * 10, -12, -6, wool(colours[i]));
			b.chest(-10 + i * 10, 1, -7, Direction.NORTH, i == 1 ? "fortress" : "ranger");
		}
		b.fill(-12, 1, 4, 12, 1, 4, joined(Blocks.SPRUCE_FENCE, true));
		for (int x = -12; x <= 12; x += 8) {
			b.stand(x, 1, 8, Direction.NORTH, new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
					new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.IRON_SWORD));
		}
		b.set(0, 1, -2, s(Blocks.CAMPFIRE));
		b.keeper(-6, 1, 0, EntityTypes.HORSE);
		b.keeper(6, 1, 1, EntityTypes.HORSE);
		b.keeper(0, 1, 10, EntityTypes.VINDICATOR);
	}

	/** The square of an abandoned village: a dry well, empty stalls, houses with their doors open. */
	static void abandonedSquare(DeepBuilder b) {
		site(b, -16, -16, 16, 16, 0, 12);
		b.fill(-6, 0, -6, 6, 0, 6, b.mix(s(Blocks.COBBLESTONE), s(Blocks.GRAVEL), s(Blocks.DIRT_PATH)));
		b.cylinder(0, 0, 1.5, 0, 1, s(Blocks.MOSSY_COBBLESTONE), true);
		for (int[] h : new int[][] {{-14, -14}, {6, -14}, {-14, 6}, {6, 6}}) {
			LandParts.cottage(b, h[0], h[1], h[0] + 7, h[1] + 7, 4, b.ruin(DeepBuilder.p(s(Blocks.OAK_PLANKS)), 0.1), log(Blocks.OAK_LOG),
					s(Blocks.OAK_PLANKS), Blocks.OAK_STAIRS, s(Blocks.OAK_PLANKS));
			b.chest(h[0] + 1, 1, h[1] + 6, Direction.EAST, "common");
		}
		b.set(3, 1, 3, Blocks.BELL);
		b.keeper(-2, 1, 4, EntityTypes.ZOMBIE_VILLAGER);
	}

	/** A nest of scorpions dug into the sand, their hoard among the bones of their prey. */
	static void scorpionNest(DeepBuilder b) {
		site(b, 12.5, 6);
		DeepBuilder.Paint sand = b.mix(s(Blocks.SANDSTONE), s(Blocks.SMOOTH_SANDSTONE), s(Blocks.SAND));
		b.ellipsoid(0, -6, 2, 9.0, 5.0, 8.0, sand, false, -5);
		b.ellipsoid(0, -6, 2, 7.0, 3.5, 6.0, (x, y, z) -> b.open(), false, -3);
		LandParts.stairway(b, -1, 1, -7, 6, sand, Blocks.SANDSTONE_STAIRS);
		b.spawner(0, -9, 4, EntityTypes.CAVE_SPIDER);
		b.chest(-4, -9, 5, Direction.EAST, "tomb");
		b.chest(4, -9, 5, Direction.WEST, "desert");
		b.fill(-2, -10, 7, 2, -10, 7, s(Blocks.BONE_BLOCK));
		b.set(0, -9, 7, head(Blocks.SKELETON_SKULL, 0));
		b.keeper(-3, -9, 1, "scorpion");
		b.keeper(3, -9, 1, "scorpion");
	}

	/** A village of huts in the jungle canopy, joined by rope bridges. */
	static void treeVillage(DeepBuilder b) {
		site(b, 15.5, 26);
		int[][] trees = {{-9, -6}, {8, -7}, {0, 9}};
		for (int[] t : trees) {
			b.cylinder(t[0], t[1], 1.5, 1, 18, log(Blocks.JUNGLE_LOG), false);
			b.disc(t[0], 10, t[1], 4.5, s(Blocks.JUNGLE_PLANKS));
			b.cylinder(t[0], t[1], 1.5, 10, 10, log(Blocks.JUNGLE_LOG), false);
			b.ellipsoid(t[0], 20, t[1], 5.0, 3.0, 5.0, (x, y, z) -> leaves(Blocks.JUNGLE_LEAVES), false, -2);
			b.walls(t[0] + 2, 11, t[1] - 1, t[0] + 4, 12, t[1] + 1, s(Blocks.BAMBOO_PLANKS));
			for (int y = 1; y <= 10; y++) {
				b.set(t[0], y, t[1] - 2, facing(Blocks.LADDER, Direction.NORTH));
			}
		}
		b.line(-9, 10, -6, 8, 10, -7, s(Blocks.JUNGLE_SLAB));
		b.line(8, 10, -7, 0, 10, 9, s(Blocks.JUNGLE_SLAB));
		b.chest(-7, 11, -8, Direction.NORTH, "jungle");
		b.chest(10, 11, -9, Direction.NORTH, "treehouse");
		b.chest(-2, 11, 11, Direction.EAST, "jungle");
		b.keeper(-6, 11, -4, "orangutan");
		b.keeper(2, 11, 7, "sloth");
	}

	/** A ruined temple where the lemurs sun themselves on the warm stones. */
	static void lemurRuins(DeepBuilder b) {
		site(b, -14, -14, 14, 14, 0, 12);
		DeepBuilder.Paint stone = b.ruin(b.worn(s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS)), 0.2);
		b.fill(-10, 0, -10, 10, 0, 10, b.mix(s(Blocks.MOSSY_COBBLESTONE), s(Blocks.GRASS_BLOCK)));
		b.walls(-10, 1, -10, 10, 3, 10, stone);
		b.fill(-4, 1, -4, 4, 2, 4, s(Blocks.MOSSY_STONE_BRICKS));
		b.fill(-2, 3, -2, 2, 3, 2, s(Blocks.CHISELED_STONE_BRICKS));
		b.chest(0, 4, 0, Direction.NORTH, "temple");
		b.clear(-1, 1, -10, 1, 2, -10);
		for (int i = 0; i < 5; i++) {
			b.keeper(-7 + i * 3, 1, -6, "lemur");
		}
	}

	/** A hunting lodge: a long hall of logs, trophies on the walls, the hunters' gear. */
	static void huntingLodge(DeepBuilder b) {
		site(b, -16, -12, 16, 12, 0, 14);
		LandParts.cottage(b, -10, -5, 10, 5, 5, DeepBuilder.p(log(Blocks.SPRUCE_LOG)), log(Blocks.STRIPPED_SPRUCE_LOG), s(Blocks.SPRUCE_PLANKS),
				Blocks.SPRUCE_STAIRS, s(Blocks.SPRUCE_PLANKS));
		b.fill(-8, 1, 3, -4, 1, 3, carpet("brown"));
		for (int x = -8; x <= 8; x += 4) {
			b.set(x, 4, 4, head(Blocks.SKELETON_SKULL, 8));
		}
		b.set(0, 1, 3, Blocks.CRAFTING_TABLE);
		b.chest(9, 1, 4, Direction.WEST, "ranger");
		b.chest(-9, 1, -4, Direction.EAST, "cabin");
		b.set(2, 1, 2, s(Blocks.SMOKER).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		b.keeper(-12, 1, 8, EntityTypes.WOLF);
		b.keeper(12, 1, 8, EntityTypes.WOLF);
	}

	/** A research station on the ice, its instruments frozen and its samples in the cold store. */
	static void frozenLab(DeepBuilder b) {
		site(b, -14, -12, 14, 12, 0, 10);
		LandParts.house(b, -10, -6, 10, 6, 4, DeepBuilder.p(s(vanilla("white_concrete"))), s(Blocks.POLISHED_ANDESITE), s(Blocks.SMOOTH_STONE));
		b.fill(-10, 5, -6, 10, 5, 6, s(Blocks.SMOOTH_STONE_SLAB));
		b.clear(0, 1, -6, 0, 2, -6);
		for (int x = -6; x <= 6; x += 6) {
			b.set(x, 2, -6, joined(Blocks.GLASS_PANE, true));
			b.set(x, 4, 0, lantern(false, true));
		}
		b.fill(-9, 1, 5, -5, 1, 5, Blocks.BARREL);
		b.chest(9, 1, 5, Direction.WEST, "observatory");
		b.chest(-9, 1, -5, Direction.EAST, "frost");
		b.fill(4, 1, 2, 8, 2, 4, s(Blocks.PACKED_ICE));
		b.set(-2, 1, 0, Blocks.BREWING_STAND);
		b.keeper(0, 1, 9, "musk_ox");
	}

	/** A monastery on the mountain: a cloister round a garden, a bell tower, a quiet library. */
	static void monastery(DeepBuilder b) {
		site(b, -16, -16, 16, 16, 0, 22);
		DeepBuilder.Paint stone = b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS));
		b.walls(-12, 1, -12, 12, 5, 12, stone);
		b.walls(-9, 1, -9, 9, 4, 9, s(Blocks.STONE_BRICK_WALL));
		b.fill(-8, 1, -8, 8, 4, 8, b.open());
		b.fill(-8, 0, -8, 8, 0, 8, s(Blocks.GRASS_BLOCK));
		b.fill(-12, 6, -12, 12, 6, 12, slab(Blocks.STONE_BRICK_SLAB, false));
		b.fill(-8, 6, -8, 8, 6, 8, b.open());
		b.clear(-1, 1, -12, 1, 3, -12);
		b.clear(-1, 1, -9, 1, 3, -9);
		// The bell tower in the north-east corner.
		b.fill(9, 1, -12, 12, 15, -9, stone);
		b.fill(10, 1, -11, 11, 15, -10, b.open());
		b.clear(9, 12, -11, 12, 14, -10);
		b.fill(9, 16, -12, 12, 16, -9, s(Blocks.STONE_BRICKS));
		b.set(10, 15, -11, s(Blocks.BELL).setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING));
		for (int y = 1; y <= 12; y++) {
			b.set(11, y, -11, facing(Blocks.LADDER, Direction.SOUTH));
		}
		b.clear(9, 1, -10, 9, 2, -10);
		for (int z = -6; z <= 6; z += 3) {
			b.fill(-11, 1, z, -11, 3, z, Blocks.BOOKSHELF);
		}
		b.chest(-11, 1, 8, Direction.EAST, "library");
		b.chest(11, 1, 8, Direction.WEST, "observatory");
		b.set(0, 1, 0, Blocks.ENCHANTING_TABLE);
		b.keeper(4, 1, 4, "yak");
	}

	/** A bank out in the badlands, its vault blown open and the robbers still counting. */
	static void mesaBank(DeepBuilder b) {
		site(b, -14, -12, 14, 12, 0, 12);
		LandParts.house(b, -9, -6, 9, 6, 6, b.worn(s(vanilla("orange_terracotta")), s(Blocks.TERRACOTTA)), s(Blocks.SMOOTH_SANDSTONE),
				s(Blocks.SPRUCE_PLANKS));
		b.fill(-9, 7, -6, 9, 7, 6, s(Blocks.SMOOTH_SANDSTONE_SLAB));
		b.clear(-1, 1, -6, 1, 3, -6);
		b.fill(-8, 1, -2, 8, 1, -2, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, true));
		b.walls(2, 1, 1, 8, 5, 5, s(Blocks.POLISHED_ANDESITE));
		b.clear(2, 1, 2, 2, 2, 3);
		b.fill(3, 1, 2, 7, 4, 4, b.open());
		b.set(7, 1, 4, Blocks.GOLD_BLOCK);
		b.chest(4, 1, 4, Direction.NORTH, "saloon");
		b.chest(6, 1, 2, Direction.SOUTH, "fortress");
		b.set(0, 6, 0, lantern(false, true));
		b.set(0, 7, 0, s(Blocks.SMOOTH_SANDSTONE));
		b.keeper(-4, 1, 2, EntityTypes.PILLAGER);
		b.keeper(0, 1, 3, EntityTypes.PILLAGER);
	}

	/** A kraal: round huts of mud and thatch inside a thorn fence, the herd in the middle. */
	static void kraal(DeepBuilder b) {
		site(b, 16.5, 10);
		b.cylinder(0, 0, 15.0, 1, 2, s(Blocks.ACACIA_FENCE), true);
		b.clear(0, 1, -15, 1, 2, -14);
		for (int i = 0; i < 5; i++) {
			double angle = i * Math.PI * 2 / 5 + 0.6;
			int x = (int) Math.round(Math.cos(angle) * 10);
			int z = (int) Math.round(Math.sin(angle) * 10);
			b.cylinder(x, z, 2.5, 1, 3, s(Blocks.PACKED_MUD), true);
			b.disc(x, 4, z, 3.0, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
			b.disc(x, 5, z, 1.5, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
			int dx = (int) Math.signum(-x);
			int dz = (int) Math.signum(-z);
			b.clear(x + dx * 2, 1, z + dz * 2, x + dx * 2, 2, z + dz * 2);
			if (i < 3) {
				b.chest(x, 1, z, Direction.NORTH, i == 0 ? "savanna" : "safari");
			}
		}
		b.set(0, 1, 0, s(Blocks.CAMPFIRE));
		b.keeper(-3, 1, 3, EntityTypes.COW);
		b.keeper(3, 1, -3, "gazelle");
	}

	/** A spider den in the bog: webs between dead trees, egg sacs and what is left of the victims. */
	static void spiderDen(DeepBuilder b) {
		site(b, 14.5, 12);
		for (int i = 0; i < 6; i++) {
			double angle = i * Math.PI / 3;
			int x = (int) Math.round(Math.cos(angle) * 8);
			int z = (int) Math.round(Math.sin(angle) * 8);
			b.pillar(x, z, 1, 7, log(Blocks.DARK_OAK_LOG));
			b.line(x, 6, z, 0, 8, 0, s(Blocks.COBWEB));
		}
		b.fill(-3, 1, -3, 3, 3, 3, s(Blocks.COBWEB));
		b.fill(-1, 1, -1, 1, 2, 1, b.open());
		b.spawner(0, 1, 0, EntityTypes.SPIDER);
		b.chest(-5, 1, 2, Direction.EAST, "swamp");
		b.chest(5, 1, -2, Direction.WEST, "crypt");
		b.stand(4, 1, 4, Direction.WEST, new ItemStack(Items.CHAINMAIL_HELMET), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
				new ItemStack(Items.IRON_SWORD));
	}

	/** A sunken garden: steps down to a walled lawn with a fountain, roses and a forgotten bench. */
	static void sunkenGarden(DeepBuilder b) {
		site(b, 14.5, 8);
		b.fill(-10, -4, -10, 10, -4, 10, s(Blocks.GRASS_BLOCK));
		b.walls(-11, -3, -11, 11, 0, 11, b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS)));
		b.fill(-10, -3, -10, 10, 0, 10, b.open());
		for (int k = 0; k < 4; k++) {
			b.fill(-1, -k - 1, -10 + k, 1, -k - 1, -10 + k, stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
		}
		b.clear(-1, -3, -11, 1, 0, -11);
		b.cylinder(0, 3, 2.5, -3, -3, s(Blocks.STONE_BRICKS), true);
		b.disc(0, -4, 3, 1.5, s(Blocks.WATER));
		for (int x = -8; x <= 8; x += 2) {
			b.set(x, -3, 9, Blocks.POPPY);
		}
		b.set(-6, -3, 0, stairs(Blocks.OAK_STAIRS, Direction.WEST, false));
		b.set(6, -3, 0, stairs(Blocks.OAK_STAIRS, Direction.EAST, false));
		b.chest(8, -3, -6, Direction.WEST, "farm");
		b.chest(-8, -3, -6, Direction.EAST, "library");
		b.keeper(4, -3, 6, "peacock");
	}

	/** The gatehouse of a fallen castle, its towers still standing guard over nothing. */
	static void castleGate(DeepBuilder b) {
		site(b, -16, -12, 16, 12, 0, 22);
		DeepBuilder.Paint stone = b.ruin(b.worn(s(Blocks.STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS)), 0.08);
		for (int side = -1; side <= 1; side += 2) {
			b.cylinder(side * 7, 0, 4.0, 1, 14, s(Blocks.STONE_BRICKS), true);
			b.disc(side * 7, 10, 0, 3.0, s(Blocks.SPRUCE_PLANKS));
			for (int y = 1; y <= 10; y++) {
				b.set(side * 7 + 3, y, 0, facing(Blocks.LADDER, Direction.WEST));
			}
			b.clear(side * 7, 1, -4, side * 7, 2, -4);
			b.chest(side * 7 - 1, 11, 1, Direction.EAST, side < 0 ? "fortress" : "common");
			b.walls(side * 15, 1, -1, side * 11, 6, 1, stone);
		}
		b.fill(-3, 1, -1, 3, 9, 1, s(Blocks.STONE_BRICKS));
		b.fill(-2, 1, -1, 2, 5, 1, b.open());
		b.fill(-2, 5, -1, 2, 5, -1, joined(Blocks.IRON_BARS, true));
		b.keeper(0, 1, 4, EntityTypes.SKELETON);
		b.keeper(-2, 1, 5, EntityTypes.SKELETON);
	}

	/** A beaver dam across a stream and the beavers' lodge in the pond behind it. */
	static void beaverDam(DeepBuilder b) {
		site(b, 14.5, 8);
		b.fill(-9, -2, 0, 9, 0, 10, s(Blocks.WATER));
		b.fill(-9, -3, -1, 9, -3, 11, s(Blocks.DIRT));
		b.fill(-10, 1, -1, 10, 1, -1, axis(Blocks.OAK_LOG, Direction.Axis.X));
		b.fill(-10, -2, -1, 10, 0, -1, axis(Blocks.SPRUCE_LOG, Direction.Axis.X));
		b.sphere(0, 0, 6, 3.5, b.mix(log(Blocks.OAK_LOG), log(Blocks.SPRUCE_LOG), s(Blocks.MUD)), true, 0);
		b.chest(0, 0, 6, Direction.NORTH, "cabin");
		b.keeper(-5, 1, -3, "beaver");
		b.keeper(5, 1, -3, "beaver");
		b.keeper(6, 1, -5, "otter");
	}

	/** A honey farm: rows of hives among the flowers, the beekeeper's shed and the honey store. */
	static void honeyFarm(DeepBuilder b) {
		site(b, -16, -14, 16, 14, 0, 8);
		Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM};
		for (int x = -12; x <= 12; x++) {
			for (int z = -2; z <= 12; z++) {
				if (b.chance(x, 1, z, 130, 0.35)) {
					b.set(x, 1, z, b.pick(x, 1, z, 131, flowers));
				}
			}
		}
		for (int x = -10; x <= 10; x += 4) {
			for (int z = 2; z <= 10; z += 4) {
				b.set(x, 1, z, Blocks.OAK_FENCE);
				b.set(x, 2, z, facing(Blocks.BEEHIVE, Direction.SOUTH).setValue(BlockStateProperties.LEVEL_HONEY, 5));
			}
		}
		LandParts.cottage(b, -6, -12, 6, -5, 3, DeepBuilder.p(s(Blocks.OAK_PLANKS)), log(Blocks.OAK_LOG), s(Blocks.OAK_PLANKS),
				Blocks.OAK_STAIRS, s(Blocks.OAK_PLANKS));
		b.fill(-5, 1, -6, -2, 1, -6, s(Blocks.HONEY_BLOCK));
		b.chest(5, 1, -6, Direction.WEST, "farm");
		b.barrel(-5, 1, -11, Direction.UP, "farm");
		b.keeper(0, 3, 6, EntityTypes.BEE);
		b.keeper(4, 3, 8, EntityTypes.BEE);
	}

	/** A barrow: a grassy burial mound, a stone passage in, and the restless dead in the chamber. */
	static void barrow(DeepBuilder b) {
		site(b, 14.5, 4);
		b.ellipsoid(0, 0, 2, 11.0, 6.0, 10.0, (x, y, z) -> y > 0 && b.get(x, y + 1, z).isAir() ? s(Blocks.GRASS_BLOCK) : s(Blocks.DIRT), false, 0);
		b.fill(-1, 1, -9, 1, 1, -9, s(Blocks.MOSSY_COBBLESTONE));
		b.fill(-2, 0, -9, 2, 4, 6, b.worn(s(Blocks.COBBLESTONE), s(Blocks.MOSSY_COBBLESTONE)));
		b.clear(-1, 1, -9, 1, 2, 5);
		b.fill(-5, 0, 2, 5, 4, 8, b.worn(s(Blocks.COBBLESTONE), s(Blocks.MOSSY_COBBLESTONE)));
		b.clear(-4, 1, 3, 4, 3, 7);
		b.spawner(0, 1, 6, EntityTypes.ZOMBIE);
		b.chest(-3, 1, 7, Direction.EAST, "crypt");
		b.chest(3, 1, 7, Direction.WEST, "tomb");
		b.set(0, 1, 4, head(Blocks.SKELETON_SKULL, 0));
		b.stand(-3, 1, 4, Direction.EAST, new ItemStack(Items.GOLDEN_HELMET), new ItemStack(Items.CHAINMAIL_CHESTPLATE), ItemStack.EMPTY,
				ItemStack.EMPTY, new ItemStack(Items.IRON_SWORD));
	}
}
