"""World features for Nature's Mod: waterfalls, cascades, tarns, cave mouths, cenotes,
boulders, fallen logs and giant trees.

Each entry produces a feature (`worldgen/feature`) and a placed feature
(`worldgen/placed_feature`). Which biomes get which placed feature is decided in
ModBiomeModifications.java.
"""

NS = "naturesmod"


def state(block, **props):
    block = block if ":" in block else f"minecraft:{block}"
    if not props:
        return block
    return {"id": block, "properties": {k: str(v).lower() for k, v in props.items()}}


def log(block):
    return state(block, axis="y")


def leaves(block):
    return state(block, distance=7, persistent=False, waterlogged=False)


# --- placement helpers -------------------------------------------------------------------

def count(n):
    return {"type": "minecraft:count", "count": n}


def rarity(chance):
    return {"type": "minecraft:rarity_filter", "chance": chance}


IN_SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}
NO_WATER = {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0}


def heightmap(kind):
    return {"type": "minecraft:heightmap", "heightmap": kind}


def terrain_placement(chance=None, n=None):
    placement = []
    if chance:
        placement.append(rarity(chance))
    if n:
        placement.append(count(n))
    placement += [IN_SQUARE, heightmap("WORLD_SURFACE_WG"), BIOME]
    return placement


def surface_placement(chance=None, n=None):
    placement = []
    if chance:
        placement.append(rarity(chance))
    if n:
        placement.append(count(n))
    placement += [IN_SQUARE, NO_WATER, heightmap("OCEAN_FLOOR"), BIOME]
    return placement


# --- features ----------------------------------------------------------------------------

def giant(style, log_block, leaf_blocks, min_height, max_height, near_water=False):
    feature = {
        "type": f"{NS}:giant_tree",
        "style": style,
        "log": log(log_block),
        "min_height": min_height,
        "max_height": max_height,
    }
    if leaf_blocks:
        feature["leaves"] = [leaves(b) for b in leaf_blocks]
    if near_water:
        feature["near_water"] = True
    return feature


def boulder(materials, moss=0.0, min_radius=1.5, max_radius=3.2):
    return {
        "type": f"{NS}:boulder",
        "materials": [state(m) for m in materials],
        "moss_chance": moss,
        "min_radius": min_radius,
        "max_radius": max_radius,
    }


def fallen(log_block, min_length=6, max_length=12, thick=0.25):
    return {
        "type": f"{NS}:fallen_log",
        "log": log(log_block),
        "min_length": min_length,
        "max_length": max_length,
        "thick_chance": thick,
    }


def rock(style, materials, banded=False, top=None):
    feature = {"type": f"{NS}:rock_formation", "style": style, "materials": [state(m) for m in materials], "banded": banded}
    if top:
        feature["top"] = state(top)
    return feature


CANYON_BANDS = ["terracotta", "orange_terracotta", "red_terracotta", "yellow_terracotta", "white_terracotta",
                "orange_terracotta", "brown_terracotta", "terracotta", "light_gray_terracotta", "red_terracotta"]
SEA_ROCK = ["stone", "andesite", "stone", "diorite", "stone", "tuff"]

AUTUMN_LEAVES = ["orange_poplar_leaves", "red_poplar_leaves", "yellow_poplar_leaves", "orange_poplar_leaves"]

FEATURES = {
    # name: (feature, placement)
    "cliff_waterfall": ({"type": f"{NS}:cliff_waterfall", "min_drop": 7, "max_channel": 6}, terrain_placement(n=3)),
    "cliff_waterfall_sparse": ({"type": f"{NS}:cliff_waterfall", "min_drop": 6, "max_channel": 5}, terrain_placement(chance=3, n=2)),
    "cascade_pools": ({"type": f"{NS}:cascade_pools", "min_pools": 3, "max_pools": 6}, terrain_placement(chance=6)),
    "alpine_tarn": ({"type": f"{NS}:alpine_tarn", "min_y": 118, "max_radius": 9}, terrain_placement(chance=5)),
    "cave_mouth": ({"type": f"{NS}:cave_mouth", "min_length": 12, "max_length": 26}, terrain_placement(chance=8)),
    "cenote": ({"type": f"{NS}:sinkhole", "water": True, "min_depth": 16, "max_depth": 32}, terrain_placement(chance=40)),
    "sinkhole": ({"type": f"{NS}:sinkhole", "water": False, "min_depth": 10, "max_depth": 22}, terrain_placement(chance=150)),

    "boulder_mossy": (boulder(["mossy_cobblestone", "cobblestone", "stone", "mossy_cobblestone", "andesite"], moss=0.55),
                      surface_placement(chance=3)),
    "boulder_stone": (boulder(["stone", "andesite", "stone", "cobblestone"], moss=0.12, max_radius=3.6), surface_placement(chance=5)),
    "boulder_granite": (boulder(["granite", "stone", "granite", "diorite"], moss=0.2), surface_placement(chance=6)),
    "boulder_snowy": (boulder(["stone", "andesite", "stone", "diorite"], max_radius=3.8), surface_placement(chance=5)),
    "boulder_badlands": (boulder(["red_sandstone", "terracotta", "orange_terracotta", "red_sandstone"], min_radius=2.0, max_radius=4.0),
                         surface_placement(chance=4)),

    "badlands_arch": (rock("arch", CANYON_BANDS, banded=True), terrain_placement(chance=10)),
    "badlands_hoodoos": (rock("hoodoos", CANYON_BANDS, banded=True, top="terracotta"), terrain_placement(chance=4)),
    "desert_arch": (rock("arch", ["sandstone", "sandstone", "smooth_sandstone", "sandstone"], banded=True), terrain_placement(chance=40)),
    "mountain_arch": (rock("arch", ["stone", "andesite", "stone", "granite", "stone"], top="grass_block"), terrain_placement(chance=30)),
    "sea_arch": (rock("arch", SEA_ROCK, top="grass_block"), terrain_placement(chance=12)),
    "sea_stack": (rock("sea_stack", SEA_ROCK, top="grass_block"), terrain_placement(chance=5)),
    "cliff_vines_lush": ({"type": f"{NS}:cliff_vines", "density": 0.45, "min_drop": 4}, [IN_SQUARE, BIOME]),
    "cliff_vines_sparse": ({"type": f"{NS}:cliff_vines", "density": 0.12, "min_drop": 6}, [IN_SQUARE, BIOME]),

    "fallen_oak_log": (fallen("oak_log"), surface_placement(chance=4)),
    "fallen_birch_log": (fallen("birch_log", 5, 10, 0.0), surface_placement(chance=4)),
    "fallen_spruce_log": (fallen("spruce_log", 7, 14, 0.35), surface_placement(chance=4)),
    "fallen_dark_oak_log": (fallen("dark_oak_log", 5, 10, 0.5), surface_placement(chance=3)),
    "fallen_jungle_log": (fallen("jungle_log", 7, 14, 0.4), surface_placement(chance=4)),
    "fallen_poplar_log": (fallen("poplar_log", 6, 12, 0.2), surface_placement(chance=3)),

    "ancient_oak": (giant("ancient", "oak_log", ["oak_leaves"], 18, 28), surface_placement(chance=5)),
    "ancient_oak_rare": (giant("ancient", "oak_log", ["oak_leaves"], 16, 24), surface_placement(chance=40)),
    "ancient_dark_oak": (giant("dark", "dark_oak_log", ["dark_oak_leaves"], 14, 20), surface_placement(chance=4)),
    "autumn_giant": (giant("autumn", "poplar_log", AUTUMN_LEAVES, 14, 22), surface_placement(chance=3)),
    "autumn_giant_rare": (giant("autumn", "poplar_log", AUTUMN_LEAVES, 14, 20), surface_placement(chance=24)),
    "giant_birch": (giant("autumn", "birch_log", ["birch_leaves", "birch_leaves", "yellow_poplar_leaves"], 18, 26),
                    surface_placement(chance=12)),
    "redwood": (giant("redwood", "spruce_log", ["spruce_leaves"], 34, 52), surface_placement(chance=2)),
    "redwood_rare": (giant("redwood", "spruce_log", ["spruce_leaves"], 28, 42), surface_placement(chance=18)),
    "weeping_willow": (giant("willow", "oak_log", ["oak_leaves"], 8, 12), surface_placement(chance=3)),
    "weeping_willow_waterside": (giant("willow", "oak_log", ["oak_leaves"], 8, 12, near_water=True), surface_placement(chance=2)),
    "mangrove_willow": (giant("willow", "mangrove_log", ["mangrove_leaves"], 8, 12), surface_placement(chance=6)),
    "baobab": (giant("baobab", "acacia_log", ["acacia_leaves"], 10, 15), surface_placement(chance=9)),
    "spruce_snag": (giant("snag", "spruce_log", [], 8, 16), surface_placement(chance=9)),
    "oak_snag": (giant("snag", "oak_log", [], 6, 12), surface_placement(chance=14)),
    "acacia_snag": (giant("snag", "acacia_log", [], 5, 9), surface_placement(chance=16)),
}


# Biomes where a mountain river may rise (its spring must also be high above sea level).
RIVER_SOURCE_BIOMES = [
    "meadow", "cherry_grove", "stony_peaks", "jagged_peaks", "frozen_peaks", "snowy_slopes", "grove",
    "windswept_hills", "windswept_forest", "windswept_gravelly_hills", "windswept_savanna", "savanna_plateau",
    "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "snowy_taiga", "forest", "flower_forest",
    "birch_forest", "old_growth_birch_forest", "dark_forest", "dappled_forest", "jungle", "sparse_jungle",
    "bamboo_jungle", "plains", "snowy_plains", "savanna", "badlands", "wooded_badlands",
    "naturesmod:heather_moors", "naturesmod:maple_woods", "naturesmod:glowshroom_forest", "naturesmod:lavender_fields",
    "naturesmod:golden_prairie", "naturesmod:tundra", "naturesmod:volcanic_peaks", "naturesmod:petrified_forest",
]


# Biomes a glacier may start in: its basin must also lie above y=300.
GLACIER_SOURCE_BIOMES = ["frozen_peaks", "jagged_peaks", "snowy_slopes", "stony_peaks"]


def structures():
    return {
        f"{NS}/worldgen/structure/glacier.json": {
            "type": f"{NS}:glacier",
            "biomes": f"#{NS}:has_structure/glacier",
            "spawn_overrides": {},
            "step": "lakes",
        },
        f"{NS}/worldgen/structure_set/glaciers.json": {
            "placement": {
                "type": "minecraft:random_spread",
                "salt": 418235167,
                "separation": 4,
                "spacing": 10,
            },
            "structures": [{"structure": f"{NS}:glacier", "weight": 1}],
        },
        f"{NS}/tags/worldgen/biome/has_structure/glacier.json": {
            "values": [f"minecraft:{b}" for b in GLACIER_SOURCE_BIOMES],
        },
        f"{NS}/worldgen/structure/mountain_river.json": {
            "type": f"{NS}:mountain_river",
            "biomes": f"#{NS}:has_structure/mountain_river",
            "spawn_overrides": {},
            "step": "lakes",
        },
        f"{NS}/worldgen/structure_set/mountain_rivers.json": {
            "placement": {
                "type": "minecraft:random_spread",
                "exclusion_zone": {"chunk_count": 3, "other_set": "minecraft:villages"},
                "salt": 1870551927,
                "separation": 5,
                "spacing": 14,
            },
            "structures": [{"structure": f"{NS}:mountain_river", "weight": 1}],
        },
        f"{NS}/tags/worldgen/biome/has_structure/mountain_river.json": {
            "values": [b if ":" in b else f"minecraft:{b}" for b in RIVER_SOURCE_BIOMES],
        },
    }


# Vanilla ground cover spread to more biomes. Each gets its own placed feature id: re-using the
# vanilla ids in extra biomes would break the global feature order and crash world generation.
GROUND_COVER = {
    "forest_leaf_litter": "patch_leaf_litter",
    "forest_bush": "patch_bush",
    "forest_large_fern": "patch_large_fern",
    "waterside_fireflies": "patch_firefly_bush_near_water",
}


def ground_cover(vanilla):
    files = {}
    for name, source in GROUND_COVER.items():
        placed = vanilla.read(f"minecraft/worldgen/placed_feature/{source}.json")
        files[f"{NS}/worldgen/placed_feature/{name}.json"] = placed
    return files


def build(vanilla):
    files = {}
    for name, (feature, placement) in FEATURES.items():
        files[f"{NS}/worldgen/feature/{name}.json"] = feature
        files[f"{NS}/worldgen/placed_feature/{name}.json"] = {"feature": f"{NS}:{name}", "placement": placement}
    files.update(structures())
    files.update(ground_cover(vanilla))
    return files
