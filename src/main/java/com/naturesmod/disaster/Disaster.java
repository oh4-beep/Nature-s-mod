package com.naturesmod.disaster;

import java.util.ArrayList;
import java.util.List;

import com.naturesmod.NaturesConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A natural disaster under way in a level. The server runs it tick by tick; every few ticks its state
 * goes out to the players of the level, whose clients draw it and make it heard.
 */
public abstract class Disaster {
	private static int nextId = 1;

	public final int id = nextId++;
	public final DisasterType type;
	public final ServerLevel level;
	/** On the scale of its type: EF number, magnitude, explosivity index, level. */
	public final float intensity;
	/** It came about on its own, rather than by command. */
	public final boolean natural;
	protected final RandomSource random;
	protected double x;
	protected double y;
	protected double z;
	protected int age;
	protected int duration;
	private boolean done;

	protected Disaster(DisasterType type, ServerLevel level, Vec3 at, float intensity, boolean natural, int duration) {
		this.type = type;
		this.level = level;
		this.intensity = type.clamp(intensity);
		this.natural = natural;
		this.random = RandomSource.create(level.getRandom().nextLong());
		this.x = at.x;
		this.y = at.y;
		this.z = at.z;
		this.duration = duration;
	}

	/** Runs one tick; the disaster ends once its time is up or it calls {@link #end()}. */
	final void tickDisaster() {
		age++;
		tick();
		if (age >= duration) {
			done = true;
		}
	}

	protected abstract void tick();

	/** When it starts: warnings, weather, anything that happens once. */
	protected void begin() {
	}

	/** When it is over, however it ended: what it leaves behind. */
	protected void finish() {
	}

	public final void end() {
		done = true;
	}

	public final boolean isDone() {
		return done;
	}

	/** Shortens or lengthens what is left of it (for tests and commands). */
	public void setDuration(int ticks) {
		duration = Math.max(age + 1, ticks);
	}

	public int age() {
		return age;
	}

	/** Skips ahead (for tests): as if `ticks` had passed with nothing happening in them. */
	public void skip(int ticks) {
		age = Math.min(duration - 1, age + ticks);
	}

	public int duration() {
		return duration;
	}

	public Vec3 position() {
		return new Vec3(x, y, z);
	}

	/** How far it reaches, in blocks. */
	public abstract float radius();

	/** How far along it is: from 0 as it forms to 1 at its height and back to 0 as it dies away. */
	public float strength() {
		float in = Math.min(1.0F, age / 100.0F);
		float out = Math.min(1.0F, (duration - age) / 200.0F);
		return Math.max(0.0F, Math.min(in, out));
	}

	/** The way it moves or the wind blows in it (a horizontal unit vector, or zero). */
	public Vec3 direction() {
		return Vec3.ZERO;
	}

	/** The numbers of its own its clients need to draw it. */
	public float[] extra() {
		return new float[0];
	}

	DisasterNet.State state() {
		Vec3 dir = direction();
		return new DisasterNet.State(id, type.ordinal(), x, y, z, radius(), intensity, strength(), (float) dir.x, (float) dir.z, age, duration,
				extra());
	}

	/** One line for /disaster list. */
	public String describe() {
		return String.format("#%d %s (%s) at %d %d %d, %ds left", id, type.id, type.scale(intensity), (int) x, (int) y, (int) z,
				Math.max(0, duration - age) / 20);
	}

	// --- helpers for every kind --------------------------------------------------------------

	protected static boolean griefing() {
		return NaturesConfig.get().disasterDamage;
	}

	/**
	 * The y of the top block of a column that isn't a plant or a leaf (over water, the water's surface).
	 * Columns not loaded aren't loaded for it: they are taken to be where the disaster is.
	 */
	protected int ground(int bx, int bz) {
		if (!loaded(bx, bz)) {
			return (int) Math.floor(y) - 1;
		}
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
	}

	/** The y of the top of the land itself in a column: under any trees, plants or snow on it (or the bed of water over it). */
	protected int terrain(int bx, int bz) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, ground(bx, bz), bz);
		if (!loaded(bx, bz)) {
			return pos.getY();
		}
		while (pos.getY() > level.getMinY()) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !state.canBeReplaced() && !state.getCollisionShape(level, pos).isEmpty()) {
				break;
			}
			pos.move(0, -1, 0);
		}
		return pos.getY();
	}

	/** Makes an entity move at `velocity`, by pushing it as much as it takes (which also tells the client). */
	protected static void setVelocity(Entity entity, Vec3 velocity) {
		Vec3 now = entity.getDeltaMovement();
		entity.push(velocity.x - now.x, velocity.y - now.y, velocity.z - now.z);
	}

	protected boolean loaded(int bx, int bz) {
		return level.hasChunkAt(bx, bz);
	}

	protected List<ServerPlayer> playersWithin(double range) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(x, player.getY(), z) <= range * range) {
				out.add(player);
			}
		}
		return out;
	}

	/** Whether an entity is out of the weather: under a roof, or below ground. */
	protected boolean sheltered(Entity entity) {
		return !level.canSeeSky(BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ()));
	}

	protected static boolean immune(Entity entity) {
		return entity instanceof Player player && (player.isCreative() || player.isSpectator());
	}

	protected void hurt(Entity entity, ResourceKey<DamageType> damage, float amount) {
		entity.hurtServer(level, entity.damageSources().source(damage), amount);
	}

	/** Lightning out of the storm, somewhere about `at`. */
	protected void lightning(double lx, double lz) {
		int bx = (int) Math.floor(lx);
		int bz = (int) Math.floor(lz);
		if (!loaded(bx, bz)) {
			return;
		}
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setPos(bx + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz), bz + 0.5);
			level.addFreshEntity(bolt);
		}
	}

	/** Warns the players within `range` blocks, if warnings are on. */
	protected void warn(double range, java.util.function.Function<ServerPlayer, Component> message) {
		if (!NaturesConfig.get().disasterWarnings) {
			return;
		}
		for (ServerPlayer player : playersWithin(range)) {
			player.sendSystemMessage(message.apply(player).copy().withStyle(ChatFormatting.GOLD));
		}
	}

	/** Which way something at (tx, tz) lies from (fx, fz), as a compass word. */
	protected static Component compass(double fx, double fz, double tx, double tz) {
		return compass(tx - fx, tz - fz);
	}

	protected static Component compass(double dx, double dz) {
		// Minecraft's north is -z, east +x.
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int sector = (int) Math.floorMod(Math.round(angle / 45.0), 8);
		String[] names = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
		return Component.translatable("naturesmod.compass." + names[sector]);
	}

	protected static double distance2d(Entity entity, double px, double pz) {
		double dx = entity.getX() - px;
		double dz = entity.getZ() - pz;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
