package com.naturesmod.creature;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The skyray: a great gentle ray that glides through the End's skies. Put a saddle on one and it
 * carries you wherever you look: hold forward to fly, jump to climb, sneak to get off. Feed it a
 * chorus fruit to heal it. A saddled skyray waits where its rider left it.
 */
public class Skyray extends EndFlyer {
	private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(Skyray.class, EntityDataSerializers.BOOLEAN);
	/** How hard it pulls through the air under a rider: a cruise of some twelve blocks a second. */
	private static final float RIDDEN_THRUST = 0.055F;

	public Skyray(EntityType<? extends Skyray> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(SADDLED, false);
	}

	public boolean isSaddledRay() {
		return this.entityData.get(SADDLED);
	}

	private void setSaddledRay(boolean saddled) {
		this.entityData.set(SADDLED, saddled);
		if (saddled) {
			this.setPersistenceRequired();
		}
	}

	@Override
	boolean staysPut() {
		return this.isSaddledRay();
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!this.isSaddledRay() && stack.is(Items.SADDLE)) {
			if (!this.level().isClientSide()) {
				this.setSaddledRay(true);
				stack.consume(1, player);
				this.playSound(SoundEvents.PHANTOM_SWOOP, 1.0F, 1.4F);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.CHORUS_FRUIT) && this.getHealth() < this.getMaxHealth()) {
			if (!this.level().isClientSide()) {
				this.heal(8.0F);
				stack.consume(1, player);
				this.playSound(SoundEvents.EVOKER_CAST_SPELL, 0.8F, 1.6F);
			}
			return InteractionResult.SUCCESS;
		}
		if (this.isSaddledRay() && !this.isVehicle() && !player.isSecondaryUseActive()) {
			if (!this.level().isClientSide()) {
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return this.isSaddledRay() && this.getFirstPassenger() instanceof Player rider ? rider : super.getControllingPassenger();
	}

	@Override
	protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
		float strafe = controller.xxa * 0.5F;
		float forward = 0.0F;
		float up = 0.0F;
		if (controller.zza != 0.0F) {
			// Fly where the rider looks.
			float pitch = controller.getXRot() * Mth.DEG_TO_RAD;
			forward = Mth.cos(pitch);
			up = -Mth.sin(pitch);
			if (controller.zza < 0.0F) {
				forward *= -0.5F;
				up *= -0.5F;
			}
		}
		if (controller.isJumping()) {
			up += 0.6F;
		}
		Vec3 input = new Vec3(strafe, up, forward);
		return input.lengthSqr() > 1.0 ? input.normalize() : input;
	}

	@Override
	protected void tickRidden(Player controller, Vec3 riddenInput) {
		super.tickRidden(controller, riddenInput);
		float yRot = this.getYRot() + Mth.wrapDegrees(controller.getYRot() - this.getYRot()) * 0.15F;
		this.setRot(yRot, controller.getXRot() * 0.4F);
		this.yRotO = yRot;
		this.yBodyRot = yRot;
		this.yHeadRot = yRot;
	}

	@Override
	protected float getRiddenSpeed(Player controller) {
		return RIDDEN_THRUST;
	}

	@Override
	public void travel(Vec3 input) {
		if (this.isVehicle() && this.getControllingPassenger() instanceof Player) {
			this.travelFlying(input, RIDDEN_THRUST);
		} else {
			super.travel(input);
		}
	}

	@Override
	public boolean isFlyingVehicle() {
		return true;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().size() < 2;
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return !this.isSaddledRay() && super.removeWhenFarAway(distSqr);
	}

	@Override
	public void die(DamageSource source) {
		if (this.isSaddledRay() && this.level() instanceof ServerLevel level) {
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY() + 0.5, this.getZ(), new ItemStack(Items.SADDLE)));
		}
		super.die(source);
	}

	@Override
	public void addAdditionalSaveData(ValueOutput tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("saddled", this.isSaddledRay());
	}

	@Override
	public void readAdditionalSaveData(ValueInput tag) {
		super.readAdditionalSaveData(tag);
		this.setSaddledRay(tag.getBooleanOr("saddled", false));
	}
}
