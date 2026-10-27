package com.naturesmod.disaster;

import com.naturesmod.NaturesMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** The sounds of the disasters, synthesized by tools/assets/sounds.py. */
public final class DisasterSounds {
	public static final SoundEvent TORNADO_ROAR = register("disaster.tornado.roar");
	public static final SoundEvent SIREN = register("disaster.tornado.siren");
	public static final SoundEvent WIND_HOWL = register("disaster.wind.howl");
	public static final SoundEvent BLIZZARD_WIND = register("disaster.blizzard.wind");
	public static final SoundEvent SANDSTORM_WIND = register("disaster.sandstorm.wind");
	public static final SoundEvent QUAKE_RUMBLE = register("disaster.quake.rumble");
	public static final SoundEvent QUAKE_JOLT = register("disaster.quake.jolt");
	public static final SoundEvent ERUPTION_BLAST = register("disaster.eruption.blast");
	public static final SoundEvent ERUPTION_ROAR = register("disaster.eruption.roar");
	public static final SoundEvent METEOR_WHOOSH = register("disaster.meteor.whoosh");
	public static final SoundEvent METEOR_IMPACT = register("disaster.meteor.impact");
	public static final SoundEvent METEOR_BOOM = register("disaster.meteor.boom");
	public static final SoundEvent FIRE_ROAR = register("disaster.fire.roar");
	public static final SoundEvent HAIL = register("disaster.hail");
	public static final SoundEvent HURRICANE_WIND = register("disaster.hurricane.wind");
	public static final SoundEvent TSUNAMI_ROAR = register("disaster.tsunami.roar");
	public static final SoundEvent AVALANCHE_ROAR = register("disaster.avalanche.roar");
	public static final SoundEvent SINKHOLE_COLLAPSE = register("disaster.sinkhole.collapse");
	public static final SoundEvent ACID_RAIN_HISS = register("disaster.acid_rain.hiss");

	private DisasterSounds() {
	}

	public static void register() {
	}

	private static SoundEvent register(String name) {
		Identifier id = NaturesMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
