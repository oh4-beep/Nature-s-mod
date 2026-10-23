package com.naturesmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A little wild plant that grows on any firm ground, in the open or deep in a cave: glowcaps, the
 * glowing mushrooms of the fungal grottos, which now and then let go a mote of light.
 */
public class WildPlantBlock extends VegetationBlock {
	private final VoxelShape shape;
	private final boolean glows;

	public WildPlantBlock(BlockBehaviour.Properties properties, double height, boolean glows) {
		super(properties);
		this.shape = Block.column(10.0, 0.0, height);
		this.glows = glows;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.isFaceSturdy(level, pos, Direction.UP);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (glows && random.nextInt(8) == 0) {
			level.addParticle(ParticleTypes.GLOW, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.5,
					pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.02 + random.nextDouble() * 0.02, 0.0);
		}
	}
}
