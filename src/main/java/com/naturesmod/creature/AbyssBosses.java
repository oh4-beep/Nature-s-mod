package com.naturesmod.creature;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import com.naturesmod.NaturesConfig;
import com.naturesmod.abyss.Abyss;

/**
 * The guardians of the descent. The Abyss does not let divers reach its treasures easily: every
 * depth has its guardian, and as a diver sinks through it the guardian comes hunting. Slay it and
 * the way down is clear for a while; flee and it may lose you, but the next one is waiting below.
 */
public final class AbyssBosses {
	public static final String HUNTER_TAG = "naturesmod.hunter";
	/** The guardians, from the top of the water down. */
	public static final String[] GUARDIANS = {"tidewyrm", "razorjaw", "gloomlantern", "stormcoil", "kraken", "siren_queen", "bonewarden",
			"abyssal_manta", "hollow_king", "dunkleosteus", "twin_terror", "megalodon"};
	/** The guardians hunt from this height down, each through a band of the water this deep. */
	public static final int TOP = 1000;
	public static final int BAND = 70;
	/** A guardian that was escaped rather than slain comes back after this long. */
	private static final long ESCAPED_TICKS = 20L * 60 * 3;

	/** For each diver, when each guardian may next come hunting them. */
	private static final Map<UUID, long[]> NEXT = new HashMap<>();
	/** The guardian hunting each diver right now. */
	private static final Map<UUID, UUID> HUNTING = new HashMap<>();

	private AbyssBosses() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(AbyssBosses::tick);
	}

	/** Which guardian keeps watch at a height of the Abyss, or -1 above and below them all. */
	public static int band(double y) {
		if (y > TOP || y <= TOP - BAND * GUARDIANS.length) {
			return -1;
		}
		return Math.min(GUARDIANS.length - 1, (int) ((TOP - y) / BAND));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0 || !NaturesConfig.abyssBosses()) {
			return;
		}
		ServerLevel level = server.getLevel(Abyss.LEVEL);
		if (level == null || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || !player.isAlive() || !player.isInWater()) {
				continue;
			}
			int band = band(player.getY());
			if (band < 0) {
				continue;
			}
			UUID current = HUNTING.get(player.getUUID());
			if (current != null) {
				Entity hunter = level.getEntity(current);
				if (hunter != null && hunter.isAlive() && hunter.distanceToSqr(player) < 128.0 * 128.0) {
					continue;
				}
				HUNTING.remove(player.getUUID());
			}
			long[] next = NEXT.computeIfAbsent(player.getUUID(), id -> new long[GUARDIANS.length]);
			if (now < next[band]) {
				continue;
			}
			Mob guardian = summon(level, player, GUARDIANS[band]);
			if (guardian != null) {
				HUNTING.put(player.getUUID(), guardian.getUUID());
				next[band] = now + ESCAPED_TICKS;
			}
		}
	}

	/** Brings a guardian out of the dark some way off from the diver, already hunting them. */
	public static Mob summon(ServerLevel level, ServerPlayer player, String id) {
		EntityType<? extends Mob> type = ModCreatures.type(id);
		if (type == null) {
			return null;
		}
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 22.0 + random.nextDouble() * 10.0;
			double x = player.getX() + Math.cos(angle) * distance;
			double y = player.getY() + random.nextDouble() * 10.0 - 5.0;
			double z = player.getZ() + Math.sin(angle) * distance;
			BlockPos pos = BlockPos.containing(x, y, z);
			if (!level.isLoaded(pos) || !level.getFluidState(pos).is(FluidTags.WATER)) {
				continue;
			}
			Mob guardian = type.create(level, EntitySpawnReason.TRIGGERED);
			if (guardian == null) {
				return null;
			}
			guardian.snapTo(x, y, z, random.nextFloat() * 360.0F, 0.0F);
			if (!level.noCollision(guardian)) {
				continue;
			}
			guardian.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.TRIGGERED, null);
			if (guardian instanceof LargeSwimmer swimmer) {
				swimmer.markHunter();
			}
			guardian.setTarget(player);
			level.addFreshEntity(guardian);
			level.playSound(null, x, y, z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 4.0F, 0.4F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.7F);
			player.sendSystemMessage(Component.translatable("message.naturesmod.guardian.hunting", guardian.getDisplayName())
					.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
			return guardian;
		}
		return null;
	}

	/** A guardian has fallen: it leaves the divers who fought it alone for a long while. */
	static void defeated(ServerLevel level, LargeSwimmer guardian) {
		int band = -1;
		for (int i = 0; i < GUARDIANS.length; i++) {
			if (GUARDIANS[i].equals(guardian.species().id())) {
				band = i;
			}
		}
		long until = level.getGameTime() + 20L * 60 * NaturesConfig.bossRespawnMinutes();
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, guardian.getBoundingBox().inflate(96.0))) {
			if (band >= 0) {
				long[] next = NEXT.computeIfAbsent(player.getUUID(), id -> new long[GUARDIANS.length]);
				next[band] = Math.max(next[band], until);
			}
			HUNTING.remove(player.getUUID(), guardian.getUUID());
			player.sendSystemMessage(Component.translatable("message.naturesmod.guardian.slain", guardian.getDisplayName())
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
		}
	}
}
