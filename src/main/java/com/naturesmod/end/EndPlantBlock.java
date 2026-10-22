package com.naturesmod.end;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A little plant of the End: tufts of ender grass, and void blooms that glow and now and then
 * breathe out a mote of purple light. It grows on any solid ground and pops off when the ground
 * under it goes.
 */
public class EndPlantBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);
	private final boolean glows;

	public EndPlantBlock(BlockBehaviour.Properties properties, boolean glows) {
		super(properties);
		this.glows = glows;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (direction == Direction.DOWN && !this.canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (glows && random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
					(random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4);
		}
	}
}
