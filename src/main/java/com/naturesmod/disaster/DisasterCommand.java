package com.naturesmod.disaster;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * /disaster &lt;kind&gt; [strength] [where]: calls up a disaster (a tornado forms out ahead of you and comes
 * your way, a fire breaks out in front of you, anything else strikes where you stand or where you say);
 * /disaster list; /disaster stop.
 */
final class DisasterCommand {
	private DisasterCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("disaster").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		for (DisasterType type : DisasterType.values()) {
			root.then(Commands.literal(type.id)
					.executes(c -> start(c.getSource(), type, type.normal, null))
					.then(Commands.argument("strength", FloatArgumentType.floatArg(type.min, type.max))
							.executes(c -> start(c.getSource(), type, FloatArgumentType.getFloat(c, "strength"), null))
							.then(Commands.argument("where", Vec3Argument.vec3())
									.executes(c -> start(c.getSource(), type, FloatArgumentType.getFloat(c, "strength"), Vec3Argument.getVec3(c, "where"))))));
		}
		root.then(Commands.literal("list").executes(c -> list(c.getSource())));
		root.then(Commands.literal("stop").executes(c -> {
			int stopped = Disasters.stop(c.getSource().getLevel(), disaster -> true);
			c.getSource().sendSuccess(() -> Component.translatable("commands.naturesmod.disaster.stopped", stopped), true);
			return stopped;
		}));
		dispatcher.register(root);
	}

	private static int start(CommandSourceStack source, DisasterType type, float strength, Vec3 where) {
		ServerLevel level = source.getLevel();
		Vec3 from = source.getPosition();
		Entity entity = source.getEntity();
		Vec3 at = where;
		if (at == null) {
			Vec3 look = entity != null ? entity.getLookAngle() : new Vec3(0, 0, 1);
			Vec3 ahead = new Vec3(look.x, 0, look.z);
			ahead = ahead.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : ahead.normalize();
			double distance = switch (type) {
				case TORNADO -> 64.0;
				case WILDFIRE -> 14.0;
				default -> 0.0;
			};
			at = from.add(ahead.scale(distance));
		}
		Disaster disaster = Disasters.start(type, level, at, strength, false);
		if (disaster instanceof Tornado tornado && where == null) {
			tornado.aim(from);
		}
		Vec3 pos = disaster.position();
		double dx = pos.x - from.x;
		double dz = pos.z - from.z;
		int distance = (int) Math.sqrt(dx * dx + dz * dz);
		Component name = Component.translatable(type.translationKey());
		if (distance < 4) {
			source.sendSuccess(() -> Component.translatable("commands.naturesmod.disaster.started_here", name, type.scale(disaster.intensity)), true);
		} else {
			source.sendSuccess(() -> Component.translatable("commands.naturesmod.disaster.started", name, type.scale(disaster.intensity), distance,
					Disaster.compass(dx, dz)), true);
		}
		return 1;
	}

	private static int list(CommandSourceStack source) {
		List<Disaster> active = Disasters.active(source.getLevel());
		if (active.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("commands.naturesmod.disaster.none"), false);
			return 0;
		}
		for (Disaster disaster : active) {
			source.sendSuccess(() -> Component.literal(disaster.describe()), false);
		}
		return active.size();
	}
}
