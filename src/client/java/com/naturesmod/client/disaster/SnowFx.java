package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterSounds;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a blizzard, whatever the land under it. Inside it a whiteout: the air gone grey-white to a few blocks
 * (the sky with it), closing in further in the gusts, the snow driving past sideways in the gale, streaming
 * along the ground and swirling by in veils, and the howl of it rising with each squall. From outside, a wall
 * of white along its edge and its wind heard before it arrives. Under the crowns of trees some of it gets
 * through; under a roof only a muffled howl, and down a cave nothing at all.
 *
 * <p>The gale leans on the player here too, in the player's own game, which is what moves the player. From the
 * server every push would also set the player's own motion back to what the server last knew of it, a tick or
 * two old, for the minutes the storm lasts: jumps would float and stops would slide. Walking into it is a
 * struggle, going with it a stagger; crouching braces against it.
 *
 * <p>Most of the whiteout is fog. The particles are a modest number about the eye, thinned with the rest on a
 * slow computer, and none where they would only be hidden.
 */
final class SnowFx {
	/** The colour of the whiteout (dimmed with the daylight by the fog itself). */
	private static final float RED = 0.84F;
	private static final float GREEN = 0.86F;
	private static final float BLUE = 0.9F;

	private SnowFx() {
	}

	static void tick(ClientDisaster d) {
		double radius = Math.max(1.0, d.radius);
		double visibility = d.extra(0) > 0.0F ? d.extra(0) : 8.0;
		double speed = d.extra(1) > 0.0F ? Math.min(3.0, d.extra(1)) : 0.8;
		double gust = d.extra(2) > 0.0F ? Math.min(2.0, d.extra(2)) : 1.0;
		double distance = Fx.horizontal(d.x, d.z);
		if (distance < radius) {
			float inside = (float) (Math.min(1.0, (radius - distance) / 60.0) * d.strength);
			if (inside > 0.0F) {
				inside(d, inside, visibility, speed, gust);
			}
		} else if (distance < radius + 300.0) {
			approach(d, distance, radius, speed);
		}
	}

	private static void inside(ClientDisaster d, float inside, double visibility, double speed, double gust) {
		// What is over the viewer's head: open sky, the crowns of trees, a roof, or the rock over a cave.
		int bx = (int) Math.floor(Fx.eye.x);
		int bz = (int) Math.floor(Fx.eye.z);
		int roof = Fx.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		double over = roof - Fx.eye.y;
		float air;
		float snow;
		float wind;
		float sound;
		if (over <= 0.0) {
			air = 1.0F;
			snow = 1.0F;
			wind = 1.0F;
			sound = 1.0F;
		} else if (over < 24.0 && Fx.level.getBlockState(new BlockPos(bx, roof - 1, bz)).is(BlockTags.LEAVES)) {
			air = 0.85F;
			snow = 0.45F;
			wind = 0.5F;
			sound = 0.9F;
		} else if (over < 10.0) {
			air = 0.3F;
			snow = 0.0F;
			wind = 0.0F;
			sound = 0.55F;
		} else {
			air = 0.0F;
			snow = 0.0F;
			wind = 0.0F;
			sound = 0.15F;
		}
		// The gusts close it in further; the lulls open it a little.
		float seeing = (float) Math.max(3.0, visibility * (1.3 - 0.3 * gust));
		if (air > 0.0F) {
			DisasterClient.fog(air * inside, RED, GREEN, BLUE, seeing / air);
		}
		float howl = (float) (0.65 + 0.35 * Math.min(1.5, gust));
		DisasterAudio.loop("blizzard" + d.id, DisasterSounds.BLIZZARD_WIND, false, Math.min(1.0F, inside * howl * sound), 0, 0, 0);
		float squall = (float) Math.max(0.0, gust - 1.1) * 2.0F * inside * sound;
		DisasterAudio.loop("blizzardgust" + d.id, DisasterSounds.WIND_HOWL, false, Math.min(1.0F, squall), 0, 0, 0);
		lean(d, inside * wind, gust);
		if (snow > 0.0F) {
			boolean low = over <= 0.0 && Fx.eye.y - roof < 6.0;
			drive(d, inside * snow, speed * gust, gust, roof, low);
		}
	}

	/** The snow flying past, about the eye: spawned upwind so that it crosses the view while it lasts. */
	private static void drive(ClientDisaster d, float amount, double drive, double gust, int ground, boolean low) {
		double wx = d.dirX;
		double wz = d.dirZ;
		// Flakes: most of them close in, where they are seen to fly by.
		int flakes = share(Fx.round(55.0 * amount * Fx.density));
		for (int i = 0; i < flakes; i++) {
			double v = drive * Fx.range(0.75, 1.25);
			int life = 10 + Fx.random.nextInt(10);
			double back = v * life * 0.5;
			double spread = Fx.random.nextInt(3) == 0 ? 10.0 : 4.0;
			Fx.flake(Fx.eye.x - wx * back + Fx.range(-spread, spread), Fx.eye.y + Fx.range(-2.5, 3.5), Fx.eye.z - wz * back + Fx.range(-spread, spread))
					.velocity(wx * v + Fx.range(-0.05, 0.05), -Fx.range(0.02, 0.1), wz * v + Fx.range(-0.05, 0.05)).drag(1.0)
					.size((float) Fx.range(0.06, 0.13)).color(0.97F, 0.98F, 1.0F).alpha(0.95F).life(life).fade(0.1F, 0.25F).spawn();
		}
		// The gale made visible: streaks of driven snow, more of them in the gusts.
		Vec3 path = new Vec3(wx * drive, -0.06, wz * drive);
		int streaks = share(Fx.round(14.0 * amount * Math.min(1.6, gust) * Fx.density));
		for (int i = 0; i < streaks; i++) {
			double v = Fx.range(0.85, 1.15);
			int life = 6 + Fx.random.nextInt(5);
			double back = drive * v * life * 0.5;
			Fx.streak(Fx.eye.x - wx * back + Fx.range(-6.0, 6.0), Fx.eye.y + Fx.range(-2.0, 3.0), Fx.eye.z - wz * back + Fx.range(-6.0, 6.0))
					.along(path).velocity(path.x * v, path.y, path.z * v).drag(1.0).size((float) Math.max(0.3, drive * Fx.range(0.5, 0.9)))
					.color(0.95F, 0.96F, 0.98F).alpha(0.45F).life(life).fade(0.2F, 0.4F).spawn();
		}
		// Veils of snow swirling past, between the viewer and the fog: few of them, since big see-through sheets close
		// to the eye cost a weak graphics card the most.
		int veils = share(Fx.round(3.5 * amount * Fx.density));
		for (int i = 0; i < veils; i++) {
			Fx.cloud(Fx.eye.x - wx * 10.0 + Fx.range(-12.0, 12.0), Fx.eye.y + Fx.range(-2.0, 5.0), Fx.eye.z - wz * 10.0 + Fx.range(-12.0, 12.0))
					.velocity(wx * drive * 0.6, Fx.range(-0.02, 0.01), wz * drive * 0.6).drag(1.0).size((float) Fx.range(3.0, 5.5))
					.color(0.9F, 0.92F, 0.95F).alpha(0.3F).life(25 + Fx.random.nextInt(15)).grow(0.5F).fade(0.25F, 0.4F)
					.spin((float) Fx.range(-0.03, 0.03)).spawn();
		}
		if (!low) {
			return;
		}
		// Spindrift: snow torn off the ground, streaming along it about the viewer's feet.
		int drifts = share(Fx.round(8.0 * amount * Math.min(1.5, gust) * Fx.density));
		for (int i = 0; i < drifts; i++) {
			double v = drive * Fx.range(0.8, 1.2);
			int life = 14 + Fx.random.nextInt(10);
			double back = v * life * 0.5;
			Fx.cloud(Fx.eye.x - wx * back + Fx.range(-8.0, 8.0), ground + Fx.range(0.0, 0.7), Fx.eye.z - wz * back + Fx.range(-8.0, 8.0))
					.velocity(wx * v, Fx.range(0.0, 0.03), wz * v).drag(0.99).size((float) Fx.range(0.6, 1.4)).color(0.95F, 0.96F, 0.98F)
					.alpha(0.45F).life(life).grow(0.8F).fade(0.15F, 0.4F).spawn();
		}
	}

	/**
	 * The gale on the player, a little every tick. On the ground, where the game slows a player quickly, it holds
	 * one walking into it to half pace (less in a squall) and hurries one going with it; off the ground, where
	 * nothing slows what it gives, a little goes a long way.
	 */
	private static void lean(ClientDisaster d, float exposure, double gust) {
		LocalPlayer player = Fx.mc.player;
		if (player == null || exposure <= 0.0F || player.isCreative() || player.isSpectator() || player.isPassenger()) {
			return;
		}
		double push = (0.008 + 0.007 * d.intensity) * Math.min(1.7, gust) * exposure;
		if (player.isShiftKeyDown()) {
			// Braced against it.
			push *= 0.5;
		}
		if (!player.onGround()) {
			push *= 0.35;
		}
		player.push(d.dirX * push, 0.0, d.dirZ * push);
	}

	/** The storm seen coming: a wall of white along its edge, its howl heard ahead of it, the air already thickening. */
	private static void approach(ClientDisaster d, double distance, double radius, double speed) {
		double gap = distance - radius;
		float near = (float) Math.max(0.0, 1.0 - gap / 300.0) * d.strength;
		DisasterClient.fog(0.3F * near * near, RED, GREEN, BLUE, 96.0F);
		DisasterAudio.loop("blizzard" + d.id, DisasterSounds.BLIZZARD_WIND, false, 0.4F * near * near, 0, 0, 0);
		double ux = (Fx.eye.x - d.x) / distance;
		double uz = (Fx.eye.z - d.z) / distance;
		// Thinning with the storm as it forms and as it dies away.
		int count = Fx.round(Fx.budget(14, gap, 300.0) * Math.min(1.0F, d.strength * 2.0F));
		for (int i = 0; i < count; i++) {
			double angle = Math.atan2(uz, ux) + Fx.random.nextGaussian() * 0.25;
			double x = d.x + Math.cos(angle) * radius;
			double z = d.z + Math.sin(angle) * radius;
			Fx.cloud(x, d.y + Fx.range(0.0, 60.0), z).velocity(d.dirX * speed * 0.3, 0.0, d.dirZ * speed * 0.3).drag(1.0)
					.size((float) Fx.range(10.0, 20.0)).color(RED, GREEN, BLUE).alpha(0.75F).life(80 + Fx.random.nextInt(60)).spawn();
		}
	}

	/** No more than the particles left to spend this tick, all disasters together. */
	private static int share(int count) {
		return Math.max(0, Math.min(count, Fx.cap - Fx.spawned));
	}
}
