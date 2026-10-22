package com.naturesmod.worldgen.deep;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Every kind of structure on the new End's lands, and how to build it. They come in the same three
 * sizes as the treasures of the deep: outposts, halls and citadels.
 */
public enum EndKind {
	// Outposts: small and common.
	ENDER_SHRINE("ender_shrine", DeepKind.Tier.RUIN, EndBuilds::enderShrine),
	OBSIDIAN_OBELISK("obsidian_obelisk", DeepKind.Tier.RUIN, EndBuilds::obsidianObelisk),
	VOID_WELL("void_well", DeepKind.Tier.RUIN, EndBuilds::voidWell),
	CRYSTAL_SHRINE("crystal_shrine", DeepKind.Tier.RUIN, EndBuilds::crystalShrine),
	FALLEN_STAR("fallen_star", DeepKind.Tier.RUIN, EndBuilds::fallenStar),
	SHULKER_NEST("shulker_nest", DeepKind.Tier.RUIN, EndBuilds::shulkerNest),
	// Halls: buildings full of riches.
	END_LIBRARY("end_library", DeepKind.Tier.VAULT, EndBuilds::endLibrary),
	STAR_OBSERVATORY("star_observatory", DeepKind.Tier.VAULT, EndBuilds::starObservatory),
	VOID_TEMPLE("void_temple", DeepKind.Tier.VAULT, EndBuilds::voidTemple),
	SKYSHIP_WRECK("skyship_wreck", DeepKind.Tier.VAULT, EndBuilds::skyshipWreck),
	STARMETAL_FORGE("starmetal_forge", DeepKind.Tier.VAULT, EndBuilds::starmetalForge),
	GRAVITY_TOWER("gravity_tower", DeepKind.Tier.VAULT, EndBuilds::gravityTower),
	// Citadels: rare, vast, and home to the Star Altar.
	STAR_CITADEL("star_citadel", DeepKind.Tier.WONDER, EndBuilds::starCitadel),
	ENDER_COLOSSUS("ender_colossus", DeepKind.Tier.WONDER, EndBuilds::enderColossus),
	VOID_PALACE("void_palace", DeepKind.Tier.WONDER, EndBuilds::voidPalace);

	private static final Map<DeepKind.Tier, List<EndKind>> BY_TIER = new EnumMap<>(DeepKind.Tier.class);
	/** How much taller than a treasure of the deep the End's structures may stand. */
	private static final int EXTRA_HEIGHT = 24;

	static {
		for (EndKind kind : values()) {
			BY_TIER.computeIfAbsent(kind.tier, tier -> new ArrayList<>()).add(kind);
		}
	}

	private final String id;
	private final DeepKind.Tier tier;
	private final Consumer<DeepBuilder> builder;

	EndKind(String id, DeepKind.Tier tier, Consumer<DeepBuilder> builder) {
		this.id = id;
		this.tier = tier;
		this.builder = builder;
	}

	public String id() {
		return id;
	}

	public DeepKind.Tier tier() {
		return tier;
	}

	/** The box it is built in: as wide as a treasure of the deep of its size, and taller, for the End's towers. */
	public BoundingBox bounds(BlockPos center) {
		BoundingBox box = tier.bounds(center);
		return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY() + EXTRA_HEIGHT, box.maxZ());
	}

	void build(DeepBuilder b) {
		builder.accept(b);
	}

	static EndKind pick(DeepKind.Tier tier, RandomSource random) {
		List<EndKind> kinds = BY_TIER.get(tier);
		return kinds.get(random.nextInt(kinds.size()));
	}

	public static EndKind byId(String id) {
		for (EndKind kind : values()) {
			if (kind.id.equals(id)) {
				return kind;
			}
		}
		return ENDER_SHRINE;
	}
}
