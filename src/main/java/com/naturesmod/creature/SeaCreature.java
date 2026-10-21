package com.naturesmod.creature;

/** Shared by every Nature's Mod sea creature entity, whatever it is built on. */
public interface SeaCreature {
	SpeciesData species();

	/** True while the creature is snapping at a target: drives the jaw animation. */
	boolean isAttacking();
}
