package com.naturesmod.worldgen.river;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.WaterWorks;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One stretch of a mountain river. Every piece knows the whole course of the river so that each
 * column is shaped by exactly one piece: the one owning the nearest river segment.
 */
public class RiverPiece extends StructurePiece {
	/** How high above the water the river may cut through the land before it runs in a tunnel. */
	private static final int MAX_CARVE = 18;
	private static final int BANK = 3;

	private final RiverPath path;
	private final int from;
	private final int to;

	RiverPiece(RiverPath path, int from, int to) {
		super(ModStructures.RIVER_PIECE, 0, bounds(path, from, to));
		this.path = path;
		this.from = from;
		this.to = to;
	}

	public RiverPiece(CompoundTag tag) {
		super(ModStructures.RIVER_PIECE, tag);
		this.path = new RiverPath(tag.getIntArray("xs").orElse(new int[0]), tag.getIntArray("zs").orElse(new int[0]),
				tag.getIntArray("levels").orElse(new int[0]), tag.getIntArray("widths").orElse(new int[0]), tag.getBooleanOr("lake", false));
		this.from = tag.getIntOr("from", 0);
		this.to = tag.getIntOr("to", 0);
	}

	private static BoundingBox bounds(RiverPath path, int from, int to) {
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int i = from; i <= Math.min(to, path.size() - 1); i++) {
			int reach = (int) Math.ceil(path.width(i)) + BANK + 2;
			if (i == path.size() - 1) {
				reach = Math.max(reach, path.lakeRadius() + BANK + 2);
			}
			minX = Math.min(minX, path.xs[i] - reach);
			maxX = Math.max(maxX, path.xs[i] + reach);
			minZ = Math.min(minZ, path.zs[i] - reach);
			maxZ = Math.max(maxZ, path.zs[i] + reach);
			minY = Math.min(minY, path.levels[i] - 8);
			maxY = Math.max(maxY, path.levels[i] + MAX_CARVE + 4);
		}
		return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putIntArray("xs", path.xs);
		tag.putIntArray("zs", path.zs);
		tag.putIntArray("levels", path.levels);
		tag.putIntArray("widths", path.widths);
		tag.putBoolean("lake", path.lakeEnd);
		tag.putInt("from", from);
		tag.putInt("to", to);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		if (path.size() < 2) {
			return;
		}
		RiverPath.Nearest nearest = new RiverPath.Nearest();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = Math.max(chunkBox.minX(), boundingBox.minX());
		int maxX = Math.min(chunkBox.maxX(), boundingBox.maxX());
		int minZ = Math.max(chunkBox.minZ(), boundingBox.minZ());
		int maxZ = Math.min(chunkBox.maxZ(), boundingBox.maxZ());
		boolean touched = false;
		int last = path.size() - 1;
		int lakeRadius = path.lakeRadius();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				path.nearest(x + 0.5, z + 0.5, nearest);
				int segment = nearest.segment;
				double distance = nearest.distance;
				double width;
				int water;
				int upstream;
				boolean atStep;
				double lakeDistance = lakeRadius > 0 ? Math.sqrt(Mth.square(x + 0.5 - path.xs[last] - 0.5) + Mth.square(z + 0.5 - path.zs[last] - 0.5))
						: Double.MAX_VALUE;
				if (lakeDistance <= lakeRadius + BANK && (segment < 0 || lakeDistance - lakeRadius < distance - path.width(Math.max(0, segment)))) {
					// The terminal lake belongs to the piece holding the last point.
					if (to < last) {
						continue;
					}
					width = lakeRadius;
					distance = lakeDistance;
					water = path.levels[last];
					upstream = water;
					atStep = false;
				} else {
					if (segment < 0 || segment < from || segment >= to) {
						continue;
					}
					double t = nearest.t;
					width = Mth.lerp(t, path.width(segment), path.width(segment + 1));
					water = t < 0.5 ? path.levels[segment] : path.levels[segment + 1];
					upstream = path.levels[segment];
					double segmentLength = Math.sqrt(Mth.square(path.xs[segment + 1] - path.xs[segment])
							+ Mth.square(path.zs[segment + 1] - path.zs[segment]));
					atStep = t >= 0.5 && (t - 0.5) * segmentLength < 1.2 && upstream > water;
				}
				if (distance > width + BANK) {
					continue;
				}
				touched = true;
				if (distance <= width) {
					channel(level, random, pos, x, z, distance / width, width, water, atStep ? upstream : water);
				} else {
					bank(level, random, pos, x, z, distance - width, water);
				}
			}
		}
		if (touched) {
			FeatureStats.placed("mountain_river_chunk");
		}
	}

	/** Water, river bed and (just below a drop) the falling curtain of a cascade. */
	private static void channel(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int x, int z, double edge,
			double width, int water, int curtainTop) {
		int ground = WorldGenUtil.groundY(level, x, z);
		int maxDepth = Mth.clamp((int) Math.round(width * 0.6), 1, 4);
		int depth = 1 + (int) Math.round((1 - edge * edge) * (maxDepth - 1));
		if (curtainTop - water >= 3) {
			depth += 2; // plunge pool under a waterfall
		}

		// Open the channel to the sky (or, under a high ridge, leave a tunnel roof).
		int carveTop = Math.min(Math.max(ground, water) + 2, water + MAX_CARVE);
		for (int y = water + 1; y <= carveTop; y++) {
			BlockState state = level.getBlockState(pos.set(x, y, z));
			if (state.isAir() || !state.getFluidState().isEmpty() || state.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
				continue;
			}
			WaterWorks.set(level, pos, WaterWorks.AIR);
		}
		for (int y = water - depth + 1; y <= water; y++) {
			WaterWorks.set(level, pos.set(x, y, z), WaterWorks.WATER);
		}
		BlockState bed = level.getBlockState(pos.set(x, water - depth, z));
		if (!WaterWorks.holdsWater(bed) || WorldGenUtil.isSoftGround(bed) || random.nextInt(3) == 0) {
			WaterWorks.set(level, pos, WaterWorks.bed(random, false));
		}
		if (curtainTop > water) {
			for (int y = water + 1; y <= curtainTop; y++) {
				WaterWorks.set(level, pos.set(x, y, z), y == curtainTop ? WaterWorks.flowing(7, false) : WaterWorks.flowing(8, true));
			}
		}
	}

	/** Shapes the river banks: levees where the land is too low, sloping banks where it is high. */
	private static void bank(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int x, int z, double fromEdge, int water) {
		int ground = WorldGenUtil.groundY(level, x, z);
		if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty() || !level.getFluidState(pos.set(x, water, z)).isEmpty()) {
			return; // leave existing lakes and rivers alone
		}
		BlockState top = level.getBlockState(pos.set(x, ground, z));
		BlockState surface = top.is(BlockTags.SUBSTRATE_OVERWORLD) || top.is(BlockTags.SAND) || top.is(Blocks.SNOW_BLOCK) || top.is(Blocks.GRAVEL)
				? top : Blocks.STONE.defaultBlockState();
		if (ground < water) {
			if (water - ground > 8) {
				return; // the land falls away: no dam, the river may spill over here
			}
			// Levee: build the bank up so the water stays in its bed.
			for (int y = ground + 1; y <= water; y++) {
				WaterWorks.set(level, pos.set(x, y, z), y == water ? surface : Blocks.DIRT.defaultBlockState());
			}
			return;
		}
		int slopeTop = water + 1 + (int) Math.round(fromEdge * 2.5);
		if (ground > slopeTop && ground - slopeTop <= MAX_CARVE) {
			for (int y = slopeTop + 1; y <= ground + 1; y++) {
				BlockState state = level.getBlockState(pos.set(x, y, z));
				if (!state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
					WaterWorks.set(level, pos, WaterWorks.AIR);
				}
			}
			WaterWorks.set(level, pos.set(x, slopeTop, z), surface);
		}
	}
}
