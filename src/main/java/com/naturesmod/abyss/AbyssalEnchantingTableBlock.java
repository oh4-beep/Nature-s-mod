package com.naturesmod.abyss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Abyssal Enchanting Table, the lost art of the drowned people. Lay an enchanted tool, weapon
 * or piece of armor on it and every one of its enchantments grows to twice its level (Fortune III
 * becomes Fortune VI). The deep only grants this once: an empowered item can never be empowered
 * again.
 */
public class AbyssalEnchantingTableBlock extends Block {
	public static final String EMPOWERED = "naturesmod_empowered";
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

	public AbyssalEnchantingTableBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Whether the Abyss has already doubled this item's enchantments. */
	public static boolean isEmpowered(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(EMPOWERED, false);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		if (stack.isEmpty()) {
			return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
		}
		ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (enchantments.isEmpty()) {
			tell(player, "message.naturesmod.empower.nothing", ChatFormatting.GRAY);
			return InteractionResult.FAIL;
		}
		if (isEmpowered(stack)) {
			tell(player, "message.naturesmod.empower.already", ChatFormatting.DARK_AQUA);
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		ItemEnchantments.Mutable doubled = new ItemEnchantments.Mutable(enchantments);
		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			doubled.set(enchantment, Math.min(255, enchantments.getLevel(enchantment) * 2));
		}
		stack.set(DataComponents.ENCHANTMENTS, doubled.toImmutable());
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(EMPOWERED, true));
		ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		stack.set(DataComponents.LORE, lore.withLineAdded(Component.translatable("item.naturesmod.empowered").withStyle(ChatFormatting.AQUA)));

		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.1;
		double z = pos.getZ() + 0.5;
		server.sendParticles(ParticleTypes.ENCHANT, x, y + 0.5, z, 120, 0.6, 0.8, 0.6, 1.2);
		server.sendParticles(ParticleTypes.NAUTILUS, x, y, z, 60, 0.5, 0.5, 0.5, 0.6);
		server.sendParticles(ParticleTypes.END_ROD, x, y + 0.3, z, 30, 0.3, 0.6, 0.3, 0.08);
		server.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.5F, 0.6F);
		server.playSound(null, pos, SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.7F);
		server.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 0.5F);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.translatable("message.naturesmod.empower.done").withStyle(ChatFormatting.AQUA), false);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		tell(player, "message.naturesmod.empower.hint", ChatFormatting.DARK_AQUA);
		return InteractionResult.SUCCESS;
	}

	private static void tell(Player player, String key, ChatFormatting color) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(Component.translatable(key).withStyle(color), true);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// Glyphs drift in towards the table from all round, as to an enchanting table from its books.
		for (int i = 0; i < 3; i++) {
			double dx = random.nextGaussian() * 1.6;
			double dz = random.nextGaussian() * 1.6;
			level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.8, pos.getZ() + 0.5, dx, random.nextFloat() - 0.6, dz);
		}
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.NAUTILUS, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5,
					random.nextGaussian() * 0.6, 0.2, random.nextGaussian() * 0.6);
		}
	}
}
