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

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A haboob: a sandstorm driven by the outflow of a distant storm. A wall of dust two hundred blocks
 * high and a couple of thousand wide rolls across the land at the speed of a galloping horse, and
 * behind it the air is so thick with sand you can barely see a few blocks: brown, dark, roaring. The
 * dust behind the wall runs deep and keeps spreading upwind as the wall runs on, so that what it
 * overtakes stays in it for minutes, until the back of the dust goes by or the wind dies and it
 * settles. The wind comes in gusts that sweep downwind in waves: in a gust walking into it is a
 * struggle and a jump carries you off with it, in the lulls between them you are free; the lee of a
 * wall or a rock takes most of it off you, and a roof all of it. Loose things blow away, animals are
 * pushed along. At its worst the sand flays bare skin: out in it without a helmet you are hurt now and
 * then. Sand drifts against whatever breaks the wind, fills the hollows and buries the small plants of
 * the desert; over green land (a dust storm rather than a sandstorm) much less of it is left behind.
 * Ahead of the wall, on the hot ground, dust devils spin up and wander, some of them strong enough to
 * lift a player a few blocks and throw them out.
 *
 * <p>The work is bounded every tick, however big the storm: only the players and what is near them are
 * looked at (the things other than players every ten ticks, so many at most), so many columns are
 * looked at for sand and so many blocks laid, and no more in all than so many; the dust devils are few
 * and only stir things up where someone is near.
 */
public final class Sandstorm extends Disaster {
	/** Where the wall starts, in blocks along the wind from the middle of the storm (upwind of it). */
	private static final double START = -320.0;
	/** How long the dust takes to settle once the wind dies, in ticks. */
	private static final int SETTLING = 800;
	private static final int MAX_DEVILS = 8;
	/** At most this many things other than players in the dust are pushed about each tick. */
	private static final int MAX_NEARBY = 96;
	/** The numbers sent for each dust devil (see {@link #extra()}). */
	private static final int DEVIL_FLOATS = 6;

	private final Vec3 wind;
	/** How fast the wall comes on, and how fast the back of the dust follows it (blocks a tick). */
	private final double speed;
	private final double tailSpeed;
	/** How deep the dust is behind the wall at first. */
	private final double startDepth;
	private final double width;
	/** How tall the wall stands over the land. */
	private final double height;
	/** How fast the air itself moves in it (blocks a tick): what it blows things toward. */
	private final double windSpeed;
	private final double visibility;
	/** The country it blows over: 0 sand, 1 red sand, 2 green land (a dust storm, leaving less sand behind). */
	private final int tint;
	private final List<Devil> devils = new ArrayList<>();
	/** Where each player was last tick: how fast one is going is how far it went since. */
	private final Map<UUID, Vec3> lastSeen = new HashMap<>();
	/** What a dust devil has just thrown out, and until when (its age) it is let be. */
	private final Map<UUID, Integer> flung = new HashMap<>();
	/** Players already told the wall is almost on them. */
	private final Set<UUID> warned = new HashSet<>();
	/** Everything other than players in the dust near a player, as last looked for. */
	private final List<Entity> nearby = new ArrayList<>();
	private int nextDevil = 1;
	private int sandLaid;

	/** A dust devil: a little whirlwind on the hot ground ahead of the wall. */
	private static final class Devil {
		final int id;
		double x;
		double y;
		double z;
		double heading;
		/** How fast it moves over the ground (blocks a tick): the air it spins carries along with it. */
		double vx;
		double vz;
		final double height;
		final double core;
		final int life;
		int age;

		Devil(int id, double x, double y, double z, double heading, double height, double core, int life) {
			this.id = id;
			this.x = x;
			this.y = y;
			this.z = z;
			this.heading = heading;
			this.height = height;
			this.core = core;
			this.life = life;
		}

		/** 0 as it spins up, 1 grown, back to 0 as it dies down. */
		double grown() {
			return Math.max(0.0, Math.min(1.0, Math.min(age / 60.0, (life - age) / 60.0)));
		}
	}

	Sandstorm(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.SANDSTORM, level, at, intensity, natural, 0);
		double angle = random.nextDouble() * Math.PI * 2.0;
		wind = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		double s = this.intensity;
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		y = ground(bx, bz) + 1;
		tint = country(bx, bz);
		boolean sandy = tint < 2;
		// The wall comes on at 40 to 60 km/h, as fast as a horse gallops: a player sees it coming for some twenty seconds.
		speed = 0.5 + 0.07 * s;
		tailSpeed = speed * 0.3;
		startDepth = 260.0 + 40.0 * s;
		width = 1300.0 + 240.0 * s;
		height = (130.0 + 24.0 * s) * (sandy ? 1.0 : 0.8);
		windSpeed = 0.16 + 0.035 * s;
		visibility = (31.0 - 5.0 * s) * (sandy ? 1.0 : 1.3);
		duration = (int) (2400 + 600 * s);
	}

	// --- where the dust is ------------------------------------------------------------------------

	/**
	 * How deep inside the dust a point is (0 outside, 1 well inside), from where it lies along the wind and across it
	 * from the middle of the storm: the wall is sharp, the back of the dust and its sides thin out over a long way.
	 * Shared by the server and its clients, which draw it.
	 */
	public static double dustDepth(double along, double across, double front, double body, double width) {
		double tail = front - body;
		double side = width / 2.0 - Math.abs(across);
		if (!(along <= front && along >= tail && side >= 0.0)) {
			return 0.0;
		}
		double edge = Math.min(Math.min((front - along) / 30.0, (along - tail) / 160.0), side / 120.0);
		return edge >= 0.0 ? Math.min(1.0, edge) : 0.0;
	}

	/**
	 * How hard the wind gusts at a point `along` blocks downwind of the middle of the storm, at an age: 0 in a lull, 1
	 * at the height of a gust. The gusts sweep downwind in waves, a few seconds apart, stronger and weaker by turns over
	 * half a minute or so; the server and its clients work them out alike.
	 */
	public static double gust(double age, double along) {
		double fast = Math.sin(age * 0.06 - along * 0.015);
		double slow = Math.sin(age * 0.013 - along * 0.004 + 1.3);
		double g = 0.42 + 0.32 * fast + 0.26 * slow;
		return Math.max(0.0, Math.min(1.0, (g - 0.12) / 0.55));
	}

	/** Where the wall is, in blocks along the wind from the middle of the storm. */
	public double front() {
		return START + speed * age;
	}

	/** How deep the dust behind the wall is now, in blocks along the wind. */
	public double body() {
		return startDepth + (speed - tailSpeed) * age;
	}

	/** How deep inside the dust a point is: 0 outside, 1 well inside. */
	public double inside(double px, double pz) {
		double dx = px - x;
		double dz = pz - z;
		return dustDepth(dx * wind.x + dz * wind.z, -dx * wind.z + dz * wind.x, front(), body(), width);
	}

	// --- for the tests ----------------------------------------------------------------------------

	/** How wide its front is, in blocks. */
	public double width() {
		return width;
	}

	/** How tall its wall stands, in blocks. */
	public double wallHeight() {
		return height;
	}

	/** How far one can see in the thick of it, in blocks. */
	public double visibility() {
		return visibility;
	}

	/** How many dust devils are spinning ahead of it now. */
	public int devilCount() {
		return devils.size();
	}

	/** How many blocks of sand it has laid down so far. */
	public int sandLaid() {
		return sandLaid;
	}

	// --- the storm as a whole ---------------------------------------------------------------------

	@Override
	public float radius() {
		return (float) Math.min(8000.0, width / 2.0 + body());
	}

	@Override
	public Vec3 direction() {
		return wind;
	}

	@Override
	public float strength() {
		float in = Math.min(1.0F, age / 60.0F);
		float out = Math.min(1.0F, (duration - age) / (float) SETTLING);
		return Math.max(0.0F, Math.min(in, out));
	}

	/**
	 * For its clients: the wall (where, how deep the dust behind it, how wide, how tall), how far one sees in it, how fast
	 * it comes on and its wind blows, the ground under its middle, the colour of its dust, then the dust devils, six
	 * numbers each (an id, where, how tall now, how wide the core).
	 */
	@Override
	public float[] extra() {
		float[] out = new float[10 + devils.size() * DEVIL_FLOATS];
		out[0] = (float) front();
		out[1] = (float) body();
		out[2] = (float) width;
		out[3] = (float) visibility;
		out[4] = (float) speed;
		out[5] = (float) y;
		out[6] = (float) height;
		out[7] = tint;
		out[8] = (float) windSpeed;
		out[9] = devils.size();
		for (int i = 0; i < devils.size(); i++) {
			Devil devil = devils.get(i);
			int k = 10 + i * DEVIL_FLOATS;
			out[k] = devil.id;
			out[k + 1] = (float) devil.x;
			out[k + 2] = (float) devil.y;
			out[k + 3] = (float) devil.z;
			out[k + 4] = (float) (devil.height * devil.grown());
			out[k + 5] = (float) devil.core;
		}
		return out;
	}

	@Override
	protected void begin() {
		warn(Math.min(4000.0, width / 2.0 + 700.0), player -> Component.translatable("naturesmod.warning.sandstorm", compass(-wind.x, -wind.z)));
		// Dust devils already dancing on the hot ground ahead of the wall.
		int count = 2 + random.nextInt(2) + (tint < 2 ? 1 : 0);
		double front = front();
		for (int i = 0; i < count; i++) {
			double along = front + 40.0 + random.nextDouble() * 260.0;
			double across = (random.nextDouble() - 0.5) * 360.0;
			spawnDevil(x + wind.x * along - wind.z * across, z + wind.z * along + wind.x * across);
		}
	}

	@Override
	protected void tick() {
		float strength = strength();
		double front = front();
		double body = body();
		List<ServerPlayer> inDust = new ArrayList<>();
		players(strength, front, body, inDust);
		if (age % 10 == 0) {
			gather(inDust);
		}
		blowThings(strength, front, body);
		if (age % 40 == 0) {
			raiseDevils(front);
		}
		devils();
		if (griefing() && strength > 0.2F) {
			drift(inDust, strength);
		}
		if (age % 100 == 0) {
			flung.values().removeIf(until -> until < age);
		}
		if (natural && age % 200 == 0 && playersWithin(width / 2.0 + body + 600.0).isEmpty()) {
			end();
		}
	}

	@Override
	protected void finish() {
		devils.clear();
		nearby.clear();
		lastSeen.clear();
		flung.clear();
	}

	/** What the land it starts over is mostly covered with: sand (0), red sand (1), or something green (2). */
	private int country(int bx, int bz) {
		int sand = 0;
		int red = 0;
		int other = 0;
		for (int i = 0; i < 5; i++) {
			int cx = bx + (i == 1 ? 24 : i == 2 ? -24 : 0);
			int cz = bz + (i == 3 ? 24 : i == 4 ? -24 : 0);
			if (!loaded(cx, cz)) {
				continue;
			}
			BlockState top = level.getBlockState(new BlockPos(cx, terrain(cx, cz), cz));
			if (top.is(Blocks.RED_SAND) || top.is(Blocks.RED_SANDSTONE) || top.is(BlockTags.TERRACOTTA)) {
				red++;
			} else if (top.is(BlockTags.SAND) || top.is(Blocks.SANDSTONE)) {
				sand++;
			} else {
				other++;
			}
		}
		if (other > sand + red) {
			return 2;
		}
		return red > sand ? 1 : 0;
	}

	// --- the players ------------------------------------------------------------------------------

	/** The players: the wind on those out in the dust, the dust devils on those caught in one, the sand, the warnings. */
	private void players(float strength, double front, double body, List<ServerPlayer> inDust) {
		Map<UUID, Vec3> seen = new HashMap<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || immune(player)) {
				continue;
			}
			Vec3 before = lastSeen.get(player.getUUID());
			seen.put(player.getUUID(), player.position());
			Vec3 moved = before == null ? Vec3.ZERO : player.position().subtract(before);
			if (moved.lengthSqr() > 9.0) {
				// Teleported, not blown.
				moved = Vec3.ZERO;
			}
			double px = player.getX() - x;
			double pz = player.getZ() - z;
			double along = px * wind.x + pz * wind.z;
			double across = -px * wind.z + pz * wind.x;
			warnNear(player, along - front, across);
			double in = dustDepth(along, across, front, body, width) * strength;
			if (in > 0.0) {
				inDust.add(player);
			}
			Devil devil = devilAt(player);
			if (in <= 0.0 && devil == null) {
				continue;
			}
			if (sheltered(player)) {
				continue;
			}
			double gust = gust(age, along);
			if (in > 0.0 && age % 40 == 0) {
				blast(player, in * gust);
			}
			if (player.isPassenger() || player.isInWater()) {
				// The mount (or the water) takes the wind.
				continue;
			}
			if (devil != null) {
				whirlPlayer(player, devil, moved);
			} else {
				blowPlayer(player, in * gust);
			}
		}
		lastSeen.clear();
		lastSeen.putAll(seen);
	}

	/**
	 * The wind on a player out in the dust: its game pulls it toward the speed of the air, from however fast it is
	 * really going. Walking into it is a struggle, standing in it you are pushed along, and off the ground (jumping,
	 * falling) you are carried off with it. In the lulls between the gusts nothing is sent at all, and the player is free.
	 */
	private void blowPlayer(ServerPlayer player, double force) {
		if (force < 0.04) {
			return;
		}
		force *= lee(player);
		boolean ground = player.onGround();
		double pull = (ground ? 0.08 + 0.02 * intensity : 0.05 + 0.012 * intensity) * Math.min(1.0, force);
		blow(player, new Vec3(wind.x * windSpeed, 0.0, wind.z * windSpeed), pull, 0.0, 1.5);
	}

	/** How much of the wind reaches a player: little in the lee of a wall or a rock (something solid just upwind). */
	private double lee(ServerPlayer player) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int ux = (int) Math.floor(player.getX() - wind.x * 1.2);
		int uz = (int) Math.floor(player.getZ() - wind.z * 1.2);
		int feet = (int) Math.floor(player.getY());
		for (int dy = 0; dy <= 1; dy++) {
			pos.set(ux, feet + dy, uz);
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return 0.3;
			}
		}
		return 1.0;
	}

	/** At its worst the sand flays bare skin: now and then a little harm to a player out in it with nothing on their head. */
	private void blast(ServerPlayer player, double force) {
		if (intensity < 3.5F || force < 0.3 || !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			return;
		}
		if (random.nextDouble() < 0.45 * force) {
			hurt(player, DisasterDamage.SANDSTORM, intensity >= 4.5F ? 1.5F : 1.0F);
		}
	}

	/** A player the wall is almost on is told what to do, once. */
	private void warnNear(ServerPlayer player, double ahead, double across) {
		if (ahead > 0.0 && ahead < 160.0 && Math.abs(across) < width / 2.0 && NaturesConfig.get().disasterWarnings && warned.add(player.getUUID())) {
			String key = intensity >= 3.5F ? "naturesmod.warning.sandstorm_blasting" : "naturesmod.warning.sandstorm_near";
			player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD));
		}
	}

	// --- everything else in the dust --------------------------------------------------------------

	/** Looks for what is in the dust near the players: dropped things and animals, so many at most. */
	private void gather(List<ServerPlayer> inDust) {
		nearby.clear();
		Set<Entity> found = new HashSet<>();
		int looked = 0;
		for (ServerPlayer player : inDust) {
			if (++looked > 6 || nearby.size() >= MAX_NEARBY) {
				break;
			}
			AABB box = new AABB(player.getX() - 32.0, player.getY() - 12.0, player.getZ() - 32.0, player.getX() + 32.0, player.getY() + 16.0,
					player.getZ() + 32.0);
			for (Entity entity : level.getEntitiesOfClass(Entity.class, box, Sandstorm::blown)) {
				if (nearby.size() >= MAX_NEARBY) {
					break;
				}
				if (found.add(entity)) {
					nearby.add(entity);
				}
			}
		}
	}

	/** What the wind takes besides the players (who are seen to on their own): dropped things and animals, not armor stands. */
	private static boolean blown(Entity entity) {
		return entity.isAlive() && (entity instanceof ItemEntity || entity instanceof LivingEntity && !(entity instanceof Player) && !(entity instanceof ArmorStand));
	}

	/** The wind on what is out in it: dropped things bounce and fly away on it, animals are pushed along. */
	private void blowThings(float strength, double front, double body) {
		if (nearby.isEmpty()) {
			return;
		}
		double airX = wind.x * windSpeed * 1.2;
		double airZ = wind.z * windSpeed * 1.2;
		for (Entity entity : nearby) {
			if (!entity.isAlive() || entity.level() != level || immune(entity)) {
				continue;
			}
			double px = entity.getX() - x;
			double pz = entity.getZ() - z;
			double along = px * wind.x + pz * wind.z;
			double force = dustDepth(along, -px * wind.z + pz * wind.x, front, body, width) * strength * gust(age, along);
			if (force < 0.04 || sheltered(entity)) {
				continue;
			}
			Vec3 velocity = entity.getDeltaMovement();
			if (entity instanceof ItemEntity) {
				double k = 0.12 * force;
				shove(entity, (airX - velocity.x) * k, entity.onGround() ? 0.1 * force : 0.0, (airZ - velocity.z) * k);
			} else {
				double k = 0.035 * force / Math.max(1.0, entity.getBbWidth() * entity.getBbHeight());
				shove(entity, (airX - velocity.x) * k, 0.0, (airZ - velocity.z) * k);
			}
		}
	}

	// --- the dust devils --------------------------------------------------------------------------

	private void spawnDevil(double dx, double dz) {
		int bx = (int) Math.floor(dx);
		int bz = (int) Math.floor(dz);
		if (devils.size() >= MAX_DEVILS || !loaded(bx, bz)) {
			return;
		}
		int top = ground(bx, bz);
		if (top <= level.getMinY() || !level.getFluidState(new BlockPos(bx, top, bz)).isEmpty()) {
			// Not over water.
			return;
		}
		double s = intensity;
		devils.add(new Devil(nextDevil++, dx, top + 1, dz, random.nextDouble() * Math.PI * 2.0, 22.0 + 5.0 * s + random.nextDouble() * 18.0,
				2.2 + 0.45 * s + random.nextDouble() * 1.2, 500 + random.nextInt(600)));
	}

	/** New dust devils spin up on the hot ground near the players the wall has yet to reach. */
	private void raiseDevils(double front) {
		int most = Math.min(MAX_DEVILS, 2 + Math.round(intensity) + (tint < 2 ? 1 : 0));
		if (devils.size() >= most || age > duration - SETTLING || random.nextInt(3) == 0) {
			return;
		}
		List<ServerPlayer> ahead = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			double px = player.getX() - x;
			double pz = player.getZ() - z;
			double gap = px * wind.x + pz * wind.z - front;
			if (gap > 40.0 && gap < 1000.0 && Math.abs(-px * wind.z + pz * wind.x) < width / 2.0 + 200.0) {
				ahead.add(player);
			}
		}
		if (ahead.isEmpty()) {
			return;
		}
		ServerPlayer player = ahead.get(random.nextInt(ahead.size()));
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = 24.0 + random.nextDouble() * 130.0;
		double dx = player.getX() + Math.cos(angle) * distance;
		double dz = player.getZ() + Math.sin(angle) * distance;
		if ((dx - x) * wind.x + (dz - z) * wind.z > front + 30.0) {
			spawnDevil(dx, dz);
		}
	}

	/** The dust devils wander ahead of the wall, drifting with the wind, until it overtakes them or they die down. */
	private void devils() {
		for (Iterator<Devil> it = devils.iterator(); it.hasNext(); ) {
			Devil devil = it.next();
			devil.age++;
			devil.heading += random.nextGaussian() * 0.08;
			devil.vx = wind.x * speed * 0.45 + Math.cos(devil.heading) * 0.12;
			devil.vz = wind.z * speed * 0.45 + Math.sin(devil.heading) * 0.12;
			devil.x += devil.vx;
			devil.z += devil.vz;
			int bx = (int) Math.floor(devil.x);
			int bz = (int) Math.floor(devil.z);
			double watcher = nearestPlayer(devil.x, devil.z);
			if (devil.age >= devil.life || watcher > 700.0 || !loaded(bx, bz) || inside(devil.x, devil.z) > 0.0) {
				it.remove();
				continue;
			}
			int top = ground(bx, bz);
			if (top <= level.getMinY() || !level.getFluidState(new BlockPos(bx, top, bz)).isEmpty()) {
				// Over water it dies.
				it.remove();
				continue;
			}
			devil.y = top + 1;
			if (watcher < 128.0) {
				whirl(devil);
			}
		}
	}

	private double nearestPlayer(double px, double pz) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator()) {
				best = Math.min(best, distance2d(player, px, pz));
			}
		}
		return best;
	}

	/** The dust devil a player is caught in, if any: close about its core and below its top. */
	private Devil devilAt(ServerPlayer player) {
		for (Devil devil : devils) {
			double grown = devil.grown();
			double h = player.getY() - devil.y;
			if (grown < 0.2 || h < -3.0 || h > devil.height * grown) {
				continue;
			}
			double dx = player.getX() - devil.x;
			double dz = player.getZ() - devil.z;
			double reach = devil.core * 2.6;
			if (dx * dx + dz * dz < reach * reach) {
				return devil;
			}
		}
		return null;
	}

	/** Ticks left for which something a dust devil threw out is let be. */
	private int flungFor(Entity entity) {
		Integer until = flung.get(entity.getUUID());
		return until == null ? 0 : until - age;
	}

	/** How high a dust devil lifts what it catches, in blocks above the ground: a few. */
	private double lift(Devil devil) {
		return (1.5 + 0.9 * intensity) * devil.grown();
	}

	/**
	 * A player caught in a dust devil: spun round it and, close to its core, lifted off the ground a few blocks, then
	 * thrown out of it to drop back down. As with the wind, the player is given the velocity it would have had plus the
	 * whirlwind's pull.
	 */
	private void whirlPlayer(ServerPlayer player, Devil devil, Vec3 moved) {
		double grown = devil.grown();
		double h = player.getY() - devil.y;
		double dx = player.getX() - devil.x;
		double dz = player.getZ() - devil.z;
		double r = Math.sqrt(dx * dx + dz * dz);
		double peak = (0.28 + 0.05 * intensity) * grown;
		Vec3 air = Vortex.air(dx, dz, Math.max(0.0, h), devil.core, peak, Math.max(4.0, devil.height * grown));
		boolean ground = player.onGround();
		double keep = ground ? 0.546 : 0.91;
		double pull = ground ? 0.2 : 0.14;
		double vy = ground ? -0.0784 : (moved.y - 0.08) * 0.98;
		boolean core = r < devil.core * 1.2;
		if (core && flungFor(player) <= 0) {
			if (h < lift(devil)) {
				// Up the core.
				vy = Math.max(vy, 0.2 + 0.14 * (1.0 - r / (devil.core * 1.2)));
				player.resetFallDistance();
			} else {
				// Thrown out at the top of its lift: outward and on round.
				double ux = r > 0.5 ? dx / r : 1.0;
				double uz = r > 0.5 ? dz / r : 0.0;
				double out = 0.45 + 0.04 * intensity;
				setVelocity(player, new Vec3(ux * out + uz * out * 0.8, 0.2, uz * out - ux * out * 0.8));
				flung.put(player.getUUID(), age + 60);
				return;
			}
		}
		// The whirl's air goes along with it as it wanders, or what it caught would be left behind.
		double airX = air.x + devil.vx;
		double airZ = air.z + devil.vz;
		Vec3 next = new Vec3(moved.x * keep + (airX - moved.x) * pull, vy, moved.z * keep + (airZ - moved.z) * pull);
		setVelocity(player, limit(next, 1.6));
	}

	/** A dust devil spins up dust and anything light near it, and lifts small animals off their feet. */
	private void whirl(Devil devil) {
		double grown = devil.grown();
		if (grown <= 0.05) {
			return;
		}
		double reach = devil.core * 2.6;
		double top = Math.max(4.0, devil.height * grown);
		double peak = (0.28 + 0.05 * intensity) * grown;
		double lift = lift(devil);
		AABB box = new AABB(devil.x - reach, devil.y - 3.0, devil.z - reach, devil.x + reach, devil.y + top, devil.z + reach);
		int count = 0;
		for (Entity entity : level.getEntitiesOfClass(Entity.class, box, Sandstorm::blown)) {
			if (++count > 24) {
				break;
			}
			if (immune(entity) || sheltered(entity)) {
				continue;
			}
			double h = entity.getY() - devil.y;
			double dx = entity.getX() - devil.x;
			double dz = entity.getZ() - devil.z;
			Vec3 air = Vortex.air(dx, dz, Math.max(0.0, h), devil.core, peak, top);
			Vec3 velocity = entity.getDeltaMovement();
			if (entity instanceof ItemEntity) {
				// Swept up and round, and out at the top.
				shove(entity, (air.x + devil.vx - velocity.x) * 0.3, (air.y - velocity.y) * 0.3, (air.z + devil.vz - velocity.z) * 0.3);
				continue;
			}
			double size = entity.getBbWidth() * entity.getBbHeight();
			double response = 0.12 / Math.max(1.0, size);
			double dy = 0.0;
			if (size <= 2.0 && dx * dx + dz * dz < devil.core * devil.core * 1.44 && flungFor(entity) <= 0) {
				if (h < lift) {
					dy = Math.max(0.0, 0.24 - velocity.y) * 0.6;
					entity.resetFallDistance();
				} else {
					flung.put(entity.getUUID(), age + 60);
				}
			}
			shove(entity, (air.x + devil.vx - velocity.x) * response, dy, (air.z + devil.vz - velocity.z) * response);
		}
	}

	// --- the sand it leaves -----------------------------------------------------------------------

	/** How many blocks of sand it may lay in all. */
	private int maxSand() {
		return tint < 2 ? (int) (1200 + 250 * intensity) : (int) (300 + 60 * intensity);
	}

	/**
	 * Sand settles out of the wind where it slackens: in the lee of whatever breaks it (a wall, a rock, a cactus) and
	 * against its windward face, in drifts a block or two deep that slope away from it, over the small plants of the
	 * desert and into its hollows. Over green land much less is left, and only against what breaks the wind. So many
	 * columns are looked at a tick near the players in the dust, so many blocks laid.
	 */
	private void drift(List<ServerPlayer> inDust, float strength) {
		if (inDust.isEmpty() || sandLaid >= maxSand()) {
			return;
		}
		int samples = (int) ((4 + 2 * intensity) * strength);
		int budget = tint < 2 ? 2 : 1;
		for (int i = 0; i < samples && budget > 0; i++) {
			ServerPlayer player = inDust.get(random.nextInt(inDust.size()));
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = 4.0 + 40.0 * Math.sqrt(random.nextDouble());
			int bx = (int) Math.floor(player.getX() + Math.cos(angle) * r);
			int bz = (int) Math.floor(player.getZ() + Math.sin(angle) * r);
			if (!loaded(bx, bz) || inside(bx + 0.5, bz + 0.5) < 0.3) {
				continue;
			}
			if (settle(bx, bz, inDust)) {
				budget--;
				sandLaid++;
			}
		}
	}

	/** The first free y over a column (above whatever stops a body, leaves and water too), or the lowest there is if it isn't loaded. */
	private int top(int bx, int bz) {
		return loaded(bx, bz) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) : Integer.MIN_VALUE;
	}

	/** One grid step most nearly along a component of the wind (eight ways round). */
	private static int step(double component) {
		return Math.abs(component) < 0.38 ? 0 : component > 0.0 ? 1 : -1;
	}

	/** Lays a block of sand on a column, if the sand would settle there. */
	private boolean settle(int bx, int bz, List<ServerPlayer> inDust) {
		int h = top(bx, bz);
		if (h <= level.getMinY() + 1 || h >= level.getMaxY() - 1) {
			return false;
		}
		BlockPos spot = new BlockPos(bx, h, bz);
		BlockState surface = level.getBlockState(spot.below());
		BlockState there = level.getBlockState(spot);
		if (!soil(surface) || !surface.getFluidState().isEmpty() || !there.getFluidState().isEmpty()) {
			return false;
		}
		boolean plant = !there.isAir();
		if (plant && !buriable(there)) {
			return false;
		}
		boolean sandy = tint < 2;
		int sx = step(wind.x);
		int sz = step(wind.z);
		int up1 = top(bx - sx, bz - sz);
		int up2 = top(bx - 2 * sx, bz - 2 * sz);
		int down1 = top(bx + sx, bz + sz);
		// In the lee: something upwind stands well above, and the drift slopes off downwind (no cliff on the open side).
		boolean lee = (up1 >= h + 2 || up2 >= h + 3) && down1 >= h;
		// Against the windward face of something, the open side upwind.
		boolean windward = down1 >= h + 2 && up1 >= h;
		// A bush on level sand is buried where it stands; a hollow fills in.
		boolean buried = sandy && plant && up1 >= h && down1 >= h;
		boolean hollow = sandy && !lee && !windward && top(bx + 1, bz) > h && top(bx - 1, bz) > h && top(bx, bz + 1) > h && top(bx, bz - 1) > h;
		if (!(lee || windward || buried || hollow)) {
			return false;
		}
		if (!sandy && random.nextInt(4) != 0) {
			return false;
		}
		for (ServerPlayer player : inDust) {
			if (Math.abs(player.getX() - (bx + 0.5)) < 1.3 && Math.abs(player.getZ() - (bz + 0.5)) < 1.3 && Math.abs(player.getY() - h) < 2.5) {
				// Not on top of anyone.
				return false;
			}
		}
		boolean red = tint == 1 || surface.is(Blocks.RED_SAND) || surface.is(Blocks.RED_SANDSTONE) || surface.is(BlockTags.TERRACOTTA);
		BlockState sand = red ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
		// A buried plant tells its neighbours (the top of a tall one goes with it); sand on bare ground needn't.
		level.setBlock(spot, sand, plant ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
		return true;
	}

	/** Ground sand may lie on: sand, soil, stone, the desert's rock. */
	private static boolean soil(BlockState state) {
		return state.is(BlockTags.SAND) || state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.TERRACOTTA)
				|| state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.GRAVEL);
	}

	/** A small plant sand can bury: grass, a bush, a flower (nothing with a block entity, nothing in water). */
	private static boolean buriable(BlockState state) {
		return !state.hasBlockEntity() && state.getFluidState().isEmpty()
				&& (state.canBeReplaced() || state.getBlock() instanceof VegetationBlock || state.is(BlockTags.FLOWERS));
	}

	private static Vec3 limit(Vec3 v, double max) {
		double length = v.length();
		if (!(length < 1.0E6)) {
			return Vec3.ZERO;
		}
		return length > max ? v.scale(max / length) : v;
	}
}
