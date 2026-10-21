package com.naturesmod.creature;

/** How a sea creature lives: what it does all day and how it treats a diver. */
public enum Behavior {
	/** Minds its own business and flees from danger. */
	PASSIVE,
	/** Swims in schools that scatter when something big comes close. */
	SCHOOL,
	/** Rests and grubs about on the sea floor. */
	BOTTOM,
	/** Drifts with the water; may sting what it touches. */
	DRIFTER,
	/** Peaceful until provoked, then fights back hard. Hunts smaller fish. */
	NEUTRAL,
	/** Lurks motionless and lunges at anything that strays too close. */
	AMBUSH,
	/** Hunts fish and divers. Smells blood from far away. */
	HUNTER,
	/** A school that attacks together. */
	PACK,
	/** The terror of the deep. */
	BOSS;

	/** Attacks divers unprovoked. */
	public boolean isHostile() {
		return this == AMBUSH || this == HUNTER || this == PACK || this == BOSS;
	}

	/** Fights back when attacked instead of fleeing. */
	public boolean fightsBack() {
		return isHostile() || this == NEUTRAL;
	}
}
