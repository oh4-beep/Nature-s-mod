package com.naturesmod.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.naturesmod.NaturesMod;

/**
 * The weapons of the stars, forged of starmetal by the star-folk of the End and kept by the Void
 * Wyrm, which leaves one behind when it falls. Like the lost weapons of the Abyss, each strikes
 * with something extra and answers its wielder's call (use) with a power of its own.
 */
public class EndWeapon extends Item {
	public static final TagKey<Item> REPAIRS = TagKey.create(Registries.ITEM, NaturesMod.id("repairs_end_weapons"));
	/** Starmetal: as hard as netherite, and it takes enchantments well. */
	public static final ToolMaterial MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2400, 9.0F, 5.0F, 20, REPAIRS);
	private static final double BLINK_RANGE = 24.0;

	private final Kind kind;

	public EndWeapon(Item.Properties properties, Kind kind) {
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
			// Cut loose from the ground: the target floats up helplessly for a moment.
			case VOIDBLADE -> target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 25, 1), attacker);
			case STARCALLER -> {
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0), attacker);
				if (!target.onGround()) {
					// Whatever flies burns brightest.
					target.hurtServer(level, attacker.damageSources().indirectMagic(attacker, attacker), 5.0F);
				}
			}
			case GRAVITY_HAMMER -> {
				AbyssWeapon.push(target, Vec3.ZERO, 0.9);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), attacker);
			}
			case SKYRIDER_GLAIVE -> {
				if (!attacker.onGround() && attacker.getDeltaMovement().y < -0.2) {
					// A strike from above, with all the weight of the fall behind it.
					target.hurtServer(level, AbyssWeapon.source(attacker), 7.0F);
					level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.4, 0.5, 0.4, 0.3);
				}
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
			case VOIDBLADE -> blink(server, player);
			case STARCALLER -> starfall(server, player);
			case GRAVITY_HAMMER -> singularity(server, player);
			case SKYRIDER_GLAIVE -> skyleap(server, player);
		};
		if (!used) {
			return InteractionResult.FAIL;
		}
		player.getCooldowns().addCooldown(stack, kind.cooldown);
		stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
		return InteractionResult.SUCCESS;
	}

	/** Voidblade: blink through the void to wherever you look, up to 24 blocks away. */
	private static boolean blink(ServerLevel level, Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(BLINK_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				player));
		double reach = hit.getType() == HitResult.Type.MISS ? BLINK_RANGE : eye.distanceTo(hit.getLocation()) - 0.8;
		// The furthest spot along the way where there is room to stand.
		for (double d = reach; d > 2.0; d -= 0.5) {
			Vec3 feet = eye.add(look.scale(d)).subtract(0.0, player.getEyeHeight(), 0.0);
			AABB box = player.getBoundingBox().move(feet.subtract(player.position()));
			if (level.noCollision(player, box)) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.3, 0.6, 0.3, 0.1);
				player.teleportTo(feet.x, feet.y, feet.z);
				player.resetFallDistance();
				level.sendParticles(ParticleTypes.PORTAL, feet.x, feet.y + 1.0, feet.z, 60, 0.4, 0.8, 0.4, 0.4);
				level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
				return true;
			}
		}
		return false;
	}

	/** Starcaller: a shower of stars on whatever you look at, or the ground you point to. */
	private static boolean starfall(ServerLevel level, Player player) {
		LivingEntity target = AbyssWeapon.lookedAt(level, player, 32.0);
		Vec3 at;
		if (target != null) {
			at = target.position();
		} else {
			Vec3 eye = player.getEyePosition();
			BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(32.0)), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.MISS) {
				return false;
			}
			at = hit.getLocation();
		}
		for (int i = 0; i < 5; i++) {
			double x = at.x + (i == 0 ? 0.0 : (level.getRandom().nextDouble() - 0.5) * 5.0);
			double z = at.z + (i == 0 ? 0.0 : (level.getRandom().nextDouble() - 0.5) * 5.0);
			level.sendParticles(ParticleTypes.END_ROD, x, at.y + 6.0, z, 25, 0.1, 5.0, 0.1, 0.0);
			level.sendParticles(ParticleTypes.FIREWORK, x, at.y + 0.5, z, 30, 0.5, 0.4, 0.5, 0.2);
		}
		for (LivingEntity foe : AbyssWeapon.foes(level, player, at, 4.5)) {
			foe.hurtServer(level, player.damageSources().indirectMagic(player, player), 10.0F);
			foe.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), player);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 2.0F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 2.0F, 1.0F);
		return true;
	}

	/** Gravity Hammer: everything within ten blocks is dragged in towards you, and left floating. */
	private static boolean singularity(ServerLevel level, Player player) {
		List<LivingEntity> foes = AbyssWeapon.foes(level, player, player.position(), 10.0);
		for (LivingEntity foe : foes) {
			Vec3 pull = player.position().subtract(foe.position());
			AbyssWeapon.push(foe, pull.normalize().scale(Math.min(1.8, pull.length() * 0.22)), 0.5);
			foe.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 1), player);
			foe.hurtServer(level, AbyssWeapon.source(player), 4.0F);
		}
		for (int i = 0; i < 24; i++) {
			double angle = i * Math.PI / 12.0;
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX() + Math.cos(angle) * 8.0, player.getY() + 1.0,
					player.getZ() + Math.sin(angle) * 8.0, 6, 0.2, 0.4, 0.2, 0.05);
		}
		level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 120, 1.0, 1.0, 1.0, 1.5);
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.6F);
		return true;
	}

	/** Skyrider Glaive: leap high into the air the way you look, and glide back down. */
	private static boolean skyleap(ServerLevel level, Player player) {
		Vec3 look = player.getLookAngle();
		player.setDeltaMovement(new Vec3(look.x * 1.3, Math.max(0.9, look.y * 1.5 + 0.9), look.z * 1.3));
		AbyssWeapon.sync(player);
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false, true));
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 20, 0.4, 0.1, 0.4, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.4, 0.3, 0.4, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 1.4F);
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.naturesmod." + kind.id + ".strike").withStyle(ChatFormatting.DARK_PURPLE));
		builder.accept(Component.translatable("item.naturesmod." + kind.id + ".power").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	public enum Kind {
		VOIDBLADE("voidblade", 4.0F, -2.2F, 60),
		STARCALLER("starcaller", 2.0F, -2.6F, 140),
		GRAVITY_HAMMER("gravity_hammer", 8.0F, -3.3F, 160),
		SKYRIDER_GLAIVE("skyrider_glaive", 5.0F, -2.7F, 80);

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
