package com.naturesmod.disaster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.naturesmod.tree.TreeEvents;
import com.naturesmod.tree.TreeFeller;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A sinkhole: the roof of a hollow that water has worn in the rock beneath gives way, and the ground
 * over it drops in. Its level, 1 to 5, is how big a pit opens: from one three blocks in radius and ten
 * deep, enough to swallow a tree, to a chasm fifteen blocks in radius and forty-five deep that
 * swallows a village. First it gives a few seconds' warning: the ground rumbles, cracks open in a ring
 * round where it will go and the middle sags. Then it falls in, from the middle outward over a second
 * to a few: its edge lobed and uneven, its walls leaning out below the rim (the shape of the hollow it
 * fell into) and drawing in a little at the floor, the floor a heap of rubble, highest in the middle.
 * What stands on the falling ground goes in with it: trees topple in, the turf and the roofs drop in
 * whole, people and animals are dragged down and battered by what comes down with them, and whoever
 * is on the brink feels the edge crumble toward the hole under their feet. If it breaks into an
 * aquifer or a pocket of lava, a little of it seeps in and pools on the floor; the rest stays behind
 * the walls. Over open water nothing happens at all.
 *
 * <p>The work is bounded every tick: the pit is dug a few hundred blocks a tick, a column at a time from
 * the middle out; only so many blocks fall whole at once (the rest are cleared), only a few trees are
 * felled, and the animals about it are looked for every few ticks. It never digs below a few blocks
 * above the bottom of the world, nor through bedrock, nor beside water or lava (which would pour in).
 */
public final class Sinkhole extends Disaster {
	/** The phases it goes through, as its clients are told (the first of {@link #extra()}). */
	public static final int WARNING = 0;
	public static final int COLLAPSING = 1;
	public static final int SETTLED = 2;

	/** The radius of the opening and the depth of the pit to its floor, in blocks, by level (1 to 5). */
	private static final double[] RADIUS = {3.0, 5.0, 8.0, 11.0, 15.0};
	private static final double[] DEPTH = {10.0, 16.0, 24.0, 32.0, 45.0};
	/** Blocks changed in one tick at most, by level. */
	private static final int[] BLOCKS_PER_TICK = {150, 210, 270, 340, 400};
	/**
	 * The ticks the collapse takes at the least, by level (a second and a half to three and a half): the columns are
	 * begun no faster than that, so that even a small pit is seen to fall in rather than vanish.
	 */
	private static final int[] COLLAPSE_TICKS = {30, 40, 50, 60, 70};
	/** How far below the rim the walls lean out, at most, as a part of the opening's radius. */
	private static final double OVERHANG = 0.2;
	/** Ground left standing over the leaning walls, at the least, in blocks. */
	private static final int LIP = 2;
	/** The longest the collapse may go on for (on a hillside there is more to dig): what is left of it then stays. */
	private static final int MAX_COLLAPSE = 400;
	/** The ticks it settles for after the collapse, after how many of them what seeped in has pooled, and for how many the rubble still comes down. */
	private static final int SETTLING = 120;
	private static final int POOLING = 50;
	private static final int BATTERING = 40;
	/** Blocks of the ground let fall whole: so many a tick and so many in the air at once. The rest are cleared. */
	private static final int FALLS_PER_TICK = 6;
	private static final int MAX_FALLING = 40;
	/** Ticks between two trees toppling in (each one is a few dozen falling blocks). */
	private static final int TREE_TICKS = 15;
	/** Things other than players dragged in at once, at most. */
	private static final int MAX_NEARBY = 64;
	/** Cracks opened in a tick at most, and blocks the sagging may change in one. */
	private static final int CRACKS_PER_TICK = 6;
	private static final int SAG_PER_TICK = 90;
	/** What a block holds back: water, lava, or what can't be known (a chunk beside it isn't loaded). */
	private static final int SEEP_WATER = 1;
	private static final int SEEP_LAVA = 2;
	private static final int SEEP_UNKNOWN = 3;
	private static final int NONE = Integer.MIN_VALUE;

	private final int grade;
	/** The column in the middle of it. */
	private final int cx;
	private final int cz;
	private final double radius;
	private final int depth;
	/** The lobes of its edge: how much its radius swells and narrows round the opening, and where. */
	private final double lobe1;
	private final double phase1;
	private final double lobe2;
	private final double phase2;
	/** How far from the middle the pit reaches at its widest, below the rim. */
	private final double reach;
	private final int warningTicks;
	private final int perTick;
	/** The height of the land round it (where the rim is), and of its floor at the walls. */
	private int rimY;
	private int floorY;
	private boolean overWater;
	/** Nowhere for it to open (open water, no room above the bottom of the world, not loaded): it ends at once. */
	private boolean blocked;
	private int collapseStart = -1;
	private int settledAt = -1;
	/** How far out from the middle the collapse has got. */
	private double front;
	private final List<Crack> cracks = new ArrayList<>();
	private int crackIndex;
	private final Deque<Sag> sagging = new ArrayDeque<>();
	private boolean sagQueued;
	/** The columns of the pit still to dig, the middle first, and those dug. */
	private final Deque<Column> queue = new ArrayDeque<>();
	private final List<Column> dug = new ArrayList<>();
	/** Every column of the pit. */
	private final LongSet pit = new LongOpenHashSet();
	private final List<FallingBlockEntity> falling = new ArrayList<>();
	private final List<LivingEntity> nearby = new ArrayList<>();
	/** Where each player near it was last tick: how fast one is going is how far it went since. */
	private final Map<UUID, Vec3> lastSeen = new HashMap<>();
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
	/**
	 * The column being dug: the next block down, the block it stops above, the ground left standing over it (its
	 * lid, let fall once the column under it is out), and whether its bottom is the floor of the pit (and takes rubble).
	 */
	private Column cut;
	private int digY;
	private int bottom;
	private int lid = NONE;
	private boolean floorHere;
	private boolean sandy;
	private int fallsLeft;
	private int treesLeft;
	private int nextTree;
	/** What it has broken into: water or lava (the first found), or nothing. */
	private int seeped;
	private boolean pooledYet;
	private int blocksDug;
	private int columnsDug;
	private int cracksOpened;
	private int sagged;
	private int released;
	private int treesFelled;
	private int pooled;

	/** A column of the pit: where, how far from the middle and toward what, whether it opens to the sky (or lies under the rim), and its order in the collapse. */
	private static final class Column {
		final int x;
		final int z;
		final double d;
		final double angle;
		final boolean open;
		/** A little unevenness, so that the walls are rough. */
		final double jitter;
		final double order;
		/** The top of what is left at its bottom, once dug. */
		int floor = NONE;

		Column(int x, int z, double d, double angle, boolean open, double jitter, double order) {
			this.x = x;
			this.z = z;
			this.d = d;
			this.angle = angle;
			this.open = open;
			this.jitter = jitter;
			this.order = order;
		}
	}

	/** A crack of the ring round it, and when in the warning (0 to 1) it opens. */
	private record Crack(int x, int z, float when, boolean deep) {
	}

	/** A column of the middle that sags before it goes, and by how much. */
	private record Sag(int x, int z, int drop, double d) {
	}

	Sinkhole(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.SINKHOLE, level, at, intensity, natural, 0);
		grade = Math.max(1, Math.min(5, Math.round(this.intensity)));
		cx = (int) Math.floor(x);
		cz = (int) Math.floor(z);
		x = cx + 0.5;
		z = cz + 0.5;
		radius = RADIUS[grade - 1] * (0.9 + random.nextDouble() * 0.2);
		depth = (int) Math.round(DEPTH[grade - 1] * (0.9 + random.nextDouble() * 0.2));
		lobe1 = 0.06 + random.nextDouble() * 0.06;
		lobe2 = 0.03 + random.nextDouble() * 0.04;
		phase1 = random.nextDouble() * Math.PI * 2.0;
		phase2 = random.nextDouble() * Math.PI * 2.0;
		reach = radius * (1.0 + lobe1 + lobe2) * (1.0 + OVERHANG) + 1.0;
		warningTicks = 70 + grade * 10 + random.nextInt(20);
		perTick = BLOCKS_PER_TICK[grade - 1];
		treesLeft = 1 + grade / 2;
		duration = warningTicks + MAX_COLLAPSE + SETTLING + 20;
		rimY = (int) Math.floor(y) - 1;
		floorY = rimY - depth;
		survey();
		y = rimY + 1;
	}

	/**
	 * Finds the lie of the land: the height of the rim (the middle one of nine spots over the opening, so that a
	 * house or a tree in the middle doesn't count), the floor below it, and whether there is open water there.
	 */
	private void survey() {
		int[] heights = new int[9];
		int found = 0;
		int wet = 0;
		for (int i = 0; i < 9; i++) {
			double angle = i * Math.PI / 4.0;
			int bx = i == 0 ? cx : (int) Math.floor(x + Math.cos(angle) * radius * 0.6);
			int bz = i == 0 ? cz : (int) Math.floor(z + Math.sin(angle) * radius * 0.6);
			if (!loaded(bx, bz)) {
				continue;
			}
			int top = ground(bx, bz);
			// Water, or ice over it.
			if (!level.getFluidState(pos.set(bx, top, bz)).isEmpty() || !level.getFluidState(pos.set(bx, top - 1, bz)).isEmpty()) {
				wet += i == 0 ? 3 : 1;
			}
			heights[found++] = terrain(bx, bz);
		}
		if (found == 0 || !loaded(cx, cz)) {
			blocked = true;
			return;
		}
		Arrays.sort(heights, 0, found);
		rimY = heights[found / 2];
		floorY = Math.max(rimY - depth, level.getMinY() + 5);
		overWater = wet >= 3;
		// Under the bedrock roof of the nether, or with no room under it for a pit.
		boolean bedrock = level.getBlockState(pos.set(cx, ground(cx, cz), cz)).is(Blocks.BEDROCK);
		blocked = overWater || bedrock || rimY - floorY < 4 || rimY >= level.getMaxY() - 2;
	}

	@Override
	protected void begin() {
		if (blocked) {
			// Open water, or nowhere for it to go: it ends as quietly as it came.
			end();
			return;
		}
		buildCracks();
		warn(radius * 4.0 + 64.0, player -> {
			int distance = (int) distance2d(player, x, z);
			return distance < radius * 1.2 + 2.0 ? Component.translatable("naturesmod.warning.sinkhole_under")
					: Component.translatable("naturesmod.warning.sinkhole", distance, compass(player.getX(), player.getZ(), x, z));
		});
	}

	@Override
	public float radius() {
		return (float) (reach + 2.0);
	}

	@Override
	public float strength() {
		if (settledAt >= 0) {
			return (float) Math.max(0.0, 1.0 - (age - settledAt) / (double) SETTLING);
		}
		if (collapseStart >= 0) {
			return 1.0F;
		}
		return (float) Math.min(1.0, age / (double) warningTicks);
	}

	/**
	 * For its clients: the phase, the radius of the opening, the depth, the rim's height, how far into the warning
	 * it is (0 to 1), how far out the collapse has got, its level, the floor's height, the lobes of its edge (two
	 * amounts and two phases: the edge toward an angle a is radius * (1 + l1 sin(3a + p1) + l2 sin(5a + p2))), the
	 * ticks since it began to fall in and since it settled (-1 until then), and how far it reaches at its widest.
	 */
	@Override
	public float[] extra() {
		return new float[] {phase(), (float) radius, rimY - floorY, rimY, (float) Math.min(1.0, age / (double) warningTicks), (float) front, grade,
				floorY, (float) lobe1, (float) phase1, (float) lobe2, (float) phase2, collapseStart < 0 ? -1.0F : age - collapseStart,
				settledAt < 0 ? -1.0F : age - settledAt, (float) reach};
	}

	// --- each tick ----------------------------------------------------------------------------

	@Override
	protected void tick() {
		if (blocked) {
			end();
			return;
		}
		falling.removeIf(block -> block.isRemoved());
		if (collapseStart < 0 && age < warningTicks) {
			if (griefing()) {
				warning();
			}
			entities(false, false);
		} else if (settledAt < 0) {
			if (collapseStart < 0) {
				startCollapse();
			}
			fallsLeft = FALLS_PER_TICK;
			if (cut != null || !queue.isEmpty()) {
				dig();
			}
			entities(true, true);
			int since = age - collapseStart;
			if ((since >= 10 && cut == null && queue.isEmpty()) || since >= MAX_COLLAPSE) {
				queue.clear();
				abandon();
				settledAt = age;
			}
		} else {
			int since = age - settledAt;
			entities(false, since < BATTERING);
			if (!pooledYet && since >= POOLING) {
				pooledYet = true;
				pool();
			}
			if (since >= SETTLING) {
				end();
			}
		}
	}

	@Override
	protected void finish() {
		queue.clear();
		sagging.clear();
		abandon();
		falling.clear();
		nearby.clear();
		lastSeen.clear();
	}

	// --- the shape of it ----------------------------------------------------------------------

	/** How far the edge of the opening is from the middle toward `angle`: lobed, uneven. */
	private double edge(double angle) {
		return radius * (1.0 + lobe1 * Math.sin(angle * 3.0 + phase1) + lobe2 * Math.sin(angle * 5.0 + phase2));
	}

	/**
	 * How far out the pit reaches toward `angle` at height `by`: as far as the edge at the rim, further below it (the
	 * walls lean out, as the hollow's did) and drawing in a little toward the floor.
	 */
	private double wall(double angle, int by) {
		double f = (rimY - by) / (double) Math.max(1, rimY - floorY);
		double out = smooth((f - 0.08) / 0.42) - 0.4 * smooth((f - 0.7) / 0.3);
		return edge(angle) * (1.0 + OVERHANG * out);
	}

	private static double smooth(double t) {
		if (t <= 0.0) {
			return 0.0;
		}
		if (t >= 1.0) {
			return 1.0;
		}
		return t * t * (3.0 - 2.0 * t);
	}

	/** The top of the rubble on a column's floor: heaped highest in the middle, where most of it came down (a fifth of the depth). */
	private int floorTop(Column c) {
		double spread = c.d / Math.max(1.0, radius * 1.2);
		double heap = (1.0 + (rimY - floorY) * 0.2) * Math.max(0.0, 1.0 - spread * spread);
		return Math.min(rimY - 2, floorY + (int) Math.round(heap + c.jitter));
	}

	// --- the warning --------------------------------------------------------------------------

	/** The ring of cracks round where it will open, each opening at its moment in the warning: spreading both ways round from where they start. */
	private void buildCracks() {
		double start = random.nextDouble() * Math.PI * 2.0;
		double out = grade >= 3 ? 2.0 : 1.0;
		double ring = radius * (1.0 + lobe1 + lobe2) + out;
		int n = Math.max(16, (int) Math.ceil(Math.PI * 2.0 * ring / 0.7));
		double gaps = random.nextDouble() * 10.0;
		LongSet seen = new LongOpenHashSet();
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0 * i / n;
			// Here and there the ring holds: the cracks run in pieces.
			if (Math.sin(angle * 7.0 + gaps) * 0.6 + Math.sin(angle * 13.0 + gaps * 1.7) * 0.4 < -0.4) {
				continue;
			}
			double r = edge(angle) + out + random.nextDouble() * 0.8 - 0.4;
			int bx = (int) Math.floor(x + Math.cos(angle) * r);
			int bz = (int) Math.floor(z + Math.sin(angle) * r);
			if (!seen.add(BlockPos.asLong(bx, 0, bz))) {
				continue;
			}
			double off = Math.abs(Math.atan2(Math.sin(angle - start), Math.cos(angle - start)));
			float when = (float) (0.08 + 0.72 * off / Math.PI + random.nextDouble() * 0.08);
			cracks.add(new Crack(bx, bz, when, grade >= 3 && random.nextInt(3) == 0));
		}
		cracks.sort((a, b) -> Float.compare(a.when(), b.when()));
	}

	/** The ground giving warning: the ring of cracks opening round it, and halfway through, the middle sagging. */
	private void warning() {
		double progress = age / (double) warningTicks;
		int opened = 0;
		while (crackIndex < cracks.size() && cracks.get(crackIndex).when() <= progress && opened < CRACKS_PER_TICK) {
			Crack c = cracks.get(crackIndex++);
			opened++;
			if (crack(c.x(), c.z(), c.deep())) {
				cracksOpened++;
			}
		}
		if (!sagQueued && progress >= 0.45) {
			sagQueued = true;
			queueSag();
		}
		int used = 0;
		while (used < SAG_PER_TICK && !sagging.isEmpty()) {
			used += 1 + sag(sagging.pollFirst());
		}
	}

	/** The middle of it, within a little over half the radius: it sags a block, a big one two in its very middle. */
	private void queueSag() {
		int r = (int) Math.ceil(radius * (1.0 + lobe1 + lobe2) * 0.6) + 1;
		List<Sag> list = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				double inner = edge(Math.atan2(dz, dx)) * 0.6;
				if (d > inner) {
					continue;
				}
				list.add(new Sag(cx + dx, cz + dz, grade >= 3 && d < inner * 0.5 ? 2 : 1, d));
			}
		}
		list.sort((a, b) -> Double.compare(a.d(), b.d()));
		sagging.addAll(list);
	}

	/** A crack opening in the turf of the ring round it, a block deep (now and then two in a big one). Returns whether it opened. */
	private boolean crack(int bx, int bz, boolean deep) {
		if (!loadedAround(bx, bz)) {
			return false;
		}
		int top = surface(bx, bz);
		if (top <= level.getMinY() + 3) {
			return false;
		}
		pos.set(bx, top, bz);
		BlockState state = level.getBlockState(pos);
		if (!natural(state) || seepage(pos, state) != 0 || standing(bx, top, bz)) {
			return false;
		}
		clearCover(bx, top, bz);
		level.setBlock(pos.set(bx, top, bz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		if (deep) {
			pos.set(bx, top - 1, bz);
			BlockState under = level.getBlockState(pos);
			if (natural(under) && seepage(pos, under) == 0) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		if (cracksOpened % 2 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), bx + 0.5, top + 0.8, bz + 0.5, 5, 0.35, 0.1, 0.35, 0.05);
		}
		return true;
	}

	/**
	 * The ground over the hollow settling a block or two, all of a piece, before it goes: the turf drops with it.
	 * Not under a tree or a building, nor beside water. Returns the blocks changed.
	 */
	private int sag(Sag s) {
		int bx = s.x();
		int bz = s.z();
		if (!loadedAround(bx, bz)) {
			return 0;
		}
		int top = surface(bx, bz);
		int low = top - s.drop();
		if (low <= level.getMinY() + 3) {
			return 0;
		}
		BlockState turf = level.getBlockState(pos.set(bx, top, bz));
		if (!natural(turf) || standing(bx, top, bz)) {
			return 0;
		}
		for (int by = top; by >= low; by--) {
			pos.set(bx, by, bz);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.hasBlockEntity() || !state.isCollisionShapeFullBlock(level, pos) || seepage(pos, state) != 0) {
				return 0;
			}
		}
		int used = clearCover(bx, top, bz);
		for (int by = top; by > low; by--) {
			level.setBlock(pos.set(bx, by, bz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			used++;
		}
		level.setBlock(pos.set(bx, low, bz), turf, Block.UPDATE_CLIENTS);
		sagged++;
		return used + 1;
	}

	// --- the collapse -------------------------------------------------------------------------

	/** The ground gives way: every column of the pit is laid out, to be dug from the middle out (a little unevenly). */
	private void startCollapse() {
		collapseStart = age;
		sagging.clear();
		if (!griefing()) {
			return;
		}
		List<Column> columns = new ArrayList<>();
		int r = (int) Math.ceil(reach);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > reach) {
					continue;
				}
				double angle = Math.atan2(dz, dx);
				double jitter = random.nextDouble() * 0.7 - 0.35;
				double edge = edge(angle) + jitter;
				if (d > edge * (1.0 + OVERHANG)) {
					continue;
				}
				columns.add(new Column(cx + dx, cz + dz, d, angle, d <= edge, jitter, d + random.nextDouble() * 1.4));
			}
		}
		columns.sort((a, b) -> Double.compare(a.order, b.order));
		for (Column c : columns) {
			pit.add(BlockPos.asLong(c.x, 0, c.z));
		}
		queue.addAll(columns);
	}

	/**
	 * Digs on from the middle out: no more than the blocks a tick allows, and no more columns begun than spread the
	 * collapse over its second or few. A column too deep for what is left of a tick is taken up again the next.
	 */
	private void dig() {
		int columns = Math.max(1, (int) Math.ceil(pit.size() / (double) COLLAPSE_TICKS[grade - 1]));
		int used = 0;
		int looked = 0;
		while (used < perTick && looked++ < perTick * 4) {
			if (cut == null) {
				if (columns <= 0) {
					break;
				}
				Column next = queue.pollFirst();
				if (next == null) {
					break;
				}
				columns--;
				front = Math.max(front, next.d);
				startColumn(next);
				continue;
			}
			used += step();
		}
	}

	/**
	 * Takes up a column to dig. One open to the sky is dug from the top of whatever stands on it down to the rubble
	 * of the floor, its ground (or the roof over it) left until last; one under the rim only where the walls lean
	 * out, with the ground over it left standing (thicker where it is sand or gravel, which would hang there).
	 */
	private void startColumn(Column c) {
		if (!loadedAround(c.x, c.z)) {
			return;
		}
		int land = ground(c.x, c.z);
		if (!level.getFluidState(pos.set(c.x, land, c.z)).isEmpty()) {
			// Water by it (a pond, a stream): that column stands, and holds the water back.
			return;
		}
		int top = terrain(c.x, c.z);
		int floor = floorTop(c);
		if (c.open) {
			cut = c;
			digY = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, c.x, c.z) - 1, level.getMaxY());
			bottom = floor;
			lid = top > floor ? top : NONE;
			floorHere = true;
			sandy = lid != NONE && level.getBlockState(pos.set(c.x, lid, c.z)).is(BlockTags.SAND);
			return;
		}
		int band = NONE;
		int low = NONE;
		for (int by = rimY; by > floor; by--) {
			if (c.d <= wall(c.angle, by) + c.jitter) {
				if (band == NONE) {
					band = by;
				}
				low = by;
			} else if (band != NONE) {
				break;
			}
		}
		if (band == NONE) {
			return;
		}
		int from = Math.min(band, top - LIP);
		for (int i = 0; i < 6 && from > low && level.getBlockState(pos.set(c.x, from + 1, c.z)).getBlock() instanceof FallingBlock; i++) {
			from--;
		}
		if (level.getBlockState(pos.set(c.x, from + 1, c.z)).getBlock() instanceof FallingBlock) {
			// Sand or gravel all the way down: it would not hang over the hole (the first knock would bring the whole lip
			// down, and its neighbours' with it), so here the wall goes straight down.
			return;
		}
		// Where the walls draw in again above the floor, the column ends in the wall: no rubble there.
		int base = low > floor + 1 ? low - 1 : floor;
		if (from <= base) {
			return;
		}
		cut = c;
		digY = from;
		bottom = base;
		lid = NONE;
		floorHere = base == floor;
		sandy = level.getBlockState(pos.set(c.x, top, c.z)).is(BlockTags.SAND);
	}

	/** Breaks the next block down the column being dug: returns the blocks changed (none for air), and finishes the column once it is through. */
	private int step() {
		Column c = cut;
		if (!loaded(c.x, c.z)) {
			cut = null;
			return 0;
		}
		if (digY <= bottom) {
			return finishColumn(true);
		}
		if (digY == lid) {
			digY--;
			return 0;
		}
		pos.set(c.x, digY, c.z);
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			digY--;
			return 0;
		}
		int seep = seepage(pos, state);
		if (seep != 0 || state.is(Blocks.BEDROCK) || state.getDestroySpeed(level, pos) < 0.0F) {
			// Bedrock, or water or lava that would pour in: the column goes no deeper, and what is left of it holds it back.
			if (seeped == 0 && (seep == SEEP_WATER || seep == SEEP_LAVA)) {
				seeped = seep;
			}
			c.floor = digY;
			return finishColumn(false);
		}
		int by = digY--;
		if (lid != NONE && by > lid) {
			return clearStanding(pos, state);
		}
		if (state.hasBlockEntity()) {
			level.destroyBlock(pos, true);
		} else {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		blocksDug++;
		return 1;
	}

	/**
	 * What stands on the ground of an open column (a plant, a tree, a lamp): a tree topples into the pit (a few of
	 * them), the rest goes as the ground does. Returns the blocks changed (a felled tree counts for a good many).
	 */
	private int clearStanding(BlockPos.MutableBlockPos at, BlockState state) {
		if (TreeFeller.isLog(state)) {
			if (treesLeft > 0 && age >= nextTree && falling.size() < MAX_FALLING / 2) {
				nextTree = age + TREE_TICKS;
				Vec3 toward = new Vec3(x - (at.getX() + 0.5), 0.0, z - (at.getZ() + 0.5));
				if (TreeEvents.snapTrunk(level, at.immutable(), toward, 0, 160, 6, 10)) {
					treesLeft--;
					treesFelled++;
					return 16;
				}
			}
			// The leaves it held up are told, so that they wither.
			level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		} else if (state.hasBlockEntity()) {
			level.destroyBlock(at, true);
		} else {
			level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		blocksDug++;
		return 1;
	}

	/**
	 * A column done: rubble on its floor, and the ground that stood over it (left till now, so that it has nothing to
	 * land on on the way) let fall in, whole if not too many are falling already. Returns the blocks changed.
	 */
	private int finishColumn(boolean through) {
		Column c = cut;
		cut = null;
		int used = 0;
		if (through) {
			c.floor = bottom;
			if (floorHere) {
				used += rubble(c.x, bottom, c.z);
			}
		}
		if (lid != NONE) {
			used += release(c);
		}
		lid = NONE;
		dug.add(c);
		columnsDug++;
		return used;
	}

	/**
	 * Stops partway down the column being dug (the collapse ran out of time, or it was stopped): the ground left over
	 * it is let go, as it would otherwise hang over the emptied part of the column.
	 */
	private void abandon() {
		if (cut != null && loadedAround(cut.x, cut.z)) {
			finishColumn(false);
		}
		cut = null;
		lid = NONE;
	}

	/** Rubble on the floor of a column, the top two blocks of it: broken rock, gravel and earth (sand in sand, cobbled deepslate down in the deepslate). */
	private int rubble(int bx, int top, int bz) {
		int used = 0;
		for (int by = top; by >= top - 1 && by > level.getMinY() + 2; by--) {
			pos.set(bx, by, bz);
			BlockState was = level.getBlockState(pos);
			if (was.isAir() || !was.getFluidState().isEmpty() || was.hasBlockEntity() || was.is(Blocks.BEDROCK) || was.getDestroySpeed(level, pos) < 0.0F
					|| !was.isCollisionShapeFullBlock(level, pos)) {
				continue;
			}
			level.setBlock(pos, rubbleFor(was), Block.UPDATE_CLIENTS);
			used++;
		}
		return used;
	}

	private BlockState rubbleFor(BlockState was) {
		int roll = random.nextInt(10);
		if (was.is(Blocks.DEEPSLATE) || was.is(Blocks.COBBLED_DEEPSLATE) || was.is(Blocks.TUFF)) {
			return roll < 5 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : roll < 7 ? Blocks.TUFF.defaultBlockState()
					: roll < 9 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
		}
		if (sandy) {
			return roll < 5 ? Blocks.SAND.defaultBlockState() : roll < 8 ? Blocks.SANDSTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
		}
		return roll < 3 ? Blocks.GRAVEL.defaultBlockState() : roll < 5 ? Blocks.COBBLESTONE.defaultBlockState()
				: roll < 7 ? Blocks.DIRT.defaultBlockState() : roll < 8 ? Blocks.COARSE_DIRT.defaultBlockState()
				: roll < 9 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	/**
	 * Lets the ground of an open column (or the roof over it) go, once nothing is left under it: whole, tipping in
	 * toward the middle, if not too many blocks are falling already and nobody stands on it (it would bury them at
	 * the bottom); otherwise it is simply gone. Returns the blocks changed.
	 */
	private int release(Column c) {
		pos.set(c.x, lid, c.z);
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.is(Blocks.BEDROCK) || state.getDestroySpeed(level, pos) < 0.0F || seepage(pos, state) != 0) {
			return 0;
		}
		if (!level.getBlockState(probe.set(c.x, lid - 1, c.z)).isAir()) {
			// The column stopped short under it: it still stands on something.
			return 0;
		}
		blocksDug++;
		if (state.hasBlockEntity()) {
			level.destroyBlock(pos, true);
			return 1;
		}
		if (fallsLeft > 0 && falling.size() < MAX_FALLING && state.isCollisionShapeFullBlock(level, pos) && !occupied(c.x, lid, c.z)) {
			FallingBlockEntity block = FallingBlockEntity.fall(level, pos.immutable(), state);
			if (block != null) {
				fallsLeft--;
				released++;
				block.disableDrop();
				block.setHurtsEntities(0.5F, 6);
				double d = Math.max(1.0, c.d);
				shove(block, (x - (c.x + 0.5)) / d * 0.04, 0.0, (z - (c.z + 0.5)) / d * 0.04);
				falling.add(block);
				return 1;
			}
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		return 1;
	}

	/** Whether a player stands on (or just over) a block. */
	private boolean occupied(int bx, int by, int bz) {
		for (ServerPlayer player : level.players()) {
			if (Math.abs(player.getX() - (bx + 0.5)) < 1.3 && Math.abs(player.getZ() - (bz + 0.5)) < 1.3 && player.getY() > by - 0.5
					&& player.getY() < by + 3.0) {
				return true;
			}
		}
		return false;
	}

	// --- what seeps in ------------------------------------------------------------------------

	/**
	 * What it broke into seeps in and pools on the lowest of its floor: a little of it (a block or two deep, a few
	 * dozen blocks of water or a handful of lava at most), and only where the pit holds it on every side.
	 */
	private void pool() {
		if ((seeped != SEEP_WATER && seeped != SEEP_LAVA) || !griefing() || dug.isEmpty()) {
			return;
		}
		boolean lava = seeped == SEEP_LAVA;
		BlockState fluid = lava ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState();
		int cap = lava ? 6 + grade * 3 : 16 + grade * 12;
		List<Column> floors = new ArrayList<>();
		for (Column c : dug) {
			if (c.floor != NONE && c.floor > level.getMinY() + 2) {
				floors.add(c);
			}
		}
		if (floors.isEmpty()) {
			return;
		}
		floors.sort((a, b) -> a.floor != b.floor ? Integer.compare(a.floor, b.floor) : Double.compare(a.d, b.d));
		int surface = floors.get(0).floor + (grade >= 3 && !lava ? 2 : 1);
		for (Column c : floors) {
			if (c.floor >= surface || pooled >= cap) {
				break;
			}
			if (!loadedAround(c.x, c.z)) {
				continue;
			}
			for (int by = c.floor + 1; by <= surface && pooled < cap; by++) {
				pos.set(c.x, by, c.z);
				if (!level.getBlockState(pos).isAir() || !held(pos)) {
					break;
				}
				level.setBlock(pos, fluid, Block.UPDATE_ALL);
				pooled++;
			}
		}
	}

	/** Whether water or lava put here stays in the pit: on something, and against a wall or more of the pit on every side. */
	private boolean held(BlockPos.MutableBlockPos at) {
		if (level.getBlockState(probe.set(at.getX(), at.getY() - 1, at.getZ())).isAir()) {
			return false;
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int nx = at.getX() + side.getStepX();
			int nz = at.getZ() + side.getStepZ();
			if (!loaded(nx, nz)) {
				return false;
			}
			BlockState next = level.getBlockState(probe.set(nx, at.getY(), nz));
			if ((next.isAir() || next.canBeReplaced()) && !pit.contains(BlockPos.asLong(nx, 0, nz))) {
				// A way out into a cave beside the pit.
				return false;
			}
		}
		return true;
	}

	// --- the living ---------------------------------------------------------------------------

	/**
	 * What is about it: the players every tick (their own games move them, and need telling), everything else alive
	 * looked for every few ticks. While it falls in, what is over the falling ground is dragged down with it, what
	 * stands on the brink feels the edge crumble toward the hole, and what is down in it is battered.
	 */
	private void entities(boolean tug, boolean batter) {
		Map<UUID, Vec3> seen = new HashMap<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || immune(player) || distance2d(player, x, z) > reach + 6.0) {
				continue;
			}
			Vec3 before = lastSeen.get(player.getUUID());
			seen.put(player.getUUID(), player.position());
			if (collapseStart < 0 || !griefing() || player.isPassenger()) {
				// Nothing opens under anyone while the land is left be.
				continue;
			}
			Vec3 moved = before == null ? Vec3.ZERO : player.position().subtract(before);
			if (moved.lengthSqr() > 9.0) {
				// Teleported, not fallen.
				moved = Vec3.ZERO;
			}
			drag(player, moved, tug, batter);
		}
		lastSeen.clear();
		lastSeen.putAll(seen);
		if (collapseStart < 0 || !griefing() || (!tug && !batter)) {
			return;
		}
		if (age % 5 == 0 || age == collapseStart) {
			nearby.clear();
			AABB box = new AABB(x - reach - 4.0, floorY - 3.0, z - reach - 4.0, x + reach + 4.0, rimY + 10.0, z + reach + 4.0);
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !(e instanceof Player))) {
				if (nearby.size() >= MAX_NEARBY) {
					break;
				}
				nearby.add(entity);
			}
		}
		for (LivingEntity entity : nearby) {
			if (entity.isAlive() && !immune(entity) && !entity.isPassenger()) {
				drag(entity, null, tug, batter);
			}
		}
	}

	/**
	 * Drags something in. A player's velocity is set to the one it would have had next anyway (from how far it went
	 * since the last tick, slowed as the game slows it in the air or on the ground) plus the pull: falling, it falls a
	 * little faster and is drawn off the walls toward the middle; on the brink it slips toward the hole unless it
	 * runs. Anything else is simply pushed (`moved` is null for it).
	 */
	private void drag(LivingEntity entity, Vec3 moved, boolean tug, boolean batter) {
		double dx = entity.getX() - x;
		double dz = entity.getZ() - z;
		double d = Math.sqrt(dx * dx + dz * dz);
		double ey = entity.getY();
		if (d > reach + 3.0 || ey > rimY + 8.0 || ey < floorY - 2.0) {
			return;
		}
		double edge = edge(Math.atan2(dz, dx));
		double inX = d > 0.3 ? -dx / d : 0.0;
		double inZ = d > 0.3 ? -dz / d : 0.0;
		if (!entity.onGround() && !entity.isInWater() && !entity.onClimbable() && d < Math.min(edge, front) - 0.3 && ey < rimY + 2.0
				&& ey > floorY + 1.0) {
			// Falling in with the ground: down, and in off the walls (not while it jumps or climbs: it may get out again).
			if (moved != null && moved.y <= 0.0) {
				double vy = Math.max(-3.0, (moved.y - 0.08) * 0.98 - 0.03);
				setVelocity(entity, new Vec3(moved.x * 0.91 + inX * 0.05, vy, moved.z * 0.91 + inZ * 0.05));
			} else if (moved == null && entity.getDeltaMovement().y > -2.0) {
				shove(entity, inX * 0.04, -0.05, inZ * 0.04);
			}
		} else if (tug && entity.onGround() && d > edge - 1.0 && d < edge + 2.5 && front > d - 4.0 && ey > rimY - 2.0) {
			// On the brink: the edge crumbling under its feet, it slips toward the hole.
			double pull = 0.03 + 0.006 * grade;
			if (moved != null) {
				setVelocity(entity, new Vec3(moved.x * 0.546 + inX * pull, -0.0784, moved.z * 0.546 + inZ * pull));
			} else {
				shove(entity, inX * pull, 0.0, inZ * pull);
			}
		}
		if (batter && age % 10 == 0 && d < edge * (1.0 + OVERHANG) + 0.5 && ey < rimY - 1.0 && random.nextFloat() < 0.6F) {
			// Battered by the earth and rock coming down with it.
			hurt(entity, DisasterDamage.SINKHOLE, 1.0F + grade * 0.5F);
		}
	}

	// --- the ground ---------------------------------------------------------------------------

	/** The top of the ground in a column: under a thin snow or a plant that keeps things off it. */
	private int surface(int bx, int bz) {
		int top = ground(bx, bz);
		BlockState state = level.getBlockState(pos.set(bx, top, bz));
		return state.canBeReplaced() && state.getFluidState().isEmpty() ? top - 1 : top;
	}

	/** A trunk, a wall, a fence post or the like stands on a block: it is left be. */
	private boolean standing(int bx, int top, int bz) {
		BlockState above = level.getBlockState(probe.set(bx, top + 1, bz));
		return TreeFeller.isLog(above) || above.hasBlockEntity() || !above.getCollisionShape(level, probe).isEmpty();
	}

	/** Clears what grows or lies on top of a column (plants, a thin snow, a torch), so that none of it is left floating. */
	private int clearCover(int bx, int top, int bz) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int cleared = 0;
		for (int by = top + 1; by <= top + 2 && by <= level.getMaxY(); by++) {
			BlockState state = level.getBlockState(at.set(bx, by, bz));
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || !state.getCollisionShape(level, at).isEmpty()) {
				break;
			}
			level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			cleared++;
		}
		return cleared;
	}

	/**
	 * Water or lava in a block, beside it or over it, that would pour into the pit if it were broken: what it is, or
	 * 0 for none. Where the chunk beside isn't loaded it can't be known, and the block is left be.
	 */
	private int seepage(BlockPos at, BlockState state) {
		int here = kind(state.getFluidState());
		if (here != 0) {
			return here;
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int nx = at.getX() + side.getStepX();
			int nz = at.getZ() + side.getStepZ();
			if (!loaded(nx, nz)) {
				return SEEP_UNKNOWN;
			}
			int beside = kind(level.getFluidState(probe.set(nx, at.getY(), nz)));
			if (beside != 0) {
				return beside;
			}
		}
		return kind(level.getFluidState(probe.set(at.getX(), at.getY() + 1, at.getZ())));
	}

	private static int kind(FluidState fluid) {
		return fluid.isEmpty() ? 0 : fluid.is(FluidTags.LAVA) ? SEEP_LAVA : SEEP_WATER;
	}

	/** The chunks of a column and of those beside it are loaded: looking next door won't load a chunk there and then. */
	private boolean loadedAround(int bx, int bz) {
		return loaded(bx, bz) && loaded(bx + 1, bz) && loaded(bx - 1, bz) && loaded(bx, bz + 1) && loaded(bx, bz - 1);
	}

	/** The land as it lies: earth, sand, gravel, rock. */
	private static boolean natural(BlockState state) {
		return WindDamage.isGround(state) || state.is(Blocks.MUD) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.DIRT_PATH)
				|| state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
	}

	// --- for the tests ------------------------------------------------------------------------

	/** Its level, 1 to 5. */
	public int grade() {
		return grade;
	}

	/** The ticks from its start until the ground gives way. */
	public int warningTicks() {
		return warningTicks;
	}

	/** {@link #WARNING}, {@link #COLLAPSING} or {@link #SETTLED}. */
	public int phase() {
		return settledAt >= 0 ? SETTLED : collapseStart >= 0 ? COLLAPSING : WARNING;
	}

	/** Whether it found open water where it was to open (and so ended there and then). */
	public boolean overWater() {
		return overWater;
	}

	/** The middle of the opening, at the height of the rim. */
	public BlockPos centre() {
		return new BlockPos(cx, rimY, cz);
	}

	/** The radius of the opening, in blocks (its edge swells and narrows a little round it). */
	public double openingRadius() {
		return radius;
	}

	/** How far from the middle it reaches at its widest, below the rim. */
	public double widest() {
		return reach;
	}

	/** The height of the rim (the land round it), and of its floor at the walls (the rubble heaps a few blocks higher in the middle). */
	public int rimHeight() {
		return rimY;
	}

	public int floorHeight() {
		return floorY;
	}

	/** The box the pit is dug in. */
	public AABB pit() {
		return new AABB(cx - reach, floorY - 1, cz - reach, cx + 1 + reach, rimY + 2, cz + 1 + reach);
	}

	/** Cracks opened in the ring round it so far, and columns of its middle that sagged. */
	public int cracksOpened() {
		return cracksOpened;
	}

	public int columnsSagged() {
		return sagged;
	}

	/** Columns of the pit dug so far, and still to dig. */
	public int columnsDug() {
		return columnsDug;
	}

	public int digging() {
		return queue.size() + (cut == null ? 0 : 1);
	}

	/** Blocks dug out of the pit (or cleared off it) so far. */
	public int blocksDug() {
		return blocksDug;
	}

	/** Blocks of its ground let fall in whole so far, and how many of them are still falling (never more than {@code MAX_FALLING}). */
	public int blocksReleased() {
		return released;
	}

	public int fallingBlocks() {
		return falling.size();
	}

	/** Trees toppled into it. */
	public int treesFelled() {
		return treesFelled;
	}

	/** Blocks of water or lava that seeped in and pooled on its floor. */
	public int fluidPooled() {
		return pooled;
	}
}
