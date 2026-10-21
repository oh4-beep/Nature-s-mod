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

Down below, two more biomes make it feel like Hell:

  * Brimstone Fields: smouldering brimstone, sulfur and cinnabar, eternal fires and ember
    crystals, blazes drifting over geysers.
  * Flesh Caverns: walls of living flesh blistered with glowing boils, the bones of titans,
    fields of bone spikes and skulls on pikes.

The vanilla biomes are reworked too (their files are overridden): scorched netherrack and ash on
the ground, flesh in the crimson forests, embers in the air, skull pikes, charred trees and little
shrines, more blazes and wither skeletons. Three new strongholds stand in the lower Nether:
Infernal Citadels, Bone Pits and Ritual Circles.

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
    # Patches on the ground and in the rock: scorched netherrack, ash, flesh, brimstone.
    "scorch": {"firstOctave": -5, "amplitudes": [1.0, 0.5]},
    "ash": {"firstOctave": -5, "amplitudes": [1.0, 0.6, 0.3]},
    "flesh": {"firstOctave": -5, "amplitudes": [1.0, 0.5]},
    "brimstone": {"firstOctave": -4, "amplitudes": [1.0, 0.6]},
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

# Every Nether biome shares one chunk generator, so the features the mod adds must come in this
# one order in every biome's underground decoration step, after the vanilla ones.
HELL_ORDER = ["geyser_field", "geyser_field_sparse", "lavafall", "lavafall_sparse", "ember_spikes", "glowstone_chandelier",
              "hanging_cage", "hanging_cage_rare", "brimstone_deposit", "brimstone_deposit_sparse", "ember_crystals",
              "ember_crystals_sparse", "flesh_growth", "flesh_growth_sparse", "titan_bones", "bone_spikes", "skull_pikes",
              "skull_pikes_sparse", "charred_tree", "charred_tree_sparse", "hell_shrine", "hell_shrine_rare"]


def decorate(base, extras):
    return list(base) + [f"{NS}:nether/{name}" for name in sorted(extras, key=HELL_ORDER.index)]


def particles(*pairs):
    return [{"particle": {"type": f"minecraft:{p}"}, "probability": chance} for p, chance in pairs]


def biome(fog, music, ambient, ambient_particles, monsters, creatures, decoration, vegetation, local=(), fog_end=None):
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
    if fog_end:
        attributes["minecraft:visual/fog_end_distance"] = fog_end
    if ambient_particles:
        attributes["minecraft:visual/ambient_particles"] = {"argument": particles(*ambient_particles), "modifier": "append"}
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
    soul_decoration = [f.replace("patch_fire", "patch_soul_fire") for f in VANILLA_DECORATION]
    return {
        "ember_wastes": biome(
            "#4a1806", "basalt_deltas", "basalt_deltas", [("ash", 0.06), ("lava", 0.003)],
            [spawn("magma_cube", 100, 2, 5), spawn("blaze", 40, 1, 2), spawn("ghast", 40, 1, 1), spawn("zombified_piglin", 20, 2, 4),
             spawn("wither_skeleton", 10, 1, 2)],
            [spawn("strider", 60, 1, 2)],
            decorate(VANILLA_DECORATION, ["geyser_field", "lavafall", "ember_spikes", "charred_tree", "skull_pikes_sparse",
                                          "brimstone_deposit_sparse", "ember_crystals_sparse", "hell_shrine_rare"]),
            ["minecraft:spring_lava"],
        ),
        "glowstone_grotto": biome(
            "#5c3a12", "crimson_forest", "crimson_forest", [("white_ash", 0.02), ("crimson_spore", 0.01)],
            [spawn("piglin", 30, 2, 4), spawn("zombified_piglin", 40, 2, 4), spawn("ghast", 25, 1, 1), spawn("enderman", 5, 1, 2)],
            [spawn("strider", 30, 1, 2)],
            decorate(VANILLA_DECORATION, ["glowstone_chandelier", "lavafall", "ember_crystals_sparse", "hell_shrine_rare"]),
            ["minecraft:spring_lava", "minecraft:weeping_vines", "minecraft:crimson_fungi", "minecraft:warped_fungi",
             "minecraft:crimson_forest_vegetation"],
        ),
        "obsidian_spires": biome(
            "#180a24", "soul_sand_valley", "soul_sand_valley", [("dripping_obsidian_tear", 0.012), ("soul", 0.002)],
            [spawn("wither_skeleton", 40, 1, 3), spawn("ghast", 35, 1, 1), spawn("enderman", 12, 1, 3), spawn("magma_cube", 20, 1, 3),
             spawn("skeleton", 10, 1, 2)],
            [],
            # Soul fire takes the place of fire: features must keep the same order in every Nether biome.
            decorate(soul_decoration, ["hanging_cage", "lavafall", "skull_pikes", "hell_shrine"]),
            ["minecraft:spring_lava"],
            local=[f"{NS}:nether/obsidian_spire"],
        ),
        "brimstone_fields": biome(
            "#5c3a08", "basalt_deltas", "basalt_deltas", [("white_ash", 0.03), ("lava", 0.004)],
            [spawn("blaze", 50, 1, 3), spawn("magma_cube", 70, 2, 4), spawn("ghast", 30, 1, 1), spawn("zombified_piglin", 10, 2, 3)],
            [spawn("strider", 40, 1, 2)],
            decorate(VANILLA_DECORATION, ["geyser_field", "lavafall", "brimstone_deposit", "ember_crystals", "charred_tree_sparse",
                                          "hell_shrine_rare"]),
            ["minecraft:spring_lava"],
            fog_end=80.0,
        ),
        "flesh_caverns": biome(
            "#4a0408", "crimson_forest", "crimson_forest", [("crimson_spore", 0.045), ("lava", 0.001)],
            [spawn("hoglin", 20, 2, 3), spawn("zombified_piglin", 40, 2, 4), spawn("ghast", 20, 1, 1), spawn("piglin", 10, 2, 3),
             spawn("magma_cube", 10, 1, 2), spawn("wither_skeleton", 8, 1, 2)],
            [spawn("strider", 20, 1, 2)],
            decorate(VANILLA_DECORATION, ["lavafall_sparse", "flesh_growth", "titan_bones", "bone_spikes", "skull_pikes", "hell_shrine_rare"]),
            ["minecraft:spring_lava", "minecraft:weeping_vines"],
            fog_end=64.0,
        ),
    }


# The vanilla biomes, reworked: the same files with Hell's marks added to them.
VANILLA_REWORK = {
    "nether_wastes": {
        "fog": "#3b0904",
        "particles": [("lava", 0.0025), ("ash", 0.004)],
        "monsters": [spawn("blaze", 10, 1, 2), spawn("wither_skeleton", 6, 1, 2)],
        "monster_weights": {"minecraft:magma_cube": 15},
        "features": ["lavafall_sparse", "hanging_cage_rare", "geyser_field_sparse", "brimstone_deposit_sparse", "ember_crystals_sparse",
                     "skull_pikes_sparse", "charred_tree_sparse", "hell_shrine_rare"],
    },
    "soul_sand_valley": {
        "particles": [("soul", 0.0015)],
        "monsters": [spawn("wither_skeleton", 12, 1, 2)],
        "features": ["lavafall_sparse", "hanging_cage_rare", "titan_bones", "bone_spikes", "skull_pikes", "hell_shrine_rare"],
    },
    "crimson_forest": {
        "fog": "#3d0303",
        "particles": [("lava", 0.0015)],
        "monsters": [],
        "features": ["lavafall_sparse", "flesh_growth_sparse", "skull_pikes_sparse"],
    },
    "warped_forest": {
        "particles": [],
        "monsters": [],
        "features": ["lavafall_sparse", "ember_crystals_sparse"],
    },
    "basalt_deltas": {
        "particles": [("lava", 0.002)],
        "monsters": [spawn("blaze", 12, 1, 2)],
        "features": ["lavafall_sparse", "hanging_cage_rare", "geyser_field_sparse", "brimstone_deposit_sparse", "charred_tree_sparse",
                     "hell_shrine_rare"],
    },
}


def vanilla_biomes(vanilla):
    out = {}
    for name, rework in VANILLA_REWORK.items():
        data = copy.deepcopy(vanilla.read(f"minecraft/worldgen/biome/{name}.json"))
        attributes = data["attributes"]
        if "fog" in rework:
            attributes["minecraft:visual/fog_color"] = rework["fog"]
        if rework["particles"]:
            ambient = attributes.setdefault("minecraft:visual/ambient_particles", {"argument": [], "modifier": "append"})
            ambient["argument"] = ambient["argument"] + particles(*rework["particles"])
        monsters = attributes["minecraft:gameplay/natural_mob_spawns"]["argument"]["spawns_by_category"].setdefault("monster", [])
        for entry in monsters:
            if entry["type"] in rework.get("monster_weights", {}):
                entry["weight"] = rework["monster_weights"][entry["type"]]
        monsters.extend(rework["monsters"])
        features = data["features"]
        while len(features) < 8:
            features.append([])
        features[7] = decorate(features[7], rework["features"])
        out[name] = data
    return out


# --- surface ------------------------------------------------------------------------------

def block(b):
    return {"type": "minecraft:block", "result_state": b if ":" in b else f"minecraft:{b}"}


def cond(test, then):
    return {"type": "minecraft:condition", "if_true": test, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def in_biome(name):
    return {"type": "minecraft:biome", "biome_is": name if ":" in name else f"{NS}:{name}"}


def threshold(noise_name, lo, hi=1.0e9):
    return {"type": "minecraft:noise_threshold", "noise": noise_name, "min_threshold": lo, "max_threshold": hi}


def mine(name):
    return f"{NS}:nether/{name}"


def material_rule():
    floor = "minecraft:on_floor"
    under_floor = "minecraft:under_floor"
    ceiling = "minecraft:on_ceiling"
    patch = "minecraft:patch"
    selector = "minecraft:nether_state_selector"
    scorched = block(f"{NS}:scorched_netherrack")
    ash = block(f"{NS}:ash_block")
    flesh = block(f"{NS}:flesh_block")
    brimstone = block(f"{NS}:brimstone")
    ember = cond(in_biome("ember_wastes"), seq(
        cond(ceiling, block("basalt")),
        cond(floor, seq(cond(threshold(patch, 0.35), block("magma_block")), cond(threshold(mine("ash"), 0.25), ash),
                        cond(threshold(selector, 0.1), block("smooth_basalt")), block("blackstone"))),
        cond(under_floor, seq(cond(threshold(selector, 0.0), block("blackstone")), block("basalt"))),
        cond(threshold(mine("scorch"), 0.4), scorched),
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
    # Brimstone, with yellow sulfur and red cinnabar breaking through it, over seething magma.
    fields = cond(in_biome("brimstone_fields"), seq(
        cond(ceiling, seq(cond(threshold(mine("brimstone"), 0.3), brimstone), block("basalt"))),
        cond(floor, seq(cond(threshold(patch, 0.4), block("magma_block")), cond(threshold(mine("brimstone"), 0.35), block("sulfur")),
                        cond(threshold(mine("brimstone"), -1.0e9, -0.45), block("cinnabar")), brimstone)),
        cond(under_floor, brimstone),
        cond(threshold(mine("brimstone"), 0.2), brimstone),
        cond(threshold(mine("scorch"), 0.3), scorched),
    ))
    # Walls of living flesh, the old netherrack showing through in streaks.
    caverns = cond(in_biome("flesh_caverns"), seq(
        cond(ceiling, flesh),
        cond(floor, seq(cond(threshold(patch, 0.4), block("bone_block")), cond(threshold(selector, 0.25), block("nether_wart_block")), flesh)),
        cond(under_floor, flesh),
        cond(threshold(selector, 0.6), block("nether_wart_block")),
        cond(threshold(mine("flesh"), -0.6), flesh),
    ))
    # The vanilla biomes get scorched ground, drifts of ash, and flesh in the crimson forests.
    wastes = cond(in_biome("minecraft:nether_wastes"), seq(
        cond(floor, seq(cond(threshold(mine("scorch"), 0.15), scorched), cond(threshold(mine("ash"), 0.45), ash))),
        cond(ceiling, cond(threshold(mine("scorch"), 0.3), scorched)),
        cond(threshold(mine("scorch"), 0.55), scorched),
    ))
    valley = cond(in_biome("minecraft:soul_sand_valley"), cond(floor, cond(threshold(mine("ash"), 0.25), ash)))
    deltas = cond(in_biome("minecraft:basalt_deltas"), cond(floor, cond(threshold(mine("ash"), 0.2), ash)))
    crimson = cond(in_biome("minecraft:crimson_forest"), seq(
        cond(ceiling, cond(threshold(mine("flesh"), 0.35), flesh)),
        cond(threshold(mine("flesh"), 0.6), flesh),
    ))
    return seq("minecraft:bedrock_floor", "minecraft:bedrock_roof", ember, grotto, spires, fields, caverns, wastes, valley, deltas, crimson,
               "minecraft:nether")


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


def hell_feature(style):
    return {"type": f"{NS}:hell_feature", "style": style}


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
        # The marks of Hell.
        "brimstone_deposit": (hell_feature("brimstone_deposit"), [count(6), IN_SQUARE, ANYWHERE, BIOME]),
        "brimstone_deposit_sparse": (hell_feature("brimstone_deposit"), [rarity(2), IN_SQUARE, ANYWHERE, BIOME]),
        "ember_crystals": (hell_feature("ember_crystals"), [count(8), IN_SQUARE, ANYWHERE, BIOME]),
        "ember_crystals_sparse": (hell_feature("ember_crystals"), [count(2), IN_SQUARE, ANYWHERE, BIOME]),
        "flesh_growth": (hell_feature("flesh_growth"), [count(12), IN_SQUARE, ANYWHERE, BIOME]),
        "flesh_growth_sparse": (hell_feature("flesh_growth"), [count(2), IN_SQUARE, ANYWHERE, BIOME]),
        "titan_bones": (hell_feature("titan_bones"), [rarity(4), IN_SQUARE, ANYWHERE, BIOME]),
        "bone_spikes": (hell_feature("bone_spikes"), [count(3), IN_SQUARE, ANYWHERE, BIOME]),
        "skull_pikes": (hell_feature("skull_pikes"), [count(3), IN_SQUARE, ANYWHERE, BIOME]),
        "skull_pikes_sparse": (hell_feature("skull_pikes"), [rarity(2), IN_SQUARE, ANYWHERE, BIOME]),
        "charred_tree": (hell_feature("charred_tree"), [count(6), IN_SQUARE, ANYWHERE, BIOME]),
        "charred_tree_sparse": (hell_feature("charred_tree"), [count(1), IN_SQUARE, ANYWHERE, BIOME]),
        "hell_shrine": (hell_feature("hell_shrine"), [rarity(3), IN_SQUARE, ANYWHERE, BIOME]),
        "hell_shrine_rare": (hell_feature("hell_shrine"), [rarity(9), IN_SQUARE, ANYWHERE, BIOME]),
    }
    files = {}
    for name, (feature, placement) in out.items():
        files[f"{NS}/worldgen/feature/nether/{name}.json"] = feature
        files[f"{NS}/worldgen/placed_feature/nether/{name}.json"] = {"feature": f"{NS}:nether/{name}", "placement": placement}
    return files


# --- the strongholds of Hell ----------------------------------------------------------------

LOWER = ["minecraft:nether_wastes", "minecraft:soul_sand_valley", "minecraft:crimson_forest", "minecraft:warped_forest",
         "minecraft:basalt_deltas", f"{NS}:ember_wastes", f"{NS}:brimstone_fields", f"{NS}:flesh_caverns"]

STRUCTURES = {
    # id: (weight, biomes, spawn overrides)
    "infernal_citadel": (1, LOWER, [spawn("wither_skeleton", 10, 1, 2), spawn("blaze", 10, 1, 2), spawn("magma_cube", 3, 2, 3),
                                    spawn("zombified_piglin", 3, 2, 4)]),
    "bone_pit": (2, ["minecraft:soul_sand_valley", "minecraft:nether_wastes", f"{NS}:ember_wastes", f"{NS}:flesh_caverns"], []),
    "ritual_circle": (2, LOWER + [f"{NS}:obsidian_spires"], []),
}


def structures():
    """The strongholds share one structure set, like fortresses and bastions do, so they never
    overlap: each spot picks one by weight and falls back on the others if it cannot stand there."""
    files = {}
    entries = []
    for name, (weight, biomes_, spawns) in STRUCTURES.items():
        overrides = {"monster": {"bounding_box": "full", "spawns": spawns}} if spawns else {}
        files[f"{NS}/worldgen/structure/{name}.json"] = {
            "type": f"{NS}:{name}", "biomes": f"#{NS}:has_structure/{name}", "spawn_overrides": overrides, "step": "surface_structures"}
        files[f"{NS}/tags/worldgen/biome/has_structure/{name}.json"] = {"values": biomes_}
        entries.append({"structure": f"{NS}:{name}", "weight": weight})
    placement = {"type": "minecraft:random_spread", "salt": 666013, "separation": 5, "spacing": 16,
                 "exclusion_zone": {"chunk_count": 3, "other_set": "minecraft:nether_complexes"}}
    files[f"{NS}/worldgen/structure_set/strongholds_of_hell.json"] = {"placement": placement, "structures": entries}
    return files


def item(name, weight, lo=1, hi=None, *extra):
    entry = {"type": "minecraft:item", "name": name if ":" in name else f"minecraft:{name}", "weight": weight}
    mods = []
    if hi is not None and hi != lo:
        mods.append({"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    elif lo != 1:
        mods.append({"type": "minecraft:set_count", "count": lo})
    mods.extend(extra)
    if mods:
        entry["modifier"] = mods if len(mods) > 1 else mods[0]
    return entry


ENCHANTED = {"type": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot"}


def chest(*pools):
    return {"type": "minecraft:chest", "pools": [{"rolls": rolls if isinstance(rolls, int) else
                                                  {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "entries": entries}
                                                 for rolls, entries in pools]}


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
        f"{NS}/loot_table/chests/infernal_citadel.json": chest(
            ((4, 7), [item("gold_ingot", 20, 2, 6), item("gold_block", 4), item("diamond", 8, 1, 3), item("netherite_scrap", 4, 1, 2),
                      item("ancient_debris", 3), item("blaze_rod", 12, 2, 5), item("magma_cream", 10, 2, 5), item(f"{NS}:ember_shard", 15, 3, 8),
                      item("golden_apple", 6), item("enchanted_golden_apple", 1), item("obsidian", 8, 2, 6), item("fire_charge", 8, 2, 5),
                      item("crying_obsidian", 6, 1, 4)]),
            ((1, 2), [item("diamond_sword", 4, 1, None, ENCHANTED), item("diamond_chestplate", 3, 1, None, ENCHANTED),
                      item("golden_helmet", 5, 1, None, ENCHANTED), item("netherite_upgrade_smithing_template", 2),
                      item("book", 8, 1, None, ENCHANTED), item("music_disc_pigstep", 1), item("wither_skeleton_skull", 2),
                      item(f"{NS}:hellfire_brazier", 3)]),
        ),
        f"{NS}/loot_table/chests/bone_pit.json": chest(
            ((3, 6), [item("bone", 25, 3, 9), item("bone_block", 10, 1, 3), item("arrow", 15, 4, 12), item("skeleton_skull", 6),
                      item("wither_skeleton_skull", 2), item("soul_lantern", 8, 1, 3), item("gold_nugget", 15, 4, 12), item("gold_ingot", 8, 1, 3),
                      item("diamond", 3), item(f"{NS}:bone_spikes", 6, 2, 5), item("soul_sand", 8, 2, 6)]),
            (1, [item("bow", 5, 1, None, ENCHANTED), item("crossbow", 3, 1, None, ENCHANTED), item("iron_sword", 4, 1, None, ENCHANTED),
                 item("book", 4, 1, None, ENCHANTED), item(f"{NS}:soul_brazier", 2)]),
        ),
        f"{NS}/loot_table/chests/ritual_circle.json": chest(
            ((3, 5), [item(f"{NS}:ember_shard", 20, 2, 6), item("blaze_powder", 15, 2, 6), item("nether_wart", 15, 3, 8), item("ghast_tear", 8, 1, 2),
                      item("crying_obsidian", 10, 2, 5), item("gold_ingot", 12, 2, 5), item("diamond", 4, 1, 2), item("experience_bottle", 10, 2, 6),
                      item("ender_pearl", 6, 1, 2)]),
            (1, [item("totem_of_undying", 1), item("book", 6, 1, None, ENCHANTED), item("golden_apple", 4),
                 item("golden_sword", 4, 1, None, ENCHANTED)]),
        ),
        f"{NS}/loot_table/chests/hell_shrine.json": chest(
            ((2, 4), [item("gold_nugget", 20, 3, 10), item(f"{NS}:ember_shard", 15, 1, 4), item("blaze_powder", 10, 1, 3), item("quartz", 12, 3, 8),
                      item("gold_ingot", 6, 1, 2), item("iron_ingot", 8, 1, 3), item("bone", 10, 2, 5), item("magma_cream", 6, 1, 2),
                      item("golden_carrot", 4, 1, 3)]),
        ),
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
    for name, b in vanilla_biomes(vanilla).items():
        out[f"minecraft/worldgen/biome/{name}.json"] = b
    out.update(features())
    out.update(structures())
    out.update(loot())
    mine_biomes = [f"{NS}:{name}" for name in biomes()]
    out["minecraft/tags/worldgen/biome/is_nether.json"] = {"replace": False, "values": mine_biomes}
    out["minecraft/tags/worldgen/biome/has_structure/bastion_remnant.json"] = {"replace": False, "values": [
        f"{NS}:ember_wastes", f"{NS}:brimstone_fields"]}
    out["minecraft/tags/worldgen/biome/has_structure/nether_fossil.json"] = {"replace": False, "values": [f"{NS}:flesh_caverns"]}
    return out
