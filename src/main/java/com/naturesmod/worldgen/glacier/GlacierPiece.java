package com.naturesmod.worldgen.glacier;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.ModStructures;
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
 * One stretch of a glacier. Like a river piece, every piece knows the whole glacier so that each
 * column is shaped by exactly one piece: the one owning the nearest segment.
 */
public class GlacierPiece extends StructurePiece {
	/** Width of the rubble ridges (moraines) along the sides and around the snout. */
	private static final int MORAINE = 3;
	/** The ice does not fill hollows deeper than this below its surface: it ends in an ice cliff. */
	private static final int MAX_FILL = 32;
	private static final double CREVASSE_SPACING = 7.0;

	private final GlacierPath path;
	private final int from;
	private final int to;

	GlacierPiece(GlacierPath path, int from, int to) {
		super(ModStructures.GLACIER_PIECE, 0, bounds(path, from, to));
		this.path = path;
		this.from = from;
		this.to = to;
	}

	public GlacierPiece(CompoundTag tag) {
		super(ModStructures.GLACIER_PIECE, tag);
		this.path = new GlacierPath(tag.getIntArray("xs").orElse(new int[0]), tag.getIntArray("zs").orElse(new int[0]),
				tag.getIntArray("surfaces").orElse(new int[0]), tag.getIntArray("widths").orElse(new int[0]), tag.getIntOr("lake_x", 0),
				tag.getIntOr("lake_z", 0), tag.getIntOr("lake_level", 0));
		this.from = tag.getIntOr("from", 0);
		this.to = tag.getIntOr("to", 0);
	}

	private static BoundingBox bounds(GlacierPath path, int from, int to) {
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		int last = path.size() - 1;
		for (int i = from; i <= Math.min(to, last); i++) {
			int reach = (int) Math.ceil(path.width(i)) + MORAINE + 2;
			minX = Math.min(minX, path.xs[i] - reach);
			maxX = Math.max(maxX, path.xs[i] + reach);
			minZ = Math.min(minZ, path.zs[i] - reach);
			maxZ = Math.max(maxZ, path.zs[i] + reach);
			minY = Math.min(minY, path.surfaces[i] - MAX_FILL - 2);
			maxY = Math.max(maxY, path.surfaces[i] + 4);
		}
		if (to >= last) {
			int reach = path.lakeRadius() + MORAINE + 2;
			minX = Math.min(minX, path.lakeX - reach);
			maxX = Math.max(maxX, path.lakeX + reach);
			minZ = Math.min(minZ, path.lakeZ - reach);
			maxZ = Math.max(maxZ, path.lakeZ + reach);
			minY = Math.min(minY, path.lakeLevel - 6);
		}
		return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putIntArray("xs", path.xs);
		tag.putIntArray("zs", path.zs);
		tag.putIntArray("surfaces", path.surfaces);
		tag.putIntArray("widths", path.widths);
		tag.putInt("lake_x", path.lakeX);
		tag.putInt("lake_z", path.lakeZ);
		tag.putInt("lake_level", path.lakeLevel);
		tag.putInt("from", from);
		tag.putInt("to", to);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		if (path.size() < 2) {
			return;
		}
		GlacierPath.Nearest nearest = new GlacierPath.Nearest();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = Math.max(chunkBox.minX(), boundingBox.minX());
		int maxX = Math.min(chunkBox.maxX(), boundingBox.maxX());
		int minZ = Math.max(chunkBox.minZ(), boundingBox.minZ());
		int maxZ = Math.min(chunkBox.maxZ(), boundingBox.maxZ());
		int last = path.size() - 1;
		int lakeRadius = path.lakeRadius();
		boolean touched = false;

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				path.nearest(x + 0.5, z + 0.5, nearest);
				int segment = nearest.segment;
				double lakeDistance = Math.sqrt(Mth.square(x - path.lakeX) + Mth.square(z - path.lakeZ));
				double iceEdge = segment < 0 ? Double.MAX_VALUE
						: nearest.distance - Mth.lerp(nearest.t, path.width(segment), path.width(segment + 1));
				if (lakeDistance <= lakeRadius + MORAINE && lakeDistance - lakeRadius < iceEdge) {
					// The meltwater lake belongs to the piece holding the snout.
					if (to >= last) {
						touched |= lake(level, random, pos, x, z, lakeDistance - lakeRadius);
					}
					continue;
				}
				if (segment < 0 || segment < from || segment >= to) {
					continue;
				}
				double t = nearest.t;
				double width = Mth.lerp(t, path.width(segment), path.width(segment + 1));
				double surface = Mth.lerp(t, path.surfaces[segment], path.surfaces[segment + 1]);
				// Past the snout the distance grows from the last point: a rounded ice front.
				double f = (segment + t) / last;
				if (nearest.distance <= width) {
					touched |= ice(level, random, pos, x, z, nearest.distance / width, surface, f, nearest.along, path.surfaces[segment]
							- path.surfaces[segment + 1]);
				} else if (nearest.distance <= width + MORAINE) {
					touched |= moraine(level, random, pos, x, z, nearest.distance - width);
				}
			}
		}
		if (touched) {
			FeatureStats.placed("glacier_chunk", chunkPos.getMiddleBlockPosition(path.surfaces[from]));
		}
	}

	/**
	 * A column of glacier ice. The surface is convex, sagging toward the margins; the top is snow
	 * up in the basin and bare ice on the lower tongue, and the body is packed ice over a core of
	 * deep blue ice. Where the bed steepens the ice cracks into crevasses.
	 */
	private static boolean ice(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int x, int z, double edge,
			double surface, double f, double along, int drop) {
		int ground = WorldGenUtil.groundY(level, x, z);
		int top = (int) Math.floor(surface - Math.pow(edge, 2.2) * 7);
		if (ground >= top || top - ground > MAX_FILL || level.getFluidState(pos.set(x, ground + 1, z)).is(net.minecraft.tags.FluidTags.LAVA)) {
			return false;
		}
		// Crevasses: cracks across the flow that bend downstream toward the margins (chevrons).
		boolean steep = drop >= 2;
		int crevasseDepth = 0;
		if ((steep || f > 0.3 && f < 0.8) && edge < 0.88) {
			double s = along - edge * edge * 4.0;
			double phase = s - Math.floor(s / CREVASSE_SPACING) * CREVASSE_SPACING;
			if (phase < (steep ? 1.6 : 1.0)) {
				crevasseDepth = Math.min(top - ground - 1, (steep ? 6 : 3) + (int) Math.round((1 - edge) * 4));
			}
		}
		boolean accumulation = f < 0.55;
		for (int y = ground - 1; y <= top; y++) {
			pos.set(x, y, z);
			BlockState existing = level.getBlockState(pos);
			if (existing.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
				continue;
			}
			int depth = top - y;
			BlockState state;
			if (depth < crevasseDepth) {
				state = WaterWorks.AIR;
			} else if (depth == 0) {
				state = accumulation || random.nextInt(5) == 0 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
			} else if (depth <= 3) {
				state = accumulation && depth == 1 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
			} else {
				state = Blocks.BLUE_ICE.defaultBlockState();
			}
			WaterWorks.set(level, pos, state);
		}
		// Whatever stood on the old ground (snow layers, grass, a stray sapling) is buried; clear the
		// odd plant or snow layer left sticking out above the new surface.
		for (int y = top + 1; y <= top + 2; y++) {
			pos.set(x, y, z);
			BlockState above = level.getBlockState(pos);
			if (!above.isAir() && WorldGenUtil.isClearable(above) && above.getFluidState().isEmpty()) {
				WaterWorks.set(level, pos, WaterWorks.AIR);
			}
		}
		return true;
	}

	/** Lateral and end moraines: low ridges of rubble the ice has pushed aside. */
	private static boolean moraine(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int x, int z, double fromEdge) {
		int ground = WorldGenUtil.groundY(level, x, z);
		BlockState top = level.getBlockState(pos.set(x, ground, z));
		if (!WorldGenUtil.isTerrain(top) || !level.getFluidState(pos.set(x, ground + 1, z)).isEmpty()) {
			return false;
		}
		WaterWorks.set(level, pos.set(x, ground, z), rubble(random));
		// A crest one block high along the middle of the ridge.
		if (Math.abs(fromEdge - MORAINE / 2.0) < 0.9 && random.nextInt(3) > 0) {
			BlockState above = level.getBlockState(pos.set(x, ground + 1, z));
			if (above.isAir() || WorldGenUtil.isClearable(above)) {
				WaterWorks.set(level, pos, rubble(random));
			}
		}
		return true;
	}

	/** The meltwater lake: a bowl of milky water in front of the snout, dammed by rubble. */
	private boolean lake(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int x, int z, double fromShore) {
		int water = path.lakeLevel;
		int ground = WorldGenUtil.groundY(level, x, z);
		if (fromShore > 0) {
			// The rim: build a rubble dam where the land is lower than the water.
			if (ground < water && water - ground <= 6) {
				for (int y = ground + 1; y <= water; y++) {
					WaterWorks.set(level, pos.set(x, y, z), rubble(random));
				}
				return true;
			}
			return ground >= water && moraine(level, random, pos, x, z, fromShore);
		}
		if (water - ground > 8 || ground - water > 10) {
			return false;
		}
		int depth = 1 + (int) Math.round(Math.min(1, -fromShore / Math.max(1, path.lakeRadius())) * 4);
		int bed = water - depth;
		for (int y = Math.max(ground, water) + 2; y > water; y--) {
			BlockState state = level.getBlockState(pos.set(x, y, z));
			if (!state.isAir() && !state.is(BlockTags.FEATURES_CANNOT_REPLACE)) {
				WaterWorks.set(level, pos, WaterWorks.AIR);
			}
		}
		for (int y = bed + 1; y <= water; y++) {
			WaterWorks.set(level, pos.set(x, y, z), WaterWorks.WATER);
		}
		WaterWorks.set(level, pos.set(x, bed, z), random.nextInt(3) == 0 ? Blocks.CLAY.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
		return true;
	}

	private static BlockState rubble(RandomSource random) {
		return switch (random.nextInt(8)) {
			case 0, 1, 2 -> Blocks.GRAVEL.defaultBlockState();
			case 3, 4 -> Blocks.COBBLESTONE.defaultBlockState();
			case 5 -> Blocks.ANDESITE.defaultBlockState();
			case 6 -> Blocks.TUFF.defaultBlockState();
			default -> Blocks.STONE.defaultBlockState();
		};
	}
}
