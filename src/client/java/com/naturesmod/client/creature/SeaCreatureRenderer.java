package com.naturesmod.client.creature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

import com.naturesmod.NaturesMod;
import com.naturesmod.creature.Jellyfish;
import com.naturesmod.creature.SeaCreature;
import com.naturesmod.creature.SpeciesData;

/** Renders one species of sea creature, with its glowing markings if it has any. */
public class SeaCreatureRenderer extends MobRenderer<Mob, CreatureRenderState, SeaCreatureModel> {
	private final SpeciesData species;
	private final Identifier texture;

	public SeaCreatureRenderer(EntityRendererProvider.Context context, SpeciesData species, ModelLayerLocation layer) {
		super(context, new SeaCreatureModel(context.bakeLayer(layer), species), Math.min(1.6F, species.width() * 0.45F));
		this.species = species;
		this.texture = NaturesMod.id("textures/entity/creature/" + species.id() + ".png");
		if (species.glow()) {
			this.addLayer(new GlowLayer(this, NaturesMod.id("textures/entity/creature/" + species.id() + "_glow.png")));
		}
	}

	@Override
	public Identifier getTextureLocation(CreatureRenderState state) {
		return this.texture;
	}

	@Override
	public CreatureRenderState createRenderState() {
		return new CreatureRenderState();
	}

	@Override
	public void extractRenderState(Mob entity, CreatureRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.attacking = entity instanceof SeaCreature creature && creature.isAttacking();
		// A jellyfish boss swims like the other bosses but still pulses its bell every couple of seconds.
		state.pulse = entity instanceof Jellyfish jelly ? jelly.pulseTime(partialTicks)
				: this.species.isJelly() ? (entity.tickCount + partialTicks) % 45.0F : 100.0F;
	}

	@Override
	protected void scale(CreatureRenderState state, PoseStack poseStack) {
		float scale = this.species.scale();
		poseStack.scale(scale, scale, scale);
	}

	@Override
	protected void setupRotations(CreatureRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		String plan = this.species.plan();
		if (!state.isInWater && state.deathTime <= 0.0F && !"jelly".equals(plan) && !"squid".equals(plan) && !"ray".equals(plan)) {
			// Out of the water a fish lies on its side.
			poseStack.translate(0.0F, 0.12F * this.species.scale(), 0.0F);
			poseStack.rotateDegrees(Axis.ZP, 90.0F);
		}
	}

	/** Full-bright markings: photophores, lures, glowing eyes and stripes. */
	private static final class GlowLayer extends EyesLayer<CreatureRenderState, SeaCreatureModel> {
		private final RenderType renderType;

		GlowLayer(RenderLayerParent<CreatureRenderState, SeaCreatureModel> renderer, Identifier texture) {
			super(renderer);
			this.renderType = RenderTypes.eyes(texture);
		}

		@Override
		public RenderType renderType() {
			return this.renderType;
		}
	}
}
