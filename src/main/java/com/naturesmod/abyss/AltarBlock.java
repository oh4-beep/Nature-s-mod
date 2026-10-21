package com.naturesmod.abyss;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.naturesmod.NaturesMod;
import com.naturesmod.creature.ModCreatures;
import com.naturesmod.item.ModItems;

/**
 * The Abyssal Altar at the heart of every Drowned Sanctum. Set the four sigils of the old sea
 * into it, the Tide, the Deep, the Bone and the Ember, and what sleeps below the altar wakes.
 */
public class AltarBlock extends Block {
	public static final BooleanProperty TIDE = BooleanProperty.create("tide");
	public static final BooleanProperty DEEP = BooleanProperty.create("deep");
	public static final BooleanProperty BONE = BooleanProperty.create("bone");
	public static final BooleanProperty EMBER = BooleanProperty.create("ember");
	public static final BooleanProperty AWAKENED = BooleanProperty.create("awakened");
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0);

	public AltarBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(TIDE, false).setValue(DEEP, false).setValue(BONE, false)
				.setValue(EMBER, false).setValue(AWAKENED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TIDE, DEEP, BONE, EMBER, AWAKENED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** How many sigils sit in the altar; it glows brighter with each. */
	public static int sigils(BlockState state) {
		return (state.getValue(TIDE) ? 1 : 0) + (state.getValue(DEEP) ? 1 : 0) + (state.getValue(BONE) ? 1 : 0) + (state.getValue(EMBER) ? 1 : 0);
	}

	private static BooleanProperty socket(Item item) {
		if (item == ModItems.TIDE_SIGIL) {
			return TIDE;
		}
		if (item == ModItems.DEEP_SIGIL) {
			return DEEP;
		}
		if (item == ModItems.BONE_SIGIL) {
			return BONE;
		}
		if (item == ModItems.EMBER_SIGIL) {
			return EMBER;
		}
		return null;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		BooleanProperty socket = socket(stack.getItem());
		if (socket == null) {
			return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
		}
		if (state.getValue(AWAKENED) || state.getValue(socket)) {
			tell(player, Component.translatable(state.getValue(AWAKENED) ? "message.naturesmod.altar.silent"
					: "message.naturesmod.altar.taken").withStyle(ChatFormatting.DARK_AQUA), true);
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server) {
			BlockState placed = state.setValue(socket, true);
			int count = sigils(placed);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			server.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.5F, 0.6F);
			server.playSound(null, pos, SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.5F + count * 0.15F);
			server.sendParticles(ParticleTypes.NAUTILUS, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.6, 0.4, 0.6, 0.4);
			tell(player, Component.translatable("message.naturesmod.altar.sigil", Component.translatable(sigilName(socket)), count)
					.withStyle(ChatFormatting.AQUA), false);
			if (count == 4) {
				placed = placed.setValue(AWAKENED, true);
				server.scheduleTick(pos, this, 80);
				server.playSound(null, pos, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 4.0F, 0.4F);
				for (ServerPlayer nearby : server.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(64.0))) {
					nearby.sendSystemMessage(Component.translatable("message.naturesmod.altar.awakening").withStyle(ChatFormatting.DARK_RED,
							ChatFormatting.BOLD), false);
					nearby.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
					award(server, nearby, "sea/awaken_the_abyss");
				}
			}
			server.setBlock(pos, placed, 3);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (state.getValue(AWAKENED)) {
			tell(player, Component.translatable("message.naturesmod.altar.silent").withStyle(ChatFormatting.DARK_AQUA), true);
		} else {
			tell(player, Component.translatable("message.naturesmod.altar.status", sigils(state)).withStyle(ChatFormatting.DARK_AQUA), true);
		}
		return InteractionResult.SUCCESS;
	}

	/** Eighty ticks after the last sigil is set: the thing below rises. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(AWAKENED)) {
			rise(level, pos, random);
		}
	}

	/** Raises the Abyssal Horror above an altar; null if it could not be made. */
	public static Mob rise(ServerLevel level, BlockPos pos, RandomSource random) {
		EntityType<? extends Mob> type = ModCreatures.type("abyssal_horror");
		Mob horror = type == null ? null : type.create(level, EntitySpawnReason.TRIGGERED);
		if (horror == null) {
			return null;
		}
		horror.snapTo(pos.getX() + 0.5, pos.getY() + 10.0, pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		horror.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.TRIGGERED, null);
		horror.setPersistenceRequired();
		level.addFreshEntity(horror);
		level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 0.5F);
		level.playSound(null, pos, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3.0F, 0.6F);
		level.sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5, pos.getY() + 6.0, pos.getZ() + 0.5, 120, 2.5, 4.0, 2.5, 0.05);
		List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, horror.getBoundingBox().inflate(64.0));
		for (ServerPlayer player : players) {
			player.sendSystemMessage(Component.translatable("message.naturesmod.altar.risen").withStyle(ChatFormatting.DARK_RED), true);
		}
		return horror;
	}

	/** The translation key of the sigil that fits a socket. */
	private static String sigilName(BooleanProperty socket) {
		String name = socket == TIDE ? "tide" : socket == DEEP ? "deep" : socket == BONE ? "bone" : "ember";
		return "item.naturesmod." + name + "_sigil";
	}

	/** A message in the chat, or above the hotbar; only the server talks to players. */
	private static void tell(Player player, Component message, boolean overlay) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(message, overlay);
		}
	}

	static void award(ServerLevel level, ServerPlayer player, String advancement) {
		Identifier id = NaturesMod.id(advancement);
		for (AdvancementHolder holder : level.getServer().getAdvancements().getAllAdvancements()) {
			if (holder.id().equals(id)) {
				for (String criterion : holder.value().criteria().keySet()) {
					player.getAdvancements().award(holder, criterion);
				}
			}
		}
	}
}
