package com.naturesmod.creature;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FollowFlockLeaderGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Fish that live in schools. Most of them scatter when a diver comes close; a pack of piranhas
 * does the opposite.
 */
public class SchoolingFish extends AbstractSchoolingFish implements SeaCreature {
	private static final EntityDataAccessor<Boolean> ATTACKING = SynchedEntityData.defineId(SchoolingFish.class, EntityDataSerializers.BOOLEAN);

	public SchoolingFish(EntityType<? extends SchoolingFish> type, Level level) {
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
		if (this.species().behavior() != Behavior.PACK) {
			super.registerGoals();
			return;
		}
		// A pack never flees. It swarms whatever bleeds in its water.
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.5, true));
		this.goalSelector.addGoal(4, new CreatureGoals.SchoolSwimGoal(this));
		this.goalSelector.addGoal(5, new FollowFlockLeaderGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		// The rotting packs of the Abyss go for any diver; piranhas wait for blood.
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
				this.species().effect().isEmpty() ? CreatureGoals.bleeding(this) : CreatureGoals.diver(this, 14.0, true)));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 20, true, false, CreatureGoals.bleeding(this)));
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
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel) {
			LivingEntity target = this.getTarget();
			this.entityData.set(ATTACKING, target != null && this.distanceToSqr(target) < 6.0);
		}
	}

	@Override
	public int getMaxSchoolSize() {
		return Math.max(2, this.species().groupMax());
	}

	@Override
	public ItemStack getBucketItemStack() {
		return ModCreatures.bucket(this.species());
	}

	@Override
	public boolean canBePickedUpWithBucket(ItemStack itemStack) {
		return this.species().bucket() && itemStack.is(Items.WATER_BUCKET);
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

	@Override
	protected SoundEvent getFlopSound() {
		return CreatureSounds.flop(this.species());
	}
}
