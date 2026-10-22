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
 * The forgotten riches on the floor of the Abyss: ruins, vaults and wonders, each of many kinds.
 * They are only found at the very bottom, below the last of the guardians, on ground that is
 * fairly level.
 */
public abstract class DeepStructure extends Structure {
	/** Only the true bottom of the Abyss: below the guardians, above the bedrock of the trenches. */
	public static final int MAX_FLOOR = 150;
	private static final int MIN_FLOOR = 18;
	private final DeepKind.Tier tier;

	protected DeepStructure(StructureSettings settings, DeepKind.Tier tier) {
		super(settings);
		this.tier = tier;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		int floor = floor(context, x, z);
		if (floor > MAX_FLOOR || floor < MIN_FLOOR) {
			return Optional.empty();
		}
		int r = tier.flatRadius();
		int low = floor;
		int high = floor;
		for (int[] offset : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}, {r, -r}, {-r, r}}) {
			int h = floor(context, x + offset[0], z + offset[1]);
			low = Math.min(low, h);
			high = Math.max(high, h);
		}
		if (high - low > tier.maxSlope()) {
			return Optional.empty();
		}
		WorldgenRandom random = context.random();
		DeepKind kind = DeepKind.pick(tier, random);
		Rotation rotation = Rotation.getRandom(random);
		long seed = random.nextLong();
		BlockPos center = new BlockPos(x, floor, z);
		FeatureStats.placed("deep/" + kind.id(), center);
		return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new DeepPiece(kind, center, rotation, seed))));
	}

	/** Y of the top block of the sea floor. */
	private static int floor(GenerationContext context, int x, int z) {
		return context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState()) - 1;
	}

	@Override
	public StructureType<?> type() {
		return switch (tier) {
			case RUIN -> ModStructures.DEEP_RUIN;
			case VAULT -> ModStructures.DEEP_VAULT;
			case WONDER -> ModStructures.DEEP_WONDER;
		};
	}

	public static final class Ruin extends DeepStructure {
		public static final MapCodec<Ruin> CODEC = simpleCodec(Ruin::new);

		public Ruin(StructureSettings settings) {
			super(settings, DeepKind.Tier.RUIN);
		}
	}

	public static final class Vault extends DeepStructure {
		public static final MapCodec<Vault> CODEC = simpleCodec(Vault::new);

		public Vault(StructureSettings settings) {
			super(settings, DeepKind.Tier.VAULT);
		}
	}

	public static final class Wonder extends DeepStructure {
		public static final MapCodec<Wonder> CODEC = simpleCodec(Wonder::new);

		public Wonder(StructureSettings settings) {
			super(settings, DeepKind.Tier.WONDER);
		}
	}
}
