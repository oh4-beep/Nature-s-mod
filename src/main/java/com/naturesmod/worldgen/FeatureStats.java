package com.naturesmod.worldgen;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Counts how often each Nature's Mod feature was placed. Read by the worldgen smoke test. */
public final class FeatureStats {
	private static final Map<String, AtomicInteger> PLACED = new ConcurrentHashMap<>();

	private FeatureStats() {
	}

	public static void placed(String feature) {
		PLACED.computeIfAbsent(feature, key -> new AtomicInteger()).incrementAndGet();
	}

	public static Map<String, Integer> snapshot() {
		Map<String, Integer> copy = new TreeMap<>();
		PLACED.forEach((key, value) -> copy.put(key, value.get()));
		return copy;
	}
}
