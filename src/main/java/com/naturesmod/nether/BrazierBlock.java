package com.naturesmod.nether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An iron brazier with a fire that never goes out: hellfire or soul fire. It lights the halls of
 * the Infernal Citadels and the ritual circles, and burns whoever climbs into it.
 */
public class BrazierBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 11.0, 14.0);
	private final boolean soul;

	public BrazierBlock(BlockBehaviour.Properties properties, boolean soul) {
		super(properties);
		this.soul = soul;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (entity instanceof LivingEntity && !entity.fireImmune()) {
			entity.hurt(level.damageSources().campfire(), soul ? 2.0F : 1.0F);
		}
		super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.7;
		double z = pos.getZ() + 0.5;
		if (random.nextInt(8) == 0) {
			level.playLocalSound(x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.6F + random.nextFloat() * 0.4F, random.nextFloat() * 0.7F + 0.3F, false);
		}
		for (int i = 0; i < 2; i++) {
			level.addParticle(soul ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.5,
					y + random.nextDouble() * 0.5, z + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.02 + random.nextDouble() * 0.03, 0.0);
		}
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.LARGE_SMOKE, x + (random.nextDouble() - 0.5) * 0.3, y + 0.9, z + (random.nextDouble() - 0.5) * 0.3,
					0.0, 0.04, 0.0);
		}
		if (!soul && random.nextInt(10) == 0) {
			level.addParticle(ParticleTypes.LAVA, x, y + 0.3, z, 0.0, 0.0, 0.0);
		}
	}
}
