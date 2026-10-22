package com.naturesmod.worldgen.deep;

import com.naturesmod.worldgen.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** One treasure of the deep, drawn by its {@link DeepKind} in every chunk it touches. */
public class DeepPiece extends StructurePiece {
	private final DeepKind kind;
	private final int cx;
	private final int cy;
	private final int cz;
	private final Rotation rotation;
	private final long seed;

	public DeepPiece(DeepKind kind, BlockPos center, Rotation rotation, long seed) {
		super(ModStructures.DEEP_PIECE, 0, kind.tier().bounds(center));
		this.kind = kind;
		this.cx = center.getX();
		this.cy = center.getY();
		this.cz = center.getZ();
		this.rotation = rotation;
		this.seed = seed;
	}

	public DeepPiece(CompoundTag tag) {
		super(ModStructures.DEEP_PIECE, tag);
		this.kind = DeepKind.byId(tag.getStringOr("kind", ""));
		this.cx = tag.getIntOr("cx", 0);
		this.cy = tag.getIntOr("cy", 0);
		this.cz = tag.getIntOr("cz", 0);
		this.rotation = Rotation.values()[Math.floorMod(tag.getIntOr("rotation", 0), Rotation.values().length)];
		this.seed = tag.getLongOr("seed", 0L);
	}

	public DeepKind kind() {
		return kind;
	}

	/** The top of the sea floor at the middle of the structure. */
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
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		kind.build(new DeepBuilder(level, chunkBox, cx, cy, cz, rotation, seed));
	}
}
