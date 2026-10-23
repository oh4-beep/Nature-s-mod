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
	 * The spot within a dozen chunks of {@code at} with the most of a biome around it, at the same height. The
	 * closest point of a biome, which a search finds, lies on its edge; what grows in it is best seen inside.
	 * For tests and tools: it looks the biomes up without generating anything.
	 */
	public static BlockPos heart(ServerLevel level, BlockPos at, ResourceKey<Biome> key) {
		int qy = QuartPos.fromBlock(at.getY());
		BlockPos best = at;
		int most = -1;
		for (int dx = -192; dx <= 192; dx += 32) {
			for (int dz = -192; dz <= 192; dz += 32) {
				int count = 0;
				for (int sx = -24; sx < 24; sx += 8) {
					for (int sz = -24; sz < 24; sz += 8) {
						if (level.getUncachedNoiseBiome(QuartPos.fromBlock(at.getX() + dx + sx), qy, QuartPos.fromBlock(at.getZ() + dz + sz)).is(key)) {
							count++;
						}
					}
				}
				if (count > most) {
					most = count;
					best = at.offset(dx, 0, dz);
				}
			}
		}
		return best;
	}
}
