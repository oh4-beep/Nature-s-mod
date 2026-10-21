package com.naturesmod.smoketest;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.TreeMap;

import com.mojang.datafixers.util.Pair;

import com.naturesmod.block.ModBlocks;
import com.naturesmod.worldgen.FeatureStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.MapColor;

/** The Nether half of the smoke test: how the new Nether is shaped, what lives where and what got built. */
final class NetherTest {
	private final SmokeTest test;
	private final MinecraftServer server;

	NetherTest(MinecraftServer server, SmokeTest test) {
		this.server = server;
		this.test = test;
	}

	void run(Runnable done) {
		try {
			long start = System.currentTimeMillis();
			survey();
			System.out.printf("[smoketest] nether checked in %.1fs%n", (System.currentTimeMillis() - start) / 1000.0);
		} catch (Throwable t) {
			test.fail("nether test exception %s", t);
			for (StackTraceElement element : t.getStackTrace()) {
				test.line("    at %s", element);
			}
		}
		done.run();
	}

	private void survey() {
		test.line("");
		test.line("=== The Nether ===");
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether == null) {
			test.fail("the Nether is missing");
			return;
		}
		test.line("nether: minY=%d maxY=%d height=%d logicalHeight=%d lavaSea=%d", nether.getMinY(), nether.getMaxY(), nether.getHeight(),
				nether.dimensionType().logicalHeight(), nether.getSeaLevel());
		if (nether.getHeight() != 256) {
			test.fail("expected a 256 block tall Nether");
		}
		ChunkGenerator generator = nether.getChunkSource().getGenerator();
		RandomState randomState = nether.getChunkSource().randomState();

		// How open is each height band? Sampled from the noise alone over 2048 x 2048 blocks.
		int bands = 8;
		int[] air = new int[bands];
		int[] lava = new int[bands];
		int[] total = new int[bands];
		for (int x = -1024; x < 1024; x += 64) {
			for (int z = -1024; z < 1024; z += 64) {
				NoiseColumn column = generator.getBaseColumn(x, z, nether, randomState);
				for (int y = 0; y < 256; y++) {
					BlockState state = column.getBlock(y);
					int band = y * bands / 256;
					total[band]++;
					if (state.isAir()) {
						air[band]++;
					} else if (state.is(Blocks.LAVA)) {
						lava[band]++;
					}
				}
			}
		}
		StringBuilder profile = new StringBuilder();
		for (int band = 0; band < bands; band++) {
			profile.append(String.format(" y%d-%d: %d%% air %d%% lava |", band * 32, band * 32 + 31, 100 * air[band] / total[band],
					100 * lava[band] / total[band]));
		}
		test.line("openness by height:%s", profile);
		int upperAir = 0;
		int upperTotal = 0;
		for (int band = 4; band < 7; band++) {
			upperAir += air[band];
			upperTotal += total[band];
		}
		if (upperAir * 100 / upperTotal < 25) {
			test.fail("the upper Nether caverns are not open enough (%d%% air)", upperAir * 100 / upperTotal);
		}

		// A slice through 1024 blocks of it.
		int width = 1024;
		BufferedImage slice = new BufferedImage(width, 256, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < width; i++) {
			NoiseColumn column = generator.getBaseColumn(i - width / 2, 0, nether, randomState);
			for (int y = 0; y < 256; y++) {
				BlockState state = column.getBlock(y);
				int color = state.isAir() ? 0x140808 : state.is(Blocks.LAVA) ? 0xE8641C : y < 5 || y > 250 ? 0x202020 : 0x6E2A28;
				slice.setRGB(i, 255 - y, color);
			}
		}
		TerrainRenderer.emit("nether_slice", slice);

		// Which biomes live where.
		for (int y : new int[] {40, 80, 120, 150, 180, 210}) {
			Map<String, Integer> counts = new TreeMap<>();
			for (int i = -16; i <= 16; i++) {
				for (int j = -16; j <= 16; j++) {
					Holder<Biome> biome = nether.getUncachedNoiseBiome((i * 64) >> 2, y >> 2, (j * 64) >> 2);
					counts.merge(biome.getRegisteredName().replace("minecraft:", "").replace("naturesmod:", "+"), 1, Integer::sum);
				}
			}
			test.line("biomes at y=%d: %s", y, counts);
		}

		// Generate a patch for real.
		long start = System.currentTimeMillis();
		int chunks = 6;
		for (int cx = -chunks / 2; cx < chunks / 2; cx++) {
			for (int cz = -chunks / 2; cz < chunks / 2; cz++) {
				nether.getChunk(cx, cz);
			}
		}
		test.line("generated %d nether chunks in %.1fs", chunks * chunks, (System.currentTimeMillis() - start) / 1000.0);
		Map<String, Integer> blocks = new TreeMap<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -chunks * 8; x < chunks * 8; x++) {
			for (int z = -chunks * 8; z < chunks * 8; z++) {
				for (int y = 1; y < 255; y++) {
					BlockState state = nether.getBlockState(pos.set(x, y, z));
					String id = state.is(ModBlocks.NETHER_GEYSER) ? "geyser" : state.is(Blocks.GLOWSTONE) ? "glowstone"
							: state.is(Blocks.CRYING_OBSIDIAN) ? "crying_obsidian" : state.is(Blocks.IRON_BARS) ? "cage_bars"
							: state.is(Blocks.LAVA) && y > nether.getSeaLevel() ? "lava_above_sea" : null;
					if (id != null) {
						blocks.merge(id, 1, Integer::sum);
					}
				}
			}
		}
		test.line("nether blocks of note near spawn: %s", blocks);
		for (int z : new int[] {-24, 0, 24}) {
			TerrainRenderer.emit("nether_spawn_slice_" + (z + 24), blockSlice(nether, -chunks * 8, chunks * 8 - 1, z, 0, 255));
		}

		// The old landmarks still have a place to stand.
		for (String name : new String[] {"fortress", "bastion_remnant"}) {
			ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(name));
			Holder<Structure> holder = nether.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
			Pair<BlockPos, Holder<Structure>> found = generator.findNearestMapStructure(nether, HolderSet.direct(holder), BlockPos.ZERO, 48, false);
			if (found == null) {
				test.fail("no %s within 48 chunks", name);
				continue;
			}
			BlockPos at = found.getFirst();
			for (int cx = -2; cx <= 2; cx++) {
				for (int cz = -2; cz <= 2; cz++) {
					nether.getChunk((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz);
				}
			}
			int bricks = 0;
			for (int x = at.getX() - 40; x <= at.getX() + 40; x++) {
				for (int z = at.getZ() - 40; z <= at.getZ() + 40; z++) {
					for (int y = 20; y < 130; y++) {
						BlockState state = nether.getBlockState(pos.set(x, y, z));
						if (state.is(Blocks.NETHER_BRICKS) || state.is(Blocks.POLISHED_BLACKSTONE_BRICKS) || state.is(Blocks.BLACKSTONE)) {
							bricks++;
						}
					}
				}
			}
			test.line("%s at %s: %d structure blocks", name, at, bricks);
			TerrainRenderer.emit("nether_" + name, blockSlice(nether, at.getX() - 48, at.getX() + 48, at.getZ(), 0, 160));
		}
		Map<String, Integer> features = new TreeMap<>();
		FeatureStats.snapshot().forEach((name, count) -> {
			if (name.startsWith("nether_")) {
				features.put(name, count);
			}
		});
		test.line("nether features: %s", features);
		if (features.isEmpty()) {
			test.fail("no Nether features were placed");
		}
	}

	private static BufferedImage blockSlice(ServerLevel level, int minX, int maxX, int z, int minY, int maxY) {
		int width = maxX - minX + 1;
		int height = maxY - minY + 1;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				BlockState state = level.getBlockState(pos.set(minX + x, minY + y, z));
				int color;
				if (state.isAir()) {
					color = 0x140808;
				} else if (state.is(Blocks.LAVA)) {
					color = 0xE8641C;
				} else if (state.is(ModBlocks.NETHER_GEYSER)) {
					color = 0xFFE040;
				} else {
					MapColor map = state.getMapColor(level, pos);
					color = map.col == 0 ? 0x808080 : map.col;
				}
				image.setRGB(x, height - 1 - y, color);
			}
		}
		return image;
	}
}
