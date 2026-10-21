package com.naturesmod.abyss;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.Vec3;

import com.naturesmod.NaturesConfig;
import com.naturesmod.NaturesMod;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.item.DivingArmor;

/**
 * The Abyss: a sea a thousand blocks deep, reached through the Abyssal Rifts in the floor of the
 * deep oceans. The deeper you dive, the harder the water presses on you: without a diving suit
 * that can take it, the pressure crushes you.
 */
public final class Abyss {
	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, NaturesMod.id("abyss"));
	public static final ResourceKey<DamageType> PRESSURE = ResourceKey.create(Registries.DAMAGE_TYPE, NaturesMod.id("pressure"));
	/** The first block of air above the sea. */
	public static final int SEA_LEVEL = 1050;
	/** Below this a diver needs a pressure rating of 4 (a full Tidewalker suit or two Abyssal pieces). */
	public static final int MIDNIGHT_DEPTH = 700;
	/** Below this only a full Abyssal suit (rating 8) or Conduit Power keeps the pressure out. */
	public static final int CRUSH_DEPTH = 350;

	private static final Map<UUID, Integer> WARNED = new HashMap<>();
	/** Vanilla treasure chests that may hold a map to the nearest Abyssal Rift. */
	private static final Set<String> RIFT_MAP_CHESTS = Set.of("chests/shipwreck_map", "chests/underwater_ruin_big", "chests/buried_treasure");
	private static final ResourceKey<LootTable> RIFT_MAP = ResourceKey.create(Registries.LOOT_TABLE, NaturesMod.id("inject/rift_map"));

	private Abyss() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Abyss::tick);
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (source.isBuiltin() && key.identifier().getNamespace().equals("minecraft") && RIFT_MAP_CHESTS.contains(key.identifier().getPath())) {
				table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(NestedLootTable.lootTableReference(RIFT_MAP)));
			}
		});
	}

	public static boolean isAbyss(Level level) {
		return level.dimension() == LEVEL;
	}

	// --- travelling ------------------------------------------------------------------------

	/** Where a rift at {@code pos} in {@code level} takes a diver. */
	static TeleportTransition riftDestination(ServerLevel level, Entity entity, BlockPos pos) {
		MinecraftServer server = level.getServer();
		if (isAbyss(level)) {
			// Back up to the sea of the Overworld, straight above where you are.
			ServerLevel overworld = server.getLevel(Level.OVERWORLD);
			if (overworld == null) {
				return null;
			}
			int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
			Vec3 target = new Vec3(pos.getX() + 0.5, y + 0.1, pos.getZ() + 0.5);
			return new TeleportTransition(overworld, target, Vec3.ZERO, entity.getYRot(), 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION),
					TeleportTransition.PLAY_PORTAL_SOUND.then(e -> e.placePortalTicket(BlockPos.containing(target))));
		}
		ServerLevel abyss = server.getLevel(LEVEL);
		if (abyss == null) {
			return null;
		}
		BlockPos center = new BlockPos(pos.getX(), SEA_LEVEL - 1, pos.getZ());
		buildArrival(abyss, center);
		Vec3 target = new Vec3(center.getX() + 3.5, SEA_LEVEL, center.getZ() + 0.5);
		return new TeleportTransition(abyss, target, Vec3.ZERO, entity.getYRot(), 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION),
				TeleportTransition.PLAY_PORTAL_SOUND.then(e -> e.placePortalTicket(center)));
	}

	/**
	 * A small platform floating on the surface of the black sea, built the first time a diver
	 * arrives at a spot: a ring of prismarine around a rift back home, lit by sea lanterns and
	 * anchored by a chain that disappears into the deep.
	 */
	static void buildArrival(ServerLevel level, BlockPos center) {
		if (level.getBlockState(center).is(ModBlocks.ABYSSAL_RIFT)) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState rift = ModBlocks.ABYSSAL_RIFT.defaultBlockState();
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				double distance = Math.sqrt(dx * dx + dz * dz);
				if (distance > 5.2) {
					continue;
				}
				pos.set(center.getX() + dx, center.getY(), center.getZ() + dz);
				BlockState state;
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					state = rift;
				} else if (distance > 4.3) {
					state = Blocks.PRISMARINE_BRICKS.defaultBlockState();
				} else {
					state = Blocks.DARK_PRISMARINE.defaultBlockState();
				}
				level.setBlock(pos, state, 2);
				// Clear the air above the deck.
				for (int up = 1; up <= 3; up++) {
					pos.set(center.getX() + dx, center.getY() + up, center.getZ() + dz);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
		// Lantern posts on the four corners of the deck.
		for (int[] corner : new int[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
			pos.set(center.getX() + corner[0], center.getY() + 1, center.getZ() + corner[1]);
			level.setBlock(pos, Blocks.PRISMARINE_WALL.defaultBlockState(), 3);
			level.setBlock(pos.move(0, 1, 0), Blocks.SEA_LANTERN.defaultBlockState(), 3);
		}
		// The anchor chain, hanging down into the dark.
		BlockState chain = Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.Y)
				.setValue(BlockStateProperties.WATERLOGGED, true);
		for (int down = 1; down <= 40; down++) {
			pos.set(center.getX() + 3, center.getY() - down, center.getZ() + 3);
			if (!level.getBlockState(pos).is(Blocks.WATER)) {
				break;
			}
			level.setBlock(pos, chain, 2);
		}
	}

	// --- pressure --------------------------------------------------------------------------

	/** Pressure rating needed to stay healthy at this height in the Abyss. */
	public static int requiredRating(double y) {
		if (y < CRUSH_DEPTH) {
			return 8;
		}
		if (y < MIDNIGHT_DEPTH) {
			return 4;
		}
		return 0;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0 || !NaturesConfig.abyssPressure()) {
			return;
		}
		ServerLevel level = server.getLevel(LEVEL);
		if (level == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
				continue;
			}
			int required = requiredRating(player.getY());
			boolean shielded = player.hasEffect(MobEffects.CONDUIT_POWER) || DivingArmor.pressureRating(player) >= required;
			if (required == 0 || shielded) {
				WARNED.remove(player.getUUID());
				continue;
			}
			if (WARNED.put(player.getUUID(), required) == null) {
				player.sendSystemMessage(Component.translatable(required >= 8 ? "message.naturesmod.pressure.crush" : "message.naturesmod.pressure.deep"), false);
			}
			player.sendSystemMessage(Component.translatable("message.naturesmod.pressure.overlay"), true);
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, true, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 40, 1, true, false, true));
			player.hurtServer(level, player.damageSources().source(PRESSURE), required >= 8 ? 4.0F : 2.0F);
		}
	}
}
