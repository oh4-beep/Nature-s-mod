package com.naturesmod.disaster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.naturesmod.block.ModBlocks;
import com.naturesmod.tree.TreeFeller;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A meteor shower. Every meteor comes from the same point of the sky (its radiant), on parallel paths:
 * most are specks that burn up high and are only seen (the clients draw them), but fireballs come
 * through all the while, now and then several at once as one breaks up on the way down. The smaller
 * fireballs burn up overhead in a flash, the bigger ones burst in the air (a flash, then seconds later
 * the sonic boom, windows blowing in), and many strike the ground: a crater with a raised rim, ejecta
 * thrown about, its floor lined with shocked and melted rock, molten pockets of lava and the meteoric
 * iron in the middle, fire flickering on the hot rock, the forest around stripped and blown flat
 * pointing away from it (like at Tunguska), and the land around set alight by the heat. In the
 * strongest showers one great fireball comes down, with a warning, and blows out a crater some forty
 * blocks across.
 *
 * <p>The work is bounded however many come down: so many fireballs are in the air at once, strikes come
 * no closer together than a second and a half (no more than one crater a second), the craters are dug
 * over some ticks out of one budget of block changes shared by them all, so many blocks of ejecta fly at
 * once, so many trees fall in a second, and the fire a strike starts is a wildfire of its own, which
 * keeps its flames in bounds (see {@link Wildfire}), so many of them at once.
 */
public final class MeteorShower extends Disaster {
	/**
	 * Where in {@link #extra()} the fresh craters start, after the radiant (3) and their number, and how many numbers
	 * each takes: its middle (x, y at the ground's surface, z), its radius and the ticks since it struck.
	 */
	public static final int CRATERS_AT = 4;
	public static final int CRATER_FLOATS = 5;
	/** For how long a crater smoulders after it struck: so long the clients are told of it. */
	public static final int SMOULDER_TICKS = 1200;
	/** How fast fireballs come in, in blocks a tick. */
	private static final double SPEED = 3.5;
	/** The height they are first seen at (or higher, over high ground). */
	private static final double ENTRY = 420.0;
	/** What becomes of a fireball. */
	private static final int BURN_UP = 0;
	private static final int BURST = 1;
	private static final int STRIKE = 2;
	/** Fireballs in the air at once, at most. */
	private static final int MAX_FIREBALLS = 12;
	/** Strikes come no closer together than this many ticks. */
	private static final int STRIKE_GAP = 30;
	/** Craters being dug at once, at most. */
	private static final int MAX_DIGGING = 3;
	/** Fresh craters the clients are told of, at most. */
	private static final int MAX_TOLD = 4;
	/** Blocks changed in one tick, all the craters together. */
	private static final int BLOCKS_PER_TICK = 320;
	/** Blocks of ejecta flying as falling blocks at once, at most, all the craters together. */
	private static final int MAX_EJECTA = 36;
	/** Trees the blasts fell in a second, at most: each is a shower of falling blocks. */
	private static final int TREES_PER_SECOND = 2;
	/** Wildfires the strikes have started that may burn at once. */
	private static final int MAX_WILDFIRES = 2;
	/** The biggest crater's radius, in blocks. */
	private static final int MAX_RADIUS = 20;
	/** The columns round a crater's middle, nearest first, out to past the rim of the biggest, as pairs (dx, dz). */
	private static final int[] OFFSETS = offsets((int) Math.ceil(MAX_RADIUS * 1.35) + 1);
	/** A crater's passes: blowing out the bowl and piling up the rim, lining its floor, the molten middle, the forest about it. */
	private static final int DIG = 0;
	private static final int LINE = 1;
	private static final int MOLTEN = 2;
	private static final int FOREST = 3;
	private static final int DONE = 4;

	/** On its scale (1 to 5), whatever it was called up with. */
	private final float rank;
	private final Vec3 radiant;
	private final List<Fireball> coming = new ArrayList<>();
	private final List<Crater> digging = new ArrayList<>();
	private final List<Scar> scars = new ArrayList<>();
	private final List<FallingBlockEntity> ejecta = new ArrayList<>();
	private final List<Wildfire> wildfires = new ArrayList<>();
	private int nextFireball;
	/** When the great fireball comes down, if one does (-1 if not, or once it has). */
	private int greatAt;
	private int lastStrike = Integer.MIN_VALUE / 2;
	private int blocksLeft;
	private int launched;
	private int strikes;
	private int bursts;
	private int lavaPlaced;

	private record Fireball(Vec3 end, BlockPos ground, double size, int fate, int arrives) {
	}

	/** A crater struck lately, as the clients are told of it. */
	private record Scar(double x, double y, double z, int radius, int struck) {
	}

	/** A crater being dug out: its columns are worked through nearest the middle first, a pass at a time. */
	private static final class Crater {
		/** The ground at its middle. */
		final int x;
		final int y;
		final int z;
		final int radius;
		final int rim;
		final int depth;
		/** How many of the offsets lie in its bowl, in the molten middle of the bowl, and out to the edge of its rim. */
		final int bowl;
		final int core;
		final int extent;
		/** Whether the heat of it sets the land round about alight. */
		final boolean kindles;
		int pass;
		int next;
		int ejecta;
		int lava;
		int fires;
		int forest;

		Crater(int x, int y, int z, int radius, boolean kindles) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.radius = radius;
			this.rim = (int) Math.ceil(radius * 1.35);
			this.depth = (int) Math.round(radius * 0.6) + 1;
			this.bowl = within(radius);
			this.core = within(radius * 0.5);
			this.extent = within(rim + 0.5);
			this.kindles = kindles;
			this.ejecta = 8 + radius;
			this.lava = 2 + radius / 2;
			this.fires = 4 + radius / 2;
			this.forest = radius * 6;
		}
	}

	MeteorShower(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.METEOR_SHOWER, level, at, intensity, natural, (int) (2400 + 600 * rated(intensity)));
		this.rank = rated(intensity);
		double azimuth = random.nextDouble() * Math.PI * 2.0;
		double elevation = Math.toRadians(30.0 + random.nextDouble() * 35.0);
		radiant = new Vec3(Math.cos(azimuth) * Math.cos(elevation), -Math.sin(elevation), Math.sin(azimuth) * Math.cos(elevation));
		nextFireball = 100 + random.nextInt(160);
		// The strongest showers bring down one great fireball, somewhere in the middle of the night.
		boolean great = rank >= 4.5F ? random.nextDouble() < 0.9 : rank >= 3.5F && random.nextDouble() < 0.2;
		greatAt = great ? (int) (duration * (0.3 + random.nextDouble() * 0.35)) : -1;
	}

	private static float rated(float intensity) {
		return Float.isNaN(intensity) ? DisasterType.METEOR_SHOWER.normal : DisasterType.METEOR_SHOWER.clamp(intensity);
	}

	@Override
	public float radius() {
		return 400.0F;
	}

	@Override
	public Vec3 direction() {
		return new Vec3(radiant.x, 0.0, radiant.z).normalize();
	}

	@Override
	public float[] extra() {
		int told = Math.min(MAX_TOLD, scars.size());
		float[] out = new float[CRATERS_AT + told * CRATER_FLOATS];
		out[0] = (float) radiant.x;
		out[1] = (float) radiant.y;
		out[2] = (float) radiant.z;
		out[3] = told;
		for (int i = 0; i < told; i++) {
			Scar scar = scars.get(scars.size() - told + i);
			int at = CRATERS_AT + i * CRATER_FLOATS;
			out[at] = (float) scar.x();
			out[at + 1] = (float) scar.y();
			out[at + 2] = (float) scar.z();
			out[at + 3] = scar.radius();
			out[at + 4] = age - scar.struck();
		}
		return out;
	}

	@Override
	protected void begin() {
		warn(500, player -> Component.translatable("naturesmod.warning.meteors"));
	}

	@Override
	protected void tick() {
		blocksLeft = BLOCKS_PER_TICK;
		if (age >= nextFireball && age < duration - 300) {
			volley();
			nextFireball = age + (int) (-Math.log(1.0 - random.nextDouble()) * 560.0 / rank) + 30;
		}
		if (greatAt >= 0 && age >= greatAt) {
			// Tried again the next tick if there was nowhere it could come down.
			if (launch(true) || age > greatAt + 400 || age >= duration - 300) {
				greatAt = -1;
			}
		}
		for (Iterator<Fireball> it = coming.iterator(); it.hasNext(); ) {
			Fireball fireball = it.next();
			if (age >= fireball.arrives()) {
				it.remove();
				if (fireball.fate() == STRIKE) {
					impact(fireball.ground(), fireball.size());
				} else if (fireball.fate() == BURST) {
					airburst(fireball.end(), fireball.size());
				}
			}
		}
		ejecta.removeIf(block -> !block.isAlive());
		scars.removeIf(scar -> age - scar.struck() > SMOULDER_TICKS);
		dig();
		if (natural && level.isBrightOutside() && age > 200) {
			end();
		}
	}

	// --- the fireballs -----------------------------------------------------------------------

	/** Sends a fireball down now (for commands and tests). */
	public void launchNow() {
		launch(false);
	}

	/** Sends the great fireball down now (for tests); false if there was nowhere it could come down. */
	public boolean launchGreat() {
		return launch(true);
	}

	/** One fireball, or now and then several together: one that broke up on its way down. */
	private void volley() {
		int count = random.nextDouble() < 0.05 + 0.04 * rank ? 2 + random.nextInt(2 + (int) (rank / 2.0F)) : 1;
		for (int i = 0; i < count; i++) {
			launch(false);
		}
	}

	/**
	 * Sends a fireball down somewhere near a player (or the middle of the shower), and if it is to strike, well clear
	 * of everyone: one that can't be is let burst in the air instead, as is one that would strike too soon after another.
	 */
	private boolean launch(boolean great) {
		if (coming.size() >= MAX_FIREBALLS) {
			return false;
		}
		List<ServerPlayer> near = playersWithin(500);
		Vec3 around = near.isEmpty() ? position() : near.get(random.nextInt(near.size())).position();
		double size = great ? 6.5 + random.nextDouble() * 2.0 : Math.min(5.5, Math.exp(random.nextGaussian() * 0.55 - 0.1) * (0.8 + 0.15 * rank));
		if (!Double.isFinite(size) || size <= 0.0) {
			size = 1.0;
		}
		int fate = great ? STRIKE : size < 0.8 ? BURN_UP : size < 1.7 ? BURST : STRIKE;
		int radius = craterRadius(size);
		int tx = 0;
		int tz = 0;
		boolean found = false;
		for (int tries = 0; tries < 4 && !found; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			// Nearer on each try, for where little of the land round a player is loaded.
			double distance = great ? 90.0 + random.nextDouble() * 70.0 : 40.0 + random.nextDouble() * 220.0 * (1.0 - tries * 0.2);
			tx = (int) Math.floor(around.x + Math.cos(angle) * distance);
			tz = (int) Math.floor(around.z + Math.sin(angle) * distance);
			found = workable(tx, tz) && (fate != STRIKE || !crowded(tx, tz, radius * 2.0 + 10.0));
		}
		if (!found) {
			if (great || !workable(tx, tz)) {
				return false;
			}
			fate = BURST;
		}
		int land = terrain(tx, tz);
		if (fate == STRIKE && (land <= level.getMinY() + 4 || land >= level.getMaxY() - 4)) {
			// Over the void, or nothing to strike.
			if (great) {
				return false;
			}
			fate = BURST;
		}
		double ground = fate == STRIKE ? land + 1.0 : level.getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz);
		// Back along the radiant to where it enters the air.
		double entry = Math.max(ENTRY, ground + 300.0);
		Vec3 target = new Vec3(tx + 0.5, ground, tz + 0.5);
		Vec3 start = target.subtract(radiant.scale((entry - ground) / -radiant.y));
		if (fate == STRIKE && !great && strikeDue(age + flight(start, ground))) {
			fate = BURST;
		}
		double endY = switch (fate) {
			case BURN_UP -> ground + 160.0 + random.nextDouble() * 80.0;
			case BURST -> ground + 60.0 + random.nextDouble() * 70.0;
			default -> ground;
		};
		int flight = flight(start, endY);
		Vec3 end = start.add(radiant.scale((endY - start.y) / radiant.y));
		Vec3 velocity = radiant.scale(SPEED);
		DisasterNet.send(level, new DisasterNet.Event(DisasterNet.Event.METEOR, start.x, start.y, start.z, new float[] {(float) velocity.x,
				(float) velocity.y, (float) velocity.z, (float) size, fate, flight}), 0);
		coming.add(new Fireball(end, new BlockPos(tx, land, tz), size, fate, age + flight));
		launched++;
		if (great) {
			double hx = tx + 0.5;
			double hz = tz + 0.5;
			warn(700, player -> Component.translatable("naturesmod.warning.meteor_great", (int) distance2d(player, hx, hz),
					compass(player.getX(), player.getZ(), hx, hz)));
		}
		return true;
	}

	/** How many ticks a fireball takes from `start` down to the height `endY`. */
	private int flight(Vec3 start, double endY) {
		double travelled = (endY - start.y) / radiant.y;
		return Math.max(1, (int) Math.ceil(travelled / SPEED));
	}

	/** Whether another strike is due within the gap of `arrives`. */
	private boolean strikeDue(int arrives) {
		if (Math.abs(arrives - lastStrike) < STRIKE_GAP) {
			return true;
		}
		for (Fireball fireball : coming) {
			if (fireball.fate() == STRIKE && Math.abs(fireball.arrives() - arrives) < STRIKE_GAP) {
				return true;
			}
		}
		return false;
	}

	/** Whether a player stands within `range` of (bx, bz). */
	private boolean crowded(int bx, int bz, double range) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && distance2d(player, bx + 0.5, bz + 0.5) < range) {
				return true;
			}
		}
		return false;
	}

	private static int craterRadius(double size) {
		return (int) Math.max(3, Math.min(MAX_RADIUS, Math.round(2.5 + 2.0 * size)));
	}

	/** Bursting in the air: a blinding flash and a shock wave that blows windows in below. */
	private void airburst(Vec3 at, double size) {
		DisasterNet.send(level, new DisasterNet.Event(DisasterNet.Event.AIRBURST, at.x, at.y, at.z, new float[] {(float) size}), 0);
		bursts++;
		double reach = 60.0 + size * 30.0;
		for (ServerPlayer player : level.players()) {
			double d = player.position().distanceTo(at);
			if (d > reach * 1.6 || player.isSpectator()) {
				continue;
			}
			if (!immune(player)) {
				Vec3 away = player.position().subtract(at);
				double length = Math.max(1.0, away.length());
				double push = Math.min(1.0, 0.3 * reach / Math.max(d, 10.0));
				shove(player, away.x / length * push, Math.abs(away.y / length) * push * 0.3, away.z / length * push);
			}
			if (griefing()) {
				shatterGlass(player.blockPosition(), (int) Math.min(24, 8 + size * 5));
			}
		}
	}

	private void shatterGlass(BlockPos around, int reach) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int broken = 0;
		for (int i = 0; i < 300 && broken < 24; i++) {
			pos.set(around.getX() + random.nextInt(reach * 2 + 1) - reach, around.getY() + random.nextInt(reach) - reach / 3,
					around.getZ() + random.nextInt(reach * 2 + 1) - reach);
			if (!loaded(pos.getX(), pos.getZ())) {
				continue;
			}
			if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath().contains("glass") && random.nextInt(3) > 0) {
				level.destroyBlock(pos, false);
				broken++;
			}
		}
	}

	// --- the strikes -------------------------------------------------------------------------

	/**
	 * Strikes the ground at `ground` (the top block of the land there): the blast and its shock wave at once, and the
	 * crater, its floor and the devastation round it over the following ticks.
	 */
	public void impact(BlockPos ground, double size) {
		double s = Double.isFinite(size) ? Math.max(0.5, Math.min(10.0, size)) : 2.0;
		int radius = craterRadius(s);
		double cx = ground.getX() + 0.5;
		double cz = ground.getZ() + 0.5;
		int gy = ground.getY();
		DisasterNet.send(level, new DisasterNet.Event(DisasterNet.Event.IMPACT, cx, gy + 1, cz, new float[] {radius}), 0);
		// Its blast's rays reach out a long way: only where the land about it is running (else the server loads it on the spot).
		if (workable(ground.getX(), ground.getZ())) {
			level.explode(null, cx, gy + 1.5, cz, (float) Math.min(9.0, 2.0 + s * 1.5), Level.ExplosionInteraction.NONE);
		}
		shockwave(cx, gy, cz, radius);
		strikes++;
		lastStrike = age;
		scars.add(new Scar(cx, gy + 1, cz, radius, age));
		if (scars.size() > MAX_TOLD) {
			scars.remove(0);
		}
		if (griefing() && workable(ground.getX(), ground.getZ()) && gy > level.getMinY() + 2 && gy < level.getMaxY() - 2) {
			if (digging.size() >= MAX_DIGGING) {
				// The oldest is left as far as it got.
				digging.remove(0);
			}
			boolean kindles = radius >= 7 && (s >= 6.0 || random.nextDouble() < 0.6);
			digging.add(new Crater(ground.getX(), gy, ground.getZ(), radius, kindles));
		}
	}

	/** The blast throws everything near outward, and hurts and burns what is close: every player in reach, and the rest near enough to matter. */
	private void shockwave(double cx, double cy, double cz, int radius) {
		double reach = radius * 6.0;
		for (ServerPlayer player : level.players()) {
			blast(player, cx, cy, cz, radius, reach);
		}
		double near = Math.min(reach, 48.0);
		for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(cx - near, cy - near, cz - near, cx + near, cy + near, cz + near),
				e -> e.isAlive() && !(e instanceof Player))) {
			blast(entity, cx, cy, cz, radius, reach);
		}
	}

	private void blast(Entity entity, double cx, double cy, double cz, int radius, double reach) {
		if (immune(entity) || !entity.isAlive()) {
			return;
		}
		Vec3 away = entity.position().subtract(cx, cy - 1.0, cz);
		double d = Math.max(1.0, away.length());
		if (d > reach) {
			return;
		}
		double power = (1.0 - d / reach) * (1.6 + radius * 0.06);
		shove(entity, away.x / d * power, Math.max(0.3, away.y / d * power) * 0.7, away.z / d * power);
		double scorch = radius * 2.5;
		if (entity instanceof LivingEntity && d < scorch) {
			hurt(entity, DisasterDamage.METEOR, (float) ((4.0 + radius * 1.5) * (1.0 - d / scorch)));
			if (d < radius * 1.5) {
				entity.igniteForSeconds(4.0F);
			}
		}
	}

	/** Works on the craters being dug, the oldest first, as far as this tick's budget goes. */
	private void dig() {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Iterator<Crater> it = digging.iterator(); it.hasNext() && blocksLeft > 0; ) {
			Crater crater = it.next();
			work(crater, pos);
			if (crater.pass >= DONE) {
				it.remove();
			}
		}
	}

	private void work(Crater c, BlockPos.MutableBlockPos pos) {
		while (blocksLeft > 0 && c.pass < DONE) {
			if (c.pass == FOREST) {
				// A few tries a tick, over some ticks.
				if (!devastate(c)) {
					c.pass = DONE;
				}
				return;
			}
			int limit = c.pass == DIG ? c.extent : c.pass == LINE ? c.bowl : c.core;
			if (c.next >= limit) {
				c.pass++;
				c.next = 0;
				if (c.pass == FOREST) {
					kindle(c);
				}
				continue;
			}
			int dx = OFFSETS[c.next * 2];
			int dz = OFFSETS[c.next * 2 + 1];
			c.next++;
			int bx = c.x + dx;
			int bz = c.z + dz;
			if (!loaded(bx, bz)) {
				continue;
			}
			double d = Math.sqrt(dx * dx + dz * dz);
			if (c.pass == DIG) {
				if (d < c.radius) {
					blowOut(c, bx, bz, dx, dz, d, pos);
				} else {
					pileRim(c, bx, bz, d, pos);
				}
			} else if (c.pass == LINE) {
				line(c, bx, bz, d, pos);
			} else {
				melt(c, bx, bz, d, pos);
			}
		}
	}

	/** The height of the crater's floor `d` blocks from its middle: deepest there, at the ground's level at its edge. */
	private int floorY(Crater c, double d) {
		double t = d / c.radius;
		return Math.max(level.getMinY() + 2, c.y - (int) Math.round(c.depth * (1.0 - t * t)));
	}

	/**
	 * Blows a column of the bowl out down to its floor: what stood on it as well, up to a height. Some of the ground
	 * is thrown out as ejecta. Water and lava are left be (a column under them isn't dug), as is what can't be broken
	 * and what holds things (a chest, a furnace).
	 */
	private void blowOut(Crater c, int bx, int bz, int dx, int dz, double d, BlockPos.MutableBlockPos pos) {
		int surface = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1, level.getMaxY() - 1);
		if (!level.getBlockState(pos.set(bx, surface, bz)).getFluidState().isEmpty()) {
			return;
		}
		int top = Math.min(surface, c.y + c.radius + 4);
		int floor = floorY(c, d);
		for (int by = top; by > floor; by--) {
			BlockState state = level.getBlockState(pos.set(bx, by, bz));
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F) {
				continue;
			}
			if (c.ejecta > 0 && ejecta.size() < MAX_EJECTA && by <= c.y + 1 && random.nextInt(6) == 0 && state.isCollisionShapeFullBlock(level, pos)) {
				FallingBlockEntity block = FallingBlockEntity.fall(level, pos.immutable(), state);
				if (block != null) {
					double out = 0.5 + random.nextDouble() * 0.7;
					// Outward from the middle; from the middle itself, any way out (not straight up, to fall back in).
					double angle = d < 1.0 ? random.nextDouble() * Math.PI * 2.0 : Math.atan2(dz, dx);
					setVelocity(block, new Vec3(Math.cos(angle) * out, 0.6 + random.nextDouble() * 0.6, Math.sin(angle) * out));
					block.setHurtsEntities(2.0F, 20);
					ejecta.add(block);
					c.ejecta--;
					blocksLeft--;
					continue;
				}
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			blocksLeft--;
		}
	}

	/** The rim: the ejecta piled up round the crater, highest at its lip, and the ground scorched beyond. */
	private void pileRim(Crater c, int bx, int bz, double d, BlockPos.MutableBlockPos pos) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
		if (top <= level.getMinY() || top >= level.getMaxY() - 8) {
			return;
		}
		BlockState surface = level.getBlockState(pos.set(bx, top, bz));
		if (!WindDamage.isGround(surface) || !surface.getFluidState().isEmpty()) {
			return;
		}
		double out = (d - c.radius) / Math.max(1.0, c.rim - c.radius);
		int height = (int) Math.round(c.radius * 0.26 * (1.0 - out) + random.nextDouble() * 0.6);
		if (height <= 0) {
			if (surface.is(Blocks.GRASS_BLOCK) && random.nextDouble() > out) {
				level.setBlock(pos, scorch(surface), Block.UPDATE_CLIENTS);
				blocksLeft--;
			}
			return;
		}
		BlockState fill = surface.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : surface;
		for (int h = 1; h <= height; h++) {
			BlockState there = level.getBlockState(pos.set(bx, top + h, bz));
			if (!there.canBeReplaced() || !there.getFluidState().isEmpty()) {
				break;
			}
			level.setBlock(pos, h == height ? scorch(surface) : fill, Block.UPDATE_CLIENTS);
			blocksLeft--;
		}
	}

	private BlockState scorch(BlockState surface) {
		if (surface.is(Blocks.GRASS_BLOCK)) {
			return random.nextInt(3) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		}
		return surface;
	}

	/** The floor of a column dug out: shocked and melted rock, and in the middle the meteorite itself and its iron. */
	private void line(Crater c, int bx, int bz, double d, BlockPos.MutableBlockPos pos) {
		int floor = floorY(c, d);
		if (!level.getBlockState(pos.set(bx, floor + 1, bz)).isAir()) {
			// Not dug out: under water, or what couldn't be broken.
			return;
		}
		if (!sound(level.getBlockState(pos.set(bx, floor, bz)), pos)) {
			return;
		}
		level.setBlock(pos, lining(d / c.radius), Block.UPDATE_CLIENTS);
		blocksLeft--;
	}

	private BlockState lining(double out) {
		if (out < 0.3) {
			int roll = random.nextInt(20);
			return roll < 11 ? ModBlocks.METEORITE.defaultBlockState() : roll < 17 ? Blocks.RAW_IRON_BLOCK.defaultBlockState()
					: Blocks.MAGMA_BLOCK.defaultBlockState();
		}
		int roll = random.nextInt(10);
		return roll < 3 ? Blocks.MAGMA_BLOCK.defaultBlockState() : roll < 6 ? Blocks.BLACKSTONE.defaultBlockState()
				: roll < 8 ? Blocks.BASALT.defaultBlockState() : Blocks.TUFF.defaultBlockState();
	}

	/** Whether a block is solid rock or soil the crater may change. */
	private boolean sound(BlockState state, BlockPos pos) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && !state.canBeReplaced()
				&& state.getDestroySpeed(level, pos) >= 0.0F;
	}

	/**
	 * The molten middle of the floor: pockets of lava where rock holds them in on every side and below, so that they stay
	 * where they are, and fire flickering on the hot rock (on rock that doesn't burn, so it soon dies down).
	 */
	private void melt(Crater c, int bx, int bz, double d, BlockPos.MutableBlockPos pos) {
		int floor = floorY(c, d);
		if (!level.getBlockState(pos.set(bx, floor + 1, bz)).isAir()) {
			return;
		}
		if (c.lava > 0 && d >= 1.0 && random.nextInt(3) == 0 && pocket(bx, floor, bz)) {
			level.setBlock(pos.set(bx, floor, bz), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
			c.lava--;
			lavaPlaced++;
			blocksLeft--;
			return;
		}
		if (c.fires > 0 && random.nextInt(3) == 0) {
			BlockState under = level.getBlockState(pos.set(bx, floor, bz));
			// Not on magma: fire on it would burn for ever.
			if (under.is(Blocks.MAGMA_BLOCK) || !under.getFluidState().isEmpty()) {
				return;
			}
			pos.set(bx, floor + 1, bz);
			if (BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
				level.setBlock(pos, BaseFireBlock.getState(level, pos), Block.UPDATE_ALL);
				c.fires--;
				blocksLeft--;
			}
		}
	}

	/** Whether lava laid at (bx, by, bz) would stay there: rock (or more lava) on every side and below. */
	private boolean pocket(int bx, int by, int bz) {
		return holds(bx, by, bz) && holds(bx - 1, by, bz) && holds(bx + 1, by, bz) && holds(bx, by, bz - 1) && holds(bx, by, bz + 1)
				&& holds(bx, by - 1, bz);
	}

	private boolean holds(int bx, int by, int bz) {
		if (!loaded(bx, bz) || by <= level.getMinY()) {
			return false;
		}
		BlockPos at = new BlockPos(bx, by, bz);
		BlockState state = level.getBlockState(at);
		return state.is(Blocks.LAVA) || state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(level, at);
	}

	/**
	 * Lays the forest round a crater waste, a few tries a tick over some seconds: the trees near it felled pointing away
	 * from it (so many a second), those further out stripped of their leaves. Returns false once it is done.
	 */
	private boolean devastate(Crater c) {
		double outer = c.radius * 4.5;
		for (int i = 0; i < 3 && c.forest > 0 && blocksLeft > 0; i++) {
			c.forest--;
			double angle = random.nextDouble() * Math.PI * 2.0;
			double d = c.rim + random.nextDouble() * Math.max(1.0, outer - c.rim);
			int bx = (int) Math.floor(c.x + 0.5 + Math.cos(angle) * d);
			int bz = (int) Math.floor(c.z + 0.5 + Math.sin(angle) * d);
			if (!loaded(bx, bz) || random.nextDouble() > 1.25 - d / outer) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
			BlockPos log = new BlockPos(bx, top, bz);
			if (!TreeFeller.isLog(level.getBlockState(log))) {
				continue;
			}
			if (d < c.radius * 3.0 && fellTree(log, new Vec3(Math.cos(angle), 0.0, Math.sin(angle)), 0, TREES_PER_SECOND)) {
				continue;
			}
			strip(bx, bz);
		}
		return c.forest > 0;
	}

	/** Strips a tree's crown of its leaves round its trunk, as the blast does further out. */
	private void strip(int bx, int bz) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int sx = bx + dx;
				int sz = bz + dz;
				if (!loaded(sx, sz)) {
					continue;
				}
				int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz) - 1, level.getMaxY() - 1);
				BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(sx, top, sz);
				for (int i = 0; i < 8 && pos.getY() > level.getMinY() && blocksLeft > 0; i++) {
					BlockState state = level.getBlockState(pos);
					if (state.is(BlockTags.LEAVES)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						blocksLeft--;
					} else if (!state.isAir() && !TreeFeller.isLog(state)) {
						break;
					}
					pos.move(0, -1, 0);
				}
			}
		}
	}

	/**
	 * The heat of a big strike sets alight what will burn round the crater: a wildfire of its own, kindled all round
	 * just outside the rim (unless it rains, or enough are burning already).
	 */
	private void kindle(Crater c) {
		wildfires.removeIf(Disaster::isDone);
		if (!c.kindles || !griefing() || level.isRaining() || wildfires.size() >= MAX_WILDFIRES) {
			return;
		}
		float heat = (float) Math.max(1.0, Math.min(3.5, 1.0 + c.radius / 8.0));
		wildfires.add(Disasters.add(new Wildfire(level, new Vec3(c.x + 0.5, c.y + 1.0, c.z + 0.5), heat, natural, c.rim + 2.0)));
	}

	/** The column offsets within `reach` of the middle, nearest first, as pairs (dx, dz). */
	private static int[] offsets(int reach) {
		List<int[]> all = new ArrayList<>();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (dx * dx + dz * dz <= reach * reach) {
					all.add(new int[] {dx, dz});
				}
			}
		}
		all.sort(Comparator.comparingInt((int[] o) -> o[0] * o[0] + o[1] * o[1]));
		int[] out = new int[all.size() * 2];
		for (int i = 0; i < all.size(); i++) {
			out[i * 2] = all.get(i)[0];
			out[i * 2 + 1] = all.get(i)[1];
		}
		return out;
	}

	/** How many of {@link #OFFSETS} lie closer than `reach` to the middle (they come nearest first). */
	private static int within(double reach) {
		double limit = reach * reach;
		int n = 0;
		while (n < OFFSETS.length / 2 && OFFSETS[n * 2] * OFFSETS[n * 2] + OFFSETS[n * 2 + 1] * OFFSETS[n * 2 + 1] < limit) {
			n++;
		}
		return n;
	}

	// --- for the tests -----------------------------------------------------------------------

	/** Fireballs sent down so far. */
	public int launched() {
		return launched;
	}

	/** Fireballs that have struck the ground so far (and impacts called for). */
	public int strikes() {
		return strikes;
	}

	/** Fireballs that have burst in the air so far. */
	public int bursts() {
		return bursts;
	}

	/** Fireballs in the air right now. */
	public int inFlight() {
		return coming.size();
	}

	/** Craters still being dug. */
	public int craterWork() {
		return digging.size();
	}

	/** Blocks of lava laid in the craters' floors so far. */
	public int lavaPlaced() {
		return lavaPlaced;
	}

	/** Blocks of ejecta flying right now (never more than {@link #MAX_EJECTA}). */
	public int ejectaFlying() {
		return ejecta.size();
	}

	/** Whether a great fireball is still to come. */
	public boolean greatComing() {
		return greatAt >= 0;
	}
}
