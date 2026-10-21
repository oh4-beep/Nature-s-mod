"""The Overworld's seas: gardens of anemones, sea fans, starfish, urchins and clams on the sea
floor, shells washed up on the beaches, and denser kelp, seagrass, coral and sea pickles.
Which biomes get which feature is decided in ModBiomeModifications."""

NS = "naturesmod"

IN_SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}


def count(n):
    return {"type": "minecraft:count", "count": n}


def heightmap(kind):
    return {"type": "minecraft:heightmap", "heightmap": kind}


def spread(n):
    trapezoid = {"type": "minecraft:trapezoid", "max": 7, "min": -7, "plateau": 0}
    return [IN_SQUARE, count(n), {"type": "minecraft:offset", "x": trapezoid,
                                  "y": {"type": "minecraft:trapezoid", "max": 0, "min": 0, "plateau": 0}, "z": trapezoid}]


WATER_HERE = {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:water"}}
KELP_SPOT = {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
    {"type": "minecraft:matching_blocks", "blocks": "minecraft:water"},
    {"type": "minecraft:matching_blocks", "blocks": "minecraft:water", "offset": [0, 1, 0]},
    {"type": "minecraft:has_sturdy_face", "direction": "up", "offset": [0, -1, 0]},
    {"type": "minecraft:not", "predicate": {"type": "minecraft:matching_block_tag", "offset": [0, -1, 0], "tag": "minecraft:cannot_support_kelp"}},
]}}


def garden(style):
    return {"type": f"{NS}:sea_garden", "style": style}


def build():
    files = {}
    features = {
        "reef_garden": (garden("reef"), [count(6), IN_SQUARE, heightmap("OCEAN_FLOOR_WG"), BIOME]),
        "reef_garden_sparse": (garden("reef"), [count(3), IN_SQUARE, heightmap("OCEAN_FLOOR_WG"), BIOME]),
        "temperate_garden": (garden("temperate"), [count(2), IN_SQUARE, heightmap("OCEAN_FLOOR_WG"), BIOME]),
        "cold_garden": (garden("cold"), [count(2), IN_SQUARE, heightmap("OCEAN_FLOOR_WG"), BIOME]),
        "beach_shells": (garden("beach"), [count(2), IN_SQUARE, heightmap("WORLD_SURFACE_WG"), BIOME]),
        # More of vanilla's own sea life.
        "extra_coral": ("minecraft:warm_ocean_vegetation", [
            {"type": "minecraft:noise_based_count", "noise_factor": 400.0, "noise_to_count_ratio": 30}, IN_SQUARE,
            heightmap("OCEAN_FLOOR_WG"), BIOME]),
        "extra_kelp": ("minecraft:kelp", [
            {"type": "minecraft:noise_based_count", "noise_factor": 80.0, "noise_to_count_ratio": 60}, IN_SQUARE,
            heightmap("OCEAN_FLOOR"), KELP_SPOT, BIOME]),
        "extra_seagrass": ("minecraft:seagrass_tall", spread(32) + [heightmap("OCEAN_FLOOR"), WATER_HERE, BIOME]),
        "extra_sea_pickles": ("minecraft:sea_pickle", [{"type": "minecraft:rarity_filter", "chance": 4}] + spread(20)
                              + [heightmap("OCEAN_FLOOR"), WATER_HERE, BIOME]),
    }
    for name, (feature, placement) in features.items():
        if isinstance(feature, dict):
            files[f"{NS}/worldgen/feature/ocean/{name}.json"] = feature
            feature = f"{NS}:ocean/{name}"
        files[f"{NS}/worldgen/placed_feature/ocean/{name}.json"] = {"feature": feature, "placement": placement}
    return files
