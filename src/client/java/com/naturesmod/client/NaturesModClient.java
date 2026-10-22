package com.naturesmod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import com.naturesmod.client.creature.CreatureRenderers;

public class NaturesModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(WaterfallAmbience::tick);
		CreatureRenderers.register();
		if (com.naturesmod.nether.NetherOverhaul.lunar()) {
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED
					.register(client -> com.naturesmod.nether.NetherOverhaul.addBiomes());
		}
	}
}
