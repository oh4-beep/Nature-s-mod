package com.naturesmod.item;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.naturesmod.NaturesMod;

/**
 * The lost weapons of the Abyss, forged by a sea-going people long drowned. Each one strikes
 * with something extra, and each answers its wielder's call (use) with a power of its own.
 */
public class AbyssWeapon extends Item {
	public static final TagKey<Item> REPAIRS = TagKey.create(Registries.ITEM, NaturesMod.id("repairs_abyss_weapons"));
	/** Better than netherite in every way but one: it can only be found, never made. */
	public static final ToolMaterial MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2600, 9.0F, 5.0F, 18, REPAIRS);

	private final Kind kind;

	public AbyssWeapon(Item.Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	// --- striking --------------------------------------------------------------------------

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!(attacker.level() instanceof ServerLevel level)) {
			return;
		}
		switch (kind) {
			case TIDECLEAVER -> {
				// The blow carries through to everything just behind the target.
				Vec3 through = target.position().subtract(attacker.position()).normalize();
				for (LivingEntity other : foes(level, attacker, target.position().add(through.scale(1.5)), 2.5)) {
					if (other != target) {
						other.hurtServer(level, source(attacker), 6.0F);
					}
				}
			}
			case LEVIATHAN_FANG -> {
				target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1), attacker);
				attacker.heal(2.0F);
			}
			case ABYSSAL_SCYTHE -> {
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1), attacker);
				attacker.heal(3.0F);
			}
			case STORMCALLER -> {
				if (level.getRandom().nextInt(4) == 0) {
					lightning(level, attacker, target.position());
				}
			}
			case KRAKENS_GRASP -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), attacker);
			case PRESSURE_MAUL -> shockwave(level, attacker, target.position(), 3.5, 5.0F, 0.9);
			case CORAL_RAPIER -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), attacker);
			case SOVEREIGN_STAFF -> {
			}
		}
	}

	// --- powers ------------------------------------------------------------------------------

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		boolean used = switch (kind) {
			case TIDECLEAVER -> tidalWave(server, player);
			case LEVIATHAN_FANG -> shadowstep(server, player);
			case ABYSSAL_SCYTHE -> reap(server, player);
			case STORMCALLER -> callStorm(server, player);
			case KRAKENS_GRASP -> grasp(server, player);
			case PRESSURE_MAUL -> slam(server, player);
			case SOVEREIGN_STAFF -> fangs(server, player);
			case CORAL_RAPIER -> lunge(server, player);
		};
		if (!used) {
			return InteractionResult.FAIL;
		}
		player.getCooldowns().addCooldown(stack, kind.cooldown);
		stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
		return InteractionResult.SUCCESS;
	}

	/** Tidecleaver: a wall of water bursts out and hurls everything around away. */
	private static boolean tidalWave(ServerLevel level, Player player) {
		for (LivingEntity foe : foes(level, player, player.position(), 7.0)) {
			foe.hurtServer(level, source(player), 8.0F);
			push(foe, foe.position().subtract(player.position()).normalize().scale(1.8), 0.5);
		}
		level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(), 200, 3.5, 1.0, 3.5, 0.4);
		level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, player.getX(), player.getY() + 0.5, player.getZ(), 120, 3.5, 0.5, 3.5, 0.3);
		level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 2.0F, 0.6F);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.5F, 0.8F);
		return true;
	}

	/** Leviathan Fang: step through the dark to strike from behind whatever you are looking at. */
	private static boolean shadowstep(ServerLevel level, Player player) {
		LivingEntity target = lookedAt(level, player, 20.0);
		if (target == null) {
			return false;
		}
		Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(1.5));
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
		player.teleportTo(behind.x, target.getY(), behind.z);
		level.sendParticles(ParticleTypes.SQUID_INK, behind.x, target.getY() + 1.0, behind.z, 30, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1), player);
		return true;
	}

	/** Abyssal Scythe: one great sweep that cuts everything near and feeds on what it cuts. */
	private static boolean reap(ServerLevel level, Player player) {
		List<LivingEntity> foes = foes(level, player, player.position(), 5.0);
		for (LivingEntity foe : foes) {
			foe.hurtServer(level, source(player), 9.0F);
			foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1), player);
		}
		player.heal(2.0F * Math.min(5, foes.size()));
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(), 12, 2.5, 0.3, 2.5, 0.0);
		level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 20, 2.5, 0.5, 2.5, 0.02);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5F, 0.5F);
		return true;
	}

	/** Stormcaller: lightning out of a sea that has never seen the sky. */
	private static boolean callStorm(ServerLevel level, Player player) {
		LivingEntity target = lookedAt(level, player, 32.0);
		if (target == null) {
			return false;
		}
		lightning(level, player, target.position());
		target.hurtServer(level, source(player), 6.0F);
		return true;
	}

	/** Kraken's Grasp: a lash of living tentacle that drags its prey to you. */
	private static boolean grasp(ServerLevel level, Player player) {
		LivingEntity target = lookedAt(level, player, 20.0);
		if (target == null) {
			return false;
		}
		Vec3 pull = player.position().subtract(target.position());
		push(target, pull.normalize().scale(Math.min(2.6, pull.length() * 0.25)), 0.3);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3), player);
		level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.SQUID_SQUIRT, SoundSource.PLAYERS, 1.5F, 0.5F);
		return true;
	}

	/** Pressure Maul: the crushing weight of the deep, brought down on everything around you. */
	private static boolean slam(ServerLevel level, Player player) {
		shockwave(level, player, player.position(), 6.0, 10.0F, 1.4);
		level.levelEvent(2013, player.getOnPos(), 750);
		level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0F, 0.7F);
		return true;
	}

	/** Sovereign's Staff: the jaws of the deep snap shut along the ground ahead of you. */
	private static boolean fangs(ServerLevel level, Player player) {
		Vec3 ahead = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (ahead.lengthSqr() < 1.0E-4) {
			ahead = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		ahead = ahead.normalize();
		float yaw = (float) Mth.atan2(ahead.z, ahead.x);
		int placed = 0;
		for (int i = 1; i <= 14; i++) {
			double x = player.getX() + ahead.x * i * 1.25;
			double z = player.getZ() + ahead.z * i * 1.25;
			Optional<Double> y = groundNear(level, x, player.getY(), z);
			if (y.isEmpty()) {
				continue;
			}
			level.addFreshEntity(new EvokerFangs(level, x, y.get(), z, yaw, i, player));
			placed++;
		}
		if (placed > 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		return placed > 0;
	}

	/** Coral Rapier: a lightning lunge in the direction you look. */
	private static boolean lunge(ServerLevel level, Player player) {
		Vec3 dash = player.getLookAngle().scale(player.isInWater() ? 2.2 : 1.6);
		player.setDeltaMovement(dash);
		sync(player);
		level.sendParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.3, 0.5, 0.3, 0.1);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 1.4F);
		return true;
	}

	// --- helpers ------------------------------------------------------------------------------

	private static DamageSource source(LivingEntity attacker) {
		return attacker instanceof Player player ? attacker.damageSources().playerAttack(player) : attacker.damageSources().mobAttack(attacker);
	}

	/** Living things near a point that the wielder may hurt: not themselves, their allies, pets or armor stands. */
	static List<LivingEntity> foes(ServerLevel level, LivingEntity wielder, Vec3 center, double radius) {
		AABB area = new AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
		return level.getEntitiesOfClass(LivingEntity.class, area, e -> e != wielder && e.isAlive() && !e.isSpectator()
				&& !wielder.isAlliedTo(e) && !(e instanceof ArmorStand) && !(e instanceof TamableAnimal pet && pet.isTame())
				&& !(e instanceof Player p && p.isCreative()) && e.distanceToSqr(center) <= radius * radius);
	}

	private static void shockwave(ServerLevel level, LivingEntity wielder, Vec3 center, double radius, float damage, double strength) {
		for (LivingEntity foe : foes(level, wielder, center, radius)) {
			Vec3 away = foe.position().subtract(center);
			double falloff = 1.0 - Math.min(1.0, away.length() / radius) * 0.5;
			foe.hurtServer(level, source(wielder), damage * (float) falloff);
			push(foe, away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(strength * falloff), 0.6 * falloff);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 3, radius * 0.4, 0.2, radius * 0.4, 0.0);
	}

	private static void lightning(ServerLevel level, LivingEntity caller, Vec3 at) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) {
			return;
		}
		bolt.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		if (caller instanceof ServerPlayer player) {
			bolt.setCause(player);
		}
		level.addFreshEntity(bolt);
	}

	private static void push(LivingEntity entity, Vec3 horizontal, double up) {
		entity.push(horizontal.x, up + horizontal.y * 0.5, horizontal.z);
		sync(entity);
	}

	private static void sync(Entity entity) {
		entity.needsSync = true;
		if (entity instanceof ServerPlayer player) {
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		}
	}

	/** The living thing the player is looking straight at, within range. */
	static LivingEntity lookedAt(ServerLevel level, Player player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(range));
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : foes(level, player, eye, range)) {
			Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.4).clip(eye, end);
			if (hit.isPresent()) {
				double distance = eye.distanceToSqr(hit.get());
				if (distance < bestDistance) {
					best = candidate;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	/** The top of the ground near a height, for the fangs to rise from. */
	private static Optional<Double> groundNear(ServerLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(x, y + 2.0, z).mutable();
		for (int i = 0; i < 6; i++) {
			BlockPos below = pos.below();
			if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return Optional.of((double) pos.getY());
			}
			pos.move(Direction.DOWN);
		}
		return Optional.empty();
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.naturesmod." + kind.id + ".strike").withStyle(ChatFormatting.DARK_AQUA));
		builder.accept(Component.translatable("item.naturesmod." + kind.id + ".power").withStyle(ChatFormatting.AQUA));
	}

	public enum Kind {
		TIDECLEAVER("tidecleaver", 6.0F, -3.0F, 160),
		LEVIATHAN_FANG("leviathan_fang", 1.0F, -1.6F, 100),
		ABYSSAL_SCYTHE("abyssal_scythe", 5.0F, -2.8F, 140),
		STORMCALLER("stormcaller", 4.0F, -2.6F, 100),
		KRAKENS_GRASP("krakens_grasp", 3.0F, -2.2F, 80),
		PRESSURE_MAUL("pressure_maul", 8.0F, -3.3F, 200),
		SOVEREIGN_STAFF("sovereign_staff", 0.0F, -2.0F, 50),
		CORAL_RAPIER("coral_rapier", 1.0F, -1.2F, 40);

		public final String id;
		final float damage;
		final float speed;
		final int cooldown;

		Kind(String id, float damage, float speed, int cooldown) {
			this.id = id;
			this.damage = damage;
			this.speed = speed;
			this.cooldown = cooldown;
		}
	}
}
