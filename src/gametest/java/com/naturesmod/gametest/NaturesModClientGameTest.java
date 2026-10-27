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
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import com.naturesmod.NaturesConfig;
import com.naturesmod.NaturesMod;
import com.naturesmod.abyss.Abyss;
import com.naturesmod.creature.ModCreatures;
import com.naturesmod.creature.SeaCreature;
import com.naturesmod.disaster.Disaster;
import com.naturesmod.disaster.DisasterNet;
import com.naturesmod.disaster.DisasterType;
import com.naturesmod.disaster.Disasters;
import com.naturesmod.disaster.Eruption;
import com.naturesmod.disaster.Hurricane;
import com.naturesmod.disaster.LightningStorm;
import com.naturesmod.disaster.MeteorShower;
import com.naturesmod.disaster.Sinkhole;
import com.naturesmod.disaster.Tornado;
import com.naturesmod.disaster.Tsunami;
import com.naturesmod.worldgen.deep.DeepKind;
import com.naturesmod.worldgen.deep.DeepPiece;
import com.naturesmod.worldgen.deep.DeepShowcase;
import com.naturesmod.worldgen.deep.LandKind;
import com.naturesmod.worldgen.deep.LandShowcase;
import com.naturesmod.worldgen.wild.WildOverworld;

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
		// No disaster of its own accord in the middle of the other scenes; the disasters' scene calls them up.
		NaturesConfig.get().naturalDisasters = false;
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
			scene("wilds", () -> wilds(context, world));
			scene("lands", () -> lands(context, world));
			// Building and visiting all 55 treasures loads the server more than anything else.
			scene("treasures", () -> treasures(context, world));
			// Last: the disasters wreck the land they happen on.
			scene("disasters", () -> disasters(context, world));
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

	// --- the Overworld's lands ----------------------------------------------------------------

	private static final List<List<String>> LAND_LINEUPS = List.of(List.of("giraffe", "elephant", "rhino", "hippo"),
			List.of("moose", "bison", "grizzly_bear", "gorilla"), List.of("zebra", "lion", "jaguar", "snow_leopard", "hyena"),
			List.of("ostrich", "deer", "boar", "crocodile", "flamingo"), List.of("monkey", "capybara", "penguin", "scorpion", "rattlesnake"),
			List.of("vulture", "eagle", "owl", "toucan"));

	/** Lines the Overworld's new animals up on a cleared field, then builds every kind of land structure and photographs it. */
	private void lands(ClientGameTestContext context, TestSingleplayerContext world) {
		command(world, "gamemode spectator @a");
		BlockPos field = world.getServer().computeOnServer(server -> {
			for (BlockPos from : List.of(new BlockPos(640, 0, 640), new BlockPos(-2000, 0, 1500), new BlockPos(3000, 0, -3000), new BlockPos(9000, 0, 9000))) {
				BlockPos at = LandShowcase.drySite(server.overworld(), from, 10);
				if (at != null) {
					return at;
				}
			}
			return null;
		});
		if (field == null) {
			failures.add("lands: no dry land for the animals");
		} else {
			teleport(context, world, Level.OVERWORLD, field.getX() + 0.5, field.getY() + 2.6, field.getZ() + 0.5, 0.0F, 8.0F);
			command(world, String.format("fill %d %d %d %d %d %d minecraft:air", field.getX() - 16, field.getY() + 1, field.getZ() - 4,
					field.getX() + 16, field.getY() + 14, field.getZ() + 28));
			command(world, String.format("fill %d %d %d %d %d %d minecraft:grass_block", field.getX() - 16, field.getY(), field.getZ() - 4,
					field.getX() + 16, field.getY(), field.getZ() + 28));
			context.waitTicks(10);
			for (List<String> lineup : LAND_LINEUPS) {
				double biggest = lineup.stream().mapToDouble(id -> ModCreatures.species(ModCreatures.type(id)).scale()).max().orElse(1.0);
				double spacing = 1.4 + biggest * 1.5;
				double distance = 4.0 + biggest * 3.0 + lineup.size() * spacing * 0.35;
				boolean walkers = ModCreatures.species(ModCreatures.type(lineup.getFirst())).walks();
				double eye = walkers ? field.getY() + 2.6 + biggest * 0.8 : field.getY() + 7.0;
				teleport(context, world, Level.OVERWORLD, field.getX() + 0.5, eye, field.getZ() + 0.5, 0.0F, walkers ? 8.0F : 0.0F);
				List<UUID> ids = world.getServer().computeOnServer(server -> endLineup(world, lineup, spacing, distance, walkers));
				context.waitTicks(20);
				shoot(context, "land_creatures_" + lineup.getFirst());
				world.getServer().runOnServer(server -> {
					for (UUID id : ids) {
						Entity entity = server.overworld().getEntity(id);
						if (entity != null) {
							entity.discard();
						}
					}
				});
			}
		}
		// Every kind of land structure, built facing the camera.
		BlockPos site = new BlockPos(9000, 0, 9000);
		for (LandKind kind : LandKind.values()) {
			int reach = kind.tier().reach();
			BlockPos from = site;
			BlockPos center = world.getServer().computeOnServer(server -> {
				BlockPos at = LandShowcase.drySite(server.overworld(), from, reach);
				return at == null ? null : LandShowcase.build(server.overworld(), kind, at, Rotation.NONE).center();
			});
			if (center == null) {
				failures.add("lands: no dry land for " + kind.id());
				return;
			}
			double distance = reach * 1.6 + 8.0;
			double[] view = world.getServer().computeOnServer(server -> landViewpoint(server.overworld(), center, reach, distance));
			double x = view[0];
			double z = view[1];
			double y = Math.max(center.getY() + reach * 0.8 + 4.0, view[2] + 3.0);
			double dx = center.getX() + 0.5 - x;
			double dz = center.getZ() + 0.5 - z;
			float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
			float pitch = (float) Math.toDegrees(Math.atan2(y - (center.getY() + reach * 0.3), Math.sqrt(dx * dx + dz * dz)));
			teleport(context, world, Level.OVERWORLD, x, y, z, yaw, pitch);
			context.waitTicks(10);
			shoot(context, "land_" + kind.id());
			site = center.offset(reach * 2 + 40, 0, 0);
			if (site.getX() > 10400) {
				site = new BlockPos(9000, 0, site.getZ() + 300);
			}
		}
	}

	/** A camera spot (x, z and the top of whatever stands under it) round a land structure, in front where the land allows. */
	private static double[] landViewpoint(ServerLevel level, BlockPos center, int reach, double distance) {
		double[] best = null;
		for (int k = 0; k < 8; k++) {
			double angle = Math.toRadians(45.0 * k);
			double ox = -distance * 0.4;
			double oz = -distance;
			double x = center.getX() + 0.5 + ox * Math.cos(angle) - oz * Math.sin(angle);
			double z = center.getZ() + 0.5 + ox * Math.sin(angle) + oz * Math.cos(angle);
			level.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
			if (best == null || ground < best[2]) {
				best = new double[] {x, z, ground};
			}
			if (ground <= center.getY() + reach * 0.6) {
				return new double[] {x, z, ground};
			}
		}
		return best;
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

	// --- the wild Overworld ------------------------------------------------------------------

	/** Looks out over each of the wild Overworld's biomes from a little above its ground, and into its two kinds of cave. */
	private void wilds(ClientGameTestContext context, TestSingleplayerContext world) {
		command(world, "gamemode spectator @a");
		for (String name : WildOverworld.BIOMES) {
			boolean cave = name.equals("crystal_caverns") || name.equals("fungal_grotto");
			ResourceKey<Biome> key = WildOverworld.biome(name);
			double[] view = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				int y = name.equals("crystal_caverns") ? -30 : name.equals("fungal_grotto") ? 20 : 90;
				Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(key), new BlockPos(0, y, 0), 9000, 32,
						cave ? 8 : 64);
				if (found == null) {
					return null;
				}
				BlockPos at = found.getFirst();
				if (!cave) {
					at = WildOverworld.heart(level, at, key, true);
					int ground = floor(level, at.getX(), at.getZ());
					return new double[] {at.getX() + 0.5, ground + 14, at.getZ() - 22.5, 28.0};
				}
				// The roomiest pocket of the cave near where the biome was found.
				BlockPos best = null;
				int roomiest = 0;
				for (int dx = -24; dx <= 24; dx += 4) {
					for (int dz = -24; dz <= 24; dz += 4) {
						level.getChunk((at.getX() + dx) >> 4, (at.getZ() + dz) >> 4);
						for (int dy = -16; dy <= 16; dy += 2) {
							BlockPos spot = at.offset(dx, dy, dz);
							if (!level.getBlockState(spot).isAir() || !level.getBiome(spot).is(key)) {
								continue;
							}
							int room = 0;
							for (int reach = 1; reach <= 8 && isOpen(level, spot, reach); reach++) {
								room = reach;
							}
							if (room > roomiest) {
								roomiest = room;
								best = spot;
							}
						}
					}
				}
				return best == null ? null : new double[] {best.getX() + 0.5, best.getY(), best.getZ() + 0.5, 12.0};
			});
			if (view == null) {
				failures.add("wilds: no " + name + " to look at");
				continue;
			}
			if (cave) {
				command(world, "effect give @a minecraft:night_vision 30 0 true");
			}
			teleport(context, world, Level.OVERWORLD, view[0], view[1], view[2], 0.0F, (float) view[3]);
			context.waitTicks(30);
			shoot(context, "wild_" + name);
			if (cave) {
				command(world, "effect clear @a minecraft:night_vision");
			}
		}
	}

	// --- natural disasters ---------------------------------------------------------------

	/** Lets each kind of natural disaster loose somewhere out of the way and photographs it as it happens. */
	private void disasters(ClientGameTestContext context, TestSingleplayerContext world) {
		command(world, "gamemode spectator @a");
		List<Runnable> scenes = List.of(() -> tornado(context, world), () -> carried(context, world), () -> earthquake(context, world),
				() -> meteors(context, world), () -> eruption(context, world), () -> wildfire(context, world), () -> sandstorm(context, world),
				() -> blizzard(context, world), () -> hurricane(context, world), () -> tsunami(context, world), () -> sinkhole(context, world),
				() -> avalanche(context, world), () -> lightningStorm(context, world), () -> acidRain(context, world));
		for (Runnable scene : scenes) {
			command(world, "time set noon");
			command(world, "weather clear");
			try {
				scene.run();
			} catch (Throwable t) {
				failures.add("disasters: " + t);
				t.printStackTrace(System.out);
			}
			world.getServer().runOnServer(server -> Disasters.stop(server.overworld(), disaster -> true));
			command(world, "gamemode spectator @a");
			command(world, "effect clear @a");
			context.waitTicks(5);
		}
		world.getServer().runOnServer(server -> {
			for (String failure : Disasters.failures()) {
				failures.add("disasters: " + failure);
			}
		});
	}

	/** Dry, fairly level land out from a place: its middle, on the ground. */
	private static Vec3 dryLand(TestSingleplayerContext world, BlockPos from) {
		return world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos site = LandShowcase.drySite(level, from, 30);
			return site == null ? null : new Vec3(site.getX() + 0.5, floor(level, site.getX(), site.getZ()), site.getZ() + 0.5);
		});
	}

	/**
	 * Puts the camera `back` blocks from a place, the given way round (an angle in degrees, 0 to the
	 * south), `up` blocks over the ground there, looking at a point `aim` blocks above the place.
	 */
	private static void view(ClientGameTestContext context, TestSingleplayerContext world, Vec3 at, double angle, double back, double up, double aim) {
		double x = at.x - Math.sin(Math.toRadians(angle)) * back;
		double z = at.z + Math.cos(Math.toRadians(angle)) * back;
		int ground = world.getServer().computeOnServer(server -> floor(server.overworld(), (int) Math.floor(x), (int) Math.floor(z)));
		double y = Math.max(ground, at.y) + up;
		double dx = at.x - x;
		double dy = at.y + aim - (y + 1.62);
		double dz = at.z - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		teleport(context, world, Level.OVERWORLD, x, y, z, yaw, pitch);
	}

	/** Where the disaster of a kind under way is now. */
	private static Vec3 where(TestSingleplayerContext world, DisasterType type) {
		return world.getServer().computeOnServer(server -> Disasters.active(server.overworld()).stream().filter(d -> d.type == type).findFirst()
				.map(Disaster::position).orElse(null));
	}

	private void tornado(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = dryLand(world, new BlockPos(-6000, 0, 6000));
		if (at == null) {
			failures.add("disasters: no land for the tornado");
			return;
		}
		// From well above the land round about, so no hill hides it.
		view(context, world, at.add(-20.0, 0.0, 0.0), 0.0, 90.0, 26.0, 22.0);
		world.getServer().runOnServer(server -> {
			// Already down on the ground, heading west across the view.
			Tornado tornado = (Tornado) Disasters.start(DisasterType.TORNADO, server.overworld(), at, 4.0F, false);
			tornado.skip(230);
			tornado.aim(at.add(-300.0, 0.0, 0.0));
		});
		context.waitTicks(80);
		shoot(context, "disaster_tornado");
		Vec3 now = where(world, DisasterType.TORNADO);
		if (now != null) {
			view(context, world, now.add(-8.0, 0.0, 0.0), 20.0, 45.0, 9.0, 16.0);
			context.waitTicks(30);
			shoot(context, "disaster_tornado_close");
		}
	}

	private void earthquake(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = dryLand(world, new BlockPos(6000, 0, 6000));
		if (at == null) {
			failures.add("disasters: no land for the earthquake");
			return;
		}
		view(context, world, at, 30.0, 30.0, 20.0, 0.0);
		world.getServer().runOnServer(server -> Disasters.start(DisasterType.EARTHQUAKE, server.overworld(), at, 7.8F, false));
		context.waitTicks(30);
		shoot(context, "disaster_earthquake");
	}

	private void meteors(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = dryLand(world, new BlockPos(6000, 0, -6000));
		if (at == null) {
			failures.add("disasters: no land for the meteor shower");
			return;
		}
		command(world, "time set midnight");
		Vec3 radiant = world.getServer().computeOnServer(server -> {
			Disaster shower = Disasters.start(DisasterType.METEOR_SHOWER, server.overworld(), at, 5.0F, false);
			float[] r = shower.extra();
			return new Vec3(r[0], r[1], r[2]);
		});
		// Looking up toward the radiant, the point of the sky the meteors all come from (they travel along `radiant`).
		float yaw = (float) Math.toDegrees(Math.atan2(radiant.x, -radiant.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(-radiant.y, Math.sqrt(radiant.x * radiant.x + radiant.z * radiant.z)));
		teleport(context, world, Level.OVERWORLD, at.x, at.y + 2.0, at.z, yaw, Math.max(-60.0F, pitch + 20.0F));
		world.getServer().runOnServer(server -> Disasters.active(server.overworld()).stream().filter(d -> d instanceof MeteorShower)
				.forEach(d -> {
					for (int i = 0; i < 5; i++) {
						((MeteorShower) d).launchNow();
					}
				}));
		context.waitTicks(25);
		shoot(context, "disaster_meteors");
		// A big one strikes the ground nearby.
		BlockPos strike = BlockPos.containing(at.add(0.0, 0.0, -50.0));
		world.getServer().runOnServer(server -> Disasters.active(server.overworld()).stream().filter(d -> d instanceof MeteorShower).findFirst()
				.ifPresent(d -> ((MeteorShower) d).impact(new BlockPos(strike.getX(), floor(server.overworld(), strike.getX(), strike.getZ()) - 1,
						strike.getZ()), 5.0)));
		teleport(context, world, Level.OVERWORLD, at.x, at.y + 4.0, at.z, 180.0F, 4.0F);
		context.waitTicks(4);
		shoot(context, "disaster_meteor_impact");
		command(world, "time set noon");
		view(context, world, new Vec3(strike.getX() + 0.5, at.y, strike.getZ() + 0.5), 0.0, 24.0, 18.0, -4.0);
		// Once the dust has drifted off.
		context.waitTicks(220);
		shoot(context, "disaster_meteor_crater");
	}

	private void eruption(ClientGameTestContext context, TestSingleplayerContext world) {
		ResourceKey<Biome> peaks = WildOverworld.biome("volcanic_peaks");
		Vec3 at = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(peaks), new BlockPos(0, 120, 0), 9000, 32, 64);
			if (found == null) {
				return null;
			}
			BlockPos heart = WildOverworld.heart(level, found.getFirst(), peaks, true);
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					level.getChunk((heart.getX() >> 4) + dx, (heart.getZ() >> 4) + dz);
				}
			}
			Eruption eruption = (Eruption) Disasters.start(DisasterType.ERUPTION, level, Vec3.atBottomCenterOf(heart), 4.0F, false);
			// On to the height of the eruption, its column fully risen.
			eruption.skip(eruption.unrestTicks() + 400 - eruption.age());
			return Vec3.atBottomCenterOf(eruption.vent());
		});
		if (at == null) {
			failures.add("disasters: no volcanic peaks to erupt");
			return;
		}
		view(context, world, at, 200.0, 95.0, 26.0, 55.0);
		context.waitTicks(100);
		shoot(context, "disaster_eruption");
		// The column collapsing: pyroclastic flows racing down the mountain.
		world.getServer().runOnServer(server -> Disasters.active(server.overworld()).stream().filter(d -> d instanceof Eruption).forEach(d -> {
			Eruption eruption = (Eruption) d;
			eruption.skip(eruption.unrestTicks() + (int) (eruption.eruptionTicks() * 0.92) - eruption.age());
		}));
		view(context, world, at, 160.0, 85.0, 26.0, -6.0);
		context.waitTicks(80);
		shoot(context, "disaster_pyroclastic_flow");
	}

	private void wildfire(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(Biomes.FOREST) || holder.is(Biomes.BIRCH_FOREST),
					new BlockPos(3000, 80, 3000), 6400, 64, 64);
			if (found == null) {
				return null;
			}
			BlockPos spot = found.getFirst();
			return new Vec3(spot.getX() + 0.5, floor(level, spot.getX(), spot.getZ()), spot.getZ() + 0.5);
		});
		if (at == null) {
			failures.add("disasters: no forest to burn");
			return;
		}
		view(context, world, at, 90.0, 55.0, 24.0, 4.0);
		world.getServer().runOnServer(server -> Disasters.start(DisasterType.WILDFIRE, server.overworld(), at, 4.0F, false));
		context.waitTicks(280);
		shoot(context, "disaster_wildfire");
	}

	private void sandstorm(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(Biomes.DESERT), new BlockPos(-3000, 80, -3000), 6400, 64,
					64);
			if (found == null) {
				return null;
			}
			// Dry land in the desert, not the sea beside it.
			BlockPos dry = LandShowcase.drySite(level, found.getFirst(), 30);
			BlockPos spot = dry != null ? dry : found.getFirst();
			return new Vec3(spot.getX() + 0.5, floor(level, spot.getX(), spot.getZ()), spot.getZ() + 0.5);
		});
		if (at == null) {
			failures.add("disasters: no desert for the sandstorm");
			return;
		}
		Vec3 wind = world.getServer().computeOnServer(server -> Disasters.start(DisasterType.SANDSTORM, server.overworld(), at, 4.0F, false).direction());
		// From the middle of the storm, looking upwind at the wall of dust coming on.
		double upwind = Math.toDegrees(Math.atan2(wind.x, -wind.z));
		int ground = world.getServer().computeOnServer(server -> floor(server.overworld(), (int) Math.floor(at.x), (int) Math.floor(at.z)));
		teleport(context, world, Level.OVERWORLD, at.x, ground + 3.0, at.z, (float) upwind, -12.0F);
		context.waitTicks(60);
		shoot(context, "disaster_sandstorm");
		// Inside it.
		world.getServer().runOnServer(server -> Disasters.active(server.overworld()).stream().filter(d -> d.type == DisasterType.SANDSTORM)
				.forEach(d -> d.skip(420)));
		context.waitTicks(40);
		shoot(context, "disaster_sandstorm_inside");
	}

	/** In green, temperate country: a blizzard brings its own cold, and its whiteout. */
	private void blizzard(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = dryLand(world, new BlockPos(-2400, 0, -6000));
		if (at == null) {
			failures.add("disasters: no land for the blizzard");
			return;
		}
		teleport(context, world, Level.OVERWORLD, at.x, at.y + 3.0, at.z, 30.0F, 8.0F);
		world.getServer().runOnServer(server -> Disasters.start(DisasterType.BLIZZARD, server.overworld(), at, 5.0F, false).skip(200));
		context.waitTicks(100);
		shoot(context, "disaster_blizzard");
	}

	/** A survival player beside an EF4 tornado's core is caught up, whirled round and carried high. */
	private void carried(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = dryLand(world, new BlockPos(-6400, 0, 5200));
		if (at == null) {
			failures.add("disasters: no land to be carried off by a tornado");
			return;
		}
		double core = world.getServer().computeOnServer(server -> {
			Tornado tornado = (Tornado) Disasters.start(DisasterType.TORNADO, server.overworld(), at, 4.0F, false);
			tornado.skip(300);
			// Crawling on west, slowly enough to stay by the player for the few seconds it takes.
			tornado.aim(at.add(-400.0, 0.0, 0.0));
			return tornado.core();
		});
		context.waitTicks(20);
		Vec3 where = where(world, DisasterType.TORNADO);
		if (where == null) {
			failures.add("disasters: the tornado to be carried off by is gone");
			return;
		}
		// On the ground just inside its reach, ahead of it, looking at the funnel.
		double px = where.x - Math.max(4.0, core * 0.9);
		double pz = where.z;
		int ground = world.getServer().computeOnServer(server -> floor(server.overworld(), (int) Math.floor(px), (int) Math.floor(pz)));
		command(world, "gamemode survival @a");
		command(world, "effect give @a minecraft:resistance 60 4 true");
		teleport(context, world, Level.OVERWORLD, px, ground, pz, -90.0F, -20.0F);
		double startY = world.getServer().computeOnServer(server -> world.getConnection().getServerPlayer().getY());
		double highest = startY;
		boolean caught = false;
		for (int i = 0; i < 24; i++) {
			context.waitTicks(5);
			double[] state = world.getServer().computeOnServer(server -> {
				ServerPlayer player = world.getConnection().getServerPlayer();
				boolean held = Disasters.active(server.overworld()).stream().anyMatch(d -> d instanceof Tornado tornado && tornado.carrying(player));
				return new double[] {player.getY(), held ? 1.0 : 0.0};
			});
			double[] seen = context.computeOnClient(client -> new double[] {client.player.getY(), client.player.getDeltaMovement().y,
					client.player.onGround() ? 1.0 : 0.0, DisasterNet.pushesReceived});
			System.out.printf(java.util.Locale.ROOT, "[gametest] carried, tick %d: held %s, server y %.2f, client y %.2f moving up %.2f, on the ground %s, %d pushes%n",
					i * 5, state[1] > 0.5, state[0], seen[0], seen[1], seen[2] > 0.5, (int) seen[3]);
			highest = Math.max(highest, state[0]);
			caught |= state[1] > 0.5;
			if (i == 8) {
				shoot(context, "disaster_tornado_carried");
			}
		}
		System.out.printf("[gametest] carried by the EF4 tornado: caught %s, up to %.1f blocks above where the player stood%n", caught, highest - startY);
		if (!caught || highest - startY < 10.0) {
			failures.add(String.format(java.util.Locale.ROOT, "disasters: an EF4 tornado didn't carry the player (caught %s, %.1f blocks up)", caught,
					highest - startY));
		}
	}

	/** In the calm eye of a category 5, the eyewall's cloud standing all round; then out in the eyewall itself. */
	private void hurricane(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = dryLand(world, new BlockPos(6400, 0, 2400));
		if (at == null) {
			failures.add("disasters: no land for the hurricane");
			return;
		}
		double eyewall = world.getServer().computeOnServer(server -> {
			Hurricane hurricane = (Hurricane) Disasters.start(DisasterType.HURRICANE, server.overworld(), at, 5.0F, false);
			hurricane.skip(400);
			return hurricane.eyewall();
		});
		teleport(context, world, Level.OVERWORLD, at.x, at.y + 6.0, at.z, 0.0F, -6.0F);
		context.waitTicks(60);
		shoot(context, "disaster_hurricane_eye");
		Vec3 centre = where(world, DisasterType.HURRICANE);
		if (centre != null) {
			int ground = world.getServer().computeOnServer(server -> floor(server.overworld(), (int) Math.floor(centre.x + eyewall), (int) Math.floor(centre.z)));
			teleport(context, world, Level.OVERWORLD, centre.x + eyewall, ground + 4.0, centre.z, 0.0F, 0.0F);
			context.waitTicks(40);
			shoot(context, "disaster_hurricane_eyewall");
		}
	}

	/** On a beach, looking out to sea as the wave comes in. */
	private void tsunami(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(BiomeTags.IS_BEACH), new BlockPos(0, 64, 0), 6400, 64, 64);
			if (found == null) {
				return null;
			}
			BlockPos spot = found.getFirst();
			return new Vec3(spot.getX() + 0.5, floor(level, spot.getX(), spot.getZ()), spot.getZ() + 0.5);
		});
		if (at == null) {
			failures.add("disasters: no beach for the tsunami");
			return;
		}
		Vec3 in = world.getServer().computeOnServer(server -> {
			Tsunami wave = (Tsunami) Disasters.start(DisasterType.TSUNAMI, server.overworld(), at, 5.0F, false);
			while (!wave.isDone() && wave.front() < wave.along(at.x, at.z) - 120.0 && wave.age() < 6000) {
				wave.skip(20);
			}
			return wave.direction();
		});
		// Looking out the way it comes from: back along its way in.
		float yaw = (float) Math.toDegrees(Math.atan2(in.x, -in.z));
		teleport(context, world, Level.OVERWORLD, at.x, at.y + 4.0, at.z, yaw, -8.0F);
		// Until it is close: about forty blocks out.
		for (int i = 0; i < 30; i++) {
			double gap = world.getServer().computeOnServer(server -> Disasters.active(server.overworld()).stream()
					.filter(d -> d instanceof Tsunami).map(d -> (Tsunami) d).findFirst().map(wave -> wave.along(at.x, at.z) - wave.front()).orElse(0.0));
			if (gap < 45.0) {
				break;
			}
			context.waitTicks(10);
		}
		shoot(context, "disaster_tsunami");
		context.waitTicks(60);
		shoot(context, "disaster_tsunami_ashore");
	}

	/** From the brink of a level-4 sinkhole once it has opened. */
	private void sinkhole(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = dryLand(world, new BlockPos(2400, 0, 6400));
		if (at == null) {
			failures.add("disasters: no land for the sinkhole");
			return;
		}
		double radius = world.getServer().computeOnServer(server -> {
			Sinkhole hole = (Sinkhole) Disasters.start(DisasterType.SINKHOLE, server.overworld(), at, 4.0F, false);
			hole.skip(hole.warningTicks() - 10 - hole.age());
			return hole.openingRadius();
		});
		view(context, world, at, 30.0, radius + 14.0, 12.0, -6.0);
		context.waitTicks(140);
		shoot(context, "disaster_sinkhole");
	}

	/** Beside the path of an avalanche coming down a snowy slope. */
	private void avalanche(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(Biomes.SNOWY_SLOPES) || holder.is(Biomes.GROVE),
					new BlockPos(0, 120, 0), 6400, 64, 64);
			if (found == null) {
				return null;
			}
			BlockPos spot = found.getFirst();
			return new Vec3(spot.getX() + 0.5, floor(level, spot.getX(), spot.getZ()), spot.getZ() + 0.5);
		});
		if (at == null) {
			failures.add("disasters: no snowy slopes for the avalanche");
			return;
		}
		world.getServer().runOnServer(server -> Disasters.start(DisasterType.AVALANCHE, server.overworld(), at, 5.0F, false));
		context.waitTicks(10);
		Vec3 release = where(world, DisasterType.AVALANCHE);
		if (release == null) {
			failures.add("disasters: the avalanche never let go");
			return;
		}
		// From the side, a little below where it let go, looking up at it coming down.
		view(context, world, release.add((at.x - release.x) * 0.5, 0.0, (at.z - release.z) * 0.5), 90.0, 50.0, 14.0, 6.0);
		context.waitTicks(60);
		shoot(context, "disaster_avalanche");
	}

	/** Under a level-5 lightning storm, ball lightning drifting by. */
	private void lightningStorm(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = dryLand(world, new BlockPos(-6400, 0, -2400));
		if (at == null) {
			failures.add("disasters: no land for the lightning storm");
			return;
		}
		world.getServer().runOnServer(server -> {
			LightningStorm storm = (LightningStorm) Disasters.start(DisasterType.LIGHTNING_STORM, server.overworld(), at, 5.0F, false);
			storm.skip(300);
			storm.formOrb(at.add(6.0, 2.0, 0.0));
		});
		teleport(context, world, Level.OVERWORLD, at.x - 6.0, at.y + 2.0, at.z, -90.0F, 5.0F);
		context.waitTicks(40);
		shoot(context, "disaster_lightning_storm");
	}

	/** In the thick of a level-5 acid rain. */
	private void acidRain(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 at = dryLand(world, new BlockPos(0, 0, -6400));
		if (at == null) {
			failures.add("disasters: no land for the acid rain");
			return;
		}
		world.getServer().runOnServer(server -> Disasters.start(DisasterType.ACID_RAIN, server.overworld(), at, 5.0F, false).skip(200));
		teleport(context, world, Level.OVERWORLD, at.x, at.y + 3.0, at.z, 45.0F, 0.0F);
		context.waitTicks(80);
		shoot(context, "disaster_acid_rain");
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
