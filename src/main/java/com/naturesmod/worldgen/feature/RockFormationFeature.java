package com.naturesmod.worldgen.feature;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.WaterWorks;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Landmark rock formations: natural arches, sea stacks rising out of the surf and clusters of
 * hoodoo spires. With {@code banded} the rock is layered like the walls of a canyon.
 */
public record RockFormationFeature(Style style, List<BlockState> materials, boolean banded, BlockState top) implements Feature {
	public static final MapCodec<RockFormationFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Style.CODEC.fieldOf("style").forGetter(RockFormationFeature::style),
			ExtraCodecs.nonEmptyList(BlockState.CODEC.listOf()).fieldOf("materials").forGetter(RockFormationFeature::materials),
			Codec.BOOL.optionalFieldOf("banded", false).forGetter(RockFormationFeature::banded),
			BlockState.CODEC.optionalFieldOf("top", Blocks.AIR.defaultBlockState()).forGetter(RockFormationFeature::top)
	).apply(instance, RockFormationFeature::new));

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 1);
		if (!WorldGenUtil.isStructureFree(level, origin)) {
			return false;
		}
		boolean placed = switch (style) {
			case ARCH -> arch(level, window, random, origin);
			case SEA_STACK -> seaStack(level, window, random, origin);
			case HOODOOS -> hoodoos(level, window, random, origin);
		};
		if (placed) {
			FeatureStats.placed("rock_" + style.getSerializedName());
		}
		return placed;
	}

	private BlockState material(RandomSource random, int x, int y, int z, int bandOffset) {
		if (banded) {
			// Thick horizontal strata, two or three blocks each.
			int band = Math.floorDiv(y + bandOffset, 3);
			return materials.get(Math.floorMod(band * 7 + (band >> 2), materials.size()));
		}
		return materials.get(random.nextInt(materials.size()));
	}

	private void setRock(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int bandOffset) {
		BlockState existing = level.getBlockState(pos);
		if (existing.is(BlockTags.FEATURES_CANNOT_REPLACE) || existing.is(Blocks.BEDROCK)) {
			return;
		}
		WaterWorks.set(level, pos, material(random, pos.getX(), pos.getY(), pos.getZ(), bandOffset));
	}

	private boolean arch(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		double angle = random.nextDouble() * Math.PI;
		double span = 12 + random.nextDouble() * 12;
		double dx = Math.cos(angle);
		double dz = Math.sin(angle);
		int ax = Mth.floor(origin.getX() + 0.5 - dx * span / 2);
		int az = Mth.floor(origin.getZ() + 0.5 - dz * span / 2);
		int bx = Mth.floor(origin.getX() + 0.5 + dx * span / 2);
		int bz = Mth.floor(origin.getZ() + 0.5 + dz * span / 2);
		double thickness = 2.2 + random.nextDouble() * 1.6;
		int margin = (int) Math.ceil(thickness * 1.8) + 1;
		if (!window.containsRadius(ax, az, margin) || !window.containsRadius(bx, bz, margin)) {
			return false;
		}
		int groundA = WorldGenUtil.groundY(level, ax, az);
		int groundB = WorldGenUtil.groundY(level, bx, bz);
		if (Math.abs(groundA - groundB) > 8 || groundA < level.getMinY() + 10) {
			return false;
		}
		double rise = span * (0.45 + random.nextDouble() * 0.25);
		int bandOffset = random.nextInt(9);
		WorldGenUtil.Wobble wobble = new WorldGenUtil.Wobble(random, 3, 1.4);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (double t = 0; t <= 1.0001; t += 0.02) {
			double cx = Mth.lerp(t, ax + 0.5, bx + 0.5);
			double cz = Mth.lerp(t, az + 0.5, bz + 0.5);
			double cy = Mth.lerp(t, groundA, groundB) + rise * Math.sin(Math.PI * t);
			double r = thickness * (1 + 0.7 * (1 - Math.sin(Math.PI * t)));
			int ir = (int) Math.ceil(r * 1.2);
			for (int ox = -ir; ox <= ir; ox++) {
				for (int oy = -ir; oy <= ir; oy++) {
					for (int oz = -ir; oz <= ir; oz++) {
						double rough = r * (1 + 0.15 * wobble.at(ox * 0.7 + t * 9, oz * 0.7 + oy * 0.5));
						if (ox * ox + oy * oy * 1.3 + oz * oz > rough * rough) {
							continue;
						}
						pos.set(Mth.floor(cx) + ox, Mth.floor(cy) + oy, Mth.floor(cz) + oz);
						setRock(level, random, pos, bandOffset);
					}
				}
			}
			// Plant the legs firmly in the ground.
			if (t < 0.15 || t > 0.85) {
				int ground = WorldGenUtil.groundY(level, Mth.floor(cx), Mth.floor(cz));
				for (int y = ground; y < cy; y++) {
					for (int ox = -1; ox <= 1; ox++) {
						for (int oz = -1; oz <= 1; oz++) {
							pos.set(Mth.floor(cx) + ox, y, Mth.floor(cz) + oz);
							setRock(level, random, pos, bandOffset);
						}
					}
				}
			}
		}
		coverTop(level, window, origin, (int) Math.ceil(span / 2 + thickness * 2));
		return true;
	}

	private boolean seaStack(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int sea = level.getSeaLevel();
		int floor = WorldGenUtil.groundY(level, origin.getX(), origin.getZ());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(origin.getX(), sea, origin.getZ());
		if (!WorldGenUtil.isWater(level.getBlockState(pos)) || sea - floor > 14 || sea - floor < 1) {
			return false;
		}
		double radius = 2.5 + random.nextDouble() * 2.5;
		int height = 10 + random.nextInt(22);
		int reach = (int) Math.ceil(radius * 1.6) + 1;
		if (!window.containsRadius(origin.getX(), origin.getZ(), reach)) {
			return false;
		}
		int topY = sea + height;
		int bandOffset = random.nextInt(9);
		WorldGenUtil.Wobble wobble = new WorldGenUtil.Wobble(random, 4, 1.0);
		WorldGenUtil.Wobble lean = new WorldGenUtil.Wobble(random, 2, 0.08);
		for (int y = floor - 2; y <= topY; y++) {
			double f = (y - floor) / (double) (topY - floor);
			double r = radius * (1.35 - 0.55 * f) * (1 + 0.1 * wobble.at(y * 0.3));
			double ox = lean.at(y) * 2.0;
			double oz = lean.at(y + 50) * 2.0;
			int ir = (int) Math.ceil(r) + 1;
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double angle = Math.atan2(dz, dx);
					double edge = r * (1 + 0.18 * wobble.at(angle * 2 + y * 0.15));
					double ddx = dx - ox;
					double ddz = dz - oz;
					if (ddx * ddx + ddz * ddz > edge * edge) {
						continue;
					}
					pos.set(origin.getX() + dx, y, origin.getZ() + dz);
					setRock(level, random, pos, bandOffset);
				}
			}
		}
		coverTop(level, window, origin, reach);
		return true;
	}

	private boolean hoodoos(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int count = 3 + random.nextInt(5);
		int placed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < count; i++) {
			int x = origin.getX() + random.nextInt(13) - 6;
			int z = origin.getZ() + random.nextInt(13) - 6;
			if (!window.containsRadius(x, z, 3)) {
				continue;
			}
			int ground = WorldGenUtil.groundY(level, x, z);
			BlockState below = level.getBlockState(pos.set(x, ground, z));
			if (!WorldGenUtil.isTerrain(below) || !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) {
				continue;
			}
			int height = 5 + random.nextInt(10);
			double radius = 0.8 + random.nextDouble() * 0.9;
			int bandOffset = random.nextInt(9);
			for (int y = ground - 1; y <= ground + height; y++) {
				double f = (y - ground) / (double) height;
				// Wide base, a pinched neck and a heavier cap rock on top.
				double r = f > 0.85 ? radius + 1.0 : radius * (1.4 - 0.6 * f);
				int ir = (int) Math.ceil(r);
				for (int dx = -ir; dx <= ir; dx++) {
					for (int dz = -ir; dz <= ir; dz++) {
						if (dx * dx + dz * dz > r * r + 0.3) {
							continue;
						}
						pos.set(x + dx, y, z + dz);
						if (f > 0.85) {
							WaterWorks.set(level, pos, top.isAir() ? materials.get(0) : top);
						} else {
							setRock(level, random, pos, bandOffset);
						}
					}
				}
			}
			placed++;
		}
		return placed > 0;
	}

	/** Grass or snow on the flat tops of a formation. */
	private void coverTop(WorldGenLevel level, WorldGenUtil.Window window, BlockPos origin, int radius) {
		if (top.isAir()) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				if (!window.contains(x, z)) {
					continue;
				}
				int y = WorldGenUtil.groundY(level, x, z);
				BlockState state = level.getBlockState(pos.set(x, y, z));
				if (materials.contains(state) && level.getBlockState(pos.set(x, y + 1, z)).isAir()
						&& level.getBlockState(pos.set(x, y + 2, z)).isAir() && y > level.getSeaLevel() + 3) {
					// Up in the mountains the crown is snow instead.
					boolean snowy = WorldGenUtil.isAlpine(y) && top.is(Blocks.GRASS_BLOCK);
					WaterWorks.set(level, pos.set(x, y, z), snowy ? Blocks.SNOW_BLOCK.defaultBlockState() : top);
				}
			}
		}
	}

	public enum Style implements StringRepresentable {
		ARCH("arch"),
		SEA_STACK("sea_stack"),
		HOODOOS("hoodoos");

		public static final Codec<Style> CODEC = StringRepresentable.fromEnum(Style::values);
		private final String name;

		Style(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}
}
