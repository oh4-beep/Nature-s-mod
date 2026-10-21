package com.naturesmod.tree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Runs small tasks a few ticks in the future on the server thread. */
public final class ServerScheduler {
	private static final List<Task> TASKS = new ArrayList<>();

	private ServerScheduler() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (TASKS.isEmpty()) {
				return;
			}
			List<Runnable> due = new ArrayList<>();
			for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
				Task task = it.next();
				if (--task.delay <= 0) {
					due.add(task.action);
					it.remove();
				}
			}
			due.forEach(Runnable::run);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TASKS.clear());
	}

	public static void schedule(int delayTicks, Runnable action) {
		TASKS.add(new Task(Math.max(1, delayTicks), action));
	}

	private static final class Task {
		private int delay;
		private final Runnable action;

		private Task(int delay, Runnable action) {
			this.delay = delay;
			this.action = action;
		}
	}
}
