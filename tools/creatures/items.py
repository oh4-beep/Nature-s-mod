"""Item textures, models, recipes and tags for the ocean overhaul (drawn procedurally)."""

import json
import math
import os

from PIL import Image

from textures import mix, rgb, shade


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(json.dumps(data, indent=2) + "\n")


def item_model(assets, name, texture=None, parent="minecraft:item/generated"):
    write_json(os.path.join(assets, "items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"naturesmod:item/{name}"}})
    write_json(os.path.join(assets, "models", "item", f"{name}.json"),
               {"parent": parent, "textures": {"layer0": f"naturesmod:item/{texture or name}"}})


def save(img, assets, name):
    path = os.path.join(assets, "textures", "item", f"{name}.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def canvas(n=16):
    return Image.new("RGBA", (n, n), (0, 0, 0, 0))


def outline(img, colour=(30, 30, 30)):
    """Adds a dark 1px outline around the opaque shape."""
    out = img.copy()
    w, h = img.size
    for x in range(w):
        for y in range(h):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and img.getpixel((nx, ny))[3] > 0:
                        out.putpixel((x, y), colour + (255,))
                        break
    return out


# --- creature items --------------------------------------------------------------------------------

def spawn_egg(sp):
    col = {k: rgb(v) for k, v in sp["colors"].items()}
    img = canvas()
    cx, cy, rx, ry = 7.5, 8.5, 5.2, 6.6
    for x in range(16):
        for y in range(16):
            dx, dy = (x - cx) / rx, (y - cy) / ry
            if dy < 0:
                dx *= 1.0 + 0.25 * (-dy)
            if dx * dx + dy * dy <= 1.0:
                t = (y - (cy - ry)) / (2 * ry)
                c = mix(col["back"], col["side"], t * 2) if t < 0.5 else mix(col["side"], col["belly"], (t - 0.5) * 2)
                if ((x * 7 + y * 13) % 11 == 0) or ((x + 2 * y) % 9 == 0 and t < 0.6):
                    c = col["accent"]
                light = 1.15 if dx < -0.3 and dy < -0.2 else 0.85 if dx > 0.5 else 1.0
                img.putpixel((x, y), shade(c, light) + (255,))
    img.putpixel((5, 7), col["eye"] + (255,))
    img.putpixel((5, 6), (240, 240, 240, 255))
    return outline(img, shade(col["back"], 0.4))


def bucket(sp):
    col = {k: rgb(v) for k, v in sp["colors"].items()}
    img = canvas()
    metal, dark, light = (150, 150, 158), (90, 90, 98), (205, 205, 212)
    for y in range(6, 16):
        half = 5 - (y - 6) * 0.25
        for x in range(16):
            if abs(x - 7.5) <= half:
                c = metal if abs(x - 7.5) < half - 1 else dark
                if x == int(7.5 - half + 2):
                    c = light
                img.putpixel((x, y), c + (255,))
    for x in range(3, 13):
        img.putpixel((x, 6), (60, 90, 200, 255))
        img.putpixel((x, 5), light + (255,)) if x in (3, 12) else None
    # A little fish poking out of the water.
    fish = [(5, 4), (6, 4), (7, 4), (8, 4), (6, 3), (7, 3), (8, 3), (9, 3), (10, 2), (10, 4), (9, 4)]
    for i, (x, y) in enumerate(fish):
        c = col["side"] if y >= 4 else col["back"]
        img.putpixel((x, y), c + (255,))
    img.putpixel((6, 3), col["eye"] + (255,))
    img.putpixel((11, 2), col["fin"] + (255,))
    img.putpixel((11, 4), col["fin"] + (255,))
    for x in (2, 13):
        for y in range(1, 6):
            if (x == 2 and y < 6) or (x == 13 and y < 6):
                pass
    for i in range(9):
        x = 3 + i
        y = 1 + abs(i - 4) // 2
        img.putpixel((x, y), dark + (255,))
    return outline(img, (40, 40, 44))


def creature_items(sp, assets):
    save(spawn_egg(sp), assets, f"{sp['id']}_spawn_egg")
    item_model(assets, f"{sp['id']}_spawn_egg")
    if sp["bucket"]:
        save(bucket(sp), assets, f"{sp['id']}_bucket")
        item_model(assets, f"{sp['id']}_bucket")


# --- other items ------------------------------------------------------------------------------------

def draw_shape(points, fill_fn):
    img = canvas()
    for x in range(16):
        for y in range(16):
            c = fill_fn(x, y)
            if c is not None:
                img.putpixel((x, y), c + (255,))
    return img


def fillet(cooked):
    base = (196, 110, 70) if cooked else (240, 140, 120)
    stripe = (150, 80, 50) if cooked else (255, 210, 200)

    def fill(x, y):
        dx, dy = (x - 7.5) / 6.5, (y - 8.5) / 3.6
        dy -= 0.25 * math.sin(x * 0.45)
        if dx * dx + dy * dy <= 1:
            if (x + y) % 4 == 0:
                return stripe
            return shade(base, 1.1 if y < 7 else 0.9)
        return None
    return outline(draw_shape(None, fill), (90, 40, 30))


def scale_icon(c1, c2, glow=None):
    def fill(x, y):
        dx, dy = (x - 7.5) / 5.5, (y - 7.0) / 6.5
        if dy > 0:
            dx *= 1 + dy * 0.6
        if dx * dx + dy * dy <= 1:
            t = (y - 1) / 14
            c = mix(c1, c2, t)
            if (y - 3) % 3 == 0 and abs(x - 7.5) < 4:
                c = shade(c, 0.85)
            if glow and (dx * dx + dy * dy) > 0.65:
                c = glow
            if x < 6 and y < 6:
                c = shade(c, 1.25)
            return c
        return None
    return outline(draw_shape(None, fill), shade(c1, 0.35))


def tooth():
    def fill(x, y):
        if 2 <= y <= 14:
            half = (14 - y) / 12 * 4.5
            if abs(x - 7.5) <= half + 0.3:
                c = (245, 240, 225)
                if y > 11:
                    c = (220, 200, 160)
                if x < 7:
                    c = shade(c, 1.05)
                else:
                    c = shade(c, 0.9)
                return c
        return None
    return outline(draw_shape(None, fill), (90, 80, 60))


def heart():
    def fill(x, y):
        u, v = (x - 7.5) / 6.5, (y - 6.5) / 6.5
        if (u * u + v * v - 0.6) ** 3 - u * u * v ** 3 * 0.9 <= 0 or (abs(u) < 0.95 and -0.1 < v < 0.9 and abs(u) < 1 - v):
            r = math.hypot(u, v - 0.1)
            if r < 0.3:
                return (120, 240, 255)
            return mix((20, 30, 80), (60, 30, 110), r)
        return None
    return outline(draw_shape(None, fill), (10, 10, 30))


def template():
    def fill(x, y):
        if 2 <= x <= 13 and 1 <= y <= 14:
            c = (52, 56, 66) if (x + y) % 5 else (44, 48, 58)
            if x in (2, 13) or y in (1, 14):
                c = (36, 38, 46)
            if abs(x - 7.5) < 1 and 4 <= y <= 11 or abs(y - 7.5) < 1 and 4 <= x <= 11:
                c = (70, 220, 240)
            return c
        return None
    return draw_shape(None, fill)


ARMOR_SHAPES = {
    "helmet": ["..XXXXXXXXXX....", ".XXXXXXXXXXXX...", ".XXXXXXXXXXXX...", ".XXX......XXX...", ".XX........XX..."],
    "chestplate": ["XXX......XXX....", "XXXX....XXXX....", "XXXXXXXXXXXX....", ".XXXXXXXXXX.....", "..XXXXXXXX......",
                   "..XXXXXXXX......", "..XXXXXXXX......", "..XXXXXXXX......", "..XXXXXXXX......"],
    "leggings": ["XXXXXXXXXX......", "XXXXXXXXXX......", "XXXX..XXXX......", "XXX....XXX......", "XXX....XXX......",
                 "XXX....XXX......", "XXX....XXX......", "XXX....XXX......"],
    "boots": ["XXX....XXX......", "XXX....XXX......", "XXXX...XXXX.....", "XXXXX..XXXXX...."],
}
ARMOR_OFFSETS = {"helmet": (2, 5), "chestplate": (2, 3), "leggings": (3, 4), "boots": (2, 7)}


def armor_icon(piece, base, accent, trim):
    img = canvas()
    ox, oy = ARMOR_OFFSETS[piece]
    rows = ARMOR_SHAPES[piece]
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "X":
                c = base
                if j == 0 or (piece == "chestplate" and j == 2):
                    c = trim
                if (i + j) % 4 == 0:
                    c = accent
                if i < 3:
                    c = shade(c, 1.15)
                img.putpixel((ox + i, oy + j), c + (255,))
    if piece == "helmet":
        for i in range(4, 9):
            img.putpixel((ox + i, oy + 3), (120, 200, 230, 255))
    return outline(img, shade(base, 0.35))


def armor_layers(base, accent, trim, visor):
    """64x32 humanoid and humanoid_leggings textures for an armor set."""
    def region(img, x0, y0, w, h, d, kind):
        # Box net at (x0, y0) with size w x h x d.
        faces = {
            "top": (x0 + d, y0, w, d), "bottom": (x0 + d + w, y0, w, d),
            "west": (x0, y0 + d, d, h), "front": (x0 + d, y0 + d, w, h),
            "east": (x0 + d + w, y0 + d, d, h), "back": (x0 + 2 * d + w, y0 + d, w, h),
        }
        for face, (fx, fy, fw, fh) in faces.items():
            for i in range(fw):
                for j in range(fh):
                    c = base
                    if (i + 2 * j) % 5 == 0:
                        c = accent
                    if i == 0 or j == 0 or i == fw - 1 or j == fh - 1:
                        c = shade(trim, 0.85)
                    if kind == "head" and face == "front" and 2 <= i <= 5 and 2 <= j <= 5:
                        c = visor
                    if kind == "body" and face == "front" and j in (3, 7):
                        c = trim
                    if kind in ("leg", "boot") and j == fh - 1:
                        c = shade(trim, 0.7)
                    c = shade(c, 1.12 if face in ("top", "front") else 0.88 if face in ("bottom", "back") else 1.0)
                    img.putpixel((fx + i, fy + j), c + (255,))

    humanoid = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    region(humanoid, 0, 0, 8, 8, 8, "head")
    region(humanoid, 16, 16, 8, 12, 4, "body")
    region(humanoid, 40, 16, 4, 12, 4, "arm")
    region(humanoid, 0, 16, 4, 12, 4, "boot")
    leggings = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    region(leggings, 16, 16, 8, 12, 4, "body")
    region(leggings, 0, 16, 4, 12, 4, "leg")
    return humanoid, leggings


ARMOR_SETS = {
    "tidewalker": {"name": "Tidewalker", "base": (36, 150, 150), "accent": (240, 150, 110), "trim": (200, 160, 70),
                   "visor": (150, 220, 240)},
    "abyssal": {"name": "Abyssal", "base": (22, 30, 58), "accent": (60, 210, 240), "trim": (70, 80, 120),
                "visor": (80, 230, 255)},
}
PIECES = [("helmet", "Helmet", ["XXX", "X X"]), ("chestplate", "Chestplate", ["X X", "XXX", "XXX"]),
          ("leggings", "Leggings", ["XXX", "X X", "X X"]), ("boots", "Boots", ["X X", "X X"])]


def other_items(assets, data):
    lang = {}
    simple = {
        "raw_fish_fillet": ("Raw Fish Fillet", fillet(False)),
        "cooked_fish_fillet": ("Cooked Fish Fillet", fillet(True)),
        "sea_scale": ("Sea Scale", scale_icon((90, 200, 200), (40, 110, 150))),
        "shark_tooth": ("Shark Tooth", tooth()),
        "abyssal_scale": ("Abyssal Scale", scale_icon((30, 40, 80), (14, 18, 40), glow=(70, 220, 255))),
        "abyssal_plate": ("Abyssal Plate", scale_icon((50, 60, 100), (20, 24, 50), glow=(110, 240, 255))),
        "abyssal_heart": ("Abyssal Heart", heart()),
        "abyssal_upgrade_smithing_template": ("Abyssal Upgrade", template()),
    }
    for name, (title, img) in simple.items():
        save(img, assets, name)
        item_model(assets, name)
        lang[f"item.naturesmod.{name}"] = title

    for key, st in ARMOR_SETS.items():
        humanoid, leggings = armor_layers(st["base"], st["accent"], st["trim"], st["visor"])
        for layer, img in (("humanoid", humanoid), ("humanoid_leggings", leggings)):
            path = os.path.join(assets, "textures", "entity", "equipment", layer, f"{key}.png")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            img.save(path)
        write_json(os.path.join(assets, "equipment", f"{key}.json"),
                   {"layers": {"humanoid": [{"texture": f"naturesmod:{key}"}], "humanoid_leggings": [{"texture": f"naturesmod:{key}"}]}})
        for piece, title, pattern in PIECES:
            name = f"{key}_{piece}"
            save(armor_icon(piece, st["base"], st["accent"], st["trim"]), assets, name)
            item_model(assets, name)
            lang[f"item.naturesmod.{name}"] = f"{st['name']} {title}"

    # Recipes.
    recipes = os.path.join(data, "recipe")
    for kind, suffix, time in (("minecraft:smelting", "", 200), ("minecraft:smoking", "_from_smoking", 100),
                               ("minecraft:campfire_cooking", "_from_campfire_cooking", 600)):
        write_json(os.path.join(recipes, f"cooked_fish_fillet{suffix}.json"), {
            "type": kind, "category": "food", "cookingtime": time, "experience": 0.35,
            "ingredient": "naturesmod:raw_fish_fillet", "result": {"id": "naturesmod:cooked_fish_fillet"}})
    for piece, title, pattern in PIECES:
        write_json(os.path.join(recipes, f"tidewalker_{piece}.json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment", "key": {"X": "naturesmod:sea_scale"},
            "pattern": pattern, "result": {"id": f"naturesmod:tidewalker_{piece}"}})
        write_json(os.path.join(recipes, f"abyssal_{piece}_smithing.json"), {
            "type": "minecraft:smithing_transform", "addition": "naturesmod:abyssal_plate",
            "base": f"naturesmod:tidewalker_{piece}", "result": {"id": f"naturesmod:abyssal_{piece}"},
            "template": "naturesmod:abyssal_upgrade_smithing_template"})
    write_json(os.path.join(recipes, "abyssal_plate.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["naturesmod:abyssal_scale", "naturesmod:abyssal_scale", "naturesmod:abyssal_scale",
                        "naturesmod:abyssal_scale", "minecraft:diamond"],
        "result": {"id": "naturesmod:abyssal_plate"}})
    write_json(os.path.join(recipes, "abyssal_upgrade_smithing_template.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"#": "minecraft:diamond", "C": "minecraft:dark_prismarine", "S": "naturesmod:abyssal_upgrade_smithing_template"},
        "pattern": ["#S#", "#C#", "###"], "result": {"count": 2, "id": "naturesmod:abyssal_upgrade_smithing_template"}})

    # Tags.
    write_json(os.path.join(data, "tags", "item", "repairs_tidewalker_armor.json"), {"values": ["naturesmod:sea_scale"]})
    write_json(os.path.join(data, "tags", "item", "repairs_abyssal_armor.json"), {"values": ["naturesmod:abyssal_plate"]})
    mc = os.path.join(os.path.dirname(data), "minecraft", "tags", "item")
    armor = [f"naturesmod:{k}_{p}" for k in ARMOR_SETS for p, _, _ in PIECES]
    write_json(os.path.join(mc, "trimmable_armor.json"), {"replace": False, "values": armor})
    for p, _, _ in PIECES:
        write_json(os.path.join(mc, f"{'head' if p == 'helmet' else 'chest' if p == 'chestplate' else 'leg' if p == 'leggings' else 'foot'}_armor.json"),
                   {"replace": False, "values": [f"naturesmod:{k}_{p}" for k in ARMOR_SETS]})
    write_json(os.path.join(mc, "fishes.json"), {"replace": False, "values": ["naturesmod:raw_fish_fillet", "naturesmod:cooked_fish_fillet"]})
    lang["itemGroup.naturesmod"] = "Nature's Mod"
    lang["item.naturesmod.abyssal_upgrade_smithing_template.applies_to"] = "Tidewalker Equipment"
    lang["item.naturesmod.abyssal_upgrade_smithing_template.ingredients"] = "Abyssal Plate"
    return lang
