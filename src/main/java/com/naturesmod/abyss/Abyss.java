package com.naturesmod.abyss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
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

	private Abyss() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Abyss::tick);
		// The first time a diver arrives, the logbook of the expedition that came before drifts by.
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (isAbyss(destination) && player.addTag("naturesmod.logbook")) {
				ItemStack book = logbook();
				if (!player.getInventory().add(book)) {
					destination.addFreshEntity(new ItemEntity(destination, player.getX(), player.getY() + 0.5, player.getZ(), book));
				}
				player.sendSystemMessage(Component.translatable("message.naturesmod.logbook").withStyle(ChatFormatting.AQUA), false);
			}
		});
	}

	/** The waterlogged logbook of the Drowned Expedition: the mission of the Abyss. */
	public static ItemStack logbook() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = new ArrayList<>();
		for (int page = 1; page <= 5; page++) {
			pages.add(Filterable.<Component>passThrough(Component.translatable("book.naturesmod.logbook.page" + page)));
		}
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("The Drowned Expedition"),
				"Captain Ilse Vael", 0, pages, true));
		return book;
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
			// Load the chunk first: the heightmap of a chunk that is not loaded reads as the bottom of the world.
			LevelChunk chunk = overworld.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
			int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX() & 15, pos.getZ() & 15) + 1;
			Vec3 target = new Vec3(pos.getX() + 0.5, y + 0.1, pos.getZ() + 0.5);
			return new TeleportTransition(overworld, target, Vec3.ZERO, 0.0F, 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION),
					TeleportTransition.PLAY_PORTAL_SOUND.then(e -> e.placePortalTicket(BlockPos.containing(target))));
		}
		ServerLevel abyss = server.getLevel(LEVEL);
		if (abyss == null) {
			return null;
		}
		BlockPos center = new BlockPos(pos.getX(), SEA_LEVEL - 1, pos.getZ());
		buildArrival(abyss, center);
		Vec3 target = new Vec3(center.getX() + 3.5, SEA_LEVEL, center.getZ() + 0.5);
		return new TeleportTransition(abyss, target, Vec3.ZERO, 0.0F, 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION),
				TeleportTransition.PLAY_PORTAL_SOUND.then(e -> e.placePortalTicket(center)));
	}

	/**
	 * A small platform floating on the surface of the black sea, built the first time a diver
	 * arrives at a spot: a ring of prismarine around a rift back home, lit by sea lanterns and
	 * anchored by a chain that disappears into the deep.
	 */
	public static void buildArrival(ServerLevel level, BlockPos center) {
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

	// --- things in the dark ------------------------------------------------------------------

	/** Somewhere out in the black water, something roars, or moans, or its heart beats. */
	private static void distantSound(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = 14.0 + random.nextDouble() * 18.0;
		double x = player.getX() + Math.cos(angle) * distance;
		double y = player.getY() + random.nextDouble() * 16.0 - 8.0;
		double z = player.getZ() + Math.sin(angle) * distance;
		switch (random.nextInt(4)) {
			case 0 -> level.playSound(null, x, y, z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.4F, 0.45F);
			case 1 -> level.playSound(null, x, y, z, SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.HOSTILE, 2.5F, 0.35F);
			case 2 -> level.playSound(null, x, y, z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0F, 0.6F);
			default -> level.playSound(null, x, y, z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.6F, 0.3F);
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
			if (!player.isSpectator() && player.getY() < MIDNIGHT_DEPTH + 100 && level.getRandom().nextInt(70) == 0) {
				distantSound(level, player);
			}
			if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
				continue;
			}
			int required = requiredRating(player.getY());
			int deficit = required - DivingArmor.pressureRating(player);
			if (deficit <= 0 || player.hasEffect(MobEffects.CONDUIT_POWER)) {
				WARNED.remove(player.getUUID());
				continue;
			}
			if (WARNED.put(player.getUUID(), required) == null) {
				player.sendSystemMessage(Component.translatable(required >= 8 ? "message.naturesmod.pressure.crush" : "message.naturesmod.pressure.deep"), false);
			}
			player.sendSystemMessage(Component.translatable("message.naturesmod.pressure.overlay"), true);
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, true, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 40, 1, true, false, true));
			// Half a heart a second for every point of pressure rating the suit falls short.
			player.hurtServer(level, player.damageSources().source(PRESSURE), deficit * 0.5F);
		}
	}
}
