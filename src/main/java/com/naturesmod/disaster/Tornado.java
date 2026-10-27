package com.naturesmod.disaster;

import java.util.ArrayList;
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

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A tornado, rated on the Enhanced Fujita scale. Its funnel lowers from the cloud base and touches
 * down, then it tracks across the land with its parent storm, steering for where it was aimed and
 * wandering a little, about as fast as a running player, until it weakens, ropes out and lifts. Its
 * winds turn as a Rankine vortex, fastest at the edge of the core: they pull in what stands near, and
 * from EF2 up what comes close to the core is caught, carried up and round the funnel for some seconds
 * (tossed about, struck by the debris going round with it) and flung out, to fall from as high as the
 * tornado lifted it: some ten blocks for an EF2, thirty and more for an EF4. Whatever it tears from the
 * land it throws about too. What it tears away depends on how strong a gust each thing can stand (see
 * {@link WindDamage}): leaves, plants and roofs from EF2, wooden houses from EF3, everything but
 * dressed masonry from EF4; an EF5 brings down stone brick and scours the soil from under its core.
 * Shelter (a roof over your head, a cellar) keeps the wind off you until the roof goes. Lightning
 * strikes out of the storm around it, and its hail falls ahead of it.
 *
 * <p>The work is bounded every tick, however much stands in its path: so many columns of land looked
 * at and so many blocks changed, so many things carried and so many blocks in the air at once; the
 * things near it other than players are looked for every few ticks, not every tick.
 */
public final class Tornado extends Disaster {
	/** The core's radius (where the winds are fastest), in blocks, by rating. */
	private static final double[] CORE = {3.0, 4.5, 6.5, 8.5, 12.0, 15.0};
	/** The peak gust (m/s) by rating: the middle of each EF band. */
	private static final double[] PEAK_WIND = {33.0, 43.0, 54.0, 68.0, 84.0, 105.0};
	private static final int[] LIFETIME = {1400, 1800, 2200, 2600, 3000, 3600};
	/** How high it carries what it catches, in blocks above its foot, by rating: below EF2 it catches nothing. */
	private static final double[] LIFT = {0.0, 0.0, 8.0, 16.0, 30.0, 42.0};
	private static final int FORMING = 240;
	/** At most this many things other than players in its wind are pushed about each tick. */
	private static final int MAX_NEARBY = 96;
	/** How far a carried thing bobs up and down (blocks), and how fast: it is tossed about, never held still. */
	private static final double BOB = 2.2;
	private static final double BOB_RATE = 0.3;

	private final int rating;
	private final double core;
	private final double peakWind;
	private final double speed;
	private final double gustPhase;
	/** What it has caught and is carrying round. */
	private final Map<UUID, Carry> carried = new HashMap<>();
	/** What it has just thrown out, and until when (its age) that is left alone: flying free, then not to be caught again at once. */
	private final Map<UUID, Integer> thrown = new HashMap<>();
	/** Everything other than players in its wind, as last looked for. */
	private final List<Entity> nearby = new ArrayList<>();
	/** The blocks it has torn loose and thrown, while they are still in the air. */
	private final List<FallingBlockEntity> debris = new ArrayList<>();
	/** Columns whose soil it has already scoured away (an EF5's). */
	private final LongSet scoured = new LongOpenHashSet();
	/** Players already told it is almost on them. */
	private final Set<UUID> warned = new HashSet<>();
	private Vec3 target;
	private double heading;
	private double turn;
	private double wind;
	private double coreNow;
	private double cloudBase;
	private boolean touchedDown;
	private boolean overWater;
	private double highest;
	private int effectsLeft;
	private int launchesLeft;

	Tornado(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.TORNADO, level, at, intensity, natural, 0);
		rating = Math.max(0, Math.min(5, Math.round(this.intensity)));
		core = CORE[rating] * (0.9 + random.nextDouble() * 0.2);
		peakWind = PEAK_WIND[rating] * (0.95 + random.nextDouble() * 0.1);
		// About as fast as a running player: a horse outruns it, a runner only just.
		speed = 0.17 + random.nextDouble() * 0.06 + rating * 0.006;
		heading = Math.toRadians(-45.0 + random.nextGaussian() * 30.0);
		duration = (int) (LIFETIME[rating] * (0.85 + random.nextDouble() * 0.3));
		gustPhase = random.nextDouble() * 100.0;
		y = ground((int) Math.floor(x), (int) Math.floor(z)) + 1;
		// The violent ones hang from a lower cloud base: squat, broad wedges.
		cloudBase = y + (rating >= 4 ? 52 : 60) + random.nextInt(20);
		coreNow = core;
	}

	/** Sets it on a track that passes near a place: it steers for it (a little to one side) until it has gone by. */
	public void aim(Vec3 at) {
		double dx = at.x - x;
		double dz = at.z - z;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d < 1.0) {
			return;
		}
		double side = random.nextGaussian() * core * 0.4;
		target = new Vec3(at.x - dz / d * side, at.y, at.z + dx / d * side);
		heading = Math.atan2(target.z - z, target.x - x);
	}

	@Override
	protected void begin() {
		if (!level.isThundering()) {
			// A tornado comes out of its supercell, whatever the sky was doing: it closes in.
			level.getServer().setWeatherParameters(0, duration + 1200, true, true);
		}
	}

	private double formation() {
		return Math.min(1.0, age / (double) FORMING);
	}

	/** 1 through its mature stage, falling to 0 as it ropes out and lifts. */
	private double fade() {
		double tail = duration * 0.25;
		double left = duration - age;
		return left >= tail ? 1.0 : Math.max(0.0, left / tail);
	}

	@Override
	public float strength() {
		return (float) Math.min(formation(), fade());
	}

	@Override
	public float radius() {
		return (float) (core * 5.0 + 24.0);
	}

	@Override
	public Vec3 direction() {
		return new Vec3(Math.cos(heading), 0.0, Math.sin(heading));
	}

	/** How far the funnel leans back along its track, per block up: more as it ropes out. */
	private double tilt() {
		return 0.1 + 0.55 * (1.0 - fade());
	}

	@Override
	public float[] extra() {
		double fade = fade();
		double tilt = tilt();
		double hail = hailStrength();
		return new float[] {(float) coreNow, (float) cloudBase, rating, (float) wind, (float) formation(), (float) (-Math.cos(heading) * tilt),
				(float) (-Math.sin(heading) * tilt), overWater ? 1.0F : 0.0F, (float) fade, (float) hailX(), (float) hailZ(),
				(float) (hail > 0.05 ? hailRadius() * hail : 0.0), (float) (0.06 + rating * 0.03)};
	}

	// --- for the tests ------------------------------------------------------------------------

	/** Its rating on the Enhanced Fujita scale. */
	public int rating() {
		return rating;
	}

	/** The radius of its core now, in blocks. */
	public double core() {
		return coreNow;
	}

	/** Its peak wind now, in metres a second: 0 until it has formed. */
	public double wind() {
		return wind;
	}

	/** Whether its funnel has reached the ground. */
	public boolean touchedDown() {
		return touchedDown;
	}

	/** Whether it has caught this and is carrying it round. */
	public boolean carrying(Entity entity) {
		return carried.containsKey(entity.getUUID());
	}

	/** How it carries this: how high it is now (above the foot) and is to go, for how long it has and is to (ticks); null if it isn't. */
	public double[] carryState(Entity entity) {
		Carry c = carried.get(entity.getUUID());
		return c == null ? null : new double[] {c.height, c.lift, c.ticks, c.hold};
	}

	/** How many things it is carrying round now. */
	public int carriedCount() {
		return carried.size();
	}

	/** The highest above its foot (in blocks) it has carried anything so far. */
	public double highestCarry() {
		return highest;
	}

	/** How many of the blocks it tore loose are in the air now. */
	public int debrisAloft() {
		return debris.size();
	}

	// --- the funnel ---------------------------------------------------------------------------

	/** Where its axis is `up` blocks above its foot, as its clients draw it: leaning back along its track, and meandering. */
	private double axisX(double up) {
		up = Math.max(0.0, up);
		return x - Math.cos(heading) * tilt() * up + Math.sin(up * 0.07 + age * 0.03) * coreNow * 0.22;
	}

	private double axisZ(double up) {
		up = Math.max(0.0, up);
		return z - Math.sin(heading) * tilt() * up + Math.cos(up * 0.05 + age * 0.025) * coreNow * 0.22;
	}

	/** The air's velocity (blocks a tick) at (dx, dz) from the axis, `height` above the foot; above `ceiling` nothing is lifted further. */
	private Vec3 air(double dx, double dz, double height, double peak, double ceiling) {
		Vec3 air = Vortex.air(dx, dz, Math.max(0.0, height), coreNow, peak, cloudBase - y);
		return height > ceiling ? new Vec3(air.x, Math.min(air.y, 0.0), air.z) : air;
	}

	// --- the hail -----------------------------------------------------------------------------

	/** The storm's hail falls hardest in its forward flank: ahead of the tornado and to its left. */
	private double hailX() {
		return x + Math.cos(heading) * 55.0 + Math.sin(heading) * 45.0;
	}

	private double hailZ() {
		return z + Math.sin(heading) * 55.0 - Math.cos(heading) * 45.0;
	}

	private double hailRadius() {
		return 40.0 + rating * 8.0;
	}

	/** How hard it hails: once the storm has matured, until it dies. */
	private double hailStrength() {
		return Math.max(0.0, Math.min(1.0, (formation() - 0.3) * 2.5)) * Math.min(1.0, fade() * 2.0);
	}

	/** The hail: bruising what is caught out in it, shredding the crops, and the bigger stones breaking windows. */
	private void hail() {
		double strength = hailStrength();
		if (strength <= 0.05) {
			return;
		}
		double hx = hailX();
		double hz = hailZ();
		double reach = hailRadius();
		if (age % 20 == 0) {
			AABB box = new AABB(hx - reach, y - 30, hz - reach, hx + reach, y + 60, hz + reach);
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> !immune(e))) {
				double d = distance2d(entity, hx, hz);
				if (d < reach && !sheltered(entity) && random.nextDouble() < strength * (1.0 - d / reach) * 0.6) {
					hurt(entity, DisasterDamage.HAIL, (float) (0.5 + rating * 0.35));
				}
			}
		}
		if (!griefing() || age % 5 != 0) {
			return;
		}
		for (int i = 0; i < 4; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = reach * Math.sqrt(random.nextDouble());
			int bx = (int) Math.floor(hx + Math.cos(angle) * r);
			int bz = (int) Math.floor(hz + Math.sin(angle) * r);
			if (!loaded(bx, bz) || random.nextDouble() > strength) {
				continue;
			}
			BlockPos top = new BlockPos(bx, level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1, bz);
			BlockState state = level.getBlockState(top);
			if (state.is(BlockTags.CROPS)) {
				level.destroyBlock(top, random.nextBoolean());
			} else if (rating >= 3 && random.nextInt(5) == 0 && WindDamage.shatters(state) && !state.is(BlockTags.LEAVES)
					&& !state.canBeReplaced()) {
				level.destroyBlock(top, false);
			}
		}
	}

	// --- each tick ----------------------------------------------------------------------------

	@Override
	protected void tick() {
		double fade = fade();
		double formed = Math.max(0.0, (formation() - 0.5) * 2.0);
		double gust = 0.86 + 0.1 * Math.sin(age * 0.045 + gustPhase) + 0.06 * Math.sin(age * 0.21 + gustPhase * 2.0);
		wind = peakWind * Math.min(1.0, formed) * Math.pow(fade, 0.7) * gust;
		// A roping tornado thins as it dies.
		coreNow = core * (fade < 0.6 ? 0.45 + fade * 0.9 : 1.0);
		move(fade);
		if (formation() >= 0.6 && !touchedDown) {
			touchedDown = true;
			announce();
		}
		hail();
		debris.removeIf(block -> !block.isAlive());
		if (touchedDown && wind > 20.0) {
			affectEntities();
			if (griefing() && !overWater) {
				damageLand();
			}
		} else if (!carried.isEmpty()) {
			releaseAll();
		}
		if (age % 100 == 0) {
			thrown.values().removeIf(until -> until < age);
		}
		if (random.nextInt(220) == 0) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 30.0 + random.nextDouble() * 70.0;
			lightning(x + Math.cos(angle) * distance, z + Math.sin(angle) * distance);
		}
		if (natural && age % 100 == 0 && playersWithin(1200).isEmpty()) {
			end();
		}
	}

	@Override
	protected void finish() {
		releaseAll();
		nearby.clear();
		debris.clear();
	}

	private void move(double fade) {
		// A tornado's track runs nearly straight, bending gently this way and that.
		turn += random.nextGaussian() * 0.00012;
		turn = Math.max(-0.002, Math.min(0.002, turn)) * 0.99;
		heading += turn;
		if (target != null && fade >= 0.6) {
			double dx = target.x - x;
			double dz = target.z - z;
			if (dx * dx + dz * dz < 20.0 * 20.0) {
				// Gone by: on it goes the way it was going.
				target = null;
			} else {
				double off = Math.atan2(dz, dx) - heading;
				off = Math.atan2(Math.sin(off), Math.cos(off));
				heading += Math.max(-0.004, Math.min(0.004, off));
			}
		}
		if (fade < 0.6) {
			// Roping out, it hooks to the left as the storm occludes.
			heading -= 0.0012;
		}
		x += Math.cos(heading) * speed;
		z += Math.sin(heading) * speed;
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		if (loaded(bx, bz)) {
			int top = ground(bx, bz);
			overWater = level.getFluidState(new BlockPos(bx, top, bz)).is(FluidTags.WATER);
			// Along the ground under the trees, not hopping over their tops.
			y = (overWater ? top : terrain(bx, bz)) + 1;
			cloudBase = Math.max(cloudBase, y + 40);
		}
	}

	private void announce() {
		Component rated = Component.literal("EF" + rating);
		Component moving = compass(Math.cos(heading), Math.sin(heading));
		warn(900, player -> Component.translatable("naturesmod.warning.tornado", rated, (int) distance2d(player, x, z),
				compass(player.getX(), player.getZ(), x, z), moving));
		DisasterNet.send(level, new DisasterNet.Event(DisasterNet.Event.SIREN, x, y, z, new float[] {rating}), 700);
	}

	// --- what it picks up ---------------------------------------------------------------------

	/** Something it has caught: carried up and round the funnel, then thrown out. */
	private static final class Carry {
		final Entity entity;
		/** How high it is to be carried, and for how long in all. */
		final double lift;
		final int hold;
		final double phase;
		double angle;
		double orbit;
		double height;
		int ticks;

		Carry(Entity entity, double lift, int hold, double phase, double angle, double orbit, double height) {
			this.entity = entity;
			this.lift = lift;
			this.hold = hold;
			this.phase = phase;
			this.angle = angle;
			this.orbit = orbit;
			this.height = height;
		}
	}

	/** How fast it lifts what it has caught, in blocks a tick. */
	private double climb() {
		return 0.3 + 0.04 * rating;
	}

	/** Whether something this far from the axis and this high above the foot is caught. */
	private boolean catches(double r, double height) {
		return rating >= 2 && wind > 36.0 && fade() >= 0.6 && r < coreNow * 1.3 && height > -6.0 && height < 6.0 + rating * 3.0;
	}

	/** Whether it can carry something this big off (not a ghast, nor a dragon). */
	private static boolean liftable(Entity entity) {
		return entity.getBbWidth() * entity.getBbHeight() <= 6.0F;
	}

	private void seize(Entity entity, double dx, double dz, double height) {
		if (entity.isPassenger()) {
			// Torn out of the saddle (or the boat, or the cart).
			entity.stopRiding();
		}
		double lift = Math.min(LIFT[rating] * (0.8 + random.nextDouble() * 0.35), (cloudBase - y) * 0.75);
		double start = Math.max(0.0, height);
		int rise = (int) (Math.max(0.0, lift - start) / climb());
		int hold = rise + 30 + random.nextInt(50);
		double r = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		carried.put(entity.getUUID(), new Carry(entity, lift, hold, random.nextDouble() * Math.PI * 2.0, Math.atan2(dz, dx), r, start));
	}

	/**
	 * Carries round what it has caught: round the axis as fast as the wind goes and up the core, rising and
	 * dropping as it is tossed about, until it is thrown out. A thing is made to go where it should be, and what
	 * it is doing now hardly matters: a player in it can do nothing, and may as well hold on.
	 */
	private void carry(double peak) {
		double around = Math.max(0.45, Math.min(1.9, peak * 1.15));
		double orbitGoal = Math.max(2.0, coreNow * 0.6);
		double climb = climb();
		double trackX = Math.cos(heading) * speed;
		double trackZ = Math.sin(heading) * speed;
		for (Iterator<Carry> it = carried.values().iterator(); it.hasNext(); ) {
			Carry c = it.next();
			Entity entity = c.entity;
			if (!entity.isAlive() || entity.level() != level || immune(entity)) {
				it.remove();
				continue;
			}
			c.ticks++;
			c.orbit += (orbitGoal - c.orbit) * 0.06;
			c.angle -= around / Math.max(1.5, c.orbit);
			boolean rising = c.height < c.lift;
			c.height = Math.min(c.lift, c.height + climb);
			// Tossed up and down once off the ground: never held level for long (a server that doesn't allow flying
			// would take a player hanging in the air for four seconds for a cheat).
			double toss = c.ticks * BOB_RATE + c.phase;
			double sway = BOB * Math.min(1.0, c.height / 4.0);
			double h = c.height + sway * Math.sin(toss);
			double goalX = axisX(h) + Math.cos(c.angle) * c.orbit;
			double goalZ = axisZ(h) + Math.sin(c.angle) * c.orbit;
			double goalY = y + h;
			double ex = goalX - entity.getX();
			double ey = goalY - entity.getY();
			double ez = goalZ - entity.getZ();
			double off = Math.sqrt(ex * ex + ey * ey + ez * ez);
			if (c.ticks >= c.hold || !(off < coreNow * 2.0 + 8.0)) {
				// Its time is up, or it has been caught on something and left behind.
				fling(c);
				it.remove();
				continue;
			}
			// Where it should go, and a pull back to where it should be. A player's own game does the pulling, from where
			// the player really is: the server only hears where it went a moment after it went there.
			Vec3 along = new Vec3(trackX + Math.sin(c.angle) * around, (rising ? climb : 0.0) + sway * BOB_RATE * Math.cos(toss),
					trackZ - Math.cos(c.angle) * around);
			if (entity instanceof ServerPlayer player) {
				carryPlayer(player, along, new Vec3(goalX, goalY, goalZ), 0.35, 2.8);
			} else {
				setVelocity(entity, limit(along.add(ex * 0.5, ey * 0.5, ez * 0.5), 2.8));
			}
			// The fall is from where it is let go: the tossing on the way up doesn't count toward it.
			entity.resetFallDistance();
			highest = Math.max(highest, entity.getY() - y);
			if (entity instanceof LivingEntity && c.ticks % 20 == 0 && random.nextBoolean()) {
				// Struck by what flies round with it.
				hurt(entity, DisasterDamage.TORNADO, 0.5F + rating * 0.4F);
			}
		}
	}

	/** Thrown out of the funnel: outward and on round, a little up, to come down where it may (from as high as it was). */
	private void fling(Carry c) {
		Entity entity = c.entity;
		if (entity.isAlive() && !immune(entity)) {
			double out = 0.8 + rating * 0.14;
			double on = 0.5 + rating * 0.1;
			setVelocity(entity, new Vec3(Math.cos(c.angle) * out + Math.sin(c.angle) * on, 0.25 + random.nextDouble() * 0.3,
					Math.sin(c.angle) * out - Math.cos(c.angle) * on));
		}
		// Left to fly for a few seconds, and not caught again at once.
		thrown.put(entity.getUUID(), age + 100);
	}

	private void releaseAll() {
		for (Carry c : carried.values()) {
			fling(c);
		}
		carried.clear();
	}

	/** Ticks left for which something it threw is let be: past 60 it is still flying free. */
	private int thrownFor(Entity entity) {
		Integer until = thrown.get(entity.getUUID());
		return until == null ? 0 : until - age;
	}

	/** Pulls in, lifts and flings what stands in its wind; catches and carries what comes near the core; batters what is in it. */
	private void affectEntities() {
		double peak = wind * Vortex.WIND_SCALE;
		double reach = coreNow * 4.0 + 10.0;
		double ceiling = 12.0 + rating * 9.0;
		carry(peak);
		int mobsCarried = 0;
		for (Carry c : carried.values()) {
			if (!(c.entity instanceof Player)) {
				mobsCarried++;
			}
		}
		// The players, every tick: their own games move them, and need telling.
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || immune(player)) {
				continue;
			}
			double height = player.getY() - y;
			double dx = player.getX() - axisX(height);
			double dz = player.getZ() - axisZ(height);
			double r = Math.sqrt(dx * dx + dz * dz);
			if (r > reach) {
				continue;
			}
			if (carried.containsKey(player.getUUID()) || sheltered(player)) {
				continue;
			}
			int free = thrownFor(player);
			if (free > 60) {
				continue;
			}
			warnNear(player, r);
			if (free <= 0 && catches(r, height)) {
				seize(player, dx, dz, height);
				continue;
			}
			if (player.isPassenger() || player.isInWater()) {
				// Its mount takes the wind for it; a swimmer moves as the water lets it, not as on land or in the air.
				continue;
			}
			blowPlayer(player, air(dx, dz, height, peak, ceiling));
			if (r < coreNow * 1.15 && age % 10 == 0) {
				// Debris in the core (a tornado too weak to pick anyone up still pelts them).
				hurt(player, DisasterDamage.TORNADO, 0.5F + rating * 0.5F);
			}
		}
		// Everything else in its wind: looked for every few ticks, pushed every tick.
		if (age % 5 == 0) {
			nearby.clear();
			AABB box = new AABB(x - reach, y - 6.0, z - reach, x + reach, y + ceiling + 12.0, z + reach);
			List<Entity> found = level.getEntitiesOfClass(Entity.class, box, e -> e.isAlive() && !(e instanceof Player));
			// The animals first: a heap of dropped things mustn't crowd them out.
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
		for (Entity entity : nearby) {
			if (!entity.isAlive() || immune(entity) || carried.containsKey(entity.getUUID())) {
				continue;
			}
			int free = thrownFor(entity);
			if (free > 60) {
				continue;
			}
			double height = entity.getY() - y;
			double dx = entity.getX() - axisX(height);
			double dz = entity.getZ() - axisZ(height);
			double r = Math.sqrt(dx * dx + dz * dz);
			if (r > reach) {
				continue;
			}
			boolean sheltered = sheltered(entity);
			if (!sheltered && free <= 0 && entity instanceof LivingEntity && mobsCarried < 4 + rating * 4 && catches(r, height) && liftable(entity)) {
				seize(entity, dx, dz, height);
				mobsCarried++;
				continue;
			}
			Vec3 air = air(dx, dz, height, peak, ceiling);
			double response;
			if (entity instanceof ItemEntity || entity instanceof FallingBlockEntity) {
				response = 0.25;
			} else {
				response = 0.13 / Math.max(1.0, entity.getBbWidth() * entity.getBbHeight() * 0.8);
			}
			if (sheltered) {
				response *= 0.06;
			}
			double gust = air.horizontalDistance();
			if (entity.onGround() && !(entity instanceof ItemEntity) && gust < 0.3) {
				// Feet on the ground hold against a light wind.
				response *= gust / 0.3 * 0.6;
			}
			// A body tumbling in the updraft catches far more of it than it does of the wind going round.
			Vec3 velocity = entity.getDeltaMovement();
			Vec3 next = velocity.add(air.subtract(velocity).multiply(response, response * 2.4, response));
			setVelocity(entity, limit(next, 2.2));
			if (entity instanceof LivingEntity && !sheltered && r < coreNow * 1.15 && age % 10 == 0) {
				hurt(entity, DisasterDamage.TORNADO, 0.5F + rating * 0.5F);
			}
		}
	}

	/**
	 * The wind on a player it hasn't caught: its game pulls it toward the speed of the air, from however fast it is
	 * really going. A player keeps running, against it or across it, only harder, and off the ground (jumping,
	 * falling) is blown about far more, and lifted in the updraft.
	 */
	private void blowPlayer(ServerPlayer player, Vec3 air) {
		double pull = 0.07 + 0.012 * rating;
		blow(player, air, pull, pull * 1.5, 2.2);
	}

	/** A player it is almost on is told what to do, once. */
	private void warnNear(ServerPlayer player, double r) {
		if (rating >= 2 && r < coreNow * 3.0 + 12.0 && NaturesConfig.get().disasterWarnings && warned.add(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("naturesmod.warning.tornado_near").withStyle(ChatFormatting.GOLD));
		}
	}

	private static Vec3 limit(Vec3 v, double max) {
		double length = v.length();
		if (!(length < 1.0E6)) {
			return Vec3.ZERO;
		}
		return length > max ? v.scale(max / length) : v;
	}

	// --- what it tears from the land ----------------------------------------------------------

	/**
	 * Tears at the land under it: from the top of each column down, as long as what it meets gives way. So many
	 * columns a tick and so many blocks changed, however much stands in its path; only a few of them break with
	 * the crack and the shower of bits of a block broken (each of those is a burst of particles for every player
	 * looking), and the rest go quietly, the dust cloud the clients draw hiding it.
	 */
	private void damageLand() {
		int samples = 12 + rating * 10;
		int budget = 60 + rating * 35;
		effectsLeft = 3 + rating;
		launchesLeft = 2 + rating / 2;
		int debrisCap = 12 + rating * 7;
		double reach = coreNow * 1.5 + 2.0;
		int depth = 3 + rating * 3;
		Vec3 dir = direction();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos under = new BlockPos.MutableBlockPos();
		for (int i = 0; i < samples && budget > 0; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = reach * Math.sqrt(random.nextDouble());
			int bx = (int) Math.floor(x + Math.cos(angle) * r);
			int bz = (int) Math.floor(z + Math.sin(angle) * r);
			if (!loaded(bx, bz)) {
				continue;
			}
			// The winds are strongest at the edge of the core, and the storm's own motion adds on one side.
			double profile = r < coreNow ? 0.75 + 0.25 * r / coreNow : Math.pow(coreNow / r, 0.6);
			double tangentialX = Math.sin(angle);
			double tangentialZ = -Math.cos(angle);
			double motion = speed * 20.0 * (tangentialX * dir.x + tangentialZ * dir.z);
			double local = wind * profile + motion;
			if (local < 20.0) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
			Vec3 throwDir = new Vec3(tangentialX, 0.0, tangentialZ);
			for (int by = top; by > top - depth && by > level.getMinY() + 1 && budget > 0; by--) {
				pos.set(bx, by, bz);
				BlockState state = level.getBlockState(pos);
				if (state.isAir()) {
					continue;
				}
				if (!state.getFluidState().isEmpty()) {
					break;
				}
				double threshold = WindDamage.threshold(level, pos, state);
				if (level.getBlockState(under.set(bx, by - 1, bz)).isAir()) {
					// A roof, or anything else with the wind getting under it, lifts off more easily.
					threshold *= 0.82;
				}
				double gust = local * (0.8 + 0.4 * random.nextDouble());
				if (gust < threshold) {
					break;
				}
				if (TreeFeller.isLog(state)) {
					// A tree felled where nothing moves (loaded at the edge of the world, but not running) would hang in the air.
					if (debris.size() < debrisCap / 2 && level.isPositionEntityTicking(pos)
							&& fellTree(pos.immutable(), throwDir, random.nextInt(3), rating >= 4 ? 2 : 1)) {
						budget -= 8;
						break;
					}
					if (rating < 3) {
						break;
					}
					// Snapped and shredded instead, a piece at a time.
				} else if (WindDamage.isGround(state)) {
					budget -= scour(pos, state, gust, r, throwDir, debrisCap);
					break;
				}
				int changed = tearAway(pos.immutable(), state, throwDir, gust, debrisCap);
				if (changed <= 0) {
					// It holds this tick: what is under it stays too.
					break;
				}
				budget -= changed;
			}
		}
	}

	/**
	 * The ground itself: strong winds strip the turf off it, and under the core of an EF5 the soil is scoured away
	 * down to the rock (three blocks at most, each column once). Returns the blocks changed.
	 */
	private int scour(BlockPos.MutableBlockPos pos, BlockState state, double gust, double r, Vec3 throwDir, int debrisCap) {
		if (rating >= 5 && gust > 95.0 && r < coreNow) {
			long column = BlockPos.asLong(pos.getX(), 0, pos.getZ());
			if (scoured.size() > 20000) {
				scoured.clear();
			}
			if (scoured.add(column)) {
				return dig(pos.getX(), pos.getY(), pos.getZ(), throwDir, debrisCap);
			}
		}
		if (gust > 70.0 && state.is(Blocks.GRASS_BLOCK) && random.nextInt(3) == 0) {
			level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
			if (random.nextInt(6) == 0) {
				level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.4, 0.2, 0.4, 0.05);
			}
			return 1;
		}
		return 0;
	}

	private int dig(int bx, int top, int bz, Vec3 throwDir, int debrisCap) {
		int changed = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int by = top; by > top - 3 && by > level.getMinY() + 1; by--) {
			at.set(bx, by, bz);
			BlockState state = level.getBlockState(at);
			if (!(WorldGenUtil.isSoftGround(state) || state.is(Blocks.SNOW_BLOCK)) || wet(bx, by, bz)) {
				break;
			}
			if (debris.size() < debrisCap && launchesLeft > 0 && random.nextInt(5) == 0 && level.isPositionEntityTicking(at)) {
				launch(at.immutable(), state, throwDir, 80.0);
			} else {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			changed++;
		}
		return changed;
	}

	/** Water (or lava) beside or above a block, that would run into the hole it left. */
	private boolean wet(int bx, int by, int bz) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		return !level.getFluidState(at.set(bx + 1, by, bz)).isEmpty() || !level.getFluidState(at.set(bx - 1, by, bz)).isEmpty()
				|| !level.getFluidState(at.set(bx, by, bz + 1)).isEmpty() || !level.getFluidState(at.set(bx, by, bz - 1)).isEmpty()
				|| !level.getFluidState(at.set(bx, by + 1, bz)).isEmpty();
	}

	/**
	 * Tears a block away: shredded, or now and then thrown whole. Returns the blocks changed (0 if it holds this
	 * tick). Only blocks in the part of the world that is running are thrown: elsewhere they would hang in the air.
	 */
	private int tearAway(BlockPos pos, BlockState state, Vec3 throwDir, double gust, int debrisCap) {
		if (state.hasBlockEntity()) {
			if (effectsLeft <= 0) {
				// A few a tick: a storeroom of chests spills out over some seconds, not all at once.
				return 0;
			}
			// Its contents spill out over the ground.
			level.destroyBlock(pos, true);
			effectsLeft--;
			return 1;
		}
		boolean shatters = WindDamage.shatters(state);
		if (!shatters && launchesLeft > 0 && debris.size() < debrisCap && random.nextInt(3) == 0 && state.isCollisionShapeFullBlock(level, pos)
				&& level.isPositionEntityTicking(pos)) {
			launch(pos, state, throwDir, gust);
			return 1;
		}
		if (effectsLeft > 0 && random.nextInt(shatters ? 4 : 2) == 0) {
			// The crack of it going, and now and then something of it left to fall.
			effectsLeft--;
			level.destroyBlock(pos, random.nextInt(shatters ? 30 : 6) == 0);
		} else {
			// Leaves, plants and glass needn't tell their neighbours; what held up a torch or a door must.
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), shatters ? Block.UPDATE_CLIENTS : Block.UPDATE_ALL);
		}
		return 1;
	}

	/** Lifts a block whole into the wind, to come down somewhere else (on whatever is there). */
	private void launch(BlockPos pos, BlockState state, Vec3 throwDir, double gust) {
		FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
		double fling = Math.min(1.6, gust * Vortex.WIND_SCALE);
		setVelocity(block, new Vec3(throwDir.x * fling, 0.4 + random.nextDouble() * 0.5, throwDir.z * fling));
		block.setHurtsEntities(1.5F, 12);
		debris.add(block);
		// Swirled round from the next tick on.
		nearby.add(block);
		launchesLeft--;
	}
}
