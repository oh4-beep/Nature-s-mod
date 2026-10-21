package com.naturesmod.worldgen.hell;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The strongholds of Hell in the Nether: Infernal Citadels, Bone Pits and Ritual Circles. Each
 * looks for a cavern floor with room above it, fairly level all round, in a band of heights.
 */
public abstract class HellStructure extends Structure {
	private static final int NONE = Integer.MIN_VALUE;
	private final HellPiece.Kind kind;

	protected HellStructure(StructureSettings settings, HellPiece.Kind kind) {
		super(settings);
		this.kind = kind;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		ChunkGenerator generator = context.chunkGenerator();
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		NoiseColumn column = generator.getBaseColumn(x, z, context.heightAccessor(), context.randomState());
		int floor = NONE;
		for (int y = kind.minFloor(); y <= kind.maxFloor() && floor == NONE; y++) {
			if (isFloor(column, y, kind.headroom())) {
				floor = y;
			}
		}
		if (floor == NONE) {
			return Optional.empty();
		}
		// The ground must be there all round, not too far above or below.
		int r = kind.flatRadius();
		for (int[] offset : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
			NoiseColumn around = generator.getBaseColumn(x + offset[0], z + offset[1], context.heightAccessor(), context.randomState());
			boolean found = false;
			for (int y = floor - kind.maxSlope(); y <= floor + kind.maxSlope() && !found; y++) {
				found = isFloor(around, y, 3);
			}
			if (!found) {
				return Optional.empty();
			}
		}
		BlockPos center = new BlockPos(x, floor, z);
		long seed = context.random().nextLong();
		FeatureStats.placed(kind.id(), center);
		return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new HellPiece(kind, center, seed))));
	}

	/** Solid ground at y with open air for the given height above it. */
	private static boolean isFloor(NoiseColumn column, int y, int headroom) {
		BlockState ground = column.getBlock(y);
		if (ground.isAir() || !ground.getFluidState().isEmpty()) {
			return false;
		}
		for (int h = 1; h <= headroom; h++) {
			if (!column.getBlock(y + h).isAir()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public StructureType<?> type() {
		return switch (kind) {
			case CITADEL -> ModStructures.INFERNAL_CITADEL;
			case BONE_PIT -> ModStructures.BONE_PIT;
			case RITUAL -> ModStructures.RITUAL_CIRCLE;
		};
	}

	public static final class Citadel extends HellStructure {
		public static final MapCodec<Citadel> CODEC = simpleCodec(Citadel::new);

		public Citadel(StructureSettings settings) {
			super(settings, HellPiece.Kind.CITADEL);
		}
	}

	public static final class BonePit extends HellStructure {
		public static final MapCodec<BonePit> CODEC = simpleCodec(BonePit::new);

		public BonePit(StructureSettings settings) {
			super(settings, HellPiece.Kind.BONE_PIT);
		}
	}

	public static final class Ritual extends HellStructure {
		public static final MapCodec<Ritual> CODEC = simpleCodec(Ritual::new);

		public Ritual(StructureSettings settings) {
			super(settings, HellPiece.Kind.RITUAL);
		}
	}
}
