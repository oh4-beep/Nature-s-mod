package com.naturesmod.worldgen.deep;

import static com.naturesmod.worldgen.deep.DeepBuilder.axis;
import static com.naturesmod.worldgen.deep.DeepBuilder.facing;
import static com.naturesmod.worldgen.deep.DeepBuilder.joined;
import static com.naturesmod.worldgen.deep.DeepBuilder.s;
import static com.naturesmod.worldgen.deep.DeepBuilder.slab;
import static com.naturesmod.worldgen.deep.DeepBuilder.stairs;
import static com.naturesmod.worldgen.deep.DeepBuilder.vanilla;

import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The building kit of the Overworld's structures: a site levelled out of whatever ground is there,
 * houses, gable roofs, tents, fields, palisades and towers.
 */
final class LandParts {
	private LandParts() {
	}

	static BlockState log(Block log) {
		return axis(log, Direction.Axis.Y);
	}

	static BlockState wool(String colour) {
		return s(vanilla(colour + "_wool"));
	}

	static BlockState carpet(String colour) {
		return s(vanilla(colour + "_carpet"));
	}

	static BlockState lit(BlockState state) {
		return state.setValue(BlockStateProperties.LIT, true);
	}

	static BlockState vine(Direction against) {
		return s(Blocks.VINE).setValue(switch (against) {
			case NORTH -> BlockStateProperties.NORTH;
			case SOUTH -> BlockStateProperties.SOUTH;
			case EAST -> BlockStateProperties.EAST;
			default -> BlockStateProperties.WEST;
		}, true);
	}

	/**
	 * The natural ground of a column near y = 0: the top block that is neither air, water, a plant, a log nor leaves,
	 * and that a block can stand on. Snow counts, and becomes a snow block when the site is levelled.
	 */
	static int groundY(DeepBuilder b, int x, int z) {
		for (int y = 10; y >= -14; y--) {
			BlockState state = b.get(x, y, z);
			if (state.is(Blocks.SNOW) || state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
					&& b.sturdy(x, y, z)) {
				return y;
			}
		}
		return -14;
	}

	/**
	 * Levels a site at y = 0 out of the ground that is there: its own surface block (grass, sand, snow...) on top, the
	 * ground built up beneath where it dips and cleared above for {@code clear} blocks. Round when {@code radius} > 0.
	 */
	static void site(DeepBuilder b, int x0, int z0, int x1, int z1, double radius, int clear) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				if (radius > 0 && x * x + z * z > radius * radius || !b.column(x, z)) {
					continue;
				}
				BlockState surface = b.get(x, groundY(b, x, z), z);
				if (surface.is(Blocks.SNOW) || surface.isAir()) {
					surface = s(Blocks.SNOW_BLOCK);
				}
				BlockState under = surface.is(Blocks.GRASS_BLOCK) || surface.is(Blocks.PODZOL) || surface.is(Blocks.MYCELIUM)
						|| surface.is(Blocks.SNOW_BLOCK) || surface.is(Blocks.MOSS_BLOCK) ? s(Blocks.DIRT) : surface;
				b.foundation(x, -1, z, under);
				b.set(x, 0, z, surface);
				b.clear(x, 1, z, x, clear, z);
			}
		}
	}

	/** A round site: see {@link #site}. */
	static void site(DeepBuilder b, double radius, int clear) {
		int r = (int) Math.ceil(radius);
		site(b, -r, -r, r, r, radius, clear);
	}

	/** Walls of a box room with posts at the corners, a floor at y = 0 and the inside cleared. */
	static void house(DeepBuilder b, int x0, int z0, int x1, int z1, int height, DeepBuilder.Paint wall, BlockState corner, BlockState floor) {
		b.fill(x0, 0, z0, x1, 0, z1, floor);
		b.walls(x0, 1, z0, x1, height, z1, wall);
		for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			b.pillar(c[0], c[1], 1, height, corner);
		}
		b.clear(x0 + 1, 1, z0 + 1, x1 - 1, height, z1 - 1);
	}

	/** A gable roof over the box from (x0, z0) to (x1, z1), its ridge along x, starting at height y; gables closed in {@code gable}. */
	static void roof(DeepBuilder b, int x0, int z0, int x1, int z1, int y, Block stairs, BlockState gable) {
		for (int k = 0; ; k++) {
			int zn = z0 - 1 + k;
			int zs = z1 + 1 - k;
			if (zn > zs) {
				break;
			}
			for (int x = x0 - 1; x <= x1 + 1; x++) {
				if (zn == zs) {
					b.set(x, y + k, zn, gable);
				} else {
					b.set(x, y + k, zn, stairs(stairs, Direction.SOUTH, false));
					b.set(x, y + k, zs, stairs(stairs, Direction.NORTH, false));
				}
			}
			if (k > 0) {
				b.fill(x0, y + k - 1, zn, x0, y + k - 1, zs, gable);
				b.fill(x1, y + k - 1, zn, x1, y + k - 1, zs, gable);
			}
		}
	}

	/** A simple house: room, door on the north side, windows, a gable roof. */
	static void cottage(DeepBuilder b, int x0, int z0, int x1, int z1, int height, DeepBuilder.Paint wall, BlockState corner, BlockState floor,
			Block roofStairs, BlockState gable) {
		house(b, x0, z0, x1, z1, height, wall, corner, floor);
		int door = (x0 + x1) / 2;
		b.clear(door, 1, z0, door, 2, z0);
		for (int x = x0 + 2; x <= x1 - 2; x += 3) {
			if (x != door) {
				b.set(x, 2, z0, joined(Blocks.GLASS_PANE, true));
			}
			b.set(x, 2, z1, joined(Blocks.GLASS_PANE, true));
		}
		b.set(x0, 2, (z0 + z1) / 2, joined(Blocks.GLASS_PANE, false));
		b.set(x1, 2, (z0 + z1) / 2, joined(Blocks.GLASS_PANE, false));
		roof(b, x0, z0, x1, z1, height + 1, roofStairs, gable);
	}

	/** An A-frame tent of cloth along z, open at its north end. */
	static void tent(DeepBuilder b, int x, int z0, int z1, BlockState cloth) {
		for (int z = z0; z <= z1; z++) {
			b.set(x - 2, 1, z, cloth);
			b.set(x + 2, 1, z, cloth);
			b.set(x - 1, 2, z, cloth);
			b.set(x + 1, 2, z, cloth);
			b.set(x, 3, z, cloth);
		}
		b.fill(x - 1, 1, z1, x + 1, 1, z1, cloth);
		b.set(x, 2, z1, cloth);
	}

	/** A field of crops on farmland, watered down the middle. */
	static void field(DeepBuilder b, int x0, int z0, int x1, int z1, Block crop) {
		b.fill(x0, 0, z0, x1, 0, z1, s(Blocks.FARMLAND).setValue(BlockStateProperties.MOISTURE, 7));
		int mid = (x0 + x1) / 2;
		b.fill(mid, 0, z0, mid, 0, z1, s(Blocks.WATER));
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (x != mid) {
					int age = (int) (b.rand(x, 1, z, 61) * 8);
					BlockState plant = s(crop);
					if (plant.hasProperty(BlockStateProperties.AGE_7)) {
						plant = plant.setValue(BlockStateProperties.AGE_7, age);
					} else if (plant.hasProperty(BlockStateProperties.AGE_3)) {
						plant = plant.setValue(BlockStateProperties.AGE_3, age / 2);
					}
					b.set(x, 1, z, plant);
				}
			}
		}
	}

	/** A palisade of upright logs round a rectangle, with a gap for a gate in the middle of the north side. */
	static void palisade(DeepBuilder b, int x0, int z0, int x1, int z1, int height, Block log) {
		b.walls(x0, 1, z0, x1, height, z1, log(log));
		int gate = (x0 + x1) / 2;
		b.clear(gate - 1, 1, z0, gate + 1, height - 1, z0);
		for (int x = x0; x <= x1; x += 2) {
			b.set(x, height + 1, z0, log(log));
			b.set(x, height + 1, z1, log(log));
		}
	}

	/** A round tower, hollow, from y = 1 up to {@code height}, with floors every {@code storey} blocks and ladders up the inside. */
	static void tower(DeepBuilder b, int cx, int cz, double radius, int height, int storey, DeepBuilder.Paint wall, BlockState floor) {
		b.cylinder(cx, cz, radius, 1, height, wall, true);
		for (int y = storey; y < height; y += storey) {
			b.disc(cx, y, cz, radius - 1.0, floor);
		}
		int lx = cx + (int) Math.floor(radius - 1.5);
		for (int y = 1; y < height; y++) {
			b.set(lx, y, cz, facing(Blocks.LADDER, Direction.WEST));
		}
		b.clear(cx, 1, cz - (int) Math.floor(radius - 0.5), cx, 2, cz - (int) Math.floor(radius - 0.5));
	}

	/** A railing of fences, bars or panes round a rectangle, each joined to its neighbours along the ring. */
	static void railing(DeepBuilder b, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		for (int x = ax; x <= bx; x++) {
			for (int z = az; z <= bz; z++) {
				boolean side = z == az || z == bz;
				boolean end = x == ax || x == bx;
				if (!side && !end) {
					continue;
				}
				BlockState state = s(block).setValue(BlockStateProperties.EAST, side && x < bx).setValue(BlockStateProperties.WEST, side && x > ax)
						.setValue(BlockStateProperties.NORTH, end && z > az).setValue(BlockStateProperties.SOUTH, end && z < bz);
				b.pillar(x, z, y0, y1, state);
			}
		}
	}

	/**
	 * A stairway down into the ground heading south from z, between x0 and x1: {@code steps} steps, each one lower and
	 * one further south, walled and roofed in {@code wall} so the ground cannot fall in on it. The last step is at
	 * y = -steps, so a floor at y = -steps - 1 just south of it carries on from the bottom.
	 */
	static void stairway(DeepBuilder b, int x0, int x1, int z, int steps, DeepBuilder.Paint wall, Block stairs) {
		stairway(b, x0, x1, 0, z, 1, steps, wall, stairs);
	}

	/**
	 * A stairway down from a floor at y0 (the floor itself is cut away above the first steps), heading south when
	 * {@code dz} is 1 and north when it is -1. The last step is at y0 - steps.
	 */
	static void stairway(DeepBuilder b, int x0, int x1, int y0, int z, int dz, int steps, DeepBuilder.Paint wall, Block stairs) {
		Direction up = dz > 0 ? Direction.NORTH : Direction.SOUTH;
		for (int k = 0; k < steps; k++) {
			int zk = z + k * dz;
			b.fill(x0 - 1, y0 - k - 1, zk, x1 + 1, Math.min(y0 - k + 3, y0 - 1), zk, wall);
			b.clear(x0, y0 - k, zk, x1, y0 - k + 2, zk);
			b.fill(x0, y0 - k - 1, zk, x1, y0 - k - 1, zk, stairs(stairs, up, false));
		}
	}

	/** A table: a fence post with a pressure plate of wood on top... or a cake on it. */
	static void table(DeepBuilder b, int x, int z, Block fence, BlockState top) {
		b.set(x, 1, z, fence);
		b.set(x, 2, z, top);
	}

	/** A stool or bench seat of stairs facing {@code facing}. */
	static void seat(DeepBuilder b, int x, int z, Block stairs, Direction facing) {
		b.set(x, 1, z, stairs(stairs, facing, false));
	}

	static BlockState topSlab(Block slab) {
		return slab(slab, true);
	}

	/** A pressure plate trap: TNT two blocks under the plate, primed when someone steps on it. */
	static void tntTrap(DeepBuilder b, int x, int y, int z, BlockState cover) {
		b.fill(x - 1, y - 2, z - 1, x + 1, y - 2, z + 1, s(Blocks.TNT));
		b.set(x, y - 1, z, cover);
		b.set(x, y, z, Blocks.STONE_PRESSURE_PLATE);
	}
}
