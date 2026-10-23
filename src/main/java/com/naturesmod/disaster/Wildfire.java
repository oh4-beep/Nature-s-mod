package com.naturesmod.disaster;

import java.util.ArrayList;
import java.util.List;

import com.naturesmod.block.ModBlocks;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A wildfire, driven by the wind. It spreads column by column through what will burn: fastest with the
 * wind (the head of the fire), slowest against it (its back), faster uphill, through the crowns of
 * the trees as well as along the ground, and the wind carries embers ahead to start spot fires. Rain
 * damps it down. What it has burnt it leaves black: trunks charred, leaves gone, the ground scorched
 * and dusted with ash.
 */
public final class Wildfire extends Disaster {
	private final Vec3 wind;
	private final double windSpeed;
	private final double reach;
	/** Burning columns and the tick each burns out. */
	private final Long2IntMap burning = new Long2IntOpenHashMap();
	private final LongSet burnt = new LongOpenHashSet();
	private final LongArrayList queue = new LongArrayList();
	private int cursor;

	Wildfire(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.WILDFIRE, level, at, intensity, natural, (int) (2400 + 1200 * intensity));
		double angle = random.nextDouble() * Math.PI * 2.0;
		wind = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		windSpeed = 3.0 + 3.0 * intensity + random.nextDouble() * 3.0;
		reach = 30.0 + 25.0 * intensity;
	}

	@Override
	public float radius() {
		return (float) reach;
	}

	@Override
	public Vec3 direction() {
		return wind;
	}

	@Override
	public float strength() {
		return Math.min(1.0F, burning.size() / 30.0F);
	}

	@Override
	public float[] extra() {
		// The wind, how much is burning, and a few of the hottest spots for the smoke to rise from.
		int spots = Math.min(20, burning.size());
		float[] out = new float[2 + spots * 3];
		out[0] = (float) windSpeed;
		out[1] = burning.size();
		int i = 0;
		for (long key : burning.keySet()) {
			if (i >= spots) {
				break;
			}
			if (random.nextInt(Math.max(1, burning.size() / spots)) != 0) {
				continue;
			}
			int bx = BlockPos.getX(key);
			int bz = BlockPos.getZ(key);
			out[2 + i * 3] = bx + 0.5F;
			out[3 + i * 3] = BlockPos.getY(key);
			out[4 + i * 3] = bz + 0.5F;
			i++;
		}
		if (i < spots) {
			float[] trimmed = new float[2 + i * 3];
			System.arraycopy(out, 0, trimmed, 0, trimmed.length);
			return trimmed;
		}
		return out;
	}

	@Override
	protected void begin() {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		boolean lit = false;
		for (int r = 0; r <= 8 && !lit; r++) {
			for (int i = 0; i < 16 && !lit; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				lit = ignite(bx + (int) Math.round(Math.cos(angle) * r), bz + (int) Math.round(Math.sin(angle) * r));
			}
		}
		if (!lit) {
			end();
			return;
		}
		warn(400, player -> Component.translatable("naturesmod.warning.wildfire", (int) distance2d(player, x, z),
				compass(player.getX(), player.getZ(), x, z), compass(wind.x, wind.z)));
	}

	@Override
	protected void tick() {
		if (burning.isEmpty()) {
			end();
			return;
		}
		boolean rain = level.isRaining();
		int budget = Math.min(queue.size(), 48);
		for (int n = 0; n < budget; n++) {
			if (queue.isEmpty()) {
				break;
			}
			if (cursor >= queue.size()) {
				cursor = 0;
			}
			long key = queue.getLong(cursor);
			if (!burning.containsKey(key)) {
				queue.removeLong(cursor);
				continue;
			}
			cursor++;
			int bx = BlockPos.getX(key);
			int bz = BlockPos.getZ(key);
			if (age >= burning.get(key) || rain && random.nextInt(4) == 0) {
				burnOut(bx, bz);
				burning.remove(key);
				continue;
			}
			spread(bx, BlockPos.getY(key), bz, rain);
		}
	}

	private void spread(int bx, int by, int bz, boolean rain) {
		double damp = rain ? 0.05 : 1.0;
		double drive = windSpeed / 5.0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				int tx = bx + dx;
				int tz = bz + dz;
				double length = Math.sqrt(dx * dx + dz * dz);
				double along = (dx * wind.x + dz * wind.z) / length;
				double windFactor = Math.exp(drive * along) / Math.exp(drive * 0.5);
				int th = loaded(tx, tz) ? ground(tx, tz) : by;
				double slope = Math.max(0.5, Math.min(2.0, Math.exp(0.18 * (th - by))));
				double chance = 0.05 * windFactor * slope * damp;
				if (random.nextDouble() < chance) {
					ignite(tx, tz);
				}
			}
		}
		// Embers on the wind start spot fires ahead of the front.
		if (random.nextDouble() < 0.003 * windSpeed * damp) {
			double distance = 6.0 + random.nextDouble() * (10.0 + windSpeed * 2.0);
			double sideways = random.nextGaussian() * distance * 0.25;
			ignite((int) Math.floor(bx + wind.x * distance - wind.z * sideways), (int) Math.floor(bz + wind.z * distance + wind.x * sideways));
		}
	}

	/** Sets a column alight, if there is anything in it to burn. */
	private boolean ignite(int bx, int bz) {
		long column = BlockPos.asLong(bx, 0, bz);
		if (burnt.contains(column) || !loaded(bx, bz)) {
			return false;
		}
		double dx = bx - x;
		double dz = bz - z;
		if (dx * dx + dz * dz > reach * reach) {
			return false;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
		BlockPos topPos = new BlockPos(bx, top, bz);
		BlockState state = level.getBlockState(topPos);
		BlockPos above = topPos.above();
		BlockState aboveState = level.getBlockState(above);
		boolean crown = state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
		boolean surface = state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.ignitedByLava() || aboveState.getBlock() instanceof VegetationBlock
				|| aboveState.is(BlockTags.FLOWERS) || aboveState.canBeReplaced() && !aboveState.isAir() && aboveState.getFluidState().isEmpty();
		if (!crown && !surface || !state.getFluidState().isEmpty()) {
			return false;
		}
		long key = BlockPos.asLong(bx, top, bz);
		if (griefing()) {
			BlockPos firePos = aboveState.isAir() ? above : aboveState.canBeReplaced() ? above : null;
			if (firePos != null) {
				level.setBlock(firePos, BaseFireBlock.getState(level, firePos), Block.UPDATE_ALL);
			}
			if (crown) {
				// Fire in the crown: flames along the canopy's edges too.
				for (int i = 0; i < 3; i++) {
					BlockPos side = topPos.offset(random.nextInt(3) - 1, -random.nextInt(3), random.nextInt(3) - 1);
					if (level.getBlockState(side).isAir() && level.getBlockState(side.below()).ignitedByLava()) {
						level.setBlock(side, BaseFireBlock.getState(level, side), Block.UPDATE_ALL);
					}
				}
			}
		}
		burning.put(key, age + (crown ? 160 + random.nextInt(120) : 60 + random.nextInt(80)));
		burnt.add(column);
		queue.add(key);
		return true;
	}

	/** What is left once the fire has passed. */
	private void burnOut(int bx, int bz) {
		if (!griefing() || !loaded(bx, bz)) {
			return;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int by = top + 1; by > top - 24 && by > level.getMinY(); by--) {
			BlockState state = level.getBlockState(pos.set(bx, by, bz));
			if (state.isAir()) {
				continue;
			}
			if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof VegetationBlock || state.is(Blocks.FIRE)
					|| state.canBeReplaced() && state.getFluidState().isEmpty()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			} else if (state.is(BlockTags.LOGS)) {
				BlockState charred = ModBlocks.CHARRED_LOG.defaultBlockState();
				if (state.hasProperty(RotatedPillarBlock.AXIS)) {
					charred = charred.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
				}
				level.setBlock(pos, charred, Block.UPDATE_ALL);
			} else {
				if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)) {
					level.setBlock(pos, random.nextInt(2) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState(),
							Block.UPDATE_ALL);
					if (random.nextInt(3) == 0 && level.getBlockState(pos.above()).isAir()) {
						level.setBlock(pos.above(), ModBlocks.ASH_LAYER.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1), Block.UPDATE_ALL);
					}
				}
				return;
			}
		}
	}

	@Override
	protected void finish() {
		// Whatever is still burning burns out.
		List<Long> left = new ArrayList<>(burning.keySet());
		for (long key : left) {
			burnOut(BlockPos.getX(key), BlockPos.getZ(key));
		}
		burning.clear();
	}

	/** How much of the land it has burnt, in columns (for the tests). */
	public int burntColumns() {
		return burnt.size();
	}
}
