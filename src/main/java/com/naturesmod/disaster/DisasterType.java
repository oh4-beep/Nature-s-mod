package com.naturesmod.disaster;

import java.util.Locale;

/** The kinds of natural disaster, each with the scale its strength is measured on. */
public enum DisasterType {
	/** Strength on the Enhanced Fujita scale, EF0 to EF5. */
	TORNADO("tornado", 0.0F, 5.0F, 3.0F),
	/** Strength as a moment magnitude. */
	EARTHQUAKE("earthquake", 3.0F, 9.5F, 6.5F),
	/** Strength from 1 (a few fireballs) to 5 (a storm of them). */
	METEOR_SHOWER("meteor_shower", 1.0F, 5.0F, 3.0F),
	/** Strength on the volcanic explosivity index. */
	ERUPTION("eruption", 2.0F, 7.0F, 4.0F),
	WILDFIRE("wildfire", 1.0F, 5.0F, 3.0F),
	SANDSTORM("sandstorm", 1.0F, 5.0F, 3.0F),
	BLIZZARD("blizzard", 1.0F, 5.0F, 3.0F);

	public final String id;
	public final float min;
	public final float max;
	/** The strength a disaster called up without one is given. */
	public final float normal;

	DisasterType(String id, float min, float max, float normal) {
		this.id = id;
		this.min = min;
		this.max = max;
		this.normal = normal;
	}

	public float clamp(float strength) {
		return Math.max(min, Math.min(max, strength));
	}

	/** The strength the way it is written: EF3, M6.8, VEI 4, level 3. */
	public String scale(float strength) {
		return switch (this) {
			case TORNADO -> "EF" + Math.round(strength);
			case EARTHQUAKE -> String.format(Locale.ROOT, "M%.1f", strength);
			case ERUPTION -> "VEI " + Math.round(strength);
			default -> "level " + Math.round(strength);
		};
	}

	public String translationKey() {
		return "disaster.naturesmod." + id;
	}

	public static DisasterType byId(String id) {
		for (DisasterType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}
}
