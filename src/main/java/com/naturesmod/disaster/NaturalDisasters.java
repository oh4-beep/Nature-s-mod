package com.naturesmod.disaster;

import java.util.EnumMap;
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
 * out of thunderstorms over open, warm land; earthquakes anywhere, mostly small, more often in the
 * mountains (their magnitudes after the Gutenberg-Richter law); meteor showers at night; eruptions
 * near the volcanic peaks; wildfires in dry, hot country when it hasn't rained; sandstorms in the
 * deserts; blizzards when it snows hard in the cold lands.
 */
final class NaturalDisasters {
	/** How often each kind is looked at: every ten seconds. */
	private static final int PERIOD = 200;
	/** Natural disasters going on at once in a level at most. */
	private static final int MAX_AT_ONCE = 2;
	/** The game time before which no new natural disaster of a kind may start. */
	private static final Map<DisasterType, Long> QUIET_UNTIL = new EnumMap<>(DisasterType.class);

	private NaturalDisasters() {
	}

	static void reset() {
		QUIET_UNTIL.clear();
	}

	static void tick(ServerLevel level) {
		NaturesConfig config = NaturesConfig.get();
		if (!config.naturalDisasters || config.disasterFrequency <= 0 || level.dimension() != Level.OVERWORLD || level.getGameTime() % PERIOD != 0
				|| level.players().isEmpty()) {
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
		for (DisasterType type : DisasterType.values()) {
			if (!enabled(type, config) || level.getGameTime() < QUIET_UNTIL.getOrDefault(type, 0L)) {
				continue;
			}
			double seconds = meanSeconds(type, level, player);
			if (seconds <= 0 || random.nextDouble() >= PERIOD / 20.0 / seconds * config.disasterFrequency) {
				continue;
			}
			Disaster disaster = begin(type, level, player, random);
			if (disaster != null) {
				// Half an hour of quiet from this kind afterwards.
				QUIET_UNTIL.put(type, level.getGameTime() + disaster.duration() + 36000L);
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
		};
	}

	/** On average how many seconds of play near this player between disasters of a kind, here and now (0 = never). */
	private static double meanSeconds(DisasterType type, ServerLevel level, ServerPlayer player) {
		BlockPos at = player.blockPosition();
		Holder<Biome> biome = level.getBiome(at);
		boolean raining = level.isRaining();
		return switch (type) {
			case TORNADO -> level.isThundering() && tornadoCountry(biome) ? 420 : 0;
			case EARTHQUAKE -> biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(WildOverworld.biome("volcanic_peaks"))
					? 4500 : 9000;
			case METEOR_SHOWER -> level.isDarkOutside() ? 9000 : 0;
			case ERUPTION -> nearVolcano(level, at) != null ? 5400 : 0;
			case WILDFIRE -> !raining && level.isBrightOutside() && dryCountry(biome) ? 3600 : 0;
			case SANDSTORM -> !raining && desert(biome) ? 2400 : 0;
			case BLIZZARD -> raining && biome.value().coldEnoughToSnow(at, level.getSeaLevel()) ? 900 : 0;
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
				tornado.aim(here.add(random.nextGaussian() * 80, 0, random.nextGaussian() * 80));
				yield tornado;
			}
			case EARTHQUAKE -> {
				double distance = 40 + random.nextDouble() * 500;
				yield Disasters.start(type, level, here.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), magnitude(random), true);
			}
			case METEOR_SHOWER -> Disasters.start(type, level, here, 1 + random.nextInt(5), true);
			case ERUPTION -> {
				BlockPos vent = nearVolcano(level, player.blockPosition());
				yield vent == null ? null : Disasters.start(type, level, Vec3.atBottomCenterOf(vent),
						2 + 5 * random.nextFloat() * random.nextFloat(), true);
			}
			case WILDFIRE -> {
				double distance = 40 + random.nextDouble() * 110;
				yield Disasters.start(type, level, here.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), 1 + random.nextInt(5), true);
			}
			case SANDSTORM, BLIZZARD -> Disasters.start(type, level, here, 1 + random.nextInt(5), true);
		};
	}

	/** EF ratings as they come in nature: most tornadoes are weak, very few violent. */
	private static float tornadoRating(RandomSource random) {
		double u = random.nextDouble();
		return u < 0.38 ? 0 : u < 0.68 ? 1 : u < 0.86 ? 2 : u < 0.955 ? 3 : u < 0.992 ? 4 : 5;
	}

	/** Gutenberg-Richter: every step of magnitude ten times rarer (a little less, so the big ones aren't unheard of). */
	private static float magnitude(RandomSource random) {
		double m = 4.0 - Math.log10(1.0 - random.nextDouble()) / 0.75;
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
