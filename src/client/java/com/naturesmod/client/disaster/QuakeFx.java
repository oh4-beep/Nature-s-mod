package com.naturesmod.client.disaster;

import java.util.HashSet;
import java.util.Set;

import com.naturesmod.disaster.DisasterSounds;
import com.naturesmod.disaster.Seismic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An earthquake as the player feels it: worked out here from where the player stands (see
 * {@link Seismic}): a jolt as the first waves arrive, then the shaking, the deep rumble, and dust
 * trickling from the ceilings and puffing up off the ground.
 */
final class QuakeFx {
	private static final Set<Integer> JOLTED = new HashSet<>();

	private QuakeFx() {
	}

	static void tick(ClientDisaster d) {
		double m = d.extra(0);
		double distance = Fx.horizontal(d.x, d.z);
		double a = Seismic.shaking(m, distance, d.age);
		CameraShake.shake(a);
		double arrival = distance / Seismic.P_SPEED;
		if (d.age >= arrival && JOLTED.add(d.id)) {
			double peak = Seismic.pga(m, distance);
			if (peak > 0.01) {
				DisasterAudio.around(DisasterSounds.QUAKE_JOLT, (float) Math.min(1.0, peak * 5.0), (float) Fx.range(0.85, 1.05));
			}
		}
		if (a > 0.006) {
			DisasterAudio.loop("quake" + d.id, DisasterSounds.QUAKE_RUMBLE, false, (float) Math.min(1.0, a * 4.0), 0, 0, 0);
		}
		if (a > 0.04) {
			dust(a);
		}
		if (d.age > d.duration - 5) {
			JOLTED.remove(d.id);
		}
	}

	/** Dust and grit shaken down from overhead, and up off the ground. */
	private static void dust(double a) {
		int count = Fx.round(Math.min(12.0, a * 30.0) * Fx.density);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < count; i++) {
			double x = Fx.eye.x + Fx.range(-8.0, 8.0);
			double z = Fx.eye.z + Fx.range(-8.0, 8.0);
			int y = (int) Math.floor(Fx.eye.y) + Fx.random.nextInt(8);
			BlockState above = Fx.level.getBlockState(pos.set(x, y + 1, z));
			if (!above.isAir() && Fx.level.getBlockState(pos.set(x, y, z)).isAir()) {
				Fx.level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, above), x, y + 0.95, z, 0.0, 0.0, 0.0);
			}
		}
		if (Fx.random.nextDouble() < a * 2.0) {
			double x = Fx.eye.x + Fx.range(-10.0, 10.0);
			double z = Fx.eye.z + Fx.range(-10.0, 10.0);
			DisasterParticle p = Fx.cloud(x, Fx.eye.y - 1.4, z);
			p.velocity(0.0, Fx.range(0.01, 0.04), 0.0).size((float) Fx.range(0.8, 1.8)).color(0.62F, 0.56F, 0.48F).alpha(0.4F).life(40).grow(1.2F)
					.spawn();
		}
	}
}
