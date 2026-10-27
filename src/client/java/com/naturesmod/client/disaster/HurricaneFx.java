package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterSounds;
import com.naturesmod.disaster.Hurricane;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a hurricane. Seen coming, far off across the land or the sea, it is a vast dark wall of cloud along
 * its edge with rain hanging under it, turning slowly as it comes on; the sky dims and its roar grows. Under
 * it the air is dark and grey and thick with rain driven slantwise on the wind: in a rain band a squall, the
 * world a few dozen blocks wide, between the bands a lull; the leaves and twigs it strips fly past, ragged low
 * clouds race overhead, spray streams off the sea. In the eyewall it is darkest, a few blocks of roaring rain,
 * the gusts buffeting. Then the eye: the rain thins, the sky lightens and all round stands the eyewall, a
 * towering ring of cloud sloping outward as it rises, turning slowly about you, until its far side arrives.
 * Under a roof only its roar is heard, muffled.
 *
 * <p>Most of it is fog. The particles are few and big: the wall a handful of billows a tick, drawn only along
 * the stretch of it facing the viewer and fewer the further off it is; the rain, spray and debris close about
 * the viewer only, thinned with the rest on a slow computer, and none under a roof.
 */
final class HurricaneFx {
	/** How far beyond its edge it is drawn and heard at all. */
	private static final double FAR = 900.0;
	/** The colour of the air under it: a dark blue-grey (dimmed with the daylight by the fog itself). */
	private static final float RED = 0.36F;
	private static final float GREEN = 0.4F;
	private static final float BLUE = 0.45F;
	/** The paler air of the eye. */
	private static final float EYE_RED = 0.7F;
	private static final float EYE_GREEN = 0.74F;
	private static final float EYE_BLUE = 0.8F;
	/** Metres a second to blocks a tick for the rain driven past, and for the spray and leaves it carries. */
	private static final double RAIN_SCALE = 0.018;
	private static final double CARRY_SCALE = 0.013;

	private HurricaneFx() {
	}

	/** Its axis: straight up through its centre, followed as it moves. */
	private static DisasterParticle.Axis axis(ClientDisaster d) {
		return new DisasterParticle.Axis() {
			@Override
			public boolean alive() {
				return !d.removed;
			}

			@Override
			public double x(double height) {
				return d.x;
			}

			@Override
			public double z(double height) {
				return d.z;
			}
		};
	}

	static void tick(ClientDisaster d) {
		double eye = Math.max(4.0, d.extra(0));
		double rmw = Math.max(eye + 2.0, d.extra(1));
		double wall = rmw - eye;
		double category = Math.max(1.0, Math.min(5.0, d.extra(2)));
		double peak = Math.max(0.0, d.extra(3));
		double cloudBase = d.extra.length > 5 ? d.extra(4) : d.y + 36.0;
		double cloudTop = Math.max(cloudBase + 20.0, d.extra(5));
		double speed = Math.max(0.0, Math.min(1.0, d.extra(6)));
		boolean sea = d.extra(7) > 0.5F;
		double outer = Math.max(rmw + wall + 10.0, d.radius);
		double dx = Fx.eye.x - d.x;
		double dz = Fx.eye.z - d.z;
		double r = Math.sqrt(dx * dx + dz * dz);
		if (!(r < outer + FAR)) {
			return;
		}
		DisasterParticle.Axis axis = axis(d);
		if (r >= outer) {
			approach(d, axis, r, outer, cloudBase, cloudTop, sea);
			return;
		}
		Vec3 wind = Hurricane.wind(dx, dz, eye, rmw, outer, peak, d.age, d.dirX * speed * 20.0, d.dirZ * speed * 20.0);
		double band = Hurricane.band(dx, dz, eye, rmw, outer, d.age);
		// How deep in the eye the viewer is: 1 well inside it, 0 at its edge and beyond.
		double calm = Math.max(0.0, Math.min(1.0, (eye - r) / (eye * 0.35)));
		inside(d, wind, band, calm, r, outer, category, cloudBase);
		if (r < eye + wall * 0.3) {
			eyewall(d, axis, eye, wall, cloudTop, peak, calm);
		}
	}

	/** Under it: the dark, the rain, the roar, and whatever the wind is carrying past. */
	private static void inside(ClientDisaster d, Vec3 wind, double band, double calm, double r, double outer, double category, double cloudBase) {
		float strength = d.strength;
		if (!(strength > 0.0F)) {
			return;
		}
		// What it carries past thins out toward its edge (the air is as dark there as it was from outside).
		float edge = (float) Math.min(1.0, (outer - r) / 80.0) * strength;
		// What is over the viewer's head: open sky, the crowns of trees, or a roof (or water, or the rock over a cave).
		int bx = (int) Math.floor(Fx.eye.x);
		int bz = (int) Math.floor(Fx.eye.z);
		int roof = Fx.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		double over = roof - Fx.eye.y;
		float open;
		if (over <= 0.0) {
			open = 1.0F;
		} else if (over < 24.0 && Fx.level.getBlockState(new BlockPos(bx, roof - 1, bz)).is(BlockTags.LEAVES)) {
			open = 0.5F;
		} else {
			open = 0.0F;
		}
		double w = wind.horizontalDistance();
		// The rain: heaviest in the bands and the eyewall, lighter between them, thin in the eye.
		double rain = Math.max(0.0, Math.min(1.0, 0.2 + 0.8 * band)) * (1.0 - 0.85 * calm);
		float seeing = (float) (120.0 - (110.0 - 5.0 * (5.0 - category)) * rain);
		float thick = (float) ((0.4 + 0.55 * rain) * (1.0 - 0.7 * calm)) * strength;
		float light = (float) calm;
		float red = RED + (EYE_RED - RED) * light;
		float green = GREEN + (EYE_GREEN - GREEN) * light;
		float blue = BLUE + (EYE_BLUE - BLUE) * light;
		if (open > 0.0F) {
			DisasterClient.fog(thick, red, green, blue, seeing);
		} else {
			DisasterClient.fog(thick * 0.35F, red, green, blue, seeing * 3.0F);
		}
		// Its roar, all round: loudest in the eyewall, and still heard from the eye.
		float roar = (float) Math.max(Math.min(1.0, 0.2 + 0.8 * w / 70.0), 0.4 * calm);
		DisasterAudio.loop("hurricane" + d.id, DisasterSounds.HURRICANE_WIND, false, roar * strength * (open > 0.0F ? 1.0F : 0.5F), 0, 0, 0);
		float howl = (float) (Math.max(0.0, Math.min(1.0, (w - 40.0) / 40.0)) * (0.5 + 0.5 * band)) * strength;
		DisasterAudio.loop("hurricanegust" + d.id, DisasterSounds.WIND_HOWL, false, howl * (open > 0.0F ? 1.0F : 0.3F), 0, 0, 0);
		if (open <= 0.0F || !(w > 1.0)) {
			return;
		}
		if (w > 45.0) {
			// Buffeted by the gusts.
			CameraShake.shake((w - 45.0) / 40.0 * 0.03 * edge * open);
		}
		double ux = wind.x / w;
		double uz = wind.z / w;
		double amount = edge * open;
		rain(ux, uz, w, rain * amount);
		boolean water = over <= 0.0 && Fx.eye.y - roof < 12.0 && !Fx.level.getBlockState(new BlockPos(bx, roof - 1, bz)).getFluidState().isEmpty();
		if (over <= 0.0 && Fx.eye.y - roof < 12.0) {
			spray(ux, uz, w, (0.3 + 0.7 * rain) * amount, roof, water);
		}
		if (w > 30.0 && !water) {
			debris(ux, uz, w, amount);
		}
		if (calm < 0.5) {
			scud(ux, uz, w, cloudBase, amount);
		}
	}

	/** The rain driven past on the wind, slanting the harder it blows: spawned upwind and above, so that it crosses the view. */
	private static void rain(double ux, double uz, double w, double amount) {
		if (amount <= 0.0) {
			return;
		}
		double drive = w * RAIN_SCALE;
		Vec3 path = new Vec3(ux * drive, -1.1, uz * drive);
		float length = (float) path.length();
		int streaks = share(Fx.round((8.0 + 30.0 * amount) * amount * Fx.density));
		for (int i = 0; i < streaks; i++) {
			double v = Fx.range(0.85, 1.15);
			int life = 6 + Fx.random.nextInt(4);
			double back = life * 0.5 * v;
			Fx.streak(Fx.eye.x - path.x * back + Fx.range(-7.0, 7.0), Fx.eye.y - path.y * back + Fx.range(-3.0, 3.0),
					Fx.eye.z - path.z * back + Fx.range(-7.0, 7.0)).along(path).velocity(path.x * v, path.y * v, path.z * v).drag(1.0)
					.size((float) Math.max(0.6, length * Fx.range(0.6, 0.9))).color(0.72F, 0.76F, 0.83F).alpha(0.4F).life(life).fade(0.15F, 0.3F)
					.spawn();
		}
		// Sheets of rain sweeping past in the squalls: grey veils between the viewer and the fog.
		int veils = share(Fx.round(3.0 * amount * Fx.density));
		for (int i = 0; i < veils; i++) {
			Fx.cloud(Fx.eye.x - ux * 12.0 + Fx.range(-12.0, 12.0), Fx.eye.y + Fx.range(-2.0, 5.0), Fx.eye.z - uz * 12.0 + Fx.range(-12.0, 12.0))
					.velocity(ux * drive * 0.8, -0.05, uz * drive * 0.8).drag(1.0).size((float) Fx.range(4.0, 7.0)).color(0.55F, 0.6F, 0.66F)
					.alpha(0.25F).life(30 + Fx.random.nextInt(15)).grow(0.5F).fade(0.25F, 0.4F).spawn();
		}
	}

	/** Spray torn off the sea, or the mist of the rain off the ground, streaming low along it about the viewer. */
	private static void spray(double ux, double uz, double w, double amount, int surface, boolean water) {
		double drive = w * CARRY_SCALE;
		int count = share(Fx.round((water ? 5.0 : 2.0) * amount * Fx.density));
		for (int i = 0; i < count; i++) {
			int life = 18 + Fx.random.nextInt(12);
			double back = drive * life * 0.5;
			float shade = (float) (water ? Fx.range(0.82, 0.92) : Fx.range(0.55, 0.65));
			Fx.cloud(Fx.eye.x - ux * back + Fx.range(-10.0, 10.0), surface + Fx.range(0.0, water ? 2.0 : 1.0), Fx.eye.z - uz * back + Fx.range(-10.0, 10.0))
					.velocity(ux * drive, Fx.range(0.0, 0.03), uz * drive).drag(0.99).size((float) Fx.range(1.2, 2.6)).color(shade, shade + 0.02F, shade + 0.05F)
					.alpha(0.4F).life(life).grow(0.8F).fade(0.15F, 0.4F).spawn();
		}
	}

	/** Leaves and twigs stripped off and flying past, tumbling. */
	private static void debris(double ux, double uz, double w, double amount) {
		double drive = w * CARRY_SCALE;
		int count = share(Fx.round(Math.min(1.0, (w - 30.0) / 40.0) * 7.0 * amount * Fx.density));
		for (int i = 0; i < count; i++) {
			int life = 14 + Fx.random.nextInt(10);
			double back = drive * life * 0.5;
			DisasterParticle p = Fx.debris(Fx.eye.x - ux * back + Fx.range(-8.0, 8.0), Fx.eye.y + Fx.range(-2.0, 4.0), Fx.eye.z - uz * back + Fx.range(-8.0, 8.0));
			if (Fx.random.nextInt(3) != 0) {
				float leaf = (float) Fx.range(0.3, 0.5);
				p.color(leaf * 0.55F, leaf, leaf * 0.35F);
			} else {
				float twig = (float) Fx.range(0.25, 0.4);
				p.color(twig, twig * 0.8F, twig * 0.6F);
			}
			p.velocity(ux * drive * Fx.range(0.8, 1.2), Fx.range(-0.05, 0.12), uz * drive * Fx.range(0.8, 1.2)).drag(0.98).fall(0.01)
					.size((float) Fx.range(0.1, 0.25)).alpha(1.0F).life(life).fade(0.05F, 0.2F).spin((float) Fx.range(-0.5, 0.5)).spawn();
		}
	}

	/** Low, ragged clouds racing overhead under the deck. */
	private static void scud(double ux, double uz, double w, double cloudBase, double amount) {
		int count = share(Fx.round(0.8 * amount * Fx.density));
		double drive = w * 0.01;
		double h = Math.max(cloudBase - 6.0, Fx.eye.y + 16.0);
		for (int i = 0; i < count; i++) {
			Fx.cloud(Fx.eye.x - ux * 40.0 + Fx.range(-40.0, 40.0), h + Fx.range(0.0, 10.0), Fx.eye.z - uz * 40.0 + Fx.range(-40.0, 40.0))
					.velocity(ux * drive, 0.0, uz * drive).drag(1.0).size((float) Fx.range(10.0, 16.0)).gray((float) Fx.range(0.3, 0.4)).alpha(0.7F)
					.life(70 + Fx.random.nextInt(30)).fade(0.25F, 0.35F).spin((float) Fx.range(-0.01, 0.01)).spawn();
		}
	}

	/**
	 * The eyewall seen from the eye: a ring of cloud towering from the ground (or the sea) to the top of the storm,
	 * sloping outward as it rises like the sides of a stadium, dark at its foot and lit white above, turning slowly
	 * about the viewer.
	 */
	private static void eyewall(ClientDisaster d, DisasterParticle.Axis axis, double eye, double wall, double cloudTop, double peak, double calm) {
		int count = share(Fx.round(4.0 * (0.4 + 0.6 * calm) * Math.max(0.0F, Math.min(1.0F, d.strength)) * Fx.density));
		double base = Math.min(d.y, Fx.eye.y - 6.0);
		double height = Math.max(20.0, cloudTop - base);
		for (int i = 0; i < count; i++) {
			double f = Math.pow(Fx.random.nextDouble(), 0.8);
			double radius = eye * (1.0 + 0.7 * f) + Fx.range(0.0, wall * 0.5);
			double angular = -(0.15 + peak * 0.006) / Math.max(4.0, radius);
			float shade = (float) (Fx.range(0.32, 0.42) + 0.45 * f);
			Fx.cloud(d.x, base + f * height, d.z).vortex(axis, Fx.random.nextDouble() * Math.PI * 2.0, radius, angular, 0.0, 0.0)
					.size((float) (Fx.range(12.0, 20.0) * (1.0 + 0.6 * f))).gray(shade).alpha(0.85F).life(60 + Fx.random.nextInt(30))
					.fade(0.25F, 0.3F).spin((float) Fx.range(-0.01, 0.01)).spawn();
		}
	}

	/**
	 * The storm seen coming: its wall of cloud along the stretch of its edge facing the viewer, rain hanging under it
	 * (paler over the sea, with the spray), turning slowly; the nearer it is the darker the sky and the louder its roar.
	 */
	private static void approach(ClientDisaster d, DisasterParticle.Axis axis, double r, double outer, double cloudBase, double cloudTop, boolean sea) {
		double gap = r - outer;
		float near = (float) Math.max(0.0, 1.0 - gap / FAR) * d.strength;
		DisasterClient.fog(0.35F * near * near, RED, GREEN, BLUE, (float) Math.max(100.0, gap * 1.2 + 60.0));
		DisasterAudio.loop("hurricane" + d.id, DisasterSounds.HURRICANE_WIND, false, 0.2F * near * near, 0, 0, 0);
		int count = Fx.budget(4, gap, FAR);
		if (count <= 0) {
			return;
		}
		double toward = Math.atan2(Fx.eye.z - d.z, Fx.eye.x - d.x);
		// Spread the wider the further off it is, as much of it is in view.
		double spread = Math.min(1.2, (0.5 * gap + 120.0) / outer);
		double deck = cloudBase + (cloudTop - cloudBase) * 0.5;
		double size = Math.max(16.0, Math.min(48.0, gap * 0.06 + 16.0));
		for (int i = 0; i < count; i++) {
			double angle = toward + Fx.random.nextGaussian() * spread;
			double radius = outer * Fx.range(0.88, 1.0);
			boolean curtain = Fx.random.nextInt(3) == 0;
			double h = curtain ? Fx.range(d.y, cloudBase) : Fx.range(cloudBase - 8.0, deck);
			float shade = curtain ? (sea ? 0.5F : 0.4F) : (float) Fx.range(0.3, 0.45);
			Fx.cloud(d.x, h, d.z).vortex(axis, angle, radius, -0.0006, 0.0, 0.0).size((float) (size * Fx.range(0.75, 1.2) * (curtain ? 0.8 : 1.0)))
					.gray(shade).alpha(curtain ? 0.5F : 0.85F).life(70 + Fx.random.nextInt(30)).grow(0.2F).fade(0.2F, 0.35F).spawn();
		}
	}

	/** No more than the particles left to spend this tick, all disasters together. */
	private static int share(int count) {
		return Math.max(0, Math.min(count, Fx.cap - Fx.spawned));
	}
}
