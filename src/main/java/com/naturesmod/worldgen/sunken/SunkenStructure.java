package com.naturesmod.worldgen.sunken;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * Structures on the floor of the sea: the Abyssal Rifts in the Overworld's deep oceans, and the
 * Sunken Temples, Bathysphere wrecks and Leviathan lairs of the Abyss. Each kind is a single
 * procedural piece; see {@link SunkenPiece}.
 */
public abstract class SunkenStructure extends Structure {
	private final SunkenPiece.Kind kind;

	protected SunkenStructure(StructureSettings settings, SunkenPiece.Kind kind) {
		super(settings);
		this.kind = kind;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		ChunkGenerator generator = context.chunkGenerator();
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		int floor = this.floor(context, x, z);
		int seaLevel = generator.getSeaLevel();
		if (seaLevel - floor < kind.minDepth() || floor > kind.maxFloor()) {
			return Optional.empty();
		}
		// The site must be fairly level.
		int r = kind.flatRadius();
		int low = floor;
		int high = floor;
		for (int[] offset : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}}) {
			int h = this.floor(context, x + offset[0], z + offset[1]);
			low = Math.min(low, h);
			high = Math.max(high, h);
		}
		if (high - low > kind.maxSlope() || seaLevel - high < kind.minDepth() / 2) {
			return Optional.empty();
		}
		BlockPos center = new BlockPos(x, floor, z);
		long seed = context.random().nextLong();
		FeatureStats.placed(kind.id(), center);
		return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new SunkenPiece(kind, center, seed))));
	}

	/** Y of the top block of the sea floor. */
	private int floor(GenerationContext context, int x, int z) {
		return context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState()) - 1;
	}

	@Override
	public StructureType<?> type() {
		return switch (kind) {
			case RIFT -> ModStructures.ABYSSAL_RIFT;
			case TEMPLE -> ModStructures.SUNKEN_TEMPLE;
			case BATHYSPHERE -> ModStructures.BATHYSPHERE;
			case LAIR -> ModStructures.LEVIATHAN_LAIR;
		};
	}

	public static final class Rift extends SunkenStructure {
		public static final MapCodec<Rift> CODEC = simpleCodec(Rift::new);

		public Rift(StructureSettings settings) {
			super(settings, SunkenPiece.Kind.RIFT);
		}
	}

	public static final class Temple extends SunkenStructure {
		public static final MapCodec<Temple> CODEC = simpleCodec(Temple::new);

		public Temple(StructureSettings settings) {
			super(settings, SunkenPiece.Kind.TEMPLE);
		}
	}

	public static final class Bathysphere extends SunkenStructure {
		public static final MapCodec<Bathysphere> CODEC = simpleCodec(Bathysphere::new);

		public Bathysphere(StructureSettings settings) {
			super(settings, SunkenPiece.Kind.BATHYSPHERE);
		}
	}

	public static final class Lair extends SunkenStructure {
		public static final MapCodec<Lair> CODEC = simpleCodec(Lair::new);

		public Lair(StructureSettings settings) {
			super(settings, SunkenPiece.Kind.LAIR);
		}
	}
}
