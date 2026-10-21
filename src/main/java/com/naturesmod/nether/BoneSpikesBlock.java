package com.naturesmod.nether;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.naturesmod.block.SeaPlantBlock;

/**
 * A cluster of sharpened bones jutting from the ground. Wading through them is slow, and every
 * step draws blood; skeletons and the fire-born pick their way through unharmed.
 */
public class BoneSpikesBlock extends SeaPlantBlock {
	public BoneSpikesBlock(BlockBehaviour.Properties properties) {
		super(properties, Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0));
		this.registerDefaultState(this.defaultBlockState().setValue(WATERLOGGED, false));
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (!(entity instanceof LivingEntity) || entity.fireImmune()) {
			return;
		}
		entity.makeStuckInBlock(state, new Vec3(0.75, 0.8, 0.75));
		if (level instanceof ServerLevel server && entity.tickCount % 10 == 0) {
			Vec3 movement = entity.oldPosition().subtract(entity.position());
			if (movement.horizontalDistanceSqr() > 0.0) {
				entity.hurtServer(server, level.damageSources().stalagmite(), 2.0F);
			}
		}
	}
}
