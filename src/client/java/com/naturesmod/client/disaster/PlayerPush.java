package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterNet;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** A disaster's push on the player: applied to how fast the player is really going, before the player's game moves it. */
final class PlayerPush {
	private PlayerPush() {
	}

	static void apply(LocalPlayer player, DisasterNet.Push push) {
		DisasterNet.pushesReceived++;
		if (player == null || player.isSpectator() || player.getAbilities().flying) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		double vx = v.x;
		double vy = v.y;
		double vz = v.z;
		switch (push.mode()) {
			case DisasterNet.Push.SET -> {
				vx = push.x();
				vy = push.y();
				vz = push.z();
			}
			case DisasterNet.Push.ADD -> {
				vx += push.x();
				vy += push.y();
				vz += push.z();
			}
			case DisasterNet.Push.CARRY -> {
				vx = push.x() + (push.goalX() - player.getX()) * push.rate();
				vy = push.y() + (push.goalY() - player.getY()) * push.rate();
				vz = push.z() + (push.goalZ() - player.getZ()) * push.rate();
			}
			case DisasterNet.Push.TOWARD -> {
				vx += (push.x() - vx) * push.rate();
				vz += (push.z() - vz) * push.rate();
				if (!player.onGround() && push.y() > vy) {
					vy += (push.y() - vy) * push.lift();
				}
			}
			default -> {
				return;
			}
		}
		double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (push.max() > 0.0F && speed > push.max()) {
			double k = push.max() / speed;
			vx *= k;
			vy *= k;
			vz *= k;
		}
		player.setDeltaMovement(vx, vy, vz);
		if ((push.mode() == DisasterNet.Push.SET || push.mode() == DisasterNet.Push.CARRY) && vy > 0.0) {
			// Off the ground with it: else a player standing still would only slide along it.
			player.setOnGround(false);
		}
	}
}
