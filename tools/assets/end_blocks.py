"""Textures, models, block states, loot, recipes and tags of the new End's blocks: end moss,
voidstone, void crystal, starmetal, stardust, chorus trees, ender grass, void blooms and vines,
bounce blooms, updraft vents and meteorite.

Painted procedurally on 16x16 canvases in vanilla's pixel style, like the Nether's blocks.
"""

import json
import math
import os
import random

from hell import _json, bricks, canvas, crack, fractal, mix, put, ramp, shade, tile_noise

# Block id -> (display name, model kind).
BLOCKS = {
    "end_moss": ("End Moss", "grassy"),
    "voidstone": ("Voidstone", "cube"),
    "voidstone_bricks": ("Voidstone Bricks", "cube"),
    "polished_voidstone": ("Polished Voidstone", "cube"),
    "void_crystal": ("Block of Void Crystal", "cube"),
    "void_crystal_cluster": ("Void Crystal Cluster", "cluster"),
    "starmetal_ore": ("Starmetal Ore", "cube"),
    "starmetal_block": ("Block of Starmetal", "cube"),
    "stardust": ("Stardust", "cube"),
    "meteorite": ("Meteorite", "cube"),
    "chorus_log": ("Chorus Log", "log"),
    "chorus_bulb": ("Chorus Bulb", "cube"),
    "bounce_stem": ("Bounce Stem", "log"),
    "bounce_bloom": ("Bounce Bloom", "cap"),
    "updraft_vent": ("Updraft Vent", "vent"),
    "ender_grass": ("Ender Grass", "cross"),
    "void_bloom": ("Void Bloom", "cross"),
    "void_vines": ("Void Vines", "cross"),
}

ITEMS = {"raw_starmetal": "Raw Starmetal", "starmetal_ingot": "Starmetal Ingot"}

END_STONE = [(0.0, (196, 196, 140)), (0.5, (221, 223, 165)), (1.0, (236, 238, 186))]
VOID = [(0.0, (16, 10, 26)), (0.5, (34, 22, 50)), (1.0, (52, 36, 74))]


def end_stone_base(seed):
    """A pale, pitted stone like the End's own."""
    tone = fractal(seed)
    rng = random.Random(seed + 1)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, ramp(END_STONE, tone[y][x]))
    for _ in range(14):
        put(img, rng.randrange(16), rng.randrange(16), (178, 176, 122))
    return img


def end_moss_top():
    tone, fleck = fractal(1001), tile_noise(1002, 8)
    rng = random.Random(1003)
    img = canvas()
    for y in range(16):
        for x in range(16):
            c = ramp([(0.0, (58, 26, 78)), (0.5, (96, 44, 120)), (1.0, (138, 70, 160))], tone[y][x])
            if fleck[y][x] > 0.74:
                c = mix(c, (74, 178, 170), (fleck[y][x] - 0.74) * 3.0)
            put(img, x, y, c)
    for _ in range(8):
        put(img, rng.randrange(16), rng.randrange(16), (170, 236, 220))
    return img


def end_moss_side():
    img = end_stone_base(1011)
    top = end_moss_top()
    rng = random.Random(1012)
    for x in range(16):
        depth = 2 + rng.randrange(3)
        for y in range(depth):
            put(img, x, y, top.getpixel((x, y)))
    return img


def voidstone():
    tone = fractal(1101)
    rng = random.Random(1102)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, ramp(VOID, tone[y][x]))
    for _ in range(7):
        put(img, rng.randrange(16), rng.randrange(16), (120, 72, 170))
    for start in ((2, 4), (11, 11)):
        crack(img, rng, start, 5, (10, 6, 16), (24, 16, 36))
    return img


def polished_voidstone():
    tone = tile_noise(1201, 4)
    img = canvas()
    for y in range(16):
        for x in range(16):
            c = ramp(VOID, 0.35 + 0.3 * tone[y][x])
            if x in (0, 15) or y in (0, 15):
                c = shade(c, 0.7)
            elif x in (1, 14) or y in (1, 14):
                c = shade(c, 1.25)
            put(img, x, y, c)
    return img


def void_crystal():
    img = canvas()
    for y in range(16):
        for x in range(16):
            # Facets: diagonal bands of light and shade.
            band = ((x + y) // 4) % 3
            d = ((x - y) % 8) / 8.0
            base = [(70, 210, 230), (120, 120, 240), (170, 90, 230)][band]
            c = shade(base, 0.75 + 0.45 * d)
            put(img, x, y, c)
    rng = random.Random(1301)
    for _ in range(6):
        put(img, rng.randrange(16), rng.randrange(16), (235, 250, 255))
    return img


def crystal_cluster():
    img = canvas()
    for cx, height, half, lean in ((8, 15, 2.6, 0.0), (4, 10, 1.9, -0.25), (12, 11, 1.9, 0.28), (6, 6, 1.4, -0.1), (10, 7, 1.3, 0.15)):
        for y in range(height):
            t = y / height
            w = half * (1 - t ** 1.6)
            mid = cx + lean * y
            for x in range(16):
                d = x - mid
                if abs(d) <= w:
                    if abs(d) > w - 0.9:
                        c = (60, 50, 150)
                    elif d < 0:
                        c = (90, 190, 230)
                    else:
                        c = (180, 140, 250)
                    put(img, x, 15 - y, c)
        put(img, round(cx + lean * (height - 1)), 16 - height, (240, 250, 255))
    return img


def starmetal_ore():
    img = end_stone_base(1401)
    rng = random.Random(1402)
    for _ in range(5):
        x, y = rng.randrange(2, 14), rng.randrange(2, 14)
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            put(img, x + dx, y + dy, (120, 170, 230) if (dx, dy) != (0, 0) else (230, 245, 255))
        if rng.random() < 0.5:
            put(img, x + 1, y + 1, (70, 110, 180))
    return img


def starmetal_block():
    img = canvas()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            c = (150, 180, 220) if not edge else (90, 110, 160)
            if (x in (1, 14) or y in (1, 14)) and not edge:
                c = (205, 225, 250)
            put(img, x, y, shade(c, 0.92 + 0.08 * math.sin((x + y) * 0.8)))
    # A star in the middle.
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        put(img, 7 + dx, 7 + dy, (245, 250, 255) if abs(dx) + abs(dy) <= 1 else (190, 215, 250))
    return img


def stardust():
    tone = fractal(1501)
    rng = random.Random(1502)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, ramp([(0.0, (178, 160, 200)), (0.5, (206, 192, 226)), (1.0, (228, 220, 244))], tone[y][x]))
    for _ in range(12):
        put(img, rng.randrange(16), rng.randrange(16), (255, 255, 255))
    for _ in range(6):
        put(img, rng.randrange(16), rng.randrange(16), (150, 200, 255))
    return img


def meteorite():
    tone = fractal(1601)
    rng = random.Random(1602)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, ramp([(0.0, (30, 28, 32)), (0.5, (54, 50, 56)), (1.0, (86, 80, 84))], tone[y][x]))
    for start in ((3, 3), (12, 10)):
        crack(img, rng, start, 7, (210, 110, 40), (120, 60, 30))
    for _ in range(6):
        put(img, rng.randrange(16), rng.randrange(16), (160, 170, 190))
    return img


def chorus_log():
    rng = random.Random(1701)
    side = canvas()
    for x in range(16):
        streak = rng.random()
        for y in range(16):
            n = (math.sin(y * 0.7 + x * 1.9) + 1) * 0.5
            c = ramp([(0.0, (70, 36, 82)), (0.5, (110, 64, 120)), (1.0, (150, 104, 156))], streak * 0.6 + n * 0.4)
            put(side, x, y, c)
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(14)
        put(side, x, y, (196, 150, 200))
        put(side, x, y + 1, (176, 130, 182))
    top = canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            c = (82, 44, 92) if d > 6.6 else (190, 150, 196) if int(d) % 2 == 0 else (160, 118, 168)
            put(top, x, y, c)
    return side, top


def chorus_bulb():
    tone = fractal(1801)
    rng = random.Random(1802)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, ramp([(0.0, (190, 90, 190)), (0.5, (232, 140, 220)), (1.0, (255, 200, 245))], tone[y][x]))
    for _ in range(10):
        x, y = rng.randrange(16), rng.randrange(16)
        put(img, x, y, (255, 240, 255))
    return img


def bounce_stem():
    rng = random.Random(1901)
    side = canvas()
    for x in range(16):
        for y in range(16):
            c = ramp([(0.0, (200, 196, 210)), (1.0, (232, 228, 240))], (math.sin(x * 1.3) + 1) * 0.5)
            put(side, x, y, c)
    for _ in range(6):
        put(side, rng.randrange(16), rng.randrange(16), (170, 160, 190))
    top = canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            put(top, x, y, (226, 222, 236) if d < 6 else (196, 190, 208))
    return side, top


def bounce_cap():
    tone = fractal(2001)
    rng = random.Random(2002)
    top = canvas()
    for y in range(16):
        for x in range(16):
            put(top, x, y, ramp([(0.0, (180, 40, 140)), (0.5, (220, 70, 170)), (1.0, (250, 110, 200))], tone[y][x]))
    for _ in range(5):
        cx, cy = rng.randrange(16), rng.randrange(16)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            put(top, (cx + dx) % 16, (cy + dy) % 16, (255, 236, 160))
    side = top.copy()
    for x in range(16):
        for y in range(12, 16):
            put(side, x, y, (130, 30, 110) if y == 15 else shade(side.getpixel((x, y))[:3], 0.8))
    bottom = canvas()
    for y in range(16):
        for x in range(16):
            gill = (int(math.atan2(y - 7.5, x - 7.5) * 6) % 2) == 0
            put(bottom, x, y, (200, 150, 190) if gill else (160, 110, 150))
    return top, side, bottom


def updraft_vent():
    top = canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 3.0:
                c = mix((230, 250, 255), (120, 200, 240), d / 3.0)
            elif d < 6.5 and (x + y) % 3 != 0:
                c = (40, 26, 58) if (x % 3 == 0 or y % 3 == 0) else (20, 14, 30)
            else:
                c = ramp(VOID, 0.5 + 0.2 * math.sin(x * 1.7 + y))
            put(top, x, y, c)
    side = voidstone()
    for x in range(16):
        if x % 4 in (1, 2):
            for y in range(3, 13):
                put(side, x, y, (20, 14, 30) if y % 3 else (90, 160, 200))
    return top, side


def ender_grass():
    rng = random.Random(2101)
    img = canvas()
    for blade in range(9):
        x = 1 + blade * 1.6 + rng.random()
        height = 6 + rng.randrange(9)
        lean = (rng.random() - 0.5) * 0.4
        for y in range(height):
            t = y / height
            c = mix((70, 30, 96), (160, 90, 200), t) if blade % 3 else mix((40, 110, 110), (100, 220, 200), t)
            put(img, round(x + lean * y), 15 - y, c)
    return img


def void_bloom():
    img = canvas()
    for y in range(9):
        put(img, 8, 15 - y, (60, 120, 110))
    put(img, 7, 11, (60, 120, 110))
    put(img, 6, 10, (70, 140, 120))
    # Petals around a glowing heart.
    for dx, dy in ((0, -2), (2, 0), (-2, 0), (0, 2), (1, -1), (-1, -1), (1, 1), (-1, 1)):
        put(img, 8 + dx, 5 + dy, (200, 90, 240) if abs(dx) + abs(dy) == 2 else (130, 230, 255))
    for dx, dy in ((0, -3), (3, 0), (-3, 0)):
        put(img, 8 + dx, 5 + dy, (150, 60, 200))
    put(img, 8, 5, (255, 255, 255))
    return img


def void_vines():
    rng = random.Random(2201)
    img = canvas()
    for strand in (3, 7, 11, 13):
        x = strand
        for y in range(16):
            if rng.random() < 0.2:
                x += rng.choice((-1, 1))
            x = max(1, min(14, x))
            put(img, x, y, (40, 120, 130))
            if y % 5 == 2:
                put(img, x, y, (140, 250, 240))
                put(img, x + 1, y, (90, 200, 210))
    return img


def raw_starmetal():
    img = canvas()
    rng = random.Random(2301)
    for y in range(4, 13):
        for x in range(3, 13):
            if math.hypot((x - 7.5) / 5.0, (y - 8.0) / 4.2) < 1.0 - rng.random() * 0.15:
                put(img, x, y, (110, 140, 190) if (x + y) % 3 else (160, 190, 235))
    for _ in range(4):
        put(img, rng.randrange(5, 11), rng.randrange(6, 11), (240, 250, 255))
    return img


def starmetal_ingot():
    img = canvas()
    for y in range(6, 12):
        for x in range(2 + (11 - y) // 2, 15 - (y - 6) // 3):
            edge = y in (6, 11)
            put(img, x, y, (120, 150, 200) if edge else (180, 205, 245))
    for x in range(5, 12):
        put(img, x, 7, (230, 242, 255))
    put(img, 11, 5, (255, 255, 255))
    put(img, 12, 4, (210, 230, 255))
    return img


def write(assets, data_root):
    tex = os.path.join(assets, "textures", "block")
    os.makedirs(tex, exist_ok=True)
    log_side, log_top = chorus_log()
    stem_side, stem_top = bounce_stem()
    cap_top, cap_side, cap_bottom = bounce_cap()
    vent_top, vent_side = updraft_vent()
    images = {
        "end_moss_top": end_moss_top(),
        "end_moss_side": end_moss_side(),
        "end_stone_base": end_stone_base(1021),
        "voidstone": voidstone(),
        "voidstone_bricks": bricks(((26, 16, 40), (46, 30, 66)), (10, 6, 16), 1151),
        "polished_voidstone": polished_voidstone(),
        "void_crystal": void_crystal(),
        "void_crystal_cluster": crystal_cluster(),
        "starmetal_ore": starmetal_ore(),
        "starmetal_block": starmetal_block(),
        "stardust": stardust(),
        "meteorite": meteorite(),
        "chorus_log": log_side,
        "chorus_log_top": log_top,
        "chorus_bulb": chorus_bulb(),
        "bounce_stem": stem_side,
        "bounce_stem_top": stem_top,
        "bounce_bloom_top": cap_top,
        "bounce_bloom_side": cap_side,
        "bounce_bloom_bottom": cap_bottom,
        "updraft_vent_top": vent_top,
        "updraft_vent_side": vent_side,
        "ender_grass": ender_grass(),
        "void_bloom": void_bloom(),
        "void_vines": void_vines(),
    }
    for name, img in images.items():
        img.save(os.path.join(tex, name + ".png"))
    item_tex = os.path.join(assets, "textures", "item")
    os.makedirs(item_tex, exist_ok=True)
    raw_starmetal().save(os.path.join(item_tex, "raw_starmetal.png"))
    starmetal_ingot().save(os.path.join(item_tex, "starmetal_ingot.png"))

    models = os.path.join(assets, "models")
    for block, (_, kind) in BLOCKS.items():
        model = f"naturesmod:block/{block}"
        item_model = {"parent": model}
        state = {"variants": {"": {"model": model}}}
        if kind == "cube":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": model}})
        elif kind == "grassy":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
                "top": model + "_top", "side": model + "_side", "bottom": "naturesmod:block/end_stone_base"}})
        elif kind == "cap":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
                "top": model + "_top", "side": model + "_side", "bottom": model + "_bottom"}})
        elif kind == "vent":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
                "top": model + "_top", "side": model + "_side", "bottom": "naturesmod:block/voidstone"}})
        elif kind == "log":
            textures = {"end": model + "_top", "side": model}
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cube_column", "textures": textures})
            _json(os.path.join(models, "block", block + "_horizontal.json"),
                  {"parent": "minecraft:block/cube_column_horizontal", "textures": textures})
            state = {"variants": {
                "axis=x": {"model": model + "_horizontal", "x": 90, "y": 90},
                "axis=y": {"model": model},
                "axis=z": {"model": model + "_horizontal", "x": 90},
            }}
        elif kind == "cross":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cross", "textures": {"cross": model}})
            item_model = {"parent": "minecraft:item/generated", "textures": {"layer0": model}}
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
        _json(os.path.join(assets, "blockstates", block + ".json"), state)
        if item_model.get("parent") == "minecraft:item/generated":
            _json(os.path.join(models, "item", block + ".json"), item_model)
            _json(os.path.join(assets, "items", block + ".json"), {"model": {"type": "minecraft:model", "model": f"naturesmod:item/{block}"}})
        else:
            _json(os.path.join(assets, "items", block + ".json"), {"model": {"type": "minecraft:model", "model": model}})
    for item in ITEMS:
        _json(os.path.join(models, "item", item + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"naturesmod:item/{item}"}})
        _json(os.path.join(assets, "items", item + ".json"), {"model": {"type": "minecraft:model", "model": f"naturesmod:item/{item}"}})

    # Loot: blocks drop themselves; starmetal ore gives raw starmetal, chorus bulbs chorus fruit,
    # and ender grass nothing at all.
    survives = {"type": "minecraft:survives_explosion"}
    special = {
        "starmetal_ore": [{"type": "minecraft:item", "name": "naturesmod:raw_starmetal", "modifier": [
            {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
            {"type": "minecraft:explosion_decay"}]}],
        "chorus_bulb": [{"type": "minecraft:item", "name": "minecraft:chorus_fruit", "modifier": [
            {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}},
            {"type": "minecraft:explosion_decay"}]}],
    }
    for block in BLOCKS:
        if block == "ender_grass":
            pools = []
        elif block in special:
            pools = [{"rolls": 1, "entries": special[block]}]
        else:
            pools = [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"naturesmod:{block}"}], "condition": survives}]
        _json(os.path.join(data_root, "naturesmod", "loot_table", "blocks", block + ".json"), {"type": "minecraft:block", "pools": pools})

    recipes = os.path.join(data_root, "naturesmod", "recipe")

    def shaped(name, pattern, key, result, count=1, category="building"):
        _json(os.path.join(recipes, name + ".json"), {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                                                     "key": key, "result": {"count": count, "id": result}})

    shaped("voidstone_bricks", ["##", "##"], {"#": "naturesmod:voidstone"}, "naturesmod:voidstone_bricks", 4)
    shaped("polished_voidstone", ["##", "##"], {"#": "naturesmod:voidstone_bricks"}, "naturesmod:polished_voidstone", 4)
    shaped("starmetal_block", ["###", "###", "###"], {"#": "naturesmod:starmetal_ingot"}, "naturesmod:starmetal_block")
    shaped("void_crystal", ["##", "##"], {"#": "naturesmod:void_crystal_cluster"}, "naturesmod:void_crystal")
    shaped("bounce_bloom", ["SS", "SC"], {"S": "minecraft:slime_ball", "C": "minecraft:chorus_fruit"}, "naturesmod:bounce_bloom", 2)
    shaped("updraft_vent", ["VPV", "VCV", "VVV"], {"V": "naturesmod:voidstone", "P": "minecraft:phantom_membrane",
                                                    "C": "naturesmod:void_crystal"}, "naturesmod:updraft_vent", 1, "misc")
    _json(os.path.join(recipes, "starmetal_ingot_from_block.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ["naturesmod:starmetal_block"],
        "result": {"count": 9, "id": "naturesmod:starmetal_ingot"}})
    for kind, time in (("smelting", 200), ("blasting", 100)):
        _json(os.path.join(recipes, f"starmetal_ingot_from_{kind}.json"), {
            "type": f"minecraft:{kind}", "category": "misc", "cookingtime": time, "experience": 1.0,
            "ingredient": "naturesmod:raw_starmetal", "result": {"id": "naturesmod:starmetal_ingot"}})

    # Which tool mines what: merged into the tag files the Nether's blocks wrote.
    tags = os.path.join(data_root, "minecraft", "tags", "block")

    def merge(tag, names):
        path = os.path.join(tags, tag + ".json")
        values = []
        if os.path.exists(path):
            with open(path, encoding="utf-8") as fh:
                values = json.load(fh)["values"]
        values += [f"naturesmod:{n}" for n in names if f"naturesmod:{n}" not in values]
        _json(path, {"replace": False, "values": values})

    merge("mineable/pickaxe", ["end_moss", "voidstone", "voidstone_bricks", "polished_voidstone", "void_crystal", "void_crystal_cluster",
                               "starmetal_ore", "starmetal_block", "meteorite", "updraft_vent", "star_altar"])
    merge("mineable/shovel", ["stardust"])
    merge("mineable/axe", ["chorus_log", "bounce_stem"])
    merge("mineable/hoe", ["chorus_bulb", "bounce_bloom"])
    merge("needs_iron_tool", ["starmetal_ore", "starmetal_block", "meteorite"])
    merge("needs_diamond_tool", ["star_altar"])
    merge("dragon_immune", ["star_altar"])
    # The dragon's breath and the End's own creatures may not dig into them.
    merge("dragon_immune", ["updraft_vent"])
    # Chorus grows on the new ground as it does on end stone, and endermen carry it about.
    merge("supports_chorus_plant", ["end_moss", "voidstone", "stardust"])
    merge("supports_chorus_flower", ["end_moss", "voidstone", "stardust"])
    merge("enderman_holdable", ["end_moss", "stardust", "void_bloom"])


def lang():
    out = {f"block.naturesmod.{block}": name for block, (name, _) in BLOCKS.items()}
    out.update({f"item.naturesmod.{item}": name for item, name in ITEMS.items()})
    return out
