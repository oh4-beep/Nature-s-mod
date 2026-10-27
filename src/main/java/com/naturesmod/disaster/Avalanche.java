package com.naturesmod.disaster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.naturesmod.NaturesConfig;
import com.naturesmod.tree.TreeFeller;
import com.naturesmod.worldgen.WorldGenUtil;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An avalanche, from level 1 (a slough of loose snow) to 5 (a whole mountainside letting go). It lets go
 * on the steep slope above where it is called up, within some hundred and fifty blocks: the snowier, the
 * higher and the more squarely it faces that way, the likelier. First the crack of the slab breaking
 * away and its roar; then its front races down the fall line, steered by the lie of the land as a
 * pyroclastic flow is, quickening on the steep to a block and more a tick and slowing on the flat and up
 * the far side, ten to forty blocks wide and running sixty to two hundred and fifty. Its front sweeps the
 * slope bare of loose snow and plants and snaps the trees in its way. What it catches it carries along,
 * tumbling and battered, and leaves where it drops it: a player lying in powder snow under a block of
 * packed snow, never deeper than can be dug or waded out of. Where it slows it lays its debris, a thick
 * blanket of packed snow with pockets of powder snow and layers on top, deepest in the lobe where it
 * comes to rest. Where the land is not snowy it is a landslide of the land itself instead (earth, rock,
 * sand or clay), and buries whoever it catches in a pocket in the rubble. With no slope above to come
 * down, it comes from the highest ground near, or forty blocks off up whatever rise there is, driven on
 * at whoever called it up. Under a roof of rock (the Nether) it keeps to the floor it starts on.
 *
 * <p>The work is bounded every tick: so many blocks changed in all (its debris is laid from a queue, a
 * column at a time, behind its front), so many columns of its front looked at, so many trees felled a
 * second and in all (and only where a player is near enough to see them fall), so many other things
 * swept along, looked for every few ticks.
 */
public final class Avalanche extends Disaster {
	/** What it is made of (the first of its numbers for the clients): snow, or for a landslide the land it is. */
	public static final int SNOW = 0;
	public static final int EARTH = 1;
	public static final int ROCK = 2;
	public static final int SAND = 3;
	public static final int CLAY = 4;
	/** How far along it is (the second of its numbers): the slab cracking, the front running, the debris settling. */
	public static final int CRACKING = 0;
	public static final int RUNNING = 1;
	public static final int SETTLING = 2;
	/** The most blocks laid over a buried player's feet: the two the player lies in and one over the head. */
	public static final int MAX_BURIAL = 3;
	/** Blocks changed in one tick, all its work together: the crown torn away, the track swept, the debris laid. */
	private static final int BLOCKS_PER_TICK = 240;
	/** How far from where it is called up it looks for a slope to let go from, and how finely. */
	private static final int SEARCH = 150;
	private static final int STEP = 6;
	/** Ticks from the crack to the slab moving off. */
	private static final int RELEASE = 30;
	/** Ticks it lies settling once it has stopped, its cloud sinking, before it is over. */
	private static final int SETTLE = 80;
	/** How the slope pulls its front on and the snow drags it back: blocks a tick gained each tick per block of drop per block of run, and lost. */
	private static final double GRAVITY = 0.05;
	private static final double FRICTION = 0.16;
	/** Things other than players swept along at once. */
	private static final int MAX_NEARBY = 48;
	/** Columns of debris waiting to be laid, and columns it has planned debris for, at most. */
	private static final int MAX_QUEUE = 8000;
	private static final int MAX_COLUMNS = 30000;
	/** The ways its front looks for the steepest way down, turning from its heading. */
	private static final double[] TURNS = {0.0, -0.35, 0.35, -0.7, 0.7, -1.1, 1.1};
	/** The four sides of a column. */
	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	/** No land in a column (or the column not loaded). */
	private static final int NONE = Integer.MIN_VALUE;

	private final int grade;
	/** Under a roof of rock, where the heightmaps are the roof: the land is looked for under where it is instead. */
	private final boolean ceiling;
	/** The top of the land where the slab broke away. */
	private final BlockPos release;
	private final int kind;
	/** A landslide's debris (snow's is its own), and what it lays where nothing is under the block to hold it. */
	private final BlockState[] rubble;
	private final BlockState stable;
	private final double width;
	private final double length;
	private final double maxSpeed;
	/** How far behind its front its dense flow reaches, and how deep it runs over the ground. */
	private final double body;
	private final double depth;
	/** How thick it lays its debris where it slows (in layers, eight to a block), and the most on any column. */
	private final int maxLayers;
	private final int maxTotal;
	private final int treeCap;
	/** Where it was called to when there was no slope to bring it down there: it is driven at it. */
	private final Vec3 aim;
	/** How far it runs whatever the land (driven on over the flat, if need be). */
	private final double minRun;
	private double dirX;
	private double dirZ;
	private double speed;
	private double travelled;
	/** The drop of the land ahead of its front, per block. */
	private double slope;
	/** How far along its run the next line of debris is laid. */
	private double nextLine;
	private int phase = CRACKING;
	private int stoppedAt = -1;
	private int blocksLeft;
	private int blocksLaid;
	private int treesFelled;
	private int buried;
	private int sweptNow;
	private final Deque<Deposit> deposits = new ArrayDeque<>();
	/** The layers of debris planned for each column it has passed over. */
	private final Long2IntMap laid = new Long2IntOpenHashMap();
	/** Columns a player was buried in: nothing more is laid over them. */
	private final LongSet graves = new LongOpenHashSet();
	private final Map<UUID, Caught> caught = new HashMap<>();
	/** Players it has already dropped: not to be caught again. */
	private final Set<UUID> dropped = new HashSet<>();
	/** Everything but the players in its flow, as last looked for. */
	private final List<Entity> nearby = new ArrayList<>();
	/** The players near enough for its work to matter to them (not to be buried too deep, to see the trees fall). */
	private final List<ServerPlayer> around = new ArrayList<>();

	/** A column of debris to lay: so many layers more, on the land nearest `near`. */
	private record Deposit(int x, int z, int layers, int near) {
	}

	/** Where it lets go, the way down from there, how far it must run whatever the land, and where it is driven (if anywhere). */
	private record Start(BlockPos at, double dirX, double dirZ, double minRun, Vec3 aim) {
	}

	/** A player it has caught: carried for so long at most. */
	private static final class Caught {
		final int hold;
		int ticks;

		Caught(int hold) {
			this.hold = hold;
		}
	}

	Avalanche(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.AVALANCHE, level, at, intensity, natural, 2400);
		double s = this.intensity;
		grade = Math.max(1, Math.min(5, Math.round(this.intensity)));
		width = 10.0 + 7.5 * (s - 1.0);
		length = (60.0 + 47.5 * (s - 1.0)) * (0.9 + random.nextDouble() * 0.2);
		maxSpeed = 1.0 + 0.125 * (s - 1.0);
		body = 10.0 + 3.0 * s;
		depth = 1.5 + 0.5 * s;
		maxLayers = (int) Math.round(8.0 * (1.2 + 0.45 * s));
		maxTotal = Math.min(36, (int) Math.round(maxLayers * 1.3));
		treeCap = 4 + 2 * grade;
		ceiling = level.dimension() == Level.NETHER || roofed(BlockPos.containing(at));
		Start start = start(at);
		release = start.at();
		double d = Math.sqrt(start.dirX() * start.dirX() + start.dirZ() * start.dirZ());
		dirX = d > 1.0E-6 ? start.dirX() / d : 1.0;
		dirZ = d > 1.0E-6 ? start.dirZ() / d : 0.0;
		minRun = start.minRun();
		aim = start.aim();
		x = release.getX() + 0.5;
		y = release.getY() + 1.0;
		z = release.getZ() + 0.5;
		boolean seen = loaded(release.getX(), release.getZ());
		BlockState ground = seen ? level.getBlockState(release) : Blocks.DIRT.defaultBlockState();
		kind = snowy(release) ? SNOW : landOf(ground);
		rubble = rubble(ground, seen);
		stable = switch (kind) {
			case SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
			case ROCK -> Blocks.COBBLESTONE.defaultBlockState();
			case SAND -> Blocks.SANDSTONE.defaultBlockState();
			default -> Blocks.COARSE_DIRT.defaultBlockState();
		};
	}

	// --- where it lets go ---------------------------------------------------------------------

	/** Where it lets go: the best slope above `at`, or failing one (on the flat, under a roof of rock) the highest ground near. */
	private Start start(Vec3 at) {
		int ax = (int) Math.floor(at.x);
		int az = (int) Math.floor(at.z);
		int near = (int) Math.floor(at.y) - 1;
		int base = surface(ax, az, near);
		if (base == NONE) {
			base = near;
		}
		Start found = ceiling ? null : findSlope(ax, az, base);
		return found != null ? found : rise(ax, az, base, at);
	}

	/**
	 * The slope it comes down, looked for over the land within {@link #SEARCH} blocks: the steeper, the higher above
	 * `base`, the snowier and the more squarely facing (ax, az) the better, nearer rather than further. Null if there is
	 * none (at least a dozen blocks up, and steeper than one in three).
	 */
	private Start findSlope(int ax, int az, int base) {
		int n = SEARCH / STEP;
		int size = 2 * n + 1;
		int[] h = new int[size * size];
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int di = i - n;
				int dj = j - n;
				h[i * size + j] = di * di + dj * dj > (n + 1) * (n + 1) ? NONE : top(ax + di * STEP, az + dj * STEP);
			}
		}
		Start best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int i = 1; i < size - 1; i++) {
			for (int j = 1; j < size - 1; j++) {
				int here = h[i * size + j];
				int east = h[(i + 1) * size + j];
				int west = h[(i - 1) * size + j];
				int south = h[i * size + j + 1];
				int north = h[i * size + j - 1];
				if (here == NONE || east == NONE || west == NONE || south == NONE || north == NONE) {
					continue;
				}
				int rise = here - base;
				if (rise < 12) {
					continue;
				}
				double gx = (east - west) / (2.0 * STEP);
				double gz = (south - north) / (2.0 * STEP);
				double steep = Math.sqrt(gx * gx + gz * gz);
				if (steep < 0.3) {
					continue;
				}
				double dx = (i - n) * STEP;
				double dz = (j - n) * STEP;
				double d = Math.sqrt(dx * dx + dz * dz);
				// The way it would come down, and whether that is toward (ax, az).
				double downX = -gx / steep;
				double downZ = -gz / steep;
				double facing = d < 1.0 ? 1.0 : -(downX * dx + downZ * dz) / d;
				// A slope coming down the far side of a ridge would never reach there.
				if (facing < -0.1) {
					continue;
				}
				double score = Math.min(rise, 80) + 40.0 * Math.min(steep, 1.2) + 40.0 * facing - 0.12 * d;
				// Snow could add no more than this: no need to look at it.
				if (score + 30.0 <= bestScore) {
					continue;
				}
				BlockPos top = new BlockPos(ax + (i - n) * STEP, here, az + (j - n) * STEP);
				BlockState state = level.getBlockState(top);
				if (state.is(BlockTags.LOGS) || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, top) < 0.0F) {
					continue;
				}
				score += snowy(top) ? 30.0 : 0.0;
				if (score > bestScore) {
					bestScore = score;
					best = new Start(top, downX, downZ, 30.0, null);
				}
			}
		}
		return best;
	}

	/**
	 * No slope above: it comes from the highest ground within a few dozen blocks, or on the flat from forty blocks off
	 * (up whatever rise there is), and is driven at `at`, to reach it whatever the land.
	 */
	private Start rise(int ax, int az, int base, Vec3 at) {
		double offset = random.nextDouble() * Math.PI * 2.0;
		int bestX = ax + (int) Math.round(Math.cos(offset) * 40.0);
		int bestZ = az + (int) Math.round(Math.sin(offset) * 40.0);
		int bestY = base;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int k = 0; k < 16; k++) {
			double angle = offset + k * Math.PI / 8.0;
			for (int d = 24; d <= 56; d += 16) {
				int bx = ax + (int) Math.round(Math.cos(angle) * d);
				int bz = az + (int) Math.round(Math.sin(angle) * d);
				int h = surface(bx, bz, base);
				if (h == NONE || h > base + 80) {
					continue;
				}
				// On the flat, forty blocks off.
				double score = h - base - (d == 40 ? 0.0 : 0.5);
				if (score > bestScore) {
					bestScore = score;
					bestX = bx;
					bestZ = bz;
					bestY = h;
				}
			}
		}
		double dx = at.x - (bestX + 0.5);
		double dz = at.z - (bestZ + 0.5);
		double d = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		return new Start(new BlockPos(bestX, bestY, bestZ), dx / d, dz / d, d + 20.0, at);
	}

	/** Whether the land at `from` lies under a roof of unbreakable rock, as in the Nether (whose heightmaps are its roof). */
	private boolean roofed(BlockPos from) {
		if (!loaded(from.getX(), from.getZ())) {
			return false;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, from.getX(), from.getZ()) - 1;
		if (top <= from.getY() + 4 || top >= level.getMaxY()) {
			return false;
		}
		BlockPos roof = new BlockPos(from.getX(), top, from.getZ());
		return level.getBlockState(roof).getDestroySpeed(level, roof) < 0.0F;
	}

	/** Whether snow lies on the land whose top block is at `top`, or it is cold enough there for it to. */
	private boolean snowy(BlockPos top) {
		if (!loaded(top.getX(), top.getZ()) || top.getY() + 1 >= level.getMaxY()) {
			return false;
		}
		BlockState state = level.getBlockState(top);
		BlockState over = level.getBlockState(top.above());
		return state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.PACKED_ICE) || over.is(Blocks.SNOW)
				|| over.is(Blocks.POWDER_SNOW) || level.getBiome(top.above()).value().coldEnoughToSnow(top.above(), level.getSeaLevel());
	}

	/** What a landslide on this ground is made of. */
	private static int landOf(BlockState ground) {
		if (ground.is(BlockTags.TERRACOTTA) || ground.is(Blocks.RED_SAND) || ground.is(Blocks.RED_SANDSTONE)) {
			return CLAY;
		}
		if (ground.is(BlockTags.SAND) || ground.is(Blocks.SANDSTONE)) {
			return SAND;
		}
		if (ground.is(BlockTags.SUBSTRATE_OVERWORLD) || ground.is(Blocks.CLAY) || ground.isAir() || ground.is(BlockTags.LOGS)
				|| ground.is(BlockTags.LEAVES)) {
			return EARTH;
		}
		return ROCK;
	}

	/**
	 * A landslide's debris: the land it tore away, broken up (under a slide of earth: soil, stones and gravel). Only
	 * natural ground is carried down as itself: not ore (to be had for nothing), ice (to melt into water) or what a
	 * player built.
	 */
	private BlockState[] rubble(BlockState ground, boolean seen) {
		BlockState own = ground.getBlock().defaultBlockState();
		boolean usable = seen && (WorldGenUtil.isTerrain(own) || own.is(BlockTags.BASE_STONE_NETHER)) && !own.isAir() && !own.hasBlockEntity()
				&& own.getFluidState().isEmpty() && !own.is(BlockTags.LOGS)
				&& !own.is(BlockTags.LEAVES) && !own.is(Blocks.GRASS_BLOCK) && !own.is(Blocks.PODZOL) && !own.is(Blocks.MYCELIUM)
				&& own.getDestroySpeed(level, release) >= 0.0F && ground.isCollisionShapeFullBlock(level, release);
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockState coarse = Blocks.COARSE_DIRT.defaultBlockState();
		BlockState gravel = Blocks.GRAVEL.defaultBlockState();
		BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
		return switch (kind) {
			case SNOW -> new BlockState[] {Blocks.SNOW_BLOCK.defaultBlockState()};
			case ROCK -> usable && !own.is(BlockTags.BASE_STONE_OVERWORLD) ? new BlockState[] {own, own, own, gravel}
					: new BlockState[] {cobble, cobble, gravel, gravel, usable ? own : Blocks.STONE.defaultBlockState(), coarse};
			case SAND -> usable ? new BlockState[] {own, own, own, Blocks.SANDSTONE.defaultBlockState()}
					: new BlockState[] {Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()};
			case CLAY -> usable ? new BlockState[] {own, own, Blocks.RED_SAND.defaultBlockState(), coarse}
					: new BlockState[] {Blocks.RED_SAND.defaultBlockState(), coarse, coarse};
			default -> new BlockState[] {dirt, dirt, coarse, coarse, gravel, cobble};
		};
	}

	// --- the land -----------------------------------------------------------------------------

	/** The heightmap's top of a column, or NONE where it isn't loaded or there is nothing: quick, for looking over a lot of land. */
	private int top(int bx, int bz) {
		if (!loaded(bx, bz)) {
			return NONE;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
		return top <= level.getMinY() + 1 ? NONE : top;
	}

	/**
	 * The top of the land in a column, under any trees, plants or loose snow on it (the bed of any water over it), or NONE
	 * where it isn't loaded or there is no land. Under a roof of rock it is the floor nearest under `near`.
	 */
	private int surface(int bx, int bz, int near) {
		if (!loaded(bx, bz)) {
			return NONE;
		}
		if (!ceiling) {
			int top = terrain(bx, bz);
			return top <= level.getMinY() + 1 ? NONE : top;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, Math.min(level.getMaxY() - 2, near + 6), bz);
		boolean open = false;
		for (int i = 0; i < 40 && pos.getY() > level.getMinY() + 1; i++) {
			BlockState state = level.getBlockState(pos);
			boolean solid = !state.getFluidState().isEmpty() || !state.getCollisionShape(level, pos).isEmpty();
			if (solid && open) {
				return pos.getY();
			}
			open = !solid;
			pos.move(0, -1, 0);
		}
		return NONE;
	}

	/** Water (or lava) beside or over a block, that would run into the gap it left (or land beside it not loaded, to be safe). */
	private boolean wet(int bx, int by, int bz) {
		if (!loaded(bx + 1, bz) || !loaded(bx - 1, bz) || !loaded(bx, bz + 1) || !loaded(bx, bz - 1)) {
			return true;
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		return !level.getFluidState(at.set(bx + 1, by, bz)).isEmpty() || !level.getFluidState(at.set(bx - 1, by, bz)).isEmpty()
				|| !level.getFluidState(at.set(bx, by, bz + 1)).isEmpty() || !level.getFluidState(at.set(bx, by, bz - 1)).isEmpty()
				|| !level.getFluidState(at.set(bx, by + 1, bz)).isEmpty();
	}

	/** Whether debris can go in a block: air, or something it buries (a plant, loose snow) with no water in it. */
	private static boolean free(BlockState state) {
		return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty() && !state.hasBlockEntity();
	}

	// --- the disaster -------------------------------------------------------------------------

	/** Half its width now: the slab as it breaks away, spreading as it runs. */
	private double half() {
		return width * (0.6 + 0.4 * Math.min(1.0, travelled / 40.0)) / 2.0;
	}

	/** How high its cloud billows over its front: higher the bigger and the faster it is, lower for the heavier dust of a landslide. */
	private double cloud() {
		return (4.0 + 3.0 * intensity) * (0.4 + 0.6 * Math.min(1.0, speed / maxSpeed)) * (kind == SNOW ? 1.0 : 0.6);
	}

	@Override
	public float radius() {
		return (float) (half() + body);
	}

	@Override
	public Vec3 direction() {
		return new Vec3(dirX, 0.0, dirZ);
	}

	@Override
	public float strength() {
		return switch (phase) {
			case CRACKING -> 0.3F;
			case RUNNING -> (float) Math.min(1.0, 0.35 + 0.65 * speed / maxSpeed);
			default -> (float) Math.max(0.0, 1.0 - (age - stoppedAt) / (double) SETTLE);
		};
	}

	/**
	 * For the clients: what it is made of (see {@link #SNOW}), how far along it is (see {@link #CRACKING}), its speed, half
	 * its width, how far behind its front its flow reaches, where it let go (x, y, z), the ticks since it stopped (-1 while
	 * it runs), how high its cloud billows, and its level.
	 */
	@Override
	public float[] extra() {
		return new float[] {kind, phase, (float) speed, (float) half(), (float) body, release.getX() + 0.5F, release.getY() + 1.0F,
				release.getZ() + 0.5F, stoppedAt < 0 ? -1.0F : age - stoppedAt, (float) cloud(), grade};
	}

	@Override
	protected void begin() {
		String key = kind == SNOW ? "naturesmod.warning.avalanche" : "naturesmod.warning.landslide";
		warn(320.0, player -> Component.translatable(key, (int) distance2d(player, x, z), compass(player.getX(), player.getZ(), x, z)));
	}

	@Override
	protected void tick() {
		blocksLeft = BLOCKS_PER_TICK;
		gather();
		if (phase == CRACKING) {
			if (age >= RELEASE) {
				phase = RUNNING;
				speed = 0.2;
				if (griefing()) {
					crown();
				}
			}
		} else if (phase == RUNNING) {
			boolean going = run();
			sweep();
			if (griefing()) {
				front();
				trail();
			}
			if (!going) {
				stop();
			}
		} else if (age - stoppedAt >= SETTLE && (deposits.isEmpty() || !griefing())) {
			end();
		}
		if (griefing()) {
			lay();
		}
	}

	@Override
	protected void finish() {
		// Stopped before it came to rest (by command): what it carried is let go where it is, and what of its debris is fair
		// to lay at once is laid.
		caught.clear();
		nearby.clear();
		if (griefing() && phase != CRACKING) {
			blocksLeft = 400;
			lay();
		}
		deposits.clear();
	}

	/**
	 * The players near enough for its work to matter to them: about its front, or anywhere back along its track, where
	 * the debris queued may still be being laid.
	 */
	private void gather() {
		around.clear();
		double reach = 128.0 + travelled;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && player.distanceToSqr(x, player.getY(), z) < reach * reach) {
				around.add(player);
			}
		}
	}

	// --- the front ----------------------------------------------------------------------------

	/** Moves its front on a tick: down the steepest way, quicker on the steep and slower on the flat. False once it has stopped. */
	private boolean run() {
		int here = surface((int) Math.floor(x), (int) Math.floor(z), (int) Math.floor(y) - 1);
		if (here == NONE) {
			return false;
		}
		steer(here);
		int ahead = surface((int) Math.floor(x + dirX * 4.0), (int) Math.floor(z + dirZ * 4.0), here);
		slope = ahead == NONE ? -2.0 : Math.max(-2.0, Math.min(2.0, (here - ahead) / 4.0));
		speed += GRAVITY * (slope - FRICTION);
		double spent = travelled / length;
		if (spent > 0.8) {
			// Toward the end of its run it has lost its snow (or its rock) along the way.
			speed -= 0.02 * (spent - 0.8) / 0.2;
		}
		if (travelled < minRun && slope > -0.75) {
			// Driven on over the flat (or on until it has run some way at all), unless the land rises like a wall before it.
			speed = Math.max(speed, aim != null ? 0.6 + 0.08 * grade : 0.35);
		}
		speed = Math.max(0.0, Math.min(maxSpeed, speed));
		x += dirX * speed;
		z += dirZ * speed;
		travelled += speed;
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int now = surface(bx, bz, here);
		if (now == NONE) {
			return false;
		}
		y = now + 1.0;
		if (!level.getFluidState(new BlockPos(bx, now + 1, bz)).isEmpty()) {
			// Into water: it slumps in and is soon spent, its cloud riding over the surface.
			speed *= 0.75;
			if (!ceiling) {
				y = Math.max(y, ground(bx, bz) + 1.0);
			}
		}
		// (Boxed in where it let go, by a wall of rock before it, it gives up after a few seconds.)
		return travelled < length && (speed > 0.05 || travelled < 4.0 && age < RELEASE + 60) && age < duration - SETTLE - 20;
	}

	/** Gravity steers its front down the steepest way, its momentum keeping it on course over small rises (and, driven, toward where it is driven). */
	private void steer(int here) {
		double heading = Math.atan2(dirZ, dirX);
		double toward = aim != null && travelled < minRun ? Math.atan2(aim.z - z, aim.x - x) : Double.NaN;
		double bestAngle = heading;
		double best = Double.MAX_VALUE;
		for (double turn : TURNS) {
			double angle = heading + turn;
			int h = surface((int) Math.floor(x + Math.cos(angle) * 6.0), (int) Math.floor(z + Math.sin(angle) * 6.0), here);
			if (h == NONE) {
				continue;
			}
			double cost = h + Math.abs(turn) * 1.5;
			if (!Double.isNaN(toward)) {
				double off = angle - toward;
				cost += Math.abs(Math.atan2(Math.sin(off), Math.cos(off))) * 4.0;
			}
			if (cost < best) {
				best = cost;
				bestAngle = angle;
			}
		}
		double angle = heading + (bestAngle - heading) * 0.3;
		dirX = Math.cos(angle);
		dirZ = Math.sin(angle);
	}

	/** It has come to rest: what it carries is dropped (and buried), and its debris piles up deepest about the end of its run. */
	private void stop() {
		phase = SETTLING;
		stoppedAt = age;
		speed = 0.0;
		sweptNow = 0;
		for (ServerPlayer player : level.players()) {
			if (caught.containsKey(player.getUUID())) {
				bury(player);
			}
		}
		caught.clear();
		nearby.clear();
		if (griefing()) {
			toe();
		}
	}

	/** The crown, where the slab broke away: a step torn across the slope, its snow (or a landslide's soil) gone with the slide. */
	private void crown() {
		double half = half();
		int near = (int) Math.floor(y) - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int row = 0; row < 2; row++) {
			for (double c = -half; c <= half && blocksLeft > 6; c += 0.8) {
				int bx = (int) Math.floor(x - dirX * row - dirZ * c);
				int bz = (int) Math.floor(z - dirZ * row + dirX * c);
				int top = surface(bx, bz, near);
				if (top == NONE || top + 3 >= level.getMaxY() || top - 2 <= level.getMinY()) {
					continue;
				}
				clearTop(pos, bx, top, bz);
				for (int k = 0; k < 2; k++) {
					BlockState state = level.getBlockState(pos.set(bx, top - k, bz));
					boolean loose = kind == SNOW ? state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) : WorldGenUtil.isSoftGround(state);
					if (!loose || state.hasBlockEntity() || wet(bx, top - k, bz)) {
						break;
					}
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					blocksLeft--;
				}
			}
		}
	}

	/**
	 * What its front meets: loose snow and plants swept off the land, the snow of a steep track scoured to the crust and
	 * the turf of a landslide's torn up, and the trees in its way snapped and carried off with it. So many columns a tick,
	 * looked at across its front.
	 */
	private void front() {
		double half = half();
		int near = (int) Math.floor(y) - 1;
		int samples = 6 + 2 * grade;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < samples && blocksLeft > 30; i++) {
			double a = -1.0 + random.nextDouble() * (speed + 3.0);
			double c = (random.nextDouble() * 2.0 - 1.0) * half;
			int bx = (int) Math.floor(x + dirX * a - dirZ * c);
			int bz = (int) Math.floor(z + dirZ * a + dirX * c);
			if (!loaded(bx, bz) || laid.containsKey(BlockPos.asLong(bx, 0, bz))) {
				continue;
			}
			int top = surface(bx, bz, near);
			if (top == NONE || top + 3 >= level.getMaxY()) {
				continue;
			}
			// The land is under the trunk of a tree: the trunk stands on it.
			if (TreeFeller.isLog(level.getBlockState(pos.set(bx, top + 1, bz)))) {
				tree(pos.immutable());
				continue;
			}
			clearTop(pos, bx, top, bz);
			BlockState ground = level.getBlockState(pos.set(bx, top, bz));
			if (kind == SNOW) {
				if (ground.is(Blocks.SNOW_BLOCK) && slope > 0.45 && random.nextInt(3) == 0 && !wet(bx, top, bz)) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					blocksLeft--;
				}
			} else if ((ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MYCELIUM)) && random.nextBoolean()) {
				level.setBlock(pos, random.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				blocksLeft--;
			}
		}
	}

	/** Sweeps loose snow and plants off the top of a column (from the top down: the upper half of a tall plant first). */
	private void clearTop(BlockPos.MutableBlockPos pos, int bx, int top, int bz) {
		for (int by = top + 2; by > top; by--) {
			BlockState state = level.getBlockState(pos.set(bx, by, bz));
			if (!state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity()
					&& (state.canBeReplaced() || state.getBlock() instanceof VegetationBlock)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				blocksLeft--;
			}
		}
	}

	/** Snaps a tree in its way and carries it off downhill: so many a second and in all, where a player is near enough to see it. */
	private void tree(BlockPos log) {
		int lx = log.getX();
		int lz = log.getZ();
		if (treesFelled >= treeCap || nearest(lx, lz) > 112.0 || !loaded(lx - 8, lz - 8) || !loaded(lx + 8, lz + 8) || !loaded(lx - 8, lz + 8)
				|| !loaded(lx + 8, lz - 8)) {
			return;
		}
		if (fellTree(log, new Vec3(dirX, 0.0, dirZ), random.nextInt(2), grade >= 4 ? 2 : 1)) {
			treesFelled++;
			blocksLeft -= 30;
		}
	}

	/** How far the nearest player about it is from (px, pz), across the land. */
	private double nearest(double px, double pz) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer player : around) {
			best = Math.min(best, distance2d(player, px, pz));
		}
		return best;
	}

	// --- what it catches ----------------------------------------------------------------------

	/**
	 * What it catches: the players in its path every tick (their own games move them, and need telling), everything else
	 * looked for every few ticks. Caught, a thing is carried along a little slower than its front, tumbling near the
	 * surface of the snow and battered; a player is let go (and buried) once the front has drawn ahead, or after some
	 * seconds. Indoors one is spared, until it buries the house; a tree is no shelter.
	 */
	private void sweep() {
		double half = half();
		sweptNow = 0;
		for (ServerPlayer player : level.players()) {
			UUID id = player.getUUID();
			Caught c = caught.get(id);
			if (!player.isAlive() || immune(player)) {
				caught.remove(id);
				continue;
			}
			if (c == null) {
				if (dropped.contains(id) || !engulfs(player, half, 0.0) || indoors(player)) {
					continue;
				}
				if (player.isPassenger()) {
					// Torn off the horse (or out of the boat, or the cart).
					player.stopRiding();
				}
				c = new Caught(40 + 8 * grade + random.nextInt(20));
				caught.put(id, c);
				hurt(player, DisasterDamage.AVALANCHE, 1.0F + 0.5F * grade);
			}
			c.ticks++;
			if (c.ticks >= c.hold || !engulfs(player, half, 3.0)) {
				caught.remove(id);
				bury(player);
				continue;
			}
			carry(player, c.ticks, 0.35);
			sweptNow++;
			if (c.ticks % 20 == 0) {
				hurt(player, DisasterDamage.AVALANCHE, 0.8F + 0.6F * grade);
			}
		}
		if (age % 4 == 0) {
			look(half);
		}
		for (Entity entity : nearby) {
			if (!entity.isAlive() || entity.isPassenger() || immune(entity) || !engulfs(entity, half, 0.0) || indoors(entity)) {
				continue;
			}
			carry(entity, entity.tickCount, 0.5);
			sweptNow++;
			if (entity instanceof LivingEntity && age % 20 == 0) {
				hurt(entity, DisasterDamage.AVALANCHE, 2.0F + grade);
			}
		}
	}

	/** Everything but the players in and just ahead of its flow (living things first): what may be swept along. */
	private void look(double half) {
		nearby.clear();
		double fx = x + dirX * 3.0;
		double fz = z + dirZ * 3.0;
		double tx = x - dirX * body;
		double tz = z - dirZ * body;
		double px = Math.abs(dirZ) * half + 1.0;
		double pz = Math.abs(dirX) * half + 1.0;
		AABB box = new AABB(Math.min(fx, tx) - px, y - 12.0, Math.min(fz, tz) - pz, Math.max(fx, tx) + px, y + body + 8.0, Math.max(fz, tz) + pz);
		// (Things hung on walls come down when pushed: only where the land may be harmed.)
		boolean all = griefing();
		List<Entity> found = level.getEntitiesOfClass(Entity.class, box, e -> e.isAlive() && !(e instanceof Player) && (all || e instanceof LivingEntity));
		for (Entity entity : found) {
			if (entity instanceof LivingEntity && nearby.size() < MAX_NEARBY) {
				nearby.add(entity);
			}
		}
		for (Entity entity : found) {
			if (!(entity instanceof LivingEntity) && nearby.size() < MAX_NEARBY) {
				nearby.add(entity);
			}
		}
	}

	/**
	 * Whether something is in its flow: behind its front and across its width, no higher over the ground than the flow runs
	 * deep, and not up on high ground beside the way it is going (a bank, a ridge, a boulder well over its middle).
	 */
	private boolean engulfs(Entity entity, double half, double margin) {
		double ex = entity.getX() - x;
		double ez = entity.getZ() - z;
		double along = ex * dirX + ez * dirZ;
		double across = -ex * dirZ + ez * dirX;
		if (along > 2.0 + margin || along < -body - margin || Math.abs(across) > half + margin) {
			return false;
		}
		int near = (int) Math.floor(entity.getY()) - 1;
		int g = surface((int) Math.floor(entity.getX()), (int) Math.floor(entity.getZ()), near);
		int middle = surface((int) Math.floor(x + dirX * along), (int) Math.floor(z + dirZ * along), near);
		if (g != NONE && middle != NONE && g > middle + depth + 1.0) {
			return false;
		}
		double ground = g == NONE ? y : g + 1.0;
		return entity.getY() < ground + depth + margin && entity.getY() > ground - 4.0 - margin;
	}

	/** Whether something is indoors: a solid block (not a tree's) over its head within a few blocks. */
	private boolean indoors(Entity entity) {
		int bx = (int) Math.floor(entity.getX());
		int by = (int) Math.floor(entity.getEyeY());
		int bz = (int) Math.floor(entity.getZ());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dy = 1; dy <= 5 && by + dy < level.getMaxY(); dy++) {
			BlockState state = level.getBlockState(pos.set(bx, by + dy, bz));
			if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && !state.getCollisionShape(level, pos).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Carries a thing along: a little slower than the front, so that the front draws ahead and leaves it behind, tumbling over and over near the surface. */
	private void carry(Entity entity, int ticks, double gain) {
		int g = surface((int) Math.floor(entity.getX()), (int) Math.floor(entity.getZ()), (int) Math.floor(entity.getY()) - 1);
		// (Over land it can't make out, it is held about where it is.)
		double ground = g == NONE ? entity.getY() - 0.9 : g + 1.0;
		double goal = ground + 0.4 + 0.5 * (1.0 + Math.sin(ticks * 0.45));
		// Down a steep slope it falls away as fast as the land does, rather than being flung clear off it.
		double drop = speed * Math.max(0.0, slope) * 0.9;
		double vy = Math.max(-0.6 - drop, Math.min(0.5, (goal - entity.getY()) * gain - drop));
		double pace = speed * (0.86 + 0.06 * Math.sin(ticks * 0.3));
		double sway = 0.06 * Math.sin(ticks * 0.21);
		setVelocity(entity, new Vec3(dirX * pace - dirZ * sway, vy, dirZ * pace + dirX * sway));
		// The fall is from where it is let go: the tumbling on the way doesn't count toward it.
		entity.resetFallDistance();
	}

	/**
	 * Leaves a player where the flow dropped them, stopped dead: in snow up to and over the head (powder snow, which one
	 * sinks in but can dig or wade out of, under a block of packed snow), or for a landslide in a pocket in the rubble,
	 * pinned on every side with a block over the head. Never more than {@link #MAX_BURIAL} blocks over the feet.
	 */
	private void bury(ServerPlayer player) {
		dropped.add(player.getUUID());
		if (!player.isAlive() || immune(player)) {
			return;
		}
		setVelocity(player, new Vec3(0.0, -0.08, 0.0));
		player.resetFallDistance();
		if (!griefing()) {
			return;
		}
		int bx = (int) Math.floor(player.getX());
		int bz = (int) Math.floor(player.getZ());
		int g = surface(bx, bz, (int) Math.floor(player.getY()) - 1);
		if (g == NONE || player.getY() - (g + 1.0) > 2.0 || g + 2 + MAX_BURIAL >= level.getMaxY()) {
			// Thrown clear (or out over nothing): it lands on the debris, if anywhere.
			return;
		}
		int feet = g + 1;
		// The blocks the player fills, from the ground to the top of the head (more of them if tossed up off the ground).
		int head = Math.max(feet + 1, (int) Math.floor(player.getY() + player.getBbHeight()));
		int highest = feet + MAX_BURIAL - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean covered = false;
		for (int by = feet; by <= Math.min(head + 1, highest); by++) {
			BlockState there = level.getBlockState(pos.set(bx, by, bz));
			if (!free(there)) {
				continue;
			}
			// Nothing solid where the player is: powder snow (or a pocket in the rubble) there, one block over the head.
			BlockState put = by > head ? stable : kind == SNOW ? Blocks.POWDER_SNOW.defaultBlockState() : null;
			if (put != null) {
				level.setBlock(pos, put, Block.UPDATE_CLIENTS);
				blocksLaid++;
				covered = true;
			}
		}
		if (kind != SNOW) {
			// Pinned in the rubble on every side.
			for (int[] side : SIDES) {
				int sx = bx + side[0];
				int sz = bz + side[1];
				if (!loaded(sx, sz) || touches(player, sx, sz) || crowded(sx, feet, sz)) {
					continue;
				}
				for (int k = 0; k < 2; k++) {
					BlockState there = level.getBlockState(pos.set(sx, feet + k, sz));
					if (!free(there)) {
						continue;
					}
					BlockState under = level.getBlockState(pos.set(sx, feet + k - 1, sz));
					BlockState put = debris(under.isFaceSturdy(level, pos, Direction.UP));
					level.setBlock(pos.set(sx, feet + k, sz), put, Block.UPDATE_CLIENTS);
					blocksLaid++;
					covered = true;
				}
			}
		}
		long key = BlockPos.asLong(bx, 0, bz);
		graves.add(key);
		laid.put(key, maxTotal);
		if (covered) {
			buried++;
			if (NaturesConfig.get().disasterWarnings) {
				player.sendSystemMessage(Component.translatable(kind == SNOW ? "naturesmod.warning.avalanche_buried" : "naturesmod.warning.landslide_buried")
						.withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** Whether another player about it is in a column, about the height from `by` to two blocks over it. */
	private boolean crowded(int cx, int by, int cz) {
		for (ServerPlayer other : around) {
			if (touches(other, cx, cz) && other.getY() > by - 2.0 && other.getY() < by + 2.0) {
				return true;
			}
		}
		return false;
	}

	/** Whether something's body reaches into a column, or so nearly that a block there would close on it. */
	private static boolean touches(Entity entity, int cx, int cz) {
		double reach = entity.getBbWidth() / 2.0 + 0.05;
		return entity.getX() + reach > cx && entity.getX() - reach < cx + 1 && entity.getZ() + reach > cz && entity.getZ() - reach < cz + 1;
	}

	// --- the debris ---------------------------------------------------------------------------

	/**
	 * The debris it leaves behind it: for every block it runs, a line of columns across its width a little behind its
	 * front, thick where it is slowing (on the flat, toward the end of its run) and thinner at its edges; on the steep,
	 * where it takes more than it leaves, at most a dusting.
	 */
	private void trail() {
		double half = half();
		double settle = Math.max(0.0, Math.min(1.0, (0.45 - slope) / 0.45));
		settle = Math.max(settle, Math.min(1.0, (travelled / length - 0.55) / 0.45));
		int near = (int) Math.floor(y) - 1;
		while (nextLine <= travelled && laid.size() < MAX_COLUMNS) {
			double back = travelled - nextLine + 3.0;
			nextLine += 0.7;
			double cx = x - dirX * back;
			double cz = z - dirZ * back;
			for (double c = -half; c <= half; c += 0.7) {
				double edge = c / half;
				int layers = (int) Math.round(maxLayers * settle * (1.0 - 0.7 * edge * edge) * (0.75 + random.nextDouble() * 0.5));
				if (layers <= 0 && kind == SNOW && random.nextInt(5) == 0) {
					layers = 1 + random.nextInt(2);
				}
				if (layers > 0) {
					plan((int) Math.floor(cx - dirZ * c), (int) Math.floor(cz + dirX * c), layers, near);
				}
			}
		}
	}

	/** Where it comes to rest its debris piles up deepest: a lobe about the end of its run, thickest in the middle. */
	private void toe() {
		double half = half() * 1.15;
		double reach = width * 0.35 + 4.0;
		double cx = x + dirX * 2.0;
		double cz = z + dirZ * 2.0;
		int near = (int) Math.floor(y) - 1;
		for (double a = -reach; a <= reach; a += 0.7) {
			for (double c = -half; c <= half; c += 0.7) {
				double r = (a / reach) * (a / reach) + (c / half) * (c / half);
				if (r > 1.0) {
					continue;
				}
				int layers = (int) Math.round(maxTotal * (1.0 - 0.75 * r) * (0.8 + random.nextDouble() * 0.4));
				plan((int) Math.floor(cx + dirX * a - dirZ * c), (int) Math.floor(cz + dirZ * a + dirX * c), layers, near);
			}
		}
	}

	/** Plans debris for a column: as deep as `layers` in all (no deeper than the most any column takes), the rest of it queued. */
	private void plan(int bx, int bz, int layers, int near) {
		long key = BlockPos.asLong(bx, 0, bz);
		int had = laid.get(key);
		int want = Math.min(maxTotal, layers);
		if (want <= had || deposits.size() >= MAX_QUEUE || graves.contains(key)) {
			return;
		}
		laid.put(key, want);
		deposits.addLast(new Deposit(bx, bz, want - had, near));
	}

	/**
	 * Lays the debris queued, a column at a time, as far as this tick's budget goes (and so many columns at most: one it
	 * can lay nothing on, in water or on a trunk, costs no blocks but still costs the looking).
	 */
	private void lay() {
		for (int n = 0; n < 160 && blocksLeft >= 6 && !deposits.isEmpty(); n++) {
			column(deposits.pollFirst());
		}
	}

	/**
	 * Lays a column of debris on whatever stands firm under it (not on water, a trunk or a fence): packed snow with pockets
	 * of powder snow and a layer on top, or the rubble of a landslide. Where a player is it lays nothing solid in the two
	 * blocks the player fills (powder snow, or nothing), and no more than {@link #MAX_BURIAL} blocks over the feet.
	 */
	private void column(Deposit deposit) {
		int bx = deposit.x();
		int bz = deposit.z();
		if (!loaded(bx, bz) || graves.contains(BlockPos.asLong(bx, 0, bz))) {
			return;
		}
		int g = surface(bx, bz, deposit.near());
		if (g == NONE || g <= level.getMinY() + 1 || g + 8 >= level.getMaxY()) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, g, bz);
		BlockState base = level.getBlockState(pos);
		if (!base.getFluidState().isEmpty() || base.is(BlockTags.LOGS) || !base.isFaceSturdy(level, pos, Direction.UP)) {
			return;
		}
		int full = deposit.layers() / 8;
		int rest = deposit.layers() % 8;
		if (kind != SNOW) {
			full += rest >= 4 ? 1 : 0;
			rest = 0;
		}
		int highest = Integer.MAX_VALUE;
		int bodyLow = Integer.MAX_VALUE;
		int bodyHigh = Integer.MIN_VALUE;
		for (ServerPlayer player : around) {
			// Where the player is, and is about to be.
			Vec3 v = player.getDeltaMovement();
			double reachX = 0.85 + Math.min(3.0, Math.abs(v.x) * 2.0);
			double reachZ = 0.85 + Math.min(3.0, Math.abs(v.z) * 2.0);
			if (Math.abs(player.getX() - (bx + 0.5)) < reachX && Math.abs(player.getZ() - (bz + 0.5)) < reachZ && player.getY() > g - 1.0
					&& player.getY() < g + 9.0) {
				int feet = (int) Math.floor(player.getY());
				highest = Math.min(highest, feet + MAX_BURIAL - 1);
				bodyLow = Math.min(bodyLow, feet);
				bodyHigh = Math.max(bodyHigh, Math.max(feet + 1, (int) Math.floor(player.getY() + player.getBbHeight())));
			}
		}
		boolean supported = true;
		int k = 0;
		for (; k < full; k++) {
			int by = g + 1 + k;
			if (by > highest) {
				break;
			}
			BlockState there = level.getBlockState(pos.set(bx, by, bz));
			if (!free(there)) {
				break;
			}
			BlockState put;
			if (by >= bodyLow && by <= bodyHigh) {
				if (kind != SNOW) {
					// A pocket about the player in the rubble.
					supported = false;
					continue;
				}
				put = Blocks.POWDER_SNOW.defaultBlockState();
			} else if (kind == SNOW) {
				put = snowBlock(k, full, rest);
			} else {
				put = debris(supported);
			}
			// A buried plant tells its neighbours (the other half of a tall one goes with it); the rest needn't.
			level.setBlock(pos, put, there.getBlock() instanceof VegetationBlock ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
			blocksLeft--;
			blocksLaid++;
			supported = !put.is(Blocks.POWDER_SNOW);
		}
		if (rest > 0 && k == full && supported) {
			int by = g + 1 + full;
			if (by <= highest && (by < bodyLow || by > bodyHigh)) {
				BlockState there = level.getBlockState(pos.set(bx, by, bz));
				BlockState layer = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, rest);
				if (free(there) && layer.canSurvive(level, pos)) {
					level.setBlock(pos, layer, there.getBlock() instanceof VegetationBlock ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
					blocksLeft--;
					blocksLaid++;
				}
			}
		}
	}

	/** A block of snow debris: packed snow, here and there a pocket of powder snow (never at the bottom, nor under a layer, which needs something firm to lie on). */
	private BlockState snowBlock(int k, int full, int rest) {
		boolean top = k == full - 1;
		double chance = k == 0 || top && rest > 0 ? 0.0 : top ? 0.12 : 0.35;
		return random.nextDouble() < chance ? Blocks.POWDER_SNOW.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
	}

	/** A block of a landslide's rubble; one that holds itself up (no gravel or sand) where nothing is under it. */
	private BlockState debris(boolean supported) {
		BlockState put = rubble[random.nextInt(rubble.length)];
		return supported || !(put.getBlock() instanceof FallingBlock) ? put : stable;
	}

	// --- for the tests ------------------------------------------------------------------------

	/** Where it let go: the top of the land where the slab broke away. */
	public BlockPos release() {
		return release;
	}

	/** Whether it is a landslide (of earth, rock, sand or clay) rather than snow. */
	public boolean landslide() {
		return kind != SNOW;
	}

	/** What it is made of: {@link #SNOW}, {@link #EARTH}, {@link #ROCK}, {@link #SAND} or {@link #CLAY}. */
	public int material() {
		return kind;
	}

	/** How far along it is: {@link #CRACKING}, {@link #RUNNING} or {@link #SETTLING}. */
	public int phase() {
		return phase;
	}

	/** How fast its front is going now, in blocks a tick. */
	public double speed() {
		return speed;
	}

	/** The fastest its front goes (1 to 1.5 blocks a tick). */
	public double maxSpeed() {
		return maxSpeed;
	}

	/** How far its front has run so far, and the furthest it may run (60 to 250 blocks). */
	public double travelled() {
		return travelled;
	}

	public double runLength() {
		return length;
	}

	/** How wide it is once it has spread (10 to 40 blocks). */
	public double width() {
		return width;
	}

	/** Blocks of debris it has laid so far: snow blocks, layers and powder snow, or rubble. */
	public int blocksLaid() {
		return blocksLaid;
	}

	/** Columns of debris still waiting to be laid. */
	public int queued() {
		return deposits.size();
	}

	public int treesFelled() {
		return treesFelled;
	}

	/** Players it has buried so far. */
	public int buried() {
		return buried;
	}

	/** Things it swept along this tick, players and all. */
	public int swept() {
		return sweptNow;
	}

	/** Whether it has caught this player and is carrying them along. */
	public boolean carrying(Entity entity) {
		return caught.containsKey(entity.getUUID());
	}
}
