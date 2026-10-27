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

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A lightning storm: a violent thunderstorm cell, two to four hundred blocks across, drifting slowly with
 * its wind and bringing thunder weather with it. Lightning comes out of it far more often than out of any
 * ordinary storm, and in bursts: a lull of rumbling with a strike now and then, then strike after strike,
 * at its worst (level 5) about one a second, never more than a few. A good share of the strikes come down
 * near the players under it, closest to those out in the open; and now and then one comes for a player in
 * the open: the air about them crackles for a second or two first (their hair stands on end), and if they
 * are still there when it comes down, it strikes them. The strikes are the game's own lightning: they set
 * fires (as the fire rule allows; the storm's rain puts most of them out), charge creepers and split trees,
 * though only a tree now and then, and seldom anything that would burn where it doesn't rain.
 *
 * <p>In the storms of level 4 and 5 ball lightning forms now and then after a strike: a glowing orb that
 * drifts about near the ground for half a minute or so, wandering this way and that and drawn a little
 * toward whoever is near, turning away from walls, and zapping whatever living thing comes close to it,
 * until it fizzles out (or bursts with a bang, zapping what is close one last time) or drifts into water.
 *
 * <p>The work is bounded every tick: a few strikes a second at most, each looking at a few columns of land;
 * a few orbs and a few crackling charges at once, each orb looking about itself in a small box every few
 * ticks; the players under it looked for every ten ticks.
 */
public final class LightningStorm extends Disaster {
	/** Strikes a second at its height, by level (1 to 5), before the bursts and lulls. */
	private static final double[] RATE = {0.0, 0.14, 0.24, 0.4, 0.65, 1.0};
	/** Never more strikes than this in one second, however hard it bursts. */
	private static final int MAX_PER_SECOND = 3;
	/** Nor two strikes closer together than this, in ticks. */
	private static final int MIN_GAP = 4;
	/** Orbs of ball lightning at once, at most. */
	private static final int MAX_ORBS = 3;
	/** Spots about to be struck (where the air crackles) at once, at most. */
	private static final int MAX_CHARGES = 3;
	/** How fast the cell drifts with its wind, in blocks a tick. */
	private static final double DRIFT = 0.035;
	/** A bolt hurts what stands within three blocks of it: strikes meant to miss a player land further off. */
	private static final double BOLT_REACH = 3.0;
	/** How close to ball lightning a living thing is zapped, in blocks, and how often an orb can zap, in ticks. */
	private static final double ZAP_REACH = 2.6;
	private static final int ZAP_GAP = 12;
	/** Ticks between strikes on anything that would burn in hot country where no rain falls. */
	private static final int TINDER_GAP = 160;
	/** The numbers sent to the clients: a few of the storm's own, then each orb's and each charge's (see {@link #extra()}). */
	private static final int HEADER = 5;
	private static final int ORB_FLOATS = 8;
	private static final int CHARGE_FLOATS = 4;

	private final int rank;
	private final double reach;
	private final double visibility;
	private double heading;
	private double veer;
	private double cloudBase;
	/** How hard it is striking now against its usual: well over 1 in a burst, under 1 in a lull. */
	private double burst = 1.0;
	private double burstTarget = 1.0;
	private int burstLeft;
	private int lastStrike = -100;
	private int second = -1;
	private int strikesThisSecond;
	/** Its age when a strike may next split a tree, set fire to dry country, and leave ball lightning. */
	private int treeReady;
	private int tinderReady;
	private int orbReady;
	private final List<Orb> orbs = new ArrayList<>();
	private final List<Charge> charges = new ArrayList<>();
	/** The players under it, as last looked for. */
	private final List<ServerPlayer> under = new ArrayList<>();
	/** When a strike may next come for each player. */
	private final Map<UUID, Integer> nextCharge = new HashMap<>();
	/** Players already told the storm is over them, that a strike is coming for them, of ball lightning. */
	private final Set<UUID> warned = new HashSet<>();
	private final Set<UUID> warnedCharge = new HashSet<>();
	private final Set<UUID> warnedOrb = new HashSet<>();
	private int strikes;
	private int strikesNear;
	private int chargedStrikes;
	private int treeStrikes;
	private int orbsFormed;
	private int zaps;

	/** A ball of lightning, drifting about near the ground. */
	private static final class Orb {
		final int born;
		final int life;
		final double speed;
		final double hover;
		final int phase;
		double x;
		double y;
		double z;
		double heading;
		/** The player near it it drifts toward, if any. */
		ServerPlayer lure;
		int lastZap = -1000;
		double zapX;
		double zapY;
		double zapZ;

		Orb(double x, double y, double z, int born, int life, double speed, double hover, double heading, int phase) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.born = born;
			this.life = life;
			this.speed = speed;
			this.hover = hover;
			this.heading = heading;
			this.phase = phase;
		}
	}

	/** A spot about to be struck, where a player stood: the strike comes down at `at` (the storm's age). */
	private record Charge(double x, double y, double z, int at) {
	}

	LightningStorm(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.LIGHTNING_STORM, level, at, intensity, natural, 0);
		rank = Math.max(1, Math.min(5, Math.round(this.intensity)));
		reach = 110.0 + 18.0 * this.intensity;
		// How far one sees in its rain and gloom: some sixty blocks at level 5.
		visibility = Math.max(50.0, 150.0 - 18.0 * this.intensity);
		duration = (int) ((3000 + 800 * this.intensity) * (0.85 + random.nextDouble() * 0.3));
		heading = random.nextDouble() * Math.PI * 2.0;
		y = ground((int) Math.floor(x), (int) Math.floor(z)) + 1;
		cloudBase = cloudBase();
		burstLeft = 60 + random.nextInt(100);
	}

	@Override
	public float radius() {
		return (float) reach;
	}

	@Override
	public Vec3 direction() {
		return new Vec3(Math.cos(heading), 0.0, Math.sin(heading));
	}

	@Override
	public float strength() {
		float in = Math.min(1.0F, age / 300.0F);
		float out = Math.min(1.0F, (duration - age) / 400.0F);
		return Math.max(0.0F, Math.min(in, out));
	}

	/**
	 * For the clients: the height of the cloud base, how far one sees in it, how hard it is striking now (the
	 * burst), the number of orbs and of charges; then for each orb its x, y and z, how bright it is (0 to 1),
	 * the age at which it last zapped something and the x, y and z of what it zapped; then for each charge its
	 * x, y and z and the ticks left before the strike.
	 */
	@Override
	public float[] extra() {
		int orbCount = Math.min(orbs.size(), MAX_ORBS);
		int chargeCount = Math.min(charges.size(), MAX_CHARGES);
		float[] out = new float[HEADER + orbCount * ORB_FLOATS + chargeCount * CHARGE_FLOATS];
		out[0] = (float) cloudBase;
		out[1] = (float) visibility;
		out[2] = (float) burst;
		out[3] = orbCount;
		out[4] = chargeCount;
		int i = HEADER;
		for (int k = 0; k < orbCount; k++) {
			Orb orb = orbs.get(k);
			out[i++] = (float) orb.x;
			out[i++] = (float) orb.y;
			out[i++] = (float) orb.z;
			out[i++] = (float) energy(orb);
			out[i++] = orb.lastZap;
			out[i++] = (float) orb.zapX;
			out[i++] = (float) orb.zapY;
			out[i++] = (float) orb.zapZ;
		}
		for (int k = 0; k < chargeCount; k++) {
			Charge charge = charges.get(k);
			out[i++] = (float) charge.x();
			out[i++] = (float) charge.y();
			out[i++] = (float) charge.z();
			out[i++] = Math.max(0, charge.at() - age);
		}
		return out;
	}

	// --- for the tests ------------------------------------------------------------------------

	/** Its level, 1 to 5. */
	public int rank() {
		return rank;
	}

	/** Bolts it has called down so far. */
	public int strikes() {
		return strikes;
	}

	/** Of those, the ones aimed near a player under it (not at one). */
	public int strikesNearPlayers() {
		return strikesNear;
	}

	/** Of those, the ones that came down where a player stood when the air about them began to crackle. */
	public int chargedStrikes() {
		return chargedStrikes;
	}

	/** Of those, the ones let come down by a tree's trunk (which the game then splits, when lightning fells trees). */
	public int treeStrikes() {
		return treeStrikes;
	}

	/** Spots crackling now, about to be struck. */
	public int pendingCharges() {
		return charges.size();
	}

	/** Orbs of ball lightning drifting about now. */
	public int orbCount() {
		return orbs.size();
	}

	/** Orbs of ball lightning it has formed so far. */
	public int orbsFormed() {
		return orbsFormed;
	}

	/** Times its ball lightning has zapped something so far. */
	public int zaps() {
		return zaps;
	}

	/** Where an orb of ball lightning is now (null if there is no such orb). */
	public Vec3 orbPosition(int index) {
		return index >= 0 && index < orbs.size() ? new Vec3(orbs.get(index).x, orbs.get(index).y, orbs.get(index).z) : null;
	}

	/** Whether the storm is over an entity: within its reach. */
	public boolean over(Entity entity) {
		return distance2d(entity, x, z) < reach;
	}

	/** Forms an orb of ball lightning at a place, whatever the storm's level (for tests and commands); false if there are too many. */
	public boolean formOrb(Vec3 at) {
		if (orbs.size() >= MAX_ORBS) {
			return false;
		}
		double hover = 0.8 + random.nextDouble();
		orbs.add(new Orb(at.x, at.y, at.z, age, 300 + random.nextInt(400), 0.05 + random.nextDouble() * 0.07, hover,
				random.nextDouble() * Math.PI * 2.0, random.nextInt(100)));
		orbsFormed++;
		if (NaturesConfig.get().disasterWarnings) {
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && player.distanceToSqr(at.x, at.y, at.z) < 48.0 * 48.0 && warnedOrb.add(player.getUUID())) {
					player.sendSystemMessage(Component.translatable("naturesmod.warning.ball_lightning").withStyle(ChatFormatting.GOLD));
				}
			}
		}
		return true;
	}

	/** Makes a spot crackle, to be struck in `delay` ticks (for tests and commands); false if too many are crackling. */
	public boolean chargeAt(Vec3 at, int delay) {
		if (charges.size() >= MAX_CHARGES) {
			return false;
		}
		charges.add(new Charge(at.x, at.y, at.z, age + Math.max(1, delay)));
		return true;
	}

	// --- the storm ----------------------------------------------------------------------------

	@Override
	protected void begin() {
		if (level.dimension() == Level.OVERWORLD && !level.isThundering()) {
			// The storm brings its own weather, whatever the sky was doing: it darkens, the rain comes and the thunder starts.
			level.getServer().setWeatherParameters(0, duration + 600, true, true);
		}
		Component moving = compass(Math.cos(heading), Math.sin(heading));
		warn(reach + 300.0, player -> {
			double d = distance2d(player, x, z);
			if (d < reach) {
				warned.add(player.getUUID());
				return Component.translatable("naturesmod.warning.lightning_storm");
			}
			return Component.translatable("naturesmod.warning.lightning_storm_coming", (int) d, compass(player.getX(), player.getZ(), x, z), moving);
		});
	}

	@Override
	protected void tick() {
		drift();
		if (age % 10 == 0) {
			lookUnder();
		}
		pulse();
		float strength = strength();
		if (strength > 0.0F) {
			thunder(strength);
		}
		charges();
		orbs();
		if (age % 40 == 0) {
			warnUnder();
		}
		if (natural && age % 100 == 0 && playersWithin(reach + 600.0).isEmpty()) {
			end();
		}
	}

	@Override
	protected void finish() {
		for (Orb orb : orbs) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, orb.x, orb.y, orb.z, 8, 0.2, 0.2, 0.2, 0.15);
		}
		orbs.clear();
		charges.clear();
		under.clear();
	}

	/** The cell drifts on with its wind, the wind veering a little as it goes. */
	private void drift() {
		veer = Math.max(-0.0006, Math.min(0.0006, veer * 0.995 + random.nextGaussian() * 0.00003));
		heading += veer;
		x += Math.cos(heading) * DRIFT;
		z += Math.sin(heading) * DRIFT;
		if (age % 100 == 0) {
			int bx = (int) Math.floor(x);
			int bz = (int) Math.floor(z);
			if (loaded(bx, bz)) {
				y = ground(bx, bz) + 1;
			}
			cloudBase = cloudBase();
		}
	}

	/** The height of its cloud base: low over the land, lower the worse the storm. */
	private double cloudBase() {
		double base = Math.max(y, level.getSeaLevel()) + 34.0 - rank * 2.0;
		return Math.max(level.getMinY() + 8.0, Math.min(level.getMaxY() - 4.0, base));
	}

	/** Bursts and lulls: a burst of strike after strike for some seconds, then a lull of rumbling for longer. */
	private void pulse() {
		if (--burstLeft <= 0) {
			if (burstTarget > 1.0) {
				burstTarget = 0.25 + random.nextDouble() * 0.25;
				burstLeft = 80 + random.nextInt(200);
			} else {
				burstTarget = 2.0 + random.nextDouble();
				burstLeft = 50 + random.nextInt(110);
			}
		}
		burst += (burstTarget - burst) * 0.1;
	}

	private void lookUnder() {
		under.clear();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && distance2d(player, x, z) < reach) {
				under.add(player);
			}
		}
	}

	/** Players who have come under it since it began are told what it is, once. */
	private void warnUnder() {
		if (!NaturesConfig.get().disasterWarnings || strength() < 0.2F) {
			return;
		}
		for (ServerPlayer player : under) {
			if (player.isAlive() && warned.add(player.getUUID())) {
				player.sendSystemMessage(Component.translatable("naturesmod.warning.lightning_storm").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	// --- the lightning ------------------------------------------------------------------------

	/** Whether a strike comes this tick, and where. */
	private void thunder(float strength) {
		int now = age / 20;
		if (now != second) {
			second = now;
			strikesThisSecond = 0;
		}
		// The crackling spots will be struck too: room is kept for them in the seconds before they are.
		if (strikesThisSecond + charges.size() >= MAX_PER_SECOND || age - lastStrike < MIN_GAP) {
			return;
		}
		double chance = RATE[rank] * burst * strength / 20.0;
		if (random.nextDouble() >= chance) {
			return;
		}
		if (strikeSomewhere()) {
			lastStrike = age;
			strikesThisSecond++;
		}
	}

	/**
	 * One strike: near a player under it (some way off, or now and then, for a player out in the open, a charge
	 * where the player stands) or anywhere under it, more of them toward its heart. True if one came down (or a
	 * charge began).
	 */
	private boolean strikeSomewhere() {
		ServerPlayer target = null;
		if (!under.isEmpty() && random.nextDouble() < 0.4 + 0.05 * rank) {
			ServerPlayer player = under.get(random.nextInt(under.size()));
			if (player.isAlive() && player.level() == level && !immune(player)) {
				target = player;
			}
		}
		if (target != null) {
			boolean open = !sheltered(target);
			if (open && charges.size() < MAX_CHARGES && age >= nextCharge.getOrDefault(target.getUUID(), 0)
					&& random.nextDouble() < 0.08 + 0.04 * rank && charge(target)) {
				return true;
			}
			double angle = random.nextDouble() * Math.PI * 2.0;
			// Close about those out in the open; further off from those under a roof, who hear it close all the same.
			double distance = open ? 5.0 + random.nextDouble() * 25.0 : 16.0 + random.nextDouble() * 32.0;
			if (strike(target.getX() + Math.cos(angle) * distance, target.getZ() + Math.sin(angle) * distance, 2.5, target)) {
				strikesNear++;
				return true;
			}
			return false;
		}
		double angle = random.nextDouble() * Math.PI * 2.0;
		double r = reach * Math.pow(random.nextDouble(), 0.7);
		return strike(x + Math.cos(angle) * r, z + Math.sin(angle) * r, 4.0, null);
	}

	/**
	 * Lightning seeks out what stands highest: of a few places about (cx, cz) it comes down on the highest, but not
	 * by a tree's trunk unless a tree may be split now, nor on anything that would burn in hot, dry country unless
	 * that may happen now, nor within a bolt's reach of `spare`. True if it came down.
	 */
	private boolean strike(double cx, double cz, double spread, Entity spare) {
		int bestX = 0;
		int bestZ = 0;
		int bestTop = Integer.MIN_VALUE;
		boolean bestTree = false;
		boolean bestTinder = false;
		boolean splitting = NaturesConfig.get().lightningFellsTrees;
		for (int i = 0; i < 3; i++) {
			int bx = (int) Math.floor(cx + random.nextGaussian() * spread);
			int bz = (int) Math.floor(cz + random.nextGaussian() * spread);
			if (!loaded(bx, bz)) {
				continue;
			}
			if (spare != null && distance2d(spare, bx + 0.5, bz + 0.5) < BOLT_REACH + 1.5) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
			if (top <= bestTop || top <= level.getMinY() + 1 || top >= level.getMaxY()) {
				continue;
			}
			boolean tree = splitting && nearTrunk(bx, top, bz);
			if (tree && !(griefing() && age >= treeReady)) {
				continue;
			}
			boolean tinder = tinder(bx, top, bz);
			if (tinder && !(griefing() && age >= tinderReady)) {
				continue;
			}
			bestX = bx;
			bestZ = bz;
			bestTop = top;
			bestTree = tree;
			bestTinder = tinder;
		}
		if (bestTop == Integer.MIN_VALUE) {
			return false;
		}
		if (bestTree) {
			// A tree split now and then, not a forest felled (each one a shower of falling blocks): one every 7 to 15 seconds.
			treeReady = age + 340 - 40 * rank;
			treeStrikes++;
		}
		if (bestTinder) {
			tinderReady = age + TINDER_GAP;
		}
		lightning(bestX + 0.5, bestZ + 0.5);
		strikes++;
		afterStrike(bestX, bestZ);
		return true;
	}

	/**
	 * Whether a strike at the top of a column would split a tree: a trunk within a block of it, as far down as the
	 * game looks for one when lightning strikes (see TreeEvents). Where the next columns aren't loaded, it might.
	 */
	private boolean nearTrunk(int bx, int top, int bz) {
		if (!loaded(bx - 1, bz - 1) || !loaded(bx + 1, bz + 1) || !loaded(bx - 1, bz + 1) || !loaded(bx + 1, bz - 1)) {
			return true;
		}
		int from = Math.min(top + 2, level.getMaxY() - 1);
		int to = Math.max(top - 24, level.getMinY());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int by = from; by >= to; by--) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (TreeFeller.isLog(level.getBlockState(pos.set(bx + dx, by, bz + dz)))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Something that would catch fire where no rain falls to put it out: in hot, dry country (a savanna's grass, a
	 * desert's brush), in snowy country (where it snows instead, and a spruce forest would burn on), or anywhere
	 * once the rain has stopped.
	 */
	private boolean tinder(int bx, int top, int bz) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, top, bz);
		Biome biome = level.getBiome(pos).value();
		if (level.isRaining() && biome.getBaseTemperature() < 1.5F && !biome.coldEnoughToSnow(pos, level.getSeaLevel())) {
			return false;
		}
		return level.getBlockState(pos).ignitedByLava() || level.getBlockState(pos.set(bx, top - 1, bz)).ignitedByLava();
	}

	/**
	 * A strike is coming for a player out in the open: the air about them crackles for a second or two, and then
	 * it comes down where they stood. False if it can't come there now.
	 */
	private boolean charge(ServerPlayer player) {
		int bx = (int) Math.floor(player.getX());
		int bz = (int) Math.floor(player.getZ());
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		boolean tinder = tinder(bx, top, bz);
		if (tinder && !(griefing() && age >= tinderReady)) {
			return false;
		}
		if (tinder) {
			tinderReady = age + TINDER_GAP;
		}
		charges.add(new Charge(player.getX(), player.getY(), player.getZ(), age + 30 + random.nextInt(16)));
		nextCharge.put(player.getUUID(), age + 600 - 80 * rank);
		if (NaturesConfig.get().disasterWarnings && warnedCharge.add(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("naturesmod.warning.lightning_charge").withStyle(ChatFormatting.GOLD));
		}
		return true;
	}

	/** The charged spots whose time has come are struck: whoever is still standing there is struck with them. */
	private void charges() {
		for (Iterator<Charge> it = charges.iterator(); it.hasNext(); ) {
			Charge charge = it.next();
			if (age < charge.at()) {
				continue;
			}
			it.remove();
			if (!loaded((int) Math.floor(charge.x()), (int) Math.floor(charge.z()))) {
				continue;
			}
			lightning(charge.x(), charge.z());
			strikes++;
			chargedStrikes++;
			strikesThisSecond++;
			lastStrike = age;
		}
	}

	/** Out of a strike in the worst storms ball lightning forms now and then, where someone is near to see it. */
	private void afterStrike(int bx, int bz) {
		if (rank < 4 || age < orbReady || orbs.size() >= (rank >= 5 ? MAX_ORBS : 1) || strength() < 0.3F
				|| random.nextDouble() >= (rank >= 5 ? 0.2 : 0.12)) {
			return;
		}
		boolean seen = false;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(bx + 0.5, player.getY(), bz + 0.5) < 64.0 * 64.0) {
				seen = true;
				break;
			}
		}
		if (!seen) {
			return;
		}
		double floor = ground(bx, bz) + 1;
		if (formOrb(new Vec3(bx + 0.5, floor + 1.2, bz + 0.5))) {
			orbReady = age + (rank >= 5 ? 160 : 300) + random.nextInt(200);
		}
	}

	// --- ball lightning -----------------------------------------------------------------------

	/** How bright an orb is: swelling as it forms, shrinking as it fades. */
	private double energy(Orb orb) {
		int lived = age - orb.born;
		int left = orb.life - lived;
		return Math.max(0.0, Math.min(1.0, Math.min(lived / 10.0, left / 30.0)));
	}

	private void orbs() {
		for (Iterator<Orb> it = orbs.iterator(); it.hasNext(); ) {
			Orb orb = it.next();
			boolean old = age - orb.born >= orb.life;
			if (old || !move(orb)) {
				// Most fizzle out; some burst with a bang.
				vanish(orb, old && random.nextInt(3) == 0);
				it.remove();
				continue;
			}
			if ((age + orb.phase) % 5 == 0) {
				zap(orb);
			}
		}
	}

	/**
	 * Drifts an orb on: wandering this way and that, drawn a little toward the player nearest it and along with the
	 * storm's wind, hovering over the land a block or two up and turning away from what stands higher than it. False
	 * once it has gone into water or out of the loaded world.
	 */
	private boolean move(Orb orb) {
		if ((age + orb.phase) % 10 == 0) {
			orb.lure = nearestPlayer(orb, 16.0);
		}
		orb.heading += random.nextGaussian() * 0.18;
		double vx = Math.cos(orb.heading) * orb.speed + Math.cos(heading) * 0.01;
		double vz = Math.sin(orb.heading) * orb.speed + Math.sin(heading) * 0.01;
		ServerPlayer lure = orb.lure;
		if (lure != null && lure.isAlive() && lure.level() == level && !immune(lure)) {
			double dx = lure.getX() - orb.x;
			double dz = lure.getZ() - orb.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > 0.5) {
				vx += dx / d * 0.025;
				vz += dz / d * 0.025;
			}
		}
		double nx = orb.x + vx;
		double nz = orb.z + vz;
		int bx = (int) Math.floor(nx);
		int bz = (int) Math.floor(nz);
		if (!loaded(bx, bz)) {
			return false;
		}
		double floor = ground(bx, bz) + 1;
		if (floor > orb.y + 0.6) {
			// A wall, a trunk or a cliff: it turns away rather than climbing it, and hovers where it is (rising out of the
			// ground, if it formed in it).
			orb.heading += Math.PI * (0.6 + random.nextDouble() * 0.8);
			floor = ground((int) Math.floor(orb.x), (int) Math.floor(orb.z)) + 1;
		} else {
			orb.x = nx;
			orb.z = nz;
		}
		double want = floor + orb.hover + Math.sin((age + orb.phase) * 0.15) * 0.3;
		orb.y += Math.max(-0.15, Math.min(0.15, want - orb.y));
		orb.y = Math.max(level.getMinY() + 1.0, Math.min(level.getMaxY() - 1.0, orb.y));
		return level.getFluidState(BlockPos.containing(orb.x, orb.y, orb.z)).isEmpty();
	}

	private ServerPlayer nearestPlayer(Orb orb, double range) {
		ServerPlayer nearest = null;
		double best = range * range;
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || immune(player)) {
				continue;
			}
			double d = player.distanceToSqr(orb.x, orb.y, orb.z);
			if (d < best) {
				best = d;
				nearest = player;
			}
		}
		return nearest;
	}

	/** Zaps the living thing nearest an orb, if one is close enough (and it hasn't just zapped): it hurts and burns, and the jolt throws them apart. */
	private void zap(Orb orb) {
		if (age - orb.lastZap < ZAP_GAP) {
			return;
		}
		LivingEntity victim = null;
		double best = ZAP_REACH * ZAP_REACH;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box(orb, ZAP_REACH), e -> e.isAlive() && !immune(e))) {
			double d = entity.distanceToSqr(orb.x, orb.y - entity.getBbHeight() * 0.5, orb.z);
			if (d < best) {
				best = d;
				victim = entity;
			}
		}
		if (victim == null) {
			return;
		}
		orb.lastZap = age;
		orb.zapX = victim.getX();
		orb.zapY = victim.getY() + victim.getBbHeight() * 0.5;
		orb.zapZ = victim.getZ();
		victim.hurtServer(level, victim.damageSources().lightningBolt(), rank >= 5 ? 5.0F : 3.5F);
		if (random.nextInt(3) == 0) {
			victim.igniteForSeconds(2.0F);
		}
		double dx = victim.getX() - orb.x;
		double dz = victim.getZ() - orb.z;
		double d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
		shove(victim, dx / d * 0.35, 0.2, dz / d * 0.35);
		orb.heading = Math.atan2(-dz, -dx) + random.nextGaussian() * 0.5;
		zaps++;
	}

	/** The end of an orb: a fizzle and a few sparks, or a bang that zaps what is close one last time. */
	private void vanish(Orb orb, boolean bang) {
		if (!bang) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, orb.x, orb.y, orb.z, 8, 0.2, 0.2, 0.2, 0.15);
			return;
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, orb.x, orb.y, orb.z, 24, 0.4, 0.4, 0.4, 0.5);
		level.playSound(null, orb.x, orb.y, orb.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.WEATHER, 2.0F, 1.3F);
		double reachSq = 3.5 * 3.5;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box(orb, 3.5), e -> e.isAlive() && !immune(e))) {
			double d = entity.distanceToSqr(orb.x, orb.y - entity.getBbHeight() * 0.5, orb.z);
			if (d < reachSq) {
				entity.hurtServer(level, entity.damageSources().lightningBolt(), (float) ((rank >= 5 ? 6.0 : 4.0) * (1.0 - Math.sqrt(d) / 3.5)) + 1.0F);
			}
		}
	}

	private static AABB box(Orb orb, double r) {
		return new AABB(orb.x - r, orb.y - r, orb.z - r, orb.x + r, orb.y + r, orb.z + r);
	}
}
