package com.naturesmod.smoketest;

import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.util.Pair;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.disaster.Blizzard;
import com.naturesmod.disaster.Disaster;
import com.naturesmod.disaster.DisasterType;
import com.naturesmod.disaster.Disasters;
import com.naturesmod.disaster.Eruption;
import com.naturesmod.disaster.MeteorShower;
import com.naturesmod.disaster.Tornado;
import com.naturesmod.disaster.Wildfire;
import com.naturesmod.tree.ServerScheduler;
import com.naturesmod.worldgen.deep.LandShowcase;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lets each natural disaster loose on the land and checks that it does what it should: a tornado
 * tears a wooden house apart, lifts the cattle and throws debris about; an earthquake cracks stone
 * bricks, brings down sand left hanging over a hole and opens a fissure along its fault; a meteorite
 * blows out a crater with iron in its floor; a volcano blows, runs lava, lays ash and sends pyroclastic
 * flows down; a wildfire burns through a forest; a sandstorm's wall rolls on; a blizzard piles up snow.
 */
final class DisasterTest {
	private final SmokeTest test;
	private final MinecraftServer server;
	private final ServerLevel level;

	DisasterTest(MinecraftServer server, SmokeTest test) {
		this.server = server;
		this.test = test;
		this.level = server.overworld();
	}

	void run(Runnable done) {
		test.line("");
		test.line("=== Natural disasters ===");
		clearSkies();
		List<Step> steps = List.of(this::tornado, this::quake, this::meteor, this::eruption, this::wildfire, this::sandstorm, this::blizzard);
		chain(steps, 0, () -> {
			Disasters.stop(level, d -> true);
			for (String failure : Disasters.failures()) {
				test.fail("a disaster failed: %s", failure);
			}
			done.run();
		});
	}

	private interface Step {
		void run(Runnable next);
	}

	private void chain(List<Step> steps, int index, Runnable done) {
		if (index >= steps.size()) {
			done.run();
			return;
		}
		long began = System.nanoTime();
		long tick = server.getTickCount();
		System.out.println("[smoketest] disaster test " + (index + 1) + " of " + steps.size());
		Runnable next = () -> {
			double seconds = (System.nanoTime() - began) / 1.0E9;
			test.line("    (%.1f s for %d ticks)", seconds, server.getTickCount() - tick);
			chain(steps, index + 1, done);
		};
		try {
			steps.get(index).run(next);
		} catch (Throwable t) {
			report(t);
			next.run();
		}
	}

	private void report(Throwable t) {
		test.fail("the disaster test threw %s", t);
		for (StackTraceElement element : t.getStackTrace()) {
			test.line("    at %s", element);
		}
	}

	/** Checks something a while from now; if the check itself throws, that fails the test and it goes on. */
	private void after(int ticks, Runnable check, Runnable next) {
		ServerScheduler.schedule(ticks, () -> {
			try {
				check.run();
			} catch (Throwable t) {
				report(t);
			}
			next.run();
		});
	}

	private void clearSkies() {
		server.setWeatherParameters(24000, 0, false, false);
	}

	/** Keeps the land around a place loaded, and loads it now. */
	private void prepare(BlockPos at, int chunks) {
		int cx = at.getX() >> 4;
		int cz = at.getZ() >> 4;
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(),
				String.format("forceload add %d %d %d %d", (cx - chunks) * 16, (cz - chunks) * 16, (cx + chunks) * 16 + 15, (cz + chunks) * 16 + 15));
		for (int dx = -chunks; dx <= chunks; dx++) {
			for (int dz = -chunks; dz <= chunks; dz++) {
				level.getChunk(cx + dx, cz + dz);
			}
		}
	}

	/** Dry, fairly level land somewhere out from a place, loaded around it. */
	private BlockPos site(BlockPos from, int chunks) {
		BlockPos site = LandShowcase.drySite(level, from, 30);
		if (site != null) {
			prepare(site, chunks);
			site = new BlockPos(site.getX(), top(site.getX(), site.getZ()), site.getZ());
		}
		return site;
	}

	/** The top solid block of a column, under any plants. */
	private int top(int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
	}

	private int count(BlockPos from, BlockPos to, TagKey<Block> tag, Block block) {
		int count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			BlockState state = level.getBlockState(pos);
			if (tag != null ? state.is(tag) : state.is(block)) {
				count++;
			}
		}
		return count;
	}

	// --- the tornado -------------------------------------------------------------------------

	private void tornado(Runnable next) {
		BlockPos site = site(new BlockPos(2400, 0, -2400), 3);
		if (site == null) {
			test.fail("no dry land for the tornado test");
			next.run();
			return;
		}
		BlockPos east = site.offset(30, 0, 0);
		BlockPos house = new BlockPos(east.getX(), top(east.getX(), east.getZ()), east.getZ());
		buildHouse(house);
		BlockPos low = house.offset(-5, 0, -5);
		BlockPos high = house.offset(5, 7, 5);
		int planksBefore = count(low, high, BlockTags.PLANKS, null);
		List<Mob> cattle = new ArrayList<>();
		List<Double> startY = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			Mob cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			if (cow != null) {
				int x = house.getX() - 14 + i * 3;
				int z = house.getZ() + 6 - (i % 3) * 6;
				cow.snapTo(x + 0.5, top(x, z) + 1, z + 0.5, 0.0F, 0.0F);
				cow.setPersistenceRequired();
				level.addFreshEntity(cow);
				cattle.add(cow);
				startY.add(cow.getY());
			}
		}
		Tornado tornado = (Tornado) Disasters.start(DisasterType.TORNADO, level, Vec3.atBottomCenterOf(site.above()), 4.0F, false);
		tornado.aim(Vec3.atBottomCenterOf(house));
		// Already down and at full strength: it moves as it forms, and the house is near.
		tornado.skip(240);
		double[] lifted = {0.0};
		int[] debris = {0};
		for (int t = 10; t <= 480; t += 10) {
			ServerScheduler.schedule(t, () -> {
				for (int i = 0; i < cattle.size(); i++) {
					lifted[0] = Math.max(lifted[0], cattle.get(i).getY() - startY.get(i));
				}
				debris[0] = Math.max(debris[0], level.getEntitiesOfClass(FallingBlockEntity.class, new AABB(site).inflate(150)).size());
			});
		}
		after(500, () -> {
			int planksAfter = count(low, high, BlockTags.PLANKS, null);
			test.line("tornado (EF4) from %s toward a house at %s: planks %d -> %d, cattle lifted up to %.1f blocks, %d pieces of debris in the air at once, "
					+ "now at %s", site.toShortString(), house.toShortString(), planksBefore, planksAfter, lifted[0], debris[0],
					BlockPos.containing(tornado.position()).toShortString());
			if (planksAfter > planksBefore * 0.8) {
				test.fail("an EF4 tornado left the wooden house in its path standing (%d of %d planks)", planksAfter, planksBefore);
			}
			if (lifted[0] < 2.0) {
				test.fail("the EF4 tornado lifted none of the cattle off the ground");
			}
			if (debris[0] == 0) {
				test.fail("the tornado threw no debris about");
			}
			tornado.end();
			cattle.forEach(Mob::discard);
			// The tornado brought its thunderstorm; clear it so the fire to come will burn.
			clearSkies();
		}, next);
	}

	/** A small wooden house with a window: walls and roof of oak planks. */
	private void buildHouse(BlockPos at) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int y = top(at.getX() + dx, at.getZ() + dz) + 1; y <= at.getY(); y++) {
					level.setBlock(pos.set(at.getX() + dx, y, at.getZ() + dz), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				for (int dy = 1; dy <= 5; dy++) {
					boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
					BlockState state = dy == 5 || wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
					if (wall && dy == 2 && dx == 0) {
						state = Blocks.GLASS.defaultBlockState();
					}
					level.setBlock(pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz), state, Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	// --- the earthquake ----------------------------------------------------------------------

	private void quake(Runnable next) {
		BlockPos site = site(new BlockPos(-2400, 0, 2400), 3);
		if (site == null) {
			test.fail("no dry land for the earthquake test");
			next.run();
			return;
		}
		// A wall of stone bricks with a window in it.
		BlockPos wall = site.offset(8, 1, 0);
		for (int i = -4; i <= 4; i++) {
			for (int dy = 0; dy < 4; dy++) {
				level.setBlock(wall.offset(0, dy, i), dy == 2 && i == 0 ? Blocks.GLASS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState(),
						Block.UPDATE_CLIENTS);
			}
		}
		// Sand on the ground, which in a moment is left hanging over a hole, as sand over a cave sometimes is.
		List<BlockPos> sand = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int x = site.getX() - 7 + dx;
				int z = site.getZ() + 7 + dz;
				BlockPos at = new BlockPos(x, top(x, z) + 1, z);
				level.setBlock(at, Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
				sand.add(at);
			}
		}
		ServerScheduler.schedule(5, () -> {
			try {
				// Dug out without telling the sand, which therefore stays put until something shakes it.
				for (BlockPos at : sand) {
					for (int dy = 1; dy <= 3; dy++) {
						level.setBlock(at.below(dy), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					}
				}
				int hanging = (int) sand.stream().filter(at -> level.getBlockState(at).is(Blocks.SAND)).count();
				int[][] before = heights(site, 24);
				Disaster quake = Disasters.start(DisasterType.EARTHQUAKE, level, Vec3.atBottomCenterOf(site.above()), 7.5F, false);
				after(400, () -> {
					int cracked = count(wall.offset(-6, -3, -8), wall.offset(6, 6, 8), null, Blocks.CRACKED_STONE_BRICKS);
					int fell = hanging - (int) sand.stream().filter(at -> level.getBlockState(at).is(Blocks.SAND)).count();
					int[][] after = heights(site, 24);
					int sunk = 0;
					for (int i = 0; i < before.length; i++) {
						for (int j = 0; j < before[i].length; j++) {
							if (after[i][j] <= before[i][j] - 2) {
								sunk++;
							}
						}
					}
					test.line("earthquake (M7.5) at %s: %d stone bricks cracked, %d of %d blocks of sand left hanging came down, "
							+ "%d columns of ground opened up along the fault", site.toShortString(), cracked, fell, hanging, sunk);
					if (cracked == 0) {
						test.fail("an M7.5 earthquake cracked none of the stone bricks near its epicentre");
					}
					if (hanging == 0) {
						test.fail("the sand meant to hang over a hole fell before the earthquake");
					} else if (fell == 0) {
						test.fail("the earthquake left all the sand hanging over a hole");
					}
					if (sunk < 5) {
						test.fail("an M7.5 earthquake opened no fissure along its fault (%d columns)", sunk);
					}
					quake.end();
				}, next);
			} catch (Throwable t) {
				report(t);
				next.run();
			}
		});
	}

	private int[][] heights(BlockPos center, int reach) {
		int[][] out = new int[reach * 2 + 1][reach * 2 + 1];
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				out[dx + reach][dz + reach] = top(center.getX() + dx, center.getZ() + dz);
			}
		}
		return out;
	}

	// --- the meteorite -----------------------------------------------------------------------

	private void meteor(Runnable next) {
		BlockPos site = site(new BlockPos(2400, 0, 2400), 3);
		if (site == null) {
			test.fail("no dry land for the meteor test");
			next.run();
			return;
		}
		MeteorShower shower = (MeteorShower) Disasters.start(DisasterType.METEOR_SHOWER, level, Vec3.atBottomCenterOf(site.above()), 5.0F, false);
		int groundBefore = site.getY();
		int rimBefore = top(site.getX() + 10, site.getZ());
		shower.impact(site, 4.0);
		after(100, () -> {
			int groundAfter = top(site.getX(), site.getZ());
			int iron = 0;
			for (BlockPos pos : BlockPos.betweenClosed(site.offset(-4, -12, -4), site.offset(4, 2, 4))) {
				BlockState state = level.getBlockState(pos);
				if (state.is(ModBlocks.METEORITE) || state.is(Blocks.RAW_IRON_BLOCK)) {
					iron++;
				}
			}
			int rim = top(site.getX() + 10, site.getZ());
			test.line("meteorite (size 4) at %s: the ground at the centre %d -> %d, %d blocks of meteoric iron in the crater floor, the rim %d -> %d",
					site.toShortString(), groundBefore, groundAfter, iron, rimBefore, rim);
			if (groundAfter > groundBefore - 2) {
				test.fail("the meteorite left no crater (ground %d -> %d)", groundBefore, groundAfter);
			}
			if (iron == 0) {
				test.fail("no meteoric iron in the crater's floor");
			}
			shower.end();
		}, next);
	}

	// --- the volcano -------------------------------------------------------------------------

	private void eruption(Runnable next) {
		BlockPos site = site(new BlockPos(-2400, 0, -2400), 3);
		if (site == null) {
			test.fail("no land for the eruption test");
			next.run();
			return;
		}
		Eruption eruption = (Eruption) Disasters.start(DisasterType.ERUPTION, level, Vec3.atBottomCenterOf(site.above()), 5.0F, false);
		// Straight to the eruption itself; a while into it, on to where the column begins to collapse.
		eruption.skip(eruption.unrestTicks() - eruption.age());
		ServerScheduler.schedule(300, () -> eruption.skip((int) (eruption.eruptionTicks() * 0.56) - (eruption.age() - eruption.unrestTicks())));
		after(700, () -> {
			BlockPos vent = eruption.vent();
			int ash = 0;
			int lava = 0;
			for (int dx = -64; dx <= 64; dx += 2) {
				for (int dz = -64; dz <= 64; dz += 2) {
					int x = vent.getX() + dx;
					int z = vent.getZ() + dz;
					if (!level.hasChunkAt(x, z)) {
						continue;
					}
					int top = top(x, z);
					for (int dy = -3; dy <= 1; dy++) {
						BlockState state = level.getBlockState(new BlockPos(x, top + dy, z));
						if (state.is(ModBlocks.ASH_LAYER)) {
							ash++;
						} else if (state.is(Blocks.LAVA) || state.is(Blocks.BASALT) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.BLACKSTONE)) {
							lava++;
						}
					}
				}
			}
			test.line("eruption (VEI 5) at %s: %d blocks of lava poured, %d sampled columns under ash, %d with lava, basalt, blackstone or magma, "
					+ "%d pyroclastic flows", vent.toShortString(), eruption.lavaPlaced(), ash, lava, eruption.flowsLaunched());
			if (eruption.lavaPlaced() == 0) {
				test.fail("the volcano put out no lava");
			}
			if (ash < 5) {
				test.fail("hardly any ash fell from the eruption (%d)", ash);
			}
			if (eruption.flowsLaunched() == 0) {
				test.fail("a VEI 5 eruption sent no pyroclastic flows down its flanks");
			}
			eruption.end();
		}, next);
	}

	// --- the wildfire ------------------------------------------------------------------------

	private void wildfire(Runnable next) {
		Pair<BlockPos, Holder<Biome>> forest = level.findClosestBiome3d(holder -> holder.is(Biomes.FOREST) || holder.is(Biomes.BIRCH_FOREST),
				new BlockPos(0, 80, 0), 6400, 64, 64);
		if (forest == null) {
			test.fail("no forest to set alight");
			next.run();
			return;
		}
		BlockPos at = forest.getFirst();
		prepare(at, 3);
		clearSkies();
		// Rain takes a while to clear off, and would put the fire out.
		ServerScheduler.schedule(level.isRaining() ? 100 : 1, () -> {
			try {
				BlockPos ground = new BlockPos(at.getX(), top(at.getX(), at.getZ()) + 1, at.getZ());
				Wildfire fire = (Wildfire) Disasters.start(DisasterType.WILDFIRE, level, Vec3.atBottomCenterOf(ground), 4.0F, false);
				after(600, () -> {
					int charred = count(ground.offset(-40, -24, -40), ground.offset(40, 24, 40), null, ModBlocks.CHARRED_LOG);
					test.line("wildfire in a forest at %s: %d columns burnt, %d charred logs, %s", ground.toShortString(), fire.burntColumns(), charred,
							fire.isDone() ? "burnt out" : "still burning");
					if (fire.burntColumns() < 15) {
						test.fail("the wildfire barely spread (%d columns)", fire.burntColumns());
					}
					fire.end();
				}, next);
			} catch (Throwable t) {
				report(t);
				next.run();
			}
		});
	}

	// --- the storms --------------------------------------------------------------------------

	private void sandstorm(Runnable next) {
		BlockPos site = site(new BlockPos(1200, 0, 1200), 2);
		Vec3 at = site == null ? new Vec3(1200, 80, 1200) : Vec3.atBottomCenterOf(site.above());
		Disaster storm = Disasters.start(DisasterType.SANDSTORM, level, at, 4.0F, false);
		float front = storm.extra()[0];
		after(200, () -> {
			float now = storm.isDone() ? front : storm.extra()[0];
			test.line("sandstorm: its wall moved %.0f blocks on in 10 seconds (%s)", now - front, storm.describe());
			if (storm.isDone()) {
				test.fail("the sandstorm ended early");
			} else if (now - front < 20.0F) {
				test.fail("the sandstorm's wall hardly moved (%.1f blocks)", now - front);
			}
			storm.end();
		}, next);
	}

	private void blizzard(Runnable next) {
		Pair<BlockPos, Holder<Biome>> cold = level.findClosestBiome3d(holder -> holder.is(Biomes.SNOWY_PLAINS) || holder.is(Biomes.SNOWY_TAIGA),
				new BlockPos(0, 80, 0), 6400, 64, 64);
		if (cold == null) {
			test.line("no snowy land near spawn to test the blizzard in");
			next.run();
			return;
		}
		BlockPos at = cold.getFirst();
		prepare(at, 3);
		List<BlockPos> columns = new ArrayList<>();
		List<Integer> depths = new ArrayList<>();
		for (int dx = -40; dx <= 40; dx += 4) {
			for (int dz = -40; dz <= 40; dz += 4) {
				BlockPos column = at.offset(dx, 0, dz);
				columns.add(column);
				depths.add(Blizzard.snowDepth(level, column));
			}
		}
		BlockPos ground = new BlockPos(at.getX(), top(at.getX(), at.getZ()) + 1, at.getZ());
		Disaster storm = Disasters.start(DisasterType.BLIZZARD, level, Vec3.atBottomCenterOf(ground), 5.0F, false);
		after(500, () -> {
			int deeper = 0;
			for (int i = 0; i < columns.size(); i++) {
				if (Blizzard.snowDepth(level, columns.get(i)) > depths.get(i)) {
					deeper++;
				}
			}
			test.line("blizzard at %s: the snow deeper on %d of %d columns after 25 seconds", ground.toShortString(), deeper, columns.size());
			if (deeper < 3) {
				test.fail("the blizzard hardly added any snow (%d columns)", deeper);
			}
			storm.end();
			clearSkies();
		}, next);
	}
}
