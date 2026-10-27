package com.naturesmod.disaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.naturesmod.NaturesConfig;
import com.naturesmod.worldgen.wild.WildOverworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Disasters that happen of their own accord, near the players, where and when they would: tornadoes
 * out of storms over open, warm land; earthquakes anywhere, more often in the mountains (their
 * magnitudes after the Gutenberg-Richter law); meteor showers at night; eruptions near the volcanic
 * peaks; wildfires in dry, hot country when it hasn't rained; sandstorms in the deserts; blizzards in
 * the cold lands; hurricanes and tsunamis on the coasts; sinkholes under the plains and swamps;
 * avalanches on snowy slopes; lightning storms and acid rain out of a darkening sky. Each kind waits a
 * while after it has struck, and all of them a little after any one has.
 */
final class NaturalDisasters {
	/** How often each kind is looked at: every ten seconds. */
	private static final int PERIOD = 200;
	/** Natural disasters going on at once in a level at most. */
	private static final int MAX_AT_ONCE = 3;
	/** Ticks of quiet from a kind after it has struck (on top of how long it lasted): ten minutes. */
	private static final long KIND_QUIET = 12000L;
	/** Ticks of quiet from every kind after any one has struck: two minutes. */
	private static final long ALL_QUIET = 2400L;
	/** The game time before which no new natural disaster of a kind may start. */
	private static final Map<DisasterType, Long> QUIET_UNTIL = new EnumMap<>(DisasterType.class);
	private static long allQuietUntil;

	private NaturalDisasters() {
	}

	static void reset() {
		QUIET_UNTIL.clear();
		allQuietUntil = 0L;
	}

	static void tick(ServerLevel level) {
		NaturesConfig config = NaturesConfig.get();
		if (!config.naturalDisasters || config.disasterFrequency <= 0 || level.dimension() != Level.OVERWORLD || level.getGameTime() % PERIOD != 0
				|| level.players().isEmpty() || level.getGameTime() < allQuietUntil) {
			return;
		}
		long natural = Disasters.active(level).stream().filter(d -> d.natural).count();
		if (natural >= MAX_AT_ONCE) {
			return;
		}
		RandomSource random = level.getRandom();
		ServerPlayer player = level.players().get(random.nextInt(level.players().size()));
		if (player.isSpectator()) {
			return;
		}
		// Looked at in a different order each time, so that no kind crowds out the ones after it.
		List<DisasterType> kinds = new ArrayList<>(List.of(DisasterType.values()));
		Collections.shuffle(kinds, new java.util.Random(random.nextLong()));
		for (DisasterType type : kinds) {
			if (!enabled(type, config) || level.getGameTime() < QUIET_UNTIL.getOrDefault(type, 0L)) {
				continue;
			}
			double seconds = meanSeconds(type, level, player);
			if (seconds <= 0 || random.nextDouble() >= PERIOD / 20.0 / seconds * config.disasterFrequency) {
				continue;
			}
			Disaster disaster = begin(type, level, player, random);
			if (disaster != null) {
				QUIET_UNTIL.put(type, level.getGameTime() + disaster.duration() + KIND_QUIET);
				allQuietUntil = level.getGameTime() + ALL_QUIET;
				return;
			}
		}
	}

	private static boolean enabled(DisasterType type, NaturesConfig config) {
		return switch (type) {
			case TORNADO -> config.tornadoes;
			case EARTHQUAKE -> config.earthquakes;
			case METEOR_SHOWER -> config.meteorShowers;
			case ERUPTION -> config.eruptions;
			case WILDFIRE -> config.wildfires;
			case SANDSTORM -> config.sandstorms;
			case BLIZZARD -> config.blizzards;
			case HURRICANE -> config.hurricanes;
			case TSUNAMI -> config.tsunamis;
			case SINKHOLE -> config.sinkholes;
			case AVALANCHE -> config.avalanches;
			case LIGHTNING_STORM -> config.lightningStorms;
			case ACID_RAIN -> config.acidRain;
		};
	}

	/** On average how many seconds of play near this player between disasters of a kind, here and now (0 = never). */
	private static double meanSeconds(DisasterType type, ServerLevel level, ServerPlayer player) {
		BlockPos at = player.blockPosition();
		Holder<Biome> biome = level.getBiome(at);
		boolean raining = level.isRaining();
		boolean thunder = level.isThundering();
		boolean cold = biome.value().coldEnoughToSnow(at, level.getSeaLevel());
		return switch (type) {
			// A tornado brings its own supercell: in a thunderstorm they come often, in plain rain now and then, under a
			// clear sky seldom.
			case TORNADO -> !tornadoCountry(biome) ? 0 : thunder ? 150 : raining ? 600 : 2400;
			case EARTHQUAKE -> biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(WildOverworld.biome("volcanic_peaks"))
					? 1200 : 2400;
			case METEOR_SHOWER -> level.isDarkOutside() ? 1500 : 0;
			case ERUPTION -> nearVolcano(level, at) != null ? 900 : 0;
			case WILDFIRE -> !raining && level.isBrightOutside() && dryCountry(biome) ? 900 : 0;
			case SANDSTORM -> !raining && desert(biome) ? 600 : 0;
			case BLIZZARD -> !cold ? 0 : raining ? 300 : 1800;
			case HURRICANE -> coast(biome) ? raining ? 900 : 2400 : 0;
			case TSUNAMI -> coast(biome) ? 2400 : 0;
			case SINKHOLE -> biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) ? 0
					: biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(WildOverworld.biome("bayou")) ? 1200 : 2400;
			case AVALANCHE -> snowySlopes(biome) ? raining ? 400 : 900 : 0;
			case LIGHTNING_STORM -> thunder ? 300 : raining ? 1200 : 3600;
			case ACID_RAIN -> raining ? 1500 : 4800;
		};
	}

	private static Disaster begin(DisasterType type, ServerLevel level, ServerPlayer player, RandomSource random) {
		Vec3 here = player.position();
		double angle = random.nextDouble() * Math.PI * 2;
		return switch (type) {
			case TORNADO -> {
				// Somewhere out on the plains, upwind of the storm's track.
				double distance = 160 + random.nextDouble() * 160;
				Vec3 at = here.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
				Tornado tornado = (Tornado) Disasters.start(type, level, at, tornadoRating(random), true);
				tornado.aim(here.add(random.nextGaussian() * 60, 0, random.nextGaussian() * 60));
				yield tornado;
			}
			case EARTHQUAKE -> {
				double distance = 40 + random.nextDouble() * 400;
				yield Disasters.start(type, level, here.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), magnitude(random), true);
			}
			case METEOR_SHOWER -> Disasters.start(type, level, here, 2 + random.nextInt(4), true);
			case ERUPTION -> {
				BlockPos vent = nearVolcano(level, player.blockPosition());
				yield vent == null ? null : Disasters.start(type, level, Vec3.atBottomCenterOf(vent),
						3 + 4 * random.nextFloat() * random.nextFloat(), true);
			}
			case WILDFIRE -> {
				double distance = 40 + random.nextDouble() * 110;
				yield Disasters.start(type, level, here.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), 2 + random.nextInt(4), true);
			}
			case SANDSTORM, BLIZZARD, LIGHTNING_STORM -> Disasters.start(type, level, here, 3 + random.nextInt(3), true);
			case HURRICANE -> {
				// Out at sea, its track bringing its eye over the coast near the player.
				double distance = 380 + random.nextDouble() * 160;
				Hurricane hurricane = (Hurricane) Disasters.start(type, level,
						here.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), 2 + random.nextInt(4), true);
				hurricane.aim(here.add(random.nextGaussian() * 60, 0, random.nextGaussian() * 60));
				yield hurricane;
			}
			// The wave finds its own way in from the open sea toward the player's stretch of coast.
			case TSUNAMI -> Disasters.start(type, level, here, 2 + random.nextInt(4), true);
			case SINKHOLE -> {
				double distance = 8 + random.nextDouble() * 50;
				yield Disasters.start(type, level, here.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), 2 + random.nextInt(4), true);
			}
			// The avalanche finds its own slope above the player.
			case AVALANCHE -> Disasters.start(type, level, here, 2 + random.nextInt(4), true);
			case ACID_RAIN -> Disasters.start(type, level, here, 1 + random.nextInt(5), true);
		};
	}

	/** EF ratings, weighted toward the violent ones that make a tornado worth fearing. */
	private static float tornadoRating(RandomSource random) {
		double u = random.nextDouble();
		return u < 0.1 ? 2 : u < 0.4 ? 3 : u < 0.8 ? 4 : 5;
	}

	/** Gutenberg-Richter: every step of magnitude some five times rarer, from magnitude 6 up. */
	private static float magnitude(RandomSource random) {
		double m = 6.0 - Math.log10(1.0 - random.nextDouble()) / 0.7;
		return (float) Math.min(9.3, m);
	}

	private static boolean tornadoCountry(Holder<Biome> biome) {
		return biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(BiomeTags.IS_SAVANNA) || biome.is(BiomeTags.IS_FOREST)
				|| biome.is(Biomes.SWAMP) || biome.is(WildOverworld.biome("golden_prairie")) || biome.is(WildOverworld.biome("lavender_fields"))
				|| biome.is(WildOverworld.biome("bayou"));
	}

	private static boolean dryCountry(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_SAVANNA) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(WildOverworld.biome("golden_prairie"))
				|| biome.is(WildOverworld.biome("petrified_forest")) || biome.value().getBaseTemperature() >= 1.0F && biome.is(BiomeTags.IS_FOREST);
	}

	private static boolean desert(Holder<Biome> biome) {
		return biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(WildOverworld.biome("salt_flats"));
	}

	private static boolean coast(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_OCEAN) || biome.is(WildOverworld.biome("tropical_shore"))
				|| biome.is(Biomes.MANGROVE_SWAMP);
	}

	private static boolean snowySlopes(Holder<Biome> biome) {
		return biome.is(Biomes.SNOWY_SLOPES) || biome.is(Biomes.FROZEN_PEAKS) || biome.is(Biomes.JAGGED_PEAKS) || biome.is(Biomes.GROVE)
				|| biome.is(Biomes.SNOWY_TAIGA) || biome.is(WildOverworld.biome("tundra"));
	}

	/** The summit of the volcanic peaks within a few hundred blocks, if there are any. */
	static BlockPos nearVolcano(ServerLevel level, BlockPos from) {
		Holder<Biome> here = null;
		BlockPos best = null;
		int bestY = Integer.MIN_VALUE;
		for (int dx = -320; dx <= 320; dx += 32) {
			for (int dz = -320; dz <= 320; dz += 32) {
				int bx = from.getX() + dx;
				int bz = from.getZ() + dz;
				here = level.getUncachedNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(from.getY()), QuartPos.fromBlock(bz));
				if (!here.is(WildOverworld.biome("volcanic_peaks")) || !level.hasChunkAt(bx, bz)) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
				if (y > bestY) {
					bestY = y;
					best = new BlockPos(bx, y, bz);
				}
			}
		}
		return best;
	}
}
