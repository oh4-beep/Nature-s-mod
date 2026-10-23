package com.naturesmod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

/** User settings, stored in {@code config/naturesmod.json}. */
public final class NaturesConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static NaturesConfig instance = new NaturesConfig();

	// Tree felling
	/** Chopping through a natural tree's trunk makes everything above the cut topple over. */
	public boolean treeFelling = true;
	/** Only fell trees when chopping with an axe. */
	public boolean fellingRequiresAxe = true;
	/** Sneak while chopping to break a single log the vanilla way. */
	public boolean sneakToChopNormally = true;
	/** Trees also topple for players in creative mode. */
	public boolean fellInCreative = false;
	/** Toppling logs and branches hurt whatever they land on. */
	public boolean fallingTreesHurt = true;
	/** Extra axe durability used per this many logs felled (0 = no extra wear). */
	public int logsPerExtraDurability = 8;
	/** Largest tree (in logs) that can be felled in one go. */
	public int maxTreeLogs = 2500;

	// Natural tree falls
	/** A lightning strike can split a tree and knock it over. */
	public boolean lightningFellsTrees = true;
	/** During thunderstorms the wind occasionally blows over a tree near a player. */
	public boolean stormWindthrow = true;
	/** Average minutes of thunderstorm between wind-thrown trees near each player. */
	public int windthrowMinutes = 6;

	// The sea
	/** Diving deep in the Abyss without a strong enough diving suit hurts. */
	public boolean abyssPressure = true;
	/** Sharks, eels and the other hunters of the sea attack divers. */
	public boolean hostileSeaCreatures = true;
	/** How much sea life to keep around players in the water (1 = lively, 0 = vanilla spawning only). */
	public double seaLifeDensity = 1.0;
	/** The twelve guardians of the Abyss hunt divers on their way down to the bottom. */
	public boolean abyssBosses = true;
	/** Minutes before a slain guardian of the descent comes hunting the same diver again. */
	public int bossRespawnMinutes = 20;

	// Natural disasters
	/** Tornadoes, earthquakes, meteor showers, eruptions, wildfires, sandstorms and blizzards happen on their own. */
	public boolean naturalDisasters = true;
	/** How often natural disasters happen (1 = normal, 2 = twice as often, 0.5 = half as often). */
	public double disasterFrequency = 1.0;
	/** Disasters change the land: tornadoes tear up buildings, quakes open fissures, meteors leave craters, fires burn forests. */
	public boolean disasterDamage = true;
	/** Players near a disaster are warned in chat (tornado warnings, earthquake alerts, the sirens). */
	public boolean disasterWarnings = true;
	public boolean tornadoes = true;
	public boolean earthquakes = true;
	public boolean meteorShowers = true;
	public boolean eruptions = true;
	public boolean wildfires = true;
	public boolean sandstorms = true;
	public boolean blizzards = true;

	// Client effects
	/** The view shakes in earthquakes and under great blasts. */
	public boolean cameraShake = true;
	/** Multiplier for the number of particles natural disasters show (1 = full, 0.5 = half). */
	public float disasterParticles = 1.0f;
	/** Mist, spray and roar at the foot of waterfalls. */
	public boolean waterfallMist = true;
	/** Multiplier for the amount of waterfall mist particles. */
	public float mistDensity = 1.0f;
	/** Waterfalls make a rushing sound. */
	public boolean waterfallSounds = true;

	public static NaturesConfig get() {
		return instance;
	}

	public static boolean abyssPressure() {
		return instance.abyssPressure;
	}

	public static boolean hostileSeaCreatures() {
		return instance.hostileSeaCreatures;
	}

	public static double seaLifeDensity() {
		return instance.seaLifeDensity;
	}

	public static boolean abyssBosses() {
		return instance.abyssBosses && instance.hostileSeaCreatures;
	}

	public static int bossRespawnMinutes() {
		return Math.max(1, instance.bossRespawnMinutes);
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(NaturesMod.MOD_ID + ".json");
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				NaturesConfig loaded = GSON.fromJson(reader, NaturesConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | JsonParseException e) {
				NaturesMod.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		// Write back so newly added options show up in the file.
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			NaturesMod.LOGGER.warn("Could not write {}", path, e);
		}
	}
}
