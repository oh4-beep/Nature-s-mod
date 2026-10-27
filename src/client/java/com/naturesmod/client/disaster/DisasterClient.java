package com.naturesmod.client.disaster;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.naturesmod.NaturesMod;
import com.naturesmod.disaster.DisasterNet;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * The disasters on this client: kept up to date by the server, drawn and sounded tick by tick (see
 * the effects of each kind), and the air they leave (the fog the player is in) worked out.
 */
public final class DisasterClient {
	private static final Map<Integer, ClientDisaster> DISASTERS = new LinkedHashMap<>();
	private static ClientLevel lastLevel;
	private static float fogAmount;
	private static float fogRed = 0.7F;
	private static float fogGreen = 0.7F;
	private static float fogBlue = 0.7F;
	private static float fogVisibility = 256.0F;
	private static float wantAmount;
	private static float wantRed;
	private static float wantGreen;
	private static float wantBlue;
	private static float wantVisibility;

	private DisasterClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DisasterNet.Sync.TYPE, (payload, context) -> sync(payload));
		ClientPlayNetworking.registerGlobalReceiver(DisasterNet.Event.TYPE, (payload, context) -> DisasterEvents.handle(payload));
		ClientTickEvents.END_CLIENT_TICK.register(DisasterClient::tick);
		DisasterParticle.registerProviders();
		DisasterFog.install();
	}

	static ClientDisaster get(int id) {
		return DISASTERS.get(id);
	}

	static Collection<ClientDisaster> all() {
		return DISASTERS.values();
	}

	private static void sync(DisasterNet.Sync payload) {
		Set<Integer> present = new HashSet<>();
		for (DisasterNet.State state : payload.states()) {
			present.add(state.id());
			ClientDisaster known = DISASTERS.get(state.id());
			if (known == null) {
				DISASTERS.put(state.id(), new ClientDisaster(state));
			} else {
				known.update(state);
			}
		}
		DISASTERS.values().removeIf(disaster -> {
			if (!present.contains(disaster.id)) {
				disaster.removed = true;
				return true;
			}
			return false;
		});
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null) {
			if (lastLevel != null) {
				clear();
				lastLevel = null;
			}
			return;
		}
		if (level != lastLevel) {
			clear();
			lastLevel = level;
		}
		if (mc.isPaused()) {
			return;
		}
		wantAmount = 0.0F;
		wantVisibility = 1024.0F;
		if (Fx.ready()) {
			Fx.begin(mc);
			for (ClientDisaster disaster : DISASTERS.values()) {
				disaster.tick();
				try {
					switch (disaster.type) {
						case TORNADO -> TornadoFx.tick(disaster);
						case EARTHQUAKE -> QuakeFx.tick(disaster);
						case METEOR_SHOWER -> SkyFx.tick(disaster);
						case ERUPTION -> VolcanoFx.tick(disaster);
						case WILDFIRE -> FireFx.tick(disaster);
						case SANDSTORM -> DustFx.tick(disaster);
						case BLIZZARD -> SnowFx.tick(disaster);
						case HURRICANE -> HurricaneFx.tick(disaster);
						case TSUNAMI -> TsunamiFx.tick(disaster);
						case SINKHOLE -> SinkholeFx.tick(disaster);
						case AVALANCHE -> AvalancheFx.tick(disaster);
						case LIGHTNING_STORM -> LightningFx.tick(disaster);
						case ACID_RAIN -> AcidRainFx.tick(disaster);
					}
				} catch (RuntimeException e) {
					NaturesMod.LOGGER.error("Drawing the {} failed", disaster.type.id, e);
				}
			}
			DisasterEvents.tick();
		}
		// The air changes over a second or two, not at once.
		float rate = 0.05F;
		fogAmount += (wantAmount - fogAmount) * rate;
		if (wantAmount > 0.0F) {
			fogRed += (wantRed - fogRed) * rate * 2.0F;
			fogGreen += (wantGreen - fogGreen) * rate * 2.0F;
			fogBlue += (wantBlue - fogBlue) * rate * 2.0F;
			fogVisibility += (wantVisibility - fogVisibility) * rate * 2.0F;
		}
		CameraShake.tick(player);
		DisasterAudio.tick();
	}

	private static void clear() {
		DISASTERS.clear();
		DisasterEvents.clear();
		DisasterAudio.stopAll();
		CameraShake.reset();
		fogAmount = 0.0F;
	}

	/** An effect would have the air this thick (0 to 1), of this colour, seeing this far: the thickest wins. */
	static void fog(float amount, float red, float green, float blue, float visibility) {
		if (amount > wantAmount) {
			wantAmount = Math.min(1.0F, amount);
			wantRed = red;
			wantGreen = green;
			wantBlue = blue;
			wantVisibility = visibility;
		}
	}

	static float fogAmount() {
		return fogAmount;
	}

	static float fogRed() {
		return fogRed;
	}

	static float fogGreen() {
		return fogGreen;
	}

	static float fogBlue() {
		return fogBlue;
	}

	static float fogVisibility() {
		return fogVisibility;
	}
}
