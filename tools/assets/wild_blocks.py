"""Textures, models, block states, loot, recipes, tags and names of the wild Overworld's blocks and
items: lavender, heather, cattails, glowcaps, blueberry bushes, coconuts, salt, azure and citrine
crystal, petrified wood and steam vents, and the names of its thirteen biomes.

Painted procedurally on 16x16 canvases in vanilla's pixel style, like the Nether's and the End's.
"""

import json
import math
import os
import random

from hell import _json, canvas, fractal, mix, put, ramp, shade, tile_noise

# Block id -> (display name, model kind).
BLOCKS = {
    "lavender": ("Lavender", "cross"),
    "heather": ("Heather", "cross"),
    "cattail": ("Cattail", "double"),
    "glowcap": ("Glowcap", "cross"),
    "glowcap_block": ("Glowcap Block", "cube"),
    "blueberry_bush": ("Blueberry Bush", "bush"),
    "coconut": ("Coconut", "coconut"),
    "salt_block": ("Block of Salt", "cube"),
    "salt_crystal": ("Salt Crystal", "cluster"),
    "azure_crystal": ("Block of Azure Crystal", "cube"),
    "azure_crystal_cluster": ("Azure Crystal Cluster", "cluster"),
    "citrine_crystal": ("Block of Citrine", "cube"),
    "citrine_crystal_cluster": ("Citrine Cluster", "cluster"),
    "petrified_log": ("Petrified Log", "log"),
    "steam_vent": ("Steam Vent", "vent"),
}

ITEMS = {"blueberries": "Blueberries"}

BIOMES = {
    "lavender_fields": "Lavender Fields",
    "golden_prairie": "Golden Prairie",
    "maple_woods": "Maple Woods",
    "glowshroom_forest": "Glowshroom Forest",
    "tropical_shore": "Tropical Shore",
    "salt_flats": "Salt Flats",
    "volcanic_peaks": "Volcanic Peaks",
    "bayou": "Bayou",
    "tundra": "Tundra",
    "petrified_forest": "Petrified Forest",
    "heather_moors": "Heather Moors",
    "crystal_caverns": "Crystal Caverns",
    "fungal_grotto": "Fungal Grotto",
}

STEM = [(0.0, (46, 92, 38)), (1.0, (92, 148, 64))]


# --- plants ----------------------------------------------------------------------------------

def stalk(img, x, top, color_low, color_high, lean=0.0):
    for y in range(15, top - 1, -1):
        t = (15 - y) / max(1, 15 - top)
        put(img, round(x + lean * (15 - y)), y, mix(color_low, color_high, t))


def lavender():
    """Slender stems topped with long spikes of tiny purple flowers."""
    rng = random.Random(3001)
    img = canvas()
    for x, top, lean in ((3, 3, 0.05), (6, 1, -0.03), (9, 2, 0.04), (12, 4, -0.06), (8, 6, 0.0)):
        stalk(img, x, top + 5, (52, 96, 44), (88, 132, 70), lean)
        for y in range(top, top + 7):
            cx = round(x + lean * (15 - y))
            c = (150, 108, 210) if (y + x) % 2 else (122, 82, 186)
            if y == top:
                c = (186, 150, 236)
            put(img, cx, y, c)
            if rng.random() < 0.55:
                put(img, cx + rng.choice((-1, 1)), y, shade(c, 0.85))
    for x in (2, 5, 11, 13):
        for y in range(12, 16):
            put(img, x, y, (70, 118, 62))
    return img


def heather():
    """A low cushion of needle leaves dusted with pink and magenta bells."""
    rng = random.Random(3101)
    img = canvas()
    for _ in range(70):
        x = rng.randrange(1, 15)
        y = rng.randrange(6, 16)
        if (x - 7.5) ** 2 / 49 + (y - 12) ** 2 / 25 > 1:
            continue
        put(img, x, y, ramp([(0.0, (40, 70, 36)), (1.0, (84, 120, 60))], rng.random()))
    for _ in range(26):
        x = rng.randrange(2, 14)
        y = rng.randrange(5, 13)
        if (x - 7.5) ** 2 / 36 + (y - 10) ** 2 / 16 > 1:
            continue
        put(img, x, y, rng.choice([(206, 92, 170), (178, 70, 150), (230, 140, 200)]))
    return img


def cattail_bottom():
    img = canvas()
    for x, lean in ((4, 0.08), (7, -0.04), (9, 0.05), (12, -0.07)):
        stalk(img, x, 0, (60, 104, 44), (104, 150, 70), lean)
    # Long blades of leaf crossing the stalks.
    for x0, dx in ((2, 0.25), (13, -0.3), (6, 0.15)):
        for y in range(15, 3, -1):
            put(img, round(x0 + dx * (15 - y)), y, (76, 128, 58) if y % 3 else (98, 150, 72))
    return img


def cattail_top():
    img = canvas()
    for x, top, lean in ((4, 2, 0.08), (9, 0, 0.05), (12, 5, -0.07)):
        # The velvet brown head on its stalk, a thin spike above it.
        for y in range(15, top + 7, -1):
            put(img, round(x + lean * (15 - y)), y, (92, 142, 66))
        for y in range(top + 2, top + 8):
            cx = round(x + lean * (15 - y))
            put(img, cx, y, (110, 64, 36) if y % 2 else (128, 78, 44))
            put(img, cx + 1, y, (92, 52, 30))
        put(img, round(x + lean * (15 - top)), top, (150, 140, 90))
        put(img, round(x + lean * (15 - top - 1)), top + 1, (150, 140, 90))
    for x0, dx in ((2, 0.25), (13, -0.3)):
        for y in range(15, 8, -1):
            put(img, round(x0 + dx * (15 - y)), y, (84, 136, 62))
    return img


def glowcap():
    """A little clutch of glowing blue mushrooms."""
    img = canvas()
    for cx, top, r in ((5, 6, 3), (11, 9, 2), (8, 11, 2)):
        for y in range(top + 2, 16):
            put(img, cx, y, (206, 214, 222))
        for dx in range(-r, r + 1):
            for dy in range(0, 3):
                if abs(dx) <= r - dy:
                    c = (90, 210, 255) if dy == 0 else (60, 170, 240)
                    if abs(dx) == r - dy:
                        c = (40, 120, 200)
                    put(img, cx + dx, top + 2 - dy, c)
        put(img, cx - 1, top, (200, 250, 255))
    return img


def glowcap_block():
    tone = fractal(3301)
    rng = random.Random(3302)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, ramp([(0.0, (30, 96, 168)), (0.5, (52, 140, 214)), (1.0, (84, 184, 240))], tone[y][x]))
    for _ in range(7):
        cx, cy = rng.randrange(16), rng.randrange(16)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            put(img, (cx + dx) % 16, (cy + dy) % 16, (190, 250, 255) if (dx, dy) == (0, 0) else (140, 230, 255))
    return img


def berry_bush(stage):
    rng = random.Random(3401 + stage)
    img = canvas()
    radius = [3.5, 5.5, 6.8, 7.2][stage]
    cy = 16 - radius
    for _ in range(int(radius * radius * 6)):
        x = rng.randrange(16)
        y = rng.randrange(16)
        if (x - 7.5) ** 2 + (y - cy) ** 2 * 1.4 > radius * radius:
            continue
        put(img, x, y, ramp([(0.0, (38, 82, 44)), (1.0, (82, 132, 74))], rng.random()))
    if stage >= 2:
        colors = [(110, 120, 170), (150, 110, 160)] if stage == 2 else [(52, 74, 168), (70, 96, 200), (98, 128, 226)]
        for _ in range(9 if stage == 2 else 13):
            x = rng.randrange(2, 14)
            y = rng.randrange(int(cy - radius * 0.6), 15)
            if (x - 7.5) ** 2 + (y - cy) ** 2 * 1.4 > (radius - 1) ** 2:
                continue
            c = rng.choice(colors)
            put(img, x, y, c)
            put(img, x + 1, y, shade(c, 0.8))
            if stage == 3:
                put(img, x, y - 1, (170, 190, 240))
    for y in range(13, 16):
        put(img, 7, y, (84, 60, 38))
        put(img, 8, y, (70, 50, 32))
    return img


def coconut():
    """Coarse brown husk with fibres."""
    rng = random.Random(3501)
    img = canvas()
    for y in range(16):
        for x in range(16):
            n = rng.random()
            c = ramp([(0.0, (74, 46, 24)), (0.6, (110, 72, 38)), (1.0, (140, 98, 54))], n * 0.7 + 0.3 * ((x + y * 3) % 5) / 4)
            put(img, x, y, c)
    for _ in range(18):
        x, y = rng.randrange(16), rng.randrange(15)
        put(img, x, y, (156, 118, 70))
        put(img, x, y + 1, (130, 96, 56))
    return img


def coconut_item():
    img = canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 6.2, (y - 8.5) / 6.0)
            if d <= 1.0:
                c = ramp([(0.0, (138, 96, 54)), (0.7, (104, 66, 34)), (1.0, (68, 42, 22))], d)
                if (x * 3 + y) % 7 == 0:
                    c = shade(c, 1.25)
                put(img, x, y, c)
    for dx, dy in ((-2, -2), (0, -3), (2, -2)):
        put(img, 7 + dx, 7 + dy, (40, 26, 14))
    return img


def blueberries_item():
    img = canvas()
    for cx, cy in ((5, 9), (10, 8), (8, 12), (11, 12), (6, 5)):
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                d = dx * dx + dy * dy
                if d <= 4:
                    put(img, cx + dx, cy + dy, (40, 60, 150) if d > 2 else (70, 98, 200))
        put(img, cx - 1, cy - 1, (170, 190, 245))
        put(img, cx, cy - 2, (30, 40, 90))
    for y in range(1, 5):
        put(img, 8, y, (70, 120, 50))
    put(img, 9, 2, (90, 150, 60))
    return img


# --- stone and crystal -----------------------------------------------------------------------

def salt_block():
    tone = fractal(3601)
    rng = random.Random(3602)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, ramp([(0.0, (214, 212, 208)), (0.6, (236, 234, 230)), (1.0, (250, 250, 248))], tone[y][x]))
    for _ in range(10):
        put(img, rng.randrange(16), rng.randrange(16), (226, 200, 206))
    # Hairline cracks of the crust.
    for x in range(16):
        y = int(7 + 3 * math.sin(x * 0.6))
        put(img, x, y, (196, 192, 186))
    for y in range(16):
        x = int(4 + 2 * math.sin(y * 0.8))
        put(img, x, y, (200, 196, 190))
    return img


def crystal_block(palette, seed):
    img = canvas()
    rng = random.Random(seed)
    for y in range(16):
        for x in range(16):
            band = ((x + y) // 4) % 3
            d = ((x - y) % 8) / 8.0
            put(img, x, y, shade(palette[band], 0.75 + 0.45 * d))
    for _ in range(6):
        put(img, rng.randrange(16), rng.randrange(16), (255, 255, 250))
    return img


def cluster(dark, light, edge, tip):
    img = canvas()
    for cx, height, half, lean in ((8, 15, 2.6, 0.0), (4, 10, 1.9, -0.25), (12, 11, 1.9, 0.28), (6, 6, 1.4, -0.1), (10, 7, 1.3, 0.15)):
        for y in range(height):
            t = y / height
            w = half * (1 - t ** 1.6)
            mid = cx + lean * y
            for x in range(16):
                dxx = x - mid
                if abs(dxx) <= w:
                    c = edge if abs(dxx) > w - 0.9 else dark if dxx < 0 else light
                    put(img, x, 15 - y, c)
        put(img, round(cx + lean * (height - 1)), 16 - height, tip)
    return img


def petrified_log():
    rng = random.Random(3701)
    side = canvas()
    for x in range(16):
        streak = rng.random()
        for y in range(16):
            n = (math.sin(y * 0.5 + x * 2.3) + 1) * 0.5
            c = ramp([(0.0, (92, 80, 70)), (0.5, (132, 116, 100)), (1.0, (168, 150, 128))], streak * 0.6 + n * 0.4)
            put(side, x, y, c)
    for _ in range(6):
        x, y = rng.randrange(16), rng.randrange(14)
        put(side, x, y, rng.choice([(196, 120, 92), (150, 170, 190), (210, 190, 150)]))
    top = canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 6.6:
                c = (100, 86, 74)
            elif d < 1.6:
                c = (226, 214, 236)
            else:
                c = (176, 156, 130) if int(d) % 2 == 0 else (150, 128, 108)
                if int(d * 3) % 5 == 0:
                    c = (198, 132, 102)
            put(top, x, y, c)
    return side, top


def steam_vent():
    rng = random.Random(3801)
    tone = fractal(3802)
    top = canvas()
    side = canvas()
    for y in range(16):
        for x in range(16):
            base = ramp([(0.0, (54, 52, 52)), (1.0, (92, 88, 86))], tone[y][x])
            put(side, x, y, base)
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2.2:
                c = (24, 22, 22)
            elif d < 3.6:
                c = mix((236, 226, 150), (210, 210, 200), (d - 2.2) / 1.4)
            elif d < 5.0 and rng.random() < 0.5:
                c = (214, 206, 176)
            else:
                c = base
            put(top, x, y, c)
    for x in range(16):
        if x % 5 in (1, 2):
            for y in range(16):
                if rng.random() < 0.6:
                    put(side, x, y, (196, 188, 160) if y % 3 else (226, 216, 150))
    return top, side


# --- writing ---------------------------------------------------------------------------------

def write(assets, data_root):
    tex = os.path.join(assets, "textures", "block")
    item_tex = os.path.join(assets, "textures", "item")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(item_tex, exist_ok=True)
    log_side, log_top = petrified_log()
    vent_top, vent_side = steam_vent()
    images = {
        "lavender": lavender(),
        "heather": heather(),
        "cattail_bottom": cattail_bottom(),
        "cattail_top": cattail_top(),
        "glowcap": glowcap(),
        "glowcap_block": glowcap_block(),
        "coconut": coconut(),
        "salt_block": salt_block(),
        "salt_crystal": cluster((206, 210, 214), (240, 242, 246), (168, 170, 176), (255, 255, 255)),
        "azure_crystal": crystal_block([(60, 170, 240), (40, 120, 220), (110, 210, 250)], 3901),
        "azure_crystal_cluster": cluster((40, 130, 220), (110, 210, 250), (20, 70, 150), (220, 250, 255)),
        "citrine_crystal": crystal_block([(240, 190, 60), (220, 150, 40), (250, 220, 110)], 3902),
        "citrine_crystal_cluster": cluster((220, 150, 40), (250, 214, 100), (150, 90, 20), (255, 248, 210)),
        "petrified_log": log_side,
        "petrified_log_top": log_top,
        "steam_vent_top": vent_top,
        "steam_vent_side": vent_side,
    }
    for stage in range(4):
        images[f"blueberry_bush_stage{stage}"] = berry_bush(stage)
    for name, img in images.items():
        img.save(os.path.join(tex, name + ".png"))
    coconut_item().save(os.path.join(item_tex, "coconut.png"))
    blueberries_item().save(os.path.join(item_tex, "blueberries.png"))

    models = os.path.join(assets, "models")
    for block, (_, kind) in BLOCKS.items():
        model = f"naturesmod:block/{block}"
        item_model = {"parent": model}
        state = {"variants": {"": {"model": model}}}
        if kind == "cube":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": model}})
        elif kind == "cross":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cross", "textures": {"cross": model}})
            item_model = {"parent": "minecraft:item/generated", "textures": {"layer0": model}}
        elif kind == "double":
            for half in ("bottom", "top"):
                _json(os.path.join(models, "block", f"{block}_{half}.json"), {"parent": "minecraft:block/cross", "textures": {"cross": f"{model}_{half}"}})
            state = {"variants": {"half=lower": {"model": model + "_bottom"}, "half=upper": {"model": model + "_top"}}}
            item_model = {"parent": "minecraft:item/generated", "textures": {"layer0": model + "_top"}}
        elif kind == "bush":
            for stage in range(4):
                _json(os.path.join(models, "block", f"{block}_stage{stage}.json"),
                      {"parent": "minecraft:block/cross", "textures": {"cross": f"{model}_stage{stage}"}})
            state = {"variants": {f"age={stage}": {"model": f"{model}_stage{stage}"} for stage in range(4)}}
            item_model = None
        elif kind == "coconut":
            # Two nuts hanging together under the fronds.
            _json(os.path.join(models, "block", block + ".json"), {
                "textures": {"particle": model, "nut": model},
                "elements": [
                    {"from": [3, 8, 3], "to": [9, 14, 9], "faces": {f: {"uv": [0, 0, 6, 6], "texture": "#nut"}
                                                                   for f in ("north", "south", "east", "west", "up", "down")}},
                    {"from": [8, 5, 7], "to": [13, 10, 12], "faces": {f: {"uv": [6, 6, 11, 11], "texture": "#nut"}
                                                                    for f in ("north", "south", "east", "west", "up", "down")}},
                    {"from": [5, 14, 5], "to": [8, 16, 8], "faces": {f: {"uv": [0, 12, 3, 14], "texture": "#nut"}
                                                                   for f in ("north", "south", "east", "west", "up", "down")}},
                ]})
            item_model = None
        elif kind == "cluster":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cross", "textures": {"cross": model}})
            state = {"variants": {
                "facing=down": {"model": model, "x": 180},
                "facing=east": {"model": model, "x": 90, "y": 90},
                "facing=north": {"model": model, "x": 90},
                "facing=south": {"model": model, "x": 90, "y": 180},
                "facing=up": {"model": model},
                "facing=west": {"model": model, "x": 90, "y": 270},
            }}
            item_model = {"parent": "minecraft:item/generated", "textures": {"layer0": model}}
        elif kind == "log":
            textures = {"end": model + "_top", "side": model}
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cube_column", "textures": textures})
            _json(os.path.join(models, "block", block + "_horizontal.json"), {"parent": "minecraft:block/cube_column_horizontal", "textures": textures})
            state = {"variants": {
                "axis=x": {"model": model + "_horizontal", "x": 90, "y": 90},
                "axis=y": {"model": model},
                "axis=z": {"model": model + "_horizontal", "x": 90},
            }}
        elif kind == "vent":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
                "top": model + "_top", "side": model + "_side", "bottom": "minecraft:block/tuff"}})
        _json(os.path.join(assets, "blockstates", block + ".json"), state)
        if item_model is None:
            continue
        if item_model.get("parent") == "minecraft:item/generated":
            _json(os.path.join(models, "item", block + ".json"), item_model)
            _json(os.path.join(assets, "items", block + ".json"), {"model": {"type": "minecraft:model", "model": f"naturesmod:item/{block}"}})
        else:
            _json(os.path.join(assets, "items", block + ".json"), {"model": {"type": "minecraft:model", "model": model}})
    # Coconuts and blueberries are food that plants: their items have pictures of their own.
    for item in ("coconut", "blueberries"):
        _json(os.path.join(models, "item", item + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"naturesmod:item/{item}"}})
        _json(os.path.join(assets, "items", item + ".json"), {"model": {"type": "minecraft:model", "model": f"naturesmod:item/{item}"}})

    loot(data_root)
    recipes(data_root)
    tags(data_root)


def loot(data_root):
    survives = {"type": "minecraft:survives_explosion"}

    def table(block, pools):
        _json(os.path.join(data_root, "naturesmod", "loot_table", "blocks", block + ".json"),
              {"type": "minecraft:block", "pools": pools, "random_sequence": f"naturesmod:blocks/{block}"})

    for block in BLOCKS:
        if block in ("cattail", "blueberry_bush", "coconut"):
            continue
        table(block, [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"naturesmod:{block}"}], "condition": survives}])
    table("cattail", [{"rolls": 1, "condition": survives, "entries": [{"type": "minecraft:item", "name": "naturesmod:cattail", "condition": {
        "type": "minecraft:match_block", "blocks": "naturesmod:cattail", "state": {"half": "lower"}}}]}])
    table("coconut", [{"rolls": 1, "condition": survives, "entries": [{"type": "minecraft:item", "name": "naturesmod:coconut"}]}])
    pools = []
    for age, low, high in ((3, 2, 3), (2, 1, 2)):
        pools.append({"rolls": 1, "condition": {"type": "minecraft:match_block", "blocks": "naturesmod:blueberry_bush", "state": {"age": str(age)}},
                      "entries": [{"type": "minecraft:item", "name": "naturesmod:blueberries"}],
                      "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}},
                                   {"type": "minecraft:explosion_decay"}]})
    table("blueberry_bush", pools)


def recipes(data_root):
    folder = os.path.join(data_root, "naturesmod", "recipe")

    def shaped(name, pattern, key, result, count=1, category="building"):
        _json(os.path.join(folder, name + ".json"), {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern, "key": key,
                                                      "result": {"count": count, "id": result}})

    def shapeless(name, ingredients, result, count=1, category="misc"):
        _json(os.path.join(folder, name + ".json"), {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients,
                                                      "result": {"count": count, "id": result}})

    shaped("salt_block", ["##", "##"], {"#": "naturesmod:salt_crystal"}, "naturesmod:salt_block")
    shaped("azure_crystal", ["##", "##"], {"#": "naturesmod:azure_crystal_cluster"}, "naturesmod:azure_crystal")
    shaped("citrine_crystal", ["##", "##"], {"#": "naturesmod:citrine_crystal_cluster"}, "naturesmod:citrine_crystal")
    shaped("glowcap_block", ["##", "##"], {"#": "naturesmod:glowcap"}, "naturesmod:glowcap_block")
    shapeless("purple_dye_from_lavender", ["naturesmod:lavender"], "minecraft:purple_dye")
    shapeless("magenta_dye_from_heather", ["naturesmod:heather"], "minecraft:magenta_dye")
    shapeless("blue_dye_from_blueberries", ["naturesmod:blueberries", "naturesmod:blueberries"], "minecraft:blue_dye")


def tags(data_root):
    folder = os.path.join(data_root, "minecraft", "tags", "block")

    def merge(tag, names):
        path = os.path.join(folder, tag + ".json")
        values = []
        if os.path.exists(path):
            with open(path, encoding="utf-8") as fh:
                values = json.load(fh)["values"]
        values += [f"naturesmod:{n}" for n in names if f"naturesmod:{n}" not in values]
        _json(path, {"replace": False, "values": values})

    merge("mineable/pickaxe", ["salt_block", "salt_crystal", "azure_crystal", "azure_crystal_cluster", "citrine_crystal",
                               "citrine_crystal_cluster", "petrified_log", "steam_vent"])
    merge("mineable/axe", ["glowcap_block", "coconut"])
    merge("small_flowers", ["lavender", "heather"])
    merge("bee_attractive", ["lavender", "heather"])
    merge("crystal_sound_blocks", ["azure_crystal", "citrine_crystal"])
    merge("sword_efficient", ["cattail", "blueberry_bush", "lavender", "heather"])


def lang():
    out = {f"block.naturesmod.{block}": name for block, (name, _) in BLOCKS.items()}
    out["item.naturesmod.coconut"] = "Coconut"
    out.update({f"item.naturesmod.{item}": name for item, name in ITEMS.items()})
    out.update({f"biome.naturesmod.{biome}": name for biome, name in BIOMES.items()})
    return out
