"""Assets of the treasures at the bottom of the Abyss: the eight lost weapons and the Abyssal
Enchanting Table, with their names, tooltips, loot, recipes and tags.

The weapons are painted on 16x16 canvases the way vanilla draws its swords: the grip at the
bottom left, the business end at the top right.
"""

import json
import math
import os

from PIL import Image

CLEAR = (0, 0, 0, 0)

WEAPONS = {
    # id: (name, strike, power)
    "tidecleaver": ("Tidecleaver", "Strike: cleaves through to the foes behind the target",
                    "Use: Tidal Wave - hurls back and hurts everything within 7 blocks"),
    "leviathan_fang": ("Leviathan Fang", "Strike: makes the target bleed (wither) and heals you",
                       "Use: Shadowstep - step through the dark to behind the creature you look at"),
    "abyssal_scythe": ("Abyssal Scythe", "Strike: weakens the target and heals you",
                       "Use: Reap - cuts everything within 5 blocks and feeds on it"),
    "stormcaller": ("Stormcaller", "Strike: may call down lightning on the target",
                    "Use: Call the Storm - strikes the creature you look at with lightning"),
    "krakens_grasp": ("Kraken's Grasp", "Strike: entangles the target (slowness)",
                      "Use: Grasp - drags the creature you look at to you"),
    "pressure_maul": ("Pressure Maul", "Strike: a shockwave hurls back everything around the target",
                      "Use: Crushing Slam - a shockwave all around you"),
    "sovereign_staff": ("Sovereign's Staff", "Strike: a weak blade, but a mighty staff",
                        "Use: Jaws of the Deep - a line of snapping fangs ahead of you"),
    "coral_rapier": ("Coral Rapier", "Strike: poisons the target",
                     "Use: Lunge - dash the way you look"),
}

LANG = {
    "block.naturesmod.abyssal_enchanting_table": "Abyssal Enchanting Table",
    "item.naturesmod.empowered": "Empowered by the Abyss",
    "message.naturesmod.empower.nothing": "Only enchanted items can be empowered.",
    "message.naturesmod.empower.already": "The Abyss has already empowered this item. It only does so once.",
    "message.naturesmod.empower.done": "The Abyss doubles every enchantment on your item!",
    "message.naturesmod.empower.hint": "Use an enchanted tool, weapon or piece of armor on the table to double its enchantments (once per item).",
    "message.naturesmod.guardian.hunting": "%s is hunting you!",
    "message.naturesmod.guardian.slain": "%s has fallen! The way down is clear... for now.",
}


def canvas():
    return Image.new("RGBA", (16, 16), CLEAR)


def put(img, x, y, color):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(color[:3]) + (255,))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def diagonal(img, start, end, color, width=1, light=None, dark=None):
    """A diagonal bar from start to end (bottom left to top right), optionally lit on one side."""
    (x0, y0), (x1, y1) = start, end
    steps = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(steps + 1):
        x = x0 + (x1 - x0) * i / max(1, steps)
        y = y0 + (y1 - y0) * i / max(1, steps)
        for w in range(width):
            put(img, x + w * 0.5, y + w * 0.5, color)
        if light:
            put(img, x - 1, y, light)
        if dark:
            put(img, x + width * 0.5 + 0.5, y + width * 0.5, dark)


def grip(img, gold=(222, 178, 60), wrap=(54, 34, 30)):
    diagonal(img, (1, 14), (4, 11), wrap, 1)
    put(img, 1, 14, gold)
    put(img, 0, 15, shade(gold, 0.7))


def tidecleaver():
    img = canvas()
    steel, edge, spine = (84, 178, 196), (214, 250, 255), (34, 92, 112)
    for i in range(10):
        x, y = 5 + i, 10 - i
        put(img, x, y, steel)
        put(img, x - 1, y, edge)
        put(img, x + 1, y, spine)
        put(img, x, y + 1, spine)
        if i % 3 == 1:
            put(img, x, y, (140, 230, 240))
    for (x, y) in ((3, 9), (4, 10), (5, 11), (6, 12), (2, 8), (7, 13)):
        put(img, x, y, (222, 178, 60))
    grip(img)
    put(img, 14, 0, edge)
    return img


def leviathan_fang():
    img = canvas()
    ivory, shadow, tip = (236, 230, 208), (176, 166, 140), (255, 252, 240)
    pts = [(6, 9), (7, 8), (8, 7), (9, 6), (10, 4), (11, 3), (12, 1)]
    for i, (x, y) in enumerate(pts):
        put(img, x, y, ivory)
        if i < 4:
            put(img, x + 1, y, shadow)
            put(img, x, y + 1, shadow)
    put(img, 12, 0, tip)
    for (x, y) in ((4, 9), (5, 10), (6, 11)):
        put(img, x, y, (30, 30, 36))
    diagonal(img, (1, 14), (5, 10), (22, 22, 28), 1)
    put(img, 2, 13, (60, 210, 230))
    return img


def abyssal_scythe():
    img = canvas()
    diagonal(img, (1, 15), (12, 4), (64, 52, 76), 1, light=(96, 84, 110))
    blade, glow = (46, 28, 66), (182, 96, 255)
    curve = [(12, 3), (11, 2), (10, 1), (9, 1), (8, 1), (7, 1), (6, 2), (5, 2), (4, 3), (3, 4)]
    for i, (x, y) in enumerate(curve):
        put(img, x, y, blade)
        put(img, x, y + 1, blade if i < 7 else glow)
        put(img, x, y - 1, glow)
    put(img, 13, 3, (222, 178, 60))
    put(img, 12, 5, (222, 178, 60))
    return img


def stormcaller():
    img = canvas()
    diagonal(img, (1, 15), (10, 6), (120, 130, 142), 1, light=(180, 190, 200))
    prong, spark = (110, 196, 255), (255, 250, 150)
    for (x, y) in ((10, 5), (11, 4), (12, 3), (13, 2), (14, 1)):
        put(img, x, y, prong)
    for (x, y) in ((9, 4), (9, 3), (9, 2), (10, 1)):
        put(img, x, y, prong)
    for (x, y) in ((11, 6), (12, 6), (13, 6), (14, 5)):
        put(img, x, y, prong)
    for (x, y) in ((10, 0), (15, 0), (15, 5), (12, 1)):
        put(img, x, y, spark)
    put(img, 10, 6, (222, 178, 60))
    return img


def krakens_grasp():
    img = canvas()
    flesh, sucker, dark = (150, 40, 74), (236, 172, 186), (96, 20, 46)
    path = [(3, 12), (4, 11), (5, 11), (6, 10), (7, 10), (8, 9), (8, 8), (9, 7), (10, 7), (11, 6), (11, 5), (12, 4), (13, 3), (13, 2), (12, 1), (11, 1)]
    for i, (x, y) in enumerate(path):
        put(img, x, y, flesh)
        put(img, x, y + 1, dark)
        if i % 3 == 0:
            put(img, x, y, sucker)
    grip(img, gold=(160, 120, 60), wrap=(40, 30, 30))
    return img


def pressure_maul():
    img = canvas()
    diagonal(img, (1, 15), (9, 7), (84, 72, 60), 1, light=(120, 104, 88))
    iron, light, rim, rune = (74, 86, 98), (118, 132, 146), (36, 42, 52), (60, 228, 228)
    # The head lies across the haft: a thick block running from top left to bottom right.
    for t in range(-4, 5):
        for w in range(-1, 2):
            x, y = 11 + t + w, 5 + t - w
            put(img, x, y, rim if abs(t) == 4 or w == 1 else light if w == -1 else iron)
    for t in (-2, 0, 2):
        put(img, 11 + t, 5 + t, rune)
    return img


def sovereign_staff():
    img = canvas()
    diagonal(img, (1, 15), (10, 6), (40, 40, 62), 1, light=(70, 70, 96))
    for (x, y) in ((4, 12), (7, 9), (10, 6)):
        put(img, x, y, (222, 178, 60))
    orb, core, white = (60, 200, 210), (120, 255, 240), (240, 255, 255)
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            if dx * dx + dy * dy <= 5:
                put(img, 12 + dx, 3 + dy, orb if dx * dx + dy * dy > 1 else core)
    put(img, 11, 2, white)
    for (x, y) in ((10, 0), (12, 0), (14, 0), (14, 2), (14, 4)):
        put(img, x, y, (222, 178, 60))
    return img


def coral_rapier():
    img = canvas()
    blade, edge = (255, 124, 104), (255, 236, 228)
    for i in range(11):
        x, y = 5 + i, 10 - i
        put(img, x, y, blade)
        if i % 2 == 0:
            put(img, x - 1, y, edge)
    for (x, y) in ((3, 10), (3, 9), (4, 8), (5, 8), (6, 9), (6, 11), (5, 12), (4, 12)):
        put(img, x, y, (222, 178, 60))
    grip(img, wrap=(110, 40, 40))
    return img


PAINTERS = {
    "tidecleaver": tidecleaver, "leviathan_fang": leviathan_fang, "abyssal_scythe": abyssal_scythe, "stormcaller": stormcaller,
    "krakens_grasp": krakens_grasp, "pressure_maul": pressure_maul, "sovereign_staff": sovereign_staff, "coral_rapier": coral_rapier,
}


def table_textures():
    top, side, bottom = canvas(), canvas(), canvas()
    for y in range(16):
        for x in range(16):
            n = ((x * 7 + y * 13) % 5) * 0.04
            top.putpixel((x, y), shade((30, 58, 70), 0.9 + n) + (255,))
            side.putpixel((x, y), shade((22, 30, 42), 0.9 + n) + (255,))
            bottom.putpixel((x, y), shade((16, 16, 22), 0.9 + n) + (255,))
    c = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - c, y - c)
            if abs(d - 6.3) < 0.5:
                top.putpixel((x, y), (222, 178, 60, 255))
            elif abs(d - 4.2) < 0.45:
                top.putpixel((x, y), (60, 230, 230, 255))
            elif d < 1.5:
                top.putpixel((x, y), (200, 255, 250, 255))
    for x in range(16):
        side.putpixel((x, 0), (222, 178, 60, 255))
        side.putpixel((x, 1), (150, 118, 40, 255))
        if x % 4 == 1:
            for y in range(5, 9):
                side.putpixel((x, y), (60, 230, 230, 255))
        if x % 4 == 3:
            side.putpixel((x, 6), (60, 230, 230, 255))
    return top, side, bottom


def _json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def write(assets, data_root):
    item_tex = os.path.join(assets, "textures", "item")
    block_tex = os.path.join(assets, "textures", "block")
    os.makedirs(item_tex, exist_ok=True)
    for wid, paint in PAINTERS.items():
        paint().save(os.path.join(item_tex, wid + ".png"))
        _json(os.path.join(assets, "models", "item", wid + ".json"),
              {"parent": "minecraft:item/handheld", "textures": {"layer0": f"naturesmod:item/{wid}"}})
        _json(os.path.join(assets, "items", wid + ".json"), {"model": {"type": "minecraft:model", "model": f"naturesmod:item/{wid}"}})

    top, side, bottom = table_textures()
    for name, img in (("top", top), ("side", side), ("bottom", bottom)):
        img.save(os.path.join(block_tex, f"abyssal_enchanting_table_{name}.png"))
    tex = "naturesmod:block/abyssal_enchanting_table_"
    faces = {"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#bottom"}
    elements = [{"from": [0, 0, 0], "to": [16, 12, 16], "faces": {
        f: ({"texture": t, "uv": [0, 4, 16, 16]} if t == "#side" else {"texture": t}) for f, t in faces.items()}}]
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
        elements.append({"from": [x, 12, z], "to": [x + 2, 15, z + 2],
                         "faces": {f: {"texture": "#pillar"} for f in ("north", "south", "east", "west", "up")}})
    elements.append({"from": [6, 13, 6], "to": [10, 17, 10], "shade": False,
                     "faces": {f: {"texture": "#orb"} for f in ("north", "south", "east", "west", "up", "down")}})
    _json(os.path.join(assets, "models", "block", "abyssal_enchanting_table.json"), {
        "textures": {"particle": tex + "side", "top": tex + "top", "side": tex + "side", "bottom": tex + "bottom",
                     "pillar": "minecraft:block/dark_prismarine", "orb": "minecraft:block/sea_lantern"},
        "elements": elements,
    })
    _json(os.path.join(assets, "blockstates", "abyssal_enchanting_table.json"),
          {"variants": {"": {"model": "naturesmod:block/abyssal_enchanting_table"}}})
    _json(os.path.join(assets, "items", "abyssal_enchanting_table.json"),
          {"model": {"type": "minecraft:model", "model": "naturesmod:block/abyssal_enchanting_table"}})

    _json(os.path.join(data_root, "naturesmod", "loot_table", "blocks", "abyssal_enchanting_table.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "naturesmod:abyssal_enchanting_table"}],
                   "condition": {"type": "minecraft:survives_explosion"}}]})
    _json(os.path.join(data_root, "naturesmod", "recipe", "abyssal_enchanting_table.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" H ", "DED", "NNN"],
        "key": {"H": "naturesmod:abyssal_heart", "D": "minecraft:diamond_block", "E": "minecraft:enchanting_table",
                "N": "minecraft:netherite_ingot"},
        "result": {"count": 1, "id": "naturesmod:abyssal_enchanting_table"}})
    _json(os.path.join(data_root, "naturesmod", "tags", "item", "repairs_abyss_weapons.json"),
          {"values": ["naturesmod:abyssal_plate"]})
    # The weapons are swords at heart: they take every sword enchantment.
    _json(os.path.join(data_root, "minecraft", "tags", "item", "swords.json"),
          {"replace": False, "values": [f"naturesmod:{w}" for w in WEAPONS]})


def lang():
    out = dict(LANG)
    for wid, (name, strike, power) in WEAPONS.items():
        out[f"item.naturesmod.{wid}"] = name
        out[f"item.naturesmod.{wid}.strike"] = strike
        out[f"item.naturesmod.{wid}.power"] = power
    return out
