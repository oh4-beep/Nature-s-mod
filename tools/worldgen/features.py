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


def build():
    files = {}
    for name, (feature, placement) in FEATURES.items():
        files[f"{NS}/worldgen/feature/{name}.json"] = feature
        files[f"{NS}/worldgen/placed_feature/{name}.json"] = {"feature": f"{NS}:{name}", "placement": placement}
    return files
