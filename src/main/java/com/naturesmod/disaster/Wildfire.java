package com.naturesmod.disaster;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.naturesmod.NaturesConfig;
import com.naturesmod.block.ModBlocks;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
 * A wildfire, driven by the wind. It starts as a front across the wind and spreads column by column
 * through what will burn: its head runs with the wind, fast (a block and a half a second for the
 * smallest, four for the worst), its flanks slower and its back hardly at all; faster uphill, through
 * the crowns of the trees as well as along the ground; and the wind carries embers far ahead of the
 * front to start spot fires. Rain damps it down and puts it out. What it has burnt it leaves black:
 * trunks charred, leaves gone, the ground scorched and dusted with ash. Whatever stands where it burns
 * catches fire, and a player it is closing in on is told which way to get out of its way.
 *
 * <p>It is bounded however big it grows. It works through so many burning columns a tick (the more are
 * burning, the more likely each is to spread when its turn comes, so that it runs as fast), so many may
 * burn at once, so many blocks change in a tick, and it burns no further than so far from where it
 * started (furthest downwind, least upwind). The flames are the game's own fire, so many at once and
 * only where a player is near to see them: it keeps watch over every fire it lit and over the fire that
 * spreads from them by itself, taking that in hand while there is room under its cap and on land it may
 * burn, and putting it out where there isn't, so that the game's fire spread never runs away with the
 * forest. When it ends its flames go out.
 */
public final class Wildfire extends Disaster {
	/**
	 * Where in {@link #extra()} the burning spots start, after the numbers every wildfire sends: the wind's speed, how
	 * many columns are burning, the middle of the burning (x, y, z), how far it is spread about that, and the number
	 * of spots. Each spot takes three numbers: x, the height its flames stand on, z.
	 */
	public static final int SPOTS_AT = 7;
	/** Burning columns worked through in a tick, however many are burning. */
	private static final int COLUMNS_PER_TICK = 120;
	/** Blocks changed in a tick, all its work together: flames lit and put out, and the land left burnt. */
	private static final int BLOCKS_PER_TICK = 240;
	/** Blocks it may change as it ends, leaving black what was still burning. */
	private static final int BLOCKS_AT_END = 2400;
	/** Fires looked over in a tick at the least for fire that has spread from them by itself (more when there are many, see {@link #watchFlames()}). */
	private static final int FLAMES_WATCHED_PER_TICK = 6;
	/** It sets flames only where a player is within this many blocks to see them; elsewhere it burns without. */
	private static final double WATCHED = 160.0;
	/** Spots sent to the clients: the burning nearest the players (a share each, of the first four), and some from all over. */
	private static final int NEAR_SPOTS = 12;
	private static final int FAR_SPOTS = 20;
	/** For its last ticks it spreads no more, and burns out. */
	private static final int DYING = 600;

	/** On its scale (1 to 5), whatever it was called up with. */
	private final float rank;
	private final Vec3 wind;
	private final double windSpeed;
	private final double reach;
	/** The middle of the land it may burn: downwind of where it started, so that it runs far with the wind and little against it. */
	private final double reachX;
	private final double reachZ;
	/** How fast its head runs with the wind, in blocks a tick. */
	private final double headSpeed;
	/** How strongly the wind shapes it: the higher, the narrower and further it runs downwind. */
	private final double shape;
	/** The most columns burning at once, the most fires (blocks of fire) it keeps alight at once, and spot fires a second. */
	private final int maxBurning;
	private final int maxFlames;
	private final int embersPerSecond;
	/** Where it is lit: all round a ring this far from where it starts (round a meteor's crater), or there itself if 0. */
	private final double kindleRing;
	/** Burning columns and the tick each burns out. */
	private final Long2IntMap burning = new Long2IntOpenHashMap();
	/** The height of the top of each burning column: what its flames stand on. */
	private final Long2IntMap tops = new Long2IntOpenHashMap();
	private final LongSet burnt = new LongOpenHashSet();
	private final LongArrayList queue = new LongArrayList();
	private int cursor;
	/** The fires it keeps watch over: those it lit, and those it has taken in hand. */
	private final LongArrayList flames = new LongArrayList();
	private final LongSet flameSet = new LongOpenHashSet();
	private int flameCursor;
	private final Set<UUID> warnedNear = new HashSet<>();
	private int blocksLeft;
	private int emberSecond = -1;
	private int embersThisSecond;
	private int spotFires;
	private int flamesLit;
	private int flamesPutOut;

	Wildfire(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		this(level, at, intensity, natural, 0.0);
	}

	Wildfire(ServerLevel level, Vec3 at, float intensity, boolean natural, double kindleRing) {
		super(DisasterType.WILDFIRE, level, at, intensity, natural, (int) (3000 + 1200 * rated(intensity)));
		this.rank = rated(intensity);
		double angle = random.nextDouble() * Math.PI * 2.0;
		wind = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		windSpeed = 4.0 + 3.0 * rank + random.nextDouble() * 3.0;
		reach = 60.0 + 40.0 * rank;
		reachX = x + wind.x * reach * 0.45;
		reachZ = z + wind.z * reach * 0.45;
		headSpeed = 0.05 + 0.03 * rank;
		shape = 0.6 + windSpeed / 12.0;
		maxBurning = (int) (160 + 90 * rank);
		maxFlames = (int) (40 + 28 * rank);
		embersPerSecond = 1 + Math.round(rank);
		this.kindleRing = Double.isFinite(kindleRing) ? Math.max(0.0, Math.min(48.0, kindleRing)) : 0.0;
	}

	private static float rated(float intensity) {
		return Float.isNaN(intensity) ? DisasterType.WILDFIRE.normal : DisasterType.WILDFIRE.clamp(intensity);
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
		return Math.min(1.0F, burning.size() / 40.0F);
	}

	@Override
	public float[] extra() {
		// The middle of the burning and how far it is spread about it (from where it started, to keep the numbers small).
		int n = burning.size();
		double sumX = 0.0;
		double sumY = 0.0;
		double sumZ = 0.0;
		double sumXX = 0.0;
		double sumZZ = 0.0;
		for (long column : burning.keySet()) {
			double cx = BlockPos.getX(column) + 0.5 - x;
			double cz = BlockPos.getZ(column) + 0.5 - z;
			sumX += cx;
			sumZ += cz;
			sumXX += cx * cx;
			sumZZ += cz * cz;
			sumY += tops.get(column);
		}
		double meanX = n > 0 ? sumX / n : 0.0;
		double meanZ = n > 0 ? sumZ / n : 0.0;
		double meanY = n > 0 ? sumY / n : y;
		double spread = n > 0 ? Math.sqrt(Math.max(0.0, sumXX / n - meanX * meanX + sumZZ / n - meanZ * meanZ)) : 0.0;
		LongArrayList spots = spots();
		float[] out = new float[SPOTS_AT + spots.size() * 3];
		out[0] = (float) windSpeed;
		out[1] = n;
		out[2] = (float) (x + meanX);
		out[3] = (float) meanY;
		out[4] = (float) (z + meanZ);
		out[5] = (float) spread;
		out[6] = spots.size();
		for (int i = 0; i < spots.size(); i++) {
			long column = spots.getLong(i);
			out[SPOTS_AT + i * 3] = BlockPos.getX(column) + 0.5F;
			out[SPOTS_AT + i * 3 + 1] = tops.get(column) + 1;
			out[SPOTS_AT + i * 3 + 2] = BlockPos.getZ(column) + 0.5F;
		}
		return out;
	}

	/** The burning the clients are told of: what is nearest each player (the flames they see close by), and a sample of the rest. */
	private LongArrayList spots() {
		LongArrayList chosen = new LongArrayList();
		if (burning.isEmpty()) {
			return chosen;
		}
		LongSet taken = new LongOpenHashSet();
		List<ServerPlayer> players = level.players();
		int each = players.isEmpty() ? 0 : Math.max(2, NEAR_SPOTS / Math.min(players.size(), 4));
		int served = 0;
		for (ServerPlayer player : players) {
			if (served >= 4) {
				break;
			}
			served++;
			long[] best = new long[each];
			double[] bestDistance = new double[each];
			int found = 0;
			for (long column : burning.keySet()) {
				double dx = BlockPos.getX(column) + 0.5 - player.getX();
				double dz = BlockPos.getZ(column) + 0.5 - player.getZ();
				double d = dx * dx + dz * dz;
				if (d > WATCHED * WATCHED || found == each && d >= bestDistance[each - 1]) {
					continue;
				}
				int at = found < each ? found : each - 1;
				while (at > 0 && bestDistance[at - 1] > d) {
					bestDistance[at] = bestDistance[at - 1];
					best[at] = best[at - 1];
					at--;
				}
				bestDistance[at] = d;
				best[at] = column;
				if (found < each) {
					found++;
				}
			}
			for (int i = 0; i < found; i++) {
				if (taken.add(best[i])) {
					chosen.add(best[i]);
				}
			}
		}
		// And a fair sample of all of it: the smoke and the glow seen from further off.
		int remaining = burning.size();
		int want = Math.min(FAR_SPOTS, remaining);
		for (long column : burning.keySet()) {
			if (want <= 0 || remaining <= 0) {
				break;
			}
			if (random.nextInt(remaining) < want) {
				if (taken.add(column)) {
					chosen.add(column);
				}
				want--;
			}
			remaining--;
		}
		return chosen;
	}

	@Override
	protected void begin() {
		blocksLeft = BLOCKS_PER_TICK;
		boolean lit = kindleRing > 0.0 ? kindleRound() : kindleFront();
		if (!lit) {
			end();
			return;
		}
		warn(reach + 300.0, player -> Component.translatable("naturesmod.warning.wildfire", (int) distance2d(player, x, z),
				compass(player.getX(), player.getZ(), x, z), compass(wind.x, wind.z)));
	}

	/** Lights the fire where it starts: the nearest thing that will burn, and a front across the wind through it. */
	private boolean kindleFront() {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		for (int r = 0; r <= 16; r += r < 4 ? 1 : 2) {
			for (int i = 0; i < 12; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				int tx = bx + (int) Math.round(Math.cos(angle) * r);
				int tz = bz + (int) Math.round(Math.sin(angle) * r);
				if (ignite(tx, tz)) {
					int half = 2 + Math.round(rank * 2.0F);
					for (int k = -half; k <= half; k++) {
						if (k != 0) {
							ignite((int) Math.floor(tx + 0.5 - wind.z * k), (int) Math.floor(tz + 0.5 + wind.x * k));
						}
					}
					return true;
				}
			}
		}
		return false;
	}

	/** Lights what will burn all round just outside a ring: the land set alight by the heat of a strike. */
	private boolean kindleRound() {
		boolean lit = false;
		int points = 10 + (int) (kindleRing * 0.6);
		for (int i = 0; i < points; i++) {
			double angle = (i + random.nextDouble() * 0.6) * Math.PI * 2.0 / points;
			for (int step = 0; step < 4; step++) {
				double d = kindleRing + step * 2.0 + random.nextDouble() * 2.0;
				if (ignite((int) Math.floor(x + Math.cos(angle) * d), (int) Math.floor(z + Math.sin(angle) * d))) {
					lit = true;
					break;
				}
			}
		}
		return lit;
	}

	@Override
	protected void tick() {
		blocksLeft = BLOCKS_PER_TICK;
		if (burning.isEmpty()) {
			end();
			return;
		}
		// The watch over the game's own fire spread comes first, so that it always has blocks left to put fire out with.
		watchFlames();
		boolean rain = level.isRaining();
		boolean dying = age >= duration - DYING;
		// Each burning column has its turn every `pass` ticks or so, and spreads as much at it as it would have in all of them.
		double pass = Math.max(1.0, queue.size() / (double) COLUMNS_PER_TICK);
		int budget = Math.min(queue.size(), COLUMNS_PER_TICK);
		for (int n = 0; n < budget && !queue.isEmpty(); n++) {
			if (cursor >= queue.size()) {
				cursor = 0;
			}
			long column = queue.getLong(cursor);
			if (!burning.containsKey(column)) {
				queue.removeLong(cursor);
				continue;
			}
			int bx = BlockPos.getX(column);
			int bz = BlockPos.getZ(column);
			if (age >= burning.get(column) || rain && random.nextDouble() < 0.06 * pass) {
				if (griefing() && blocksLeft < 32) {
					// No budget left to leave it burnt this tick: on its next turn.
					cursor++;
					continue;
				}
				blocksLeft -= burnOut(bx, bz);
				burning.remove(column);
				tops.remove(column);
				queue.removeLong(cursor);
				continue;
			}
			cursor++;
			if (!dying) {
				spread(bx, bz, rain, pass);
			}
		}
		if (age % 10 == 0) {
			scorch();
		}
	}

	private void spread(int bx, int bz, boolean rain, double pass) {
		double damp = rain ? 0.08 : 1.0;
		int here = Integer.MIN_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				int tx = bx + dx;
				int tz = bz + dz;
				if (burnt.contains(column(tx, tz))) {
					continue;
				}
				double length = dx != 0 && dz != 0 ? Math.sqrt(2.0) : 1.0;
				double along = (dx * wind.x + dz * wind.z) / length;
				double chance = headSpeed * Math.exp(shape * (along - 1.0)) / length * damp * pass;
				double roll = random.nextDouble();
				// Not even uphill: most neighbours are passed over here, before the land is looked at.
				if (roll >= Math.min(0.95, chance * 2.5) || !loaded(tx, tz)) {
					continue;
				}
				if (here == Integer.MIN_VALUE) {
					here = ground(bx, bz);
				}
				double slope = Math.max(0.5, Math.min(2.5, Math.exp(0.2 * (ground(tx, tz) - here))));
				if (roll < Math.min(0.95, chance * slope)) {
					ignite(tx, tz);
				}
			}
		}
		// Embers on the wind start spot fires ahead of the front.
		if (!rain && random.nextDouble() < Math.min(0.5, 0.0012 * windSpeed * pass) && ember()) {
			double distance = 8.0 + random.nextDouble() * (12.0 + windSpeed * 3.0);
			double sideways = random.nextGaussian() * distance * 0.2;
			if (ignite((int) Math.floor(bx + 0.5 + wind.x * distance - wind.z * sideways), (int) Math.floor(bz + 0.5 + wind.z * distance + wind.x * sideways))) {
				spotFires++;
			}
		}
	}

	/** Whether another ember may start a spot fire this second. */
	private boolean ember() {
		int second = age / 20;
		if (second != emberSecond) {
			emberSecond = second;
			embersThisSecond = 0;
		}
		if (embersThisSecond >= embersPerSecond) {
			return false;
		}
		embersThisSecond++;
		return true;
	}

	private static long column(int bx, int bz) {
		return BlockPos.asLong(bx, 0, bz);
	}

	/** Whether a column lies in the land it may burn. */
	private boolean inBounds(int bx, int bz) {
		double dx = bx + 0.5 - reachX;
		double dz = bz + 0.5 - reachZ;
		return dx * dx + dz * dz <= reach * reach;
	}

	/** Whether a player (a spectator too: they see it) is near enough to see flames at (bx, bz). */
	private boolean watched(int bx, int bz) {
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - bx;
			double dz = player.getZ() - bz;
			if (dx * dx + dz * dz < WATCHED * WATCHED) {
				return true;
			}
		}
		return false;
	}

	/** Sets a column alight, if there is anything in it to burn and room for more to burn. */
	private boolean ignite(int bx, int bz) {
		long column = column(bx, bz);
		if (burnt.contains(column) || burning.size() >= maxBurning || !inBounds(bx, bz) || !workable(bx, bz)) {
			return false;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
		if (top <= level.getMinY() || top >= level.getMaxY() - 1) {
			return false;
		}
		BlockPos topPos = new BlockPos(bx, top, bz);
		BlockState state = level.getBlockState(topPos);
		BlockPos above = topPos.above();
		BlockState aboveState = level.getBlockState(above);
		if (!state.getFluidState().isEmpty() || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW) || aboveState.is(Blocks.SNOW)) {
			return false;
		}
		boolean crown = state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
		boolean surface = state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.ignitedByLava() || aboveState.getBlock() instanceof VegetationBlock
				|| aboveState.is(BlockTags.FLOWERS) || aboveState.canBeReplaced() && !aboveState.isAir() && aboveState.getFluidState().isEmpty();
		if (!crown && !surface) {
			return false;
		}
		if (griefing() && watched(bx, bz)) {
			if (aboveState.isAir() || aboveState.canBeReplaced() && aboveState.getFluidState().isEmpty()) {
				light(above);
			}
			if (crown) {
				// Fire in the crown: flames along the canopy's edges too.
				for (int i = 0; i < 2; i++) {
					int sx = bx + random.nextInt(3) - 1;
					int sz = bz + random.nextInt(3) - 1;
					if (!loaded(sx, sz)) {
						continue;
					}
					BlockPos side = new BlockPos(sx, top - random.nextInt(3), sz);
					if (level.getBlockState(side).isAir() && level.getBlockState(side.below()).ignitedByLava()) {
						light(side);
					}
				}
			}
		}
		burning.put(column, age + (crown ? 160 + random.nextInt(120) : 60 + random.nextInt(80)));
		tops.put(column, top);
		burnt.add(column);
		queue.add(column);
		return true;
	}

	/** Sets a fire, if there is room under the cap and in this tick's budget, and keeps watch over it. */
	private void light(BlockPos pos) {
		if (flameSet.size() >= maxFlames || blocksLeft <= 0) {
			return;
		}
		BlockState under = level.getBlockState(pos.below());
		if (under.is(Blocks.MAGMA_BLOCK) || under.is(Blocks.NETHERRACK)) {
			// Fire on these would burn for ever.
			return;
		}
		level.setBlock(pos, BaseFireBlock.getState(level, pos), Block.UPDATE_ALL);
		blocksLeft--;
		flamesLit++;
		long key = pos.asLong();
		if (flameSet.add(key)) {
			flames.add(key);
		}
	}

	/**
	 * Looks over a few of its fires a tick, each again within some fifteen ticks: sooner than the game's fire spreads
	 * again (every thirty ticks or more), so that what has spread from one is found before it can spread any further
	 * itself. Fire that has spread from one by itself (the game's fire spreads up to four blocks above and one to the
	 * side) is taken in hand while there is room under the cap and it is on land the wildfire may burn, and put out
	 * otherwise. One that has gone out is looked round once more, for what it spread before it did, and forgotten.
	 */
	private void watchFlames() {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int watch = Math.min(flames.size(), Math.max(FLAMES_WATCHED_PER_TICK, flames.size() / 15 + 1));
		for (int n = 0; n < watch && !flames.isEmpty(); n++) {
			if (flameCursor >= flames.size()) {
				flameCursor = 0;
			}
			long key = flames.getLong(flameCursor);
			int fx = BlockPos.getX(key);
			int fy = BlockPos.getY(key);
			int fz = BlockPos.getZ(key);
			if (!loaded(fx, fz)) {
				// Out of reach for now: looked at again when it is back.
				flameCursor++;
				continue;
			}
			if (level.getBlockState(pos.set(fx, fy, fz)).is(Blocks.FIRE)) {
				flameCursor++;
			} else {
				flames.removeLong(flameCursor);
				flameSet.remove(key);
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (!loaded(fx + dx, fz + dz)) {
						continue;
					}
					for (int dy = -1; dy <= 4; dy++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						pos.set(fx + dx, fy + dy, fz + dz);
						if (!level.getBlockState(pos).is(Blocks.FIRE)) {
							continue;
						}
						long found = pos.asLong();
						if (flameSet.contains(found)) {
							continue;
						}
						if (flameSet.size() < maxFlames && inBounds(fx + dx, fz + dz)) {
							flameSet.add(found);
							flames.add(found);
						} else if (blocksLeft > 0) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
							blocksLeft--;
							flamesPutOut++;
						}
					}
				}
			}
		}
	}

	/** What is left once the fire has passed; returns how many blocks it changed. */
	private int burnOut(int bx, int bz) {
		if (!griefing() || !workable(bx, bz)) {
			return 0;
		}
		int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1, level.getMaxY() - 2);
		int changed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int by = top + 1; by > top - 24 && by > level.getMinY(); by--) {
			BlockState state = level.getBlockState(pos.set(bx, by, bz));
			if (state.isAir()) {
				continue;
			}
			if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof VegetationBlock || state.is(Blocks.FIRE)
					|| state.canBeReplaced() && state.getFluidState().isEmpty()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				changed++;
			} else if (state.is(BlockTags.LOGS)) {
				BlockState charred = ModBlocks.CHARRED_LOG.defaultBlockState();
				if (state.hasProperty(RotatedPillarBlock.AXIS)) {
					charred = charred.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
				}
				level.setBlock(pos, charred, Block.UPDATE_CLIENTS);
				changed++;
			} else {
				if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL)) {
					level.setBlock(pos, random.nextInt(2) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState(),
							Block.UPDATE_CLIENTS);
					changed++;
					if (random.nextInt(3) == 0 && level.getBlockState(pos.above()).isAir()) {
						level.setBlock(pos.above(), ModBlocks.ASH_LAYER.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1), Block.UPDATE_CLIENTS);
						changed++;
					}
				}
				return changed;
			}
		}
		return changed;
	}

	/**
	 * Twice a second: what stands where it burns catches fire, the players and the creatures about them; and a player it
	 * is closing in on is told, once, which way to get out of its way.
	 */
	private void scorch() {
		boolean warnings = NaturesConfig.get().disasterWarnings;
		double near = reach + 48.0;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			double dx = player.getX() - reachX;
			double dz = player.getZ() - reachZ;
			if (dx * dx + dz * dz > near * near) {
				continue;
			}
			if (!immune(player)) {
				catchFire(player);
			}
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24.0),
					e -> e.isAlive() && !(e instanceof Player))) {
				catchFire(entity);
			}
			if (warnings && !warnedNear.contains(player.getUUID()) && burningWithin(player, 24.0)) {
				warnedNear.add(player.getUUID());
				player.sendSystemMessage(Component.translatable("naturesmod.warning.wildfire_near", compass(wind.x, wind.z))
						.withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** Sets an entity alight if it stands in a burning column, about the height its flames are at. */
	private void catchFire(Entity entity) {
		long column = column((int) Math.floor(entity.getX()), (int) Math.floor(entity.getZ()));
		if (!burning.containsKey(column) || entity.isInWater()) {
			return;
		}
		int top = tops.get(column);
		if (entity.getY() > top + 3.0 || entity.getY() < top - 14.0) {
			return;
		}
		entity.igniteForSeconds(4.0F);
	}

	private boolean burningWithin(Entity entity, double range) {
		for (long column : burning.keySet()) {
			double dx = BlockPos.getX(column) + 0.5 - entity.getX();
			double dz = BlockPos.getZ(column) + 0.5 - entity.getZ();
			if (dx * dx + dz * dz < range * range) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void finish() {
		// Its flames go out (those it can reach) and so does any fire spread from them by itself; and as much of what is
		// still burning as is fair to change at once is left burnt.
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < flames.size(); i++) {
			long key = flames.getLong(i);
			int fx = BlockPos.getX(key);
			int fy = BlockPos.getY(key);
			int fz = BlockPos.getZ(key);
			if (!loaded(fx, fz)) {
				// Out of reach now: put out as soon as its land is back, not left to burn on as the game's own fire.
				leaveForLater(BlockPos.of(key), Blocks.FIRE, Blocks.AIR.defaultBlockState());
				continue;
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (!loaded(fx + dx, fz + dz)) {
						continue;
					}
					for (int dy = -1; dy <= 4; dy++) {
						if (level.getBlockState(pos.set(fx + dx, fy + dy, fz + dz)).is(Blocks.FIRE)) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
		flames.clear();
		flameSet.clear();
		int left = BLOCKS_AT_END;
		for (long column : burning.keySet()) {
			if (left <= 0) {
				break;
			}
			left -= burnOut(BlockPos.getX(column), BlockPos.getZ(column));
		}
		burning.clear();
		tops.clear();
		queue.clear();
	}

	// --- for the tests -----------------------------------------------------------------------

	/** How much of the land it has burnt, in columns (for the tests). */
	public int burntColumns() {
		return burnt.size();
	}

	/** Columns burning right now (never more than {@link #maxBurning()}). */
	public int burningColumns() {
		return burning.size();
	}

	public int maxBurning() {
		return maxBurning;
	}

	/** Fires it keeps watch over right now (never more than {@link #maxFlames()}). */
	public int flames() {
		return flameSet.size();
	}

	public int maxFlames() {
		return maxFlames;
	}

	/** Fires it has lit in all. */
	public int flamesLit() {
		return flamesLit;
	}

	/** Fire that spread by itself beyond its cap or its bounds, and was put out. */
	public int flamesPutOut() {
		return flamesPutOut;
	}

	/** Spot fires started by embers ahead of the front. */
	public int spotFires() {
		return spotFires;
	}
}
