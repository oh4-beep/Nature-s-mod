package com.naturesmod.worldgen.deep;

import static com.naturesmod.worldgen.deep.Climate.BADLANDS;
import static com.naturesmod.worldgen.deep.Climate.DESERT;
import static com.naturesmod.worldgen.deep.Climate.FOREST;
import static com.naturesmod.worldgen.deep.Climate.JUNGLE;
import static com.naturesmod.worldgen.deep.Climate.MOUNTAIN;
import static com.naturesmod.worldgen.deep.Climate.PLAINS;
import static com.naturesmod.worldgen.deep.Climate.SAVANNA;
import static com.naturesmod.worldgen.deep.Climate.SNOWY;
import static com.naturesmod.worldgen.deep.Climate.SWAMP;
import static com.naturesmod.worldgen.deep.Climate.TAIGA;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Every kind of structure on the Overworld's lands and how to build it, in three tiers: easy finds
 * (camps, cabins, shrines), harder halls with keepers and traps (hideouts, tombs, temples, a
 * saloon), and the great dungeons, full of monsters and riches (fortresses, pyramids, a ziggurat, a
 * colosseum, a tower of trials). Each belongs to the climates it is built in; one with none can be
 * found anywhere.
 */
public enum LandKind {
	// Easy.
	ABANDONED_CAMP("abandoned_camp", DeepKind.Tier.RUIN, LandEasy::abandonedCamp),
	HUNTERS_CABIN("hunters_cabin", DeepKind.Tier.RUIN, LandEasy::huntersCabin, FOREST, TAIGA, SNOWY),
	WISHING_WELL("wishing_well", DeepKind.Tier.RUIN, LandEasy::wishingWell),
	STONE_CIRCLE("stone_circle", DeepKind.Tier.RUIN, LandEasy::stoneCircle, PLAINS, MOUNTAIN, TAIGA, FOREST),
	WRECKED_CART("wrecked_cart", DeepKind.Tier.RUIN, LandEasy::wreckedCart, PLAINS, SAVANNA, DESERT, BADLANDS),
	SCARECROW_FIELD("scarecrow_field", DeepKind.Tier.RUIN, LandEasy::scarecrowField, PLAINS, FOREST),
	BEEKEEPER_GROVE("beekeeper_grove", DeepKind.Tier.RUIN, LandEasy::beekeeperGrove, PLAINS, FOREST),
	OASIS("oasis", DeepKind.Tier.RUIN, LandEasy::oasis, DESERT),
	NOMAD_TENT("nomad_tent", DeepKind.Tier.RUIN, LandEasy::nomadTent, DESERT, BADLANDS, SAVANNA),
	JUNGLE_SHRINE("jungle_shrine", DeepKind.Tier.RUIN, LandEasy::jungleShrine, JUNGLE),
	BAMBOO_HUT("bamboo_hut", DeepKind.Tier.RUIN, LandEasy::bambooHut, JUNGLE),
	SNOW_FORT("snow_fort", DeepKind.Tier.RUIN, LandEasy::snowFort, SNOWY),
	FROZEN_EXPLORER("frozen_explorer", DeepKind.Tier.RUIN, LandEasy::frozenExplorer, SNOWY, MOUNTAIN),
	SAVANNA_LOOKOUT("savanna_lookout", DeepKind.Tier.RUIN, LandEasy::savannaLookout, SAVANNA),
	TERMITE_MOUND("termite_mound", DeepKind.Tier.RUIN, LandEasy::termiteMound, SAVANNA),
	MINE_ENTRANCE("mine_entrance", DeepKind.Tier.RUIN, LandEasy::mineEntrance, BADLANDS, MOUNTAIN),
	BOG_SHRINE("bog_shrine", DeepKind.Tier.RUIN, LandEasy::bogShrine, SWAMP),
	RUINED_WATCHTOWER("ruined_watchtower", DeepKind.Tier.RUIN, LandEasy::ruinedWatchtower),
	PICNIC_SPOT("picnic_spot", DeepKind.Tier.RUIN, LandEasy::picnicSpot, PLAINS, FOREST),
	TOTEM_POLE("totem_pole", DeepKind.Tier.RUIN, LandEasy::totemPole, TAIGA, SNOWY),
	// Medium.
	BANDIT_HIDEOUT("bandit_hideout", DeepKind.Tier.VAULT, LandMedium::banditHideout, FOREST, TAIGA),
	WITCH_TOWER("witch_tower", DeepKind.Tier.VAULT, LandMedium::witchTower, SWAMP),
	HAUNTED_GRAVEYARD("haunted_graveyard", DeepKind.Tier.VAULT, LandMedium::hauntedGraveyard, PLAINS, FOREST, TAIGA),
	DESERT_BAZAAR("desert_bazaar", DeepKind.Tier.VAULT, LandMedium::desertBazaar, DESERT),
	SANDSTONE_TOMB("sandstone_tomb", DeepKind.Tier.VAULT, LandMedium::sandstoneTomb, DESERT),
	OVERGROWN_TEMPLE("overgrown_temple", DeepKind.Tier.VAULT, LandMedium::overgrownTemple, JUNGLE),
	RANGER_OUTPOST("ranger_outpost", DeepKind.Tier.VAULT, LandMedium::rangerOutpost, TAIGA, SNOWY),
	MOUNTAIN_OBSERVATORY("mountain_observatory", DeepKind.Tier.VAULT, LandMedium::mountainObservatory, MOUNTAIN),
	FARMSTEAD("farmstead", DeepKind.Tier.VAULT, LandMedium::farmstead, PLAINS),
	LUMBER_MILL("lumber_mill", DeepKind.Tier.VAULT, LandMedium::lumberMill, FOREST, TAIGA),
	MAMMOTH_GRAVEYARD("mammoth_graveyard", DeepKind.Tier.VAULT, LandMedium::mammothGraveyard, SNOWY),
	SAFARI_CAMP("safari_camp", DeepKind.Tier.VAULT, LandMedium::safariCamp, SAVANNA),
	OUTLAW_SALOON("outlaw_saloon", DeepKind.Tier.VAULT, LandMedium::outlawSaloon, BADLANDS, DESERT),
	MONKEY_TEMPLE("monkey_temple", DeepKind.Tier.VAULT, LandMedium::monkeyTemple, JUNGLE),
	BEAST_DEN("beast_den", DeepKind.Tier.VAULT, LandMedium::beastDen, SAVANNA, FOREST, TAIGA),
	FORGOTTEN_LIBRARY("forgotten_library", DeepKind.Tier.VAULT, LandMedium::forgottenLibrary),
	WINDMILL("windmill", DeepKind.Tier.VAULT, LandMedium::windmill, PLAINS),
	DWARVEN_FORGE("dwarven_forge", DeepKind.Tier.VAULT, LandMedium::dwarvenForge, MOUNTAIN, BADLANDS),
	// Hard.
	WARLORD_FORTRESS("warlord_fortress", DeepKind.Tier.WONDER, LandHard::warlordFortress, PLAINS, FOREST, TAIGA),
	NECROMANCER_CRYPT("necromancer_crypt", DeepKind.Tier.WONDER, LandHard::necromancerCrypt),
	LOST_PYRAMID("lost_pyramid", DeepKind.Tier.WONDER, LandHard::lostPyramid, DESERT),
	JUNGLE_ZIGGURAT("jungle_ziggurat", DeepKind.Tier.WONDER, LandHard::jungleZiggurat, JUNGLE),
	FROST_CITADEL("frost_citadel", DeepKind.Tier.WONDER, LandHard::frostCitadel, SNOWY, MOUNTAIN),
	HAUNTED_MANOR("haunted_manor", DeepKind.Tier.WONDER, LandHard::hauntedManor, FOREST, SWAMP),
	OUTLAW_FORTRESS("outlaw_fortress", DeepKind.Tier.WONDER, LandHard::outlawFortress, BADLANDS),
	COLOSSEUM("colosseum", DeepKind.Tier.WONDER, LandHard::colosseum, SAVANNA, PLAINS),
	TRIAL_TOWER("trial_tower", DeepKind.Tier.WONDER, LandHard::trialTower),
	DEEP_MINE("deep_mine", DeepKind.Tier.WONDER, LandHard::deepMine, MOUNTAIN, BADLANDS),
	ELDER_TREEHOUSE("elder_treehouse", DeepKind.Tier.WONDER, LandHard::elderTreehouse, FOREST, JUNGLE),
	BLAZE_SPIRE("blaze_spire", DeepKind.Tier.WONDER, LandHard::blazeSpire, DESERT, BADLANDS, SAVANNA);

	private final String id;
	private final DeepKind.Tier tier;
	private final Consumer<DeepBuilder> builder;
	private final Set<Climate> climates;

	LandKind(String id, DeepKind.Tier tier, Consumer<DeepBuilder> builder, Climate... climates) {
		this.id = id;
		this.tier = tier;
		this.builder = builder;
		this.climates = climates.length == 0 ? EnumSet.noneOf(Climate.class) : EnumSet.of(climates[0], climates);
	}

	public String id() {
		return id;
	}

	public DeepKind.Tier tier() {
		return tier;
	}

	/** Whether it can be found anywhere, rather than in the climates it belongs to. */
	public boolean anywhere() {
		return climates.isEmpty();
	}

	public Set<Climate> climates() {
		return climates;
	}

	/** The box it is built in: as wide as a treasure of the deep of its size, and taller and deeper for the towers and crypts. */
	public BoundingBox bounds(BlockPos center) {
		return bounds(tier, center);
	}

	/** The box any kind of a tier is built in. */
	public static BoundingBox bounds(DeepKind.Tier tier, BlockPos center) {
		BoundingBox box = tier.bounds(center);
		int above = tier == DeepKind.Tier.WONDER ? 40 : 24;
		return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY() + above, box.maxZ());
	}

	void build(DeepBuilder b) {
		builder.accept(b);
	}

	/** A kind of the tier for a site: mostly one that belongs to the climate, now and then one found anywhere. */
	static LandKind pick(DeepKind.Tier tier, Climate climate, RandomSource random) {
		List<LandKind> fit = new ArrayList<>();
		List<LandKind> anywhere = new ArrayList<>();
		for (LandKind kind : values()) {
			if (kind.tier == tier) {
				if (kind.climates.contains(climate)) {
					fit.add(kind);
				} else if (kind.anywhere()) {
					anywhere.add(kind);
				}
			}
		}
		int roll = random.nextInt(3);
		if (!fit.isEmpty() && (anywhere.isEmpty() || roll != 0)) {
			return fit.get(random.nextInt(fit.size()));
		}
		if (!anywhere.isEmpty()) {
			return anywhere.get(random.nextInt(anywhere.size()));
		}
		List<LandKind> all = new ArrayList<>();
		for (LandKind kind : values()) {
			if (kind.tier == tier) {
				all.add(kind);
			}
		}
		return all.get(random.nextInt(all.size()));
	}

	public static LandKind byId(String id) {
		for (LandKind kind : values()) {
			if (kind.id.equals(id)) {
				return kind;
			}
		}
		return ABANDONED_CAMP;
	}
}
