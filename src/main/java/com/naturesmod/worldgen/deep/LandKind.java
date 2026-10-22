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
	BLAZE_SPIRE("blaze_spire", DeepKind.Tier.WONDER, LandHard::blazeSpire, DESERT, BADLANDS, SAVANNA),
	// The second wave.
	FISHING_HUT("fishing_hut", DeepKind.Tier.RUIN, LandEasy2::fishingHut, PLAINS, FOREST, SWAMP, TAIGA),
	SHEPHERDS_HUT("shepherds_hut", DeepKind.Tier.RUIN, LandEasy2::shepherdsHut, PLAINS, MOUNTAIN),
	FALLEN_METEOR("fallen_meteor", DeepKind.Tier.RUIN, LandEasy2::fallenMeteor),
	MUSHROOM_RING("mushroom_ring", DeepKind.Tier.RUIN, LandEasy2::mushroomRing, FOREST, SWAMP),
	ROADSIDE_SHRINE("roadside_shrine", DeepKind.Tier.RUIN, LandEasy2::roadsideShrine),
	HERMIT_CAVE("hermit_cave", DeepKind.Tier.RUIN, LandEasy2::hermitCave, MOUNTAIN, TAIGA, BADLANDS),
	CACTUS_GARDEN("cactus_garden", DeepKind.Tier.RUIN, LandEasy2::cactusGarden, DESERT, BADLANDS),
	SAND_RUINS("sand_ruins", DeepKind.Tier.RUIN, LandEasy2::sandRuins, DESERT),
	TREE_PLATFORM("tree_platform", DeepKind.Tier.RUIN, LandEasy2::treePlatform, FOREST, JUNGLE),
	IGLOOS("igloos", DeepKind.Tier.RUIN, LandEasy2::igloos, SNOWY),
	ICE_FISHING_CAMP("ice_fishing_camp", DeepKind.Tier.RUIN, LandEasy2::iceFishingCamp, SNOWY, TAIGA),
	PROSPECTOR_CAMP("prospector_camp", DeepKind.Tier.RUIN, LandEasy2::prospectorCamp, BADLANDS, MOUNTAIN),
	BAOBAB("baobab", DeepKind.Tier.RUIN, LandEasy2::baobab, SAVANNA),
	STILT_HUT("stilt_hut", DeepKind.Tier.RUIN, LandEasy2::stiltHut, SWAMP, JUNGLE),
	GAZEBO("gazebo", DeepKind.Tier.RUIN, LandEasy2::gazebo, PLAINS, FOREST),
	WAGON_CIRCLE("wagon_circle", DeepKind.Tier.RUIN, LandEasy2::wagonCircle, PLAINS, SAVANNA),
	ARCHERY_RANGE("archery_range", DeepKind.Tier.RUIN, LandEasy2::archeryRange, PLAINS, FOREST),
	TRAPPER_LODGE("trapper_lodge", DeepKind.Tier.RUIN, LandEasy2::trapperLodge, TAIGA, SNOWY),
	FOSSIL("fossil", DeepKind.Tier.RUIN, LandEasy2::fossil, DESERT, BADLANDS, SAVANNA),
	FAIRY_RING("fairy_ring", DeepKind.Tier.RUIN, LandEasy2::fairyRing, FOREST, PLAINS),
	SMUGGLERS_COVE("smugglers_cove", DeepKind.Tier.VAULT, LandMedium2::smugglersCove),
	ALCHEMIST_TOWER("alchemist_tower", DeepKind.Tier.VAULT, LandMedium2::alchemistTower, FOREST, SWAMP, PLAINS),
	KNIGHTS_CAMP("knights_camp", DeepKind.Tier.VAULT, LandMedium2::knightsCamp, PLAINS),
	ABANDONED_SQUARE("abandoned_square", DeepKind.Tier.VAULT, LandMedium2::abandonedSquare, PLAINS, SAVANNA, FOREST),
	SCORPION_NEST("scorpion_nest", DeepKind.Tier.VAULT, LandMedium2::scorpionNest, DESERT, BADLANDS),
	TREE_VILLAGE("tree_village", DeepKind.Tier.VAULT, LandMedium2::treeVillage, JUNGLE, FOREST),
	LEMUR_RUINS("lemur_ruins", DeepKind.Tier.VAULT, LandMedium2::lemurRuins, JUNGLE),
	HUNTING_LODGE("hunting_lodge", DeepKind.Tier.VAULT, LandMedium2::huntingLodge, TAIGA, FOREST),
	FROZEN_LAB("frozen_lab", DeepKind.Tier.VAULT, LandMedium2::frozenLab, SNOWY),
	MONASTERY("monastery", DeepKind.Tier.VAULT, LandMedium2::monastery, MOUNTAIN, TAIGA),
	MESA_BANK("mesa_bank", DeepKind.Tier.VAULT, LandMedium2::mesaBank, BADLANDS),
	KRAAL("kraal", DeepKind.Tier.VAULT, LandMedium2::kraal, SAVANNA),
	SPIDER_DEN("spider_den", DeepKind.Tier.VAULT, LandMedium2::spiderDen, FOREST, JUNGLE, SWAMP),
	SUNKEN_GARDEN("sunken_garden", DeepKind.Tier.VAULT, LandMedium2::sunkenGarden, PLAINS, FOREST, SWAMP),
	CASTLE_GATE("castle_gate", DeepKind.Tier.VAULT, LandMedium2::castleGate, PLAINS, MOUNTAIN),
	BEAVER_DAM("beaver_dam", DeepKind.Tier.VAULT, LandMedium2::beaverDam, TAIGA, FOREST),
	HONEY_FARM("honey_farm", DeepKind.Tier.VAULT, LandMedium2::honeyFarm, PLAINS, FOREST),
	BARROW("barrow", DeepKind.Tier.VAULT, LandMedium2::barrow, PLAINS, TAIGA, MOUNTAIN),
	DRAGON_BONES("dragon_bones", DeepKind.Tier.WONDER, LandHard2::dragonBones, MOUNTAIN, BADLANDS, SNOWY),
	SKY_CASTLE("sky_castle", DeepKind.Tier.WONDER, LandHard2::skyCastle, PLAINS, MOUNTAIN, FOREST),
	VOLCANO("volcano", DeepKind.Tier.WONDER, LandHard2::volcano, BADLANDS, SAVANNA, MOUNTAIN),
	WITCH_COVEN("witch_coven", DeepKind.Tier.WONDER, LandHard2::witchCoven, SWAMP),
	BARBARIAN_HALL("barbarian_hall", DeepKind.Tier.WONDER, LandHard2::barbarianHall, TAIGA, SNOWY),
	SUN_TEMPLE("sun_temple", DeepKind.Tier.WONDER, LandHard2::sunTemple, DESERT, SAVANNA),
	JUNGLE_PALACE("jungle_palace", DeepKind.Tier.WONDER, LandHard2::junglePalace, JUNGLE),
	GLACIER_VAULT("glacier_vault", DeepKind.Tier.WONDER, LandHard2::glacierVault, SNOWY),
	WIZARD_TOWER("wizard_tower", DeepKind.Tier.WONDER, LandHard2::wizardTower),
	BUNKER("bunker", DeepKind.Tier.WONDER, LandHard2::bunker, PLAINS, FOREST),
	CATHEDRAL("cathedral", DeepKind.Tier.WONDER, LandHard2::cathedral, PLAINS, FOREST, TAIGA),
	RUINED_CITY("ruined_city", DeepKind.Tier.WONDER, LandHard2::ruinedCity);

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
