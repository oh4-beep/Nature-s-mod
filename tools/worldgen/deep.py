"""The forgotten riches at the bottom of the Abyss.

Below the last of the guardians, the floor of the Abyss is strewn with the ruins, vaults and
wonders of a drowned people (built in Java: worldgen/deep). This module writes their structure
set and the loot of their chests: netherite, diamonds, mob heads, music discs, elytra,
enchanted books at their highest levels (for the Abyssal Enchanting Table to double), and the
lost weapons of the deep.
"""

import os

from abyss import DEEP_ENCHANT, ENCHANT, NS, SEA_ENCHANT, WATER_BREATHING, chest, empty, item, pool

FLOOR = [f"{NS}:abyssal_plains", f"{NS}:bone_fields", f"{NS}:glowing_reef", f"{NS}:hydrothermal_vents"]
TIERS = {"deep_ruin": 6, "deep_vault": 3, "deep_wonder": 1}
WEAPONS = ["tidecleaver", "leviathan_fang", "abyssal_scythe", "stormcaller", "krakens_grasp", "pressure_maul", "sovereign_staff",
           "coral_rapier"]
HEADS = [("skeleton_skull", 4), ("wither_skeleton_skull", 3), ("zombie_head", 4), ("creeper_head", 4), ("piglin_head", 3),
         ("player_head", 2), ("dragon_head", 1)]
# The highest enchanting the deep can do: an enchanted book of the deep gets twice these levels.
GREAT = {"type": "minecraft:enchant_with_levels", "levels": 39, "options": "#minecraft:on_random_loot"}


def ref(name, weight=1):
    return {"type": "minecraft:loot_table", "value": f"{NS}:chests/abyss/parts/{name}", "weight": weight}


def parts(vanilla):
    """Small tables the treasure chests draw from: one disc, one head, one great book, one weapon."""
    root = vanilla.root
    discs = sorted(name[:-5] for name in os.listdir(os.path.join(root, "minecraft", "jukebox_song")))
    trims = sorted(name[:-5] for name in os.listdir(os.path.join(root, "minecraft", "trim_pattern")))
    books = []
    for name in sorted(os.listdir(os.path.join(root, "minecraft", "enchantment"))):
        enchantment = name[:-5]
        if "curse" in enchantment:
            continue
        level = vanilla.read(f"minecraft/enchantment/{name}")["max_level"]
        # Books of enchantments that go high are the prize: the table doubles them.
        books.append(item("book", 1 + level, 1, {"type": "minecraft:set_enchantments", "enchantments": {f"minecraft:{enchantment}": level}}))
    return {
        "disc": chest(pool(1, *[item(f"music_disc_{disc}") for disc in discs])),
        "head": chest(pool(1, *[item(head, weight) for head, weight in HEADS])),
        "great_book": chest(pool(1, *books)),
        "weapon": chest(pool(1, *[item(f"{NS}:{weapon}") for weapon in WEAPONS])),
        "trim": chest(pool(1, *[item(f"{trim}_armor_trim_smithing_template") for trim in trims])),
    }


def tables():
    great = lambda name, weight=1: item(name, weight, None, GREAT)  # noqa: E731
    potion = lambda which, weight=1, kind="potion": item(kind, weight, None, {"type": "minecraft:set_potion", "id": f"minecraft:{which}"})  # noqa: E731
    return {
        # The ruins: the most common, still richer than anything on land.
        "ruin": chest(
            pool((4, 7),
                 item("diamond", 10, (1, 3)), item("gold_ingot", 14, (2, 7)), item("emerald", 10, (2, 6)), item("iron_ingot", 10, (3, 8)),
                 item("netherite_scrap", 4, (1, 2)), item(f"{NS}:abyssal_scale", 8, (2, 5)), item(f"{NS}:pearl", 8, (1, 4)),
                 item("lapis_lazuli", 6, (4, 12)), item("amethyst_shard", 6, (2, 6)), item("nautilus_shell", 4), item("heart_of_the_sea", 1),
                 item("golden_apple", 4), item("experience_bottle", 6, (2, 6)), item("book", 5, 1, DEEP_ENCHANT),
                 item("potion", 4, None, WATER_BREATHING)),
            pool(1, empty(8), ref("disc"), ref("head"), ref("great_book"), ref("trim")),
            pool(1, empty(30), ref("weapon")),
        ),
        # The vaults: a fortune in every chest.
        "vault": chest(
            pool((5, 9),
                 item("diamond", 14, (2, 5)), item("gold_ingot", 10, (4, 10)), item("emerald", 10, (3, 8)), item("netherite_scrap", 8, (1, 3)),
                 item("netherite_ingot", 2), item("gold_block", 4, (1, 2)), item(f"{NS}:abyssal_plate", 6, (1, 2)), item("golden_apple", 6),
                 item("enchanted_golden_apple", 1), item("book", 8, 1, DEEP_ENCHANT), item("experience_bottle", 6, (3, 8)),
                 item("ender_pearl", 4, (1, 3)), item("heart_of_the_sea", 2), item("lapis_lazuli", 6, (6, 16))),
            pool(1, empty(3), ref("disc"), ref("head"), ref("great_book", 2), ref("trim")),
            pool(1, empty(12), ref("weapon"), item("elytra")),
            pool(1, empty(8), great("diamond_sword"), great("diamond_pickaxe"), great("diamond_chestplate"), great("diamond_helmet"),
                 great("trident")),
        ),
        # The wonders: the richest chests in Minecraft.
        "legendary": chest(
            pool((6, 10),
                 item("diamond", 12, (4, 9)), item("netherite_ingot", 6, (1, 3)), item("netherite_scrap", 8, (2, 4)),
                 item("diamond_block", 4, (1, 2)), item("emerald_block", 4, (1, 3)), item("gold_block", 6, (2, 4)),
                 item("enchanted_golden_apple", 3), item("golden_apple", 6, (2, 4)), item("totem_of_undying", 3),
                 item("experience_bottle", 6, (8, 16)), item("heart_of_the_sea", 3), item(f"{NS}:abyssal_plate", 6, (2, 4)),
                 item("netherite_upgrade_smithing_template", 3), item(f"{NS}:abyssal_upgrade_smithing_template", 3)),
            pool((2, 3), ref("great_book", 3), ref("disc"), ref("head"), ref("trim")),
            pool(1, empty(3), ref("weapon")),
            pool(1, empty(4), item("elytra")),
            pool(1, empty(13), item(f"{NS}:abyssal_enchanting_table")),
            pool(1, empty(6), great("netherite_sword"), great("netherite_pickaxe"), great("netherite_helmet"), great("netherite_chestplate"),
                 great("netherite_leggings"), great("netherite_boots"), great("mace")),
        ),
        "treasury": chest(
            pool((5, 9),
                 item("gold_block", 10, (1, 4)), item("gold_ingot", 14, (6, 16)), item("diamond", 12, (3, 8)), item("emerald_block", 6, (1, 3)),
                 item("emerald", 10, (6, 14)), item("diamond_block", 3, (1, 2)), item("netherite_ingot", 3, (1, 2)),
                 item("raw_gold_block", 6, (1, 3)), item("golden_apple", 6), item("enchanted_golden_apple", 1)),
            pool(1, empty(4), ref("head"), ref("disc"), great("golden_helmet"), great("golden_chestplate")),
        ),
        "armory": chest(
            pool((3, 6),
                 great("diamond_sword", 4), great("diamond_axe", 4), great("diamond_pickaxe", 3), great("diamond_helmet", 3),
                 great("diamond_chestplate", 3), great("diamond_leggings", 3), great("diamond_boots", 3), great("trident", 4),
                 great("crossbow", 3), great("bow", 3), great("mace", 1), great("netherite_sword", 1), great("netherite_chestplate", 1),
                 item("arrow", 6, (8, 24)), item("spectral_arrow", 4, (4, 12)), item("shield", 3), item("iron_ingot", 6, (4, 10))),
            pool(1, empty(8), ref("weapon")),
            pool(1, ref("great_book")),
            pool(1, empty(4), ref("trim")),
        ),
        "forge": chest(
            pool((4, 7),
                 item("netherite_scrap", 12, (2, 5)), item("netherite_ingot", 4, (1, 2)), item("ancient_debris", 8, (1, 4)),
                 item("iron_block", 4, (1, 3)), item("gold_ingot", 10, (4, 10)), item("diamond", 8, (2, 5)), item("coal_block", 6, (1, 4)),
                 item("netherite_upgrade_smithing_template", 4), item(f"{NS}:abyssal_upgrade_smithing_template", 3), item("lava_bucket", 3)),
            pool(1, ref("trim")),
            pool(1, empty(6), ref("weapon")),
        ),
        "mine": chest(
            pool((4, 8),
                 item("diamond", 12, (2, 6)), item("raw_gold", 10, (4, 12)), item("raw_iron", 10, (4, 12)), item("ancient_debris", 6, (1, 3)),
                 item("netherite_scrap", 4, (1, 3)), item("emerald", 6, (2, 6)), item("lapis_lazuli", 6, (6, 16)), item("redstone", 6, (6, 16)),
                 item("coal", 8, (8, 24)), item("tnt", 4, (2, 6)), great("diamond_pickaxe", 3), item("rail", 4, (8, 16)),
                 item("lantern", 3, (1, 3))),
            pool(1, empty(6), ref("great_book")),
        ),
        "library": chest(
            pool((4, 8),
                 item("book", 14, 1, DEEP_ENCHANT), item("book", 8, 1, ENCHANT), item("book", 10, (2, 5)), item("paper", 8, (3, 10)),
                 item("lapis_lazuli", 8, (4, 12)), item("experience_bottle", 8, (3, 10)), item("writable_book", 3), item("glow_ink_sac", 4, (1, 4)),
                 item("name_tag", 2), item("map", 3), item("compass", 2)),
            pool((1, 2), ref("great_book")),
            pool(1, empty(12), item(f"{NS}:abyssal_enchanting_table")),
        ),
        "discs": chest(
            pool((2, 4), ref("disc")),
            pool((1, 3), item("diamond", 3, (1, 3)), item("emerald", 4, (2, 5)), item("gold_ingot", 4, (2, 6)), item("note_block", 2),
                 item("jukebox", 1)),
        ),
        "heads": chest(
            pool((2, 4), ref("head")),
            pool((1, 3), item("diamond", 3, (1, 3)), item("bone", 6, (3, 8)), item("gold_ingot", 4, (2, 6)), item("netherite_scrap", 2)),
        ),
        "ship": chest(
            pool((4, 8),
                 item("gold_ingot", 10, (2, 7)), item("emerald", 8, (2, 6)), item("diamond", 8, (1, 3)), item("paper", 8, (2, 8)), item("map", 4),
                 item("compass", 4), item("clock", 3), item("spyglass", 4), item("potion", 6, None, WATER_BREATHING),
                 item("firework_rocket", 8, (4, 12)), item("ender_pearl", 4, (1, 3)), item("chorus_fruit", 4, (2, 6)),
                 item("shulker_shell", 3, (1, 2)), item("golden_apple", 4), item("book", 4, 1, SEA_ENCHANT)),
            pool(1, empty(6), item("elytra")),
            pool(1, empty(4), ref("disc")),
        ),
        "tomb": chest(
            pool((4, 7),
                 item("gold_ingot", 12, (3, 8)), item("emerald", 8, (2, 6)), item("diamond", 8, (1, 4)), item("bone", 10, (3, 9)),
                 item("rotten_flesh", 6, (2, 6)), item("totem_of_undying", 3), item("golden_apple", 6), item("enchanted_golden_apple", 1),
                 item("book", 6, 1, DEEP_ENCHANT), item("gold_block", 3), item("netherite_scrap", 4, (1, 2)), item("experience_bottle", 4, (2, 6))),
            pool(1, empty(2), ref("head")),
            pool(1, empty(10), ref("weapon")),
        ),
        "lab": chest(
            pool((4, 7),
                 potion("long_water_breathing", 6), potion("strong_healing", 4), potion("long_night_vision", 5), potion("strong_strength", 4),
                 potion("long_swiftness", 3), potion("strong_regeneration", 4), potion("long_fire_resistance", 3),
                 potion("strong_turtle_master", 2), potion("strong_healing", 3, "splash_potion"), item("glass_bottle", 4, (2, 6)),
                 item("blaze_powder", 4, (1, 4)), item("nether_wart", 4, (2, 6)), item("glowstone_dust", 4, (2, 8)), item("ghast_tear", 2),
                 item("golden_apple", 4), item("experience_bottle", 6, (4, 10)), item("dragon_breath", 2, (1, 3))),
            pool(1, empty(4), ref("great_book")),
        ),
    }


# The ruins up on the seamounts: worth the look, but the fortunes lie at the bottom.
HIGHLAND = [f"{NS}:abyssal_plains", f"{NS}:bone_fields", f"{NS}:glowing_reef", f"{NS}:hydrothermal_vents", f"{NS}:midnight_zone",
            f"{NS}:twilight_waters"]


def highland_tables():
    def base(*extra):
        return chest(
            pool((3, 6),
                 item("gold_ingot", 12, (2, 5)), item("iron_ingot", 12, (2, 6)), item("emerald", 8, (1, 4)), item("diamond", 3, (1, 2)),
                 item(f"{NS}:pearl", 8, (1, 3)), item(f"{NS}:sea_scale", 10, (2, 5)), item("prismarine_crystals", 8, (2, 6)),
                 item("lapis_lazuli", 6, (3, 8)), item("nautilus_shell", 4), item("experience_bottle", 6, (1, 4)),
                 item("book", 4, 1, SEA_ENCHANT), item("potion", 5, None, WATER_BREATHING), item("golden_apple", 2)),
            *extra)
    return {
        "ruin": base(pool(1, empty(10), ref("disc"), ref("head"), ref("trim"))),
        "treasury": base(pool((1, 3), item("gold_ingot", 10, (3, 8)), item("gold_block", 2), item("emerald", 6, (2, 6)))),
        "heads": base(pool(1, empty(2), ref("head"))),
        "mine": base(pool((1, 3), item("raw_iron", 8, (3, 8)), item("raw_gold", 6, (2, 6)), item("coal", 8, (6, 16)), item("diamond", 2))),
        "ship": base(pool((1, 2), item("map", 3), item("compass", 3), item("spyglass", 2), item("firework_rocket", 4, (2, 6)))),
        "tomb": base(pool((1, 2), item("bone", 8, (2, 6)), item("gold_ingot", 6, (2, 5)), item("totem_of_undying", 1))),
        "discs": base(pool(1, empty(1), ref("disc"))),
        "library": base(pool((1, 3), item("book", 8, 1, ENCHANT), item("paper", 6, (3, 9)), item("book", 6, (1, 3)))),
    }


def finds():
    """Little finds all over the Abyss: amphorae and cargo on the floor, lost cargo drifting above it."""
    return {
        "amphora": chest(
            pool((1, 3),
                 item("gold_nugget", 12, (3, 9)), item("emerald", 6, (1, 3)), item("gold_ingot", 6, (1, 2)), item(f"{NS}:pearl", 6, (1, 2)),
                 item("prismarine_crystals", 6, (1, 4)), item("lapis_lazuli", 6, (2, 6)), item("diamond", 1), item("glow_ink_sac", 4, (1, 3)),
                 item("bone_meal", 4, (2, 6)))),
        "cargo": chest(
            pool((3, 6),
                 item("iron_ingot", 10, (2, 6)), item("gold_ingot", 6, (1, 4)), item("emerald", 6, (1, 4)), item("coal", 8, (4, 12)),
                 item("bread", 6, (2, 5)), item("paper", 6, (2, 8)), item("leather", 6, (2, 5)), item("lantern", 3, (1, 2)),
                 item("potion", 5, None, WATER_BREATHING), item(f"{NS}:sea_scale", 6, (2, 5)), item("diamond", 2),
                 item("book", 3, 1, SEA_ENCHANT)),
            pool(1, empty(8), item(f"{NS}:tidewalker_helmet"), item(f"{NS}:tidewalker_boots"))),
        "drift_cargo": chest(
            pool((3, 6),
                 item("gold_ingot", 10, (2, 6)), item("emerald", 8, (2, 5)), item("diamond", 4, (1, 2)), item("iron_ingot", 8, (3, 8)),
                 item(f"{NS}:abyssal_scale", 6, (1, 3)), item(f"{NS}:pearl", 6, (1, 3)), item("experience_bottle", 6, (2, 5)),
                 item("potion", 6, None, WATER_BREATHING), item("golden_apple", 3), item("book", 4, 1, DEEP_ENCHANT),
                 item("ender_pearl", 3, (1, 2))),
            pool(1, empty(6), ref("disc"), ref("trim"), item(f"{NS}:tidewalker_chestplate"), item(f"{NS}:tidewalker_leggings"))),
    }


def structures():
    files = {}
    for name in TIERS:
        files[f"{NS}/worldgen/structure/{name}.json"] = {
            "type": f"{NS}:{name}",
            "biomes": f"#{NS}:has_structure/abyss_treasure",
            "spawn_overrides": {},
            "step": "surface_structures",
        }
    # One set for all three, so they never land on each other; a site too rough for a wonder
    # gets a vault or a ruin instead.
    files[f"{NS}/worldgen/structure_set/abyss_treasures.json"] = {
        "placement": {"type": "minecraft:random_spread", "salt": 640219877, "separation": 2, "spacing": 6,
                      "exclusion_zone": {"other_set": f"{NS}:drowned_sanctum", "chunk_count": 4}},
        "structures": [{"structure": f"{NS}:{name}", "weight": weight} for name, weight in TIERS.items()],
    }
    files[f"{NS}/tags/worldgen/biome/has_structure/abyss_treasure.json"] = {"values": FLOOR}
    files[f"{NS}/tags/worldgen/structure/abyss_treasures.json"] = {"values": [f"{NS}:{name}" for name in TIERS]}
    files[f"{NS}/worldgen/structure/deep_highland.json"] = {
        "type": f"{NS}:deep_highland",
        "biomes": f"#{NS}:has_structure/abyss_highland",
        "spawn_overrides": {},
        "step": "surface_structures",
    }
    files[f"{NS}/worldgen/structure_set/abyss_highlands.json"] = {
        "placement": {"type": "minecraft:random_spread", "salt": 118204573, "separation": 2, "spacing": 5},
        "structures": [{"structure": f"{NS}:deep_highland", "weight": 1}],
    }
    files[f"{NS}/tags/worldgen/biome/has_structure/abyss_highland.json"] = {"values": HIGHLAND}
    return files


def build(vanilla):
    files = structures()
    for name, table in parts(vanilla).items():
        files[f"{NS}/loot_table/chests/abyss/parts/{name}.json"] = table
    for name, table in tables().items():
        files[f"{NS}/loot_table/chests/abyss/{name}.json"] = table
    for name, table in finds().items():
        files[f"{NS}/loot_table/chests/abyss/{name}.json"] = table
    for name, table in highland_tables().items():
        files[f"{NS}/loot_table/chests/abyss/highland/{name}.json"] = table
    return files
