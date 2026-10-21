package com.naturesmod.worldgen.tree;

import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import com.naturesmod.worldgen.WaterWorks;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Collects the logs and leaves of a procedurally grown tree, then writes them to the world in one
 * go. Leaves get correct decay distances, so the tree behaves exactly like a vanilla one: cut the
 * logs and the canopy decays.
 */
public final class TreeBuilder {
	private final WorldGenLevel level;
	private final WorldGenUtil.Window window;
	private final RandomSource random;
	private final Long2ObjectMap<BlockState> logs = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectMap<BlockState> leaves = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectMap<BlockState> decorations = new Long2ObjectOpenHashMap<>();
	private boolean blocked;

	public TreeBuilder(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random) {
		this.level = level;
		this.window = window;
		this.random = random;
	}

	public RandomSource random() {
		return random;
	}

	public WorldGenLevel level() {
		return level;
	}

	/** How far the tree may reach horizontally from (x, z) without leaving the writable window. */
	public double maxReach(int x, int z) {
		return Math.min(Math.min(x - window.minX(), window.maxX() - x), Math.min(z - window.minZ(), window.maxZ() - z));
	}

	/** True when a structural part of the tree (its trunk) ran into something solid. */
	public boolean blocked() {
		return blocked;
	}

	private boolean inBounds(int x, int y, int z) {
		return window.contains(x, z) && y > level.getMinY() && y < level.getMaxY();
	}

	private static boolean freeForTree(BlockState state) {
		return state.isAir() || WorldGenUtil.isClearable(state) || state.is(BlockTags.LEAVES) || state.is(Blocks.SNOW);
	}

	/**
	 * Adds a log. Structural logs (the trunk) mark the tree as blocked if they can't be placed;
	 * roots may also push into soft ground.
	 */
	public void log(int x, int y, int z, BlockState state, LogKind kind) {
		if (!inBounds(x, y, z)) {
			if (kind == LogKind.TRUNK) {
				blocked = true;
			}
			return;
		}
		long key = BlockPos.asLong(x, y, z);
		if (logs.containsKey(key)) {
			return;
		}
		BlockState existing = level.getBlockState(BlockPos.of(key));
		boolean free = freeForTree(existing);
		boolean soil = existing.is(BlockTags.SUBSTRATE_OVERWORLD) || existing.is(BlockTags.SAND) || existing.is(Blocks.GRAVEL);
		boolean ok = switch (kind) {
			case TRUNK -> free || soil;
			case BRANCH -> free;
			case ROOT -> free || soil;
		};
		if (!ok) {
			if (kind == LogKind.TRUNK) {
				blocked = true;
			}
			return;
		}
		logs.put(key, state);
		leaves.remove(key);
	}

	public void leaf(int x, int y, int z, BlockState state) {
		if (!inBounds(x, y, z)) {
			return;
		}
		long key = BlockPos.asLong(x, y, z);
		if (logs.containsKey(key) || leaves.containsKey(key)) {
			return;
		}
		BlockState existing = level.getBlockState(BlockPos.of(key));
		if (existing.isAir() || WorldGenUtil.isClearable(existing)) {
			leaves.put(key, state);
		}
	}

	/** Extra blocks (vines, litter...) placed after the tree itself if the spot is still free. */
	public void decoration(int x, int y, int z, BlockState state) {
		if (inBounds(x, y, z)) {
			decorations.putIfAbsent(BlockPos.asLong(x, y, z), state);
		}
	}

	public boolean isLog(int x, int y, int z) {
		return logs.containsKey(BlockPos.asLong(x, y, z));
	}

	public boolean isLeaf(int x, int y, int z) {
		return leaves.containsKey(BlockPos.asLong(x, y, z));
	}

	public Long2ObjectMap<BlockState> leaves() {
		return leaves;
	}

	public int logCount() {
		return logs.size();
	}

	/** Orients a log block along the dominant axis of a branch. */
	public static BlockState oriented(BlockState log, double dx, double dy, double dz) {
		if (!log.hasProperty(BlockStateProperties.AXIS)) {
			return log;
		}
		double ax = Math.abs(dx);
		double ay = Math.abs(dy);
		double az = Math.abs(dz);
		Direction.Axis axis = ay >= ax && ay >= az ? Direction.Axis.Y : ax >= az ? Direction.Axis.X : Direction.Axis.Z;
		return log.setValue(BlockStateProperties.AXIS, axis);
	}

	/** A tapering limb from {@code from} to {@code to}. */
	public void limb(Vec3 from, Vec3 to, double startRadius, double endRadius, BlockState log, LogKind kind) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		if (length < 0.01) {
			return;
		}
		BlockState state = oriented(log, delta.x, delta.y, delta.z);
		int steps = Math.max(1, (int) Math.ceil(length * 2));
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			Vec3 p = from.add(delta.scale(t));
			double r = Mth.lerp(t, startRadius, endRadius);
			disc(p, r, state, kind);
		}
	}

	/** A filled sphere of logs of the given radius (a cross-section of a thick limb). */
	public void disc(Vec3 center, double radius, BlockState state, LogKind kind) {
		if (radius < 0.75) {
			log(Mth.floor(center.x), Mth.floor(center.y), Mth.floor(center.z), state, kind);
			return;
		}
		int r = (int) Math.ceil(radius);
		double r2 = radius * radius;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					double ox = Mth.floor(center.x) + dx + 0.5 - center.x;
					double oy = Mth.floor(center.y) + dy + 0.5 - center.y;
					double oz = Mth.floor(center.z) + dz + 0.5 - center.z;
					if (ox * ox + oy * oy + oz * oz <= r2) {
						log(Mth.floor(center.x) + dx, Mth.floor(center.y) + dy, Mth.floor(center.z) + dz, state, kind);
					}
				}
			}
		}
	}

	/** A horizontal slice of trunk: every block whose centre lies within the radius. */
	public void trunkLayer(double cx, int y, double cz, double radius, BlockState state, LogKind kind) {
		int r = (int) Math.ceil(radius);
		double r2 = radius * radius;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double ox = Mth.floor(cx) + dx + 0.5 - cx;
				double oz = Mth.floor(cz) + dz + 0.5 - cz;
				if (ox * ox + oz * oz <= r2 || radius < 0.75 && dx == 0 && dz == 0) {
					log(Mth.floor(cx) + dx, y, Mth.floor(cz) + dz, state, kind);
				}
			}
		}
	}

	/** A lumpy cloud of leaves. */
	public void leafBlob(double cx, double cy, double cz, double rx, double ry, double rz, List<BlockState> palette, double density) {
		BlockState leafState = palette.get(random.nextInt(palette.size()));
		int ix = (int) Math.ceil(rx);
		int iy = (int) Math.ceil(ry);
		int iz = (int) Math.ceil(rz);
		for (int dx = -ix; dx <= ix; dx++) {
			for (int dy = -iy; dy <= iy; dy++) {
				for (int dz = -iz; dz <= iz; dz++) {
					double nx = dx / rx;
					double ny = dy / ry;
					double nz = dz / rz;
					double d = nx * nx + ny * ny + nz * nz;
					if (d > 1.0) {
						continue;
					}
					// Thin out the fringe so the canopy edge is ragged rather than a perfect ball.
					if (d > 0.55 && random.nextDouble() > density) {
						continue;
					}
					leaf(Mth.floor(cx) + dx, Mth.floor(cy) + dy, Mth.floor(cz) + dz, leafState);
				}
			}
		}
	}

	/**
	 * Writes everything to the world. Leaves that would be further than vanilla's decay distance
	 * from any log are dropped, the rest get their real distance so they never decay on their own.
	 */
	public void commit() {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Long2ObjectMap.Entry<BlockState> entry : logs.long2ObjectEntrySet()) {
			WaterWorks.set(level, pos.set(entry.getLongKey()), entry.getValue());
		}

		Long2IntMap distance = new Long2IntOpenHashMap();
		distance.defaultReturnValue(Integer.MAX_VALUE);
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		for (long key : logs.keySet()) {
			distance.put(key, 0);
			queue.enqueue(key);
		}
		while (!queue.isEmpty()) {
			long key = queue.dequeueLong();
			int d = distance.get(key);
			if (d >= LeavesBlock.DECAY_DISTANCE - 1) {
				continue;
			}
			for (Direction direction : Direction.values()) {
				long next = BlockPos.offset(key, direction);
				if (leaves.containsKey(next) && distance.get(next) > d + 1) {
					distance.put(next, d + 1);
					queue.enqueue(next);
				}
			}
		}
		for (Long2ObjectMap.Entry<BlockState> entry : leaves.long2ObjectEntrySet()) {
			int d = distance.get(entry.getLongKey());
			if (d >= LeavesBlock.DECAY_DISTANCE) {
				continue;
			}
			BlockState state = entry.getValue();
			if (state.hasProperty(LeavesBlock.DISTANCE)) {
				state = state.setValue(LeavesBlock.DISTANCE, d);
			}
			if (state.hasProperty(LeavesBlock.PERSISTENT)) {
				state = state.setValue(LeavesBlock.PERSISTENT, false);
			}
			WaterWorks.set(level, pos.set(entry.getLongKey()), state);
		}

		for (Long2ObjectMap.Entry<BlockState> entry : decorations.long2ObjectEntrySet()) {
			pos.set(entry.getLongKey());
			if (level.getBlockState(pos).isAir()) {
				WaterWorks.set(level, pos, entry.getValue());
			}
		}
	}

	public enum LogKind {
		/** Must be placeable or the tree is abandoned. */
		TRUNK,
		/** Skipped where blocked. */
		BRANCH,
		/** May replace soil, so roots can be half buried. */
		ROOT
	}
}
