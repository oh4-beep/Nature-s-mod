package com.naturesmod.disaster;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An earthquake of a given magnitude. The waves run out from the epicentre (see {@link Seismic}): the
 * first ones jolt, the second ones shake things down, and far off the shaking comes later and weaker.
 * Where it shakes hard, loose sand and gravel slump, stone bricks crack, glass shatters, pots and
 * lanterns fall, built blocks with nothing under them come down, cliff edges crumble and stalactites
 * break off cave ceilings. A strong quake breaks the surface along its fault: a fissure opening out
 * from the epicentre both ways, the ground on one side heaved up into a step. Players far enough off
 * get an early warning before the shaking arrives, and a report after; the big ones bring aftershocks,
 * the largest about 1.2 magnitudes smaller (Båth's law), most of them soon after (Omori's law).
 */
public final class Earthquake extends Disaster {
	private static final Map<Block, Block> CRACKED = Map.of(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.DEEPSLATE_BRICKS,
			Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS,
			Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);

	private final double magnitude;
	private final boolean aftershock;
	private final double strike;
	private final double ruptureHalf;
	private final double felt;
	private final double phase;
	private final int scarp;
	private final int width;
	private final int depth;
	private double carved;
	private boolean quiet;

	Earthquake(ServerLevel level, Vec3 at, float magnitude, boolean natural, boolean aftershock) {
		super(DisasterType.EARTHQUAKE, level, at, magnitude, natural, 0);
		this.magnitude = this.intensity;
		this.aftershock = aftershock;
		strike = random.nextDouble() * Math.PI;
		phase = random.nextDouble() * 100.0;
		felt = Math.min(Seismic.feltRadius(this.magnitude), 2400.0);
		ruptureHalf = this.magnitude >= 6.0 ? 6.0 * Math.pow(2.2, this.magnitude - 6.0) : 0.0;
		scarp = this.magnitude >= 6.5 ? 1 + (this.magnitude >= 7.5 ? 1 : 0) + (this.magnitude >= 8.5 ? 1 : 0) : 0;
		width = 1 + (this.magnitude >= 7.0 ? 1 : 0) + (this.magnitude >= 8.5 ? 1 : 0);
		depth = (int) (3 + Math.max(0.0, this.magnitude - 6.0) * 3.0);
		duration = (int) (Math.min(felt, 1600.0) / Seismic.S_SPEED + Seismic.shakingTicks(this.magnitude) * 2.6) + 60;
		y = ground((int) Math.floor(x), (int) Math.floor(z)) + 1;
	}

	/** No warnings or reports: a tremor under a volcano, say. */
	Earthquake quietly() {
		quiet = true;
		return this;
	}

	@Override
	public float radius() {
		return (float) felt;
	}

	@Override
	public float strength() {
		return 1.0F;
	}

	@Override
	public float[] extra() {
		return new float[] {(float) magnitude, (float) strike, (float) ruptureHalf, (float) carved, Seismic.shakingTicks(magnitude), (float) felt};
	}

	@Override
	protected void begin() {
		if (!quiet && magnitude >= 4.5) {
			warnings();
		}
	}

	@Override
	protected void tick() {
		if (ruptureHalf > 0.0 && griefing()) {
			// The rupture runs out both ways at a couple of blocks a tick.
			double target = Math.min(ruptureHalf, age * 4.0);
			while (carved < target) {
				carved += 1.0;
				fissure(carved);
				fissure(-carved);
			}
		}
		if (griefing()) {
			shakeLand();
		}
		if (age % 3 == 0) {
			shakeEntities();
		}
	}

	// --- the land ----------------------------------------------------------------------------

	private void shakeLand() {
		int samples = (int) (20 + 30 * (magnitude - 4.0));
		double reach = Math.min(felt, 600.0);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < samples; i++) {
			double d = reach * random.nextDouble() * random.nextDouble();
			double a = Seismic.shaking(magnitude, d, age);
			if (a < 0.08) {
				continue;
			}
			double angle = random.nextDouble() * Math.PI * 2.0;
			int bx = (int) Math.floor(x + Math.cos(angle) * d);
			int bz = (int) Math.floor(z + Math.sin(angle) * d);
			if (!loaded(bx, bz)) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
			int by = random.nextInt(4) == 0 ? top - 2 - random.nextInt(48) : top - random.nextInt(4);
			if (by <= level.getMinY()) {
				continue;
			}
			shake(pos.set(bx, by, bz), a);
		}
	}

	private void shake(BlockPos.MutableBlockPos pos, double a) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return;
		}
		Block block = state.getBlock();
		if (block instanceof PointedDripstoneBlock && state.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.DOWN) {
			if (a > 0.2 && random.nextDouble() < a) {
				breakStalactite(pos.immutable());
			}
			return;
		}
		BlockState below = level.getBlockState(pos.below());
		if (block instanceof FallingBlock && FallingBlock.isFree(below)) {
			FallingBlockEntity.fall(level, pos.immutable(), state);
			return;
		}
		Block cracked = CRACKED.get(block);
		if (cracked != null) {
			if (a > 0.15 && random.nextDouble() < a) {
				level.setBlock(pos, cracked.defaultBlockState(), Block.UPDATE_ALL);
			}
			return;
		}
		String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (name.contains("glass")) {
			if (a > 0.12 && random.nextDouble() < a * 0.8) {
				level.destroyBlock(pos, false);
			}
			return;
		}
		if (block instanceof FlowerPotBlock || block instanceof LanternBlock || block instanceof CandleBlock) {
			if (random.nextDouble() < a * 1.5) {
				level.destroyBlock(pos, true);
			}
			return;
		}
		boolean free = FallingBlock.isFree(below);
		if (a > 0.2 && free && built(pos, state) && supports(pos) <= 1 && random.nextDouble() < a) {
			// Built over nothing and held up by too little: it comes down.
			fall(pos.immutable(), state);
		} else if (a > 0.3 && WindDamage.isGround(state) && (free || cliffEdge(pos)) && random.nextDouble() < a * 0.5) {
			// Rock and earth slide off the edge.
			fall(pos.immutable(), state);
		}
	}

	private boolean built(BlockPos pos, BlockState state) {
		return state.isCollisionShapeFullBlock(level, pos) && !WindDamage.isGround(state) && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
				&& !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0.0F && !state.is(Blocks.OBSIDIAN);
	}

	private int supports(BlockPos pos) {
		int count = 0;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (!level.getBlockState(pos.relative(side)).isAir()) {
				count++;
			}
		}
		return count;
	}

	/** Open air beside it and below that: the brink of a cliff. */
	private boolean cliffEdge(BlockPos pos) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos next = pos.relative(side);
			if (level.getBlockState(next).isAir() && level.getBlockState(next.below()).isAir() && level.getBlockState(next.below(2)).isAir()) {
				return true;
			}
		}
		return false;
	}

	private void fall(BlockPos pos, BlockState state) {
		FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
		block.setHurtsEntities(2.0F, 30);
	}

	/** A stalactite breaks off whole at its root and falls, point first. */
	private void breakStalactite(BlockPos pos) {
		BlockPos root = pos;
		while (level.getBlockState(root.above()).getBlock() instanceof PointedDripstoneBlock) {
			root = root.above();
		}
		for (BlockPos at = root; level.getBlockState(at).getBlock() instanceof PointedDripstoneBlock; at = at.below()) {
			FallingBlockEntity piece = FallingBlockEntity.fall(level, at, level.getBlockState(at));
			piece.setHurtsEntities(2.0F, 40);
		}
	}

	/** One step of the surface rupture, `along` blocks out from the epicentre. */
	private void fissure(double along) {
		double sx = Math.cos(strike);
		double sz = Math.sin(strike);
		double px = -sz;
		double pz = sx;
		double wobble = Math.sin(along * 0.23 + phase) * 1.2 + Math.sin(along * 0.61 + phase * 2.0) * 0.6;
		double cx = x + sx * along + px * wobble;
		double cz = z + sz * along + pz * wobble;
		double taper = 1.0 - Math.abs(along) / ruptureHalf;
		int w = Math.max(1, (int) Math.round(width * (0.5 + 0.5 * taper)));
		int d = Math.max(2, (int) Math.round(depth * (0.4 + 0.6 * taper)));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int k = 0; k < w; k++) {
			double off = k - (w - 1) / 2.0;
			int bx = (int) Math.floor(cx + px * off);
			int bz = (int) Math.floor(cz + pz * off);
			if (!loaded(bx, bz)) {
				continue;
			}
			int top = ground(bx, bz);
			int bottom = top - d - random.nextInt(2);
			for (int by = top; by > bottom && by > level.getMinY() + 1; by--) {
				BlockState state = level.getBlockState(pos.set(bx, by, bz));
				if (state.is(Blocks.BEDROCK) || state.getFluidState().isSource()) {
					break;
				}
				if (by == top) {
					level.destroyBlock(pos, false);
				} else {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		if (scarp > 0) {
			// The far side of the fault heaves up.
			for (int k = 1; k <= 2; k++) {
				double off = (w - 1) / 2.0 + k;
				int bx = (int) Math.floor(cx + px * off);
				int bz = (int) Math.floor(cz + pz * off);
				if (loaded(bx, bz)) {
					heave(bx, bz);
				}
			}
		}
		if (random.nextInt(9) == 0) {
			crack(cx, cz, strike + (random.nextBoolean() ? 1 : -1) * (0.5 + random.nextDouble() * 0.6), 3 + random.nextInt(6));
		}
	}

	private void heave(int bx, int bz) {
		int top = ground(bx, bz);
		BlockPos surface = new BlockPos(bx, top, bz);
		BlockState state = level.getBlockState(surface);
		if (!WindDamage.isGround(state)) {
			return;
		}
		BlockState fill = state.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : state;
		for (int h = 1; h <= scarp; h++) {
			BlockPos at = surface.above(h);
			BlockState there = level.getBlockState(at);
			if (!there.isAir() && !there.canBeReplaced()) {
				return;
			}
		}
		level.setBlock(surface, fill, Block.UPDATE_ALL);
		for (int h = 1; h <= scarp; h++) {
			level.setBlock(surface.above(h), h == scarp ? state : fill, Block.UPDATE_ALL);
		}
	}

	/** A narrow crack branching off the main fissure. */
	private void crack(double cx, double cz, double angle, int length) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 1; i <= length; i++) {
			int bx = (int) Math.floor(cx + Math.cos(angle) * i);
			int bz = (int) Math.floor(cz + Math.sin(angle) * i);
			if (!loaded(bx, bz)) {
				return;
			}
			int top = ground(bx, bz);
			int deep = 2 + random.nextInt(3) - i / 3;
			for (int by = top; by > top - deep && by > level.getMinY() + 1; by--) {
				BlockState state = level.getBlockState(pos.set(bx, by, bz));
				if (state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty()) {
					break;
				}
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	// --- the living --------------------------------------------------------------------------

	private void shakeEntities() {
		for (ServerPlayer player : playersWithin(felt)) {
			double a = Seismic.shaking(magnitude, distance2d(player, x, z), age);
			jolt(player, a);
			// The animals and monsters around the player sway and stumble too.
			if (a > 0.05) {
				for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32.0), e -> e != player)) {
					jolt(mob, Seismic.shaking(magnitude, distance2d(mob, x, z), age));
				}
			}
		}
	}

	private void jolt(LivingEntity entity, double a) {
		if (a < 0.03 || immune(entity) || !entity.onGround()) {
			return;
		}
		double push = Math.min(0.35, a * 0.18);
		entity.push((random.nextDouble() - 0.5) * push, a > 0.4 ? random.nextDouble() * push * 0.3 : 0.0, (random.nextDouble() - 0.5) * push);
		if (a > 0.5 && random.nextDouble() < 0.08) {
			// Thrown off your feet.
			entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3, true, false, true));
		}
	}

	private void warnings() {
		for (ServerPlayer player : playersWithin(felt)) {
			double d = distance2d(player, x, z);
			double expected = Seismic.pga(magnitude, d);
			if (expected < 0.03) {
				continue;
			}
			int arrives = (int) (d / Seismic.S_SPEED);
			if (arrives >= 60 && expected >= 0.08) {
				warn(player, Component.translatable("naturesmod.warning.quake", arrives / 20));
			}
			Component report = Component.translatable("naturesmod.report.quake", String.format(java.util.Locale.ROOT, "%.1f", magnitude), (int) d,
					compass(player.getX(), player.getZ(), x, z), Component.translatable("naturesmod.shaking." + Seismic.feeling(expected)));
			Disasters.later(arrives + 60, () -> {
				if (!player.isRemoved()) {
					warn(player, report);
				}
			});
		}
	}

	private void warn(ServerPlayer player, Component message) {
		if (com.naturesmod.NaturesConfig.get().disasterWarnings) {
			player.sendSystemMessage(message.copy().withStyle(net.minecraft.ChatFormatting.GOLD));
		}
	}

	@Override
	protected void finish() {
		if (aftershock || magnitude < 5.0) {
			return;
		}
		int count = 1 + (int) ((magnitude - 5.0) * 1.6) + random.nextInt(2);
		for (int i = 0; i < count; i++) {
			double m = magnitude - 1.2 - (-Math.log(1.0 - random.nextDouble())) * 0.5;
			if (m < 3.5) {
				continue;
			}
			int delay = (int) (200 + 2400 * random.nextDouble() * random.nextDouble());
			double spread = Seismic.reach(m) * 0.5;
			Vec3 at = new Vec3(x + random.nextGaussian() * spread, y, z + random.nextGaussian() * spread);
			float aftershockMagnitude = (float) m;
			Disasters.later(delay, () -> Disasters.add(new Earthquake(level, at, aftershockMagnitude, natural, true)));
		}
	}

	/** Anything left standing close to the fault: so the client test and players can find it. */
	public AABB rupture() {
		double half = Math.max(ruptureHalf, 4.0);
		return new AABB(x - half, y - 12, z - half, x + half, y + 6, z + half);
	}
}
