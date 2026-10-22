package com.naturesmod.end;

import net.fabricmc.fabric.api.biome.v1.TheEndBiomes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import com.naturesmod.NaturesMod;
import com.naturesmod.block.ModBlocks;

/**
 * The new End's biomes and its updrafts. The End's terrain itself (continents beyond the void
 * ring, sky isles above them), its surface and its features are data: see tools/worldgen/end.py.
 *
 * <p>The End's biome source keeps the dragon's island for itself and, further out, sorts the land
 * by the noise router's "erosion" into highlands, midlands, barrens and small islands; the new
 * terrain sets that to highlands on the continents and small islands up in the sky, and Fabric's
 * End biome API shares those out between the new biomes.
 */
public final class EndOverhaul {
	/** The continental biomes and their weights; vanilla's End Highlands keep a weight of 1. */
	private static final String[] CONTINENTAL = {"chorus_forest", "crystal_highlands", "starfall_plains", "void_marsh", "shattered_spires",
			"enderglow_wilds"};
	private static final double[] WEIGHTS = {1.3, 1.0, 1.0, 0.9, 0.9, 1.1};
	/** How far above its vent an updraft still lifts. */
	private static final int UPDRAFT_HEIGHT = 100;

	private EndOverhaul() {
	}

	public static void register() {
		for (int i = 0; i < CONTINENTAL.length; i++) {
			ResourceKey<Biome> biome = biome(CONTINENTAL[i]);
			TheEndBiomes.addHighlandsBiome(biome, WEIGHTS[i]);
			// The edges of a continent belong to the same biome as its heart.
			TheEndBiomes.addMidlandsBiome(biome, biome, 1.0);
			TheEndBiomes.addBarrensBiome(biome, biome, 1.0);
		}
		TheEndBiomes.addSmallIslandsBiome(biome("sky_isles"), 9.0);
		ServerTickEvents.END_SERVER_TICK.register(EndOverhaul::tick);
	}

	public static ResourceKey<Biome> biome(String name) {
		return ResourceKey.create(Registries.BIOME, NaturesMod.id(name));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 5 != 0) {
			return;
		}
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) {
			return;
		}
		for (ServerPlayer player : end.players()) {
			if (player.isSpectator() || player.getAbilities().flying || player.isPassenger()) {
				continue;
			}
			if (!player.isShiftKeyDown() && overVent(end, player.blockPosition())) {
				// Up on the wind, and a gentle glide down once out of it.
				player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 12, 9, true, false));
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, true, false));
			}
		}
	}

	/** Whether a position is in the open column of air over an updraft vent, within its reach. */
	public static boolean overVent(ServerLevel level, BlockPos feet) {
		BlockPos.MutableBlockPos pos = feet.mutable();
		for (int i = 0; i <= UPDRAFT_HEIGHT && pos.getY() > level.getMinY(); i++) {
			BlockState state = level.getBlockState(pos);
			if (state.is(ModBlocks.UPDRAFT_VENT)) {
				return true;
			}
			if (!state.getCollisionShape(level, pos).isEmpty()) {
				return false;
			}
			pos.move(Direction.DOWN);
		}
		return false;
	}
}
