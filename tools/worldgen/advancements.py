"""Advancements that lead players through the sea, the Abyss and the new Nether."""

NS = "naturesmod"


def display(name, icon, frame=None, hidden=False, background=None, toast=True):
    d = {
        "description": {"translate": f"advancements.{NS}.{name}.description"},
        "icon": {"id": icon},
        "title": {"translate": f"advancements.{NS}.{name}.title"},
    }
    if frame:
        d["frame"] = frame
    if hidden:
        d["hidden"] = True
    if background:
        d["background"] = background
        d["show_toast"] = toast
        d["announce_to_chat"] = False
    return d


def advancement(parent, criteria, disp, rewards=None, any_of=True):
    adv = {"criteria": criteria, "display": disp,
           "requirements": [list(criteria)] if any_of else [[name] for name in criteria],
           "sends_telemetry_event": False}
    if parent:
        adv["parent"] = parent
    if rewards:
        adv["rewards"] = rewards
    return adv


def has(*items):
    return {f"has_{item.split(':')[1]}": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}}
            for item in items}


def player_where(predicate):
    return {"trigger": "minecraft:location",
            "conditions": {"player": {"type": "minecraft:entity_properties", "entity": "this", "predicate": predicate}}}


def suit(name):
    return {"wearing": player_where({"minecraft:equipment": {
        "head": {"items": f"{NS}:{name}_helmet"}, "chest": {"items": f"{NS}:{name}_chestplate"},
        "legs": {"items": f"{NS}:{name}_leggings"}, "feet": {"items": f"{NS}:{name}_boots"}}})}


def build(buckets):
    sea = f"{NS}:sea"
    out = {
        "sea/root": advancement(None, has(f"{NS}:raw_fish_fillet", f"{NS}:sea_scale"),
                                display("sea.root", f"{NS}:sea_scale", background="minecraft:block/dark_prismarine", toast=False)),
        "sea/pet_fish": advancement(f"{sea}/root", {"bucket": {"trigger": "minecraft:filled_bucket",
                                                                "conditions": {"item": {"items": buckets}}}},
                                    display("sea.pet_fish", f"{NS}:clownfish_bucket")),
        "sea/jaws": advancement(f"{sea}/root", has(f"{NS}:shark_tooth"), display("sea.jaws", f"{NS}:shark_tooth")),
        "sea/tidewalker": advancement(f"{sea}/root", suit("tidewalker"), display("sea.tidewalker", f"{NS}:tidewalker_helmet")),
        "sea/into_the_abyss": advancement(f"{sea}/tidewalker", {"entered": {"trigger": "minecraft:changed_dimension",
                                                                             "conditions": {"to": f"{NS}:abyss"}}},
                                          display("sea.into_the_abyss", "minecraft:heart_of_the_sea")),
        "sea/crushing_depths": advancement(f"{sea}/into_the_abyss", {"deep": player_where({"minecraft:location": {
            "dimension": f"{NS}:abyss", "position": {"y": {"max": 350.0}}}})}, display("sea.crushing_depths", "minecraft:sculk", frame="goal")),
        "sea/sunken_temple": advancement(f"{sea}/into_the_abyss", {"temple": player_where({"minecraft:location": {
            "structures": f"{NS}:sunken_temple"}})}, display("sea.sunken_temple", "minecraft:dark_prismarine")),
        "sea/pressure_proof": advancement(f"{sea}/crushing_depths", suit("abyssal"),
                                          display("sea.pressure_proof", f"{NS}:abyssal_helmet", frame="goal")),
        "sea/slayer_of_the_deep": advancement(f"{sea}/crushing_depths", {"leviathan": {
            "trigger": "minecraft:player_killed_entity", "conditions": {"entity": {
                "type": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": f"{NS}:abyssal_leviathan"}}}}},
            display("sea.slayer_of_the_deep", f"{NS}:abyssal_heart", frame="challenge"), rewards={"experience": 500}),
        # The Drowned Expedition: the mission of the Abyss.
        "sea/sigil_seeker": advancement(f"{sea}/into_the_abyss",
                                        has(f"{NS}:tide_sigil", f"{NS}:deep_sigil", f"{NS}:bone_sigil", f"{NS}:ember_sigil"),
                                        display("sea.sigil_seeker", f"{NS}:tide_sigil")),
        "sea/four_seals": advancement(f"{sea}/sigil_seeker",
                                      has(f"{NS}:tide_sigil", f"{NS}:deep_sigil", f"{NS}:bone_sigil", f"{NS}:ember_sigil"),
                                      display("sea.four_seals", f"{NS}:ember_sigil", frame="goal"), any_of=False),
        "sea/awaken_the_abyss": advancement(f"{sea}/four_seals", {"awakened": {"trigger": "minecraft:impossible"}},
                                            display("sea.awaken_the_abyss", f"{NS}:abyssal_altar", frame="goal")),
        "sea/the_horror_below": advancement(f"{sea}/awaken_the_abyss", {"horror": {
            "trigger": "minecraft:player_killed_entity", "conditions": {"entity": {
                "type": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": f"{NS}:abyssal_horror"}}}}},
            display("sea.the_horror_below", f"{NS}:crown_of_the_deep", frame="challenge"), rewards={"experience": 1000}),
        "sea/crowned": advancement(f"{sea}/the_horror_below", {"wearing": player_where({"minecraft:equipment": {
            "head": {"items": f"{NS}:crown_of_the_deep"}}})}, display("sea.crowned", f"{NS}:crown_of_the_deep", frame="challenge")),
        "nether/above_the_lava_sea": advancement("minecraft:nether/root", {"high": player_where({"minecraft:location": {
            "dimension": "minecraft:the_nether", "position": {"y": {"min": 150.0}}}})}, display("nether.above_the_lava_sea", "minecraft:netherrack")),
        "nether/ember_wastes": advancement("minecraft:nether/root", {"there": player_where({"minecraft:location": {
            "biomes": f"{NS}:ember_wastes"}})}, display("nether.ember_wastes", f"{NS}:nether_geyser")),
        "nether/glowstone_grotto": advancement(f"{NS}:nether/above_the_lava_sea", {"there": player_where({"minecraft:location": {
            "biomes": f"{NS}:glowstone_grotto"}})}, display("nether.glowstone_grotto", "minecraft:glowstone")),
        "nether/obsidian_spires": advancement(f"{NS}:nether/above_the_lava_sea", {"there": player_where({"minecraft:location": {
            "biomes": f"{NS}:obsidian_spires"}})}, display("nether.obsidian_spires", "minecraft:crying_obsidian")),
        "nether/brimstone_fields": advancement("minecraft:nether/root", {"there": player_where({"minecraft:location": {
            "biomes": f"{NS}:brimstone_fields"}})}, display("nether.brimstone_fields", f"{NS}:brimstone")),
        "nether/flesh_caverns": advancement("minecraft:nether/root", {"there": player_where({"minecraft:location": {
            "biomes": f"{NS}:flesh_caverns"}})}, display("nether.flesh_caverns", f"{NS}:flesh_block")),
        "nether/bone_pit": advancement("minecraft:nether/root", {"there": player_where({"minecraft:location": {
            "structures": f"{NS}:bone_pit"}})}, display("nether.bone_pit", "minecraft:skeleton_skull")),
        "nether/ritual_circle": advancement("minecraft:nether/root", {"there": player_where({"minecraft:location": {
            "structures": f"{NS}:ritual_circle"}})}, display("nether.ritual_circle", f"{NS}:soul_brazier")),
        "nether/infernal_citadel": advancement("minecraft:nether/root", {"there": player_where({"minecraft:location": {
            "structures": f"{NS}:infernal_citadel"}})}, display("nether.infernal_citadel", f"{NS}:hellfire_brazier", frame="goal")),
    }
    return {f"{NS}/advancement/{name}.json": adv for name, adv in out.items()}


LANG = {
    "sea.root": ("Something Fishy", "Catch one of the new creatures of the sea"),
    "sea.pet_fish": ("Pet Fish", "Scoop up a fish in a bucket"),
    "sea.jaws": ("Jaws", "Get a shark tooth"),
    "sea.tidewalker": ("Tidewalker", "Wear a full Tidewalker diving suit"),
    "sea.into_the_abyss": ("Into the Abyss", "Swim through an Abyssal Rift on the floor of a deep ocean"),
    "sea.crushing_depths": ("Crushing Depths", "Dive below y=350 in the Abyss"),
    "sea.sunken_temple": ("Lost Temple", "Find a Sunken Temple on the floor of the Abyss"),
    "sea.pressure_proof": ("Pressure Proof", "Wear a full Abyssal diving suit"),
    "sea.slayer_of_the_deep": ("Slayer of the Deep", "Defeat the Abyssal Leviathan"),
    "sea.sigil_seeker": ("The Drowned Expedition", "Find one of the four sigils that seal the Abyss"),
    "sea.four_seals": ("The Four Seals", "Hold the sigils of the Tide, the Deep, the Bone and the Ember"),
    "sea.awaken_the_abyss": ("Awaken the Abyss", "Set all four sigils into the altar of a Drowned Sanctum"),
    "sea.the_horror_below": ("The Horror Below", "Defeat the Abyssal Horror"),
    "sea.crowned": ("Ruler of the Deep", "Wear the Crown of the Deep"),
    "nether.above_the_lava_sea": ("Above the Lava Sea", "Climb into the great caverns of the upper Nether"),
    "nether.ember_wastes": ("Ash and Embers", "Cross the Ember Wastes"),
    "nether.glowstone_grotto": ("Chandeliers of Light", "Find the Glowstone Grotto"),
    "nether.obsidian_spires": ("Weeping Spires", "Walk among the Obsidian Spires"),
    "nether.brimstone_fields": ("Fire and Brimstone", "Cross the smouldering Brimstone Fields"),
    "nether.flesh_caverns": ("The Belly of the Beast", "Enter the Flesh Caverns"),
    "nether.bone_pit": ("Down Among the Dead", "Find a Bone Pit"),
    "nether.ritual_circle": ("The Ritual", "Stand in a Ritual Circle"),
    "nether.infernal_citadel": ("Storm the Citadel", "Enter an Infernal Citadel"),
}


def lang():
    out = {}
    for key, (title, description) in LANG.items():
        out[f"advancements.{NS}.{key}.title"] = title
        out[f"advancements.{NS}.{key}.description"] = description
    return out
