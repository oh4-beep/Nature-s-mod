"""The wild Overworld: thirteen new biomes grown out of vanilla's.

Vanilla's own biome source still decides the climate everywhere; Nature's Mod's biome source
(WildBiomeSource.java) wraps it and turns a share of some vanilla biomes into new ones, each laid
out by its own noise field (see VARIANTS). The normal world preset is pointed at it.

Each new biome is built from a vanilla template biome: the same carvers, ores and underground,
its own colours, sky, fog, music, particles and creatures, some of the template's plants taken
out and its own features added at the end of each step. It also joins every vanilla biome tag its
template is listed in, so villages, mineshafts, strongholds and the rest find it as they would
the template.
"""

import copy
import json
import os

NS = "naturesmod"

# Decoration steps: indices into a biome's feature lists.
RAW, LAKES, LOCAL, UNDERGROUND_STRUCTURES, SURFACE_STRUCTURES, STRONGHOLDS, ORES, UNDERGROUND, SPRINGS, VEGETAL, TOP = range(11)

NETHER_AND_END = {"nether_wastes", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas", "the_end", "end_highlands",
                  "end_midlands", "end_barrens", "small_end_islands", "the_void"}
CAVE_BIOMES = {"lush_caves", "dripstone_caves", "deep_dark", "sulfur_caves"}

# Where the fungal grottos open up: under the green, wet lands.
GROTTO_HOSTS = ["plains", "sunflower_plains", "forest", "flower_forest", "birch_forest", "old_growth_birch_forest", "dark_forest",
                "dappled_forest", "pale_garden", "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "jungle", "sparse_jungle",
                "bamboo_jungle", "swamp", "mangrove_swamp", "meadow", "cherry_grove", "savanna", "windswept_forest", "mushroom_fields"]

# name: how it grows out of vanilla. The fields of one biome share a salt and a scale (blocks per
# noise unit) and so cover each of their hosts with the same patches, each to its own share.
# Caves come first: underground they win over the surface biome above them.
VARIANTS = {
    "crystal_caverns": {"hosts": "caves", "coverage": 0.2, "salt": 101, "scale": 110, "min_y": -64, "max_y": -6, "vertical_scale": 40},
    "fungal_grotto": {"hosts": {b: 0.16 for b in GROTTO_HOSTS}, "salt": 102, "scale": 96, "min_y": -4, "max_y": 44, "vertical_scale": 36},
    "lavender_fields": {"hosts": {"plains": 0.32, "sunflower_plains": 0.32, "meadow": 0.28}, "salt": 1, "scale": 320},
    "golden_prairie": {"hosts": {"plains": 0.3, "sunflower_plains": 0.3, "savanna": 0.22}, "salt": 2, "scale": 420},
    "maple_woods": {"hosts": {"forest": 0.28, "birch_forest": 0.3, "flower_forest": 0.25}, "salt": 3, "scale": 380},
    "glowshroom_forest": {"hosts": {"dark_forest": 0.45, "forest": 0.08}, "salt": 4, "scale": 300},
    "tropical_shore": {"hosts": {"beach": 0.6}, "salt": 5, "scale": 260, "min_temperature": -0.05},
    "salt_flats": {"hosts": {"desert": 0.3}, "salt": 6, "scale": 420},
    "volcanic_peaks": {"hosts": {"windswept_savanna": 0.5, "savanna_plateau": 0.3, "stony_peaks": 0.15, "badlands": 0.15}, "salt": 7,
                       "scale": 520},
    "bayou": {"hosts": {"swamp": 0.5, "mangrove_swamp": 0.25}, "salt": 8, "scale": 360},
    "tundra": {"hosts": {"snowy_plains": 0.45}, "salt": 9, "scale": 480},
    "petrified_forest": {"hosts": {"wooded_badlands": 0.35, "eroded_badlands": 0.2, "savanna": 0.12}, "salt": 10, "scale": 360},
    "heather_moors": {"hosts": {"windswept_hills": 0.4, "windswept_gravelly_hills": 0.4, "meadow": 0.22}, "salt": 11, "scale": 420},
}

SURFACE_BIOMES = [b for b in VARIANTS if b not in ("crystal_caverns", "fungal_grotto")]


# --- helpers ---------------------------------------------------------------------------------

def spawn(entity, weight, lo, hi):
    count = lo if lo == hi else {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}
    return {"type": f"minecraft:{entity}", "count": count, "weight": weight}


def particles(*pairs):
    return [{"particle": {"type": f"minecraft:{p}"}, "probability": chance} for p, chance in pairs]


def state(block, **props):
    block = block if ":" in block else f"minecraft:{block}"
    if not props:
        return {"id": block}
    return {"id": block, "properties": {k: str(v).lower() for k, v in props.items()}}


def weighted(*entries):
    return {"type": "minecraft:weighted", "entries": [{"data": s if isinstance(s, dict) else (s if ":" in s else f"minecraft:{s}"), "weight": w}
                                                       for s, w in entries]}


def simple(provider):
    return {"type": "minecraft:simple_block", "to_place": provider}


def wild(style):
    return {"type": f"{NS}:wild_feature", "style": style}


def giant(style, log, leaves, lo, hi, near_water=False):
    feature = {"type": f"{NS}:giant_tree", "style": style, "log": {"id": log if ":" in log else f"minecraft:{log}", "properties": {"axis": "y"}},
               "min_height": lo, "max_height": hi}
    if leaves:
        feature["leaves"] = [{"id": f"minecraft:{b}", "properties": {"distance": "7", "persistent": "false", "waterlogged": "false"}}
                             for b in leaves]
    if near_water:
        feature["near_water"] = True
    return feature


def count(n):
    return {"type": "minecraft:count", "count": n}


def rarity(chance):
    return {"type": "minecraft:rarity_filter", "chance": chance}


IN_SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}
NO_WATER = {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0}
AIR = {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}


def heightmap(kind):
    return {"type": "minecraft:heightmap", "heightmap": kind}


def trapezoid(spread):
    return {"type": "minecraft:trapezoid", "max": spread, "min": -spread, "plateau": 0}


def offset(xz, y):
    return {"type": "minecraft:offset", "x": trapezoid(xz), "y": trapezoid(y), "z": trapezoid(xz)}


def on(block, *more):
    return {"type": "minecraft:matching_blocks", "blocks": [block, *more] if more else block, "offset": [0, -1, 0]}


def filtered(*predicates):
    if len(predicates) == 1:
        return {"type": "minecraft:block_predicate_filter", "predicate": predicates[0]}
    return {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": list(predicates)}}


def patch(tries, spread=7, spread_y=3, chance=None, n=None, predicate=None, height="MOTION_BLOCKING"):
    """Vanilla-style plant patch: picked spots per chunk, then `tries` scattered attempts round each."""
    placement = []
    if chance:
        placement.append(rarity(chance))
    if n:
        placement.append(count(n))
    placement += [IN_SQUARE, heightmap(height), BIOME, count(tries), offset(spread, spread_y), filtered(*(predicate or [AIR]))]
    return placement


def surface(chance=None, n=None, height="WORLD_SURFACE_WG", dry=False):
    placement = []
    if chance:
        placement.append(rarity(chance))
    if n:
        placement.append(count(n))
    placement.append(IN_SQUARE)
    if dry:
        placement.append(NO_WATER)
    placement += [heightmap(height), BIOME]
    return placement


def cave(n, low, high, scan=None):
    placement = [count(n), IN_SQUARE, {"type": "minecraft:height_range", "height": {
        "type": "minecraft:uniform", "min_inclusive": {"absolute": low}, "max_inclusive": {"absolute": high}}}]
    if scan:
        placement.append({"type": "minecraft:environment_scan", "allowed_search_condition": AIR, "direction_of_search": scan, "max_steps": 12,
                          "target_condition": {"type": "minecraft:solid"}})
        placement.append({"type": "minecraft:offset", "x": 0, "y": 1 if scan == "down" else -1, "z": 0})
    placement.append(BIOME)
    return placement


def near_water():
    water = ["minecraft:water", "minecraft:flowing_water"]
    return {"type": "minecraft:any_of", "predicates": [
        {"type": "minecraft:matching_fluids", "fluids": water, "offset": off} for off in ([1, -1, 0], [-1, -1, 0], [0, -1, 1], [0, -1, -1])]}


def geode(crystal, cluster, chance):
    return ({
        "type": "minecraft:geode",
        "blocks": {
            "alternate_inner_layer_provider": state(crystal),
            "cannot_replace": "#minecraft:features_cannot_replace",
            "filling_provider": state("air"),
            "inner_layer_provider": state(crystal),
            "inner_placements": [state(cluster, facing="up", waterlogged=False)],
            "invalid_blocks": "#minecraft:geode_invalid_blocks",
            "middle_layer_provider": state("calcite"),
            "outer_layer_provider": state("smooth_basalt"),
        },
        "crack": {"generate_crack_chance": 0.9},
        "invalid_blocks_threshold": 1,
        "layers": {},
        "outer_wall_distance": {"type": "minecraft:uniform", "min_inclusive": 4, "max_inclusive": 6},
        "use_alternate_layer0_chance": 0.0,
    }, chance)


# --- features --------------------------------------------------------------------------------

def features():
    """name: (feature, placement). Every one is `naturesmod:wild/<name>`."""
    out = {
        # Lavender fields and golden prairies.
        "lavender_field": (wild("lavender_field"), surface(n=1)),
        "lavender_wildflowers": (simple(weighted(*((state("wildflowers", facing=f, flower_amount=a), 1) for f in ("north", "east", "south", "west")
                                                   for a in (2, 3, 4)))), patch(24, 6, 2, n=1)),
        "prairie_grass": (simple(state("tall_grass", half="lower")), patch(64, n=2)),
        "prairie_flowers": (simple(weighted(("dandelion", 4), ("cornflower", 2), ("poppy", 1), (state("wildflowers", facing="north", flower_amount=4), 3),
                                            (state("wildflowers", facing="south", flower_amount=3), 3))), patch(32, 6, 2, n=2)),
        "prairie_sunflowers": (simple(state("sunflower", half="lower")), patch(48, chance=3)),
        "prairie_hay": ({"type": "minecraft:block_pile", "state_provider": {"type": "minecraft:rotated", "state": state("hay_block", axis="y")}},
                        surface(chance=28, dry=True)),
        # Maple woods: oaks the biome turns red, and drifts of fallen leaves.
        "maple_trees": ({"type": "minecraft:random_selector", "default": "minecraft:oak_bees_0002_leaf_litter", "features": [
            {"chance": 0.3, "feature": "minecraft:fancy_oak_bees_0002_leaf_litter"}, {"chance": 0.0125, "feature": "minecraft:fallen_oak_tree"}]},
            [{"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [{"data": 9, "weight": 9}, {"data": 11, "weight": 1}]}},
             IN_SQUARE, NO_WATER, heightmap("OCEAN_FLOOR"), BIOME]),
        "maple_leaf_litter": (simple(weighted(*((state("leaf_litter", facing=f, segment_amount=a), 1) for f in ("north", "east", "south", "west")
                                                for a in (2, 3, 4)))), patch(48, n=3, height="WORLD_SURFACE_WG", predicate=[AIR, on("minecraft:grass_block")])),
        # Glowshroom forests and fungal grottos.
        "giant_glowcaps": (wild("giant_glowcap"), surface(n=3, height="MOTION_BLOCKING_NO_LEAVES")),
        "glowcaps": (simple(state(f"{NS}:glowcap")), patch(24, 5, 2, n=4, height="MOTION_BLOCKING_NO_LEAVES")),
        "glowcaps_sparse": (simple(state(f"{NS}:glowcap")), patch(12, 4, 2, chance=6, height="MOTION_BLOCKING_NO_LEAVES")),
        "grotto_floor": ({"type": "minecraft:vegetation_patch", "depth": 1, "extra_bottom_block_chance": 0.0, "extra_edge_column_chance": 0.4,
                          "ground_state": weighted(("mycelium", 3), ("moss_block", 2), ("podzol", 1)), "replaceable": "#minecraft:moss_replaceable",
                          "surface": "floor", "vegetation_chance": 0.5,
                          "vegetation_feature": {"feature": simple(weighted((state(f"{NS}:glowcap"), 6), ("brown_mushroom", 3), ("red_mushroom", 1),
                                                                             ("moss_carpet", 4))), "placement": []},
                          "vertical_range": 5, "xz_radius": {"type": "minecraft:uniform", "min_inclusive": 3, "max_inclusive": 6}},
                         cave(90, -4, 44, "down")),
        "grotto_glowcaps": (wild("grotto_glowcap"), cave(24, -4, 44, "down")),
        "grotto_lichen": ({"type": "minecraft:multiface_growth", "block": "minecraft:glow_lichen", "can_be_placed_on": [
            "minecraft:stone", "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff", "minecraft:deepslate",
            "minecraft:mycelium", "minecraft:moss_block", "minecraft:mushroom_stem"], "can_place_on_ceiling": True, "can_place_on_wall": True,
            "search_range": 20}, cave(160, -4, 44)),
        # The tropical shore.
        "palms": (giant("palm", "jungle_log", ["jungle_leaves"], 6, 11), surface(n=2, height="OCEAN_FLOOR", dry=True)),
        # Salt flats.
        "brine_pools": (wild("brine_pool"), surface(chance=3)),
        "salt_crystals": (simple(state(f"{NS}:salt_crystal", facing="up", waterlogged=False)),
                          patch(12, 6, 1, n=3, height="WORLD_SURFACE_WG", predicate=[AIR, on(f"{NS}:salt_block")])),
        # Volcanic peaks.
        "volcanoes": (wild("volcano"), surface(chance=4)),
        "steam_vents": (wild("steam_vents"), surface(chance=2)),
        "basalt_columns": ("minecraft:small_basalt_columns", surface(n=1)),
        "charred_snags": (giant("snag", f"{NS}:charred_log", [], 5, 10), surface(chance=2, dry=True, height="OCEAN_FLOOR")),
        # The bayou.
        "cypress": (giant("cypress", "spruce_log", ["oak_leaves"], 12, 20), surface(n=3, height="OCEAN_FLOOR")),
        "bayou_lilies": ("minecraft:waterlily", patch(16, n=5, height="WORLD_SURFACE_WG")),
        "cattails": (simple(state(f"{NS}:cattail", half="lower")),
                     patch(20, 4, 0, n=4, predicate=[AIR, {"type": "minecraft:would_survive", "state": state(f"{NS}:cattail", half="lower")},
                                                    near_water()])),
        # The tundra.
        "tundra_shrubs": (simple(weighted((state(f"{NS}:blueberry_bush", age=3), 8), (state("sweet_berry_bush", age=2), 2), ("short_grass", 10),
                                          ("fern", 6), ("dead_bush", 2))), patch(24, 6, 2, n=3, height="WORLD_SURFACE_WG",
                                                                                predicate=[AIR, on("minecraft:grass_block", "minecraft:podzol",
                                                                                                   "minecraft:coarse_dirt")])),
        "tundra_moss": ({"type": "minecraft:vegetation_patch", "depth": 1, "extra_bottom_block_chance": 0.0, "extra_edge_column_chance": 0.3,
                         "ground_state": state("moss_block"), "replaceable": "#minecraft:moss_replaceable", "surface": "floor",
                         "vegetation_chance": 0.4, "vegetation_feature": {"feature": simple(weighted(("moss_carpet", 6), ("short_grass", 3),
                                                                                                     (state(f"{NS}:blueberry_bush", age=3), 1))),
                                                                           "placement": []},
                         "vertical_range": 4, "xz_radius": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 5}},
                        surface(chance=3, dry=True)),
        # The petrified forest.
        "petrified_trees": (wild("petrified_tree"), surface(n=3, dry=True)),
        # The moors.
        "heather": (simple(state(f"{NS}:heather")), patch(40, 6, 2, n=5)),
        "moor_ferns": (simple(state("large_fern", half="lower")), patch(24, chance=4)),
        "moor_walls": (wild("stone_wall"), surface(chance=2, dry=True)),
        # Crystal caverns.
        "crystal_spires_floor": (wild("crystal_spire"), cave(28, -62, -8, "down")),
        "crystal_spires_ceiling": (wild("crystal_spire"), cave(20, -62, -8, "up")),
        "crystal_growth": (wild("crystal_growth"), cave(90, -62, -8)),
        "azure_geode": geode(f"{NS}:azure_crystal", f"{NS}:azure_crystal_cluster", 4),
        "citrine_geode": geode(f"{NS}:citrine_crystal", f"{NS}:citrine_crystal_cluster", 5),
        "cavern_lichen": ({"type": "minecraft:multiface_growth", "block": "minecraft:glow_lichen", "can_be_placed_on": [
            "minecraft:stone", "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff", "minecraft:deepslate",
            "minecraft:calcite"], "can_place_on_ceiling": True, "can_place_on_wall": True, "search_range": 20}, cave(140, -62, -8)),
        # Spread by Fabric to vanilla's biomes too (see ModBiomeModifications.java).
        "meteor_crater": (wild("meteor_crater"), surface(chance=180)),
        "hot_spring": (wild("hot_spring"), surface(chance=24)),
        "blueberries": (simple(state(f"{NS}:blueberry_bush", age=3)), patch(32, chance=12, height="WORLD_SURFACE_WG",
                                                                         predicate=[AIR, on("minecraft:grass_block", "minecraft:podzol")])),
        "riverside_cattails": (simple(state(f"{NS}:cattail", half="lower")),
                               patch(16, 4, 0, chance=3, predicate=[AIR, {"type": "minecraft:would_survive",
                                                                         "state": state(f"{NS}:cattail", half="lower")}, near_water()])),
        "rare_azure_geode": geode(f"{NS}:azure_crystal", f"{NS}:azure_crystal_cluster", 70),
        "rare_citrine_geode": geode(f"{NS}:citrine_crystal", f"{NS}:citrine_crystal_cluster", 80),
    }
    files = {}
    for name, (feature, placement) in out.items():
        if isinstance(placement, int):
            # Geodes: buried deep, like vanilla's amethyst ones.
            low, high = (-58, -10) if name in ("azure_geode", "citrine_geode") else (-50, 20)
            placement = [rarity(placement), IN_SQUARE, {"type": "minecraft:height_range", "height": {
                "type": "minecraft:uniform", "min_inclusive": {"absolute": low}, "max_inclusive": {"absolute": high}}}, BIOME]
        if isinstance(feature, str):
            # A vanilla feature placed our own way: only the placed feature is new.
            files[f"{NS}/worldgen/placed_feature/wild/{name}.json"] = {"feature": feature, "placement": placement}
        else:
            files[f"{NS}/worldgen/feature/wild/{name}.json"] = feature
            files[f"{NS}/worldgen/placed_feature/wild/{name}.json"] = {"feature": f"{NS}:wild/{name}", "placement": placement}
    return files


# The one order every new feature keeps in every biome that has it.
ORDER = ["azure_geode", "citrine_geode", "volcanoes", "steam_vents", "brine_pools", "moor_walls", "petrified_trees", "crystal_spires_floor",
         "crystal_spires_ceiling", "crystal_growth", "lavender_field", "maple_trees", "palms", "cypress", "giant_glowcaps", "grotto_floor",
         "grotto_glowcaps", "basalt_columns", "charred_snags", "lavender_wildflowers", "prairie_grass", "prairie_flowers", "prairie_sunflowers",
         "prairie_hay", "maple_leaf_litter", "glowcaps", "bayou_lilies", "cattails", "tundra_shrubs", "tundra_moss", "heather", "moor_ferns",
         "salt_crystals", "grotto_lichen", "cavern_lichen"]


# --- biomes ----------------------------------------------------------------------------------

BIOMES = {
    "lavender_fields": {
        "template": "plains", "temperature": 0.75, "downfall": 0.5,
        "effects": {"grass_color": "#9cc46b", "foliage_color": "#88b35c", "water_color": "#4f8fe0"},
        "sky": "#8db3ff", "music": "meadow", "particles": [("cherry_leaves", 0.0006)],
        "remove": ["flower_plains"],
        "add": {VEGETAL: ["lavender_field", "lavender_wildflowers"]},
        "creatures": [spawn("rabbit", 8, 2, 3)],
        "climate": "plains",
    },
    "golden_prairie": {
        "template": "plains", "temperature": 0.9, "downfall": 0.3,
        "effects": {"grass_color": "#c9b65a", "foliage_color": "#b2a252", "dry_foliage_color": "#a88a45", "water_color": "#4a86d8"},
        "sky": "#7eb2ff", "music": "meadow",
        "remove": ["trees_plains", "flower_plains", "patch_tall_grass_2"],
        "add": {VEGETAL: ["prairie_grass", "prairie_flowers", "prairie_sunflowers", "prairie_hay"]},
        "creatures": [spawn("horse", 6, 2, 6), spawn("rabbit", 6, 2, 3)],
        "climate": "plains",
    },
    "maple_woods": {
        "template": "forest", "temperature": 0.6, "downfall": 0.7,
        "effects": {"grass_color": "#a9a24c", "foliage_color": "#d2401c", "dry_foliage_color": "#a5451c", "water_color": "#3f6fd0"},
        "sky": "#86aaff", "fog": "#e3d6c8", "music": "forest", "particles": [("red_poplar_leaves", 0.004), ("orange_poplar_leaves", 0.0015)],
        "remove": ["trees_birch_and_oak_leaf_litter"],
        "add": {VEGETAL: ["maple_trees", "maple_leaf_litter"]},
        "creatures": [spawn("fox", 6, 2, 3)],
        "climate": "forest",
    },
    "glowshroom_forest": {
        "template": "dark_forest", "temperature": 0.65, "downfall": 0.9,
        "effects": {"grass_color": "#3f7a6c", "foliage_color": "#2e6a72", "dry_foliage_color": "#3a5a52", "water_color": "#2c7fa8"},
        "sky": "#6f8fc4", "fog": "#8fb3d6", "water_fog": "#1d4d63", "music": "forest",
        "particles": [("firefly", 0.006), ("spore_blossom_air", 0.003)],
        "add": {VEGETAL: ["giant_glowcaps", "glowcaps"]},
        "climate": "forest",
    },
    "tropical_shore": {
        "template": "beach", "temperature": 0.95, "downfall": 0.8,
        "effects": {"grass_color": "#7ccf4a", "foliage_color": "#4fbf35", "water_color": "#1fc4d8"},
        "sky": "#6cb8ff", "water_fog": "#12a0b8", "music": None,
        "remove": ["patch_pumpkin", "patch_grass_badlands"],
        "add": {VEGETAL: ["palms"]},
        "creatures": [spawn("parrot", 8, 1, 2)],
        "climate": "jungle",
    },
    "salt_flats": {
        "template": "desert", "temperature": 2.0, "downfall": 0.0,
        "effects": {"grass_color": "#bfb77a", "foliage_color": "#aea76e", "water_color": "#7fd6e8"},
        "sky": "#a8d4ff", "fog": "#f2f4f6", "music": "desert", "particles": [("white_ash", 0.0012)],
        "remove": ["patch_cactus_desert", "patch_dry_grass_desert", "patch_sugar_cane_desert", "patch_grass_badlands", "patch_pumpkin"],
        "add": {LOCAL: ["brine_pools"], VEGETAL: ["salt_crystals"]},
        "climate": "desert",
    },
    "volcanic_peaks": {
        "template": "stony_peaks", "temperature": 1.4, "downfall": 0.2, "precipitation": False,
        "effects": {"grass_color": "#6e6b4c", "foliage_color": "#5d5a3e", "water_color": "#4a6f73"},
        "sky": "#8e9fb4", "fog": "#a39a92", "music": "stony_peaks", "particles": [("ash", 0.012), ("white_ash", 0.004)],
        "add": {LOCAL: ["volcanoes", "steam_vents"], VEGETAL: ["basalt_columns", "charred_snags"]},
        "monsters": [spawn("magma_cube", 6, 1, 2)],
        "climate": "badlands",
    },
    "bayou": {
        "template": "swamp", "temperature": 0.85, "downfall": 0.95,
        "effects": {"foliage_color": "#5f7d33", "dry_foliage_color": "#6e5432", "water_color": "#4d5b29"},
        "sky": "#86a0c8", "fog": "#b2bfa0", "water_fog": "#2a3117", "fog_end": 0.75, "music": "swamp",
        "particles": [("firefly", 0.01)],
        "remove": ["trees_swamp"],
        "add": {VEGETAL: ["cypress", "bayou_lilies", "cattails"]},
        "climate": "swamp",
    },
    "tundra": {
        # Bare ground amid the snowy plains: just warm enough that no snow settles, but on the hilltops. Snow is laid
        # chunk by chunk by whichever biome owns the chunk's corner, column by column as cold as each is, so the
        # tundra must be too warm for it rather than go without: snowless chunks would cut squares out of the snow.
        "template": "snowy_plains", "temperature": 0.2, "downfall": 0.4,
        "effects": {"grass_color": "#a0a66c", "foliage_color": "#8d9a62", "water_color": "#3d57d6"},
        "sky": "#a2c2ee", "music": None,
        "remove": ["patch_pumpkin", "flower_default"],
        "add": {VEGETAL: ["tundra_shrubs", "tundra_moss"]},
        "creatures": [spawn("fox", 8, 2, 4), spawn("wolf", 4, 2, 4)],
        "climate": "snowy",
    },
    "petrified_forest": {
        "template": "wooded_badlands", "temperature": 1.6, "downfall": 0.1, "precipitation": False,
        "effects": {"grass_color": "#b4a75d", "foliage_color": "#a59553", "water_color": "#3f76e4"},
        "sky": "#80b8f2", "music": "badlands",
        "remove": ["trees_badlands", "patch_cactus_decorated"],
        "add": {LOCAL: ["petrified_trees"]},
        "creatures": [spawn("armadillo", 6, 1, 2)],
        "climate": "savanna",
    },
    "heather_moors": {
        "template": "windswept_hills", "temperature": 0.35, "downfall": 0.8,
        "effects": {"grass_color": "#8e9958", "foliage_color": "#7c884c", "water_color": "#3b5ab5"},
        "sky": "#93a6c2", "fog": "#bcc5cf", "fog_end": 0.55, "music": None,
        "remove": ["trees_windswept_hills", "flower_default"],
        "add": {LOCAL: ["moor_walls"], VEGETAL: ["heather", "moor_ferns"]},
        "creatures": [spawn("sheep", 14, 3, 6)],
        "climate": "mountain",
    },
    "crystal_caverns": {
        "template": "dripstone_caves",
        "effects": {"water_color": "#5ad4f0"},
        "water_fog": "#2a8fb4", "music": "dripstone_caves", "particles": [("end_rod", 0.0025)],
        "remove": ["large_dripstone", "dripstone_cluster", "pointed_dripstone"],
        "add": {LOCAL: ["azure_geode", "citrine_geode"], UNDERGROUND: ["crystal_spires_floor", "crystal_spires_ceiling", "crystal_growth"],
                VEGETAL: ["cavern_lichen"]},
    },
    "fungal_grotto": {
        "template": "lush_caves",
        "effects": {"water_color": "#3a9fb0"},
        "music": "lush_caves", "particles": [("spore_blossom_air", 0.01), ("mycelium", 0.004)],
        "remove": ["lush_caves_ceiling_vegetation", "cave_vines", "lush_caves_clay", "lush_caves_vegetation", "rooted_azalea_tree", "spore_blossom",
                   "classic_vines_cave_feature"],
        "add": {VEGETAL: ["grotto_floor", "grotto_glowcaps", "grotto_lichen"]},
    },
}


def make_biome(vanilla, name, spec):
    data = copy.deepcopy(vanilla.read(f"minecraft/worldgen/biome/{spec['template']}.json"))
    for key in ("temperature", "downfall"):
        if key in spec:
            data[key] = spec[key]
    if "precipitation" in spec:
        data["has_precipitation"] = spec["precipitation"]
    data["effects"].update(spec.get("effects", {}))
    attributes = data["attributes"]
    for key, attribute in (("sky", "sky_color"), ("fog", "fog_color"), ("water_fog", "water_fog_color")):
        if key in spec:
            attributes[f"minecraft:visual/{attribute}"] = spec[key]
    if "fog_end" in spec:
        attributes["minecraft:visual/fog_end_distance"] = {"argument": spec["fog_end"], "modifier": "multiply"}
    if "music" in spec:
        if spec["music"]:
            attributes["minecraft:audio/background_music"] = {
                "default": {"max_delay": 24000, "min_delay": 12000, "sound": f"minecraft:music.overworld.{spec['music']}"}}
        else:
            attributes.pop("minecraft:audio/background_music", None)
    if spec.get("particles"):
        attributes["minecraft:visual/ambient_particles"] = {"argument": particles(*spec["particles"]), "modifier": "append"}
    spawns = attributes.setdefault("minecraft:gameplay/natural_mob_spawns", {"argument": {"spawn_costs": {}, "spawns_by_category": {}},
                                                                           "modifier": "overlay"})["argument"]["spawns_by_category"]
    for category, key in (("creature", "creatures"), ("monster", "monsters")):
        if spec.get(key):
            listed = spawns.setdefault(category, [])
            known = {entry["type"] for entry in listed}
            listed.extend(entry for entry in spec[key] if entry["type"] not in known)
    steps = data["features"]
    while len(steps) < 11:
        steps.append([])
    removed = {f"minecraft:{f}" for f in spec.get("remove", ())}
    missing = removed - {f for step in steps for f in step}
    if missing:
        raise SystemExit(f"{name}: its template {spec['template']} has no {sorted(missing)} to remove")
    for i, step in enumerate(steps):
        steps[i] = [f for f in step if f not in removed]
        steps[i] += [f"{NS}:wild/{f}" for f in sorted(spec.get("add", {}).get(i, ()), key=ORDER.index)]
    return data


def vanilla_overworld(vanilla):
    folder = os.path.join(vanilla.root, "minecraft", "worldgen", "biome")
    return sorted(n[:-5] for n in os.listdir(folder) if n.endswith(".json") and n[:-5] not in NETHER_AND_END)


def variants(vanilla):
    out = []
    caves = [b for b in vanilla_overworld(vanilla) if b not in CAVE_BIOMES]
    for name, spec in VARIANTS.items():
        hosts = {b: spec["coverage"] for b in caves} if spec["hosts"] == "caves" else spec["hosts"]
        # One entry per share: hosts covered alike go together.
        shares = {}
        for host, share in hosts.items():
            shares.setdefault(share, []).append(f"minecraft:{host}")
        for share, replaced in shares.items():
            entry = {"biome": f"{NS}:{name}", "replaces": replaced, "salt": spec["salt"], "scale": spec["scale"], "coverage": share}
            for key in ("min_y", "max_y", "vertical_scale", "min_temperature"):
                if key in spec:
                    entry[key] = spec[key]
            out.append(entry)
    return out


def preset(vanilla):
    data = copy.deepcopy(vanilla.read("minecraft/worldgen/world_preset/normal.json"))
    generator = data["dimensions"]["minecraft:overworld"]["generator"]
    generator["biome_source"] = {"type": f"{NS}:wild", "base": generator["biome_source"], "variants": variants(vanilla)}
    return data


# --- tags ------------------------------------------------------------------------------------

def tags(vanilla):
    """Each new biome joins every vanilla biome tag its template is listed in, and a climate tag of its own."""
    folder = os.path.join(vanilla.root, "minecraft", "tags", "worldgen", "biome")
    out = {}
    for root, _, names in os.walk(folder):
        for file in sorted(names):
            if not file.endswith(".json"):
                continue
            with open(os.path.join(root, file), encoding="utf-8") as fh:
                values = json.load(fh)["values"]
            members = [f"{NS}:{name}" for name, spec in BIOMES.items() if f"minecraft:{spec['template']}" in values]
            if members:
                rel = os.path.relpath(os.path.join(root, file), folder).replace(os.sep, "/")
                out[f"minecraft/tags/worldgen/biome/{rel}"] = {"replace": False, "values": members}
    climates = {}
    for name, spec in BIOMES.items():
        if "climate" in spec:
            climates.setdefault(spec["climate"], []).append(f"{NS}:{name}")
    for climate, members in climates.items():
        out[f"{NS}/tags/worldgen/biome/climate/{climate}.json"] = {"values": members}
    return out


# --- surface ---------------------------------------------------------------------------------

def block(b):
    return {"type": "minecraft:block", "result_state": b if ":" in b else f"minecraft:{b}"}


def cond(test, then):
    return {"type": "minecraft:condition", "if_true": test, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def biome_is(name):
    return {"type": "minecraft:biome", "biome_is": f"{NS}:{name}"}


def noise(name, lo, hi=1.0e9):
    return {"type": "minecraft:noise_threshold", "noise": f"minecraft:{name}", "min_threshold": lo, "max_threshold": hi}


FLOOR = "minecraft:on_floor"
UNDER = "minecraft:under_floor"
DRY = "minecraft:not_underwater"


def wild_surface():
    """The ground of the new biomes; any other biome falls through to vanilla's rules."""
    return seq(
        cond(biome_is("salt_flats"), seq(
            cond(FLOOR, seq(cond(DRY, seq(cond(noise("surface", -0.04, 0.04), block("calcite")), block(f"{NS}:salt_block"))), block("sand"))),
            cond(UNDER, block("sandstone")))),
        cond(biome_is("volcanic_peaks"), seq(
            cond(FLOOR, seq(cond(noise("patch", 0.45), block("magma_block")), cond(noise("surface", 0.3), block(f"{NS}:ash_block")),
                            cond(noise("surface", -0.35, 0.3), block("blackstone")), block("basalt"))),
            cond(UNDER, seq(cond(noise("surface", 0.0), block("tuff")), block("blackstone"))))),
        cond(biome_is("tropical_shore"), seq(cond(FLOOR, block("sand")), cond(UNDER, "minecraft:overworld/sand_or_sandstone_if_ceiling"))),
        cond(biome_is("tundra"), cond(FLOOR, cond(DRY, seq(cond(noise("surface", 0.32), block("coarse_dirt")),
                                                           cond(noise("surface", -0.62, -0.4), block("gravel")),
                                                           cond(noise("patch", 0.3), block("podzol")))))),
        cond(biome_is("petrified_forest"), seq(
            cond(FLOOR, cond(DRY, seq(cond(noise("surface", 0.35), block("red_sand")), cond(noise("surface", -0.15, 0.35), block("coarse_dirt")),
                                      cond(noise("patch", 0.4), block("terracotta"))))),
            cond(UNDER, cond(noise("surface", 0.35), block("red_sandstone"))))),
        cond(biome_is("heather_moors"), cond(FLOOR, cond(DRY, seq(cond(noise("patch", 0.3), block("podzol")),
                                                                  cond(noise("surface", -0.1, 0.08), block("coarse_dirt")))))),
        cond(biome_is("bayou"), cond(FLOOR, cond(noise("surface", 0.35), block("mud")))),
        cond(biome_is("glowshroom_forest"), cond(FLOOR, cond(DRY, seq(cond(noise("patch", 0.25), block("podzol")),
                                                                      cond(noise("surface", 0.5), block("moss_block")))))),
    )


def build(vanilla):
    files = {}
    for name, spec in BIOMES.items():
        files[f"{NS}/worldgen/biome/{name}.json"] = make_biome(vanilla, name, spec)
    files.update(features())
    files["minecraft/worldgen/world_preset/normal.json"] = preset(vanilla)
    files.update(tags(vanilla))
    files[f"{NS}/worldgen/material_rule/overworld/wild_surface.json"] = wild_surface()
    return files
