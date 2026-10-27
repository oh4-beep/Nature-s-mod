package com.naturesmod.smoketest;

import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.util.Pair;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.disaster.AcidRain;
import com.naturesmod.disaster.Avalanche;
import com.naturesmod.disaster.Blizzard;
import com.naturesmod.disaster.Disaster;
import com.naturesmod.disaster.DisasterType;
import com.naturesmod.disaster.Disasters;
import com.naturesmod.disaster.Earthquake;
import com.naturesmod.disaster.Eruption;
import com.naturesmod.disaster.Hurricane;
import com.naturesmod.disaster.LightningStorm;
import com.naturesmod.disaster.MeteorShower;
import com.naturesmod.disaster.Sinkhole;
import com.naturesmod.disaster.Tornado;
import com.naturesmod.disaster.Tsunami;
import com.naturesmod.disaster.Wildfire;
import com.naturesmod.tree.ServerScheduler;
import com.naturesmod.worldgen.deep.LandShowcase;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
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
 * flows down, and rumbles through its unrest first without bringing the game down; a wildfire burns through a
 * forest; a sandstorm's wall rolls on; a blizzard piles up snow even where it is warm; a hurricane's winds
 * peak in its eyewall; a tsunami floods the beach and drains away; a sinkhole swallows the ground; an
 * avalanche runs down and comes to rest; a lightning storm's ball lightning zaps; acid rain eats the land.
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
		List<Step> steps = List.of(this::tornado, this::quake, this::meteor, this::eruption, this::unrest, this::wildfire, this::sandstorm,
				this::blizzard, this::hurricane, this::tsunami, this::sinkhole, this::avalanche, this::lightning, this::acidRain);
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

	private BlockPos firstSite(int chunks, BlockPos... from) {
		for (BlockPos each : from) {
			BlockPos site = site(each, chunks);
			if (site != null) {
				return site;
			}
		}
		return null;
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
			test.line("    the tornado carried things up to %.1f blocks above its foot", tornado.highestCarry());
			if (lifted[0] < 2.0) {
				test.fail("the EF4 tornado lifted none of the cattle off the ground");
			}
			if (tornado.highestCarry() < 12.0) {
				test.fail("the EF4 tornado carried nothing high (%.1f blocks)", tornado.highestCarry());
			}
			if (tornado.debrisAloft() > 12 + 7 * 4) {
				test.fail("the tornado has more debris in the air than its cap (%d)", tornado.debrisAloft());
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
					Earthquake earthquake = (Earthquake) quake;
					test.line("    its fissure %d blocks deep, %d blocks broken, %d pits, %d cracks, %d columns sunk, %d still to dig",
							earthquake.fissureDepth(), earthquake.blocksBroken(), earthquake.pitsOpened(), earthquake.cracksOpened(),
							earthquake.columnsSunk(), earthquake.digging());
					if (earthquake.fissureDepth() < 12) {
						test.fail("an M7.5 earthquake's fissure is shallow (%d blocks)", earthquake.fissureDepth());
					}
					if (earthquake.blocksBroken() < 500) {
						test.fail("an M7.5 earthquake broke little ground (%d blocks)", earthquake.blocksBroken());
					}
					if (earthquake.fallingBlocks() > 48) {
						test.fail("the earthquake has more falling blocks than its cap (%d)", earthquake.fallingBlocks());
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
			test.line("eruption (VEI 5) at %s: %d blocks of lava poured in %d flows and the crater, %d sampled columns under ash, %d with lava, basalt, "
					+ "blackstone or magma, %d pyroclastic flows", vent.toShortString(), eruption.lavaPlaced(), eruption.lavaFlowsStarted(), ash, lava,
					eruption.flowsLaunched());
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
		BlockPos site = firstSite(2, new BlockPos(1200, 0, 1200), new BlockPos(-1800, 0, 1200), new BlockPos(2400, 0, -600));
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

	/** In warm, green country: a blizzard brings its own cold. */
	private void blizzard(Runnable next) {
		BlockPos site = site(new BlockPos(0, 0, 1800), 3);
		if (site == null) {
			test.fail("no land for the blizzard test");
			next.run();
			return;
		}
		List<BlockPos> columns = new ArrayList<>();
		List<Integer> depths = new ArrayList<>();
		for (int dx = -40; dx <= 40; dx += 4) {
			for (int dz = -40; dz <= 40; dz += 4) {
				BlockPos column = site.offset(dx, 0, dz);
				columns.add(column);
				depths.add(Blizzard.snowDepth(level, column));
			}
		}
		Holder<Biome> biome = level.getBiome(site);
		Blizzard storm = (Blizzard) Disasters.start(DisasterType.BLIZZARD, level, Vec3.atBottomCenterOf(site.above()), 5.0F, false);
		after(500, () -> {
			int deeper = 0;
			for (int i = 0; i < columns.size(); i++) {
				if (Blizzard.snowDepth(level, columns.get(i)) > depths.get(i)) {
					deeper++;
				}
			}
			test.line("blizzard at %s (in %s): the snow deeper on %d of %d columns after 25 seconds, %d layers laid, %d water frozen, visibility %.0f",
					site.toShortString(), biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("?"), deeper, columns.size(), storm.snowLaid(),
					storm.iceFormed(), storm.visibility());
			if (storm.snowLaid() == 0 || deeper < 3) {
				test.fail("the blizzard hardly added any snow (%d columns, %d layers)", deeper, storm.snowLaid());
			}
			storm.end();
			clearSkies();
		}, next);
	}

	// --- the volcano's unrest ----------------------------------------------------------------

	/** A volcano rumbling before it blows, as it does on its own: the tremors it sets off must not bring the game down. */
	private void unrest(Runnable next) {
		BlockPos site = site(new BlockPos(-1800, 0, -600), 2);
		if (site == null) {
			test.fail("no land for the volcanic unrest test");
			next.run();
			return;
		}
		int failures = Disasters.failures().size();
		Eruption eruption = (Eruption) Disasters.start(DisasterType.ERUPTION, level, Vec3.atBottomCenterOf(site.above()), 4.0F, false);
		int[] tremors = {0};
		for (int t = 20; t <= 480; t += 20) {
			ServerScheduler.schedule(t, () -> tremors[0] = Math.max(tremors[0],
					(int) Disasters.active(level).stream().filter(d -> d.type == DisasterType.EARTHQUAKE).count()));
		}
		after(500, () -> {
			test.line("volcanic unrest at %s: %d tremors under way at most, the eruption %s after 500 ticks", eruption.vent().toShortString(), tremors[0],
					eruption.isDone() ? "over" : "still building");
			if (eruption.isDone()) {
				test.fail("the volcano's unrest ended the eruption before it blew");
			}
			if (tremors[0] == 0) {
				test.fail("the volcano's unrest set off no tremors");
			}
			if (Disasters.failures().size() > failures) {
				test.fail("the volcano's unrest failed: %s", Disasters.failures().subList(failures, Disasters.failures().size()));
			}
			Disasters.stop(level, d -> d == eruption || d.type == DisasterType.EARTHQUAKE);
		}, next);
	}

	// --- the new storms ----------------------------------------------------------------------

	private void hurricane(Runnable next) {
		BlockPos site = site(new BlockPos(1800, 0, -1200), 2);
		Vec3 at = site == null ? new Vec3(1800, 80, -1200) : Vec3.atBottomCenterOf(site.above());
		Hurricane storm = (Hurricane) Disasters.start(DisasterType.HURRICANE, level, at, 4.0F, false);
		storm.skip(400);
		after(100, () -> {
			Vec3 centre = storm.position();
			double calm = storm.windAt(centre).length();
			double wall = storm.windAt(centre.add(storm.eyewall(), 0.0, 0.0)).length();
			test.line("hurricane (category %d): eye %.0f blocks, eyewall at %.0f, peak wind %.0f m/s, %.0f m/s in the eye and %.0f in the eyewall",
					storm.category(), storm.eye(), storm.eyewall(), storm.peakWind(), calm, wall);
			if (storm.isDone()) {
				test.fail("the hurricane ended early");
			} else if (wall <= calm || wall < 20.0) {
				test.fail("the hurricane's eyewall winds (%.0f m/s) are no stronger than its eye (%.0f m/s)", wall, calm);
			}
			storm.end();
			clearSkies();
		}, next);
	}

	private void tsunami(Runnable next) {
		Pair<BlockPos, Holder<Biome>> beach = level.findClosestBiome3d(holder -> holder.is(BiomeTags.IS_BEACH), new BlockPos(0, 64, 0), 6400, 64, 64);
		if (beach == null) {
			test.line("no beach near spawn to test the tsunami on");
			next.run();
			return;
		}
		BlockPos shore = beach.getFirst();
		prepare(shore, 4);
		Vec3 at = new Vec3(shore.getX() + 0.5, top(shore.getX(), shore.getZ()) + 1, shore.getZ() + 0.5);
		Tsunami wave = (Tsunami) Disasters.start(DisasterType.TSUNAMI, level, at, 4.0F, false);
		wave.watch(at);
		// On until the front is a little way out from the beach.
		int ticks = 0;
		while (!wave.isDone() && wave.front() < wave.along(at.x, at.z) - 40.0 && ticks < 4000) {
			wave.skip(20);
			ticks += 20;
		}
		after(160, () -> {
			test.line("tsunami (level 4%s) at %s: %.1f blocks high at its front, %d water placed, %d removed, %d standing, %d blocks broken, "
					+ "%d swept up", wave.inland() ? ", a flood wave inland" : "", BlockPos.containing(at).toShortString(), wave.height(), wave.waterPlaced(),
					wave.waterRemoved(), wave.waterStanding(), wave.blocksBroken(), wave.sweptUp());
			if (wave.waterPlaced() == 0) {
				test.fail("the tsunami never flooded the land it was watched over");
			}
			if (wave.waterStanding() > 900) {
				test.fail("the tsunami left more water standing than its cap (%d)", wave.waterStanding());
			}
			wave.end();
		}, () -> after(10, () -> {
			if (wave.waterStanding() > 0) {
				test.fail("the tsunami left %d blocks of water behind after it ended", wave.waterStanding());
			}
		}, next));
	}

	private void sinkhole(Runnable next) {
		BlockPos site = site(new BlockPos(600, 0, -1800), 2);
		if (site == null) {
			test.fail("no dry land for the sinkhole test");
			next.run();
			return;
		}
		Sinkhole hole = (Sinkhole) Disasters.start(DisasterType.SINKHOLE, level, Vec3.atBottomCenterOf(site.above()), 3.0F, false);
		if (hole.overWater()) {
			test.line("the sinkhole test site at %s was water; skipped", site.toShortString());
			hole.end();
			next.run();
			return;
		}
		hole.skip(hole.warningTicks() - 30 - hole.age());
		int[] mostFalling = {0};
		for (int t = 10; t <= 600; t += 10) {
			ServerScheduler.schedule(t, () -> mostFalling[0] = Math.max(mostFalling[0], hole.fallingBlocks()));
		}
		after(600, () -> {
			BlockPos centre = hole.centre();
			int bottom = top(centre.getX(), centre.getZ());
			test.line("sinkhole (level 3) at %s: %s, %d cracks, %d columns dug, %d blocks, rim %d, floor %d, ground in the middle now %d, "
					+ "%d falling at most", centre.toShortString(), hole.phase() == Sinkhole.SETTLED ? "settled" : "phase " + hole.phase(), hole.cracksOpened(),
					hole.columnsDug(), hole.blocksDug(), hole.rimHeight(), hole.floorHeight(), bottom, mostFalling[0]);
			if (hole.columnsDug() < 40 || hole.blocksDug() < 500) {
				test.fail("the sinkhole hardly opened (%d columns, %d blocks)", hole.columnsDug(), hole.blocksDug());
			}
			if (bottom > hole.rimHeight() - 5) {
				test.fail("the sinkhole left no pit in its middle (ground %d, rim %d)", bottom, hole.rimHeight());
			}
			if (mostFalling[0] > 40) {
				test.fail("the sinkhole had more falling blocks than its cap (%d)", mostFalling[0]);
			}
			hole.end();
		}, next);
	}

	private void avalanche(Runnable next) {
		Pair<BlockPos, Holder<Biome>> slope = level.findClosestBiome3d(holder -> holder.is(Biomes.SNOWY_SLOPES) || holder.is(Biomes.GROVE)
				|| holder.is(Biomes.FROZEN_PEAKS), new BlockPos(0, 120, 0), 6400, 64, 64);
		BlockPos from = slope != null ? slope.getFirst() : new BlockPos(-600, 0, 1800);
		prepare(from, 4);
		Vec3 at = new Vec3(from.getX() + 0.5, top(from.getX(), from.getZ()) + 1, from.getZ() + 0.5);
		Avalanche slide = (Avalanche) Disasters.start(DisasterType.AVALANCHE, level, at, 4.0F, false);
		double[] fastest = {0.0};
		for (int t = 10; t <= 500; t += 10) {
			ServerScheduler.schedule(t, () -> fastest[0] = Math.max(fastest[0], slide.speed()));
		}
		after(500, () -> {
			test.line("avalanche (level 4, %s) let go at %s: ran %.0f of %.0f blocks, %.0f wide, at up to %.2f blocks a tick, %d blocks laid, %d trees felled, "
					+ "%s", slide.landslide() ? "a landslide" : "snow", slide.release().toShortString(), slide.travelled(), slide.runLength(), slide.width(),
					fastest[0], slide.blocksLaid(), slide.treesFelled(), slide.phase() == Avalanche.SETTLING || slide.isDone() ? "come to rest" : "still running");
			if (slide.travelled() < 20.0) {
				test.fail("the avalanche hardly ran (%.0f blocks)", slide.travelled());
			}
			if (slide.blocksLaid() == 0) {
				test.fail("the avalanche laid no debris");
			}
			if (fastest[0] > slide.maxSpeed() + 1.0E-6) {
				test.fail("the avalanche ran faster than it may (%.2f > %.2f)", fastest[0], slide.maxSpeed());
			}
			slide.end();
		}, next);
	}

	private void lightning(Runnable next) {
		BlockPos site = site(new BlockPos(-1200, 0, -1800), 2);
		if (site == null) {
			test.fail("no land for the lightning storm test");
			next.run();
			return;
		}
		LightningStorm storm = (LightningStorm) Disasters.start(DisasterType.LIGHTNING_STORM, level, Vec3.atBottomCenterOf(site.above()), 5.0F, false);
		storm.skip(300);
		Mob cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
		if (cow != null) {
			cow.snapTo(site.getX() + 0.5, site.getY() + 1, site.getZ() + 0.5, 0.0F, 0.0F);
			cow.setPersistenceRequired();
			level.addFreshEntity(cow);
			storm.formOrb(new Vec3(site.getX() + 0.5, site.getY() + 1.8, site.getZ() + 0.5));
		}
		storm.chargeAt(Vec3.atBottomCenterOf(site.offset(12, 1, 12)), 10);
		after(80, () -> {
			test.line("lightning storm (level 5) at %s: %d strikes, %d on a charged spot, %d orbs formed, %d zaps", site.toShortString(), storm.strikes(),
					storm.chargedStrikes(), storm.orbsFormed(), storm.zaps());
			if (storm.chargedStrikes() == 0) {
				test.fail("the lightning storm never struck the spot it charged");
			}
			if (cow != null && storm.zaps() == 0) {
				test.fail("the ball lightning zapped nothing");
			}
			storm.end();
			if (cow != null) {
				cow.discard();
			}
			clearSkies();
		}, next);
	}

	private void acidRain(Runnable next) {
		// Not every place has dry, level land near it (the sea is wide east of spawn): the first of a few that has.
		BlockPos site = firstSite(2, new BlockPos(1800, 0, 600), new BlockPos(-600, 0, 1200), new BlockPos(-1800, 0, 1200),
				new BlockPos(2400, 0, -600), new BlockPos(-600, 0, -1200), new BlockPos(600, 0, -1200));
		if (site == null) {
			test.fail("no land for the acid rain test");
			next.run();
			return;
		}
		// A poppy on grass, and a leaf block on grass beside it.
		BlockPos flower = site.offset(3, 0, 0);
		BlockPos leaves = site.offset(-3, 0, 0);
		level.setBlock(new BlockPos(flower.getX(), top(flower.getX(), flower.getZ()), flower.getZ()), Blocks.GRASS_BLOCK.defaultBlockState(),
				Block.UPDATE_CLIENTS);
		level.setBlock(new BlockPos(flower.getX(), top(flower.getX(), flower.getZ()) + 1, flower.getZ()), Blocks.POPPY.defaultBlockState(),
				Block.UPDATE_CLIENTS);
		level.setBlock(new BlockPos(leaves.getX(), top(leaves.getX(), leaves.getZ()) + 1, leaves.getZ()),
				Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), Block.UPDATE_CLIENTS);
		AcidRain rain = (AcidRain) Disasters.start(DisasterType.ACID_RAIN, level, Vec3.atBottomCenterOf(site.above()), 5.0F, false);
		boolean ateFlower = rain.eatAway(flower) && rain.eatAway(flower);
		boolean ateLeaves = rain.eatAway(leaves);
		after(200, () -> {
			BlockState ground = level.getBlockState(new BlockPos(flower.getX(), top(flower.getX(), flower.getZ()), flower.getZ()));
			test.line("acid rain (level 5) at %s: the poppy %s, the ground under it now %s, the leaves %s; radius %.0f", site.toShortString(),
					ateFlower ? "eaten" : "unharmed", BuiltInRegistries.BLOCK.getKey(ground.getBlock()).getPath(), ateLeaves ? "withered" : "unharmed", rain.radius());
			if (!ateFlower || !ateLeaves) {
				test.fail("acid rain didn't eat away the poppy and grass (%s) or the leaves (%s)", ateFlower, ateLeaves);
			}
			rain.end();
			clearSkies();
		}, next);
	}

}
