package com.naturesmod.client.creature;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import net.minecraft.client.model.geom.ModelLayerLocation;

import com.naturesmod.NaturesMod;
import com.naturesmod.creature.CreatureSpecies;
import com.naturesmod.creature.ModCreatures;
import com.naturesmod.creature.SpeciesData;

/** Hooks the sea creature models and renderers up to their entity types. */
public final class CreatureRenderers {
	private CreatureRenderers() {
	}

	public static void register() {
		for (SpeciesData species : CreatureSpecies.ALL) {
			ModelLayerLocation layer = new ModelLayerLocation(NaturesMod.id("creature/" + species.id()), "main");
			ModelLayerRegistry.registerModelLayer(layer, () -> CreatureModels.create(species.id()));
			EntityRendererRegistry.register(ModCreatures.type(species.id()), context -> new SeaCreatureRenderer(context, species, layer));
		}
	}
}
