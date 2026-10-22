package com.naturesmod.end;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Void vines: glowing strands that hang from the undersides of the End's lands, down into the
 * void. Each hangs from solid rock or from the vine above it.
 */
public class HangingVineBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);

	public HangingVineBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos above = pos.above();
		BlockState holder = level.getBlockState(above);
		return holder.is(this) || holder.isFaceSturdy(level, above, Direction.DOWN);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (direction == Direction.UP && !this.canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}
}
