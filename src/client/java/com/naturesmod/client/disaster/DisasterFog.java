package com.naturesmod.client.disaster;

import java.lang.reflect.Field;
import java.util.List;

import com.naturesmod.NaturesMod;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The air of a disaster: thick with dust in a sandstorm, white in a blizzard, dark under an ash cloud,
 * brown with smoke downwind of a fire. Out in the open air it takes over from the game's own fog,
 * drawing the fog in to the distance one can still see and tinting it (the sky too, which fades into
 * the fog): in the worst of a whiteout or a haboob, a few blocks.
 */
final class DisasterFog extends AtmosphericFogEnvironment {
	private final Vector3f color = new Vector3f();

	/** Puts it in the game's list of fog environments, just before the open air's own. */
	@SuppressWarnings("unchecked")
	static void install() {
		try {
			Field field = FogRenderer.class.getDeclaredField("FOG_ENVIRONMENTS");
			field.setAccessible(true);
			List<FogEnvironment> environments = (List<FogEnvironment>) field.get(null);
			int index = environments.size();
			for (int i = 0; i < environments.size(); i++) {
				if (environments.get(i) instanceof AtmosphericFogEnvironment) {
					index = i;
					break;
				}
			}
			environments.add(index, new DisasterFog());
		} catch (ReflectiveOperationException | RuntimeException e) {
			NaturesMod.LOGGER.warn("Could not add the disasters' fog; sandstorms and blizzards will be clear", e);
		}
	}

	@Override
	public boolean isApplicable(FogType fogType, Entity entity) {
		return fogType == FogType.ATMOSPHERIC && DisasterClient.fogAmount() > 0.002F;
	}

	@Override
	public Vector3fc getBaseColor(ClientLevel level, Camera camera, int renderDistance, float partialTicks) {
		Vector3fc base = super.getBaseColor(level, camera, renderDistance, partialTicks);
		float amount = DisasterClient.fogAmount();
		// Disaster air is lit by the same sky: dim it as the day dims.
		float light = Math.max(0.15F, Math.max(base.x(), Math.max(base.y(), base.z())) * 1.15F);
		light = Math.min(1.0F, light);
		color.set(base.x() + (DisasterClient.fogRed() * light - base.x()) * amount, base.y() + (DisasterClient.fogGreen() * light - base.y()) * amount,
				base.z() + (DisasterClient.fogBlue() * light - base.z()) * amount);
		return color;
	}

	@Override
	public void setupFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker) {
		super.setupFog(fog, camera, level, renderDistance, deltaTracker);
		float amount = DisasterClient.fogAmount();
		float visibility = DisasterClient.fogVisibility();
		float end = fog.environmentalEnd + (visibility - fog.environmentalEnd) * amount;
		float start = fog.environmentalStart + (-visibility * 0.2F - fog.environmentalStart) * amount;
		fog.environmentalEnd = Math.min(fog.environmentalEnd, end);
		fog.environmentalStart = Math.min(fog.environmentalStart, start);
		fog.skyEnd = Math.min(fog.skyEnd, fog.skyEnd + (visibility * 1.5F - fog.skyEnd) * amount);
		fog.cloudEnd = Math.min(fog.cloudEnd, fog.cloudEnd + (visibility * 2.0F - fog.cloudEnd) * amount);
	}
}
