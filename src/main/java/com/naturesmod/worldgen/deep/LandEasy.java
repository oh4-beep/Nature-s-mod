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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The easy finds of the Overworld: camps, cabins, wells, shrines and lookouts, a little loot in each. */
final class LandEasy {
	private static final String[] COLOURS = {"red", "blue", "green", "yellow", "white", "orange", "purple", "brown", "cyan", "light_blue"};

	private LandEasy() {
	}

	private static String colour(DeepBuilder b) {
		return COLOURS[b.layout.nextInt(COLOURS.length)];
	}

	private static BlockState leaves(Block leaves) {
		return s(leaves).setValue(BlockStateProperties.PERSISTENT, true);
	}

	/** A small tree: a trunk and a round crown of leaves that never decay. */
	private static void tree(DeepBuilder b, int x, int z, int height, Block log, Block leaves) {
		b.pillar(x, z, 1, height, log(log));
		b.sphere(x, height + 1, z, 2.3, (px, py, pz) -> b.chance(px, py, pz, 71, 0.85) ? leaves(leaves) : b.open(), false, -1);
		b.set(x, height, z, log(log));
	}

	/** A campfire with logs to sit on round it. */
	private static void fireplace(DeepBuilder b, int x, int z, boolean lit) {
		b.set(x, 1, z, s(Blocks.CAMPFIRE).setValue(BlockStateProperties.LIT, lit));
		b.fill(x - 1, 1, z - 2, x + 1, 1, z - 2, axis(Blocks.SPRUCE_LOG, Direction.Axis.X));
		b.set(x - 2, 1, z, log(Blocks.OAK_LOG));
		b.set(x + 2, 1, z, log(Blocks.OAK_LOG));
	}

	/** Two tents round a cold campfire, abandoned in a hurry. */
	static void abandonedCamp(DeepBuilder b) {
		site(b, 8.5, 8);
		LandParts.tent(b, -4, 1, 5, wool(colour(b)));
		LandParts.tent(b, 4, 1, 5, wool(colour(b)));
		fireplace(b, 0, -2, false);
		b.set(-4, 1, 3, carpet("red"));
		b.chest(-4, 1, 4, Direction.NORTH, "camp");
		b.barrel(4, 1, 4, Direction.UP, "camp");
		b.set(1, 1, -5, Blocks.OAK_FENCE);
		b.set(1, 2, -5, lantern(false, false));
		b.set(-1, 1, -5, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
	}

	/** A log cabin in the woods, its owner out hunting, with a woodpile and a stone chimney. */
	static void huntersCabin(DeepBuilder b) {
		site(b, -7, -6, 7, 6, 0, 12);
		LandParts.cottage(b, -4, -3, 4, 3, 4, DeepBuilder.p(s(Blocks.SPRUCE_PLANKS)), log(Blocks.SPRUCE_LOG), s(Blocks.SPRUCE_PLANKS),
				Blocks.SPRUCE_STAIRS, s(Blocks.SPRUCE_PLANKS));
		b.bed(-3, 1, 1, Direction.SOUTH, vanilla("brown_bed"));
		b.chest(3, 1, 2, Direction.WEST, "cabin");
		b.set(3, 1, -2, facing(Blocks.SMOKER, Direction.WEST));
		b.set(-3, 1, -2, Blocks.CRAFTING_TABLE);
		b.set(0, 4, 0, lantern(false, true));
		b.frame(0, 2, 2, Direction.NORTH, Items.BOW);
		b.set(-1, 1, 2, carpet("brown"));
		// The chimney, and the woodpile against the east wall.
		b.pillar(5, 0, 1, 8, s(Blocks.COBBLESTONE));
		b.fill(5, 1, -2, 6, 2, -1, axis(Blocks.SPRUCE_LOG, Direction.Axis.X));
		b.set(-5, 1, -1, s(Blocks.COMPOSTER));
	}

	/** An old stone well; what people threw in for luck is still at the bottom. */
	static void wishingWell(DeepBuilder b) {
		site(b, 6.5, 8);
		DeepBuilder.Paint stone = b.worn(s(Blocks.COBBLESTONE), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.STONE_BRICKS));
		b.disc(0, 0, 0, 4.5, b.mix(s(Blocks.COBBLESTONE), s(Blocks.GRAVEL), s(Blocks.DIRT_PATH)));
		b.cylinder(0, 0, 2.5, -5, 1, stone, true);
		b.fill(-1, -5, -1, 1, -5, 1, s(Blocks.COBBLESTONE));
		b.fill(-1, -4, -1, 1, 0, 1, s(Blocks.WATER));
		b.chest(0, -4, 0, Direction.NORTH, "wishing");
		for (int x = -2; x <= 2; x += 4) {
			b.pillar(x, 0, 2, 3, s(Blocks.OAK_FENCE));
		}
		b.fill(-2, 4, -1, 2, 4, 1, slab(Blocks.OAK_SLAB, false));
		b.set(0, 3, 0, lantern(false, true));
		b.set(2, 1, 2, facing(Blocks.BARREL, Direction.UP));
	}

	/** A ring of standing stones, older than anyone remembers, round an altar. */
	static void stoneCircle(DeepBuilder b) {
		site(b, 9.5, 8);
		DeepBuilder.Paint stone = b.mix(s(Blocks.STONE), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.ANDESITE), s(Blocks.STONE));
		for (int i = 0; i < 9; i++) {
			double angle = i * Math.PI * 2.0 / 9.0;
			int x = (int) Math.round(Math.cos(angle) * 7.0);
			int z = (int) Math.round(Math.sin(angle) * 7.0);
			int height = 2 + b.layout.nextInt(3);
			b.pillar(x, z, 1, height, stone);
			if (b.layout.nextInt(3) == 0) {
				b.set(x, height + 1, z, Blocks.MOSS_CARPET);
			}
		}
		b.set(0, 1, 0, Blocks.CHISELED_STONE_BRICKS);
		b.set(0, 2, 0, s(vanilla("white_candle")).setValue(BlockStateProperties.CANDLES, 3).setValue(BlockStateProperties.LIT, true));
		b.fill(-1, 0, -1, 1, 0, 1, s(Blocks.MOSSY_STONE_BRICKS));
		b.chest(0, 1, 1, Direction.SOUTH, "common");
	}

	/** A trader's cart that lost a wheel, its cargo spilled along the road. */
	static void wreckedCart(DeepBuilder b) {
		site(b, 7.5, 8);
		b.fill(-1, 1, -2, 1, 1, 2, s(Blocks.SPRUCE_PLANKS));
		b.walls(-1, 2, -2, 1, 2, 2, s(Blocks.SPRUCE_SLAB));
		b.chest(0, 2, 0, Direction.NORTH, "common");
		b.set(-2, 1, -1, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
		b.set(-2, 1, 1, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
		b.set(2, 1, -1, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
		// The lost wheel, and the shafts lying in the road.
		b.set(4, 1, 3, log(Blocks.DARK_OAK_LOG));
		b.fill(0, 1, -3, 0, 1, -5, axis(Blocks.SPRUCE_LOG, Direction.Axis.Z));
		b.barrel(3, 1, -2, Direction.UP, "common");
		b.barrel(-3, 1, 2, Direction.EAST, "common");
		b.set(-3, 1, -2, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
		b.set(2, 1, 4, Blocks.BONE_BLOCK);
		b.set(3, 1, 5, s(Blocks.SKELETON_SKULL));
	}

	/** A farmer's field of wheat and carrots, watched over by a scarecrow. */
	static void scarecrowField(DeepBuilder b) {
		site(b, -9, -8, 9, 8, 0, 6);
		LandParts.field(b, -8, -6, -2, 6, Blocks.WHEAT);
		LandParts.field(b, 2, -6, 8, 6, Blocks.CARROTS);
		b.fill(-1, 0, -7, 1, 0, 7, s(Blocks.DIRT_PATH));
		b.set(0, 1, 0, Blocks.OAK_FENCE);
		b.set(0, 2, 0, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
		b.set(0, 3, 0, facing(Blocks.CARVED_PUMPKIN, Direction.NORTH));
		b.set(-1, 2, 0, joined(Blocks.OAK_FENCE, true));
		b.set(1, 2, 0, joined(Blocks.OAK_FENCE, true));
		b.barrel(0, 1, -7, Direction.UP, "farm");
		b.set(1, 1, -7, s(Blocks.COMPOSTER));
		b.set(-1, 1, -7, s(Blocks.HAY_BLOCK));
	}

	/** Beehives hanging in two trees over a meadow of flowers, and the keeper's bench and honey. */
	static void beekeeperGrove(DeepBuilder b) {
		site(b, 8.5, 10);
		Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.AZURE_BLUET};
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				if (x * x + z * z < 50 && b.chance(x, 1, z, 72, 0.35)) {
					b.set(x, 1, z, b.pick(x, 1, z, 73, flowers));
				}
			}
		}
		tree(b, -4, 2, 5, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		tree(b, 4, -2, 5, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
		b.set(-4, 3, 1, facing(Blocks.BEEHIVE, Direction.NORTH).setValue(BlockStateProperties.LEVEL_HONEY, 5));
		b.set(4, 3, -3, facing(Blocks.BEEHIVE, Direction.NORTH).setValue(BlockStateProperties.LEVEL_HONEY, 3));
		b.set(-1, 1, 4, stairs(Blocks.OAK_STAIRS, Direction.SOUTH, false));
		b.set(0, 1, 4, stairs(Blocks.OAK_STAIRS, Direction.SOUTH, false));
		b.chest(1, 1, 4, Direction.NORTH, "farm");
		b.keeper(-2, 2, 0, EntityTypes.BEE);
		b.keeper(2, 2, 0, EntityTypes.BEE);
	}

	/** A pool of water in the sand, palms leaning over it, and a camel resting in their shade. */
	static void oasis(DeepBuilder b) {
		site(b, 9.5, 12);
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d <= 4.2) {
					b.set(x, 0, z, Blocks.WATER);
					if (d <= 2.6) {
						b.set(x, -1, z, Blocks.WATER);
					}
				} else if (d <= 5.4) {
					b.set(x, 0, z, b.chance(x, 0, z, 74, 0.5) ? s(Blocks.GRASS_BLOCK) : s(Blocks.SAND));
					if (b.chance(x, 1, z, 75, 0.3)) {
						b.set(x, 1, z, Blocks.SHORT_GRASS);
					}
				}
			}
		}
		for (int[] palm : new int[][] {{-6, -3, 1}, {5, 4, -1}, {2, -7, 1}}) {
			int x = palm[0];
			int z = palm[1];
			for (int y = 1; y <= 6; y++) {
				b.set(x + (y > 4 ? palm[2] : 0), y, z, log(Blocks.JUNGLE_LOG));
			}
			int tx = x + palm[2];
			for (Direction side : Direction.Plane.HORIZONTAL) {
				for (int k = 1; k <= 3; k++) {
					b.set(tx + side.getStepX() * k, 7 - (k == 3 ? 1 : 0), z + side.getStepZ() * k, leaves(Blocks.JUNGLE_LEAVES));
				}
			}
			b.set(tx, 7, z, leaves(Blocks.JUNGLE_LEAVES));
		}
		b.set(-4, 1, 4, Blocks.SUGAR_CANE);
		b.set(-4, 2, 4, Blocks.SUGAR_CANE);
		b.set(4, 1, -4, Blocks.DEAD_BUSH);
		b.chest(6, 0, -2, Direction.WEST, "desert");
		b.keeper(-6, 1, 1, EntityTypes.CAMEL);
	}

	/** A nomad's striped tent, its loom and its cooking pot, with a camel and a llama tethered outside. */
	static void nomadTent(DeepBuilder b) {
		site(b, 8.5, 8);
		LandParts.tent(b, 0, -3, 3, wool(colour(b)));
		b.fill(-1, 1, -2, 1, 1, 2, carpet("red"));
		b.chest(0, 1, 2, Direction.NORTH, "desert");
		b.set(-1, 1, 2, Blocks.LOOM);
		b.set(1, 1, 2, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 2));
		fireplace(b, 0, -6, true);
		b.set(4, 1, 0, Blocks.OAK_FENCE);
		b.set(4, 1, 1, Blocks.OAK_FENCE);
		b.keeper(5, 1, -2, EntityTypes.CAMEL);
		b.keeper(-5, 1, 1, EntityTypes.LLAMA);
		b.barrel(-3, 1, -4, Direction.UP, "desert");
	}

	/** A little shrine of mossy stone in the jungle, a monkey living on its roof. */
	static void jungleShrine(DeepBuilder b) {
		site(b, 7.5, 10);
		DeepBuilder.Paint moss = b.worn(s(Blocks.MOSSY_COBBLESTONE), s(Blocks.COBBLESTONE), s(Blocks.MOSSY_STONE_BRICKS));
		b.fill(-3, 0, -3, 3, 0, 3, moss);
		b.fill(-2, 1, -2, 2, 2, 2, moss);
		b.fill(-1, 3, -1, 1, 3, 1, s(Blocks.MOSSY_STONE_BRICKS));
		b.set(0, 4, 0, Blocks.CHISELED_STONE_BRICKS);
		b.clear(-1, 1, -1, 1, 2, 1);
		b.clear(0, 1, -2, 0, 2, -2);
		b.chest(0, 1, 1, Direction.NORTH, "jungle");
		b.set(-1, 1, 1, s(Blocks.FLOWER_POT));
		for (int x = -2; x <= 2; x += 4) {
			for (int y = 1; y <= 2; y++) {
				b.set(x, y, -3, vine(Direction.SOUTH));
				b.set(x, y, 3, vine(Direction.NORTH));
			}
		}
		b.set(-2, 1, -3, s(Blocks.TORCH));
		b.keeper(0, 5, 0, "monkey");
	}

	/** A hut of bamboo on stilts, with a ladder up to its door. */
	static void bambooHut(DeepBuilder b) {
		site(b, 7.5, 12);
		for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			b.pillar(c[0], c[1], 1, 3, axis(Blocks.BAMBOO_BLOCK, Direction.Axis.Y));
		}
		b.fill(-3, 3, -3, 3, 3, 3, s(Blocks.BAMBOO_PLANKS));
		b.walls(-2, 4, -2, 2, 6, 2, axis(Blocks.BAMBOO_BLOCK, Direction.Axis.Y));
		b.clear(0, 4, -2, 0, 5, -2);
		b.set(-2, 5, 0, joined(Blocks.GLASS_PANE, false));
		b.set(2, 5, 0, joined(Blocks.GLASS_PANE, false));
		LandParts.roof(b, -2, -2, 2, 2, 7, Blocks.BAMBOO_STAIRS, s(Blocks.BAMBOO_PLANKS));
		b.pillar(0, -3, 1, 2, axis(Blocks.BAMBOO_BLOCK, Direction.Axis.Y));
		for (int y = 1; y <= 3; y++) {
			b.set(0, y, -4, facing(Blocks.LADDER, Direction.NORTH));
		}
		b.chest(1, 4, 1, Direction.WEST, "jungle");
		b.set(-1, 4, 1, carpet("green"));
		b.set(0, 6, 0, lantern(false, true));
	}

	/** A fort of packed snow, kept by two snow golems who throw snowballs at anything that comes near. */
	static void snowFort(DeepBuilder b) {
		site(b, 8.5, 8);
		b.walls(-5, 1, -5, 5, 2, 5, s(Blocks.SNOW_BLOCK));
		for (int i = -5; i <= 5; i += 2) {
			b.set(i, 3, -5, Blocks.SNOW_BLOCK);
			b.set(i, 3, 5, Blocks.SNOW_BLOCK);
			b.set(-5, 3, i, Blocks.SNOW_BLOCK);
			b.set(5, 3, i, Blocks.SNOW_BLOCK);
		}
		b.clear(0, 1, -5, 0, 2, -5);
		for (int[] c : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
			b.pillar(c[0], c[1], 1, 4, s(Blocks.PACKED_ICE));
		}
		b.chest(0, 1, 4, Direction.NORTH, "explorer");
		b.set(-3, 1, 3, s(Blocks.SNOW).setValue(BlockStateProperties.LAYERS, 3));
		b.set(3, 1, 3, s(Blocks.SNOW).setValue(BlockStateProperties.LAYERS, 2));
		b.keeper(-2, 1, 0, EntityTypes.SNOW_GOLEM);
		b.keeper(2, 1, 0, EntityTypes.SNOW_GOLEM);
	}

	/** An explorer frozen into the ice where the storm caught them, their sled and supplies beside them. */
	static void frozenExplorer(DeepBuilder b) {
		site(b, 6.5, 8);
		b.fill(-1, 1, -1, 1, 3, 1, s(Blocks.ICE));
		b.clear(0, 1, 0, 0, 2, 0);
		b.stand(0, 1, 0, Direction.SOUTH, new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.LEATHER_CHESTPLATE),
				new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.COMPASS));
		b.fill(2, 1, 2, 3, 1, 5, slab(Blocks.SPRUCE_SLAB, false));
		b.chest(3, 2, 4, Direction.WEST, "explorer");
		b.set(2, 2, 3, s(Blocks.BARREL));
		b.set(-3, 1, 2, s(Blocks.CAMPFIRE).setValue(BlockStateProperties.LIT, false));
		b.set(-3, 1, -2, lantern(false, false));
		b.set(-2, 1, 3, s(Blocks.SNOW).setValue(BlockStateProperties.LAYERS, 2));
	}

	/** A lookout of acacia on tall stilts over the grass, with a roof against the sun. */
	static void savannaLookout(DeepBuilder b) {
		site(b, 6.5, 14);
		for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			b.pillar(c[0], c[1], 1, 6, log(Blocks.ACACIA_LOG));
			b.pillar(c[0], c[1], 8, 10, s(Blocks.ACACIA_FENCE));
		}
		b.fill(-3, 7, -3, 3, 7, 3, s(Blocks.ACACIA_PLANKS));
		b.walls(-3, 8, -3, 3, 8, 3, joined(Blocks.ACACIA_FENCE, true));
		b.fill(-3, 11, -3, 3, 11, 3, slab(Blocks.ACACIA_SLAB, false));
		b.pillar(0, -2, 1, 6, log(Blocks.ACACIA_LOG));
		for (int y = 1; y <= 7; y++) {
			b.set(0, y, -3, facing(Blocks.LADDER, Direction.NORTH));
		}
		b.clear(0, 8, -3, 0, 8, -3);
		b.chest(2, 8, 2, Direction.WEST, "savanna");
		b.set(-2, 8, 2, Blocks.BARREL);
	}

	/** A towering termite mound of clay, hollow inside, and still busy. */
	static void termiteMound(DeepBuilder b) {
		site(b, 6.5, 12);
		DeepBuilder.Paint clay = b.mix(s(Blocks.TERRACOTTA), s(vanilla("orange_terracotta")), s(vanilla("brown_terracotta")),
				s(Blocks.TERRACOTTA));
		b.ellipsoid(0, 0, 0, 3.6, 8.0, 3.6, clay, false, 0);
		b.ellipsoid(2, 0, 1, 2.0, 4.5, 2.0, clay, false, 0);
		b.clear(-1, 1, -1, 1, 2, 1);
		b.clear(0, 1, -3, 0, 1, -2);
		b.chest(0, 1, 1, Direction.NORTH, "savanna");
		b.spawner(0, 3, 0, EntityTypes.SILVERFISH);
	}

	/** The mouth of an old mine, a tunnel sloping down to where the last cart was left. */
	static void mineEntrance(DeepBuilder b) {
		site(b, 7.5, 6);
		for (int k = 0; k <= 8; k++) {
			int z = -5 + k;
			b.fill(-1, -k - 1, z, 1, -k - 1, z, s(Blocks.OAK_PLANKS));
			b.clear(-1, -k, z, 1, -k + 2, z);
			if (k % 3 == 0) {
				b.set(-1, -k, z, Blocks.OAK_FENCE);
				b.set(-1, -k + 1, z, Blocks.OAK_FENCE);
				b.set(1, -k, z, Blocks.OAK_FENCE);
				b.set(1, -k + 1, z, Blocks.OAK_FENCE);
				b.fill(-1, -k + 2, z, 1, -k + 2, z, s(Blocks.OAK_PLANKS));
			}
		}
		// The frame over the mouth, and the chamber at the bottom.
		b.pillar(-2, -5, 1, 3, log(Blocks.OAK_LOG));
		b.pillar(2, -5, 1, 3, log(Blocks.OAK_LOG));
		b.fill(-2, 4, -5, 2, 4, -5, axis(Blocks.OAK_LOG, Direction.Axis.X));
		b.fill(-3, -10, 4, 3, -10, 9, s(Blocks.OAK_PLANKS));
		b.clear(-3, -9, 4, 3, -6, 9);
		b.fill(0, -9, 4, 0, -9, 9, s(Blocks.RAIL));
		b.chest(-3, -9, 8, Direction.EAST, "mine");
		b.set(3, -9, 8, Blocks.BARREL);
		b.set(2, -9, 5, Blocks.TNT);
		b.set(0, -6, 6, lantern(false, true));
		for (int[] ore : new int[][] {{-4, -8, 6}, {4, -7, 7}, {-3, -6, 10}, {2, -8, 10}}) {
			b.set(ore[0], ore[1], ore[2], b.pick(ore[0], ore[1], ore[2], 76, s(Blocks.IRON_ORE), s(Blocks.COAL_ORE), s(Blocks.GOLD_ORE)));
		}
	}

	/** A shrine of mud brick in the bog: a cauldron, a brewing stand and the frogs that keep it. */
	static void bogShrine(DeepBuilder b) {
		site(b, 7.5, 8);
		b.fill(-4, 0, -4, 4, 0, 4, b.mix(s(Blocks.MUD_BRICKS), s(Blocks.PACKED_MUD), s(Blocks.MUD_BRICKS)));
		b.walls(-4, 1, -4, 4, 1, 4, b.ruin(DeepBuilder.p(s(Blocks.MOSSY_STONE_BRICKS)), 0.35));
		b.set(0, 1, 0, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		b.set(0, 1, 2, Blocks.BREWING_STAND);
		b.chest(-2, 1, 2, Direction.EAST, "swamp");
		for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
			b.pillar(c[0], c[1], 1, 3, s(Blocks.DARK_OAK_FENCE));
			b.set(c[0], 4, c[1], lantern(true, false));
		}
		b.set(2, 1, -2, Blocks.RED_MUSHROOM);
		b.set(-2, 1, -2, Blocks.BROWN_MUSHROOM);
		b.set(3, 1, 2, head(Blocks.SKELETON_SKULL, 8));
		b.keeper(2, 1, 1, EntityTypes.FROG);
		b.keeper(-1, 1, -2, EntityTypes.FROG);
	}

	/** A watchtower of stone, falling down, its last guard's chest still at the top. */
	static void ruinedWatchtower(DeepBuilder b) {
		site(b, 6.5, 16);
		DeepBuilder.Paint stone = b.ruin(b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS)), 0.12);
		LandParts.tower(b, 0, 0, 3.5, 12, 5, stone, s(Blocks.SPRUCE_PLANKS));
		b.chest(-1, 11, 1, Direction.EAST, "common");
		b.set(1, 6, -1, Blocks.BARREL);
		for (int i = 0; i < 6; i++) {
			int x = b.layout.nextInt(11) - 5;
			int z = b.layout.nextInt(11) - 5;
			if (x * x + z * z > 16) {
				b.set(x, 1, z, b.pick(x, 1, z, 77, s(Blocks.COBBLESTONE), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.STONE_BRICKS)));
			}
		}
	}

	/** A picnic blanket under a tree: a cake, a basket and flowers in a pot, the picnickers nowhere to be seen. */
	static void picnicSpot(DeepBuilder b) {
		site(b, 6.5, 10);
		for (int x = -2; x <= 1; x++) {
			for (int z = -2; z <= 1; z++) {
				b.set(x, 1, z, carpet((x + z & 1) == 0 ? "red" : "white"));
			}
		}
		b.set(0, 1, 0, Blocks.CAKE);
		b.set(-1, 1, -1, s(Blocks.POTTED_POPPY));
		b.barrel(1, 1, -2, Direction.UP, "farm");
		tree(b, 4, 3, 5, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		b.set(-3, 1, 2, Blocks.DANDELION);
		b.set(2, 1, 2, Blocks.CORNFLOWER);
	}

	/** A carved totem pole with its wings spread, and a tipi and fire beside it. */
	static void totemPole(DeepBuilder b) {
		site(b, 7.5, 12);
		BlockState[] bands = {s(vanilla("red_terracotta")), s(vanilla("yellow_terracotta")), s(vanilla("blue_terracotta"))};
		for (int y = 1; y <= 8; y++) {
			b.set(0, y, 0, y % 2 == 0 ? bands[(y / 2) % 3] : log(Blocks.STRIPPED_SPRUCE_LOG));
		}
		b.set(0, 9, 0, facing(Blocks.CARVED_PUMPKIN, Direction.NORTH));
		b.fill(-2, 8, 0, 2, 8, 0, joined(Blocks.SPRUCE_FENCE, true));
		b.set(0, 8, 0, bands[0]);
		LandParts.tent(b, -4, 2, 6, wool("brown"));
		b.chest(-4, 1, 5, Direction.NORTH, "cabin");
		b.set(-4, 1, 3, carpet("brown"));
		b.set(3, 1, 3, s(Blocks.CAMPFIRE));
	}
}
