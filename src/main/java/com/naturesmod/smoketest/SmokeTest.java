package com.naturesmod.smoketest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import com.naturesmod.NaturesMod;
import com.naturesmod.tree.ServerScheduler;
import com.naturesmod.tree.TreeFeller;
import com.naturesmod.worldgen.FeatureStats;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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
	private static final int[][] REGIONS = {{0, 0}, {70, -45}, {-95, 30}, {160, 130}, {-210, -170}, {320, -60}, {-40, 280}, {450, 400},
			{-500, 120}, {600, -350}};
	private static final String RAMP = ".,:;=+*#%@";
	private static final int[] BANDS = {72, 90, 110, 140, 170, 210, 260, 320, 380};

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
				Thread.sleep(25 * 60 * 1000L);
			} catch (InterruptedException e) {
				return;
			}
			System.out.println("[smoketest] TIMEOUT after 25 minutes");
			Runtime.getRuntime().halt(2);
		}, "naturesmod-smoketest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
	}

	private void line(String format, Object... args) {
		report.append(String.format(format, args)).append('\n');
	}

	private void fail(String format, Object... args) {
		failed = true;
		line("FAIL: " + format, args);
	}

	private void start() {
		System.out.println("[smoketest] server started, generating regions");
		try {
			line("=== Nature's Mod worldgen smoke test ===");
			line("overworld: minY=%d maxY=%d height=%d seaLevel=%d", level.getMinY(), level.getMaxY(), level.getHeight(), level.getSeaLevel());
			if (level.getHeight() != 576) {
				fail("expected a 576 block tall overworld");
			}
			long start = System.currentTimeMillis();
			int[] allHeights = new int[0];
			Map<String, Integer> biomes = new TreeMap<>();
			for (int[] region : REGIONS) {
				long regionStart = System.currentTimeMillis();
				int[] heights = region(region[0], region[1], biomes);
				System.out.printf("[smoketest] region %d,%d generated in %.1fs%n", region[0], region[1],
						(System.currentTimeMillis() - regionStart) / 1000.0);
				int offset = allHeights.length;
				allHeights = Arrays.copyOf(allHeights, offset + heights.length);
				System.arraycopy(heights, 0, allHeights, offset, heights.length);
			}
			line("");
			line("generated %d chunks in %.1fs", REGIONS.length * REGION_CHUNKS * REGION_CHUNKS, (System.currentTimeMillis() - start) / 1000.0);
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

	private int[] region(int centerChunkX, int centerChunkZ, Map<String, Integer> biomes) {
		int minChunkX = centerChunkX - REGION_CHUNKS / 2;
		int minChunkZ = centerChunkZ - REGION_CHUNKS / 2;
		for (int cx = 0; cx < REGION_CHUNKS; cx++) {
			for (int cz = 0; cz < REGION_CHUNKS; cz++) {
				level.getChunk(minChunkX + cx, minChunkZ + cz);
			}
		}
		int size = REGION_CHUNKS * 16;
		int minX = minChunkX * 16;
		int minZ = minChunkZ * 16;
		int[] heights = new int[size * size];
		boolean[] water = new boolean[size * size];
		boolean[] elevatedWater = new boolean[size * size];
		int fallingWater = 0;
		int snowCapped = 0;
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
					} else if (!state.isAir() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && !state.canBeReplaced()) {
						break;
					}
					ground--;
				}
				heights[x * size + z] = wet ? top : ground;
				if (ground > 300 && level.getBlockState(pos.set(wx, ground, wz)).is(Blocks.SNOW_BLOCK)) {
					snowCapped++;
				}
				if (x % 16 == 8 && z % 16 == 8) {
					String biome = level.getBiome(pos.set(wx, ground, wz)).getRegisteredName().replace("minecraft:", "");
					biomes.merge(biome, 1, Integer::sum);
				}
			}
		}

		line("");
		line("--- region at chunk %d,%d (blocks %d..%d, %d..%d) ---", centerChunkX, centerChunkZ, minX, minX + size - 1, minZ, minZ + size - 1);
		summary("region", heights);
		int elevated = 0;
		for (boolean b : elevatedWater) {
			elevated += b ? 1 : 0;
		}
		line("elevated water columns: %d, falling water blocks: %d, snow-capped columns above y=300: %d", elevated, fallingWater, snowCapped);
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

	private void summary(String label, int[] heights) {
		int[] sorted = heights.clone();
		Arrays.sort(sorted);
		long sum = 0;
		int above200 = 0;
		int above300 = 0;
		for (int h : sorted) {
			sum += h;
			above200 += h > 200 ? 1 : 0;
			above300 += h > 300 ? 1 : 0;
		}
		line("%s heights: min=%d p10=%d median=%d mean=%.1f p90=%d p99=%d max=%d | >200: %.1f%% >300: %.1f%%", label, sorted[0],
				sorted[sorted.length / 10], sorted[sorted.length / 2], sum / (double) sorted.length, sorted[sorted.length * 9 / 10],
				sorted[sorted.length * 99 / 100], sorted[sorted.length - 1], 100.0 * above200 / sorted.length, 100.0 * above300 / sorted.length);
	}

	/** Fells a tree near spawn and checks that it ends up lying on the ground. */
	private void treeTest() {
		BlockPos base = findTreeBase();
		if (base == null) {
			line("tree test: no natural tree found in the first region, skipped");
			finish();
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
			finish();
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
			finish();
		});
	}

	private BlockPos findTreeBase() {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = (REGIONS[0][0] - REGION_CHUNKS / 2) * 16;
		int minZ = (REGIONS[0][1] - REGION_CHUNKS / 2) * 16;
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
