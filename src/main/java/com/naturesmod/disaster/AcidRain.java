package com.naturesmod.disaster;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.naturesmod.NaturesConfig;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Acid rain: a sickly yellow-green rain out of a low, dirty sky, two or three hundred blocks across, drifting
 * slowly on its wind for a few minutes and coming in heavier and lighter showers. Out in it whatever lives is
 * burnt: a sting now and then at its mildest, steadily at its worst. A helmet keeps much of it off (and is eaten
 * away in its place), a roof or the water all of it; in the worst of it the drips through the crowns of trees
 * burn too. The land suffers under it: the leaves at the top of a tree wither and fall away, so that it is
 * stripped from the top down as the rain goes on; grass dies back to bare dirt; crops, flowers and the small
 * plants of the meadow shrivel, now and then leaving a dead bush where they stood.
 *
 * <p>The work is bounded every tick: so many columns of land looked at about each player under it (and a few
 * anywhere in its reach), so many blocks changed in all, however many players there are; the living things
 * about the players are looked for once a second, so many at most.
 */
public final class AcidRain extends Disaster {
	/** At most this many blocks changed a tick, all players together. */
	private static final int MAX_CHANGES = 48;
	/** At most this many columns looked at a tick, all players together. */
	private static final int MAX_COLUMNS = 192;
	/** At most this many withering blocks shown crumbling a tick. */
	private static final int MAX_EFFECTS = 3;
	/** How far about each player the land is eaten where it is seen (only a little of it further off). */
	private static final double NEAR = 48.0;
	/** Things other than players looked at about the players, once a second. */
	private static final int MAX_NEARBY = 48;
	/** Over how many blocks its edge thins out to nothing. */
	private static final double EDGE = 40.0;
	/** How fast the whole storm drifts with its wind, in blocks a tick. */
	private static final double DRIFT = 0.03;

	private final double reach;
	/** How far the drops are blown sideways for every block they fall. */
	private final double slant;
	private double heading;
	private double veer;
	private Vec3 wind;
	/** How heavy the rain is now: some 0.45 between the showers to 1 in the heaviest of them. */
	private double downpour = 0.7;
	private double downpourTarget = 0.7;
	private int showerLeft;
	/** Players already told they are under it. */
	private final Set<UUID> warned = new HashSet<>();
	/** Players already told it is burning them. */
	private final Set<UUID> burnt = new HashSet<>();
	/** Columns that may still be looked at this tick. */
	private int columnsLeft;
	private int effectsLeft;
	private int leavesWithered;
	private int grassKilled;
	private int plantsKilled;
	private int burns;

	AcidRain(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.ACID_RAIN, level, at, intensity, natural, (int) (2400 + 720 * DisasterType.ACID_RAIN.clamp(intensity)));
		heading = random.nextDouble() * Math.PI * 2.0;
		wind = new Vec3(Math.cos(heading), 0.0, Math.sin(heading));
		reach = 140.0 + 22.0 * this.intensity;
		slant = 0.12 + 0.04 * this.intensity;
		y = ground((int) Math.floor(x), (int) Math.floor(z)) + 1;
	}

	@Override
	public float radius() {
		return (float) reach;
	}

	@Override
	public Vec3 direction() {
		return wind;
	}

	@Override
	public float strength() {
		float in = Math.min(1.0F, age / 200.0F);
		float out = Math.min(1.0F, (duration - age) / 300.0F);
		return Math.max(0.0F, Math.min(in, out));
	}

	/** For its clients: how heavy the rain is now, and how far its drops are blown sideways for every block they fall. */
	@Override
	public float[] extra() {
		return new float[] {(float) downpour, (float) slant};
	}

	// --- for the tests ------------------------------------------------------------------------

	/** How heavy the rain is now: some 0.45 between the showers to 1 in the heaviest of them. */
	public double downpour() {
		return downpour;
	}

	/**
	 * The chance a second that the rain burns something out in the thick of it, at its height and in its heaviest
	 * shower: one in twenty at level 1, three in four at level 5.
	 */
	public double burnChance() {
		double s = Math.max(0.0, Math.min(1.0, (intensity - 1.0) / 4.0));
		return 0.05 + 0.7 * Math.pow(s, 1.3);
	}

	/** How deep under it a point is: 0 outside, 1 well inside (its edge thins out over some forty blocks). */
	public double inside(double px, double pz) {
		double dx = px - x;
		double dz = pz - z;
		double d = Math.sqrt(dx * dx + dz * dz);
		return d >= reach ? 0.0 : Math.min(1.0, (reach - d) / EDGE);
	}

	/** Whether the rain gets at something: under the open sky (no roof over it, not even of glass) and not in the water. */
	public boolean exposed(LivingEntity entity) {
		return !entity.isInWater() && !sheltered(entity) && !roofed(entity);
	}

	/** How many times it has burnt something so far, players and animals together. */
	public int burns() {
		return burns;
	}

	/** Leaves it has withered away so far. */
	public int leavesWithered() {
		return leavesWithered;
	}

	/** Grass blocks it has killed back to dirt so far. */
	public int grassKilled() {
		return grassKilled;
	}

	/** Crops, flowers and other small plants it has killed so far. */
	public int plantsKilled() {
		return plantsKilled;
	}

	/**
	 * Lets the acid at one column of land once, as if the rain there did its worst (for tests): the plant standing on
	 * it dies, or else the leaves at its top wither, or else its grass dies back. True if a block changed.
	 */
	public boolean eatAway(BlockPos column) {
		return eat(column.getX(), column.getZ(), true);
	}

	// --- the storm ----------------------------------------------------------------------------

	@Override
	protected void begin() {
		weather();
		Component moving = compass(wind.x, wind.z);
		warn(reach + 250.0, player -> {
			if (distance2d(player, x, z) < reach) {
				warned.add(player.getUUID());
				return Component.translatable("naturesmod.warning.acid_rain");
			}
			return Component.translatable("naturesmod.warning.acid_rain_coming", compass(player.getX(), player.getZ(), x, z), moving);
		});
	}

	@Override
	protected void tick() {
		drift();
		showers();
		if (age % 200 == 0 && age < duration - 400) {
			// The rain the sky was already shedding may run out before this does.
			weather();
		}
		float strength = strength();
		if (strength <= 0.0F) {
			return;
		}
		if (age % 20 == 0) {
			burn(strength);
		}
		if (age % 40 == 0) {
			warnUnder();
		}
		if (griefing()) {
			corrode(strength);
		}
		if (natural && age % 200 == 0 && playersWithin(reach + 400.0).isEmpty()) {
			end();
		}
	}

	@Override
	protected void finish() {
		warned.clear();
		burnt.clear();
	}

	/** The sky clouds over and rains while it lasts, whatever it was doing (in the overworld: nowhere else has weather). */
	private void weather() {
		if (level.dimension() == Level.OVERWORLD && !level.isRaining()) {
			level.getServer().setWeatherParameters(0, Math.max(1200, duration - age + 600), true, false);
		}
	}

	/** It drifts on with its wind, the wind veering a little as it goes. */
	private void drift() {
		veer = Math.max(-0.0005, Math.min(0.0005, veer * 0.995 + random.nextGaussian() * 0.00002));
		heading += veer;
		wind = new Vec3(Math.cos(heading), 0.0, Math.sin(heading));
		x += wind.x * DRIFT;
		z += wind.z * DRIFT;
	}

	/** The rain comes in showers: every so often a heavier one, between them lighter rain. */
	private void showers() {
		if (--showerLeft <= 0) {
			showerLeft = 100 + random.nextInt(200);
			downpourTarget = random.nextInt(3) == 0 ? 0.9 + random.nextDouble() * 0.1 : 0.45 + random.nextDouble() * 0.35;
		}
		downpour += (downpourTarget - downpour) * 0.02;
	}

	/** A player who comes in under it is told what it is, once. */
	private void warnUnder() {
		if (!NaturesConfig.get().disasterWarnings) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && distance2d(player, x, z) < reach && warned.add(player.getUUID())) {
				player.sendSystemMessage(Component.translatable("naturesmod.warning.acid_rain").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	// --- the burns ----------------------------------------------------------------------------

	/** Once a second: the rain on the players out in it, and on the animals and monsters about them (so many at most). */
	private void burn(float strength) {
		double chance = burnChance();
		float amount = intensity >= 4.5F ? 1.5F : 1.0F;
		Set<LivingEntity> seen = new HashSet<>();
		int looked = 0;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			double d = distance2d(player, x, z);
			if (d > reach) {
				continue;
			}
			double force = strength * downpour * Math.min(1.0, (reach - d) / EDGE);
			if (!immune(player)) {
				burnPlayer(player, chance * force, amount);
			}
			if (looked >= MAX_NEARBY) {
				continue;
			}
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24.0), e -> e.isAlive() && !(e instanceof Player))) {
				if (looked >= MAX_NEARBY) {
					break;
				}
				if (!seen.add(entity)) {
					continue;
				}
				looked++;
				if (distance2d(entity, x, z) > reach || !exposed(entity)) {
					continue;
				}
				// Hide and fur and scales: the animals stand it a little better.
				if (random.nextDouble() < chance * force * 0.75) {
					hurt(entity, DisasterDamage.ACID_RAIN, amount);
					burns++;
				}
			}
		}
	}

	/**
	 * The rain on a player out in it: now and then a burn. A helmet keeps much of it off and is eaten away instead, a
	 * little wear each time; in a storm of level 4 or 5 the drips through the crowns of trees burn too, less often.
	 */
	private void burnPlayer(ServerPlayer player, double chance, float amount) {
		if (player.isInWater()) {
			return;
		}
		if (sheltered(player) || roofed(player)) {
			if (intensity < 3.5F || !dripping(player)) {
				return;
			}
			chance *= 0.25;
		}
		if (!(random.nextDouble() < chance)) {
			return;
		}
		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
		boolean covered = !helmet.isEmpty();
		if (covered) {
			helmet.hurtAndBreak(intensity >= 3.5F ? 2 : 1, player, EquipmentSlot.HEAD);
			amount *= 0.4F;
		}
		hurt(player, DisasterDamage.ACID_RAIN, amount);
		burns++;
		if (NaturesConfig.get().disasterWarnings && burnt.add(player.getUUID())) {
			String key = covered ? "naturesmod.warning.acid_rain_helmet" : "naturesmod.warning.acid_rain_burning";
			player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD));
		}
	}

	/**
	 * Whether anything the rain stops on is over an entity's head. Glass lets the sky's light through, so that a
	 * greenhouse doesn't count as shelter by the light alone, but it keeps the rain off as well as any roof (as it
	 * keeps it off the crops under it).
	 */
	private boolean roofed(LivingEntity entity) {
		int bx = (int) Math.floor(entity.getX());
		int bz = (int) Math.floor(entity.getZ());
		return loaded(bx, bz) && level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) > entity.getEyeY();
	}

	/** Whether the rain drips on something through the crowns of trees over it, with nothing else between. */
	private boolean dripping(LivingEntity entity) {
		int bx = (int) Math.floor(entity.getX());
		int bz = (int) Math.floor(entity.getZ());
		if (!loaded(bx, bz)) {
			return false;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		double over = top - entity.getEyeY();
		return over > 0.0 && over < 24.0 && level.getBlockState(new BlockPos(bx, top - 1, bz)).is(BlockTags.LEAVES);
	}

	// --- the land -----------------------------------------------------------------------------

	/**
	 * The acid on the land: so many columns a tick about each player under it, within a few dozen blocks (where it is
	 * seen); a few more anywhere in its reach. The heavier the rain, the more of them.
	 */
	private void corrode(float strength) {
		int per = Math.round((10.0F + 8.0F * intensity) * strength * (float) downpour);
		int changes = MAX_CHANGES;
		columnsLeft = MAX_COLUMNS;
		effectsLeft = MAX_EFFECTS;
		double far = reach + NEAR;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || changes <= 0 || columnsLeft <= 0) {
				continue;
			}
			double dx = player.getX() - x;
			double dz = player.getZ() - z;
			if (dx * dx + dz * dz > far * far) {
				continue;
			}
			changes = fall(player.getX(), player.getZ(), NEAR, per, changes);
		}
		fall(x, z, reach, Math.round(6.0F * strength), changes);
	}

	/** The acid on `samples` columns about a place, no more than `changes` blocks changed; returns how many changes are left. */
	private int fall(double cx, double cz, double spread, int samples, int changes) {
		for (int i = 0; i < samples && changes > 0 && columnsLeft > 0; i++) {
			columnsLeft--;
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = spread * Math.sqrt(random.nextDouble());
			double px = cx + Math.cos(angle) * r;
			double pz = cz + Math.sin(angle) * r;
			double dx = px - x;
			double dz = pz - z;
			double from = Math.sqrt(dx * dx + dz * dz);
			// Thinning out toward its edge.
			if (from > reach || from > reach - EDGE && random.nextDouble() * EDGE > reach - from) {
				continue;
			}
			if (eat((int) Math.floor(px), (int) Math.floor(pz), false)) {
				changes--;
			}
		}
		return changes;
	}

	/**
	 * The acid on one column, where the rain lands on it: a plant standing there shrivels, else the leaves at the top
	 * of a tree wither, else grass dies back to dirt; on anything else (stone, sand, wood, a roof, water, snow) it
	 * does nothing. Each has its chance of happening, the better the stronger the storm, unless `sure`. True if a
	 * block changed.
	 */
	private boolean eat(int bx, int bz, boolean sure) {
		if (!loaded(bx, bz)) {
			return false;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		if (top <= level.getMinY() + 1 || top >= level.getMaxY()) {
			return false;
		}
		double s = Math.max(0.0, Math.min(1.0, (intensity - 1.0) / 4.0));
		BlockPos at = new BlockPos(bx, top, bz);
		BlockState there = level.getBlockState(at);
		if (!there.isAir()) {
			// Plants don't stop a body: they stand on the top of the column, and the rain finds them first.
			if (!plant(there) || !sure && random.nextDouble() >= 0.2 + 0.6 * s) {
				return false;
			}
			shrivel(at, there);
			return true;
		}
		BlockPos below = at.below();
		BlockState base = level.getBlockState(below);
		if (base.is(BlockTags.LEAVES)) {
			if (!base.getFluidState().isEmpty() || !sure && random.nextDouble() >= 0.12 + 0.7 * s) {
				return false;
			}
			wither(below, base);
			return true;
		}
		if (base.is(Blocks.GRASS_BLOCK)) {
			if (!sure && random.nextDouble() >= 0.08 + 0.5 * s) {
				return false;
			}
			boolean coarse = intensity >= 3.0F && random.nextInt(3) == 0;
			level.setBlock(below, coarse ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
			grassKilled++;
			return true;
		}
		return false;
	}

	/** A plant killed by the acid: gone (the top of a tall one with it), now and then a dead bush left where it stood. */
	private void shrivel(BlockPos at, BlockState state) {
		BlockState dead = Blocks.DEAD_BUSH.defaultBlockState();
		// What stands on it or over it must hear of it: the top half of a tall plant goes too.
		if (!state.is(BlockTags.CROPS) && random.nextInt(4) == 0 && dead.canSurvive(level, at)) {
			level.setBlock(at, dead, Block.UPDATE_ALL);
		} else {
			level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		plantsKilled++;
		crumble(at, state);
	}

	/** Leaves eaten through: they fall away quietly, their neighbours left be. */
	private void wither(BlockPos pos, BlockState state) {
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		leavesWithered++;
		crumble(pos, state);
	}

	/** A few of the things the acid kills are seen to crumble away. */
	private void crumble(BlockPos pos, BlockState state) {
		if (effectsLeft <= 0) {
			return;
		}
		effectsLeft--;
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.35, 0.3, 0.35,
				0.02);
	}

	/** A small plant the acid kills: a crop, a flower, a sapling, grass, a bush (not one already dead, nothing in water). */
	private static boolean plant(BlockState state) {
		return !state.hasBlockEntity() && state.getFluidState().isEmpty() && !state.is(Blocks.DEAD_BUSH)
				&& (state.is(BlockTags.CROPS) || state.is(BlockTags.FLOWERS) || state.is(WorldGenUtil.SAPLINGS) || state.getBlock() instanceof VegetationBlock);
	}
}
