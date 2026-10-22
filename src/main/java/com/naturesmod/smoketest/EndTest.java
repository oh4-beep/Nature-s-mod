package com.naturesmod.smoketest;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.mojang.datafixers.util.Pair;

import com.naturesmod.block.ModBlocks;
import com.naturesmod.end.EndOverhaul;
import com.naturesmod.tree.ServerScheduler;
import com.naturesmod.worldgen.FeatureStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.MapColor;

/**
 * The End half of the smoke test: how the new End is shaped, which biomes it has where, what its
 * features built, and whether its bounce blooms and updraft vents work.
 */
final class EndTest {
	private static final String[] BIOMES = {"chorus_forest", "crystal_highlands", "starfall_plains", "void_marsh", "shattered_spires",
			"enderglow_wilds", "sky_isles"};
	private final SmokeTest test;
	private final MinecraftServer server;
	private ServerLevel end;

	EndTest(MinecraftServer server, SmokeTest test) {
		this.server = server;
		this.test = test;
	}

	void run(Runnable done) {
		try {
			long start = System.currentTimeMillis();
			BlockPos land = survey();
			System.out.printf("[smoketest] end surveyed in %.1fs%n", (System.currentTimeMillis() - start) / 1000.0);
			if (land != null) {
				mechanics(land, done);
				return;
			}
		} catch (Throwable t) {
			test.fail("end test exception %s", t);
			for (StackTraceElement element : t.getStackTrace()) {
				test.line("    at %s", element);
			}
		}
		done.run();
	}

	/** Surveys the End and returns a spot on solid land out on the continents. */
	private BlockPos survey() {
		test.line("");
		test.line("=== The End ===");
		end = server.getLevel(Level.END);
		if (end == null) {
			test.fail("the End is missing");
			return null;
		}
		ChunkGenerator generator = end.getChunkSource().getGenerator();
		RandomState randomState = end.getChunkSource().randomState();

		// A map of the End, 4096 blocks across, from the noise alone.
		int n = 129;
		int step = 32;
		BufferedImage map = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
		int outer = 0;
		int outerLand = 0;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				int x = (i - n / 2) * step;
				int z = (j - n / 2) * step;
				int top = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, end, randomState);
				boolean land = top > end.getMinY() + 1;
				if (x * x + z * z > 1150 * 1150) {
					outer++;
					if (land) {
						outerLand++;
					}
				}
				int shade = Math.max(0, Math.min(255, top));
				map.setRGB(i, j, land ? (shade << 16) | ((shade * 3 / 4) << 8) | Math.min(255, shade + 60) : 0x05030A);
			}
		}
		TerrainRenderer.emit("end_map", map);
		test.line("end: %d%% of the outer End is land", 100 * outerLand / Math.max(1, outer));
		if (outerLand * 4 < outer) {
			test.fail("the outer End has too little land: %d%%", 100 * outerLand / Math.max(1, outer));
		}

		// A slice through 1024 blocks of the outer End, from the void ring outwards.
		int width = 1024;
		BufferedImage slice = new BufferedImage(width, 256, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < width; i++) {
			NoiseColumn column = generator.getBaseColumn(900 + i, 0, end, randomState);
			for (int y = 0; y < 256; y++) {
				BlockState state = column.getBlock(y);
				slice.setRGB(i, 255 - y, state.isAir() ? 0x05030A : 0xD8D49A);
			}
		}
		TerrainRenderer.emit("end_slice", slice);

		// Which biomes live where, out beyond the void ring.
		Map<String, Integer> seen = new TreeMap<>();
		Map<String, BlockPos> where = new LinkedHashMap<>();
		for (int y : new int[] {70, 100, 160, 190}) {
			Map<String, Integer> counts = new TreeMap<>();
			for (int i = 0; i <= 32; i++) {
				for (int j = 0; j <= 32; j++) {
					int x = 1200 + i * 128;
					int z = -2048 + j * 128;
					Holder<Biome> biome = end.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2);
					String name = biome.getRegisteredName().replace("minecraft:", "").replace("naturesmod:", "");
					counts.merge(name, 1, Integer::sum);
					int top = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, end, randomState);
					if (y == 70 && top > 40 && top < 130 && !where.containsKey(name)) {
						where.put(name, new BlockPos(x, top, z));
					}
				}
			}
			test.line("biomes at y=%d: %s", y, counts);
			counts.forEach((name, count) -> seen.merge(name, count, Integer::sum));
		}
		for (String biome : BIOMES) {
			if (!seen.containsKey(biome)) {
				test.fail("the %s biome never shows up in the End", biome);
			}
		}

		// Generate a little of each biome for real, and see what grew there.
		Map<String, Integer> blocks = new TreeMap<>();
		BlockPos land = null;
		long start = System.currentTimeMillis();
		for (Map.Entry<String, BlockPos> entry : where.entrySet()) {
			BlockPos at = entry.getValue();
			for (int cx = -2; cx <= 2; cx++) {
				for (int cz = -2; cz <= 2; cz++) {
					end.getChunk((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz);
				}
			}
			Map<String, Integer> here = count(at, 32);
			here.forEach((name, c) -> blocks.merge(name, c, Integer::sum));
			test.line("%s at %s: %s", entry.getKey(), at, here);
			if (land == null && entry.getKey().equals("chorus_forest")) {
				land = at;
			}
			TerrainRenderer.emit("end_" + entry.getKey(), topMap(at.getX() - 40, at.getZ() - 40, 80));
		}
		test.line("generated the End's biomes in %.1fs: %s", (System.currentTimeMillis() - start) / 1000.0, blocks);
		for (String needed : new String[] {"end_moss", "voidstone", "void_crystal", "chorus_log", "chorus_bulb", "ender_grass", "stardust"}) {
			if (blocks.getOrDefault(needed, 0) == 0) {
				test.fail("no %s was found in the generated End", needed);
			}
		}
		Map<String, Integer> features = new TreeMap<>();
		FeatureStats.snapshot().forEach((name, c) -> {
			if (name.startsWith("end_")) {
				features.put(name, c);
			}
		});
		test.line("end features: %s", features);
		for (String feature : new String[] {"end_chorus_tree", "end_crystal_spikes", "end_garden", "end_hanging_vines"}) {
			if (!features.containsKey(feature)) {
				test.fail("no %s was placed in the End", feature);
			}
		}

		// End cities still find a home on the new lands.
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("end_city"));
		Holder<Structure> holder = end.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		Pair<BlockPos, Holder<Structure>> city = generator.findNearestMapStructure(end, HolderSet.direct(holder), new BlockPos(1600, 64, 0), 64, false);
		if (city == null) {
			test.fail("no End city within 64 chunks of the continents");
		} else {
			test.line("end city at %s, in %s", city.getFirst(), end.getBiome(city.getFirst().atY(80)).getRegisteredName());
		}
		if (land == null && !where.isEmpty()) {
			land = where.values().iterator().next();
		}
		return land;
	}

	/** Counts the new End's blocks in a box around a spot. */
	private Map<String, Integer> count(BlockPos at, int reach) {
		Map<String, Integer> counts = new TreeMap<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = at.getX() - reach; x <= at.getX() + reach; x++) {
			for (int z = at.getZ() - reach; z <= at.getZ() + reach; z++) {
				for (int y = 1; y < 250; y++) {
					BlockState state = end.getBlockState(pos.set(x, y, z));
					if (state.isAir() || state.is(Blocks.END_STONE)) {
						continue;
					}
					String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
					if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("naturesmod") || id.equals("obsidian")
							|| id.equals("water") || id.equals("chorus_plant") || id.equals("crying_obsidian")) {
						counts.merge(id, 1, Integer::sum);
					}
				}
			}
		}
		return counts;
	}

	/** A top-down view of the generated blocks around a spot. */
	private BufferedImage topMap(int minX, int minZ, int size) {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int top = end.getHeight(Heightmap.Types.WORLD_SURFACE, minX + i, minZ + j) - 1;
				if (top <= end.getMinY()) {
					image.setRGB(i, j, 0x05030A);
					continue;
				}
				BlockState state = end.getBlockState(pos.set(minX + i, top, minZ + j));
				MapColor color = state.getMapColor(end, pos);
				int rgb = color.col == 0 ? 0x808080 : color.col;
				// Shade by height so the shapes show.
				double light = 0.55 + 0.45 * Math.min(1.0, Math.max(0.0, (top - 40) / 120.0));
				int r = (int) (((rgb >> 16) & 255) * light);
				int g = (int) (((rgb >> 8) & 255) * light);
				int b = (int) ((rgb & 255) * light);
				image.setRGB(i, j, (r << 16) | (g << 8) | b);
			}
		}
		return image;
	}

	/** Bounce blooms throw what steps on them into the air; updraft vents are found under a player. */
	private void mechanics(BlockPos land, Runnable done) {
		int top = end.getHeight(Heightmap.Types.MOTION_BLOCKING, land.getX(), land.getZ());
		BlockPos bloom = new BlockPos(land.getX(), top, land.getZ());
		for (int y = 1; y <= 12; y++) {
			end.setBlock(bloom.above(y), Blocks.AIR.defaultBlockState(), 3);
		}
		end.setBlock(bloom, ModBlocks.BOUNCE_BLOOM.defaultBlockState(), 3);
		BlockPos vent = bloom.offset(3, 0, 0);
		end.setBlock(vent, ModBlocks.UPDRAFT_VENT.defaultBlockState(), 3);
		for (int y = 1; y <= 12; y++) {
			end.setBlock(vent.above(y), Blocks.AIR.defaultBlockState(), 3);
		}
		boolean lifts = EndOverhaul.overVent(end, vent.above(6));
		end.setBlock(vent.above(3), Blocks.END_STONE.defaultBlockState(), 3);
		boolean blocked = EndOverhaul.overVent(end, vent.above(6));
		test.line("updraft vent at %s: lifts a player 6 blocks up: %s, through a roof: %s", vent, lifts, blocked);
		if (!lifts || blocked) {
			test.fail("the updraft vent's column of air is not found as it should be");
		}
		Mob jumper = EntityTypes.ZOMBIE.create(end, EntitySpawnReason.COMMAND);
		if (jumper == null) {
			done.run();
			return;
		}
		jumper.setPos(bloom.getX() + 0.5, bloom.getY() + 1.0, bloom.getZ() + 0.5);
		jumper.setPersistenceRequired();
		end.addFreshEntity(jumper);
		double startY = jumper.getY();
		double[] highest = {startY};
		for (int t = 1; t <= 30; t++) {
			ServerScheduler.schedule(t, () -> highest[0] = Math.max(highest[0], jumper.getY()));
		}
		ServerScheduler.schedule(31, () -> {
			test.line("bounce bloom at %s threw a zombie %.1f blocks up", bloom, highest[0] - startY);
			if (highest[0] - startY < 2.0) {
				test.fail("the bounce bloom did not throw the zombie into the air");
			}
			jumper.discard();
			done.run();
		});
	}
}
