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
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import com.naturesmod.NaturesMod;
import com.naturesmod.abyss.Abyss;
import com.naturesmod.creature.ModCreatures;
import com.naturesmod.creature.SeaCreature;

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
			List.of("abyssal_leviathan"));

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
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			scene("spawn", () -> context.takeScreenshot("nm_overworld_spawn"));
			scene("armor", () -> armor(context, world));
			scene("creatures", () -> creatures(context, world));
			scene("rift", () -> rift(context, world));
			scene("abyss", () -> abyss(context, world));
			scene("nether", () -> nether(context, world));
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
		world.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute in %s run tp @a %.2f %.2f %.2f %.1f %.1f",
				dimension.identifier(), x, y, z, yaw, pitch));
		context.waitTicks(5);
		world.getConnection().waitForChunksRender();
		context.waitTicks(10);
	}

	private static void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot("nm_" + name);
	}

	// --- the diving suits ----------------------------------------------------------------

	private void armor(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("gamemode creative @a");
		BlockPos ground = world.getServer().computeOnServer(server -> {
			ServerPlayer player = world.getConnection().getServerPlayer();
			ServerLevel level = server.overworld();
			int x = player.getBlockX();
			int z = player.getBlockZ();
			return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		});
		// Stand on a pillar in the sky so nothing gets between the camera and the diver.
		world.getServer().runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d minecraft:dark_prismarine",
				ground.getX(), ground.getY() + 40, ground.getZ(), ground.getX(), ground.getY() + 44, ground.getZ()));
		teleport(context, world, Level.OVERWORLD, ground.getX() + 0.5, ground.getY() + 45, ground.getZ() + 0.5, 180.0F, 0.0F);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		for (String set : new String[] {"tidewalker", "abyssal"}) {
			for (String[] piece : new String[][] {{"head", "helmet"}, {"chest", "chestplate"}, {"legs", "leggings"}, {"feet", "boots"}}) {
				world.getServer().runCommand("item replace entity @a armor." + piece[0] + " with naturesmod:" + set + "_" + piece[1]);
			}
			context.waitTicks(10);
			shoot(context, "armor_" + set);
		}
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		world.getServer().runCommand("clear @a");
	}

	// --- the creatures -------------------------------------------------------------------

	private void creatures(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos sea = world.getServer().computeOnServer(server -> {
			Pair<BlockPos, Holder<Biome>> found = server.overworld().findClosestBiome3d(holder -> holder.is(BiomeTags.IS_DEEP_OCEAN),
					BlockPos.ZERO, 6400, 32, 64);
			return found == null ? BlockPos.ZERO : found.getFirst();
		});
		world.getServer().runCommand("gamemode spectator @a");
		world.getServer().runCommand("effect give @a minecraft:night_vision infinite 0 true");
		teleport(context, world, Level.OVERWORLD, sea.getX() + 0.5, 48, sea.getZ() + 0.5, 0.0F, 0.0F);
		// Let the eyes get used to the water so the fog lifts.
		context.waitTicks(80);
		for (List<String> lineup : LINEUPS) {
			double biggest = lineup.stream().mapToDouble(id -> ModCreatures.species(ModCreatures.type(id)).scale()).max().orElse(1.0);
			double spacing = 1.2 + biggest * 1.6;
			double distance = 3.5 + biggest * 3.2 + lineup.size() * spacing * 0.35;
			List<UUID> ids = world.getServer().computeOnServer(server -> lineup(world, lineup, spacing, distance));
			context.waitTicks(20);
			String server = world.getServer().computeOnServer(s -> {
				ServerLevel level = (ServerLevel) world.getConnection().getServerPlayer().level();
				StringBuilder out = new StringBuilder("player " + world.getConnection().getServerPlayer().position());
				for (UUID id : ids) {
					Entity entity = level.getEntity(id);
					out.append(entity == null ? " gone" : " " + EntityType.getKey(entity.getType()).getPath() + "@" + entity.blockPosition()
							+ (entity.isAlive() ? "" : "(dead)"));
				}
				return out.toString();
			});
			String client = context.computeOnClient(c -> {
				StringBuilder out = new StringBuilder("player " + c.player.position() + " sees");
				for (Entity entity : c.level.entitiesForRendering()) {
					if (entity instanceof SeaCreature) {
						out.append(" ").append(EntityType.getKey(entity.getType()).getPath()).append("@").append(entity.blockPosition());
					}
				}
				return out.toString();
			});
			System.out.println("[gametest] lineup " + lineup.getFirst() + " server: " + server);
			System.out.println("[gametest] lineup " + lineup.getFirst() + " client: " + client);
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
		world.getServer().runCommand("gamemode creative @a");
		teleport(context, world, Abyss.LEVEL, 3.5, Abyss.SEA_LEVEL, 0.5, 90.0F, 25.0F);
		shoot(context, "abyss_arrival");
		world.getServer().runCommand("gamemode spectator @a");
		world.getServer().runCommand("effect give @a minecraft:night_vision infinite 0 true");
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
		for (String structure : new String[] {"sunken_temple", "bathysphere", "leviathan_lair"}) {
			BlockPos at = world.getServer().computeOnServer(server -> locate(server.getLevel(Abyss.LEVEL), NaturesMod.id(structure)));
			if (at == null) {
				failures.add("abyss: no " + structure);
				continue;
			}
			// Structures sit in the middle of their chunk; frame them from up and back, clear of the slope.
			BlockPos center = at.offset(8, 0, 8);
			int back = structure.equals("bathysphere") ? 11 : 20;
			int height = world.getServer().computeOnServer(server -> {
				ServerLevel abyss = server.getLevel(Abyss.LEVEL);
				return Math.max(ringFloor(abyss, center, back / 2) + back * 3 / 4, floor(abyss, center.getX(), center.getZ() - back) + 4);
			});
			teleport(context, world, Abyss.LEVEL, center.getX() + 0.5, height, center.getZ() - back + 0.5, 0.0F, 36.0F);
			shoot(context, "abyss_" + structure);
		}
	}

	// --- the Nether ----------------------------------------------------------------------

	private void nether(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("gamemode spectator @a");
		world.getServer().runCommand("effect give @a minecraft:night_vision infinite 0 true");
		BlockPos cavern = world.getServer().computeOnServer(server -> openAir(server.getLevel(Level.NETHER), 150, 190));
		if (cavern != null) {
			teleport(context, world, Level.NETHER, cavern.getX() + 0.5, cavern.getY(), cavern.getZ() + 0.5, 0.0F, 8.0F);
			shoot(context, "nether_upper_caverns");
			teleport(context, world, Level.NETHER, cavern.getX() + 0.5, cavern.getY(), cavern.getZ() + 0.5, 0.0F, 60.0F);
			shoot(context, "nether_upper_caverns_down");
		} else {
			failures.add("nether: no open cavern found");
		}
		world.getServer().runCommand("effect clear @a minecraft:night_vision");
		for (String[] spot : new String[][] {{"ember_wastes", "60"}, {"glowstone_grotto", "170"}, {"obsidian_spires", "170"}}) {
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
			shoot(context, "nether_" + biome);
		}
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
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, NaturesMod.id(name));
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
