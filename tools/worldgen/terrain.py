"""Terrain overhaul for Nature's Mod.

Derives the Nature's Mod overworld terrain from the vanilla density functions so the
output always matches the exact data format of the targeted Minecraft version.

What changes compared to vanilla:
  * The world is taller: build height goes up to y=511 so mountains have room.
  * Mountain ranges are much taller and sharper: the terrain "offset" spline is
    stretched above the hill line and the jaggedness of peaks is boosted.
  * Biomes, continents, mountain ranges and river valleys are 25% larger, so the
    land reads as broad, believable landscapes instead of noisy patchwork.
  * Oceans are a little deeper.
  * Cave entrances are larger and more common, so hillsides open into caves.
  * A natural snow line caps every peak above ~y=335 (bare rock on steep faces).
"""

import copy

NS = "naturesmod"

MIN_Y = -64
WORLD_TOP = 512                      # exclusive; vanilla is 320
WORLD_HEIGHT = WORLD_TOP - MIN_Y     # 576, a multiple of 16
BLOCKS_PER_DEPTH_UNIT = 128.0        # vanilla slope of the depth gradient (384 blocks / 3.0)
TOP_SLIDE = (448, 464)               # vanilla (240, 256): terrain fades to air across this band

HORIZONTAL_SCALE = 0.8               # < 1 makes climate/terrain features larger
OFFSET_HILL_LINE = 0.25              # offsets above this (~y=95) get stretched...
OFFSET_STRETCH = 1.6                 # ...by this factor
OCEAN_LINE = -0.15                   # offsets below this (ocean floors)...
OCEAN_STRETCH = 1.15                 # ...get deeper by this factor
JAGGEDNESS_SCALE = 1.35              # sharper peaks

ENTRANCE_BIAS = 0.30                 # vanilla 0.37: lower = more cave entrances
ENTRANCE_XZ_SCALE = 0.6              # vanilla 0.75: lower = wider entrances

SNOW_LINE = 335

# Vanilla overworld density functions that get a Nature's Mod variant.
OVERRIDDEN = [
    "continents", "erosion", "ridges", "ridges_folded", "temperature", "vegetation",
    "offset", "factor", "jaggedness", "depth", "sloped_cheese",
    "final_density", "preliminary_surface_level", "chunk_surface_level",
    "caves/entrances",
]
REMAP = {f"minecraft:overworld/{n}": f"{NS}:overworld/{n}" for n in OVERRIDDEN}


def remap_refs(node):
    """Point references at the Nature's Mod variants of the overridden functions."""
    if isinstance(node, str):
        return REMAP.get(node, node)
    if isinstance(node, list):
        return [remap_refs(v) for v in node]
    if isinstance(node, dict):
        return {REMAP.get(k, k): remap_refs(v) for k, v in node.items()}
    return node


def walk(node, fn):
    """Depth-first visit of every dict in a JSON tree."""
    if isinstance(node, dict):
        fn(node)
        for v in node.values():
            walk(v, fn)
    elif isinstance(node, list):
        for v in node:
            walk(v, fn)


def is_y_gradient(node, frm, to):
    return (node.get("type") == "minecraft:gradient" and node.get("axis") == "y"
            and node.get("from_coordinate") == frm and node.get("to_coordinate") == to)


def retarget_heights(node):
    """Extend the depth gradient, move the top slide and raise height caps."""
    def fix(n):
        if is_y_gradient(n, MIN_Y, 320) and n.get("from_value") == 1.5 and n.get("to_value") == -1.5:
            # Same slope as vanilla, just continued to the new world top.
            n["to_coordinate"] = WORLD_TOP
            n["to_value"] = 1.5 - (WORLD_TOP - MIN_Y) / BLOCKS_PER_DEPTH_UNIT
        elif is_y_gradient(n, 240, 256) and n.get("from_value") == 1.0 and n.get("to_value") == 0.0:
            n["from_coordinate"], n["to_coordinate"] = TOP_SLIDE
        elif n.get("type") == "minecraft:clamp" and n.get("max") == 320.0 and n.get("min") == -40.0:
            n["max"] = float(WORLD_TOP)
    walk(node, fix)
    return node


def scale_horizontal(node):
    def fix(n):
        if n.get("type") == "minecraft:noise" and "shift_x" in n:
            n["xz_scale"] = round(n["xz_scale"] * HORIZONTAL_SCALE, 6)
    walk(node, fix)
    return node


def transform_spline_leaves(node, fn):
    """Apply fn(value, derivative) -> (value, derivative) to every numeric spline point."""
    def fix(n):
        if "coordinate" in n and "points" in n:
            for p in n["points"]:
                if isinstance(p["value"], (int, float)):
                    p["value"], p["derivative"] = fn(p["value"], p["derivative"])
    walk(node, fix)
    return node


def offset_curve(v, d):
    if v > OFFSET_HILL_LINE:
        return round(OFFSET_HILL_LINE + (v - OFFSET_HILL_LINE) * OFFSET_STRETCH, 5), round(d * OFFSET_STRETCH, 5)
    if v < OCEAN_LINE:
        return round(OCEAN_LINE + (v - OCEAN_LINE) * OCEAN_STRETCH, 5), round(d * OCEAN_STRETCH, 5)
    return v, d


def jaggedness_curve(v, d):
    return round(v * JAGGEDNESS_SCALE, 5), round(d * JAGGEDNESS_SCALE, 5)


def tune_entrances(node):
    def fix(n):
        if n.get("type") == "minecraft:add" and n.get("right") == 0.37 and isinstance(n.get("left"), dict) \
                and n["left"].get("noise") == "minecraft:cave_entrance":
            n["right"] = ENTRANCE_BIAS
            n["left"]["xz_scale"] = ENTRANCE_XZ_SCALE
    walk(node, fix)
    return node


def build(vanilla):
    """Returns {relative output path: json} for every terrain file."""
    out = {}
    df_dir = "worldgen/density_function/overworld/"

    for name in OVERRIDDEN:
        node = copy.deepcopy(vanilla.read(f"minecraft/{df_dir}{name}.json"))
        if name in ("continents", "erosion", "ridges", "temperature", "vegetation"):
            scale_horizontal(node)
        if name == "offset":
            transform_spline_leaves(node, offset_curve)
        if name == "jaggedness":
            transform_spline_leaves(node, jaggedness_curve)
        if name == "caves/entrances":
            tune_entrances(node)
        retarget_heights(node)
        out[f"{NS}/{df_dir}{name}.json"] = remap_refs(node)

    settings = copy.deepcopy(vanilla.read("minecraft/worldgen/noise_settings/overworld.json"))
    settings["noise"]["height"] = WORLD_HEIGHT
    settings["material_rule"] = f"{NS}:overworld"
    retarget_heights(settings)
    out["minecraft/worldgen/noise_settings/overworld.json"] = remap_refs(settings)

    dim = copy.deepcopy(vanilla.read("minecraft/dimension_type/overworld.json"))
    dim["height"] = WORLD_HEIGHT
    dim["logical_height"] = WORLD_HEIGHT
    dim["min_y"] = MIN_Y
    out["minecraft/dimension_type/overworld.json"] = dim

    out.update(material_rules(vanilla))
    return out


def material_rules(vanilla):
    root = copy.deepcopy(vanilla.read("minecraft/worldgen/material_rule/overworld.json"))
    # Run the snow line before the per-biome surface so it wins on high ground.
    for i, entry in enumerate(root["sequence"]):
        if isinstance(entry, dict) and entry.get("then_run") == "minecraft:overworld/surface":
            entry["then_run"] = {
                "type": "minecraft:sequence",
                "sequence": [f"{NS}:overworld/snow_line", "minecraft:overworld/surface"],
            }
            break
    else:
        raise ValueError("vanilla overworld material rule layout changed")

    block = lambda b: {"type": "minecraft:block", "result_state": b}
    snow_line = {
        "type": "minecraft:condition",
        "if_true": {
            "type": "minecraft:y_above",
            "anchor": {"absolute": SNOW_LINE},
            "surface_depth_multiplier": 4,
            "add_stone_depth": False,
        },
        "then_run": {
            "type": "minecraft:condition",
            "if_true": "minecraft:not_underwater",
            "then_run": {
                "type": "minecraft:sequence",
                "sequence": [
                    {
                        "type": "minecraft:condition",
                        "if_true": {"type": "minecraft:steep"},
                        "then_run": {
                            "type": "minecraft:condition",
                            "if_true": "minecraft:under_floor",
                            "then_run": {
                                "type": "minecraft:sequence",
                                "sequence": [
                                    {
                                        "type": "minecraft:condition",
                                        "if_true": {
                                            "type": "minecraft:noise_threshold",
                                            "noise": "minecraft:packed_ice",
                                            "min_threshold": 0.0,
                                            "max_threshold": 0.15,
                                        },
                                        "then_run": block("minecraft:packed_ice"),
                                    },
                                    block("minecraft:stone"),
                                ],
                            },
                        },
                    },
                    {
                        "type": "minecraft:condition",
                        "if_true": "minecraft:under_floor",
                        "then_run": block("minecraft:snow_block"),
                    },
                ],
            },
        },
    }
    return {
        f"{NS}/worldgen/material_rule/overworld.json": root,
        f"{NS}/worldgen/material_rule/overworld/snow_line.json": snow_line,
    }
