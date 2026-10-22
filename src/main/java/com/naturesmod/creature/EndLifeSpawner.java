package com.naturesmod.creature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import com.naturesmod.NaturesConfig;

/**
 * Brings the new End to life: every two seconds it tops up the creatures around each player out
 * on the End's continents (the dragon's island is left as it was), picking species that live in
 * the biome at the spot: flyers in the air over the land and the void, walkers on the ground.
 * They despawn like any other creature once the player moves on.
 */
public final class EndLifeSpawner {
	private static final int INTERVAL = 40;
	private static final int TARGET = 16;
	private static final double REACH = 48.0;
	/** The dragon's island and the void ring around it keep to themselves. */
	private static final double RING = 900.0;

	private static Map<Identifier, List<SpeciesData>> byBiome;

	private EndLifeSpawner() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(EndLifeSpawner::tick);
	}

	private static void tick(ServerLevel level) {
		if (level.dimension() != Level.END || level.getGameTime() % INTERVAL != 0) {
			return;
		}
		double density = NaturesConfig.seaLifeDensity();
		if (density <= 0.0 || !level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS)) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && player.getX() * player.getX() + player.getZ() * player.getZ() > RING * RING) {
				topUp(level, player, (int) Math.round(TARGET * density));
			}
		}
	}

	private static void topUp(ServerLevel level, ServerPlayer player, int target) {
		AABB around = player.getBoundingBox().inflate(REACH, 32.0, REACH);
		int present = level.getEntitiesOfClass(Mob.class, around, mob -> mob instanceof SeaCreature creature && !creature.species().inWater()).size();
		RandomSource random = level.getRandom();
		int groups = 0;
		for (int attempt = 0; attempt < 10 && groups < 2 && present < target; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 14.0 + random.nextDouble() * 30.0;
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			if (!level.isLoaded(new BlockPos(x, 64, z))) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			boolean land = ground > level.getMinY() + 1;
			int airY = land ? ground + 3 + random.nextInt(14) : Mth.floor(player.getY()) + random.nextInt(17) - 6;
			airY = Mth.clamp(airY, level.getMinY() + 24, level.getMaxY() - 8);
			SpeciesData species = pick(level, new BlockPos(x, land ? ground : airY, z), land, random);
			if (species == null) {
				continue;
			}
			BlockPos at = species.walks() ? new BlockPos(x, ground, z) : new BlockPos(x, airY, z);
			if (!level.isEmptyBlock(at) || !level.isEmptyBlock(at.above())) {
				continue;
			}
			int count = species.groupMin() + random.nextInt(species.groupMax() - species.groupMin() + 1);
			int spawned = spawnGroup(level, species, at, Math.max(1, Math.min(count, target - present + 2)), random);
			if (spawned > 0) {
				present += spawned;
				groups++;
			}
		}
	}

	/** A species that lives in the biome at this spot, picked by weight; walkers only where there is ground. */
	private static SpeciesData pick(ServerLevel level, BlockPos pos, boolean land, RandomSource random) {
		Holder<Biome> biome = level.getBiome(pos);
		List<SpeciesData> living = biome.unwrapKey().map(key -> index().get(key.identifier())).orElse(null);
		if (living == null || living.isEmpty()) {
			return null;
		}
		List<SpeciesData> fits = new ArrayList<>();
		int total = 0;
		for (SpeciesData species : living) {
			if (land || species.flies()) {
				fits.add(species);
				total += species.weight();
			}
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (SpeciesData species : fits) {
			roll -= species.weight();
			if (roll < 0) {
				return species;
			}
		}
		return fits.getLast();
	}

	private static int spawnGroup(ServerLevel level, SpeciesData species, BlockPos center, int count, RandomSource random) {
		EntityType<? extends Mob> type = ModCreatures.type(species.id());
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			BlockPos pos = i == 0 ? center : center.offset(random.nextInt(7) - 3, species.flies() ? random.nextInt(5) - 2 : 0, random.nextInt(7) - 3);
			if (species.walks()) {
				pos = new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()), pos.getZ());
				if (pos.getY() <= level.getMinY() + 1) {
					continue;
				}
			}
			if (!level.isEmptyBlock(pos)) {
				continue;
			}
			Mob mob = type.create(level, EntitySpawnReason.NATURAL);
			if (mob == null) {
				continue;
			}
			mob.snapTo(pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (!level.noCollision(mob)) {
				mob.discard();
				continue;
			}
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
			level.addFreshEntity(mob);
			spawned++;
		}
		return spawned;
	}

	/** Which End creatures live in which biome, built from the species table on first use. */
	private static Map<Identifier, List<SpeciesData>> index() {
		if (byBiome == null) {
			Map<Identifier, List<SpeciesData>> map = new HashMap<>();
			for (SpeciesData species : CreatureSpecies.ALL) {
				if (species.inWater()) {
					continue;
				}
				for (String habitat : species.habitats()) {
					int colon = habitat.indexOf(':');
					Identifier id = colon < 0 ? Identifier.withDefaultNamespace(habitat)
							: Identifier.fromNamespaceAndPath(habitat.substring(0, colon), habitat.substring(colon + 1));
					map.computeIfAbsent(id, key -> new ArrayList<>()).add(species);
				}
			}
			byBiome = map;
		}
		return byBiome;
	}
}
