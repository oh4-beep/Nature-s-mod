package com.naturesmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.naturesmod.creature.SeaCreature;

/** A sea urchin: step on one and its spines sting and slow you down. Sea creatures know better. */
public class SeaUrchinBlock extends SeaPlantBlock {
	public SeaUrchinBlock(BlockBehaviour.Properties properties) {
		super(properties, Block.box(3.0, 0.0, 3.0, 13.0, 8.0, 13.0));
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		if (!(entity instanceof LivingEntity living) || entity instanceof SeaCreature) {
			return;
		}
		entity.makeStuckInBlock(state, new Vec3(0.7, 0.8, 0.7));
		if (level instanceof ServerLevel server && entity.tickCount % 10 == 0) {
			Vec3 movement = entity.oldPosition().subtract(entity.position());
			if (movement.horizontalDistanceSqr() > 0.0) {
				entity.hurtServer(server, level.damageSources().sweetBerryBush(), 1.5F);
				living.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
			}
		}
	}
}
