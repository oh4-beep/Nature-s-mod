package com.naturesmod.worldgen.hell;

import com.naturesmod.NaturesMod;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.worldgen.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Builds one of the strongholds of Hell. Like the sunken structures, the whole structure is laid
 * out in every chunk it touches and only the blocks inside that chunk are placed, so every
 * per-block choice comes from a hash of the block's position, and every layout choice from a
 * random sequence that each chunk replays in full.
 */
public class HellPiece extends StructurePiece {
	private final Kind kind;
	private final int cx;
	private final int cy;
	private final int cz;
	private final long seed;

	HellPiece(Kind kind, BlockPos center, long seed) {
		super(ModStructures.HELL_PIECE, 0, kind.bounds(center));
		this.kind = kind;
		this.cx = center.getX();
		this.cy = center.getY();
		this.cz = center.getZ();
		this.seed = seed;
	}

	public HellPiece(CompoundTag tag) {
		super(ModStructures.HELL_PIECE, tag);
		this.kind = Kind.byId(tag.getStringOr("kind", "infernal_citadel"));
		this.cx = tag.getIntOr("cx", 0);
		this.cy = tag.getIntOr("cy", 0);
		this.cz = tag.getIntOr("cz", 0);
		this.seed = tag.getLongOr("seed", 0L);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("kind", kind.id());
		tag.putInt("cx", cx);
		tag.putInt("cy", cy);
		tag.putInt("cz", cz);
		tag.putLong("seed", seed);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		Builder b = new Builder(level, chunkBox, seed);
		switch (kind) {
			case CITADEL -> citadel(b);
			case BONE_PIT -> bonePit(b);
			case RITUAL -> ritual(b);
		}
	}

	private static ResourceKey<LootTable> loot(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, NaturesMod.id(path));
	}

	private static BlockState skull(Builder b, int x, int y, int z, double wither) {
		BlockState skull = (b.rand(x, y, z, 77) < wither ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL).defaultBlockState();
		return skull.setValue(SkullBlock.ROTATION, (int) (b.rand(x, y, z, 78) * 16));
	}

	// --- the Infernal Citadel ------------------------------------------------------------

	/**
	 * A fortress of scorched brick and blackstone ringed by a moat of lava: high walls with corner
	 * towers, braziers lining the road from the gate, and a great keep where a throne waits
	 * between two treasure chests, wither skeletons rise from the hall floor and blazes guard
	 * the roof.
	 */
	private void citadel(Builder b) {
		int floor = cy + 1;
		BlockState scorched = ModBlocks.SCORCHED_BRICKS.defaultBlockState();
		BlockState blackBricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState brimstone = ModBlocks.BRIMSTONE_BRICKS.defaultBlockState();
		BlockState blackstone = Blocks.BLACKSTONE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int dx = -24; dx <= 24; dx++) {
			for (int dz = -24; dz <= 24; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!b.column(x, z)) {
					continue;
				}
				int m = Math.max(Math.abs(dx), Math.abs(dz));
				boolean road = Math.abs(dx) <= 1 && dz < 0;
				if (m <= 20) {
					b.foundation(x, floor - 1, z, 24, blackstone);
					BlockState ground = road ? blackBricks : b.rand(x, floor, z, 1) < 0.22 ? ModBlocks.SCORCHED_NETHERRACK.defaultBlockState()
							: b.rand(x, floor, z, 2) < 0.05 ? Blocks.MAGMA_BLOCK.defaultBlockState() : blackstone;
					b.set(x, floor, z, ground);
					for (int h = 1; h <= 14; h++) {
						b.set(x, floor + h, z, air);
					}
				} else if (m <= 23) {
					// The moat, crossed by the road on a bridge.
					b.foundation(x, floor - 2, z, 24, blackstone);
					b.set(x, floor - 1, z, blackstone);
					b.set(x, floor, z, road ? blackBricks : Blocks.LAVA.defaultBlockState());
					for (int h = 1; h <= 5; h++) {
						b.set(x, floor + h, z, air);
					}
				} else {
					b.foundation(x, floor, z, 24, blackstone);
					b.set(x, floor, z, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
					for (int h = 1; h <= 4; h++) {
						b.set(x, floor + h, z, air);
					}
				}
				// The outer wall, two thick, with battlements and arrow slits.
				if (m >= 19 && m <= 20) {
					boolean gate = dz <= -19 && Math.abs(dx) <= 1;
					for (int h = 1; h <= 9; h++) {
						int y = floor + h;
						if (gate && h <= 4) {
							b.set(x, y, z, h == 4 ? bars(dx, dz) : air);
							continue;
						}
						boolean slit = m == 20 && (h == 5 || h == 6) && Math.abs(dx) != Math.abs(dz)
								&& (Math.abs(dz) == 20 ? dx % 5 == 0 : dz % 5 == 0);
						b.set(x, y, z, slit ? bars(dx, dz) : wall(b, x, y, z, h, scorched, blackBricks, cracked, brimstone));
					}
					if (m == 20 && (dx + dz) % 2 == 0) {
						b.set(x, floor + 10, z, blackBricks);
					}
				}
			}
		}
		// Braziers line the road; soul fire pits burn either side of it.
		for (int dz = -16; dz <= -4; dz += 4) {
			for (int side = -1; side <= 1; side += 2) {
				b.set(cx + side * 3, floor + 1, cz + dz, ModBlocks.HELLFIRE_BRAZIER.defaultBlockState());
			}
		}
		for (int side = -1; side <= 1; side += 2) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = cx + side * 11 + dx;
					int z = cz - 11 + dz;
					b.set(x, floor, z, Blocks.SOUL_SOIL.defaultBlockState());
					b.set(x, floor + 1, z, Blocks.SOUL_FIRE.defaultBlockState());
				}
			}
			// Pikes in the far corners of the courtyard.
			for (int i = 0; i < 4; i++) {
				int x = cx + side * (14 + (i % 2) * 2);
				int z = cz + 15 - (i / 2) * 2;
				b.set(x, floor + 1, z, Blocks.NETHER_BRICK_FENCE.defaultBlockState());
				b.set(x, floor + 2, z, Blocks.NETHER_BRICK_FENCE.defaultBlockState());
				b.set(x, floor + 3, z, skull(b, x, floor + 3, z, 0.3));
			}
		}
		// The corner towers.
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				tower(b, cx + sx * 20, floor, cz + sz * 20, -sx, -sz, scorched, blackBricks, cracked, brimstone);
			}
		}
		keep(b, floor, scorched, blackBricks, cracked, brimstone);
	}

	/** Iron bars in a wall, joined along it. */
	private static BlockState bars(int dx, int dz) {
		boolean alongX = Math.abs(dz) >= Math.abs(dx);
		return Blocks.IRON_BARS.defaultBlockState().setValue(BlockStateProperties.EAST, alongX).setValue(BlockStateProperties.WEST, alongX)
				.setValue(BlockStateProperties.NORTH, !alongX).setValue(BlockStateProperties.SOUTH, !alongX);
	}

	/** Wall masonry: scorched brick, blackstone brick, a band of brimstone every fourth course. */
	private static BlockState wall(Builder b, int x, int y, int z, int course, BlockState scorched, BlockState blackBricks, BlockState cracked,
			BlockState brimstone) {
		if (course % 4 == 0) {
			return brimstone;
		}
		double roll = b.rand(x, y, z, 3);
		return roll < 0.55 ? scorched : roll < 0.88 ? blackBricks : roll < 0.97 ? cracked : Blocks.GILDED_BLACKSTONE.defaultBlockState();
	}

	private void tower(Builder b, int tx, int floor, int tz, int inX, int inZ, BlockState scorched, BlockState blackBricks, BlockState cracked,
			BlockState brimstone) {
		int top = floor + 16;
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				int x = tx + dx;
				int z = tz + dz;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 4.6 || !b.column(x, z)) {
					continue;
				}
				b.foundation(x, floor, z, 24, Blocks.BLACKSTONE.defaultBlockState());
				boolean rim = d > 3.2;
				for (int h = 0; h <= 16; h++) {
					int y = floor + h;
					if (h == 0 || h == 8 || h == 16) {
						b.set(x, y, z, rim ? wall(b, x, y, z, h, scorched, blackBricks, cracked, brimstone) : blackBricks);
					} else if (rim) {
						boolean window = (h == 5 || h == 12) && (Math.abs(dx) <= 0 || Math.abs(dz) <= 0);
						boolean door = h <= 2 && Integer.signum(dx) == inX && Integer.signum(dz) == inZ && Math.abs(Math.abs(dx) - Math.abs(dz)) <= 1;
						b.set(x, y, z, door ? Blocks.AIR.defaultBlockState() : window ? bars(dx, dz) : wall(b, x, y, z, h, scorched, blackBricks, cracked, brimstone));
					} else {
						b.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
				if (rim && (dx + dz) % 2 == 0) {
					b.set(x, top + 1, z, blackBricks);
				}
			}
		}
		b.set(tx, top + 1, tz, ModBlocks.HELLFIRE_BRAZIER.defaultBlockState());
		b.set(tx, floor + 9, tz, ModBlocks.SOUL_BRAZIER.defaultBlockState());
	}

	private void keep(Builder b, int floor, BlockState scorched, BlockState blackBricks, BlockState cracked, BlockState brimstone) {
		int roof = floor + 20;
		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -1; dz <= 13; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!b.column(x, z)) {
					continue;
				}
				boolean edge = Math.abs(dx) == 7 || dz == -1 || dz == 13;
				boolean corner = Math.abs(dx) == 7 && (dz == -1 || dz == 13);
				b.set(x, floor, z, edge ? blackBricks : Blocks.RED_NETHER_BRICKS.defaultBlockState());
				for (int h = 1; h <= 20; h++) {
					int y = floor + h;
					if (edge) {
						boolean door = dz == -1 && Math.abs(dx) <= 1 && h <= 4;
						boolean window = !corner && (h == 6 || h == 7 || h == 16) && (dx % 3 == 0 && Math.abs(dx) < 7 || (dz - 6) % 3 == 0 && Math.abs(dx) == 7);
						if (door) {
							b.set(x, y, z, Blocks.AIR.defaultBlockState());
						} else if (window) {
							b.set(x, y, z, bars(Math.abs(dx) == 7 ? dx : 0, Math.abs(dx) == 7 ? 0 : dz - 6));
						} else {
							b.set(x, y, z, corner ? brimstone : wall(b, x, y, z, h, scorched, blackBricks, cracked, brimstone));
						}
					} else if (h == 13 || h == 20) {
						b.set(x, y, z, blackBricks);
					} else {
						b.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
				if (edge && !corner && (dx + dz) % 2 == 0) {
					b.set(x, roof + 1, z, blackBricks);
				}
			}
		}
		// A golden lintel over the door, braziers flanking it.
		for (int dx = -1; dx <= 1; dx++) {
			b.set(cx + dx, floor + 5, cz - 1, Blocks.GILDED_BLACKSTONE.defaultBlockState());
		}
		b.set(cx - 3, floor + 1, cz - 2, ModBlocks.HELLFIRE_BRAZIER.defaultBlockState());
		b.set(cx + 3, floor + 1, cz - 2, ModBlocks.HELLFIRE_BRAZIER.defaultBlockState());
		// Pillars down the hall, soul lanterns hanging between them.
		for (int side = -1; side <= 1; side += 2) {
			for (int dz : new int[] {3, 8}) {
				for (int h = 1; h <= 12; h++) {
					b.set(cx + side * 4, floor + h, cz + dz, Blocks.POLISHED_BASALT.defaultBlockState());
				}
			}
			for (int h = 10; h <= 12; h++) {
				b.set(cx + side * 2, floor + h, cz + 5, Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
			}
			b.set(cx + side * 2, floor + 9, cz + 5, Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
		}
		// The throne on its dais, with the treasure either side of it.
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = 10; dz <= 12; dz++) {
				b.set(cx + dx, floor + 1, cz + dz, blackBricks);
			}
		}
		b.set(cx, floor + 2, cz + 11, Blocks.GILDED_BLACKSTONE.defaultBlockState());
		for (int h = 2; h <= 5; h++) {
			b.set(cx, floor + h, cz + 12, Blocks.GILDED_BLACKSTONE.defaultBlockState());
		}
		b.set(cx, floor + 6, cz + 12, Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
		b.set(cx - 1, floor + 2, cz + 11, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
		b.set(cx + 1, floor + 2, cz + 11, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
		b.chest(cx - 2, floor + 2, cz + 11, Direction.NORTH, loot("chests/infernal_citadel"));
		b.chest(cx + 2, floor + 2, cz + 11, Direction.NORTH, loot("chests/infernal_citadel"));
		// Wither skeletons rise from the hall floor; blazes keep the roof.
		b.spawner(cx, floor, cz + 5, EntityTypes.WITHER_SKELETON);
		b.spawner(cx, roof + 1, cz + 6, EntityTypes.BLAZE);
		for (int[] corner : new int[][] {{-7, -1}, {7, -1}, {-7, 13}, {7, 13}}) {
			b.set(cx + corner[0], roof + 1, cz + corner[1], ModBlocks.HELLFIRE_BRAZIER.defaultBlockState());
		}
	}

	// --- the Bone Pit --------------------------------------------------------------------

	/**
	 * A great pit lined with bone and soul soil, its floor of soul sand burning with soul fire,
	 * where giant ribs curl up over the edge like the fingers of a buried hand. A chest sits on a
	 * mound of bones in the middle, and something keeps raising the dead down there.
	 */
	private void bonePit(Builder b) {
		int rim = cy;
		int bottom = cy - 10;
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int dx = -14; dx <= 14; dx++) {
			for (int dz = -14; dz <= 14; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!b.column(x, z)) {
					continue;
				}
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > 13.5) {
					continue;
				}
				for (int y = bottom - 1; y <= rim + 8; y++) {
					int depth = rim - y;
					if (y > rim) {
						if (r <= 11.5) {
							b.set(x, y, z, air);
						}
						continue;
					}
					double radius = 11.0 - Math.max(0, depth) * 0.42;
					if (y == bottom) {
						if (r < radius + 1.0) {
							b.set(x, y, z, Blocks.SOUL_SAND.defaultBlockState());
						}
					} else if (y == bottom - 1) {
						if (r < radius + 2.0) {
							b.set(x, y, z, Blocks.SOUL_SOIL.defaultBlockState());
						}
					} else if (r < radius) {
						b.set(x, y, z, air);
					} else if (r < radius + 1.7) {
						double roll = b.rand(x, y, z, 1);
						b.set(x, y, z, roll < 0.42 ? Blocks.BONE_BLOCK.defaultBlockState() : roll < 0.75 ? Blocks.SOUL_SOIL.defaultBlockState()
								: ModBlocks.ASH_BLOCK.defaultBlockState());
					}
				}
				// Soul fire, skulls and bone spikes on the floor of the pit.
				double radiusAtBottom = 11.0 - 10 * 0.42;
				if (r < radiusAtBottom - 0.5 && r > 3.5) {
					double roll = b.rand(x, bottom + 1, z, 2);
					if (roll < 0.1) {
						b.set(x, bottom + 1, z, Blocks.SOUL_FIRE.defaultBlockState());
					} else if (roll < 0.18) {
						b.set(x, bottom + 1, z, skull(b, x, bottom + 1, z, 0.25));
					} else if (roll < 0.3) {
						b.set(x, bottom + 1, z, ModBlocks.BONE_SPIKES.defaultBlockState());
					}
				}
				// The mound of bones in the middle.
				if (r <= 3.0) {
					int height = (int) Math.round(3.2 - r);
					for (int h = 1; h <= height; h++) {
						b.set(x, bottom + h, z, Blocks.BONE_BLOCK.defaultBlockState());
					}
				}
			}
		}
		b.chest(cx, bottom + 4, cz, Direction.SOUTH, loot("chests/bone_pit"));
		b.spawner(cx + 5, bottom + 1, cz, EntityTypes.WITHER_SKELETON);
		// Four giant ribs reach up out of the pit and curl in over it.
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 4 + i * Math.PI / 2;
			for (int t = 0; t <= 24; t++) {
				double f = t / 24.0;
				double radius = 7.0 - 3.0 * f * f;
				int x = cx + (int) Math.round(Math.cos(angle) * radius);
				int z = cz + (int) Math.round(Math.sin(angle) * radius);
				int y = bottom + 1 + (int) Math.round(f * 17);
				b.set(x, y, z, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
			}
		}
		// Skulls on pikes all round the rim.
		for (int i = 0; i < 10; i++) {
			double angle = i * Math.PI * 2 / 10;
			int x = cx + (int) Math.round(Math.cos(angle) * 12.5);
			int z = cz + (int) Math.round(Math.sin(angle) * 12.5);
			for (int h = 1; h <= 3; h++) {
				b.set(x, rim + h, z, Blocks.NETHER_BRICK_FENCE.defaultBlockState());
			}
			b.set(x, rim + 4, z, skull(b, x, rim + 4, z, 0.35));
		}
	}

	// --- the Ritual Circle ---------------------------------------------------------------

	/**
	 * A raised circle of black stone inlaid with rings and spokes of blood-red brick, ringed by
	 * pillars of obsidian crowned with braziers. On the altar at its heart lies whatever was
	 * offered last; caged skulls watch, and the dead stand guard.
	 */
	private void ritual(Builder b) {
		int top = cy + 1;
		RandomSource layout = RandomSource.create(seed);
		int[] heights = new int[8];
		for (int i = 0; i < 8; i++) {
			heights[i] = 4 + layout.nextInt(4);
		}
		for (int dx = -11; dx <= 11; dx++) {
			for (int dz = -11; dz <= 11; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!b.column(x, z)) {
					continue;
				}
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > 10.5) {
					continue;
				}
				b.foundation(x, top - 1, z, 14, Blocks.BLACKSTONE.defaultBlockState());
				double angle = Math.toDegrees(Math.atan2(dz, dx));
				double spoke = Math.abs(((angle % 45) + 45) % 45 - 22.5);
				BlockState stone;
				if (r > 9.5) {
					stone = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
				} else if (Math.abs(r - 6.0) < 0.5 || Math.abs(r - 3.0) < 0.5) {
					stone = b.rand(x, top, z, 1) < 0.2 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.RED_NETHER_BRICKS.defaultBlockState();
				} else if (spoke > 19.0 && r > 3.0) {
					stone = Blocks.RED_NETHER_BRICKS.defaultBlockState();
				} else if (b.rand(x, top, z, 2) < 0.07) {
					stone = Blocks.NETHER_WART_BLOCK.defaultBlockState();
				} else {
					stone = b.rand(x, top, z, 3) < 0.5 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
				}
				b.set(x, top, z, stone);
				for (int h = 1; h <= 9; h++) {
					b.set(x, top + h, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
		// Eight pillars of obsidian, each crowned with a brazier.
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * 2 * i / 8 + Math.PI / 8;
			int x = cx + (int) Math.round(Math.cos(angle) * 8.5);
			int z = cz + (int) Math.round(Math.sin(angle) * 8.5);
			for (int h = 1; h <= heights[i]; h++) {
				b.set(x, top + h, z, (h % 2 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState());
			}
			b.set(x, top + heights[i] + 1, z, (i % 2 == 0 ? ModBlocks.SOUL_BRAZIER : ModBlocks.HELLFIRE_BRAZIER).defaultBlockState());
		}
		// The altar, stained with blood, an offering on top.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				b.set(cx + dx, top + 1, cz + dz, dx == 0 && dz == 0 ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
						: Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
			}
		}
		for (int[] corner : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			b.set(cx + corner[0], top + 1, cz + corner[1], skull(b, cx + corner[0], top + 1, cz + corner[1], 0.5));
		}
		b.chest(cx, top + 2, cz, Direction.SOUTH, loot("chests/ritual_circle"));
		b.spawner(cx, top + 1, cz + 4, EntityTypes.WITHER_SKELETON);
		// Two cages at the edge, each with what is left of its prisoner.
		for (int side = -1; side <= 1; side += 2) {
			int x0 = cx + side * 6;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int x = x0 + dx;
					int z = cz + dz;
					b.set(x, top + 3, z, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
					if (dx == 0 && dz == 0) {
						b.set(x, top + 1, z, skull(b, x, top + 1, z, 0.2));
						continue;
					}
					BlockState bars = Blocks.IRON_BARS.defaultBlockState()
							.setValue(BlockStateProperties.NORTH, dz >= 0 && !(dx == 0 && dz == 1))
							.setValue(BlockStateProperties.SOUTH, dz <= 0 && !(dx == 0 && dz == -1))
							.setValue(BlockStateProperties.WEST, dx >= 0 && !(dz == 0 && dx == 1))
							.setValue(BlockStateProperties.EAST, dx <= 0 && !(dz == 0 && dx == -1));
					b.set(x, top + 1, z, bars);
					b.set(x, top + 2, z, bars);
				}
			}
		}
	}

	/** Places blocks of one structure inside one chunk. */
	private final class Builder {
		private final WorldGenLevel level;
		private final BoundingBox box;
		private final long seed;
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		Builder(WorldGenLevel level, BoundingBox box, long seed) {
			this.level = level;
			this.box = box;
			this.seed = seed;
		}

		boolean column(int x, int z) {
			return x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
		}

		BlockState get(int x, int y, int z) {
			if (!box.isInside(x, y, z)) {
				return Blocks.AIR.defaultBlockState();
			}
			return level.getBlockState(pos.set(x, y, z));
		}

		void set(int x, int y, int z, BlockState state) {
			if (!box.isInside(x, y, z) || y <= level.getMinY() || y >= level.getMaxY() - 1) {
				return;
			}
			level.setBlock(pos.set(x, y, z), state, 2);
		}

		/** Fills down from y until it meets solid ground, so nothing floats over a drop. */
		void foundation(int x, int y, int z, int reach, BlockState state) {
			for (int i = 0; i < reach; i++) {
				BlockState here = get(x, y - i, z);
				if (i > 0 && !here.isAir() && here.getFluidState().isEmpty()) {
					return;
				}
				set(x, y - i, z, state);
			}
		}

		void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> lootTable) {
			if (!box.isInside(x, y, z)) {
				return;
			}
			BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
			HellPiece.this.createChest(level, box, RandomSource.create(seed ^ x * 31L ^ z * 17L ^ y), new BlockPos(x, y, z), lootTable, chest);
		}

		void spawner(int x, int y, int z, EntityType<?> type) {
			if (!box.isInside(x, y, z)) {
				return;
			}
			set(x, y, z, Blocks.SPAWNER.defaultBlockState());
			if (level.getBlockEntity(pos.set(x, y, z)) instanceof SpawnerBlockEntity spawner) {
				spawner.setEntityId(type, RandomSource.create(seed ^ x ^ y ^ z));
			}
		}

		/** A random number in [0, 1) that depends only on the position. */
		double rand(int x, int y, int z, int salt) {
			long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ salt * 0x27D4EB2F165667C5L;
			h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
			h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
			h ^= h >>> 33;
			return (h >>> 11) * 0x1.0p-53;
		}
	}

	public enum Kind {
		CITADEL("infernal_citadel", 34, 92, 10, 14, 10, 26, -26, 28),
		BONE_PIT("bone_pit", 46, 100, 8, 10, 6, 15, -13, 19),
		RITUAL("ritual_circle", 34, 200, 8, 8, 5, 12, -15, 12);

		private final String id;
		private final int minFloor;
		private final int maxFloor;
		private final int headroom;
		private final int flatRadius;
		private final int maxSlope;
		private final int reach;
		private final int below;
		private final int above;

		Kind(String id, int minFloor, int maxFloor, int headroom, int flatRadius, int maxSlope, int reach, int below, int above) {
			this.id = id;
			this.minFloor = minFloor;
			this.maxFloor = maxFloor;
			this.headroom = headroom;
			this.flatRadius = flatRadius;
			this.maxSlope = maxSlope;
			this.reach = reach;
			this.below = below;
			this.above = above;
		}

		public String id() {
			return id;
		}

		int minFloor() {
			return minFloor;
		}

		int maxFloor() {
			return maxFloor;
		}

		int headroom() {
			return headroom;
		}

		int flatRadius() {
			return flatRadius;
		}

		int maxSlope() {
			return maxSlope;
		}

		BoundingBox bounds(BlockPos center) {
			return new BoundingBox(center.getX() - reach, center.getY() + below, center.getZ() - reach,
					center.getX() + reach, center.getY() + above, center.getZ() + reach);
		}

		static Kind byId(String id) {
			for (Kind kind : values()) {
				if (kind.id.equals(id)) {
					return kind;
				}
			}
			return CITADEL;
		}
	}
}
