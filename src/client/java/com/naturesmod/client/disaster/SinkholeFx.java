package com.naturesmod.client.disaster;

import java.util.HashSet;
import java.util.Set;

import com.naturesmod.disaster.DisasterSounds;
import com.naturesmod.disaster.Sinkhole;

/**
 * Draws a sinkhole. While the ground gives warning, a deep rumble and now and then the groan of the
 * rock giving way below, the view trembling close by, and dust and grit puffing up from the ring of
 * cracks round it. Then it falls in: the crash of it and a jolt, a cloud of dust bursting up out of
 * the hole as it widens, clods and grit pouring in over its edge, and close by the air thick with
 * dust. Afterward the dust hangs in the pit and drifts off, and bits of the walls trickle down.
 * Everything is worked out from the few numbers the server sends (see Sinkhole.extra()): nothing
 * is looked up block by block.
 */
final class SinkholeFx {
	/** The sinkholes already heard falling in: each crash is heard once. */
	private static final Set<Integer> HEARD = new HashSet<>();

	private SinkholeFx() {
	}

	/** How far its edge is from the middle toward `angle`, shaped as the server shapes it. */
	private static double edge(ClientDisaster d, double angle) {
		double radius = Math.max(1.0, d.extra(1));
		return radius * (1.0 + d.extra(8) * Math.sin(angle * 3.0 + d.extra(9)) + d.extra(10) * Math.sin(angle * 5.0 + d.extra(11)));
	}

	static void tick(ClientDisaster d) {
		int phase = Math.round(d.extra(0));
		double radius = Math.max(1.0, d.extra(1));
		double depth = Math.max(2.0, d.extra(2));
		double rim = d.extra(3);
		int grade = Math.max(1, Math.min(5, Math.round(d.extra(6))));
		double distance = Fx.distance(d.x, rim, d.z);
		// Heard a good way off; felt underfoot only close by.
		double heard = Math.max(0.0, 1.0 - distance / (radius * 4.0 + 72.0));
		double close = Math.max(0.0, 1.0 - distance / (radius * 2.5 + 28.0));
		if (phase == Sinkhole.WARNING) {
			warning(d, rim, grade, distance, heard, close);
		} else if (phase == Sinkhole.COLLAPSING) {
			collapsing(d, rim, radius, depth, grade, distance, heard, close);
		} else {
			settled(d, rim, radius, depth, grade, distance, heard, close);
		}
		if (HEARD.size() > 64) {
			HEARD.clear();
		}
	}

	/** The rumble growing, the rock groaning, and dust puffing from the cracks opening in the ring round it. */
	private static void warning(ClientDisaster d, double rim, int grade, double distance, double heard, double close) {
		double progress = Math.max(0.0, Math.min(1.0, d.extra(4)));
		// Not fallen in yet (the ids start over with each world).
		HEARD.remove(d.id);
		DisasterAudio.loop("sinkhole" + d.id, DisasterSounds.QUAKE_RUMBLE, false, (float) (heard * heard * (0.25 + 0.6 * progress)), 0, 0, 0);
		if (heard > 0.0 && Fx.random.nextDouble() < 0.025 + 0.03 * progress) {
			DisasterAudio.distant(DisasterSounds.SINKHOLE_COLLAPSE, d.x, rim - 4.0, d.z, (float) (0.3 + 0.4 * progress), (float) Fx.range(0.5, 0.65));
		}
		CameraShake.shake(close * (0.012 + 0.04 * progress * (0.6 + grade * 0.1)));
		double out = grade >= 3 ? 2.0 : 1.0;
		int puffs = Fx.round(Fx.budget(2 + grade, distance, 160.0) * (0.3 + 0.7 * progress));
		for (int i = 0; i < puffs; i++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = edge(d, angle) + out + Fx.range(-0.4, 0.4);
			double px = d.x + Math.cos(angle) * r;
			double pz = d.z + Math.sin(angle) * r;
			float tone = (float) Fx.range(0.5, 0.62);
			Fx.cloud(px, rim + 1.0, pz).velocity(Fx.range(-0.01, 0.01), Fx.range(0.02, 0.06), Fx.range(-0.01, 0.01)).size((float) Fx.range(0.8, 1.8))
					.color(tone, tone * 0.9F, tone * 0.78F).alpha(0.45F).life(30 + Fx.random.nextInt(25)).grow(1.2F).spawn();
			if (Fx.random.nextInt(3) == 0) {
				float grit = (float) Fx.range(0.3, 0.45);
				Fx.debris(px, rim + 1.05, pz).velocity(Fx.range(-0.04, 0.04), Fx.range(0.12, 0.22), Fx.range(-0.04, 0.04)).fall(0.04)
						.size((float) Fx.range(0.08, 0.16)).color(grit, grit * 0.88F, grit * 0.72F).alpha(1.0F).life(16 + Fx.random.nextInt(10))
						.spin((float) Fx.range(-0.4, 0.4)).spawn();
			}
		}
	}

	/** The crash and the jolt of it falling in, the dust bursting up out of the widening hole, the edge pouring in. */
	private static void collapsing(ClientDisaster d, double rim, double radius, double depth, int grade, double distance, double heard,
			double close) {
		double front = Math.max(1.0, d.extra(5));
		double since = Math.max(0.0, d.extra(12));
		if (HEARD.add(d.id)) {
			DisasterAudio.distant(DisasterSounds.SINKHOLE_COLLAPSE, d.x, rim, d.z, (float) Math.min(1.0, 0.6 + grade * 0.1), (float) Fx.range(0.8, 0.95));
			CameraShake.jolt(close * Math.min(0.9, 0.25 + grade * 0.12));
		}
		DisasterAudio.loop("sinkhole" + d.id, DisasterSounds.QUAKE_RUMBLE, false, (float) Math.min(1.0, heard * heard * 1.1), 0, 0, 0);
		CameraShake.shake(close * (0.05 + grade * 0.025));
		if (heard > 0.0 && Fx.random.nextDouble() < 0.05) {
			// More of the ground going, here and there round the edge.
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = Math.min(front, edge(d, angle));
			DisasterAudio.distant(DisasterSounds.SINKHOLE_COLLAPSE, d.x + Math.cos(angle) * r, rim, d.z + Math.sin(angle) * r, 0.5F,
					(float) Fx.range(0.9, 1.15));
		}
		// The dust bursting up out of the hole: thickest as it first gives way. Few, big billows.
		double burst = since < 12.0 ? 1.6 : 1.0;
		int clouds = Fx.round(Fx.budget(5 + grade * 2, distance, 480.0) * burst);
		for (int i = 0; i < clouds; i++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = Math.min(front, edge(d, angle)) * Math.sqrt(Fx.random.nextDouble());
			double outward = Fx.range(0.0, 0.06);
			float tone = (float) Fx.range(0.46, 0.6);
			Fx.cloud(d.x + Math.cos(angle) * r, rim + Fx.range(-Math.min(depth, 8.0), 1.0), d.z + Math.sin(angle) * r)
					.velocity(Math.cos(angle) * outward, Fx.range(0.1, 0.3), Math.sin(angle) * outward).drag(0.96)
					.size((float) (Fx.range(2.4, 4.4) + radius * 0.18)).color(tone, tone * 0.92F, tone * 0.8F).alpha(0.7F)
					.life(50 + Fx.random.nextInt(40)).grow(1.6F).spin((float) Fx.range(-0.02, 0.02)).spawn();
		}
		// Clods and grit pouring in over the edge.
		int clods = Fx.budget(3 + grade, distance, 96.0);
		for (int i = 0; i < clods; i++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = Math.min(front, edge(d, angle)) + Fx.range(-0.3, 0.6);
			double in = Fx.range(0.03, 0.1);
			float tone = (float) Fx.range(0.28, 0.44);
			Fx.debris(d.x + Math.cos(angle) * r, rim + Fx.range(0.0, 1.0), d.z + Math.sin(angle) * r)
					.velocity(-Math.cos(angle) * in, Fx.range(-0.05, 0.12), -Math.sin(angle) * in).fall(0.05).drag(0.98)
					.size((float) Fx.range(0.15, 0.4)).color(tone, tone * 0.88F, tone * 0.72F).alpha(1.0F).life(30 + Fx.random.nextInt(20))
					.spin((float) Fx.range(-0.4, 0.4)).spawn();
		}
		fog(d, rim, radius, depth, 1.0);
	}

	/** The dust hanging in the pit and drifting off, the walls still trickling, the rumble dying away. */
	private static void settled(ClientDisaster d, double rim, double radius, double depth, int grade, double distance, double heard,
			double close) {
		double since = Math.max(0.0, d.extra(13));
		double fade = Math.max(0.0, 1.0 - since / 120.0);
		if (fade <= 0.0) {
			HEARD.remove(d.id);
			return;
		}
		DisasterAudio.loop("sinkhole" + d.id, DisasterSounds.QUAKE_RUMBLE, false, (float) (heard * heard * 0.6 * fade), 0, 0, 0);
		CameraShake.shake(close * 0.03 * fade);
		int clouds = Fx.round(Fx.budget(3 + grade, distance, 400.0) * fade);
		for (int i = 0; i < clouds; i++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = edge(d, angle) * Math.sqrt(Fx.random.nextDouble());
			float tone = (float) Fx.range(0.5, 0.62);
			Fx.cloud(d.x + Math.cos(angle) * r, rim - Fx.range(0.0, depth * 0.7), d.z + Math.sin(angle) * r)
					.velocity(Fx.range(-0.02, 0.02), Fx.range(0.02, 0.07), Fx.range(-0.02, 0.02)).drag(0.98)
					.size((float) (Fx.range(3.0, 5.5) + radius * 0.12)).color(tone, tone * 0.92F, tone * 0.82F).alpha(0.45F)
					.life(70 + Fx.random.nextInt(40)).grow(1.3F).spin((float) Fx.range(-0.015, 0.015)).spawn();
		}
		int grit = Fx.round(Fx.budget(2, distance, 64.0) * fade);
		for (int i = 0; i < grit; i++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double r = edge(d, angle) - 0.2;
			float tone = (float) Fx.range(0.3, 0.45);
			Fx.debris(d.x + Math.cos(angle) * r, rim + Fx.range(-1.5, 0.5), d.z + Math.sin(angle) * r)
					.velocity(-Math.cos(angle) * 0.02, 0.0, -Math.sin(angle) * 0.02).fall(0.04).size((float) Fx.range(0.08, 0.2))
					.color(tone, tone * 0.88F, tone * 0.72F).alpha(1.0F).life(30 + Fx.random.nextInt(20)).spawn();
		}
		fog(d, rim, radius, depth, fade * 0.7);
	}

	/** Over the pit in its dust cloud (or down in it), the air is thick with dust. */
	private static void fog(ClientDisaster d, double rim, double radius, double depth, double thick) {
		double reach = radius * 1.6 + 4.0;
		double h = Fx.horizontal(d.x, d.z);
		double above = Fx.eye.y - rim;
		if (h < reach && above < radius + 10.0 && above > -depth - 4.0) {
			DisasterClient.fog((float) (0.6 * thick * (1.0 - h / reach)), 0.56F, 0.51F, 0.44F, 24.0F);
		}
	}
}
