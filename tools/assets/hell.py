"""Textures, models, block states, loot, recipes and tags of the hellish Nether's blocks:
brimstone, scorched netherrack, ash, flesh, charred logs, bone spikes, ember crystals and the
braziers that light the Infernal Citadels.

Everything is painted procedurally on 16x16 canvases in vanilla's pixel style. Textures tile:
the noise wraps around at the edges.
"""

import json
import math
import os
import random

from PIL import Image

CLEAR = (0, 0, 0, 0)

# Block id -> (display name, model kind).
BLOCKS = {
    "brimstone": ("Brimstone", "cube"),
    "brimstone_bricks": ("Brimstone Bricks", "cube"),
    "scorched_netherrack": ("Scorched Netherrack", "cube"),
    "scorched_bricks": ("Scorched Bricks", "cube"),
    "ash_block": ("Block of Ash", "cube"),
    "flesh_block": ("Flesh Block", "cube"),
    "charred_log": ("Charred Log", "log"),
    "bone_spikes": ("Bone Spikes", "cross"),
    "ember_crystal": ("Ember Crystal", "cluster"),
    "hellfire_brazier": ("Hellfire Brazier", "brazier"),
    "soul_brazier": ("Soul Brazier", "brazier"),
}

ITEMS = {"ember_shard": "Ember Shard"}


def canvas():
    return Image.new("RGBA", (16, 16), CLEAR)


def put(img, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((int(x), int(y)), tuple(color[:3]) + ((color[3],) if len(color) == 4 else (255,)))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(c[i] * f))) for i in range(3)) + (255,)


def tile_noise(seed, period=4):
    """Smooth value noise on a 16x16 torus: a grid of random values every 16/period pixels."""
    rng = random.Random(seed)
    grid = [[rng.random() for _ in range(period)] for _ in range(period)]
    cell = 16 / period
    out = [[0.0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            gx, gy = x / cell, y / cell
            x0, y0 = int(gx) % period, int(gy) % period
            x1, y1 = (x0 + 1) % period, (y0 + 1) % period
            tx, ty = gx - int(gx), gy - int(gy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            top = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
            bottom = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
            out[y][x] = top * (1 - ty) + bottom * ty
    return out


def fractal(seed):
    a, b, c = tile_noise(seed, 2), tile_noise(seed + 1, 4), tile_noise(seed + 2, 8)
    return [[a[y][x] * 0.5 + b[y][x] * 0.3 + c[y][x] * 0.2 for x in range(16)] for y in range(16)]


def ramp(stops, t):
    """Colour at t along a list of (position, colour) stops."""
    t = max(0.0, min(1.0, t))
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if t <= p1:
            return mix(c0, c1, (t - p0) / max(1e-6, p1 - p0))
    return stops[-1][1] + (255,)


def crack(img, rng, start, steps, core, edge, wrap=True):
    """A wandering crack, glowing at its core."""
    x, y = start
    dx, dy = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
    for _ in range(steps):
        put(img, x % 16 if wrap else x, y % 16 if wrap else y, core)
        for ex, ey in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            ex, ey = (ex % 16, ey % 16) if wrap else (ex, ey)
            if 0 <= ex < 16 and 0 <= ey < 16 and img.getpixel((ex, ey))[:3] != tuple(core[:3]) and rng.random() < 0.35:
                put(img, ex, ey, edge)
        if rng.random() < 0.35:
            dx, dy = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1), (dx, dy)])
        x += dx
        y += dy


# --- stone ---------------------------------------------------------------------------------

def brimstone():
    tone, burn = fractal(101), tile_noise(102, 4)
    rng = random.Random(103)
    stops = [(0.0, (92, 64, 24)), (0.3, (150, 112, 38)), (0.6, (196, 164, 58)), (1.0, (222, 206, 92))]
    img = canvas()
    for y in range(16):
        for x in range(16):
            c = ramp(stops, tone[y][x] * 1.2 - 0.1)
            if burn[y][x] > 0.68:
                c = mix(c, (172, 74, 24), (burn[y][x] - 0.68) * 2.6)
            put(img, x, y, c)
    # A crust of dark pits and cracks, and bright sulfur crystals here and there.
    for start in ((3, 1), (12, 8)):
        crack(img, rng, start, 7, (70, 46, 18), (110, 78, 30))
    for _ in range(10):
        x, y = rng.randrange(16), rng.randrange(16)
        put(img, x, y, (64, 42, 16))
    for _ in range(6):
        x, y = rng.randrange(16), rng.randrange(16)
        put(img, x, y, (238, 232, 120))
    return img


def bricks(body, mortar, seed, glow=None):
    """Bricks eight pixels long and four high, every other row shifted by four."""
    rng = random.Random(seed)
    tone = tile_noise(seed, 8)
    img = canvas()
    for y in range(16):
        row = y // 4
        for x in range(16):
            in_mortar = y % 4 == 3 or (x + (row % 2) * 4) % 8 == 7
            if in_mortar:
                c = mortar if glow is None or rng.random() > 0.25 else glow
                put(img, x, y, c)
            else:
                lit = 1.0 + (0.12 if y % 4 == 0 else 0.0) - (0.1 if (x + (row % 2) * 4) % 8 == 6 else 0.0)
                put(img, x, y, shade(mix(body[0], body[1], tone[y][x]), lit))
    return img


def scorched_netherrack():
    tone = fractal(201)
    rng = random.Random(202)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, ramp([(0.0, (22, 6, 6)), (0.5, (46, 13, 11)), (1.0, (74, 22, 16))], tone[y][x]))
    for start in ((2, 3), (11, 9), (6, 13)):
        crack(img, rng, start, 9, (255, 150, 52), (178, 58, 20))
    for _ in range(4):
        put(img, rng.randrange(16), rng.randrange(16), (255, 214, 120))
    return img


def ash_block():
    tone = fractal(301)
    rng = random.Random(302)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, ramp([(0.0, (78, 76, 74)), (0.5, (110, 108, 104)), (1.0, (140, 137, 132))], tone[y][x]))
    for _ in range(14):
        put(img, rng.randrange(16), rng.randrange(16), (58, 56, 55))
    for _ in range(10):
        put(img, rng.randrange(16), rng.randrange(16), (176, 173, 168))
    return img


def flesh_block():
    tone = fractal(401)
    rng = random.Random(402)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, ramp([(0.0, (78, 8, 16)), (0.45, (122, 20, 30)), (1.0, (156, 36, 44))], tone[y][x]))
    for start in ((1, 2), (9, 6), (4, 12), (13, 14)):
        crack(img, rng, start, 10, (188, 54, 70), (140, 30, 44))
    for _ in range(6):
        x, y = rng.randrange(16), rng.randrange(16)
        put(img, x, y, (232, 150, 160))
    for _ in range(8):
        put(img, rng.randrange(16), rng.randrange(16), (56, 4, 10))
    return img


def charred_log():
    rng = random.Random(501)
    side = canvas()
    for x in range(16):
        streak = rng.random()
        for y in range(16):
            n = (math.sin(y * 0.9 + x * 2.3) + 1) * 0.5
            c = ramp([(0.0, (18, 15, 14)), (0.5, (34, 29, 27)), (1.0, (56, 48, 44))], streak * 0.6 + n * 0.4)
            put(side, x, y, c)
    for _ in range(4):
        x, y = rng.randrange(16), rng.randrange(13)
        for i in range(rng.randint(2, 4)):
            put(side, x, y + i, (196, 72, 22) if i % 2 == 0 else (238, 120, 40))
    top = canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 6.6:
                c = (30, 26, 24)
            elif int(d) % 2 == 0:
                c = (58, 52, 48)
            else:
                c = (40, 35, 32)
            if d < 1.6:
                c = (214, 92, 30)
            put(top, x, y, c)
    return side, top


# --- plants and crystals --------------------------------------------------------------------

def bone_spikes():
    rng = random.Random(601)
    img = canvas()
    for base_x, height, width in ((2, 11, 2), (5, 15, 3), (9, 13, 3), (12, 9, 2), (7, 8, 2)):
        for y in range(height):
            w = max(1, round(width * (1 - y / height) + 0.4))
            for i in range(w):
                x = base_x + i - w // 2
                c = (248, 244, 228) if i == w - 1 else (226, 218, 194) if i > 0 or w == 1 else (190, 180, 154)
                put(img, x, 15 - y, c)
        put(img, base_x, 15 - height, (255, 252, 240))
    for _ in range(3):
        put(img, rng.randrange(1, 15), 15, (170, 160, 136))
    return img


def ember_crystal():
    img = canvas()
    for cx, height, half, lean in ((8, 14, 2.5, 0.0), (4, 9, 1.8, -0.25), (12, 10, 1.8, 0.28), (6, 6, 1.4, -0.1)):
        for y in range(height):
            t = y / height
            w = half * (1 - t ** 1.6)
            mid = cx + lean * y
            for x in range(16):
                d = x - mid
                if abs(d) <= w:
                    if abs(d) > w - 0.9:
                        c = (172, 52, 18)
                    elif d < 0:
                        c = (238, 104, 36)
                    else:
                        c = (255, 172, 78)
                    put(img, x, 15 - y, c)
        put(img, round(cx + lean * (height - 1)), 16 - height, (255, 236, 180))
    return img


def ember_shard():
    img = canvas()
    for i in range(10):
        x, y = 3 + i, 12 - i
        for d in (-1, 0, 1):
            put(img, x + d, y, (238, 104, 36) if d < 0 else (255, 172, 78) if d == 0 else (172, 52, 18))
        if i % 3 == 0:
            put(img, x, y - 1, (255, 236, 180))
    put(img, 13, 2, (255, 246, 210))
    return img


# --- braziers -------------------------------------------------------------------------------

def brazier_side():
    img = canvas()
    for y in range(16):
        for x in range(16):
            c = (52, 50, 56) if (x + y) % 5 else (60, 58, 64)
            if y < 2:
                c = (92, 90, 98)
            put(img, x, y, c)
    for x in (2, 7, 13):
        put(img, x, 4, (28, 28, 32))
        put(img, x, 11, (28, 28, 32))
    return img


def brazier_coals(dark, hot, hotter, seed):
    rng = random.Random(seed)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, dark)
    for _ in range(36):
        x, y = rng.randrange(16), rng.randrange(16)
        put(img, x, y, hot if rng.random() < 0.7 else hotter)
    return img


def _json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def brazier_model(fire, top):
    faces = lambda: {f: {"texture": "#side"} for f in ("north", "south", "east", "west", "up", "down")}
    bowl = {f: {"texture": "#top" if f == "up" else "#side"} for f in ("north", "south", "east", "west", "up", "down")}
    plane = lambda axis_faces: {f: {"uv": [0, 0, 16, 16], "texture": "#fire"} for f in axis_faces}
    return {
        "ambientocclusion": False,
        "textures": {"particle": "naturesmod:block/brazier_side", "side": "naturesmod:block/brazier_side", "top": top, "fire": fire},
        "elements": [
            {"from": [4, 0, 4], "to": [12, 2, 12], "faces": faces()},
            {"from": [6, 2, 6], "to": [10, 6, 10], "faces": faces()},
            {"from": [2, 6, 2], "to": [14, 10, 14], "faces": bowl},
            {"from": [2, 10, 2], "to": [14, 11, 3], "faces": faces()},
            {"from": [2, 10, 13], "to": [14, 11, 14], "faces": faces()},
            {"from": [2, 10, 3], "to": [3, 11, 13], "faces": faces()},
            {"from": [13, 10, 3], "to": [14, 11, 13], "faces": faces()},
            {"from": [1.5, 10, 8], "to": [14.5, 26, 8], "shade": False,
             "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}, "faces": plane(("north", "south"))},
            {"from": [8, 10, 1.5], "to": [8, 26, 14.5], "shade": False,
             "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}, "faces": plane(("east", "west"))},
        ],
    }


def write(assets, data_root):
    tex = os.path.join(assets, "textures", "block")
    os.makedirs(tex, exist_ok=True)
    side, top = charred_log()
    images = {
        "brimstone": brimstone(),
        "brimstone_bricks": bricks(((150, 110, 40), (190, 154, 60)), (74, 50, 20), 111),
        "scorched_netherrack": scorched_netherrack(),
        "scorched_bricks": bricks(((28, 12, 12), (52, 20, 18)), (214, 92, 28), 211, glow=(255, 170, 70)),
        "ash_block": ash_block(),
        "flesh_block": flesh_block(),
        "charred_log": side,
        "charred_log_top": top,
        "bone_spikes": bone_spikes(),
        "ember_crystal": ember_crystal(),
        "brazier_side": brazier_side(),
        "hellfire_brazier_top": brazier_coals((40, 18, 12), (255, 118, 30), (255, 196, 90), 701),
        "soul_brazier_top": brazier_coals((18, 24, 28), (56, 206, 226), (160, 244, 255), 702),
    }
    for name, img in images.items():
        img.save(os.path.join(tex, name + ".png"))
    item_tex = os.path.join(assets, "textures", "item")
    os.makedirs(item_tex, exist_ok=True)
    ember_shard().save(os.path.join(item_tex, "ember_shard.png"))

    models = os.path.join(assets, "models")
    for block, (_, kind) in BLOCKS.items():
        model = f"naturesmod:block/{block}"
        item_model = {"parent": model}
        if kind == "cube":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": model}})
            _json(os.path.join(assets, "blockstates", block + ".json"), {"variants": {"": {"model": model}}})
        elif kind == "log":
            textures = {"end": model + "_top", "side": model}
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cube_column", "textures": textures})
            _json(os.path.join(models, "block", block + "_horizontal.json"),
                  {"parent": "minecraft:block/cube_column_horizontal", "textures": textures})
            _json(os.path.join(assets, "blockstates", block + ".json"), {"variants": {
                "axis=x": {"model": model + "_horizontal", "x": 90, "y": 90},
                "axis=y": {"model": model},
                "axis=z": {"model": model + "_horizontal", "x": 90},
            }})
        elif kind == "cross":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cross", "textures": {"cross": model}})
            _json(os.path.join(assets, "blockstates", block + ".json"), {"variants": {"": {"model": model}}})
            item_model = {"parent": "minecraft:item/generated", "textures": {"layer0": model}}
        elif kind == "cluster":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cross", "textures": {"cross": model}})
            _json(os.path.join(assets, "blockstates", block + ".json"), {"variants": {
                "facing=down": {"model": model, "x": 180},
                "facing=east": {"model": model, "x": 90, "y": 90},
                "facing=north": {"model": model, "x": 90},
                "facing=south": {"model": model, "x": 90, "y": 180},
                "facing=up": {"model": model},
                "facing=west": {"model": model, "x": 90, "y": 270},
            }})
            item_model = {"parent": "minecraft:item/generated", "textures": {"layer0": model}}
        elif kind == "brazier":
            soul = block.startswith("soul")
            _json(os.path.join(models, "block", block + ".json"),
                  brazier_model("minecraft:block/soul_fire_0" if soul else "minecraft:block/fire_0", f"naturesmod:block/{block}_top"))
            _json(os.path.join(assets, "blockstates", block + ".json"), {"variants": {"": {"model": model}}})
        if item_model.get("parent") == "minecraft:item/generated":
            _json(os.path.join(models, "item", block + ".json"), item_model)
            _json(os.path.join(assets, "items", block + ".json"), {"model": {"type": "minecraft:model", "model": f"naturesmod:item/{block}"}})
        else:
            _json(os.path.join(assets, "items", block + ".json"), {"model": {"type": "minecraft:model", "model": model}})
    _json(os.path.join(models, "item", "ember_shard.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": "naturesmod:item/ember_shard"}})
    _json(os.path.join(assets, "items", "ember_shard.json"), {"model": {"type": "minecraft:model", "model": "naturesmod:item/ember_shard"}})

    # Loot: every block drops itself, but an ember crystal shatters into shards.
    survives = {"type": "minecraft:survives_explosion"}
    for block in BLOCKS:
        if block == "ember_crystal":
            pools = [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "naturesmod:ember_shard", "modifier": [
                {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}},
                {"type": "minecraft:explosion_decay"}]}]}]
        else:
            pools = [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"naturesmod:{block}"}], "condition": survives}]
        _json(os.path.join(data_root, "naturesmod", "loot_table", "blocks", block + ".json"), {"type": "minecraft:block", "pools": pools})

    recipes = os.path.join(data_root, "naturesmod", "recipe")
    square = lambda ingredient, result, count: {
        "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["##", "##"],
        "key": {"#": ingredient}, "result": {"count": count, "id": result}}
    _json(os.path.join(recipes, "brimstone_bricks.json"), square("naturesmod:brimstone", "naturesmod:brimstone_bricks", 4))
    _json(os.path.join(recipes, "scorched_bricks.json"), square("naturesmod:scorched_netherrack", "naturesmod:scorched_bricks", 4))
    _json(os.path.join(recipes, "flesh_block.json"), {
        "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###", "###", "###"],
        "key": {"#": "minecraft:rotten_flesh"}, "result": {"count": 1, "id": "naturesmod:flesh_block"}})
    _json(os.path.join(recipes, "rotten_flesh_from_flesh_block.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ["naturesmod:flesh_block"],
        "result": {"count": 9, "id": "minecraft:rotten_flesh"}})
    _json(os.path.join(recipes, "blaze_powder_from_ember_shards.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ["naturesmod:ember_shard", "naturesmod:ember_shard"],
        "result": {"count": 1, "id": "minecraft:blaze_powder"}})
    for block, fuel in (("hellfire_brazier", "#minecraft:coals"), ("soul_brazier", "#minecraft:soul_fire_base_blocks")):
        _json(os.path.join(recipes, block + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["I#I", " I ", "III"],
            "key": {"I": "minecraft:iron_ingot", "#": fuel}, "result": {"count": 1, "id": f"naturesmod:{block}"}})
    _json(os.path.join(recipes, "charred_log.json"), {
        "type": "minecraft:campfire_cooking", "category": "blocks", "cookingtime": 600, "experience": 0.1,
        "ingredient": "#minecraft:logs_that_burn", "result": {"id": "naturesmod:charred_log"}})
    _json(os.path.join(recipes, "ash_block.json"), {
        "type": "minecraft:smelting", "category": "blocks", "cookingtime": 200, "experience": 0.1,
        "ingredient": "naturesmod:charred_log", "result": {"id": "naturesmod:ash_block"}})

    # Which tool mines what; brimstone burns forever like netherrack.
    tags = os.path.join(data_root, "minecraft", "tags", "block")
    ns = lambda names: [f"naturesmod:{n}" for n in names]
    _json(os.path.join(tags, "mineable", "pickaxe.json"), {"replace": False, "values": ns([
        "brimstone", "brimstone_bricks", "scorched_netherrack", "scorched_bricks", "ember_crystal", "hellfire_brazier", "soul_brazier",
        "nether_geyser", "pearl_block", "abyssal_enchanting_table"])})
    _json(os.path.join(tags, "mineable", "shovel.json"), {"replace": False, "values": ns(["ash_block"])})
    _json(os.path.join(tags, "mineable", "hoe.json"), {"replace": False, "values": ns(["flesh_block"])})
    _json(os.path.join(tags, "mineable", "axe.json"), {"replace": False, "values": ns(["charred_log"])})
    for world in ("overworld", "nether", "end"):
        _json(os.path.join(tags, f"infiniburn_{world}.json"), {"replace": False, "values": ns(["brimstone"])})
    _json(os.path.join(tags, "base_stone_nether.json"), {"replace": False, "values": ns(["scorched_netherrack", "brimstone"])})


def lang():
    out = {f"block.naturesmod.{block}": name for block, (name, _) in BLOCKS.items()}
    out.update({f"item.naturesmod.{item}": name for item, name in ITEMS.items()})
    return out
