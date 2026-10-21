package com.naturesmod.item;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import com.naturesmod.NaturesMod;

/**
 * The two diving suits. Tidewalker gear is sewn from the scales of sea creatures; Abyssal gear is
 * Tidewalker gear reinforced with plates from the deepest sea. Every piece lets you hold your
 * breath longer or swim faster, and a full suit lets you breathe underwater and glide through
 * the sea like a dolphin. Abyssal suits also light up the dark and withstand the crushing
 * pressure of the Abyss.
 */
public final class DivingArmor {
	public static final TagKey<Item> REPAIRS_TIDEWALKER = TagKey.create(Registries.ITEM, NaturesMod.id("repairs_tidewalker_armor"));
	public static final TagKey<Item> REPAIRS_ABYSSAL = TagKey.create(Registries.ITEM, NaturesMod.id("repairs_abyssal_armor"));

	public static final ArmorMaterial TIDEWALKER = new ArmorMaterial(22, defense(2, 5, 6, 2), 12, SoundEvents.ARMOR_EQUIP_TURTLE,
			0.5F, 0.0F, REPAIRS_TIDEWALKER, asset("tidewalker"));
	public static final ArmorMaterial ABYSSAL = new ArmorMaterial(40, defense(3, 6, 8, 3), 16, SoundEvents.ARMOR_EQUIP_NETHERITE,
			2.5F, 0.05F, REPAIRS_ABYSSAL, asset("abyssal"));

	private DivingArmor() {
	}

	private static Map<ArmorType, Integer> defense(int boots, int leggings, int chestplate, int helmet) {
		Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
		defense.put(ArmorType.BOOTS, boots);
		defense.put(ArmorType.LEGGINGS, leggings);
		defense.put(ArmorType.CHESTPLATE, chestplate);
		defense.put(ArmorType.HELMET, helmet);
		return defense;
	}

	private static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, NaturesMod.id(name));
	}

	/** Properties of one piece of a suit: the armour itself plus its diving bonuses. */
	static Item.Properties properties(ArmorMaterial material, ArmorType type) {
		boolean abyssal = material == ABYSSAL;
		EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(type.getSlot());
		Identifier id = NaturesMod.id("armor." + type.getName());
		ItemAttributeModifiers.Builder modifiers = ItemAttributeModifiers.builder();
		add(modifiers, Attributes.ARMOR, id, material.defense().getOrDefault(type, 0), slot);
		add(modifiers, Attributes.ARMOR_TOUGHNESS, id, material.toughness(), slot);
		if (material.knockbackResistance() > 0) {
			add(modifiers, Attributes.KNOCKBACK_RESISTANCE, id, material.knockbackResistance(), slot);
		}
		switch (type) {
			case HELMET -> {
				add(modifiers, Attributes.OXYGEN_BONUS, id, abyssal ? 3.0 : 1.5, slot);
				add(modifiers, Attributes.SUBMERGED_MINING_SPEED, id, abyssal ? 0.6 : 0.3, slot);
			}
			case CHESTPLATE -> add(modifiers, Attributes.OXYGEN_BONUS, id, abyssal ? 2.0 : 1.0, slot);
			case LEGGINGS, BOOTS -> add(modifiers, Attributes.WATER_MOVEMENT_EFFICIENCY, id, abyssal ? 0.35 : 0.2, slot);
			default -> {
			}
		}
		Item.Properties properties = new Item.Properties().humanoidArmor(material, type).attributes(modifiers.build());
		return abyssal ? properties.fireResistant() : properties;
	}

	private static void add(ItemAttributeModifiers.Builder modifiers, Holder<Attribute> attribute, Identifier id, double amount, EquipmentSlotGroup slot) {
		modifiers.add(attribute, new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE), slot);
	}

	private static final EquipmentSlot[] SUIT = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** How many pieces of diving gear the player wears, counting Abyssal pieces twice. */
	public static int pressureRating(ServerPlayer player) {
		if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CROWN_OF_THE_DEEP)) {
			// The Crown of the Deep: the Abyss itself no longer presses on its wearer.
			return 8;
		}
		int rating = 0;
		for (EquipmentSlot slot : SUIT) {
			ItemStack stack = player.getItemBySlot(slot);
			if (ModItems.isAbyssal(stack)) {
				rating += 2;
			} else if (ModItems.isTidewalker(stack)) {
				rating += 1;
			}
		}
		return rating;
	}

	/** Full-suit bonuses, refreshed once a second. */
	public static void tick(ServerPlayer player) {
		if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CROWN_OF_THE_DEEP) && player.isInWater()) {
			refresh(player, MobEffects.WATER_BREATHING, 0);
			refresh(player, MobEffects.CONDUIT_POWER, 0);
			refresh(player, MobEffects.DOLPHINS_GRACE, 0);
			MobEffectInstance sight = player.getEffect(MobEffects.NIGHT_VISION);
			if (sight == null || sight.getDuration() < 260) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
			}
			return;
		}
		int tidewalker = 0;
		int abyssal = 0;
		for (EquipmentSlot slot : SUIT) {
			ItemStack stack = player.getItemBySlot(slot);
			if (ModItems.isAbyssal(stack)) {
				abyssal++;
			} else if (ModItems.isTidewalker(stack)) {
				tidewalker++;
			}
		}
		if (tidewalker + abyssal < 4 || !player.isInWater()) {
			return;
		}
		refresh(player, MobEffects.WATER_BREATHING, 0);
		if (player.isSwimming() || player.isEyeInFluid(FluidTags.WATER)) {
			refresh(player, MobEffects.DOLPHINS_GRACE, 0);
		}
		if (abyssal == 4 && player.isEyeInFluid(FluidTags.WATER)) {
			// Conduit Power: clear sight and quick hands in the deep.
			refresh(player, MobEffects.CONDUIT_POWER, 0);
		}
	}

	private static void refresh(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null || current.getDuration() < 60) {
			player.addEffect(new MobEffectInstance(effect, 100, amplifier, true, false, true));
		}
	}
}
