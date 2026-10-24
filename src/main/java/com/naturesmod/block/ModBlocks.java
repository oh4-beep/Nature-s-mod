package com.naturesmod.block;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import com.naturesmod.NaturesMod;
import com.naturesmod.abyss.AbyssalEnchantingTableBlock;
import com.naturesmod.abyss.AltarBlock;
import com.naturesmod.abyss.RiftBlock;
import com.naturesmod.end.BounceBloomBlock;
import com.naturesmod.end.EndPlantBlock;
import com.naturesmod.end.HangingVineBlock;
import com.naturesmod.end.StarAltarBlock;
import com.naturesmod.end.UpdraftVentBlock;
import com.naturesmod.item.ModItems;
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

	public static final Block ABYSSAL_ENCHANTING_TABLE = register("abyssal_enchanting_table", AbyssalEnchantingTableBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_CYAN)
					.requiresCorrectToolForDrops()
					.strength(5.0F, 1200.0F)
					.lightLevel(state -> 12)
					.noOcclusion()
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
	/** Volcanic ash settling over the land like snow, layer by layer; it doesn't melt. */
	public static final Block ASH_LAYER = register("ash_layer", SnowLayerBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_GRAY)
			.replaceable()
			.forceSolidOff()
			.strength(0.1F)
			.sound(SoundType.SAND)
			.isViewBlocking((state, level, pos) -> state.getValue(SnowLayerBlock.LAYERS) >= 8)
			.pushReaction(PushReaction.DESTROY));
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

	// --- the new End -----------------------------------------------------------------------------
	public static final Block END_MOSS = register("end_moss", Block::new, stone(MapColor.COLOR_PURPLE, 3.0F, SoundType.MOSS));
	public static final Block VOIDSTONE = register("voidstone", Block::new, stone(MapColor.COLOR_BLACK, 3.0F, SoundType.DEEPSLATE));
	public static final Block VOIDSTONE_BRICKS = register("voidstone_bricks", Block::new, stone(MapColor.COLOR_BLACK, 3.0F, SoundType.DEEPSLATE_BRICKS));
	public static final Block POLISHED_VOIDSTONE = register("polished_voidstone", Block::new, stone(MapColor.COLOR_BLACK, 3.0F, SoundType.DEEPSLATE_BRICKS));
	public static final Block VOID_CRYSTAL = register("void_crystal", Block::new, stone(MapColor.COLOR_LIGHT_BLUE, 1.5F, SoundType.AMETHYST)
			.lightLevel(state -> 12));
	public static final Block VOID_CRYSTAL_CLUSTER = register("void_crystal_cluster", props -> new AmethystClusterBlock(7.0F, 10.0F, props),
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_LIGHT_BLUE)
					.noOcclusion()
					.strength(1.5F)
					.lightLevel(state -> 9)
					.emissiveRendering(state -> true)
					.sound(SoundType.AMETHYST_CLUSTER));
	public static final Block STARMETAL_ORE = register("starmetal_ore", Block::new, stone(MapColor.SAND, 4.0F, SoundType.STONE).lightLevel(state -> 3));
	public static final Block STARMETAL_BLOCK = register("starmetal_block", Block::new, stone(MapColor.METAL, 5.0F, SoundType.METAL)
			.lightLevel(state -> 6));
	public static final Block STARDUST = register("stardust", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.QUARTZ)
			.strength(0.5F)
			.lightLevel(state -> 2)
			.sound(SoundType.SAND));
	public static final Block METEORITE = register("meteorite", Block::new, stone(MapColor.COLOR_BLACK, 5.0F, SoundType.ANCIENT_DEBRIS));
	public static final Block CHORUS_LOG = register("chorus_log", RotatedPillarBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE)
			.strength(2.0F)
			.sound(SoundType.STEM));
	public static final Block CHORUS_BULB = register("chorus_bulb", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_MAGENTA)
			.strength(1.0F)
			.lightLevel(state -> 14)
			.sound(SoundType.SHROOMLIGHT));
	public static final Block BOUNCE_STEM = register("bounce_stem", RotatedPillarBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.TERRACOTTA_WHITE)
			.strength(1.5F)
			.sound(SoundType.STEM));
	public static final Block BOUNCE_BLOOM = register("bounce_bloom", BounceBloomBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PINK)
			.strength(0.8F)
			.lightLevel(state -> 10)
			.sound(SoundType.SLIME_BLOCK));
	public static final Block UPDRAFT_VENT = register("updraft_vent", UpdraftVentBlock::new, stone(MapColor.COLOR_BLACK, 3.0F, SoundType.DEEPSLATE)
			.lightLevel(state -> 7));
	public static final Block ENDER_GRASS = register("ender_grass", props -> new EndPlantBlock(props, false), endPlant(MapColor.COLOR_PURPLE, 0, true));
	public static final Block VOID_BLOOM = register("void_bloom", props -> new EndPlantBlock(props, true), endPlant(MapColor.COLOR_MAGENTA, 9, true));
	public static final Block VOID_VINES = register("void_vines", HangingVineBlock::new, endPlant(MapColor.COLOR_CYAN, 7, false));
	public static final Block STAR_ALTAR = register("star_altar", StarAltarBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE)
			.requiresCorrectToolForDrops()
			.strength(30.0F, 1200.0F)
			.lightLevel(state -> 4 + 2 * state.getValue(StarAltarBlock.FRAGMENTS))
			.noOcclusion()
			.sound(SoundType.DEEPSLATE_BRICKS));

	// --- the wild Overworld ---------------------------------------------------------------------
	public static final Block LAVENDER = register("lavender", props -> new FlowerBlock(MobEffects.REGENERATION, 6.0F, props), flower(MapColor.COLOR_PURPLE));
	public static final Block HEATHER = register("heather", props -> new FlowerBlock(MobEffects.NIGHT_VISION, 5.0F, props), flower(MapColor.COLOR_MAGENTA));
	public static final Block CATTAIL = register("cattail", DoublePlantBlock::new, flower(MapColor.COLOR_BROWN).ignitedByLava());
	public static final Block GLOWCAP = register("glowcap", props -> new WildPlantBlock(props, 8.0, true), flower(MapColor.COLOR_LIGHT_BLUE)
			.lightLevel(state -> 9).emissiveRendering(state -> true));
	public static final Block GLOWCAP_BLOCK = register("glowcap_block", Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_BLUE)
			.strength(0.4F)
			.lightLevel(state -> 13)
			.sound(SoundType.SHROOMLIGHT));
	public static final Block BLUEBERRY_BUSH = register("blueberry_bush", props -> new BerryBushBlock(props, () -> ModItems.BLUEBERRIES),
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.PLANT)
					.randomTicks()
					.noCollision()
					.sound(SoundType.SWEET_BERRY_BUSH)
					.pushReaction(PushReaction.DESTROY));
	public static final Block COCONUT = register("coconut", CoconutBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BROWN)
			.strength(0.5F)
			.noOcclusion()
			.sound(SoundType.WOOD)
			.pushReaction(PushReaction.DESTROY));
	public static final Block SALT_BLOCK = register("salt_block", Block::new, stone(MapColor.SNOW, 1.2F, SoundType.CALCITE));
	public static final Block SALT_CRYSTAL = register("salt_crystal", props -> new AmethystClusterBlock(5.0F, 9.0F, props), cluster(MapColor.SNOW, 0));
	public static final Block AZURE_CRYSTAL = register("azure_crystal", Block::new, stone(MapColor.COLOR_LIGHT_BLUE, 1.5F, SoundType.AMETHYST)
			.lightLevel(state -> 12));
	public static final Block AZURE_CRYSTAL_CLUSTER = register("azure_crystal_cluster", props -> new AmethystClusterBlock(7.0F, 10.0F, props),
			cluster(MapColor.COLOR_LIGHT_BLUE, 9));
	public static final Block CITRINE_CRYSTAL = register("citrine_crystal", Block::new, stone(MapColor.COLOR_YELLOW, 1.5F, SoundType.AMETHYST)
			.lightLevel(state -> 12));
	public static final Block CITRINE_CRYSTAL_CLUSTER = register("citrine_crystal_cluster", props -> new AmethystClusterBlock(7.0F, 10.0F, props),
			cluster(MapColor.COLOR_YELLOW, 9));
	public static final Block PETRIFIED_LOG = register("petrified_log", RotatedPillarBlock::new, stone(MapColor.TERRACOTTA_BROWN, 2.0F, SoundType.STONE));
	public static final Block STEAM_VENT = register("steam_vent", SteamVentBlock::new, stone(MapColor.COLOR_GRAY, 2.0F, SoundType.BASALT)
			.lightLevel(state -> 3));

	/** Every block of the wild Overworld that is its own item, in creative tab order (coconuts and blueberries are food). */
	public static final Block[] WILD = {LAVENDER, HEATHER, CATTAIL, GLOWCAP, GLOWCAP_BLOCK, SALT_BLOCK, SALT_CRYSTAL, AZURE_CRYSTAL,
			AZURE_CRYSTAL_CLUSTER, CITRINE_CRYSTAL, CITRINE_CRYSTAL_CLUSTER, PETRIFIED_LOG, STEAM_VENT};

	/** Every block of the new End, in creative tab order. */
	public static final Block[] END = {END_MOSS, VOIDSTONE, VOIDSTONE_BRICKS, POLISHED_VOIDSTONE, VOID_CRYSTAL, VOID_CRYSTAL_CLUSTER, STARMETAL_ORE,
			STARMETAL_BLOCK, STARDUST, METEORITE, CHORUS_LOG, CHORUS_BULB, BOUNCE_STEM, BOUNCE_BLOOM, UPDRAFT_VENT, ENDER_GRASS, VOID_BLOOM, VOID_VINES};

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

	private static BlockBehaviour.Properties flower(MapColor color) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.noCollision()
				.instabreak()
				.offsetType(BlockBehaviour.OffsetType.XZ)
				.sound(SoundType.GRASS)
				.pushReaction(PushReaction.DESTROY);
	}

	private static BlockBehaviour.Properties cluster(MapColor color, int light) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.noOcclusion()
				.strength(1.5F)
				.lightLevel(state -> light)
				.sound(SoundType.AMETHYST_CLUSTER)
				.pushReaction(PushReaction.DESTROY);
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

	private static BlockBehaviour.Properties stone(MapColor color, float strength, SoundType sound) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.requiresCorrectToolForDrops()
				.strength(strength, 9.0F)
				.sound(sound);
	}

	private static BlockBehaviour.Properties endPlant(MapColor color, int light, boolean offset) {
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
				.mapColor(color)
				.noCollision()
				.instabreak()
				.replaceable()
				.lightLevel(state -> light)
				.sound(SoundType.GRASS);
		return offset ? properties.offsetType(BlockBehaviour.OffsetType.XZ) : properties;
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
