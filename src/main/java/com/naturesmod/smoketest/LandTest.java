package com.naturesmod.smoketest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.mojang.datafixers.util.Pair;

import com.naturesmod.NaturesMod;
import com.naturesmod.creature.CreatureSpecies;
import com.naturesmod.creature.ModCreatures;
import com.naturesmod.creature.Mount;
import com.naturesmod.creature.SpeciesData;
import com.naturesmod.tree.ServerScheduler;
import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.deep.LandKind;
import com.naturesmod.worldgen.deep.LandPiece;
import com.naturesmod.worldgen.deep.LandShowcase;

import net.fabricmc.fabric.api.entity.FakePlayer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Overworld's new lands: where its structures turn up in the wild, every kind of them built,
 * checked and looted, and its new animals let loose to see that they live, move and can be ridden.
 */
final class LandTest {
	/** Blocks that fall off or go out when what holds them up is missing. */
	private static final Set<Block> HELD_UP = Set.of(Blocks.LADDER, Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.TORCH, Blocks.WALL_TORCH,
			Blocks.BELL, Blocks.RAIL, Blocks.STONE_PRESSURE_PLATE, Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.IRON_DOOR,
			Blocks.STONE_BUTTON, Blocks.CANDLE, Blocks.DEAD_BUSH);
	private final SmokeTest test;
	private final MinecraftServer server;
	private final ServerLevel level;

	LandTest(MinecraftServer server, SmokeTest test) {
		this.server = server;
		this.test = test;
		this.level = server.overworld();
	}

	void run(Runnable done) {
		try {
			test.line("");
			test.line("=== The Overworld's lands ===");
			long start = System.currentTimeMillis();
			wild();
			showcase();
			System.out.printf("[smoketest] land structures checked in %.1fs%n", (System.currentTimeMillis() - start) / 1000.0);
			born();
			BlockPos field = LandShowcase.drySite(level, new BlockPos(-320, 0, -320), 12);
			if (field != null) {
				animals(field, done);
				return;
			}
			test.fail("no dry land near spawn to let the new animals loose on");
		} catch (Throwable t) {
			test.fail("land test exception %s", t);
			for (StackTraceElement element : t.getStackTrace()) {
				test.line("    at %s", element);
			}
		}
		done.run();
	}

	/** Finds the nearest structure of each tier, generates the chunks it stands in, and looks it over. */
	private void wild() {
		for (String name : new String[] {"land_easy", "land_medium", "land_hard"}) {
			ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, NaturesMod.id(name));
			Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
			long began = System.currentTimeMillis();
			Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
					HolderSet.direct(holder), BlockPos.ZERO, 48, false);
			if (found == null) {
				if (name.equals("land_hard")) {
					test.line("no %s within 48 chunks of spawn", name);
				} else {
					test.fail("no %s within 48 chunks of spawn", name);
				}
				continue;
			}
			BlockPos at = found.getFirst();
			StructureStart start = level.getChunk(at.getX() >> 4, at.getZ() >> 4).getStartForStructure(holder.value());
			if (start == null || !start.isValid()) {
				test.fail("the %s found at %s has no start", name, at);
				continue;
			}
			for (StructurePiece piece : start.getPieces()) {
				if (piece instanceof LandPiece land) {
					// Generating every chunk it touches builds it chunk by chunk, as world generation does.
					BoundingBox bounds = land.getBoundingBox();
					for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
						for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
							level.getChunk(cx, cz);
						}
					}
					String kind = land.kind() == null ? "(unbuilt)" : land.kind().id();
					String climate = land.climate() == null ? "?" : land.climate().name().toLowerCase();
					Map<String, Integer> counts = inspect(land, "the wild " + kind);
					test.line("%s: a %s (%s) at %s, found and built in %.1fs: %s", name, kind, climate, land.center(),
							(System.currentTimeMillis() - began) / 1000.0, counts);
					if (land.kind() == null) {
						test.fail("the %s at %s was never built", name, land.center());
					}
				}
			}
		}
		Map<String, Integer> placed = new TreeMap<>();
		FeatureStats.snapshot().forEach((name, count) -> {
			if (name.startsWith("land/")) {
				placed.put(name.substring(5), count);
			}
		});
		test.line("land structures placed in the generated Overworld so far: %s", placed);
	}

	/** Builds every kind of land structure on its own patch of dry land, and looks each one over. */
	private void showcase() {
		Map<String, Integer> totals = new TreeMap<>();
		List<BoundingBox> built = new ArrayList<>();
		BlockPos cursor = new BlockPos(3000, 0, 3000);
		int index = 0;
		for (LandKind kind : LandKind.values()) {
			int reach = kind.tier().reach();
			BlockPos site = null;
			for (int tries = 0; tries < 80 && site == null; tries++) {
				BlockPos candidate = LandShowcase.drySite(level, cursor, reach);
				if (candidate == null) {
					break;
				}
				BoundingBox box = kind.bounds(candidate).inflatedBy(4);
				if (built.stream().anyMatch(other -> other.intersects(box))) {
					cursor = cursor.getX() > 3800 ? new BlockPos(3000, 0, cursor.getZ() + 64) : cursor.offset(64, 0, 0);
				} else {
					site = candidate;
				}
			}
			if (site == null) {
				test.fail("no dry land to build the %s on near %s", kind.id(), cursor);
				return;
			}
			Rotation rotation = Rotation.values()[index++ % 4];
			LandPiece piece;
			try {
				piece = LandShowcase.build(level, kind, site, rotation);
			} catch (Throwable t) {
				test.fail("the %s threw %s while building", kind.id(), t);
				for (StackTraceElement element : t.getStackTrace()) {
					test.line("    at %s", element);
				}
				continue;
			}
			built.add(piece.getBoundingBox());
			Map<String, Integer> counts = inspect(piece, "the " + kind.id());
			test.line("%s (%s, %s, %s) at %s: %s", kind.id(), kind.tier().name().toLowerCase(), piece.climate().name().toLowerCase(),
					rotation.name().toLowerCase(), piece.center(), counts);
			counts.forEach((name, count) -> totals.merge(name, count, Integer::sum));
			cursor = new BlockPos(site.getX() + reach * 2 + 16, 0, site.getZ());
			if (cursor.getX() > 3800) {
				cursor = new BlockPos(3000, 0, site.getZ() + 100);
			}
		}
		test.line("all %d kinds of land structure hold: %s", LandKind.values().length, totals);
		for (String needed : new String[] {"spawners", "keepers", "diamond", "books", "saddle", "riches", "armor_stands"}) {
			if (totals.getOrDefault(needed, 0) == 0) {
				test.fail("the land structures hold no %s", needed);
			}
		}
	}

	/**
	 * Counts what a built structure holds, opening its chests, and fails it for anything out of
	 * place: a block with nothing to hold it up, an empty chest, a frame on thin air, a keeper
	 * stuck in a wall.
	 */
	private Map<String, Integer> inspect(LandPiece piece, String label) {
		BoundingBox bounds = piece.getBoundingBox();
		Map<String, Integer> counts = new TreeMap<>();
		List<String> wrong = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
			for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
				for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
					BlockState state = level.getBlockState(pos.set(x, y, z));
					if (state.isAir()) {
						continue;
					}
					if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) {
						counts.merge("containers", 1, Integer::sum);
						if (level.getBlockEntity(pos) instanceof RandomizableContainer container && container.getLootTable() != null) {
							container.unpackLootTable(null);
							int items = 0;
							if (container instanceof Container inventory) {
								for (int i = 0; i < inventory.getContainerSize(); i++) {
									items += loot(inventory.getItem(i), counts);
								}
							}
							if (items == 0) {
								wrong.add("an empty chest at " + pos.toShortString());
							}
						}
					} else if (state.is(Blocks.SPAWNER)) {
						counts.merge("spawners", 1, Integer::sum);
					} else if (state.getBlock() instanceof AbstractSkullBlock) {
						counts.merge("heads", 1, Integer::sum);
					} else if (state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.EMERALD_BLOCK) || state.is(Blocks.DIAMOND_ORE)) {
						counts.merge("riches", 1, Integer::sum);
					} else if (state.is(Blocks.TNT)) {
						counts.merge("tnt", 1, Integer::sum);
					}
					if (state.getLightEmission() > 0) {
						counts.merge("lights", 1, Integer::sum);
					}
					if ((HELD_UP.contains(state.getBlock()) || state.getBlock() instanceof CarpetBlock) && !state.canSurvive(level, pos)) {
						wrong.add("a loose " + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + " at " + pos.toShortString());
					}
				}
			}
		}
		AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box)) {
			counts.merge("frames", 1, Integer::sum);
			loot(frame.getItem(), counts);
			if (!frame.survives()) {
				wrong.add("an item frame on nothing at " + frame.blockPosition().toShortString());
			}
		}
		counts.merge("armor_stands", level.getEntitiesOfClass(ArmorStand.class, box).size(), Integer::sum);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box.inflate(4.0), Mob::isPersistenceRequired)) {
			counts.merge("keepers", 1, Integer::sum);
			if (!level.noCollision(mob, mob.getBoundingBox().deflate(0.1))) {
				wrong.add("a " + BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath() + " stuck in a wall at " + mob.blockPosition().toShortString());
			}
		}
		counts.values().removeIf(v -> v == 0);
		if (!wrong.isEmpty()) {
			test.fail("%s has %d things out of place: %s", label, wrong.size(), wrong.subList(0, Math.min(10, wrong.size())));
		}
		if (counts.getOrDefault("containers", 0) == 0) {
			test.fail("%s has nothing to loot", label);
		}
		return counts;
	}

	/** Tallies the notable things among the loot, and returns how many items the stack holds. */
	private static int loot(ItemStack stack, Map<String, Integer> counts) {
		if (stack.isEmpty()) {
			return 0;
		}
		counts.merge("items", stack.getCount(), Integer::sum);
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (id.startsWith("diamond")) {
			counts.merge("diamond", stack.getCount(), Integer::sum);
		} else if (id.equals("enchanted_book")) {
			counts.merge("books", 1, Integer::sum);
		} else if (stack.is(Items.SADDLE)) {
			counts.merge("saddle", 1, Integer::sum);
		} else if (stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.TOTEM_OF_UNDYING)) {
			counts.merge("relics", 1, Integer::sum);
		} else if (id.endsWith("_smithing_template")) {
			counts.merge("templates", 1, Integer::sum);
		}
		return stack.getCount();
	}

	/** Which of the new animals were born with the chunks the test has generated. */
	private void born() {
		Map<EntityType<?>, String> ids = new HashMap<>();
		for (SpeciesData species : CreatureSpecies.ALL) {
			if (species.overworld()) {
				ids.put(ModCreatures.type(species.id()), species.id());
			}
		}
		Map<String, Integer> counts = new TreeMap<>();
		for (Entity entity : level.getAllEntities()) {
			String id = ids.get(entity.getType());
			if (id != null) {
				counts.merge(id, 1, Integer::sum);
			}
		}
		test.line("new animals born with the generated Overworld: %s", counts);
		if (counts.isEmpty()) {
			test.fail("none of the new animals were born with the Overworld's chunks");
		}
	}

	/** Lets every new animal of the Overworld loose in a field: they must live, move about, and the mounts take a saddle and a rider. */
	private void animals(BlockPos field, Runnable done) {
		int cx = field.getX() >> 4;
		int cz = field.getZ() >> 4;
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(),
				String.format("forceload add %d %d %d %d", (cx - 2) * 16, (cz - 2) * 16, (cx + 2) * 16 + 15, (cz + 2) * 16 + 15));
		// A level field of grass, cleared of trees, so nobody starts up in a tree or falls off one.
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = field.getX() - 20; x <= field.getX() + 20; x++) {
			for (int z = field.getZ() - 18; z <= field.getZ() + 18; z++) {
				for (int y = field.getY() + 1; y <= field.getY() + 20; y++) {
					level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				}
				level.setBlock(pos.set(x, field.getY(), z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
				for (int y = field.getY() - 1; y > field.getY() - 12 && level.getBlockState(pos.set(x, y, z)).isAir(); y--) {
					level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
				}
			}
		}
		List<Mob> spawned = new ArrayList<>();
		List<Vec3> starts = new ArrayList<>();
		int index = 0;
		for (SpeciesData species : CreatureSpecies.ALL) {
			if (!species.overworld()) {
				continue;
			}
			int x = field.getX() - 15 + (index % 6) * 6;
			int z = field.getZ() - 12 + (index / 6) * 6;
			index++;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			Mob mob = ModCreatures.type(species.id()).create(level, EntitySpawnReason.COMMAND);
			if (mob == null) {
				test.fail("could not create a %s", species.id());
				continue;
			}
			mob.snapTo(x + 0.5, ground + (species.flies() ? 3.0 : 0.2), z + 0.5, index * 40.0F, 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.COMMAND, null);
			mob.setPersistenceRequired();
			level.addFreshEntity(mob);
			spawned.add(mob);
			starts.add(mob.position());
			if (species.rideable() != mob instanceof Mount) {
				test.fail("the %s is %sa mount, and should %sbe", species.id(), mob instanceof Mount ? "" : "not ", species.rideable() ? "" : "not ");
			}
		}
		test.line("let %d new animals loose around %s", spawned.size(), field);
		ride(spawned);
		// How far each has been from where it started, at any time over the ten seconds.
		double[] farthest = new double[spawned.size()];
		for (int t = 10; t < 200; t += 10) {
			ServerScheduler.schedule(t, () -> {
				for (int i = 0; i < spawned.size(); i++) {
					farthest[i] = Math.max(farthest[i], spawned.get(i).position().distanceTo(starts.get(i)));
				}
			});
		}
		ServerScheduler.schedule(200, () -> {
			int alive = 0;
			int moved = 0;
			int still = 0;
			int lost = 0;
			StringBuilder report = new StringBuilder();
			for (int i = 0; i < spawned.size(); i++) {
				Mob mob = spawned.get(i);
				String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
				if (!mob.isAlive()) {
					String cause = mob.getLastDamageSource() == null ? "?" : mob.getLastDamageSource().getMsgId();
					report.append(id).append(":dead(").append(cause).append(") ");
					// Being hunted is how the wild works; anything else killing them is a fault.
					if (!cause.equals("?") && !cause.equals("mob") && !cause.equals("player")) {
						lost++;
					}
					continue;
				}
				alive++;
				double distance = Math.max(farthest[i], mob.position().distanceTo(starts.get(i)));
				if (distance > 0.5) {
					moved++;
				}
				if (!level.isPositionEntityTicking(mob.blockPosition())) {
					still++;
					report.append("(not ticking) ");
				}
				report.append(String.format("%s:%.1f ", id, distance));
				mob.discard();
			}
			test.line("after 10s: %d of %d new animals alive, %d moved: %s", alive, spawned.size(), moved, report);
			if (lost > 0) {
				test.fail("%d new animals died of something other than being hunted", lost);
			}
			if (still > 0) {
				test.line("%d of them stood in chunks that were not ticking, so their movement says nothing", still);
			} else if (moved * 2 < alive) {
				test.fail("most of the new animals did not move about");
			}
			done.run();
		});
	}

	/** Saddles each kind of mount and puts a rider on it. */
	private void ride(List<Mob> spawned) {
		FakePlayer rider = FakePlayer.get(level);
		for (Mob mob : spawned) {
			if (!(mob instanceof Mount mount)) {
				continue;
			}
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
			rider.snapTo(mount.getX(), mount.getY(), mount.getZ(), 0.0F, 0.0F);
			rider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
			mount.mobInteract(rider, InteractionHand.MAIN_HAND);
			boolean saddled = mount.isSaddledMount();
			rider.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			mount.mobInteract(rider, InteractionHand.MAIN_HAND);
			boolean riding = rider.getVehicle() == mount && mount.getControllingPassenger() == rider;
			rider.stopRiding();
			test.line("a %s took a saddle: %s, and a rider: %s", id, saddled, riding);
			if (!saddled) {
				test.fail("the %s could not be saddled", id);
			}
		}
	}
}
