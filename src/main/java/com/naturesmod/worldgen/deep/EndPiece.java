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

/** One structure of the new End, drawn by its {@link EndKind} in every chunk it touches. */
public class EndPiece extends StructurePiece {
	private final EndKind kind;
	private final int cx;
	private final int cy;
	private final int cz;
	private final Rotation rotation;
	private final long seed;

	public EndPiece(EndKind kind, BlockPos center, Rotation rotation, long seed) {
		super(ModStructures.END_PIECE, 0, kind.bounds(center));
		this.kind = kind;
		this.cx = center.getX();
		this.cy = center.getY();
		this.cz = center.getZ();
		this.rotation = rotation;
		this.seed = seed;
	}

	public EndPiece(CompoundTag tag) {
		super(ModStructures.END_PIECE, tag);
		this.kind = EndKind.byId(tag.getStringOr("kind", ""));
		this.cx = tag.getIntOr("cx", 0);
		this.cy = tag.getIntOr("cy", 0);
		this.cz = tag.getIntOr("cz", 0);
		this.rotation = Rotation.values()[Math.floorMod(tag.getIntOr("rotation", 0), Rotation.values().length)];
		this.seed = tag.getLongOr("seed", 0L);
	}

	public EndKind kind() {
		return kind;
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
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		kind.build(new DeepBuilder(level, chunkBox, cx, cy, cz, rotation, seed, "chests/end/", Blocks.AIR.defaultBlockState()));
	}
}
