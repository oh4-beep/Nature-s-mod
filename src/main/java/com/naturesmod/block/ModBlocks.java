package com.naturesmod.block;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.naturesmod.NaturesMod;
import com.naturesmod.abyss.AltarBlock;
import com.naturesmod.abyss.RiftBlock;
import com.naturesmod.nether.BoneSpikesBlock;
import com.naturesmod.nether.BrazierBlock;
import com.naturesmod.nether.BrimstoneBlock;
import com.naturesmod.nether.FleshBlock;
import com.naturesmod.nether.GeyserBlock;

/** Blocks added by Nature's Mod. */
public final class ModBlocks {
	public static final Block ABYSSAL_RIFT = register("abyssal_rift", RiftBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLUE)
			.noCollision()
			.strength(-1.0F, 3600000.0F)
			.noLootTable()
			.lightLevel(state -> 12)
			.sound(SoundType.GLASS));

	public static final Block NETHER_GEYSER = register("nether_geyser", GeyserBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.NETHER)
			.requiresCorrectToolForDrops()
			.strength(1.2F, 4.0F)
			.lightLevel(state -> 6)
			.randomTicks()
			.emissiveRendering(state -> true)
			.isValidSpawn((state, level, pos, type) -> type.fireImmune())
			.sound(SoundType.BASALT));

	// --- the sea floor ------------------------------------------------------------------------
	public static final Block RED_SEA_ANEMONE = register("red_sea_anemone", props -> new SeaPlantBlock(props, SeaPlantBlock.PLANT), plant(MapColor.COLOR_RED, 3));
	public static final Block PURPLE_SEA_ANEMONE = register("purple_sea_anemone", props -> new SeaPlantBlock(props, SeaPlantBlock.PLANT), plant(MapColor.COLOR_PURPLE, 3));
	public static final Block GREEN_SEA_ANEMONE = register("green_sea_anemone", props -> new SeaPlantBlock(props, SeaPlantBlock.PLANT), plant(MapColor.COLOR_LIGHT_GREEN, 3));
	public static final Block PURPLE_SEA_FAN = register("purple_sea_fan", props -> new SeaPlantBlock(props, SeaPlantBlock.PLANT), plant(MapColor.COLOR_MAGENTA, 0));
	public static final Block ORANGE_SEA_FAN = register("orange_sea_fan", props -> new SeaPlantBlock(props, SeaPlantBlock.PLANT), plant(MapColor.COLOR_ORANGE, 0));
	public static final Block TUBE_WORMS = register("tube_worms", props -> new SeaPlantBlock(props, SeaPlantBlock.PLANT), plant(MapColor.SNOW, 0));
	public static final Block ABYSSAL_GLOWMOSS = register("abyssal_glowmoss", props -> new SeaPlantBlock(props, SeaPlantBlock.PLANT), plant(MapColor.COLOR_CYAN, 10));
	public static final Block ORANGE_STARFISH = register("orange_starfish", props -> new SeaPlantBlock(props, SeaPlantBlock.FLAT), floor(MapColor.COLOR_ORANGE));
	public static final Block BLUE_STARFISH = register("blue_starfish", props -> new SeaPlantBlock(props, SeaPlantBlock.FLAT), floor(MapColor.COLOR_BLUE));
	public static final Block SEASHELLS = register("seashells", props -> new SeaPlantBlock(props, SeaPlantBlock.FLAT), floor(MapColor.SAND));
	public static final Block SEA_URCHIN = register("sea_urchin", SeaUrchinBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE)
			.noCollision()
			.strength(0.3F)
			.sound(SoundType.CORAL_BLOCK));
	public static final Block GIANT_CLAM = register("giant_clam", GiantClamBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE)
			.strength(1.0F)
			.randomTicks()
			.sound(SoundType.CORAL_BLOCK));
	public static final Block PEARL_BLOCK = register("pearl_block", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.SNOW)
			.strength(1.5F)
			.lightLevel(state -> 6)
			.sound(SoundType.AMETHYST));

	public static final Block ABYSSAL_ALTAR = register("abyssal_altar", AltarBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_CYAN)
			.strength(-1.0F, 3600000.0F)
			.noLootTable()
			.lightLevel(state -> 4 + 2 * AltarBlock.sigils(state) + (state.getValue(AltarBlock.AWAKENED) ? 3 : 0))
			.sound(SoundType.DEEPSLATE_BRICKS));

	// --- the hellish Nether ------------------------------------------------------------------
	public static final Block BRIMSTONE = register("brimstone", BrimstoneBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_YELLOW)
			.requiresCorrectToolForDrops()
			.strength(1.5F, 6.0F)
			.lightLevel(state -> 3)
			.isValidSpawn((state, level, pos, type) -> type.fireImmune())
			.sound(SoundType.NETHERRACK));
	public static final Block BRIMSTONE_BRICKS = register("brimstone_bricks", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_YELLOW)
			.requiresCorrectToolForDrops()
			.strength(2.0F, 6.0F)
			.sound(SoundType.NETHER_BRICKS));
	public static final Block SCORCHED_NETHERRACK = register("scorched_netherrack", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.NETHER)
			.requiresCorrectToolForDrops()
			.strength(0.6F)
			.lightLevel(state -> 2)
			.emissiveRendering(state -> true)
			.sound(SoundType.NETHERRACK));
	public static final Block SCORCHED_BRICKS = register("scorched_bricks", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.requiresCorrectToolForDrops()
			.strength(2.0F, 6.0F)
			.lightLevel(state -> 3)
			.emissiveRendering(state -> true)
			.sound(SoundType.NETHER_BRICKS));
	public static final Block ASH_BLOCK = register("ash_block", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_GRAY)
			.strength(0.5F)
			.sound(SoundType.SAND));
	public static final Block FLESH_BLOCK = register("flesh_block", FleshBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_RED)
			.strength(0.8F)
			.sound(SoundType.SLIME_BLOCK));
	public static final Block CHARRED_LOG = register("charred_log", RotatedPillarBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.strength(2.0F)
			.sound(SoundType.BASALT));
	public static final Block BONE_SPIKES = register("bone_spikes", BoneSpikesBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.SAND)
			.noCollision()
			.strength(0.8F)
			.sound(SoundType.BONE_BLOCK));
	public static final Block EMBER_CRYSTAL = register("ember_crystal", props -> new AmethystClusterBlock(7.0F, 10.0F, props), BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_ORANGE)
			.noOcclusion()
			.strength(1.5F)
			.lightLevel(state -> 10)
			.emissiveRendering(state -> true)
			.sound(SoundType.AMETHYST_CLUSTER));
	public static final Block HELLFIRE_BRAZIER = register("hellfire_brazier", props -> new BrazierBlock(props, false), brazier(15));
	public static final Block SOUL_BRAZIER = register("soul_brazier", props -> new BrazierBlock(props, true), brazier(10));

	/** Every block of the hellish Nether, in creative tab order. */
	public static final Block[] HELL = {BRIMSTONE, BRIMSTONE_BRICKS, SCORCHED_NETHERRACK, SCORCHED_BRICKS, ASH_BLOCK, FLESH_BLOCK, CHARRED_LOG,
			BONE_SPIKES, EMBER_CRYSTAL, HELLFIRE_BRAZIER, SOUL_BRAZIER};

	/** Every block of the sea floor, in creative tab order. */
	public static final Block[] SEA_FLOOR = {RED_SEA_ANEMONE, PURPLE_SEA_ANEMONE, GREEN_SEA_ANEMONE, PURPLE_SEA_FAN, ORANGE_SEA_FAN,
			TUBE_WORMS, ABYSSAL_GLOWMOSS, ORANGE_STARFISH, BLUE_STARFISH, SEASHELLS, SEA_URCHIN, GIANT_CLAM, PEARL_BLOCK};

	private ModBlocks() {
	}

	private static BlockBehaviour.Properties plant(MapColor color, int light) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.noCollision()
				.instabreak()
				.lightLevel(state -> light)
				.offsetType(BlockBehaviour.OffsetType.XZ)
				.sound(SoundType.WET_GRASS);
	}

	private static BlockBehaviour.Properties brazier(int light) {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL)
				.requiresCorrectToolForDrops()
				.strength(3.0F, 6.0F)
				.lightLevel(state -> light)
				.noOcclusion()
				.sound(SoundType.LANTERN);
	}

	private static BlockBehaviour.Properties floor(MapColor color) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.noCollision()
				.instabreak()
				.sound(SoundType.CORAL_BLOCK);
	}

	/** Forces the registrations above to run during mod initialisation. */
	public static void register() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, NaturesMod.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}
}
