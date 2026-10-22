package com.naturesmod.worldgen.deep;

import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The structures of the Overworld's lands, in three sizes: easy finds, harder halls and the great
 * dungeons, on ground that is level, dry and solid all the way round. Each picks a kind that suits
 * the climate it stands in when it is built (see {@link LandPiece}).
 */
public abstract class LandStructure extends Structure {
	private final DeepKind.Tier tier;

	protected LandStructure(StructureSettings settings, DeepKind.Tier tier) {
		super(settings);
		this.tier = tier;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		int ground = ground(context, x, z);
		// Dry land only: the ground is the surface.
		if (ground < context.chunkGenerator().getSeaLevel() - 1 || ground != surface(context, x, z)) {
			return Optional.empty();
		}
		int r = tier.flatRadius();
		int low = ground;
		int high = ground;
		int reach = tier.reach();
		for (int[] offset : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}, {r, -r}, {-r, r},
				{reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}}) {
			int h = ground(context, x + offset[0], z + offset[1]);
			if (h != surface(context, x + offset[0], z + offset[1])) {
				return Optional.empty();
			}
			low = Math.min(low, h);
			high = Math.max(high, h);
		}
		if (high - low > tier.maxSlope() + 2) {
			return Optional.empty();
		}
		WorldgenRandom random = context.random();
		Rotation rotation = Rotation.getRandom(random);
		long seed = random.nextLong();
		BlockPos center = new BlockPos(x, ground, z);
		FeatureStats.placed("land/" + tier.name().toLowerCase(Locale.ROOT), center);
		return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new LandPiece(tier, center, rotation, seed))));
	}

	/** Y of the top block of solid ground. */
	private static int ground(GenerationContext context, int x, int z) {
		return context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState()) - 1;
	}

	/** Y of the top block of anything, water included. */
	private static int surface(GenerationContext context, int x, int z) {
		return context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()) - 1;
	}

	@Override
	public StructureType<?> type() {
		return switch (tier) {
			case RUIN -> ModStructures.LAND_EASY;
			case VAULT -> ModStructures.LAND_MEDIUM;
			case WONDER -> ModStructures.LAND_HARD;
		};
	}

	public static final class Easy extends LandStructure {
		public static final MapCodec<Easy> CODEC = simpleCodec(Easy::new);

		public Easy(StructureSettings settings) {
			super(settings, DeepKind.Tier.RUIN);
		}
	}

	public static final class Medium extends LandStructure {
		public static final MapCodec<Medium> CODEC = simpleCodec(Medium::new);

		public Medium(StructureSettings settings) {
			super(settings, DeepKind.Tier.VAULT);
		}
	}

	public static final class Hard extends LandStructure {
		public static final MapCodec<Hard> CODEC = simpleCodec(Hard::new);

		public Hard(StructureSettings settings) {
			super(settings, DeepKind.Tier.WONDER);
		}
	}
}
