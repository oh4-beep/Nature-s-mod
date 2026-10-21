package com.naturesmod.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;

import com.naturesmod.NaturesMod;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.creature.CreatureSpecies;
import com.naturesmod.creature.ModCreatures;
import com.naturesmod.creature.SpeciesData;

/** Items of the ocean overhaul: catches, diving gear, buckets and spawn eggs. */
public final class ModItems {
	public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, NaturesMod.id("naturesmod"));
	private static final List<Item> TAB_ITEMS = new ArrayList<>();
	private static final List<Item> TIDEWALKER_SET = new ArrayList<>();
	private static final List<Item> ABYSSAL_SET = new ArrayList<>();

	public static Item RAW_FISH_FILLET;
	public static Item COOKED_FISH_FILLET;
	public static Item SEA_SCALE;
	public static Item SHARK_TOOTH;
	public static Item ABYSSAL_SCALE;
	public static Item ABYSSAL_PLATE;
	public static Item ABYSSAL_HEART;
	public static Item ABYSSAL_UPGRADE_SMITHING_TEMPLATE;
	public static Item PEARL;
	public static Item TIDE_SIGIL;
	public static Item DEEP_SIGIL;
	public static Item BONE_SIGIL;
	public static Item EMBER_SIGIL;
	public static Item CROWN_OF_THE_DEEP;

	private ModItems() {
	}

	public static void register() {
		RAW_FISH_FILLET = register("raw_fish_fillet", Item::new, new Item.Properties()
				.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).build()));
		COOKED_FISH_FILLET = register("cooked_fish_fillet", Item::new, new Item.Properties()
				.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.8F).build()));
		SEA_SCALE = register("sea_scale", Item::new, new Item.Properties());
		SHARK_TOOTH = register("shark_tooth", Item::new, new Item.Properties());
		ABYSSAL_SCALE = register("abyssal_scale", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
		ABYSSAL_PLATE = register("abyssal_plate", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());
		ABYSSAL_HEART = register("abyssal_heart", Item::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(16));
		ABYSSAL_UPGRADE_SMITHING_TEMPLATE = register("abyssal_upgrade_smithing_template", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));

		register("nether_geyser", properties -> new BlockItem(ModBlocks.NETHER_GEYSER, properties), new Item.Properties().useBlockDescriptionPrefix());
		PEARL = register("pearl", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
		for (Block block : ModBlocks.SEA_FLOOR) {
			register(BuiltInRegistries.BLOCK.getKey(block).getPath(), properties -> new BlockItem(block, properties),
					new Item.Properties().useBlockDescriptionPrefix());
		}

		armor("tidewalker", DivingArmor.TIDEWALKER, TIDEWALKER_SET);
		armor("abyssal", DivingArmor.ABYSSAL, ABYSSAL_SET);

		// The Drowned Expedition: the four sigils that wake the Abyss, and the crown it guards.
		TIDE_SIGIL = sigil("tide_sigil");
		DEEP_SIGIL = sigil("deep_sigil");
		BONE_SIGIL = sigil("bone_sigil");
		EMBER_SIGIL = sigil("ember_sigil");
		CROWN_OF_THE_DEEP = register("crown_of_the_deep", Item::new, DivingArmor.properties(DivingArmor.ABYSSAL, ArmorType.HELMET)
				.rarity(Rarity.EPIC).fireResistant());
		register("abyssal_altar", properties -> new BlockItem(ModBlocks.ABYSSAL_ALTAR, properties),
				new Item.Properties().useBlockDescriptionPrefix().rarity(Rarity.EPIC));

		for (SpeciesData species : CreatureSpecies.ALL) {
			EntityType<? extends Mob> type = ModCreatures.type(species.id());
			if (species.bucket()) {
				Item bucket = register(species.id() + "_bucket", properties -> new MobBucketItem(type, Fluids.WATER, SoundEvents.BUCKET_EMPTY_FISH, properties),
						new Item.Properties().stacksTo(1).component(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY));
				ModCreatures.registerBucket(species.id(), bucket);
			}
		}
		for (SpeciesData species : CreatureSpecies.ALL) {
			register(species.id() + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ModCreatures.type(species.id())));
		}

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.naturesmod"))
				.icon(() -> new ItemStack(ABYSSAL_HEART))
				.displayItems((parameters, output) -> TAB_ITEMS.forEach(output::accept))
				.build());

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					DivingArmor.tick(player);
				}
			}
		});
	}

	private static Item sigil(String name) {
		return register(name, Item::new, new Item.Properties().rarity(Rarity.RARE).stacksTo(16).fireResistant()
				.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
	}

	private static void armor(String name, ArmorMaterial material, List<Item> set) {
		for (ArmorType type : new ArmorType[] {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS}) {
			set.add(register(name + "_" + type.getName(), Item::new, DivingArmor.properties(material, type)));
		}
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, NaturesMod.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		TAB_ITEMS.add(item);
		return item;
	}

	public static boolean isTidewalker(ItemStack stack) {
		return !stack.isEmpty() && TIDEWALKER_SET.contains(stack.getItem());
	}

	public static boolean isAbyssal(ItemStack stack) {
		return !stack.isEmpty() && ABYSSAL_SET.contains(stack.getItem());
	}
}
