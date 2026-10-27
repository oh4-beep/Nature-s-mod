package com.naturesmod.client.disaster;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.naturesmod.disaster.DisasterSounds;
import com.naturesmod.disaster.Earthquake;
import com.naturesmod.disaster.Seismic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An earthquake as the player feels it: worked out here from where the player stands (see
 * {@link Seismic}): a jolt as the first waves arrive, a heave as the second do, then the shaking (now
 * and then a lurch), the deep rumble, and dust trickling from the ceilings and puffing up off the
 * ground. Where the ground has just torn open (the fissure, a crack, a pit falling in) dust boils up
 * out of it, clods are flung up and the crash of it giving way is heard.
 */
final class QuakeFx {
	/** How far each quake's waves have got to this player: 1 once the first have arrived, 2 once the second have. */
	private static final Map<Integer, Integer> ARRIVED = new HashMap<>();
	/** The openings in the ground already heard giving way. */
	private static final Set<Long> HEARD = new HashSet<>();

	private QuakeFx() {
	}

	static void tick(ClientDisaster d) {
		double m = d.extra(0);
		double distance = Fx.horizontal(d.x, d.z);
		double a = Seismic.shaking(m, distance, d.age);
		CameraShake.shake(a);
		arrivals(d, m, distance);
		if (a > 0.006) {
			DisasterAudio.loop("quake" + d.id, DisasterSounds.QUAKE_RUMBLE, false, (float) Math.min(1.0, a * 4.0), 0, 0, 0);
		}
		if (a > 0.3 && Fx.random.nextDouble() < a * 0.03) {
			// The ground lurching under you.
			CameraShake.jolt(Math.min(0.9, a * 0.5));
		}
		if (a > 0.04) {
			dust(a);
		}
		openings(d, m);
		if (d.age > d.duration - 5) {
			ARRIVED.remove(d.id);
		}
		if (ARRIVED.size() > 64) {
			ARRIVED.clear();
		}
	}

	/** The first waves arriving with a jolt, the second with a heave (in a strong quake, enough to rock you). */
	private static void arrivals(ClientDisaster d, double m, double distance) {
		int stage = ARRIVED.getOrDefault(d.id, 0);
		double peak = Seismic.pga(m, distance);
		if (stage == 0 && d.age >= distance / Seismic.P_SPEED) {
			ARRIVED.put(d.id, 1);
			if (peak > 0.01) {
				DisasterAudio.around(DisasterSounds.QUAKE_JOLT, (float) Math.min(1.0, peak * 5.0), (float) Fx.range(0.85, 1.05));
			}
		} else if (stage == 1 && d.age >= distance / Seismic.S_SPEED) {
			ARRIVED.put(d.id, 2);
			if (peak > 0.05) {
				CameraShake.jolt(Math.min(0.8, peak * 0.6));
				DisasterAudio.around(DisasterSounds.QUAKE_JOLT, (float) Math.min(1.0, peak * 3.0), (float) Fx.range(0.6, 0.75));
			}
		}
	}

	/** Dust and grit shaken down from overhead, and up off the ground. */
	private static void dust(double a) {
		int count = Fx.round(Math.min(12.0, a * 30.0) * Fx.density);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < count && Fx.spawned < Fx.cap; i++) {
			double x = Fx.eye.x + Fx.range(-8.0, 8.0);
			double z = Fx.eye.z + Fx.range(-8.0, 8.0);
			int y = (int) Math.floor(Fx.eye.y) + Fx.random.nextInt(8);
			BlockState above = Fx.level.getBlockState(pos.set(x, y + 1, z));
			if (!above.isAir() && Fx.level.getBlockState(pos.set(x, y, z)).isAir()) {
				Fx.level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, above), x, y + 0.95, z, 0.0, 0.0, 0.0);
				Fx.spawned++;
			}
		}
		int puffs = Fx.round(Math.min(3.0, a * 2.5) * Fx.density);
		for (int i = 0; i < puffs; i++) {
			double x = Fx.eye.x + Fx.range(-12.0, 12.0);
			double z = Fx.eye.z + Fx.range(-12.0, 12.0);
			float tone = (float) Fx.range(0.56, 0.66);
			Fx.cloud(x, Fx.eye.y - 1.4, z).velocity(Fx.range(-0.02, 0.02), Fx.range(0.01, 0.05), Fx.range(-0.02, 0.02))
					.size((float) Fx.range(0.9, 2.2)).color(tone, tone * 0.9F, tone * 0.78F).alpha(0.4F).life(40 + Fx.random.nextInt(20)).grow(1.3F)
					.spawn();
		}
	}

	/** Where the ground has just opened: dust boiling up, clods flung up close by, and the crash of it heard once. */
	private static void openings(ClientDisaster d, double m) {
		int count = Math.max(0, Math.min(Earthquake.MAX_OPENINGS, (int) d.extra(Earthquake.OPENINGS_AT)));
		for (int i = 0; i < count; i++) {
			int at = Earthquake.OPENINGS_AT + 1 + i * 6;
			double ox = d.extra(at);
			double oy = d.extra(at + 1);
			double oz = d.extra(at + 2);
			double size = Math.max(1.0, d.extra(at + 3));
			int kind = (int) d.extra(at + 4);
			double since = Math.max(0.0, d.extra(at + 5));
			double fresh = 1.0 - since / Earthquake.OPENING_TICKS;
			if (fresh <= 0.0 || kind <= 0) {
				continue;
			}
			double distance = Fx.distance(ox, oy, oz);
			long key = BlockPos.asLong((int) Math.floor(ox), d.id & 1023, (int) Math.floor(oz));
			if (since < 20.0 && HEARD.add(key)) {
				if (kind == Earthquake.OPEN_PIT) {
					DisasterAudio.distant(DisasterSounds.SINKHOLE_COLLAPSE, ox, oy, oz, (float) Math.min(1.0, 0.5 + size * 0.1),
							(float) Fx.range(0.85, 1.1));
				} else if (distance < 24.0) {
					DisasterAudio.distant(DisasterSounds.QUAKE_JOLT, ox, oy, oz, 1.0F, (float) Fx.range(0.55, 0.75));
				}
			}
			int puffs = Fx.round(Fx.budget(kind == Earthquake.OPEN_PIT ? 6 : kind == Earthquake.OPEN_SINK ? 2 : 3, distance, 320.0) * fresh);
			for (int k = 0; k < puffs; k++) {
				float tone = (float) Fx.range(0.48, 0.6);
				Fx.cloud(ox + Fx.range(-size, size), oy + Fx.range(-1.0, 1.0), oz + Fx.range(-size, size))
						.velocity(Fx.range(-0.03, 0.03), Fx.range(0.04, 0.12), Fx.range(-0.03, 0.03)).drag(0.97)
						.size((float) (Fx.range(1.8, 3.4) + size * 0.35)).color(tone, tone * 0.92F, tone * 0.8F).alpha(0.6F)
						.life(70 + Fx.random.nextInt(50)).grow(1.6F).spin((float) Fx.range(-0.02, 0.02)).spawn();
			}
			if (since < 30.0 && kind != Earthquake.OPEN_SINK) {
				int clods = Fx.budget(3, distance, 64.0);
				for (int k = 0; k < clods; k++) {
					float tone = (float) Fx.range(0.3, 0.45);
					Fx.debris(ox + Fx.range(-size, size) * 0.6, oy + 0.3, oz + Fx.range(-size, size) * 0.6)
							.velocity(Fx.range(-0.15, 0.15), Fx.range(0.2, 0.45), Fx.range(-0.15, 0.15)).fall(0.04).drag(0.98)
							.size((float) Fx.range(0.15, 0.35)).color(tone, tone * 0.9F, tone * 0.78F).alpha(1.0F).life(30 + Fx.random.nextInt(20))
							.spin((float) Fx.range(-0.4, 0.4)).spawn();
				}
			}
			if (m >= 7.0 && distance < 24.0 + size * 3.0) {
				// Close by a great opening the air is thick with its dust.
				DisasterClient.fog((float) (0.3 * fresh), 0.6F, 0.56F, 0.5F, 60.0F);
			}
		}
		if (HEARD.size() > 512) {
			HEARD.clear();
		}
	}
}
