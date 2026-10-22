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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * The great dungeons of the Overworld: a warlord's fortress, a necromancer's crypt, a lost pyramid,
 * a jungle ziggurat, a citadel of ice, a haunted manor, an outlaw fort, a colosseum, a tower of
 * trials, a deep mine, an elder treehouse and a spire of blazes. Each is guarded by spawners and
 * keepers and holds a treasure worth the fight. Keepers are placed last, since each one takes a
 * turn from the layout's random sequence in the one chunk that holds it.
 */
final class LandHard {
	private LandHard() {
	}

	private static BlockState leaves(Block leaves) {
		return s(leaves).setValue(BlockStateProperties.PERSISTENT, true);
	}

	/** Battlements: every other block round a rectangle at y. */
	private static void battlements(DeepBuilder b, int x0, int y, int z0, int x1, int z1, BlockState state) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if ((x == x0 || x == x1 || z == z0 || z == z1) && Math.floorMod(x + z, 2) == 0) {
					b.set(x, y, z, state);
				}
			}
		}
	}

	/** Battlements round a circle at y: every other block of its outermost ring. */
	private static void ringBattlements(DeepBuilder b, int cx, int cz, double radius, int y, BlockState state) {
		int r = (int) Math.ceil(radius);
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
				if (d <= radius && d > radius - 1.0 && Math.floorMod(x + z, 2) == 0) {
					b.set(x, y, z, state);
				}
			}
		}
	}

	/** A ring of fence posts round the edge of a disc at y. */
	private static void ringFence(DeepBuilder b, int cx, int cz, double radius, int y, BlockState state) {
		int r = (int) Math.ceil(radius);
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
				if (d <= radius && d > radius - 1.0) {
					b.set(x, y, z, state);
				}
			}
		}
	}

	private static void ladder(DeepBuilder b, int x, int z, int y0, int y1, Direction facing) {
		for (int y = y0; y <= y1; y++) {
			b.set(x, y, z, facing(Blocks.LADDER, facing));
		}
	}

	/** A straw dummy on a post, to practise on. */
	private static void dummy(DeepBuilder b, int x, int z) {
		b.set(x, 1, z, Blocks.OAK_FENCE);
		b.set(x, 2, z, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
		b.set(x, 3, z, facing(Blocks.CARVED_PUMPKIN, Direction.NORTH));
	}

	/** Cobwebs scattered round a point. */
	private static void webs(DeepBuilder b, int x0, int y0, int z0, int radius, int count, int salt) {
		for (int i = 0; i < count; i++) {
			int x = x0 + (int) (b.rand(i, y0, salt, 96) * (radius * 2 + 1)) - radius;
			int z = z0 + (int) (b.rand(i, y0, salt, 97) * (radius * 2 + 1)) - radius;
			int y = y0 + (int) (b.rand(i, y0, salt, 98) * 3);
			if (b.get(x, y, z).isAir()) {
				b.set(x, y, z, Blocks.COBWEB);
			}
		}
	}

	// --- a warlord's fortress ----------------------------------------------------------------------

	/** A round corner tower on the curtain walls: solid below the wall-walk, a room above it, a roof behind battlements. */
	private static void cornerTower(DeepBuilder b, int cx, int cz, DeepBuilder.Paint wall) {
		int sx = Integer.signum(cx);
		int sz = Integer.signum(cz);
		b.cylinder(cx, cz, 4.5, 1, 8, wall, false);
		b.cylinder(cx, cz, 4.5, 9, 14, wall, true);
		b.cylinder(cx, cz, 3.5, 9, 14, b.open(), false);
		b.disc(cx, 15, cz, 4.5, s(Blocks.STONE_BRICKS));
		ringBattlements(b, cx, cz, 4.5, 16, s(Blocks.STONE_BRICKS));
		// Doors in from both wall-walks, and a ladder up to the roof.
		b.clear(cx - 4 * sx, 9, Math.min(cz, cz - sz), cx - 4 * sx, 10, Math.max(cz, cz - sz));
		b.clear(Math.min(cx, cx - sx), 9, cz - 4 * sz, Math.max(cx, cx - sx), 10, cz - 4 * sz);
		ladder(b, cx, cz + 3 * sz, 9, 15, sz > 0 ? Direction.NORTH : Direction.SOUTH);
	}

	/**
	 * A warlord's fortress: curtain walls and corner towers round a courtyard with barracks, a
	 * smithy and a ravager's pen, and the keep, where the warlord waits on his throne below his
	 * armory and his treasury.
	 */
	static void warlordFortress(DeepBuilder b) {
		site(b, -26, -26, 26, 26, 0, 30);
		DeepBuilder.Paint wall = b.worn(s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS));
		BlockState brick = s(Blocks.STONE_BRICKS);
		// The curtain walls, two thick, walked along the top behind battlements.
		b.walls(-22, 1, -22, 22, 8, 22, wall);
		b.walls(-21, 1, -21, 21, 8, 21, wall);
		battlements(b, -22, 9, -22, 22, 22, brick);
		for (int[] c : new int[][] {{-22, -22}, {22, -22}, {-22, 22}, {22, 22}}) {
			cornerTower(b, c[0], c[1], wall);
		}
		// The gate, its portcullis raised, and stairs up onto the walls either side of it.
		b.clear(-2, 1, -22, 2, 5, -21);
		b.fill(-2, 5, -22, 2, 5, -22, joined(Blocks.IRON_BARS, true));
		for (int x = -4; x <= 4; x += 8) {
			b.fill(x, 5, -23, x, 7, -23, wool("red"));
		}
		for (int k = 0; k < 8; k++) {
			if (k > 0) {
				b.pillar(5 + k, -20, 1, k, wall);
				b.pillar(-5 - k, -20, 1, k, wall);
			}
			b.set(5 + k, 1 + k, -20, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, false));
			b.set(-5 - k, 1 + k, -20, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, false));
		}
		b.fill(-2, 0, -20, 2, 0, 5, s(Blocks.DIRT_PATH));
		// The barracks.
		LandParts.cottage(b, -19, -10, -9, 2, 5, DeepBuilder.p(s(Blocks.SPRUCE_PLANKS)), log(Blocks.SPRUCE_LOG), s(Blocks.SPRUCE_PLANKS),
				Blocks.SPRUCE_STAIRS, s(Blocks.DARK_OAK_PLANKS));
		for (int x = -18; x <= -12; x += 3) {
			b.bed(x, 1, 0, Direction.SOUTH, vanilla("red_bed"));
		}
		b.chest(-10, 1, 1, Direction.WEST, "fortress");
		b.set(-14, 4, -4, lantern(false, true));
		b.set(-14, 5, -4, s(Blocks.SPRUCE_PLANKS));
		// The training yard, the smithy and the ravager's pen.
		for (int x : new int[] {-8, -6, 6, 8}) {
			dummy(b, x, -14);
		}
		b.fill(14, 5, -3, 20, 5, 5, slab(Blocks.SPRUCE_SLAB, false));
		b.pillar(14, -3, 1, 4, log(Blocks.SPRUCE_LOG));
		b.pillar(14, 5, 1, 4, log(Blocks.SPRUCE_LOG));
		b.set(19, 1, -1, facing(Blocks.ANVIL, Direction.WEST));
		b.set(19, 1, 1, s(Blocks.BLAST_FURNACE).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
				.setValue(BlockStateProperties.LIT, true));
		b.set(19, 1, 3, s(Blocks.GRINDSTONE).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
		b.chest(16, 1, 4, Direction.WEST, "fortress");
		LandParts.railing(b, 8, 1, -18, 17, 2, -8, Blocks.SPRUCE_FENCE);
		b.fill(15, 1, -17, 16, 1, -16, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
		// The keep: the great hall below, the armory above it, the treasury at the top.
		LandParts.house(b, -9, 6, 9, 19, 18, wall, s(Blocks.CHISELED_STONE_BRICKS), s(Blocks.POLISHED_ANDESITE));
		b.fill(-8, 6, 7, 8, 6, 18, s(Blocks.SPRUCE_PLANKS));
		b.fill(-8, 12, 7, 8, 12, 18, s(Blocks.SPRUCE_PLANKS));
		b.fill(-9, 18, 6, 9, 18, 19, brick);
		battlements(b, -9, 19, 6, 9, 19, brick);
		b.clear(-1, 1, 6, 1, 3, 6);
		for (int y = 3; y <= 15; y += 6) {
			for (int x = -6; x <= 6; x += 12) {
				b.set(x, y, 19, joined(Blocks.IRON_BARS, true));
				if (y > 3) {
					b.set(x, y, 6, joined(Blocks.IRON_BARS, true));
				}
			}
			for (int z = 10; z <= 15; z += 5) {
				b.set(-9, y, z, joined(Blocks.IRON_BARS, false));
				b.set(9, y, z, joined(Blocks.IRON_BARS, false));
			}
		}
		for (int k = 0; k <= 5; k++) {
			if (k > 0) {
				b.pillar(-8, 8 + k, 1, k, brick);
				b.pillar(8, 8 + k, 7, 6 + k, brick);
			}
			b.set(-8, 1 + k, 8 + k, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
			b.set(8, 7 + k, 8 + k, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
		}
		b.clear(-8, 6, 11, -8, 6, 12);
		b.clear(8, 12, 11, 8, 12, 12);
		ladder(b, -8, 18, 13, 18, Direction.NORTH);
		// The great hall: a long table, a red carpet, and the throne.
		b.fill(-1, 1, 9, 1, 1, 15, slab(Blocks.DARK_OAK_SLAB, true));
		for (int z = 10; z <= 14; z += 2) {
			LandParts.seat(b, -2, z, Blocks.DARK_OAK_STAIRS, Direction.WEST);
			LandParts.seat(b, 2, z, Blocks.DARK_OAK_STAIRS, Direction.EAST);
		}
		b.fill(-1, 1, 7, 1, 1, 8, carpet("red"));
		b.fill(-1, 1, 16, 1, 1, 17, carpet("red"));
		b.set(0, 1, 18, stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
		b.set(-1, 1, 18, Blocks.GOLD_BLOCK);
		b.set(1, 1, 18, Blocks.GOLD_BLOCK);
		for (int x = -3; x <= 3; x += 6) {
			b.fill(x, 2, 18, x, 4, 18, wool("red"));
		}
		for (int z = 10; z <= 15; z += 5) {
			b.set(-4, 5, z, lantern(false, true));
			b.set(4, 5, z, lantern(false, true));
		}
		// The armory.
		b.stand(-5, 7, 14, Direction.EAST, new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
				new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.IRON_SWORD));
		b.stand(-5, 7, 16, Direction.EAST, new ItemStack(Items.CHAINMAIL_HELMET), new ItemStack(Items.CHAINMAIL_CHESTPLATE),
				new ItemStack(Items.CHAINMAIL_LEGGINGS), new ItemStack(Items.CHAINMAIL_BOOTS), new ItemStack(Items.CROSSBOW));
		b.stand(5, 7, 16, Direction.WEST, new ItemStack(Items.IRON_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE), ItemStack.EMPTY,
				ItemStack.EMPTY, new ItemStack(Items.IRON_AXE));
		b.chest(-7, 7, 17, Direction.EAST, "fortress");
		b.chest(6, 7, 18, Direction.NORTH, "fortress");
		b.set(0, 11, 12, lantern(false, true));
		// The treasury.
		b.chest(0, 13, 17, Direction.NORTH, "treasure");
		b.set(-2, 13, 17, Blocks.GOLD_BLOCK);
		b.set(2, 13, 17, Blocks.GOLD_BLOCK);
		b.chest(-6, 13, 16, Direction.EAST, "fortress");
		b.set(0, 17, 12, lantern(false, true));
		// The warlord's banner over the keep.
		b.pillar(0, 12, 19, 24, s(Blocks.SPRUCE_FENCE));
		b.fill(1, 22, 12, 3, 24, 12, wool("red"));
		// The garrison.
		b.keeper(0, 1, 16, EntityTypes.EVOKER);
		b.keeper(-3, 13, 12, EntityTypes.VINDICATOR);
		b.keeper(3, 13, 12, EntityTypes.VINDICATOR);
		b.keeper(0, 1, -5, EntityTypes.VINDICATOR);
		b.keeper(-14, 1, -5, EntityTypes.PILLAGER);
		b.keeper(-6, 19, 15, EntityTypes.PILLAGER);
		b.keeper(6, 19, 15, EntityTypes.PILLAGER);
		b.keeper(-15, 9, -21, EntityTypes.PILLAGER);
		b.keeper(15, 9, 21, EntityTypes.PILLAGER);
		b.keeper(12, 1, -13, EntityTypes.RAVAGER);
	}

	// --- a necromancer's crypt ---------------------------------------------------------------------

	/**
	 * A necromancer's crypt: a mausoleum in a dead garden, a crypt of burial niches below it where
	 * the dead keep rising, and below that the necromancer's sanctum, his altar and his hoard.
	 */
	static void necromancerCrypt(DeepBuilder b) {
		site(b, 18.5, 20);
		DeepBuilder.Paint dark = b.worn(s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), s(Blocks.BLACKSTONE));
		DeepBuilder.Paint crypt = b.worn(s(Blocks.DEEPSLATE_BRICKS), s(Blocks.CRACKED_DEEPSLATE_BRICKS), s(Blocks.DEEPSLATE_TILES));
		// The dead garden: grey earth, gravestones in a ring, soul lanterns on posts.
		b.disc(0, 0, 0, 17.5, b.mix(s(Blocks.COARSE_DIRT), s(Blocks.PODZOL), s(Blocks.GRAVEL), s(Blocks.COARSE_DIRT)));
		for (int i = 0; i < 18; i++) {
			double angle = i * Math.PI * 2 / 18;
			int x = (int) Math.round(Math.cos(angle) * 13);
			int z = (int) Math.round(Math.sin(angle) * 13);
			if (z < -8 && Math.abs(x) < 5) {
				continue;
			}
			b.set(x, 1, z, b.pick(x, 1, z, 91, s(Blocks.STONE_BRICK_WALL), s(Blocks.MOSSY_STONE_BRICK_WALL), s(Blocks.COBBLESTONE_WALL)));
			if (b.chance(x, 2, z, 92, 0.5)) {
				b.set(x + Integer.signum(x), 0, z + Integer.signum(z), Blocks.COARSE_DIRT);
				b.set(x + Integer.signum(x), 1, z + Integer.signum(z), Blocks.DEAD_BUSH);
			}
		}
		for (int[] c : new int[][] {{-11, -11}, {11, -11}, {-11, 11}, {11, 11}}) {
			b.pillar(c[0], c[1], 1, 2, s(Blocks.DARK_OAK_FENCE));
			b.set(c[0], 3, c[1], lantern(true, false));
		}
		// The mausoleum: dark stone under a stepped roof, columns along its front.
		b.fill(-7, 0, -7, 7, 0, 7, s(Blocks.POLISHED_DEEPSLATE));
		b.walls(-7, 1, -7, 7, 8, 7, dark);
		b.clear(-6, 1, -6, 6, 8, 6);
		for (int k = 0; k <= 4; k++) {
			b.fill(-8 + k, 9 + k, -8 + k, 8 - k, 9 + k, 8 - k, dark);
		}
		b.set(0, 14, 0, Blocks.SOUL_CAMPFIRE);
		for (int x : new int[] {-6, -3, 3, 6}) {
			b.pillar(x, -8, 1, 8, s(Blocks.CHISELED_POLISHED_BLACKSTONE));
		}
		b.clear(-1, 1, -7, 1, 4, -7);
		for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
			b.set(c[0], 8, c[1], lantern(true, true));
		}
		b.fill(4, 1, 2, 5, 1, 4, s(Blocks.POLISHED_BLACKSTONE));
		b.fill(-5, 1, 2, -4, 1, 4, s(Blocks.POLISHED_BLACKSTONE));
		b.chest(5, 1, 5, Direction.WEST, "crypt");
		// The stairs down, and the crypt of burial niches where the dead keep rising.
		LandParts.stairway(b, -1, 1, 0, -3, 1, 9, crypt, Blocks.DEEPSLATE_BRICK_STAIRS);
		b.fill(-10, -10, 6, 10, -10, 24, s(Blocks.POLISHED_DEEPSLATE));
		b.walls(-10, -9, 6, 10, -4, 24, crypt);
		b.fill(-10, -3, 6, 10, -3, 24, crypt);
		b.clear(-9, -9, 7, 9, -4, 23);
		b.clear(-1, -9, 6, 1, -7, 6);
		for (int z = 9; z <= 21; z += 3) {
			b.set(-9, -9, z, axis(Blocks.BONE_BLOCK, Direction.Axis.X));
			b.set(-9, -8, z, head(Blocks.SKELETON_SKULL, 12));
			b.set(9, -9, z, axis(Blocks.BONE_BLOCK, Direction.Axis.X));
			b.set(9, -8, z, head(Blocks.SKELETON_SKULL, 4));
		}
		for (int x = -5; x <= 5; x += 10) {
			for (int z = 10; z <= 22; z += 4) {
				b.pillar(x, z, -9, -4, s(Blocks.CHISELED_DEEPSLATE));
			}
		}
		b.spawner(0, -9, 11, EntityTypes.ZOMBIE);
		b.spawner(0, -9, 16, EntityTypes.SKELETON);
		b.chest(-8, -9, 23, Direction.EAST, "crypt");
		b.chest(8, -9, 23, Direction.WEST, "crypt");
		webs(b, 0, -6, 15, 8, 14, 1);
		// Further down, the necromancer's sanctum.
		LandParts.stairway(b, -1, 1, -10, 22, -1, 10, crypt, Blocks.DEEPSLATE_TILE_STAIRS);
		b.fill(-10, -21, -4, 10, -21, 12, s(Blocks.POLISHED_BLACKSTONE));
		b.walls(-10, -20, -4, 10, -14, 12, dark);
		b.fill(-10, -13, -4, 10, -13, 12, dark);
		b.clear(-9, -20, -3, 9, -14, 11);
		b.clear(-1, -20, 12, 1, -18, 12);
		b.fill(-4, -20, -3, 4, -20, -1, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
		b.set(0, -19, -3, Blocks.SOUL_CAMPFIRE);
		b.set(-1, -19, -3, Blocks.CRYING_OBSIDIAN);
		b.set(1, -19, -3, Blocks.CRYING_OBSIDIAN);
		b.set(-1, -18, -3, head(Blocks.WITHER_SKELETON_SKULL, 0));
		b.set(1, -18, -3, head(Blocks.WITHER_SKELETON_SKULL, 0));
		b.chest(0, -19, -1, Direction.SOUTH, "treasure");
		b.chest(-3, -19, -2, Direction.SOUTH, "crypt");
		b.chest(3, -19, -2, Direction.SOUTH, "crypt");
		for (int x = -6; x <= 6; x += 12) {
			b.pike(x, -2, -20, 2, Blocks.DARK_OAK_FENCE);
			for (int z = 0; z <= 8; z += 4) {
				b.pillar(x, z, -20, -14, s(Blocks.CHISELED_POLISHED_BLACKSTONE));
			}
		}
		b.spawner(-7, -20, 7, EntityTypes.SKELETON);
		b.spawner(7, -20, 7, EntityTypes.SKELETON);
		webs(b, 0, -16, 4, 8, 10, 2);
		// The necromancer and his servants.
		b.keeper(0, -20, 2, EntityTypes.EVOKER);
		b.keeper(-3, -20, 5, EntityTypes.SKELETON);
		b.keeper(3, -20, 5, EntityTypes.SKELETON);
		b.keeper(0, -9, 20, EntityTypes.ZOMBIE);
	}

	// --- a lost pyramid ------------------------------------------------------------------------------

	/**
	 * A lost pyramid, forty blocks across: a corridor with a trap, a burial chamber and two tombs
	 * kept by husks, and under the chamber floor, down a hidden shaft, the pharaoh's vault.
	 */
	static void lostPyramid(DeepBuilder b) {
		site(b, -24, -24, 24, 24, 0, 24);
		DeepBuilder.Paint stone = b.worn(s(Blocks.SANDSTONE), s(Blocks.SMOOTH_SANDSTONE), s(Blocks.CUT_SANDSTONE));
		BlockState cut = s(Blocks.CUT_SANDSTONE);
		BlockState orange = s(vanilla("orange_terracotta"));
		BlockState blue = s(vanilla("blue_terracotta"));
		for (int y = 1; y <= 21; y++) {
			int w = 21 - y;
			b.fill(-w, y, -w, w, y, w, stone);
		}
		b.set(0, 21, 0, Blocks.GOLD_BLOCK);
		b.walls(-14, 7, -14, 14, 7, 14, orange);
		// Obelisks at the corners.
		for (int[] c : new int[][] {{-22, -22}, {22, -22}, {-22, 22}, {22, 22}}) {
			b.pillar(c[0], c[1], 1, 7, cut);
			b.set(c[0], 8, c[1], s(Blocks.CHISELED_SANDSTONE));
			b.set(c[0], 9, c[1], s(Blocks.SANDSTONE_WALL));
		}
		// The burial chamber.
		b.walls(-7, 2, -7, 7, 2, 7, orange);
		b.walls(-7, 6, -7, 7, 6, 7, blue);
		b.clear(-6, 1, -6, 6, 8, 6);
		b.fill(-6, 0, -6, 6, 0, 6, cut);
		b.fill(-2, 0, -2, 2, 0, 2, orange);
		b.set(0, 0, 0, blue);
		b.fill(-1, 1, -1, 1, 1, 1, s(Blocks.CHISELED_SANDSTONE));
		b.set(0, 2, 0, Blocks.GOLD_BLOCK);
		// The way in: a doorway in the north face and a long corridor, with a trap across it.
		b.clear(-1, 1, -21, 1, 3, -7);
		b.fill(-2, 1, -21, -2, 4, -21, s(Blocks.CHISELED_SANDSTONE));
		b.fill(2, 1, -21, 2, 4, -21, s(Blocks.CHISELED_SANDSTONE));
		b.fill(-2, 4, -21, 2, 4, -21, cut);
		b.fill(-1, -1, -14, 1, -1, -12, s(Blocks.TNT));
		b.fill(-1, 0, -14, 1, 0, -12, cut);
		b.fill(-1, 1, -13, 1, 1, -13, s(Blocks.STONE_PRESSURE_PLATE));
		// The two tombs either side of the chamber.
		for (int side = -1; side <= 1; side += 2) {
			int near = side * 7;
			int far = side * 15;
			b.clear(Math.min(near, side * 10), 1, -1, Math.max(near, side * 10), 3, 1);
			b.clear(Math.min(side * 11, far), 1, -3, Math.max(side * 11, far), 5, 3);
			b.fill(Math.min(side * 11, far), 0, -3, Math.max(side * 11, far), 0, 3, cut);
			b.fill(side * 13, 1, -2, side * 14, 1, -1, s(Blocks.CHISELED_SANDSTONE));
			b.set(side * 13, 2, -2, head(Blocks.SKELETON_SKULL, side < 0 ? 12 : 4));
			b.chest(side * 14, 1, 2, side < 0 ? Direction.EAST : Direction.WEST, "tomb");
			b.walls(side * 11, 4, -3, far, 4, 3, orange);
		}
		b.chest(-5, 1, -5, Direction.SOUTH, "pyramid");
		b.chest(5, 1, -5, Direction.SOUTH, "pyramid");
		b.chest(-5, 1, 5, Direction.NORTH, "pyramid");
		b.chest(5, 1, 5, Direction.NORTH, "pyramid");
		b.spawner(-3, 1, 5, EntityTypes.HUSK);
		b.spawner(3, 1, 5, EntityTypes.HUSK);
		// The pharaoh's vault, down a ladder in the chamber floor.
		b.fill(-5, -10, -5, 5, -4, 5, cut);
		b.clear(-4, -9, -4, 4, -5, 4);
		b.clear(0, -4, 4, 0, 0, 4);
		ladder(b, 0, 4, -9, 0, Direction.NORTH);
		b.chest(0, -9, -3, Direction.SOUTH, "treasure");
		b.chest(-3, -9, -3, Direction.SOUTH, "pyramid");
		b.chest(3, -9, -3, Direction.SOUTH, "pyramid");
		for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 2}, {4, 2}}) {
			b.set(c[0], -9, c[1], Blocks.GOLD_BLOCK);
		}
		b.spawner(0, -9, 0, EntityTypes.HUSK);
		b.keeper(-2, 1, 2, EntityTypes.HUSK);
		b.keeper(2, 1, -2, EntityTypes.HUSK);
		b.keeper(-12, 1, 0, EntityTypes.HUSK);
		b.keeper(12, 1, 0, EntityTypes.HUSK);
	}

	// --- a jungle ziggurat ---------------------------------------------------------------------------

	/**
	 * A ziggurat of five mossy tiers: a great stair up the north face to the shrine at the top, and
	 * inside the lowest tier a pillared hall of cave spiders, its treasure across a bridge over lava.
	 */
	static void jungleZiggurat(DeepBuilder b) {
		site(b, -24, -24, 24, 24, 0, 32);
		DeepBuilder.Paint stone = b.worn(s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.CRACKED_STONE_BRICKS),
				s(Blocks.STONE_BRICKS));
		for (int t = 0; t < 5; t++) {
			int w = 20 - 4 * t;
			b.fill(-w, 1 + 4 * t, -w, w, 4 + 4 * t, w, stone);
		}
		// The hall inside the lowest tier, entered from east and west.
		b.clear(-17, 1, -17, 17, 3, 17);
		b.clear(-20, 1, -1, -18, 2, 1);
		b.clear(18, 1, -1, 20, 2, 1);
		for (int x = -12; x <= 12; x += 6) {
			for (int z = -12; z <= 12; z += 6) {
				if (Math.abs(x) <= 5 && z >= 6) {
					continue;
				}
				b.pillar(x, z, 1, 3, s(Blocks.CHISELED_STONE_BRICKS));
			}
		}
		b.spawner(-9, 1, -9, EntityTypes.CAVE_SPIDER);
		b.spawner(9, 1, -9, EntityTypes.CAVE_SPIDER);
		webs(b, -9, 1, -9, 3, 8, 1);
		webs(b, 9, 1, -9, 3, 8, 2);
		b.chest(-16, 1, 8, Direction.EAST, "ziggurat");
		b.chest(16, 1, 8, Direction.WEST, "ziggurat");
		// The lava pit, the bridge across it, and the treasure beyond.
		b.clear(-5, -1, 7, 5, 0, 14);
		b.fill(-5, -3, 7, 5, -3, 14, s(Blocks.STONE));
		b.fill(-5, -2, 7, 5, -2, 14, s(Blocks.LAVA));
		b.fill(0, 0, 7, 0, 0, 14, s(Blocks.MOSSY_COBBLESTONE));
		b.fill(-2, 0, 15, 2, 0, 16, s(Blocks.CHISELED_STONE_BRICKS));
		b.chest(-1, 1, 16, Direction.NORTH, "ziggurat");
		b.chest(1, 1, 16, Direction.NORTH, "treasure");
		b.set(0, 1, 16, Blocks.GOLD_BLOCK);
		// The great stair up the north face.
		for (int k = 0; k < 20; k++) {
			b.fill(-2, k + 1, -21 + k, 2, k + 1, -21 + k, stairs(Blocks.MOSSY_STONE_BRICK_STAIRS, Direction.SOUTH, false));
			b.clear(-2, k + 2, -21 + k, 2, k + 4, -21 + k);
		}
		// The shrine on the top.
		b.walls(-3, 21, -1, 3, 24, 3, s(Blocks.CHISELED_STONE_BRICKS));
		b.fill(-4, 25, -2, 4, 25, 4, stone);
		b.clear(0, 21, -1, 0, 22, -1);
		b.chest(0, 21, 2, Direction.NORTH, "ziggurat");
		b.set(-2, 21, 2, Blocks.GOLD_BLOCK);
		b.set(2, 21, 2, Blocks.GOLD_BLOCK);
		// The jungle growing over it.
		for (int i = 0; i < 80; i++) {
			int x = b.layout.nextInt(41) - 20;
			int z = b.layout.nextInt(41) - 20;
			int m = Math.max(Math.abs(x), Math.abs(z));
			if (m <= 4 || Math.abs(x) <= 2 && z < 0) {
				continue;
			}
			int t = Math.min(4, (20 - m) / 4);
			b.set(x, 5 + 4 * t, z, leaves(Blocks.JUNGLE_LEAVES));
		}
		for (int y = 1; y <= 4; y++) {
			for (int k = -19; k <= 19; k += 3) {
				if (Math.abs(k) > 2) {
					b.set(k, y, -21, vine(Direction.SOUTH));
				}
				if (Math.abs(k) > 1) {
					b.set(21, y, k, vine(Direction.WEST));
					b.set(-21, y, k, vine(Direction.EAST));
				}
			}
		}
		b.keeper(0, 21, 1, "jaguar");
		b.keeper(-14, 9, 4, "monkey");
		b.keeper(14, 9, -4, "monkey");
		b.keeper(6, 13, 10, "monkey");
		b.keeper(-2, 26, 1, EntityTypes.PARROT);
		b.keeper(2, 26, 1, EntityTypes.PARROT);
	}

	// --- a citadel of ice -----------------------------------------------------------------------------

	/**
	 * A citadel of packed and blue ice: a ring wall with six spired towers, and a domed keep where
	 * strays rise in the dark below and a frost mage holds the treasure at the top.
	 */
	static void frostCitadel(DeepBuilder b) {
		site(b, 26.5, 34);
		DeepBuilder.Paint ice = b.mix(s(Blocks.PACKED_ICE), s(Blocks.PACKED_ICE), s(Blocks.BLUE_ICE));
		BlockState snow = s(Blocks.SNOW_BLOCK);
		b.disc(0, 0, 0, 22.5, snow);
		// The ring wall, two thick, with a gate in the north.
		b.cylinder(0, 0, 22.5, 1, 10, ice, true);
		b.cylinder(0, 0, 21.5, 1, 10, ice, true);
		ringBattlements(b, 0, 0, 22.5, 11, s(Blocks.PACKED_ICE));
		b.clear(-2, 1, -23, 2, 5, -20);
		// Six towers on the wall, with spires of blue ice.
		for (int i = 0; i < 6; i++) {
			double angle = i * Math.PI / 3;
			int cx = (int) Math.round(Math.cos(angle) * 22);
			int cz = (int) Math.round(Math.sin(angle) * 22);
			b.cylinder(cx, cz, 4.0, 1, 15, ice, false);
			for (int k = 0; k <= 6; k++) {
				b.disc(cx, 16 + k, cz, 4.0 - k * 0.6, s(Blocks.BLUE_ICE));
			}
		}
		// Ice spikes in the courtyard.
		for (int i = 0; i < 10; i++) {
			double angle = b.layout.nextDouble() * Math.PI * 2;
			double r = 13 + b.layout.nextDouble() * 6;
			int x = (int) Math.round(Math.cos(angle) * r);
			int z = (int) Math.round(Math.sin(angle) * r);
			int h = 3 + b.layout.nextInt(5);
			if (z < -10 && Math.abs(x) < 5) {
				continue;
			}
			b.pillar(x, z, 1, h, s(Blocks.PACKED_ICE));
		}
		// The keep.
		b.cylinder(0, 0, 9.5, 1, 22, ice, true);
		b.disc(0, 0, 0, 8.5, s(Blocks.POLISHED_DIORITE));
		b.disc(0, 8, 0, 8.5, snow);
		b.disc(0, 15, 0, 8.5, snow);
		b.sphere(0, 22, 0, 9.5, (x, y, z) -> b.chance(x, y, z, 92, 0.3) ? s(Blocks.BLUE_ICE) : s(Blocks.PACKED_ICE), true, 0);
		b.clear(-1, 1, -10, 1, 3, -8);
		ladder(b, 8, 0, 1, 8, Direction.WEST);
		ladder(b, -8, 0, 9, 15, Direction.EAST);
		// The ground floor, dark, where the strays rise.
		b.spawner(-5, 1, 4, EntityTypes.STRAY);
		b.spawner(5, 1, 4, EntityTypes.STRAY);
		b.chest(-7, 1, -3, Direction.EAST, "frost");
		b.chest(6, 1, -4, Direction.WEST, "frost");
		for (int x = -4; x <= 4; x += 8) {
			b.pillar(x, -4, 1, 7, s(Blocks.PACKED_ICE));
		}
		// The middle floor: the garrison's beds and gear.
		b.bed(-4, 9, 5, Direction.SOUTH, vanilla("light_blue_bed"));
		b.bed(4, 9, 5, Direction.SOUTH, vanilla("light_blue_bed"));
		b.chest(0, 9, 7, Direction.NORTH, "frost");
		b.stand(-6, 9, -2, Direction.EAST, new ItemStack(Items.CHAINMAIL_HELMET), new ItemStack(Items.CHAINMAIL_CHESTPLATE),
				new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.BOW));
		b.set(0, 14, 0, lantern(true, true));
		// The top floor under the dome: the frost mage's throne and hoard.
		b.set(0, 16, 7, stairs(Blocks.QUARTZ_STAIRS, Direction.SOUTH, false));
		b.set(-1, 16, 7, Blocks.BLUE_ICE);
		b.set(1, 16, 7, Blocks.BLUE_ICE);
		b.chest(0, 16, 8, Direction.NORTH, "treasure");
		b.chest(-3, 16, 7, Direction.NORTH, "frost");
		b.chest(3, 16, 7, Direction.NORTH, "frost");
		for (int[] c : new int[][] {{-6, -3}, {6, -3}, {-6, 3}, {6, 3}}) {
			b.set(c[0], 16, c[1], lantern(true, false));
		}
		b.keeper(0, 16, 2, EntityTypes.EVOKER);
		b.keeper(-2, 1, 0, EntityTypes.STRAY);
		b.keeper(2, 1, 0, EntityTypes.STRAY);
		b.keeper(12, 1, 6, EntityTypes.POLAR_BEAR);
		b.keeper(-12, 1, -6, EntityTypes.POLAR_BEAR);
		b.keeper(-8, 1, 14, "snow_leopard");
	}

	// --- a haunted manor -------------------------------------------------------------------------------

	/**
	 * A haunted manor of three storeys in grounds gone to ruin: a witch upstairs, the dead rising in
	 * the cellar, skeletons in the attic, and the old family's treasure up among the cobwebs.
	 */
	static void hauntedManor(DeepBuilder b) {
		site(b, -24, -20, 24, 20, 0, 30);
		DeepBuilder.Paint planks = b.ruin(DeepBuilder.p(s(Blocks.DARK_OAK_PLANKS)), 0.02);
		BlockState post = log(Blocks.DARK_OAK_LOG);
		BlockState beam = s(Blocks.STRIPPED_DARK_OAK_WOOD);
		DeepBuilder.Paint cobble = b.worn(s(Blocks.COBBLESTONE), s(Blocks.MOSSY_COBBLESTONE));
		// The grounds: a rusted railing, dead bushes, pumpkins.
		LandParts.railing(b, -22, 1, -18, 22, 1, 18, Blocks.IRON_BARS);
		b.clear(-2, 1, -18, 2, 1, -18);
		for (int i = 0; i < 24; i++) {
			int x = b.layout.nextInt(41) - 20;
			int z = b.layout.nextInt(33) - 16;
			if (Math.abs(x) <= 16 && Math.abs(z) <= 15 || Math.abs(x) <= 2) {
				continue;
			}
			b.set(x, 1, z, i % 4 == 0 ? facing(Blocks.CARVED_PUMPKIN, Direction.SOUTH) : s(Blocks.DEAD_BUSH));
		}
		// The house: three storeys of dark timber under a steep roof.
		b.fill(-16, 0, -11, 16, 0, 11, cobble);
		LandParts.house(b, -15, -10, 15, 10, 15, planks, post, s(Blocks.DARK_OAK_PLANKS));
		for (int x = -10; x <= 10; x += 5) {
			b.pillar(x, -10, 1, 15, post);
			b.pillar(x, 10, 1, 15, post);
		}
		for (int z = -5; z <= 5; z += 5) {
			b.pillar(-15, z, 1, 15, post);
			b.pillar(15, z, 1, 15, post);
		}
		b.walls(-15, 6, -10, 15, 6, 10, beam);
		b.walls(-15, 12, -10, 15, 12, 10, beam);
		b.fill(-14, 6, -9, 14, 6, 9, s(Blocks.SPRUCE_PLANKS));
		b.fill(-14, 12, -9, 14, 12, 9, s(Blocks.SPRUCE_PLANKS));
		LandParts.roof(b, -15, -10, 15, 10, 16, Blocks.DARK_OAK_STAIRS, s(Blocks.DARK_OAK_PLANKS));
		for (int y = 2; y <= 8; y += 6) {
			for (int x : new int[] {-12, -7, -3, 3, 7, 12}) {
				for (int z = -10; z <= 10; z += 20) {
					b.fill(x, y, z, x, y + 1, z, b.chance(x, y, z, 93, 0.25) ? b.open() : joined(Blocks.GLASS_PANE, true));
				}
			}
			for (int z : new int[] {-7, -2, 2, 7}) {
				for (int x = -15; x <= 15; x += 30) {
					b.fill(x, y, z, x, y + 1, z, b.chance(x, y, z, 94, 0.25) ? b.open() : joined(Blocks.GLASS_PANE, false));
				}
			}
		}
		// The porch and the door.
		b.fill(-4, 0, -14, 4, 0, -11, s(Blocks.SPRUCE_PLANKS));
		b.pillar(-4, -14, 1, 4, s(Blocks.DARK_OAK_FENCE));
		b.pillar(4, -14, 1, 4, s(Blocks.DARK_OAK_FENCE));
		b.fill(-5, 5, -15, 5, 5, -11, slab(Blocks.DARK_OAK_SLAB, false));
		b.set(0, 4, -12, lantern(true, true));
		b.clear(-1, 1, -10, 1, 3, -10);
		// Inside, the ground floor: an entrance hall between the dining room and the kitchen.
		b.fill(-5, 1, -9, -5, 5, 9, planks);
		b.clear(-5, 1, -1, -5, 2, 0);
		b.fill(5, 1, -9, 5, 5, 9, planks);
		b.clear(5, 1, 4, 5, 2, 5);
		for (int k = 0; k <= 5; k++) {
			if (k > 0) {
				b.pillar(4, -5 + k, 1, k, s(Blocks.DARK_OAK_PLANKS));
				b.pillar(-4, -5 + k, 7, 6 + k, s(Blocks.DARK_OAK_PLANKS));
			}
			b.set(4, 1 + k, -5 + k, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
			b.set(-4, 7 + k, -5 + k, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
		}
		b.clear(4, 6, -2, 4, 6, -1);
		b.clear(-4, 12, -2, -4, 12, -1);
		b.fill(-1, 1, -9, 1, 1, 9, carpet("red"));
		b.set(0, 5, 2, lantern(true, true));
		// The dining room.
		b.fill(-12, 1, -1, -7, 1, 1, slab(Blocks.DARK_OAK_SLAB, true));
		for (int x = -12; x <= -8; x += 2) {
			LandParts.seat(b, x, -2, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
			LandParts.seat(b, x, 2, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
		}
		b.set(-10, 2, 0, s(Blocks.CANDLE).setValue(BlockStateProperties.CANDLES, 3));
		b.chest(-14, 1, -8, Direction.EAST, "manor");
		webs(b, -10, 3, 0, 4, 6, 1);
		// The kitchen, and the stairs down to the cellar.
		b.set(14, 1, 2, facing(Blocks.FURNACE, Direction.WEST));
		b.set(14, 1, 3, facing(Blocks.SMOKER, Direction.WEST));
		b.barrel(14, 1, 5, Direction.UP, "manor");
		b.set(14, 1, 6, Blocks.BARREL);
		b.set(13, 1, 8, Blocks.CAULDRON);
		b.set(7, 1, 8, Blocks.CRAFTING_TABLE);
		LandParts.stairway(b, 10, 11, 0, -7, 1, 6, cobble, Blocks.COBBLESTONE_STAIRS);
		b.fill(5, -7, -1, 15, -7, 9, s(Blocks.COBBLESTONE));
		b.walls(5, -6, -1, 15, -2, 9, cobble);
		b.fill(5, -1, -1, 15, -1, 9, cobble);
		b.clear(6, -6, 0, 14, -2, 8);
		b.clear(10, -6, -1, 11, -4, -1);
		b.spawner(8, -6, 6, EntityTypes.ZOMBIE);
		b.chest(14, -6, 8, Direction.WEST, "manor");
		b.fill(6, -6, 7, 6, -5, 8, Blocks.BARREL);
		webs(b, 10, -4, 4, 4, 6, 2);
		// Upstairs: bedrooms, a library corner and the witch's room.
		for (int x : new int[] {-12, -8}) {
			b.bed(x, 7, -8, Direction.NORTH, vanilla("gray_bed"));
		}
		for (int z = 4; z <= 9; z++) {
			b.fill(-14, 7, z, -14, 9, z, Blocks.BOOKSHELF);
		}
		b.fill(-13, 7, 9, -10, 9, 9, Blocks.BOOKSHELF);
		b.set(-11, 7, 6, facing(Blocks.LECTERN, Direction.WEST));
		b.set(10, 7, 6, Blocks.BREWING_STAND);
		b.set(12, 7, 6, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 2));
		b.chest(14, 7, 8, Direction.WEST, "manor");
		b.set(-9, 11, 0, lantern(true, true));
		// The attic: dark, and full of cobwebs.
		webs(b, 0, 13, 0, 12, 30, 3);
		b.spawner(6, 13, 4, EntityTypes.SKELETON);
		b.chest(12, 13, 0, Direction.WEST, "manor");
		b.chest(-13, 13, 0, Direction.EAST, "treasure");
		b.fill(-13, 13, 3, -12, 14, 4, Blocks.BARREL);
		b.keeper(10, 7, 3, EntityTypes.WITCH);
		b.keeper(-9, 1, -5, EntityTypes.VINDICATOR);
		b.keeper(0, 14, -4, EntityTypes.BAT);
		b.keeper(-6, 14, 4, EntityTypes.BAT);
	}

	// --- an outlaw fort -----------------------------------------------------------------------------------

	/** One corner watchtower: a platform on four posts over the wall, roofed, with a ladder up from the yard. */
	private static void watchtower(DeepBuilder b, int cx, int cz) {
		int sx = Integer.signum(cx);
		int sz = Integer.signum(cz);
		for (int dx = -2; dx <= 2; dx += 4) {
			for (int dz = -2; dz <= 2; dz += 4) {
				b.pillar(cx + dx, cz + dz, 1, 15, log(Blocks.SPRUCE_LOG));
			}
		}
		b.fill(cx - 2, 12, cz - 2, cx + 2, 12, cz + 2, s(Blocks.SPRUCE_PLANKS));
		LandParts.railing(b, cx - 2, 13, cz - 2, cx + 2, 13, cz + 2, Blocks.SPRUCE_FENCE);
		b.fill(cx - 3, 16, cz - 3, cx + 3, 16, cz + 3, slab(Blocks.SPRUCE_SLAB, false));
		ladder(b, cx - 2 * sx, cz - sz, 1, 12, sz > 0 ? Direction.SOUTH : Direction.NORTH);
		b.clear(cx - 2 * sx, 13, cz - sz, cx - 2 * sx, 13, cz - sz);
	}

	/**
	 * An outlaws' fort of baked clay: watchtowers at the corners, a saloon where the gang drinks, a
	 * jail with prisoners to free, stables, and the bank vault they robbed, behind an iron door.
	 */
	static void outlawFortress(DeepBuilder b) {
		site(b, -26, -26, 26, 26, 0, 22);
		DeepBuilder.Paint clay = b.mix(s(Blocks.TERRACOTTA), s(vanilla("orange_terracotta")), s(vanilla("red_terracotta")), s(Blocks.TERRACOTTA));
		b.walls(-20, 1, -20, 20, 7, 20, clay);
		b.walls(-19, 1, -19, 19, 7, 19, clay);
		battlements(b, -20, 8, -20, 20, 20, s(Blocks.TERRACOTTA));
		b.clear(-2, 1, -20, 2, 5, -19);
		for (int k = 0; k < 7; k++) {
			if (k > 0) {
				b.pillar(5 + k, -18, 1, k, clay);
			}
			b.set(5 + k, 1 + k, -18, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
		}
		for (int[] c : new int[][] {{-19, -19}, {19, -19}, {-19, 19}, {19, 19}}) {
			watchtower(b, c[0], c[1]);
		}
		// The saloon.
		DeepBuilder.Paint planks = DeepBuilder.p(s(Blocks.SPRUCE_PLANKS));
		LandParts.house(b, -16, 2, -2, 16, 10, planks, log(Blocks.DARK_OAK_LOG), s(Blocks.DARK_OAK_PLANKS));
		b.fill(-15, 5, 3, -3, 5, 15, s(Blocks.SPRUCE_PLANKS));
		b.fill(-16, 11, 2, -2, 11, 16, slab(Blocks.DARK_OAK_SLAB, false));
		b.fill(-16, 11, 2, -2, 13, 2, planks);
		b.clear(-10, 1, 2, -8, 3, 2);
		b.fill(-15, 1, 12, -9, 1, 12, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, true));
		b.fill(-15, 1, 15, -9, 2, 15, Blocks.BARREL);
		b.chest(-15, 1, 14, Direction.EAST, "saloon");
		for (int[] t : new int[][] {{-11, 6}, {-6, 6}, {-6, 10}}) {
			LandParts.table(b, t[0], t[1], Blocks.DARK_OAK_FENCE, carpet("green"));
			LandParts.seat(b, t[0] - 1, t[1], Blocks.SPRUCE_STAIRS, Direction.WEST);
			LandParts.seat(b, t[0] + 1, t[1], Blocks.SPRUCE_STAIRS, Direction.EAST);
		}
		for (int k = 0; k <= 4; k++) {
			b.set(-15, 1 + k, 4 + k, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
		}
		b.clear(-15, 5, 6, -15, 5, 7);
		b.bed(-8, 6, 14, Direction.SOUTH, vanilla("red_bed"));
		b.chest(-3, 6, 15, Direction.WEST, "fortress");
		b.set(-9, 4, 9, lantern(false, true));
		b.set(-9, 10, 9, lantern(false, true));
		// The jail, with prisoners in two of its cells.
		LandParts.house(b, 4, 8, 16, 16, 5, b.worn(s(Blocks.STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS)), s(Blocks.STONE_BRICKS),
				s(Blocks.STONE_BRICKS));
		b.fill(4, 6, 8, 16, 6, 16, slab(Blocks.STONE_BRICK_SLAB, false));
		b.clear(10, 1, 8, 10, 2, 8);
		b.fill(5, 1, 11, 15, 3, 11, joined(Blocks.IRON_BARS, true));
		b.fill(8, 1, 12, 8, 3, 15, joined(Blocks.IRON_BARS, false));
		b.fill(12, 1, 12, 12, 3, 15, joined(Blocks.IRON_BARS, false));
		b.set(10, 1, 15, axis(Blocks.BONE_BLOCK, Direction.Axis.X));
		b.set(10, 1, 14, head(Blocks.SKELETON_SKULL, 8));
		b.chest(15, 1, 9, Direction.WEST, "saloon");
		b.set(10, 4, 10, lantern(false, true));
		b.set(10, 5, 10, s(Blocks.STONE_BRICKS));
		// The bank vault, shut behind an iron door with a button beside it.
		LandParts.house(b, -16, -16, -8, -8, 5, DeepBuilder.p(s(Blocks.POLISHED_ANDESITE)), s(Blocks.CHISELED_STONE_BRICKS),
				s(Blocks.POLISHED_ANDESITE));
		b.fill(-16, 6, -16, -8, 6, -8, s(Blocks.POLISHED_ANDESITE));
		BlockState door = s(Blocks.IRON_DOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
				.setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT);
		b.set(-12, 1, -8, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		b.set(-12, 2, -8, door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		b.set(-11, 2, -7, s(Blocks.STONE_BUTTON).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		b.set(-15, 1, -15, Blocks.GOLD_BLOCK);
		b.set(-9, 1, -15, Blocks.GOLD_BLOCK);
		b.set(-15, 1, -9, Blocks.EMERALD_BLOCK);
		b.chest(-12, 1, -15, Direction.SOUTH, "fortress");
		b.chest(-15, 1, -12, Direction.EAST, "saloon");
		// The stables.
		LandParts.railing(b, 8, 1, -16, 17, 1, -6, Blocks.SPRUCE_FENCE);
		b.set(12, 1, -6, facing(Blocks.SPRUCE_FENCE_GATE, Direction.NORTH));
		b.fill(15, 1, -15, 16, 1, -14, axis(Blocks.HAY_BLOCK, Direction.Axis.Y));
		b.set(9, 1, -15, s(Blocks.WATER_CAULDRON).setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		// The yard.
		b.set(0, 1, -6, s(Blocks.CAMPFIRE));
		b.fill(-6, 1, -16, -4, 2, -15, Blocks.BARREL);
		b.keeper(-19, 13, -19, EntityTypes.PILLAGER);
		b.keeper(19, 13, -19, EntityTypes.PILLAGER);
		b.keeper(-19, 13, 19, EntityTypes.PILLAGER);
		b.keeper(19, 13, 19, EntityTypes.PILLAGER);
		b.keeper(-11, 1, 8, EntityTypes.PILLAGER);
		b.keeper(-6, 6, 8, EntityTypes.PILLAGER);
		b.keeper(-12, 1, -12, EntityTypes.VINDICATOR);
		b.keeper(10, 1, 9, EntityTypes.VINDICATOR);
		b.keeper(6, 1, 13, EntityTypes.VILLAGER);
		b.keeper(14, 1, 13, EntityTypes.VILLAGER);
		b.keeper(11, 1, -11, EntityTypes.HORSE);
		b.keeper(14, 1, -9, EntityTypes.HORSE);
	}

	// --- a colosseum --------------------------------------------------------------------------------------

	/**
	 * A colosseum: an oval of rising stands round a sand arena where the dead and the bandits fight
	 * day and night, lions caged under the stands, and the emperor's box above it all.
	 */
	static void colosseum(DeepBuilder b) {
		site(b, -27, -23, 27, 23, 0, 18);
		DeepBuilder.Paint stone = b.worn(s(Blocks.SMOOTH_SANDSTONE), s(Blocks.SANDSTONE), s(Blocks.CUT_SANDSTONE));
		DeepBuilder.Paint seats = b.worn(s(Blocks.POLISHED_ANDESITE), s(Blocks.ANDESITE));
		DeepBuilder.Paint arena = b.mix(s(Blocks.SAND), s(Blocks.SAND), s(Blocks.SAND), s(Blocks.RED_SAND));
		for (int x = -26; x <= 26; x++) {
			for (int z = -22; z <= 22; z++) {
				if (!b.column(x, z)) {
					continue;
				}
				double fx = x / 26.5;
				double fz = z / 22.5;
				double d = Math.sqrt(fx * fx + fz * fz);
				if (d > 1.0) {
					continue;
				}
				if (d <= 0.56) {
					b.set(x, 0, z, arena);
				} else if (d > 0.93) {
					b.pillar(x, z, 1, 14, stone);
					b.set(x, 5, z, s(Blocks.CHISELED_SANDSTONE));
					b.set(x, 10, z, s(Blocks.CHISELED_SANDSTONE));
					if (Math.floorMod(x + z, 2) == 0) {
						b.set(x, 15, z, stone);
					}
				} else {
					int h = 3 + (int) ((d - 0.56) / 0.37 * 9.0);
					b.pillar(x, z, 1, h - 1, stone);
					b.set(x, h, z, seats);
				}
			}
		}
		// The gates, east and west.
		for (int side = -1; side <= 1; side += 2) {
			b.clear(Math.min(side * 14, side * 26), 1, -1, Math.max(side * 14, side * 26), 4, 1);
		}
		// The lions' cages under the south stands.
		for (int side = -1; side <= 1; side += 2) {
			int cx = side * 5;
			b.clear(cx - 1, 1, 12, cx + 1, 3, 14);
			b.fill(cx - 1, 0, 12, cx + 1, 0, 14, s(Blocks.COARSE_DIRT));
			b.fill(cx - 1, 1, 12, cx + 1, 3, 12, joined(Blocks.IRON_BARS, true));
		}
		// The emperor's box on the north side.
		b.fill(-4, 1, -19, 4, 8, -15, stone);
		b.clear(-4, 9, -19, 4, 13, -15);
		LandParts.railing(b, -4, 9, -15, 4, 9, -15, Blocks.SPRUCE_FENCE);
		for (int[] c : new int[][] {{-4, -19}, {4, -19}, {-4, -15}, {4, -15}}) {
			b.pillar(c[0], c[1], 9, 11, s(Blocks.SPRUCE_FENCE));
		}
		b.fill(-5, 12, -20, 5, 12, -14, wool("red"));
		b.set(0, 9, -18, stairs(Blocks.QUARTZ_STAIRS, Direction.NORTH, false));
		b.set(-1, 9, -18, Blocks.GOLD_BLOCK);
		b.set(1, 9, -18, Blocks.GOLD_BLOCK);
		b.chest(-3, 9, -18, Direction.SOUTH, "colosseum");
		b.chest(3, 9, -18, Direction.SOUTH, "colosseum");
		b.chest(0, 9, -19, Direction.SOUTH, "treasure");
		// What fights in the arena.
		b.spawner(-7, 0, 0, EntityTypes.HUSK);
		b.spawner(7, 0, 0, EntityTypes.SKELETON);
		b.keeper(-5, 1, 13, "lion");
		b.keeper(5, 1, 13, "lion");
		b.keeper(0, 1, 5, "rhino");
		b.keeper(-4, 1, -4, EntityTypes.VINDICATOR);
		b.keeper(4, 1, -4, EntityTypes.VINDICATOR);
		b.keeper(0, 9, -16, EntityTypes.PILLAGER);
	}

	// --- a tower of trials ----------------------------------------------------------------------------

	/** A tower of trials: five storeys, each with its spawner and its chest, and the master of the tower waiting on the roof. */
	static void trialTower(DeepBuilder b) {
		site(b, 12.5, 14);
		DeepBuilder.Paint stone = (x, y, z) -> Math.floorMod(y, 12) == 0 ? s(Blocks.CHISELED_STONE_BRICKS)
				: b.worn(x, y, z, s(Blocks.STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS));
		b.cylinder(0, 0, 7.5, 1, 60, stone, true);
		b.disc(0, 0, 0, 6.5, s(Blocks.POLISHED_ANDESITE));
		for (int storey = 1; storey <= 4; storey++) {
			b.disc(0, 12 * storey, 0, 6.5, s(Blocks.SPRUCE_PLANKS));
		}
		b.disc(0, 60, 0, 7.5, s(Blocks.STONE_BRICKS));
		b.cylinder(0, 0, 8.5, 60, 61, stone, true);
		ringBattlements(b, 0, 0, 8.5, 62, s(Blocks.STONE_BRICKS));
		b.clear(-1, 1, -8, 1, 3, -6);
		for (int[] c : new int[][] {{-10, -4}, {10, -4}, {-10, 4}, {10, 4}}) {
			b.pillar(c[0], c[1], 1, 3, s(Blocks.STONE_BRICK_WALL));
			b.set(c[0], 4, c[1], lantern(true, false));
		}
		// The trials, one to a storey, climbing from one side to the other.
		EntityType<?>[] guards = {EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.STRAY, EntityTypes.BLAZE};
		for (int storey = 0; storey < 5; storey++) {
			int floor = 12 * storey;
			int side = storey % 2 == 0 ? 1 : -1;
			ladder(b, 6 * side, 0, floor + 1, floor + 12, side > 0 ? Direction.WEST : Direction.EAST);
			b.spawner(0, floor + 1, 0, guards[storey]);
			b.chest(-4 * side, floor + 1, 3, Direction.NORTH, "tower");
			b.set(0, floor + 9, -7, joined(Blocks.IRON_BARS, true));
			b.set(0, floor + 9, 7, joined(Blocks.IRON_BARS, true));
		}
		// The roof.
		b.chest(0, 61, 0, Direction.SOUTH, "treasure");
		b.chest(-3, 61, 2, Direction.EAST, "tower");
		b.set(0, 61, -2, Blocks.GOLD_BLOCK);
		for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
			b.set(c[0], 61, c[1], lantern(false, false));
		}
		b.keeper(2, 61, 2, EntityTypes.EVOKER);
	}

	// --- a deep mine ----------------------------------------------------------------------------------------

	/** A straight tunnel of the mine on a floor, timbered every few blocks, with ore in its walls and a rail down the middle. */
	private static void tunnel(DeepBuilder b, int floor, int x0, int z0, int x1, int z1, Block... ores) {
		boolean alongX = z0 == z1;
		int length = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
		int dx = Integer.signum(x1 - x0);
		int dz = Integer.signum(z1 - z0);
		BlockState rail = s(Blocks.RAIL).setValue(BlockStateProperties.RAIL_SHAPE, alongX ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH);
		DeepBuilder.Paint ground = b.mix(s(Blocks.COBBLESTONE), s(Blocks.GRAVEL), s(Blocks.STONE), s(Blocks.COBBLESTONE));
		for (int i = 0; i <= length; i++) {
			int x = x0 + i * dx;
			int z = z0 + i * dz;
			boolean frame = i % 4 == 2;
			for (int w = -2; w <= 2; w++) {
				int tx = alongX ? x : x + w;
				int tz = alongX ? z + w : z;
				if (Math.abs(w) <= 1) {
					b.set(tx, floor, tz, ground);
					b.clear(tx, floor + 1, tz, tx, floor + 3, tz);
				} else if (frame) {
					b.pillar(tx, tz, floor + 1, floor + 3, log(Blocks.SPRUCE_LOG));
				} else if (b.chance(tx, floor, tz, 95, 0.14)) {
					b.set(tx, floor + 1 + (int) (b.rand(tx, floor, tz, 96) * 3), tz, s(b.pick(tx, floor, tz, 97, ores)));
				}
			}
			if (frame) {
				b.fill(alongX ? x : x - 2, floor + 4, alongX ? z - 2 : z, alongX ? x : x + 2, floor + 4, alongX ? z + 2 : z,
						s(Blocks.SPRUCE_PLANKS));
			}
			b.set(x, floor + 1, z, rail);
		}
	}

	/**
	 * A deep mine: a headframe over a timbered shaft, two levels of tunnels with ore in their walls,
	 * cave spiders nesting at their ends, and at the bottom a cavern with lava, diamonds and a hoard.
	 */
	static void deepMine(DeepBuilder b) {
		site(b, 14.5, 22);
		BlockState planks = s(Blocks.SPRUCE_PLANKS);
		// The headframe, its wheel turning over the shaft.
		for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			b.pillar(c[0], c[1], 1, 14, log(Blocks.SPRUCE_LOG));
		}
		for (int y = 4; y <= 12; y += 4) {
			LandParts.railing(b, -3, y, -3, 3, y, 3, Blocks.SPRUCE_FENCE);
		}
		b.fill(-3, 15, -3, 3, 15, 3, planks);
		b.clear(-1, 15, -1, 1, 15, 1);
		for (int i = 0; i < 16; i++) {
			double angle = i * Math.PI / 8;
			b.set((int) Math.round(Math.cos(angle) * 3), 20 + (int) Math.round(Math.sin(angle) * 3), 2, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
		}
		b.line(-3, 20, 2, 3, 20, 2, s(Blocks.DARK_OAK_FENCE));
		b.line(0, 17, 2, 0, 23, 2, s(Blocks.DARK_OAK_FENCE));
		b.set(0, 20, 2, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
		b.pillar(0, 3, 16, 19, s(Blocks.DARK_OAK_FENCE));
		// The shaft, lined with planks, a ladder down its north side.
		b.walls(-2, -23, -2, 2, -1, 2, planks);
		b.clear(-1, -22, -1, 1, 0, 1);
		b.fill(-1, -23, -1, 1, -23, 1, s(Blocks.COBBLESTONE));
		ladder(b, 0, -1, -22, 0, Direction.SOUTH);
		// Two levels of tunnels, east, west and south.
		Block[] upper = {Blocks.IRON_ORE, Blocks.COAL_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.COAL_ORE};
		Block[] lower = {Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.GOLD_ORE,
				Blocks.IRON_ORE};
		for (int floor = -11; floor >= -23; floor -= 12) {
			Block[] ores = floor == -11 ? upper : lower;
			tunnel(b, floor, 2, 0, 23, 0, ores);
			tunnel(b, floor, -2, 0, -23, 0, ores);
			tunnel(b, floor, 0, 2, 0, 24, ores);
			b.set(2, floor + 3, 2, lantern(false, true));
		}
		// The upper level: spiders in the east, a cave-in in the west, a hoard in the south.
		b.spawner(23, -10, 0, EntityTypes.CAVE_SPIDER);
		webs(b, 21, -10, 0, 2, 8, 1);
		b.fill(-23, -10, -1, -21, -9, 1, s(Blocks.GRAVEL));
		b.barrel(-20, -10, 1, Direction.UP, "mine");
		b.chest(1, -10, 24, Direction.WEST, "deep_mine");
		// The lower level: spiders in the west, a hoard in the south, and the cavern in the east.
		b.spawner(-23, -22, 0, EntityTypes.CAVE_SPIDER);
		webs(b, -21, -22, 0, 2, 8, 2);
		b.chest(1, -22, 24, Direction.WEST, "deep_mine");
		b.ellipsoid(22, -21, 0, 4.5, 3.5, 4.5, (x, y, z) -> b.open(), false, -1);
		b.fill(18, -23, -4, 26, -23, 4, s(Blocks.STONE));
		b.disc(24, -23, 2, 1.8, s(Blocks.LAVA));
		b.chest(20, -22, -3, Direction.EAST, "treasure");
		b.chest(23, -22, -3, Direction.SOUTH, "deep_mine");
		for (int[] c : new int[][] {{26, -2}, {22, 5}, {22, -5}}) {
			b.set(c[0], -22, c[1], Blocks.DIAMOND_ORE);
		}
		// A shed at the top for the tools.
		b.walls(7, 1, -9, 11, 3, -5, planks);
		b.fill(6, 4, -10, 12, 4, -4, slab(Blocks.SPRUCE_SLAB, false));
		b.clear(9, 1, -5, 9, 2, -5);
		b.barrel(10, 1, -8, Direction.UP, "mine");
		b.set(8, 1, -8, Blocks.CRAFTING_TABLE);
		b.keeper(-12, -10, 0, EntityTypes.SKELETON);
		b.keeper(10, -10, 0, EntityTypes.SKELETON);
		b.keeper(0, -22, 12, EntityTypes.ZOMBIE);
	}

	// --- an elder treehouse ----------------------------------------------------------------------------

	/**
	 * An elder tree with a house in its branches: three decks round the trunk with huts and lookouts,
	 * a ladder up the bark, and in the hollow at its foot, a nest of spiders.
	 */
	static void elderTreehouse(DeepBuilder b) {
		site(b, 16.5, 46);
		boolean jungle = b.climate() == Climate.JUNGLE;
		BlockState wood = log(jungle ? Blocks.JUNGLE_LOG : Blocks.DARK_OAK_LOG);
		BlockState leaf = leaves(jungle ? Blocks.JUNGLE_LEAVES : Blocks.DARK_OAK_LEAVES);
		BlockState planks = s(jungle ? Blocks.JUNGLE_PLANKS : Blocks.SPRUCE_PLANKS);
		BlockState fence = s(jungle ? Blocks.JUNGLE_FENCE : Blocks.SPRUCE_FENCE);
		// The trunk and its roots.
		b.cylinder(0, 0, 3.5, 1, 34, wood, false);
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4 + 0.3;
			int x0 = (int) Math.round(Math.cos(angle) * 3);
			int z0 = (int) Math.round(Math.sin(angle) * 3);
			int x1 = (int) Math.round(Math.cos(angle) * 8);
			int z1 = (int) Math.round(Math.sin(angle) * 8);
			b.line(x0, 3, z0, x1, 0, z1, wood);
			b.line(x0, 2, z0, x1, -1, z1, wood);
		}
		// Branches, each ending in a crown of leaves, and the great canopy over all.
		for (int i = 0; i < 6; i++) {
			double angle = i * Math.PI / 3 + 0.5;
			int y0 = 14 + i % 3 * 6;
			int x1 = (int) Math.round(Math.cos(angle) * 11);
			int z1 = (int) Math.round(Math.sin(angle) * 11);
			b.line((int) Math.round(Math.cos(angle) * 3), y0, (int) Math.round(Math.sin(angle) * 3), x1, y0 + 4, z1, wood);
			b.ellipsoid(x1, y0 + 5, z1, 4.5, 3.0, 4.5, (x, y, z) -> leaf, false, -2);
		}
		b.ellipsoid(0, 37, 0, 12.0, 6.0, 12.0, (x, y, z) -> leaf, false, -3);
		b.cylinder(0, 0, 3.5, 30, 35, wood, false);
		// The decks, the trunk passing up through each.
		int[][] decks = {{10, 8}, {18, 8}, {26, 6}};
		for (int[] deck : decks) {
			b.disc(0, deck[0], 0, deck[1], planks);
			ringFence(b, 0, 0, deck[1], deck[0] + 1, fence);
			b.cylinder(0, 0, 3.5, deck[0], deck[0] + 1, wood, false);
		}
		// The hollow at the foot of the trunk, and what nests there.
		b.cylinder(0, 0, 2.5, 1, 5, b.open(), false);
		b.clear(0, 1, 3, 0, 2, 3);
		b.spawner(0, 1, 0, EntityTypes.SPIDER);
		b.chest(-2, 1, 0, Direction.EAST, "treehouse");
		webs(b, 0, 2, 0, 2, 6, 1);
		// The ladder up the north side of the trunk.
		ladder(b, 0, -4, 1, 26, Direction.NORTH);
		// A hut on the first deck, a workshop on the second, a lookout on the third.
		b.walls(4, 11, -2, 7, 13, 2, planks);
		b.fill(3, 14, -3, 8, 14, 3, slab(Blocks.SPRUCE_SLAB, false));
		b.clear(4, 11, 0, 4, 12, 0);
		b.bed(5, 11, -1, Direction.EAST, vanilla("green_bed"));
		b.chest(6, 11, 1, Direction.WEST, "treehouse");
		b.walls(-7, 19, -2, -4, 21, 2, planks);
		b.fill(-8, 22, -3, -3, 22, 3, slab(Blocks.SPRUCE_SLAB, false));
		b.clear(-4, 19, 0, -4, 20, 0);
		b.set(-6, 19, -1, Blocks.CRAFTING_TABLE);
		b.chest(-6, 19, 1, Direction.EAST, "treehouse");
		b.chest(0, 27, 5, Direction.NORTH, "treehouse");
		for (int[] c : new int[][] {{5, 4}, {-5, -4}}) {
			b.set(c[0], 11, c[1], lantern(false, false));
			b.set(c[0], 19, c[1], lantern(false, false));
		}
		b.set(4, 27, -2, lantern(false, false));
		if (jungle) {
			b.keeper(5, 11, 4, "monkey");
			b.keeper(-4, 19, -4, "monkey");
			b.keeper(3, 27, 3, "toucan");
			b.keeper(-3, 27, -2, "toucan");
		} else {
			b.keeper(3, 27, 3, "owl");
			b.keeper(-3, 27, -2, "owl");
			b.keeper(9, 1, 9, EntityTypes.FOX);
		}
	}

	// --- a spire of blazes ------------------------------------------------------------------------------

	/**
	 * A spire of nether brick risen out of the dry lands, banded with magma and crowned with fire:
	 * magma cubes at its foot, blazes on its floors, and the hoard of whoever called them at the top.
	 */
	static void blazeSpire(DeepBuilder b) {
		site(b, 14.5, 52);
		DeepBuilder.Paint brick = b.worn(s(Blocks.NETHER_BRICKS), s(Blocks.RED_NETHER_BRICKS), s(Blocks.CRACKED_NETHER_BRICKS));
		DeepBuilder.Paint banded = (x, y, z) -> Math.floorMod(y, 6) == 0 ? s(Blocks.MAGMA_BLOCK) : brick.at(x, y, z);
		BlockState floor = s(Blocks.NETHER_BRICKS);
		b.disc(0, 0, 0, 12.5, b.mix(s(Blocks.BLACKSTONE), s(Blocks.BLACKSTONE), s(Blocks.BLACKSTONE), s(Blocks.POLISHED_BLACKSTONE),
				s(Blocks.POLISHED_BLACKSTONE), s(Blocks.POLISHED_BLACKSTONE), s(Blocks.GILDED_BLACKSTONE), s(Blocks.MAGMA_BLOCK)));
		// Braziers of soul fire round the foot.
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4;
			int x = (int) Math.round(Math.cos(angle) * 11);
			int z = (int) Math.round(Math.sin(angle) * 11);
			b.pillar(x, z, 1, 5, axis(Blocks.BASALT, Direction.Axis.Y));
			b.set(x, 6, z, Blocks.SOUL_SOIL);
			b.set(x, 7, z, Blocks.SOUL_FIRE);
		}
		// The spire, narrowing as it climbs, crowned with fire.
		b.cylinder(0, 0, 6.5, 1, 12, banded, true);
		b.cylinder(0, 0, 5.5, 13, 24, banded, true);
		b.cylinder(0, 0, 4.5, 25, 36, banded, true);
		b.cylinder(0, 0, 3.5, 37, 44, banded, true);
		b.disc(0, 12, 0, 6.5, floor);
		b.disc(0, 24, 0, 5.5, floor);
		b.disc(0, 36, 0, 4.5, floor);
		b.disc(0, 44, 0, 3.5, floor);
		b.disc(0, 45, 0, 2.5, floor);
		b.disc(0, 46, 0, 1.5, floor);
		b.set(0, 47, 0, Blocks.NETHERRACK);
		b.set(0, 48, 0, Blocks.FIRE);
		b.clear(-1, 1, -7, 1, 3, -5);
		// Ladders between the floors, each on a pilaster so it comes out inside the narrower floor above.
		b.pillar(5, 0, 1, 12, floor);
		ladder(b, 4, 0, 1, 12, Direction.WEST);
		b.pillar(-4, 0, 13, 24, floor);
		ladder(b, -3, 0, 13, 24, Direction.EAST);
		b.pillar(3, 0, 25, 36, floor);
		ladder(b, 2, 0, 25, 36, Direction.WEST);
		for (int y = 6; y <= 30; y += 12) {
			int r = y < 12 ? 6 : y < 24 ? 5 : 4;
			b.set(0, y, -r, joined(Blocks.IRON_BARS, true));
			b.set(0, y, r, joined(Blocks.IRON_BARS, true));
		}
		// The floors: magma cubes below, blazes above, the hoard at the top.
		b.spawner(-2, 1, 2, EntityTypes.MAGMA_CUBE);
		b.set(-3, 1, -3, Blocks.LAVA_CAULDRON);
		b.set(3, 1, 3, Blocks.LAVA_CAULDRON);
		b.chest(-4, 1, 1, Direction.EAST, "blaze");
		b.spawner(0, 13, 0, EntityTypes.BLAZE);
		b.chest(2, 13, 3, Direction.NORTH, "blaze");
		b.spawner(0, 25, 0, EntityTypes.BLAZE);
		b.chest(-2, 25, -1, Direction.EAST, "blaze");
		b.chest(0, 37, 2, Direction.NORTH, "treasure");
		b.chest(-1, 37, -2, Direction.SOUTH, "blaze");
		b.set(1, 37, -2, Blocks.GOLD_BLOCK);
		b.keeper(0, 37, 0, EntityTypes.BLAZE);
		b.keeper(-6, 1, -9, EntityTypes.MAGMA_CUBE);
		b.keeper(6, 1, -9, EntityTypes.MAGMA_CUBE);
	}
}
