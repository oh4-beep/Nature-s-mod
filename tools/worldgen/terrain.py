"""Terrain overhaul for Nature's Mod.

Derives the Nature's Mod overworld terrain from the vanilla density functions so the
output always matches the exact data format of the targeted Minecraft version.

What changes compared to vanilla:
  * The world is taller: build height goes up to y=575 so mountains have room.
  * Mountain ranges are far taller: the terrain "offset" spline is reshaped above the
    hill line so foothills rise gently while great ranges tower two to three times
    higher than vanilla, topping out around y=420-480. Peaks are more jagged.
  * Continents, mountain ranges and river valleys are ~40% larger and biomes 25%
    larger, so mountains stay majestic instead of spiky and the land reads as broad,
    believable landscapes instead of noisy patchwork.
  * Oceans are a little deeper.
  * Cave entrances are larger and more common, so hillsides open into caves.
  * A natural snow line caps every peak above ~y=290 (bare rock on steep faces).
"""

import copy

NS = "naturesmod"

MIN_Y = -64
WORLD_TOP = 576                      # exclusive; vanilla is 320
WORLD_HEIGHT = WORLD_TOP - MIN_Y     # 640, a multiple of 16
BLOCKS_PER_DEPTH_UNIT = 128.0        # vanilla slope of the depth gradient (384 blocks / 3.0)
TOP_SLIDE = (496, 512)               # vanilla (240, 256): terrain fades to air across this band

CLIMATE_SCALE = 0.8                  # temperature/vegetation noise: < 1 makes biomes larger
LANDFORM_SCALE = 0.7                 # continents/erosion/ridges noise: < 1 makes landforms larger

# The offset spline's leaf values map to surface heights as y = 63.5 + 128 * value. Above the
# hill line (value 0.25, y = 95.5) the height gained over the hill line is reshaped by this
# curve: (vanilla blocks above the hill line, Nature's Mod blocks above it). Foothills rise a
# little faster, big ranges grow 2.3-2.5x and the very highest flatten out below the top slide.
HILL_LINE = 0.25
MOUNTAIN_CURVE = [(0, 0), (32, 58), (64, 135), (96, 215), (128, 280), (160, 330), (200, 370)]
MOUNTAIN_CURVE_START_SLOPE = 1.3
OCEAN_LINE = -0.15                   # offsets below this (ocean floors)...
OCEAN_STRETCH = 1.15                 # ...get deeper by this factor
JAGGEDNESS_SCALE = 1.5               # sharper peaks

ENTRANCE_BIAS = 0.30                 # vanilla 0.37: lower = more cave entrances
ENTRANCE_XZ_SCALE = 0.6              # vanilla 0.75: lower = wider entrances

SNOW_LINE = 290

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


def scale_horizontal(node, scale):
    def fix(n):
        if n.get("type") == "minecraft:noise" and "shift_x" in n:
            n["xz_scale"] = round(n["xz_scale"] * scale, 6)
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


def _curve_tangents(points, start_slope):
    """Fritsch-Carlson tangents: a monotone cubic through the points, no overshoot."""
    secants = [(y1 - y0) / (x1 - x0) for (x0, y0), (x1, y1) in zip(points, points[1:])]
    tangents = [start_slope]
    for a, b in zip(secants, secants[1:]):
        tangents.append(0.0 if a * b <= 0 else 2 * a * b / (a + b))
    tangents.append(secants[-1])
    return tangents


_MOUNTAIN_TANGENTS = _curve_tangents(MOUNTAIN_CURVE, MOUNTAIN_CURVE_START_SLOPE)


def mountain_curve(dy):
    """Maps vanilla blocks above the hill line to Nature's Mod blocks; returns (height, slope)."""
    pts, tans = MOUNTAIN_CURVE, _MOUNTAIN_TANGENTS
    if dy >= pts[-1][0]:
        return pts[-1][1] + tans[-1] * (dy - pts[-1][0]), tans[-1]
    for i in range(len(pts) - 1):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        if x0 <= dy <= x1:
            h = x1 - x0
            t = (dy - x0) / h
            m0, m1 = tans[i] * h, tans[i + 1] * h
            t2, t3 = t * t, t * t * t
            value = (2 * t3 - 3 * t2 + 1) * y0 + (t3 - 2 * t2 + t) * m0 + (-2 * t3 + 3 * t2) * y1 + (t3 - t2) * m1
            slope = ((6 * t2 - 6 * t) * y0 + (3 * t2 - 4 * t + 1) * m0 + (-6 * t2 + 6 * t) * y1 + (3 * t2 - 2 * t) * m1) / h
            return value, slope
    raise ValueError(dy)


def offset_curve(v, d):
    if v > HILL_LINE:
        height, slope = mountain_curve((v - HILL_LINE) * BLOCKS_PER_DEPTH_UNIT)
        return round(HILL_LINE + height / BLOCKS_PER_DEPTH_UNIT, 5), round(d * slope, 5)
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
        if name in ("continents", "erosion", "ridges"):
            scale_horizontal(node, LANDFORM_SCALE)
        if name in ("temperature", "vegetation"):
            scale_horizontal(node, CLIMATE_SCALE)
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
