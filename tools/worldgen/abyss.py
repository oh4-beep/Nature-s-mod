"""The Abyss: a dimension that is one ocean, a thousand blocks deep.

Divers reach it through Abyssal Rifts on the floor of the deep oceans and arrive on a little
platform at the surface of a black, starless sea. Below them the water goes down for a thousand
blocks:

  * The twilight waters (above y ~730): the last of the light.
  * The midnight zone: open black water around the flanks of the seamounts.
  * The sea floor (y ~60-110): abyssal plains of grey ooze, glowing reefs, hydrothermal vent
    fields of black smokers and the bone fields where great creatures sink to die. Seamounts
    rise up to 500 blocks from it and trenches cut down to bedrock.

The terrain is a height field (the sea floor) carved by 3D noise into overhangs and arches.
Biomes are picked by absolute height ("erosion" axis) and by height above the local sea floor
("depth" axis), so the floor biomes hug the floor wherever it is.
"""

import copy

NS = "naturesmod"
DF = f"{NS}:abyss"

MIN_Y = 0
HEIGHT = 1088
SEA_LEVEL = 1050          # water up to y = 1049, a band of air above it
FLOOR = 72                # height of the abyssal plain
TWILIGHT_Y = 730          # above this the water is twilight
FLOOR_ZONE = 32           # floor biomes reach this far above the sea floor


# --- density function helpers ------------------------------------------------------------

def add(a, b):
    return {"type": "minecraft:add", "left": a, "right": b}


def mul(a, b):
    return {"type": "minecraft:mul", "left": a, "right": b}


def clamp(x, lo, hi):
    return {"type": "minecraft:clamp", "input": x, "min": lo, "max": hi}


def mx(a, b):
    return {"type": "minecraft:max", "left": a, "right": b}


def mn(a, b):
    return {"type": "minecraft:min", "left": a, "right": b}


def cache(x):
    return {"type": "minecraft:cache", "input": x}


def noise(name, xz=1.0, y=0.0):
    return {"type": "minecraft:noise", "noise": f"{NS}:abyss/{name}", "xz_scale": xz, "y_scale": y}


def gradient_y(y0, v0, y1, v1):
    return {"type": "minecraft:gradient", "axis": "y", "from_coordinate": y0, "from_value": v0, "to_coordinate": y1, "to_value": v1}


def band(y0, y1, y2, y3):
    """0 below y0, rising to 1 at y1, 1 up to y2, falling to 0 at y3."""
    return mn(clamp(gradient_y(y0, 0.0, y1, 1.0), 0.0, 1.0), clamp(gradient_y(y2, 1.0, y3, 0.0), 0.0, 1.0))


def spline(coordinate, points):
    """points: (location, value, derivative)."""
    return {
        "type": "minecraft:spline",
        "spline": {
            "coordinate": coordinate,
            "points": [{"location": loc, "value": val, "derivative": der} for loc, val, der in points],
        },
    }


def ref(name):
    return f"{DF}/{name}"


# --- noises -------------------------------------------------------------------------------

NOISES = {
    "plains": {"firstOctave": -7, "amplitudes": [1.0, 1.0, 0.5]},
    "seamount": {"firstOctave": -9, "amplitudes": [1.0, 1.0, 0.0, 0.5]},
    "trench": {"firstOctave": -9, "amplitudes": [1.0, 0.5, 0.25]},
    "spire": {"firstOctave": -5, "amplitudes": [1.0, 0.4]},
    "rough": {"firstOctave": -5, "amplitudes": [1.0, 1.0, 1.0]},
    "carve": {"firstOctave": -6, "amplitudes": [1.0, 0.5, 0.5, 0.25]},
    "temperature": {"firstOctave": -9, "amplitudes": [1.0, 1.0]},
    "vegetation": {"firstOctave": -9, "amplitudes": [1.0, 0.5]},
    "patch": {"firstOctave": -5, "amplitudes": [1.0, 0.5]},
    "detail": {"firstOctave": -3, "amplitudes": [1.0, 1.0]},
    "drift": {"firstOctave": -6, "amplitudes": [1.0, 0.6, 0.3]},
}


# --- density functions --------------------------------------------------------------------

def density_functions():
    out = {}
    # Seamounts: most of the sea floor is plain, but where the seamount noise runs high the
    # floor climbs in great ridges and peaks; the tallest break into the twilight waters ~700
    # blocks up, where kelp forests sway on their summits.
    out["seamount"] = cache(mul(
        spline(noise("seamount"), [
            (-1.0, -12.0, 0.0), (0.1, 0.0, 0.0), (0.26, 25.0, 300.0), (0.4, 150.0, 1100.0),
            (0.54, 340.0, 1300.0), (0.68, 530.0, 1100.0), (0.84, 680.0, 650.0), (1.0, 760.0, 250.0), (1.3, 780.0, 0.0),
        ]),
        add(1.0, mul(noise("rough"), 0.18)),
    ))
    # Damps trenches and spires where the floor already rises into a seamount.
    out["plain_factor"] = cache(clamp(add(1.0, mul(ref("seamount"), -1.0 / 60.0)), 0.0, 1.0))
    # Trenches: long winding canyons where the trench noise crosses zero.
    out["trench"] = cache(mul(
        spline({"type": "minecraft:abs", "input": noise("trench")}, [
            (0.0, 66.0, 0.0), (0.035, 58.0, -400.0), (0.08, 12.0, -400.0), (0.11, 0.0, 0.0), (1.0, 0.0, 0.0),
        ]),
        ref("plain_factor"),
    ))
    # Spires: lonely columns of rock that tower out of the plains.
    out["spire"] = cache(mul(
        spline(noise("spire"), [
            (0.0, 0.0, 0.0), (0.52, 0.0, 0.0), (0.6, 70.0, 1800.0), (0.7, 240.0, 1200.0), (0.85, 330.0, 0.0),
        ]),
        ref("plain_factor"),
    ))
    # Height of the sea floor.
    out["floor"] = cache(add(
        add(float(FLOOR), mul(noise("plains"), 10.0)),
        add(add(ref("seamount"), ref("spire")), mul(ref("trench"), -1.0)),
    ))
    # 3D carving: overhangs, arches and grottoes, strongest on the flanks of the seamounts.
    carve_strength = add(0.25, mul(clamp(mul(add(ref("seamount"), ref("spire")), 1.0 / 150.0), 0.0, 1.0), 0.55))
    carve = mul(noise("carve", 1.0, 1.4), carve_strength)
    solid = add(mul(add(ref("floor"), {"type": "minecraft:negate", "input": "minecraft:y"}), 1.0 / 24.0), carve)
    # Drifting islands: great flat-bottomed rocks that float in the black water between the
    # sea floor and the twilight. Outside their band they fall well below zero so the max
    # leaves the rest of the sea alone.
    out["islands"] = add(mul(add(noise("drift", 1.0, 2.2), -0.6), 7.0), mul(add(band(280, 360, 800, 880), -1.0), 12.0))
    out["final_density"] = {
        "type": "minecraft:interpolated",
        "cell_size_xz": 4,
        "cell_size_y": 8,
        "input": add(
            # Keep the bottom of the world solid under even the deepest trench.
            mx(add(solid, gradient_y(0, 2.0, 12, 0.0)), ref("islands")),
            {"type": "minecraft:beardifier"},
        ),
    }
    # Biome axes: absolute height and height above the local sea floor.
    out["height"] = gradient_y(MIN_Y, -1.0, HEIGHT, 1.0)
    out["above_floor"] = clamp(mul(add("minecraft:y", mul(ref("floor"), -1.0)), 1.0 / 128.0), -2.0, 2.0)
    return out


def noise_settings():
    return {
        "aquifers": None,
        "default_block": "minecraft:deepslate",
        "default_fluid": "minecraft:water",
        "disable_mob_generation": False,
        "legacy_random_source": False,
        "material_rule": f"{NS}:abyss",
        "noise": {"height": HEIGHT, "min_y": MIN_Y},
        "noise_router": {
            "chunk_surface_level": ref("floor"),
            "continents": 0.0,
            "depth": ref("above_floor"),
            "erosion": ref("height"),
            "final_density": ref("final_density"),
            "ridges": 0.0,
            "temperature": noise("temperature", 1.0),
            "vegetation": noise("vegetation", 1.0),
        },
        "sea_level": SEA_LEVEL,
        "spawn_target": [],
    }


# --- biomes -------------------------------------------------------------------------------

def height_param(y):
    return round(-1.0 + 2.0 * (y - MIN_Y) / HEIGHT, 4)


def above_param(blocks):
    return round(blocks / 128.0, 4)


TWILIGHT = height_param(TWILIGHT_Y)
FLOOR_TOP = above_param(FLOOR_ZONE)


def point(biome, temperature=(-2.0, 2.0), humidity=(-2.0, 2.0), erosion=(-2.0, 2.0), depth=(-2.0, 2.0)):
    return {
        "biome": f"{NS}:{biome}",
        "parameters": {
            "temperature": list(temperature),
            "humidity": list(humidity),
            "continentalness": 0.0,
            "erosion": list(erosion),
            "weirdness": 0.0,
            "depth": list(depth),
            "offset": 0.0,
        },
    }


def biome_source():
    deep = (-2.0, TWILIGHT)
    floor = (-2.0, FLOOR_TOP)
    return {
        "type": "minecraft:multi_noise",
        "biomes": [
            point("twilight_waters", erosion=(TWILIGHT, 2.0)),
            point("midnight_zone", erosion=deep, depth=(FLOOR_TOP, 2.0)),
            point("hydrothermal_vents", erosion=deep, depth=floor, temperature=(0.42, 2.0)),
            point("bone_fields", erosion=deep, depth=floor, temperature=(-2.0, -0.32)),
            point("glowing_reef", erosion=deep, depth=floor, temperature=(-0.32, 0.42), humidity=(0.22, 2.0)),
            point("abyssal_plains", erosion=deep, depth=floor, temperature=(-0.32, 0.42), humidity=(-2.0, 0.22)),
        ],
    }


def dimension_type():
    return {
        "ambient_light": 0.25,
        "attributes": {
            "minecraft:audio/ambient_sounds": {
                "mood": {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 6000},
            },
            "minecraft:audio/background_music": {
                "default": {"max_delay": 12000, "min_delay": 6000, "sound": "minecraft:music.under_water"},
            },
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": False},
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/sky_light_level": 0.0,
            "minecraft:visual/ambient_light_color": "#24405a",
            "minecraft:visual/fog_color": "#02060c",
            "minecraft:visual/sky_color": "#000000",
            "minecraft:visual/sky_light_factor": 0.0,
            "minecraft:visual/fog_start_distance": 16.0,
            "minecraft:visual/fog_end_distance": 128.0,
        },
        "coordinate_scale": 1.0,
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_fixed_time": True,
        "has_skylight": False,
        "height": HEIGHT,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": HEIGHT,
        "min_y": MIN_Y,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0,
        "skybox": "none",
        "timelines": "#minecraft:in_nether",
    }


def dimension():
    return {
        "type": f"{NS}:abyss",
        "generator": {
            "type": "minecraft:noise",
            "biome_source": biome_source(),
            "settings": f"{NS}:abyss",
        },
    }


EMPTY_SPAWNS = {"argument": {"spawn_costs": {}, "spawns_by_category": {}}, "modifier": "overlay"}


def biome(water_fog, fog_distance, particles=None, features=None, music="minecraft:music.under_water", water="#0a1a3a", eerie=False):
    sounds = {
        "loop": "minecraft:ambient.underwater.loop",
        "additions": {"sound": "minecraft:ambient.underwater.loop.additions.rare", "tick_chance": 0.006},
    }
    if eerie:
        # Down here the sea whispers, and something far off answers.
        sounds["additions"] = {"sound": "minecraft:ambient.soul_sand_valley.additions", "tick_chance": 0.005}
        sounds["mood"] = {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 3000}
    attributes = {
        "minecraft:audio/background_music": {
            "default": {"max_delay": 12000, "min_delay": 6000, "sound": music},
            "underwater": {"max_delay": 12000, "min_delay": 6000, "sound": music},
        },
        "minecraft:audio/ambient_sounds": sounds,
        "minecraft:gameplay/natural_mob_spawns": EMPTY_SPAWNS,
        "minecraft:visual/water_fog_color": water_fog,
        "minecraft:visual/water_fog_end_distance": fog_distance,
        "minecraft:visual/fog_color": "#02060c",
        "minecraft:visual/sky_color": "#000000",
    }
    if particles:
        attributes["minecraft:visual/ambient_particles"] = {
            "argument": [{"particle": {"type": p}, "probability": chance} for p, chance in particles],
            "modifier": "append",
        }
    steps = [[] for _ in range(11)]
    for step, name in features or []:
        steps[step].append(name)
    return {
        "attributes": attributes,
        "carvers": [],
        "downfall": 0.5,
        "effects": {"water_color": water},
        "features": steps,
        "has_precipitation": False,
        "temperature": 0.5,
    }


LOCAL = 2
ORES = 6
UNDERGROUND = 7
VEGETATION = 9

COMMON = [(ORES, f"{NS}:abyss/ore_{o}") for o in ("diamond", "gold", "lapis", "redstone", "emerald", "copper")] + [
    (UNDERGROUND, f"{NS}:abyss/glow_lichen"),
]


def biomes():
    marine_snow = [("minecraft:white_ash", 0.025), ("minecraft:underwater", 0.02)]
    sparks = [("minecraft:glow", 0.003), ("minecraft:underwater", 0.025)]
    return {
        "twilight_waters": biome("#103c72", 128.0, [("minecraft:underwater", 0.04), ("minecraft:glow", 0.001)], COMMON + [
            (LOCAL, f"{NS}:abyss/giant_kelp"), (LOCAL, f"{NS}:abyss/hanging_glow"),
            (VEGETATION, "minecraft:kelp_cold"), (VEGETATION, "minecraft:seagrass_deep"), (VEGETATION, f"{NS}:abyss/sea_pickles"),
        ], water="#1d4f8f"),
        "midnight_zone": biome("#071630", 96.0, sparks, COMMON + [(LOCAL, f"{NS}:abyss/hanging_glow")], water="#0c2348", eerie=True),
        "abyssal_plains": biome("#0c1d2e", 104.0, marine_snow, COMMON + [
            (LOCAL, f"{NS}:abyss/nodules"), (LOCAL, f"{NS}:abyss/whale_fall_rare"), (VEGETATION, f"{NS}:abyss/sea_pickles_sparse"),
            (VEGETATION, f"{NS}:abyss/garden_floor"), (LOCAL, f"{NS}:abyss/bone_pile_sparse"), (LOCAL, f"{NS}:abyss/diver_remains"),
        ], water="#10283e", eerie=True),
        "glowing_reef": biome("#0a4249", 120.0, [("minecraft:glow", 0.004), ("minecraft:underwater", 0.02)], COMMON + [
            (LOCAL, f"{NS}:abyss/glow_reef"), (VEGETATION, f"{NS}:abyss/reef_coral"), (VEGETATION, f"{NS}:abyss/sea_pickles"),
            (VEGETATION, f"{NS}:abyss/garden_reef"),
        ], water="#178a8a"),
        "hydrothermal_vents": biome("#2c1a10", 72.0, [("minecraft:ash", 0.03), ("minecraft:bubble", 0.01)], COMMON + [
            (LOCAL, f"{NS}:abyss/black_smoker"), (LOCAL, f"{NS}:abyss/soul_vent"), (VEGETATION, f"{NS}:abyss/garden_vents"),
            (LOCAL, f"{NS}:abyss/diver_remains"),
        ], water="#4a2c1a", eerie=True),
        "bone_fields": biome("#17221f", 96.0, marine_snow, COMMON + [
            (LOCAL, f"{NS}:abyss/whale_fall"), (LOCAL, f"{NS}:abyss/nodules"), (VEGETATION, f"{NS}:abyss/garden_floor"),
            (LOCAL, f"{NS}:abyss/bone_pile"), (LOCAL, f"{NS}:abyss/diver_remains"),
        ], water="#22342e", eerie=True),
    }


# --- surface ------------------------------------------------------------------------------

def block(b):
    return {"type": "minecraft:block", "result_state": b if ":" in b else f"minecraft:{b}"}


def cond(test, then):
    return {"type": "minecraft:condition", "if_true": test, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def in_biome(*names):
    return {"type": "minecraft:biome", "biome_is": [f"{NS}:{n}" for n in names]} if len(names) > 1 else \
        {"type": "minecraft:biome", "biome_is": f"{NS}:{names[0]}"}


def patch(lo, hi=1.0e9, which="patch"):
    return {"type": "minecraft:noise_threshold", "noise": f"{NS}:abyss/{which}", "min_threshold": lo, "max_threshold": hi}


STEEP = {"type": "minecraft:steep"}


def material_rule():
    on_floor = "minecraft:on_floor"
    under_floor = "minecraft:under_floor"
    vents = cond(in_biome("hydrothermal_vents"), seq(
        cond(on_floor, seq(cond(patch(0.45), block("magma_block")), cond(patch(-0.1, 0.45), block("blackstone")), block("basalt"))),
        cond(under_floor, seq(cond(patch(0.0), block("blackstone")), block("smooth_basalt"))),
    ))
    reef = cond(in_biome("glowing_reef"), seq(
        cond(on_floor, seq(
            cond(patch(0.55), block("tube_coral_block")),
            cond(patch(0.4, 0.55), block("brain_coral_block")),
            cond(patch(-0.55, -0.4), block("horn_coral_block")),
            cond(patch(-1.0e9, -0.55), block("bubble_coral_block")),
            cond(patch(0.2, 1.0e9, "detail"), block("prismarine")),
            block("sand"),
        )),
        cond(under_floor, block("sandstone")),
    ))
    bones = cond(in_biome("bone_fields"), seq(
        cond(on_floor, seq(cond(patch(0.5, 1.0e9, "detail"), block("bone_block")), cond(patch(0.0), block("calcite")), block("gravel"))),
        cond(under_floor, seq(cond(patch(-0.2), block("calcite")), block("diorite"))),
    ))
    plains = cond(in_biome("abyssal_plains"), seq(
        cond(on_floor, seq(cond(patch(0.42), block("clay")), cond(patch(-1.0e9, -0.5), block("gravel")), block("mud"))),
        cond(under_floor, block("packed_mud")),
    ))
    slopes = cond(in_biome("midnight_zone", "twilight_waters"), seq(
        cond(STEEP, cond(under_floor, block("tuff"))),
        cond(on_floor, seq(cond(patch(0.35), block("gravel")), cond(patch(-0.3, 0.35), block("tuff")), block("cobbled_deepslate"))),
    ))
    return seq(
        "minecraft:bedrock_floor",
        cond(STEEP, cond(under_floor, seq(cond(patch(0.3, 1.0e9, "detail"), block("tuff")), block("deepslate")))),
        vents, reef, bones, plains, slopes,
    )


# --- features -----------------------------------------------------------------------------

def count(n):
    return {"type": "minecraft:count", "count": n}


def rarity(chance):
    return {"type": "minecraft:rarity_filter", "chance": chance}


IN_SQUARE = {"type": "minecraft:in_square"}
BIOME = {"type": "minecraft:biome"}


def height_range(lo, hi):
    return {"type": "minecraft:height_range",
            "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}


def floor_placement(n=None, chance=None):
    placement = []
    if chance:
        placement.append(rarity(chance))
    if n:
        placement.append(count(n))
    return placement + [IN_SQUARE, {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"}, BIOME]


def ore(name, size, n, lo, hi, discard=0.0):
    feature = {
        "type": "minecraft:ore",
        "discard_chance_on_air_exposure": discard,
        "size": size,
        "targets": [{
            "state": f"minecraft:deepslate_{name}_ore",
            "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"},
        }],
    }
    return feature, [count(n), IN_SQUARE, height_range(lo, hi), BIOME]


def abyss_feature(style):
    return {"type": f"{NS}:abyss_feature", "style": style}


def features():
    out = {
        "ore_diamond": ore("diamond", 8, 4, 8, 220, 0.5),
        "ore_gold": ore("gold", 9, 8, 8, 400),
        "ore_lapis": ore("lapis", 7, 5, 8, 300),
        "ore_redstone": ore("redstone", 8, 6, 8, 200),
        "ore_emerald": ore("emerald", 3, 6, 120, 600),
        "ore_copper": ore("copper", 12, 8, 8, 600),
        "glow_lichen": ("minecraft:glow_lichen", [count(90), IN_SQUARE, height_range(8, 900), BIOME]),
        "giant_kelp": (abyss_feature("giant_kelp"), floor_placement(n=5)),
        "garden_reef": ({"type": f"{NS}:sea_garden", "style": "abyss_reef"}, floor_placement(n=4)),
        "garden_vents": ({"type": f"{NS}:sea_garden", "style": "abyss_vents"}, floor_placement(n=4)),
        "garden_floor": ({"type": f"{NS}:sea_garden", "style": "abyss_floor"}, floor_placement(n=2)),
        "bone_pile": (abyss_feature("bone_pile"), floor_placement(n=2)),
        "bone_pile_sparse": (abyss_feature("bone_pile"), floor_placement(chance=3)),
        "diver_remains": (abyss_feature("diver_remains"), floor_placement(chance=14)),
        "hanging_glow": (abyss_feature("hanging_glow"), [count(40), IN_SQUARE, height_range(280, 900), BIOME]),
        "sea_pickles": ("minecraft:sea_pickle", floor_placement(n=6)),
        "sea_pickles_sparse": ("minecraft:sea_pickle", floor_placement(n=1, chance=3)),
        "reef_coral": ("minecraft:warm_ocean_vegetation", floor_placement(n=8)),
        "black_smoker": (abyss_feature("black_smoker"), floor_placement(n=2)),
        "soul_vent": (abyss_feature("soul_vent"), floor_placement(chance=3)),
        "glow_reef": (abyss_feature("glow_reef"), floor_placement(n=3)),
        "nodules": (abyss_feature("nodules"), floor_placement(n=2)),
        "whale_fall": (abyss_feature("whale_fall"), floor_placement(chance=5)),
        "whale_fall_rare": (abyss_feature("whale_fall"), floor_placement(chance=60)),
    }
    files = {}
    for name, (feature, placement) in out.items():
        if isinstance(feature, dict):
            files[f"{NS}/worldgen/feature/abyss/{name}.json"] = feature
            feature = f"{NS}:abyss/{name}"
        files[f"{NS}/worldgen/placed_feature/abyss/{name}.json"] = {"feature": feature, "placement": placement}
    return files


# --- structures ---------------------------------------------------------------------------

def structures():
    files = {}

    def structure(name, biomes, spacing, separation, salt):
        files[f"{NS}/worldgen/structure/{name}.json"] = {
            "type": f"{NS}:{name}",
            "biomes": f"#{NS}:has_structure/{name}",
            "spawn_overrides": {},
            "step": "surface_structures",
        }
        files[f"{NS}/worldgen/structure_set/{name}.json"] = {
            "placement": {"type": "minecraft:random_spread", "salt": salt, "separation": separation, "spacing": spacing},
            "structures": [{"structure": f"{NS}:{name}", "weight": 1}],
        }
        files[f"{NS}/tags/worldgen/biome/has_structure/{name}.json"] = {"values": biomes}

    structure("abyssal_rift", ["minecraft:deep_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_lukewarm_ocean",
                                       "minecraft:deep_frozen_ocean"], 26, 9, 207914625)
    structure("sunken_temple", [f"{NS}:abyssal_plains", f"{NS}:glowing_reef", f"{NS}:bone_fields"], 22, 8, 581230447)
    structure("bathysphere", [f"{NS}:abyssal_plains", f"{NS}:glowing_reef", f"{NS}:hydrothermal_vents",
                                             f"{NS}:bone_fields", f"{NS}:midnight_zone", f"{NS}:twilight_waters"], 12, 5, 309871244)
    structure("leviathan_lair", [f"{NS}:bone_fields", f"{NS}:abyssal_plains"], 36, 14, 778120931)
    structure("vent_shrine", [f"{NS}:hydrothermal_vents"], 18, 6, 412907733)
    structure("drowned_sanctum", [f"{NS}:abyssal_plains", f"{NS}:bone_fields", f"{NS}:glowing_reef", f"{NS}:hydrothermal_vents"],
              40, 16, 923401877)
    files[f"{NS}/tags/worldgen/structure/on_sanctum_maps.json"] = {"values": [f"{NS}:drowned_sanctum"]}
    # Ships that sank, and ruins that were drowned long ago, lie on the floor of the Abyss too.
    floor = [f"{NS}:abyssal_plains", f"{NS}:bone_fields", f"{NS}:glowing_reef", f"{NS}:hydrothermal_vents"]
    files["minecraft/tags/worldgen/biome/has_structure/shipwreck.json"] = {"replace": False, "values": floor}
    files["minecraft/tags/worldgen/biome/has_structure/ocean_ruin_cold.json"] = {"replace": False, "values": floor}
    return files


# --- the damage that pressure does ---------------------------------------------------------

def damage():
    return {
        f"{NS}/damage_type/pressure.json": {
            "exhaustion": 0.0,
            "message_id": "naturesmod.pressure",
            "scaling": "never",
        },
        "minecraft/tags/damage_type/bypasses_armor.json": {"replace": False, "values": [f"{NS}:pressure"]},
        "minecraft/tags/damage_type/no_knockback.json": {"replace": False, "values": [f"{NS}:pressure"]},
    }


# --- treasure -----------------------------------------------------------------------------

def _count(c):
    if isinstance(c, (int, float)):
        return c
    return {"type": "minecraft:uniform", "min": c[0], "max": c[1]}


def item(name, weight=1, count=None, *mods):
    name = name if ":" in name else f"minecraft:{name}"
    entry = {"type": "minecraft:item", "name": name, "weight": weight}
    modifiers = []
    if count is not None:
        modifiers.append({"type": "minecraft:set_count", "count": _count(count)})
    modifiers += list(mods)
    if modifiers:
        entry["modifier"] = modifiers if len(modifiers) > 1 else modifiers[0]
    return entry


def empty(weight):
    return {"type": "minecraft:empty", "weight": weight}


def pool(rolls, *entries):
    return {"rolls": _count(rolls), "entries": list(entries)}


def chest(*pools):
    return {"type": "minecraft:chest", "pools": list(pools)}


ENCHANT = {"type": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot"}
SEA_ENCHANT = {"type": "minecraft:enchant_randomly",
               "options": ["minecraft:depth_strider", "minecraft:respiration", "minecraft:aqua_affinity", "minecraft:riptide",
                           "minecraft:loyalty", "minecraft:impaling", "minecraft:luck_of_the_sea", "minecraft:lure"]}
DEEP_ENCHANT = {"type": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": 25, "max": 39},
                "options": "#minecraft:on_random_loot"}
WATER_BREATHING = {"type": "minecraft:set_potion", "id": "minecraft:long_water_breathing"}
SANCTUM_MAP = [
    {"type": "minecraft:exploration_map", "decoration": "minecraft:monument", "destination": f"#{NS}:on_sanctum_maps",
     "skip_existing_chunks": False, "zoom": 2, "search_radius": 100},
    {"type": "minecraft:set_name", "name": {"translate": "filled_map.naturesmod.drowned_sanctum"}, "target": "item_name"},
]
RIFT_MAP = [
    {"type": "minecraft:exploration_map", "decoration": "minecraft:red_x", "destination": f"#{NS}:on_rift_maps",
     "skip_existing_chunks": False, "zoom": 2},
    {"type": "minecraft:set_name", "name": {"translate": "filled_map.naturesmod.abyssal_rift"}, "target": "item_name"},
]


def loot(vanilla):
    tables = {
        "chests/abyssal_rift": chest(
            pool((3, 6),
                 item(f"{NS}:sea_scale", 20, (2, 6)), item("prismarine_shard", 15, (2, 8)), item("prismarine_crystals", 10, (2, 6)),
                 item("nautilus_shell", 8, (1, 2)), item(f"{NS}:raw_fish_fillet", 10, (2, 5)), item("gold_ingot", 6, (1, 4)),
                 item("diamond", 2, (1, 2)), item("book", 4, 1, SEA_ENCHANT)),
            pool(1, empty(6), item(f"{NS}:tidewalker_helmet"), item(f"{NS}:tidewalker_boots"), item("heart_of_the_sea"),
                 item("potion", 2, None, WATER_BREATHING)),
            pool(1, empty(5), item(f"{NS}:abyssal_upgrade_smithing_template")),
        ),
        "chests/sunken_temple": chest(
            pool(1, item(f"{NS}:abyssal_upgrade_smithing_template", 3), empty(2)),
            pool((4, 8),
                 item(f"{NS}:abyssal_scale", 20, (2, 6)), item("diamond", 10, (1, 3)), item("gold_ingot", 15, (2, 7)),
                 item("emerald", 10, (2, 6)), item("prismarine_crystals", 10, (3, 9)), item("golden_apple", 5),
                 item("book", 8, 1, SEA_ENCHANT), item("book", 6, 1, ENCHANT), item("nautilus_shell", 6, (1, 3)),
                 item("potion", 6, None, WATER_BREATHING)),
            pool(1, empty(7), item("heart_of_the_sea", 2), item("trident", 1, None, DEEP_ENCHANT), item("conduit", 1),
                 item("enchanted_golden_apple", 1)),
            pool(1, item(f"{NS}:tide_sigil")),
            pool(1, item("map", 1, None, *SANCTUM_MAP)),
        ),
        "chests/bathysphere": chest(
            pool((3, 7),
                 item("compass", 4), item("clock", 3), item("spyglass", 3), item("map", 5), item("paper", 8, (2, 8)),
                 item("bread", 10, (1, 4)), item(f"{NS}:cooked_fish_fillet", 10, (2, 6)), item("iron_ingot", 10, (2, 6)),
                 item("torch", 6, (4, 12)), item("lantern", 3), item(f"{NS}:sea_scale", 8, (2, 5)),
                 item("potion", 6, None, WATER_BREATHING), item("book", 3, 1, SEA_ENCHANT)),
            pool(1, empty(5), item(f"{NS}:tidewalker_chestplate"), item(f"{NS}:tidewalker_leggings"), item("map", 2, None, *RIFT_MAP)),
            pool(1, empty(4), item(f"{NS}:abyssal_upgrade_smithing_template")),
            pool(1, item(f"{NS}:deep_sigil")),
        ),
        "chests/vent_shrine": chest(
            pool(1, item(f"{NS}:ember_sigil")),
            pool((3, 6),
                 item("magma_cream", 10, (1, 4)), item("blaze_powder", 8, (1, 4)), item("gold_ingot", 10, (2, 6)),
                 item("obsidian", 8, (1, 4)), item("fire_charge", 6, (1, 3)), item(f"{NS}:abyssal_scale", 10, (2, 5)),
                 item("diamond", 3, (1, 2)), item("book", 4, 1, DEEP_ENCHANT),
                 item("potion", 5, None, {"type": "minecraft:set_potion", "id": "minecraft:long_fire_resistance"})),
        ),
        "chests/drowned_sanctum": chest(
            pool((5, 9),
                 item("diamond", 12, (2, 5)), item("emerald", 10, (3, 8)), item("gold_block", 6, (1, 2)), item("heart_of_the_sea", 4),
                 item(f"{NS}:abyssal_plate", 10, (1, 3)), item(f"{NS}:abyssal_scale", 12, (3, 8)), item(f"{NS}:pearl", 10, (2, 6)),
                 item("trident", 3, None, ENCHANT), item("book", 8, 1, DEEP_ENCHANT), item("enchanted_golden_apple", 1),
                 item("nautilus_shell", 6, (1, 3)), item("golden_apple", 6, (1, 2))),
            pool(1, empty(2), item(f"{NS}:abyssal_upgrade_smithing_template")),
        ),
        "chests/diver_remains": chest(
            pool((2, 5),
                 item("bone", 12, (2, 6)), item("rotten_flesh", 8, (1, 4)), item("iron_ingot", 8, (1, 4)), item("gold_nugget", 8, (3, 12)),
                 item("compass", 3), item("lantern", 3), item("paper", 6, (1, 5)), item(f"{NS}:abyssal_scale", 6, (1, 3)),
                 item("potion", 5, None, WATER_BREATHING), item("book", 3, 1, SEA_ENCHANT), item("diamond", 2, (1, 2)),
                 item("name_tag", 1)),
            pool(1, empty(3), item(f"{NS}:tidewalker_helmet"), item(f"{NS}:tidewalker_boots"), item("map", 1, None, *RIFT_MAP)),
        ),
        "chests/leviathan_hoard": chest(
            pool(1, item(f"{NS}:abyssal_upgrade_smithing_template", 1, (1, 2))),
            pool((5, 9),
                 item("diamond", 15, (2, 6)), item("gold_block", 8, (1, 3)), item("emerald_block", 4, (1, 2)),
                 item(f"{NS}:abyssal_plate", 10, (1, 3)), item(f"{NS}:abyssal_scale", 15, (3, 8)), item("netherite_scrap", 4, (1, 2)),
                 item("book", 8, 1, DEEP_ENCHANT), item("golden_apple", 6, (1, 2)), item("heart_of_the_sea", 3)),
            pool(1, empty(3), item("enchanted_golden_apple", 1), item("trident", 1, None, DEEP_ENCHANT)),
        ),
    }
    files = {f"{NS}/loot_table/{name}.json": table for name, table in tables.items()}
    # Treasure maps to the nearest Abyssal Rift turn up in the sea's treasure chests.
    for name, chance in (("shipwreck_map", 1), ("underwater_ruin_big", 2), ("buried_treasure", 1)):
        table = copy.deepcopy(vanilla.read(f"minecraft/loot_table/chests/{name}.json"))
        table["pools"].append(pool(1, empty(chance), item("map", 1, None, *RIFT_MAP)))
        files[f"minecraft/loot_table/chests/{name}.json"] = table
    files[f"{NS}/tags/worldgen/structure/on_rift_maps.json"] = {"values": [f"{NS}:abyssal_rift"]}
    return files


def noise_json(params):
    """26.3 noise parameters: the base octave, the number of octaves and their amplitudes."""
    amplitudes = params["amplitudes"]
    out = {"base_amplitude": 0.95, "base_octave": params["firstOctave"]}
    if len(amplitudes) > 1:
        out["octave_count"] = len(amplitudes)
    if any(a != 1.0 for a in amplitudes):
        out["amplitude_modifiers"] = amplitudes
    return out


def build(vanilla):
    out = {
        f"{NS}/dimension_type/abyss.json": dimension_type(),
        f"{NS}/dimension/abyss.json": dimension(),
        f"{NS}/worldgen/noise_settings/abyss.json": noise_settings(),
        f"{NS}/worldgen/material_rule/abyss.json": material_rule(),
    }
    for name, params in NOISES.items():
        out[f"{NS}/worldgen/noise/abyss/{name}.json"] = noise_json(params)
    for name, fn in density_functions().items():
        out[f"{NS}/worldgen/density_function/abyss/{name}.json"] = fn
    for name, b in biomes().items():
        out[f"{NS}/worldgen/biome/{name}.json"] = b
    out.update(features())
    out.update(structures())
    out.update(damage())
    out.update(loot(vanilla))
    # The noise settings must not carry an explicit null: leave aquifers out entirely.
    del out[f"{NS}/worldgen/noise_settings/abyss.json"]["aquifers"]
    return out
