package com.naturesmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * A crack in the rock breathing steam: a plume of white vapour in the open air, and a stream of
 * bubbles under the water of a hot spring.
 */
public class SteamVentBlock extends Block {
	public SteamVentBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		BlockPos above = pos.above();
		FluidState fluid = level.getFluidState(above);
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		if (!fluid.isEmpty()) {
			for (int i = 0; i < 2; i++) {
				level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, x + (random.nextDouble() - 0.5) * 0.6, above.getY() + 0.1,
						z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.04, 0.0);
			}
			return;
		}
		if (!level.getBlockState(above).isAir()) {
			return;
		}
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.CLOUD, x + (random.nextDouble() - 0.5) * 0.4, above.getY() + 0.1, z + (random.nextDouble() - 0.5) * 0.4,
					(random.nextDouble() - 0.5) * 0.02, 0.08 + random.nextDouble() * 0.06, (random.nextDouble() - 0.5) * 0.02);
		}
		if (random.nextInt(12) == 0) {
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, above.getY() + 0.2, z, 0.0, 0.05, 0.0);
		}
	}
}
