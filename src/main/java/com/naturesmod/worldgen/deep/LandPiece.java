package com.naturesmod.worldgen.deep;

import com.naturesmod.worldgen.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One structure of the Overworld, drawn by its {@link LandKind} in every chunk it touches. A
 * structure placed by world generation knows only its tier at first: the first chunk to build it
 * reads the climate at its middle and picks the kind that suits it, and every other chunk comes to
 * the same choice from the same seed.
 */
public class LandPiece extends StructurePiece {
	private final DeepKind.Tier tier;
	private final int cx;
	private final int cy;
	private final int cz;
	private final Rotation rotation;
	private final long seed;
	private volatile LandKind kind;
	private volatile Climate climate;

	/** A structure of a tier, its kind to be chosen for the climate it stands in. */
	public LandPiece(DeepKind.Tier tier, BlockPos center, Rotation rotation, long seed) {
		super(ModStructures.LAND_PIECE, 0, LandKind.bounds(tier, center));
		this.tier = tier;
		this.cx = center.getX();
		this.cy = center.getY();
		this.cz = center.getZ();
		this.rotation = rotation;
		this.seed = seed;
	}

	/** A structure of a given kind, built as if it stood in the given climate. */
	public LandPiece(LandKind kind, BlockPos center, Rotation rotation, long seed, Climate climate) {
		this(kind.tier(), center, rotation, seed);
		this.kind = kind;
		this.climate = climate;
	}

	public LandPiece(CompoundTag tag) {
		super(ModStructures.LAND_PIECE, tag);
		this.tier = tier(tag.getStringOr("tier", ""));
		this.cx = tag.getIntOr("cx", 0);
		this.cy = tag.getIntOr("cy", 0);
		this.cz = tag.getIntOr("cz", 0);
		this.rotation = Rotation.values()[Math.floorMod(tag.getIntOr("rotation", 0), Rotation.values().length)];
		this.seed = tag.getLongOr("seed", 0L);
		String kind = tag.getStringOr("kind", "");
		this.kind = kind.isEmpty() ? null : LandKind.byId(kind);
		String climate = tag.getStringOr("climate", "");
		this.climate = climate.isEmpty() ? null : Climate.byName(climate);
	}

	private static DeepKind.Tier tier(String name) {
		for (DeepKind.Tier tier : DeepKind.Tier.values()) {
			if (tier.name().equals(name)) {
				return tier;
			}
		}
		return DeepKind.Tier.RUIN;
	}

	/** The kind of structure this is, once it has been built or was given one; null until then. */
	public LandKind kind() {
		return kind;
	}

	/** The climate it was built for, once it has been built or was given one; null until then. */
	public Climate climate() {
		return climate;
	}

	/** The top of the ground at the middle of the structure. */
	public BlockPos center() {
		return new BlockPos(cx, cy, cz);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("tier", tier.name());
		tag.putInt("cx", cx);
		tag.putInt("cy", cy);
		tag.putInt("cz", cz);
		tag.putInt("rotation", rotation.ordinal());
		tag.putLong("seed", seed);
		LandKind kind = this.kind;
		if (kind != null) {
			tag.putString("kind", kind.id());
		}
		Climate climate = this.climate;
		if (climate != null) {
			tag.putString("climate", climate.name());
		}
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		Climate climate = this.climate;
		if (climate == null) {
			climate = Climate.of(level.getBiome(center()));
			this.climate = climate;
		}
		LandKind kind = this.kind;
		if (kind == null) {
			kind = LandKind.pick(tier, climate, RandomSource.create(seed));
			this.kind = kind;
		}
		kind.build(new DeepBuilder(level, chunkBox, cx, cy, cz, rotation, seed, "chests/land/", Blocks.AIR.defaultBlockState()).withClimate(climate));
	}
}
