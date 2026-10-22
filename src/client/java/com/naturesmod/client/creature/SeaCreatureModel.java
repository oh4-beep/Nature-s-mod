package com.naturesmod.client.creature;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

import com.naturesmod.creature.SpeciesData;

/**
 * Animates any creature model built from one of the body plans: tails that beat and bodies that
 * ripple, fins that scull, wings that flap, bells that pulse and jaws that snap, and the legs of
 * the End's walkers.
 */
public class SeaCreatureModel extends EntityModel<CreatureRenderState> {
	private static final float DEG = (float) (Math.PI / 180.0);

	private final SpeciesData species;
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart tail;
	private final ModelPart tailFin;
	private final ModelPart dorsal;
	private final ModelPart leftFin;
	private final ModelPart rightFin;
	private final ModelPart leftWing;
	private final ModelPart rightWing;
	private final ModelPart leftHorn;
	private final ModelPart rightHorn;
	private final ModelPart lure;
	private final ModelPart barbel;
	private final ModelPart leftBarbel;
	private final ModelPart rightBarbel;
	private final ModelPart cape;
	private final List<ModelPart> segments;
	private final List<ModelPart> tentacles;
	private final List<ModelPart> arms;
	// The End's walkers.
	private final List<ModelPart> legs;
	private final ModelPart leftArm;
	private final ModelPart rightArm;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;
	private final ModelPart leftFrontLeg;
	private final ModelPart rightFrontLeg;
	private final ModelPart leftHindLeg;
	private final ModelPart rightHindLeg;
	private final ModelPart leftEar;
	private final ModelPart rightEar;
	private final ModelPart leftFoot;
	private final ModelPart rightFoot;
	private final ModelPart leftAntenna;
	private final ModelPart rightAntenna;
	private final ModelPart leftMandible;
	private final ModelPart rightMandible;

	public SeaCreatureModel(ModelPart root, SpeciesData species) {
		super(root, species.translucent() ? RenderTypes::entityTranslucent : RenderTypes::entityCutout);
		this.species = species;
		Function<String, ModelPart> parts = root.createPartLookup();
		this.body = parts.apply("body");
		this.head = parts.apply("head");
		this.jaw = parts.apply("jaw");
		this.tail = parts.apply("tail");
		this.tailFin = parts.apply("tail_fin");
		this.dorsal = parts.apply("dorsal");
		this.leftFin = parts.apply("left_fin");
		this.rightFin = parts.apply("right_fin");
		this.leftWing = parts.apply("left_wing");
		this.rightWing = parts.apply("right_wing");
		this.leftHorn = parts.apply("left_horn");
		this.rightHorn = parts.apply("right_horn");
		this.lure = parts.apply("lure");
		this.barbel = parts.apply("barbel");
		this.leftBarbel = parts.apply("left_barbel");
		this.rightBarbel = parts.apply("right_barbel");
		this.cape = parts.apply("cape");
		this.segments = numbered(parts, "segment");
		this.tentacles = numbered(parts, "tentacle");
		this.arms = numbered(parts, "arm");
		this.legs = numbered(parts, "leg");
		this.leftArm = parts.apply("left_arm");
		this.rightArm = parts.apply("right_arm");
		this.leftLeg = parts.apply("left_leg");
		this.rightLeg = parts.apply("right_leg");
		this.leftFrontLeg = parts.apply("left_front_leg");
		this.rightFrontLeg = parts.apply("right_front_leg");
		this.leftHindLeg = parts.apply("left_hind_leg");
		this.rightHindLeg = parts.apply("right_hind_leg");
		this.leftEar = parts.apply("left_ear");
		this.rightEar = parts.apply("right_ear");
		this.leftFoot = parts.apply("left_foot");
		this.rightFoot = parts.apply("right_foot");
		this.leftAntenna = parts.apply("left_antenna");
		this.rightAntenna = parts.apply("right_antenna");
		this.leftMandible = parts.apply("left_mandible");
		this.rightMandible = parts.apply("right_mandible");
	}

	private static List<ModelPart> numbered(Function<String, ModelPart> parts, String prefix) {
		List<ModelPart> list = new ArrayList<>();
		for (int i = 0; ; i++) {
			ModelPart part = parts.apply(prefix + i);
			if (part == null) {
				return list;
			}
			list.add(part);
		}
	}

	@Override
	public void setupAnim(CreatureRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float moving = Mth.clamp(state.walkAnimationSpeed * 3.0F, 0.0F, 1.0F);
		// Big animals beat their tails slowly and deliberately, small fish flick theirs.
		float tempo = 1.0F / Mth.sqrt(Math.max(0.4F, this.species.scale()));
		// The End's flyers swim through the air, never stranded.
		boolean stranded = this.species.inWater() && !state.isInWater;
		switch (this.species.plan()) {
			case "jelly" -> this.animateJelly(state, age);
			case "squid" -> this.animateSquid(state, age, tempo);
			case "ray" -> this.animateRay(age, tempo, moving);
			case "seahorse" -> this.animateSeahorse(age);
			case "long" -> this.animateLong(age, tempo, moving, stranded);
			case "beetle" -> this.animateBeetle(state, age);
			case "golem" -> this.animateGolem(state, age);
			case "stalker" -> this.animateStalker(state, age);
			case "hopper" -> this.animateHopper(state, age);
			default -> this.animateFish(age, tempo, moving, stranded);
		}
		this.animateJaw(state, age);
		if (this.species.walks()) {
			// Walkers turn their heads to look about; their bodies stay level.
			if (this.head != null) {
				this.head.yRot += Mth.clamp(state.yRot, -45.0F, 45.0F) * DEG;
				this.head.xRot += Mth.clamp(state.xRot, -30.0F, 30.0F) * DEG;
			}
		} else if (this.body != null && !"jelly".equals(this.species.plan()) && !"seahorse".equals(this.species.plan())) {
			this.body.xRot += state.xRot * DEG;
		}
	}

	// --- the End's walkers -----------------------------------------------------------------------

	/** Six legs in a rippling tripod gait, feelers waving, mandibles working. */
	private void animateBeetle(CreatureRenderState state, float age) {
		float pos = state.walkAnimationPos;
		float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
		for (int i = 0; i < this.legs.size(); i++) {
			// Legs 0, 3 and 4 step together, then 1, 2 and 5.
			boolean first = i == 0 || i == 3 || i == 4;
			float phase = first ? 0.0F : (float) Math.PI;
			ModelPart leg = this.legs.get(i);
			leg.yRot += Mth.cos(pos * 1.3F + phase) * 0.55F * speed;
			leg.zRot += Math.max(0.0F, Mth.sin(pos * 1.3F + phase)) * 0.35F * speed * (i % 2 == 0 ? -1.0F : 1.0F);
		}
		this.sway(this.leftAntenna, age, 0.0F);
		this.sway(this.rightAntenna, age, 2.0F);
		float chew = state.attacking ? 0.4F * Math.abs(Mth.sin(age * 0.5F)) : 0.08F * Mth.sin(age * 0.15F);
		if (this.leftMandible != null) {
			this.leftMandible.yRot -= chew;
		}
		if (this.rightMandible != null) {
			this.rightMandible.yRot += chew;
		}
	}

	/** A heavy two-legged stride; the long arms swing, and rise to smash when it attacks. */
	private void animateGolem(CreatureRenderState state, float age) {
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		float swing = Mth.cos(pos * 0.6662F) * 1.2F * speed;
		if (this.leftLeg != null) {
			this.leftLeg.xRot += swing;
		}
		if (this.rightLeg != null) {
			this.rightLeg.xRot -= swing;
		}
		float arms = state.attacking ? -1.6F + Mth.sin(age * 0.45F) * 0.5F : 0.0F;
		if (this.leftArm != null) {
			this.leftArm.xRot += arms - swing * 0.6F;
			this.leftArm.zRot -= 0.06F + Mth.sin(age * 0.05F) * 0.03F;
		}
		if (this.rightArm != null) {
			this.rightArm.xRot += arms + swing * 0.6F;
			this.rightArm.zRot += 0.06F + Mth.sin(age * 0.05F) * 0.03F;
		}
		if (this.body != null) {
			this.body.y += Math.abs(Mth.cos(pos * 0.6662F)) * speed * 0.8F;
		}
	}

	/** Four stilts in a long, deliberate stride; the head bobs and the tail sways. */
	private void animateStalker(CreatureRenderState state, float age) {
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		float swing = Mth.cos(pos * 0.5F) * 0.9F * speed;
		if (this.leftFrontLeg != null) {
			this.leftFrontLeg.xRot += swing;
		}
		if (this.rightHindLeg != null) {
			this.rightHindLeg.xRot += swing;
		}
		if (this.rightFrontLeg != null) {
			this.rightFrontLeg.xRot -= swing;
		}
		if (this.leftHindLeg != null) {
			this.leftHindLeg.xRot -= swing;
		}
		if (this.head != null) {
			this.head.xRot += Mth.sin(pos * 1.0F) * 0.08F * speed + Mth.sin(age * 0.04F) * 0.04F;
		}
		if (this.tail != null) {
			this.tail.yRot += Mth.sin(age * 0.08F) * 0.3F;
			this.tail.xRot += Mth.sin(age * 0.05F) * 0.1F;
		}
	}

	/** Hops: the body bounces, the feet kick, the ears flop. */
	private void animateHopper(CreatureRenderState state, float age) {
		float pos = state.walkAnimationPos;
		float speed = Mth.clamp(state.walkAnimationSpeed * 2.0F, 0.0F, 1.0F);
		float hop = Math.abs(Mth.sin(pos * 0.9F)) * speed;
		if (this.body != null) {
			this.body.y -= hop * 3.0F;
			this.body.xRot += Mth.sin(pos * 0.9F) * 0.15F * speed;
		}
		float kick = Mth.cos(pos * 0.9F) * 0.6F * speed;
		if (this.leftFoot != null) {
			this.leftFoot.xRot += kick;
			this.leftFoot.y -= hop * 1.5F;
		}
		if (this.rightFoot != null) {
			this.rightFoot.xRot += kick;
			this.rightFoot.y -= hop * 1.5F;
		}
		float flop = Mth.sin(age * 0.1F) * 0.08F + hop * 0.4F;
		if (this.leftEar != null) {
			this.leftEar.xRot += flop;
		}
		if (this.rightEar != null) {
			this.rightEar.xRot += flop * 0.9F;
		}
	}

	private void animateFish(float age, float tempo, float moving, boolean stranded) {
		float speed = stranded ? 1.7F : (0.35F + 0.4F * moving) * tempo;
		float amplitude = stranded ? 0.8F : 0.3F + 0.2F * moving;
		float beat = Mth.sin(age * speed);
		if (this.tail != null) {
			this.tail.yRot += -amplitude * beat;
		}
		if (this.tailFin != null) {
			this.tailFin.yRot += -amplitude * 0.8F * Mth.sin(age * speed - 0.7F);
		}
		if (this.body != null) {
			this.body.yRot += amplitude * 0.12F * Mth.sin(age * speed + 1.2F);
		}
		float scull = Mth.sin(age * 0.3F * tempo) * 0.3F;
		if (this.leftFin != null) {
			this.leftFin.zRot += scull;
		}
		if (this.rightFin != null) {
			this.rightFin.zRot -= scull;
		}
		this.sway(this.leftBarbel, age, 0.0F);
		this.sway(this.rightBarbel, age, 1.5F);
		if (this.lure != null) {
			this.lure.xRot += Mth.sin(age * 0.11F) * 0.12F;
		}
	}

	private void animateLong(float age, float tempo, float moving, boolean stranded) {
		float speed = stranded ? 1.2F : (0.25F + 0.3F * moving) * tempo;
		float amplitude = stranded ? 0.45F : 0.16F + 0.1F * moving;
		for (int i = 0; i < this.segments.size(); i++) {
			// A wave that travels from head to tail, swinging wider towards the tail.
			this.segments.get(i).yRot += amplitude * (0.6F + 0.15F * i) * Mth.sin(age * speed - i * 0.9F);
		}
		if (this.tailFin != null) {
			this.tailFin.yRot += amplitude * 1.6F * Mth.sin(age * speed - this.segments.size() * 0.9F);
		}
		if (this.head != null) {
			this.head.yRot += amplitude * 0.4F * Mth.sin(age * speed + 0.9F);
		}
		if (this.barbel != null) {
			this.barbel.xRot += Mth.sin(age * 0.09F) * 0.15F;
			this.barbel.zRot += Mth.sin(age * 0.13F) * 0.1F;
		}
	}

	private void animateRay(float age, float tempo, float moving) {
		boolean flat = this.tail == null;
		float speed = (flat ? 0.2F : 0.12F + 0.05F * moving) * tempo * 2.0F;
		float flap = (flat ? 0.12F : 0.45F) * Mth.sin(age * speed);
		if (this.leftWing != null) {
			this.leftWing.zRot += flap;
		}
		if (this.rightWing != null) {
			this.rightWing.zRot -= flap;
		}
		if (this.tail != null) {
			this.tail.yRot += Mth.sin(age * 0.07F) * 0.15F;
			this.tail.xRot += Mth.sin(age * speed - 1.0F) * 0.06F;
		}
		if (this.leftHorn != null) {
			this.leftHorn.xRot += Mth.sin(age * 0.05F) * 0.2F;
		}
		if (this.rightHorn != null) {
			this.rightHorn.xRot += Mth.sin(age * 0.05F + 0.5F) * 0.2F;
		}
		if (this.body != null) {
			this.body.y += Mth.sin(age * speed + 1.5F) * 0.6F;
		}
	}

	private void animateJelly(CreatureRenderState state, float age) {
		float pulse = state.pulse;
		// A quick squeeze, then the bell slowly relaxes again.
		float squeeze = pulse < 5.0F ? pulse / 5.0F : pulse < 25.0F ? 1.0F - (pulse - 5.0F) / 20.0F : 0.0F;
		if (this.tentacles.isEmpty()) {
			// Comb jellies glide on their cilia instead of pulsing.
			squeeze = 0.08F * (Mth.sin(age * 0.08F) + 1.0F);
		}
		if (this.body != null) {
			this.body.xScale = 1.0F - 0.2F * squeeze;
			this.body.zScale = 1.0F - 0.2F * squeeze;
			this.body.yScale = 1.0F + 0.14F * squeeze;
			this.body.yRot += age * 0.004F;
			if (this.species.inWater() && !state.isInWater) {
				this.body.yScale *= 0.55F;
				this.body.y += 4.0F;
			}
		}
		float spread = 0.32F * (1.0F - squeeze) - 0.08F;
		for (int i = 0; i < this.tentacles.size(); i++) {
			ModelPart tentacle = this.tentacles.get(i);
			tentacle.zRot += -spread + Mth.sin(age * 0.07F + i * 0.8F) * 0.1F;
			tentacle.xRot += Mth.sin(age * 0.05F + i * 1.7F) * 0.12F;
		}
		for (int i = 0; i < this.arms.size(); i++) {
			ModelPart arm = this.arms.get(i);
			arm.zRot += -spread * 0.4F + Mth.sin(age * 0.06F + i * 2.1F) * 0.08F;
			arm.xRot += Mth.sin(age * 0.045F + i) * 0.1F;
		}
	}

	private void animateSquid(CreatureRenderState state, float age, float tempo) {
		boolean dumbo = this.tentacles.isEmpty() && this.cape == null && this.species.scale() < 1.0F;
		float cycle = (Mth.sin(age * 0.12F * tempo) + 1.0F) * 0.5F;
		int count = this.arms.size();
		for (int i = 0; i < count; i++) {
			ModelPart arm = this.arms.get(i);
			float angle = (float) (Math.PI * 2.0) * i / count;
			float spread = (dumbo ? 0.25F : 0.12F) + 0.3F * cycle;
			// Arms fan out from the body and close again as the animal jets along.
			arm.xRot += Mth.sin(angle) * spread;
			arm.yRot += -Mth.cos(angle) * spread;
			arm.zRot += Mth.sin(age * 0.1F + i) * 0.05F;
		}
		for (int i = 0; i < this.tentacles.size(); i++) {
			ModelPart tentacle = this.tentacles.get(i);
			tentacle.yRot += Mth.sin(age * 0.08F + i * 3.0F) * 0.12F;
			tentacle.xRot += Mth.sin(age * 0.06F + i) * 0.08F + (state.attacking ? -0.3F : 0.0F);
		}
		float flap = Mth.sin(age * (dumbo ? 0.18F : 0.25F)) * (dumbo ? 0.5F : 0.3F);
		if (this.leftFin != null) {
			this.leftFin.zRot += flap;
		}
		if (this.rightFin != null) {
			this.rightFin.zRot -= flap;
		}
		if (this.cape != null) {
			float open = 1.0F + 0.12F * cycle;
			this.cape.xScale = open;
			this.cape.yScale = open;
		}
	}

	private void animateSeahorse(float age) {
		if (this.body != null) {
			this.body.y += Mth.sin(age * 0.08F) * 0.8F;
			this.body.xRot += Mth.sin(age * 0.05F) * 0.06F;
		}
		if (this.dorsal != null) {
			this.dorsal.yRot += Mth.sin(age * 1.3F) * 0.4F;
		}
		if (this.tail != null) {
			this.tail.xRot += Mth.sin(age * 0.07F) * 0.12F;
		}
		if (this.tailFin != null) {
			this.tailFin.xRot += Mth.sin(age * 0.07F + 0.6F) * 0.25F;
		}
		if (this.head != null) {
			this.head.xRot += Mth.sin(age * 0.05F + 1.0F) * 0.05F;
		}
	}

	private void animateJaw(CreatureRenderState state, float age) {
		if (this.jaw == null) {
			return;
		}
		if (state.attacking) {
			this.jaw.xRot += 0.35F + 0.3F * Math.abs(Mth.sin(age * 0.45F));
		} else {
			this.jaw.xRot += 0.04F + 0.04F * Mth.sin(age * 0.1F);
		}
	}

	private void sway(ModelPart part, float age, float phase) {
		if (part != null) {
			part.xRot += Mth.sin(age * 0.1F + phase) * 0.12F;
			part.yRot += Mth.sin(age * 0.07F + phase) * 0.1F;
		}
	}
}
