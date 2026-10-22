package com.naturesmod.end;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The springy, glowing cap of a bounce bloom. Whatever lands on it comes to no harm, and whatever
 * steps onto it is flung high into the air, again and again, unless it is sneaking.
 */
public class BounceBloomBlock extends Block {
	/** Enough to throw a player some seven blocks up. */
	private static final double LAUNCH = 1.05;

	public BounceBloomBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		if (entity.isSuppressingBounce()) {
			super.fallOn(level, state, pos, entity, fallDistance);
		} else {
			entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
		}
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (entity instanceof LivingEntity && !entity.isSteppingCarefully() && !entity.isSuppressingBounce()) {
			Vec3 movement = entity.getDeltaMovement();
			if (movement.y < LAUNCH * 0.5) {
				entity.setDeltaMovement(movement.x * 1.1, LAUNCH, movement.z * 1.1);
				entity.resetFallDistance();
				if (level instanceof ServerLevel server) {
					server.playSound(null, pos, SoundEvents.SLIME_JUMP, SoundSource.BLOCKS, 0.8F, 1.4F);
					server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.05);
				}
			}
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
					0.0, 0.02, 0.0);
		}
	}
}
