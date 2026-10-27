package com.naturesmod.disaster;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.naturesmod.block.ModBlocks;
import com.naturesmod.tree.TreeFeller;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
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
 * most are specks that burn up high and are only seen (the clients draw them), but now and then a
 * fireball comes through. The smaller fireballs burn up overhead in a flash, the bigger ones burst in
 * the air (a flash, then seconds later the sonic boom, windows blowing in), and the biggest strike the
 * ground: a crater with a raised rim, ejecta thrown about, molten rock and meteoric iron in its floor,
 * trees blown flat all around (like at Tunguska) and fires lit by the heat.
 */
public final class MeteorShower extends Disaster {
	/** How fast fireballs come in, in blocks a tick. */
	private static final double SPEED = 3.5;
	/** The height they are first seen at. */
	private static final double ENTRY = 420.0;

	private final Vec3 radiant;
	private final List<Fireball> coming = new ArrayList<>();
	private int nextFireball;

	private record Fireball(Vec3 end, double size, int fate, int arrives) {
	}

	MeteorShower(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.METEOR_SHOWER, level, at, intensity, natural, (int) (2400 + 600 * intensity));
		double azimuth = random.nextDouble() * Math.PI * 2.0;
		double elevation = Math.toRadians(30.0 + random.nextDouble() * 35.0);
		radiant = new Vec3(Math.cos(azimuth) * Math.cos(elevation), -Math.sin(elevation), Math.sin(azimuth) * Math.cos(elevation));
		nextFireball = 160 + random.nextInt(200);
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
		return new float[] {(float) radiant.x, (float) radiant.y, (float) radiant.z};
	}

	@Override
	protected void begin() {
		warn(500, player -> Component.translatable("naturesmod.warning.meteors"));
	}

	@Override
	protected void tick() {
		if (age >= nextFireball && age < duration - 300) {
			launch();
			nextFireball = age + (int) (-Math.log(1.0 - random.nextDouble()) * 900.0 / intensity) + 40;
		}
		for (Iterator<Fireball> it = coming.iterator(); it.hasNext(); ) {
			Fireball fireball = it.next();
			if (age >= fireball.arrives) {
				it.remove();
				if (fireball.fate == 2) {
					impact(BlockPos.containing(fireball.end), fireball.size);
				} else if (fireball.fate == 1) {
					airburst(fireball.end, fireball.size);
				}
			}
		}
		if (natural && level.isBrightOutside() && age > 200) {
			end();
		}
	}

	/** Sends a fireball down now (for commands and tests). */
	public void launchNow() {
		launch();
	}

	/** Sends a fireball down at somewhere near a player (or the middle of the shower), but not right on anyone. */
	private void launch() {
		List<ServerPlayer> near = playersWithin(500);
		Vec3 around = near.isEmpty() ? position() : near.get(random.nextInt(near.size())).position();
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = 40.0 + random.nextDouble() * 220.0;
		int tx = (int) Math.floor(around.x + Math.cos(angle) * distance);
		int tz = (int) Math.floor(around.z + Math.sin(angle) * distance);
		if (!loaded(tx, tz)) {
			return;
		}
		double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz);
		double size = Math.exp(random.nextGaussian() * 0.6 - 0.3) * (0.8 + 0.1 * intensity);
		int fate = size < 0.9 ? 0 : size < 2.0 ? 1 : 2;
		double endY = switch (fate) {
			case 0 -> ground + 160.0 + random.nextDouble() * 80.0;
			case 1 -> ground + 60.0 + random.nextDouble() * 70.0;
			default -> ground;
		};
		Vec3 target = new Vec3(tx + 0.5, ground, tz + 0.5);
		// Back along the radiant to where it enters the air.
		double back = (ENTRY - ground) / -radiant.y;
		Vec3 start = target.subtract(radiant.scale(back));
		double travelled = (endY - start.y) / radiant.y;
		Vec3 end = start.add(radiant.scale(travelled));
		int flight = (int) Math.ceil(travelled / SPEED);
		Vec3 velocity = radiant.scale(SPEED);
		DisasterNet.send(level, new DisasterNet.Event(DisasterNet.Event.METEOR, start.x, start.y, start.z, new float[] {(float) velocity.x,
				(float) velocity.y, (float) velocity.z, (float) size, fate, flight}), 0);
		coming.add(new Fireball(end, size, fate, age + flight));
	}

	/** Bursting in the air: a blinding flash and a shock wave that blows windows in below. */
	private void airburst(Vec3 at, double size) {
		DisasterNet.send(level, new DisasterNet.Event(DisasterNet.Event.AIRBURST, at.x, at.y, at.z, new float[] {(float) size}), 0);
		double reach = 50.0 + size * 25.0;
		for (ServerPlayer player : level.players()) {
			double d = player.position().distanceTo(at);
			if (d > reach * 1.6 || player.isSpectator()) {
				continue;
			}
			Vec3 away = player.position().subtract(at).normalize().scale(Math.min(0.8, 0.25 * reach / Math.max(d, 10.0)));
			shove(player, away.x, Math.abs(away.y) * 0.3, away.z);
			if (griefing()) {
				shatterGlass(player.blockPosition(), (int) Math.min(20, 8 + size * 4));
			}
		}
	}

	private void shatterGlass(BlockPos around, int reach) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 400; i++) {
			pos.set(around.getX() + random.nextInt(reach * 2 + 1) - reach, around.getY() + random.nextInt(reach) - reach / 3,
					around.getZ() + random.nextInt(reach * 2 + 1) - reach);
			if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath().contains("glass") && random.nextInt(3) > 0) {
				level.destroyBlock(pos, false);
			}
		}
	}

	/** Strikes the ground: blows out a crater, throws the ejecta about and sets the land around it alight. */
	public void impact(BlockPos ground, double size) {
		int radius = (int) Math.min(11, Math.round(2.0 + 1.6 * size));
		double cx = ground.getX() + 0.5;
		double cz = ground.getZ() + 0.5;
		DisasterNet.send(level, new DisasterNet.Event(DisasterNet.Event.IMPACT, cx, ground.getY() + 1, cz, new float[] {radius}), 0);
		level.explode(null, cx, ground.getY() + 1.5, cz, (float) (2.0 + size * 1.6), Level.ExplosionInteraction.NONE);
		shockwave(cx, ground.getY(), cz, radius);
		if (griefing()) {
			crater(ground, radius);
			flattenTrees(cx, cz, radius);
		}
	}

	private void crater(BlockPos ground, int radius) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int ejected = 0;
		int rim = (int) Math.ceil(radius * 1.35);
		for (int dx = -rim; dx <= rim; dx++) {
			for (int dz = -rim; dz <= rim; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > rim) {
					continue;
				}
				int bx = ground.getX() + dx;
				int bz = ground.getZ() + dz;
				if (!loaded(bx, bz)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
				BlockState surface = level.getBlockState(pos.set(bx, top, bz));
				if (d < radius) {
					// The bowl: deepest in the middle.
					int depth = (int) Math.round(radius * 0.55 * (1.0 - (d / radius) * (d / radius))) + 1;
					int floor = ground.getY() - depth;
					for (int by = top; by > floor && by > level.getMinY() + 1; by--) {
						BlockState state = level.getBlockState(pos.set(bx, by, bz));
						if (state.is(Blocks.BEDROCK) || state.isAir()) {
							continue;
						}
						if (ejected < 40 && random.nextInt(6) == 0 && state.isCollisionShapeFullBlock(level, pos)) {
							FallingBlockEntity ejecta = FallingBlockEntity.fall(level, pos.immutable(), state);
							double out = 0.5 + random.nextDouble() * 0.7;
							ejecta.setDeltaMovement(dx / Math.max(d, 1.0) * out, 0.6 + random.nextDouble() * 0.6, dz / Math.max(d, 1.0) * out);
							ejecta.setHurtsEntities(2.0F, 20);
							ejected++;
						} else {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
					// The floor: shocked and melted rock, and the meteorite itself in the middle.
					BlockPos floorPos = new BlockPos(bx, floor, bz);
					if (level.getBlockState(floorPos).isAir()) {
						continue;
					}
					BlockState lining;
					if (d < radius * 0.3) {
						lining = random.nextInt(3) == 0 ? Blocks.RAW_IRON_BLOCK.defaultBlockState() : ModBlocks.METEORITE.defaultBlockState();
					} else {
						int roll = random.nextInt(10);
						lining = roll < 3 ? Blocks.MAGMA_BLOCK.defaultBlockState() : roll < 6 ? Blocks.BLACKSTONE.defaultBlockState()
								: roll < 8 ? Blocks.BASALT.defaultBlockState() : Blocks.TUFF.defaultBlockState();
					}
					level.setBlock(floorPos, lining, Block.UPDATE_ALL);
				} else if (WindDamage.isGround(surface)) {
					// The rim: the ejecta piled up round the crater.
					int height = (int) Math.round(radius * 0.22 * (1.0 - (d - radius) / (rim - radius + 0.01)) + random.nextDouble() * 0.6);
					BlockState fill = surface.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : surface;
					for (int h = 1; h <= height; h++) {
						BlockPos at = new BlockPos(bx, top + h, bz);
						if (level.getBlockState(at).canBeReplaced()) {
							level.setBlock(at, h == height ? scorch(surface) : fill, Block.UPDATE_ALL);
						}
					}
				}
			}
		}
		// The heat sets fire to what will burn round about.
		for (int i = 0; i < radius * 8; i++) {
			int bx = ground.getX() + random.nextInt(radius * 5) - radius * 5 / 2;
			int bz = ground.getZ() + random.nextInt(radius * 5) - radius * 5 / 2;
			if (!loaded(bx, bz)) {
				continue;
			}
			BlockPos above = new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz), bz);
			BlockState under = level.getBlockState(above.below());
			if (level.getBlockState(above).isAir() && (under.ignitedByLava() || under.is(Blocks.GRASS_BLOCK) && random.nextInt(3) == 0)) {
				level.setBlock(above, BaseFireBlock.getState(level, above), Block.UPDATE_ALL);
			}
		}
	}

	private BlockState scorch(BlockState surface) {
		if (surface.is(Blocks.GRASS_BLOCK)) {
			return random.nextInt(3) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		}
		return surface;
	}

	/** The blast throws everything near outward and hurts it. */
	private void shockwave(double cx, double cy, double cz, int radius) {
		double reach = radius * 6.0;
		for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(cx - reach, cy - reach, cz - reach, cx + reach, cy + reach, cz + reach))) {
			if (immune(entity)) {
				continue;
			}
			Vec3 away = entity.position().subtract(cx, cy - 1.0, cz);
			double d = Math.max(1.0, away.length());
			if (d > reach) {
				continue;
			}
			double power = (1.0 - d / reach) * 2.2;
			Vec3 push = away.normalize().scale(power);
			shove(entity, push.x, Math.max(0.3, push.y) * 0.6, push.z);
			if (entity instanceof LivingEntity && d < radius * 3.0) {
				hurt(entity, DisasterDamage.METEOR, (float) (radius * 3.0 * (1.0 - d / (radius * 3.0))));
			}
		}
	}

	/** Trees laid flat all around, pointing away from the blast. */
	private void flattenTrees(double cx, double cz, int radius) {
		int reach = radius * 5;
		for (int i = 0; i < reach * 6; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double d = radius * 1.2 + random.nextDouble() * (reach - radius * 1.2);
			int bx = (int) Math.floor(cx + Math.cos(angle) * d);
			int bz = (int) Math.floor(cz + Math.sin(angle) * d);
			if (!loaded(bx, bz) || random.nextDouble() > 1.2 - d / reach) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
			BlockPos pos = new BlockPos(bx, top, bz);
			if (TreeFeller.isLog(level.getBlockState(pos))) {
				fellTree(pos, new Vec3(Math.cos(angle), 0.0, Math.sin(angle)), 0, 4 + radius);
			}
		}
	}
}
