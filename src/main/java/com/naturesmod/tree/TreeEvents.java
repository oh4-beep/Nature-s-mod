package com.naturesmod.tree;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import com.naturesmod.NaturesConfig;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Hooks tree felling into the game: chopping, lightning strikes and storm windthrow. */
public final class TreeEvents {
	private TreeEvents() {
	}

	public static void register() {
		ServerScheduler.init();

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			NaturesConfig config = NaturesConfig.get();
			if (!(level instanceof ServerLevel serverLevel) || !config.treeFelling || !state.is(BlockTags.LOGS)) {
				return;
			}
			if (player.isCreative() && !config.fellInCreative || config.sneakToChopNormally && player.isShiftKeyDown()) {
				return;
			}
			ItemStack tool = player.getMainHandItem();
			if (config.fellingRequiresAxe && !tool.is(ItemTags.AXES)) {
				return;
			}
			Vec3 away = new Vec3(pos.getX() + 0.5 - player.getX(), 0, pos.getZ() + 0.5 - player.getZ());
			if (away.lengthSqr() < 0.01) {
				away = Vec3.directionFromRotation(0, player.getYRot());
			}
			int felled = TreeFeller.fellAround(serverLevel, pos, away);
			if (felled > 0 && !player.isCreative()) {
				if (config.logsPerExtraDurability > 0 && felled >= config.logsPerExtraDurability && !tool.isEmpty()) {
					tool.hurtAndBreak(felled / config.logsPerExtraDurability, player, EquipmentSlot.MAINHAND);
				}
				player.causeFoodExhaustion(Math.min(felled, 300) * 0.005f);
			}
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LightningBolt bolt && NaturesConfig.get().lightningFellsTrees) {
				ServerScheduler.schedule(2, () -> lightningStrike(level, bolt.blockPosition()));
			}
		});

		ServerTickEvents.END_LEVEL_TICK.register(TreeEvents::windthrowTick);
	}

	/** A bolt that hits a tree splits its trunk and knocks it over. */
	private static void lightningStrike(ServerLevel level, BlockPos strike) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dy = 2; dy >= -24; dy--) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					pos.set(strike.getX() + dx, strike.getY() + dy, strike.getZ() + dz);
					if (TreeFeller.isLog(level.getBlockState(pos))) {
						RandomSource random = level.getRandom();
						double angle = random.nextDouble() * Math.PI * 2;
						if (snapTrunk(level, pos.immutable(), new Vec3(Math.cos(angle), 0, Math.sin(angle)), 1 + random.nextInt(3))) {
							level.playSound(null, strike, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 2.0f, 0.6f);
						}
						return;
					}
				}
			}
		}
	}

	/**
	 * Breaks a standing natural tree at {@code heightAboveBase} blocks above its base and lets the
	 * rest fall. Returns false if the logs at {@code anyLog} don't belong to a natural tree.
	 */
	public static boolean snapTrunk(ServerLevel level, BlockPos anyLog, Vec3 direction, int heightAboveBase) {
		TreeFeller.TreePart tree = TreeFeller.scan(level, anyLog, new LongOpenHashSet(), NaturesConfig.get().maxTreeLogs);
		if (tree == null || !tree.natural() || !tree.grounded() || tree.logs().size() < 4) {
			return false;
		}
		int cutY = Math.min(tree.minY() + heightAboveBase, tree.maxY() - 2);
		BlockPos cut = null;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < tree.logs().size(); i++) {
			pos.set(tree.logs().getLong(i));
			if (pos.getY() == cutY) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
				if (cut == null) {
					cut = pos.immutable();
				}
			}
		}
		return cut != null && TreeFeller.fellAround(level, cut, direction) > 0;
	}

	/** During thunderstorms the wind occasionally topples a tree near a player. */
	private static void windthrowTick(ServerLevel level) {
		NaturesConfig config = NaturesConfig.get();
		if (!config.stormWindthrow || level.dimension() != Level.OVERWORLD || !level.isThundering() || level.getGameTime() % 20 != 0) {
			return;
		}
		RandomSource random = level.getRandom();
		double chancePerSecond = 1.0 / (Math.max(1, config.windthrowMinutes) * 60.0);
		// The storm blows from one direction for a while.
		double windAngle = (level.getGameTime() / 6000L) * 2.399;
		Vec3 wind = new Vec3(Math.cos(windAngle), 0, Math.sin(windAngle));
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || random.nextDouble() >= chancePerSecond) {
				continue;
			}
			for (int attempt = 0; attempt < 12; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double distance = 20 + random.nextDouble() * 28;
				int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
				int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
				if (!level.hasChunkAt(x, z)) {
					continue;
				}
				BlockPos log = findTrunk(level, x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
				if (log != null && snapTrunk(level, log, wind, 0)) {
					break;
				}
			}
		}
	}

	private static BlockPos findTrunk(ServerLevel level, int x, int top, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
		for (int y = top; y > top - 40 && y > level.getMinY(); y--) {
			pos.setY(y);
			if (TreeFeller.isLog(level.getBlockState(pos))) {
				return pos.immutable();
			}
			if (!level.getBlockState(pos).is(BlockTags.LEAVES) && !level.getBlockState(pos).isAir()) {
				return null;
			}
		}
		return null;
	}
}
