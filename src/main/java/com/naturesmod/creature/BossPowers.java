package com.naturesmod.creature;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.phys.Vec3;

/**
 * What makes each boss of the Abyss its own fight. Every boss charges; on top of that each has
 * powers of its own, on their own cooldowns.
 */
final class BossPowers {
	private BossPowers() {
	}

	static void tick(LargeSwimmer boss, ServerLevel level, LivingEntity target) {
		RandomSource random = boss.getRandom();
		String id = boss.species().id();
		boolean enraged = boss.getHealth() < boss.getMaxHealth() * 0.5F;
		// The main power.
		if (--boss.powerCooldown <= 0) {
			boss.powerCooldown = switch (id) {
				case "tidewyrm" -> whirlpool(boss, level);
				case "gloomlantern" -> lureFlash(boss, level, target);
				case "stormcoil" -> discharge(boss, level);
				case "kraken" -> grab(boss, level, target);
				case "siren_queen" -> toxicBloom(boss, level, target);
				case "bonewarden" -> boneBarrage(boss, level, target);
				case "abyssal_manta" -> shadowDive(boss, level);
				case "hollow_king" -> gaze(boss, level);
				case "dunkleosteus" -> pressureWave(boss, level);
				case "twin_terror" -> venomSpit(boss, level, target);
				case "razorjaw", "megalodon" -> frenzy(boss);
				default -> roar(boss, level);
			} + random.nextInt(60);
		}
		// A second power: calling up their brood, or the kraken's ink.
		if (--boss.broodCooldown <= 0) {
			boss.broodCooldown = switch (id) {
				case "siren_queen" -> brood(boss, level, "lions_mane_jelly", 3);
				case "bonewarden" -> brood(boss, level, "bone_shark", 2);
				case "hollow_king" -> brood(boss, level, "hollow_eye", 2);
				case "abyssal_horror" -> brood(boss, level, "rotfin", 4);
				case "kraken" -> ink(boss, level);
				default -> 100000;
			} + random.nextInt(100);
		}
		// The Megalodon goes mad with blood when it is hurt.
		if (enraged && "megalodon".equals(id) && boss.tickCount % 20 == 0) {
			boss.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1, true, false));
		}
		// The manta's dive ends in a strike out of nowhere.
		if (boss.diveTicks > 0 && --boss.diveTicks == 0) {
			boss.charge(target, 1.9, 16);
		}
	}

	// --- helpers ------------------------------------------------------------------------------

	static List<ServerPlayer> victims(ServerLevel level, Entity boss, double radius) {
		return level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(radius),
				player -> !player.isCreative() && !player.isSpectator() && player.isAlive());
	}

	static void push(Entity entity, Vec3 motion) {
		entity.push(motion.x, motion.y, motion.z);
		entity.needsSync = true;
		if (entity instanceof ServerPlayer player) {
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		}
	}

	private static void effect(LivingEntity victim, Entity source, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
			int ticks, int amplifier) {
		victim.addEffect(new MobEffectInstance(effect, ticks, amplifier), source);
	}

	// --- the powers -------------------------------------------------------------------------
	// Each returns the ticks until it can be used again.

	/** The Leviathan and the Horror: a roar that plunges divers into darkness. */
	private static int roar(LargeSwimmer boss, ServerLevel level) {
		boss.playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 4.0F, 0.5F);
		for (ServerPlayer player : victims(level, boss, 48.0)) {
			effect(player, boss, MobEffects.DARKNESS, 200, 0);
			effect(player, boss, MobEffects.MINING_FATIGUE, 600, 1);
		}
		return 300;
	}

	/** Tidewyrm: a whirlpool that drags divers in and spins their heads. */
	private static int whirlpool(LargeSwimmer boss, ServerLevel level) {
		for (ServerPlayer player : victims(level, boss, 24.0)) {
			Vec3 in = boss.position().subtract(player.position());
			Vec3 swirl = new Vec3(-in.z, 0.0, in.x).normalize().scale(0.6);
			push(player, in.normalize().scale(1.1).add(swirl));
			effect(player, boss, MobEffects.NAUSEA, 140, 0);
			effect(player, boss, MobEffects.SLOWNESS, 60, 1);
		}
		for (int i = 0; i < 6; i++) {
			double angle = i * Math.PI / 3;
			level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, boss.getX() + Math.cos(angle) * 4, boss.getY(), boss.getZ() + Math.sin(angle) * 4,
					30, 0.6, 2.0, 0.6, 0.2);
		}
		boss.playSound(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 4.0F, 0.5F);
		return 160;
	}

	/** Gloomlantern: the lure blazes, the diver is blinded, and then the jaws come. */
	private static int lureFlash(LargeSwimmer boss, ServerLevel level, LivingEntity target) {
		for (ServerPlayer player : victims(level, boss, 32.0)) {
			effect(player, boss, MobEffects.BLINDNESS, 80, 0);
			effect(player, boss, MobEffects.DARKNESS, 180, 0);
		}
		level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + boss.getBbHeight(), boss.getZ(), 80, 1.0, 1.0, 1.0, 0.25);
		boss.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 0.6F);
		boss.charge(target, 1.6, 14);
		return 200;
	}

	/** Stormcoil: the storm in its body breaks loose all around it. */
	private static int discharge(LargeSwimmer boss, ServerLevel level) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(boss.getX(), boss.getY(), boss.getZ(), 0.0F, 0.0F);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		for (ServerPlayer player : victims(level, boss, 10.0)) {
			player.hurtServer(level, boss.damageSources().lightningBolt(), 8.0F);
			effect(player, boss, MobEffects.SLOWNESS, 80, 2);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + 1.0, boss.getZ(), 200, 5.0, 3.0, 5.0, 0.6);
		boss.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 1.4F);
		return 130;
	}

	/** Kraken: a tentacle shoots out and drags its prey in. */
	private static int grab(LargeSwimmer boss, ServerLevel level, LivingEntity target) {
		if (boss.distanceToSqr(target) < 24.0 * 24.0) {
			push(target, boss.position().subtract(target.position()).normalize().scale(1.8));
			effect(target, boss, MobEffects.SLOWNESS, 60, 3);
			boss.playSound(SoundEvents.SQUID_SQUIRT, 3.0F, 0.4F);
		}
		return 110;
	}

	/** Kraken: a cloud of ink that blinds everything around. */
	private static int ink(LargeSwimmer boss, ServerLevel level) {
		level.sendParticles(ParticleTypes.SQUID_INK, boss.getX(), boss.getY() + 1.0, boss.getZ(), 300, 4.0, 3.0, 4.0, 0.1);
		for (ServerPlayer player : victims(level, boss, 16.0)) {
			effect(player, boss, MobEffects.BLINDNESS, 120, 0);
		}
		boss.playSound(SoundEvents.SQUID_SQUIRT, 4.0F, 0.3F);
		return 200;
	}

	/** Siren Queen: a bloom of poison all round her prey. */
	private static int toxicBloom(LargeSwimmer boss, ServerLevel level, LivingEntity target) {
		for (int i = 0; i < 3; i++) {
			double angle = i * Math.PI * 2 / 3;
			cloud(level, boss, target.getX() + Math.cos(angle) * 3, target.getY(), target.getZ() + Math.sin(angle) * 3, 4.0F,
					new MobEffectInstance(MobEffects.POISON, 120, 1));
		}
		boss.playSound(SoundEvents.PUFFER_FISH_BLOW_UP, 3.0F, 0.5F);
		return 180;
	}

	private static void cloud(ServerLevel level, Mob owner, double x, double y, double z, float radius, MobEffectInstance effect) {
		AreaEffectCloud cloud = new AreaEffectCloud(level, x, y, z);
		cloud.setOwner(owner);
		cloud.setRadius(radius);
		cloud.setDuration(160);
		cloud.setWaitTime(10);
		cloud.setRadiusPerTick(-radius / 200.0F);
		cloud.addEffect(effect);
		level.addFreshEntity(cloud);
	}

	/** Bonewarden: skulls of black fire, hurled at its prey. */
	private static int boneBarrage(LargeSwimmer boss, ServerLevel level, LivingEntity target) {
		RandomSource random = boss.getRandom();
		Vec3 from = boss.position().add(0.0, boss.getBbHeight() * 0.6, 0.0);
		for (int i = 0; i < 3; i++) {
			Vec3 aim = target.getEyePosition().subtract(from).normalize()
					.add(random.nextGaussian() * 0.08, random.nextGaussian() * 0.08, random.nextGaussian() * 0.08);
			WitherSkull skull = new WitherSkull(level, boss, aim);
			skull.setPos(from.x, from.y, from.z);
			level.addFreshEntity(skull);
		}
		boss.playSound(SoundEvents.WITHER_SHOOT, 3.0F, 0.6F);
		return 140;
	}

	/** Abyssal Manta: it vanishes into the dark, and strikes from nowhere a moment later. */
	private static int shadowDive(LargeSwimmer boss, ServerLevel level) {
		boss.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false));
		level.sendParticles(ParticleTypes.SQUID_INK, boss.getX(), boss.getY() + 0.5, boss.getZ(), 120, 3.0, 0.5, 3.0, 0.05);
		boss.playSound(SoundEvents.PHANTOM_SWOOP, 3.0F, 0.5F);
		boss.diveTicks = 40;
		return 220;
	}

	/** The Hollow King: its gaze withers, darkens and weighs on everyone it can see. */
	private static int gaze(LargeSwimmer boss, ServerLevel level) {
		for (ServerPlayer player : victims(level, boss, 40.0)) {
			if (boss.hasLineOfSight(player)) {
				effect(player, boss, MobEffects.MINING_FATIGUE, 600, 2);
				effect(player, boss, MobEffects.WITHER, 100, 0);
				effect(player, boss, MobEffects.DARKNESS, 160, 0);
			}
		}
		boss.playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 4.0F, 0.8F);
		return 220;
	}

	/** Dunkleosteus: a pressure wave that slams everything away and leaves it reeling. */
	private static int pressureWave(LargeSwimmer boss, ServerLevel level) {
		for (ServerPlayer player : victims(level, boss, 10.0)) {
			Vec3 away = player.position().subtract(boss.position()).normalize();
			push(player, away.scale(1.6).add(0.0, 0.4, 0.0));
			player.hurtServer(level, boss.damageSources().mobAttack(boss), 6.0F);
			effect(player, boss, MobEffects.NAUSEA, 120, 0);
			effect(player, boss, MobEffects.MINING_FATIGUE, 200, 1);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 1.0, boss.getZ(), 8, 3.0, 1.0, 3.0, 0.0);
		boss.playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.6F);
		return 180;
	}

	/** Twin Terror: both heads spit venom where its prey swims. */
	private static int venomSpit(LargeSwimmer boss, ServerLevel level, LivingEntity target) {
		cloud(level, boss, target.getX(), target.getY(), target.getZ(), 3.5F, new MobEffectInstance(MobEffects.POISON, 140, 1));
		cloud(level, boss, target.getX(), target.getY() + 1.0, target.getZ(), 2.5F, new MobEffectInstance(MobEffects.WITHER, 80, 0));
		boss.playSound(SoundEvents.LLAMA_SPIT, 3.0F, 0.5F);
		return 120;
	}

	/** Razorjaw and Megalodon: a frenzy of charges, one after the other. */
	private static int frenzy(LargeSwimmer boss) {
		boss.frenzyCharges = 3;
		boss.chargeCooldown = 0;
		boss.playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
		return 240;
	}

	/** Calls up a brood of lesser creatures, as long as there are not too many already. */
	private static int brood(LargeSwimmer boss, ServerLevel level, String species, int count) {
		EntityType<? extends Mob> type = ModCreatures.type(species);
		if (type == null) {
			return 600;
		}
		if (level.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(24.0), mob -> mob.getType() == type).size() >= count * 2) {
			return 300;
		}
		RandomSource random = boss.getRandom();
		for (int i = 0; i < count; i++) {
			Mob minion = type.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (minion == null) {
				break;
			}
			double x = boss.getX() + random.nextGaussian() * 3.0;
			double y = boss.getY() + random.nextGaussian();
			double z = boss.getZ() + random.nextGaussian() * 3.0;
			if (!level.getFluidState(BlockPos.containing(x, y, z)).isEmpty()) {
				minion.snapTo(x, y, z, random.nextFloat() * 360.0F, 0.0F);
				minion.setTarget(boss.getTarget());
				level.addFreshEntity(minion);
			}
		}
		level.sendParticles(ParticleTypes.SCULK_SOUL, boss.getX(), boss.getY() + 1.0, boss.getZ(), 40, 2.0, 1.0, 2.0, 0.05);
		return 500;
	}
}
