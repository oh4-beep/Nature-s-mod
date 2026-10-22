package com.naturesmod.creature;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The beasts of the Overworld that carry a rider once they are saddled: giraffes, elephants, zebras
 * and ostriches. Walk the way you look, jump with the jump key, sneak to get off. Feed one wheat or
 * an apple to heal it. A saddled mount waits where its rider left it.
 */
public class Mount extends EndWalker {
	private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(Mount.class, EntityDataSerializers.BOOLEAN);

	public Mount(EntityType<? extends Mount> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(SADDLED, false);
	}

	public boolean isSaddledMount() {
		return this.entityData.get(SADDLED);
	}

	private void setSaddledMount(boolean saddled) {
		this.entityData.set(SADDLED, saddled);
		if (saddled) {
			this.setPersistenceRequired();
		}
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!this.isSaddledMount() && stack.is(Items.SADDLE) && !this.isAggressive()) {
			if (!this.level().isClientSide()) {
				this.setSaddledMount(true);
				stack.consume(1, player);
				this.playSound(SoundEvents.LLAMA_SWAG, 1.0F, 1.0F);
			}
			return InteractionResult.SUCCESS;
		}
		if ((stack.is(Items.WHEAT) || stack.is(Items.APPLE)) && this.getHealth() < this.getMaxHealth()) {
			if (!this.level().isClientSide()) {
				this.heal(6.0F);
				stack.consume(1, player);
				this.playSound(SoundEvents.HORSE_EAT, 1.0F, 0.9F);
			}
			return InteractionResult.SUCCESS;
		}
		if (this.isSaddledMount() && !this.isVehicle() && !player.isSecondaryUseActive()) {
			if (!this.level().isClientSide()) {
				this.setTarget(null);
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return this.isSaddledMount() && this.getFirstPassenger() instanceof Player rider ? rider : super.getControllingPassenger();
	}

	@Override
	protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
		float forward = controller.zza;
		if (forward < 0.0F) {
			forward *= 0.4F;
		}
		return new Vec3(controller.xxa * 0.5F, 0.0, forward);
	}

	@Override
	protected void tickRidden(Player controller, Vec3 riddenInput) {
		super.tickRidden(controller, riddenInput);
		this.setRot(controller.getYRot(), controller.getXRot() * 0.5F);
		this.yRotO = this.getYRot();
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
		if (controller.isJumping() && this.onGround()) {
			this.jumpFromGround();
		}
	}

	@Override
	protected float getRiddenSpeed(Player controller) {
		return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		// An elephant has room for two.
		return this.getPassengers().size() < (this.species().scale() >= 1.5F ? 2 : 1);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return !this.isSaddledMount() && super.removeWhenFarAway(distSqr);
	}

	@Override
	public void die(DamageSource source) {
		if (this.isSaddledMount() && this.level() instanceof ServerLevel level) {
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY() + 0.5, this.getZ(), new ItemStack(Items.SADDLE)));
		}
		super.die(source);
	}

	@Override
	public void addAdditionalSaveData(ValueOutput tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("saddled", this.isSaddledMount());
	}

	@Override
	public void readAdditionalSaveData(ValueInput tag) {
		super.readAdditionalSaveData(tag);
		this.setSaddledMount(tag.getBooleanOr("saddled", false));
	}
}
