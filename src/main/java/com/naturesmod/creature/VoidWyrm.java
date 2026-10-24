package com.naturesmod.creature;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Void Wyrm: a great serpent of the void that comes down out of the dark when four star
 * fragments are set in a Star Altar. It dives at its prey like the other hunters of the End's sky,
 * and between its dives it breathes the void, calls down falling stars (watch for the pillars of
 * light where they will strike), looses volleys of shulker bullets that leave you floating, and
 * calls up its brood of void serpents. Hurt below half its health, it blinks about the sky.
 */
public class VoidWyrm extends EndFlyer {
	/** How far from where it strikes a falling star still hurts. */
	private static final double STAR_RADIUS = 3.5;
	private final ServerBossEvent bossEvent = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.PURPLE,
			BossEvent.BossBarOverlay.NOTCHED_10);
	/** Falling stars on their way down: where each will strike, and in how many ticks. */
	private final List<Star> stars = new ArrayList<>();
	private int breathCooldown = 60;
	private int starfallCooldown = 160;
	private int volleyCooldown = 110;
	private int broodCooldown = 300;
	private int blinkCooldown = 200;

	public VoidWyrm(EntityType<? extends VoidWyrm> type, Level level) {
		super(type, level);
		this.bossEvent.setCreateWorldFog(true);
	}

	private record Star(Vec3 at, int ticks) {
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	private boolean enraged() {
		return this.getHealth() < this.getMaxHealth() * 0.5F;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
		this.fallingStars(level);
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive()) {
			if (this.tickCount % 40 == 0) {
				this.heal(3.0F);
			}
			return;
		}
		boolean enraged = this.enraged();
		int haste = enraged ? 2 : 1;
		double distance = Math.sqrt(this.distanceToSqr(target));
		if (--this.breathCooldown <= 0 && distance < 48.0) {
			this.breathCooldown = (90 + this.random.nextInt(60)) / haste;
			this.breathe(level, target);
		}
		if (--this.starfallCooldown <= 0 && distance < 64.0) {
			this.starfallCooldown = (220 + this.random.nextInt(100)) / haste;
			this.starfall(level, target);
		}
		if (--this.volleyCooldown <= 0 && distance < 40.0) {
			this.volleyCooldown = (160 + this.random.nextInt(80)) / haste;
			this.volley(level, target);
		}
		if (--this.broodCooldown <= 0) {
			this.broodCooldown = 500 + this.random.nextInt(300);
			this.brood(level);
		}
		if (enraged && --this.blinkCooldown <= 0) {
			this.blinkCooldown = 120 + this.random.nextInt(120);
			this.blink(level, target);
		}
	}

	/** Void breath: a ball of the dragon's own fire, that leaves a lingering cloud where it bursts. */
	private void breathe(ServerLevel level, LivingEntity target) {
		Vec3 from = this.position().add(this.getLookAngle().scale(this.getBbWidth() * 0.6)).add(0.0, this.getBbHeight() * 0.5, 0.0);
		Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(from).normalize();
		DragonFireball fireball = new DragonFireball(level, this, aim);
		fireball.snapTo(from.x, from.y, from.z, this.getYRot(), this.getXRot());
		level.addFreshEntity(fireball);
		this.playSound(SoundEvents.ENDER_DRAGON_SHOOT, 4.0F, 0.7F);
	}

	/**
	 * Starfall: stars come down round its prey. A pillar of light marks each one a second and a
	 * half before it strikes; step out of it.
	 */
	private void starfall(ServerLevel level, LivingEntity target) {
		int count = this.enraged() ? 7 : 5;
		for (int i = 0; i < count; i++) {
			double x = target.getX() + (i == 0 ? 0.0 : (this.random.nextDouble() - 0.5) * 12.0);
			double z = target.getZ() + (i == 0 ? 0.0 : (this.random.nextDouble() - 0.5) * 12.0);
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			double y = ground > level.getMinY() + 1 && Math.abs(ground - target.getY()) < 16.0 ? ground : target.getY();
			this.stars.add(new Star(new Vec3(x, y, z), 30 + i * 4));
		}
		this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 5.0F, 1.2F);
	}

	private void fallingStars(ServerLevel level) {
		if (this.stars.isEmpty()) {
			return;
		}
		List<Star> waiting = new ArrayList<>();
		for (Star star : this.stars) {
			Vec3 at = star.at();
			if (star.ticks() > 0) {
				if (star.ticks() % 3 == 0) {
					level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 3.0, at.z, 8, 0.25, 3.0, 0.25, 0.0);
				}
				waiting.add(new Star(at, star.ticks() - 1));
				continue;
			}
			// The star strikes: it burns and hurls whatever stands where it lands, but leaves the ground whole.
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, 7.0, 7.0, 7.0), e -> e != this && e.isAlive())) {
				Vec3 away = victim.position().subtract(at);
				double falloff = 1.0 - Math.min(1.0, away.length() / STAR_RADIUS);
				if (falloff > 0.0 && victim.hurtServer(level, this.damageSources().explosion(this, this), (float) (2.0 + 12.0 * falloff))) {
					Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.8 * falloff);
					BossPowers.push(victim, new Vec3(push.x, 0.5 * falloff, push.z));
				}
			}
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y + 0.5, at.z, 60, 0.6, 0.6, 0.6, 0.25);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 30, 0.4, 0.4, 0.4, 0.3);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 3.0F, 0.6F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 2.0F, 1.2F);
		}
		this.stars.clear();
		this.stars.addAll(waiting);
	}

	/** A volley of shulker bullets: whoever they hit floats helpless for a while. */
	private void volley(ServerLevel level, LivingEntity target) {
		int count = this.enraged() ? 5 : 3;
		Direction.Axis[] axes = Direction.Axis.values();
		for (int i = 0; i < count; i++) {
			level.addFreshEntity(new ShulkerBullet(level, this, target, axes[this.random.nextInt(axes.length)]));
		}
		this.playSound(SoundEvents.SHULKER_SHOOT, 4.0F, 0.6F);
	}

	/** Its brood: void serpents, out of the dark. */
	private void brood(ServerLevel level) {
		EntityType<? extends Mob> type = ModCreatures.type("void_serpent");
		if (type == null) {
			return;
		}
		int alive = level.getEntitiesOfClass(EndFlyer.class, this.getBoundingBox().inflate(48.0), e -> e.getType() == type).size();
		for (int i = alive; i < 3; i++) {
			Mob serpent = type.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (serpent == null) {
				return;
			}
			serpent.snapTo(this.getX() + (this.random.nextDouble() - 0.5) * 8.0, this.getY() + 2.0, this.getZ() + (this.random.nextDouble() - 0.5) * 8.0,
					this.random.nextFloat() * 360.0F, 0.0F);
			serpent.finalizeSpawn(level, level.getCurrentDifficultyAt(serpent.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
			serpent.setTarget(this.getTarget());
			level.addFreshEntity(serpent);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, serpent.getX(), serpent.getY() + 0.5, serpent.getZ(), 40, 0.6, 0.6, 0.6, 0.1);
		}
		this.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 4.0F, 0.6F);
	}

	/** Blink: gone in a puff of the void, to strike again from somewhere else in the sky. */
	private void blink(ServerLevel level, LivingEntity target) {
		RandomSource random = this.random;
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double reach = 14.0 + random.nextDouble() * 10.0;
			double x = target.getX() + Math.cos(angle) * reach;
			double z = target.getZ() + Math.sin(angle) * reach;
			double y = Math.max(target.getY() + 6.0 + random.nextDouble() * 8.0, level.getMinY() + 24.0);
			Vec3 from = this.position();
			// Takes to the air there, if there is room.
			if (level.isLoaded(BlockPos.containing(x, y, z))
					&& level.noCollision(this, this.getBoundingBox().move(x - this.getX(), y - this.getY(), z - this.getZ()))) {
				this.teleportTo(x, y, z);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 80, 1.5, 1.5, 1.5, 0.2);
				level.sendParticles(ParticleTypes.PORTAL, x, y + 1.0, z, 80, 1.5, 1.5, 1.5, 0.5);
				this.playSound(SoundEvents.ENDERMAN_TELEPORT, 4.0F, 0.5F);
				return;
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean wasEnraged = this.enraged();
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && !wasEnraged && this.enraged() && this.isAlive()) {
			// Half dead, and furious.
			this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 6.0F, 0.6F);
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(64.0))) {
				player.sendSystemMessage(Component.translatable("message.naturesmod.void_wyrm.enraged").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE),
						true);
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
			}
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.0, this.getZ(), 300, 3.0, 2.0, 3.0, 0.3);
			level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 200, 3.0, 2.0, 3.0, 0.2);
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(96.0))) {
				player.sendSystemMessage(Component.translatable("message.naturesmod.void_wyrm.slain")
						.withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.BOLD), false);
			}
		}
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 500;
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		this.bossEvent.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bossEvent.removePlayer(player);
	}

	@Override
	public void setCustomName(Component name) {
		super.setCustomName(name);
		this.bossEvent.setName(this.getDisplayName());
	}

	@Override
	protected float getSoundVolume() {
		return 5.0F;
	}
}
