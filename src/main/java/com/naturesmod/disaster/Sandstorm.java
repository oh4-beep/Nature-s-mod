package com.naturesmod.disaster;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A haboob: a sandstorm driven by the outflow of a distant storm. A wall of dust a hundred blocks and
 * more high rolls across the desert, and behind it the air is so thick with sand you can barely see
 * your hand; the wind shoves at everything for minutes on end. Before it, on the hot ground, dust
 * devils spin up and wander about.
 */
public final class Sandstorm extends Disaster {
	private static final double START = -260.0;

	private final Vec3 wind;
	private final double speed;
	private final double body;
	private final double width;
	private final double visibility;
	private final List<Devil> devils = new ArrayList<>();

	private static final class Devil {
		double x;
		double z;
		double heading;
		final double height;
		int life;

		Devil(double x, double z, double heading, double height, int life) {
			this.x = x;
			this.z = z;
			this.heading = heading;
			this.height = height;
			this.life = life;
		}
	}

	Sandstorm(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.SANDSTORM, level, at, intensity, natural, 0);
		double angle = random.nextDouble() * Math.PI * 2.0;
		wind = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		speed = 0.35 + 0.08 * intensity;
		body = 250.0 + 80.0 * intensity;
		width = 520.0;
		visibility = 28.0 - 4.0 * intensity;
		duration = (int) ((-START + body + 200.0) / speed);
		y = ground((int) Math.floor(x), (int) Math.floor(z)) + 1;
		int count = 2 + random.nextInt(3);
		for (int i = 0; i < count; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double d = 20.0 + random.nextDouble() * 120.0;
			devils.add(new Devil(x + Math.cos(a) * d, z + Math.sin(a) * d, random.nextDouble() * Math.PI * 2.0, 14.0 + random.nextDouble() * 18.0,
					300 + random.nextInt(500)));
		}
	}

	/** Where the wall is, in blocks along the wind from the middle of the storm. */
	private double front() {
		return START + speed * age;
	}

	/** How deep inside the dust a point is: 0 outside, 1 well inside. */
	public double inside(double px, double pz) {
		double along = (px - x) * wind.x + (pz - z) * wind.z;
		double across = Math.abs(-(px - x) * wind.z + (pz - z) * wind.x);
		double front = front();
		if (along > front || along < front - body || across > width / 2.0) {
			return 0.0;
		}
		double edge = Math.min(Math.min(front - along, along - (front - body)), width / 2.0 - across);
		return Math.min(1.0, edge / 40.0);
	}

	@Override
	public float radius() {
		return (float) (width / 2.0 + body);
	}

	@Override
	public Vec3 direction() {
		return wind;
	}

	@Override
	public float strength() {
		float in = Math.min(1.0F, age / 60.0F);
		float out = Math.min(1.0F, (duration - age) / 300.0F);
		return Math.max(0.0F, Math.min(in, out));
	}

	@Override
	public float[] extra() {
		float[] out = new float[7 + devils.size() * 3];
		out[0] = (float) front();
		out[1] = (float) body;
		out[2] = (float) width;
		out[3] = (float) visibility;
		out[4] = (float) speed;
		out[5] = (float) y;
		out[6] = devils.size();
		for (int i = 0; i < devils.size(); i++) {
			Devil devil = devils.get(i);
			out[7 + i * 3] = (float) devil.x;
			out[8 + i * 3] = (float) devil.z;
			out[9 + i * 3] = (float) devil.height;
		}
		return out;
	}

	@Override
	protected void begin() {
		warn(500, player -> Component.translatable("naturesmod.warning.sandstorm", compass(-wind.x, -wind.z)));
	}

	@Override
	protected void tick() {
		blow();
		devils.removeIf(devil -> --devil.life <= 0 || front() > 0.0);
		for (Devil devil : devils) {
			devil.heading += random.nextGaussian() * 0.08;
			devil.x += Math.cos(devil.heading) * 0.12;
			devil.z += Math.sin(devil.heading) * 0.12;
			whirl(devil);
		}
	}

	/** The wind inside the storm shoves at everything, harder at what has nothing to hold on to. */
	private void blow() {
		double push = 0.012 + 0.006 * intensity;
		for (Player player : level.players()) {
			if (immune(player)) {
				continue;
			}
			double in = inside(player.getX(), player.getZ());
			if (in > 0.0 && !sheltered(player)) {
				player.push(wind.x * push * in, 0.0, wind.z * push * in);
			}
		}
		if (age % 4 == 0) {
			for (Player player : level.players()) {
				if (inside(player.getX(), player.getZ()) <= 0.0) {
					continue;
				}
				for (Entity entity : level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(48.0), e -> !(e instanceof Player))) {
					double in = inside(entity.getX(), entity.getZ());
					double strength = entity instanceof ItemEntity ? 0.08 : 0.03;
					entity.push(wind.x * strength * in, entity instanceof ItemEntity ? 0.02 * in : 0.0, wind.z * strength * in);
				}
			}
		}
	}

	/** A dust devil lifts dust and anything light around it. */
	private void whirl(Devil devil) {
		AABB box = new AABB(devil.x - 4, y - 3, devil.z - 4, devil.x + 4, y + devil.height, devil.z + 4);
		for (Entity entity : level.getEntitiesOfClass(Entity.class, box, e -> !immune(e))) {
			double dx = entity.getX() - devil.x;
			double dz = entity.getZ() - devil.z;
			Vec3 air = Vortex.air(dx, dz, Math.max(0.0, entity.getY() - y), 1.8, 0.35, devil.height);
			double response = entity instanceof ItemEntity ? 0.3 : 0.06;
			entity.push(air.x * response, air.y * response, air.z * response);
		}
	}
}
