"""Textures, models and block states of the sea plants and decorations: anemones, sea fans, tube
worms, abyssal glowmoss, starfish, seashells, sea urchins, giant clams and pearls.

Everything is painted procedurally on 16x16 canvases so the plants match vanilla's pixel style.
"""

import json
import math
import os
import random

from PIL import Image

CLEAR = (0, 0, 0, 0)

# Block id -> (display name, model kind). Kinds: cross (a plant seen from every side), flat (lies
# on the sea floor), urchin, clam.
BLOCKS = {
    "red_sea_anemone": ("Red Sea Anemone", "cross"),
    "purple_sea_anemone": ("Purple Sea Anemone", "cross"),
    "green_sea_anemone": ("Green Sea Anemone", "cross"),
    "purple_sea_fan": ("Purple Sea Fan", "cross"),
    "orange_sea_fan": ("Orange Sea Fan", "cross"),
    "tube_worms": ("Tube Worms", "cross"),
    "abyssal_glowmoss": ("Abyssal Glowmoss", "cross"),
    "orange_starfish": ("Orange Starfish", "flat"),
    "blue_starfish": ("Blue Starfish", "flat"),
    "seashells": ("Seashells", "flat"),
    "sea_urchin": ("Sea Urchin", "urchin"),
    "giant_clam": ("Giant Clam", "clam"),
    "pearl_block": ("Pearl Block", "cube"),
}

ITEMS = {"pearl": "Pearl"}


def canvas():
    return Image.new("RGBA", (16, 16), CLEAR)


def put(img, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((int(x), int(y)), color if len(color) == 4 else color + (255,))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(c[i] * f))) for i in range(3)) + (255,)


# --- cross plants ------------------------------------------------------------------------------

def anemone(base, body, tip, seed):
    """A squat column crowned with waving tentacles, their tips lighter."""
    rng = random.Random(seed)
    img = canvas()
    for y in range(11, 16):
        half = 2 + (1 if y >= 13 else 0)
        for x in range(8 - half, 8 + half):
            put(img, x, y, shade(base, 0.85 + 0.1 * ((x + y) % 2)))
    strands = 9
    for i in range(strands):
        spread = (i - (strands - 1) / 2) / ((strands - 1) / 2)
        length = 7 + rng.randint(0, 3) - int(abs(spread) * 2)
        sway = rng.uniform(-0.6, 0.6)
        x, y = 7.5 + spread * 1.5, 11.0
        for step in range(length):
            t = step / max(1, length - 1)
            x += spread * 0.55 + sway * math.sin(step * 0.9)
            y -= 1.0
            put(img, round(x), round(y), tip if step == length - 1 else mix(body, tip, t * 0.7))
    return img


def sea_fan(color, polyp, seed):
    """A gorgonian: a short stem opening into a broad, flat net of branches."""
    rng = random.Random(seed)
    img = canvas()
    stem = shade(color, 0.6)
    for y in range(13, 16):
        put(img, 7, y, stem)
        put(img, 8, y, shade(stem, 0.85))
    # The fan: an oval of netting, denser near the rim, with branches running out from the stem.
    cx, cy, rx, ry = 7.5, 7.0, 7.2, 6.4
    for y in range(16):
        for x in range(16):
            d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
            if d > 1.0 or y > 13:
                continue
            branch = abs(math.sin(math.atan2(y - 13, x - cx) * 6.0)) < 0.28
            mesh = (x + y) % 3 == 0 or (x - y) % 3 == 0
            if branch or (mesh and rng.random() < 0.8) or d > 0.82:
                put(img, x, y, shade(color, 0.78 + 0.3 * rng.random()))
    for _ in range(16):
        x, y = rng.randrange(1, 15), rng.randrange(1, 12)
        if img.getpixel((x, y))[3]:
            put(img, x, y, polyp)
    return img


def tube_worms(seed):
    """White tubes standing in a cluster, each crowned with a blood-red plume."""
    rng = random.Random(seed)
    img = canvas()
    tube = (226, 222, 210)
    plume = (196, 24, 32)
    for x0, top in ((2, 6), (5, 3), (8, 5), (11, 2), (13, 7)):
        for y in range(top + 2, 16):
            put(img, x0, y, shade(tube, 0.95 + 0.05 * (y % 3 == 0)))
            put(img, x0 + 1, y, shade(tube, 0.78))
        for dx in (-1, 0, 1, 2):
            for dy in (0, 1):
                if rng.random() < 0.85:
                    put(img, x0 + dx, top + dy, shade(plume, 0.8 + 0.4 * rng.random()))
        put(img, x0, top - 1, shade(plume, 1.15))
    return img


def glowmoss(seed):
    """Fine bioluminescent strands rising from the rock, flecked with cold light."""
    rng = random.Random(seed)
    img = canvas()
    dark = (18, 70, 80)
    glow = (120, 255, 230)
    for i in range(12):
        x = 1 + i * 1.2 + rng.uniform(-0.4, 0.4)
        h = rng.randint(5, 12)
        for k in range(h):
            y = 15 - k
            xx = x + math.sin(k * 0.7 + i) * 0.6
            put(img, round(xx), y, mix(dark, glow, k / h))
        put(img, round(x), 15 - h, (230, 255, 250, 255))
    for _ in range(8):
        x, y = rng.randrange(16), rng.randrange(4, 15)
        if img.getpixel((x, y))[3]:
            put(img, x, y, glow)
    return img


# --- flat decorations ----------------------------------------------------------------------------

def starfish(color, dots, seed):
    """Top view: a five-armed star with a lighter heart and darker dimples."""
    rng = random.Random(seed)
    img = canvas()
    turn = rng.uniform(0, math.pi * 2)
    c = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx) - turn
            # Arm radius: wide at the centre, tapering to a point at r ~ 7.
            arm = abs(math.cos(a * 2.5))
            limit = 2.2 + 5.0 * arm ** 3
            if r <= limit:
                t = r / 7.5
                col = mix(shade(color, 1.15), shade(color, 0.8), t)
                if (x * 7 + y * 3) % 5 == 0 and r > 1.5:
                    col = dots + (255,)
                put(img, x, y, col)
    put(img, 7, 7, shade(color, 1.3))
    put(img, 8, 8, shade(color, 1.3))
    return img


def seashells(seed):
    """A scallop, a conch and a cowrie washed together on the sand."""
    rng = random.Random(seed)
    img = canvas()
    # Scallop: a ribbed fan.
    cx, cy = 4.5, 5.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            if r < 3.8 and -2.6 < a < -0.5:
                rib = (int((a + 3) * 6) % 2 == 0)
                put(img, x, y, (236, 170, 150, 255) if rib else (214, 140, 124, 255))
    put(img, 4, 6, (190, 120, 100, 255))
    # Conch: a cream spiral with brown flecks.
    cx, cy = 11.0, 10.0
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            r = math.hypot(dx, dy)
            if r < 3.2:
                a = math.atan2(dy, dx)
                spiral = math.sin(a * 1.0 + r * 2.2)
                put(img, x, y, (240, 226, 196, 255) if spiral > -0.3 else (176, 120, 72, 255))
    put(img, 13, 12, (250, 210, 190, 255))
    put(img, 14, 13, (250, 210, 190, 255))
    # Cowrie: a small glossy oval.
    for y in range(11, 15):
        for x in range(2, 6):
            if ((x - 3.5) / 2.0) ** 2 + ((y - 12.5) / 1.6) ** 2 <= 1.0:
                put(img, x, y, (250, 246, 236, 255) if x != 3 else (170, 140, 110, 255))
    return img


# --- urchin, clam and pearls ---------------------------------------------------------------------------

def urchin_body():
    img = canvas()
    for y in range(16):
        for x in range(16):
            v = ((x * 5 + y * 3) % 4)
            base = (54, 20, 64)
            put(img, x, y, shade(base, 0.8 + 0.12 * v))
            if (x + 2 * y) % 5 == 0:
                put(img, x, y, (120, 70, 140, 255))
    return img


def urchin_spikes(seed):
    """Long dark spines radiating out of the body, tipped with violet."""
    rng = random.Random(seed)
    img = canvas()
    c = (7.5, 13.0)
    for i in range(17):
        a = math.radians(-172 + i * 10.7 + rng.uniform(-3, 3))
        length = rng.uniform(7.5, 10.5)
        for k in range(1, int(length)):
            x = c[0] + math.cos(a) * k
            y = c[1] + math.sin(a) * k
            put(img, round(x), round(y), (40, 14, 46, 255) if k < length - 2 else (176, 112, 214, 255))
    return img


def clam_textures():
    side = canvas()
    top = canvas()
    mantle = canvas()
    for y in range(16):
        for x in range(16):
            wave = math.sin(x * 1.6) * 1.2
            ridge = (y + wave) % 3 < 1
            base = (174, 182, 190)
            put(side, x, y, shade(base, 0.82 if ridge else 1.0))
            dx, dy = x - 7.5, y - 15.5
            a = math.atan2(dy, dx)
            rib = int(a * 9) % 2 == 0
            put(top, x, y, shade(base, 0.86 if rib else 1.02))
            m = math.sin(x * 0.9 + y * 0.4) + math.cos(y * 1.1 - x * 0.3)
            col = (30, 120, 200) if m > 0.3 else (20, 170, 170) if m > -0.6 else (90, 60, 200)
            if (x * 3 + y * 7) % 11 == 0:
                col = (170, 240, 255)
            put(mantle, x, y, col)
    return side, top, mantle


def pearl_item():
    img = canvas()
    c = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - c, y - c)
            if d < 5.2:
                light = 1.0 - d / 6.5
                hue = math.sin(x * 0.7 - y * 0.5)
                base = (236, 230, 240) if hue > 0 else (240, 228, 232)
                put(img, x, y, shade(base, 0.72 + 0.35 * light))
    for x, y in ((5, 5), (6, 5), (5, 6)):
        put(img, x, y, (255, 255, 255, 255))
    return img


def pearl_block():
    img = canvas()
    for y in range(16):
        for x in range(16):
            cell = ((x // 4) + (y // 4)) % 2
            d = math.hypot((x % 4) - 1.5, (y % 4) - 1.5)
            sheen = math.sin(x * 0.5 + y * 0.3)
            base = (232, 226, 236) if sheen > 0 else (236, 228, 226)
            col = shade(base, (0.98 if cell else 0.92) - d * 0.04)
            if (x % 4 == 0) or (y % 4 == 0):
                col = shade(base, 0.8)
            put(img, x, y, col)
    return img


# --- writing ---------------------------------------------------------------------------------------

def _json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def _rotations(model):
    return [{"model": model, "y": y} if y else {"model": model} for y in (0, 90, 180, 270)]


def write(assets, data_root):
    tex = os.path.join(assets, "textures", "block")
    os.makedirs(tex, exist_ok=True)
    images = {
        "red_sea_anemone": anemone((120, 18, 34), (210, 50, 72), (255, 180, 186), 11),
        "purple_sea_anemone": anemone((70, 26, 100), (160, 72, 210), (240, 196, 255), 12),
        "green_sea_anemone": anemone((24, 80, 46), (80, 190, 104), (214, 255, 196), 13),
        "purple_sea_fan": sea_fan((150, 58, 170), (230, 170, 240, 255), 21),
        "orange_sea_fan": sea_fan((226, 118, 40), (255, 214, 140, 255), 22),
        "tube_worms": tube_worms(31),
        "abyssal_glowmoss": glowmoss(41),
        "orange_starfish": starfish((226, 108, 40), (150, 60, 24), 51),
        "blue_starfish": starfish((44, 96, 206), (150, 190, 255), 52),
        "seashells": seashells(61),
        "sea_urchin": urchin_body(),
        "sea_urchin_spikes": urchin_spikes(71),
        "pearl_block": pearl_block(),
    }
    side, top, mantle = clam_textures()
    images.update({"giant_clam_side": side, "giant_clam_top": top, "giant_clam_mantle": mantle})
    for name, img in images.items():
        img.save(os.path.join(tex, name + ".png"))
    item_tex = os.path.join(assets, "textures", "item")
    os.makedirs(item_tex, exist_ok=True)
    pearl_item().save(os.path.join(item_tex, "pearl.png"))

    models = os.path.join(assets, "models")
    for block, (_, kind) in BLOCKS.items():
        model = f"naturesmod:block/{block}"
        if kind == "cross":
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cross", "textures": {"cross": model}})
            _json(os.path.join(assets, "blockstates", block + ".json"), {"variants": {"": {"model": model}}})
            item_model = {"parent": "minecraft:item/generated", "textures": {"layer0": model}}
        elif kind == "flat":
            _json(os.path.join(models, "block", block + ".json"), {
                "ambientocclusion": False,
                "textures": {"particle": model, "texture": model},
                "elements": [{"from": [0, 0.25, 0], "to": [16, 0.25, 16], "shade": False, "faces": {
                    "up": {"uv": [0, 0, 16, 16], "texture": "#texture"},
                    "down": {"uv": [0, 16, 16, 0], "texture": "#texture"}}}],
            })
            _json(os.path.join(assets, "blockstates", block + ".json"), {"variants": {"": _rotations(model)}})
            item_model = {"parent": "minecraft:item/generated", "textures": {"layer0": model}}
        elif kind == "urchin":
            _json(os.path.join(models, "block", block + ".json"), {
                "ambientocclusion": False,
                "textures": {"particle": model, "body": model, "spikes": f"naturesmod:block/{block}_spikes"},
                "elements": [
                    {"from": [5, 0, 5], "to": [11, 5, 11], "faces": {f: {"uv": [5, 5, 11, 10] if f not in ("up", "down") else [5, 5, 11, 11],
                                                                          "texture": "#body"} for f in ("north", "south", "east", "west", "up", "down")}},
                    {"from": [0.8, 0, 8], "to": [15.2, 12, 8], "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
                     "faces": {"north": {"uv": [0, 4, 16, 16], "texture": "#spikes"}, "south": {"uv": [0, 4, 16, 16], "texture": "#spikes"}}},
                    {"from": [8, 0, 0.8], "to": [8, 12, 15.2], "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
                     "faces": {"west": {"uv": [0, 4, 16, 16], "texture": "#spikes"}, "east": {"uv": [0, 4, 16, 16], "texture": "#spikes"}}},
                ],
            })
            _json(os.path.join(assets, "blockstates", block + ".json"), {"variants": {"": _rotations(model)}})
            item_model = {"parent": model}
        elif kind == "clam":
            shell = lambda frm, to, faces: {"from": frm, "to": to, "faces": faces}
            all_faces = lambda top_tex: {f: {"texture": "#top" if f == "up" and top_tex else "#side"} for f in
                                         ("north", "south", "east", "west", "up", "down")}
            closed = {
                "textures": {"particle": f"naturesmod:block/{block}_side", "side": f"naturesmod:block/{block}_side",
                             "top": f"naturesmod:block/{block}_top", "mantle": f"naturesmod:block/{block}_mantle",
                             "pearl": "naturesmod:item/pearl"},
                "elements": [shell([1, 0, 2], [15, 5, 14], all_faces(False)), shell([1, 5, 2], [15, 8, 14], all_faces(True))],
            }
            opened = json.loads(json.dumps(closed))
            opened["elements"] = [
                shell([1, 0, 2], [15, 5, 14], all_faces(False)),
                {"from": [2, 5.01, 3], "to": [14, 5.01, 13], "faces": {"up": {"uv": [2, 3, 14, 13], "texture": "#mantle"}}},
                {"from": [7, 5, 7], "to": [9, 7, 9], "faces": {f: {"uv": [5, 5, 11, 11], "texture": "#pearl"} for f in
                                                               ("north", "south", "east", "west", "up")}},
                {"from": [1, 5, 2], "to": [15, 8, 14], "rotation": {"origin": [8, 5, 14], "axis": "x", "angle": 45},
                 "faces": all_faces(True)},
            ]
            _json(os.path.join(models, "block", block + ".json"), closed)
            _json(os.path.join(models, "block", block + "_open.json"), opened)
            variants = {}
            for pearl in ("false", "true"):
                for wet in ("false", "true"):
                    variants[f"has_pearl={pearl},waterlogged={wet}"] = _rotations(model + ("_open" if pearl == "true" else ""))
            _json(os.path.join(assets, "blockstates", block + ".json"), {"variants": variants})
            item_model = {"parent": model}
        else:
            _json(os.path.join(models, "block", block + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": model}})
            _json(os.path.join(assets, "blockstates", block + ".json"), {"variants": {"": {"model": model}}})
            item_model = {"parent": model}
        if item_model.get("parent") == "minecraft:item/generated":
            _json(os.path.join(models, "item", block + ".json"), item_model)
            _json(os.path.join(assets, "items", block + ".json"), {"model": {"type": "minecraft:model", "model": f"naturesmod:item/{block}"}})
        else:
            _json(os.path.join(assets, "items", block + ".json"), {"model": {"type": "minecraft:model", "model": model}})
    _json(os.path.join(models, "item", "pearl.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "naturesmod:item/pearl"}})
    _json(os.path.join(assets, "items", "pearl.json"), {"model": {"type": "minecraft:model", "model": "naturesmod:item/pearl"}})

    # Loot: every plant drops itself; a clam also gives up its pearl.
    for block in BLOCKS:
        pools = [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"naturesmod:{block}"}],
                  "condition": {"type": "minecraft:survives_explosion"}}]
        if block == "giant_clam":
            pools.append({"rolls": 1, "entries": [{"type": "minecraft:item", "name": "naturesmod:pearl"}], "condition": {
                "type": "minecraft:match_block", "blocks": "naturesmod:giant_clam", "state": {"has_pearl": "true"}}})
        _json(os.path.join(data_root, "naturesmod", "loot_table", "blocks", block + ".json"), {"type": "minecraft:block", "pools": pools})
    # Nine pearls make a block of pearl and back.
    recipes = os.path.join(data_root, "naturesmod", "recipe")
    _json(os.path.join(recipes, "pearl_block.json"), {
        "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###", "###", "###"],
        "key": {"#": "naturesmod:pearl"}, "result": {"count": 1, "id": "naturesmod:pearl_block"}})
    _json(os.path.join(recipes, "pearl_from_block.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ["naturesmod:pearl_block"],
        "result": {"count": 9, "id": "naturesmod:pearl"}})


def lang():
    out = {f"block.naturesmod.{block}": name for block, (name, _) in BLOCKS.items()}
    out.update({f"item.naturesmod.{item}": name for item, name in ITEMS.items()})
    return out
