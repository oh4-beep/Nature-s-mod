package com.naturesmod.worldgen.deep;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Every kind of treasure on the floor of the Abyss, and how to build it. */
public enum DeepKind {
	// Ruins: small and common, scattered all over the bottom.
	COLUMN_CIRCLE("column_circle", Tier.RUIN, Ruins::columnCircle),
	FALLEN_STATUE("fallen_statue", Tier.RUIN, Ruins::fallenStatue),
	TREASURE_PILE("treasure_pile", Tier.RUIN, Ruins::treasurePile),
	CORAL_GAZEBO("coral_gazebo", Tier.RUIN, Ruins::coralGazebo),
	SUNKEN_WELL("sunken_well", Tier.RUIN, Ruins::sunkenWell),
	SKULL_SHRINE("skull_shrine", Tier.RUIN, Ruins::skullShrine),
	MONK_CELL("monk_cell", Tier.RUIN, Ruins::monkCell),
	LOST_CART("lost_cart", Tier.RUIN, Ruins::lostCart),
	ANCHOR_SITE("anchor_site", Tier.RUIN, Ruins::anchorSite),
	DIVING_BELL("diving_bell", Tier.RUIN, Ruins::divingBell),
	BONE_ALTAR("bone_altar", Tier.RUIN, Ruins::boneAltar),
	OBSIDIAN_MONOLITH("obsidian_monolith", Tier.RUIN, Ruins::obsidianMonolith),
	CRYSTAL_GEODE("crystal_geode", Tier.RUIN, Ruins::crystalGeode),
	TOMB("tomb", Tier.RUIN, Ruins::tomb),
	RUINED_ARCH("ruined_arch", Tier.RUIN, Ruins::ruinedArch),
	PEARL_BED("pearl_bed", Tier.RUIN, Ruins::pearlBed),
	LIGHTHOUSE_RUIN("lighthouse_ruin", Tier.RUIN, Ruins::lighthouseRuin),
	STONE_HEAD("stone_head", Tier.RUIN, Ruins::stoneHead),
	LOST_CAMP("lost_camp", Tier.RUIN, Ruins::lostCamp),
	DEBRIS_VEIN("debris_vein", Tier.RUIN, Ruins::debrisVein),
	// Vaults: buildings and wrecks full of riches.
	DROWNED_HOUSE("drowned_house", Tier.VAULT, Vaults::drownedHouse),
	ANCIENT_LIBRARY("ancient_library", Tier.VAULT, Vaults::ancientLibrary),
	ARMORY("armory", Tier.VAULT, Vaults::armory),
	TREASURY("treasury", Tier.VAULT, Vaults::treasury),
	MUSIC_HALL("music_hall", Tier.VAULT, Vaults::musicHall),
	SKULL_MUSEUM("skull_museum", Tier.VAULT, Vaults::skullMuseum),
	ELYTRA_SHIP("elytra_ship", Tier.VAULT, Vaults::elytraShip),
	FORGE("forge", Tier.VAULT, Vaults::forge),
	ENCHANTING_SANCTUM("enchanting_sanctum", Tier.VAULT, Vaults::enchantingSanctum),
	THRONE_ROOM("throne_room", Tier.VAULT, Vaults::throneRoom),
	GALLEON("galleon", Tier.VAULT, Vaults::galleon),
	SUBMARINE("submarine", Tier.VAULT, Vaults::submarine),
	CRYPT("crypt", Tier.VAULT, Vaults::crypt),
	PYRAMID("pyramid", Tier.VAULT, Vaults::pyramid),
	CORAL_PALACE("coral_palace", Tier.VAULT, Vaults::coralPalace),
	OBSERVATORY("observatory", Tier.VAULT, Vaults::observatory),
	PRISON("prison", Tier.VAULT, Vaults::prison),
	ARENA("arena", Tier.VAULT, Vaults::arena),
	DRAGON_SKELETON("dragon_skeleton", Tier.VAULT, Vaults::dragonSkeleton),
	SUNKEN_MARKET("sunken_market", Tier.VAULT, Vaults::sunkenMarket),
	// Wonders: rare, vast, and the richest places in Minecraft.
	CATHEDRAL("cathedral", Tier.WONDER, Wonders::cathedral),
	ZIGGURAT("ziggurat", Tier.WONDER, Wonders::ziggurat),
	CITADEL("citadel", Tier.WONDER, Wonders::citadel),
	COLOSSUS("colossus", Tier.WONDER, Wonders::colossus),
	DROWNED_CITY("drowned_city", Tier.WONDER, Wonders::drownedCity),
	TITAN_SKULL("titan_skull", Tier.WONDER, Wonders::titanSkull),
	GREAT_VAULT("great_vault", Tier.WONDER, Wonders::greatVault),
	TEMPLE_OF_THE_DEEP("temple_of_the_deep", Tier.WONDER, Wonders::templeOfTheDeep),
	LEVIATHAN_GRAVEYARD("leviathan_graveyard", Tier.WONDER, Wonders::leviathanGraveyard),
	ABYSSAL_GATE("abyssal_gate", Tier.WONDER, Wonders::abyssalGate),
	CORAL_TOWER("coral_tower", Tier.WONDER, Wonders::coralTower),
	SEA_DRAGON_NEST("sea_dragon_nest", Tier.WONDER, Wonders::seaDragonNest),
	MINING_OUTPOST("mining_outpost", Tier.WONDER, Wonders::miningOutpost),
	LABORATORY("laboratory", Tier.WONDER, Wonders::laboratory),
	ROYAL_TOMB("royal_tomb", Tier.WONDER, Wonders::royalTomb);

	private static final Map<Tier, List<DeepKind>> BY_TIER = new EnumMap<>(Tier.class);

	static {
		for (DeepKind kind : values()) {
			BY_TIER.computeIfAbsent(kind.tier, tier -> new ArrayList<>()).add(kind);
		}
	}

	private final String id;
	private final Tier tier;
	private final Consumer<DeepBuilder> builder;

	DeepKind(String id, Tier tier, Consumer<DeepBuilder> builder) {
		this.id = id;
		this.tier = tier;
		this.builder = builder;
	}

	public String id() {
		return id;
	}

	public Tier tier() {
		return tier;
	}

	void build(DeepBuilder b) {
		builder.accept(b);
	}

	static DeepKind pick(Tier tier, RandomSource random) {
		List<DeepKind> kinds = BY_TIER.get(tier);
		return kinds.get(random.nextInt(kinds.size()));
	}

	public static DeepKind byId(String id) {
		for (DeepKind kind : values()) {
			if (kind.id.equals(id)) {
				return kind;
			}
		}
		return COLUMN_CIRCLE;
	}

	/** How big each tier is, and how level the ground under it must be. */
	public enum Tier {
		RUIN(10, 6, 6, -16, 24),
		VAULT(17, 10, 7, -18, 32),
		WONDER(27, 16, 9, -26, 48);

		private final int reach;
		private final int flatRadius;
		private final int maxSlope;
		private final int below;
		private final int above;

		Tier(int reach, int flatRadius, int maxSlope, int below, int above) {
			this.reach = reach;
			this.flatRadius = flatRadius;
			this.maxSlope = maxSlope;
			this.below = below;
			this.above = above;
		}

		int flatRadius() {
			return flatRadius;
		}

		int maxSlope() {
			return maxSlope;
		}

		public int reach() {
			return reach;
		}

		public BoundingBox bounds(BlockPos center) {
			return new BoundingBox(center.getX() - reach, center.getY() + below, center.getZ() - reach,
					center.getX() + reach, center.getY() + above, center.getZ() + reach);
		}
	}
}
