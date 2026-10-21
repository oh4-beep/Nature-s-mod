package com.naturesmod.smoketest;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.mojang.datafixers.util.Pair;

import com.naturesmod.abyss.Abyss;
import com.naturesmod.abyss.RiftBlock;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.creature.CreatureSpecies;
import com.naturesmod.creature.ModCreatures;
import com.naturesmod.creature.SpeciesData;
import com.naturesmod.tree.ServerScheduler;
import com.naturesmod.worldgen.FeatureStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;

/**
 * The sea half of the smoke test: surveys and renders the Abyss, finds one of each sunken
 * structure and renders it, rides an Abyssal Rift down and back up, and lets every sea creature
 * swim, fight and die once.
 */
final class SeaTest {
	private final SmokeTest test;
	private final MinecraftServer server;
	private final ServerLevel overworld;
	private ServerLevel abyss;
	private BlockPos riftSite;

	SeaTest(MinecraftServer server, SmokeTest test) {
		this.server = server;
		this.test = test;
		this.overworld = server.overworld();
	}

	void run(Runnable done) {
		try {
			long start = System.currentTimeMillis();
			abyssSurvey();
			System.out.printf("[smoketest] abyss surveyed in %.1fs%n", (System.currentTimeMillis() - start) / 1000.0);
			structures();
			System.out.printf("[smoketest] sea structures checked in %.1fs%n", (System.currentTimeMillis() - start) / 1000.0);
			riftTravel();
			creatures(done);
		} catch (Throwable t) {
			test.fail("sea test exception %s", t);
			for (StackTraceElement element : t.getStackTrace()) {
				test.line("    at %s", element);
			}
			done.run();
		}
	}

	// --- the Abyss ---------------------------------------------------------------------------

	private void abyssSurvey() {
		test.line("");
		test.line("=== The Abyss ===");
		abyss = server.getLevel(Abyss.LEVEL);
		if (abyss == null) {
			test.fail("the Abyss dimension is missing");
			return;
		}
		test.line("abyss: minY=%d maxY=%d height=%d seaLevel=%d", abyss.getMinY(), abyss.getMaxY(), abyss.getHeight(), abyss.getSeaLevel());
		if (abyss.getHeight() != 1088 || abyss.getSeaLevel() != Abyss.SEA_LEVEL) {
			test.fail("the Abyss should be 1088 blocks tall with its sea surface at %d", Abyss.SEA_LEVEL);
		}
		ChunkGenerator generator = abyss.getChunkSource().getGenerator();
		RandomState randomState = abyss.getChunkSource().randomState();

		// The sea floor, straight from the noise, over 2048 x 2048 blocks.
		int n = 129;
		int step = 16;
		int[] floors = new int[n * n];
		BufferedImage map = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				int x = (i - n / 2) * step;
				int z = (j - n / 2) * step;
				int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, abyss, randomState) - 1;
				floors[i * n + j] = floor;
				map.setRGB(i, j, depthColor(floor));
			}
		}
		TerrainRenderer.emit("abyss_floor", map);
		test.summary("abyss floor", floors);

		// A vertical slice through 1024 blocks of the sea.
		int width = 1024;
		BufferedImage slice = new BufferedImage(width, abyss.getHeight() / 2, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < width; i++) {
			NoiseColumn column = generator.getBaseColumn(i - width / 2, 0, abyss, randomState);
			for (int y = 0; y < abyss.getHeight(); y += 2) {
				BlockState state = column.getBlock(y);
				int color;
				if (state.isAir()) {
					color = 0x000000;
				} else if (!state.getFluidState().isEmpty()) {
					double depth = (Abyss.SEA_LEVEL - y) / (double) Abyss.SEA_LEVEL;
					color = rgb(10 + (int) (30 * (1 - depth)), 30 + (int) (90 * (1 - depth)), 60 + (int) (140 * (1 - depth)));
				} else {
					color = y < 6 ? 0x202020 : rgb(110, 105, 100);
				}
				slice.setRGB(i, slice.getHeight() - 1 - y / 2, color);
			}
		}
		TerrainRenderer.emit("abyss_slice", slice);

		// Which biomes live at which depths.
		for (int y : new int[] {40, 90, 160, 300, 500, 700, 900, 1040}) {
			Map<String, Integer> counts = new TreeMap<>();
			for (int i = -16; i <= 16; i++) {
				for (int j = -16; j <= 16; j++) {
					Holder<Biome> biome = abyss.getUncachedNoiseBiome((i * 64) >> 2, y >> 2, (j * 64) >> 2);
					counts.merge(biome.getRegisteredName().replace("naturesmod:", ""), 1, Integer::sum);
				}
			}
			test.line("biomes at y=%d: %s", y, counts);
		}
		// Biomes right on the floor.
		Map<String, Integer> floorBiomes = new TreeMap<>();
		for (int i = 0; i < n; i += 4) {
			for (int j = 0; j < n; j += 4) {
				int x = (i - n / 2) * step;
				int z = (j - n / 2) * step;
				Holder<Biome> biome = abyss.getUncachedNoiseBiome(x >> 2, (floors[i * n + j] + 2) >> 2, z >> 2);
				floorBiomes.merge(biome.getRegisteredName().replace("naturesmod:", ""), 1, Integer::sum);
			}
		}
		test.line("biomes on the sea floor: %s", floorBiomes);

		// Generate a patch of the Abyss for real and look at it.
		long start = System.currentTimeMillis();
		int chunks = 6;
		for (int cx = -chunks / 2; cx < chunks / 2; cx++) {
			for (int cz = -chunks / 2; cz < chunks / 2; cz++) {
				abyss.getChunk(cx, cz);
			}
		}
		test.line("generated %d abyss chunks in %.1fs", chunks * chunks, (System.currentTimeMillis() - start) / 1000.0);
		TerrainRenderer.emit("abyss_spawn_top", floorMap(abyss, -chunks * 8, -chunks * 8, chunks * 16));
		test.line("abyss features so far: %s", abyssFeatures());
	}

	private static Map<String, Integer> abyssFeatures() {
		Map<String, Integer> counts = new TreeMap<>();
		FeatureStats.snapshot().forEach((name, count) -> {
			if (name.startsWith("abyss") || name.contains("rift") || name.contains("temple") || name.contains("bathysphere") || name.contains("lair")) {
				counts.put(name, count);
			}
		});
		return counts;
	}

	// --- structures ----------------------------------------------------------------------

	private void structures() {
		test.line("");
		test.line("--- sea structures ---");
		riftSite = locate(overworld, "abyssal_rift", 12, 3);
		if (riftSite == null) {
			test.fail("no Abyssal Rift found in the Overworld");
		}
		if (abyss != null) {
			for (String name : new String[] {"sunken_temple", "bathysphere", "leviathan_lair"}) {
				if (locate(abyss, name, name.equals("bathysphere") ? 1 : 2, 2) == null) {
					test.fail("no %s found in the Abyss", name);
				}
			}
		}
		test.line("sea features and structures: %s", abyssFeatures());
	}

	/** Finds the nearest structure of a kind, generates the area around it and renders it. */
	private BlockPos locate(ServerLevel level, String name, int minRiftBlocks, int radiusChunks) {
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, com.naturesmod.NaturesMod.id(name));
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		long start = System.currentTimeMillis();
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, HolderSet.direct(holder), BlockPos.ZERO, 64, false);
		if (found == null) {
			test.line("%s: none within 64 chunks of the origin", name);
			return null;
		}
		BlockPos at = found.getFirst();
		for (int cx = -radiusChunks; cx <= radiusChunks; cx++) {
			for (int cz = -radiusChunks; cz <= radiusChunks; cz++) {
				level.getChunk((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz);
			}
		}
		// Count what got built.
		int reach = radiusChunks * 16;
		Map<String, Integer> blocks = new TreeMap<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minY = Math.max(level.getMinY(), level.getSeaLevel() > 1000 ? 0 : level.getMinY());
		int floor = Integer.MAX_VALUE;
		BlockPos rift = null;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int x = at.getX() + dx;
				int z = at.getZ() + dz;
				int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
				int low = Math.max(minY, top - 140);
				for (int y = low; y <= top + 100 && y < level.getMaxY(); y++) {
					BlockState state = level.getBlockState(pos.set(x, y, z));
					String id = special(state);
					if (id != null) {
						blocks.merge(id, 1, Integer::sum);
						if (state.is(ModBlocks.ABYSSAL_RIFT) && rift == null) {
							rift = pos.immutable();
						}
					}
				}
				if (Math.abs(dx) < 3 && Math.abs(dz) < 3) {
					floor = Math.min(floor, top);
				}
			}
		}
		List<Mob> keepers = level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(48), mob -> mob.getType() == ModCreatures.type("abyssal_leviathan"));
		test.line("%s at %s (floor y=%d, found in %.1fs): %s%s", name, at, floor, (System.currentTimeMillis() - start) / 1000.0, blocks,
				keepers.isEmpty() ? "" : ", guarded by " + keepers.size() + " leviathan(s)");
		if (minRiftBlocks > 0 && blocks.getOrDefault("abyssal_rift", 0) < minRiftBlocks && !name.equals("bathysphere")) {
			test.fail("%s has %d rift blocks, expected at least %d", name, blocks.getOrDefault("abyssal_rift", 0), minRiftBlocks);
		}
		if (blocks.getOrDefault("chest", 0) == 0) {
			test.fail("%s has no treasure chest", name);
		}
		if (name.equals("leviathan_lair") && keepers.isEmpty()) {
			test.fail("the leviathan lair is empty");
		}
		int y = rift != null ? rift.getY() : floor;
		TerrainRenderer.emit("slice_" + name, slice(level, at.getX() - reach, at.getX() + reach, at.getZ(), y - 20, y + 60));
		TerrainRenderer.emit("top_" + name, floorMap(level, at.getX() - reach, at.getZ() - reach, reach * 2 + 1));
		return rift != null ? rift : at;
	}

	private static String special(BlockState state) {
		if (state.is(ModBlocks.ABYSSAL_RIFT)) {
			return "abyssal_rift";
		}
		if (state.is(Blocks.CHEST)) {
			return "chest";
		}
		if (state.is(Blocks.SEA_LANTERN) || state.is(Blocks.BONE_BLOCK) || state.is(Blocks.IRON_CHAIN) || state.is(Blocks.PRISMARINE_BRICKS)
				|| state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.IRON_BLOCK) || state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.MAGMA_BLOCK)
				|| state.is(Blocks.BUBBLE_COLUMN)) {
			return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		}
		return null;
	}

	// --- travelling by rift --------------------------------------------------------------

	private void riftTravel() {
		test.line("");
		if (riftSite == null || abyss == null) {
			test.line("rift travel: skipped");
			return;
		}
		BlockPos rift = riftSite;
		if (!overworld.getBlockState(rift).is(ModBlocks.ABYSSAL_RIFT)) {
			test.fail("rift travel: no rift block at %s", rift);
			return;
		}
		Entity traveller = EntityTypes.COD.create(overworld, EntitySpawnReason.COMMAND);
		traveller.snapTo(rift.getX() + 0.5, rift.getY() + 0.5, rift.getZ() + 0.5, 0.0F, 0.0F);
		overworld.addFreshEntity(traveller);
		RiftBlock block = (RiftBlock) ModBlocks.ABYSSAL_RIFT;
		TeleportTransition down = block.getPortalDestination(overworld, traveller, rift);
		if (down == null || down.newLevel() != abyss) {
			test.fail("rift travel: the rift does not lead to the Abyss");
			return;
		}
		BlockPos arrival = BlockPos.containing(down.position());
		int platform = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				BlockState state = abyss.getBlockState(pos.set(rift.getX() + dx, Abyss.SEA_LEVEL - 1, rift.getZ() + dz));
				if (state.is(ModBlocks.ABYSSAL_RIFT)) {
					platform++;
				}
			}
		}
		Entity arrived = traveller.teleport(down);
		test.line("rift travel: entered at %s, arrived at %s in %s; arrival platform has %d rift blocks; standing on %s", rift, arrival,
				arrived == null ? "nowhere" : arrived.level().dimension().identifier(), platform,
				abyss.getBlockState(arrival.below()).getBlock().getName().getString());
		if (platform != 9) {
			test.fail("rift travel: the arrival platform should hold a 3x3 rift home, found %d", platform);
		}
		if (arrived == null || arrived.level() != abyss) {
			test.fail("rift travel: the traveller did not reach the Abyss");
			return;
		}
		BlockPos home = new BlockPos(rift.getX(), Abyss.SEA_LEVEL - 1, rift.getZ());
		TeleportTransition up = block.getPortalDestination(abyss, arrived, home);
		test.line("rift travel: the way home leads to %s in %s", up == null ? null : BlockPos.containing(up.position()),
				up == null ? null : up.newLevel().dimension().identifier());
		if (up == null || up.newLevel() != overworld || up.position().y < overworld.getSeaLevel() - 1) {
			test.fail("rift travel: the rift in the Abyss does not lead back to the Overworld's sea");
		}
		arrived.discard();
		TerrainRenderer.emit("slice_arrival", slice(abyss, rift.getX() - 12, rift.getX() + 12, rift.getZ(), Abyss.SEA_LEVEL - 45, Abyss.SEA_LEVEL + 6));
	}

	// --- creatures -----------------------------------------------------------------------

	private void creatures(Runnable done) {
		test.line("");
		test.line("--- sea creatures ---");
		ServerLevel level = overworld;
		BlockPos site = riftSite != null ? riftSite : new BlockPos(0, level.getSeaLevel() - 10, 0);
		int cx = site.getX() >> 4;
		int cz = site.getZ() >> 4;
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
				String.format("forceload add %d %d %d %d", (cx - 3) * 16, (cz - 3) * 16, (cx + 3) * 16, (cz + 3) * 16));
		List<Mob> spawned = new ArrayList<>();
		List<double[]> starts = new ArrayList<>();
		int index = 0;
		for (SpeciesData species : CreatureSpecies.ALL) {
			EntityType<? extends Mob> type = ModCreatures.type(species.id());
			int x = site.getX() - 28 + (index % 8) * 8;
			int z = site.getZ() - 28 + (index / 8) * 8;
			index++;
			int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
			int y = Math.max(floor + 2, Math.min(level.getSeaLevel() - 4, floor + 6));
			Mob mob = type.create(level, EntitySpawnReason.COMMAND);
			if (mob == null) {
				test.fail("could not create a %s", species.id());
				continue;
			}
			mob.snapTo(x + 0.5, y, z + 0.5, index * 37.0F, 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.COMMAND, null);
			level.addFreshEntity(mob);
			spawned.add(mob);
			starts.add(new double[] {mob.getX(), mob.getY(), mob.getZ()});
		}
		test.line("spawned %d sea creatures around %s", spawned.size(), site);
		ServerScheduler.schedule(200, () -> {
			try {
				int alive = 0;
				int moved = 0;
				int inWater = 0;
				StringBuilder still = new StringBuilder();
				for (int i = 0; i < spawned.size(); i++) {
					Mob mob = spawned.get(i);
					double[] start = starts.get(i);
					if (!mob.isAlive() || mob.isRemoved()) {
						continue;
					}
					alive++;
					double distance = Math.sqrt(mob.distanceToSqr(start[0], start[1], start[2]));
					if (distance > 0.5) {
						moved++;
					} else {
						still.append(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath()).append(' ');
					}
					if (mob.isInWater() || mob.level().getFluidState(mob.blockPosition()).is(FluidTags.WATER)) {
						inWater++;
					}
				}
				test.line("after 10s: %d alive, %d moved, %d in the water", alive, moved, inWater);
				if (still.length() > 0) {
					test.line("did not move: %s", still);
				}
				if (alive < spawned.size() * 3 / 4) {
					test.fail("too many sea creatures died or vanished in 10 seconds");
				}
				if (moved < alive / 2) {
					test.fail("most sea creatures did not swim about");
				}
				for (Mob mob : spawned) {
					if (mob.isAlive()) {
						mob.kill(level);
					}
				}
				ServerScheduler.schedule(10, () -> {
					Map<String, Integer> drops = new TreeMap<>();
					for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(site).inflate(64))) {
						drops.merge(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(), item.getItem().getCount(), Integer::sum);
						item.discard();
					}
					test.line("their drops: %s", drops);
					if (!drops.containsKey("naturesmod:raw_fish_fillet")) {
						test.fail("sea creatures did not drop fish fillets");
					}
					done.run();
				});
			} catch (Throwable t) {
				test.fail("sea creature check exception %s", t);
				done.run();
			}
		});
	}

	// --- pictures ------------------------------------------------------------------------

	private static int rgb(int r, int g, int b) {
		return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
	}

	/** Deep blue for the deepest floor to pale cyan for the summits of the seamounts. */
	private static int depthColor(int y) {
		double t = Math.max(0, Math.min(1, y / 600.0));
		return rgb((int) (20 + 200 * t * t), (int) (30 + 190 * t), (int) (70 + 170 * t));
	}

	/** Top-down map of the ground under the water, shaded by its slope. */
	private static BufferedImage floorMap(ServerLevel level, int minX, int minZ, int size) {
		int[] heights = new int[size * size];
		BlockState[] states = new BlockState[size * size];
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, minX + x, minZ + z) - 1;
				heights[x * size + z] = y;
				states[x * size + z] = level.getBlockState(new BlockPos(minX + x, y, minZ + z));
			}
		}
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				int h = heights[x * size + z];
				int west = heights[Math.max(0, x - 1) * size + z];
				int north = heights[x * size + Math.max(0, z - 1)];
				double shade = 1.0 + Math.max(-0.4, Math.min(0.4, ((h - west) + (h - north)) * 0.08));
				MapColor color = states[x * size + z].getMapColor(level, BlockPos.ZERO);
				int c = color.col == 0 ? 0x707070 : color.col;
				image.setRGB(x, z, rgb((int) (((c >> 16) & 255) * shade), (int) (((c >> 8) & 255) * shade), (int) ((c & 255) * shade)));
			}
		}
		return image;
	}

	/** Vertical slice along x at a fixed z, with real block colours; water tinted by depth. */
	private static BufferedImage slice(ServerLevel level, int minX, int maxX, int z, int minY, int maxY) {
		minY = Math.max(level.getMinY(), minY);
		maxY = Math.min(level.getMaxY(), maxY);
		int width = maxX - minX + 1;
		int height = maxY - minY + 1;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				BlockState state = level.getBlockState(pos.set(minX + x, minY + y, z));
				int color;
				if (state.isAir()) {
					color = 0x0a0a12;
				} else if (state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN)) {
					color = state.is(Blocks.BUBBLE_COLUMN) ? 0x6aa8e8 : 0x1c3a78;
				} else if (state.is(ModBlocks.ABYSSAL_RIFT)) {
					color = 0x40e0ff;
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
