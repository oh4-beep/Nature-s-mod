package com.naturesmod.client.disaster;

import net.minecraft.world.phys.Vec3;

/**
 * A meteor shower's shooting stars: brief streaks high overhead, all running from the shower's
 * radiant on parallel paths (so they seem to fan out from one point of the sky), a bright head and a
 * fading trail. Best seen at night; by day only the brightest show. The fireballs that come down
 * among them are the server's (see {@link DisasterEvents}).
 */
final class SkyFx {
	private SkyFx() {
	}

	static void tick(ClientDisaster d) {
		Vec3 radiant = new Vec3(d.extra(0), d.extra(1), d.extra(2));
		if (radiant.lengthSqr() < 0.5) {
			return;
		}
		float night = Fx.level.isDarkOutside() ? 1.0F : 0.2F;
		double rate = d.intensity * 0.09 * night * d.strength * Fx.density;
		int count = Fx.round(rate);
		for (int i = 0; i < count; i++) {
			double angle = Fx.random.nextDouble() * Math.PI * 2.0;
			double distance = Fx.range(40.0, 260.0);
			Vec3 start = new Vec3(Fx.eye.x + Math.cos(angle) * distance, Fx.eye.y + Fx.range(110.0, 200.0), Fx.eye.z + Math.sin(angle) * distance);
			double length = Fx.range(30.0, 90.0);
			int ticks = 6 + Fx.random.nextInt(8);
			boolean green = Fx.random.nextInt(4) == 0;
			DisasterEvents.streak(start, radiant.scale(length / ticks), ticks, (float) Fx.range(0.5, 1.2), green);
		}
	}
}
