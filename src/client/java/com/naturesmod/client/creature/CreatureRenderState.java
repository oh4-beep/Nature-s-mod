package com.naturesmod.client.creature;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the sea creature renderer needs to know about one creature in one frame. */
public class CreatureRenderState extends LivingEntityRenderState {
	/** Snapping at its prey: opens the jaws. */
	public boolean attacking;
	/** Jellyfish: ticks since the bell last pulsed. */
	public float pulse = 100.0F;
}
