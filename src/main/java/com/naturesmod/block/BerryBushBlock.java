package com.naturesmod.block;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A berry bush without thorns: it grows in four stages, and once its berries are ripe a hand
 * picks them, leaving the bush to fruit again. Blueberries grow in the taigas and on the tundra.
 */
public class BerryBushBlock extends VegetationBlock {
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
	private static final VoxelShape SHAPE_SAPLING = Block.column(10.0, 0.0, 8.0);
	private static final VoxelShape SHAPE_GROWING = Block.column(14.0, 0.0, 16.0);
	private final Supplier<Item> berries;

	public BerryBushBlock(BlockBehaviour.Properties properties, Supplier<Item> berries) {
		super(properties);
		this.berries = berries;
		this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(berries.get());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(AGE)) {
			case 0 -> SHAPE_SAPLING;
			case 3 -> Shapes.block();
			default -> SHAPE_GROWING;
		};
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(AGE) < 3;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int age = state.getValue(AGE);
		if (age < 3 && random.nextInt(5) == 0 && level.getRawBrightness(pos.above(), 0) >= 9) {
			BlockState grown = state.setValue(AGE, age + 1);
			level.setBlock(pos, grown, 2);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(grown));
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		int age = state.getValue(AGE);
		if (age < 2) {
			return super.useWithoutItem(state, level, pos, player, hitResult);
		}
		if (level instanceof ServerLevel server) {
			int count = 1 + server.getRandom().nextInt(2) + (age == 3 ? 1 : 0);
			Block.popResource(server, pos, new ItemStack(berries.get(), count));
			server.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + server.getRandom().nextFloat() * 0.4F);
			BlockState picked = state.setValue(AGE, 1);
			server.setBlock(pos, picked, 2);
			server.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, picked));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}
}
