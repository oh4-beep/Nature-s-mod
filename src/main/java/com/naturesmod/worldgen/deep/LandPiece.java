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

/** One structure of the Overworld, drawn by its {@link LandKind} in every chunk it touches. */
public class LandPiece extends StructurePiece {
	private final LandKind kind;
	private final int cx;
	private final int cy;
	private final int cz;
	private final Rotation rotation;
	private final long seed;
	private final Climate climate;

	public LandPiece(LandKind kind, BlockPos center, Rotation rotation, long seed, Climate climate) {
		super(ModStructures.LAND_PIECE, 0, kind.bounds(center));
		this.kind = kind;
		this.cx = center.getX();
		this.cy = center.getY();
		this.cz = center.getZ();
		this.rotation = rotation;
		this.seed = seed;
		this.climate = climate;
	}

	public LandPiece(CompoundTag tag) {
		super(ModStructures.LAND_PIECE, tag);
		this.kind = LandKind.byId(tag.getStringOr("kind", ""));
		this.cx = tag.getIntOr("cx", 0);
		this.cy = tag.getIntOr("cy", 0);
		this.cz = tag.getIntOr("cz", 0);
		this.rotation = Rotation.values()[Math.floorMod(tag.getIntOr("rotation", 0), Rotation.values().length)];
		this.seed = tag.getLongOr("seed", 0L);
		this.climate = Climate.byName(tag.getStringOr("climate", ""));
	}

	public LandKind kind() {
		return kind;
	}

	public Climate climate() {
		return climate;
	}

	/** The top of the ground at the middle of the structure. */
	public BlockPos center() {
		return new BlockPos(cx, cy, cz);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("kind", kind.id());
		tag.putInt("cx", cx);
		tag.putInt("cy", cy);
		tag.putInt("cz", cz);
		tag.putInt("rotation", rotation.ordinal());
		tag.putLong("seed", seed);
		tag.putString("climate", climate.name());
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		kind.build(new DeepBuilder(level, chunkBox, cx, cy, cz, rotation, seed, "chests/land/", Blocks.AIR.defaultBlockState()).withClimate(climate));
	}
}
