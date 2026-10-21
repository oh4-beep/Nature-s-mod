package com.naturesmod.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.BlockPos;

/** Counts how often each Nature's Mod feature was placed. Read by the worldgen smoke test. */
public final class FeatureStats {
	private static final int MAX_LOCATIONS = 8;
	private static final Map<String, AtomicInteger> PLACED = new ConcurrentHashMap<>();
	private static final Map<String, List<BlockPos>> LOCATIONS = new ConcurrentHashMap<>();

	private FeatureStats() {
	}

	public static void placed(String feature) {
		PLACED.computeIfAbsent(feature, key -> new AtomicInteger()).incrementAndGet();
	}

	/** Counts a placement and remembers where it happened (the first few of each kind). */
	public static void placed(String feature, BlockPos pos) {
		placed(feature);
		List<BlockPos> list = LOCATIONS.computeIfAbsent(feature, key -> new ArrayList<>());
		synchronized (list) {
			if (list.size() < MAX_LOCATIONS) {
				list.add(pos.immutable());
			}
		}
	}

	public static Map<String, Integer> snapshot() {
		Map<String, Integer> copy = new TreeMap<>();
		PLACED.forEach((key, value) -> copy.put(key, value.get()));
		return copy;
	}

	public static List<BlockPos> locations(String feature) {
		List<BlockPos> list = LOCATIONS.getOrDefault(feature, List.of());
		synchronized (list) {
			return List.copyOf(list);
		}
	}
}
