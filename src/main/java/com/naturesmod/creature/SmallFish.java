package com.naturesmod.creature;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Small fish that dart about on their own and can be carried home in a bucket. */
public class SmallFish extends AbstractFish implements SeaCreature {
	public SmallFish(EntityType<? extends SmallFish> type, Level level) {
		super(type, level);
	}

	@Override
	public SpeciesData species() {
		return ModCreatures.species(this.getType());
	}

	@Override
	public boolean isAttacking() {
		return false;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		if (this.species().behavior() == Behavior.BOTTOM) {
			this.goalSelector.addGoal(3, new CreatureGoals.BottomSwimGoal(this, 0.8, 50));
		}
	}

	@Override
	public int getMaxSpawnClusterSize() {
		return this.species().groupMax();
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
