package com.naturesmod.worldgen.deep;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Builds a treasure of the deep on demand, for the tests that look at every kind of them. */
public final class DeepShowcase {
	private DeepShowcase() {
	}

	/** Somewhere near {@code from} (searching east and south) where the floor is low and level enough. */
	public static BlockPos levelSite(ServerLevel level, BlockPos from, int reach) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		for (int attempt = 0; attempt < 400; attempt++) {
			int x = from.getX() + (attempt % 20) * 24;
			int z = from.getZ() + (attempt / 20) * 24;
			int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
			if (floor < 30 || floor > DeepStructure.MAX_FLOOR) {
				continue;
			}
			int r = reach * 2 / 3;
			int low = floor;
			int high = floor;
			for (int[] offset : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}, {r, -r}, {-r, r}}) {
				int h = generator.getBaseHeight(x + offset[0], z + offset[1], Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
				low = Math.min(low, h);
				high = Math.max(high, h);
			}
			if (high - low <= 8) {
				return new BlockPos(x, floor, z);
			}
		}
		return null;
	}

	/** Generates the ground at a site and builds a kind of treasure on it, exactly as world generation would. */
	public static DeepPiece build(ServerLevel level, DeepKind kind, BlockPos site, Rotation rotation) {
		int reach = kind.tier().reach();
		for (int cx = (site.getX() - reach) >> 4; cx <= (site.getX() + reach) >> 4; cx++) {
			for (int cz = (site.getZ() - reach) >> 4; cz <= (site.getZ() + reach) >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
		int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, site.getX(), site.getZ()) - 1;
		BlockPos center = new BlockPos(site.getX(), floor, site.getZ());
		DeepPiece piece = new DeepPiece(kind, center, rotation, center.asLong());
		BoundingBox bounds = piece.getBoundingBox();
		// Like a chunk being decorated: the whole height of the columns the piece covers.
		BoundingBox columns = new BoundingBox(bounds.minX(), level.getMinY() + 1, bounds.minZ(), bounds.maxX(), level.getMaxY() - 1, bounds.maxZ());
		piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(), columns, new ChunkPos(center),
				center);
		return piece;
	}
}
