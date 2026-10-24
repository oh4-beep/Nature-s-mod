package com.naturesmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.SelfPlacing;
import com.naturesmod.worldgen.WaterWorks;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

/** Drapes curtains of vines down every cliff face in the chunk, with moss along the cliff tops. */
public record CliffVinesFeature(float density, int minDrop) implements SelfPlacing {
	public static final MapCodec<CliffVinesFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.floatRange(0, 1).optionalFieldOf("density", 0.35f).forGetter(CliffVinesFeature::density),
			Codec.intRange(2, 64).optionalFieldOf("min_drop", 5).forGetter(CliffVinesFeature::minDrop)
	).apply(instance, CliffVinesFeature::new));

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int chunkX = origin.getX() & ~15;
		int chunkZ = origin.getZ() & ~15;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int strands = 0;
		for (int x = chunkX; x < chunkX + 16; x++) {
			for (int z = chunkZ; z < chunkZ + 16; z++) {
				int ground = WorldGenUtil.groundY(level, x, z);
				for (Direction direction : Direction.Plane.HORIZONTAL) {
					int nx = x + direction.getStepX();
					int nz = z + direction.getStepZ();
					int neighbour = WorldGenUtil.groundY(level, nx, nz);
					if (neighbour - ground < minDrop || WorldGenUtil.isAlpine(neighbour) || random.nextFloat() >= density) {
						continue;
					}
					BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(direction), true);
					int length = Math.min(neighbour - ground, 3 + random.nextInt(14));
					int placed = 0;
					for (int y = neighbour; y > neighbour - length && y > ground; y--) {
						pos.set(x, y, z);
						if (!level.getBlockState(pos).isAir() || !WorldGenUtil.isTerrain(level.getBlockState(pos.set(nx, y, nz)))) {
							if (placed > 0) {
								break;
							}
							continue;
						}
						WaterWorks.set(level, pos.set(x, y, z), vine);
						placed++;
					}
					if (placed > 0) {
						strands++;
						// Moss creeping over the brink above.
						pos.set(nx, neighbour + 1, nz);
						if (random.nextBoolean() && level.getBlockState(pos).isAir()) {
							WaterWorks.set(level, pos, Blocks.MOSS_CARPET.defaultBlockState());
						}
					}
				}
			}
		}
		if (strands > 0) {
			FeatureStats.placed("cliff_vines");
		}
		return strands > 0;
	}
}
