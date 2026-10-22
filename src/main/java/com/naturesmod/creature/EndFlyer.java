package com.naturesmod.creature;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The creatures of the open air: in the new End, void jellies, voidfin, star squid, skyrays, ender
 * sharks and void serpents, which swim through its sky the way fish swim through water, drifting
 * over the lands and the void; in the Overworld, its eagles, vultures, owls and toucans. The
 * hunters among them swoop on whatever crosses their sky.
 */
public class EndFlyer extends PathfinderMob implements SeaCreature {
	private static final EntityDataAccessor<Boolean> ATTACKING = SynchedEntityData.defineId(EndFlyer.class, EntityDataSerializers.BOOLEAN);

	public EndFlyer(EntityType<? extends EndFlyer> type, Level level) {
		super(type, level);
		this.moveControl = new FlyingMoveControl<>(this, 12, true);
		this.setNoGravity(true);
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

	void setAttacking(boolean attacking) {
		this.entityData.set(ATTACKING, attacking);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
		navigation.setCanOpenDoors(false);
		navigation.setCanFloat(true);
		return navigation;
	}

	@Override
	protected void registerGoals() {
		Behavior behavior = this.species().behavior();
		if (behavior.fightsBack()) {
			this.goalSelector.addGoal(1, new SwoopGoal(this));
			this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		}
		if (behavior.isHostile()) {
			this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		}
		this.goalSelector.addGoal(4, new DriftGoal(this));
		if (!this.species().isJelly()) {
			this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
		}
	}

	@Override
	public void travel(Vec3 input) {
		this.travelFlying(input, this.getSpeed());
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
	}

	@Override
	public boolean onClimbable() {
		return false;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!this.level().isClientSide() && this.level().getDifficulty() == Difficulty.PEACEFUL && this.species().behavior().isHostile()) {
			this.discard();
			return;
		}
		if (!this.level().isClientSide() && this.tickCount % 10 == 0) {
			LivingEntity target = this.getTarget();
			this.setAttacking(target != null && this.distanceToSqr(target) < 36.0);
		}
		// Whatever happens, a creature of the air never lets itself fall into the void.
		if (this.getY() < this.level().getMinY() + 12 && this.getDeltaMovement().y < 0.2) {
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.08, 0.0));
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			this.playSound(SoundEvents.PHANTOM_BITE, 1.0F, 0.7F + this.random.nextFloat() * 0.3F);
			CreatureEffects.bite(this, this.species(), target);
		}
		return hit;
	}

	/** The Overworld's birds stay about, like its animals; the End's flyers come and go. */
	@Override
	public boolean removeWhenFarAway(double distSqr) {
		if (this.species().overworld() && this.species().mobCategory() == MobCategory.CREATURE) {
			return false;
		}
		return super.removeWhenFarAway(distSqr);
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

	/** Whether this flyer should hold still rather than wander off (a saddled skyray waits for its rider). */
	boolean staysPut() {
		return false;
	}

	/**
	 * Wanders the open air: picks a spot a little way off and glides to it, keeping a few blocks
	 * over the land below and well clear of the void. Schooling flyers keep to their school.
	 */
	static class DriftGoal extends Goal {
		private final EndFlyer mob;

		DriftGoal(EndFlyer mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (this.mob.isVehicle() || this.mob.staysPut() || this.mob.getTarget() != null) {
				return false;
			}
			return !this.mob.getMoveControl().hasWanted() || this.mob.getRandom().nextInt(this.mob.species().isJelly() ? 120 : 60) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			SpeciesData species = this.mob.species();
			Level level = this.mob.level();
			double range = species.isJelly() ? 8.0 : 18.0;
			double cx = this.mob.getX();
			double cy = this.mob.getY();
			double cz = this.mob.getZ();
			if (species.schools()) {
				// Head for the middle of the school, with a little scatter.
				List<EndFlyer> school = level.getEntitiesOfClass(EndFlyer.class, this.mob.getBoundingBox().inflate(10.0),
						other -> other.getType() == this.mob.getType());
				if (school.size() > 1) {
					cx = school.stream().mapToDouble(Entity::getX).average().orElse(cx);
					cy = school.stream().mapToDouble(Entity::getY).average().orElse(cy);
					cz = school.stream().mapToDouble(Entity::getZ).average().orElse(cz);
					range = 9.0;
				}
			}
			for (int attempt = 0; attempt < 8; attempt++) {
				double x = cx + (this.mob.getRandom().nextDouble() * 2.0 - 1.0) * range;
				double z = cz + (this.mob.getRandom().nextDouble() * 2.0 - 1.0) * range;
				double y = cy + (this.mob.getRandom().nextDouble() * 2.0 - 1.0) * range * 0.4;
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
				if (ground > level.getMinY() + 1) {
					// Over land: keep between a few blocks and a couple of dozen above it.
					y = Mth.clamp(y, ground + 2.5, ground + 26.0);
				}
				y = Mth.clamp(y, level.getMinY() + 24.0, level.getMaxY() - 8.0);
				BlockPos target = BlockPos.containing(x, y, z);
				if (level.isEmptyBlock(target) && level.isEmptyBlock(target.above())) {
					double speed = species.isJelly() ? 0.6 : this.mob.getLastHurtByMob() != null ? 1.6 : 1.0;
					this.mob.getMoveControl().setWantedPosition(x, y, z, speed);
					return;
				}
			}
		}
	}

	/** The hunters' attack: dive at the target, bite, and climb away to come round again. */
	static class SwoopGoal extends Goal {
		private final EndFlyer mob;
		private int cooldown;
		private int retreat;

		SwoopGoal(EndFlyer mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.mob.getTarget();
			return target != null && target.isAlive() && !this.mob.isVehicle();
		}

		@Override
		public void start() {
			this.cooldown = 10;
			this.retreat = 0;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.mob.getTarget();
			if (target == null) {
				return;
			}
			this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (this.cooldown > 0) {
				this.cooldown--;
			}
			if (this.retreat > 0) {
				this.retreat--;
				Vec3 away = this.mob.position().subtract(target.position()).multiply(1.0, 0.0, 1.0).normalize().scale(6.0);
				this.mob.getMoveControl().setWantedPosition(target.getX() + away.x, target.getY() + 5.0, target.getZ() + away.z, 1.3);
				return;
			}
			this.mob.getMoveControl().setWantedPosition(target.getX(), target.getY(0.5), target.getZ(), 1.7);
			AABB reach = this.mob.getBoundingBox().inflate(0.6);
			if (this.cooldown <= 0 && reach.intersects(target.getBoundingBox()) && this.mob.level() instanceof ServerLevel level) {
				this.mob.doHurtTarget(level, target);
				this.cooldown = 20;
				this.retreat = 15 + this.mob.getRandom().nextInt(20);
			}
		}
	}
}
