package com.naturesmod.block;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.naturesmod.NaturesMod;
import com.naturesmod.abyss.AltarBlock;
import com.naturesmod.abyss.RiftBlock;
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
