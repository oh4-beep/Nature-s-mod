package com.naturesmod.worldgen.deep;

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
 * The structures of the new End: outposts, halls and citadels on the continents beyond the void
 * ring, wherever the land is level enough to hold them.
 */
public abstract class EndStructure extends Structure {
	/** The dragon's island and the void ring around it are left alone. */
	private static final int RING = 1100;
	private final DeepKind.Tier tier;

	protected EndStructure(StructureSettings settings, DeepKind.Tier tier) {
		super(settings);
		this.tier = tier;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		if ((long) x * x + (long) z * z < (long) RING * RING) {
			return Optional.empty();
		}
		int ground = ground(context, x, z);
		if (ground < 30 || ground > 220) {
			return Optional.empty();
		}
		int r = tier.flatRadius();
		int low = ground;
		int high = ground;
		for (int[] offset : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}, {r, -r}, {-r, r}}) {
			int h = ground(context, x + offset[0], z + offset[1]);
			low = Math.min(low, h);
			high = Math.max(high, h);
		}
		// Level enough, and land all the way round: nothing hangs out over the void.
		if (high - low > tier.maxSlope() || low < 30) {
			return Optional.empty();
		}
		int reach = tier.reach();
		for (int[] offset : new int[][] {{reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}, {reach, reach}, {-reach, -reach}, {reach, -reach},
				{-reach, reach}}) {
			if (ground(context, x + offset[0], z + offset[1]) < low - tier.maxSlope() * 2) {
				return Optional.empty();
			}
		}
		WorldgenRandom random = context.random();
		EndKind kind = EndKind.pick(tier, random);
		Rotation rotation = Rotation.getRandom(random);
		long seed = random.nextLong();
		BlockPos center = new BlockPos(x, ground, z);
		FeatureStats.placed("end/" + kind.id(), center);
		return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new EndPiece(kind, center, rotation, seed))));
	}

	/** Y of the top block of the ground; far below anything when the column is open void. */
	private static int ground(GenerationContext context, int x, int z) {
		return context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()) - 1;
	}

	@Override
	public StructureType<?> type() {
		return switch (tier) {
			case RUIN -> ModStructures.END_OUTPOST;
			case VAULT -> ModStructures.END_HALL;
			case WONDER -> ModStructures.END_CITADEL;
		};
	}

	public static final class Outpost extends EndStructure {
		public static final MapCodec<Outpost> CODEC = simpleCodec(Outpost::new);

		public Outpost(StructureSettings settings) {
			super(settings, DeepKind.Tier.RUIN);
		}
	}

	public static final class Hall extends EndStructure {
		public static final MapCodec<Hall> CODEC = simpleCodec(Hall::new);

		public Hall(StructureSettings settings) {
			super(settings, DeepKind.Tier.VAULT);
		}
	}

	public static final class Citadel extends EndStructure {
		public static final MapCodec<Citadel> CODEC = simpleCodec(Citadel::new);

		public Citadel(StructureSettings settings) {
			super(settings, DeepKind.Tier.WONDER);
		}
	}
}
