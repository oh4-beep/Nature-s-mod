package com.naturesmod.creature;

import java.util.Set;

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
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The creatures that walk on land: in the new End, ender beetles grazing the chorus, crystal golems,
 * void stalkers striding on their stilts (a struck one blinks away, as the endermen do) and the
 * little endlings; in the Overworld, its beasts. There the hunters (lions, jaguars, hyenas,
 * crocodiles...) stalk the other animals as well as you, the hunted keep away from them, the shy
 * keep away from everyone, and monkeys climb anything and make off with whatever they find lying
 * about.
 */
public class EndWalker extends PathfinderMob implements SeaCreature {
	private static final EntityDataAccessor<Boolean> ATTACKING = SynchedEntityData.defineId(EndWalker.class, EntityDataSerializers.BOOLEAN);
	/** The shy: they keep their distance from people. */
	private static final Set<String> SHY = Set.of("deer", "flamingo", "capybara");

	public EndWalker(EntityType<? extends EndWalker> type, Level level) {
		super(type, level);
		if (this.isMonkey()) {
			this.setCanPickUpLoot(true);
		}
	}

	private boolean isMonkey() {
		return "monkey".equals(this.species().id());
	}

	/** Monkeys climb walls and trees as spiders do. */
	private boolean climbs() {
		return this.isMonkey();
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return this.climbs() ? new WallClimberNavigation(this, level) : super.createNavigation(level);
	}

	@Override
	public boolean onClimbable() {
		return this.climbs() && this.horizontalCollision || super.onClimbable();
	}

	/** What an Overworld hunter will chase: animals smaller than itself, but never someone's pet. */
	private boolean isPrey(LivingEntity target) {
		if (target instanceof TamableAnimal pet && pet.isTame() || target instanceof Mount mount && mount.isSaddledMount()) {
			return false;
		}
		boolean animal = target instanceof Animal || target instanceof EndWalker walker && !walker.species().behavior().isHostile();
		return animal && target.getBbWidth() * target.getBbHeight() < this.getBbWidth() * this.getBbHeight();
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
			if (this.species().overworld()) {
				this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
						(target, level) -> this.isPrey(target)));
			}
		} else if (this.species().overworld()) {
			// The hunted keep away from the hunters...
			this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, EndWalker.class, 12.0F, 1.1, 1.5,
					other -> other instanceof EndWalker walker && walker.species().behavior().isHostile() && walker.species().overworld()));
			// ...the shy from everyone, and a monkey from anyone who might want back what it is holding.
			if (SHY.contains(this.species().id())) {
				this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 10.0F, 1.1, 1.5));
			}
			if (this.isMonkey()) {
				this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 8.0F, 1.2, 1.6, player -> !this.getMainHandItem().isEmpty()));
			}
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
				if (this.randomTeleport(x, y, z, true)) {
					this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.8F);
					break;
				}
			}
		}
		return hurt;
	}

	/** The Overworld's animals stay where they are, as its cows and sheep do; its hunters come and go. */
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
}
