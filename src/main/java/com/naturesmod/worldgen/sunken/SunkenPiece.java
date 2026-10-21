package com.naturesmod.worldgen.sunken;

import com.naturesmod.NaturesMod;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.creature.ModCreatures;
import com.naturesmod.worldgen.ModStructures;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Builds one of the sunken structures. The whole structure is laid out in every chunk it
 * touches and only the blocks inside that chunk are placed, so every per-block choice comes from
 * a hash of the block's position: the result is the same no matter which chunk builds it.
 */
public class SunkenPiece extends StructurePiece {
	private final Kind kind;
	private final int cx;
	private final int cy;
	private final int cz;
	private final long seed;

	SunkenPiece(Kind kind, BlockPos center, long seed) {
		super(ModStructures.SUNKEN_PIECE, 0, kind.bounds(center));
		this.kind = kind;
		this.cx = center.getX();
		this.cy = center.getY();
		this.cz = center.getZ();
		this.seed = seed;
	}

	public SunkenPiece(CompoundTag tag) {
		super(ModStructures.SUNKEN_PIECE, tag);
		this.kind = Kind.byId(tag.getStringOr("kind", "rift"));
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
			case RIFT -> rift(b);
			case TEMPLE -> temple(b);
			case BATHYSPHERE -> bathysphere(b);
			case LAIR -> lair(b, level, chunkBox);
			case SHRINE -> shrine(b);
			case SANCTUM -> sanctum(b);
		}
	}

	// --- the Vent Shrine -----------------------------------------------------------------

	/** A small blackstone shrine among the vents, half fallen in, where the Ember Sigil is kept. */
	private void shrine(Builder b) {
		BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState stone = Blocks.BLACKSTONE.defaultBlockState();
		BlockState water = Blocks.WATER.defaultBlockState();
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!b.column(x, z)) {
					continue;
				}
				int ax = Math.abs(dx);
				int az = Math.abs(dz);
				int ring = Math.max(ax, az);
				if (ring > 5) {
					continue;
				}
				int ground = b.ground(x, cy + 3, z, 14);
				for (int y = ground + 1; y <= cy; y++) {
					b.set(x, y, z, stone);
				}
				boolean vent = (ax + az) % 3 == 0 && ring >= 2 && ring <= 4;
				b.set(x, cy + 1, z, vent ? Blocks.MAGMA_BLOCK.defaultBlockState() : b.rand(x, cy + 1, z, 1) < 0.3 ? cracked : bricks);
				for (int h = 2; h <= 5; h++) {
					int y = cy + h;
					if (ax == 4 && az == 4) {
						// Corner pillars, each crowned with a soul lantern or a skull.
						b.set(x, y, z, h == 5 ? (b.rand(x, 0, z, 2) < 0.5 ? Blocks.SOUL_LANTERN.defaultBlockState()
								: Blocks.WITHER_SKELETON_SKULL.defaultBlockState()) : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
					} else if (ring == 5 && h <= 3 && !(dz == -5 && ax <= 1) && b.rand(x, y, z, 3) < 0.7) {
						b.set(x, y, z, b.rand(x, y, z, 4) < 0.35 ? cracked : bricks);
					} else {
						b.set(x, y, z, water);
					}
				}
			}
		}
		// The pedestal and its chest.
		b.set(cx, cy + 2, cz + 2, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
		b.set(cx - 1, cy + 2, cz + 2, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState());
		b.chest(cx, cy + 3, cz + 2, Direction.NORTH, loot("chests/vent_shrine"));
		b.set(cx, cy + 2, cz - 1, Blocks.MAGMA_BLOCK.defaultBlockState());
	}

	// --- the Drowned Sanctum -------------------------------------------------------------

	/**
	 * A vast drowned cathedral at the bottom of the Abyss: broken walls, great pillars, arches of
	 * bone overhead, and on a raised dais in the middle the Abyssal Altar with its four sockets.
	 */
	private void sanctum(Builder b) {
		BlockState tiles = Blocks.DEEPSLATE_TILES.defaultBlockState();
		BlockState bricks = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
		BlockState polished = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		BlockState dark = Blocks.DARK_PRISMARINE.defaultBlockState();
		BlockState lantern = Blocks.SEA_LANTERN.defaultBlockState();
		BlockState water = Blocks.WATER.defaultBlockState();
		RandomSource layout = RandomSource.create(seed);
		for (int dx = -17; dx <= 17; dx++) {
			for (int dz = -17; dz <= 17; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!b.column(x, z)) {
					continue;
				}
				int ax = Math.abs(dx);
				int az = Math.abs(dz);
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > 16.5) {
					continue;
				}
				// The floor, on a foundation down to the sea bed.
				int ground = b.ground(x, cy + 4, z, 20);
				for (int y = ground + 1; y <= cy; y++) {
					b.set(x, y, z, Blocks.DEEPSLATE.defaultBlockState());
				}
				b.set(x, cy + 1, z, r < 5 ? dark : (ax + az) % 7 == 0 ? lantern : b.rand(x, cy, z, 1) < 0.15 ? cracked : tiles);
				// Clear the nave of mud and kelp.
				for (int h = 2; h <= 22; h++) {
					if (!b.get(x, cy + h, z).isAir()) {
						b.set(x, cy + h, z, water);
					}
				}
				// The outer wall, broken down to stumps in places.
				if (r > 14.5) {
					double angle = Math.atan2(dz, dx);
					int height = 3 + (int) Math.round(6 * Math.abs(Math.sin(angle * 3.0))) + (b.rand(x, 0, z, 2) < 0.3 ? -3 : 0);
					boolean door = dz < 0 && ax <= 2;
					for (int h = 2; h <= Math.max(2, height); h++) {
						if (!door) {
							b.set(x, cy + h, z, b.crumble(x, cy + h, z, b.rand(x, cy + h, z, 3) < 0.3 ? cracked : bricks));
						}
					}
				}
				// The dais: two steps up to the altar.
				if (r < 5.5) {
					b.set(x, cy + 2, z, r < 4.5 ? dark : Blocks.DARK_PRISMARINE_STAIRS.defaultBlockState());
				}
				if (r < 3.5) {
					b.set(x, cy + 3, z, dark);
				}
			}
		}
		// Eight great pillars in a ring; some have fallen.
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * 2 * i / 8 + Math.PI / 8;
			int px = cx + (int) Math.round(Math.cos(angle) * 10);
			int pz = cz + (int) Math.round(Math.sin(angle) * 10);
			int height = layout.nextInt(4) == 0 ? 4 + layout.nextInt(4) : 13 + layout.nextInt(6);
			for (int ox = 0; ox <= 1; ox++) {
				for (int oz = 0; oz <= 1; oz++) {
					if (!b.column(px + ox, pz + oz)) {
						continue;
					}
					for (int h = 2; h <= height; h++) {
						b.set(px + ox, cy + h, pz + oz, h == height ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : polished);
					}
				}
			}
			if (height > 10 && b.column(px, pz)) {
				b.set(px, cy + height + 1, pz, lantern);
				b.set(px, cy + height + 2, pz, Blocks.SKELETON_SKULL.defaultBlockState());
			}
		}
		// Two arches of bone vault over the dais.
		for (int arch = 0; arch < 2; arch++) {
			for (int t = -9; t <= 9; t++) {
				int x = cx + (arch == 0 ? t : 0);
				int z = cz + (arch == 0 ? 0 : t);
				int y = cy + 3 + (int) Math.round(Math.sqrt(Math.max(0, 81 - t * t)) * 1.35);
				if (b.column(x, z)) {
					b.set(x, y, z, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, arch == 0 ? Direction.Axis.X : Direction.Axis.Z));
				}
			}
		}
		// Chains hang from the crossing of the arches down towards the altar.
		for (int h = 8; h <= 15; h++) {
			if (b.column(cx + 2, cz + 2)) {
				b.set(cx + 2, cy + h, cz + 2, Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
			}
		}
		// The altar, waiting for its four sigils, and the obelisks that watch it.
		if (b.column(cx, cz)) {
			b.set(cx, cy + 4, cz, ModBlocks.ABYSSAL_ALTAR.defaultBlockState());
		}
		for (int[] corner : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
			int x = cx + corner[0];
			int z = cz + corner[1];
			if (b.column(x, z)) {
				for (int h = 4; h <= 6; h++) {
					b.set(x, cy + h, z, h == 6 ? lantern : polished);
				}
			}
		}
		b.chest(cx - 7, cy + 2, cz + 9, Direction.NORTH, loot("chests/drowned_sanctum"));
		b.chest(cx + 7, cy + 2, cz + 9, Direction.NORTH, loot("chests/drowned_sanctum"));
	}

	// --- the Abyssal Rift ----------------------------------------------------------------

	private void rift(Builder b) {
		RandomSource layout = RandomSource.create(seed);
		int depth = 24 + layout.nextInt(9);
		double top = 11 + layout.nextInt(3);
		// A funnel narrowing down through the sea floor to the rift at the bottom.
		for (int dx = -16; dx <= 16; dx++) {
			for (int dz = -16; dz <= 16; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!b.column(x, z)) {
					continue;
				}
				for (int d = -4; d < depth; d++) {
					int y = cy - d;
					double f = Math.max(0, d) / (double) depth;
					double radius = 3.0 + (top - 3.0) * Math.pow(1.0 - f, 1.4);
					double dist = Math.sqrt(dx * dx + dz * dz) + b.rand(x, y, z, 1) * 0.9;
					if (dist < radius) {
						b.set(x, y, z, Blocks.WATER.defaultBlockState());
					} else if (d >= 0 && dist < radius + 1.7) {
						b.set(x, y, z, riftWall(b.rand(x, y, z, 2), d, depth));
					}
				}
				double dist = Math.sqrt(dx * dx + dz * dz);
				int y = cy - depth;
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					b.set(x, y, z, ModBlocks.ABYSSAL_RIFT.defaultBlockState());
				} else if (dist < 4.6) {
					b.set(x, y, z, (b.rand(x, y, z, 3) < 0.35 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState());
				}
				if (dist < 4.6) {
					b.set(x, y - 1, z, Blocks.OBSIDIAN.defaultBlockState());
				}
			}
		}
		// A ring of ancient pillars around the mouth of the funnel.
		int pillars = 8;
		double phase = layout.nextDouble() * Math.PI;
		int chestPillar = layout.nextInt(pillars);
		for (int i = 0; i < pillars; i++) {
			double angle = phase + Math.PI * 2 * i / pillars;
			int px = cx + (int) Math.round(Math.cos(angle) * (top + 3.5));
			int pz = cz + (int) Math.round(Math.sin(angle) * (top + 3.5));
			int height = 3 + layout.nextInt(6);
			boolean broken = layout.nextInt(3) == 0;
			if (!b.column(px, pz)) {
				continue;
			}
			int ground = b.ground(px, cy + 6, pz, 14);
			for (int h = 1; h <= height; h++) {
				boolean cap = h == height;
				BlockState state = cap && !broken ? Blocks.DARK_PRISMARINE.defaultBlockState()
						: b.rand(px, ground + h, pz, 4) < 0.2 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState();
				b.set(px, ground + h, pz, state);
			}
			if (!broken) {
				b.set(px, ground + height + 1, pz, Blocks.SEA_LANTERN.defaultBlockState());
			}
			if (i == chestPillar) {
				int chestX = cx + (int) Math.round(Math.cos(angle) * (top + 2.2));
				int chestZ = cz + (int) Math.round(Math.sin(angle) * (top + 2.2));
				if (b.column(chestX, chestZ)) {
					b.chest(chestX, b.ground(chestX, cy + 6, chestZ, 14) + 1, chestZ, Direction.NORTH, loot("chests/abyssal_rift"));
				}
			}
		}
	}

	private static BlockState riftWall(double roll, int d, int depth) {
		if (roll < 0.1) {
			return Blocks.CRYING_OBSIDIAN.defaultBlockState();
		}
		if (roll < 0.18) {
			return Blocks.OBSIDIAN.defaultBlockState();
		}
		if (roll < 0.23 && d < depth - 6) {
			// Magma pulls the water down into the rift in whirling currents.
			return Blocks.MAGMA_BLOCK.defaultBlockState();
		}
		if (roll < 0.26) {
			return Blocks.SEA_LANTERN.defaultBlockState();
		}
		if (roll < 0.42) {
			return Blocks.TUFF.defaultBlockState();
		}
		return Blocks.DEEPSLATE.defaultBlockState();
	}

	// --- the Sunken Temple ---------------------------------------------------------------

	private void temple(Builder b) {
		BlockState bricks = Blocks.PRISMARINE_BRICKS.defaultBlockState();
		BlockState dark = Blocks.DARK_PRISMARINE.defaultBlockState();
		BlockState lantern = Blocks.SEA_LANTERN.defaultBlockState();
		BlockState water = Blocks.WATER.defaultBlockState();
		for (int dx = -13; dx <= 13; dx++) {
			for (int dz = -13; dz <= 13; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!b.column(x, z)) {
					continue;
				}
				int ax = Math.abs(dx);
				int az = Math.abs(dz);
				int ring = Math.max(ax, az);
				if (ring <= 12) {
					// Foundation down to the sea floor, so the temple sits firmly on uneven ground.
					int ground = b.ground(x, cy + 2, z, 18);
					for (int y = ground + 1; y <= cy; y++) {
						b.set(x, y, z, dark);
					}
					b.set(x, cy + 1, z, ring == 12 ? dark : (ax + az) % 4 == 0 ? lantern : bricks);
				}
				// The great hall.
				if (ring <= 10) {
					for (int h = 2; h <= 6; h++) {
						int y = cy + h;
						BlockState state;
						if (h == 6) {
							state = ring == 10 ? dark : bricks;
						} else if (ring == 10) {
							boolean corner = ax == 10 && az == 10;
							boolean door = dz == -10 && ax <= 1 && h <= 4;
							boolean window = ring == 10 && h == 4 && (ax % 4 == 2 || az % 4 == 2) && !corner;
							state = door || window ? water : corner ? dark : bricks;
						} else {
							boolean column = (ax == 5 || ax == 6) && (az == 5 || az == 6);
							state = column ? (h == 5 ? lantern : dark) : water;
						}
						b.set(x, y, z, b.crumble(x, y, z, state));
					}
					// A mosaic floor inside.
					if (ring < 10) {
						b.set(x, cy + 1, z, (ax + az) % 2 == 0 ? dark : bricks);
					}
				}
				// The courtyard of the rift on the roof.
				if (ring <= 7) {
					for (int h = 7; h <= 10; h++) {
						int y = cy + h;
						if (ring == 7) {
							boolean merlon = h == 10 && (ax + az) % 2 == 1;
							b.set(x, y, z, b.crumble(x, y, z, h == 10 && !merlon ? water : dark));
						} else {
							b.set(x, y, z, water);
						}
					}
					boolean obelisk = ax == 4 && az == 4;
					if (obelisk) {
						for (int h = 7; h <= 11; h++) {
							b.set(x, cy + h, z, dark);
						}
						b.set(x, cy + 12, z, lantern);
					}
					if (ax <= 1 && az <= 1) {
						b.set(x, cy + 6, z, ModBlocks.ABYSSAL_RIFT.defaultBlockState());
					} else if (ax <= 2 && az <= 2) {
						b.set(x, cy + 6, z, Blocks.CRYING_OBSIDIAN.defaultBlockState());
					}
				}
			}
		}
		// Treasure against the back wall of the hall.
		b.chest(cx - 3, cy + 2, cz + 9, Direction.NORTH, loot("chests/sunken_temple"));
		b.chest(cx + 3, cy + 2, cz + 9, Direction.NORTH, loot("chests/sunken_temple"));
	}

	// --- the Bathysphere -----------------------------------------------------------------

	private void bathysphere(Builder b) {
		RandomSource layout = RandomSource.create(seed);
		int tether = 20 + layout.nextInt(70);
		double radius = 4.4;
		int my = cy + 3;
		BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
		BlockState copper = Blocks.PRISMARINE.defaultBlockState();
		BlockState glass = Blocks.GLASS.defaultBlockState();
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!b.column(x, z)) {
					continue;
				}
				for (int dy = -5; dy <= 5; dy++) {
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					int y = my + dy;
					if (d <= radius - 1.0) {
						BlockState inside = Blocks.AIR.defaultBlockState();
						if (dy == -3 && Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
							inside = Blocks.SPRUCE_PLANKS.defaultBlockState();
						}
						b.set(x, y, z, inside);
					} else if (d <= radius) {
						// Round portholes look out on the four sides and up through the hatch.
						boolean porthole = dy >= 0 && dy <= 1 && (Math.abs(dx) <= 1 && Math.abs(dz) >= 4 || Math.abs(dz) <= 1 && Math.abs(dx) >= 4);
						boolean hatch = dy >= 4 && Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
						BlockState shell = porthole || hatch ? glass : (dy == 0 || b.rand(x, y, z, 5) < 0.3 ? copper : iron);
						b.set(x, y, z, shell);
					}
				}
			}
		}
		// The cabin: a lamp, supplies and a chest of the crew's belongings.
		b.set(cx + 1, my - 2, cz + 1, Blocks.LANTERN.defaultBlockState());
		b.set(cx - 2, my - 2, cz + 1, Blocks.BARREL.defaultBlockState());
		b.chest(cx - 1, my - 2, cz - 1, Direction.SOUTH, loot("chests/bathysphere"));
		// The tether that once held it, snapped somewhere far above.
		BlockState chain = Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		if (b.column(cx, cz)) {
			for (int h = 6; h <= 5 + tether; h++) {
				BlockState here = b.get(cx, my + h, cz);
				if (!WorldGenUtil.isWater(here)) {
					break;
				}
				b.set(cx, my + h, cz, chain);
			}
		}
	}

	// --- the Leviathan's Lair ------------------------------------------------------------

	private void lair(Builder b, WorldGenLevel level, BoundingBox chunkBox) {
		RandomSource layout = RandomSource.create(seed);
		int radius = 18;
		BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
		// A great bowl scoured out of the sea floor.
		for (int dx = -radius - 3; dx <= radius + 3; dx++) {
			for (int dz = -radius - 3; dz <= radius + 3; dz++) {
				int x = cx + dx;
				int z = cz + dz;
				if (!b.column(x, z)) {
					continue;
				}
				double dist = Math.sqrt(dx * dx + dz * dz) + b.rand(x, 0, z, 6) * 1.2;
				if (dist < radius) {
					int bottom = cy - (int) Math.round(7 * (1 - Mth.square(dist / radius)));
					for (int y = bottom + 1; y <= cy + 6; y++) {
						b.set(x, y, z, Blocks.WATER.defaultBlockState());
					}
					double roll = b.rand(x, bottom, z, 7);
					b.set(x, bottom, z, (roll < 0.1 ? Blocks.BONE_BLOCK : roll < 0.45 ? Blocks.GRAVEL : Blocks.MUD).defaultBlockState());
				} else if (dist < radius + 3) {
					int ground = b.ground(x, cy + 6, z, 12);
					int rise = 1 + (int) Math.round(3 * (1 - Math.abs(dist - radius - 1.5) / 1.5));
					for (int h = 1; h <= rise; h++) {
						b.set(x, ground + h, z, (b.rand(x, ground + h, z, 8) < 0.3 ? Blocks.TUFF : Blocks.DEEPSLATE).defaultBlockState());
					}
				}
			}
		}
		// The ribcage of something colossal arches over the bowl.
		for (int rib = -3; rib <= 3; rib++) {
			int x = cx + rib * 4;
			double span = Math.sqrt(Math.max(0, radius * radius - (rib * 4) * (rib * 4))) - 1;
			int height = 10 + (int) Math.round(8 * (1 - Math.abs(rib) / 4.0));
			boolean broken = layout.nextInt(3) == 0;
			for (int dz = (int) -span; dz <= span; dz++) {
				double t = dz / span;
				if (broken && t > 0.3) {
					break;
				}
				int y = cy - 2 + (int) Math.round(height * (1 - t * t));
				for (int thick = 0; thick < 2; thick++) {
					if (b.column(x + thick, cz + dz)) {
						b.set(x + thick, y, cz + dz, bone.setValue(RotatedPillarBlock.AXIS, Math.abs(t) > 0.6 ? Direction.Axis.Y : Direction.Axis.Z));
						b.set(x + thick, y - 1, cz + dz, bone.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
					}
				}
			}
		}
		// The spine along the bottom of the bowl.
		for (int dx = -radius + 3; dx <= radius - 3; dx++) {
			if (b.column(cx + dx, cz)) {
				b.set(cx + dx, cy - 6, cz, bone.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
			}
		}
		// The hoard in the middle.
		for (int i = 0; i < 10; i++) {
			int x = cx + layout.nextInt(9) - 4;
			int z = cz + layout.nextInt(9) - 4;
			Block treasure = layout.nextInt(3) == 0 ? Blocks.GOLD_BLOCK : layout.nextBoolean() ? Blocks.RAW_GOLD_BLOCK : Blocks.SEA_LANTERN;
			if (b.column(x, z)) {
				b.set(x, b.ground(x, cy, z, 12) + 1, z, treasure.defaultBlockState());
			}
		}
		b.chest(cx, cy - 5, cz + 2, Direction.NORTH, loot("chests/leviathan_hoard"));
		b.chest(cx + 1, cy - 5, cz - 2, Direction.SOUTH, loot("chests/leviathan_hoard"));
		// And its keeper.
		BlockPos lairCenter = new BlockPos(cx, cy + 4, cz);
		if (chunkBox.isInside(lairCenter)) {
			EntityType<? extends Mob> type = ModCreatures.type("abyssal_leviathan");
			Mob leviathan = type == null ? null : type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
			if (leviathan != null) {
				leviathan.snapTo(cx + 0.5, cy + 4, cz + 0.5, 0.0F, 0.0F);
				leviathan.finalizeSpawn(level, level.getCurrentDifficultyAt(lairCenter), EntitySpawnReason.STRUCTURE, null);
				leviathan.setPersistenceRequired();
				level.addFreshEntityWithPassengers(leviathan);
			}
		}
	}

	private static ResourceKey<LootTable> loot(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, NaturesMod.id(path));
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

		/** Sets a block inside this chunk, waterlogging it when it replaces water. */
		void set(int x, int y, int z, BlockState state) {
			if (!box.isInside(x, y, z) || y <= level.getMinY()) {
				return;
			}
			pos.set(x, y, z);
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				state = state.setValue(BlockStateProperties.WATERLOGGED, WorldGenUtil.isWater(level.getBlockState(pos)));
			}
			level.setBlock(pos, state, 2);
		}

		/** Top of the solid ground in a column, searching down from {@code y}. */
		int ground(int x, int y, int z, int reach) {
			for (int i = 0; i < reach; i++) {
				BlockState state = get(x, y - i, z);
				if (!state.isAir() && state.getFluidState().isEmpty()) {
					return y - i;
				}
			}
			return y - reach;
		}

		/** Ruins are never perfectly whole: now and then a block has crumbled away. */
		BlockState crumble(int x, int y, int z, BlockState state) {
			if (!state.is(Blocks.WATER) && rand(x, y, z, 9) < 0.05) {
				return Blocks.WATER.defaultBlockState();
			}
			return state;
		}

		void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> lootTable) {
			if (!box.isInside(x, y, z)) {
				return;
			}
			BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
			if (chest.hasProperty(BlockStateProperties.WATERLOGGED)) {
				chest = chest.setValue(BlockStateProperties.WATERLOGGED, WorldGenUtil.isWater(get(x, y, z)));
			}
			SunkenPiece.this.createChest(level, box, RandomSource.create(seed ^ x * 31L ^ z * 17L ^ y), new BlockPos(x, y, z), lootTable, chest);
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
		RIFT("abyssal_rift", 12, 600, 12, 7, 16, -40, 14),
		TEMPLE("sunken_temple", 40, 700, 12, 9, 13, -20, 14),
		BATHYSPHERE("bathysphere", 30, 1000, 5, 5, 6, -3, 98),
		LAIR("leviathan_lair", 60, 420, 16, 10, 22, -10, 22),
		SHRINE("vent_shrine", 40, 400, 6, 7, 8, -14, 10),
		SANCTUM("drowned_sanctum", 200, 180, 14, 14, 18, -20, 26);

		private final String id;
		private final int minDepth;
		private final int maxFloor;
		private final int flatRadius;
		private final int maxSlope;
		private final int reach;
		private final int below;
		private final int above;

		Kind(String id, int minDepth, int maxFloor, int flatRadius, int maxSlope, int reach, int below, int above) {
			this.id = id;
			this.minDepth = minDepth;
			this.maxFloor = maxFloor;
			this.flatRadius = flatRadius;
			this.maxSlope = maxSlope;
			this.reach = reach;
			this.below = below;
			this.above = above;
		}

		public String id() {
			return id;
		}

		int minDepth() {
			return minDepth;
		}

		int maxFloor() {
			return maxFloor;
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
			return RIFT;
		}
	}
}
