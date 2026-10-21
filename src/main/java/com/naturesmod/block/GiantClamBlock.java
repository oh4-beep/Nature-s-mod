package com.naturesmod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import com.naturesmod.item.ModItems;

/**
 * A giant clam resting on the reef. Now and then it opens to show the pearl it has grown; reach
 * in and take it, and in time it grows another.
 */
public class GiantClamBlock extends SeaPlantBlock {
	public static final BooleanProperty HAS_PEARL = BooleanProperty.create("has_pearl");

	public GiantClamBlock(BlockBehaviour.Properties properties) {
		super(properties, Block.box(1.0, 0.0, 2.0, 15.0, 8.0, 14.0));
		this.registerDefaultState(this.defaultBlockState().setValue(HAS_PEARL, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(HAS_PEARL);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(HAS_PEARL) && state.getValue(WATERLOGGED) && random.nextInt(12) == 0) {
			level.setBlock(pos, state.setValue(HAS_PEARL, true), 3);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!state.getValue(HAS_PEARL)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			level.setBlock(pos, state.setValue(HAS_PEARL, false), 3);
			Block.popResource(level, pos.above(), new ItemStack(ModItems.PEARL));
			server.playSound(null, pos, SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 0.5F, 1.6F);
			server.sendParticles(ParticleTypes.BUBBLE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.05);
		}
		return InteractionResult.SUCCESS;
	}
}
