package com.naturesmod.nether;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import com.naturesmod.NaturesMod;

/**
 * The Nether overhaul's biomes and atmosphere. The Nether's terrain itself (twice as tall, with
 * vast caverns, floating islands and colossal pillars above the classic Nether), the reworked
 * vanilla biomes and the strongholds of Hell are data: see tools/worldgen/nether.py.
 */
public final class NetherOverhaul {
	private NetherOverhaul() {
	}

	public static void register() {
		// The new biomes are listed in the Nether's dimension file (data/minecraft/dimension/the_nether.json),
		// not added to the vanilla preset: Lunar Client rebuilds that preset from vanilla data and crashes on them.
		ServerTickEvents.END_SERVER_TICK.register(NetherOverhaul::tick);
	}

	private static ResourceKey<Biome> biome(String name) {
		return ResourceKey.create(Registries.BIOME, NaturesMod.id(name));
	}

	/** Now and then something screams, far off in the dark. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether == null) {
			return;
		}
		RandomSource random = nether.getRandom();
		for (ServerPlayer player : nether.players()) {
			if (random.nextInt(45) != 0) {
				continue;
			}
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 16.0 + random.nextDouble() * 20.0;
			double x = player.getX() + Math.cos(angle) * distance;
			double y = player.getY() + random.nextDouble() * 12.0 - 4.0;
			double z = player.getZ() + Math.sin(angle) * distance;
			switch (random.nextInt(4)) {
				case 0 -> nether.playSound(null, x, y, z, SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 1.2F, 0.45F + random.nextFloat() * 0.2F);
				case 1 -> nether.playSound(null, x, y, z, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.8F, 0.35F);
				case 2 -> nether.playSound(null, x, y, z, SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.4F);
				default -> nether.playSound(null, x, y, z, SoundEvents.GHAST_WARN, SoundSource.HOSTILE, 1.0F, 0.5F);
			}
		}
	}
}
