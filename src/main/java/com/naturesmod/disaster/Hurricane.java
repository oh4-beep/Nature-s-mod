package com.naturesmod.disaster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A hurricane, rated on the Saffir-Simpson scale, category 1 to 5: a cyclone some five hundred to a
 * thousand blocks across, coming in slowly from far off along its track. First its outer rain bands
 * arrive, squalls of driving rain and gusts with lulls between them, the wind rising and the sky
 * darkening band after band; then the eyewall, a ring of towering storm about a calm eye, where the
 * winds are strongest; then the eye itself, calm, the sky opening overhead with the wall of cloud all
 * round; then the far side of the eyewall, its wind now from the other way, and the bands again as it
 * goes off. Its winds turn counterclockwise seen from above and spiral in toward the eye (see
 * {@link #wind}), strongest on the right of its track, where its own motion adds to them. While it
 * lasts the sky rains and thunders, and lightning strikes in its bands.
 *
 * <p>The wind leans on what is out in it: dropped things blow away, animals are pushed along, a player
 * walks into it with difficulty and is carried off with it once off the ground; in the eyewall of the
 * strongest a gust now and then knocks a player off their feet and flings them downwind, and what flies
 * in it wounds. A roof over your head keeps all of it off. The land suffers by category: leaves are
 * stripped and crops and flowers flattened from category 1, windows shattered as the gusts rise, and
 * from category 3 the fittings of buildings (fences, doors, wool, signs) and the planks of wooden walls
 * and roofs are torn away and trees brought down; stone stands. Now and then, from category 2, a
 * short-lived tornado spins up in one of its bands.
 *
 * <p>The work is bounded every tick, however big it is: only the players and what is near them are
 * looked at (the things other than players every ten ticks, so many at most), so many columns of land
 * about the players looked at and so many blocks changed in all, a few trees a second at most, and so
 * many torn-off blocks in the air at once.
 */
public final class Hurricane extends Disaster {
	/** The peak sustained wind (m/s) by category: about the middle of each band of the Saffir-Simpson scale. */
	private static final double[] PEAK_WIND = {0.0, 38.0, 46.0, 54.0, 64.0, 78.0};
	/** How many trees it may bring down a second, by category (each one a shower of falling blocks: two at most, as for a tornado). */
	private static final int[] TREES = {0, 0, 1, 1, 2, 2};
	/** Metres a second to blocks a tick for what it blows about (as for a tornado's winds). */
	private static final double SCALE = 0.012;
	/** The share of its peak wind at the edge of the eye: the eye is calm. */
	private static final double CALM = 0.12;
	/** How far its wind turns in toward the eye from going round it, outside the eyewall (radians). */
	private static final double INFLOW = 0.35;
	/** How many spiral rain bands it has, how tightly they wind in, and how fast they turn (radians a tick). */
	private static final int ARMS = 3;
	private static final double SPIRAL = 2.4;
	private static final double BAND_TURN = 0.0012;
	/** Ticks over which it spins up, and over which it dies away at the end. */
	private static final int FORMING = 400;
	private static final int FADING = 600;
	private static final int MAX_DURATION = 18000;
	/** At most this many columns of land looked at a tick, and this many blocks changed, all players together. */
	private static final int MAX_COLUMNS = 128;
	private static final int MAX_CHANGES = 96;
	/** How far about each player the land is torn at (where it is seen). */
	private static final double NEAR = 40.0;
	/** How far down from the top of a column it looks for what the wind can get at. */
	private static final int DEPTH = 12;
	/** At most this many things other than players in its wind are pushed about each tick. */
	private static final int MAX_NEARBY = 96;
	/** No wind weaker than this (m/s) pushes anything. */
	private static final double FELT = 18.0;
	/** The gusts (m/s) things begin to give way to. */
	private static final double PLANT = 30.0;
	private static final double LEAF = 33.0;
	private static final double GLASS = 42.0;
	private static final double FITTING = 46.0;
	private static final double TREE = 52.0;
	private static final double WOOD = 56.0;
	/** The light fittings of a building, by name: what goes once the gusts are strong enough (if it isn't iron or stone). */
	private static final String[] FITTINGS = {"fence", "door", "sign", "banner", "carpet", "wool", "torch", "ladder", "scaffolding", "flower_pot",
			"potted_", "hay_block", "button", "pressure_plate", "lever", "candle"};

	/** What a block is to the wind. */
	private enum Kind {
		/** The land itself, or what can't be broken: nothing under it is looked at. */
		GROUND,
		LOG,
		LEAVES,
		/** Crops, flowers, grass and the like. */
		PLANT,
		GLASS,
		/** Fences, doors, wool, signs and the like. */
		FITTING,
		/** Planks and wooden slabs and stairs: the walls and roofs of wooden buildings. */
		WOOD,
		/** Anything else, which stands. */
		OTHER
	}

	private final int category;
	private final double peakWind;
	/** The radius of the eye, how thick the eyewall is, and the radius of the strongest winds (the eye plus the wall). */
	private final double eye;
	private final double wall;
	private final double rmw;
	/** How far its winds reach. */
	private final double outer;
	/** How fast it moves along its track (blocks a tick). */
	private final double speed;
	private final int debrisCap;
	private double heading;
	private double turn;
	private Vec3 target;
	/** Its peak wind now (m/s): naught as it forms and dies away. */
	private double peakNow;
	private boolean overWater;
	private boolean seenSea;
	private boolean landfall;
	/** The last tornado it spun up, and how many more it may. */
	private Disaster tornado;
	private int tornadoesLeft;
	/** What a gust has just knocked down and thrown, and until when (its age) that is let fly. */
	private final Map<UUID, Integer> flung = new HashMap<>();
	/** Players already told its eyewall is almost on them, or that they are in its eye. */
	private final Set<UUID> warnedWall = new HashSet<>();
	private final Set<UUID> warnedEye = new HashSet<>();
	/** Everything other than players in its wind near a player, as last looked for. */
	private final List<Entity> nearby = new ArrayList<>();
	/** The blocks it has torn loose and thrown, while they are still in the air. */
	private final List<FallingBlockEntity> debris = new ArrayList<>();
	/** What each kind of block is to the wind, as worked out the first time it was met. */
	private final Map<Block, Kind> kinds = new HashMap<>();
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
	/** What may still be done this tick. */
	private int columnsLeft;
	private int changesLeft;
	private int effectsLeft;
	private int dropsLeft;
	private int launchesLeft;
	private int leavesStripped;
	private int plantsFlattened;
	private int windowsBroken;
	private int buildingTorn;
	private int treesFelled;
	private int strikes;
	private int tornadoes;
	private int flings;

	Hurricane(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.HURRICANE, level, at, intensity, natural, 0);
		category = Math.max(1, Math.min(5, Math.round(this.intensity)));
		peakWind = PEAK_WIND[category] * (0.95 + random.nextDouble() * 0.1);
		// The strongest wind up the tightest eyes.
		eye = Math.max(15.0, Math.min(40.0, 36.0 - 4.0 * category + random.nextGaussian() * 3.0));
		wall = 10.0 + 2.0 * category;
		rmw = eye + wall;
		outer = Math.min(500.0, 250.0 + 55.0 * (category - 1) + random.nextDouble() * 30.0);
		speed = 0.11 + random.nextDouble() * 0.03;
		heading = random.nextDouble() * Math.PI * 2.0;
		debrisCap = 6 + 4 * category;
		tornadoesLeft = category >= 4 ? 2 : category >= 2 ? 1 : 0;
		duration = 4800 + 600 * category;
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		// It lives on the sea and the low land by it: never drawn from under the ground.
		y = Math.max(level.getSeaLevel(), loaded(bx, bz) ? ground(bx, bz) + 1 : y);
	}

	/**
	 * Sets it on a track that brings its eye over (or close by) a place: it steers for it until it has gone by,
	 * and lasts until its far side has passed over it too.
	 */
	public void aim(Vec3 at) {
		if (at == null) {
			return;
		}
		double dx = at.x - x;
		double dz = at.z - z;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (!(d >= 1.0)) {
			return;
		}
		double side = random.nextGaussian() * eye * 0.5;
		target = new Vec3(at.x - dz / d * side, at.y, at.z + dx / d * side);
		heading = Math.atan2(target.z - z, target.x - x);
		int crossing = (int) ((d + outer * 0.75) / speed) + FADING;
		duration = Math.max(duration, Math.min(MAX_DURATION, age + crossing));
	}

	@Override
	protected void begin() {
		weather();
	}

	/** The sky rains and thunders while it lasts, whatever it was doing. */
	private void weather() {
		if (level.dimension() == Level.OVERWORLD && !level.isThundering()) {
			level.getServer().setWeatherParameters(0, Math.max(1200, duration - age + 1200), true, true);
		}
	}

	@Override
	public float strength() {
		float in = Math.min(1.0F, age / (float) FORMING);
		float out = Math.min(1.0F, (duration - age) / (float) FADING);
		return Math.max(0.0F, Math.min(in, out));
	}

	@Override
	public float radius() {
		return (float) outer;
	}

	@Override
	public Vec3 direction() {
		return new Vec3(Math.cos(heading), 0.0, Math.sin(heading));
	}

	/**
	 * For its clients: the radius of the eye and of the strongest winds, the category, the peak wind now (m/s), the
	 * base and the top of its clouds, how fast it moves, and whether its centre is over the sea.
	 */
	@Override
	public float[] extra() {
		return new float[] {(float) eye, (float) rmw, category, (float) peakNow, (float) cloudBase(), (float) cloudTop(), (float) speed,
				overWater ? 1.0F : 0.0F};
	}

	private double cloudBase() {
		return y + 36.0;
	}

	private double cloudTop() {
		return Math.min(level.getMaxY() - 2.0, y + 120.0 + 14.0 * category);
	}

	// --- its winds (shared with its clients) ---------------------------------------------------

	/**
	 * The share of its peak wind `r` blocks from its centre: calm in the eye, rising steeply through the eyewall to
	 * its greatest at `rmw`, falling off slowly outside it, and dying away over the last fifth of its reach.
	 */
	public static double profile(double r, double eye, double rmw, double outer) {
		if (!(r >= 0.0) || !(outer > 0.0) || r >= outer) {
			return 0.0;
		}
		eye = Math.max(1.0, eye);
		rmw = Math.max(eye + 1.0, rmw);
		double share;
		if (r < eye) {
			double q = r / eye;
			share = CALM * q * q;
		} else if (r < rmw) {
			double q = (r - eye) / (rmw - eye);
			share = CALM + (1.0 - CALM) * Math.sin(q * Math.PI / 2.0);
		} else {
			share = Math.sqrt(rmw / r);
		}
		return share * Math.min(1.0, (outer - r) / (outer * 0.2));
	}

	/**
	 * How much rain band lies over a point (dx, dz) from its centre at an age: 1 under the eyewall, 1 in the heart
	 * of a spiral band and next to nothing between the bands, nothing in the eye. Three bands wind in to the eyewall
	 * the way the wind turns, and the whole pattern turns slowly with it, so that the bands sweep over the land.
	 */
	public static double band(double dx, double dz, double eye, double rmw, double outer, double age) {
		double r = Math.sqrt(dx * dx + dz * dz);
		eye = Math.max(1.0, eye);
		rmw = Math.max(eye + 1.0, rmw);
		double wall = rmw - eye;
		if (!(r < outer) || r < eye * 0.8) {
			return 0.0;
		}
		// The eyewall: a closed ring of storm, its inner edge sharp.
		double ring;
		if (r < eye) {
			ring = (r - eye * 0.8) / (eye * 0.2);
		} else if (r < rmw + wall) {
			ring = 1.0;
		} else {
			ring = Math.max(0.0, 1.0 - (r - rmw - wall) / wall);
		}
		if (r < rmw + wall) {
			return ring;
		}
		double theta = Math.atan2(dz, dx);
		double phase = ARMS * (theta + age * BAND_TURN) - SPIRAL * Math.log(r / rmw);
		double spiral = 0.5 + 0.5 * Math.cos(phase);
		// Narrow bands, more broken toward its edge.
		spiral = spiral * spiral * spiral * (1.0 - 0.4 * r / outer);
		return Math.max(ring, spiral);
	}

	/** How hard the wind gusts at (dx, dz) from its centre at an age, against its usual: from about 0.7 in a lull to 1 in a gust. */
	public static double gust(double age, double dx, double dz) {
		return 0.86 + 0.1 * Math.sin(age * 0.045 + (dx + dz) * 0.013) + 0.06 * Math.sin(age * 0.17 + dx * 0.041 - dz * 0.029);
	}

	/**
	 * The wind (m/s, a horizontal vector) at (dx, dz) from the centre of a hurricane with an eye of radius `eye`, its
	 * strongest winds at `rmw`, reaching `outer` blocks, its peak wind `peak` (m/s) now, at an age, moving at
	 * (moveX, moveZ) m/s. It turns counterclockwise seen from above, spiralling in toward the eyewall, gustier in the
	 * rain bands, and the storm's own motion adds to it on the right of its track and takes from it on the left.
	 */
	public static Vec3 wind(double dx, double dz, double eye, double rmw, double outer, double peak, double age, double moveX, double moveZ) {
		double r = Math.sqrt(dx * dx + dz * dz);
		double share = profile(r, eye, rmw, outer);
		if (!(share > 0.0) || !(peak > 0.0) || !(r > 1.0E-6)) {
			return Vec3.ZERO;
		}
		double speed = peak * share * (gust(age, dx, dz) + 0.25 * band(dx, dz, eye, rmw, outer, age));
		double inflow = r < eye ? 0.0 : INFLOW * Math.min(1.0, (r - eye) / Math.max(1.0, rmw - eye));
		double c = Math.cos(inflow);
		double s = Math.sin(inflow);
		// Round (counterclockwise from above: Minecraft's north is -z) and in.
		double tx = dz / r;
		double tz = -dx / r;
		double ix = -dx / r;
		double iz = -dz / r;
		double carry = Math.min(1.0, share * 2.0);
		double wx = (tx * c + ix * s) * speed + moveX * carry;
		double wz = (tz * c + iz * s) * speed + moveZ * carry;
		return Double.isFinite(wx) && Double.isFinite(wz) ? new Vec3(wx, 0.0, wz) : Vec3.ZERO;
	}

	/** Its motion along its track, in m/s. */
	private double moveX() {
		return Math.cos(heading) * speed * 20.0;
	}

	private double moveZ() {
		return Math.sin(heading) * speed * 20.0;
	}

	private Vec3 windAt(double px, double pz) {
		return wind(px - x, pz - z, eye, rmw, outer, peakNow, age, moveX(), moveZ());
	}

	// --- for the tests ------------------------------------------------------------------------

	/** Its category on the Saffir-Simpson scale. */
	public int category() {
		return category;
	}

	/** The radius of its eye, in blocks. */
	public double eye() {
		return eye;
	}

	/** How far from its centre its winds are strongest (the eye and the eyewall), in blocks. */
	public double eyewall() {
		return rmw;
	}

	/** Its peak wind now, in metres a second: naught as it forms. */
	public double peakWind() {
		return peakNow;
	}

	/** The wind at a place now, in metres a second (a horizontal vector). */
	public Vec3 windAt(Vec3 at) {
		return windAt(at.x, at.z);
	}

	/** Whether something is in its calm eye. */
	public boolean inEye(Entity entity) {
		return distance2d(entity, x, z) < eye;
	}

	/** Where it is steering for, until it has gone by (null then, or if it was never aimed). */
	public Vec3 target() {
		return target;
	}

	/** Whether its centre has come ashore from the sea. */
	public boolean madeLandfall() {
		return landfall;
	}

	public int leavesStripped() {
		return leavesStripped;
	}

	public int plantsFlattened() {
		return plantsFlattened;
	}

	public int windowsBroken() {
		return windowsBroken;
	}

	/** Blocks of fences, doors, wool, signs and wooden walls and roofs it has torn away so far. */
	public int buildingTorn() {
		return buildingTorn;
	}

	public int treesFelled() {
		return treesFelled;
	}

	public int lightningStrikes() {
		return strikes;
	}

	public int tornadoesSpawned() {
		return tornadoes;
	}

	/** How many times a gust has knocked something off its feet and thrown it. */
	public int flings() {
		return flings;
	}

	/** How many of the blocks it tore loose are in the air now. */
	public int debrisAloft() {
		return debris.size();
	}

	// --- each tick ----------------------------------------------------------------------------

	@Override
	protected void tick() {
		move();
		float strength = strength();
		peakNow = peakWind * strength;
		if (age == 1) {
			// Its first warning waits for the first tick: by then it has been aimed, and knows which way it is going.
			announce();
		}
		if (age % 40 == 0) {
			surface();
		}
		if (age % 400 == 0 && strength > 0.2F) {
			weather();
		}
		debris.removeIf(block -> !block.isAlive());
		players(strength);
		if (age % 10 == 0) {
			gather();
		}
		blowThings();
		if (griefing() && strength > 0.15F) {
			damageLand(strength);
		}
		storms(strength);
		if (age % 300 == 150) {
			spinUp();
		}
		if (age % 200 == 0) {
			flung.values().removeIf(until -> until < age);
		}
		if (natural && age % 200 == 0 && playersWithin(outer + 1200.0).isEmpty()) {
			end();
		}
	}

	@Override
	protected void finish() {
		nearby.clear();
		debris.clear();
		flung.clear();
		kinds.clear();
	}

	/** Along its track: slowly, bending gently, steering for where it was aimed until it has gone by. */
	private void move() {
		turn += random.nextGaussian() * 0.00005;
		turn = Math.max(-0.0008, Math.min(0.0008, turn)) * 0.995;
		heading += turn;
		if (target != null) {
			double dx = target.x - x;
			double dz = target.z - z;
			if (dx * dx + dz * dz < 30.0 * 30.0) {
				// Gone over: on it goes the way it was going.
				target = null;
			} else {
				double off = Math.atan2(dz, dx) - heading;
				off = Math.atan2(Math.sin(off), Math.cos(off));
				heading += Math.max(-0.002, Math.min(0.002, off));
			}
		}
		x += Math.cos(heading) * speed;
		z += Math.sin(heading) * speed;
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		if (loaded(bx, bz)) {
			double floor = Math.max(level.getSeaLevel(), ground(bx, bz) + 1);
			y += (floor - y) * 0.05;
		}
	}

	/** Whether its centre is over the sea, from the lie of the land there (loaded or not); coming ashore is its landfall. */
	private void surface() {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		Holder<Biome> biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock((int) Math.floor(y)), QuartPos.fromBlock(bz));
		boolean sea = biome.is(BiomeTags.IS_OCEAN);
		if (sea) {
			seenSea = true;
		} else if (overWater && seenSea && !landfall && strength() > 0.3F) {
			landfall = true;
			Component moving = compass(Math.cos(heading), Math.sin(heading));
			warn(outer + 400.0, player -> Component.translatable("naturesmod.warning.hurricane_landfall", category, (int) distance2d(player, x, z),
					compass(player.getX(), player.getZ(), x, z), moving));
		}
		overWater = sea;
	}

	private void announce() {
		Component moving = compass(Math.cos(heading), Math.sin(heading));
		warn(outer + 600.0, player -> Component.translatable("naturesmod.warning.hurricane", category, (int) distance2d(player, x, z),
				compass(player.getX(), player.getZ(), x, z), moving));
	}

	/** A player its eyewall is almost on is told to get under a roof, once; one in its eye, that the calm won't last. */
	private void warnNear(ServerPlayer player, double r, float strength) {
		if (strength < 0.5F || !NaturesConfig.get().disasterWarnings) {
			return;
		}
		double edge = rmw + wall;
		if (r > edge && r < edge + 140.0 && warnedWall.add(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("naturesmod.warning.hurricane_eyewall", (int) (r - edge),
					compass(player.getX(), player.getZ(), x, z)).withStyle(ChatFormatting.GOLD));
		} else if (r < eye * 0.9 && warnedEye.add(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("naturesmod.warning.hurricane_eye").withStyle(ChatFormatting.GOLD));
		}
	}

	// --- the wind on what is out in it --------------------------------------------------------

	/** Whether something this far from the centre is in the eyewall, where the worst of it is. */
	private boolean inEyewall(double r) {
		return r > eye * 0.9 && r < rmw + wall;
	}

	/**
	 * The players out in it, every tick: the wind on them, the gusts that knock them down in the eyewall and what
	 * flies there. Between the rain bands, in the lulls between gusts, nothing is sent and a player is free.
	 */
	private void players(float strength) {
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			double dx = player.getX() - x;
			double dz = player.getZ() - z;
			double r = Math.sqrt(dx * dx + dz * dz);
			if (!(r < outer)) {
				continue;
			}
			warnNear(player, r, strength);
			if (immune(player)) {
				continue;
			}
			Vec3 wind = windAt(player.getX(), player.getZ());
			double w = wind.horizontalDistance();
			if (w < FELT || sheltered(player)) {
				continue;
			}
			boolean eyewall = inEyewall(r);
			if (eyewall && age % 20 == 0) {
				batter(player, w);
			}
			if (player.isPassenger() || player.isInWater()) {
				// The mount (or the water) takes the wind.
				continue;
			}
			boolean steady = eyewall || band(dx, dz, eye, rmw, outer, age) > 0.4 || gust(age, dx, dz) > 0.9;
			if (!steady && flungFor(player) <= 0) {
				continue;
			}
			Vec3 air = wind.scale(SCALE);
			if (eyewall && player.onGround() && knocksDown(player, w)) {
				fling(player, air, w);
				continue;
			}
			blowPlayer(player, air, w);
		}
	}

	/**
	 * The wind on a player: its game pulls it toward the speed of the air, from however fast it is really going.
	 * Walking into it is a struggle (in the eyewall of the strongest, hopeless), standing in it you are pushed along,
	 * and off the ground you are carried off with it. Crouching braces against it.
	 */
	private void blowPlayer(ServerPlayer player, Vec3 air, double w) {
		double pull = Math.min(0.18, 0.02 + 0.0018 * w);
		if (!player.onGround()) {
			pull *= 0.75;
		}
		if (player.isShiftKeyDown()) {
			pull *= 0.6;
		}
		blow(player, air, pull, 0.0, 2.0);
	}

	/** Whether a gust in the eyewall knocks something off its feet: from category 3 in the strongest gusts, often in a category 5. */
	private boolean knocksDown(Entity entity, double w) {
		if (category < 3 || w < 58.0 || flungFor(entity) > 0) {
			return false;
		}
		double chance = (w - 55.0) / 1500.0;
		if (entity.isShiftKeyDown()) {
			chance *= 0.35;
		}
		return random.nextDouble() < chance;
	}

	/** Knocked off its feet and thrown downwind, a few blocks up, to come down a dozen or more blocks off. */
	private void fling(Entity entity, Vec3 air, double w) {
		double k = Math.max(0.0, Math.min(1.0, (w - 55.0) / 35.0));
		double h = air.horizontalDistance();
		double ux = h > 1.0E-4 ? air.x / h : 0.0;
		double uz = h > 1.0E-4 ? air.z / h : 0.0;
		double out = 0.75 + 0.6 * k;
		setVelocity(entity, new Vec3(ux * out, 0.45 + 0.35 * k + random.nextDouble() * 0.1, uz * out));
		flung.put(entity.getUUID(), age + 40);
		flings++;
	}

	/** Ticks left for which something a gust threw is let fly before it can be knocked down again. */
	private int flungFor(Entity entity) {
		Integer until = flung.get(entity.getUUID());
		return until == null ? 0 : until - age;
	}

	/** What flies in the eyewall wounds what is out in it: from category 3, now and then, harder the stronger it is. */
	private void batter(LivingEntity entity, double w) {
		if (category < 3 || w < 45.0) {
			return;
		}
		if (random.nextDouble() < 0.3 + 0.1 * category) {
			hurt(entity, DisasterDamage.HURRICANE, 0.5F * (category - 2));
		}
	}

	/**
	 * Looks for what is in its wind near the players: animals first, then dropped things and loose blocks, so many at
	 * most. Not what hangs on walls (pushed, a painting or a frame falls off) nor anything else fixed in place.
	 */
	private void gather() {
		nearby.clear();
		Set<Entity> found = new HashSet<>();
		int looked = 0;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !(distance2d(player, x, z) < outer)) {
				continue;
			}
			if (++looked > 6 || nearby.size() >= MAX_NEARBY) {
				break;
			}
			AABB box = new AABB(player.getX() - 28.0, player.getY() - 12.0, player.getZ() - 28.0, player.getX() + 28.0, player.getY() + 20.0,
					player.getZ() + 28.0);
			List<Entity> list = level.getEntitiesOfClass(Entity.class, box, e -> e.isAlive()
					&& (e instanceof ItemEntity || e instanceof FallingBlockEntity || e instanceof LivingEntity && !(e instanceof Player)));
			for (Entity entity : list) {
				if (entity instanceof LivingEntity && nearby.size() < MAX_NEARBY && found.add(entity)) {
					nearby.add(entity);
				}
			}
			for (Entity entity : list) {
				if (!(entity instanceof LivingEntity) && nearby.size() < MAX_NEARBY && found.add(entity)) {
					nearby.add(entity);
				}
			}
		}
		// What it has torn loose flies on with it, wherever it is.
		for (FallingBlockEntity block : debris) {
			if (found.add(block)) {
				nearby.add(block);
			}
		}
	}

	/**
	 * The wind on everything else out in it: dropped things skip and fly away on it, torn-off blocks are carried,
	 * animals are pushed along, and in the eyewall the small ones are knocked down and thrown and all are battered.
	 */
	private void blowThings() {
		for (Entity entity : nearby) {
			if (!entity.isAlive() || entity.level() != level || immune(entity) || entity.isPassenger()) {
				continue;
			}
			double dx = entity.getX() - x;
			double dz = entity.getZ() - z;
			double r = Math.sqrt(dx * dx + dz * dz);
			if (!(r < outer)) {
				continue;
			}
			Vec3 wind = windAt(entity.getX(), entity.getZ());
			double w = wind.horizontalDistance();
			if (w < FELT || sheltered(entity)) {
				continue;
			}
			Vec3 air = wind.scale(SCALE);
			Vec3 velocity = entity.getDeltaMovement();
			if (entity instanceof ItemEntity) {
				double hop = entity.onGround() ? 0.04 + 0.002 * w : 0.0;
				shove(entity, (air.x - velocity.x) * 0.2, hop, (air.z - velocity.z) * 0.2);
				continue;
			}
			if (entity instanceof FallingBlockEntity) {
				shove(entity, (air.x - velocity.x) * 0.08, 0.0, (air.z - velocity.z) * 0.08);
				continue;
			}
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}
			double size = entity.getBbWidth() * entity.getBbHeight();
			if (inEyewall(r)) {
				if (age % 20 == 0) {
					batter(living, w);
				}
				if (entity.onGround() && size <= 4.0 && knocksDown(entity, w)) {
					fling(entity, air, w);
					continue;
				}
			}
			double response = 0.09 / Math.max(1.0, size);
			if (entity.onGround() && w < 40.0) {
				// Feet on the ground hold against a moderate wind.
				response *= 0.5;
			}
			shove(entity, (air.x - velocity.x) * response, 0.0, (air.z - velocity.z) * response);
		}
	}

	private static Vec3 limit(Vec3 v, double max) {
		double length = v.length();
		if (!(length < 1.0E6)) {
			return Vec3.ZERO;
		}
		return length > max ? v.scale(max / length) : v;
	}

	// --- lightning and tornadoes ---------------------------------------------------------------

	/** Lightning out of its bands and its eyewall, where someone is there to see it: every few seconds, more in the strongest. */
	private void storms(float strength) {
		if (random.nextDouble() >= (0.004 + 0.003 * category) * strength) {
			return;
		}
		List<ServerPlayer> inside = playersWithin(outer);
		if (inside.isEmpty()) {
			return;
		}
		ServerPlayer player = inside.get(random.nextInt(inside.size()));
		for (int i = 0; i < 4; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 16.0 + random.nextDouble() * 110.0;
			double lx = player.getX() + Math.cos(angle) * distance;
			double lz = player.getZ() + Math.sin(angle) * distance;
			double b = band(lx - x, lz - z, eye, rmw, outer, age);
			if (b > 0.45 && random.nextDouble() < b) {
				lightning(lx, lz);
				strikes++;
				return;
			}
		}
	}

	/**
	 * Now and then, from category 2, a short-lived weak tornado (EF0 to EF2) spins up in a rain band near a player,
	 * one at a time, a couple at most.
	 */
	private void spinUp() {
		if (tornadoesLeft <= 0 || tornado != null && !tornado.isDone() || strength() < 0.6F || random.nextInt(3) != 0) {
			return;
		}
		List<ServerPlayer> inside = playersWithin(outer * 0.85);
		if (inside.isEmpty()) {
			return;
		}
		ServerPlayer player = inside.get(random.nextInt(inside.size()));
		for (int i = 0; i < 4; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 60.0 + random.nextDouble() * 100.0;
			double tx = player.getX() + Math.cos(angle) * distance;
			double tz = player.getZ() + Math.sin(angle) * distance;
			double dx = tx - x;
			double dz = tz - z;
			double r = Math.sqrt(dx * dx + dz * dz);
			if (!(r > rmw + 2.0 * wall && r < outer * 0.85) || band(dx, dz, eye, rmw, outer, age) < 0.45) {
				continue;
			}
			int bx = (int) Math.floor(tx);
			int bz = (int) Math.floor(tz);
			if (!loaded(bx, bz)) {
				continue;
			}
			int top = ground(bx, bz);
			if (top <= level.getMinY() || !level.getFluidState(new BlockPos(bx, top, bz)).isEmpty()) {
				// Not over water.
				continue;
			}
			float rating = category >= 4 ? 1 + random.nextInt(2) : random.nextInt(2);
			Disaster spun = Disasters.start(DisasterType.TORNADO, level, new Vec3(tx, top + 1, tz), rating, natural);
			spun.setDuration(500 + random.nextInt(500));
			if (spun instanceof Tornado twister) {
				twister.aim(player.position());
			}
			tornado = spun;
			tornadoesLeft--;
			tornadoes++;
			return;
		}
	}

	// --- what it does to the land --------------------------------------------------------------

	/**
	 * Tears at the land: so many columns a tick about each player in its reach (where it is seen), and a few along
	 * its eyewall wherever that is (leaves and windows only, so that it leaves a swath behind it), so many blocks
	 * changed in all. Only a few go with the crack and the shower of bits of a block broken (each of those is a burst
	 * of particles for every player near); the rest go quietly, the rain hiding it.
	 */
	private void damageLand(float strength) {
		columnsLeft = MAX_COLUMNS;
		changesLeft = MAX_CHANGES;
		// A crack and a dropped crop now and then, not every tick: each crack is a burst of bits of block for every player near.
		effectsLeft = age % 4 == 0 ? 1 : 0;
		dropsLeft = age % 4 == 0 ? 1 : 0;
		launchesLeft = category >= 4 ? 1 : 0;
		int per = Math.round((12 + 10 * category) * strength);
		for (ServerPlayer player : level.players()) {
			if (columnsLeft <= 0 || changesLeft <= 0) {
				break;
			}
			if (player.isSpectator() || !(distance2d(player, x, z) < outer + NEAR)) {
				continue;
			}
			for (int i = 0; i < per && columnsLeft > 0 && changesLeft > 0; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				double r = NEAR * Math.sqrt(random.nextDouble());
				strike(player.getX() + Math.cos(angle) * r, player.getZ() + Math.sin(angle) * r, true);
			}
		}
		for (int i = 0; i < 6 && columnsLeft > 0 && changesLeft > 0; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = eye + random.nextDouble() * wall * 2.0;
			strike(x + Math.cos(angle) * r, z + Math.sin(angle) * r, false);
		}
	}

	/** The wind on one column of land, if it blows hard enough there to do anything. */
	private void strike(double px, double pz, boolean near) {
		columnsLeft--;
		int bx = (int) Math.floor(px);
		int bz = (int) Math.floor(pz);
		if (!workable(bx, bz)) {
			return;
		}
		Vec3 wind = windAt(px, pz);
		double w = wind.horizontalDistance();
		if (!(w >= PLANT - 2.0)) {
			return;
		}
		column(bx, bz, w, new Vec3(wind.x / w, 0.0, wind.z / w), near);
	}

	/**
	 * Down a column from its top, as far as the land itself: what the wind gets at (open to the air above or beside
	 * it) goes if the gust is strong enough for it. Under a roof, or the top of a wall, only the windows are got at.
	 * A trunk is brought down whole, now and then, where someone is near to see it. Two blocks a column at most.
	 */
	private void column(int bx, int bz, double w, Vec3 dir, boolean near) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
		if (top >= level.getMaxY() || top <= level.getMinY() + 1) {
			return;
		}
		int bottom = Math.max(level.getMinY() + 1, top - DEPTH);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean covered = false;
		int changed = 0;
		for (int by = top; by >= bottom && changed < 2 && changesLeft > 0; by--) {
			pos.set(bx, by, bz);
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (!state.getFluidState().isEmpty()) {
				return;
			}
			Kind kind = kind(state, pos);
			if (kind == Kind.GROUND) {
				return;
			}
			double gust = w * (0.85 + 0.3 * random.nextDouble());
			if (kind == Kind.LOG) {
				int trees = TREES[category];
				if (near && !covered && trees > 0 && gust >= TREE && random.nextDouble() < 0.3
						&& fellTree(pos.immutable(), dir, random.nextInt(3), trees)) {
					treesFelled++;
					changesLeft -= 8;
				}
				return;
			}
			boolean torn;
			if (kind == Kind.GLASS) {
				torn = chance(gust, GLASS, 0.5) && exposed(bx, by, bz) && shatter(pos.immutable());
			} else if (covered) {
				torn = false;
			} else if (kind == Kind.LEAVES) {
				torn = chance(gust, LEAF, 0.6) && exposed(bx, by, bz) && strip(pos);
			} else if (kind == Kind.PLANT) {
				torn = chance(gust, PLANT, 0.5) && flatten(pos.immutable(), state);
			} else if (kind == Kind.FITTING) {
				torn = near && category >= 3 && chance(gust, FITTING, 0.4) && exposed(bx, by, bz) && tear(pos.immutable(), state, dir, gust, false);
			} else if (kind == Kind.WOOD) {
				// A roof, with the wind getting under it, lifts off more easily.
				double holds = level.getBlockState(probe.set(bx, by - 1, bz)).isAir() ? WOOD * 0.85 : WOOD;
				torn = near && category >= 3 && chance(gust, holds, 0.25) && exposed(bx, by, bz) && tear(pos.immutable(), state, dir, gust, true);
			} else {
				torn = false;
			}
			if (torn) {
				changed++;
				changesLeft--;
			} else if (kind != Kind.LEAVES && kind != Kind.PLANT && !state.getCollisionShape(level, pos).isEmpty()) {
				covered = true;
			}
		}
	}

	/** Whether a gust of this strength takes something that gives way at `threshold`: likelier the further over it is. */
	private boolean chance(double gust, double threshold, double most) {
		return gust >= threshold && random.nextDouble() < Math.min(1.0, (gust - threshold) / 30.0) * most;
	}

	/** Whether the wind gets at a block: open air above it or beside it. */
	private boolean exposed(int bx, int by, int bz) {
		return air(bx, by + 1, bz) || air(bx + 1, by, bz) || air(bx - 1, by, bz) || air(bx, by, bz + 1) || air(bx, by, bz - 1);
	}

	private boolean air(int bx, int by, int bz) {
		return loaded(bx, bz) && level.getBlockState(probe.set(bx, by, bz)).isAir();
	}

	/** Leaves torn off: quietly, their neighbours left be. */
	private boolean strip(BlockPos pos) {
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		leavesStripped++;
		return true;
	}

	/** A plant flattened: a crop now and then with what it bore spilled to blow away, the top of a tall one going with it. */
	private boolean flatten(BlockPos pos, BlockState state) {
		if (state.is(BlockTags.CROPS) && dropsLeft > 0) {
			dropsLeft--;
			level.destroyBlock(pos, true);
		} else {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		plantsFlattened++;
		return true;
	}

	/** A window blown in: a few with the crash of breaking glass, the rest quietly. */
	private boolean shatter(BlockPos pos) {
		if (effectsLeft > 0) {
			effectsLeft--;
			level.destroyBlock(pos, false);
		} else {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		windowsBroken++;
		return true;
	}

	/** A fitting or a plank torn away: now and then thrown whole on the wind, now and then left to blow away, mostly shredded. */
	private boolean tear(BlockPos pos, BlockState state, Vec3 dir, double gust, boolean wood) {
		if (state.hasBlockEntity()) {
			// A sign or a banner: it goes whole, to be found again.
			if (dropsLeft <= 0) {
				return false;
			}
			dropsLeft--;
			level.destroyBlock(pos, true);
		} else if (wood && launchesLeft > 0 && debris.size() < debrisCap && random.nextInt(3) == 0 && state.isCollisionShapeFullBlock(level, pos)) {
			launch(pos, state, dir, gust);
		} else if (effectsLeft > 0 && random.nextBoolean()) {
			effectsLeft--;
			boolean drop = dropsLeft > 0 && random.nextInt(4) == 0;
			if (drop) {
				dropsLeft--;
			}
			level.destroyBlock(pos, drop);
		} else {
			// What held up a torch or a door must tell it.
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		buildingTorn++;
		return true;
	}

	/** Lifts a block whole into the wind, to come down somewhere downwind (on whatever is there). */
	private void launch(BlockPos pos, BlockState state, Vec3 dir, double gust) {
		FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
		double fling = Math.min(1.4, gust * SCALE);
		setVelocity(block, new Vec3(dir.x * fling, 0.3 + random.nextDouble() * 0.4, dir.z * fling));
		block.setHurtsEntities(1.5F, 12);
		debris.add(block);
		nearby.add(block);
		launchesLeft--;
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

	/** What a block is to the wind (worked out once for each kind of block). */
	private Kind kindOf(BlockState state, BlockPos pos) {
		if (state.is(BlockTags.LEAVES)) {
			return Kind.LEAVES;
		}
		if (TreeFeller.isLog(state)) {
			return Kind.LOG;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F || land(state)) {
			return Kind.GROUND;
		}
		Block block = state.getBlock();
		if (state.is(BlockTags.CROPS) || state.is(BlockTags.FLOWERS) || state.is(WorldGenUtil.SAPLINGS) || block instanceof VegetationBlock
				|| state.canBeReplaced() && !state.is(Blocks.SNOW)) {
			return state.hasBlockEntity() ? Kind.OTHER : Kind.PLANT;
		}
		String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (name.contains("glass")) {
			return state.hasBlockEntity() ? Kind.OTHER : Kind.GLASS;
		}
		boolean light = hardness <= 3.0F && block.getExplosionResistance() <= 3.0F;
		if (light && named(name, FITTINGS) && (!state.hasBlockEntity() || name.contains("sign") || name.contains("banner"))) {
			return Kind.FITTING;
		}
		if (!state.hasBlockEntity() && (state.is(BlockTags.PLANKS) || light && state.ignitedByLava() && (name.endsWith("_slab") || name.endsWith("_stairs")))) {
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
}
