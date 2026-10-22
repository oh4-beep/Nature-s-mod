package com.naturesmod.end;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A vent in the End stone that breathes a steady wind straight up. Step into the column of air
 * over it and it carries you up into the sky, to the isles that float there; sneak to drop out.
 * The lifting is done by {@link EndOverhaul}; the block only shows the wind.
 */
public class UpdraftVentBlock extends Block {
	public UpdraftVentBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		for (int i = 0; i < 3; i++) {
			double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
			double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
			level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.CLOUD, x, pos.getY() + 1.05, z,
					0.0, 0.35 + random.nextDouble() * 0.35, 0.0);
		}
	}
}
