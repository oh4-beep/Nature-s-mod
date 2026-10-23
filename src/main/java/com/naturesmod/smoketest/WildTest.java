package com.naturesmod.smoketest;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.wild.WildBiomeSource;
import com.naturesmod.worldgen.wild.WildOverworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Checks the wild Overworld: that the normal world preset grows the new biomes, that each of them
 * can be found within a few thousand blocks of spawn, and what grows in it (its ground, plants,
 * trees and landmarks), and that the biome's own blocks are really there.
 */
final class WildTest {
	private static final int SEARCH_RADIUS = 9000;
	/** Blocks worth counting in a wild biome. */
	private static final Set<String> NOTABLE = Set.of("lavender", "heather", "cattail", "glowcap", "glowcap_block", "blueberry_bush", "coconut",
			"salt_block", "salt_crystal", "azure_crystal", "azure_crystal_cluster", "citrine_crystal", "citrine_crystal_cluster", "petrified_log",
			"steam_vent", "basalt", "blackstone", "magma_block", "lava", "ash_block", "jungle_log", "jungle_leaves", "spruce_log", "oak_leaves",
			"mushroom_stem", "calcite", "coarse_dirt", "podzol", "mycelium", "moss_block", "tall_grass", "sunflower", "hay_block",
			"pale_hanging_moss", "lily_pad", "mossy_cobblestone", "meteorite", "amethyst_cluster", "charred_log", "wildflowers", "leaf_litter",
			"red_sand", "mud", "sweet_berry_bush", "large_fern", "glow_lichen");
	/** For each biome, blocks one of which must be found in it: its ground, or something that grows in every chunk of it. */
	private static final Map<String, Set<String>> SIGNATURES = Map.of("lavender_fields", Set.of("lavender"), "salt_flats", Set.of("salt_block"),
			"volcanic_peaks", Set.of("blackstone", "basalt"), "heather_moors", Set.of("heather"), "petrified_forest", Set.of("petrified_log"),
			"crystal_caverns", Set.of("azure_crystal", "azure_crystal_cluster", "citrine_crystal", "citrine_crystal_cluster", "amethyst_cluster"),
			"fungal_grotto", Set.of("glowcap", "mycelium", "glowcap_block"));

	private final SmokeTest test;
	private final ServerLevel level;

	WildTest(MinecraftServer server, SmokeTest test) {
		this.test = test;
		this.level = server.overworld();
	}

	void run(Runnable done) {
		try {
			test.line("");
			test.line("=== The wild Overworld ===");
			BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
			if (!(source instanceof WildBiomeSource)) {
				test.fail("the Overworld's biome source is %s, not the wild one", source.getClass().getSimpleName());
				done.run();
				return;
			}
			long start = System.currentTimeMillis();
			for (String name : WildOverworld.BIOMES) {
				visit(name);
			}
			test.line("wild features placed so far: %s", wildFeatures());
			System.out.printf("[smoketest] wild biomes checked in %.1fs%n", (System.currentTimeMillis() - start) / 1000.0);
		} catch (Throwable t) {
			test.fail("the wild Overworld test threw %s", t);
			for (StackTraceElement element : t.getStackTrace()) {
				test.line("    at %s", element);
			}
		}
		done.run();
	}

	private void visit(String name) {
		ResourceKey<Biome> key = WildOverworld.biome(name);
		boolean cave = name.equals("crystal_caverns") || name.equals("fungal_grotto");
		int searchY = name.equals("crystal_caverns") ? -30 : name.equals("fungal_grotto") ? 20 : 90;
		long start = System.currentTimeMillis();
		var found = level.findClosestBiome3d(holder -> holder.is(key), new BlockPos(0, searchY, 0), SEARCH_RADIUS, 32, cave ? 8 : 64);
		if (found == null) {
			test.fail("no %s within %d blocks of spawn", name, SEARCH_RADIUS);
			return;
		}
		BlockPos at = found.getFirst();
		double seconds = (System.currentTimeMillis() - start) / 1000.0;
		int cx = at.getX() >> 4;
		int cz = at.getZ() >> 4;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.getChunk(cx + dx, cz + dz);
			}
		}
		Map<String, Integer> counts = new TreeMap<>();
		int columns = 0;
		int inside = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = (cx - 1) << 4; x < (cx + 2) << 4; x++) {
			for (int z = (cz - 1) << 4; z < (cz + 2) << 4; z++) {
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				int high = cave ? Math.min(top, at.getY() + 32) : top;
				int low = cave ? at.getY() - 32 : top - 40;
				columns++;
				if (level.getBiome(pos.set(x, cave ? at.getY() : top - 1, z)).is(key)) {
					inside++;
				}
				for (int y = low; y <= high; y++) {
					BlockState state = level.getBlockState(pos.set(x, y, z));
					if (state.isAir()) {
						continue;
					}
					String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
					if (NOTABLE.contains(id)) {
						counts.merge(id, 1, Integer::sum);
					}
				}
			}
		}
		test.line("%s: found at %s, %d blocks from spawn, in %.1fs; %d%% of the area around it is %s: %s", name, at.toShortString(),
				(int) Math.sqrt(at.getX() * (double) at.getX() + at.getZ() * (double) at.getZ()), seconds, 100 * inside / columns, name, counts);
		Set<String> signature = SIGNATURES.get(name);
		if (signature != null && signature.stream().noneMatch(counts::containsKey)) {
			test.fail("the %s around %s has none of %s", name, at.toShortString(), signature);
		}
	}

	private static Map<String, Integer> wildFeatures() {
		Map<String, Integer> out = new TreeMap<>();
		FeatureStats.snapshot().forEach((name, count) -> {
			if (name.startsWith("wild_") || name.startsWith("giant_tree_palm") || name.startsWith("giant_tree_cypress")) {
				out.put(name, count);
			}
		});
		return out;
	}
}
