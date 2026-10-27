package com.naturesmod.client.disaster;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.naturesmod.disaster.DisasterNet;
import com.naturesmod.disaster.DisasterSounds;
import com.naturesmod.disaster.Eruption;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The moments of the disasters, drawn here: fireballs crossing the sky trailing fire and smoke, the
 * flash of an airburst and an impact's fireball and dust cloud, the blast of a volcano, bombs arcing
 * out of its vent, the sirens. What is heard of them comes when sound would (see
 * {@link DisasterAudio#distant}), and so does the jolt of the shock wave.
 */
final class DisasterEvents {
	private static final List<Mover> MOVERS = new ArrayList<>();
	private static final List<Delayed> DELAYED = new ArrayList<>();

	private DisasterEvents() {
	}

	/** Something moving across the scene for a while: a fireball, a bomb, a shooting star. */
	private abstract static class Mover {
		Vec3 pos;
		Vec3 velocity;
		int age;

		Mover(Vec3 pos, Vec3 velocity) {
			this.pos = pos;
			this.velocity = velocity;
		}

		/** Moves on a tick; false once it's done. */
		abstract boolean tick();
	}

	private record Delayed(int[] left, Runnable action) {
	}

	static void handle(DisasterNet.Event event) {
		Minecraft mc = Minecraft.getInstance();
		if (!Fx.ready() || mc.level == null || mc.player == null) {
			return;
		}
		Fx.level = mc.level;
		Fx.eye = mc.player.getEyePosition();
		Vec3 at = new Vec3(event.x(), event.y(), event.z());
		switch (event.kind()) {
			case DisasterNet.Event.METEOR -> moving(new Fireball(at, new Vec3(event.data(0), event.data(1), event.data(2)), event.data(3),
					(int) event.data(4), (int) event.data(5)));
			case DisasterNet.Event.IMPACT -> impact(at, event.data(0));
			case DisasterNet.Event.AIRBURST -> airburst(at, event.data(0));
			case DisasterNet.Event.BLAST -> blast(at, event.data(0));
			case DisasterNet.Event.BOMB -> moving(new Bomb(at, new Vec3(event.data(0), event.data(1), event.data(2)), event.data(3)));
			case DisasterNet.Event.BOMB_LANDS -> bombLands(at, event.data(0));
			case DisasterNet.Event.SIREN -> DisasterAudio.around(DisasterSounds.SIREN, 0.6F, 1.0F);
			default -> {
			}
		}
	}

	/** No more than this many fireballs, bombs and shooting stars in flight at once: the oldest are let go first. */
	private static final int MAX_MOVERS = 48;

	private static void moving(Mover mover) {
		while (MOVERS.size() >= MAX_MOVERS) {
			MOVERS.remove(0);
		}
		MOVERS.add(mover);
	}

	static void tick() {
		MOVERS.removeIf(mover -> !mover.tick());
		for (Iterator<Delayed> it = DELAYED.iterator(); it.hasNext(); ) {
			Delayed delayed = it.next();
			if (--delayed.left()[0] <= 0) {
				it.remove();
				delayed.action().run();
			}
		}
	}

	static void clear() {
		MOVERS.clear();
		DELAYED.clear();
	}

	/**
	 * How many of `count` particles to draw: the full number at full density, fewer as the player's settings or a slow
	 * computer thin the disasters out (see {@link Fx#density}). Meteors now strike often, and each strike is a burst.
	 */
	private static int scaled(int count) {
		return Fx.round(count * Math.max(0.15, Fx.density));
	}

	private static void later(int ticks, Runnable action) {
		DELAYED.add(new Delayed(new int[] {ticks}, action));
	}

	/** A shooting star: a bright head and a glowing trail behind it, gone in a moment. */
	static void streak(Vec3 start, Vec3 velocity, int ticks, float size, boolean green) {
		moving(new Mover(start, velocity) {
			@Override
			boolean tick() {
				pos = pos.add(velocity);
				double distance = Fx.distance(pos.x, pos.y, pos.z);
				// Brightest in the middle of its flight.
				float bright = (float) Math.sin(Math.PI * (age + 0.5) / ticks);
				float r = green ? 0.7F : 1.0F;
				float g = green ? 1.0F : 0.97F;
				float b = green ? 0.75F : 0.85F;
				Vec3 mid = pos.subtract(velocity.scale(1.5));
				Fx.streak(mid.x, mid.y, mid.z).along(velocity).size((float) (velocity.length() * 1.5)).color(r, g, b).alpha(0.9F * bright).life(2)
						.fade(0.0F, 0.6F).spawn();
				Fx.spark(pos.x, pos.y, pos.z).size(Fx.apparent(size, distance, 0.0035)).color(r, g, b).glow().alpha(bright).life(2).fade(0.0F, 0.7F)
						.spawn();
				return ++age < ticks;
			}
		});
	}

	// --- fireballs ---------------------------------------------------------------------------

	private static final class Fireball extends Mover {
		private final float size;
		private final int fate;
		private final int flight;
		private boolean heard;

		Fireball(Vec3 pos, Vec3 velocity, float size, int fate, int flight) {
			super(pos, velocity);
			this.size = size;
			this.fate = fate;
			this.flight = flight;
		}

		@Override
		boolean tick() {
			pos = pos.add(velocity);
			age++;
			double distance = Fx.distance(pos.x, pos.y, pos.z);
			float glow = Fx.apparent(1.4F + size * 2.0F, distance, 0.012 + size * 0.004);
			// The head, white-hot, and the fire streaming off it.
			Fx.spark(pos.x, pos.y, pos.z).size(glow).color(1.0F, 0.95F, 0.82F).glow().alpha(1.0F).life(3).fade(0.0F, 0.5F).spawn();
			Vec3 mid = pos.subtract(velocity.scale(2.5));
			Fx.streak(mid.x, mid.y, mid.z).along(velocity).size((float) (velocity.length() * 2.5)).color(1.0F, 0.75F, 0.4F).alpha(0.95F).life(2)
					.fade(0.0F, 0.5F).spawn();
			Fx.streak(mid.x, mid.y, mid.z).along(velocity).size((float) (velocity.length() * 2.2)).color(1.0F, 0.98F, 0.9F).alpha(1.0F).life(2)
					.fade(0.0F, 0.5F).spawn();
			int trail = distance < 900 ? Fx.round(2 * Math.max(0.3, Fx.density)) : Fx.round(Math.max(0.3, Fx.density));
			for (int i = 0; i < trail; i++) {
				Vec3 back = pos.subtract(velocity.scale(Fx.random.nextDouble()));
				Fx.cloud(back.x, back.y, back.z).size(glow * 0.9F).color(1.0F, 0.55F, 0.18F).glow().alpha(0.9F).life(8 + Fx.random.nextInt(6))
						.grow(0.6F).fade(0.0F, 0.7F).spawn();
				// The smoke trail lingers long after it has gone.
				Fx.cloud(back.x, back.y, back.z).size(1.2F + size).gray((float) Fx.range(0.45, 0.6)).alpha(0.55F).life(140 + Fx.random.nextInt(100))
						.grow(2.2F).fade(0.05F, 0.6F).velocity(Fx.range(-0.02, 0.02), Fx.range(-0.01, 0.01), Fx.range(-0.02, 0.02)).spawn();
			}
			if (Fx.random.nextInt(3) == 0) {
				// Fragments breaking off.
				Fx.spark(pos.x, pos.y, pos.z).velocity(velocity.x * 0.6 + Fx.range(-0.3, 0.3), velocity.y * 0.6 + Fx.range(-0.3, 0.3),
						velocity.z * 0.6 + Fx.range(-0.3, 0.3)).fall(0.03).size(0.3F).color(1.0F, 0.7F, 0.3F).glow().alpha(1.0F).life(20).spawn();
			}
			if (!heard && distance < 260.0) {
				heard = true;
				DisasterAudio.follow(DisasterSounds.METEOR_WHOOSH, () -> pos, Math.min(1.0F, 0.5F + size * 0.25F), (float) Fx.range(0.85, 1.1));
			}
			if (age >= flight) {
				if (fate == 0) {
					burst(pos, 3.0F + size * 3.0F, 6);
				}
				return false;
			}
			return true;
		}
	}

	/** A flash and a puff of fire and smoke. */
	private static void burst(Vec3 at, float size, int sparks) {
		Fx.spark(at.x, at.y, at.z).size(size).color(1.0F, 0.98F, 0.9F).glow().alpha(1.0F).life(5).fade(0.0F, 0.8F).spawn();
		for (int i = 0; i < sparks; i++) {
			Fx.spark(at.x, at.y, at.z).velocity(Fx.range(-0.6, 0.6), Fx.range(-0.6, 0.6), Fx.range(-0.6, 0.6)).drag(0.9).fall(0.01)
					.size((float) Fx.range(0.3, 0.7)).color(1.0F, 0.75F, 0.35F).glow().alpha(1.0F).life(15 + Fx.random.nextInt(15)).spawn();
		}
	}

	private static void airburst(Vec3 at, float size) {
		double distance = Fx.distance(at.x, at.y, at.z);
		// The flash lights the sky for a moment.
		Fx.spark(at.x, at.y, at.z).size(18.0F + size * 12.0F).color(1.0F, 0.97F, 0.88F).glow().alpha(0.95F).life(12).fade(0.0F, 0.85F).spawn();
		burst(at, 6.0F + size * 4.0F, 30);
		for (int i = 0, n = scaled(24); i < n; i++) {
			Fx.cloud(at.x, at.y, at.z).velocity(Fx.range(-0.4, 0.4), Fx.range(-0.2, 0.3), Fx.range(-0.4, 0.4)).drag(0.95).size((float) Fx.range(4.0, 8.0))
					.gray((float) Fx.range(0.55, 0.7)).alpha(0.6F).life(200 + Fx.random.nextInt(100)).grow(1.5F).spawn();
		}
		DisasterAudio.distant(DisasterSounds.METEOR_BOOM, at.x, at.y, at.z, 1.0F, (float) Fx.range(0.85, 1.0));
		int delay = (int) (distance / 17.15);
		later(delay, () -> CameraShake.jolt(Math.min(0.6, 25.0 * size / Math.max(40.0, distance))));
	}

	private static void impact(Vec3 at, float radius) {
		double distance = Fx.distance(at.x, at.y, at.z);
		Fx.spark(at.x, at.y + 2, at.z).size(radius * 4.0F).color(1.0F, 0.97F, 0.88F).glow().alpha(1.0F).life(8).fade(0.0F, 0.8F).spawn();
		// The fireball.
		for (int i = 0, n = scaled(26); i < n; i++) {
			Fx.cloud(at.x, at.y + 1, at.z).velocity(Fx.range(-0.6, 0.6), Fx.range(0.1, 0.8), Fx.range(-0.6, 0.6)).drag(0.88)
					.size((float) Fx.range(radius * 0.5, radius)).color(1.0F, (float) Fx.range(0.45, 0.7), 0.15F).glow().alpha(0.95F)
					.life(18 + Fx.random.nextInt(14)).grow(1.0F).fade(0.0F, 0.6F).spawn();
		}
		// The dust and smoke boiling up into a mushroom and drifting off.
		for (int i = 0, n = scaled(40); i < n; i++) {
			float tone = (float) Fx.range(0.32, 0.48);
			Fx.cloud(at.x + Fx.range(-radius, radius), at.y + 1, at.z + Fx.range(-radius, radius))
					.velocity(Fx.range(-0.15, 0.15), Fx.range(0.35, 1.0), Fx.range(-0.15, 0.15)).drag(0.97).size((float) Fx.range(3.0, 6.0))
					.color(tone, tone * 0.88F, tone * 0.75F).alpha(0.8F).life(110 + Fx.random.nextInt(80)).grow(3.0F).fade(0.05F, 0.6F).spawn();
		}
		// A skirt of dust racing out along the ground with the blast.
		for (int i = 0, n = scaled(24); i < n; i++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double speed = Fx.range(0.5, 1.1);
			float tone = (float) Fx.range(0.38, 0.5);
			Fx.cloud(at.x, at.y + 1, at.z).velocity(Math.cos(angle) * speed, Fx.range(0.0, 0.08), Math.sin(angle) * speed).drag(0.92)
					.size((float) Fx.range(2.0, 4.0)).color(tone, tone * 0.9F, tone * 0.78F).alpha(0.7F).life(50 + Fx.random.nextInt(40)).grow(2.0F)
					.fade(0.0F, 0.6F).spawn();
		}
		// Ejecta.
		for (int i = 0, n = scaled(40); i < n; i++) {
			float tone = (float) Fx.range(0.2, 0.45);
			Fx.debris(at.x, at.y + 1, at.z).velocity(Fx.range(-0.8, 0.8), Fx.range(0.5, 1.4), Fx.range(-0.8, 0.8)).fall(0.05).drag(0.99)
					.size((float) Fx.range(0.25, 0.6)).color(tone, tone * 0.9F, tone * 0.8F).alpha(1.0F).life(50 + Fx.random.nextInt(30))
					.spin((float) Fx.range(-0.5, 0.5)).spawn();
		}
		DisasterAudio.distant(DisasterSounds.METEOR_IMPACT, at.x, at.y, at.z, 1.0F, (float) Fx.range(0.85, 1.05));
		int delay = (int) (distance / 17.15);
		later(delay, () -> CameraShake.jolt(Math.min(1.0, radius * 4.0 / Math.max(20.0, distance))));
	}

	// --- volcanoes ---------------------------------------------------------------------------

	private static void blast(Vec3 at, float vei) {
		double distance = Fx.distance(at.x, at.y, at.z);
		Fx.spark(at.x, at.y + 6, at.z).size(30.0F + vei * 8.0F).color(1.0F, 0.8F, 0.55F).glow().alpha(0.9F).life(10).fade(0.0F, 0.8F).spawn();
		// The shock wave condensing the air into a ring as it races out.
		for (int i = 0, n = scaled(60); i < n; i++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double speed = Fx.range(1.0, 1.6);
			Fx.cloud(at.x, at.y + Fx.range(0.0, 12.0), at.z).velocity(Math.cos(angle) * speed, Fx.range(0.0, 0.2), Math.sin(angle) * speed).drag(0.93)
					.size((float) Fx.range(4.0, 8.0)).gray(0.92F).alpha(0.5F).life(24 + Fx.random.nextInt(10)).grow(1.0F).fade(0.0F, 0.7F).spawn();
		}
		DisasterAudio.distant(DisasterSounds.ERUPTION_BLAST, at.x, at.y, at.z, 1.0F, (float) Fx.range(0.8, 0.95));
		int delay = (int) (distance / 17.15);
		later(delay, () -> CameraShake.jolt(Math.min(1.0, vei * 30.0 / Math.max(30.0, distance))));
	}

	private static final class Bomb extends Mover {
		private final float size;

		Bomb(Vec3 pos, Vec3 velocity, float size) {
			super(pos, velocity);
			this.size = size;
		}

		@Override
		boolean tick() {
			pos = pos.add(velocity);
			velocity = velocity.scale(Eruption.DRAG).add(0.0, -Eruption.GRAVITY, 0.0);
			Fx.spark(pos.x, pos.y, pos.z).size(0.5F + size * 0.6F).color(1.0F, 0.55F, 0.15F).glow().alpha(1.0F).life(3).fade(0.0F, 0.5F).spawn();
			Vec3 back = pos.subtract(velocity.scale(0.5));
			Fx.cloud(back.x, back.y, back.z).size(1.0F + size).gray((float) Fx.range(0.3, 0.42)).alpha(0.55F).life(40 + Fx.random.nextInt(40))
					.grow(2.0F).fade(0.05F, 0.6F).spawn();
			BlockPos at = BlockPos.containing(pos);
			if (!Fx.level.hasChunkAt(at.getX(), at.getZ()) || pos.y < Fx.level.getMinY()) {
				return false;
			}
			BlockState state = Fx.level.getBlockState(at);
			return ++age < 600 && !(velocity.y < 0 && !state.isAir() && !state.canBeReplaced());
		}
	}

	private static void bombLands(Vec3 at, float size) {
		burst(at, 1.5F + size, 8);
		for (int i = 0, n = scaled(6); i < n; i++) {
			Fx.cloud(at.x, at.y, at.z).velocity(Fx.range(-0.1, 0.1), Fx.range(0.05, 0.2), Fx.range(-0.1, 0.1)).size((float) Fx.range(1.0, 2.5))
					.gray(0.35F).alpha(0.7F).life(60).grow(1.5F).spawn();
		}
		if (Fx.distance(at.x, at.y, at.z) < 32.0) {
			for (int i = 0, n = scaled(6); i < n; i++) {
				Fx.level.addParticle(ParticleTypes.LAVA, at.x, at.y, at.z, 0.0, 0.0, 0.0);
			}
		}
	}
}
