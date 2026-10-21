package com.naturesmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NaturesMod implements ModInitializer {
	public static final String MOD_ID = "naturesmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Nature's Mod is reshaping the Overworld");

		if (Boolean.getBoolean("naturesmod.smoketest")) {
			ServerLifecycleEvents.SERVER_STARTED.register(server -> server.halt(false));
		}
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
