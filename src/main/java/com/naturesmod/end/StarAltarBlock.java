package com.naturesmod.end;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.naturesmod.abyss.AltarBlock;
import com.naturesmod.creature.ModCreatures;
import com.naturesmod.item.ModItems;

/**
 * The Star Altar at the heart of every Star Citadel (one can be made too, from void crystal,
 * starmetal, an End crystal and obsidian). Set four star fragments into it out in the End, and the
 * Void Wyrm comes down out of the dark to claim them.
 */
public class StarAltarBlock extends Block {
	public static final IntegerProperty FRAGMENTS = IntegerProperty.create("fragments", 0, 4);
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

	public StarAltarBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FRAGMENTS, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FRAGMENTS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		if (!stack.is(ModItems.STAR_FRAGMENT)) {
			return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
		}
		int fragments = state.getValue(FRAGMENTS);
		if (fragments >= 4) {
			tell(player, Component.translatable("message.naturesmod.star_altar.waking").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return InteractionResult.FAIL;
		}
		if (level.dimension() != Level.END) {
			tell(player, Component.translatable("message.naturesmod.star_altar.not_end").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server) {
			int count = fragments + 1;
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			server.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.5F, 0.8F);
			server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 0.6F + count * 0.2F);
			server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.08);
			tell(player, Component.translatable("message.naturesmod.star_altar.fragment", count).withStyle(ChatFormatting.LIGHT_PURPLE), false);
			if (count == 4) {
				server.scheduleTick(pos, this, 80);
				server.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 4.0F, 0.7F);
				for (ServerPlayer nearby : server.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(64.0))) {
					nearby.sendSystemMessage(Component.translatable("message.naturesmod.star_altar.awakening")
							.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
					nearby.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
					AltarBlock.award(server, nearby, "end/call_the_wyrm");
				}
			}
			server.setBlock(pos, state.setValue(FRAGMENTS, count), 3);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		tell(player, Component.translatable(state.getValue(FRAGMENTS) >= 4 ? "message.naturesmod.star_altar.waking"
				: "message.naturesmod.star_altar.status", state.getValue(FRAGMENTS)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
		return InteractionResult.SUCCESS;
	}

	/** Four seconds after the last fragment is set, the wyrm comes; the altar is empty again, ready for another four. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(FRAGMENTS) >= 4) {
			level.setBlock(pos, state.setValue(FRAGMENTS, 0), 3);
			summon(level, pos, random);
		}
	}

	/** Brings the Void Wyrm down over an altar; null if it could not be made. */
	public static Mob summon(ServerLevel level, BlockPos pos, RandomSource random) {
		EntityType<? extends Mob> type = ModCreatures.type("void_wyrm");
		Mob wyrm = type == null ? null : type.create(level, EntitySpawnReason.TRIGGERED);
		if (wyrm == null) {
			return null;
		}
		wyrm.snapTo(pos.getX() + 0.5, pos.getY() + 24.0, pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		wyrm.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.TRIGGERED, null);
		wyrm.setPersistenceRequired();
		level.addFreshEntity(wyrm);
		level.playSound(null, pos, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 0.6F);
		level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 0.8F);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 20.0, pos.getZ() + 0.5, 300, 3.0, 4.0, 3.0, 0.2);
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 4.0, pos.getZ() + 0.5, 120, 0.3, 4.0, 0.3, 0.05);
		List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, wyrm.getBoundingBox().inflate(96.0));
		for (ServerPlayer player : players) {
			player.sendSystemMessage(Component.translatable("message.naturesmod.star_altar.risen").withStyle(ChatFormatting.DARK_PURPLE), true);
		}
		return wyrm;
	}

	/** A message in the chat, or above the hotbar; only the server talks to players. */
	private static void tell(Player player, Component message, boolean overlay) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(message, overlay);
		}
	}
}
