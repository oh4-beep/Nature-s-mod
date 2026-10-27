package com.naturesmod.disaster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.naturesmod.NaturesConfig;
import com.naturesmod.tree.TreeFeller;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A tsunami: a wave raised far out at sea that comes ashore as a wall of water. It comes in from the
 * nearest open sea toward the stretch of coast it was sent at. First the sea draws back from the
 * shore and the roar of the wave is heard building out at sea; then the wave is seen coming, a long
 * low swell that rises as it nears the land and comes ashore as a wall of water four to twenty
 * blocks high (by its strength), rolling inland at about the speed of a running player and slowing
 * as it runs up the land. Whatever is in it is swept along and tumbled over and over, and what it
 * catches it carries along in its face for some seconds, battered by the wreckage going with it,
 * before it lets go; behind its face the flood runs on inland, lower and slower. It smashes what is
 * weak in its way: leaves and plants, glass, fences, doors and torches, and from strength four the
 * planks and wool of wooden buildings and the trees. Stone stands, and so does anything holding
 * something (a chest, a furnace); a roof over your head keeps the water off as long as the wave
 * doesn't top it, and high ground stays dry. It runs some sixty to two hundred and fifty blocks
 * inland, by its strength (less where the land rises out of its reach), and then the water drains
 * back to the sea for a while, dragging what is near the ground with it. With no sea near enough to
 * reach where it was sent, it comes anyway over the land: a smaller flood wave.
 *
 * <p>The water of the wave is real only about its face and near the players, who see it: a band of
 * water blocks rolls in with the front, each block standing four ticks before it is taken away again.
 * A block of water placed doesn't flow before its fifth tick, so this water never runs off, never
 * spreads and never makes new sources of itself, and none of it is ever left behind; the rest of the
 * wave is drawn by its clients. The work is bounded every tick: so many blocks of water poured (and
 * as many taken away), so many columns looked at for what the wave breaks and so many blocks broken,
 * and only the players and what is near them are looked at (the things other than players every few
 * ticks, so many at most).
 */
public final class Tsunami extends Disaster {
	/** Its phases: the sea drawing back, the wave coming in over the sea, the wave ashore, the water draining back. */
	public static final int DRAWING_BACK = 0;
	public static final int AT_SEA = 1;
	public static final int ASHORE = 2;
	public static final int BACKWASH = 3;
	/** Where its clients find each of the numbers it sends them (see {@link #extra()}). */
	public static final int FRONT = 0;
	public static final int SPEED = 1;
	public static final int HEIGHT = 2;
	public static final int BASE = 3;
	public static final int HALF_WIDTH = 4;
	public static final int PHASE = 5;
	public static final int LANDFALL = 6;
	public static final int REACH = 7;
	public static final int DRAINED = 8;
	public static final int INLAND = 9;
	public static final int SENT_AT = 10;
	public static final int FULL = 11;
	public static final int CREST = 12;
	public static final int FLOOD = 13;
	private static final int NUMBERS = 14;

	/** The wave's height ashore, in blocks above the sea, at strengths 1 to 5. */
	private static final double[] HEIGHTS = {4.0, 7.0, 11.0, 15.0, 20.0};
	/** Ticks the sea spends drawing back before the wave is under way. */
	private static final int DRAWBACK = 80;
	/**
	 * How many ticks a block of the wave's water stands before it is taken away again. A block of water waits five
	 * ticks before it first flows: taken away before then, it never runs off and never makes a new source of itself.
	 */
	private static final int WATER_LIFE = 4;
	/** At most this many blocks of water poured a tick (as many are taken away a tick, four ticks on), and standing at once. */
	private static final int WATER_PER_TICK = 150;
	private static final int MAX_WATER = 900;
	/** Blocks of water whose land was no longer loaded to take them away: tried again now and then, so many at most. */
	private static final int MAX_STRANDED = 4096;
	/** At most this many things other than players in the water are pushed about each tick, and this many animals carried. */
	private static final int MAX_NEARBY = 96;
	private static final int MAX_CARRIED_MOBS = 24;
	/** At most this many columns looked at a tick for what the wave breaks. */
	private static final int MAX_COLUMNS = 48;
	/** How far ahead of a player the wave is when the player is told it is almost there. */
	private static final double NEAR_WARNING = 80.0;
	/** The light fittings of a building, by name: what the wave tears away (if it isn't iron or stone). */
	private static final String[] FITTINGS = {"fence", "door", "torch", "lantern", "ladder", "carpet", "button", "pressure_plate", "lever", "rail",
			"scaffolding", "flower_pot", "potted_", "candle"};
	/** The parts of the wave something is in: the sea drawing back ahead of it, its face, the flood behind it, the water draining back. */
	private static final int IN_DRAWBACK = 0;
	private static final int IN_FACE = 1;
	private static final int IN_FLOOD = 2;
	private static final int IN_BACKWASH = 3;

	/** What a block is to the wave. */
	private enum Kind {
		/** The land itself, or what can't be broken: nothing under it is looked at. */
		LAND,
		LOG,
		LEAVES,
		/** Crops, flowers, grass and the like. */
		PLANT,
		GLASS,
		/** Fences, doors, torches and the like. */
		FITTING,
		/** Planks, wooden slabs and stairs, wool: the walls and roofs of wooden buildings. */
		WOOD,
		/** Anything else, which stands. */
		OTHER
	}

	/** A block of the wave's water, and the age at which it is taken away again. */
	private record Placed(int x, int y, int z, int until) {
	}

	/** A column the front has just reached, and how far it is (across the wave's way) from the player it is poured for. */
	private record Cell(int x, int z, double off) {
	}

	/** The water about something: which part of the wave, how high its top stands, and how it flows. */
	private record Flow(int zone, double top, Vec3 velocity) {
	}

	/** Something the wave has caught and is carrying along in its face, until it lets go (its age then). */
	private static final class Carry {
		final Entity entity;
		final int until;
		final double phase;
		int ticks;

		Carry(Entity entity, int until, double phase) {
			this.entity = entity;
			this.until = until;
			this.phase = phase;
		}
	}

	private final int rank;
	/** How fast the wave crosses the sea, and how fast it comes ashore (it slows as it runs up the land), in blocks a tick. */
	private final double seaSpeed;
	private final double landSpeed;
	/** How far behind its face the flood still stands, in blocks. */
	private final double floodLength;
	/** How long the water takes to drain back to the sea, in ticks. */
	private final int backwashTicks;
	/** The stretch of coast it was sent at. */
	private final double targetX;
	private final double targetZ;
	/** Its full height ashore, how far it runs inland, and how far its front reaches to either side of the line it comes in on. */
	private double full;
	private double runUp;
	private double halfWidth;
	/** The way it comes in (a unit vector): from where it starts (this disaster's x and z) toward the stretch of coast it was sent at. */
	private double dirX = 1.0;
	private double dirZ = 0.0;
	/** The level of the water it rides on: the sea's, or, for a flood wave inland, the land's where it was sent. */
	private double base;
	/** How far along its way it comes ashore, and how far along its way it runs at most. */
	private double landfall;
	private double reach;
	private boolean inland;
	private int phase = DRAWING_BACK;
	/** How far along its way its front has come, now and a tick ago. */
	private double front;
	private double previous;
	private double speedNow;
	private double heightNow;
	/** The age at which the water began to drain back. */
	private int drainFrom = -1;
	/** How many looks in a row found the land standing above it everywhere: it has run as far as it can. */
	private int stalled;
	private int effectsLeft;
	private int mobsCarried;
	/** Its water standing now, oldest first, and any whose land wasn't loaded to take it away. */
	private final Deque<Placed> water = new ArrayDeque<>();
	private final List<Placed> stranded = new ArrayList<>();
	/** Where each player was last tick: how fast one is going is how far it went since. */
	private final Map<UUID, Vec3> lastSeen = new HashMap<>();
	private final Map<UUID, Carry> carried = new HashMap<>();
	/** What it has just let go of, and until when (its age) that is not caught again. */
	private final Map<UUID, Integer> letGo = new HashMap<>();
	/** What its face has already struck. */
	private final Set<UUID> struck = new HashSet<>();
	/** Players already told it is almost on them. */
	private final Set<UUID> warnedNear = new HashSet<>();
	/** Everything other than players near the players, as last looked for. */
	private final List<Entity> nearby = new ArrayList<>();
	/** Places the tests have it treat as if a player stood there (see {@link #watch}). */
	private final List<Vec3> watchers = new ArrayList<>();
	private final Map<Block, Kind> kinds = new HashMap<>();
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
	private int waterPlaced;
	private int waterRemoved;
	private int blocksBroken;
	private int treesFelled;
	private int sweptUp;

	Tsunami(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.TSUNAMI, level, at, intensity, natural, 2400);
		double s = this.intensity;
		rank = Math.max(1, Math.min(5, Math.round(this.intensity)));
		full = heightFor(s);
		runUp = 60.0 + (s - 1.0) * 47.5;
		halfWidth = 200.0 + 40.0 * s;
		seaSpeed = 1.05 + 0.05 * s;
		landSpeed = 0.62 + 0.08 * s;
		floodLength = 20.0 + 6.0 * s;
		backwashTicks = (int) (200 + 30 * s);
		targetX = at.x;
		targetZ = at.z;
		base = level.getSeaLevel();
		// Until its way in is worked out (see begin): straight in from the west to where it was sent.
		landfall = 300.0;
		reach = landfall + runUp;
		x = targetX - landfall;
		heightNow = full * 0.35;
	}

	/** The wave's full height ashore at a strength, between the heights of the whole strengths. */
	private static double heightFor(double strength) {
		double i = Math.max(0.0, Math.min(4.0, strength - 1.0));
		int k = (int) Math.floor(i);
		if (k >= 4) {
			return HEIGHTS[4];
		}
		return HEIGHTS[k] + (HEIGHTS[k + 1] - HEIGHTS[k]) * (i - k);
	}

	// --- the shape of the wave (shared with its clients, which draw it) -------------------------------

	/**
	 * How high the wave stands (blocks above its base) when its front is `along` blocks along its way: out at sea a
	 * long low swell that rises over the last stretch before the land, then its full height as it comes ashore, falling
	 * away as it runs up the land to `reach`. A flood wave over the land stands its full height until it reaches the
	 * place it was sent at (its `landfall`), and falls away after.
	 */
	public static double waveHeight(double along, double full, double landfall, double reach, boolean inland) {
		if (!(full > 0.0)) {
			return 0.0;
		}
		if (along < landfall) {
			if (inland) {
				return full;
			}
			double shoal = Math.max(0.0, Math.min(1.0, 1.0 - (landfall - along) / 120.0));
			return full * (0.35 + 0.65 * shoal);
		}
		double run = reach - landfall;
		double t = run > 1.0 ? Math.max(0.0, Math.min(1.0, (along - landfall) / run)) : 1.0;
		return full * (1.0 - 0.85 * t * t);
	}

	/**
	 * How high the water stands (blocks above the wave's base) `behind` blocks behind its front, where the wave stood
	 * `there` blocks high as it went by: its face as high as the wave, the flood behind it lower and falling, and past
	 * `flood` blocks nothing (drained away).
	 */
	public static double floodHeight(double behind, double there, double crest, double flood) {
		if (!(behind >= 0.0) || behind > flood) {
			return 0.0;
		}
		if (behind <= crest) {
			return there;
		}
		double k = (behind - crest) / Math.max(1.0, flood - crest);
		return there * 0.55 * (1.0 - 0.6 * k);
	}

	/** How deep the water draining back to the sea runs over the land, `drained` of the way (0 to 1) through the backwash. */
	public static double backwashDepth(double full, double drained) {
		double left = Math.max(0.0, Math.min(1.0, 1.0 - drained));
		return 0.8 + 0.25 * full * Math.pow(left, 1.2);
	}

	/** How fast the water drains back (blocks a tick): quickly gathering, then slackening. */
	public static double backwashSpeed(double strength, double drained) {
		double d = Math.max(0.0, Math.min(1.0, drained));
		return (0.22 + 0.05 * strength) * Math.pow(1.0 - d, 0.8) * Math.min(1.0, d / 0.08 + 0.1);
	}

	/** How deep its face is, from its front back, in blocks: the higher the wave, the thicker. */
	private double crestLength() {
		return 2.5 + 0.2 * heightNow;
	}

	// --- the disaster as a whole -------------------------------------------------------------------

	@Override
	public float radius() {
		return (float) Math.max(16.0, Math.max(reach, halfWidth));
	}

	@Override
	public Vec3 direction() {
		return new Vec3(dirX, 0.0, dirZ);
	}

	@Override
	public float strength() {
		if (phase == DRAWING_BACK) {
			return Math.max(0.0F, Math.min(1.0F, age / (float) DRAWBACK));
		}
		if (phase == BACKWASH) {
			return (float) (1.0 - drained());
		}
		return 1.0F;
	}

	/**
	 * For its clients: where its front is along its way and how fast it comes on, how high it stands now and on what
	 * water, how far its front reaches to either side, its phase, where it comes ashore and how far it runs, how far the
	 * water has drained back, whether it is a flood wave inland, the age these numbers were sent at, its full height,
	 * how deep its face is and how far behind it the flood stands.
	 */
	@Override
	public float[] extra() {
		float[] out = new float[NUMBERS];
		out[FRONT] = (float) front;
		out[SPEED] = (float) (phase == AT_SEA || phase == ASHORE ? speedNow : 0.0);
		out[HEIGHT] = (float) heightNow;
		out[BASE] = (float) base;
		out[HALF_WIDTH] = (float) halfWidth;
		out[PHASE] = phase;
		out[LANDFALL] = (float) landfall;
		out[REACH] = (float) reach;
		out[DRAINED] = (float) drained();
		out[INLAND] = inland ? 1.0F : 0.0F;
		out[SENT_AT] = age;
		out[FULL] = (float) full;
		out[CREST] = (float) crestLength();
		out[FLOOD] = (float) floodLength;
		return out;
	}

	// --- for the tests ----------------------------------------------------------------------------

	/** Its phase: {@link #DRAWING_BACK}, {@link #AT_SEA}, {@link #ASHORE} or {@link #BACKWASH}. */
	public int phase() {
		return phase;
	}

	/** How far its front has come along its way, in blocks from where it started (this disaster's position). */
	public double front() {
		return front;
	}

	/** Where the middle of its front is now. */
	public Vec3 frontMiddle() {
		return new Vec3(x + dirX * front, base, z + dirZ * front);
	}

	/** How far along its way a place lies (to compare with {@link #front()}). */
	public double along(double px, double pz) {
		return (px - x) * dirX + (pz - z) * dirZ;
	}

	/** How high it stands at its front now, in blocks above its base. */
	public double height() {
		return heightNow;
	}

	/** Its full height ashore, in blocks above its base. */
	public double fullHeight() {
		return full;
	}

	/** The level of the water it rides on: the sea's, or (a flood wave inland) the land's where it was sent. */
	public double base() {
		return base;
	}

	/** Whether no sea was found near where it was sent, and it comes as a flood wave over the land. */
	public boolean inland() {
		return inland;
	}

	/** How far along its way it comes ashore (for a flood wave, where it was sent). */
	public double landfall() {
		return landfall;
	}

	/** How far along its way it runs at most (once it is draining back, how far it got). */
	public double reach() {
		return reach;
	}

	/** Whether the wave (its face, the flood behind it or the water draining back) has water at a place now. */
	public boolean inWater(double px, double py, double pz) {
		Flow flow = flowAt(px, py, pz);
		return flow != null && flow.zone() != IN_DRAWBACK;
	}

	/** How many blocks of water it has poured so far. */
	public int waterPlaced() {
		return waterPlaced;
	}

	/** How many blocks of its water stand in the world now: none once it is over (unless their land was unloaded). */
	public int waterStanding() {
		return water.size() + stranded.size();
	}

	/** How many blocks of its water it has taken away again so far. */
	public int waterRemoved() {
		return waterRemoved;
	}

	/** How many blocks it has broken so far (not counting the trees it brought down). */
	public int blocksBroken() {
		return blocksBroken;
	}

	/** How many trees it has brought down so far. */
	public int treesFelled() {
		return treesFelled;
	}

	/** How many things it has caught and carried along so far. */
	public int sweptUp() {
		return sweptUp;
	}

	/** How many things it is carrying along now. */
	public int carriedCount() {
		return carried.size();
	}

	/** Whether it has caught this and is carrying it along. */
	public boolean carrying(Entity entity) {
		return carried.containsKey(entity.getUUID());
	}

	/**
	 * Has it treat a place as if a player stood there (for tests, which have no players): it pours its water, breaks
	 * what is weak and sweeps along what is in it about that place too, as it does about the players.
	 */
	public void watch(Vec3 at) {
		if (watchers.size() < 8) {
			watchers.add(at);
		}
	}

	/** Skips ahead (for tests): the wave goes on its way as if the time had passed, with nothing else happening in it. */
	@Override
	public void skip(int ticks) {
		int from = age;
		super.skip(ticks);
		int to = age;
		for (int t = from + 1; t <= to && phase != BACKWASH; t++) {
			age = t;
			advance(true);
		}
		age = to;
		// The water of the ticks skipped never was.
		previous = front;
	}

	// --- where it comes from ----------------------------------------------------------------------

	@Override
	protected void begin() {
		plan();
		Component from = compass(-dirX, -dirZ);
		int high = (int) Math.round(full);
		String key = inland ? "naturesmod.warning.tsunami_flood" : "naturesmod.warning.tsunami";
		warn(reach + 400.0, player -> Component.translatable(key, high, from));
	}

	/**
	 * Works out its way in: from the nearest open sea toward the stretch of coast it was sent at, starting far enough
	 * out that it is seen coming; where it comes ashore; and how long it all takes. With no sea near enough for the wave
	 * to run up to where it was sent, a flood wave from some way over the land, standing on the land where it was sent.
	 */
	private void plan() {
		int tx = block(targetX);
		int tz = block(targetZ);
		int sea = level.getSeaLevel();
		double[] open = findSea(tx, tz, sea);
		inland = true;
		if (open != null) {
			double dx = targetX - open[0];
			double dz = targetZ - open[1];
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > 1.0) {
				dirX = dx / d;
				dirZ = dz / d;
			} else {
				setRandomWay();
			}
			double start = Math.max(240.0, Math.min(520.0, d + 140.0));
			x = targetX - dirX * start;
			z = targetZ - dirZ * start;
			base = sea;
			// The coast is the first land past the open sea (an island further out doesn't count).
			landfall = coast(Math.max(0.0, start - d), start);
			reach = landfall + runUp;
			// Sent further inland than the wave runs, it comes as a flood wave over the land instead (from the sea's side).
			inland = reach < start + 8.0;
		} else {
			setRandomWay();
		}
		if (inland) {
			// Standing on the land where it was sent (ground() takes an unloaded column to be where the disaster is: there).
			base = ground(tx, tz) + 1;
			full = Math.max(3.0, full * 0.55);
			runUp *= 0.5;
			halfWidth = 90.0 + 15.0 * intensity;
			double start = 150.0 + 10.0 * intensity;
			x = targetX - dirX * start;
			z = targetZ - dirZ * start;
			landfall = start;
			reach = landfall + runUp;
		}
		y = base;
		heightNow = waveHeight(0.0, full, landfall, reach, inland);
		double seaTicks = inland ? 0.0 : landfall / seaSpeed;
		double landTicks = (inland ? reach : reach - landfall) / (landSpeed * 0.6);
		duration = DRAWBACK + (int) Math.ceil(seaTicks + landTicks) + backwashTicks + 40;
	}

	private void setRandomWay() {
		double angle = random.nextDouble() * Math.PI * 2.0;
		dirX = Math.cos(angle);
		dirZ = Math.sin(angle);
	}

	/**
	 * The nearest open sea (wide, deep water) within some 380 blocks of a place: looked for ring by ring outward, and of
	 * the sea found on the nearest ring the part with the most sea beyond it. Null if there is none.
	 */
	private double[] findSea(int tx, int tz, int sea) {
		for (int r = 32; r <= 384; r += 24) {
			int samples = Math.max(12, (int) (Math.PI * 2.0 * r / 28.0));
			double turn = random.nextDouble() * Math.PI * 2.0;
			double[] best = null;
			int bestOpen = -1;
			for (int k = 0; k < samples; k++) {
				double angle = turn + Math.PI * 2.0 * k / samples;
				double cx = Math.cos(angle);
				double cz = Math.sin(angle);
				int bx = tx + (int) Math.round(cx * r);
				int bz = tz + (int) Math.round(cz * r);
				if (!openWater(bx, bz, sea)) {
					continue;
				}
				int open = 0;
				for (int step = 1; step <= 3; step++) {
					if (ocean(tx + (int) Math.round(cx * (r + 48 * step)), tz + (int) Math.round(cz * (r + 48 * step)), sea)) {
						open++;
					}
				}
				if (open > bestOpen) {
					bestOpen = open;
					best = new double[] {bx + 0.5, bz + 0.5};
				}
			}
			if (best != null) {
				return best;
			}
		}
		return null;
	}

	/** Whether a place lies in an ocean, from the lie of the land (loaded or not). */
	private boolean ocean(int bx, int bz, int sea) {
		return level.getUncachedNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(sea), QuartPos.fromBlock(bz)).is(BiomeTags.IS_OCEAN);
	}

	/** Open sea: an ocean, and where it is loaded, water at the sea's surface and some blocks deep. */
	private boolean openWater(int bx, int bz, int sea) {
		if (!ocean(bx, bz, sea)) {
			return false;
		}
		if (!loaded(bx, bz)) {
			return true;
		}
		int top = ground(bx, bz);
		if (top < sea - 3 || top > sea + 1) {
			return false;
		}
		BlockPos surface = new BlockPos(bx, top, bz);
		return level.getFluidState(surface).is(FluidTags.WATER) && level.getFluidState(surface.below(5)).is(FluidTags.WATER);
	}

	/** How far along its way the wave first meets dry land (the coast), looking along the line it comes in on from `from` on. */
	private double coast(double from, double start) {
		double limit = start + runUp;
		for (double f = from; f <= limit; f += 3.0) {
			int bx = block(x + dirX * f);
			int bz = block(z + dirZ * f);
			if (dryLand(bx, bz)) {
				return f;
			}
		}
		// No land met: it comes ashore where it was sent.
		return start;
	}

	/** Whether a column is dry land: from its blocks where it is loaded, from the lie of the land where not. */
	private boolean dryLand(int bx, int bz) {
		if (loaded(bx, bz)) {
			return level.getFluidState(new BlockPos(bx, ground(bx, bz), bz)).isEmpty();
		}
		Holder<Biome> biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock((int) base), QuartPos.fromBlock(bz));
		return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER);
	}

	// --- each tick ----------------------------------------------------------------------------------

	@Override
	protected void tick() {
		drain();
		advance(false);
		List<Vec3> near = watched();
		if (age % 5 == 0) {
			gather(near);
		}
		carry();
		sweepPlayers();
		sweepThings();
		if (griefing() && (phase == AT_SEA || phase == ASHORE)) {
			batter(near);
			pourWater(near);
		}
		if (age % 100 == 0) {
			letGo.values().removeIf(until -> until < age);
		}
		if (natural && age % 100 == 0 && playersWithin(reach + halfWidth + 200.0).isEmpty()) {
			end();
		}
	}

	@Override
	protected void finish() {
		for (Placed placed : water) {
			if (!dry(placed) && stranded.size() < MAX_STRANDED) {
				stranded.add(placed);
			}
		}
		water.clear();
		stranded.removeIf(this::dry);
		for (Carry c : carried.values()) {
			release(c.entity);
		}
		carried.clear();
		nearby.clear();
		lastSeen.clear();
	}

	/** Moves the wave on its way (quietly when skipping ahead: nothing told, nothing let go of). */
	private void advance(boolean quiet) {
		previous = front;
		if (phase == DRAWING_BACK) {
			if (age < DRAWBACK) {
				return;
			}
			phase = inland ? ASHORE : AT_SEA;
		}
		if (phase == BACKWASH) {
			return;
		}
		speedNow = speedAt(front);
		front = Math.min(reach, front + speedNow);
		heightNow = waveHeight(front, full, landfall, reach, inland);
		if (phase == AT_SEA && front >= landfall) {
			phase = ASHORE;
		}
		if (!quiet && phase == ASHORE && age % 10 == 0) {
			checkStall();
		}
		if (front >= reach || stalled >= 4) {
			startBackwash(quiet);
		}
	}

	/** How fast the wave comes on with its front at `along`: fast over the sea, slower ashore and slowing as it runs up the land. */
	private double speedAt(double along) {
		if (!inland && along < landfall) {
			return seaSpeed;
		}
		double run = reach - landfall;
		double t = run > 1.0 ? Math.max(0.0, Math.min(1.0, (along - landfall) / run)) : 1.0;
		return landSpeed * (1.0 - 0.4 * t);
	}

	/** Whether the land just ahead of it stands above it everywhere it is looked at (about its middle and the players): it can go no further. */
	private void checkStall() {
		double top = base + heightNow;
		double[] across = new double[7];
		int count = 0;
		across[count++] = 0.0;
		for (Vec3 at : watched()) {
			if (count >= across.length) {
				break;
			}
			double px = at.x - x;
			double pz = at.z - z;
			across[count++] = Math.max(-halfWidth, Math.min(halfWidth, -px * dirZ + pz * dirX));
		}
		int looked = 0;
		boolean open = false;
		double a = front + 2.0;
		for (int i = 0; i < count && !open; i++) {
			int bx = block(x + dirX * a - dirZ * across[i]);
			int bz = block(z + dirZ * a + dirX * across[i]);
			if (!loaded(bx, bz)) {
				continue;
			}
			looked++;
			open = ground(bx, bz) + 1.0 < top - 0.5;
		}
		stalled = looked > 0 && !open ? stalled + 1 : 0;
	}

	/** It has run as far as it goes: the water drains back to the sea for a while, and then it is over. */
	private void startBackwash(boolean quiet) {
		phase = BACKWASH;
		drainFrom = age;
		reach = front;
		speedNow = 0.0;
		duration = Math.max(age + 1, Math.min(duration, age + backwashTicks));
		if (quiet) {
			return;
		}
		for (Carry c : carried.values()) {
			release(c.entity);
		}
		carried.clear();
		if (!NaturesConfig.get().disasterWarnings) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && flowAt(player.getX(), player.getY(), player.getZ()) != null) {
				player.sendSystemMessage(Component.translatable("naturesmod.warning.tsunami_backwash").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** How far the water has drained back: 0 as it begins, 1 when it is gone. */
	private double drained() {
		if (phase != BACKWASH || drainFrom < 0) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, (age - drainFrom) / (double) Math.max(1, duration - drainFrom)));
	}

	/** Where the players in or near its way are (as far as it reaches), and any places the tests have it watch: where it is seen. */
	private List<Vec3> watched() {
		List<Vec3> out = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && out.size() < 8 && nearWay(player.getX(), player.getZ())) {
				out.add(player.position());
			}
		}
		for (Vec3 at : watchers) {
			if (out.size() < 8 && nearWay(at.x, at.z)) {
				out.add(at);
			}
		}
		return out;
	}

	private boolean nearWay(double px, double pz) {
		double ex = px - x;
		double ez = pz - z;
		double along = ex * dirX + ez * dirZ;
		double across = -ex * dirZ + ez * dirX;
		return Math.abs(across) < halfWidth + 40.0 && along > -60.0 && along < reach + 80.0;
	}

	// --- the water about things -----------------------------------------------------------------------

	/**
	 * The water of the wave at a place now, or null where it has none: its face (as high as the wave), the flood
	 * behind it, the water draining back over the land it flooded, or (ahead of it) the sea drawing back from the
	 * shore. Where the land stands above the water, there is none; nor deep under the land (a cave, a cellar).
	 */
	private Flow flowAt(double px, double py, double pz) {
		double ex = px - x;
		double ez = pz - z;
		double along = ex * dirX + ez * dirZ;
		double across = -ex * dirZ + ez * dirX;
		if (!(Math.abs(across) <= halfWidth)) {
			return null;
		}
		int bx = block(px);
		int bz = block(pz);
		if (!loaded(bx, bz)) {
			return null;
		}
		int land = landAt(bx, bz);
		if (py < land - 8.0) {
			return null;
		}
		if (phase == BACKWASH) {
			double drained = drained();
			if (along < landfall - 40.0 || along > reach + 2.0) {
				return null;
			}
			double there = waveHeight(along, full, landfall, reach, inland);
			if (land + 1.0 >= base + there) {
				// The wave never got up here.
				return null;
			}
			double top = land + 1.0 + backwashDepth(full, drained);
			if (py > top) {
				return null;
			}
			double v = backwashSpeed(intensity, drained);
			return new Flow(IN_BACKWASH, top, new Vec3(-dirX * v, 0.0, -dirZ * v));
		}
		double behind = front - along;
		if (behind < -1.5) {
			// Ahead of it, the sea draws back from the shore.
			if (!inland && phase != ASHORE && along < landfall + 8.0 && behind < -20.0) {
				return new Flow(IN_DRAWBACK, base, new Vec3(-dirX * 0.1, 0.0, -dirZ * 0.1));
			}
			return null;
		}
		if (phase == DRAWING_BACK) {
			return null;
		}
		double crest = crestLength();
		double there = behind <= crest ? heightNow : waveHeight(along, full, landfall, reach, inland);
		double top = base + floodHeight(Math.max(0.0, behind), there, crest, floodLength);
		if (top <= land + 1.0 || py > top + 0.3) {
			return null;
		}
		if (behind <= crest) {
			double v = speedNow + 0.3;
			return new Flow(IN_FACE, top, new Vec3(dirX * v, 0.0, dirZ * v));
		}
		double k = (behind - crest) / Math.max(1.0, floodLength - crest);
		double current = speedNow * 0.65 * (1.0 - 0.7 * k);
		return new Flow(IN_FLOOD, top, new Vec3(dirX * current, 0.0, dirZ * current));
	}

	/**
	 * The top of the land in a column under any water standing on it above the sea: the wave's own water, standing a few
	 * ticks where its front has just passed, is no land for the wave to run up (nor for what it carries to rest on).
	 * The sea itself counts as the land, as it does for {@link #ground}.
	 */
	private int landAt(int bx, int bz) {
		int top = ground(bx, bz);
		if (!loaded(bx, bz)) {
			return top;
		}
		int low = Math.max(level.getMinY(), Math.max(top - 16, (int) Math.floor(base) - 1));
		while (top > low && !level.getFluidState(probe.set(bx, top, bz)).isEmpty()) {
			top--;
		}
		return top;
	}

	/**
	 * Whether something is under a roof: out of the sky (see {@link #sheltered}) with something solid over its head.
	 * Water over it is no roof: neither the wave's own, standing where it has just passed, nor the sea over a swimmer.
	 */
	private boolean roofed(Entity entity) {
		return sheltered(entity) && level.getHeight(Heightmap.Types.OCEAN_FLOOR, block(entity.getX()), block(entity.getZ())) > entity.getEyeY();
	}

	/** The players: the warnings, and the water on those in it (one riding a boat rides it out in the boat). */
	private void sweepPlayers() {
		Map<UUID, Vec3> seen = new HashMap<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || immune(player)) {
				continue;
			}
			Vec3 before = lastSeen.get(player.getUUID());
			seen.put(player.getUUID(), player.position());
			Vec3 moved = before == null ? Vec3.ZERO : player.position().subtract(before);
			if (moved.lengthSqr() > 9.0) {
				// Teleported, not swept.
				moved = Vec3.ZERO;
			}
			warnNear(player);
			if (carried.containsKey(player.getUUID())) {
				continue;
			}
			if (player.isPassenger()) {
				Entity vehicle = player.getVehicle();
				if (!(vehicle instanceof LivingEntity)) {
					// The boat (or the cart) takes the water for its rider: it is pushed with the things.
					continue;
				}
				Flow flow = flowAt(player.getX(), player.getY(), player.getZ());
				if (flow == null || flow.zone() != IN_FACE) {
					// The mount takes the current for its rider; only the face of the wave tears a rider off.
					continue;
				}
			}
			push(player, moved);
		}
		lastSeen.clear();
		lastSeen.putAll(seen);
	}

	/** A player the wave is almost on is told what to do, once. */
	private void warnNear(ServerPlayer player) {
		if ((phase != AT_SEA && phase != ASHORE) || !NaturesConfig.get().disasterWarnings || warnedNear.contains(player.getUUID())) {
			return;
		}
		double px = player.getX() - x;
		double pz = player.getZ() - z;
		double ahead = px * dirX + pz * dirZ - front;
		double across = -px * dirZ + pz * dirX;
		if (ahead > 0.0 && ahead < NEAR_WARNING && Math.abs(across) <= halfWidth && player.getY() < base + heightNow + 3.0) {
			warnedNear.add(player.getUUID());
			player.sendSystemMessage(Component.translatable("naturesmod.warning.tsunami_near").withStyle(ChatFormatting.GOLD));
		}
	}

	/** Looks for what is near the players in its way: animals first, then dropped things and boats, so many at most. */
	private void gather(List<Vec3> near) {
		nearby.clear();
		Set<Entity> found = new HashSet<>();
		List<Entity> rest = new ArrayList<>();
		int looked = 0;
		for (Vec3 at : near) {
			if (++looked > 6) {
				break;
			}
			AABB box = new AABB(at.x - 40.0, at.y - 16.0, at.z - 40.0, at.x + 40.0, at.y + 24.0, at.z + 40.0);
			for (Entity entity : level.getEntitiesOfClass(Entity.class, box, candidate -> candidate.isAlive() && !(candidate instanceof Player)
					&& (candidate instanceof LivingEntity || candidate instanceof ItemEntity || candidate instanceof FallingBlockEntity
					|| candidate.isVehicle()))) {
				if (!found.add(entity)) {
					continue;
				}
				if (entity instanceof LivingEntity) {
					if (nearby.size() < MAX_NEARBY) {
						nearby.add(entity);
					}
				} else {
					rest.add(entity);
				}
			}
		}
		for (Entity entity : rest) {
			if (nearby.size() >= MAX_NEARBY) {
				break;
			}
			nearby.add(entity);
		}
	}

	/** Everything else in the water near the players: swept along, carried, drawn back. */
	private void sweepThings() {
		for (Entity entity : nearby) {
			if (!entity.isAlive() || entity.level() != level || immune(entity) || carried.containsKey(entity.getUUID())) {
				continue;
			}
			push(entity, Vec3.ZERO);
			if (entity.isVehicle()) {
				// A boat a player rides is moved by the player's own game, which must hear of the push.
				entity.hurtMarked = true;
			}
		}
	}

	/** The water on something in it, by the part of the wave it is in. A roof over it takes most of it off. */
	private void push(Entity entity, Vec3 moved) {
		Flow flow = flowAt(entity.getX(), entity.getY(), entity.getZ());
		if (flow == null) {
			return;
		}
		boolean player = entity instanceof Player;
		boolean sheltered = roofed(entity);
		double shelter = sheltered ? 0.4 : 1.0;
		switch (flow.zone()) {
			case IN_DRAWBACK -> {
				if (entity.isInWater()) {
					drift(entity, moved, flow.velocity(), 0.05 * shelter, player);
				}
			}
			case IN_FACE -> face(entity, moved, flow, sheltered);
			case IN_FLOOD -> {
				drift(entity, moved, flow.velocity(), (player ? 0.25 : 0.12) * shelter, player);
				entity.resetFallDistance();
			}
			default -> drift(entity, moved, flow.velocity(), (player ? 0.28 : 0.14) * shelter, player);
		}
	}

	/**
	 * Draws something toward moving with the water, by `pull` of the difference a tick. A player's own game moves the
	 * player, so a player is given the velocity it would have had next anyway (from how far it went since the last
	 * tick, slowed as the game slows it in water, on the ground or in the air) plus the water's pull: it can still wade
	 * or swim, against the current or across it, only harder.
	 */
	private void drift(Entity entity, Vec3 moved, Vec3 water, double pull, boolean player) {
		if (player) {
			boolean wet = entity.isInWater();
			boolean ground = entity.onGround();
			double keep = wet ? 0.8 : ground ? 0.546 : 0.91;
			// In water a player sinks slowly (not as a falling one would); on the ground it is pressed down (that is how its
			// game knows it is standing).
			double vy = wet ? moved.y * 0.8 - 0.005 : ground ? -0.0784 : (moved.y - 0.08) * 0.98;
			Vec3 next = new Vec3(moved.x * keep + (water.x - moved.x) * pull, vy, moved.z * keep + (water.z - moved.z) * pull);
			setVelocity(entity, limit(next, 1.6));
			return;
		}
		Vec3 velocity = entity.getDeltaMovement();
		double response = pull;
		if (entity instanceof ItemEntity || entity instanceof FallingBlockEntity) {
			response = Math.min(1.0, pull * 2.5);
		} else if (entity instanceof LivingEntity) {
			response = pull / Math.max(1.0, entity.getBbWidth() * entity.getBbHeight() * 0.6);
		}
		shove(entity, (water.x - velocity.x) * response, 0.0, (water.z - velocity.z) * response);
	}

	/** The face of the wave strikes what it reaches, and catches what it can carry; the rest it bears along all the same. */
	private void face(Entity entity, Vec3 moved, Flow flow, boolean sheltered) {
		UUID id = entity.getUUID();
		boolean living = entity instanceof LivingEntity;
		if (living && struck.add(id)) {
			hurt(entity, DisasterDamage.TSUNAMI, (float) ((1.0 + rank) * (sheltered ? 0.5 : 1.0)));
		}
		Integer free = letGo.get(id);
		boolean player = entity instanceof Player;
		if (!sheltered && living && (free == null || free < age) && liftable(entity) && (player || mobsCarried < MAX_CARRIED_MOBS)) {
			seize(entity);
			return;
		}
		drift(entity, moved, flow.velocity(), sheltered ? 0.12 : 0.35, player);
		entity.resetFallDistance();
	}

	/** Whether the wave can carry something this big off (not a ghast, nor a dragon). */
	private static boolean liftable(Entity entity) {
		return entity.getBbWidth() * entity.getBbHeight() <= 6.0F;
	}

	private void seize(Entity entity) {
		if (entity.isPassenger()) {
			// Torn off its mount.
			entity.stopRiding();
		}
		int hold = 30 + 12 * rank + random.nextInt(20);
		carried.put(entity.getUUID(), new Carry(entity, age + hold, random.nextDouble() * Math.PI * 2.0));
		if (!(entity instanceof Player)) {
			mobsCarried++;
		}
		sweptUp++;
	}

	/**
	 * Carries along what the wave has caught: just behind its face, tumbled over and over near the bottom of the water
	 * (rising and dropping, thrown from side to side), battered by the wreckage going with it, until it lets go, or the
	 * thing is left behind (caught on something), or the water gets too shallow to carry it. A thing is made to go
	 * where it should be: a player in it can do nothing, and may as well hold their breath.
	 */
	private void carry() {
		int mobs = 0;
		double crest = crestLength();
		double aim = front - 1.6;
		double top = base + heightNow - 0.8;
		for (Iterator<Carry> it = carried.values().iterator(); it.hasNext(); ) {
			Carry c = it.next();
			Entity entity = c.entity;
			if (!entity.isAlive() || entity.level() != level || immune(entity) || phase == BACKWASH) {
				release(entity);
				it.remove();
				continue;
			}
			c.ticks++;
			double ex = entity.getX() - x;
			double ez = entity.getZ() - z;
			double along = ex * dirX + ez * dirZ;
			double across = -ex * dirZ + ez * dirX;
			int bx = block(entity.getX());
			int bz = block(entity.getZ());
			double floor = loaded(bx, bz) ? landAt(bx, bz) + 1.0 : entity.getY();
			if (age >= c.until || !(Math.abs(along - aim) < crest + 6.0) || Math.abs(across) > halfWidth + 2.0 || floor > top - 0.5) {
				release(entity);
				it.remove();
				continue;
			}
			double toss = c.ticks * 0.33 + c.phase;
			double want = Math.max(floor + 0.2, Math.min(top - 1.0, floor + 1.4 + 1.3 * Math.sin(toss)));
			double onward = Math.max(-0.2, Math.min(speedNow + 0.9, speedNow + (aim - along) * 0.25));
			double side = 0.14 * Math.sin(c.ticks * 0.21 + c.phase * 2.0);
			double rise = (want - entity.getY()) * 0.3 + 0.06 * Math.cos(toss);
			setVelocity(entity, limit(new Vec3(dirX * onward - dirZ * side, rise, dirZ * onward + dirX * side), 2.0));
			entity.resetFallDistance();
			if (entity instanceof LivingEntity && c.ticks % 20 == 0) {
				// Struck by what goes along with it.
				hurt(entity, DisasterDamage.TSUNAMI, 0.5F + 0.35F * rank);
			}
			if (!(entity instanceof Player)) {
				mobs++;
			}
		}
		mobsCarried = mobs;
	}

	/** Lets go of something it carried: borne on a little, near the ground, its fall forgiven; and not caught again at once. */
	private void release(Entity entity) {
		if (entity.isAlive() && !immune(entity)) {
			setVelocity(entity, new Vec3(dirX * speedNow * 0.6, 0.12, dirZ * speedNow * 0.6));
			entity.resetFallDistance();
		}
		letGo.put(entity.getUUID(), age + 80);
	}

	private static Vec3 limit(Vec3 v, double max) {
		double length = v.length();
		if (!(length < 1.0E6)) {
			return Vec3.ZERO;
		}
		return length > max ? v.scale(max / length) : v;
	}

	// --- what it breaks -------------------------------------------------------------------------------

	/**
	 * Smashes what is weak in its way, just ahead of its face, about the players near it (where it is seen): so many
	 * columns a tick, so many blocks broken, only a few with the crack and the shower of bits of a block broken.
	 */
	private void batter(List<Vec3> near) {
		int budget = 30 + 12 * rank;
		effectsLeft = 3;
		int columns = 0;
		double top = base + heightNow;
		for (Vec3 at : near) {
			if (columns >= MAX_COLUMNS || budget <= 0) {
				break;
			}
			double px = at.x - x;
			double pz = at.z - z;
			double along = px * dirX + pz * dirZ;
			double across = -px * dirZ + pz * dirX;
			if (Math.abs(along - front) > 72.0) {
				continue;
			}
			int samples = 8 + 2 * rank;
			for (int i = 0; i < samples && columns < MAX_COLUMNS && budget > 0; i++) {
				columns++;
				double a = front - 0.5 + random.nextDouble() * 3.0;
				double c = Math.max(-halfWidth, Math.min(halfWidth, across + random.nextGaussian() * 20.0));
				int bx = block(x + dirX * a - dirZ * c);
				int bz = block(z + dirZ * a + dirX * c);
				if (loaded(bx, bz)) {
					budget -= smash(bx, bz, top, budget);
				}
			}
		}
	}

	/** Down a column from the top of the water as far as the land: what is weak and open to the water goes. Returns the blocks changed. */
	private int smash(int bx, int bz, double top, int budget) {
		int high = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1, (int) Math.floor(top) - 1);
		high = Math.min(high, level.getMaxY() - 1);
		int low = Math.max(level.getMinY() + 1, high - 24);
		int changed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int by = high; by >= low && changed < 4 && changed < budget; by--) {
			pos.set(bx, by, bz);
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (!state.getFluidState().isEmpty()) {
				return changed;
			}
			Kind kind = kind(state, pos);
			if (kind == Kind.LAND) {
				return changed;
			}
			if (kind == Kind.LOG) {
				if (rank >= 4 && random.nextInt(4) == 0 && fellTree(pos.immutable(), direction(), random.nextInt(2), rank >= 5 ? 2 : 1)) {
					treesFelled++;
					changed += 4;
				}
				return changed;
			}
			if (kind == Kind.OTHER || state.hasBlockEntity() || kind == Kind.WOOD && rank < 4 || !exposed(bx, by, bz)) {
				continue;
			}
			smashBlock(pos.immutable(), kind);
			changed++;
		}
		return changed;
	}

	private void smashBlock(BlockPos pos, Kind kind) {
		if (effectsLeft > 0 && kind != Kind.LEAVES && kind != Kind.PLANT && random.nextInt(3) == 0) {
			// The crack of it going.
			effectsLeft--;
			level.destroyBlock(pos, false);
		} else if (kind == Kind.LEAVES || kind == Kind.PLANT || kind == Kind.GLASS) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		} else {
			// What held up a torch or a door must tell it.
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		blocksBroken++;
	}

	/** Whether the water gets at a block: open air (or water) above it or beside it. */
	private boolean exposed(int bx, int by, int bz) {
		return open(bx, by + 1, bz) || open(bx + 1, by, bz) || open(bx - 1, by, bz) || open(bx, by, bz + 1) || open(bx, by, bz - 1);
	}

	private boolean open(int bx, int by, int bz) {
		if (!loaded(bx, bz)) {
			return false;
		}
		BlockState state = level.getBlockState(probe.set(bx, by, bz));
		return state.isAir() || !state.getFluidState().isEmpty();
	}

	private Kind kind(BlockState state, BlockPos pos) {
		Block block = state.getBlock();
		Kind kind = kinds.get(block);
		if (kind == null) {
			kind = kindOf(state, pos);
			kinds.put(block, kind);
		}
		return kind;
	}

	/** What a block is to the wave (worked out once for each kind of block). */
	private Kind kindOf(BlockState state, BlockPos pos) {
		if (state.is(BlockTags.LEAVES)) {
			return Kind.LEAVES;
		}
		if (TreeFeller.isLog(state)) {
			return Kind.LOG;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F || land(state)) {
			return Kind.LAND;
		}
		if (state.hasBlockEntity()) {
			return Kind.OTHER;
		}
		Block block = state.getBlock();
		if (state.is(BlockTags.CROPS) || state.is(BlockTags.FLOWERS) || state.is(WorldGenUtil.SAPLINGS) || block instanceof VegetationBlock
				|| state.canBeReplaced()) {
			return Kind.PLANT;
		}
		String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (name.contains("glass")) {
			return Kind.GLASS;
		}
		boolean light = hardness <= 3.0F && block.getExplosionResistance() <= 3.0F;
		if (light && named(name, FITTINGS)) {
			return Kind.FITTING;
		}
		if (state.is(BlockTags.PLANKS) || name.contains("wool") || light && state.ignitedByLava() && (name.endsWith("_slab") || name.endsWith("_stairs"))) {
			return Kind.WOOD;
		}
		return Kind.OTHER;
	}

	private static boolean named(String name, String[] parts) {
		for (String part : parts) {
			if (name.contains(part)) {
				return true;
			}
		}
		return false;
	}

	/** The land itself: soil, stone, sand and the like. */
	private static boolean land(BlockState state) {
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND)
				|| state.is(BlockTags.TERRACOTTA) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SNOW_BLOCK)
				|| state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE);
	}

	// --- its water ------------------------------------------------------------------------------------

	/**
	 * Pours the water of its face into the columns its front has just reached, about the players near it (and any place
	 * it was told to watch, see {@link #watch}): from the ground up (over any water already there, into air or the small
	 * plants it washes away, never into anything solid) toward the top of the wave, the nearest columns to each player
	 * first, as much as this tick's share allows and no more than so much standing at once. Each block is taken away
	 * again four ticks on (see {@link #drain()}), and neither pouring nor taking it away stirs the blocks beside it.
	 */
	private void pourWater(List<Vec3> near) {
		double to = front;
		double from = Math.max(previous, to - 4.0);
		if (!(to > from)) {
			return;
		}
		int room = Math.min(WATER_PER_TICK, MAX_WATER - water.size() - stranded.size());
		if (room <= 0) {
			return;
		}
		double band = 12.0 + 2.0 * intensity;
		double[] lows = new double[4];
		double[] highs = new double[4];
		int bands = 0;
		List<Cell> cells = new ArrayList<>();
		for (Vec3 at : near) {
			if (bands >= lows.length) {
				break;
			}
			double px = at.x - x;
			double pz = at.z - z;
			double pa = px * dirX + pz * dirZ;
			double pc = -px * dirZ + pz * dirX;
			if (Math.abs(pa - to) > 64.0) {
				continue;
			}
			double c0 = Math.max(-halfWidth, pc - band);
			double c1 = Math.min(halfWidth, pc + band);
			if (!(c0 < c1)) {
				continue;
			}
			// The grid columns about this stretch of the front: those whose middles it passed this tick.
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (int k = 0; k < 4; k++) {
				double a = k < 2 ? from : to;
				double c = (k & 1) == 0 ? c0 : c1;
				int cx = block(x + dirX * a - dirZ * c);
				int cz = block(z + dirZ * a + dirX * c);
				minX = Math.min(minX, cx);
				maxX = Math.max(maxX, cx);
				minZ = Math.min(minZ, cz);
				maxZ = Math.max(maxZ, cz);
			}
			for (int bx = minX; bx <= maxX; bx++) {
				for (int bz = minZ; bz <= maxZ; bz++) {
					double cx = bx + 0.5 - x;
					double cz = bz + 0.5 - z;
					double a = cx * dirX + cz * dirZ;
					if (!(a > from && a <= to)) {
						continue;
					}
					double c = -cx * dirZ + cz * dirX;
					if (c < c0 || c > c1 || within(lows, highs, bands, c)) {
						continue;
					}
					cells.add(new Cell(bx, bz, Math.abs(c - pc)));
				}
			}
			lows[bands] = c0;
			highs[bands] = c1;
			bands++;
		}
		if (cells.isEmpty()) {
			return;
		}
		cells.sort(Comparator.comparingDouble(Cell::off));
		int depth = Math.max(2, Math.min(3 + 2 * rank, room / cells.size()));
		double surface = base + heightNow;
		for (Cell cell : cells) {
			if (room <= 0) {
				break;
			}
			room -= pour(cell.x(), cell.z(), surface, Math.min(depth, room));
		}
	}

	private static boolean within(double[] lows, double[] highs, int count, double c) {
		for (int i = 0; i < count; i++) {
			if (c >= lows[i] && c <= highs[i]) {
				return true;
			}
		}
		return false;
	}

	/** Pours water into a column from the first free block over its ground up toward the surface, `most` blocks at most. Returns how many. */
	private int pour(int bx, int bz, double surface, int most) {
		if (!loaded(bx, bz)) {
			return 0;
		}
		int start = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), level.getMinY() + 1);
		int top = Math.min((int) Math.floor(surface) - 1, level.getMaxY() - 1);
		int poured = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int by = start; by <= top && poured < most; by++) {
			pos.set(bx, by, bz);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && !tall(pos, state)) {
				// Its neighbours are left be: the water of a pond or the sea beside it isn't stirred into flowing.
				level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				water.add(new Placed(bx, by, bz, age + WATER_LIFE));
				poured++;
			}
		}
		waterPlaced += poured;
		return poured;
	}

	/** Takes away the water that has stood its time (and, now and then, any left where the land wasn't loaded). */
	private void drain() {
		while (!water.isEmpty() && water.peekFirst().until() <= age) {
			Placed placed = water.pollFirst();
			if (!dry(placed) && stranded.size() < MAX_STRANDED) {
				stranded.add(placed);
			}
		}
		if (!stranded.isEmpty() && age % 20 == 0) {
			stranded.removeIf(this::dry);
		}
	}

	/** Takes a block of its water away again, if it is still there; false if its land isn't loaded to do it. */
	private boolean dry(Placed placed) {
		if (!loaded(placed.x(), placed.z())) {
			return false;
		}
		BlockPos pos = new BlockPos(placed.x(), placed.y(), placed.z());
		if (level.getBlockState(pos).is(Blocks.WATER)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			waterRemoved++;
		}
		return true;
	}

	/** Part of a plant two blocks tall (or a hanging run of vines): left be, so that no half of it is left floating. */
	private boolean tall(BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		return level.getBlockState(pos.above()).is(block) || level.getBlockState(pos.below()).is(block);
	}

	private static int block(double v) {
		return (int) Math.floor(v);
	}
}
