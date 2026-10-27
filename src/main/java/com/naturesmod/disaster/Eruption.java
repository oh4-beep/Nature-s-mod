package com.naturesmod.disaster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import com.naturesmod.block.ModBlocks;
import com.naturesmod.tree.TreeEvents;
import com.naturesmod.tree.TreeFeller;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A volcano erupting, rated on the volcanic explosivity index (VEI 2 to 7). First the unrest: swarms
 * of small tremors under the mountain, more often and stronger as the magma rises, and steam from its
 * summit, and a last warning shortly before it goes. Then it blows: a blast heard far off (the sound
 * arriving well after the flash), everything near thrown back from the mountain, the forests round it
 * flattened near the summit and scorched and stripped further out, and the summit torn open into a
 * crater with a lake of lava in its floor. An eruption column rises hundreds of blocks and spreads into
 * an umbrella; ash falls downwind, settling in layers and, near the vent, blocks deep; lightning
 * flickers in the plume; glowing bombs fly out of the vent and burst where they come down, blowing out
 * pits and setting alight what will burn; lava wells over the crater's lowest lip and runs down the
 * steepest way, its crust cooling to basalt and blackstone behind it and quenched to new land where it
 * meets shallow water. From index 3 up the column collapses again and again in pyroclastic flows,
 * avalanches of searing gas and ash as wide as a road that race down the flanks faster than anything
 * can run, burn all they reach, char the forests, burn wooden houses away and bury the land in ash.
 * Then it wanes to steam.
 *
 * <p>A vent under the sea erupts from the water's surface: no crater and no lava flows there, but the
 * column, the bombs, the ash and the flows (which run out over the water) all the same.
 *
 * <p>The work is bounded every tick, however great the eruption: one budget of block changes is shared
 * by all of it (the crater is dug out over some ticks, from the vent outward), so many bombs are in the
 * air and so many of them burst a second, so many flows run at once, and so many trees are felled and
 * fires lit in all. The bombs are told to the clients in the disaster's own numbers rather than one
 * event each, so that a client draws no more of them than are in the air.
 */
public final class Eruption extends Disaster {
	/** Volcanic bombs fly under these, on the server and the clients alike. */
	public static final double GRAVITY = 0.05;
	public static final double DRAG = 0.995;
	/**
	 * Where in {@link #extra()} the pyroclastic flows start, after the numbers every eruption sends (see there), and
	 * how many numbers each flow takes: its x and z from the vent, its y, the way it is heading (x, z), its width and
	 * how far along its run it is (0 to 1).
	 */
	public static final int FLOWS_AT = 12;
	public static final int FLOW_FLOATS = 7;
	/** How many numbers each freshly thrown bomb takes, after the flows: its number, the ticks since it was thrown, its velocity (3) and its size. */
	public static final int BOMB_FLOATS = 6;
	/** For how many ticks after it is thrown a bomb is told to the clients (they follow its flight themselves from then on). */
	public static final int BOMB_NEWS = 8;
	/** The most pyroclastic flows racing down at once. */
	public static final int MAX_FLOWS = 8;
	/** The most bombs in the air at once. */
	public static final int MAX_BOMBS = 40;
	/** Blocks changed in one tick, all the eruption's work together: crater, blast, flows, lava and ash. */
	private static final int BLOCKS_PER_TICK = 320;
	/** Bombs bursting in one second, at most: each burst is a bang and a puff of particles on every client near it. */
	private static final int BURSTS_PER_SECOND = 6;
	/** Bombs only burst and set fire where a player is within this many blocks to see it; elsewhere they just come down. */
	private static final double WATCHED = 64.0;
	/** For how long after the blast the land round the mountain is laid waste. */
	private static final int DEVASTATION_TICKS = 300;
	/** Lava crusting over in one tick, at most. */
	private static final int COOLING_PER_TICK = 48;
	/** The ways a pyroclastic flow looks for the steepest way down, turning from its heading. */
	private static final double[] TURNS = {0.0, -0.5, 0.5, -1.0, 1.0};

	private final int vei;
	private final BlockPos vent;
	/** The height the column rises from: the summit, or the sea's surface over a vent under water. */
	private final int base;
	private final boolean submarine;
	private final int unrest;
	private final int plinian;
	private final double columnHeight;
	private final Vec3 wind;
	private final double ashReach;
	/** How far the blast throws things back and lays the forests waste. */
	private final double blastReach;
	private final int crater;
	/** How much lava the flows may pour in all, how many fires the bombs may light and how many trees the blast may fell. */
	private final int lavaCap;
	private final int fireCap;
	private final int treeCap;
	/** When, into the eruption, each collapse of the column sends pyroclastic flows down. */
	private final int[] pulseTimes;
	private final List<Bomb> bombs = new ArrayList<>();
	private final List<Flow> flows = new ArrayList<>();
	private final List<LavaFlow> lavaFlows = new ArrayList<>();
	private final Deque<Cooling> cooling = new ArrayDeque<>();
	/** The crater's columns still to be dug, nearest the vent first: dug out in a first pass, the floor laid in a second. */
	private final List<CraterColumn> craterWork = new ArrayList<>();
	private int craterNext;
	private int craterPass;
	/** How far down the column being dug has been dug. */
	private int digY = Integer.MIN_VALUE;
	private int blocksLeft;
	private int lavaPlaced;
	private int flowLava;
	/** How far from the vent the crater as dug ends: where the lava wells over its lip. */
	private double lip;
	private int lavaFlowsStarted;
	private int flowsLaunched;
	private int pulses;
	private int bombsThrown;
	private int firesLit;
	private int treesFelled;
	private int blastAge = -1;
	private boolean blown;
	private boolean warnedBlast;
	private int nextTremor = 20;
	private int burstSecond = -1;
	private int bursts;
	private int fellSecond = -1;
	private int felledThisSecond;

	private static final class Bomb {
		final int number;
		final int thrown;
		/** How it left the vent: what the clients are told, to follow its flight themselves. */
		final Vec3 launch;
		final double size;
		Vec3 pos;
		Vec3 velocity;

		Bomb(int number, int thrown, Vec3 pos, Vec3 velocity, double size) {
			this.number = number;
			this.thrown = thrown;
			this.launch = velocity;
			this.size = size;
			this.pos = pos;
			this.velocity = velocity;
		}
	}

	/** A pyroclastic flow: its front, racing downhill. */
	private static final class Flow {
		double x;
		double y;
		double z;
		double dirX;
		double dirZ;
		double travelled;
		final double length;
		final double width;
		final double speed;
		/** Which ticks it looks for what it engulfs on: the flows take turns. */
		final int stagger;

		Flow(double x, double y, double z, double angle, double length, double width, double speed, int stagger) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.dirX = Math.cos(angle);
			this.dirZ = Math.sin(angle);
			this.length = length;
			this.width = width;
			this.speed = speed;
			this.stagger = stagger;
		}
	}

	/** The head of a lava flow, feeling its way down. */
	private static final class LavaFlow {
		BlockPos head;
		int steps;
		final int length;
		int wait;
		int dx;
		int dz;
		/** Steps in a row it has poured nothing: run out over water, or onto lava already poured. */
		int idle;

		LavaFlow(BlockPos head, int length, int dx, int dz) {
			this.head = head;
			this.length = length;
			this.dx = dx;
			this.dz = dz;
		}
	}

	private record Cooling(BlockPos pos, int at) {
	}

	/** A column of the crater: dug from its top down to just above its floor, with lava on the floor if it lies under the lake. */
	private record CraterColumn(int x, int z, int top, int floor, boolean lava, double distance) {
	}

	Eruption(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.ERUPTION, level, at, intensity, natural, 0);
		vei = Math.max(2, Math.min(7, Math.round(this.intensity)));
		vent = summit(BlockPos.containing(at));
		int sea = seaSurface(vent);
		submarine = sea > vent.getY();
		base = submarine ? sea : vent.getY();
		x = vent.getX() + 0.5;
		y = base + 1;
		z = vent.getZ() + 0.5;
		unrest = 500 + 100 * vei;
		plinian = 1200 + 500 * (vei - 2);
		duration = unrest + plinian + 900;
		columnHeight = Math.min(640.0, 45.0 * Math.pow(vei, 1.6));
		double windAngle = random.nextDouble() * Math.PI * 2.0;
		wind = new Vec3(Math.cos(windAngle), 0.0, Math.sin(windAngle));
		ashReach = 60.0 + 50.0 * vei;
		blastReach = 40.0 + 16.0 * vei;
		crater = 4 + 2 * vei;
		lip = crater + 1.0;
		lavaCap = 150 + 70 * vei;
		fireCap = 20 + 6 * vei;
		treeCap = 8 + 2 * vei;
		int pulseCount = vei >= 3 ? vei - 2 : 0;
		pulseTimes = new int[pulseCount];
		for (int i = 0; i < pulseCount; i++) {
			pulseTimes[i] = (int) (plinian * (0.45 + 0.45 * (i + 0.2 + random.nextDouble() * 0.6) / pulseCount));
		}
	}

	/** The highest ground within forty blocks: the summit of the mountain. */
	private BlockPos summit(BlockPos around) {
		BlockPos best = around;
		int bestY = Integer.MIN_VALUE;
		for (int dx = -40; dx <= 40; dx += 3) {
			for (int dz = -40; dz <= 40; dz += 3) {
				int bx = around.getX() + dx;
				int bz = around.getZ() + dz;
				if (!loaded(bx, bz)) {
					continue;
				}
				int top = terrain(bx, bz);
				// Close to where it was asked for, all else being equal.
				int score = top * 4 - (Math.abs(dx) + Math.abs(dz)) / 6;
				if (score > bestY) {
					bestY = score;
					best = new BlockPos(bx, top, bz);
				}
			}
		}
		return best;
	}

	/** The surface of the water standing over a column, or the column's own height where it is dry. */
	private int seaSurface(BlockPos column) {
		if (!loaded(column.getX(), column.getZ())) {
			return column.getY();
		}
		int top = ground(column.getX(), column.getZ());
		boolean wet = top > column.getY() && level.getFluidState(new BlockPos(column.getX(), top, column.getZ())).is(FluidTags.WATER);
		return wet ? top : column.getY();
	}

	private int phase() {
		return age < unrest ? 0 : age < unrest + plinian ? 1 : 2;
	}

	@Override
	public float radius() {
		return (float) ashReach;
	}

	@Override
	public Vec3 direction() {
		return wind;
	}

	@Override
	public float strength() {
		return switch (phase()) {
			case 0 -> 0.2F;
			case 1 -> 1.0F;
			default -> (float) Math.max(0.0, 1.0 - (age - unrest - plinian) / 900.0);
		};
	}

	/**
	 * For the clients: the index, the phase (0 unrest, 1 eruption, 2 waning), the height the column rises from,
	 * the column's height now, the wind (x, z), how far the ash reaches, the crater's radius, how many flows and
	 * how many fresh bombs follow, the ticks since the blast (-1 before it), and how far the unrest has got (0 to
	 * 1); then the flows and the bombs (see {@link #FLOWS_AT}, {@link #BOMB_FLOATS}).
	 */
	@Override
	public float[] extra() {
		int phase = phase();
		double rising = phase == 1 ? Math.min(1.0, (age - unrest) / 400.0) : phase == 2 ? strength() : 0.0;
		int flowCount = Math.min(flows.size(), MAX_FLOWS);
		int news = 0;
		for (Bomb bomb : bombs) {
			if (age - bomb.thrown <= BOMB_NEWS) {
				news++;
			}
		}
		float[] out = new float[FLOWS_AT + flowCount * FLOW_FLOATS + news * BOMB_FLOATS];
		out[0] = vei;
		out[1] = phase;
		out[2] = base;
		out[3] = (float) (columnHeight * rising);
		out[4] = (float) wind.x;
		out[5] = (float) wind.z;
		out[6] = (float) ashReach;
		out[7] = crater;
		out[8] = flowCount;
		out[9] = news;
		out[10] = blastAge < 0 ? -1.0F : age - blastAge;
		out[11] = phase == 0 ? Math.min(1.0F, (float) age / unrest) : 1.0F;
		int i = FLOWS_AT;
		for (int k = 0; k < flowCount; k++) {
			Flow flow = flows.get(k);
			out[i++] = (float) (flow.x - x);
			out[i++] = (float) flow.y;
			out[i++] = (float) (flow.z - z);
			out[i++] = (float) flow.dirX;
			out[i++] = (float) flow.dirZ;
			out[i++] = (float) flow.width;
			out[i++] = (float) Math.min(1.0, flow.travelled / flow.length);
		}
		for (Bomb bomb : bombs) {
			if (age - bomb.thrown > BOMB_NEWS || i + BOMB_FLOATS > out.length) {
				continue;
			}
			out[i++] = bomb.number;
			out[i++] = age - bomb.thrown;
			out[i++] = (float) bomb.launch.x;
			out[i++] = (float) bomb.launch.y;
			out[i++] = (float) bomb.launch.z;
			out[i++] = (float) bomb.size;
		}
		return out;
	}

	@Override
	protected void begin() {
		warn(1200, player -> Component.translatable("naturesmod.warning.eruption", (int) distance2d(player, x, z),
				compass(player.getX(), player.getZ(), x, z)));
	}

	@Override
	protected void tick() {
		blocksLeft = BLOCKS_PER_TICK;
		int phase = phase();
		if (phase == 0) {
			unrest();
		} else if (!blown) {
			blown = true;
			blast();
		}
		// The crater comes first: it is torn open in the first ticks after the blast. Then the lava, which pours only a
		// block or two a tick but would otherwise find the budget spent every tick by the ash, the blast's devastation
		// and the pyroclastic flows, and never run at all.
		digCrater();
		tickLava();
		if (phase == 1) {
			erupt();
		} else if (phase == 2) {
			wane();
		}
		devastate();
		tickBombs();
		tickFlows();
	}

	@Override
	protected void finish() {
		// The lava still molten from the flows crusts over now, all of it, so that none is left burning for good: there is
		// never more queued than the flows may pour in all (a few hundred blocks, once).
		while (!cooling.isEmpty()) {
			BlockPos pos = cooling.pollFirst().pos();
			if (loaded(pos.getX(), pos.getZ())) {
				cool(pos);
			} else {
				// Not left molten for good: crusted over as soon as its land is back.
				leaveForLater(pos, Blocks.LAVA, Blocks.BASALT.defaultBlockState());
			}
		}
	}

	// --- the unrest --------------------------------------------------------------------------

	/** Magma forcing its way up: swarms of small quakes under the mountain, more often and stronger as it nears the surface. */
	private void unrest() {
		double nearing = Math.min(1.0, (double) age / unrest);
		if (age >= nextTremor) {
			Disasters.add(new Earthquake(level, new Vec3(x + random.nextGaussian() * 8.0, vent.getY(), z + random.nextGaussian() * 8.0),
					(float) (2.6 + nearing * 0.9 + random.nextDouble() * 0.7), natural, true).quietly());
			nextTremor = age + (int) ((80 + random.nextInt(140)) * (1.0 - 0.45 * nearing));
		}
		if (!warnedBlast && age >= unrest - 200) {
			warnedBlast = true;
			warn(ashReach * 1.5, player -> Component.translatable("naturesmod.warning.eruption_imminent"));
		}
	}

	// --- the blast ---------------------------------------------------------------------------

	private void blast() {
		blastAge = age;
		DisasterNet.send(level, new DisasterNet.Event(DisasterNet.Event.BLAST, x, base + 1, z, new float[] {vei}), 0);
		// Its rays reach out a long way: only where the land about it is running (else the server loads it on the spot).
		if (workable(vent.getX(), vent.getZ())) {
			level.explode(null, x, base + 3, z, 5.0F + vei, Level.ExplosionInteraction.NONE);
		}
		warn(ashReach * 1.5, player -> Component.translatable("naturesmod.warning.eruption_ash", compass(wind.x, wind.z)));
		// The shock wave: the players wherever they are, and whatever else is near enough to matter.
		for (ServerPlayer player : level.players()) {
			shock(player);
		}
		// (Things hung on walls come down when pushed: only where the land may be harmed.)
		double near = Math.min(blastReach, 64.0);
		boolean all = griefing();
		for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(x - near, base - near, z - near, x + near, base + near, z + near),
				e -> !(e instanceof Player) && (all || e instanceof LivingEntity))) {
			shock(entity);
		}
		if (griefing() && !submarine) {
			planCrater();
		}
	}

	/** Throws an entity back from the mountain, the harder the nearer; what is closest is burned as well. */
	private void shock(Entity entity) {
		if (immune(entity)) {
			return;
		}
		Vec3 away = entity.position().subtract(x, base - 4, z);
		double d = Math.max(1.0, away.length());
		if (d > blastReach) {
			return;
		}
		double power = (1.0 - d / blastReach) * (1.4 + vei * 0.3);
		shove(entity, away.x / d * power, Math.max(power * 0.4, away.y / d * power), away.z / d * power);
		double scorch = crater * 2.5;
		if (entity instanceof LivingEntity && d < scorch) {
			hurt(entity, DisasterDamage.PYROCLASTIC, (float) ((4.0 + vei * 2.0) * (1.0 - d / scorch)));
			entity.igniteForSeconds(6.0F);
		}
	}

	/**
	 * Works out the crater the blast tears open: a bowl round the vent, and in its floor a lake of lava whose surface
	 * lies below the lowest point of the rim, so that it stays where it is. Columns under water are left be.
	 */
	private void planCrater() {
		int lowest = Integer.MAX_VALUE;
		for (int k = 0; k < 32; k++) {
			double angle = k * Math.PI / 16.0;
			int bx = (int) Math.floor(x + Math.cos(angle) * (crater + 1.5));
			int bz = (int) Math.floor(z + Math.sin(angle) * (crater + 1.5));
			if (!loaded(bx, bz)) {
				// Where the rim can't be seen, no lake: it might pour out there.
				lowest = Integer.MIN_VALUE;
				break;
			}
			lowest = Math.min(lowest, terrain(bx, bz));
		}
		int depth = (int) Math.round(crater * 0.7);
		int lakeY = lowest == Integer.MIN_VALUE ? Integer.MIN_VALUE : Math.min(vent.getY() - (int) Math.round(crater * 0.3), lowest - 1);
		if (lakeY != Integer.MIN_VALUE && lakeY - 2 < vent.getY() - depth) {
			// A steep peak, its sides falling away fast: the bowl is sunk deeper (within reason), so that the lake still lies
			// in it below the lowest of the rim, where it can't pour out.
			depth = Math.min((int) Math.round(crater * 1.6), vent.getY() - (lakeY - 2));
		}
		int bottom = vent.getY() - depth;
		boolean lake = !submarine && lakeY > bottom + 1 && lakeY > level.getMinY() + 2 && lakeY < level.getMaxY() - 2;
		List<CraterColumn> plan = new ArrayList<>();
		for (int dx = -crater; dx <= crater; dx++) {
			for (int dz = -crater; dz <= crater; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > crater) {
					continue;
				}
				int bx = vent.getX() + dx;
				int bz = vent.getZ() + dz;
				if (!loaded(bx, bz)) {
					continue;
				}
				int surface = ground(bx, bz);
				if (!level.getFluidState(new BlockPos(bx, surface, bz)).isEmpty()) {
					continue;
				}
				int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1, level.getMaxY() - 1);
				int floor = vent.getY() - (int) Math.round(depth * (1.0 - (d / crater) * (d / crater)));
				// Under the lake the bowl is floored at its bed; the lava lies there where the land reaches that high.
				boolean underLake = lake && floor < lakeY;
				if (underLake) {
					floor = lakeY - 1;
				}
				boolean lava = underLake && top >= lakeY;
				floor = Math.max(floor, level.getMinY() + 2);
				if (top <= floor && !lava) {
					continue;
				}
				plan.add(new CraterColumn(bx, bz, top, floor, lava, d));
			}
		}
		plan.sort(Comparator.comparingDouble(CraterColumn::distance));
		// On a steep peak the bowl is cut only near the vent, and the lava wells over just past where it ends.
		lip = (plan.isEmpty() ? crater : plan.get(plan.size() - 1).distance()) + 1.5;
		craterWork.clear();
		craterWork.addAll(plan);
		craterNext = 0;
		craterPass = 0;
		digY = Integer.MIN_VALUE;
	}

	/** Digs the crater out a column at a time from the vent outward, as far as this tick's budget goes; then lays its floor and the lava lake. */
	private void digCrater() {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (blocksLeft > 0 && craterNext < craterWork.size()) {
			CraterColumn column = craterWork.get(craterNext);
			if (!loaded(column.x(), column.z())) {
				nextColumn();
				continue;
			}
			if (craterPass == 0) {
				if (digY == Integer.MIN_VALUE) {
					digY = column.top();
				}
				while (digY > column.floor() && blocksLeft > 0) {
					BlockState state = level.getBlockState(pos.set(column.x(), digY, column.z()));
					// Water lying in it is left (it would only run off); so is what can't be broken.
					if (!state.isAir() && state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0.0F) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						blocksLeft--;
					}
					digY--;
				}
				if (digY > column.floor()) {
					return;
				}
			} else {
				// The floor: shattered and melted rock, and under the lake a bed that holds it.
				BlockState floor = level.getBlockState(pos.set(column.x(), column.floor(), column.z()));
				boolean solid = !floor.isAir() && !floor.canBeReplaced();
				if (solid && floor.getFluidState().isEmpty() && !floor.hasBlockEntity() && floor.getDestroySpeed(level, pos) >= 0.0F
						|| !solid && column.lava()) {
					level.setBlock(pos, column.lava() ? Blocks.BASALT.defaultBlockState() : volcanicRock(), Block.UPDATE_CLIENTS);
					blocksLeft--;
				}
				if (column.lava()) {
					BlockState there = level.getBlockState(pos.set(column.x(), column.floor() + 1, column.z()));
					if (there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty()) {
						level.setBlock(pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
						blocksLeft--;
						lavaPlaced++;
					}
				}
			}
			nextColumn();
		}
	}

	private void nextColumn() {
		digY = Integer.MIN_VALUE;
		craterNext++;
		if (craterNext >= craterWork.size()) {
			craterNext = 0;
			if (craterPass == 0) {
				craterPass = 1;
			} else {
				craterWork.clear();
			}
		}
	}

	private BlockState volcanicRock() {
		int roll = random.nextInt(10);
		return roll < 4 ? Blocks.BASALT.defaultBlockState() : roll < 7 ? Blocks.BLACKSTONE.defaultBlockState()
				: roll < 9 ? Blocks.TUFF.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
	}

	/**
	 * For a while after the blast the land about the mountain is laid waste: near it the trees are flattened, pointing
	 * away from it (a few a second, where a player is there to see them fall); further out the forests are scorched and
	 * stripped, thinning out toward the edge of the blast.
	 */
	private void devastate() {
		if (blastAge < 0 || age - blastAge > DEVASTATION_TICKS || !griefing()) {
			return;
		}
		double inner = crater + 2.0;
		for (int i = 0; i < 20 && blocksLeft > 0; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double d = inner + Math.pow(random.nextDouble(), 1.4) * Math.max(1.0, blastReach - inner);
			int bx = (int) Math.floor(x + Math.cos(angle) * d);
			int bz = (int) Math.floor(z + Math.sin(angle) * d);
			if (!loaded(bx, bz)) {
				continue;
			}
			if (d < blastReach * 0.6) {
				BlockPos top = new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1, bz);
				if (TreeFeller.isLog(level.getBlockState(top)) && fell(top, angle)) {
					continue;
				}
			}
			if (random.nextDouble() < 1.2 - d / blastReach) {
				charColumn(bx, bz);
			}
		}
	}

	/** Fells a tree the blast has reached, away from the mountain: two a second at most, near a player, so many in all. */
	private boolean fell(BlockPos log, double angle) {
		int second = age / 20;
		if (second != fellSecond) {
			fellSecond = second;
			felledThisSecond = 0;
		}
		int lx = log.getX();
		int lz = log.getZ();
		if (treesFelled >= treeCap || felledThisSecond >= 2 || nearestPlayer(lx, lz) > 96.0 || !loaded(lx - 8, lz - 8) || !loaded(lx + 8, lz + 8)
				|| !loaded(lx - 8, lz + 8) || !loaded(lx + 8, lz - 8)) {
			return false;
		}
		felledThisSecond++;
		// Few of its blocks fall whole: the crown is shredded and the rest of the trunk dropped.
		if (TreeEvents.snapTrunk(level, log, new Vec3(Math.cos(angle), 0.0, Math.sin(angle)), 0, 320, 4, 12)) {
			treesFelled++;
			return true;
		}
		return false;
	}

	// --- the eruption ------------------------------------------------------------------------

	private void erupt() {
		int since = age - unrest;
		double vigour = Math.min(1.0, since / 200.0);
		// Bombs out of the vent.
		if (bombs.size() < MAX_BOMBS && random.nextDouble() < (0.1 + 0.05 * vei) * vigour) {
			throwBomb();
		}
		// Lava over the rim and down the flanks.
		if (griefing() && !submarine && flowLava < lavaCap && lavaFlows.size() < 2 + vei / 2 && random.nextInt(Math.max(30, 200 - vei * 25)) == 0) {
			startLavaFlow();
		}
		// Ash on the wind.
		if (griefing()) {
			for (int i = 0; i < 8 + 5 * vei; i++) {
				ashfall();
			}
		}
		// Lightning in the plume.
		if (random.nextInt(Math.max(20, 120 - vei * 15)) == 0) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double d = 8.0 + random.nextDouble() * (20.0 + vei * 6.0);
			lightning(x + Math.cos(angle) * d + wind.x * 25.0, z + Math.sin(angle) * d + wind.z * 25.0);
		}
		// The column collapses: pyroclastic flows.
		if (pulses < pulseTimes.length && since >= pulseTimes[pulses]) {
			collapse();
		}
	}

	/** Waning: the column sinks, the ash thins out, and for a while a last bomb now and then. */
	private void wane() {
		double left = strength();
		if (griefing()) {
			int count = (int) Math.round((4 + 2 * vei) * left);
			for (int i = 0; i < count; i++) {
				ashfall();
			}
		}
		if (bombs.size() < MAX_BOMBS && random.nextDouble() < 0.05 * left * left) {
			throwBomb();
		}
	}

	private void throwBomb() {
		double angle = random.nextDouble() * Math.PI * 2.0;
		double elevation = Math.toRadians(48.0 + random.nextDouble() * 38.0);
		double speed = 0.9 + random.nextDouble() * (0.8 + vei * 0.25);
		Vec3 velocity = new Vec3(Math.cos(angle) * Math.cos(elevation) * speed, Math.sin(elevation) * speed, Math.sin(angle) * Math.cos(elevation) * speed);
		double size = 0.5 + random.nextDouble() * (0.5 + vei * 0.18);
		bombs.add(new Bomb(++bombsThrown, age, new Vec3(x, base + 2.0, z), velocity, size));
	}

	/** The bombs in flight, as the clients fly them too (see {@code VolcanoFx}); one that leaves the loaded land is gone. */
	private void tickBombs() {
		for (Iterator<Bomb> it = bombs.iterator(); it.hasNext(); ) {
			Bomb bomb = it.next();
			bomb.pos = bomb.pos.add(bomb.velocity);
			bomb.velocity = bomb.velocity.scale(DRAG).add(0.0, -GRAVITY, 0.0);
			BlockPos at = BlockPos.containing(bomb.pos);
			if (age - bomb.thrown > 400 || at.getY() < level.getMinY() || !loaded(at.getX(), at.getZ())) {
				it.remove();
				continue;
			}
			if (at.getY() >= level.getMaxY()) {
				continue;
			}
			BlockState state = level.getBlockState(at);
			if (bomb.velocity.y < 0 && !state.isAir() && !state.canBeReplaced()) {
				it.remove();
				land(bomb, at.above());
			}
		}
	}

	/**
	 * A bomb comes down: it bursts where someone is near to see it, sets alight what stands by, and the bigger ones
	 * blow out a pit with the bomb itself at the bottom, still glowing.
	 */
	private void land(Bomb bomb, BlockPos at) {
		// A fast one has gone in deeper than the face it struck: back out to it.
		for (int i = 0; i < 4 && at.getY() < level.getMaxY() - 1; i++) {
			BlockState state = level.getBlockState(at);
			if (state.isAir() || state.canBeReplaced()) {
				break;
			}
			at = at.above();
		}
		boolean watched = nearestPlayer(at.getX(), at.getZ()) < WATCHED;
		int second = age / 20;
		if (second != burstSecond) {
			burstSecond = second;
			bursts = 0;
		}
		if (watched && bursts < BURSTS_PER_SECOND) {
			bursts++;
			level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, (float) (0.8 + bomb.size * 0.8), Level.ExplosionInteraction.NONE);
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(at).inflate(1.5 + bomb.size), e -> !immune(e))) {
				entity.igniteForSeconds(5.0F);
			}
		}
		if (!griefing() || blocksLeft <= 0 || !loaded(at.getX() - 3, at.getZ() - 3) || !loaded(at.getX() + 3, at.getZ() + 3)
				|| !loaded(at.getX() - 3, at.getZ() + 3) || !loaded(at.getX() + 3, at.getZ() - 3)) {
			return;
		}
		BlockPos hit = at.below();
		int pit = bomb.size > 1.6 ? 2 : bomb.size > 1.1 ? 1 : 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dy = 1 - pit; dy <= 0; dy++) {
			for (int dx = -pit; dx <= pit; dx++) {
				for (int dz = -pit; dz <= pit; dz++) {
					if (dx * dx + dy * dy + dz * dz > pit * pit) {
						continue;
					}
					BlockState state = level.getBlockState(pos.set(hit.getX() + dx, hit.getY() + dy, hit.getZ() + dz));
					if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F) {
						continue;
					}
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					blocksLeft--;
				}
			}
		}
		BlockPos bottom = hit.below(pit);
		BlockState under = level.getBlockState(bottom);
		if (!under.isAir() && under.getFluidState().isEmpty() && !under.hasBlockEntity() && under.getDestroySpeed(level, bottom) >= 0.0F) {
			level.setBlock(bottom, random.nextBoolean() ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_ALL);
			blocksLeft--;
		}
		if (watched && firesLit < fireCap) {
			BlockPos fire = at.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
			if (level.getBlockState(fire).isAir() && BaseFireBlock.canBePlacedAt(level, fire, Direction.UP)) {
				level.setBlock(fire, BaseFireBlock.getState(level, fire), Block.UPDATE_ALL);
				blocksLeft--;
				firesLit++;
			}
		}
	}

	/** How thick the ash falls at (px, pz), from 1 at the vent to 0 at the edge of the cloud: a plume drawn out downwind. */
	private double ashWeight(double px, double pz) {
		double ex = px - x;
		double ez = pz - z;
		double along = (ex * wind.x + ez * wind.z) / ashReach;
		double across = Math.abs(-ex * wind.z + ez * wind.x) / ashReach;
		if (along < -0.15 || along > 1.0 || across > 0.15 + Math.max(0.0, along) * 0.35) {
			return 0.0;
		}
		return Math.max(0.0, 1.0 - Math.sqrt(ex * ex + ez * ez) / ashReach);
	}

	/** Lays ash somewhere under the cloud: anywhere downwind, thick near the vent and a dusting far off, and now and then right about a player in it. */
	private void ashfall() {
		double ax;
		double az;
		List<ServerPlayer> players = level.players();
		if (!players.isEmpty() && random.nextInt(4) == 0) {
			ServerPlayer player = players.get(random.nextInt(players.size()));
			ax = player.getX() + random.nextGaussian() * 16.0;
			az = player.getZ() + random.nextGaussian() * 16.0;
		} else {
			double u = -0.15 + random.nextDouble() * 1.15;
			double v = (random.nextDouble() * 2.0 - 1.0) * (0.15 + Math.max(0.0, u) * 0.35);
			ax = x + (wind.x * u - wind.z * v) * ashReach;
			az = z + (wind.z * u + wind.x * v) * ashReach;
		}
		double weight = ashWeight(ax, az);
		if (weight <= 0.0 || random.nextDouble() > weight + 0.1) {
			return;
		}
		int bx = (int) Math.floor(ax);
		int bz = (int) Math.floor(az);
		if (!loaded(bx, bz)) {
			return;
		}
		ash(bx, bz, 1 + (int) Math.round(weight * weight * (6 + 3 * vei)));
	}

	/** Adds a layer of ash to a column, until it lies `most` layers deep: eight layers to a block, packed into ash blocks below the top one. */
	private void ash(int bx, int bz, int most) {
		if (blocksLeft <= 0) {
			return;
		}
		int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		if (topY <= level.getMinY() + 1 || topY >= level.getMaxY() - 1) {
			return;
		}
		BlockPos top = new BlockPos(bx, topY, bz);
		BlockState there = level.getBlockState(top);
		// Ash layers don't block motion, so the heightmap stops on one already lying there.
		if (there.is(ModBlocks.ASH_LAYER)) {
			int layers = there.getValue(SnowLayerBlock.LAYERS);
			int deep = layers;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, topY - 1, bz);
			for (int i = 0; i < 4 && level.getBlockState(pos).is(ModBlocks.ASH_BLOCK); i++) {
				deep += 8;
				pos.move(0, -1, 0);
			}
			if (deep >= most) {
				return;
			}
			if (layers < 8) {
				level.setBlock(top, there.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
				blocksLeft--;
			} else if (level.getBlockState(top.above()).isAir()) {
				level.setBlock(top, ModBlocks.ASH_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(top.above(), ModBlocks.ASH_LAYER.defaultBlockState(), Block.UPDATE_ALL);
				blocksLeft -= 2;
			}
			return;
		}
		BlockState under = level.getBlockState(top.below());
		if ((there.isAir() || there.canBeReplaced()) && there.getFluidState().isEmpty() && under.getFluidState().isEmpty()
				&& under.isFaceSturdy(level, top.below(), Direction.UP)) {
			level.setBlock(top, ModBlocks.ASH_LAYER.defaultBlockState(), Block.UPDATE_ALL);
			blocksLeft--;
		}
	}

	// --- lava --------------------------------------------------------------------------------

	/** Lava wells over the crater's lip: at the lowest of a few places round the rim, where it would spill first. */
	private void startLavaFlow() {
		BlockPos best = null;
		double bestAngle = 0.0;
		for (int k = 0; k < 6; k++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int bx = (int) Math.floor(x + Math.cos(angle) * lip);
			int bz = (int) Math.floor(z + Math.sin(angle) * lip);
			if (!loaded(bx, bz)) {
				continue;
			}
			int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
			if (wet(bx, h, bz)) {
				continue;
			}
			if (best == null || h < best.getY()) {
				best = new BlockPos(bx, h, bz);
				bestAngle = angle;
			}
		}
		if (best != null) {
			lavaFlows.add(new LavaFlow(best, 80 + vei * 30, (int) Math.round(Math.cos(bestAngle)), (int) Math.round(Math.sin(bestAngle))));
			lavaFlowsStarted++;
		}
	}

	private void tickLava() {
		// The flows stop being fed with the eruption proper, so that what they poured has cooled by the time it is over.
		boolean feeding = griefing() && age < unrest + plinian;
		for (Iterator<LavaFlow> it = lavaFlows.iterator(); it.hasNext(); ) {
			LavaFlow flow = it.next();
			if (--flow.wait > 0) {
				continue;
			}
			flow.wait = 5 + random.nextInt(7);
			if (!feeding || flowLava >= lavaCap || flow.steps >= flow.length) {
				it.remove();
				continue;
			}
			if (blocksLeft <= 0) {
				continue;
			}
			int before = lavaPlaced;
			if (!pour(flow.head)) {
				it.remove();
				continue;
			}
			if (random.nextInt(3) == 0) {
				// The flow spreads a little to the side as it goes.
				pour(flow.head.offset(flow.dz, 0, -flow.dx));
			}
			// One that has run out over water, or onto lava already poured, makes way for a new one from the crater.
			flow.idle = lavaPlaced > before ? 0 : flow.idle + 1;
			if (flow.idle >= 4) {
				it.remove();
				continue;
			}
			BlockPos next = downhill(flow);
			if (next == null) {
				it.remove();
				continue;
			}
			flow.dx = next.getX() - flow.head.getX();
			flow.dz = next.getZ() - flow.head.getZ();
			flow.head = next;
			flow.steps++;
		}
		int cooled = 0;
		while (!cooling.isEmpty() && cooling.peekFirst().at() <= age && cooled < COOLING_PER_TICK) {
			Cooling next = cooling.pollFirst();
			if (loaded(next.pos().getX(), next.pos().getZ())) {
				cool(next.pos());
			} else {
				// Not to be got at now: later.
				cooling.addLast(new Cooling(next.pos(), age + 200));
			}
			cooled++;
		}
	}

	/** The crust of a flow setting: basalt and blackstone, and here and there a block still glowing within. */
	private void cool(BlockPos pos) {
		if (!loaded(pos.getX(), pos.getZ())) {
			return;
		}
		if (level.getBlockState(pos).is(Blocks.LAVA) && level.getFluidState(pos).isSource()) {
			int roll = random.nextInt(10);
			level.setBlock(pos, roll < 5 ? Blocks.BASALT.defaultBlockState() : roll < 8 ? Blocks.BLACKSTONE.defaultBlockState()
					: Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	/**
	 * Pours lava onto a column. Into shallow water it quenches with a hiss to new land; deep water swallows the flow
	 * (false: it goes no further). Over lava already poured it just runs on.
	 */
	private boolean pour(BlockPos at) {
		if (!loaded(at.getX(), at.getZ()) || blocksLeft <= 0) {
			return true;
		}
		int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		if (topY <= level.getMinY() + 1 || topY >= level.getMaxY() - 1) {
			return false;
		}
		BlockPos top = new BlockPos(at.getX(), topY, at.getZ());
		BlockPos below = top.below();
		if (level.getFluidState(below).is(FluidTags.WATER)) {
			if (!level.getFluidState(below.below()).isEmpty()) {
				return false;
			}
			level.setBlock(below, random.nextBoolean() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BASALT.defaultBlockState(), Block.UPDATE_ALL);
			blocksLeft--;
			return true;
		}
		if (!level.getFluidState(below).isEmpty()) {
			return true;
		}
		BlockState there = level.getBlockState(top);
		// Over the flowers and the grass, and the leaves lying on the ground (the heightmap looks past them), as over air.
		if (there.isAir() || there.getFluidState().isEmpty() && !there.hasBlockEntity()
				&& (there.canBeReplaced() || there.is(BlockTags.LEAVES) || there.getBlock() instanceof VegetationBlock)) {
			level.setBlock(top, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
			blocksLeft--;
			lavaPlaced++;
			flowLava++;
			cooling.addLast(new Cooling(top, age + 400 + random.nextInt(300)));
		}
		return true;
	}

	/** The lowest of the ground around the head of a flow, ahead of it more than behind. */
	private BlockPos downhill(LavaFlow flow) {
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		int here = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, flow.head.getX(), flow.head.getZ());
		double out = Math.hypot(flow.head.getX() + 0.5 - x, flow.head.getZ() + 0.5 - z);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0 || dx == -flow.dx && dz == -flow.dz) {
					continue;
				}
				int bx = flow.head.getX() + dx;
				int bz = flow.head.getZ() + dz;
				// Down the flank, never back over the rim: the crater is the lowest ground about, and a flow that
				// turned into it would only run on over the lake there and pour nothing.
				if (!loaded(bx, bz) || Math.hypot(bx + 0.5 - x, bz + 0.5 - z) < Math.max(lip, out - 0.5)) {
					continue;
				}
				int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
				// Water counts as ground to the heightmap, the lowest about: it is the last way to go, not the first.
				double score = h - (dx == flow.dx && dz == flow.dz ? 0.6 : 0.0) + random.nextDouble() * 0.5 + (wet(bx, h, bz) ? 8.0 : 0.0);
				if (h <= here + 1 && score < bestScore) {
					bestScore = score;
					best = new BlockPos(bx, h, bz);
				}
			}
		}
		return best;
	}

	/** Whether a column's top (`h` its heightmap height) is water or lava standing there. */
	private boolean wet(int bx, int h, int bz) {
		return !level.getFluidState(new BlockPos(bx, h - 1, bz)).isEmpty();
	}

	// --- pyroclastic flows -------------------------------------------------------------------

	/**
	 * The column collapses: its ash and gas pour back down over the rim and race down the mountain as pyroclastic
	 * flows, one or more at a time, down the steepest flanks most of all.
	 */
	private void collapse() {
		if (pulses == 0) {
			warnFlows();
		}
		pulses++;
		double[] angles = new double[12];
		double[] drops = new double[12];
		double total = 0.0;
		for (int k = 0; k < 12; k++) {
			angles[k] = (k + random.nextDouble()) * Math.PI / 6.0;
			int bx = (int) Math.floor(x + Math.cos(angles[k]) * (crater + 24.0));
			int bz = (int) Math.floor(z + Math.sin(angles[k]) * (crater + 24.0));
			double drop = loaded(bx, bz) ? base - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) : 0.0;
			drops[k] = 1.0 + Math.max(0.0, drop);
			total += drops[k];
		}
		int count = 1 + vei / 3;
		for (int n = 0; n < count && flows.size() < MAX_FLOWS && total > 0.0; n++) {
			double pick = random.nextDouble() * total;
			int k = 0;
			while (k < 11 && pick > drops[k]) {
				pick -= drops[k];
				k++;
			}
			total -= drops[k];
			drops[k] = 0.0;
			launchFlow(angles[k]);
		}
	}

	private void launchFlow(double angle) {
		flowsLaunched++;
		double length = 120.0 + vei * 40.0 + random.nextDouble() * 60.0;
		double width = 12.0 + vei * 3.0 + random.nextDouble() * 4.0;
		double speed = 1.2 + vei * 0.06 + random.nextDouble() * 0.5;
		flows.add(new Flow(x + Math.cos(angle) * crater, base, z + Math.sin(angle) * crater, angle, length, width, speed, flowsLaunched % 4));
	}

	private void tickFlows() {
		for (Iterator<Flow> it = flows.iterator(); it.hasNext(); ) {
			Flow flow = it.next();
			steer(flow);
			flow.x += flow.dirX * flow.speed;
			flow.z += flow.dirZ * flow.speed;
			flow.travelled += flow.speed;
			int bx = (int) Math.floor(flow.x);
			int bz = (int) Math.floor(flow.z);
			if (loaded(bx, bz)) {
				flow.y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
			}
			sear(flow);
			if (flow.travelled > flow.length) {
				it.remove();
			}
		}
	}

	/** Gravity steers a flow down the steepest way, its momentum keeping it on course over small rises. */
	private void steer(Flow flow) {
		double heading = Math.atan2(flow.dirZ, flow.dirX);
		double bestAngle = heading;
		double best = Double.MAX_VALUE;
		for (double turn : TURNS) {
			double angle = heading + turn;
			int bx = (int) Math.floor(flow.x + Math.cos(angle) * 6.0);
			int bz = (int) Math.floor(flow.z + Math.sin(angle) * 6.0);
			if (!loaded(bx, bz)) {
				continue;
			}
			double h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) + Math.abs(turn) * 1.5;
			if (h < best) {
				best = h;
				bestAngle = angle;
			}
		}
		double angle = heading + (bestAngle - heading) * 0.35;
		flow.dirX = Math.cos(angle);
		flow.dirZ = Math.sin(angle);
	}

	/**
	 * What a flow engulfs, its front and the searing cloud just behind it, is burned and swept along: few live through
	 * it, and a roof is little help. The land under it is charred and buried in ash, and what wood stands in its way
	 * burns away.
	 */
	private void sear(Flow flow) {
		double half = flow.width / 2.0;
		// It weakens as it runs out of ash and heat.
		double force = 1.0 - 0.6 * Math.min(1.0, flow.travelled / flow.length);
		if ((age + flow.stagger) % 4 == 0) {
			double tx = flow.x - flow.dirX * 20.0;
			double tz = flow.z - flow.dirZ * 20.0;
			AABB box = new AABB(Math.min(flow.x, tx) - half, flow.y - 8.0, Math.min(flow.z, tz) - half, Math.max(flow.x, tx) + half, flow.y + 24.0,
					Math.max(flow.z, tz) + half);
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> !immune(e))) {
				float damage = (float) ((3.0 + vei * 1.5) * force);
				hurt(entity, DisasterDamage.PYROCLASTIC, sheltered(entity) ? damage * 0.6F : damage);
				entity.igniteForSeconds(10.0F);
				shove(entity, flow.dirX * 0.45 * force, 0.1, flow.dirZ * 0.45 * force);
			}
		}
		if (!griefing()) {
			return;
		}
		for (int i = 0; i < 12 && blocksLeft > 0; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = half * Math.sqrt(random.nextDouble());
			int bx = (int) Math.floor(flow.x + Math.cos(angle) * r);
			int bz = (int) Math.floor(flow.z + Math.sin(angle) * r);
			if (loaded(bx, bz)) {
				charColumn(bx, bz);
				if (random.nextInt(3) == 0) {
					ash(bx, bz, 2 + random.nextInt(3));
				}
			}
		}
	}

	/**
	 * Burns a column black, from its top down: leaves, plants and snow gone, wood charred, what else would burn (planks,
	 * thatch, wool) burned away, the grass scorched. Ash already lying there is left lying.
	 */
	private void charColumn(int bx, int bz) {
		int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1, level.getMaxY() - 1);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int by = top; by > top - 20 && by > level.getMinY() && blocksLeft > 0; by--) {
			BlockState state = level.getBlockState(pos.set(bx, by, bz));
			if (state.isAir()) {
				continue;
			}
			if (!state.getFluidState().isEmpty() || state.hasBlockEntity() || state.is(ModBlocks.ASH_LAYER) || state.is(ModBlocks.ASH_BLOCK)
					|| BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("ice")) {
				return;
			}
			if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof VegetationBlock || state.is(Blocks.SNOW) || state.canBeReplaced()) {
				// Nothing about it needs to hear of it: its neighbours are going too.
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				blocksLeft--;
			} else if (state.is(BlockTags.LOGS)) {
				if (!state.is(ModBlocks.CHARRED_LOG)) {
					BlockState charred = ModBlocks.CHARRED_LOG.defaultBlockState();
					if (state.hasProperty(RotatedPillarBlock.AXIS)) {
						charred = charred.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
					}
					level.setBlock(pos, charred, Block.UPDATE_CLIENTS);
					blocksLeft--;
				}
			} else if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
				level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				blocksLeft--;
				return;
			} else if (state.ignitedByLava()) {
				// Burned away: what hangs on it or stands beside it (a door's other half, a torch) has to hear of it.
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				blocksLeft--;
			} else {
				return;
			}
		}
	}

	/** How far the nearest player (not a spectator) is from (px, pz), across the land. */
	private double nearestPlayer(double px, double pz) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator()) {
				best = Math.min(best, distance2d(player, px, pz));
			}
		}
		return best;
	}

	/** Players in the way get a warning when the flows start down the mountain. */
	private void warnFlows() {
		warn(ashReach, player -> Component.translatable("naturesmod.warning.pyroclastic"));
	}

	// --- for the tests -----------------------------------------------------------------------

	public BlockPos vent() {
		return vent;
	}

	/** How far it has got: the ticks of unrest and of the eruption proper (for tests). */
	public int unrestTicks() {
		return unrest;
	}

	public int eruptionTicks() {
		return plinian;
	}

	public int flowsLaunched() {
		return flowsLaunched;
	}

	/** Lava blocks poured: into the crater's lake and by the flows down the flanks. */
	public int lavaPlaced() {
		return lavaPlaced;
	}

	/** How many flows of lava have welled over the crater's lip so far. */
	public int lavaFlowsStarted() {
		return lavaFlowsStarted;
	}

	public int craterRadius() {
		return crater;
	}

	/** Whether the crater has been dug out and its lake laid (it takes some ticks after the blast; at once if there is none to dig). */
	public boolean craterDug() {
		return blown && craterWork.isEmpty();
	}

	/** The bombs in the air now (never more than {@link #MAX_BOMBS}), and thrown in all. */
	public int bombsInAir() {
		return bombs.size();
	}

	public int bombsThrown() {
		return bombsThrown;
	}

	/** The pyroclastic flows racing down now (never more than {@link #MAX_FLOWS}). */
	public int flowsRunning() {
		return flows.size();
	}

	public int treesFelled() {
		return treesFelled;
	}

	public int firesLit() {
		return firesLit;
	}

	/** Whether the vent is under water (the column then rises from the surface, and no crater is dug). */
	public boolean submarine() {
		return submarine;
	}
}
