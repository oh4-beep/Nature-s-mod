#!/usr/bin/env python3
"""Converts the Nature's Mod data pack from the formats of Minecraft 26.3, which the generators in tools/ write, to
the formats of Minecraft 26.2, which the mod now targets.

Usage:
    python3 tools/port/to262.py --vanilla /path/to/26.2/data --vanilla-from /path/to/26.3/data

Rewrites src/main/resources/data in place. Files already in the 26.2 formats are left as they are, so it is safe to
run again after regenerating part of the data. --vanilla and --vanilla-from point at the `data` directories of the
vanilla 26.2 and 26.3 data packs (e.g. checkouts of the `26.2-data` and `26.3-data` tags of
https://github.com/misode/mcmeta): the mod's data names parts of vanilla 26.3 that 26.2 does not have, which are
written out in place, and 26.2 keeps the Overworld's ore veins in its noise router.

What changes from 26.3 to 26.2:
  * Configured features live in worldgen/configured_feature and keep their settings under "config"; block states
    are {"Name", "Properties"} objects and block state providers are spelled out.
  * Loot tables, advancements and placement predicates use lists of "conditions" (named by "condition") and
    "functions" (named by "function") instead of a single "condition" or "modifier" named by "type".
  * Density functions take "argument"/"argument1"/"argument2"; 26.3's sub, negate, lerp, div and gradient become
    sums, products, inverses and y_clamped_gradient, and its single cache becomes the cache that fits its input.
  * Noise settings hold their surface rule inline instead of naming a material rule, the aquifer and ore vein
    functions sit in the noise router, the cell size is set once, and the spawn target is a climate point.
  * Noise parameters are "firstOctave" and "amplitudes", without an explicit base amplitude.
  * Biomes and structures list their mob spawns in "spawners"/"spawn_costs" rather than as an attribute, and
    environment attributes take plain values rather than modifiers.
  * Dimension types know no straw bed rule, and a bed "explodes" rather than being destroyed on use.
"""

import argparse
import copy
import json
import os
import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]
DATA = REPO / "src" / "main" / "resources" / "data"


class Context:
    """Where references resolve while converting. A reference names a file of the data pack being converted, else one
    of the vanilla 26.3 data pack it was written against; vanilla 26.2 tells which of those 26.2 has too, and holds
    what 26.2 keeps elsewhere (the Overworld's ore veins)."""

    def __init__(self, data, vanilla262=None, vanilla263=None):
        self.data = pathlib.Path(data)
        self.vanilla262 = pathlib.Path(vanilla262) if vanilla262 else None
        self.vanilla263 = pathlib.Path(vanilla263) if vanilla263 else None

    @staticmethod
    def _find(roots, folder, ref):
        namespace, _, path = ref.partition(":")
        if not path:
            namespace, path = "minecraft", namespace
        for root in roots:
            f = root / namespace / folder / (path + ".json")
            if f.exists():
                return json.loads(f.read_text())
        return None

    def read(self, folder, ref):
        """The file a reference names, as the data pack (or vanilla 26.3) writes it."""
        return self._find([r for r in (self.data, self.vanilla263) if r], folder, ref)

    def in_262(self, folder, ref):
        """Whether the reference still names a file in 26.2: one of the data pack's own, or one vanilla 26.2 has."""
        if self._find([self.data], folder, ref) is not None:
            return True
        return self.vanilla262 is not None and self._find([self.vanilla262], folder, ref) is not None

    def vanilla_read(self, rel):
        if self.vanilla262 is None:
            raise SystemExit("--vanilla is needed to convert " + rel)
        return json.loads((self.vanilla262 / "minecraft" / rel).read_text())


# ----------------------------------------------------------------------------------------------------------------
# Block states and block state providers

# Blocks 26.3 added that the mod uses, and their nearest kin in 26.2. In the Maple Woods, whose foliage color is a deep
# autumn red, the vanilla leaves turn as red as 26.3's poplar leaves.
SUBSTITUTES = {
    "minecraft:poplar_log": "minecraft:birch_log",
    "minecraft:red_poplar_leaves": "minecraft:dark_oak_leaves",
    "minecraft:orange_poplar_leaves": "minecraft:oak_leaves",
    "minecraft:yellow_poplar_leaves": "minecraft:acacia_leaves",
}
# The falling poplar leaves become tinted ones of their color.
LEAF_PARTICLES = {
    "minecraft:red_poplar_leaves": 0xFFB3311E,
    "minecraft:orange_poplar_leaves": 0xFFD8761E,
    "minecraft:yellow_poplar_leaves": 0xFFE0B23A,
}
# Things 26.3 added that 26.2 has nothing like: references to them are left out.
GONE = {"minecraft:dappled_forest", "#minecraft:dappled_forest"}


def state(value):
    """A block state: 26.3 writes "id" or {"id", "properties"}, 26.2 wants {"Name", "Properties"}."""
    if isinstance(value, str):
        return {"Name": SUBSTITUTES.get(value, value)}
    if isinstance(value, dict) and "Name" not in value and "id" in value:
        out = {"Name": SUBSTITUTES.get(value["id"], value["id"])}
        if value.get("properties"):
            out["Properties"] = {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in value["properties"].items()}
        return out
    if isinstance(value, dict) and value.get("Name") in SUBSTITUTES:
        return dict(value, Name=SUBSTITUTES[value["Name"]])
    return value


def drop_gone(value):
    """Leaves references to things 26.2 does not have out of every list."""
    if isinstance(value, dict):
        return {k: drop_gone(v) for k, v in value.items()}
    if isinstance(value, list):
        return [drop_gone(v) for v in value if not (isinstance(v, str) and v in GONE)]
    return value


def signed(argb):
    return argb - (1 << 32) if argb >= 1 << 31 else argb


def provider(value):
    """A block state provider: 26.3 lets a plain state stand for one and shortens the type names."""
    if isinstance(value, dict) and "type" in value:
        kind = value["type"]
        if kind in ("minecraft:weighted", "minecraft:weighted_state_provider"):
            return {"type": "minecraft:weighted_state_provider",
                    "entries": [{"data": state(e["data"]), "weight": e["weight"]} for e in value["entries"]]}
        if kind in ("minecraft:rotated", "minecraft:rotated_block_provider"):
            return {"type": "minecraft:rotated_block_provider", "state": state(value["state"])}
        if kind in ("minecraft:simple", "minecraft:simple_state_provider"):
            return {"type": "minecraft:simple_state_provider", "state": state(value["state"])}
        raise ValueError("unknown block state provider " + kind)
    return {"type": "minecraft:simple_state_provider", "state": state(value)}


# ----------------------------------------------------------------------------------------------------------------
# Conditions, functions and predicates

PREDICATE_STATE_KINDS = {"minecraft:would_survive"}


def condition(value):
    """A loot condition: 26.3 names its kind by "type", 26.2 by "condition"."""
    if not isinstance(value, dict) or "condition" in value:
        return value
    out = {}
    kind = value["type"]
    for key, field in value.items():
        if key == "type":
            continue
        if key in ("term",):
            out[key] = condition(field)
        elif key == "terms":
            out[key] = [condition(t) for t in field]
        else:
            out[key] = field
    if kind == "minecraft:match_block":
        # 26.3 matches any of a set of blocks; 26.2 checks the state of one.
        blocks = out.pop("blocks")
        if isinstance(blocks, list):
            if len(blocks) != 1:
                raise ValueError("match_block over several blocks: %r" % blocks)
            blocks = blocks[0]
        out = {"block": blocks, "properties": out.pop("state", {}), **out}
        kind = "minecraft:block_state_property"
    out["condition"] = kind
    return out


def conditions(holder):
    """Moves a 26.3 "condition" (one condition) into a 26.2 "conditions" list, which needs no all_of around it."""
    if "condition" in holder and isinstance(holder["condition"], dict):
        found = holder.pop("condition")
        if found.get("type") == "minecraft:all_of":
            holder["conditions"] = [condition(c) for c in found["terms"]]
        else:
            holder["conditions"] = [condition(found)]
    elif "conditions" in holder:
        holder["conditions"] = [condition(c) for c in holder["conditions"]]
    return holder


def function(value):
    """A loot function: 26.3 names its kind by "type", 26.2 by "function"."""
    if "function" in value:
        out = dict(value)
    else:
        out = {k: v for k, v in value.items() if k != "type"}
        out["function"] = value["type"]
    conditions(out)
    for key in ("on_pass", "on_fail"):
        if key in out:
            out[key] = function(out[key])
    if "functions" in out:
        out["functions"] = [function(f) for f in out["functions"]]
    if out["function"] == "minecraft:sequence" and "functions" not in out and "modifier" in out:
        out["functions"] = [function(f) for f in listed(out.pop("modifier"))]
    return out


def listed(value):
    return value if isinstance(value, list) else [value]


def functions(holder):
    """Moves a 26.3 "modifier" (one function or a list) into a 26.2 "functions" list."""
    if "modifier" in holder:
        holder["functions"] = [function(f) for f in listed(holder.pop("modifier"))]
    elif "functions" in holder:
        holder["functions"] = [function(f) for f in holder["functions"]]
    return holder


def loot_entry(value):
    out = dict(value)
    conditions(out)
    functions(out)
    if "children" in out:
        out["children"] = [loot_entry(c) for c in out["children"]]
    if isinstance(out.get("value"), dict):
        out["value"] = loot_table(out["value"])
    return out


def loot_table(data):
    out = dict(data)
    functions(out)
    pools = []
    for pool in data.get("pools", []):
        pool = dict(pool)
        conditions(pool)
        functions(pool)
        pool["entries"] = [loot_entry(e) for e in pool.get("entries", [])]
        pools.append(pool)
    if "pools" in data:
        out["pools"] = pools
    return out


def advancement(data):
    out = copy.deepcopy(data)
    for criterion in out.get("criteria", {}).values():
        conds = criterion.get("conditions")
        if not isinstance(conds, dict):
            continue
        for key, value in list(conds.items()):
            # Entity checks (player, entity, ...) are one condition in 26.3 and a list of them in 26.2.
            if isinstance(value, dict) and "type" in value and "predicate" in value:
                conds[key] = [condition(value)]
    return out


def block_predicate(value):
    """Block predicates of placement filters: only the state of would_survive changes."""
    if isinstance(value, dict):
        out = {k: block_predicate(v) for k, v in value.items()}
        if value.get("type") in PREDICATE_STATE_KINDS and "state" in value:
            out["state"] = state(value["state"])
        return out
    if isinstance(value, list):
        return [block_predicate(v) for v in value]
    return value


# ----------------------------------------------------------------------------------------------------------------
# Features

def placement(modifiers):
    out = []
    for m in modifiers:
        m = block_predicate(m)
        if m.get("type") == "minecraft:offset":
            # 26.3 offsets each axis by its own provider; 26.2 spreads x and z alike.
            x, z = m.get("x", 0), m.get("z", 0)
            if x != z:
                raise ValueError("offset spreads x and z differently: %r" % m)
            out.append({"type": "minecraft:random_offset", "xz_spread": x, "y_spread": m.get("y", 0)})
        else:
            out.append(m)
    return out


def placed(value):
    """A placed feature, inline or named; 26.2 names placed features where 26.3 names features."""
    if isinstance(value, str):
        return value
    out = dict(value)
    if isinstance(out.get("feature"), dict):
        out["feature"] = configured(out["feature"])
    out["placement"] = placement(out.get("placement", []))
    return out


STATE_LISTS = {
    "naturesmod:boulder": ("materials",),
    "naturesmod:rock_formation": ("materials",),
    "naturesmod:giant_tree": ("leaves",),
}
STATES = {
    "naturesmod:fallen_log": ("log",),
    "naturesmod:giant_tree": ("log",),
    "naturesmod:rock_formation": ("top",),
}
VANILLA_PLAIN = {"minecraft:multiface_growth"}


def configured(data):
    """A configured feature: 26.3 lays its settings out beside "type", 26.2 keeps them under "config"."""
    if "config" in data:
        return data
    kind = data["type"]
    config = {k: v for k, v in data.items() if k != "type"}
    if kind == "minecraft:simple_block":
        config["to_place"] = provider(config["to_place"])
    elif kind == "minecraft:block_pile":
        config["state_provider"] = provider(config["state_provider"])
    elif kind == "minecraft:ore" or kind == "minecraft:scattered_ore":
        config["targets"] = [dict(t, state=state(t["state"])) for t in config["targets"]]
    elif kind == "minecraft:geode":
        blocks = dict(config["blocks"])
        for key in list(blocks):
            if key.endswith("_provider"):
                blocks[key] = provider(blocks[key])
        blocks["inner_placements"] = [state(s) for s in blocks["inner_placements"]]
        config["blocks"] = blocks
    elif kind == "minecraft:vegetation_patch":
        config["ground_state"] = provider(config["ground_state"])
        config["vegetation_feature"] = placed(config["vegetation_feature"])
    elif kind == "minecraft:random_selector":
        config["default"] = placed(config["default"])
        config["features"] = [dict(f, feature=placed(f["feature"])) for f in config["features"]]
    elif kind.startswith("naturesmod:"):
        for key in STATES.get(kind, ()):
            if key in config:
                config[key] = state(config[key])
        for key in STATE_LISTS.get(kind, ()):
            if key in config:
                config[key] = [state(s) for s in config[key]]
    elif kind not in VANILLA_PLAIN:
        raise ValueError("no 26.2 conversion for feature type " + kind)
    return {"type": kind, "config": config}


def placed_feature(data):
    return {"feature": data["feature"] if isinstance(data["feature"], str) else configured(data["feature"]),
            "placement": placement(data["placement"])}


# ----------------------------------------------------------------------------------------------------------------
# Biomes, structures and dimension types

MOB_CATEGORIES = ("ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature", "water_ambient",
                  "water_creature")
SPAWNS = "minecraft:gameplay/natural_mob_spawns"


def spawner(entry):
    count = entry.get("count", 1)
    if isinstance(count, dict):
        low, high = count["min_inclusive"], count["max_inclusive"]
    else:
        low = high = count
    return {"type": entry["type"], "maxCount": high, "minCount": low, "weight": entry["weight"]}


# Attribute modifiers 26.3 added; 26.2 takes the plain value instead (and knows multiply, maximum, and, or).
NEW_MODIFIERS = {"append", "overlay", "override"}


def attributes(values):
    out = {}
    for key, value in values.items():
        if isinstance(value, dict) and set(value) == {"argument", "modifier"} and value["modifier"] in NEW_MODIFIERS:
            value = value["argument"]
        out[key] = value
    return out


def biome(data):
    out = dict(data)
    attrs = dict(data.get("attributes", {}))
    spawns = attrs.pop(SPAWNS, None)
    probability = attrs.pop("minecraft:gameplay/creature_world_gen_spawn_probability", None)
    if isinstance(probability, dict) and "argument" in probability:
        probability = probability["argument"]
    if probability is not None:
        out["creature_spawn_probability"] = probability
    if isinstance(spawns, dict) and "argument" in spawns:
        spawns = spawns["argument"]
    particles = attrs.get("minecraft:visual/ambient_particles")
    if isinstance(particles, dict) and "argument" in particles:
        particles = particles["argument"]
    if isinstance(particles, list):
        attrs["minecraft:visual/ambient_particles"] = [
            dict(p, particle={"type": "minecraft:tinted_leaves", "color": signed(LEAF_PARTICLES[p["particle"]["type"]])})
            if p.get("particle", {}).get("type") in LEAF_PARTICLES else p for p in particles]
    out["attributes"] = attributes(attrs)
    by_category = (spawns or {}).get("spawns_by_category", {})
    if "spawners" not in data:
        out["spawners"] = {c: [spawner(e) for e in by_category.get(c, [])] for c in MOB_CATEGORIES}
        out["spawn_costs"] = (spawns or {}).get("spawn_costs", {})
    if not out["attributes"]:
        del out["attributes"]
    return out


def structure(data):
    out = copy.deepcopy(data)
    for override in out.get("spawn_overrides", {}).values():
        if "spawns" in override:
            override["spawns"] = [spawner(e) if "count" in e or "minCount" not in e else e for e in override["spawns"]]
    return out


def dimension_type(data):
    out = dict(data)
    attrs = dict(data.get("attributes", {}))
    attrs.pop("minecraft:gameplay/straw_bed_rule", None)
    bed = attrs.get("minecraft:gameplay/bed_rule")
    if isinstance(bed, dict) and "destroy_on_use" in bed:
        bed = dict(bed)
        if bed.pop("destroy_on_use"):
            bed["explodes"] = True
        attrs["minecraft:gameplay/bed_rule"] = bed
    out["attributes"] = attributes(attrs)
    return out


# ----------------------------------------------------------------------------------------------------------------
# Noise parameters and density functions

def noise(data):
    if "firstOctave" in data:
        return data
    amplitudes = data.get("amplitude_modifiers") or [1.0] * data.get("octave_count", 1)
    return {"amplitudes": amplitudes, "firstOctave": data["base_octave"]}


ONE_ARGUMENT = {"minecraft:abs", "minecraft:square", "minecraft:cube", "minecraft:half_negative",
                "minecraft:quarter_negative", "minecraft:squeeze", "minecraft:invert", "minecraft:flat_cache",
                "minecraft:cache_2d", "minecraft:cache_once", "minecraft:cache_all_in_cell", "minecraft:blend_density",
                "minecraft:interpolated"}
TWO_ARGUMENTS = {"minecraft:add", "minecraft:mul", "minecraft:min", "minecraft:max"}
ONLY_262 = {"minecraft:y_clamped_gradient", "minecraft:shifted_noise"}


def const(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def mul(a, b):
    if const(a) and const(b):
        return a * b
    if const(a):
        a, b = b, a
    return {"type": "minecraft:mul", "argument1": a, "argument2": b} if not const(a) else {"type": "minecraft:mul", "argument1": b, "argument2": a}


def add(a, b):
    if const(a) and const(b):
        return a + b
    return {"type": "minecraft:add", "argument1": a, "argument2": b}


def neg(a):
    return -a if const(a) else mul(a, -1.0)


class Density:
    """Converts density functions, telling 2D inputs (which flat_cache suits) from 3D ones (cache_once)."""

    # Kinds whose value changes along y.
    VERTICAL = {"minecraft:gradient", "minecraft:y_clamped_gradient", "minecraft:old_blended_noise", "minecraft:beardifier",
                "minecraft:blend_density", "minecraft:interpolated", "minecraft:end_islands"}

    def __init__(self, ctx):
        self.ctx = ctx
        self.flat_memo = {}

    def is_flat(self, node):
        """Whether a 26.3 density function is the same at every height of a column."""
        if const(node):
            return True
        if isinstance(node, str):
            if node not in self.flat_memo:
                self.flat_memo[node] = None
                target = self.ctx.read("worldgen/density_function", node)
                self.flat_memo[node] = False if target is None else self.is_flat(target)
            return bool(self.flat_memo[node])
        kind = node.get("type")
        if kind in self.VERTICAL:
            return False
        if kind == "minecraft:slice":
            return True
        if kind in ("minecraft:noise", "minecraft:shifted_noise", "minecraft:noise_threshold"):
            return node.get("y_scale", 0.0) == 0.0
        if kind == "minecraft:find_top_surface":
            return True
        return all(self.is_flat(v) for k, v in node.items() if k != "type" and isinstance(v, (dict, str, int, float))
                   and not isinstance(v, bool) and k not in ("noise", "metric", "axis"))

    def convert(self, node):
        if isinstance(node, str):
            if self.ctx.in_262("worldgen/density_function", node):
                return node
            # A function 26.3 names and 26.2 does not have: written out in place.
            target = self.ctx.read("worldgen/density_function", node)
            if target is None:
                raise ValueError("unknown density function " + node)
            return self.convert(target)
        if not isinstance(node, dict):
            return node
        kind = node.get("type")
        if kind is None:
            return node
        if "argument" in node or "argument1" in node or kind.startswith("naturesmod:") or kind in ONLY_262:
            return node
        c = self.convert
        if kind == "minecraft:sub":
            return add(c(node["left"]), neg(c(node["right"])))
        if kind == "minecraft:negate":
            return neg(c(node["input"]))
        if kind == "minecraft:div":
            right = c(node["right"])
            inverse = 1.0 / right if const(right) else {"type": "minecraft:invert", "argument": right}
            return mul(c(node["left"]), inverse)
        if kind == "minecraft:lerp":
            alpha, first, second = c(node["alpha"]), c(node["first"]), c(node["second"])
            if const(first):
                return add(first, mul(alpha, add(second, -first)))
            alpha = alpha if const(alpha) or isinstance(alpha, str) else {"type": "minecraft:cache_once", "argument": alpha}
            return add(mul(first, add(1.0, neg(alpha))), mul(second, alpha))
        if kind == "minecraft:gradient":
            if node.get("axis", "y") != "y":
                raise ValueError("26.2 only has gradients along y")
            return {"type": "minecraft:y_clamped_gradient", "from_y": node["from_coordinate"], "to_y": node["to_coordinate"],
                    "from_value": node["from_value"], "to_value": node["to_value"]}
        if kind == "minecraft:cache":
            inner = node["input"]
            flat = self.is_flat(inner)
            converted = c(inner)
            if flat:
                if isinstance(converted, dict) and converted.get("type") in ("minecraft:shift_a", "minecraft:shift_b"):
                    converted = {"type": "minecraft:cache_2d", "argument": converted}
                return {"type": "minecraft:flat_cache", "argument": converted}
            return {"type": "minecraft:cache_once", "argument": converted}
        if kind == "minecraft:noise":
            out = {"type": "minecraft:noise", "noise": node["noise"], "xz_scale": node.get("xz_scale", 1.0),
                   "y_scale": node.get("y_scale", 1.0)}
            if "shift_x" in node or "shift_y" in node or "shift_z" in node:
                out = {"type": "minecraft:shifted_noise", "noise": node["noise"], "shift_x": c(node.get("shift_x", 0.0)),
                       "shift_y": c(node.get("shift_y", 0.0)), "shift_z": c(node.get("shift_z", 0.0)),
                       "xz_scale": node.get("xz_scale", 1.0), "y_scale": node.get("y_scale", 1.0)}
            return out
        if kind in ("minecraft:shift_a", "minecraft:shift_b", "minecraft:shift"):
            return {"type": kind, "argument": node.get("noise", node.get("argument"))}
        if kind in ONE_ARGUMENT:
            if kind == "minecraft:interpolated":
                return {"type": kind, "argument": c(node["input"])}
            return {"type": kind, "argument": c(node["input"])}
        if kind in TWO_ARGUMENTS:
            left, right = c(node["left"]), c(node["right"])
            if kind == "minecraft:add" and isinstance(right, dict) and right.get("type") == "minecraft:beardifier":
                # 26.2 adds the beardifier to the final density itself.
                return left
            return {"type": kind, "argument1": left, "argument2": right}
        if kind == "minecraft:clamp":
            return {"type": kind, "input": c(node["input"]), "min": node["min"], "max": node["max"]}
        if kind == "minecraft:range_choice":
            return {"type": kind, "input": c(node["input"]), "min_inclusive": node["min_inclusive"],
                    "max_exclusive": node["max_exclusive"], "when_in_range": c(node["when_in_range"]),
                    "when_out_of_range": c(node["when_out_of_range"])}
        if kind == "minecraft:spline":
            return {"type": kind, "spline": self.spline(node["spline"])}
        if kind == "minecraft:find_top_surface":
            return {"type": kind, "density": c(node["density"]), "upper_bound": c(node["upper_bound"]),
                    "lower_bound": node["lower_bound"], "cell_height": node["cell_height"]}
        if kind == "minecraft:interval_select":
            return {"type": kind, "input": c(node["input"]), "thresholds": node["thresholds"],
                    "functions": [c(f) for f in node["functions"]]}
        if kind == "minecraft:beardifier":
            return 0.0
        if kind in ("minecraft:blend_alpha", "minecraft:blend_offset", "minecraft:end_islands", "minecraft:old_blended_noise",
                    "minecraft:constant"):
            return node
        if kind == "minecraft:end_outer_islands":
            return {"type": "minecraft:end_islands"}
        if kind in ("minecraft:slice", "minecraft:distance_to_point"):
            # No vanilla 26.2 equivalent: Nature's Mod provides these two itself.
            out = {k: c(v) if k == "input" else v for k, v in node.items()}
            out["type"] = "naturesmod:" + kind.split(":")[1]
            return out
        raise ValueError("no 26.2 conversion for density function " + kind)

    def spline(self, spline):
        if const(spline):
            return spline
        return {"coordinate": self.convert(spline["coordinate"]),
                "points": [dict(p, value=self.spline(p["value"])) for p in spline["points"]]}


# ----------------------------------------------------------------------------------------------------------------
# Surface rules

class Surface:
    """Inlines 26.3's named material rules and conditions into one 26.2 surface rule."""

    def __init__(self, ctx):
        self.ctx = ctx
        # 26.3 places ore veins by material rules; 26.2 by the noise router, so they only switch its veins on.
        self.ore_veins = []

    def vanilla_262(self, ref):
        """The vanilla rules whose 26.2 counterparts are parts of 26.2's own surface rules, taken whole from there: 26.3
        changed some of their content."""
        if ref in ("minecraft:overworld/surface", "minecraft:overworld/underground"):
            sequence = self.ctx.vanilla_read("worldgen/noise_settings/overworld.json")["surface_rule"]["sequence"]
            branch = next(i for i, r in enumerate(sequence)
                          if r.get("if_true", {}).get("type") == "minecraft:above_preliminary_surface")
            if ref == "minecraft:overworld/surface":
                return sequence[branch]["then_run"]
            return {"type": "minecraft:sequence", "sequence": sequence[branch + 1:]}
        if ref == "minecraft:nether":
            return self.ctx.vanilla_read("worldgen/noise_settings/nether.json")["surface_rule"]
        return None

    def rule(self, node):
        if isinstance(node, str):
            vanilla = self.vanilla_262(node)
            if vanilla is not None:
                return copy.deepcopy(vanilla)
            target = self.ctx.read("worldgen/material_rule", node)
            if target is None:
                raise ValueError("unknown material rule " + node)
            return self.rule(target)
        kind = node["type"]
        if kind == "minecraft:ore_vein":
            self.ore_veins.append(node)
            return None
        if kind == "minecraft:sequence":
            return {"type": kind, "sequence": [r for r in (self.rule(r) for r in node["sequence"]) if r is not None]}
        if kind == "minecraft:condition":
            then_run = self.rule(node["then_run"])
            if then_run is None:
                return None
            return {"type": kind, "if_true": self.condition(node["if_true"]), "then_run": then_run}
        if kind == "minecraft:block":
            return {"type": kind, "result_state": state(node["result_state"])}
        if kind in ("minecraft:bandlands",):
            return node
        raise ValueError("no 26.2 conversion for material rule " + kind)

    def condition(self, node):
        if isinstance(node, str):
            target = self.ctx.read("worldgen/material_condition", node)
            if target is None:
                raise ValueError("unknown material condition " + node)
            return self.condition(target)
        kind = node["type"]
        if kind == "minecraft:not":
            return {"type": kind, "invert": self.condition(node.get("invert", node.get("input")))}
        if kind == "minecraft:biome":
            biomes = node.get("biome_is", node.get("biomes"))
            if isinstance(biomes, str):
                biomes = [biomes]
            return {"type": kind, "biome_is": biomes}
        return node


# ----------------------------------------------------------------------------------------------------------------
# Noise settings

ROUTER_ZERO = ("barrier", "fluid_level_floodedness", "fluid_level_spread", "lava", "vein_toggle", "vein_ridged", "vein_gap")
CLIMATE = {"continents": "continentalness", "erosion": "erosion", "ridges": "weirdness", "temperature": "temperature",
           "vegetation": "humidity", "depth": "depth"}


def noise_settings(data, ctx, rel):
    if "surface_rule" in data:
        return data
    density = Density(ctx)
    out = {}
    aquifers = data.get("aquifers")
    out["aquifers_enabled"] = aquifers is not None
    out["default_block"] = state(data["default_block"])
    out["default_fluid"] = state(data["default_fluid"])
    out["disable_mob_generation"] = data.get("disable_mob_generation", False)
    out["legacy_random_source"] = data.get("legacy_random_source", False)
    cells = cell_size(data["noise_router"]["final_density"], ctx)
    out["noise"] = {"height": data["noise"]["height"], "min_y": data["noise"]["min_y"],
                    "size_horizontal": cells[0] // 4, "size_vertical": cells[1] // 4}
    router = {}
    old = data["noise_router"]
    for key in ("continents", "depth", "erosion", "ridges", "temperature", "vegetation", "final_density"):
        router[key] = density.convert(old.get(key, 0.0))
    for key in ROUTER_ZERO:
        router[key] = 0.0
    if aquifers is not None:
        for key in ("barrier", "fluid_level_floodedness", "fluid_level_spread", "lava"):
            router[key] = density.convert(aquifers.get(key, 0.0))
    surface = old.get("chunk_surface_level")
    if aquifers is not None and "surface_level" in aquifers:
        surface = aquifers["surface_level"]
    router["preliminary_surface_level"] = density.convert(surface if surface is not None else 0.0)
    out["noise_router"] = router
    surface_rules = Surface(ctx)
    rule = surface_rules.rule(data["material_rule"])
    out["ore_veins_enabled"] = bool(surface_rules.ore_veins)
    if surface_rules.ore_veins:
        # The only ore veins are the Overworld's copper and iron, which 26.2 makes from these three functions.
        vanilla_router = ctx.vanilla_read("worldgen/noise_settings/overworld.json")["noise_router"]
        for key in ("vein_toggle", "vein_ridged", "vein_gap"):
            router[key] = vanilla_router[key]
    out["sea_level"] = data["sea_level"]
    out["spawn_target"] = [spawn_point(p) for p in data.get("spawn_target", [])]
    out["surface_rule"] = rule
    return out


def cell_size(final_density, ctx):
    """The cell size of the first interpolated function in the final density: 26.2 sets it once for a dimension."""
    found = []

    def walk(node, depth=0):
        if found or depth > 40:
            return
        if isinstance(node, str):
            target = ctx.read("worldgen/density_function", node)
            if target is not None:
                walk(target, depth + 1)
        elif isinstance(node, dict):
            if node.get("type") == "minecraft:interpolated" and "cell_size_xz" in node:
                found.append((node["cell_size_xz"], node["cell_size_y"]))
                return
            for v in node.values():
                walk(v, depth + 1)
        elif isinstance(node, list):
            for v in node:
                walk(v, depth + 1)

    walk(final_density)
    return found[0] if found else (4, 8)


def spawn_point(point):
    out = {"continentalness": 0.0, "depth": 0.0, "erosion": 0.0, "humidity": 0.0, "offset": 0.0, "temperature": 0.0,
           "weirdness": 0.0}
    for key, value in point.items():
        name = key.split("/")[-1]
        if name in CLIMATE:
            out[CLIMATE[name]] = value
        elif key in out:
            out[key] = value
        else:
            raise ValueError("unknown spawn target parameter " + key)
    return out


# ----------------------------------------------------------------------------------------------------------------

def vanilla_loot_override(rel, data, ctx):
    """A vanilla loot table the mod adds pools to: 26.2's own table with the mod's pools added, since 26.3 changed some
    of vanilla's loot (its treasure maps, for one)."""
    table = ctx.vanilla_read(rel)
    added = [p for p in loot_table(data)["pools"] if "naturesmod" in json.dumps(p)]
    table["pools"] = table["pools"] + added
    return table


def convert(rel, data, ctx, namespace=None):
    """Converts one data file, given by its path below its namespace. Returns the files it becomes (path, data)."""
    if namespace == "minecraft" and rel.startswith("loot_table/") and ctx.vanilla262 is not None \
            and (ctx.vanilla262 / "minecraft" / rel).exists():
        return [(rel, vanilla_loot_override(rel, data, ctx))]
    data = drop_gone(data)
    parts = rel.split("/")
    if parts[0] == "worldgen" and parts[1] == "feature":
        return [("worldgen/configured_feature/" + "/".join(parts[2:]), configured(data))]
    if parts[0] == "worldgen" and parts[1] == "placed_feature":
        return [(rel, placed_feature(data))]
    if parts[0] == "loot_table":
        return [(rel, loot_table(data))]
    if parts[0] == "tags":
        return [(rel, drop_gone(data))]
    if parts[0] == "advancement":
        return [(rel, advancement(data))]
    if parts[0] == "worldgen" and parts[1] == "biome":
        return [(rel, biome(data))]
    if parts[0] == "worldgen" and parts[1] == "structure":
        return [(rel, structure(data))]
    if parts[0] == "dimension_type":
        return [(rel, dimension_type(data))]
    if parts[0] == "worldgen" and parts[1] == "noise":
        return [(rel, noise(data))]
    if parts[0] == "worldgen" and parts[1] == "density_function":
        return [(rel, Density(ctx).convert(data) if isinstance(data, dict) else data)]
    if parts[0] == "worldgen" and parts[1] == "noise_settings":
        return [(rel, noise_settings(data, ctx, rel))]
    if parts[0] == "worldgen" and parts[1] in ("material_rule", "material_condition"):
        return []
    return [(rel, data)]


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--vanilla", required=True, help="the data directory of the vanilla 26.2 data pack")
    parser.add_argument("--vanilla-from", required=True, help="the data directory of the vanilla 26.3 data pack")
    parser.add_argument("--data", default=str(DATA), help="the data directory to convert (default: the mod's)")
    args = parser.parse_args()
    data_dir = pathlib.Path(args.data)
    ctx = Context(data_dir, args.vanilla, args.vanilla_from)
    # Everything is converted from the files as they are before any is rewritten: conversions read each other.
    written = {}
    removed = []
    for f in sorted(data_dir.rglob("*.json")):
        rel = f.relative_to(data_dir).as_posix()
        namespace, _, rel = rel.partition("/")
        results = convert(rel, json.loads(f.read_text()), ctx, namespace)
        if [r for r, _ in results] != [rel]:
            removed.append(f)
        for new_rel, new in results:
            written[namespace + "/" + new_rel] = new
    for f in removed:
        f.unlink()
    for new_rel, new in written.items():
        target = data_dir / new_rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(new, indent=2) + "\n")
    for folder in sorted({p.parent for p in removed}, key=lambda p: -len(p.parts)):
        while folder != data_dir and folder.exists() and not any(folder.iterdir()):
            folder.rmdir()
            folder = folder.parent
    print("%d files written, %d moved or folded into others" % (len(written), len(removed)))


if __name__ == "__main__":
    main()
