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
import com.naturesmod.abyss.RiftBlock;

/** Blocks added by Nature's Mod. */
public final class ModBlocks {
	public static final Block ABYSSAL_RIFT = register("abyssal_rift", RiftBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLUE)
			.noCollision()
			.strength(-1.0F, 3600000.0F)
			.noLootTable()
			.lightLevel(state -> 12)
			.sound(SoundType.GLASS));

	private ModBlocks() {
	}

	/** Forces the registrations above to run during mod initialisation. */
	public static void register() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, NaturesMod.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}
}
