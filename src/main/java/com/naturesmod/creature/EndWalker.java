package com.naturesmod.creature;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The creatures that walk the new End's lands: ender beetles grazing the chorus, crystal golems,
 * void stalkers striding on their stilts, and the little endlings hopping over the moss. A void
 * stalker that is struck blinks away, as the endermen do.
 */
public class EndWalker extends PathfinderMob implements SeaCreature {
	private static final EntityDataAccessor<Boolean> ATTACKING = SynchedEntityData.defineId(EndWalker.class, EntityDataSerializers.BOOLEAN);

	public EndWalker(EntityType<? extends EndWalker> type, Level level) {
		super(type, level);
	}

	@Override
	public SpeciesData species() {
		return ModCreatures.species(this.getType());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ATTACKING, false);
	}

	@Override
	public boolean isAttacking() {
		return this.entityData.get(ATTACKING);
	}

	@Override
	protected void registerGoals() {
		Behavior behavior = this.species().behavior();
		this.goalSelector.addGoal(0, new FloatGoal(this));
		if (behavior.fightsBack()) {
			this.goalSelector.addGoal(2, new MeleeAttackGoal(this, behavior.isHostile() ? 1.25 : 1.15, false));
			this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		} else {
			this.goalSelector.addGoal(1, new PanicGoal(this, 1.7));
		}
		if (behavior.isHostile()) {
			this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		}
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!this.level().isClientSide() && this.level().getDifficulty() == Difficulty.PEACEFUL && this.species().behavior().isHostile()) {
			this.discard();
			return;
		}
		if (!this.level().isClientSide() && this.tickCount % 5 == 0) {
			LivingEntity target = this.getTarget();
			this.entityData.set(ATTACKING, target != null && this.distanceToSqr(target) < 9.0 + this.getBbWidth() * this.getBbWidth());
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			CreatureEffects.bite(this, this.species(), target);
		}
		return hit;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.isAlive() && "stalker".equals(this.species().plan()) && this.random.nextInt(3) != 0) {
			// Blink away: somewhere close by, on solid ground.
			for (int attempt = 0; attempt < 12; attempt++) {
				double x = this.getX() + (this.random.nextDouble() - 0.5) * 16.0;
				double y = this.getY() + this.random.nextInt(9) - 4;
				double z = this.getZ() + (this.random.nextDouble() - 0.5) * 16.0;
				if (this.randomTeleport(x, y, z, true, state -> false)) {
					this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.8F);
					break;
				}
			}
		}
		return hurt;
	}

	@Override
	public int getMaxSpawnClusterSize() {
		return this.species().groupMax();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return CreatureSounds.ambient(this.species());
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return CreatureSounds.hurt(this.species());
	}

	@Override
	protected SoundEvent getDeathSound() {
		return CreatureSounds.death(this.species());
	}
}
