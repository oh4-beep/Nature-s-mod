"""The new End.

The dragon's island in the middle is left as it was, and so is the ring of void around it. But
beyond the void, where vanilla scatters lumpy islands, a whole new world floats in the dark:

  * Continents: great flat-topped landmasses hundreds of blocks across, hanging over the void on
    undersides that dangle with spikes of rock, with hills and here and there a needle spire.
  * Sky isles: small islands floating high above the continents (y ~150-210), reached by the
    updraft vents that blow up from the land below.

Seven new biomes divide the continents between them (the End's biome source keeps the dragon's
island and picks them by its own noise, through Fabric's End biome API):

  * Chorus Forest: purple moss under towering chorus trees hung with glowing bulbs.
  * Crystal Highlands: pale stone split by forests of glowing void crystal.
  * Starfall Plains: fields of glittering stardust pocked with the craters of fallen stars.
  * Void Marsh: dark moss and still black pools, bounce blooms and hanging void vines.
  * Shattered Spires: black obsidian spires and shards hanging in the air.
  * Enderglow Wilds: rolling hills of moss and ender grass alive with glowing blooms.
  * Sky Isles: the little islands of the upper air.

Vanilla's End Highlands keep a share of the continents, so the End Cities still have a home of
their own (they stand in the new biomes too).
"""

from abyss import DEEP_ENCHANT, ENCHANT, chest, empty, item, noise_json, pool

NS = "naturesmod"
DF = f"{NS}:end"
HEIGHT = 256
RING = 1000           # the void ring: the continents begin this far from the middle
SKY = 132             # above this the sky isles' biome takes over


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


def cache(x):
    return {"type": "minecraft:cache", "input": x}


def lerp(alpha, first, second):
    return {"type": "minecraft:lerp", "alpha": alpha, "first": first, "second": second}


def square(x):
    return {"type": "minecraft:square", "input": x}


def neg(x):
    return {"type": "minecraft:negate", "input": x}


def gradient_y(y0, v0, y1, v1):
    return {"type": "minecraft:gradient", "axis": "y", "from_coordinate": y0, "from_value": v0, "to_coordinate": y1, "to_value": v1}


def noise(name, xz=1.0, y=0.0):
    return {"type": "minecraft:noise", "noise": f"{NS}:end/{name}", "xz_scale": xz, "y_scale": y}


def ref(name):
    return f"{DF}/{name}"


Y = "minecraft:y"

NOISES = {
    "continent": {"firstOctave": -9, "amplitudes": [1.0, 1.0, 0.5]},
    "relief": {"firstOctave": -8, "amplitudes": [1.0, 0.5]},
    "hills": {"firstOctave": -6, "amplitudes": [1.0, 0.5, 0.25]},
    "thickness": {"firstOctave": -7, "amplitudes": [1.0, 0.5]},
    "roots": {"firstOctave": -3, "amplitudes": [1.0, 0.5]},
    "sky": {"firstOctave": -6, "amplitudes": [1.0, 0.6, 0.3]},
    "sky_height": {"firstOctave": -7, "amplitudes": [1.0]},
    "spire": {"firstOctave": -4, "amplitudes": [1.0, 0.3]},
    "spire_region": {"firstOctave": -8, "amplitudes": [1.0]},
    "carve": {"firstOctave": -5, "amplitudes": [1.0, 0.5, 0.25]},
    "patch": {"firstOctave": -5, "amplitudes": [1.0, 0.5]},
    "detail": {"firstOctave": -3, "amplitudes": [1.0, 1.0]},
}


def vanilla_island():
    """The dragon's island, exactly as vanilla makes it, without vanilla's outer islands."""
    central = {"type": "minecraft:slice", "axis": "y", "coordinate": 0, "input": mul(add(clamp(
        {"type": "minecraft:sub", "left": 100.0, "right": {"type": "minecraft:distance_to_point", "metric": "euclidean", "point": [0, 0, 0]}},
        -100.0, 80.0), -8.0), 0.0078125)}
    sloped = add(central, "minecraft:end/base_3d_noise")
    return mul({"type": "minecraft:blend_density", "input": lerp(
        gradient_y(4, 0.0, 32, 1.0), -0.234375,
        lerp(gradient_y(56, 1.0, 312, 0.0), -23.4375, sloped))}, 0.64)


def density_functions():
    out = {}
    distance = {"type": "minecraft:slice", "axis": "y", "coordinate": 0,
                "input": {"type": "minecraft:distance_to_point", "metric": "euclidean", "point": [0, 0, 0]}}
    # 0 inside the void ring, rising to 1 a little way beyond it.
    out["outer"] = cache(clamp(add(mul(distance, 1.0 / 140.0), -RING / 140.0), 0.0, 1.0))
    # How much of a continent there is here: 0 over the void between them, 1 in their hearts.
    out["land"] = cache(mul(clamp(mul(add(noise("continent"), 0.05), 3.2), 0.0, 1.0), ref("outer")))
    # Needle spires rise in some regions of the continents.
    spire = clamp(mul(add(noise("spire"), -0.5), 2.6), 0.0, 1.0)
    spire_region = clamp(mul(add(noise("spire_region"), -0.1), 3.0), 0.0, 1.0)
    out["spire"] = cache(mul(mul(square(spire), spire_region), ref("land")))
    # The height of the ground: rolling, higher in the hearts of the continents, sloping down
    # to their edges, with the spires standing up out of it.
    out["surface"] = cache(add(add(add(70.0, mul(noise("relief"), 14.0)), mul(noise("hills"), 9.0)),
                               add(mul(add(ref("land"), -1.0), 16.0), mul(ref("spire"), 85.0))))
    # The underside: thick under the middle of a continent, thin at its edges, hung with spikes.
    roots = mul(clamp(mul(add(noise("roots"), -0.15), 2.0), 0.0, 1.0), 22.0)
    thickness = add(mul(ref("land"), add(30.0, add(mul(noise("thickness"), 16.0), roots))), -7.0)
    out["bottom"] = cache(add(ref("surface"), mul(thickness, -1.0)))
    carve = noise("carve", 1.0, 1.4)
    slab = mn(add(ref("surface"), neg(Y)), add(Y, neg(ref("bottom"))))
    continents = add(mul(slab, 1.0 / 6.0), mul(carve, 0.45))
    # Sky isles: little islands floating high over the continents, domed on top, coming to a
    # point beneath.
    isle = mul(clamp(mul(add(noise("sky"), -0.42), 3.0), 0.0, 1.0), ref("outer"))
    out["isle"] = cache(isle)
    sky_level = add(172.0, mul(noise("sky_height"), 26.0))
    top = add(sky_level, mul(ref("isle"), 5.0))
    under = add(sky_level, add(mul(ref("isle"), -24.0), 3.0))
    sky = add(mul(mn(add(top, neg(Y)), add(Y, neg(under))), 0.25), mul(carve, 0.3))
    outer_terrain = lerp(ref("outer"), -1.0, mx(continents, sky))
    terrain = mx(vanilla_island(), outer_terrain)
    # Nothing touches the very bottom or the very top of the world.
    terrain = mn(mn(terrain, mul(add(Y, -6.0), 0.25)), mul(add(neg(Y), 248.0), 0.25))
    out["final_density"] = add(
        {"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 4, "input": terrain},
        {"type": "minecraft:beardifier"},
    )
    # What the End's biome source reads as "erosion": the continents are highlands, the upper
    # air is small islands (each replaced by the mod's biomes through Fabric's End biome API).
    out["biome_layer"] = lerp(clamp(gradient_y(SKY, 0.0, SKY + 10, 1.0), 0.0, 1.0), 0.5, -0.5)
    return out


def noise_settings():
    return {
        "default_block": "minecraft:end_stone",
        "default_fluid": "minecraft:air",
        "disable_mob_generation": True,
        "legacy_random_source": True,
        "material_rule": f"{NS}:end",
        "noise": {"height": HEIGHT, "min_y": 0},
        "noise_router": {
            "chunk_surface_level": 0.0,
            "continents": 0.0,
            "depth": 0.0,
            "erosion": ref("biome_layer"),
            "final_density": ref("final_density"),
            "ridges": 0.0,
            "temperature": 0.0,
            "vegetation": 0.0,
        },
        "sea_level": 0,
        "spawn_target": [],
    }


# --- biomes ---------------------------------------------------------------------------------

BIOMES = ["chorus_forest", "crystal_highlands", "starfall_plains", "void_marsh", "shattered_spires", "enderglow_wilds", "sky_isles"]
# Continental biomes and their share of the continents (vanilla's highlands have weight 1).
CONTINENTAL = {"chorus_forest": 1.3, "crystal_highlands": 1.0, "starfall_plains": 1.0, "void_marsh": 0.9, "shattered_spires": 0.9,
               "enderglow_wilds": 1.1}

LOCAL = 2
SURFACE = 4
ORES = 6
VEGETATION = 9


def spawn(entity, weight, lo, hi):
    count = lo if lo == hi else {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}
    return {"type": entity if ":" in entity else f"minecraft:{entity}", "count": count, "weight": weight}


def biome(fog, particles, features, water="#3f76e4", water_fog="#050533", sky_light=None, endermen=10):
    attributes = {
        "minecraft:gameplay/natural_mob_spawns": {
            "argument": {"spawn_costs": {}, "spawns_by_category": {"monster": [spawn("enderman", endermen, 1, 4)]}},
            "modifier": "overlay",
        },
        "minecraft:visual/fog_color": fog,
        "minecraft:visual/water_fog_color": water_fog,
    }
    if sky_light:
        attributes["minecraft:visual/sky_light_color"] = sky_light
    if particles:
        attributes["minecraft:visual/ambient_particles"] = {
            "argument": [{"particle": {"type": f"minecraft:{p}"}, "probability": chance} for p, chance in particles],
            "modifier": "append",
        }
    steps = [[] for _ in range(11)]
    for step, name in features:
        steps[step].append(name if ":" in name else f"{NS}:end/{name}")
    return {
        "attributes": attributes,
        "carvers": [],
        "downfall": 0.5,
        "effects": {"water_color": water},
        "features": steps,
        "has_precipitation": False,
        "temperature": 0.5,
    }


# Every End biome shares one chunk generator, so the mod's features keep this one order in
# every step of every biome they appear in.
ORDER = ["end_gateway_return", "shard_field", "spires", "spires_sparse", "crystal_spikes", "crystal_spikes_sparse", "meteor", "meteor_rare",
         "void_pool", "void_pool_sparse", "chorus_trees", "chorus_trees_sparse", "bounce_blooms", "bounce_blooms_sparse",
         "updraft_vents", "updraft_vents_sparse", "hanging_vines", "end_garden", "end_garden_sparse", "crystal_clusters",
         "starmetal_ore", "chorus_plant"]


def features_of(*names):
    """(step, name) pairs for a biome, in the shared order."""
    steps = {"end_gateway_return": SURFACE, "chorus_plant": VEGETATION, "hanging_vines": VEGETATION, "end_garden": VEGETATION,
             "end_garden_sparse": VEGETATION, "crystal_clusters": VEGETATION, "starmetal_ore": ORES}
    out = []
    for name in sorted(names, key=ORDER.index):
        step = steps.get(name, LOCAL)
        out.append((step, "minecraft:" + name if name in ("end_gateway_return", "chorus_plant") else name))
    return out


def biomes():
    return {
        "chorus_forest": biome("#1e0f26", [("portal", 0.02), ("reverse_portal", 0.004)], features_of(
            "end_gateway_return", "chorus_trees", "bounce_blooms_sparse", "updraft_vents_sparse", "hanging_vines", "end_garden",
            "chorus_plant", "starmetal_ore")),
        "crystal_highlands": biome("#121a2a", [("end_rod", 0.004), ("glow", 0.002)], features_of(
            "end_gateway_return", "crystal_spikes", "shard_field", "updraft_vents_sparse", "hanging_vines", "end_garden_sparse",
            "crystal_clusters", "starmetal_ore")),
        "starfall_plains": biome("#1a1620", [("end_rod", 0.0025), ("white_ash", 0.01)], features_of(
            "end_gateway_return", "meteor", "crystal_spikes_sparse", "updraft_vents_sparse", "hanging_vines", "end_garden_sparse",
            "starmetal_ore")),
        "void_marsh": biome("#0b0a14", [("reverse_portal", 0.008), ("dragon_breath", 0.0006)], features_of(
            "end_gateway_return", "void_pool", "bounce_blooms", "updraft_vents_sparse", "hanging_vines", "end_garden",
            "starmetal_ore"), water="#2a1a4a", water_fog="#0c0618"),
        "shattered_spires": biome("#140a1c", [("portal", 0.012), ("ash", 0.006)], features_of(
            "end_gateway_return", "shard_field", "spires", "updraft_vents", "hanging_vines", "end_garden_sparse", "starmetal_ore"),
            endermen=16),
        "enderglow_wilds": biome("#1a1030", [("portal", 0.01), ("end_rod", 0.0015)], features_of(
            "end_gateway_return", "spires_sparse", "meteor_rare", "chorus_trees_sparse", "bounce_blooms_sparse", "updraft_vents_sparse",
            "hanging_vines", "end_garden", "crystal_clusters", "chorus_plant", "starmetal_ore")),
        "sky_isles": biome("#2a2040", [("end_rod", 0.003), ("cloud", 0.0008)], features_of(
            "chorus_trees_sparse", "bounce_blooms_sparse", "hanging_vines", "end_garden", "crystal_clusters", "chorus_plant",
            "starmetal_ore"), endermen=4),
    }


# --- surface ------------------------------------------------------------------------------

def block(b):
    return {"type": "minecraft:block", "result_state": b if ":" in b else f"minecraft:{b}"}


def mod_block(b):
    return {"type": "minecraft:block", "result_state": f"{NS}:{b}"}


def cond(test, then):
    return {"type": "minecraft:condition", "if_true": test, "then_run": then}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def in_biome(*names):
    ids = [n if ":" in n else f"{NS}:{n}" for n in names]
    return {"type": "minecraft:biome", "biome_is": ids if len(ids) > 1 else ids[0]}


def patch(lo, hi=1.0e9, which="patch"):
    return {"type": "minecraft:noise_threshold", "noise": f"{NS}:end/{which}", "min_threshold": lo, "max_threshold": hi}


ON_FLOOR = "minecraft:on_floor"
UNDER_FLOOR = "minecraft:under_floor"
ON_CEILING = "minecraft:on_ceiling"
STEEP = {"type": "minecraft:steep"}


def material_rule():
    moss = mod_block("end_moss")
    voidstone = mod_block("voidstone")
    rules = [
        # Bare cliffs are dark voidstone (the undersides of the lands are, below).
        cond(in_biome(*BIOMES), cond(STEEP, voidstone)),
        cond(in_biome("chorus_forest", "enderglow_wilds", "sky_isles"), cond(ON_FLOOR, seq(cond(patch(-1.0e9, -0.55), block("end_stone")), moss))),
        cond(in_biome("void_marsh"), seq(
            cond(ON_FLOOR, seq(cond(patch(-1.0e9, -0.35), voidstone), moss)),
            cond(UNDER_FLOOR, voidstone),
        )),
        cond(in_biome("crystal_highlands"), cond(ON_FLOOR, seq(
            cond(patch(0.55, 1.0e9, "detail"), voidstone),
            cond(patch(0.3), block("calcite")),
            block("end_stone"),
        ))),
        cond(in_biome("starfall_plains"), seq(
            cond(ON_FLOOR, seq(cond(patch(-1.0e9, -0.45), block("end_stone")), mod_block("stardust"))),
            cond(UNDER_FLOOR, cond(patch(-0.2), mod_block("stardust"))),
        )),
        cond(in_biome("shattered_spires"), seq(
            cond(ON_FLOOR, seq(cond(patch(0.62, 1.0e9, "detail"), block("crying_obsidian")), cond(patch(0.4), block("obsidian")), voidstone)),
            cond(UNDER_FLOOR, voidstone),
        )),
    ]
    # Every rule paints the ground's surface, just under it, or the undersides of the lands: the
    # stone deep inside the continents skips the lot (and its biome lookups) rather than test each block.
    return seq(
        cond(ON_CEILING, cond(in_biome(*BIOMES), voidstone)),
        cond(UNDER_FLOOR, seq(*rules)),
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


def surface_placement(n=None, chance=None):
    placement = []
    if chance:
        placement.append(rarity(chance))
    if n:
        placement.append(count(n))
    return placement + [IN_SQUARE, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, BIOME]


def end_feature(style):
    return {"type": f"{NS}:end_feature", "style": style}


def features():
    out = {
        "chorus_trees": (end_feature("chorus_tree"), surface_placement(n=3)),
        "chorus_trees_sparse": (end_feature("chorus_tree"), surface_placement(chance=3)),
        "crystal_spikes": (end_feature("crystal_spikes"), surface_placement(n=1)),
        "crystal_spikes_sparse": (end_feature("crystal_spikes"), surface_placement(chance=6)),
        "meteor": (end_feature("meteor"), surface_placement(chance=3)),
        "meteor_rare": (end_feature("meteor"), surface_placement(chance=24)),
        "spires": (end_feature("spire"), surface_placement(n=1)),
        "spires_sparse": (end_feature("spire"), surface_placement(chance=8)),
        "shard_field": (end_feature("shards"), surface_placement(chance=2)),
        "void_pool": (end_feature("void_pool"), surface_placement(n=1)),
        "void_pool_sparse": (end_feature("void_pool"), surface_placement(chance=5)),
        "bounce_blooms": (end_feature("bounce_blooms"), surface_placement(n=1)),
        "bounce_blooms_sparse": (end_feature("bounce_blooms"), surface_placement(chance=5)),
        "updraft_vents": (end_feature("updraft_vents"), surface_placement(chance=2)),
        "updraft_vents_sparse": (end_feature("updraft_vents"), surface_placement(chance=7)),
        # Vines hang from the undersides of the lands: the feature looks up for a ceiling itself.
        "hanging_vines": (end_feature("hanging_vines"), [count(6), IN_SQUARE, height_range(8, 200), BIOME]),
        "end_garden": (end_feature("garden"), surface_placement(n=3)),
        "end_garden_sparse": (end_feature("garden"), surface_placement(n=1)),
        "crystal_clusters": (end_feature("crystal_clusters"), surface_placement(n=2)),
        "starmetal_ore": ({
            "type": "minecraft:ore", "discard_chance_on_air_exposure": 0.2, "size": 5,
            "targets": [{"state": f"{NS}:starmetal_ore",
                         "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:end_stone"}},
                        {"state": f"{NS}:starmetal_ore",
                         "target": {"predicate_type": "minecraft:block_match", "block": f"{NS}:voidstone"}}],
        }, [count(6), IN_SQUARE, height_range(10, 200), BIOME]),
    }
    files = {}
    for name, (feature, placement) in out.items():
        files[f"{NS}/worldgen/feature/end/{name}.json"] = feature
        files[f"{NS}/worldgen/placed_feature/end/{name}.json"] = {"feature": f"{NS}:end/{name}", "placement": placement}
    return files


# --- structures ----------------------------------------------------------------------------

END_TIERS = {"end_outpost": 6, "end_hall": 3, "end_citadel": 1}
WEAPONS = ["voidblade", "starcaller", "gravity_hammer", "skyrider_glaive"]


def end_loot():
    star = lambda w=1, n=1: item(f"{NS}:star_fragment", w, n)  # noqa: E731
    weapon = [item(f"{NS}:{w}") for w in WEAPONS]
    base = [item("ender_pearl", 10, (1, 3)), item("chorus_fruit", 8, (2, 6)), item("gold_ingot", 8, (2, 5)), item("iron_ingot", 8, (2, 6)),
            item("emerald", 6, (1, 4)), item("diamond", 4, (1, 2)), item(f"{NS}:raw_starmetal", 8, (1, 4)), item("experience_bottle", 6, (1, 4)),
            item(f"{NS}:void_crystal_cluster", 5, (1, 3)), item("book", 4, 1, ENCHANT)]
    rich = [item("diamond", 12, (2, 5)), item("gold_ingot", 8, (4, 10)), item("emerald", 8, (3, 8)), item(f"{NS}:starmetal_ingot", 10, (1, 4)),
            item("ender_pearl", 8, (2, 5)), item("experience_bottle", 6, (3, 8)), item("book", 8, 1, DEEP_ENCHANT), item("golden_apple", 4),
            item("shulker_shell", 5, (1, 2)), item("dragon_breath", 3, (1, 2)), item("netherite_scrap", 3, (1, 2))]
    return {
        "outpost": chest(pool((3, 6), *base), pool(1, empty(3), star())),
        "star": chest(pool((3, 5), item(f"{NS}:raw_starmetal", 12, (2, 6)), item(f"{NS}:starmetal_ingot", 6, (1, 2)), item(f"{NS}:meteorite", 6, (1, 3)),
                           item("diamond", 4, (1, 3)), item("firework_rocket", 6, (2, 6)), item("fire_charge", 4, (1, 3))), pool(1, empty(1), star(1, (1, 2)))),
        "nest": chest(pool((3, 6), item("shulker_shell", 10, (1, 3)), *base), pool(1, empty(3), star()), pool(1, empty(12), item("elytra"))),
        "library": chest(pool((4, 7), item("book", 14, 1, DEEP_ENCHANT), item("book", 8, 1, ENCHANT), item("paper", 8, (3, 10)),
                              item("lapis_lazuli", 8, (4, 12)), item("experience_bottle", 8, (3, 10)), item("name_tag", 2)), pool(1, empty(2), star())),
        "observatory": chest(pool((3, 6), item("spyglass", 6), item("compass", 5), item("clock", 5), item("map", 4), item("firework_rocket", 6, (3, 8)),
                                  item("amethyst_shard", 6, (2, 6)), item("glowstone_dust", 6, (2, 8)), *base[:4]), pool(1, empty(1), star(1, (1, 2)))),
        "temple": chest(pool((4, 8), *rich), pool(1, empty(1), star(1, (1, 2))), pool(1, empty(8), *weapon)),
        "ship": chest(pool((4, 8), item("firework_rocket", 10, (4, 12)), *rich),
                      pool(1, empty(4), item("elytra")), pool(1, empty(1), star()), pool(1, empty(10), *weapon)),
        "forge": chest(pool((4, 8), item(f"{NS}:starmetal_ingot", 14, (2, 6)), item(f"{NS}:raw_starmetal", 10, (3, 8)),
                            item(f"{NS}:starmetal_block", 2), item("netherite_scrap", 6, (1, 3)), item("diamond", 8, (2, 4)),
                            item("netherite_upgrade_smithing_template", 3), item("lava_bucket", 3)), pool(1, empty(2), star()),
                       pool(1, empty(8), *weapon)),
        "tower": chest(pool((4, 7), item("firework_rocket", 10, (4, 12)), item("phantom_membrane", 6, (1, 4)), item("feather", 6, (3, 8)), *rich),
                       pool(1, empty(1), star(1, (1, 2))), pool(1, empty(8), item("elytra")), pool(1, empty(10), *weapon)),
        "hall": chest(pool((4, 7), *rich), pool(1, empty(2), star())),
        "citadel": chest(pool((5, 9), *rich, item("diamond_block", 3), item("enchanted_golden_apple", 2), item("totem_of_undying", 2)),
                         pool(1, star(1, (2, 4))), pool(1, empty(2), item("elytra")), pool(1, empty(3), *weapon)),
        "colossus": chest(pool((5, 9), item("ender_eye", 6, (1, 4)), *rich, item("netherite_ingot", 3)),
                          pool(1, star(1, (1, 3))), pool(1, empty(3), *weapon), pool(1, empty(6), item("dragon_head"))),
        "palace": chest(pool((5, 9), item("gold_block", 8, (1, 3)), item("diamond_block", 4, (1, 2)), item("emerald_block", 6, (1, 3)),
                             item("netherite_ingot", 4, (1, 2)), item("enchanted_golden_apple", 3), *rich),
                        pool(1, star(1, (2, 4))), pool(1, empty(2), item("elytra")), pool(1, empty(2), *weapon)),
    }


def structures():
    files = {}
    for name in END_TIERS:
        files[f"{NS}/worldgen/structure/{name}.json"] = {
            "type": f"{NS}:{name}", "biomes": f"#{NS}:has_structure/end_structure", "spawn_overrides": {}, "step": "surface_structures"}
    files[f"{NS}/worldgen/structure_set/end_structures.json"] = {
        "placement": {"type": "minecraft:random_spread", "salt": 771203945, "separation": 3, "spacing": 9},
        "structures": [{"structure": f"{NS}:{name}", "weight": weight} for name, weight in END_TIERS.items()],
    }
    files[f"{NS}/tags/worldgen/biome/has_structure/end_structure.json"] = {
        "values": [f"{NS}:{b}" for b in BIOMES if b != "sky_isles"] + ["minecraft:end_highlands"]}
    files[f"{NS}/tags/worldgen/structure/end_structures.json"] = {"values": [f"{NS}:{name}" for name in END_TIERS]}
    for name, table in end_loot().items():
        files[f"{NS}/loot_table/chests/end/{name}.json"] = table
    return files


def build(vanilla):
    out = {
        "minecraft/worldgen/noise_settings/end.json": noise_settings(),
        f"{NS}/worldgen/material_rule/end.json": material_rule(),
    }
    for name, params in NOISES.items():
        out[f"{NS}/worldgen/noise/end/{name}.json"] = noise_json(params)
    for name, fn in density_functions().items():
        out[f"{NS}/worldgen/density_function/end/{name}.json"] = fn
    for name, b in biomes().items():
        out[f"{NS}/worldgen/biome/{name}.json"] = b
    out.update(features())
    out.update(structures())
    # End cities stand in the new lands too.
    out["minecraft/tags/worldgen/biome/has_structure/end_city.json"] = {
        "replace": False, "values": [f"{NS}:{b}" for b in BIOMES if b != "sky_isles"]}
    return out
