package com.naturesmod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.datafixers.util.Pair;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import com.naturesmod.NaturesMod;
import com.naturesmod.abyss.Abyss;
import com.naturesmod.creature.ModCreatures;
import com.naturesmod.creature.SeaCreature;
import com.naturesmod.worldgen.deep.DeepKind;
import com.naturesmod.worldgen.deep.DeepPiece;
import com.naturesmod.worldgen.deep.DeepShowcase;

/**
 * Plays through Nature's Mod's sea and Nether in a real client and screenshots what it sees:
 * every sea creature, both diving suits, an Abyssal Rift, the Abyss and the new Nether. Run with
 * {@code ./gradlew runClientGameTest}; screenshots land in build/run/clientGameTest/screenshots.
 */
public class NaturesModClientGameTest implements FabricClientGameTest {
	private static final List<List<String>> LINEUPS = List.of(
			List.of("clownfish", "blue_tang", "butterflyfish", "queen_angelfish", "parrotfish", "lionfish", "seahorse"),
			List.of("rainbow_trout", "brown_trout", "perch", "carp", "piranha", "arctic_char", "icefish", "herring", "mackerel"),
			List.of("catfish", "flounder", "northern_pike", "barracuda", "moray_eel"),
			List.of("swordfish", "bluefin_tuna", "sturgeon", "coelacanth", "giant_grouper"),
			List.of("reef_shark", "hammerhead_shark", "great_white_shark"),
			List.of("greenland_shark", "goblin_shark", "frilled_shark", "ghost_shark"),
			List.of("moon_jelly", "lions_mane_jelly", "box_jellyfish", "comb_jelly"),
			List.of("vampire_squid", "dumbo_octopus", "giant_squid"),
			List.of("lanternfish", "hatchetfish", "anglerfish", "viperfish", "fangtooth", "black_dragonfish", "barreleye", "tripod_fish"),
			List.of("gulper_eel", "manta_ray", "ocean_sunfish"),
			List.of("rotfin", "hollow_eye", "bone_shark", "twinhead_eel", "pale_lurker", "abyssal_maw"),
			List.of("lantern_jelly", "glass_squid", "siphonophore", "ghost_eel", "starry_ray"),
			List.of("ghost_whale"),
			List.of("abyssal_leviathan"),
			List.of("abyssal_horror"),
			List.of("tidewyrm", "razorjaw", "gloomlantern"),
			List.of("stormcoil", "kraken", "siren_queen"),
			List.of("bonewarden", "abyssal_manta", "hollow_king"),
			List.of("dunkleosteus", "twin_terror"),
			List.of("megalodon"));

	/** GLFW's key code for F1 (LWJGL is not on the test classpath). */
	private static final int KEY_F1 = 290;

	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(creator -> {
					creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
					creator.setSeed("8675309");
					creator.getGameRules().set(GameRules.ADVANCE_TIME, false, null);
					creator.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
					creator.getGameRules().set(GameRules.SPAWN_MOBS, false, null);
				})
				.create()) {
			world.getConnection().waitForChunksRender();
			// F1: hide the hotbar and crosshair so the screenshots show only the world.
			context.getInput().pressKey(KEY_F1);
			command(world, "time set noon");
			command(world, "weather clear");
			scene("spawn", () -> context.takeScreenshot("nm_overworld_spawn"));
			scene("armor", () -> armor(context, world));
			scene("creatures", () -> creatures(context, world));
			scene("rift", () -> rift(context, world));
			scene("abyss", () -> abyss(context, world));
			scene("nether", () -> nether(context, world));
			scene("end", () -> end(context, world));
			// Last: building and visiting all 55 treasures loads the server more than anything else.
			scene("treasures", () -> treasures(context, world));
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("Some scenes failed: " + failures);
		}
	}

	private void scene(String name, Runnable body) {
		System.out.println("[gametest] scene " + name);
		try {
			body.run();
		} catch (Throwable t) {
			failures.add(name + ": " + t);
			System.out.println("[gametest] scene " + name + " failed: " + t);
			t.printStackTrace(System.out);
		}
	}

	// --- moving about --------------------------------------------------------------------

	private static void teleport(ClientGameTestContext context, TestSingleplayerContext world, ResourceKey<Level> dimension,
			double x, double y, double z, float yaw, float pitch) {
		command(world, String.format(java.util.Locale.ROOT, "execute in %s run tp @a %.2f %.2f %.2f %.1f %.1f",
				dimension.identifier(), x, y, z, yaw, pitch));
		context.waitTicks(5);
		world.getConnection().waitForChunksRender();
		context.waitTicks(10);
	}

	/** Runs a command quietly, so its feedback does not end up in the chat on the screenshots. */
	private static void command(TestSingleplayerContext world, String command) {
		world.getServer().runOnServer(server -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
				command));
	}

	private static void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot("nm_" + name);
	}

	// --- the diving suits ----------------------------------------------------------------

	private void armor(ClientGameTestContext context, TestSingleplayerContext world) {
		command(world, "gamemode creative @a");
		BlockPos ground = world.getServer().computeOnServer(server -> {
			ServerPlayer player = world.getConnection().getServerPlayer();
			ServerLevel level = server.overworld();
			int x = player.getBlockX();
			int z = player.getBlockZ();
			return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		});
		// Stand on a pillar in the sky so nothing gets between the camera and the diver.
		command(world, String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d minecraft:dark_prismarine",
				ground.getX(), ground.getY() + 40, ground.getZ(), ground.getX(), ground.getY() + 44, ground.getZ()));
		teleport(context, world, Level.OVERWORLD, ground.getX() + 0.5, ground.getY() + 45, ground.getZ() + 0.5, 180.0F, 0.0F);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		for (String set : new String[] {"tidewalker", "abyssal"}) {
			for (String[] piece : new String[][] {{"head", "helmet"}, {"chest", "chestplate"}, {"legs", "leggings"}, {"feet", "boots"}}) {
				command(world, "item replace entity @a armor." + piece[0] + " with naturesmod:" + set + "_" + piece[1]);
			}
			context.waitTicks(10);
			shoot(context, "armor_" + set);
		}
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		command(world, "clear @a");
	}

	// --- the creatures -------------------------------------------------------------------

	private void creatures(ClientGameTestContext context, TestSingleplayerContext world) {
		// Open water high up in the Abyss: nothing for the line-ups to hide in.
		command(world, "gamemode spectator @a");
		command(world, "effect give @a minecraft:night_vision infinite 0 true");
		teleport(context, world, Abyss.LEVEL, 3000.5, 960, 3000.5, 0.0F, 0.0F);
		// Let the eyes get used to the water so the fog lifts.
		context.waitTicks(80);
		for (List<String> lineup : LINEUPS) {
			double biggest = lineup.stream().mapToDouble(id -> ModCreatures.species(ModCreatures.type(id)).scale()).max().orElse(1.0);
			double spacing = 1.2 + biggest * 1.6;
			double distance = 3.5 + biggest * 3.2 + lineup.size() * spacing * 0.35;
			List<UUID> ids = world.getServer().computeOnServer(server -> lineup(world, lineup, spacing, distance));
			context.waitTicks(20);
			String onServer = world.getServer().computeOnServer(s -> {
				ServerLevel level = (ServerLevel) world.getConnection().getServerPlayer().level();
				StringBuilder out = new StringBuilder("player " + world.getConnection().getServerPlayer().position());
				for (UUID id : ids) {
					Entity entity = level.getEntity(id);
					out.append(entity == null ? " gone" : " " + EntityType.getKey(entity.getType()).getPath() + "@" + entity.blockPosition()
							+ (entity.isAlive() ? "" : "(dead)"));
				}
				return out.toString();
			});
			String onClient = context.computeOnClient(c -> {
				StringBuilder out = new StringBuilder("player " + c.player.position() + " sees");
				for (Entity entity : c.level.entitiesForRendering()) {
					if (entity instanceof SeaCreature) {
						out.append(" ").append(EntityType.getKey(entity.getType()).getPath()).append("@").append(entity.blockPosition());
					}
				}
				return out.toString();
			});
			System.out.println("[gametest] lineup " + lineup.getFirst() + " server: " + onServer);
			System.out.println("[gametest] lineup " + lineup.getFirst() + " client: " + onClient);
			shoot(context, "creatures_" + lineup.getFirst());
			world.getServer().runOnServer(server -> {
				ServerLevel level = (ServerLevel) world.getConnection().getServerPlayer().level();
				for (UUID id : ids) {
					Entity entity = level.getEntity(id);
					if (entity != null) {
						entity.discard();
					}
				}
			});
		}
	}

	/** Lines the creatures up side by side in front of the camera, showing their flanks. */
	private static List<UUID> lineup(TestSingleplayerContext world, List<String> species, double spacing, double distance) {
		ServerPlayer player = world.getConnection().getServerPlayer();
		ServerLevel level = (ServerLevel) player.level();
		List<UUID> ids = new ArrayList<>();
		double start = player.getX() - spacing * (species.size() - 1) / 2.0;
		for (int i = 0; i < species.size(); i++) {
			EntityType<? extends Mob> type = ModCreatures.type(species.get(i));
			Mob mob = type.create(level, EntitySpawnReason.COMMAND);
			if (mob == null) {
				continue;
			}
			mob.snapTo(start + i * spacing, player.getY() + 0.6, player.getZ() + distance, 90.0F, 0.0F);
			mob.setYHeadRot(90.0F);
			mob.yBodyRot = 90.0F;
			mob.setNoAi(true);
			mob.setPersistenceRequired();
			level.addFreshEntity(mob);
			ids.add(mob.getUUID());
		}
		return ids;
	}

	// --- the rift ------------------------------------------------------------------------

	private void rift(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos found = world.getServer().computeOnServer(server -> locate(server.overworld(), NaturesMod.id("abyssal_rift")));
		if (found == null) {
			throw new AssertionError("no abyssal rift near spawn");
		}
		// The rift lies at the bottom of a funnel some thirty blocks deep in the middle of its chunk.
		BlockPos rift = found.offset(8, 0, 8);
		int bottom = world.getServer().computeOnServer(server -> floor(server.overworld(), rift.getX(), rift.getZ()));
		int rim = world.getServer().computeOnServer(server -> ringFloor(server.overworld(), rift, 20));
		teleport(context, world, Level.OVERWORLD, rift.getX() + 0.5, rim + 9, rift.getZ() - 19.5, 0.0F, 42.0F);
		shoot(context, "rift_from_above");
		teleport(context, world, Level.OVERWORLD, rift.getX() + 0.5, bottom + 9, rift.getZ() - 1.5, 0.0F, 72.0F);
		shoot(context, "rift_in_the_funnel");
	}

	// --- the Abyss -----------------------------------------------------------------------

	private void abyss(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> Abyss.buildArrival(server.getLevel(Abyss.LEVEL), new BlockPos(0, Abyss.SEA_LEVEL - 1, 0)));
		command(world, "gamemode creative @a");
		teleport(context, world, Abyss.LEVEL, 3.5, Abyss.SEA_LEVEL, 0.5, 90.0F, 25.0F);
		shoot(context, "abyss_arrival");
		command(world, "gamemode spectator @a");
		command(world, "effect give @a minecraft:night_vision infinite 0 true");
		teleport(context, world, Abyss.LEVEL, 3.5, Abyss.SEA_LEVEL - 20, 0.5, 90.0F, 25.0F);
		context.waitTicks(80);
		for (String biome : new String[] {"glowing_reef", "hydrothermal_vents", "bone_fields", "abyssal_plains"}) {
			BlockPos at = world.getServer().computeOnServer(server -> findBiome(server.getLevel(Abyss.LEVEL), biome, 90));
			if (at == null) {
				failures.add("abyss: no " + biome);
				continue;
			}
			int floor = world.getServer().computeOnServer(server -> {
				ServerLevel abyss = server.getLevel(Abyss.LEVEL);
				return Math.max(floor(abyss, at.getX(), at.getZ()) + 8, floor(abyss, at.getX(), at.getZ() - 8) + 4);
			});
			teleport(context, world, Abyss.LEVEL, at.getX() + 0.5, floor, at.getZ() - 7.5, 0.0F, 35.0F);
			shoot(context, "abyss_" + biome);
		}
		for (String structure : new String[] {"sunken_temple", "bathysphere", "leviathan_lair", "vent_shrine", "drowned_sanctum"}) {
			BlockPos at = world.getServer().computeOnServer(server -> locate(server.getLevel(Abyss.LEVEL), NaturesMod.id(structure)));
			if (at == null) {
				failures.add("abyss: no " + structure);
				continue;
			}
			// Structures sit in the middle of their chunk; frame them from up and back, clear of the slope.
			BlockPos center = at.offset(8, 0, 8);
			int back = switch (structure) {
				case "bathysphere" -> 11;
				case "vent_shrine" -> 13;
				case "drowned_sanctum" -> 30;
				default -> 20;
			};
			int height = world.getServer().computeOnServer(server -> {
				ServerLevel abyss = server.getLevel(Abyss.LEVEL);
				return Math.max(ringFloor(abyss, center, back / 2) + back * 3 / 4, floor(abyss, center.getX(), center.getZ() - back) + 4);
			});
			teleport(context, world, Abyss.LEVEL, center.getX() + 0.5, height, center.getZ() - back + 0.5, 0.0F, 36.0F);
			shoot(context, "abyss_" + structure);
		}
	}

	// --- the new End ------------------------------------------------------------------------

	private static final String[] END_BIOMES = {"chorus_forest", "crystal_highlands", "starfall_plains", "void_marsh", "enderglow_wilds",
			"shattered_spires", "sky_isles"};

	private void end(ClientGameTestContext context, TestSingleplayerContext world) {
		command(world, "gamemode spectator @a");
		command(world, "effect give @a minecraft:night_vision infinite 0 true");
		BlockPos forest = null;
		for (String biome : END_BIOMES) {
			BlockPos at = world.getServer().computeOnServer(server -> endLand(server.getLevel(Level.END), biome));
			if (at == null) {
				failures.add("end: no " + biome + " with land");
				continue;
			}
			if (forest == null && biome.equals("chorus_forest")) {
				forest = at;
			}
			// From up and back, looking down over the land.
			teleport(context, world, Level.END, at.getX() + 0.5, at.getY() + 16, at.getZ() - 22.5, 0.0F, 30.0F);
			context.waitTicks(20);
			shoot(context, "end_" + biome);
		}
		// Looking up at the sky isles from the continent below them.
		BlockPos isle = world.getServer().computeOnServer(server -> endLand(server.getLevel(Level.END), "sky_isles"));
		if (isle != null) {
			teleport(context, world, Level.END, isle.getX() + 0.5, isle.getY() - 40, isle.getZ() - 40.5, 0.0F, -28.0F);
			shoot(context, "end_sky_isles_from_below");
		}
		if (forest == null) {
			return;
		}
		// The End's creatures, in the air and on the ground.
		BlockPos stage = forest;
		for (List<String> lineup : List.of(List.of("void_jelly", "voidfin", "star_squid", "void_serpent", "ender_shark"), List.of("skyray"),
				List.of("ender_beetle", "crystal_golem", "void_stalker", "endling"))) {
			double biggest = lineup.stream().mapToDouble(id -> ModCreatures.species(ModCreatures.type(id)).scale()).max().orElse(1.0);
			double spacing = 1.4 + biggest * 1.5;
			double distance = 4.0 + biggest * 3.0 + lineup.size() * spacing * 0.35;
			boolean walkers = ModCreatures.species(ModCreatures.type(lineup.getFirst())).walks();
			int ground = world.getServer().computeOnServer(server -> server.getLevel(Level.END).getHeight(Heightmap.Types.MOTION_BLOCKING,
					stage.getX(), stage.getZ() + (int) distance));
			double eye = walkers ? ground + 1.6 : ground + 6.0;
			teleport(context, world, Level.END, stage.getX() + 0.5, eye, stage.getZ() + 0.5, 0.0F, walkers ? 8.0F : 0.0F);
			List<UUID> ids = world.getServer().computeOnServer(server -> endLineup(world, lineup, spacing, distance, walkers));
			context.waitTicks(20);
			shoot(context, "end_creatures_" + lineup.getFirst());
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.getLevel(Level.END);
				for (UUID id : ids) {
					Entity entity = level.getEntity(id);
					if (entity != null) {
						entity.discard();
					}
				}
			});
		}
		command(world, "effect clear @a minecraft:night_vision");
	}

	/** A spot on solid land in a biome of the new End: on a continent, or up on a sky isle. */
	private static BlockPos endLand(ServerLevel end, String biome) {
		boolean sky = biome.equals("sky_isles");
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, NaturesMod.id(biome));
		for (int i = 0; i <= 40; i++) {
			for (int j = 0; j <= 40; j++) {
				int x = 1200 + i * 96;
				int z = -1920 + j * 96;
				int top = end.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, end,
						end.getChunkSource().randomState());
				if (sky ? top < 150 || top > 230 : top < 45 || top > 130) {
					continue;
				}
				if (end.getUncachedNoiseBiome(x >> 2, (top + 2) >> 2, z >> 2).is(key)) {
					end.getChunk(x >> 4, z >> 4);
					return new BlockPos(x, end.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
				}
			}
		}
		return null;
	}

	/** Lines End creatures up in front of the camera: flyers in the air, walkers on the ground. */
	private static List<UUID> endLineup(TestSingleplayerContext world, List<String> species, double spacing, double distance, boolean walkers) {
		ServerPlayer player = world.getConnection().getServerPlayer();
		ServerLevel level = (ServerLevel) player.level();
		List<UUID> ids = new ArrayList<>();
		double start = player.getX() - spacing * (species.size() - 1) / 2.0;
		for (int i = 0; i < species.size(); i++) {
			Mob mob = ModCreatures.type(species.get(i)).create(level, EntitySpawnReason.COMMAND);
			if (mob == null) {
				continue;
			}
			double x = start + i * spacing;
			double z = player.getZ() + distance;
			double y = walkers ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)) : player.getY() + 0.6;
			// Walkers face the camera; flyers show their flanks.
			float yaw = walkers ? 180.0F : 90.0F;
			mob.snapTo(x, y, z, yaw, 0.0F);
			mob.setYHeadRot(yaw);
			mob.yBodyRot = yaw;
			mob.setNoAi(true);
			mob.setPersistenceRequired();
			level.addFreshEntity(mob);
			ids.add(mob.getUUID());
		}
		return ids;
	}

	// --- the treasures of the deep ------------------------------------------------------

	/** Builds every kind of treasure on the floor of the Abyss and photographs it from the front. */
	private void treasures(ClientGameTestContext context, TestSingleplayerContext world) {
		command(world, "gamemode spectator @a");
		command(world, "effect give @a minecraft:night_vision infinite 0 true");
		BlockPos site = new BlockPos(6000, 0, 6000);
		for (DeepKind kind : DeepKind.values()) {
			int reach = kind.tier().reach();
			BlockPos from = site;
			BlockPos center = world.getServer().computeOnServer(server -> {
				ServerLevel abyss = server.getLevel(Abyss.LEVEL);
				BlockPos at = DeepShowcase.levelSite(abyss, from, reach);
				if (at == null) {
					return null;
				}
				DeepPiece piece = DeepShowcase.build(abyss, kind, at, Rotation.NONE);
				return piece.center();
			});
			if (center == null) {
				failures.add("treasures: no level floor for " + kind.id());
				return;
			}
			// From in front and a little to the left, up high enough to see over the walls; if a seamount
			// stands there, from whichever side is open.
			double distance = reach * 1.45 + 6.0;
			double[] view = world.getServer().computeOnServer(server -> viewpoint(server.getLevel(Abyss.LEVEL), center, reach, distance));
			double x = view[0];
			double z = view[1];
			double y = Math.max(center.getY() + reach * 0.75 + 3.0, view[2] + 3.0);
			double dx = center.getX() + 0.5 - x;
			double dz = center.getZ() + 0.5 - z;
			float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
			float pitch = (float) Math.toDegrees(Math.atan2(y - (center.getY() + reach * 0.25), Math.sqrt(dx * dx + dz * dz)));
			teleport(context, world, Abyss.LEVEL, x, y, z, yaw, pitch);
			context.waitTicks(10);
			shoot(context, "treasure_" + kind.id());
			site = center.offset(reach * 2 + 48, 0, 0);
			if (site.getX() > 7600) {
				site = new BlockPos(6000, 0, site.getZ() + 400);
			}
		}
		// And one as a diver would really see it, without night vision.
		command(world, "effect clear @a minecraft:night_vision");
		context.waitTicks(40);
		shoot(context, "treasure_last_without_night_vision");
	}

	/** A camera spot (x, z and the ground under it) around a structure, where the ground is not much higher than the structure. */
	private static double[] viewpoint(ServerLevel level, BlockPos center, int reach, double distance) {
		double[] best = null;
		for (int k = 0; k < 8; k++) {
			double angle = Math.toRadians(45.0 * k);
			double ox = -distance * 0.4;
			double oz = -distance;
			double x = center.getX() + 0.5 + ox * Math.cos(angle) - oz * Math.sin(angle);
			double z = center.getZ() + 0.5 + ox * Math.sin(angle) + oz * Math.cos(angle);
			int ground = floor(level, (int) Math.floor(x), (int) Math.floor(z));
			if (best == null || ground < best[2]) {
				best = new double[] {x, z, ground};
			}
			if (ground <= center.getY() + reach * 0.6) {
				return new double[] {x, z, ground};
			}
		}
		return best;
	}

	// --- the Nether ----------------------------------------------------------------------

	private void nether(ClientGameTestContext context, TestSingleplayerContext world) {
		command(world, "gamemode spectator @a");
		command(world, "effect give @a minecraft:night_vision infinite 0 true");
		BlockPos cavern = world.getServer().computeOnServer(server -> openAir(server.getLevel(Level.NETHER), 150, 190));
		if (cavern != null) {
			teleport(context, world, Level.NETHER, cavern.getX() + 0.5, cavern.getY(), cavern.getZ() + 0.5, 0.0F, 8.0F);
			shoot(context, "nether_upper_caverns");
			teleport(context, world, Level.NETHER, cavern.getX() + 0.5, cavern.getY(), cavern.getZ() + 0.5, 0.0F, 60.0F);
			shoot(context, "nether_upper_caverns_down");
		} else {
			failures.add("nether: no open cavern found");
		}
		command(world, "effect clear @a minecraft:night_vision");
		for (String[] spot : new String[][] {{"ember_wastes", "60"}, {"brimstone_fields", "60"}, {"flesh_caverns", "60"},
				{"minecraft:nether_wastes", "60"}, {"minecraft:soul_sand_valley", "60"}, {"glowstone_grotto", "170"}, {"obsidian_spires", "170"}}) {
			String biome = spot[0];
			int y = Integer.parseInt(spot[1]);
			BlockPos at = world.getServer().computeOnServer(server -> {
				ServerLevel nether = server.getLevel(Level.NETHER);
				BlockPos found = findBiome(nether, biome, y);
				return found == null ? null : nearbyAir(nether, found);
			});
			if (at == null) {
				failures.add("nether: no " + biome);
				continue;
			}
			teleport(context, world, Level.NETHER, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 15.0F);
			shoot(context, "nether_" + biome.replace("minecraft:", ""));
		}
		// The strongholds of Hell, framed from inside their walls; night vision to see the detail.
		command(world, "effect give @a minecraft:night_vision infinite 0 true");
		for (String structure : new String[] {"infernal_citadel", "bone_pit", "ritual_circle"}) {
			BlockPos at = world.getServer().computeOnServer(server -> locate(server.getLevel(Level.NETHER), NaturesMod.id(structure)));
			if (at == null) {
				failures.add("nether: no " + structure);
				continue;
			}
			// Find the treasure: every stronghold keeps a chest at a known spot above its floor.
			BlockPos center = at.offset(8, 0, 8);
			int chestX = structure.equals("infernal_citadel") ? center.getX() - 2 : center.getX();
			int chestZ = structure.equals("infernal_citadel") ? center.getZ() + 11 : center.getZ();
			int chestY = world.getServer().computeOnServer(server -> {
				ServerLevel nether = server.getLevel(Level.NETHER);
				for (int y = 30; y < 220; y++) {
					if (nether.getBlockState(new BlockPos(chestX, y, chestZ)).is(Blocks.CHEST)) {
						return y;
					}
				}
				return -1;
			});
			if (chestY < 0) {
				failures.add("nether: no chest in the " + structure + " at " + center);
				continue;
			}
			double[] camera = switch (structure) {
				case "infernal_citadel" -> new double[] {center.getX() + 0.5, chestY + 7, center.getZ() - 17.5, 14.0};
				case "bone_pit" -> new double[] {center.getX() + 0.5, chestY + 13, center.getZ() - 11.5, 40.0};
				default -> new double[] {center.getX() + 0.5, chestY + 4, center.getZ() - 10.5, 22.0};
			};
			teleport(context, world, Level.NETHER, camera[0], camera[1], camera[2], 0.0F, (float) camera[3]);
			shoot(context, "nether_" + structure);
		}
		command(world, "effect clear @a minecraft:night_vision");
	}

	// --- finding places ------------------------------------------------------------------

	private static BlockPos locate(ServerLevel level, Identifier id) {
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.getOrThrow(ResourceKey.create(Registries.STRUCTURE, id));
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, HolderSet.direct(structure), BlockPos.ZERO, 64, false);
		return found == null ? null : found.getFirst();
	}

	private static BlockPos findBiome(ServerLevel level, String name, int y) {
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, name.startsWith("minecraft:")
				? Identifier.withDefaultNamespace(name.substring("minecraft:".length())) : NaturesMod.id(name));
		Predicate<Holder<Biome>> wanted = holder -> holder.is(key);
		Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(wanted, new BlockPos(0, y, 0), 3200, 32, 16);
		return found == null ? null : found.getFirst();
	}

	/** The top of the ground in a column (the sea floor under water). */
	private static int floor(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
	}

	/** The median height of the ground on a ring around a position. */
	private static int ringFloor(ServerLevel level, BlockPos center, int radius) {
		List<Integer> heights = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			double angle = Math.PI * 2 * i / 16;
			heights.add(floor(level, center.getX() + (int) Math.round(Math.cos(angle) * radius), center.getZ() + (int) Math.round(Math.sin(angle) * radius)));
		}
		heights.sort(null);
		return heights.get(heights.size() / 2);
	}

	/** A spot in the middle of a big open space between the given heights, near the origin. */
	private static BlockPos openAir(ServerLevel level, int minY, int maxY) {
		for (int r = 0; r <= 384; r += 48) {
			for (int dx = -r; dx <= r; dx += 48) {
				for (int dz = -r; dz <= r; dz += 48) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int y = minY; y <= maxY; y += 10) {
						if (isOpen(level, new BlockPos(dx, y, dz), 6)) {
							return new BlockPos(dx, y, dz);
						}
					}
				}
			}
		}
		return null;
	}

	private static boolean isOpen(ServerLevel level, BlockPos pos, int reach) {
		for (int[] offset : new int[][] {{0, 0, 0}, {reach, 0, 0}, {-reach, 0, 0}, {0, 0, reach}, {0, 0, -reach}, {0, reach, 0}, {0, -reach, 0}}) {
			if (!level.getBlockState(pos.offset(offset[0], offset[1], offset[2])).isAir()) {
				return false;
			}
		}
		return true;
	}

	/** The nearest pocket of air (with room to stand) above or below a position. */
	private static BlockPos nearbyAir(ServerLevel level, BlockPos pos) {
		for (int d = 0; d < 48; d++) {
			for (int sign : new int[] {1, -1}) {
				BlockPos at = pos.above(d * sign);
				if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && level.getBlockState(at.above(2)).isAir()) {
					return at;
				}
			}
		}
		return null;
	}
}
