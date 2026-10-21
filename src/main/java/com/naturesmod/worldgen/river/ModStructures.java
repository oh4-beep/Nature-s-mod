package com.naturesmod.worldgen.river;

import com.naturesmod.NaturesMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** Registers the mountain river structure and its pieces. */
public final class ModStructures {
	public static final StructurePieceType RIVER_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, NaturesMod.id("river"),
			(StructurePieceType.ContextlessType) RiverPiece::new);
	public static final StructureType<MountainRiverStructure> MOUNTAIN_RIVER = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			NaturesMod.id("mountain_river"), () -> MountainRiverStructure.CODEC);

	private ModStructures() {
	}

	/** Forces the registrations above to run during mod initialisation. */
	public static void register() {
	}
}
