package com.naturesmod.client.disaster;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The disasters' sounds on this client: loops that keep going while something keeps asking for them
 * (a tornado's roar, the wind of a blizzard) and fade away once nothing does, sounds that follow
 * something moving (a fireball overhead), and sounds from far off, which arrive as late as sound
 * travels (some 17 blocks a tick): the flash of a blast first, its boom seconds after.
 */
final class DisasterAudio {
	private static final Map<String, Loop> LOOPS = new HashMap<>();

	private DisasterAudio() {
	}

	/** Keeps a loop going this tick, at a volume and (if positional) a place. */
	static void loop(String key, SoundEvent event, boolean positional, float volume, double x, double y, double z) {
		Loop loop = LOOPS.get(key);
		if (loop == null || loop.isStopped()) {
			if (volume < 0.01F) {
				return;
			}
			loop = new Loop(event, positional);
			LOOPS.put(key, loop);
			Minecraft.getInstance().getSoundManager().play(loop);
		}
		loop.keep(volume, x, y, z);
	}

	static void tick() {
		LOOPS.values().removeIf(AbstractTickableSoundInstance::isStopped);
	}

	static void stopAll() {
		for (Loop loop : LOOPS.values()) {
			Minecraft.getInstance().getSoundManager().stop(loop);
		}
		LOOPS.clear();
	}

	/** A sound from (x, y, z), heard once its sound has had time to get here. */
	static void distant(SoundEvent event, double x, double y, double z, float volume, float pitch) {
		double distance = Fx.distance(x, y, z);
		int delay = (int) (distance / 17.15);
		Minecraft.getInstance().getSoundManager().playDelayed(new OneShot(event, x, y, z, volume, pitch, false), delay);
	}

	/** A sound heard as if all around (a siren from the towns, a rumble from below). */
	static void around(SoundEvent event, float volume, float pitch) {
		Minecraft.getInstance().getSoundManager().play(new OneShot(event, 0.0, 0.0, 0.0, volume, pitch, true));
	}

	/** A sound that follows something about until it stops. */
	static void follow(SoundEvent event, Supplier<Vec3> where, float volume, float pitch) {
		Minecraft.getInstance().getSoundManager().play(new Following(event, where, volume, pitch));
	}

	private static final class Loop extends AbstractTickableSoundInstance {
		private float target;
		private double nextX;
		private double nextY;
		private double nextZ;
		private int unasked;

		Loop(SoundEvent event, boolean positional) {
			super(event, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01F;
			if (!positional) {
				this.relative = true;
				this.attenuation = SoundInstance.Attenuation.NONE;
			}
		}

		void keep(float volume, double x, double y, double z) {
			this.target = Math.max(0.0F, Math.min(1.0F, volume));
			this.nextX = x;
			this.nextY = y;
			this.nextZ = z;
			this.unasked = 0;
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			if (++unasked > 10) {
				target = 0.0F;
			}
			volume += (target - volume) * 0.08F;
			if (!relative) {
				x = nextX;
				y = nextY;
				z = nextZ;
			}
			if (unasked > 10 && volume < 0.01F) {
				stop();
			}
		}
	}

	private static final class OneShot extends AbstractSoundInstance {
		OneShot(SoundEvent event, double x, double y, double z, float volume, float pitch, boolean around) {
			super(event, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
			this.x = x;
			this.y = y;
			this.z = z;
			this.volume = volume;
			this.pitch = pitch;
			if (around) {
				this.relative = true;
				this.attenuation = SoundInstance.Attenuation.NONE;
			}
		}
	}

	private static final class Following extends AbstractTickableSoundInstance {
		private final Supplier<Vec3> where;

		Following(SoundEvent event, Supplier<Vec3> where, float volume, float pitch) {
			super(event, SoundSource.WEATHER, SoundInstance.createUnseededRandom());
			this.where = where;
			this.volume = volume;
			this.pitch = pitch;
			Vec3 at = where.get();
			if (at != null) {
				this.x = at.x;
				this.y = at.y;
				this.z = at.z;
			}
		}

		@Override
		public void tick() {
			Vec3 at = where.get();
			if (at == null) {
				return;
			}
			x = at.x;
			y = at.y;
			z = at.z;
		}
	}
}
