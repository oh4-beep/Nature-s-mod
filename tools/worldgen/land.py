"""The structures of the Overworld's lands.

Fifty kinds in three tiers, built in Java (LandEasy, LandMedium, LandHard) and chosen by the climate
of the site they stand on:

  * land_easy:   camps, cabins, shrines, a wishing well, an oasis, a snow fort...
  * land_medium: a bandit hideout, a witch's tower, a sandstone tomb, a saloon, a monkey temple...
  * land_hard:   a warlord's fortress, a necromancer's crypt, a lost pyramid, a colosseum, a tower
                 of trials, a deep mine, a spire of blazes...

The easy and medium ones share one spread of sites, a good deal denser than villages; the great
dungeons have a sparser spread of their own. Both keep clear of villages and of each other.
"""

from abyss import DEEP_ENCHANT, ENCHANT, chest, empty, item, pool

NS = "naturesmod"
TIERS = {"land_easy": 2, "land_medium": 1}
HARD = "land_hard"

# The Overworld's dry lands: everything but the seas, rivers, shores and caves.
LAND_BIOMES = [
    "plains", "sunflower_plains", "snowy_plains", "ice_spikes", "desert", "swamp", "mangrove_swamp", "forest", "flower_forest",
    "birch_forest", "dark_forest", "pale_garden", "dappled_forest", "old_growth_birch_forest", "old_growth_pine_taiga",
    "old_growth_spruce_taiga", "taiga", "snowy_taiga", "savanna", "savanna_plateau", "windswept_hills", "windswept_gravelly_hills",
    "windswept_forest", "windswept_savanna", "jungle", "sparse_jungle", "bamboo_jungle", "badlands", "eroded_badlands",
    "wooded_badlands", "meadow", "cherry_grove", "grove", "snowy_slopes", "frozen_peaks", "jagged_peaks", "stony_peaks",
    "mushroom_fields",
]


def potion(name):
    return {"type": "minecraft:set_potion", "id": f"minecraft:{name}"}


def land_loot():
    food = [item("bread", 8, (1, 4)), item("apple", 6, (1, 3)), item("cooked_beef", 5, (1, 3)), item("baked_potato", 5, (1, 4))]
    basic = [item("iron_ingot", 6, (1, 3)), item("gold_nugget", 5, (2, 8)), item("coal", 6, (2, 6)), item("torch", 6, (2, 8)),
             item("string", 5, (1, 4)), item("arrow", 5, (2, 8))]
    rich = [item("diamond", 8, (1, 3)), item("emerald", 8, (2, 6)), item("gold_ingot", 8, (2, 6)), item("iron_ingot", 8, (3, 8)),
            item("golden_apple", 4), item("experience_bottle", 6, (2, 6)), item("book", 6, 1, DEEP_ENCHANT), item("name_tag", 2)]
    horse = [item("saddle", 4), item("iron_horse_armor", 3), item("golden_horse_armor", 2), item("diamond_horse_armor", 1)]
    rare = lambda *extra: pool(1, empty(8), item("enchanted_golden_apple"), item("totem_of_undying"), *extra)  # noqa: E731
    return {
        # Easy finds.
        "meteor": chest(pool((3, 6), item("iron_ingot", 8, (2, 6)), item("raw_iron", 6, (2, 6)), item("magma_cream", 4, (1, 3)),
                             item("obsidian", 5, (1, 4)), item("amethyst_shard", 5, (2, 6)), item("diamond", 2, (1, 2)),
                             item("netherite_scrap", 1), item("fire_charge", 4, (1, 3)))),
        "dragon": chest(pool((6, 10), *rich, item("gold_block", 8, (1, 4)), item("diamond_block", 2), item("netherite_ingot", 1),
                             item("golden_horse_armor", 3), item("diamond_sword", 3, 1, DEEP_ENCHANT), item("diamond_chestplate", 2, 1, DEEP_ENCHANT)),
                        rare(item("dragon_breath", 2))),
        "camp": chest(pool((3, 6), *food, *basic, item("leather", 5, (1, 3)), item("flint_and_steel", 2), item("saddle", 1))),
        "cabin": chest(pool((3, 6), *food, item("cooked_rabbit", 5, (1, 3)), item("rabbit_hide", 5, (1, 4)), item("leather", 6, (2, 5)),
                            item("bow", 3, 1, ENCHANT), item("iron_axe", 3, 1, ENCHANT), item("leather_chestplate", 2, 1, ENCHANT),
                            item("arrow", 6, (4, 12)), item("spruce_sapling", 4, (1, 3)), item("lead", 2))),
        "wishing": chest(pool((3, 7), item("gold_nugget", 10, (3, 9)), item("iron_nugget", 8, (3, 9)), item("emerald", 5, (1, 3)),
                              item("gold_ingot", 3, (1, 2)), item("lapis_lazuli", 4, (2, 6)), item("amethyst_shard", 4, (1, 4)),
                              item("diamond", 1))),
        "common": chest(pool((3, 6), *food, *basic, item("bone", 5, (1, 4)), item("rotten_flesh", 5, (1, 4)), item("saddle", 1),
                             item("name_tag", 1), item("book", 2, 1, ENCHANT))),
        "farm": chest(pool((4, 8), item("wheat", 10, (3, 8)), item("wheat_seeds", 6, (2, 8)), item("carrot", 6, (2, 6)), item("potato", 6, (2, 6)),
                           item("beetroot_seeds", 4, (2, 6)), item("pumpkin_seeds", 4, (1, 4)), item("melon_seeds", 4, (1, 4)), item("bread", 6, (2, 5)),
                           item("egg", 5, (1, 4)), item("bone_meal", 6, (2, 8)), item("hay_block", 3, (1, 2)), item("iron_hoe", 2, 1, ENCHANT),
                           item("saddle", 1), item("lead", 2))),
        "desert": chest(pool((3, 6), item("gold_ingot", 6, (1, 3)), item("emerald", 4, (1, 2)), item("bone", 8, (2, 6)), item("gunpowder", 6, (1, 4)),
                             item("rotten_flesh", 8, (2, 6)), item("cactus", 4, (1, 4)), item("saddle", 3), item("golden_apple", 1),
                             item("diamond", 1))),
        "jungle": chest(pool((3, 6), item("cocoa_beans", 8, (2, 6)), item("bamboo", 8, (3, 10)), item("melon_slice", 6, (2, 6)),
                             item("gold_ingot", 5, (1, 3)), item("emerald", 3, (1, 2)), item("arrow", 6, (2, 8)), item("bone", 6, (1, 4)),
                             item("jungle_sapling", 4, (1, 3)), item("saddle", 2), item("diamond", 1))),
        "explorer": chest(pool((3, 6), item("map", 4), item("compass", 5), item("spyglass", 3), item("torch", 8, (4, 12)), item("bread", 8, (2, 5)),
                               item("leather_boots", 3, 1, ENCHANT), item("iron_pickaxe", 2, 1, ENCHANT), item("iron_ingot", 5, (1, 4)),
                               item("emerald", 3, (1, 3)), item("book", 2, 1, ENCHANT), item("snowball", 4, (4, 12)))),
        "savanna": chest(pool((3, 6), item("saddle", 4), item("lead", 4, (1, 2)), item("leather", 8, (2, 6)), item("bone", 6, (1, 4)),
                              item("arrow", 6, (2, 8)), item("iron_ingot", 4, (1, 3)), item("gold_ingot", 3, (1, 2)), item("emerald", 2, (1, 2)),
                              item("acacia_sapling", 4, (1, 3)), item("bread", 6, (1, 4)))),
        "mine": chest(pool((3, 7), item("coal", 10, (3, 10)), item("raw_iron", 8, (2, 6)), item("raw_copper", 6, (3, 8)), item("iron_ingot", 6, (1, 4)),
                           item("gold_ingot", 4, (1, 3)), item("redstone", 6, (3, 9)), item("lapis_lazuli", 4, (2, 8)), item("rail", 8, (4, 12)),
                           item("torch", 8, (3, 10)), item("iron_pickaxe", 3, 1, ENCHANT), item("tnt", 2, (1, 2)), item("diamond", 1, (1, 2)))),
        "swamp": chest(pool((3, 6), item("slime_ball", 8, (1, 4)), item("glass_bottle", 6, (1, 3)), item("spider_eye", 6, (1, 3)),
                            item("sugar", 5, (1, 4)), item("redstone", 5, (2, 6)), item("glowstone_dust", 4, (1, 4)), item("lily_pad", 4, (1, 3)),
                            item("fermented_spider_eye", 3), item("emerald", 2, (1, 2)), item("potion", 2, 1, potion("swiftness")))),
        # Harder halls.
        "bandit": chest(pool((4, 8), item("emerald", 8, (2, 6)), item("gold_ingot", 8, (2, 5)), item("iron_ingot", 8, (2, 6)),
                             item("crossbow", 4, 1, ENCHANT), item("arrow", 8, (4, 16)), item("iron_sword", 3, 1, ENCHANT), item("leather", 6, (2, 5)),
                             item("saddle", 3), item("bread", 6, (2, 5)), item("book", 3, 1, ENCHANT), item("diamond", 2, (1, 2)), item("name_tag", 2)),
                        pool(1, empty(3), item("sentry_armor_trim_smithing_template"))),
        "witch": chest(pool((4, 8), item("glass_bottle", 6, (2, 5)), item("glowstone_dust", 6, (2, 6)), item("redstone", 6, (2, 6)),
                            item("sugar", 5, (2, 5)), item("spider_eye", 5, (1, 4)), item("fermented_spider_eye", 4, (1, 2)),
                            item("rabbit_foot", 3), item("nether_wart", 4, (1, 4)), item("blaze_powder", 3, (1, 2)), item("ender_pearl", 3, (1, 2)),
                            item("emerald", 4, (1, 4)), item("book", 4, 1, ENCHANT), item("experience_bottle", 4, (1, 3)),
                            item("potion", 3, 1, potion("healing")), item("potion", 3, 1, potion("strength")),
                            item("potion", 3, 1, potion("invisibility")), item("splash_potion", 2, 1, potion("harming")))),
        "crypt": chest(pool((4, 8), item("bone", 12, (2, 8)), item("rotten_flesh", 10, (2, 8)), item("gold_ingot", 5, (1, 3)),
                            item("iron_ingot", 5, (1, 3)), item("emerald", 3, (1, 2)), item("golden_apple", 2), item("book", 4, 1, ENCHANT),
                            item("iron_sword", 2, 1, ENCHANT), item("name_tag", 2), item("experience_bottle", 3, (1, 3)), item("diamond", 1)),
                       pool(1, empty(8), item("skeleton_skull"))),
        "market": chest(pool((4, 8), item("emerald", 10, (3, 8)), item("gold_ingot", 6, (2, 5)), item("bread", 6, (2, 5)),
                             item("cookie", 4, (2, 6)), item("sweet_berries", 5, (2, 8)), item("sugar", 4, (2, 6)), item("red_dye", 3, (2, 6)),
                             item("blue_dye", 3, (2, 6)), item("leather", 5, (2, 5)), item("paper", 5, (3, 8)), item("lead", 3), item("saddle", 2),
                             item("map", 2), item("firework_rocket", 3, (2, 6)), item("spyglass", 1), item("cake", 1))),
        "tomb": chest(pool((4, 8), item("gold_ingot", 10, (2, 6)), item("emerald", 6, (2, 5)), item("diamond", 4, (1, 3)), item("bone", 8, (2, 6)),
                           item("rotten_flesh", 6, (2, 6)), item("gunpowder", 6, (2, 6)), item("golden_apple", 3), item("book", 6, 1, DEEP_ENCHANT),
                           *horse),
                      pool(1, empty(4), item("gold_block"), item("dune_armor_trim_smithing_template")),
                      pool(1, empty(40), item("enchanted_golden_apple"))),
        "temple": chest(pool((4, 8), item("gold_ingot", 8, (2, 6)), item("emerald", 6, (2, 5)), item("diamond", 4, (1, 3)), item("bamboo", 6, (3, 9)),
                             item("arrow", 8, (4, 12)), item("iron_ingot", 6, (2, 5)), item("book", 6, 1, ENCHANT), item("golden_apple", 2),
                             item("cocoa_beans", 5, (2, 6)), item("saddle", 3), item("iron_horse_armor", 2)),
                        pool(1, empty(4), item("wild_armor_trim_smithing_template"))),
        "ranger": chest(pool((4, 8), item("arrow", 10, (8, 20)), item("crossbow", 4, 1, ENCHANT), item("bow", 4, 1, ENCHANT),
                             item("chainmail_helmet", 2, 1, ENCHANT), item("chainmail_chestplate", 2, 1, ENCHANT),
                             item("chainmail_leggings", 2, 1, ENCHANT), item("chainmail_boots", 2, 1, ENCHANT), item("bread", 8, (2, 5)),
                             item("cooked_beef", 6, (2, 5)), item("compass", 3), item("map", 3), item("spyglass", 3), item("shield", 3),
                             item("iron_ingot", 5, (2, 5)))),
        "observatory": chest(pool((4, 8), item("spyglass", 8), item("compass", 6), item("clock", 6), item("map", 6), item("paper", 6, (3, 8)),
                                  item("glass", 5, (2, 6)), item("amethyst_shard", 6, (2, 6)), item("lapis_lazuli", 5, (3, 8)),
                                  item("redstone", 4, (2, 6)), item("gold_ingot", 4, (1, 3)), item("book", 5, 1, ENCHANT),
                                  item("experience_bottle", 4, (1, 4)), item("firework_rocket", 5, (2, 6)))),
        "forge": chest(pool((4, 8), item("iron_ingot", 10, (4, 10)), item("gold_ingot", 8, (2, 6)), item("diamond", 4, (1, 3)),
                            item("coal", 8, (4, 12)), item("raw_iron", 6, (3, 8)), item("iron_sword", 3, 1, ENCHANT), item("iron_pickaxe", 3, 1, ENCHANT),
                            item("iron_chestplate", 2, 1, ENCHANT), item("shield", 3), item("lava_bucket", 2), item("iron_block", 2)),
                       pool(1, empty(6), item("netherite_scrap"), item("netherite_upgrade_smithing_template"))),
        "safari": chest(pool((4, 8), item("saddle", 10), item("lead", 8, (1, 3)), item("name_tag", 4), item("hay_block", 4, (1, 3)),
                             item("apple", 6, (2, 5)), item("golden_carrot", 4, (1, 4)), item("carrot", 6, (2, 6)), item("leather", 6, (2, 5)),
                             item("compass", 3), item("map", 3), item("spyglass", 3), item("bread", 6, (2, 5)), item("acacia_sapling", 3, (1, 3)),
                             *horse)),
        "den": chest(pool((4, 8), item("bone", 12, (3, 10)), item("leather", 10, (2, 6)), item("rotten_flesh", 8, (2, 6)), item("beef", 6, (1, 4)),
                          item("feather", 6, (2, 6)), item("rabbit_hide", 5, (1, 4)), item("iron_ingot", 4, (1, 3)), item("gold_ingot", 3, (1, 3)),
                          item("emerald", 2, (1, 2)), item("chainmail_helmet", 2, 1, ENCHANT), item("book", 2, 1, ENCHANT), item("diamond", 1))),
        "library": chest(pool((4, 8), item("book", 14, 1, ENCHANT), item("book", 6, 1, DEEP_ENCHANT), item("paper", 8, (3, 10)),
                              item("writable_book", 3), item("ink_sac", 4, (1, 4)), item("feather", 4, (1, 4)), item("lapis_lazuli", 6, (3, 10)),
                              item("experience_bottle", 6, (2, 6)), item("name_tag", 2), item("map", 3), item("compass", 2))),
        "saloon": chest(pool((4, 8), item("emerald", 8, (2, 6)), item("gold_ingot", 8, (2, 6)), item("gold_nugget", 6, (4, 12)),
                             item("iron_ingot", 6, (2, 5)), item("honey_bottle", 5, (1, 3)), item("bread", 6, (2, 5)), item("cooked_beef", 6, (2, 5)),
                             item("crossbow", 3, 1, ENCHANT), item("arrow", 6, (4, 12)), item("saddle", 4), item("lead", 3), item("name_tag", 2),
                             item("diamond", 2, (1, 2)), item("clock", 2))),
        # The great dungeons.
        "fortress": chest(pool((5, 9), *rich, item("crossbow", 4, 1, DEEP_ENCHANT), item("iron_sword", 3, 1, DEEP_ENCHANT),
                               item("iron_helmet", 3, 1, ENCHANT), item("iron_chestplate", 3, 1, ENCHANT), item("iron_leggings", 3, 1, ENCHANT),
                               item("iron_boots", 3, 1, ENCHANT), item("shield", 3), item("arrow", 6, (6, 16))),
                          pool(1, empty(3), item("diamond_sword", 1, 1, DEEP_ENCHANT), item("diamond_chestplate", 1, 1, DEEP_ENCHANT),
                               item("sentry_armor_trim_smithing_template")),
                          rare()),
        "treasure": chest(pool((6, 10), *rich, item("gold_block", 6, (1, 3)), item("emerald_block", 4, (1, 2)), item("diamond_block", 2),
                               item("netherite_scrap", 4, (1, 2)), item("golden_apple", 6, (1, 3)), item("diamond_horse_armor", 3)),
                          pool(1, item("enchanted_golden_apple", 2), item("totem_of_undying", 2), item("netherite_upgrade_smithing_template", 2),
                               item("heart_of_the_sea"), item("trident", 1, 1, ENCHANT), item("music_disc_pigstep"))),
        "pyramid": chest(pool((5, 9), *rich, item("gold_ingot", 10, (4, 10)), item("bone", 8, (2, 6)), item("gunpowder", 8, (2, 6)),
                              item("rotten_flesh", 6, (2, 6)), *horse),
                         pool(1, empty(2), item("gold_block", 3, (1, 2)), item("dune_armor_trim_smithing_template", 2)),
                         rare()),
        "ziggurat": chest(pool((5, 9), *rich, item("gold_ingot", 10, (3, 8)), item("arrow", 8, (4, 12)), item("bamboo", 6, (3, 9)),
                               item("cocoa_beans", 6, (2, 6)), item("iron_horse_armor", 3), item("golden_horse_armor", 2), item("saddle", 3)),
                          pool(1, empty(2), item("emerald_block"), item("wild_armor_trim_smithing_template", 2)),
                          rare()),
        "frost": chest(pool((5, 9), *rich, item("blue_ice", 4, (1, 4)), item("packed_ice", 4, (2, 6)), item("snowball", 5, (4, 16)),
                            item("powder_snow_bucket", 2), item("leather_boots", 3, 1, DEEP_ENCHANT), item("bow", 4, 1, DEEP_ENCHANT),
                            item("tipped_arrow", 4, (4, 10), potion("slowness"))),
                       pool(1, empty(3), item("sentry_armor_trim_smithing_template")),
                       rare()),
        "manor": chest(pool((5, 9), *rich, item("lead", 3), item("clock", 3), item("candle", 4, (1, 4)), item("music_disc_13", 2),
                            item("music_disc_cat", 2), item("music_disc_11", 1)),
                       pool(1, empty(3), item("vex_armor_trim_smithing_template")),
                       rare()),
        "colosseum": chest(pool((5, 9), *rich, item("iron_sword", 3, 1, DEEP_ENCHANT), item("shield", 4), item("iron_chestplate", 3, 1, ENCHANT),
                                *horse, item("lead", 3)),
                           pool(1, empty(3), item("diamond_sword", 1, 1, DEEP_ENCHANT), item("diamond_helmet", 1, 1, DEEP_ENCHANT)),
                           rare()),
        "tower": chest(pool((4, 8), item("iron_ingot", 8, (2, 6)), item("gold_ingot", 6, (2, 5)), item("emerald", 6, (2, 5)),
                            item("diamond", 4, (1, 2)), item("arrow", 6, (4, 12)), item("bone", 6, (2, 6)), item("string", 6, (2, 6)),
                            item("gunpowder", 6, (2, 6)), item("blaze_rod", 3, (1, 3)), item("ender_pearl", 4, (1, 2)),
                            item("book", 6, 1, ENCHANT), item("experience_bottle", 6, (2, 5)), item("golden_apple", 2))),
        "deep_mine": chest(pool((5, 9), item("diamond", 6, (1, 4)), item("iron_ingot", 10, (3, 8)), item("gold_ingot", 8, (2, 6)),
                                item("raw_gold", 6, (2, 5)), item("redstone", 8, (4, 12)), item("lapis_lazuli", 6, (4, 12)), item("emerald", 4, (1, 3)),
                                item("coal", 8, (5, 15)), item("rail", 6, (6, 16)), item("powered_rail", 4, (2, 6)), item("tnt", 4, (2, 5)),
                                item("amethyst_shard", 4, (2, 6)), item("iron_pickaxe", 3, 1, DEEP_ENCHANT)),
                           pool(1, empty(4), item("diamond_pickaxe", 1, 1, DEEP_ENCHANT), item("diamond_block"))),
        "treehouse": chest(pool((4, 8), item("apple", 8, (2, 5)), item("golden_apple", 3), item("stick", 6, (3, 8)), item("string", 6, (2, 6)),
                                item("feather", 6, (2, 6)), item("arrow", 8, (4, 12)), item("bow", 4, 1, ENCHANT), item("leather", 6, (2, 5)),
                                item("name_tag", 2), item("lead", 3), item("emerald", 4, (1, 4)), item("gold_ingot", 4, (1, 3)),
                                item("book", 4, 1, ENCHANT), item("oak_sapling", 4, (1, 3)), item("cocoa_beans", 4, (1, 4)), item("spyglass", 2)),
                           pool(1, empty(4), item("diamond", 1, (1, 2)))),
        "blaze": chest(pool((5, 9), item("blaze_rod", 10, (2, 6)), item("blaze_powder", 8, (2, 6)), item("magma_cream", 6, (1, 4)),
                            item("nether_wart", 6, (2, 6)), item("gold_ingot", 8, (2, 6)), item("quartz", 8, (4, 12)), item("obsidian", 4, (1, 4)),
                            item("fire_charge", 6, (2, 5)), item("gunpowder", 6, (2, 6)), item("diamond", 4, (1, 3)), item("golden_apple", 3),
                            item("book", 5, 1, DEEP_ENCHANT)),
                       pool(1, empty(3), item("netherite_scrap"), item("rib_armor_trim_smithing_template"))),
    }


def structures():
    files = {}
    for name in [*TIERS, HARD]:
        files[f"{NS}/worldgen/structure/{name}.json"] = {
            "type": f"{NS}:{name}", "biomes": f"#{NS}:has_structure/land_structure", "spawn_overrides": {}, "step": "surface_structures"}
    files[f"{NS}/worldgen/structure_set/land_structures.json"] = {
        "placement": {"type": "minecraft:random_spread", "salt": 1983621409, "separation": 3, "spacing": 10,
                      "exclusion_zone": {"other_set": "minecraft:villages", "chunk_count": 3}},
        "structures": [{"structure": f"{NS}:{name}", "weight": weight} for name, weight in TIERS.items()],
    }
    files[f"{NS}/worldgen/structure_set/land_dungeons.json"] = {
        "placement": {"type": "minecraft:random_spread", "salt": 1983621413, "separation": 7, "spacing": 22,
                      "exclusion_zone": {"other_set": f"{NS}:land_structures", "chunk_count": 3}},
        "structures": [{"structure": f"{NS}:{HARD}", "weight": 1}],
    }
    files[f"{NS}/tags/worldgen/biome/has_structure/land_structure.json"] = {"values": [f"minecraft:{b}" for b in LAND_BIOMES]}
    files[f"{NS}/tags/worldgen/structure/land_structures.json"] = {"values": [f"{NS}:{name}" for name in [*TIERS, HARD]]}
    for name, table in land_loot().items():
        files[f"{NS}/loot_table/chests/land/{name}.json"] = table
    return files


def build():
    return structures()
