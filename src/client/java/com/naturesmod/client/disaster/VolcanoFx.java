package com.naturesmod.client.disaster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.naturesmod.disaster.DisasterSounds;
import com.naturesmod.disaster.Eruption;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Draws an eruption: the steam of its unrest, thickening as it nears, with now and then a dark burst
 * of ash; then the blast's own cloud rolling out over the land in every direction, and the column, a
 * jet of dark ash roaring out of the vent, lit red from beneath by the fountaining lava, boiling up
 * hundreds of blocks, bending downwind and spreading at the top into an umbrella cloud; glowing bombs
 * arcing out of the vent trailing smoke and bursting where they land; ash falling under the cloud,
 * where the air goes dark; pyroclastic flows rolling down the mountain, a grey wall as wide as the flow
 * with great billows lifting off behind it, and the roar of them; at last the steam again as it wanes.
 * Close to the mountain the ground trembles the whole time it erupts.
 *
 * <p>Everything goes through the particle budget, few and big: the column and the flows are some
 * dozens of billows a tick between them, the bombs a spark each, and the bombs this client follows are
 * capped and let go as soon as they land or leave the land it has loaded.
 */
final class VolcanoFx {
	/** Bombs followed in flight at once, per volcano. */
	private static final int MAX_BOMBS = 32;
	private static final Map<Integer, Volcano> VOLCANOES = new HashMap<>();

	private VolcanoFx() {
	}

	/**
	 * What this client keeps of a volcano from one tick to the next: the bombs it is following in flight. (Not the world
	 * it is in: a world left behind would be kept from being let go.)
	 */
	private static final class Volcano {
		final ClientDisaster disaster;
		final List<Bomb> bombs = new ArrayList<>();
		/** The number of the last bomb heard of. */
		int lastBomb;

		Volcano(ClientDisaster disaster) {
			this.disaster = disaster;
		}
	}

	/** A bomb in flight, flown as the server flies it (see {@link Eruption#GRAVITY}). */
	private static final class Bomb {
		double x;
		double y;
		double z;
		double vx;
		double vy;
		double vz;
		final float size;
		int age;

		Bomb(double x, double y, double z, double vx, double vy, double vz, float size) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			this.size = size;
		}

		void move() {
			x += vx;
			y += vy;
			z += vz;
			vx *= Eruption.DRAG;
			vy = vy * Eruption.DRAG - Eruption.GRAVITY;
			vz *= Eruption.DRAG;
			age++;
		}
	}

	static void tick(ClientDisaster d) {
		int vei = Math.max(2, Math.min(7, Math.round(finite(d.extra(0), 4.0F))));
		int phase = Math.max(0, Math.min(2, (int) d.extra(1)));
		double base = finite(d.extra(2), (float) d.y);
		double column = Math.max(0.0, Math.min(800.0, finite(d.extra(3), 0.0F)));
		double windX = finite(d.extra(4), 1.0F);
		double windZ = finite(d.extra(5), 0.0F);
		double windLength = Math.sqrt(windX * windX + windZ * windZ);
		if (windLength < 0.01) {
			windX = 1.0;
			windZ = 0.0;
		} else {
			windX /= windLength;
			windZ /= windLength;
		}
		double ashReach = Math.max(1.0, Math.min(1000.0, finite(d.extra(6), 100.0F)));
		double crater = Math.max(2.0, Math.min(40.0, finite(d.extra(7), 6.0F)));
		// How many flows and fresh bombs follow, and where: no more than the numbers sent can hold.
		int sentFlows = Math.max(0, Math.min(64, (int) d.extra(8)));
		int flows = Math.min(Math.min(sentFlows, Eruption.MAX_FLOWS), Math.max(0, (d.extra.length - Eruption.FLOWS_AT) / Eruption.FLOW_FLOATS));
		int bombsAt = Eruption.FLOWS_AT + sentFlows * Eruption.FLOW_FLOATS;
		int news = Math.max(0, Math.min((int) d.extra(9), (d.extra.length - bombsAt) / Eruption.BOMB_FLOATS));
		double sinceBlast = finite(d.extra(10), -1.0F);
		double nearing = Math.max(0.0, Math.min(1.0, finite(d.extra(11), 0.0F)));
		double distance = Fx.horizontal(d.x, d.z);
		Volcano volcano = volcano(d);
		sound(d, phase, base, nearing);
		rumble(d, phase, distance, vei);
		ash(d, phase, windX, windZ, ashReach);
		news(d, volcano, base, bombsAt, news);
		bombs(volcano);
		int budget = Fx.budget(70 + 6 * vei, distance, 1600.0);
		if (budget > 0) {
			if (phase == 0) {
				steam(d, budget, base, crater, windX, windZ, nearing, false);
			} else if (phase == 2) {
				steam(d, budget, base, crater, windX, windZ, 0.3, true);
			}
			if (phase != 0 && column > 1.0) {
				column(d, budget, base, column, windX, windZ, crater, phase == 1 || d.strength > 0.5F);
			}
			if (sinceBlast >= 0.0 && sinceBlast < 100.0) {
				surge(d, budget, base, crater, sinceBlast, vei);
			}
		}
		flows(d, flows);
	}

	private static float finite(float value, float otherwise) {
		return Float.isFinite(value) ? value : otherwise;
	}

	/**
	 * This client's record of a volcano, begun the first time it is drawn; those of volcanoes gone are dropped (leaving a
	 * world forgets all its disasters, so theirs go too).
	 */
	private static Volcano volcano(ClientDisaster d) {
		if (!VOLCANOES.isEmpty()) {
			VOLCANOES.values().removeIf(v -> DisasterClient.get(v.disaster.id) != v.disaster);
		}
		Volcano volcano = VOLCANOES.get(d.id);
		if (volcano == null) {
			volcano = new Volcano(d);
			VOLCANOES.put(d.id, volcano);
		}
		return volcano;
	}

	/** The height of the ground at (x, z) as this client has it, or `otherwise` where it hasn't that land loaded. */
	private static double ground(double x, double z, double otherwise) {
		int top = Fx.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
		return top > Fx.level.getMinY() + 1 ? top : otherwise;
	}

	/**
	 * Steam off the summit. In the unrest it wisps, then billows as the magma nears, and now and then the vent clears
	 * its throat in a dark burst of ash; as the eruption wanes it is grey with the last of the ash.
	 */
	private static void steam(ClientDisaster d, int budget, double base, double crater, double windX, double windZ, double amount, boolean waning) {
		int count = Math.max(1, Fx.round(budget * (0.04 + 0.1 * amount)));
		for (int i = 0; i < count; i++) {
			Fx.cloud(d.x + Fx.range(-crater, crater), base + Fx.range(0.5, 2.5), d.z + Fx.range(-crater, crater))
					.velocity(windX * 0.05, Fx.range(0.1, 0.25 + 0.15 * amount), windZ * 0.05).drag(0.99).size((float) Fx.range(3.0, 6.0 + 3.0 * amount))
					.gray(waning ? 0.6F : 0.92F).alpha(0.55F).life(90 + Fx.random.nextInt(60)).grow(1.5F).spawn();
		}
		if (!waning && amount > 0.5 && Fx.random.nextInt(160) == 0) {
			int puffs = Math.min(budget, 12);
			for (int i = 0; i < puffs; i++) {
				float tone = (float) Fx.range(0.22, 0.34);
				Fx.cloud(d.x + Fx.range(-crater * 0.5, crater * 0.5), base + 1.0, d.z + Fx.range(-crater * 0.5, crater * 0.5))
						.velocity(Fx.range(-0.08, 0.08), Fx.range(0.5, 0.9), Fx.range(-0.08, 0.08)).drag(0.95).size((float) Fx.range(4.0, 8.0))
						.color(tone, tone * 0.95F, tone * 0.9F).alpha(0.85F).life(80 + Fx.random.nextInt(50)).grow(2.0F).fade(0.05F, 0.5F).spawn();
			}
		}
	}

	private static void column(ClientDisaster d, int budget, double base, double column, double windX, double windZ, double crater, boolean erupting) {
		// The jet and the convecting column above it, bending with the wind as it climbs, its foot lit by the lava.
		int jet = (int) (budget * 0.4);
		for (int i = 0; i < jet; i++) {
			double u = Math.pow(Fx.random.nextDouble(), 1.3);
			double h = base + u * column;
			double bend = u * u * column * 0.28;
			double spread = crater * 0.7 + u * column * 0.13;
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = spread * Math.sqrt(Fx.random.nextDouble());
			float tone = (float) Fx.range(0.17, 0.3);
			DisasterParticle p = Fx.cloud(d.x + windX * bend + Math.cos(angle) * r, h, d.z + windZ * bend + Math.sin(angle) * r)
					.velocity(windX * 0.1, 0.45 * (1.0 - u) + 0.08, windZ * 0.1).drag(0.985).size((float) (5.0 + u * 18.0 + Fx.random.nextDouble() * 4.0))
					.alpha(0.9F).life(50 + Fx.random.nextInt(50)).grow(1.3F).spin((float) Fx.range(-0.03, 0.03));
			if (erupting && u < 0.06) {
				p.color(Math.min(1.0F, tone + 0.4F), tone + 0.1F, tone * 0.6F).glow();
			} else {
				p.color(tone, tone * 0.95F, tone * 0.9F);
			}
			p.spawn();
		}
		// The umbrella, spreading downwind at the top.
		int umbrella = Fx.round(budget * 0.08);
		for (int i = 0; i < umbrella; i++) {
			double along = column * 0.3 + Fx.random.nextDouble() * column * 0.9;
			double across = Fx.random.nextGaussian() * column * 0.25;
			double ux = d.x + windX * along - windZ * across;
			double uz = d.z + windZ * along + windX * across;
			Fx.cloud(ux, base + column * Fx.range(0.92, 1.05), uz).velocity(windX * 0.15, 0.0, windZ * 0.15).drag(0.995)
					.size((float) Fx.range(26.0, 42.0)).color(0.3F, 0.29F, 0.28F).alpha(0.72F).life(200 + Fx.random.nextInt(100)).fade(0.2F, 0.4F).spawn();
		}
		if (!erupting) {
			return;
		}
		// The lava fountain, and its glow on the underside of the column.
		int lava = (int) (budget * 0.12);
		for (int i = 0; i < lava; i++) {
			Fx.spark(d.x + Fx.range(-crater * 0.5, crater * 0.5), base + 1.0, d.z + Fx.range(-crater * 0.5, crater * 0.5))
					.velocity(Fx.range(-0.3, 0.3), Fx.range(0.9, 1.9), Fx.range(-0.3, 0.3)).fall(0.05).drag(0.99).size((float) Fx.range(0.5, 1.1))
					.color(1.0F, (float) Fx.range(0.4, 0.6), 0.1F).glow().alpha(1.0F).life(30 + Fx.random.nextInt(25)).fade(0.0F, 0.3F).spawn();
		}
		int glow = Fx.round(budget * 0.03);
		for (int i = 0; i < glow; i++) {
			Fx.cloud(d.x + Fx.range(-crater, crater), base + Fx.range(2.0, 10.0), d.z + Fx.range(-crater, crater)).velocity(0.0, Fx.range(0.1, 0.3), 0.0)
					.size((float) Fx.range(8.0, 14.0)).color(1.0F, 0.42F, 0.12F).glow().alpha(0.35F).life(20 + Fx.random.nextInt(15)).fade(0.2F, 0.5F)
					.spawn();
		}
		if (Fx.distance(d.x, base, d.z) < 48.0) {
			int drops = Fx.round(3 * Fx.density);
			for (int i = 0; i < drops; i++) {
				Fx.level.addParticle(ParticleTypes.LAVA, d.x + Fx.range(-crater, crater), base + 0.5, d.z + Fx.range(-crater, crater), 0.0, 0.0, 0.0);
			}
		}
	}

	/**
	 * The blast's own cloud, in its first seconds: a dark wall of ash and shattered rock bursting out of the mountain
	 * and rolling out over the land in every direction, faster than anyone can run, until it spends itself.
	 */
	private static void surge(ClientDisaster d, int budget, double base, double crater, double since, int vei) {
		double fade = 1.0 - since / 100.0;
		int count = Fx.round(budget * 0.25 * fade);
		double front = Math.min(40.0 + 16.0 * vei, crater + since * 1.6);
		for (int i = 0; i < count; i++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double out = front * Fx.range(0.55, 1.0);
			double px = d.x + Math.cos(angle) * out;
			double pz = d.z + Math.sin(angle) * out;
			double py = ground(px, pz, base - out * 0.3);
			float tone = (float) Fx.range(0.26, 0.4);
			Fx.cloud(px, py + Fx.range(0.0, 8.0), pz).velocity(Math.cos(angle) * 0.5, Fx.range(0.02, 0.12), Math.sin(angle) * 0.5).drag(0.95)
					.size((float) Fx.range(8.0, 15.0)).color(tone, tone * 0.93F, tone * 0.85F).alpha(0.88F).life(70 + Fx.random.nextInt(50)).grow(1.8F)
					.fade(0.05F, 0.5F).spawn();
		}
	}

	/** Bombs just thrown, as the server tells of them: followed from the vent, caught up to where each is by now. */
	private static void news(ClientDisaster d, Volcano volcano, double base, int at, int count) {
		for (int k = 0; k < count; k++) {
			int i = at + k * Eruption.BOMB_FLOATS;
			int number = (int) d.extra(i);
			if (number <= volcano.lastBomb) {
				continue;
			}
			volcano.lastBomb = number;
			double vx = d.extra(i + 2);
			double vy = d.extra(i + 3);
			double vz = d.extra(i + 4);
			if (!Double.isFinite(vx + vy + vz) || Math.abs(vx) + Math.abs(vy) + Math.abs(vz) > 30.0) {
				continue;
			}
			if (volcano.bombs.size() >= MAX_BOMBS) {
				volcano.bombs.remove(0);
			}
			Bomb bomb = new Bomb(d.x, base + 2.0, d.z, vx, vy, vz, Math.max(0.2F, Math.min(4.0F, finite(d.extra(i + 5), 1.0F))));
			int behind = Math.max(0, Math.min(Eruption.BOMB_NEWS + 4, (int) d.extra(i + 1)));
			for (int t = 0; t < behind; t++) {
				bomb.move();
			}
			volcano.bombs.add(bomb);
		}
	}

	/** The bombs in flight: a glowing clot of lava trailing smoke; gone once it lands, or flies off the land this client has. */
	private static void bombs(Volcano volcano) {
		for (Iterator<Bomb> it = volcano.bombs.iterator(); it.hasNext(); ) {
			Bomb bomb = it.next();
			bomb.move();
			int bx = (int) Math.floor(bomb.x);
			int by = (int) Math.floor(bomb.y);
			int bz = (int) Math.floor(bomb.z);
			// Over land this client hasn't loaded the height comes back as the bottom of the world: let it go there.
			if (bomb.age > 400 || by < Fx.level.getMinY() || Fx.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) <= Fx.level.getMinY()) {
				it.remove();
				continue;
			}
			BlockState state = Fx.level.getBlockState(new BlockPos(bx, by, bz));
			if (bomb.vy < 0.0 && !state.isAir() && !state.canBeReplaced()) {
				it.remove();
				// A fast one has gone in deeper than the face it struck: back out to it.
				int surface = by + 1;
				for (int i = 0; i < 4; i++) {
					BlockState above = Fx.level.getBlockState(new BlockPos(bx, surface, bz));
					if (above.isAir() || above.canBeReplaced()) {
						break;
					}
					surface++;
				}
				land(bomb, surface);
				continue;
			}
			double distance = Fx.distance(bomb.x, bomb.y, bomb.z);
			int count = Fx.budget(2, distance, 900.0);
			if (count >= 1) {
				Fx.spark(bomb.x, bomb.y, bomb.z).size(Fx.apparent(0.6F + bomb.size * 0.6F, distance, 0.004)).color(1.0F, 0.55F, 0.15F).glow().alpha(1.0F)
						.life(3).fade(0.0F, 0.5F).spawn();
			}
			if (count >= 2 && bomb.age % 2 == 0) {
				Fx.cloud(bomb.x - bomb.vx * 0.5, bomb.y - bomb.vy * 0.5, bomb.z - bomb.vz * 0.5).size(1.0F + bomb.size).gray((float) Fx.range(0.3, 0.42))
						.alpha(0.55F).life(40 + Fx.random.nextInt(30)).grow(2.0F).fade(0.05F, 0.6F).spawn();
			}
		}
	}

	/** Where a bomb comes down: a flash, a spray of embers and a puff of dust (its bang is the server's, where anyone is near to hear it). */
	private static void land(Bomb bomb, double y) {
		double distance = Fx.distance(bomb.x, y, bomb.z);
		int count = Fx.budget(10, distance, 500.0);
		if (count <= 0) {
			return;
		}
		Fx.spark(bomb.x, y + 0.3, bomb.z).size(1.5F + bomb.size * 1.5F).color(1.0F, 0.8F, 0.5F).glow().alpha(1.0F).life(5).fade(0.0F, 0.8F).spawn();
		for (int i = 1; i < count; i++) {
			if (i % 2 == 0) {
				Fx.spark(bomb.x, y + 0.2, bomb.z).velocity(Fx.range(-0.35, 0.35), Fx.range(0.2, 0.6), Fx.range(-0.35, 0.35)).fall(0.04).drag(0.95)
						.size((float) Fx.range(0.25, 0.5)).color(1.0F, (float) Fx.range(0.45, 0.7), 0.15F).glow().alpha(1.0F).life(15 + Fx.random.nextInt(15))
						.spawn();
			} else {
				Fx.cloud(bomb.x, y, bomb.z).velocity(Fx.range(-0.1, 0.1), Fx.range(0.05, 0.2), Fx.range(-0.1, 0.1)).size((float) Fx.range(1.0, 2.5 + bomb.size))
						.gray(0.35F).alpha(0.7F).life(60).grow(1.5F).spawn();
			}
		}
		if (distance < 32.0) {
			for (int i = 0; i < 2; i++) {
				Fx.level.addParticle(ParticleTypes.LAVA, bomb.x, y, bomb.z, 0.0, 0.0, 0.0);
			}
		}
	}

	/** The pyroclastic flows, their share of the budget split between them, and the roar of the nearest. */
	private static void flows(ClientDisaster d, int count) {
		if (count <= 0) {
			return;
		}
		int share = Math.max(8, 36 / count);
		double nearest = Double.MAX_VALUE;
		double nx = d.x;
		double ny = d.y;
		double nz = d.z;
		for (int k = 0; k < count; k++) {
			int i = Eruption.FLOWS_AT + k * Eruption.FLOW_FLOATS;
			double fx = d.x + finite(d.extra(i), 0.0F);
			double fy = finite(d.extra(i + 1), (float) d.y);
			double fz = d.z + finite(d.extra(i + 2), 0.0F);
			double ux = finite(d.extra(i + 3), 0.0F);
			double uz = finite(d.extra(i + 4), 0.0F);
			double length = Math.sqrt(ux * ux + uz * uz);
			if (length < 0.01) {
				// Heading nowhere: away from the vent, then.
				ux = fx - d.x;
				uz = fz - d.z;
				length = Math.max(1.0, Math.sqrt(ux * ux + uz * uz));
			}
			ux /= length;
			uz /= length;
			double width = Math.max(4.0, Math.min(60.0, finite(d.extra(i + 5), 16.0F)));
			double gone = Math.max(0.0, Math.min(1.0, finite(d.extra(i + 6), 0.0F)));
			double distance = Fx.horizontal(fx, fz);
			flow(share, fx, fy, fz, ux, uz, width, gone, distance);
			if (distance < nearest) {
				nearest = distance;
				nx = fx;
				ny = fy;
				nz = fz;
			}
		}
		DisasterAudio.loop("pyroclastic" + d.id, DisasterSounds.AVALANCHE_ROAR, true, (float) Math.max(0.0, 1.0 - nearest / 500.0), nx, ny + 2.0, nz);
		if (nearest < 90.0) {
			CameraShake.shake(0.05 * (1.0 - nearest / 90.0));
		}
	}

	/**
	 * A pyroclastic flow: an avalanche of searing ash and gas racing down the mountain, hugging the ground. Its front is
	 * a rolling wall of grey cloud as wide as the flow, dull red at its foot while it is fresh; behind it the ash it
	 * sheds lifts off in great billows. Inside it there is nothing to be seen.
	 */
	private static void flow(int base, double fx, double fy, double fz, double ux, double uz, double width, double gone, double distance) {
		int count = Fx.budget(base, distance, 1400.0);
		double half = width / 2.0;
		for (int i = 0; i < count; i++) {
			int kind = Fx.random.nextInt(10);
			if (kind < 6 || kind == 9 && gone >= 0.5) {
				double back = Math.pow(Fx.random.nextDouble(), 2.0) * 14.0;
				double side = (Fx.random.nextDouble() * 2.0 - 1.0) * half;
				double x = fx - ux * back - uz * side;
				double z = fz - uz * back + ux * side;
				float tone = (float) Fx.range(0.38, 0.5);
				Fx.cloud(x, ground(x, z, fy) + Fx.range(0.0, 4.0), z).velocity(ux * Fx.range(0.4, 0.9), Fx.range(0.0, 0.05), uz * Fx.range(0.4, 0.9))
						.drag(0.96).size((float) Fx.range(5.0, 10.0)).color(tone, tone * 0.95F, tone * 0.88F).alpha(0.94F).life(50 + Fx.random.nextInt(50))
						.grow(1.8F).fade(0.06F, 0.5F).spin((float) Fx.range(-0.04, 0.04)).spawn();
			} else if (kind < 9) {
				double back = 6.0 + Fx.random.nextDouble() * 40.0;
				double side = Fx.random.nextGaussian() * half * 0.5;
				double x = fx - ux * back - uz * side;
				double z = fz - uz * back + ux * side;
				float tone = (float) Fx.range(0.42, 0.56);
				Fx.cloud(x, ground(x, z, fy) + Fx.range(3.0, 12.0), z).velocity(ux * 0.1, Fx.range(0.08, 0.2), uz * 0.1).drag(0.98)
						.size((float) Fx.range(8.0, 14.0)).color(tone, tone * 0.96F, tone * 0.9F).alpha(0.8F).life(120 + Fx.random.nextInt(80)).grow(2.4F)
						.fade(0.1F, 0.5F).spawn();
			} else {
				double side = (Fx.random.nextDouble() * 2.0 - 1.0) * half * 0.8;
				double x = fx - uz * side;
				double z = fz + ux * side;
				Fx.cloud(x, ground(x, z, fy) + Fx.range(0.0, 1.5), z).velocity(ux * 0.6, 0.0, uz * 0.6).drag(0.95).size((float) Fx.range(3.0, 6.0))
						.color(1.0F, 0.4F, 0.12F).glow().alpha(0.5F).life(15 + Fx.random.nextInt(15)).fade(0.1F, 0.6F).spawn();
			}
		}
		if (distance < half + 6.0) {
			DisasterClient.fog(0.95F, 0.4F, 0.37F, 0.33F, 6.0F);
		}
	}

	/** Under the ash cloud: ash coming down thick and grey, and the daylight going. */
	private static void ash(ClientDisaster d, int phase, double windX, double windZ, double ashReach) {
		if (phase == 0) {
			return;
		}
		double ex = Fx.eye.x - d.x;
		double ez = Fx.eye.z - d.z;
		double along = (ex * windX + ez * windZ) / ashReach;
		double across = Math.abs(-ex * windZ + ez * windX) / ashReach;
		if (along < -0.15 || along > 1.0 || across > 0.15 + Math.max(0.0, along) * 0.35) {
			return;
		}
		double weight = 1.0 - Math.sqrt(ex * ex + ez * ez) / ashReach;
		if (weight <= 0.0) {
			return;
		}
		float strength = Math.max(0.0F, Math.min(1.0F, d.strength));
		float thick = (float) (Math.min(1.0, 0.45 + weight) * strength * 0.9);
		float seeing = (float) (24.0 + 100.0 * (1.0 - weight));
		// Under a roof only a haze of it gets in, and nothing falls.
		if (ground(Fx.eye.x, Fx.eye.z, Fx.eye.y - 2.0) > Fx.eye.y) {
			DisasterClient.fog(thick * 0.35F, 0.3F, 0.28F, 0.26F, seeing * 3.0F);
			return;
		}
		DisasterClient.fog(thick, 0.3F, 0.28F, 0.26F, seeing);
		// Only where the sky is open above: not under the eaves.
		int flakes = Fx.round(14 * weight * strength * Fx.density);
		for (int i = 0; i < flakes; i++) {
			double px = Fx.eye.x + Fx.range(-12.0, 12.0);
			double py = Fx.eye.y + Fx.range(-1.0, 9.0);
			double pz = Fx.eye.z + Fx.range(-12.0, 12.0);
			if (py < ground(px, pz, Fx.eye.y - 2.0)) {
				continue;
			}
			Fx.flake(px, py, pz).velocity(windX * 0.05, -Fx.range(0.03, 0.08), windZ * 0.05).size((float) Fx.range(0.08, 0.16))
					.gray((float) Fx.range(0.25, 0.45)).alpha(0.9F).life(60 + Fx.random.nextInt(40)).spawn();
		}
		int specks = Fx.round(8 * weight * strength * Fx.density);
		for (int i = 0; i < specks; i++) {
			Fx.level.addParticle(Fx.random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH, Fx.eye.x + Fx.range(-14.0, 14.0),
					Fx.eye.y + Fx.range(-2.0, 10.0), Fx.eye.z + Fx.range(-14.0, 14.0), 0.0, 0.0, 0.0);
		}
	}

	/** Close to the mountain while it erupts, the ground never stops trembling. */
	private static void rumble(ClientDisaster d, int phase, double distance, int vei) {
		if (phase == 0) {
			return;
		}
		double reach = 80.0 + 25.0 * vei;
		if (distance < reach) {
			double near = 1.0 - distance / reach;
			CameraShake.shake(near * near * (0.02 + vei * 0.006) * Math.max(0.0F, Math.min(1.0F, d.strength)));
		}
	}

	private static void sound(ClientDisaster d, int phase, double base, double nearing) {
		float volume = switch (phase) {
			case 0 -> (float) (0.08 + 0.25 * nearing);
			case 1 -> 1.0F;
			default -> d.strength * 0.6F;
		};
		DisasterAudio.loop("volcano" + d.id, DisasterSounds.ERUPTION_ROAR, true, volume, d.x, base + 4.0, d.z);
	}
}
