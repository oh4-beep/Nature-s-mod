package com.naturesmod.tree;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.naturesmod.NaturesConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Makes trees topple. When the trunk of a natural tree is cut through, everything above the cut
 * swings down around the stump as falling blocks: the top travels furthest, so the trunk ends up
 * lying on the ground along the direction it fell, with its crown still attached.
 */
public final class TreeFeller {
	/** Gravity of falling blocks in blocks/tick^2. */
	private static final double GRAVITY = 0.04;
	private static final int MAX_FALLING_LEAVES = 450;
	private static final int MAX_FALLING_LOGS = 1400;
	private static final int MAX_LEAVES = 9000;

	private TreeFeller() {
	}

	/** A connected group of logs plus the leaves that belong to it. */
	public record TreePart(LongList logs, LongList leaves, boolean grounded, boolean natural, int minY, int maxY) {
	}

	public static boolean isLog(BlockState state) {
		return state.is(BlockTags.LOGS);
	}

	private static boolean isNaturalLeaf(BlockState state) {
		return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
	}

	/** Blocks a trunk can stand on. */
	private static boolean supports(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES) && !isLog(state);
	}

	/**
	 * Called after the log at {@code cutPos} disappeared. Every natural tree part that no longer
	 * touches the ground falls in {@code direction}. Returns the number of logs that fell.
	 */
	public static int fellAround(ServerLevel level, BlockPos cutPos, Vec3 direction) {
		return fellAround(level, cutPos, direction, NaturesConfig.get().maxTreeLogs, MAX_FALLING_LEAVES, MAX_FALLING_LOGS);
	}

	/**
	 * As {@link #fellAround(ServerLevel, BlockPos, Vec3)}, for trees of at most {@code maxLogs} logs, of
	 * which at most {@code fallingLeaves} leaves and {@code fallingLogs} logs fall as blocks.
	 */
	public static int fellAround(ServerLevel level, BlockPos cutPos, Vec3 direction, int maxLogs, int fallingLeaves, int fallingLogs) {
		NaturesConfig config = NaturesConfig.get();
		LongSet visited = new LongOpenHashSet();
		int felled = 0;
		for (int dy = 1; dy >= -1; dy--) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos start = cutPos.offset(dx, dy, dz);
					if (visited.contains(start.asLong()) || !isLog(level.getBlockState(start))) {
						continue;
					}
					TreePart part = scan(level, start, visited, maxLogs);
					if (part == null || part.grounded() || !part.natural()) {
						continue;
					}
					topple(level, part, cutPos, direction, config.fallingTreesHurt, fallingLeaves, fallingLogs);
					felled += part.logs().size();
				}
			}
		}
		return felled;
	}

	/** Collects the logs connected to {@code start}. Returns null for structures too big to be a tree. */
	public static TreePart scan(ServerLevel level, BlockPos start, LongSet visited, int maxLogs) {
		LongList logs = new LongArrayList();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		queue.enqueue(start.asLong());
		visited.add(start.asLong());
		boolean grounded = false;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean tooBig = false;

		while (!queue.isEmpty()) {
			long key = queue.dequeueLong();
			logs.add(key);
			if (logs.size() > maxLogs) {
				tooBig = true;
				break;
			}
			int x = BlockPos.getX(key);
			int y = BlockPos.getY(key);
			int z = BlockPos.getZ(key);
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
			if (!grounded && supports(level.getBlockState(pos.set(x, y - 1, z)))) {
				grounded = true;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						long next = BlockPos.asLong(x + dx, y + dy, z + dz);
						if (visited.contains(next)) {
							continue;
						}
						if (isLog(level.getBlockState(pos.set(next)))) {
							visited.add(next);
							queue.enqueue(next);
						}
					}
				}
			}
		}
		if (tooBig) {
			return null;
		}

		// Leaves whose nearest wood is this part of the tree.
		LongList leaves = new LongArrayList();
		Long2IntMap depth = new Long2IntOpenHashMap();
		LongArrayFIFOQueue leafQueue = new LongArrayFIFOQueue();
		int naturalLeaves = 0;
		for (int i = 0; i < logs.size(); i++) {
			long key = logs.getLong(i);
			for (Direction direction : Direction.values()) {
				long next = BlockPos.offset(key, direction);
				if (depth.containsKey(next)) {
					continue;
				}
				BlockState state = level.getBlockState(pos.set(next));
				if (isNaturalLeaf(state)) {
					depth.put(next, 1);
					leafQueue.enqueue(next);
					naturalLeaves++;
				}
			}
		}
		while (!leafQueue.isEmpty() && leaves.size() < MAX_LEAVES) {
			long key = leafQueue.dequeueLong();
			int d = depth.get(key);
			BlockState state = level.getBlockState(pos.set(key));
			int own = state.hasProperty(LeavesBlock.DISTANCE) ? state.getValue(LeavesBlock.DISTANCE) : LeavesBlock.DECAY_DISTANCE;
			if (d > own) {
				continue; // closer to some other tree
			}
			leaves.add(key);
			if (d >= 6) {
				continue;
			}
			for (Direction direction : Direction.values()) {
				long next = BlockPos.offset(key, direction);
				if (depth.containsKey(next)) {
					continue;
				}
				if (isNaturalLeaf(level.getBlockState(pos.set(next)))) {
					depth.put(next, d + 1);
					leafQueue.enqueue(next);
				}
			}
		}
		return new TreePart(logs, leaves, grounded, naturalLeaves >= 2, minY, maxY);
	}

	/** Swings a tree part down around its base. */
	public static void topple(ServerLevel level, TreePart part, BlockPos pivot, Vec3 direction, boolean hurts) {
		topple(level, part, pivot, direction, hurts, MAX_FALLING_LEAVES, MAX_FALLING_LOGS);
	}

	/** As above, with at most so many leaves and logs falling as blocks; the rest of the leaves are shredded, the rest of the logs dropped. */
	public static void topple(ServerLevel level, TreePart part, BlockPos pivot, Vec3 direction, boolean hurts, int maxFallingLeaves,
			int maxFallingLogs) {
		RandomSource random = level.getRandom();
		Vec3 dir = new Vec3(direction.x, 0, direction.z);
		if (dir.lengthSqr() < 1.0e-6) {
			dir = new Vec3(1, 0, 0);
		}
		dir = dir.normalize();
		Direction.Axis fallAxis = Math.abs(dir.x) >= Math.abs(dir.z) ? Direction.Axis.X : Direction.Axis.Z;
		double pivotX = pivot.getX() + 0.5;
		double pivotZ = pivot.getZ() + 0.5;
		int base = part.minY();
		int height = part.maxY() - base + 1;

		// Crack!
		BlockPos soundPos = pivot.above();
		level.playSound(null, soundPos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.8f, 0.5f + random.nextFloat() * 0.15f);
		level.playSound(null, soundPos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.4f, 0.55f);
		level.playSound(null, soundPos.above(Math.min(height, 12)), SoundEvents.AZALEA_LEAVES_FALL, SoundSource.BLOCKS, 1.5f, 0.6f);

		// Leaves first, so falling logs never leave a floating canopy behind.
		LongList leafOrder = part.leaves();
		int falling = 0;
		int keepEvery = Math.max(1, leafOrder.size() / Math.max(1, maxFallingLeaves));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < leafOrder.size(); i++) {
			pos.set(leafOrder.getLong(i));
			BlockState state = level.getBlockState(pos);
			if (!state.is(BlockTags.LEAVES)) {
				continue;
			}
			if (i % keepEvery == 0 && falling < maxFallingLeaves) {
				BlockState landed = state;
				if (landed.hasProperty(LeavesBlock.DISTANCE)) {
					landed = landed.setValue(LeavesBlock.DISTANCE, LeavesBlock.DECAY_DISTANCE);
				}
				if (landed.hasProperty(LeavesBlock.WATERLOGGED)) {
					landed = landed.setValue(LeavesBlock.WATERLOGGED, false);
				}
				FallingBlockEntity entity = FallingBlockEntity.fall(level, pos.immutable(), landed);
				entity.disableDrop();
				launch(entity, pos, base, pivotX, pivotZ, dir, random, 0.18);
				falling++;
			} else {
				level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				if (random.nextInt(12) == 0) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
							pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
				}
			}
		}

		int logsFalling = 0;
		for (int i = 0; i < part.logs().size(); i++) {
			pos.set(part.logs().getLong(i));
			BlockState state = level.getBlockState(pos);
			if (!isLog(state)) {
				continue;
			}
			if (logsFalling >= maxFallingLogs) {
				Block.dropResources(state, level, pos);
				level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				continue;
			}
			FallingBlockEntity entity = FallingBlockEntity.fall(level, pos.immutable(), rotate(state, fallAxis));
			if (hurts) {
				entity.setHurtsEntities(1.0f, 12);
			}
			launch(entity, pos, base, pivotX, pivotZ, dir, random, 0.04);
			logsFalling++;
		}

		// The crash when the crown hits the ground.
		double landingTicks = Math.sqrt(2 * height / GRAVITY);
		Vec3 crash = new Vec3(pivotX + dir.x * height * 0.75, base, pivotZ + dir.z * height * 0.75);
		ServerScheduler.schedule((int) landingTicks, () -> {
			BlockPos at = BlockPos.containing(crash);
			level.playSound(null, at, SoundEvents.GENERIC_BIG_FALL, SoundSource.BLOCKS, 2.0f, 0.5f);
			level.playSound(null, at, SoundEvents.WOOD_FALL, SoundSource.BLOCKS, 2.0f, 0.45f);
			level.playSound(null, at, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.BLOCKS, 1.5f, 0.7f);
			level.sendParticles(ParticleTypes.POOF, crash.x, crash.y + 1, crash.z, 25, height * 0.2, 0.5, height * 0.2, 0.05);
		});
	}

	/**
	 * Gives a block the velocity it would have if the tree were rotating about its base: blocks
	 * high up travel further, so each one lands roughly where it would lie on the ground.
	 */
	private static void launch(FallingBlockEntity entity, BlockPos pos, int base, double pivotX, double pivotZ, Vec3 dir,
			RandomSource random, double jitter) {
		double h = pos.getY() - base + 1.0;
		double along = (pos.getX() + 0.5 - pivotX) * dir.x + (pos.getZ() + 0.5 - pivotZ) * dir.z;
		double speed = Math.sqrt(GRAVITY * h / 2) * 1.12 * (1 + (random.nextDouble() - 0.5) * jitter);
		double vy = Mth.clamp(-along * Math.sqrt(GRAVITY / (2 * h)) * 0.6, -0.4, 0.25) + 0.04;
		double side = (random.nextDouble() - 0.5) * jitter * 0.3;
		entity.setDeltaMovement(dir.x * speed - dir.z * side, vy, dir.z * speed + dir.x * side);
	}

	/** A log lying in the fall direction: upright logs become horizontal and vice versa. */
	private static BlockState rotate(BlockState state, Direction.Axis fallAxis) {
		if (!state.hasProperty(BlockStateProperties.AXIS)) {
			return state;
		}
		Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
		if (axis == Direction.Axis.Y) {
			return state.setValue(BlockStateProperties.AXIS, fallAxis);
		}
		if (axis == fallAxis) {
			return state.setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
		}
		return state;
	}
}
