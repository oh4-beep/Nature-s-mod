package com.naturesmod.worldgen.glacier;

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
 * A valley glacier: a tongue of ice that pours from a snowy basin high on a great peak down the
 * valley below it, cracked by crevasses, flanked by moraines and ending in a meltwater lake.
 */
public class GlacierStructure extends Structure {
	public static final MapCodec<GlacierStructure> CODEC = simpleCodec(GlacierStructure::new);
	/** The ice gathers only above this height... */
	private static final int MIN_SOURCE = 300;
	/** ...and melts away below this one. */
	private static final int SNOUT = 215;
	private static final int SEGMENTS_PER_PIECE = 8;

	public GlacierStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		ChunkGenerator generator = context.chunkGenerator();
		WorldgenRandom random = context.random();
		GlacierPath.HeightSampler heights = (x, z) ->
				generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState()) - 1;

		// The basin sits at the highest of a few spots in the chunk.
		int sourceX = chunk.getMiddleBlockX();
		int sourceZ = chunk.getMiddleBlockZ();
		int sourceHeight = heights.height(sourceX, sourceZ);
		for (int i = 0; i < 4; i++) {
			int x = chunk.getMinBlockX() + random.nextInt(16);
			int z = chunk.getMinBlockZ() + random.nextInt(16);
			int h = heights.height(x, z);
			if (h > sourceHeight) {
				sourceX = x;
				sourceZ = z;
				sourceHeight = h;
			}
		}
		if (sourceHeight < MIN_SOURCE) {
			return Optional.empty();
		}
		GlacierPath path = GlacierPath.trace(heights, sourceX, sourceZ, sourceHeight, Math.max(SNOUT, generator.getSeaLevel() + 40), random);
		if (path == null) {
			return Optional.empty();
		}
		FeatureStats.placed("glacier");
		return Optional.of(new GenerationStub(new BlockPos(sourceX, sourceHeight, sourceZ), builder -> {
			int segments = path.size() - 1;
			for (int from = 0; from < segments; from += SEGMENTS_PER_PIECE) {
				builder.addPiece(new GlacierPiece(path, from, Math.min(segments, from + SEGMENTS_PER_PIECE)));
			}
		}));
	}

	@Override
	public StructureType<?> type() {
		return ModStructures.GLACIER;
	}
}
