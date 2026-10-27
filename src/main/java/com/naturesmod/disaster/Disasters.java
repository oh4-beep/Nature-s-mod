package com.naturesmod.disaster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.naturesmod.NaturesMod;
import com.naturesmod.tree.ServerScheduler;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Runs the natural disasters: starts them (by command or of their own accord, see
 * {@link NaturalDisasters}), ticks them with their level and keeps the players of the level informed.
 */
public final class Disasters {
	private static final Map<ResourceKey<Level>, List<Disaster>> ACTIVE = new HashMap<>();
	/** What went wrong in the disasters so far (for the tests). */
	private static final List<String> FAILURES = new ArrayList<>();
	/** Levels whose players were last told of some disaster: they need telling once more when the last one ends. */
	private static final Map<ResourceKey<Level>, Boolean> TOLD = new HashMap<>();
	/**
	 * Disasters started while the disasters of a level are being run (a volcano's tremors, a hurricane's tornadoes):
	 * they join once the round is over, as the list can't change under the loop going through it.
	 */
	private static final List<Disaster> PENDING = new ArrayList<>();
	private static boolean ticking;

	private Disasters() {
	}

	public static void register() {
		DisasterNet.register();
		DisasterSounds.register();
		DisasterParticles.register();
		ServerTickEvents.END_LEVEL_TICK.register(Disasters::tick);
		// Before the worlds are saved, every disaster under way ends and cleans up after itself (a tsunami's water still
		// flowing, an eruption's molten lava), so nothing half done is saved into the world.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PENDING.clear();
			for (List<Disaster> list : ACTIVE.values()) {
				for (Disaster disaster : List.copyOf(list)) {
					disaster.end();
					try {
						disaster.finish();
					} catch (RuntimeException e) {
						NaturesMod.LOGGER.error("The {} could not finish as the server stopped", disaster.type.id, e);
					}
				}
				list.clear();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			TOLD.clear();
			PENDING.clear();
			Disaster.clearLeftovers();
			NaturalDisasters.reset();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> DisasterCommand.register(dispatcher));
	}

	/** Starts a disaster of a kind at a place, at a strength on its scale. */
	public static Disaster start(DisasterType type, ServerLevel level, Vec3 at, float intensity, boolean natural) {
		Disaster disaster = switch (type) {
			case TORNADO -> new Tornado(level, at, intensity, natural);
			case EARTHQUAKE -> new Earthquake(level, at, intensity, natural, false);
			case METEOR_SHOWER -> new MeteorShower(level, at, intensity, natural);
			case ERUPTION -> new Eruption(level, at, intensity, natural);
			case WILDFIRE -> new Wildfire(level, at, intensity, natural);
			case SANDSTORM -> new Sandstorm(level, at, intensity, natural);
			case BLIZZARD -> new Blizzard(level, at, intensity, natural);
			case HURRICANE -> new Hurricane(level, at, intensity, natural);
			case TSUNAMI -> new Tsunami(level, at, intensity, natural);
			case SINKHOLE -> new Sinkhole(level, at, intensity, natural);
			case AVALANCHE -> new Avalanche(level, at, intensity, natural);
			case LIGHTNING_STORM -> new LightningStorm(level, at, intensity, natural);
			case ACID_RAIN -> new AcidRain(level, at, intensity, natural);
		};
		return add(disaster);
	}

	/** Sets a disaster going. Safe to call from inside another disaster's tick: it then joins at the end of the round. */
	static <T extends Disaster> T add(T disaster) {
		if (ticking) {
			PENDING.add(disaster);
		} else {
			ACTIVE.computeIfAbsent(disaster.level.dimension(), key -> new ArrayList<>()).add(disaster);
		}
		try {
			disaster.begin();
		} catch (RuntimeException e) {
			NaturesMod.LOGGER.error("The {} could not begin", disaster.type.id, e);
			FAILURES.add(disaster.type.id + " (beginning): " + e);
			disaster.end();
		}
		return disaster;
	}

	public static List<Disaster> active(ServerLevel level) {
		List<Disaster> list = ACTIVE.get(level.dimension());
		return list == null ? List.of() : List.copyOf(list);
	}

	/** Ends the disasters of a level that match; returns how many. */
	public static int stop(ServerLevel level, Predicate<Disaster> which) {
		int stopped = 0;
		for (Disaster disaster : active(level)) {
			if (which.test(disaster) && !disaster.isDone()) {
				disaster.end();
				stopped++;
			}
		}
		return stopped;
	}

	public static List<String> failures() {
		return List.copyOf(FAILURES);
	}

	/** Runs something a while from now on the server thread. */
	static void later(int ticks, Runnable action) {
		ServerScheduler.schedule(ticks, action);
	}

	private static void tick(ServerLevel level) {
		List<Disaster> list = ACTIVE.get(level.dimension());
		if (list != null && !list.isEmpty()) {
			ticking = true;
			try {
				run(list);
			} finally {
				ticking = false;
			}
		}
		if (!PENDING.isEmpty()) {
			for (Disaster disaster : List.copyOf(PENDING)) {
				ACTIVE.computeIfAbsent(disaster.level.dimension(), key -> new ArrayList<>()).add(disaster);
			}
			PENDING.clear();
			list = ACTIVE.get(level.dimension());
		}
		NaturalDisasters.tick(level);
		Disaster.tickLeftovers(level);
		if (level.getGameTime() % 2 == 0) {
			sync(level, list);
		}
	}

	private static void run(List<Disaster> list) {
		for (Iterator<Disaster> it = list.iterator(); it.hasNext(); ) {
			Disaster disaster = it.next();
			if (!disaster.isDone()) {
				try {
					disaster.tickDisaster();
				} catch (RuntimeException e) {
					NaturesMod.LOGGER.error("The {} at {} failed and was ended", disaster.type.id, disaster.position(), e);
					FAILURES.add(disaster.type.id + ": " + e);
					disaster.end();
				}
			}
			if (disaster.isDone()) {
				try {
					disaster.finish();
				} catch (RuntimeException e) {
					NaturesMod.LOGGER.error("The {} could not finish", disaster.type.id, e);
					FAILURES.add(disaster.type.id + " (finishing): " + e);
				}
				it.remove();
			}
		}
	}

	private static void sync(ServerLevel level, List<Disaster> list) {
		boolean any = list != null && !list.isEmpty();
		if (!any && !TOLD.getOrDefault(level.dimension(), false)) {
			return;
		}
		List<DisasterNet.State> states = new ArrayList<>();
		if (any) {
			for (Disaster disaster : list) {
				states.add(disaster.state());
			}
		}
		DisasterNet.Sync payload = new DisasterNet.Sync(states);
		for (ServerPlayer player : level.players()) {
			if (ServerPlayNetworking.canSend(player, DisasterNet.Sync.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
		TOLD.put(level.dimension(), any);
	}
}
