package com.naturesmod.disaster;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.naturesmod.tree.TreeFeller;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * down, then it tracks across the land with its parent storm (mostly toward the northeast), wandering
 * a little, until it weakens, ropes out and lifts. Its winds turn as a Rankine vortex, fastest at the
 * edge of the core: they pull in what stands near, lift it up the core and fling it out of the top.
 * What it tears from the land depends on how strong a gust each thing can stand: leaves and plants in
 * the weakest, roofs and walls of wood from EF2, masonry only in the violent ones. Shelter (a roof
 * over your head, a cellar) keeps most of the wind off you. Lightning strikes out of the storm around it.
 */
public final class Tornado extends Disaster {
	private static final double[] CORE = {3.0, 4.0, 6.0, 8.0, 11.0, 15.0};
	private static final double[] PEAK_WIND = {33.0, 43.0, 54.0, 68.0, 84.0, 105.0};
	private static final int[] LIFETIME = {1400, 2000, 2600, 3200, 4000, 4800};
	private static final int FORMING = 240;

	private final int rating;
	private final double core;
	private final double peakWind;
	private final double speed;
	private final double gustPhase;
	private final Map<UUID, Vec3> lastSeen = new HashMap<>();
	private double heading;
	private double turn;
	private double wind;
	private double coreNow;
	private double cloudBase;
	private boolean touchedDown;
	private boolean overWater;
	private int debrisNearby;

	Tornado(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.TORNADO, level, at, intensity, natural, 0);
		rating = Math.round(this.intensity);
		core = CORE[rating] * (0.85 + random.nextDouble() * 0.3);
		peakWind = PEAK_WIND[rating] * (0.95 + random.nextDouble() * 0.1);
		speed = 0.13 + random.nextDouble() * 0.12;
		heading = Math.toRadians(-45.0 + random.nextGaussian() * 30.0);
		duration = (int) (LIFETIME[rating] * (0.8 + random.nextDouble() * 0.4));
		gustPhase = random.nextDouble() * 100.0;
		y = ground((int) Math.floor(x), (int) Math.floor(z)) + 1;
		cloudBase = y + 60 + random.nextInt(20);
		coreNow = core;
	}

	/** Sets it on a track that passes near a place. */
	public void aim(Vec3 target) {
		heading = Math.atan2(target.z - z, target.x - x) + Math.toRadians(random.nextGaussian() * 12.0);
	}

	@Override
	protected void begin() {
		if (!natural && !level.isThundering()) {
			// A tornado needs its supercell: the sky closes in.
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

	@Override
	public float[] extra() {
		double fade = fade();
		double tilt = 0.1 + 0.55 * (1.0 - fade);
		return new float[] {(float) coreNow, (float) cloudBase, rating, (float) wind, (float) formation(), (float) (-Math.cos(heading) * tilt),
				(float) (-Math.sin(heading) * tilt), overWater ? 1.0F : 0.0F, (float) fade};
	}

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
		if (touchedDown && wind > 20.0) {
			affectEntities();
			if (griefing() && !overWater) {
				if (age % 20 == 0) {
					debrisNearby = level.getEntitiesOfClass(FallingBlockEntity.class, new AABB(x - 80, y - 20, z - 80, x + 80, y + 120, z + 80)).size();
				}
				damageLand();
			}
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

	private void move(double fade) {
		// A tornado's track runs nearly straight, bending gently this way and that.
		turn += random.nextGaussian() * 0.00012;
		turn = Math.max(-0.002, Math.min(0.002, turn)) * 0.99;
		// Roping out, it hooks to the left as the storm occludes.
		double hook = fade < 0.6 ? -0.0012 : 0.0;
		heading += turn + hook;
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

	/** Pulls in, lifts and flings what stands in its wind; batters what is in its core. */
	private void affectEntities() {
		double peak = wind * Vortex.WIND_SCALE;
		double reach = Math.max(core * 5.0, 26.0);
		double depth = cloudBase - y;
		double ceiling = 12.0 + rating * 9.0;
		AABB box = new AABB(x - reach, y - 6, z - reach, x + reach, cloudBase, z + reach);
		Map<UUID, Vec3> seen = new HashMap<>();
		for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> e.isAlive() && !immune(e))) {
			double dx = entity.getX() - x;
			double dz = entity.getZ() - z;
			double r = Math.sqrt(dx * dx + dz * dz);
			if (r > reach) {
				continue;
			}
			double height = Math.max(0.0, entity.getY() - y);
			Vec3 air = Vortex.air(dx, dz, height, coreNow, peak, depth);
			if (height > ceiling) {
				air = new Vec3(air.x, Math.min(air.y, 0.0), air.z);
			}
			double response;
			if (entity instanceof ItemEntity || entity instanceof FallingBlockEntity) {
				response = 0.25;
			} else if (entity instanceof Player) {
				response = 0.11;
			} else {
				response = 0.13 / Math.max(1.0, entity.getBbWidth() * entity.getBbHeight() * 0.8);
			}
			if (sheltered(entity)) {
				response *= 0.06;
			}
			double gust = air.horizontalDistance();
			if (entity.onGround() && !(entity instanceof ItemEntity) && gust < 0.3) {
				// Feet on the ground hold against a light wind.
				response *= gust / 0.3 * 0.6;
			}
			Vec3 velocity = entity.getDeltaMovement();
			if (entity instanceof Player) {
				// The server doesn't move players itself: how fast one goes is how far it went since last tick.
				Vec3 before = lastSeen.get(entity.getUUID());
				velocity = before == null ? Vec3.ZERO : entity.position().subtract(before);
				seen.put(entity.getUUID(), entity.position());
			}
			// A body tumbling in the updraft catches far more of it than it does of the wind going round: enough
			// for a strong tornado to lift cattle, and people.
			Vec3 next = velocity.add(air.subtract(velocity).multiply(response, response * 2.4, response));
			if (next.length() > 2.2) {
				next = next.normalize().scale(2.2);
			}
			setVelocity(entity, next);
			if (entity instanceof LivingEntity && rating >= 1 && r < coreNow * 1.15 && age % 10 == 0) {
				// Debris in the core.
				hurt(entity, DisasterDamage.TORNADO, 0.5F + rating * 0.7F);
			}
		}
		lastSeen.clear();
		lastSeen.putAll(seen);
	}

	/** Tears at the land under it: from the top of each column down, as long as what it meets gives way. */
	private void damageLand() {
		int samples = 6 + rating * 9;
		double reach = coreNow * 1.5 + 2.0;
		int depth = 2 + rating * 2;
		Vec3 dir = direction();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < samples; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = reach * Math.sqrt(random.nextDouble());
			int bx = (int) Math.floor(x + Math.cos(angle) * r);
			int bz = (int) Math.floor(z + Math.sin(angle) * r);
			if (!loaded(bx, bz)) {
				continue;
			}
			// The winds are strongest at the edge of the core, and the storm's own motion adds on one side.
			double profile = r < coreNow ? 0.6 + 0.4 * r / coreNow : Math.pow(coreNow / r, 0.6);
			double tangentialX = Math.sin(angle);
			double tangentialZ = -Math.cos(angle);
			double motion = speed * 20.0 * (tangentialX * dir.x + tangentialZ * dir.z);
			double local = wind * profile + motion;
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
			Vec3 throwDir = new Vec3(tangentialX, 0.0, tangentialZ);
			for (int by = top; by > top - depth && by > level.getMinY(); by--) {
				pos.set(bx, by, bz);
				BlockState state = level.getBlockState(pos);
				if (state.isAir()) {
					continue;
				}
				if (!state.getFluidState().isEmpty()) {
					break;
				}
				double threshold = WindDamage.threshold(level, pos, state);
				double gust = local * (0.8 + 0.4 * random.nextDouble());
				if (gust < threshold) {
					break;
				}
				if (TreeFeller.isLog(state)) {
					fellTree(pos.immutable(), throwDir, random.nextInt(3), 1 + rating / 2);
					break;
				}
				if (WindDamage.isGround(state)) {
					scour(pos, state, gust);
					break;
				}
				tearAway(pos.immutable(), state, throwDir, gust);
			}
		}
	}

	/** The ground itself: the worst winds strip the turf off it. */
	private void scour(BlockPos pos, BlockState state, double gust) {
		if (gust > 70.0 && state.is(Blocks.GRASS_BLOCK) && random.nextInt(3) == 0) {
			level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
			level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.4, 0.2, 0.4, 0.05);
		} else if (gust > 95.0 && random.nextInt(8) == 0 && state.is(Blocks.DIRT) && debrisNearby <= 30 + rating * 15) {
			launch(pos, state, new Vec3(0, 1, 0), gust);
		}
	}

	private void tearAway(BlockPos pos, BlockState state, Vec3 throwDir, double gust) {
		if (state.hasBlockEntity() || WindDamage.shatters(state) || !state.isCollisionShapeFullBlock(level, pos) || debrisNearby > 30 + rating * 15) {
			// Shredded: now and then something of it is left to fall.
			level.destroyBlock(pos, state.hasBlockEntity() || random.nextInt(WindDamage.shatters(state) ? 40 : 5) == 0);
			return;
		}
		launch(pos, state, throwDir, gust);
	}

	/** Lifts a block whole into the wind, to come down somewhere else (on whatever is there). */
	private void launch(BlockPos pos, BlockState state, Vec3 throwDir, double gust) {
		FallingBlockEntity debris = FallingBlockEntity.fall(level, pos, state);
		double speed = gust * Vortex.WIND_SCALE;
		debris.setDeltaMovement(throwDir.x * speed, 0.35 + random.nextDouble() * 0.45, throwDir.z * speed);
		debris.setHurtsEntities(1.5F, 12);
		debrisNearby++;
	}
}
