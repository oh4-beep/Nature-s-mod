package com.naturesmod.nether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Brimstone: sulfurous rock that smoulders underfoot, sets the careless alight now and then,
 * and feeds fires that never go out. Its fumes curl up wherever it meets the air.
 */
public class BrimstoneBlock extends Block {
	public BrimstoneBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (level instanceof ServerLevel server && !entity.isSteppingCarefully() && entity instanceof LivingEntity living && !living.fireImmune()) {
			living.hurtServer(server, level.damageSources().hotFloor(), 1.5F);
			if (server.getRandom().nextInt(40) == 0) {
				living.igniteForSeconds(2.0F);
			}
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(10) == 0 && level.getBlockState(pos.above()).isAir()) {
			level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(), 0.0, 0.03, 0.0);
		}
	}
}
