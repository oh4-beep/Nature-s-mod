package com.naturesmod.worldgen.wild;

import java.util.List;

import com.naturesmod.NaturesMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The wild Overworld: thirteen new biomes grown out of vanilla's, by {@link WildBiomeSource}. The
 * world presets name the source (see tools/worldgen/wild.py); the biomes themselves, their surface,
 * features and creatures are data.
 */
public final class WildOverworld {
	/** Every new biome of the Overworld: eleven on the surface, then two deep in its caves. */
	public static final List<String> BIOMES = List.of("lavender_fields", "golden_prairie", "maple_woods", "glowshroom_forest",
			"tropical_shore", "salt_flats", "volcanic_peaks", "bayou", "tundra", "petrified_forest", "heather_moors", "crystal_caverns",
			"fungal_grotto");

	private WildOverworld() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.BIOME_SOURCE, NaturesMod.id("wild"), WildBiomeSource.CODEC);
	}

	public static ResourceKey<Biome> biome(String name) {
		return ResourceKey.create(Registries.BIOME, NaturesMod.id(name));
	}

	/**
	 * Somewhere in the thick of a biome near {@code at}, at the same height, and on the surface where most of it
	 * lies on dry land. The closest point of a biome, which a search finds, lies on its edge or out in a lake, and
	 * what grows in it is best seen inside: this looks over the few hundred blocks around, and when little of the
	 * biome is there, over a few thousand. For tests and tools: it only works the biomes and the lie of the land
	 * out, and generates nothing.
	 */
	public static BlockPos heart(ServerLevel level, BlockPos at, ResourceKey<Biome> key, boolean dry) {
		Spot near = thickest(level, at, key, dry, 24);
		if (near.count() >= 12) {
			return near.at();
		}
		Spot far = thickest(level, at, key, dry, 96);
		if (far.count() == 0) {
			return near.at();
		}
		Spot there = thickest(level, far.at(), key, dry, 24);
		return there.count() > near.count() ? there.at() : near.at();
	}

	/** A spot, and how many of the 25 places around it are good ones. */
	private record Spot(BlockPos at, int count) {
	}

	/**
	 * Looks at the biome on a 33 by 33 grid of places {@code step} blocks apart, centred on {@code at}, and finds
	 * the middle of the five by five places with the most of the biome (on dry land, when {@code dry}).
	 */
	private static Spot thickest(ServerLevel level, BlockPos at, ResourceKey<Biome> key, boolean dry, int step) {
		int n = 33;
		int reach = n / 2 * step;
		boolean[][] good = new boolean[n][n];
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		int sea = generator.getSeaLevel();
		int qy = QuartPos.fromBlock(at.getY());
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				int x = at.getX() - reach + i * step;
				int z = at.getZ() - reach + j * step;
				if (!level.getUncachedNoiseBiome(QuartPos.fromBlock(x), qy, QuartPos.fromBlock(z)).is(key)) {
					continue;
				}
				if (dry) {
					int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
					if (floor <= sea || floor != generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random)) {
						continue;
					}
				}
				good[i][j] = true;
			}
		}
		Spot best = new Spot(at, 0);
		for (int i = 2; i < n - 2; i++) {
			for (int j = 2; j < n - 2; j++) {
				int count = 0;
				for (int di = -2; di <= 2; di++) {
					for (int dj = -2; dj <= 2; dj++) {
						if (good[i + di][j + dj]) {
							count++;
						}
					}
				}
				if (count > best.count()) {
					best = new Spot(new BlockPos(at.getX() - reach + i * step, at.getY(), at.getZ() - reach + j * step), count);
				}
			}
		}
		return best;
	}
}
