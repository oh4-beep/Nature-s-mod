package com.naturesmod.creature;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A jellyfish: it has no brain to speak of. It drifts, sinks slowly, and every few seconds
 * pulses its bell to push itself along. Most species sting whatever touches them.
 */
public class Jellyfish extends WaterAnimal implements SeaCreature {
	/** Entity event telling clients to start the pulse animation. */
	private static final byte PULSE_EVENT = 19;

	private int pulseCooldown;
	/** Ticks since the bell last pulsed, for the animation. */
	private int pulseAge = 100;

	public Jellyfish(EntityType<? extends Jellyfish> type, Level level) {
		super(type, level);
		this.pulseCooldown = this.random.nextInt(40);
	}

	@Override
	public SpeciesData species() {
		return ModCreatures.species(this.getType());
	}

	@Override
	public boolean isAttacking() {
		return false;
	}

	/** Ticks since the last pulse plus the partial tick. */
	public float pulseTime(float partialTick) {
		return this.pulseAge + partialTick;
	}

	@Override
	protected void registerGoals() {
		// Jellyfish don't think: they drift and pulse (see aiStep).
	}

	@Override
	public int getMaxSpawnClusterSize() {
		return this.species().groupMax();
	}

	@Override
	public void aiStep() {
		super.aiStep();
		this.pulseAge++;
		if (!(this.level() instanceof ServerLevel level) || !this.isInWater() || this.isNoAi()) {
			return;
		}
		if (--this.pulseCooldown <= 0) {
			SpeciesData species = this.species();
			this.pulseCooldown = (int) (24 / species.speed()) + this.random.nextInt(30);
			this.setDeltaMovement(this.getDeltaMovement().add(this.pulseDirection(level).scale(0.06 + species.speed() * 0.2)));
			level.broadcastEntityEvent(this, PULSE_EVENT);
			this.pulseAge = 0;
		}
	}

	/** Mostly upwards, unless the surface is close; with some wandering to the side. */
	private Vec3 pulseDirection(ServerLevel level) {
		BlockPos above = this.blockPosition().above(3);
		boolean nearSurface = !level.getFluidState(above).is(FluidTags.WATER);
		double up = nearSurface ? -0.3 : 0.4 + this.random.nextDouble() * 0.6;
		double angle = this.random.nextDouble() * Math.PI * 2;
		double side = 0.3 + this.random.nextDouble() * 0.7;
		return new Vec3(Math.cos(angle) * side, up, Math.sin(angle) * side).normalize();
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == PULSE_EVENT) {
			this.pulseAge = 0;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
		this.move(MoverType.SELF, this.getDeltaMovement());
		// The water soon soaks up a pulse, then the jelly slowly sinks.
		this.setDeltaMovement(this.getDeltaMovement().scale(0.92).add(0.0, -0.0015, 0.0));
	}

	@Override
	public void playerTouch(Player player) {
		SpeciesData species = this.species();
		if (species.attack() <= 0 || !(this.level() instanceof ServerLevel level) || player.isCreative() || player.isSpectator()
				|| level.getDifficulty() == Difficulty.PEACEFUL || this.isAlliedTo(player)) {
			return;
		}
		if (player.hurtServer(level, this.damageSources().mobAttack(this), (float) species.attack())) {
			int strength = (int) species.attack();
			player.addEffect(new MobEffectInstance(MobEffects.POISON, 60 + 30 * strength, strength >= 4 ? 1 : 0), this);
			if (strength >= 3) {
				// The worst stings leave you reeling.
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0), this);
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1), this);
			}
			this.playSound(SoundEvents.PUFFER_FISH_STING, 1.0F, 1.3F);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SQUID_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SQUID_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.4F;
	}
}
