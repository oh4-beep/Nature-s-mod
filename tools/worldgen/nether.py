"""The Nether overhaul.

The Nether is twice as tall (256 blocks). Its lower half keeps the classic Nether, with its
lava sea at y=32, so fortresses, bastions and the old biomes feel at home. Above it opens a second
world of vast caverns: islands of netherrack float in the open air, colossal pillars join the
floor to the roof, and three new biomes live up there and down below:

  * Ember Wastes (lower Nether): dunes of ash and blackstone, rivers of magma, erupting geysers,
    blazes roaming in the open.
  * Glowstone Grotto (upper caverns): huge glowstone chandeliers hanging from the roof over
    teal and crimson moss, piglins hunting for the gold.
  * Obsidian Spires (upper caverns): a forest of black obsidian spires weeping purple tears,
    soul fire, wither skeletons and ghasts.

Biomes are picked by temperature and humidity as in vanilla, plus a "depth" axis that rises with
height so the upper caverns get their own biomes.
"""

import copy

from abyss import noise_json

NS = "naturesmod"
DF = f"{NS}:nether"
HEIGHT = 256
UPPER = 110            # the upper caverns' biomes take over from here up


def add(a, b):
    return {"type": "minecraft:add", "left": a, "right": b}


def mul(a, b):
    return {"type": "minecraft:mul", "left": a, "right": b}


def mx(a, b):
    return {"type": "minecraft:max", "left": a, "right": b}


def mn(a, b):
    return {"type": "minecraft:min", "left": a, "right": b}


def clamp(x, lo, hi):
    return {"type": "minecraft:clamp", "input": x, "min": lo, "max": hi}


def neg_abs(x):
    return mul({"type": "minecraft:abs", "input": x}, -1.0)


def gradient_y(y0, v0, y1, v1):
    return {"type": "minecraft:gradient", "axis": "y", "from_coordinate": y0, "from_value": v0, "to_coordinate": y1, "to_value": v1}


def lerp(alpha, first, second):
    return {"type": "minecraft:lerp", "alpha": alpha, "first": first, "second": second}


def noise(name, xz=1.0, y=1.0):
    return {"type": "minecraft:noise", "noise": f"{NS}:nether/{name}", "xz_scale": xz, "y_scale": y}


def ref(name):
    return f"{DF}/{name}"


def band(y0, y1, y2, y3):
    """0 below y0, rising to 1 at y1, 1 up to y2, falling to 0 at y3."""
    return mn(clamp(gradient_y(y0, 0.0, y1, 1.0), 0.0, 1.0), clamp(gradient_y(y2, 1.0, y3, 0.0), 0.0, 1.0))


NOISES = {
    "islands": {"firstOctave": -6, "amplitudes": [1.0, 0.6, 0.3]},
    "pillar_a": {"firstOctave": -7, "amplitudes": [1.0]},
    "pillar_b": {"firstOctave": -7, "amplitudes": [1.0]},
    "pillar_wobble": {"firstOctave": -4, "amplitudes": [1.0, 0.5]},
    "caverns": {"firstOctave": -8, "amplitudes": [1.0, 0.5]},
}


def density_functions():
    out = {}
    # The classic Nether noise, stretched a little taller.
    out["lower"] = {"type": "minecraft:old_blended_noise", "smear_scale_multiplier": 8.0, "xz_factor": 80.0, "xz_scale": 0.25,
                    "y_factor": 80.0, "y_scale": 0.3}
    # The upper caverns: the same kind of noise with features twice the size, mostly open air
    # where the cavern noise says so.
    cavern_bias = add(-0.22, mul(noise("caverns", 1.0, 0.0), 0.25))
    out["upper"] = add({"type": "minecraft:old_blended_noise", "smear_scale_multiplier": 8.0, "xz_factor": 170.0, "xz_scale": 0.25,
                        "y_factor": 150.0, "y_scale": 0.18}, cavern_bias)
    # Floating islands drift in the middle of the caverns. Outside their band they must be well
    # below zero rather than zero: the terrain takes the max, and a zero would fill every cave.
    out["islands"] = add(mul(add(noise("islands", 1.0, 1.6), -0.38), 5.0), mul(add(band(124, 140, 180, 200), -1.0), 8.0))
    # Colossal pillars stand where two winding lines cross, rooted below and holding up the roof.
    wobble = mul(noise("pillar_wobble", 1.0, 0.5), 0.02)
    width = add(0.045, add(wobble, mul(band(60, 90, 200, 236), -0.012)))
    pillar = mn(add(width, neg_abs(noise("pillar_a", 1.0, 0.0))), add(width, neg_abs(noise("pillar_b", 1.0, 0.0))))
    out["pillars"] = add(mul(pillar, 40.0), mul(add(band(40, 70, 220, 250), -1.0), 40.0))
    terrain = lerp(clamp(gradient_y(96, 0.0, 150, 1.0), 0.0, 1.0), ref("lower"), ref("upper"))
    terrain = mx(mx(terrain, ref("islands")), ref("pillars"))
    slid = lerp(clamp(gradient_y(-8, 0.0, 24, 1.0), 0.0, 1.0), 2.5,
                lerp(clamp(gradient_y(226, 1.0, 252, 0.0), 0.0, 1.0), 0.9375, terrain))
    out["final_density"] = add(
        {"type": "minecraft:squeeze", "input": {"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 8,
                                                "input": mul({"type": "minecraft:blend_density", "input": slid}, 0.64)}},
        {"type": "minecraft:beardifier"},
    )
    out["depth"] = clamp(mul(add("minecraft:y", -float(UPPER)), 1.0 / 80.0), -0.5, 1.6)
    return out


def noise_settings(vanilla):
    settings = copy.deepcopy(vanilla.read("minecraft/worldgen/noise_settings/nether.json"))
    settings["noise"]["height"] = HEIGHT
    settings["material_rule"] = f"{NS}:nether"
    router = settings["noise_router"]
    router["final_density"] = ref("final_density")
    router["depth"] = ref("depth")
    settings.pop("debug_functions", None)
    return settings


def dimension_type(vanilla):
    dim = copy.deepcopy(vanilla.read("minecraft/dimension_type/the_nether.json"))
    dim["height"] = HEIGHT
    dim["logical_height"] = HEIGHT
    return dim


# --- biomes ---------------------------------------------------------------------------------

def spawn(entity, weight, lo, hi):
    count = lo if lo == hi else {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}
    return {"type": f"minecraft:{entity}", "count": count, "weight": weight}


VANILLA_DECORATION = ["minecraft:spring_open", "minecraft:patch_fire", "minecraft:glowstone_extra", "minecraft:glowstone",
                      "minecraft:ore_magma", "minecraft:spring_closed", "minecraft:ore_gravel_nether", "minecraft:ore_blackstone",
                      "minecraft:ore_gold_nether", "minecraft:ore_quartz_nether", "minecraft:ore_ancient_debris_large",
                      "minecraft:ore_debris_small"]


def biome(fog, music, ambient, particles, monsters, creatures, decoration, vegetation, local=()):
    attributes = {
        "minecraft:audio/ambient_sounds": {
            "additions": {"sound": f"minecraft:ambient.{ambient}.additions", "tick_chance": 0.0111},
            "loop": f"minecraft:ambient.{ambient}.loop",
            "mood": {"block_search_extent": 8, "offset": 2.0, "sound": f"minecraft:ambient.{ambient}.mood", "tick_delay": 6000},
        },
        "minecraft:audio/background_music": {
            "default": {"max_delay": 24000, "min_delay": 12000, "sound": f"minecraft:music.nether.{music}"},
        },
        "minecraft:gameplay/natural_mob_spawns": {
            "argument": {"spawn_costs": {}, "spawns_by_category": {"monster": monsters, **({"creature": creatures} if creatures else {})}},
            "modifier": "overlay",
        },
        "minecraft:visual/fog_color": fog,
    }
    if particles:
        attributes["minecraft:visual/ambient_particles"] = {
            "argument": [{"particle": {"type": p}, "probability": chance} for p, chance in particles],
            "modifier": "append",
        }
    steps = [[] for _ in range(10)]
    steps[2] = list(local)
    steps[7] = list(decoration)
    steps[9] = list(vegetation)
    return {
        "attributes": attributes,
        "carvers": "minecraft:nether_cave",
        "downfall": 0.0,
        "effects": {"water_color": "#3f76e4"},
        "features": steps,
        "has_precipitation": False,
        "temperature": 2.0,
    }


def biomes():
    return {
        "ember_wastes": biome(
            "#4a1806", "basalt_deltas", "basalt_deltas", [("minecraft:ash", 0.06), ("minecraft:lava", 0.0008)],
            [spawn("magma_cube", 100, 2, 5), spawn("blaze", 30, 1, 2), spawn("ghast", 40, 1, 1), spawn("zombified_piglin", 20, 2, 4)],
            [spawn("strider", 60, 1, 2)],
            VANILLA_DECORATION + [f"{NS}:nether/geyser_field", f"{NS}:nether/lavafall", f"{NS}:nether/ember_spikes"],
            ["minecraft:spring_lava"],
        ),
        "glowstone_grotto": biome(
            "#5c3a12", "crimson_forest", "crimson_forest", [("minecraft:white_ash", 0.02), ("minecraft:crimson_spore", 0.01)],
            [spawn("piglin", 30, 2, 4), spawn("zombified_piglin", 40, 2, 4), spawn("ghast", 25, 1, 1), spawn("enderman", 5, 1, 2)],
            [spawn("strider", 30, 1, 2)],
            VANILLA_DECORATION + [f"{NS}:nether/glowstone_chandelier", f"{NS}:nether/lavafall"],
            ["minecraft:spring_lava", "minecraft:weeping_vines", "minecraft:crimson_fungi", "minecraft:warped_fungi",
             "minecraft:crimson_forest_vegetation"],
        ),
        "obsidian_spires": biome(
            "#180a24", "soul_sand_valley", "soul_sand_valley", [("minecraft:dripping_obsidian_tear", 0.012), ("minecraft:soul", 0.002)],
            [spawn("wither_skeleton", 40, 1, 3), spawn("ghast", 35, 1, 1), spawn("enderman", 12, 1, 3), spawn("magma_cube", 20, 1, 3),
             spawn("skeleton", 10, 1, 2)],
            [],
            # Soul fire takes the place of fire: features must keep the same order in every Nether biome.
            [f.replace("patch_fire", "patch_soul_fire") for f in VANILLA_DECORATION] + [f"{NS}:nether/hanging_cage", f"{NS}:nether/lavafall"],
            ["minecraft:spring_lava"],
            local=[f"{NS}:nether/obsidian_spire"],
        ),
    }


# --- surface ------------------------------------------------------------------------------

def block(b):
    return {"type": "minecraft:block", "result_state": b if ":" in b else f"minecraft:{b}"}


def cond(test, then):
    return {"type": "minecraft:condition", "if_true": test, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def in_biome(name):
    return {"type": "minecraft:biome", "biome_is": f"{NS}:{name}"}


def threshold(noise_name, lo, hi=1.0e9):
    return {"type": "minecraft:noise_threshold", "noise": noise_name, "min_threshold": lo, "max_threshold": hi}


def material_rule():
    floor = "minecraft:on_floor"
    under_floor = "minecraft:under_floor"
    ceiling = "minecraft:on_ceiling"
    patch = "minecraft:patch"
    selector = "minecraft:nether_state_selector"
    ember = cond(in_biome("ember_wastes"), seq(
        cond(ceiling, block("basalt")),
        cond(floor, seq(cond(threshold(patch, 0.35), block("magma_block")), cond(threshold(selector, 0.1), block("smooth_basalt")),
                        block("blackstone"))),
        cond(under_floor, seq(cond(threshold(selector, 0.0), block("blackstone")), block("basalt"))),
    ))
    grotto = cond(in_biome("glowstone_grotto"), seq(
        cond(floor, seq(cond(threshold(patch, 0.25), block("warped_nylium")), cond(threshold(patch, -1.0e9, -0.3), block("crimson_nylium")),
                        cond(threshold(selector, 0.6), block("gilded_blackstone")), block("netherrack"))),
    ))
    spires = cond(in_biome("obsidian_spires"), seq(
        cond(ceiling, block("blackstone")),
        cond(floor, seq(cond(threshold(patch, 0.45), block("crying_obsidian")), cond(threshold(selector, 0.3), block("soul_soil")),
                        block("blackstone"))),
        cond(under_floor, block("basalt")),
    ))
    return seq("minecraft:bedrock_floor", "minecraft:bedrock_roof", ember, grotto, spires, "minecraft:nether")


# --- features -----------------------------------------------------------------------------

def count(n):
    return {"type": "minecraft:count", "count": n}


def rarity(chance):
    return {"type": "minecraft:rarity_filter", "chance": chance}


IN_SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}
ANYWHERE = {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 8},
                                                       "max_inclusive": {"below_top": 8}}}


def nether_feature(style):
    return {"type": f"{NS}:nether_feature", "style": style}


def features():
    out = {
        "lavafall": (nether_feature("lavafall"), [count(6), IN_SQUARE, ANYWHERE, BIOME]),
        "lavafall_sparse": (nether_feature("lavafall"), [count(2), IN_SQUARE, ANYWHERE, BIOME]),
        "geyser_field": (nether_feature("geyser_field"), [count(3), IN_SQUARE, ANYWHERE, BIOME]),
        "geyser_field_sparse": (nether_feature("geyser_field"), [rarity(2), IN_SQUARE, ANYWHERE, BIOME]),
        "ember_spikes": (nether_feature("ember_spikes"), [count(4), IN_SQUARE, ANYWHERE, BIOME]),
        "glowstone_chandelier": (nether_feature("glowstone_chandelier"), [count(5), IN_SQUARE, ANYWHERE, BIOME]),
        "obsidian_spire": (nether_feature("obsidian_spire"), [count(4), IN_SQUARE, ANYWHERE, BIOME]),
        "hanging_cage": (nether_feature("hanging_cage"), [rarity(2), IN_SQUARE, ANYWHERE, BIOME]),
        "hanging_cage_rare": (nether_feature("hanging_cage"), [rarity(6), IN_SQUARE, ANYWHERE, BIOME]),
    }
    files = {}
    for name, (feature, placement) in out.items():
        files[f"{NS}/worldgen/feature/nether/{name}.json"] = feature
        files[f"{NS}/worldgen/placed_feature/nether/{name}.json"] = {"feature": f"{NS}:nether/{name}", "placement": placement}
    return files


def loot():
    return {
        f"{NS}/loot_table/chests/nether_cage.json": {
            "type": "minecraft:chest",
            "pools": [
                {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
                    {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 15,
                     "modifier": {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 5}}},
                    {"type": "minecraft:item", "name": "minecraft:bone", "weight": 20,
                     "modifier": {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}},
                    {"type": "minecraft:item", "name": "minecraft:gold_nugget", "weight": 15,
                     "modifier": {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 4, "max": 12}}},
                    {"type": "minecraft:item", "name": "minecraft:quartz", "weight": 10,
                     "modifier": {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 3, "max": 9}}},
                    {"type": "minecraft:item", "name": "minecraft:diamond", "weight": 3},
                    {"type": "minecraft:item", "name": "minecraft:netherite_scrap", "weight": 2},
                    {"type": "minecraft:item", "name": "minecraft:wither_skeleton_skull", "weight": 1},
                    {"type": "minecraft:item", "name": "minecraft:golden_apple", "weight": 3},
                ]},
            ],
        },
        f"{NS}/loot_table/blocks/nether_geyser.json": {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:nether_geyser"}],
                       "condition": {"type": "minecraft:survives_explosion"}}],
        },
    }


def build(vanilla):
    out = {
        "minecraft/worldgen/noise_settings/nether.json": noise_settings(vanilla),
        "minecraft/dimension_type/the_nether.json": dimension_type(vanilla),
        f"{NS}/worldgen/material_rule/nether.json": material_rule(),
    }
    for name, params in NOISES.items():
        out[f"{NS}/worldgen/noise/nether/{name}.json"] = noise_json(params)
    for name, fn in density_functions().items():
        out[f"{NS}/worldgen/density_function/nether/{name}.json"] = fn
    for name, b in biomes().items():
        out[f"{NS}/worldgen/biome/{name}.json"] = b
    out.update(features())
    out.update(loot())
    mine = [f"{NS}:{name}" for name in biomes()]
    out["minecraft/tags/worldgen/biome/is_nether.json"] = {"replace": False, "values": mine}
    out["minecraft/tags/worldgen/biome/has_structure/bastion_remnant.json"] = {"replace": False, "values": [f"{NS}:ember_wastes"]}
    return out
