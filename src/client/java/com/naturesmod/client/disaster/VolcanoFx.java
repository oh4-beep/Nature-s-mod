package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterSounds;

import net.minecraft.core.particles.ParticleTypes;

/**
 * Draws an eruption: the steam of its unrest; then the column, a jet of dark ash roaring out of the
 * vent and boiling up hundreds of blocks, bending downwind and spreading at the top into an umbrella
 * cloud; lava fountaining from the crater; ash falling under the cloud, where the air goes dark;
 * pyroclastic flows billowing down the mountain; at last the steam again as it wanes.
 */
final class VolcanoFx {
	private VolcanoFx() {
	}

	static void tick(ClientDisaster d) {
		int phase = (int) d.extra(1);
		double ventY = d.extra(2);
		double column = d.extra(3);
		double windX = d.extra(4);
		double windZ = d.extra(5);
		double ashReach = d.extra(6);
		double crater = d.extra(7);
		double distance = Fx.horizontal(d.x, d.z);
		sound(d, phase);
		ash(d, phase, windX, windZ, ashReach);
		int budget = Fx.budget(90, distance, 1600.0);
		if (budget <= 0) {
			return;
		}
		if (phase == 0 || phase == 2) {
			// Steam, and a little ash as it wanes.
			int steam = Math.max(1, budget / 12);
			for (int i = 0; i < steam; i++) {
				Fx.cloud(d.x + Fx.range(-crater, crater), ventY + 1.0, d.z + Fx.range(-crater, crater))
						.velocity(windX * 0.05, Fx.range(0.1, 0.25), windZ * 0.05).drag(0.99).size((float) Fx.range(3.0, 6.0))
						.gray(phase == 0 ? 0.92F : 0.6F).alpha(0.55F).life(90 + Fx.random.nextInt(60)).grow(1.5F).spawn();
			}
		}
		if (phase == 1 || phase == 2 && column > 1.0) {
			column(d, budget, ventY, column, windX, windZ, crater);
		}
		int flows = (int) d.extra(8);
		for (int i = 0; i < flows; i++) {
			flow(d.extra(9 + i * 3), d.extra(10 + i * 3), d.extra(11 + i * 3), windX, windZ);
		}
	}

	private static void column(ClientDisaster d, int budget, double ventY, double column, double windX, double windZ, double crater) {
		// The jet and the convecting column above it, bending with the wind as it climbs.
		int jet = (int) (budget * 0.45);
		for (int i = 0; i < jet; i++) {
			double u = Math.pow(Fx.random.nextDouble(), 1.4);
			double h = ventY + u * column;
			double bend = u * u * column * 0.25;
			double spread = crater * 0.8 + u * column * 0.12;
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = spread * Math.sqrt(Fx.random.nextDouble());
			float tone = (float) Fx.range(0.2, 0.32);
			Fx.cloud(d.x + windX * bend + Math.cos(angle) * r, h, d.z + windZ * bend + Math.sin(angle) * r)
					.velocity(windX * 0.08, 0.35 * (1.0 - u) + 0.08, windZ * 0.08).drag(0.985).size((float) (4.0 + u * 14.0 + Fx.random.nextDouble() * 3.0))
					.color(tone, tone * 0.95F, tone * 0.9F).alpha(0.88F).life(60 + Fx.random.nextInt(60)).grow(1.2F).spin((float) Fx.range(-0.03, 0.03))
					.spawn();
		}
		// The umbrella, spreading downwind at the top.
		int umbrella = (int) (budget * 0.2);
		for (int i = 0; i < umbrella; i++) {
			double along = column * 0.3 + Fx.random.nextDouble() * column * 0.9;
			double across = Fx.random.nextGaussian() * column * 0.25;
			double ux = d.x + windX * along - windZ * across;
			double uz = d.z + windZ * along + windX * across;
			Fx.cloud(ux, ventY + column * Fx.range(0.92, 1.05), uz).velocity(windX * 0.15, 0.0, windZ * 0.15).drag(0.995)
					.size((float) Fx.range(18.0, 34.0)).color(0.3F, 0.29F, 0.28F).alpha(0.72F).life(200 + Fx.random.nextInt(120)).fade(0.2F, 0.4F).spawn();
		}
		// The lava fountain.
		int lava = (int) (budget * 0.15);
		for (int i = 0; i < lava; i++) {
			Fx.spark(d.x + Fx.range(-crater * 0.5, crater * 0.5), ventY + 1.0, d.z + Fx.range(-crater * 0.5, crater * 0.5))
					.velocity(Fx.range(-0.25, 0.25), Fx.range(0.8, 1.7), Fx.range(-0.25, 0.25)).fall(0.05).drag(0.99).size((float) Fx.range(0.4, 0.9))
					.color(1.0F, (float) Fx.range(0.4, 0.6), 0.1F).glow().alpha(1.0F).life(30 + Fx.random.nextInt(25)).fade(0.0F, 0.3F).spawn();
		}
		if (Fx.distance(d.x, ventY, d.z) < 40.0) {
			for (int i = 0; i < 4; i++) {
				Fx.level.addParticle(ParticleTypes.LAVA, d.x + Fx.range(-crater, crater), ventY + 0.5, d.z + Fx.range(-crater, crater), 0.0, 0.0, 0.0);
			}
		}
	}

	/** A pyroclastic flow: a wall of searing grey cloud boiling along the ground. */
	private static void flow(double fx, double fy, double fz, double windX, double windZ) {
		int count = Fx.budget(8, Fx.horizontal(fx, fz), 900.0);
		for (int i = 0; i < count; i++) {
			float tone = (float) Fx.range(0.38, 0.48);
			Fx.cloud(fx + Fx.range(-10.0, 10.0), fy + Fx.range(0.0, 6.0), fz + Fx.range(-10.0, 10.0))
					.velocity(windX * 0.05 + Fx.range(-0.1, 0.1), Fx.range(0.1, 0.3), windZ * 0.05 + Fx.range(-0.1, 0.1)).drag(0.97)
					.size((float) Fx.range(8.0, 16.0)).color(tone, tone * 0.95F, tone * 0.88F).alpha(0.9F).life(100 + Fx.random.nextInt(80)).grow(1.5F)
					.spawn();
		}
		if (Fx.horizontal(fx, fz) < 30.0) {
			DisasterClient.fog(0.95F, 0.4F, 0.37F, 0.33F, 6.0F);
		}
	}

	/** Under the ash cloud: ash coming down and the daylight going. */
	private static void ash(ClientDisaster d, int phase, double windX, double windZ, double ashReach) {
		if (phase == 0 || ashReach <= 0.0) {
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
		float thick = (float) (Math.min(1.0, 0.4 + weight) * d.strength * 0.8);
		DisasterClient.fog(thick, 0.33F, 0.31F, 0.29F, (float) (40.0 + 90.0 * (1.0 - weight)));
		int fall = Fx.round(12 * weight * Fx.density);
		for (int i = 0; i < fall; i++) {
			Fx.level.addParticle(Fx.random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH, Fx.eye.x + Fx.range(-14.0, 14.0),
					Fx.eye.y + Fx.range(-2.0, 10.0), Fx.eye.z + Fx.range(-14.0, 14.0), 0.0, 0.0, 0.0);
		}
	}

	private static void sound(ClientDisaster d, int phase) {
		float volume = switch (phase) {
			case 0 -> 0.15F;
			case 1 -> 1.0F;
			default -> d.strength * 0.6F;
		};
		DisasterAudio.loop("volcano" + d.id, DisasterSounds.ERUPTION_ROAR, true, volume, d.x, d.extra(2) + 4.0, d.z);
	}
}
