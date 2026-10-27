package com.naturesmod.disaster;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A blizzard: gale-force wind and driving snow, a whiteout where you can't see a few blocks. Out in it
 * without warm clothes (leather) or a fire close by, you chill and then freeze; under a roof you are
 * out of the worst of it. Snow piles up fast, drifting deepest in the lee of whatever breaks the wind,
 * and still water freezes over. Only where it is cold enough to snow, of course.
 */
public final class Blizzard extends Disaster {
	private final Vec3 wind;
	private final double windSpeed;
	private final double reach;
	private final double visibility;

	Blizzard(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.BLIZZARD, level, at, intensity, natural, (int) (3000 + 1200 * intensity));
		double angle = random.nextDouble() * Math.PI * 2.0;
		wind = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		windSpeed = 0.06 + 0.02 * intensity;
		reach = 150.0 + 25.0 * intensity;
		visibility = 30.0 - 5.0 * intensity;
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

	@Override
	public float[] extra() {
		return new float[] {(float) visibility, (float) windSpeed};
	}

	@Override
	protected void begin() {
		if (!natural && !level.isRaining()) {
			level.getServer().setWeatherParameters(0, duration + 600, true, false);
		}
		warn(reach + 100, player -> Component.translatable("naturesmod.warning.blizzard"));
	}

	@Override
	protected void tick() {
		// The storm drifts with its wind.
		x += wind.x * 0.03;
		z += wind.z * 0.03;
		float strength = strength();
		if (strength <= 0.0F) {
			return;
		}
		chill(strength);
		if (griefing()) {
			for (int i = 0; i < (int) ((20 + 10 * intensity) * strength); i++) {
				snow();
			}
		}
	}

	private boolean cold(BlockPos pos) {
		return level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
	}

	/** Wind and cold on everything out in the storm. */
	private void chill(float strength) {
		for (Player player : level.players()) {
			double d = distance2d(player, x, z);
			if (d > reach || immune(player)) {
				continue;
			}
			if (!cold(player.blockPosition())) {
				continue;
			}
			boolean shelter = sheltered(player);
			if (!shelter) {
				double push = windSpeed * 0.15 * strength;
				shove(player, wind.x * push, 0.0, wind.z * push);
			}
			if (age % 5 == 0) {
				freeze(player, shelter);
				for (Entity entity : level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(32.0), e -> e != player)) {
					if (entity instanceof ItemEntity item) {
						shove(item, wind.x * 0.05, 0.0, wind.z * 0.05);
					} else if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
						freeze(living, sheltered(living));
					}
				}
			}
		}
	}

	/** Exposed to the storm and not dressed for it, the cold sets in (five ticks' worth at a time). */
	private void freeze(LivingEntity entity, boolean shelter) {
		if (!entity.canFreeze() || shelter || nearHeat(entity.blockPosition())) {
			return;
		}
		// The game thaws two ticks' worth a tick; the storm freezes faster than that.
		int ticks = entity.getTicksFrozen() + 5 * 3;
		entity.setTicksFrozen(Math.min(entity.getTicksRequiredToFreeze() + 40, ticks));
	}

	private boolean nearHeat(BlockPos at) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -1; dy <= 2; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					BlockState state = level.getBlockState(pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz));
					if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)
							|| state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** A little more snow somewhere in the storm: deepest where something breaks the wind. */
	private void snow() {
		double angle = random.nextDouble() * Math.PI * 2.0;
		double d = reach * Math.sqrt(random.nextDouble());
		int bx = (int) Math.floor(x + Math.cos(angle) * d);
		int bz = (int) Math.floor(z + Math.sin(angle) * d);
		if (!loaded(bx, bz)) {
			return;
		}
		BlockPos top = new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz), bz);
		if (!cold(top)) {
			return;
		}
		BlockState under = level.getBlockState(top.below());
		if (under.is(Blocks.WATER) && level.getFluidState(top.below()).isSource() && level.getFluidState(top.below()).is(FluidTags.WATER)) {
			level.setBlock(top.below(), Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
			return;
		}
		// Upwind something taller breaks the wind: the snow piles up in its lee.
		int upwindX = (int) Math.floor(bx - wind.x * 2.0);
		int upwindZ = (int) Math.floor(bz - wind.z * 2.0);
		boolean lee = loaded(upwindX, upwindZ) && level.getHeight(Heightmap.Types.MOTION_BLOCKING, upwindX, upwindZ) > top.getY() + 1;
		int most = lee ? 7 : 3;
		BlockState there = level.getBlockState(top);
		if (under.is(Blocks.SNOW)) {
			int layers = under.getValue(SnowLayerBlock.LAYERS);
			if (layers < most) {
				level.setBlock(top.below(), under.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
			}
		} else if (there.is(Blocks.SNOW)) {
			int layers = there.getValue(SnowLayerBlock.LAYERS);
			if (layers < most) {
				level.setBlock(top, there.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
			}
		} else if ((there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty()) && Blocks.SNOW.defaultBlockState().canSurvive(level, top)) {
			level.setBlock(top, Blocks.SNOW.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	/** Snow deep enough to measure (for the tests). */
	public static int snowDepth(ServerLevel level, BlockPos column) {
		BlockPos top = new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()), column.getZ());
		BlockState state = level.getBlockState(top);
		BlockState under = level.getBlockState(top.below());
		if (state.is(Blocks.SNOW)) {
			return state.getValue(SnowLayerBlock.LAYERS);
		}
		return under.is(Blocks.SNOW) ? under.getValue(SnowLayerBlock.LAYERS) : 0;
	}
}
