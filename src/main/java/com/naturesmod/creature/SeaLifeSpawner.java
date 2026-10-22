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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

import com.naturesmod.NaturesConfig;
import com.naturesmod.abyss.Abyss;

/**
 * Keeps the seas alive. Vanilla's mob caps leave room for only a handful of water animals, and
 * the cod, salmon and squid take most of it, so on its own the ocean feels empty. Every two
 * seconds this tops up the sea life around each player who is in or near deep water, picking
 * species that live in the biome and at the depth of the spot. The creatures despawn like any
 * other once the player swims away.
 */
public final class SeaLifeSpawner {
	private static final int INTERVAL = 40;
	/** Sea creatures kept within reach of a player in the Overworld's seas and in the Abyss. */
	private static final int OVERWORLD_TARGET = 16;
	private static final int ABYSS_TARGET = 40;
	private static final double REACH = 48.0;

	private static Map<Identifier, List<SpeciesData>> byBiome;

	private SeaLifeSpawner() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(SeaLifeSpawner::tick);
	}

	private static void tick(ServerLevel level) {
		if (level.getGameTime() % INTERVAL != 0) {
			return;
		}
		double density = NaturesConfig.seaLifeDensity();
		boolean abyss = Abyss.isAbyss(level);
		if (density <= 0.0 || !abyss && level.dimension() != Level.OVERWORLD || !level.getGameRules().get(GameRules.SPAWN_MOBS)) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && (abyss || nearDeepWater(level, player))) {
				topUp(level, player, abyss, density);
			}
		}
	}

	/** Swimming, or standing or sailing right over water at least a few blocks deep. */
	private static boolean nearDeepWater(ServerLevel level, ServerPlayer player) {
		if (player.isInWater()) {
			return true;
		}
		BlockPos feet = player.blockPosition();
		for (int down = 1; down <= 4; down++) {
			if (level.getFluidState(feet.below(down)).is(FluidTags.WATER) && level.getFluidState(feet.below(down + 2)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	private static void topUp(ServerLevel level, ServerPlayer player, boolean abyss, double density) {
		int target = (int) Math.round((abyss ? ABYSS_TARGET : OVERWORLD_TARGET) * density);
		AABB around = player.getBoundingBox().inflate(REACH, abyss ? 40.0 : 24.0, REACH);
		int present = level.getEntitiesOfClass(Mob.class, around, mob -> mob instanceof SeaCreature).size();
		RandomSource random = level.getRandom();
		int groups = 0;
		int maxGroups = abyss ? 3 : 2;
		for (int attempt = 0; attempt < (abyss ? 12 : 8) && groups < maxGroups && present < target; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 12.0 + random.nextDouble() * 30.0;
			int spread = abyss ? 24 : 10;
			int y = Mth.clamp(Mth.floor(player.getY()) + random.nextInt(spread * 2 + 1) - spread, level.getMinY() + 2, level.getSeaLevel() - 2);
			BlockPos pos = BlockPos.containing(player.getX() + Math.cos(angle) * distance, y, player.getZ() + Math.sin(angle) * distance);
			if (!level.isLoaded(pos) || !level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos.below()).is(FluidTags.WATER)
					&& !level.getFluidState(pos.above()).is(FluidTags.WATER)) {
				continue;
			}
			SpeciesData species = pick(level, pos, random);
			if (species == null) {
				continue;
			}
			int count = species.groupMin() + random.nextInt(species.groupMax() - species.groupMin() + 1);
			count = Math.max(1, Math.min(count, target - present + 2));
			int spawned = spawnGroup(level, species, pos, count, random);
			if (spawned > 0) {
				present += spawned;
				groups++;
			}
		}
	}

	/** A species that lives in the biome at this spot and keeps to this depth, picked by weight. */
	private static SpeciesData pick(ServerLevel level, BlockPos pos, RandomSource random) {
		Holder<Biome> biome = level.getBiome(pos);
		List<SpeciesData> living = biome.unwrapKey().map(key -> index().get(key.identifier())).orElse(null);
		if (living == null || living.isEmpty()) {
			return null;
		}
		List<SpeciesData> fits = new ArrayList<>();
		int total = 0;
		for (SpeciesData species : living) {
			if (ModCreatures.canSpawn(species, level, pos, random)) {
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
			BlockPos pos = i == 0 ? center : center.offset(random.nextInt(5) - 2, random.nextInt(3) - 1, random.nextInt(5) - 2);
			if (!level.getFluidState(pos).is(FluidTags.WATER)) {
				continue;
			}
			Mob mob = type.create(level, EntitySpawnReason.NATURAL);
			if (mob == null) {
				continue;
			}
			mob.snapTo(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
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

	/** Which species live in which biome, built from the species table on first use. */
	private static Map<Identifier, List<SpeciesData>> index() {
		if (byBiome == null) {
			Map<Identifier, List<SpeciesData>> map = new HashMap<>();
			for (SpeciesData species : CreatureSpecies.ALL) {
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
