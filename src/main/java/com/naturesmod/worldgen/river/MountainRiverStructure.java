package com.naturesmod.worldgen.river;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A river born from a spring high in the mountains. It follows the land downhill for hundreds of
 * blocks, cutting gorges through ridges and tumbling over waterfalls, until it reaches the sea or
 * a lake.
 */
public class MountainRiverStructure extends Structure {
	public static final MapCodec<MountainRiverStructure> CODEC = simpleCodec(MountainRiverStructure::new);
	/** Springs must sit at least this far above sea level. */
	private static final int MIN_SOURCE_RISE = 55;
	private static final int SEGMENTS_PER_PIECE = 10;

	public MountainRiverStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		ChunkGenerator generator = context.chunkGenerator();
		WorldgenRandom random = context.random();
		int seaLevel = generator.getSeaLevel();
		RiverPath.HeightSampler heights = (x, z) ->
				generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState()) - 1;

		// Start from the highest of a few spots in the chunk.
		int sourceX = chunk.getMiddleBlockX();
		int sourceZ = chunk.getMiddleBlockZ();
		int sourceHeight = heights.height(sourceX, sourceZ);
		for (int i = 0; i < 3; i++) {
			int x = chunk.getMinBlockX() + random.nextInt(16);
			int z = chunk.getMinBlockZ() + random.nextInt(16);
			int h = heights.height(x, z);
			if (h > sourceHeight) {
				sourceX = x;
				sourceZ = z;
				sourceHeight = h;
			}
		}
		if (sourceHeight < seaLevel + MIN_SOURCE_RISE) {
			return Optional.empty();
		}
		// The structure system checks the biome at the spring once we return it.
		RiverPath path = RiverPath.trace(heights, sourceX, sourceZ, sourceHeight, seaLevel, random);
		if (path == null) {
			return Optional.empty();
		}
		FeatureStats.placed("mountain_river");
		return Optional.of(new GenerationStub(new BlockPos(sourceX, sourceHeight, sourceZ), builder -> {
			int segments = path.size() - 1;
			for (int from = 0; from < segments; from += SEGMENTS_PER_PIECE) {
				builder.addPiece(new RiverPiece(path, from, Math.min(segments, from + SEGMENTS_PER_PIECE)));
			}
		}));
	}

	@Override
	public StructureType<?> type() {
		return ModStructures.MOUNTAIN_RIVER;
	}
}
