package com.naturesmod.nether;

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
import net.minecraft.world.phys.AABB;

/**
 * A Nether geyser: a vent in the rock that rumbles and smokes, and erupts in a column of fire
 * every now and then, or a moment after something steps on it. The blast throws whatever stands
 * over it high into the air and sets it alight.
 */
public class GeyserBlock extends Block {
	private static final int HEIGHT = 7;

	public GeyserBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (level instanceof ServerLevel server && !entity.isSteppingCarefully() && !server.getBlockTicks().hasScheduledTick(pos, this)) {
			// A warning rumble, then the eruption.
			server.scheduleTick(pos, this, 14);
			server.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.2F, 0.5F);
			server.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.02);
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		erupt(level, pos);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		erupt(level, pos);
	}

	private static void erupt(ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos.above()).isAir()) {
			return;
		}
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		AABB column = new AABB(pos.above()).expandTowards(0.0, HEIGHT, 0.0).inflate(0.3, 0.0, 0.3);
		for (Entity entity : level.getEntitiesOfClass(Entity.class, column)) {
			entity.push(0.0, 1.3, 0.0);
			entity.hurtMarked = true;
			if (entity instanceof LivingEntity living && !living.fireImmune()) {
				living.igniteForSeconds(6.0F);
				living.hurtServer(level, level.damageSources().hotFloor(), 4.0F);
			}
		}
		level.sendParticles(ParticleTypes.FLAME, x, y + 1.5, z, 40, 0.15, 1.6, 0.15, 0.06);
		level.sendParticles(ParticleTypes.LAVA, x, y + 0.5, z, 10, 0.2, 0.4, 0.2, 0.2);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 3.0, z, 24, 0.35, 2.0, 0.35, 0.03);
		level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.6F, 0.45F);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
		double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
		if (random.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 1.05, z, 0.0, 0.08, 0.0);
		}
		if (random.nextInt(30) == 0) {
			level.addParticle(ParticleTypes.LAVA, x, pos.getY() + 1.0, z, 0.0, 0.0, 0.0);
		}
	}
}
