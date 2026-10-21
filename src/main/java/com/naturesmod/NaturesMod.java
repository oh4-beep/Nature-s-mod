package com.naturesmod;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.naturesmod.creature.ModCreatures;
import com.naturesmod.item.ModItems;
import com.naturesmod.smoketest.SmokeTest;
import com.naturesmod.tree.TreeEvents;
import com.naturesmod.worldgen.ModBiomeModifications;
import com.naturesmod.worldgen.ModFeatures;
import com.naturesmod.worldgen.ModStructures;

public class NaturesMod implements ModInitializer {
	public static final String MOD_ID = "naturesmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		NaturesConfig.load();
		ModFeatures.register();
		ModStructures.register();
		ModBiomeModifications.register();
		TreeEvents.register();
		ModCreatures.register();
		ModItems.register();

		if (Boolean.getBoolean("naturesmod.smoketest")) {
			SmokeTest.register();
		}
		LOGGER.info("Nature's Mod is reshaping the Overworld and filling the seas");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
