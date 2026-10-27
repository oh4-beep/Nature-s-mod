package com.naturesmod.disaster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An earthquake of a given magnitude. The waves run out from the epicentre (see {@link Seismic}): the
 * first ones jolt, the second ones shake things down, and far off the shaking comes later and weaker.
 * Where it shakes hard, people and animals are thrown about, loose sand and gravel slump, stone bricks
 * crack, glass shatters, pots and lanterns fall, the tops of walls and chimneys rock off, built blocks
 * with too little under them come down, cliff edges crumble and stalactites break off cave ceilings.
 * A strong quake breaks the surface along its fault: a fissure tearing open both ways from beside the
 * epicentre, the wider and deeper the stronger the quake (ten blocks deep at M6.5, twenty at M7.5, a
 * chasm of thirty and more beyond M8.5), its walls ragged, cracks branching off it every few blocks and
 * the ground on one side heaved up into a scarp. All about, wherever the shaking is strong, the ground
 * gives way too: cracks open, round pits collapse, and sand, gravel and the wet ground by water turn
 * to slurry and sink. The digging is queued and done a few hundred blocks a tick, so that the server
 * keeps up; it stops short of water and lava (never letting them in), of bedrock and of the bottom of
 * the world. Players far enough off get an early warning before the shaking arrives, and a report
 * after; the big ones bring aftershocks, the largest about 1.2 magnitudes smaller (Båth's law), most of
 * them soon after (Omori's law).
 */
public final class Earthquake extends Disaster {
	/** Where in {@link #extra()} the fresh openings in the ground start: how many, then the x, y, z, size, kind and age of each. */
	public static final int OPENINGS_AT = 6;
	/** The most openings the clients are told of at once, and for how long after each opens. */
	public static final int MAX_OPENINGS = 8;
	public static final int OPENING_TICKS = 80;
	/** The kinds of opening: the fault's own fissure, a crack, a collapse pit, liquefied ground sinking. */
	public static final int OPEN_FISSURE = 1;
	public static final int OPEN_CRACK = 2;
	public static final int OPEN_PIT = 3;
	public static final int OPEN_SINK = 4;

	private static final Map<Block, Block> CRACKED = Map.of(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.DEEPSLATE_BRICKS,
			Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS,
			Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
	/** Blocks the ground breaking open may change in one tick, fissure, cracks and pits together. */
	private static final int BLOCKS_PER_TICK = 320;
	/** Blocks shaken loose to fall in one tick, and falling at once: no more, or the falling blocks would bring the server down with them. */
	private static final int FALLS_PER_TICK = 8;
	private static final int MAX_FALLING = 48;
	/** Panes and pots broken in one tick (each breaking is a burst of particles on every client near). */
	private static final int SHATTERS_PER_TICK = 4;
	/** How fast the rupture runs along the fault, in blocks a tick. */
	private static final double RUPTURE_SPEED = 3.0;
	/** What is done to a queued column: broken open, heaved up or let sink. */
	private static final int CARVE = 0;
	private static final int HEAVE = 1;
	private static final int SINK = 2;
	/**
	 * The blocks left to change this tick, and the tick that is: the budget is shared by every quake going on at once
	 * (aftershocks overlap, and a natural quake may come during one started by command), so that together they never dig more.
	 */
	private static long budgetTick = Long.MIN_VALUE;
	private static int budgetLeft;

	private final double magnitude;
	private final boolean aftershock;
	private final double strike;
	private final double ruptureHalf;
	private final double felt;
	/** How far out the ground shakes hard enough to break (0.12 g): the cracks, pits and sinking ground open within it. */
	private final double strong;
	private final double phase;
	/** Where the fault runs: a little to one side of the epicentre, so that it tears open beside whoever is standing there. */
	private final double faultX;
	private final double faultZ;
	private final int scarp;
	private final double width;
	private final int depth;
	/** The side of the fault that heaves up, +1 or -1 across it. */
	private final int heaveSide;
	/** The fault's fissure, scarp and branching cracks, dug as the rupture runs. */
	private final Carver rupture = new Carver();
	/** The cracks, pits and sinking ground about it. */
	private final Carver holes = new Carver();
	/** Every column queued so far: each is dug, heaved or sunk only once. */
	private final LongSet queued = new LongOpenHashSet();
	private final List<FallingBlockEntity> falling = new ArrayList<>();
	private final Deque<Opening> recent = new ArrayDeque<>();
	private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
	private double carved;
	private boolean quiet;
	private int pitsLeft;
	private int cracksLeft;
	private int sinksLeft;
	private int falls;
	private int slides;
	private int shatters;
	private int broken;
	private int sunk;
	private int pitsOpened;
	private int cracksOpened;

	/** One column of ground to change: dug `depth` blocks down, heaved up or let sink that many. */
	private record Cut(int x, int z, int depth, int kind, boolean anything, int opening, float size) {
	}

	/** A place the ground has just opened, told to the clients for the dust boiling up out of it. */
	private record Opening(double x, double y, double z, float size, int kind, int at) {
	}

	Earthquake(ServerLevel level, Vec3 at, float magnitude, boolean natural, boolean aftershock) {
		super(DisasterType.EARTHQUAKE, level, at, magnitude, natural, 0);
		this.magnitude = this.intensity;
		this.aftershock = aftershock;
		strike = random.nextDouble() * Math.PI;
		phase = random.nextDouble() * 100.0;
		felt = Math.min(Seismic.feltRadius(this.magnitude), 2400.0);
		strong = Seismic.radiusOf(this.magnitude, 0.12);
		ruptureHalf = this.magnitude >= 6.0 ? 10.0 * Math.pow(2.1, this.magnitude - 6.0) : 0.0;
		scarp = this.magnitude >= 6.5 ? (int) Math.round(this.magnitude - 5.5) : 0;
		width = Math.max(1.0, Math.min(6.5, 1.5 + (this.magnitude - 6.0) * 1.4));
		depth = (int) Math.round(Math.max(4.0, 10.0 + (this.magnitude - 6.5) * 11.0) * (0.9 + random.nextDouble() * 0.2));
		heaveSide = random.nextBoolean() ? 1 : -1;
		double aside = (random.nextBoolean() ? 1.0 : -1.0) * (4.0 + width);
		faultX = x - Math.sin(strike) * aside;
		faultZ = z + Math.cos(strike) * aside;
		pitsLeft = this.magnitude >= 6.0 ? (int) Math.round((this.magnitude - 5.6) * 5.0) : 0;
		cracksLeft = this.magnitude >= 5.5 ? (int) Math.round((this.magnitude - 5.2) * 8.0) : 0;
		sinksLeft = this.magnitude >= 6.0 ? (int) Math.round((this.magnitude - 5.5) * 4.0) : 0;
		duration = (int) (Math.min(felt, 1600.0) / Seismic.S_SPEED + Seismic.shakingTicks(this.magnitude) * 2.6) + 60;
		y = ground((int) Math.floor(x), (int) Math.floor(z)) + 1;
	}

	/** No warnings or reports: a tremor under a volcano, say. */
	Earthquake quietly() {
		quiet = true;
		return this;
	}

	@Override
	public float radius() {
		return (float) felt;
	}

	@Override
	public float strength() {
		return 1.0F;
	}

	@Override
	public float[] extra() {
		int count = 0;
		for (Opening opening : recent) {
			if (age - opening.at() < OPENING_TICKS) {
				count++;
			}
		}
		float[] out = new float[OPENINGS_AT + 1 + count * 6];
		out[0] = (float) magnitude;
		out[1] = (float) strike;
		out[2] = (float) ruptureHalf;
		out[3] = (float) carved;
		out[4] = Seismic.shakingTicks(magnitude);
		out[5] = (float) felt;
		out[OPENINGS_AT] = count;
		int i = OPENINGS_AT + 1;
		for (Opening opening : recent) {
			if (age - opening.at() >= OPENING_TICKS || i + 6 > out.length) {
				continue;
			}
			out[i++] = (float) opening.x();
			out[i++] = (float) opening.y();
			out[i++] = (float) opening.z();
			out[i++] = opening.size();
			out[i++] = opening.kind();
			out[i++] = age - opening.at();
		}
		return out;
	}

	@Override
	protected void begin() {
		if (!quiet && magnitude >= 4.5) {
			warnings();
		}
	}

	@Override
	protected void tick() {
		if (griefing()) {
			if (ruptureHalf > 0.0) {
				// The rupture runs out both ways along the fault.
				double target = Math.min(ruptureHalf, age * RUPTURE_SPEED);
				while (carved < target) {
					carved += 0.5;
					fissure(carved);
					fissure(-carved);
				}
			}
			breakGround();
			dig();
			shakeLand();
		}
		if (age % 2 == 0) {
			shakeEntities();
		}
	}

	// --- the fault ---------------------------------------------------------------------------

	/**
	 * One step of the surface rupture, `along` blocks out from the middle of the fault: the fissure across it,
	 * widest and deepest in the middle and closing up toward the ends, V-shaped and ragged; the scarp heaved up
	 * beside it; and now and then a crack branching off.
	 */
	private void fissure(double along) {
		double sx = Math.cos(strike);
		double sz = Math.sin(strike);
		double px = -sz;
		double pz = sx;
		double wobble = (Math.sin(along * 0.19 + phase) * 1.4 + Math.sin(along * 0.53 + phase * 2.0) * 0.7) * (0.8 + width * 0.2);
		double cx = faultX + sx * along + px * wobble;
		double cz = faultZ + sz * along + pz * wobble;
		double shape = Math.sqrt(Math.max(0.0, 1.0 - Math.abs(along) / ruptureHalf));
		double w = Math.max(1.0, width * (0.35 + 0.65 * shape) * (0.8 + 0.4 * random.nextDouble()));
		double d = Math.max(3.0, depth * (0.3 + 0.7 * shape));
		int n = Math.max(1, (int) Math.round(w / 0.7));
		boolean mark = Math.round(Math.abs(along) * 2.0) % 10 == 0;
		for (int k = 0; k < n; k++) {
			double off = (k - (n - 1) / 2.0) * 0.7;
			// The walls close in toward the bottom.
			double edge = n <= 1 ? 0.0 : Math.abs(off) / ((n - 1) * 0.35 + 0.35);
			int cd = Math.max(2, (int) Math.round(d * (1.0 - 0.5 * edge * edge) + random.nextGaussian() * d * 0.1));
			enqueue(rupture, new Cut((int) Math.floor(cx + px * off), (int) Math.floor(cz + pz * off), cd, CARVE, true,
					mark && k == n / 2 ? OPEN_FISSURE : 0, (float) w));
		}
		if (scarp > 0) {
			// The far side of the fault heaves up into a step, sloping off away from it.
			int h = Math.max(1, (int) Math.round(scarp * (0.4 + 0.6 * shape)));
			double from = (n - 1) * 0.35 + 1.0;
			for (double o = 0.0; o <= h + 1.0; o += 0.7) {
				int rise = Math.max(1, Math.min(h, (int) Math.round(h + 1.0 - o)));
				double off = heaveSide * (from + o);
				enqueue(rupture, new Cut((int) Math.floor(cx + px * off), (int) Math.floor(cz + pz * off), rise, HEAVE, true, 0, 0.0F));
			}
		}
		if (random.nextInt(8) == 0) {
			// A crack branching off to one side, running away from the middle of the fault.
			int side = random.nextBoolean() ? 1 : -1;
			double turn = 0.5 + random.nextDouble() * 0.6;
			double angle = along >= 0.0 ? strike + side * turn : strike + Math.PI - side * turn;
			double edge = (n - 1) * 0.35 + 0.5;
			double length = 4.0 + random.nextDouble() * (4.0 + (magnitude - 6.0) * 4.0);
			crack(rupture, cx + px * side * edge, cz + pz * side * edge, angle, length, Math.max(3.0, d * (0.35 + random.nextDouble() * 0.2)),
					Math.max(1.0, w * 0.4), true);
		}
	}

	/** A crack wandering off from (cx, cz) along `angle`, narrowing and shallowing to nothing at its far end. */
	private void crack(Carver into, double cx, double cz, double angle, double length, double deep, double wide, boolean anything) {
		double px = cx;
		double pz = cz;
		double heading = angle;
		boolean marked = false;
		for (double t = 0.0; t < length; t += 0.6) {
			heading += random.nextGaussian() * 0.08;
			px += Math.cos(heading) * 0.6;
			pz += Math.sin(heading) * 0.6;
			double f = 1.0 - t / length;
			double w = Math.max(0.6, wide * (0.4 + 0.6 * f));
			double d = 2.0 + (deep - 2.0) * f;
			int n = Math.max(1, (int) Math.round(w / 0.7));
			for (int k = 0; k < n; k++) {
				double off = (k - (n - 1) / 2.0) * 0.7;
				int bx = (int) Math.floor(px - Math.sin(heading) * off);
				int bz = (int) Math.floor(pz + Math.cos(heading) * off);
				int cd = Math.max(1, (int) Math.round(d * (n > 1 && k != n / 2 ? 0.7 : 1.0) + random.nextGaussian() * 0.8));
				if (enqueue(into, new Cut(bx, bz, cd, CARVE, anything, marked ? 0 : OPEN_CRACK, (float) wide))) {
					marked = true;
				}
			}
		}
	}

	// --- the ground giving way about it --------------------------------------------------------

	/**
	 * Every so often, somewhere the ground is shaking hard just now, it gives way: a crack opens, a pit
	 * collapses or loose, wet ground sinks. Most along the fault and near the epicentre, some near the
	 * players (where they will be seen), and none until the shaking has got there.
	 */
	private void breakGround() {
		if (age % 2 != 0 || age > duration - 200 || pitsLeft + cracksLeft + sinksLeft <= 0 || holes.size() > 4000) {
			return;
		}
		double px;
		double pz;
		List<ServerPlayer> players = level.players();
		int pick = random.nextInt(3);
		if (pick == 0 && !players.isEmpty()) {
			ServerPlayer player = players.get(random.nextInt(players.size()));
			double angle = random.nextDouble() * Math.PI * 2.0;
			double d = 6.0 + random.nextDouble() * 42.0;
			px = player.getX() + Math.cos(angle) * d;
			pz = player.getZ() + Math.sin(angle) * d;
		} else if (pick == 1 && ruptureHalf > 0.0) {
			double along = (random.nextDouble() * 2.0 - 1.0) * (ruptureHalf + 16.0);
			double across = random.nextGaussian() * (8.0 + width * 3.0);
			px = faultX + Math.cos(strike) * along - Math.sin(strike) * across;
			pz = faultZ + Math.sin(strike) * along + Math.cos(strike) * across;
		} else {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double d = Math.min(strong, 240.0) * Math.pow(random.nextDouble(), 1.5);
			px = x + Math.cos(angle) * d;
			pz = z + Math.sin(angle) * d;
		}
		int bx = (int) Math.floor(px);
		int bz = (int) Math.floor(pz);
		if (!loaded(bx, bz)) {
			return;
		}
		double dx = px - x;
		double dz = pz - z;
		double a = Seismic.shaking(magnitude, Math.sqrt(dx * dx + dz * dz), age);
		if (a < 0.12) {
			return;
		}
		int roll = random.nextInt(pitsLeft + cracksLeft + sinksLeft);
		if (roll < pitsLeft) {
			if (a >= 0.2 && pit(bx, bz, a)) {
				pitsLeft--;
			}
		} else if (roll < pitsLeft + cracksLeft) {
			crackAround(px, pz);
			cracksLeft--;
		} else if (liquefy(bx, bz)) {
			sinksLeft--;
		}
	}

	/** A crack opening in the ground about the fault: most run out from the epicentre or along the fault. */
	private void crackAround(double px, double pz) {
		double angle = random.nextBoolean() ? Math.atan2(pz - z, px - x) + random.nextGaussian() * 0.3 : strike + random.nextGaussian() * 0.4;
		if (random.nextBoolean()) {
			angle += Math.PI;
		}
		double length = 6.0 + random.nextDouble() * (6.0 + Math.max(0.0, magnitude - 6.0) * 6.0);
		double deep = 3.0 + random.nextDouble() * Math.max(1.0, (magnitude - 5.5) * 2.5);
		double wide = 1.0 + (magnitude >= 7.0 ? random.nextDouble() * (magnitude - 6.5) : 0.0);
		crack(holes, px, pz, angle, length, deep, wide, false);
		cracksOpened++;
	}

	/**
	 * A round pit where the ground collapses into itself, its walls sheer and its edge lobed. Only in open
	 * ground (not under a building, a tree or water, nor beside a lake or a river) and never under a player.
	 */
	private boolean pit(int cx, int cz, double a) {
		double big = Math.min(1.0, a);
		double widest = Math.max(2.0, Math.min(6.0, 2.0 + (magnitude - 6.0) * 1.6));
		double deepest = Math.max(6.0, Math.min(20.0, 6.0 + (magnitude - 6.0) * 6.0));
		int r = (int) Math.round(2.0 + random.nextDouble() * (widest - 2.0) * (0.5 + 0.5 * big));
		int deep = (int) Math.round(6.0 + random.nextDouble() * (deepest - 6.0) * (0.5 + 0.5 * big));
		for (ServerPlayer player : level.players()) {
			if (distance2d(player, cx + 0.5, cz + 0.5) < r + 2.5) {
				return false;
			}
		}
		// Water close by: the pit would fill, or let a lake in.
		for (int i = 0; i < 16; i++) {
			double angle = i * Math.PI / 8.0;
			int bx = (int) Math.floor(cx + 0.5 + Math.cos(angle) * (r + 2));
			int bz = (int) Math.floor(cz + 0.5 + Math.sin(angle) * (r + 2));
			if (!loaded(bx, bz) || !level.getBlockState(scratch.set(bx, ground(bx, bz), bz)).getFluidState().isEmpty()) {
				return false;
			}
		}
		double lobe = random.nextDouble() * Math.PI * 2.0;
		double lobes = 0.1 + random.nextDouble() * 0.15;
		List<Cut> cuts = new ArrayList<>();
		// Ring by ring from the middle out: it falls in from the centre.
		for (int ring = 0; ring <= r + 1; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					double d = Math.sqrt(dx * dx + dz * dz);
					double edge = r * (1.0 + lobes * Math.sin(Math.atan2(dz, dx) * 3.0 + lobe)) + random.nextDouble() * 0.6 - 0.3;
					if (d > edge) {
						continue;
					}
					int bx = cx + dx;
					int bz = cz + dz;
					if (!loaded(bx, bz)) {
						return false;
					}
					BlockState surface = level.getBlockState(scratch.set(bx, ground(bx, bz), bz));
					if (!natural(surface) || !surface.getFluidState().isEmpty()) {
						return false;
					}
					int cd = d < edge - 1.3 ? deep + random.nextInt(3) - 1 : Math.max(2, (int) (deep * (0.45 + random.nextDouble() * 0.35)));
					cuts.add(new Cut(bx, bz, cd, CARVE, false, 0, r));
				}
			}
		}
		if (cuts.isEmpty()) {
			return false;
		}
		boolean marked = false;
		for (Cut cut : cuts) {
			Cut next = marked ? cut : new Cut(cut.x(), cut.z(), cut.depth(), CARVE, false, OPEN_PIT, r);
			if (enqueue(holes, next)) {
				marked = true;
			}
		}
		pitsOpened++;
		return true;
	}

	/** Loose sand and gravel, or the wet ground by water, turning to slurry in the shaking and settling: the ground sinks a block or a few. */
	private boolean liquefy(int bx, int bz) {
		int top = ground(bx, bz);
		BlockState surface = level.getBlockState(scratch.set(bx, top, bz));
		if (!sinkable(surface)) {
			return false;
		}
		boolean loose = surface.is(BlockTags.SAND) || surface.is(Blocks.GRAVEL) || surface.is(Blocks.MUD) || surface.is(Blocks.CLAY);
		if (!loose && !nearWater(bx, top, bz)) {
			return false;
		}
		int r = 2 + random.nextInt(4);
		int most = Math.max(1, Math.min(3, (int) Math.round((magnitude - 5.5) * 1.5)));
		int deep = 1 + random.nextInt(most);
		boolean marked = false;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r + random.nextDouble() * 0.8 - 0.4) {
					continue;
				}
				double f = d / (r + 1.0);
				int down = Math.max(1, (int) Math.round(deep * (1.0 - f * f) + 0.3));
				if (enqueue(holes, new Cut(bx + dx, bz + dz, down, SINK, false, marked ? 0 : OPEN_SINK, r))) {
					marked = true;
				}
			}
		}
		return true;
	}

	/** Water lying within a few blocks, about level with (bx, by, bz). */
	private boolean nearWater(int bx, int by, int bz) {
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4.0;
			for (int reach = 3; reach <= 7; reach += 4) {
				int wx = (int) Math.floor(bx + 0.5 + Math.cos(angle) * reach);
				int wz = (int) Math.floor(bz + 0.5 + Math.sin(angle) * reach);
				if (!loaded(wx, wz)) {
					continue;
				}
				int wy = ground(wx, wz);
				if (Math.abs(wy - by) <= 2 && level.getBlockState(scratch.set(wx, wy, wz)).getFluidState().is(FluidTags.WATER)) {
					return true;
				}
			}
		}
		return false;
	}

	// --- the digging -------------------------------------------------------------------------

	/** Queues a column to change, unless it is already: each column is dug, heaved or sunk once. */
	private boolean enqueue(Carver into, Cut cut) {
		if (!queued.add(BlockPos.asLong(cut.x(), 0, cut.z()))) {
			return false;
		}
		into.queue.addLast(cut);
		return true;
	}

	/** Works through the queued ground: the fault and the holes about it sharing the blocks a tick allows. */
	private void dig() {
		if (rupture.idle() && holes.idle()) {
			return;
		}
		long now = level.getGameTime();
		if (now != budgetTick) {
			budgetTick = now;
			budgetLeft = BLOCKS_PER_TICK;
		}
		int budget = budgetLeft;
		if (budget <= 0) {
			return;
		}
		if (!holes.idle()) {
			budget -= holes.run(rupture.idle() ? budget : budget / 2);
		}
		if (budget > 0) {
			budget -= rupture.run(budget);
		}
		if (budget > 0 && !holes.idle()) {
			budget -= holes.run(budget);
		}
		budgetLeft = Math.max(0, budget);
	}

	/**
	 * Columns waiting to be dug, worked through a few hundred blocks a tick: a column too deep for what is left
	 * of a tick is taken up again the next. The top block of a column goes with the neighbours told (so that
	 * what stands on it drops), the rest quietly: nothing below needs to hear of it.
	 */
	private final class Carver {
		private final Deque<Cut> queue = new ArrayDeque<>();
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		/** The column being dug, its top, the next block down and where it stops. */
		private Cut cut;
		private int top;
		private int at;
		private int bottom;

		boolean idle() {
			return cut == null && queue.isEmpty();
		}

		int size() {
			return queue.size() + (cut == null ? 0 : 1);
		}

		/** Digs on, changing no more than `budget` blocks; returns how many it changed. */
		int run(int budget) {
			int used = 0;
			int looked = 0;
			while (used < budget && looked++ < budget * 4) {
				if (cut == null) {
					Cut next = queue.pollFirst();
					if (next == null) {
						break;
					}
					used += switch (next.kind()) {
						case HEAVE -> heave(next);
						case SINK -> sink(next);
						default -> start(next);
					};
					continue;
				}
				int changed = breakNext();
				if (changed < 0) {
					cut = null;
				} else {
					used += changed;
				}
			}
			return used;
		}

		/** Takes up a column to dig: finds its top and clears what grows on it; returns the blocks that took. */
		private int start(Cut next) {
			if (!loaded(next.x(), next.z()) || !loadedAround(next.x(), next.z())) {
				return 0;
			}
			int t = ground(next.x(), next.z());
			BlockState surface = level.getBlockState(pos.set(next.x(), t, next.z()));
			if (!surface.getFluidState().isEmpty() || !next.anything() && !natural(surface)) {
				return 0;
			}
			cut = next;
			top = t;
			at = t;
			// Measured down from the land itself, so that a trunk or a snowdrift standing on it doesn't make the hole shallower.
			int land = next.anything() ? Math.max(t - 32, Math.min(t, terrain(next.x(), next.z()))) : t;
			bottom = Math.max(land - next.depth(), level.getMinY() + 1);
			if (next.opening() > 0) {
				opened(next.x() + 0.5, t + 1.0, next.z() + 0.5, next.size(), next.opening());
			}
			return clearCover(next.x(), t, next.z());
		}

		/** Breaks the next block down the column: 1 if it broke one, 0 if there was nothing to break, -1 once the column is done. */
		private int breakNext() {
			if (at <= bottom) {
				return -1;
			}
			pos.set(cut.x(), at, cut.z());
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0.0F || besideFluid(pos)) {
				// Bedrock, or water or lava that would pour in: the hole goes no deeper.
				return -1;
			}
			at--;
			if (state.isAir()) {
				return 0;
			}
			if (state.hasBlockEntity()) {
				level.destroyBlock(pos, true);
			} else {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), pos.getY() == top ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
			}
			broken++;
			return 1;
		}
	}

	/** Heaves a column up `depth` blocks (the scarp): more of the same ground under its top. */
	private int heave(Cut cut) {
		if (!loaded(cut.x(), cut.z())) {
			return 0;
		}
		int top = ground(cut.x(), cut.z());
		BlockPos surfacePos = new BlockPos(cut.x(), top, cut.z());
		BlockState surface = level.getBlockState(surfacePos);
		if (!WindDamage.isGround(surface) || top + cut.depth() >= level.getMaxY()) {
			return 0;
		}
		for (ServerPlayer player : level.players()) {
			// Not up under someone's feet, which would bury them in it.
			if (Math.abs(player.getX() - (cut.x() + 0.5)) < 1.4 && Math.abs(player.getZ() - (cut.z() + 0.5)) < 1.4 && player.getY() > top - 2
					&& player.getY() < top + cut.depth() + 3) {
				return 0;
			}
		}
		for (int h = 1; h <= cut.depth(); h++) {
			BlockState there = level.getBlockState(surfacePos.above(h));
			if (!there.getFluidState().isEmpty() || !there.isAir() && !there.canBeReplaced()) {
				return 0;
			}
		}
		BlockState fill = surface.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : surface;
		level.setBlock(surfacePos, fill, Block.UPDATE_CLIENTS);
		for (int h = 1; h <= cut.depth(); h++) {
			boolean last = h == cut.depth();
			level.setBlock(surfacePos.above(h), last ? surface : fill, last ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
		}
		return cut.depth() + 1;
	}

	/** Lets a column of liquefied ground sink `depth` blocks, its top (grass, sand) settling with it. Never next to water. */
	private int sink(Cut cut) {
		int bx = cut.x();
		int bz = cut.z();
		if (!loaded(bx, bz) || !loadedAround(bx, bz)) {
			return 0;
		}
		int top = ground(bx, bz);
		BlockState surface = level.getBlockState(scratch.set(bx, top, bz));
		int floor = top - cut.depth();
		if (!sinkable(surface) || floor <= level.getMinY() + 1) {
			return 0;
		}
		for (int by = top; by >= floor; by--) {
			BlockState state = level.getBlockState(scratch.set(bx, by, bz));
			if (state.isAir() || state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty() || besideFluid(scratch)) {
				return 0;
			}
		}
		int used = clearCover(bx, top, bz);
		for (int by = top; by > floor; by--) {
			level.setBlock(scratch.set(bx, by, bz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			used++;
		}
		level.setBlock(scratch.set(bx, floor, bz), surface, Block.UPDATE_CLIENTS);
		sunk++;
		if (cut.opening() > 0) {
			opened(bx + 0.5, floor + 1.0, bz + 0.5, cut.size(), cut.opening());
		}
		return used + 1;
	}

	/** Clears what grows or lies on top of a column (plants, a thin snow, a torch), so that none of it is left floating over the hole. */
	private int clearCover(int bx, int top, int bz) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int cleared = 0;
		for (int by = top + 1; by <= top + 2 && by <= level.getMaxY(); by++) {
			BlockState state = level.getBlockState(pos.set(bx, by, bz));
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || !state.getCollisionShape(level, pos).isEmpty()) {
				break;
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			cleared++;
		}
		return cleared;
	}

	/** Whether water or lava lies beside a block, or might (where the chunk beside isn't loaded): breaking it would let it in. */
	private boolean besideFluid(BlockPos pos) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int nx = pos.getX() + side.getStepX();
			int nz = pos.getZ() + side.getStepZ();
			if (!loaded(nx, nz) || !level.getBlockState(probe.set(nx, pos.getY(), nz)).getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** The chunks of a column and of those beside it are loaded: looking next door won't load a chunk there and then. */
	private boolean loadedAround(int bx, int bz) {
		return loaded(bx, bz) && loaded(bx + 1, bz) && loaded(bx - 1, bz) && loaded(bx, bz + 1) && loaded(bx, bz - 1);
	}

	/** The land as it lies, not built on: where the lesser cracks and the pits may open. */
	private static boolean natural(BlockState state) {
		return WindDamage.isGround(state) || state.is(Blocks.MUD) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.DIRT_PATH)
				|| state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.SNOW);
	}

	/** Ground that can settle a few blocks whole: a full block of earth, sand or the like. */
	private static boolean sinkable(BlockState state) {
		return WindDamage.isGround(state) || state.is(Blocks.MUD);
	}

	/**
	 * Tells the clients of a fresh opening. While the fault is torn open there are many of those along it, so to make room
	 * the stale ones go first, then the oldest that isn't a pit (a pit falling in is rarer, and is heard).
	 */
	private void opened(double ox, double oy, double oz, double size, int kind) {
		recent.removeIf(opening -> age - opening.at() >= OPENING_TICKS);
		if (recent.size() >= MAX_OPENINGS) {
			Opening oldest = recent.peekFirst();
			for (Opening opening : recent) {
				if (opening.kind() != OPEN_PIT) {
					oldest = opening;
					break;
				}
			}
			recent.remove(oldest);
		}
		recent.addLast(new Opening(ox, oy, oz, (float) size, kind, age));
	}

	// --- the shaking -------------------------------------------------------------------------

	private void shakeLand() {
		falls = 0;
		slides = 0;
		shatters = 0;
		falling.removeIf(block -> block.isRemoved());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// About the epicentre, more the nearer.
		int samples = (int) (20 + 30 * (magnitude - 4.0));
		double reach = Math.min(felt, 600.0);
		for (int i = 0; i < samples; i++) {
			double d = reach * random.nextDouble() * random.nextDouble();
			double a = Seismic.shaking(magnitude, d, age);
			if (a < 0.08) {
				continue;
			}
			double angle = random.nextDouble() * Math.PI * 2.0;
			int bx = (int) Math.floor(x + Math.cos(angle) * d);
			int bz = (int) Math.floor(z + Math.sin(angle) * d);
			if (!loaded(bx, bz)) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
			int by = random.nextInt(4) == 0 ? top - 2 - random.nextInt(48) : top - random.nextInt(4);
			if (by <= level.getMinY()) {
				continue;
			}
			shake(pos.set(bx, by, bz), a);
		}
		// Around the players, where it is seen: the tops of walls and chimneys, the edges of roofs, the windows and what hangs in the rooms.
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			double a = Seismic.shaking(magnitude, distance2d(player, x, z), age);
			if (a < 0.12) {
				continue;
			}
			int count = (int) Math.min(48.0, a * 60.0);
			int px = (int) Math.floor(player.getX());
			int py = (int) Math.floor(player.getY());
			int pz = (int) Math.floor(player.getZ());
			for (int i = 0; i < count; i++) {
				int bx = px + random.nextInt(41) - 20;
				int bz = pz + random.nextInt(41) - 20;
				if (!loaded(bx, bz)) {
					continue;
				}
				int by = i % 2 == 0 ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1 - random.nextInt(2)
						: py - 3 + random.nextInt(16);
				if (by <= level.getMinY() || by >= level.getMaxY()) {
					continue;
				}
				shake(pos.set(bx, by, bz), a);
			}
		}
	}

	private void shake(BlockPos.MutableBlockPos pos, double a) {
		if (!loadedAround(pos.getX(), pos.getZ())) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return;
		}
		Block block = state.getBlock();
		if (block instanceof PointedDripstoneBlock && state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.DOWN) {
			if (a > 0.2 && random.nextDouble() < a) {
				breakStalactite(pos.immutable());
			}
			return;
		}
		BlockState below = level.getBlockState(pos.below());
		if (block instanceof FallingBlock && FallingBlock.isFree(below)) {
			drop(pos.immutable(), state, false);
			return;
		}
		Block cracked = CRACKED.get(block);
		if (cracked != null) {
			if (a > 0.15 && random.nextDouble() < a) {
				level.setBlock(pos, cracked.defaultBlockState(), Block.UPDATE_ALL);
			}
			return;
		}
		String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (name.contains("glass")) {
			if (a > 0.12 && shatters < SHATTERS_PER_TICK && random.nextDouble() < a * 0.8) {
				shatters++;
				level.destroyBlock(pos, false);
			}
			return;
		}
		if (block instanceof FlowerPotBlock || block instanceof LanternBlock || block instanceof CandleBlock) {
			if (shatters < SHATTERS_PER_TICK && random.nextDouble() < a * 1.5) {
				shatters++;
				level.destroyBlock(pos, true);
			}
			return;
		}
		boolean free = FallingBlock.isFree(below);
		if (built(pos, state)) {
			int sides = supports(pos);
			if (a > 0.2 && free && sides <= 1 && random.nextDouble() < a) {
				// Built over nothing and held up by too little: it comes down.
				drop(pos.immutable(), state, true);
			} else if (a > 0.55 && free && sides <= 2 && random.nextDouble() < a * 0.4) {
				// In violent shaking an eave or a lintel gives way too.
				drop(pos.immutable(), state, true);
			} else if (a > 0.25 && sides <= 2 && level.getBlockState(pos.above()).isAir() && random.nextDouble() < a * 0.45) {
				topple(pos.immutable(), state, a);
			}
		} else if (a > 0.3 && WindDamage.isGround(state) && (free || cliffEdge(pos)) && slides < 3 && falling.size() < MAX_FALLING / 2
				&& random.nextDouble() < a * 0.5) {
			// Rock and earth slide off the edge (the walls of the fissure too).
			slides++;
			drop(pos.immutable(), state, true);
		}
	}

	private boolean built(BlockPos pos, BlockState state) {
		return state.isCollisionShapeFullBlock(level, pos) && !WindDamage.isGround(state) && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
				&& !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0.0F && !state.is(Blocks.OBSIDIAN);
	}

	private int supports(BlockPos pos) {
		int count = 0;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (!level.getBlockState(pos.relative(side)).isAir()) {
				count++;
			}
		}
		return count;
	}

	/** Open air beside it and below that: the brink of a cliff. */
	private boolean cliffEdge(BlockPos pos) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos next = pos.relative(side);
			if (level.getBlockState(next).isAir() && level.getBlockState(next.below()).isAir() && level.getBlockState(next.below(2)).isAir()) {
				return true;
			}
		}
		return false;
	}

	/** Lets a block fall, if not too many are falling already; it hurts what it lands on if `hurts`. */
	private FallingBlockEntity drop(BlockPos pos, BlockState state, boolean hurts) {
		if (falls >= FALLS_PER_TICK || falling.size() >= MAX_FALLING) {
			return null;
		}
		falls++;
		FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
		if (block == null) {
			return null;
		}
		if (hurts) {
			block.setHurtsEntities(2.0F, 30);
		}
		falling.add(block);
		return block;
	}

	/** The top of a wall or a chimney rocks off and falls away to one side. */
	private void topple(BlockPos pos, BlockState state, double a) {
		Direction toward = null;
		int open = 0;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(pos.relative(side)).isAir() && random.nextInt(++open) == 0) {
				toward = side;
			}
		}
		if (toward == null) {
			return;
		}
		FallingBlockEntity block = drop(pos, state, true);
		if (block != null) {
			double push = 0.12 + a * 0.12;
			shove(block, toward.getStepX() * push, 0.05, toward.getStepZ() * push);
		}
	}

	/** A stalactite breaks off whole at its root and falls, point first. */
	private void breakStalactite(BlockPos pos) {
		BlockPos root = pos;
		for (int i = 0; i < 16 && level.getBlockState(root.above()).getBlock() instanceof PointedDripstoneBlock; i++) {
			root = root.above();
		}
		int length = 0;
		for (BlockPos at = root; length < 16 && level.getBlockState(at).getBlock() instanceof PointedDripstoneBlock; at = at.below()) {
			length++;
		}
		if (falling.size() + length > MAX_FALLING) {
			return;
		}
		BlockPos at = root;
		for (int i = 0; i < length; i++) {
			FallingBlockEntity piece = FallingBlockEntity.fall(level, at, level.getBlockState(at));
			if (piece != null) {
				piece.setHurtsEntities(2.0F, 40);
				falling.add(piece);
			}
			at = at.below();
		}
	}

	// --- the living --------------------------------------------------------------------------

	private void shakeEntities() {
		boolean mobs = age % 6 == 0;
		Set<LivingEntity> shaken = mobs ? new HashSet<>() : null;
		for (ServerPlayer player : playersWithin(felt)) {
			double a = Seismic.shaking(magnitude, distance2d(player, x, z), age);
			jolt(player, a, 1.0);
			// The animals and monsters around the player stagger and are thrown about too (less often, harder).
			if (mobs && a > 0.05) {
				for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24.0), e -> !(e instanceof Player))) {
					if (shaken.add(mob)) {
						jolt(mob, Seismic.shaking(magnitude, distance2d(mob, x, z), age), 1.8);
					}
				}
			}
		}
	}

	/** The ground lurching under someone: a shove one way or another, in hard shaking a hop, a stumble or a fall. */
	private void jolt(LivingEntity entity, double a, double scale) {
		if (a < 0.03 || immune(entity) || !entity.onGround()) {
			return;
		}
		double push = Math.min(0.8, Math.min(0.6, a * 0.4) * scale);
		double angle = random.nextDouble() * Math.PI * 2.0;
		double up = a > 0.3 ? random.nextDouble() * Math.min(0.3, push * 0.6) : 0.0;
		shove(entity, Math.cos(angle) * push, up, Math.sin(angle) * push);
		if (a > 0.45 && random.nextDouble() < 0.06) {
			// Thrown off your feet.
			entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3, true, false, true));
		}
		if (a > 0.9 && random.nextDouble() < 0.008) {
			// Thrown down hard.
			hurt(entity, DisasterDamage.EARTHQUAKE, (float) Math.min(4.0, 1.0 + a * 1.5));
		}
	}

	private void warnings() {
		for (ServerPlayer player : playersWithin(felt)) {
			double d = distance2d(player, x, z);
			double expected = Seismic.pga(magnitude, d);
			if (expected < 0.03) {
				continue;
			}
			int arrives = (int) (d / Seismic.S_SPEED);
			if (arrives >= 60 && expected >= 0.08) {
				warn(player, Component.translatable("naturesmod.warning.quake", arrives / 20));
			}
			Component report = Component.translatable("naturesmod.report.quake", String.format(java.util.Locale.ROOT, "%.1f", magnitude), (int) d,
					compass(player.getX(), player.getZ(), x, z), Component.translatable("naturesmod.shaking." + Seismic.feeling(expected)));
			Disasters.later(arrives + 60, () -> {
				if (!player.isRemoved()) {
					warn(player, report);
				}
			});
		}
	}

	private void warn(ServerPlayer player, Component message) {
		if (com.naturesmod.NaturesConfig.get().disasterWarnings) {
			player.sendSystemMessage(message.copy().withStyle(net.minecraft.ChatFormatting.GOLD));
		}
	}

	@Override
	protected void finish() {
		if (aftershock || magnitude < 5.0) {
			return;
		}
		int count = 1 + (int) ((magnitude - 5.0) * 1.6) + random.nextInt(2);
		for (int i = 0; i < count; i++) {
			double m = magnitude - 1.2 - (-Math.log(1.0 - random.nextDouble())) * 0.5;
			if (m < 3.5) {
				continue;
			}
			int delay = (int) (200 + 2400 * random.nextDouble() * random.nextDouble());
			double spread = Seismic.reach(m) * 0.5;
			Vec3 at = new Vec3(x + random.nextGaussian() * spread, y, z + random.nextGaussian() * spread);
			float aftershockMagnitude = (float) m;
			Disasters.later(delay, () -> Disasters.add(new Earthquake(level, at, aftershockMagnitude, natural, true)));
		}
	}

	// --- for tests and commands --------------------------------------------------------------

	/** Anything left standing close to the fault: so the client test and players can find it. */
	public AABB rupture() {
		// The fault runs 4 + width blocks to one side of the epicentre, and the fissure is up to `width` across.
		double half = Math.max(ruptureHalf, 4.0) + 4.0 + width * 2.0;
		return new AABB(x - half, y - depth - 6, z - half, x + half, y + scarp + 6, z + half);
	}

	/** How deep the fault's fissure is torn in its middle, in blocks (0 below M6, which break no ground). */
	public int fissureDepth() {
		return ruptureHalf > 0.0 ? depth : 0;
	}

	/** How far the fissure runs each way from the middle of the fault, in blocks. */
	public double ruptureHalfLength() {
		return ruptureHalf;
	}

	/** Blocks dug out of the ground so far: the fissure, the cracks and the pits. */
	public int blocksBroken() {
		return broken;
	}

	/** Collapse pits opened so far (they are dug over the next few ticks). */
	public int pitsOpened() {
		return pitsOpened;
	}

	/** Cracks opened about the fault so far, not counting those branching off it. */
	public int cracksOpened() {
		return cracksOpened;
	}

	/** Columns of liquefied ground that have sunk. */
	public int columnsSunk() {
		return sunk;
	}

	/** Columns still waiting to be dug, heaved or sunk. */
	public int digging() {
		return rupture.size() + holes.size();
	}

	/** Falling blocks this quake has shaken loose that are still falling. */
	public int fallingBlocks() {
		return falling.size();
	}
}
