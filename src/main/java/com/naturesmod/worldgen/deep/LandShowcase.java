package com.naturesmod.worldgen.deep;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Builds the structures of the Overworld on demand, for the tests that look at every kind of them. */
public final class LandShowcase {
	private LandShowcase() {
	}

	/** Somewhere near {@code from} (searching east and south) on dry land, level enough to build on. */
	public static BlockPos drySite(ServerLevel level, BlockPos from, int reach) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		int sea = generator.getSeaLevel();
		for (int attempt = 0; attempt < 400; attempt++) {
			int x = from.getX() + (attempt % 20) * 32;
			int z = from.getZ() + (attempt / 20) * 32;
			int ground = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
			if (ground < sea || ground != generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random) - 1) {
				continue;
			}
			int r = reach * 2 / 3;
			int low = ground;
			int high = ground;
			boolean dry = true;
			for (int[] offset : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}, {r, -r}, {-r, r}}) {
				int h = generator.getBaseHeight(x + offset[0], z + offset[1], Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
				dry &= h >= sea && h == generator.getBaseHeight(x + offset[0], z + offset[1], Heightmap.Types.WORLD_SURFACE_WG, level, random) - 1;
				low = Math.min(low, h);
				high = Math.max(high, h);
			}
			if (dry && high - low <= 10) {
				return new BlockPos(x, ground, z);
			}
		}
		return null;
	}

	/** Generates the land around a site and builds a kind of structure on it, exactly as world generation would. */
	public static LandPiece build(ServerLevel level, LandKind kind, BlockPos site, Rotation rotation) {
		BoundingBox reach = kind.bounds(site);
		for (int cx = reach.minX() >> 4; cx <= reach.maxX() >> 4; cx++) {
			for (int cz = reach.minZ() >> 4; cz <= reach.maxZ() >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		int ground = generator.getBaseHeight(site.getX(), site.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState()) - 1;
		BlockPos center = new BlockPos(site.getX(), ground, site.getZ());
		LandPiece piece = new LandPiece(kind, center, rotation, center.asLong(), Climate.of(level.getBiome(center)));
		BoundingBox bounds = piece.getBoundingBox();
		// Like a chunk being decorated: the whole height of the columns the piece covers.
		BoundingBox columns = new BoundingBox(bounds.minX(), level.getMinY() + 1, bounds.minZ(), bounds.maxX(), level.getMaxY() - 1, bounds.maxZ());
		piece.postProcess(level, level.structureManager(), generator, level.getRandom(), columns, new ChunkPos(center.getX() >> 4, center.getZ() >> 4),
				center);
		return piece;
	}
}
