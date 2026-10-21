package com.naturesmod.worldgen.tree;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.WaterWorks;
import com.naturesmod.worldgen.WorldGenUtil;
import com.naturesmod.worldgen.feature.FallenLogFeature;
import com.naturesmod.worldgen.tree.TreeBuilder.LogKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.Vec3;

/**
 * Procedurally grown giant trees. Every tree is unique: trunks flare into roots, limbs fork and
 * twist, and the canopy is built from many leaf clouds.
 */
public record GiantTreeFeature(Style style, BlockState log, List<BlockState> leaves, int minHeight, int maxHeight, boolean nearWater)
		implements Feature {
	public static final MapCodec<GiantTreeFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Style.CODEC.fieldOf("style").forGetter(GiantTreeFeature::style),
			BlockState.CODEC.fieldOf("log").forGetter(GiantTreeFeature::log),
			BlockState.CODEC.listOf().optionalFieldOf("leaves", List.of()).forGetter(GiantTreeFeature::leaves),
			Codec.intRange(3, 96).optionalFieldOf("min_height", 16).forGetter(GiantTreeFeature::minHeight),
			Codec.intRange(3, 96).optionalFieldOf("max_height", 24).forGetter(GiantTreeFeature::maxHeight),
			Codec.BOOL.optionalFieldOf("near_water", false).forGetter(GiantTreeFeature::nearWater)
	).apply(instance, GiantTreeFeature::new));

	@Override
	public MapCodec<? extends Feature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 0);
		int ground = WorldGenUtil.groundY(level, origin.getX(), origin.getZ());
		BlockPos base = new BlockPos(origin.getX(), ground, origin.getZ());
		BlockState soil = level.getBlockState(base);
		if (!soil.is(BlockTags.SUPPORTS_VEGETATION) && !(style == Style.BAOBAB && soil.is(BlockTags.SAND))) {
			return false;
		}
		if (!level.getFluidState(base.above()).isEmpty() || !WorldGenUtil.isStructureFree(level, base)) {
			return false;
		}
		if (nearWater && !waterNearby(level, window, base)) {
			return false;
		}
		if (leaves.isEmpty() && style != Style.SNAG) {
			return false;
		}
		int height = minHeight + random.nextInt(Math.max(1, maxHeight - minHeight + 1));
		if (ground + height + 12 >= level.getMaxY()) {
			return false;
		}

		TreeBuilder builder = new TreeBuilder(level, window, random);
		switch (style) {
			case ANCIENT -> broadleaf(builder, base, height, 1.2, 0.7, 5, 4, 1.0, false);
			case DARK -> broadleaf(builder, base, height, 1.7, 0.6, 4, 3, 1.15, false);
			case AUTUMN -> broadleaf(builder, base, height, 0.85, 0.5, 4, 3, 0.85, true);
			case REDWOOD -> redwood(builder, base, height);
			case WILLOW -> willow(builder, base, height);
			case BAOBAB -> baobab(builder, base, height);
			case SNAG -> snag(builder, base, height);
		}
		if (builder.blocked() || builder.logCount() == 0) {
			return false;
		}
		builder.commit();
		groundCover(level, window, random, base);
		FeatureStats.placed("giant_tree_" + style.getSerializedName());
		return true;
	}

	private static boolean waterNearby(WorldGenLevel level, WorldGenUtil.Window window, BlockPos base) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -7; dx <= 7; dx += 2) {
			for (int dz = -7; dz <= 7; dz += 2) {
				if (!window.contains(base.getX() + dx, base.getZ() + dz)) {
					continue;
				}
				for (int dy = -2; dy <= 1; dy++) {
					if (WorldGenUtil.isWater(level.getBlockState(pos.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz)))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private BlockState logAlong(Direction.Axis axis) {
		return log.hasProperty(BlockStateProperties.AXIS) ? log.setValue(BlockStateProperties.AXIS, axis) : log;
	}

	private BlockState randomLeaves(RandomSource random) {
		return leaves.get(random.nextInt(leaves.size()));
	}

	/** Oaks, dark oaks and autumn giants: a thick trunk splitting into great spreading limbs. */
	private void broadleaf(TreeBuilder b, BlockPos base, int height, double trunkRadius, double radiusJitter, int minBranches,
			int extraBranches, double spread, boolean autumn) {
		RandomSource r = b.random();
		double radius = trunkRadius + r.nextDouble() * radiusJitter;
		int trunkHeight = Math.max(4, (int) (height * (0.45 + r.nextDouble() * 0.12)));
		double leanX = (r.nextDouble() - 0.5) * 0.14;
		double leanZ = (r.nextDouble() - 0.5) * 0.14;
		double cx = base.getX() + 0.5;
		double cz = base.getZ() + 0.5;
		int y0 = base.getY() + 1;
		BlockState upright = logAlong(Direction.Axis.Y);

		for (int i = -1; i <= trunkHeight; i++) {
			double flare = i < 3 ? (3 - i) * 0.4 : 0;
			double layer = radius * (1 - 0.2 * Math.max(0, i) / trunkHeight) + flare;
			b.trunkLayer(cx + leanX * i, y0 + i, cz + leanZ * i, layer, upright, i < 0 ? LogKind.ROOT : LogKind.TRUNK);
		}

		int roots = 4 + r.nextInt(4);
		for (int k = 0; k < roots; k++) {
			double angle = Math.PI * 2 * k / roots + (r.nextDouble() - 0.5) * 0.6;
			double length = radius + 2 + r.nextDouble() * 3;
			Vec3 from = new Vec3(cx + Math.cos(angle) * radius * 0.6, y0 + 0.6, cz + Math.sin(angle) * radius * 0.6);
			Vec3 to = new Vec3(cx + Math.cos(angle) * length, y0 - 1.3, cz + Math.sin(angle) * length);
			b.limb(from, to, 0.9, 0.0, log, LogKind.ROOT);
		}

		List<Vec3> tips = new ArrayList<>();
		Vec3 top = new Vec3(cx + leanX * trunkHeight, y0 + trunkHeight, cz + leanZ * trunkHeight);
		int branches = minBranches + r.nextInt(extraBranches + 1);
		// Keep the crown inside the area we may build in (leaf clouds and twigs add ~6 blocks).
		double reach = b.maxReach(base.getX(), base.getZ()) - 7;
		double offset = r.nextDouble() * Math.PI * 2;
		for (int k = 0; k < branches; k++) {
			double h = trunkHeight * (0.55 + r.nextDouble() * 0.45);
			Vec3 start = new Vec3(cx + leanX * h, y0 + h, cz + leanZ * h);
			double angle = offset + Math.PI * 2 * k / branches + (r.nextDouble() - 0.5) * 0.7;
			double pitch = Math.toRadians(18 + r.nextDouble() * 32);
			double length = height * (0.32 + r.nextDouble() * 0.22) * spread;
			double horizontal = Math.cos(pitch) * length + Math.abs(h * leanX) + Math.abs(h * leanZ) + 1.5;
			if (horizontal > reach) {
				length *= Math.max(0.3, reach / horizontal);
			}
			Vec3 end = start.add(Math.cos(angle) * Math.cos(pitch) * length, Math.sin(pitch) * length, Math.sin(angle) * Math.cos(pitch) * length);
			// A slight kink halfway makes the limbs look gnarled.
			Vec3 mid = start.add(end.subtract(start).scale(0.5)).add((r.nextDouble() - 0.5) * 1.5, r.nextDouble() * 1.2, (r.nextDouble() - 0.5) * 1.5);
			b.limb(start, mid, Math.max(0.6, radius * 0.55), 0.6, log, LogKind.BRANCH);
			b.limb(mid, end, 0.6, 0.0, log, LogKind.BRANCH);
			tips.add(end);
			tips.add(mid);
			if (r.nextInt(3) != 0) {
				double fork = angle + (r.nextBoolean() ? 0.8 : -0.8);
				double forkLength = length * (0.4 + r.nextDouble() * 0.2);
				Vec3 forkEnd = mid.add(Math.cos(fork) * forkLength, forkLength * 0.45, Math.sin(fork) * forkLength);
				b.limb(mid, forkEnd, 0.5, 0.0, log, LogKind.BRANCH);
				tips.add(forkEnd);
			}
		}
		Vec3 leader = top.add((r.nextDouble() - 0.5) * 2, height * 0.3, (r.nextDouble() - 0.5) * 2);
		b.limb(top, leader, Math.max(0.6, radius * 0.6), 0.0, log, LogKind.BRANCH);
		tips.add(leader);

		for (Vec3 tip : tips) {
			twigs(b, tip, 2);
			double rx = (autumn ? 2.8 : 3.2) + r.nextDouble() * 1.5;
			List<BlockState> palette = List.of(randomLeaves(r));
			b.leafBlob(tip.x, tip.y + 0.5, tip.z, rx, rx * (0.55 + r.nextDouble() * 0.15), rx, palette, 0.7);
		}
	}

	/** Short twigs inside a leaf cloud keep every leaf close enough to wood that it never decays. */
	private void twigs(TreeBuilder b, Vec3 tip, int count) {
		RandomSource r = b.random();
		for (int i = 0; i < count; i++) {
			double angle = r.nextDouble() * Math.PI * 2;
			Vec3 end = tip.add(Math.cos(angle) * 2, r.nextDouble() * 1.5, Math.sin(angle) * 2);
			b.limb(tip, end, 0.0, 0.0, log, LogKind.BRANCH);
		}
	}

	/** Towering conifer with buttress roots and a narrow layered crown. */
	private void redwood(TreeBuilder b, BlockPos base, int height) {
		RandomSource r = b.random();
		double radius = 1.3 + r.nextDouble() * 0.9;
		double cx = base.getX() + 0.5;
		double cz = base.getZ() + 0.5;
		int y0 = base.getY() + 1;
		BlockState upright = logAlong(Direction.Axis.Y);

		for (int i = -1; i <= height; i++) {
			double flare = i < 4 ? (4 - i) * 0.35 : 0;
			double layer = Math.max(0.5, radius * (1 - 0.75 * Math.max(0, i) / (double) height) + flare);
			b.trunkLayer(cx, y0 + i, cz, layer, upright, i < 0 ? LogKind.ROOT : LogKind.TRUNK);
		}
		int buttresses = 4 + r.nextInt(3);
		for (int k = 0; k < buttresses; k++) {
			double angle = Math.PI * 2 * k / buttresses + (r.nextDouble() - 0.5) * 0.5;
			Vec3 from = new Vec3(cx + Math.cos(angle) * radius, y0 + 3 + r.nextDouble() * 3, cz + Math.sin(angle) * radius);
			Vec3 to = new Vec3(cx + Math.cos(angle) * (radius + 2.5 + r.nextDouble() * 1.5), y0 - 1, cz + Math.sin(angle) * (radius + 2.5));
			b.limb(from, to, 0.9, 0.5, log, LogKind.ROOT);
		}

		int crownStart = (int) (height * (0.42 + r.nextDouble() * 0.1));
		for (int y = crownStart; y < height - 1; y += 2 + r.nextInt(2)) {
			double t = (y - crownStart) / (double) (height - crownStart);
			int count = 2 + r.nextInt(3);
			double offset = r.nextDouble() * Math.PI * 2;
			for (int k = 0; k < count; k++) {
				double angle = offset + Math.PI * 2 * k / count + (r.nextDouble() - 0.5) * 0.6;
				double length = (1 - t) * (4.5 + r.nextDouble() * 2.5) + 1.5;
				Vec3 start = new Vec3(cx, y0 + y, cz);
				Vec3 end = start.add(Math.cos(angle) * length, -r.nextDouble() * 1.2, Math.sin(angle) * length);
				b.limb(start, end, 0.0, 0.0, log, LogKind.BRANCH);
				double pad = Math.max(1.6, length * 0.45);
				b.leafBlob(end.x, end.y + 0.6, end.z, pad, 1.3, pad, leaves, 0.8);
				Vec3 mid = start.add(end.subtract(start).scale(0.5));
				b.leafBlob(mid.x, mid.y + 0.8, mid.z, pad * 0.8, 1.2, pad * 0.8, leaves, 0.75);
			}
			b.leafBlob(cx, y0 + y + 1, cz, 2.0 + (1 - t) * 1.5, 1.2, 2.0 + (1 - t) * 1.5, leaves, 0.6);
		}
		// Spire.
		for (int i = 0; i < 3; i++) {
			b.leaf(Mth.floor(cx), y0 + height + 1 + i, Mth.floor(cz), randomLeaves(r));
		}
		b.leafBlob(cx, y0 + height, cz, 1.6, 1.8, 1.6, leaves, 0.9);
	}

	/** Weeping willow: a short leaning trunk under a dome that drips curtains of leaves and vines. */
	private void willow(TreeBuilder b, BlockPos base, int height) {
		RandomSource r = b.random();
		double cx = base.getX() + 0.5;
		double cz = base.getZ() + 0.5;
		int y0 = base.getY() + 1;
		int trunkHeight = Math.max(3, height / 2);
		double leanX = (r.nextDouble() - 0.5) * 0.35;
		double leanZ = (r.nextDouble() - 0.5) * 0.35;
		double radius = 0.6 + r.nextDouble() * 0.5;
		BlockState upright = logAlong(Direction.Axis.Y);
		for (int i = -1; i <= trunkHeight; i++) {
			b.trunkLayer(cx + leanX * i, y0 + i, cz + leanZ * i, radius + (i < 1 ? 0.6 : 0), upright, i < 0 ? LogKind.ROOT : LogKind.TRUNK);
		}
		Vec3 top = new Vec3(cx + leanX * trunkHeight, y0 + trunkHeight, cz + leanZ * trunkHeight);
		int arms = 4 + r.nextInt(3);
		double offset = r.nextDouble() * Math.PI * 2;
		List<Vec3> tips = new ArrayList<>();
		for (int k = 0; k < arms; k++) {
			double angle = offset + Math.PI * 2 * k / arms + (r.nextDouble() - 0.5) * 0.5;
			double length = 3 + r.nextDouble() * 2.5;
			Vec3 end = top.add(Math.cos(angle) * length, 1 + r.nextDouble() * 2.5, Math.sin(angle) * length);
			b.limb(top.subtract(0, 1, 0), end, 0.55, 0.0, log, LogKind.BRANCH);
			tips.add(end);
		}
		for (Vec3 tip : tips) {
			b.leafBlob(tip.x, tip.y, tip.z, 3.2, 2.2, 3.2, leaves, 0.8);
		}
		b.leafBlob(top.x, top.y + 2, top.z, 3.5, 2.5, 3.5, leaves, 0.8);

		// Curtains: strands of leaves dripping off the edge of the canopy, trailing into vines.
		List<long[]> strands = new ArrayList<>();
		for (Long2ObjectMap.Entry<BlockState> entry : b.leaves().long2ObjectEntrySet()) {
			long key = entry.getLongKey();
			int x = BlockPos.getX(key);
			int y = BlockPos.getY(key);
			int z = BlockPos.getZ(key);
			double dx = x + 0.5 - top.x;
			double dz = z + 0.5 - top.z;
			if (dx * dx + dz * dz < 6.5 || b.isLeaf(x, y - 1, z) || b.isLog(x, y - 1, z) || r.nextInt(10) < 4) {
				continue;
			}
			strands.add(new long[] {x, y, z});
		}
		for (long[] strand : strands) {
			int x = (int) strand[0];
			int y = (int) strand[1];
			int z = (int) strand[2];
			int length = 1 + r.nextInt(3);
			for (int i = 1; i <= length; i++) {
				b.leaf(x, y - i, z, randomLeaves(r));
			}
			int bottom = y - length;
			Direction outward = Direction.getApproximateNearest(x + 0.5 - top.x, 0, z + 0.5 - top.z);
			if (outward.getAxis() == Direction.Axis.Y) {
				continue;
			}
			// Vines hang on the outside of the lowest leaf and keep going down.
			BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(outward.getOpposite()), true);
			int vineLength = 2 + r.nextInt(5);
			for (int i = 0; i < vineLength; i++) {
				b.decoration(x + outward.getStepX(), bottom - i, z + outward.getStepZ(), vine);
			}
		}
	}

	/** Savanna baobab: a bulging bottle trunk crowned with stubby limbs and flat leaf pads. */
	private void baobab(TreeBuilder b, BlockPos base, int height) {
		RandomSource r = b.random();
		double cx = base.getX() + 0.5;
		double cz = base.getZ() + 0.5;
		int y0 = base.getY() + 1;
		double radius = 1.8 + r.nextDouble() * 0.6;
		BlockState upright = logAlong(Direction.Axis.Y);
		for (int i = -1; i <= height; i++) {
			double t = Math.max(0, i) / (double) height;
			double bulge = t < 0.8 ? 1.0 + 0.25 * Math.sin(t / 0.8 * Math.PI) : Mth.lerp((t - 0.8) / 0.2, 1.0, 0.55);
			b.trunkLayer(cx, y0 + i, cz, radius * bulge, upright, i < 0 ? LogKind.ROOT : LogKind.TRUNK);
		}
		Vec3 top = new Vec3(cx, y0 + height, cz);
		int limbs = 6 + r.nextInt(4);
		double offset = r.nextDouble() * Math.PI * 2;
		for (int k = 0; k < limbs; k++) {
			double angle = offset + Math.PI * 2 * k / limbs + (r.nextDouble() - 0.5) * 0.4;
			double pitch = Math.toRadians(10 + r.nextDouble() * 30);
			double length = 3 + r.nextDouble() * 3.5;
			Vec3 end = top.add(Math.cos(angle) * Math.cos(pitch) * length, Math.sin(pitch) * length, Math.sin(angle) * Math.cos(pitch) * length);
			b.limb(top.subtract(0, 1.5, 0), end, 0.9, 0.0, log, LogKind.BRANCH);
			b.leafBlob(end.x, end.y + 0.8, end.z, 2.4 + r.nextDouble(), 1.0, 2.4 + r.nextDouble(), leaves, 0.75);
		}
	}

	/** A dead, bleached snag: bare limbs reaching out of the forest. */
	private void snag(TreeBuilder b, BlockPos base, int height) {
		RandomSource r = b.random();
		double cx = base.getX() + 0.5;
		double cz = base.getZ() + 0.5;
		int y0 = base.getY() + 1;
		double radius = r.nextInt(3) == 0 ? 1.0 : 0.4;
		BlockState upright = logAlong(Direction.Axis.Y);
		for (int i = -1; i < height; i++) {
			b.trunkLayer(cx, y0 + i, cz, radius, upright, i < 0 ? LogKind.ROOT : LogKind.TRUNK);
		}
		int limbs = 1 + r.nextInt(3);
		for (int k = 0; k < limbs; k++) {
			double angle = r.nextDouble() * Math.PI * 2;
			Vec3 start = new Vec3(cx, y0 + height * (0.5 + r.nextDouble() * 0.4), cz);
			double length = 2 + r.nextDouble() * 2.5;
			b.limb(start, start.add(Math.cos(angle) * length, 0.8 + r.nextDouble() * 1.5, Math.sin(angle) * length), 0.0, 0.0, log, LogKind.BRANCH);
		}
		// Vines creeping up the dead wood.
		for (int i = 0; i < height; i++) {
			if (r.nextInt(3) != 0) {
				continue;
			}
			Direction face = Direction.Plane.HORIZONTAL.getRandomDirection(r);
			int x = Mth.floor(cx) + face.getStepX() * (radius > 0.9 ? 2 : 1);
			int z = Mth.floor(cz) + face.getStepZ() * (radius > 0.9 ? 2 : 1);
			if (b.isLog(x - face.getStepX(), y0 + i, z - face.getStepZ())) {
				b.decoration(x, y0 + i, z, Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(face.getOpposite()), true));
			}
		}
	}

	/** Soil and litter under the tree. */
	private void groundCover(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos base) {
		int radius = switch (style) {
			case REDWOOD -> 6;
			case AUTUMN, ANCIENT, DARK -> 7;
			case BAOBAB -> 4;
			default -> 0;
		};
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius || !window.contains(base.getX() + dx, base.getZ() + dz)) {
					continue;
				}
				int ground = WorldGenUtil.groundY(level, base.getX() + dx, base.getZ() + dz);
				if (Math.abs(ground - base.getY()) > 3) {
					continue;
				}
				pos.set(base.getX() + dx, ground, base.getZ() + dz);
				BlockState state = level.getBlockState(pos);
				if (!state.is(Blocks.GRASS_BLOCK)) {
					continue;
				}
				switch (style) {
					case REDWOOD -> {
						int roll = random.nextInt(10);
						if (roll < 4) {
							WaterWorks.set(level, pos, Blocks.PODZOL.defaultBlockState());
						} else if (roll < 5) {
							WaterWorks.set(level, pos, Blocks.COARSE_DIRT.defaultBlockState());
						}
						if (random.nextInt(9) == 0 && level.getBlockState(pos.move(Direction.UP)).isAir()) {
							WaterWorks.set(level, pos, Blocks.FERN.defaultBlockState());
						}
					}
					case BAOBAB -> {
						if (random.nextInt(3) == 0) {
							WaterWorks.set(level, pos, Blocks.COARSE_DIRT.defaultBlockState());
						}
					}
					default -> {
						int chance = style == Style.AUTUMN ? 3 : 7;
						if (random.nextInt(chance) == 0 && level.getBlockState(pos.move(Direction.UP)).isAir()) {
							WaterWorks.set(level, pos, FallenLogFeature.leafLitter(random));
						}
					}
				}
			}
		}
	}

	public enum Style implements StringRepresentable {
		ANCIENT("ancient"),
		DARK("dark"),
		AUTUMN("autumn"),
		REDWOOD("redwood"),
		WILLOW("willow"),
		BAOBAB("baobab"),
		SNAG("snag");

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
