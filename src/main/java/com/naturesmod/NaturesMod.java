package com.naturesmod;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.naturesmod.abyss.Abyss;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.creature.AbyssBosses;
import com.naturesmod.creature.EndLifeSpawner;
import com.naturesmod.creature.ModCreatures;
import com.naturesmod.creature.SeaLifeSpawner;
import com.naturesmod.end.EndOverhaul;
import com.naturesmod.item.ModItems;
import com.naturesmod.nether.NetherOverhaul;
import com.naturesmod.smoketest.SmokeTest;
import com.naturesmod.tree.TreeEvents;
import com.naturesmod.worldgen.ModBiomeModifications;
import com.naturesmod.worldgen.ModFeatures;
import com.naturesmod.worldgen.ModStructures;
import com.naturesmod.worldgen.wild.WildOverworld;

public class NaturesMod implements ModInitializer {
	public static final String MOD_ID = "naturesmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		NaturesConfig.load();
		ModBlocks.register();
		ModFeatures.register();
		ModStructures.register();
		WildOverworld.register();
		ModBiomeModifications.register();
		TreeEvents.register();
		ModCreatures.register();
		AbyssBosses.register();
		SeaLifeSpawner.register();
		EndLifeSpawner.register();
		ModItems.register();
		Abyss.register();
		NetherOverhaul.register();
		EndOverhaul.register();

		if (Boolean.getBoolean("naturesmod.smoketest")) {
			SmokeTest.register();
		}
		LOGGER.info("Nature's Mod is reshaping the Overworld, growing new wilds, filling the seas, stoking the Nether and remaking the End");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
