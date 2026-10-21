package com.naturesmod.client;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import com.naturesmod.NaturesConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Finds the foot of every waterfall near the player and surrounds it with drifting mist,
 * spray and a rushing roar that grows with the height of the fall.
 */
public final class WaterfallAmbience {
	private static final int RADIUS = 28;
	private static final int VERTICAL = 24;
	/** The scan volume is spread over this many ticks. */
	private static final int SLICES = 16;
	private static final int MIN_FALL = 3;
	private static final double MAX_DISTANCE_SQR = 48 * 48;

	private static Long2IntMap impacts = new Long2IntOpenHashMap();
	private static Long2IntMap scanning = new Long2IntOpenHashMap();
	private static int slice;
	private static BlockPos scanCenter = BlockPos.ZERO;

	private WaterfallAmbience() {
	}

	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		NaturesConfig config = NaturesConfig.get();
		if (level == null || player == null || client.isPaused() || !config.waterfallMist && !config.waterfallSounds) {
			return;
		}
		if (slice == 0) {
			scanCenter = player.blockPosition();
		}
		scan(level, slice);
		if (++slice >= SLICES) {
			impacts = scanning;
			scanning = new Long2IntOpenHashMap();
			slice = 0;
		}
		emit(level, player, config);
	}

	/** Scans every SLICES-th column of the area around the player for the bottoms of falling water. */
	private static void scan(ClientLevel level, int part) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minY = Math.max(level.getMinY(), scanCenter.getY() - VERTICAL);
		int maxY = Math.min(level.getMaxY(), scanCenter.getY() + VERTICAL);
		for (int dx = -RADIUS + part; dx <= RADIUS; dx += SLICES) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				int x = scanCenter.getX() + dx;
				int z = scanCenter.getZ() + dz;
				boolean inFall = false;
				for (int y = maxY; y >= minY; y--) {
					boolean falling = isFallingWater(level.getFluidState(pos.set(x, y, z)));
					if (inFall && !falling) {
						// y + 1 was the lowest block of a falling column.
						int height = fallHeight(level, pos.set(x, y + 1, z));
						if (height >= MIN_FALL) {
							scanning.put(BlockPos.asLong(x, y + 1, z), height);
						}
					}
					inFall = falling;
				}
			}
		}
	}

	private static boolean isFallingWater(FluidState fluid) {
		return fluid.is(FluidTags.WATER) && !fluid.isSource() && fluid.getValue(FlowingFluid.FALLING);
	}

	private static int fallHeight(ClientLevel level, BlockPos bottom) {
		BlockPos.MutableBlockPos pos = bottom.mutable();
		int height = 0;
		while (height < 96 && isFallingWater(level.getFluidState(pos))) {
			height++;
			pos.move(0, 1, 0);
		}
		return height;
	}

	private static void emit(ClientLevel level, LocalPlayer player, NaturesConfig config) {
		if (impacts.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Long2IntMap.Entry entry : impacts.long2IntEntrySet()) {
			long key = entry.getLongKey();
			int height = entry.getIntValue();
			double x = BlockPos.getX(key) + 0.5;
			double y = BlockPos.getY(key);
			double z = BlockPos.getZ(key) + 0.5;
			double dx = x - player.getX();
			double dy = y - player.getY();
			double dz = z - player.getZ();
			if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQR) {
				continue;
			}
			double power = Math.min(height, 40) / 10.0;

			if (config.waterfallMist) {
				double mist = power * 0.9 * config.mistDensity;
				int count = (int) mist + (random.nextDouble() < mist - (int) mist ? 1 : 0);
				for (int i = 0; i < count; i++) {
					level.addParticle(ParticleTypes.CLOUD,
							x + random.nextGaussian() * 0.7, y + 0.2 + random.nextDouble() * 0.6, z + random.nextGaussian() * 0.7,
							random.nextGaussian() * 0.025, 0.015 + random.nextDouble() * 0.035, random.nextGaussian() * 0.025);
				}
				if (random.nextDouble() < 0.4 + power * 0.2) {
					level.addParticle(ParticleTypes.SPLASH, x + random.nextGaussian() * 0.6, y + 0.9, z + random.nextGaussian() * 0.6,
							random.nextGaussian() * 0.1, 0.2, random.nextGaussian() * 0.1);
				}
				if (random.nextDouble() < 0.12 * power) {
					level.addParticle(ParticleTypes.FALLING_WATER, x + (random.nextDouble() - 0.5) * 1.6, y + random.nextDouble() * height,
							z + (random.nextDouble() - 0.5) * 1.6, 0, 0, 0);
				}
			}
			if (config.waterfallSounds && random.nextInt(36) == 0) {
				float volume = (float) Math.min(1.8, 0.35 + height / 18.0);
				level.playLocalSound(x, y + 1, z, SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT, volume, 0.5f + random.nextFloat() * 0.25f, false);
			}
		}
	}
}
