package com.naturesmod.worldgen;

import com.naturesmod.NaturesMod;
import com.naturesmod.worldgen.glacier.GlacierPiece;
import com.naturesmod.worldgen.glacier.GlacierStructure;
import com.naturesmod.worldgen.river.MountainRiverStructure;
import com.naturesmod.worldgen.river.RiverPiece;
import com.naturesmod.worldgen.sunken.SunkenPiece;
import com.naturesmod.worldgen.sunken.SunkenStructure;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** Registers the Nature's Mod structures and their pieces. */
public final class ModStructures {
	public static final StructurePieceType RIVER_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, NaturesMod.id("river"),
			(StructurePieceType.ContextlessType) RiverPiece::new);
	public static final StructureType<MountainRiverStructure> MOUNTAIN_RIVER = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			NaturesMod.id("mountain_river"), () -> MountainRiverStructure.CODEC);
	public static final StructurePieceType GLACIER_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, NaturesMod.id("glacier"),
			(StructurePieceType.ContextlessType) GlacierPiece::new);
	public static final StructureType<GlacierStructure> GLACIER = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			NaturesMod.id("glacier"), () -> GlacierStructure.CODEC);

	public static final StructurePieceType SUNKEN_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, NaturesMod.id("sunken"),
			(StructurePieceType.ContextlessType) SunkenPiece::new);
	public static final StructureType<SunkenStructure.Rift> ABYSSAL_RIFT = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			NaturesMod.id("abyssal_rift"), () -> SunkenStructure.Rift.CODEC);
	public static final StructureType<SunkenStructure.Temple> SUNKEN_TEMPLE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			NaturesMod.id("sunken_temple"), () -> SunkenStructure.Temple.CODEC);
	public static final StructureType<SunkenStructure.Bathysphere> BATHYSPHERE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			NaturesMod.id("bathysphere"), () -> SunkenStructure.Bathysphere.CODEC);
	public static final StructureType<SunkenStructure.Lair> LEVIATHAN_LAIR = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			NaturesMod.id("leviathan_lair"), () -> SunkenStructure.Lair.CODEC);

	private ModStructures() {
	}

	/** Forces the registrations above to run during mod initialisation. */
	public static void register() {
	}
}
