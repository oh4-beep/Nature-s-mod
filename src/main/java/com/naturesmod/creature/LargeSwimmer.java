package com.naturesmod.creature;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The big swimmers: sharks, eels, rays, squid, the deep-sea hunters and the Abyssal Leviathan.
 * They glide smoothly through the water and pitch up and down as they swim. What they do with a
 * diver depends on their {@link Behavior}.
 */
public class LargeSwimmer extends WaterAnimal implements SeaCreature {
	private static final EntityDataAccessor<Boolean> ATTACKING = SynchedEntityData.defineId(LargeSwimmer.class, EntityDataSerializers.BOOLEAN);

	/** Ticks until a predator that has just eaten is hungry again. */
	private int fullTicks;
	/** Where an ambush predator lurks. */
	private BlockPos home;
	private ServerBossEvent bossEvent;
	// The boss powers (see BossPowers) and the charge every boss has.
	int powerCooldown = 100;
	int broodCooldown = 300;
	int chargeCooldown = 160;
	int chargeTicks;
	int diveTicks;
	int frenzyCharges;
	private double chargeSpeed = 1.4;
	/** Ticks a hunting boss of the descent has been left without a diver near it. */
	private int lonelyTicks;
	private Boolean hunter;

	public LargeSwimmer(EntityType<? extends LargeSwimmer> type, Level level) {
		super(type, level);
		this.moveControl = new SmoothSwimmingMoveControl<>(this, 85, 10, 0.02F, 0.1F, true);
		this.lookControl = new SmoothSwimmingLookControl(this, 10);
		if (this.species().behavior() == Behavior.BOSS) {
			this.bossEvent = new ServerBossEvent(this.getUUID(), this.getDisplayName(), barColor(this.species().id()), BossEvent.BossBarOverlay.NOTCHED_10);
			this.bossEvent.setDarkenScreen(true);
		}
	}

	private static BossEvent.BossBarColor barColor(String id) {
		return switch (id) {
			case "tidewyrm", "twin_terror" -> BossEvent.BossBarColor.GREEN;
			case "razorjaw", "kraken", "hollow_king", "megalodon" -> BossEvent.BossBarColor.RED;
			case "gloomlantern" -> BossEvent.BossBarColor.YELLOW;
			case "siren_queen" -> BossEvent.BossBarColor.PINK;
			case "bonewarden", "dunkleosteus" -> BossEvent.BossBarColor.WHITE;
			case "abyssal_horror" -> BossEvent.BossBarColor.PURPLE;
			default -> BossEvent.BossBarColor.BLUE;
		};
	}

	/** Whether this boss was sent by the Abyss to hunt a diver on the way down. */
	public boolean isHunter() {
		if (this.hunter == null) {
			// The mark is kept as an entity tag so it survives saving; adding it tells whether it was there.
			this.hunter = !this.addTag(AbyssBosses.HUNTER_TAG);
			if (!this.hunter) {
				this.removeTag(AbyssBosses.HUNTER_TAG);
			}
		}
		return this.hunter;
	}

	void markHunter() {
		this.addTag(AbyssBosses.HUNTER_TAG);
		this.hunter = true;
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

	BlockPos home() {
		return this.home;
	}

	private boolean hungry() {
		return this.fullTicks <= 0;
	}

	@Override
	protected void registerGoals() {
		SpeciesData species = this.species();
		Behavior behavior = species.behavior();
		double size = species.width();
		switch (behavior) {
			case PASSIVE, BOTTOM, DRIFTER, SCHOOL -> {
				this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
				this.goalSelector.addGoal(4, behavior == Behavior.BOTTOM
						? new CreatureGoals.BottomSwimGoal(this, 0.9, 40)
						: new RandomSwimmingGoal(this, behavior == Behavior.DRIFTER ? 0.6 : 1.0, 30));
			}
			case NEUTRAL -> {
				this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3, true));
				this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 20));
				this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
				this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractFish.class, 40, true, false,
						CreatureGoals.prey(this, 12 + size * 4, this::hungry)));
			}
			case AMBUSH -> {
				this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 2.0, false));
				this.goalSelector.addGoal(4, new CreatureGoals.LurkGoal(this));
				this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
				this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 2, true, false,
						CreatureGoals.diver(this, 4.5, false)));
				this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractFish.class, 10, true, false,
						CreatureGoals.prey(this, 4.5, this::hungry)));
			}
			case HUNTER, PACK, BOSS -> {
				this.goalSelector.addGoal(1, new MeleeAttackGoal(this, behavior == Behavior.BOSS ? 1.2 : 1.4, true));
				this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 10));
				this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
				this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
						CreatureGoals.diver(this, behavior == Behavior.BOSS ? 48 : 10 + size * 3, true)));
				this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractFish.class, 40, true, false,
						CreatureGoals.prey(this, 16 + size * 4, this::hungry)));
			}
		}
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WaterBoundPathNavigation(this, level);
	}

	@Override
	protected void travelInWater(Vec3 input, double baseGravity, boolean isFalling, double oldY) {
		this.moveRelative(this.getSpeed(), input);
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
		if (this.getTarget() == null && this.home == null) {
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.005, 0.0));
		}
	}

	@Override
	public int getMaxHeadXRot() {
		return 1;
	}

	@Override
	public int getMaxHeadYRot() {
		return 1;
	}

	@Override
	public int getMaxSpawnClusterSize() {
		return this.species().groupMax();
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return this.species().behavior() != Behavior.BOSS && !this.hasCustomName();
	}

	@Override
	public boolean requiresCustomPersistence() {
		return super.requiresCustomPersistence() || this.species().behavior() == Behavior.BOSS;
	}

	@Override
	public void aiStep() {
		SpeciesData species = this.species();
		if (!this.isInWater() && this.onGround() && this.verticalCollision && species.scale() < 2.0F) {
			// Stranded: flop about helplessly.
			this.setDeltaMovement(this.getDeltaMovement().add((this.random.nextFloat() * 2.0F - 1.0F) * 0.05F, 0.4F, (this.random.nextFloat() * 2.0F - 1.0F) * 0.05F));
			this.setOnGround(false);
			this.needsSync = true;
			this.makeSound(CreatureSounds.flop(species));
		}
		super.aiStep();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.fullTicks > 0) {
			this.fullTicks--;
		}
		if (this.home == null && species.behavior() == Behavior.AMBUSH) {
			this.home = this.blockPosition();
		}
		LivingEntity target = this.getTarget();
		double reach = this.getBbWidth() + 2.5;
		this.entityData.set(ATTACKING, target != null && this.distanceToSqr(target) < reach * reach);
		if (species.behavior() == Behavior.BOSS) {
			this.bossTick(level, target);
		}
	}

	@Override
	public void playerTouch(Player player) {
		if (this.species().stings() && this.level() instanceof ServerLevel level) {
			this.sting(level, player);
		}
	}

	/** Venomous spines: a lionfish hurts whoever brushes against it. */
	private void sting(ServerLevel level, Player player) {
		if (player.isCreative() || player.isSpectator() || level.getDifficulty() == Difficulty.PEACEFUL || !this.doTeamsAllowDamage(player)) {
			return;
		}
		float damage = (float) this.species().attack();
		if (player.hurtServer(level, this.damageSources().mobAttack(this), damage)) {
			player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), this);
			this.playSound(SoundEvents.PUFFER_FISH_STING, 1.0F, 1.0F);
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			this.playSound(SoundEvents.PHANTOM_BITE, 1.0F, 0.6F + this.random.nextFloat() * 0.3F);
			CreatureEffects.bite(this, this.species(), target);
			if (target instanceof LivingEntity living && living.isDeadOrDying()) {
				// A full stomach: leave the other fish alone for a while.
				this.fullTicks = 1200 + this.random.nextInt(2400);
				this.heal(4.0F);
			}
		}
		return hit;
	}

	/** Every boss: its powers, a charge that knocks divers about, and slow regeneration. */
	private void bossTick(ServerLevel level, LivingEntity target) {
		this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
		if (this.isHunter() && this.tickCount % 20 == 0) {
			// A hunter that has lost its prey gives up and sinks back into the dark.
			this.lonelyTicks = level.getNearestPlayer(this, 96.0) == null ? this.lonelyTicks + 20 : 0;
			if (this.lonelyTicks > 1200) {
				this.discard();
				return;
			}
		}
		if (target == null) {
			if (this.tickCount % 40 == 0) {
				this.heal(2.0F);
			}
			return;
		}
		BossPowers.tick(this, level, target);
		double distance = this.distanceToSqr(target);
		if (this.chargeTicks > 0) {
			this.chargeTicks--;
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(1.0), e -> e != this && e.isAlive()
					&& !(e instanceof LargeSwimmer other && other.species().behavior() == Behavior.BOSS))) {
				if (victim.hurtServer(level, this.damageSources().mobAttack(this), (float) this.species().attack() * 0.75F)) {
					Vec3 push = victim.position().subtract(this.position()).normalize().scale(this.chargeSpeed + 0.2);
					BossPowers.push(victim, new Vec3(push.x, 0.4 + push.y * 0.5, push.z));
					CreatureEffects.bite(this, this.species(), victim);
				}
			}
		} else if (--this.chargeCooldown <= 0 && distance > 36.0 && distance < 900.0) {
			boolean frenzy = this.frenzyCharges > 0;
			if (frenzy) {
				this.frenzyCharges--;
			}
			boolean enraged = this.getHealth() < this.getMaxHealth() * 0.5F;
			this.chargeCooldown = frenzy ? 25 : (140 + this.random.nextInt(120)) / (enraged ? 2 : 1);
			this.charge(target, frenzy ? 1.7 : 1.4, 12);
		}
	}

	/** Dashes at the target, hurting and hurling whatever it hits on the way. */
	void charge(LivingEntity target, double speed, int ticks) {
		this.chargeTicks = ticks;
		this.chargeSpeed = speed;
		Vec3 dash = target.getEyePosition().subtract(this.position()).normalize().scale(speed);
		this.setDeltaMovement(dash);
		this.playSound(SoundEvents.ELDER_GUARDIAN_HURT, 3.0F, 0.4F);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level && this.isHunter()) {
			AbyssBosses.defeated(level, this);
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (this.bossEvent != null) {
			this.bossEvent.addPlayer(player);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		if (this.bossEvent != null) {
			this.bossEvent.removePlayer(player);
		}
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		SpeciesData species = this.species();
		if (species.behavior() == Behavior.BOSS) {
			return 250;
		}
		return 1 + (int) (species.health() / 6.0) + this.random.nextInt(3);
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
	protected SoundEvent getSwimSound() {
		return SoundEvents.FISH_SWIM;
	}

	@Override
	protected float getSoundVolume() {
		return Math.min(4.0F, 0.6F + this.species().scale() * 0.3F);
	}

	@Override
	public void setCustomName(Component name) {
		super.setCustomName(name);
		if (this.bossEvent != null) {
			this.bossEvent.setName(this.getDisplayName());
		}
	}
}
