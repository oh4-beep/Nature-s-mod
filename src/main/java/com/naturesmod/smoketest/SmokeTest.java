package com.naturesmod.smoketest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DeflaterOutputStream;

import com.naturesmod.NaturesMod;
import com.naturesmod.tree.ServerScheduler;
import com.naturesmod.tree.TreeFeller;
import com.naturesmod.worldgen.FeatureStats;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Headless worldgen check used by CI ({@code ./gradlew runSmokeTest}). Generates several regions
 * of the Overworld, prints a terrain report with ASCII relief maps and feature counts, fells a
 * tree, then stops the server. Only active with {@code -Dnaturesmod.smoketest=true}.
 */
public final class SmokeTest {
	private static final int REGION_CHUNKS = 8;
	/** Besides the spawn area, the test visits the nearest example of each of these biomes. */
	private static final String[] TARGET_BIOMES = {"jagged_peaks", "stony_peaks", "frozen_peaks", "snowy_slopes", "meadow", "cherry_grove",
			"windswept_hills", "jungle", "badlands", "dappled_forest", "old_growth_spruce_taiga", "swamp", "stony_shore", "desert"};
	private static final String RAMP = ".,:;=+*#%@";
	private static final int[] BANDS = {72, 100, 140, 180, 230, 280, 330, 380, 430};
	/** The terrain survey samples heights every SURVEY_STEP blocks out to SURVEY_RADIUS from spawn. */
	private static final int SURVEY_STEP = 48;
	private static final int SURVEY_RADIUS = 64 * SURVEY_STEP;
	private static final int SUMMIT_REGION_CHUNKS = 16;

	private final StringBuilder report = new StringBuilder();
	private final MinecraftServer server;
	private final ServerLevel level;
	private boolean failed;

	private SmokeTest(MinecraftServer server) {
		this.server = server;
		this.level = server.overworld();
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> System.out.println("[smoketest] server starting"));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> new SmokeTest(server).start());
		// Never let a stuck test hold CI hostage.
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(16 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			System.out.println("[smoketest] TIMEOUT after 16 minutes");
			Runtime.getRuntime().halt(2);
		}, "naturesmod-smoketest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
	}

	void line(String format, Object... args) {
		report.append(String.format(format, args)).append('\n');
	}

	void fail(String format, Object... args) {
		failed = true;
		line("FAIL: " + format, args);
	}

	private void start() {
		System.out.println("[smoketest] server started, generating regions");
		try {
			line("=== Nature's Mod worldgen smoke test ===");
			line("overworld: minY=%d maxY=%d height=%d seaLevel=%d", level.getMinY(), level.getMaxY(), level.getHeight(), level.getSeaLevel());
			if (level.getHeight() != 640) {
				fail("expected a 640 block tall overworld");
			}
			long start = System.currentTimeMillis();
			List<int[]> regions = new ArrayList<>();
			List<String> labels = new ArrayList<>();
			regions.add(new int[] {0, 0, REGION_CHUNKS});
			labels.add("spawn");
			List<int[]> summits = survey();
			System.out.printf("[smoketest] surveyed the terrain in %.1fs%n", (System.currentTimeMillis() - start) / 1000.0);
			for (int i = 0; i < summits.size(); i++) {
				int[] summit = summits.get(i);
				regions.add(new int[] {summit[0] >> 4, summit[1] >> 4, i == 0 ? SUMMIT_REGION_CHUNKS : REGION_CHUNKS});
				labels.add("summit_" + (i + 1));
			}
			for (String name : TARGET_BIOMES) {
				ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.withDefaultNamespace(name));
				var found = level.findClosestBiome3d(holder -> holder.is(key), BlockPos.ZERO, 6400, 32, 64);
				if (found == null) {
					line("no %s within 6400 blocks", name);
					continue;
				}
				BlockPos at = found.getFirst();
				int cx = at.getX() >> 4;
				int cz = at.getZ() >> 4;
				boolean overlaps = false;
				for (int[] other : regions) {
					int reach = (other[2] + REGION_CHUNKS) / 2;
					overlaps |= Math.abs(other[0] - cx) < reach && Math.abs(other[1] - cz) < reach;
				}
				if (!overlaps) {
					regions.add(new int[] {cx, cz, REGION_CHUNKS});
					labels.add(name);
				}
			}
			System.out.printf("[smoketest] located %d regions in %.1fs%n", regions.size(), (System.currentTimeMillis() - start) / 1000.0);
			start = System.currentTimeMillis();
			int[] allHeights = new int[0];
			Map<String, Integer> biomes = new TreeMap<>();
			for (int i = 0; i < regions.size(); i++) {
				int[] region = regions.get(i);
				long regionStart = System.currentTimeMillis();
				int[] heights = region(region[0], region[1], region[2], labels.get(i), biomes);
				System.out.printf("[smoketest] region %s at %d,%d generated in %.1fs%n", labels.get(i), region[0], region[1],
						(System.currentTimeMillis() - regionStart) / 1000.0);
				int offset = allHeights.length;
				allHeights = Arrays.copyOf(allHeights, offset + heights.length);
				System.arraycopy(heights, 0, allHeights, offset, heights.length);
			}
			// Close-ups of the first glacier and mountain river the regions above ran into.
			for (String structure : new String[] {"glacier", "mountain_river"}) {
				List<BlockPos> spots = FeatureStats.locations(structure + "_chunk");
				if (spots.isEmpty()) {
					continue;
				}
				// Centre on the chunks the first structure touched (they are recorded in generation order).
				BlockPos first = spots.getFirst();
				long sumX = 0;
				long sumZ = 0;
				int count = 0;
				for (BlockPos pos : spots) {
					if (pos.distManhattan(first) < 256) {
						sumX += pos.getX();
						sumZ += pos.getZ();
						count++;
					}
				}
				BlockPos spot = new BlockPos((int) (sumX / count), first.getY(), (int) (sumZ / count));
				long regionStart = System.currentTimeMillis();
				region(spot.getX() >> 4, spot.getZ() >> 4, REGION_CHUNKS, structure, biomes);
				regions.add(new int[] {spot.getX() >> 4, spot.getZ() >> 4, REGION_CHUNKS});
				System.out.printf("[smoketest] region %s at %s generated in %.1fs%n", structure, spot,
						(System.currentTimeMillis() - regionStart) / 1000.0);
			}
			line("");
			int chunks = 0;
			for (int[] region : regions) {
				chunks += region[2] * region[2];
			}
			line("generated %d chunks in %.1fs", chunks, (System.currentTimeMillis() - start) / 1000.0);
			summary("ALL REGIONS", allHeights);
			line("biomes sampled: %s", biomes);
			line("features placed: %s", FeatureStats.snapshot());
			if (FeatureStats.snapshot().isEmpty()) {
				fail("no Nature's Mod features were placed");
			}
			treeTest();
		} catch (Throwable t) {
			failed = true;
			line("FAIL: exception %s", t);
			for (StackTraceElement element : t.getStackTrace()) {
				line("    at %s", element);
			}
			finish();
		}
	}

	/**
	 * Samples the terrain height from the noise alone (without generating chunks) over a wide area
	 * around spawn, reports how high the land rises and returns the three highest summits that
	 * are well apart from each other.
	 */
	private List<int[]> survey() {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		NoiseRouter router = generator instanceof NoiseBasedChunkGenerator noise ? noise.generatorSettings().value().noiseRouter() : null;
		List<int[]> samples = new ArrayList<>();
		// The height and climate behind every sample (continentalness, erosion, weirdness and the
		// terrain offset, i.e. depth at y=128 where the depth gradient is zero) go into a compressed
		// data file for offline analysis.
		ByteArrayOutputStream climate = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(climate))) {
			for (int x = -SURVEY_RADIUS; x <= SURVEY_RADIUS; x += SURVEY_STEP) {
				for (int z = -SURVEY_RADIUS; z <= SURVEY_RADIUS; z += SURVEY_STEP) {
					int height = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
					samples.add(new int[] {x, z, height});
					out.writeShort(height);
					if (router != null) {
						out.writeFloat(randomState.sampleBlockValueUncached(router.continents(), x, 128, z));
						out.writeFloat(randomState.sampleBlockValueUncached(router.erosion(), x, 128, z));
						out.writeFloat(randomState.sampleBlockValueUncached(router.ridges(), x, 128, z));
						out.writeFloat(randomState.sampleBlockValueUncached(router.depth(), x, 128, z));
					}
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		TerrainRenderer.emit(router != null ? "climate.bin" : "survey.bin", climate.toByteArray());
		int land = 0;
		int[] landHeights = new int[samples.size()];
		for (int[] sample : samples) {
			if (sample[2] > level.getSeaLevel() + 1) {
				landHeights[land++] = sample[2];
			}
		}
		line("");
		line("--- terrain survey: %d samples every %d blocks within %d blocks of spawn ---", samples.size(), SURVEY_STEP, SURVEY_RADIUS);
		line("land: %.1f%% of samples", 100.0 * land / samples.size());
		if (land > 0) {
			summary("land", Arrays.copyOf(landHeights, land));
		}
		samples.sort((a, b) -> Integer.compare(b[2], a[2]));
		List<int[]> summits = new ArrayList<>();
		for (int[] sample : samples) {
			boolean apart = true;
			for (int[] summit : summits) {
				apart &= Math.abs(summit[0] - sample[0]) > 512 || Math.abs(summit[1] - sample[1]) > 512;
			}
			if (apart) {
				summits.add(sample);
				line("summit %d: y=%d at %d, %d", summits.size(), sample[2], sample[0], sample[1]);
				if (summits.size() == 3) {
					break;
				}
			}
		}
		return summits;
	}

	private int[] region(int centerChunkX, int centerChunkZ, int regionChunks, String label, Map<String, Integer> biomes) {
		int minChunkX = centerChunkX - regionChunks / 2;
		int minChunkZ = centerChunkZ - regionChunks / 2;
		for (int cx = 0; cx < regionChunks; cx++) {
			for (int cz = 0; cz < regionChunks; cz++) {
				level.getChunk(minChunkX + cx, minChunkZ + cz);
			}
		}
		int size = regionChunks * 16;
		int minX = minChunkX * 16;
		int minZ = minChunkZ * 16;
		int[] heights = new int[size * size];
		boolean[] water = new boolean[size * size];
		boolean[] elevatedWater = new boolean[size * size];
		int fallingWater = 0;
		int snowCapped = 0;
		int leaves = 0;
		int decayingLeaves = 0;
		int[] gapTop = new int[size * size];
		int[] gapSize = new int[size * size];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				int wx = minX + x;
				int wz = minZ + z;
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
				int ground = top;
				boolean wet = false;
				while (ground > level.getMinY()) {
					BlockState state = level.getBlockState(pos.set(wx, ground, wz));
					FluidState fluid = state.getFluidState();
					if (fluid.is(FluidTags.WATER)) {
						if (!wet) {
							wet = true;
							water[x * size + z] = true;
							elevatedWater[x * size + z] = ground > level.getSeaLevel() + 2;
						}
						if (!fluid.isSource() && fluid.getValue(FlowingFluid.FALLING)) {
							fallingWater++;
						}
					} else if (state.is(BlockTags.LEAVES)) {
						leaves++;
						if (state.hasProperty(LeavesBlock.DISTANCE) && state.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE
								&& !state.getValue(LeavesBlock.PERSISTENT)) {
							decayingLeaves++;
						}
					} else if (!state.isAir() && !state.is(BlockTags.LOGS) && !state.canBeReplaced()) {
						break;
					}
					ground--;
				}
				heights[x * size + z] = wet ? top : ground;
				// The first air pocket under the top layer of land (an overhang or just a cave).
				if (!wet) {
					int gap = 0;
					for (int y = ground - 1; y > ground - 48 && y > level.getMinY(); y--) {
						if (level.getBlockState(pos.set(wx, y, wz)).isAir()) {
							if (gap++ == 0) {
								gapTop[x * size + z] = y;
							}
						} else if (gap > 0) {
							break;
						}
					}
					gapSize[x * size + z] = gap;
				}
				if (ground > 290 && level.getBlockState(pos.set(wx, ground, wz)).is(Blocks.SNOW_BLOCK)) {
					snowCapped++;
				}
				if (x % 16 == 8 && z % 16 == 8) {
					String biome = level.getBiome(pos.set(wx, ground, wz)).getRegisteredName().replace("minecraft:", "");
					biomes.merge(biome, 1, Integer::sum);
				}
			}
		}

		TerrainRenderer renderer = new TerrainRenderer(level, minX, minZ, size, heights);
		TerrainRenderer.emit("top_" + label, renderer.topDown());
		TerrainRenderer.emit("view_" + label, renderer.oblique());
		if (label.equals("glacier")) {
			TerrainRenderer.emit("ice_" + label, renderer.iceMap());
		}

		line("");
		line("--- %s: region at chunk %d,%d (blocks %d..%d, %d..%d) ---", label, centerChunkX, centerChunkZ, minX, minX + size - 1, minZ,
				minZ + size - 1);
		summary("region", heights);
		int elevated = 0;
		for (boolean b : elevatedWater) {
			elevated += b ? 1 : 0;
		}
		line("elevated water columns: %d, falling water blocks: %d, snow-capped columns above y=290: %d", elevated, fallingWater, snowCapped);
		line("leaves seen from above: %d, of which would decay: %d", leaves, decayingLeaves);
		// An air pocket only counts as an overhang when it opens to the side, i.e. a nearby column's
		// surface lies below the roof. A thin roof over a tall open gap is a floating slab.
		int overhangs = 0;
		int floating = 0;
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				int gap = gapSize[x * size + z];
				if (gap < 3) {
					continue;
				}
				int roof = gapTop[x * size + z];
				boolean open = false;
				for (int dx = -6; dx <= 6 && !open; dx++) {
					for (int dz = -6; dz <= 6 && !open; dz++) {
						int nx = x + dx;
						int nz = z + dz;
						open = nx >= 0 && nz >= 0 && nx < size && nz < size && heights[nx * size + nz] < roof - 1;
					}
				}
				if (open) {
					overhangs++;
					if (gap >= 8 && heights[x * size + z] - roof <= 6) {
						floating++;
					}
				}
			}
		}
		line("columns with an open overhang: %d, with a thin floating slab: %d", overhangs, floating);
		if (leaves > 200 && decayingLeaves * 20 > leaves) {
			fail("more than 5%% of tree leaves are detached and would decay");
		}
		line("legend: ~ sea-level water, w water above sea level, height bands %s from <%d to >=%d", RAMP, BANDS[0], BANDS[BANDS.length - 1]);
		for (int row = 0; row < size; row += 4) {
			StringBuilder map = new StringBuilder();
			for (int col = 0; col < size; col += 2) {
				int best = Integer.MIN_VALUE;
				boolean seaWater = false;
				boolean highWater = false;
				for (int dz = 0; dz < 4; dz++) {
					for (int dx = 0; dx < 2; dx++) {
						int index = (col + dx) * size + (row + dz);
						best = Math.max(best, heights[index]);
						seaWater |= water[index] && !elevatedWater[index];
						highWater |= elevatedWater[index];
					}
				}
				if (highWater) {
					map.append('w');
				} else if (seaWater && best <= level.getSeaLevel() + 1) {
					map.append('~');
				} else {
					int band = 0;
					while (band < BANDS.length && best >= BANDS[band]) {
						band++;
					}
					map.append(RAMP.charAt(band));
				}
			}
			line("  %s", map);
		}
		return heights;
	}

	void summary(String label, int[] heights) {
		int[] sorted = heights.clone();
		Arrays.sort(sorted);
		long sum = 0;
		int above200 = 0;
		int above300 = 0;
		int above400 = 0;
		for (int h : sorted) {
			sum += h;
			above200 += h > 200 ? 1 : 0;
			above300 += h > 300 ? 1 : 0;
			above400 += h > 400 ? 1 : 0;
		}
		line("%s heights: min=%d p10=%d median=%d mean=%.1f p90=%d p99=%d max=%d | >200: %.1f%% >300: %.1f%% >400: %.2f%%", label,
				sorted[0], sorted[sorted.length / 10], sorted[sorted.length / 2], sum / (double) sorted.length, sorted[sorted.length * 9 / 10],
				sorted[sorted.length * 99 / 100], sorted[sorted.length - 1], 100.0 * above200 / sorted.length, 100.0 * above300 / sorted.length,
				100.0 * above400 / sorted.length);
	}

	/** Fells a tree near spawn and checks that it ends up lying on the ground. */
	private void treeTest() {
		BlockPos base = findTreeBase();
		if (base == null) {
			line("tree test: no natural tree found in the first region, skipped");
			seaTest();
			return;
		}
		int cx = base.getX() >> 4;
		int cz = base.getZ() >> 4;
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
				String.format("forceload add %d %d %d %d", (cx - 2) * 16, (cz - 2) * 16, (cx + 2) * 16, (cz + 2) * 16));
		int logsBefore = countLogsAbove(base);
		level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
		int felled = TreeFeller.fellAround(level, base, new Vec3(1, 0, 0.3));
		line("");
		line("tree test: tree at %s with %d logs above the cut, %d logs started falling", base, logsBefore, felled);
		if (felled == 0) {
			fail("chopping the trunk of a natural tree did not make it fall");
			seaTest();
			return;
		}
		ServerScheduler.schedule(160, () -> {
			int stillFalling = level.getEntities(EntityTypeTest.forClass(FallingBlockEntity.class), entity -> true).size();
			int standing = countLogsAbove(base);
			int lying = 0;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int dx = -40; dx <= 40; dx++) {
				for (int dz = -40; dz <= 40; dz++) {
					for (int dy = -6; dy <= 6; dy++) {
						if (level.getBlockState(pos.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz)).is(BlockTags.LOGS)) {
							lying++;
						}
					}
				}
			}
			line("tree test after 8s: %d falling blocks still in the air, %d logs left standing above the cut, %d logs near the ground",
					stillFalling, standing, lying);
			if (standing > 0) {
				fail("logs above the cut were left standing");
			}
			seaTest();
		});
	}

	private void seaTest() {
		new SeaTest(server, this).run(() -> new NetherTest(server, this).run(this::finish));
	}

	private BlockPos findTreeBase() {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = -REGION_CHUNKS / 2 * 16;
		int minZ = -REGION_CHUNKS / 2 * 16;
		for (int x = minX + 8; x < minX + REGION_CHUNKS * 16 - 8; x++) {
			for (int z = minZ + 8; z < minZ + REGION_CHUNKS * 16 - 8; z++) {
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				if (!level.getBlockState(pos.set(x, top, z)).is(BlockTags.LOGS)) {
					continue;
				}
				int y = top;
				while (level.getBlockState(pos.set(x, y - 1, z)).is(BlockTags.LOGS)) {
					y--;
				}
				if (top - y >= 3 && level.getBlockState(pos.set(x, y - 1, z)).is(BlockTags.SUBSTRATE_OVERWORLD)
						&& level.getBlockState(pos.set(x, top + 1, z)).is(BlockTags.LEAVES)) {
					return new BlockPos(x, y, z);
				}
			}
		}
		return null;
	}

	private int countLogsAbove(BlockPos base) {
		int count = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = base.getY() + 1; y < base.getY() + 60; y++) {
			if (level.getBlockState(pos.set(base.getX(), y, base.getZ())).is(BlockTags.LOGS)) {
				count++;
			}
		}
		return count;
	}

	private void finish() {
		line("");
		line(failed ? "RESULT: FAILED" : "RESULT: PASSED");
		String text = report.toString();
		System.out.println(text);
		try {
			Files.writeString(Path.of("naturesmod-report.txt"), text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			NaturesMod.LOGGER.error("Could not write the smoke test report", e);
		}
		if (failed) {
			Runtime.getRuntime().halt(1);
		}
		server.halt(false);
	}
}
