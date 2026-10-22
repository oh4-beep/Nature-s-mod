package com.naturesmod.smoketest;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.mojang.datafixers.util.Pair;

import com.naturesmod.block.ModBlocks;
import com.naturesmod.creature.LargeSwimmer;
import com.naturesmod.item.AbyssWeapon;
import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.deep.DeepKind;
import com.naturesmod.worldgen.deep.DeepPiece;
import com.naturesmod.worldgen.deep.DeepShowcase;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

/**
 * Checks the treasures of the deep: finds each tier in the Abyss, then builds every kind on its
 * own patch of sea floor, opens all its chests and counts what it holds.
 */
final class TreasureTest {
	private final SmokeTest test;
	private final ServerLevel abyss;

	TreasureTest(SmokeTest test, ServerLevel abyss) {
		this.test = test;
		this.abyss = abyss;
	}

	void run() {
		test.line("");
		test.line("--- the treasures of the deep ---");
		long start = System.currentTimeMillis();
		for (String tier : new String[] {"deep_ruin", "deep_vault", "deep_wonder", "deep_highland"}) {
			locate(tier);
		}
		test.line("treasures placed so far: %s", placed());
		Map<String, Integer> totals = new TreeMap<>();
		int index = 0;
		BlockPos site = new BlockPos(24000, 0, 24000);
		for (DeepKind kind : DeepKind.values()) {
			site = DeepShowcase.levelSite(abyss, site, kind.tier().reach());
			if (site == null) {
				test.fail("no level sea floor for %s", kind.id());
				return;
			}
			build(kind, site, Rotation.values()[index++ % 4], totals);
			site = site.offset(kind.tier().reach() * 2 + 40, 0, 0);
			if (site.getX() > 26000) {
				site = new BlockPos(24000, 0, site.getZ() + 400);
			}
		}
		test.line("all %d kinds hold: %s", DeepKind.values().length, totals);
		for (String needed : new String[] {"abyssal_enchanting_table", "elytra", "weapon", "music_disc", "head", "netherite", "diamond",
				"spawner", "keeper"}) {
			if (totals.getOrDefault(needed, 0) == 0) {
				test.fail("the treasures of the deep hold no %s", needed);
			}
		}
		System.out.printf("[smoketest] treasures checked in %.1fs%n", (System.currentTimeMillis() - start) / 1000.0);
	}

	private Map<String, Integer> placed() {
		Map<String, Integer> counts = new TreeMap<>();
		FeatureStats.snapshot().forEach((name, count) -> {
			if (name.startsWith("deep/")) {
				counts.put(name.substring(5), count);
			}
		});
		return counts;
	}

	/** Finds the nearest structure of a tier and reports what kind it is and what it holds. */
	private void locate(String name) {
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, com.naturesmod.NaturesMod.id(name));
		Holder<Structure> holder = abyss.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		long start = System.currentTimeMillis();
		Pair<BlockPos, Holder<Structure>> found = abyss.getChunkSource().getGenerator()
				.findNearestMapStructure(abyss, HolderSet.direct(holder), BlockPos.ZERO, 64, false);
		if (found == null) {
			test.fail("no %s within 64 chunks of the origin", name);
			return;
		}
		BlockPos at = found.getFirst();
		for (int cx = -2; cx <= 2; cx++) {
			for (int cz = -2; cz <= 2; cz++) {
				abyss.getChunk((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz);
			}
		}
		test.line("%s found at %s in %.1fs", name, at, (System.currentTimeMillis() - start) / 1000.0);
	}

	/** Builds one kind for real, then counts its blocks, opens its chests and checks its frames. */
	private void build(DeepKind kind, BlockPos site, Rotation rotation, Map<String, Integer> totals) {
		DeepPiece piece;
		try {
			piece = DeepShowcase.build(abyss, kind, site, rotation);
		} catch (Throwable t) {
			test.fail("%s threw %s while building", kind.id(), t);
			for (StackTraceElement element : t.getStackTrace()) {
				test.line("    at %s", element);
			}
			return;
		}
		BlockPos center = piece.center();
		BoundingBox bounds = piece.getBoundingBox();
		Map<String, Integer> counts = new TreeMap<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
			for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
				for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
					BlockState state = abyss.getBlockState(pos.set(x, y, z));
					String what = classify(state);
					if (what != null) {
						counts.merge(what, 1, Integer::sum);
					}
					if (state.getLightEmission() > 0) {
						counts.merge("lights", 1, Integer::sum);
					}
					if (state.hasBlockEntity() && abyss.getBlockEntity(pos) instanceof RandomizableContainer container
							&& container.getLootTable() != null) {
						container.unpackLootTable(null);
						if (container instanceof Container inventory) {
							for (int i = 0; i < inventory.getContainerSize(); i++) {
								loot(inventory.getItem(i), counts);
							}
						}
					}
				}
			}
		}
		AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
		List<ItemFrame> frames = abyss.getEntitiesOfClass(ItemFrame.class, box);
		for (ItemFrame frame : frames) {
			counts.merge("frames", 1, Integer::sum);
			loot(frame.getItem(), counts);
			if (!frame.survives()) {
				test.fail("%s: an item frame at %s has nothing to hang on", kind.id(), frame.blockPosition());
			}
		}
		counts.merge("armor_stands", abyss.getEntitiesOfClass(ArmorStand.class, box).size(), Integer::sum);
		counts.merge("keeper", abyss.getEntitiesOfClass(LargeSwimmer.class, box.inflate(8.0)).size(), Integer::sum);
		counts.values().removeIf(v -> v == 0);
		test.line("%s (%s, %s) at %s: %s", kind.id(), kind.tier().name().toLowerCase(), rotation.name().toLowerCase(), center, counts);
		if (counts.getOrDefault("containers", 0) == 0) {
			test.fail("%s has nothing to loot", kind.id());
		}
		if (counts.getOrDefault("lights", 0) < 2) {
			test.fail("%s is left in the dark", kind.id());
		}
		counts.forEach((name, count) -> totals.merge(name, count, Integer::sum));
	}

	private static String classify(BlockState state) {
		if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) {
			return "containers";
		}
		if (state.is(Blocks.SPAWNER)) {
			return "spawner";
		}
		if (state.is(ModBlocks.ABYSSAL_ENCHANTING_TABLE)) {
			return "abyssal_enchanting_table";
		}
		if (state.getBlock() instanceof AbstractSkullBlock) {
			return "head";
		}
		if (state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.RAW_GOLD_BLOCK)) {
			return "gold_blocks";
		}
		if (state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE) || state.is(Blocks.DIAMOND_ORE)) {
			return "diamond_blocks";
		}
		if (state.is(Blocks.NETHERITE_BLOCK) || state.is(Blocks.ANCIENT_DEBRIS)) {
			return "netherite_blocks";
		}
		if (state.is(Blocks.BEACON) || state.is(Blocks.CONDUIT)) {
			return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		}
		return null;
	}

	/** Tallies the valuable things among the loot. */
	private static void loot(ItemStack stack, Map<String, Integer> counts) {
		if (stack.isEmpty()) {
			return;
		}
		counts.merge("items", stack.getCount(), Integer::sum);
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (stack.getItem() instanceof AbyssWeapon) {
			counts.merge("weapon", 1, Integer::sum);
		} else if (stack.is(Items.ELYTRA)) {
			counts.merge("elytra", 1, Integer::sum);
		} else if (id.startsWith("music_disc_")) {
			counts.merge("music_disc", 1, Integer::sum);
		} else if (id.endsWith("_head") || id.endsWith("_skull")) {
			counts.merge("head", stack.getCount(), Integer::sum);
		} else if (id.startsWith("netherite") || id.equals("ancient_debris")) {
			counts.merge("netherite", stack.getCount(), Integer::sum);
		} else if (id.startsWith("diamond")) {
			counts.merge("diamond", stack.getCount(), Integer::sum);
		} else if (id.equals("abyssal_enchanting_table")) {
			counts.merge("abyssal_enchanting_table", 1, Integer::sum);
		} else if (id.equals("enchanted_book")) {
			counts.merge("books", 1, Integer::sum);
		}
	}
}
