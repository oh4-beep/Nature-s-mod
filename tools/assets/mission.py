"""Assets of the Drowned Expedition, the mission of the Abyss: the four sigils, the Crown of the
Deep, the Abyssal Altar, and the words of the expedition's logbook."""

import json
import math
import os

from PIL import Image

CLEAR = (0, 0, 0, 0)

SIGILS = {
    # id: (name, stone colour, rune colour, rune shape)
    "tide_sigil": ("Sigil of the Tide", (40, 78, 92), (90, 240, 255), "wave"),
    "deep_sigil": ("Sigil of the Deep", (20, 28, 60), (110, 130, 255), "spiral"),
    "bone_sigil": ("Sigil of Bone", (70, 66, 58), (245, 238, 214), "skull"),
    "ember_sigil": ("Sigil of the Ember", (52, 22, 16), (255, 140, 40), "flame"),
}

LANG = {
    "item.naturesmod.crown_of_the_deep": "Crown of the Deep",
    "block.naturesmod.abyssal_altar": "Abyssal Altar",
    "filled_map.naturesmod.drowned_sanctum": "Map to the Drowned Sanctum",
    "message.naturesmod.logbook": "A waterlogged logbook drifts down beside you: the log of the Drowned Expedition.",
    "message.naturesmod.altar.sigil": "%s sinks into the altar. (%s/4)",
    "message.naturesmod.altar.taken": "That socket already holds its sigil.",
    "message.naturesmod.altar.status": "The altar holds %s of its four sigils: the Tide, the Deep, the Bone and the Ember.",
    "message.naturesmod.altar.silent": "The altar is silent now. What slept here is awake.",
    "message.naturesmod.altar.awakening": "The four sigils blaze. Far below the altar, something vast begins to stir...",
    "message.naturesmod.altar.risen": "The Abyssal Horror has risen!",
    "book.naturesmod.logbook.page1": "Day 1.\nThe rift swallowed the ship whole and spat us out on this black sea. Captain's orders: dive.\n\nThe old texts were right. There is a bottom down there, a thousand blocks below, and things live on it.\n\n- I. Vael",
    "book.naturesmod.logbook.page2": "Day 9.\nWe have found the first of the seals: the Sigil of the Tide, kept in the courtyard temples on the plains.\n\nThe texts name four of them. The Tide, the Deep, the Bone and the Ember.",
    "book.naturesmod.logbook.page3": "Day 14.\nThe Sigil of the Deep went down with the bathyspheres of those who came before us.\n\nThe Leviathan carries the Sigil of Bone inside it.\n\nThe Sigil of the Ember burns in the shrines among the vents.",
    "book.naturesmod.logbook.page4": "Day 20.\nSet all four into the altar of the Drowned Sanctum, under the bone arches at the bottom of the sea, and what sleeps there wakes.\n\nThe temple chests keep maps to the Sanctum.",
    "book.naturesmod.logbook.page5": "Day 23.\nWe heard it breathing beneath the altar.\n\nWhoever finds this: be ready before you set the last sigil. It guards a crown that the sea itself obeys.\n\nGod help us. It is so close now.",
}


def _json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def rune_mask(shape):
    """Which pixels of a 6x6 rune, centred in the tablet, are lit."""
    lit = set()
    if shape == "wave":
        for x in range(6):
            lit.add((x, 2 + round(math.sin(x * 1.2) * 1.2)))
            lit.add((x, 4 + round(math.sin(x * 1.2 + 1.0) * 0.8)))
    elif shape == "spiral":
        for i in range(30):
            a = i * 0.45
            r = 0.25 + i * 0.09
            x, y = 2.5 + math.cos(a) * r, 2.5 + math.sin(a) * r
            if 0 <= x < 6 and 0 <= y < 6:
                lit.add((int(round(x)), int(round(y))))
    elif shape == "skull":
        rows = [" ####", "######", "#  # #", "######", " #### ", " # # "]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "#":
                    lit.add((x, y))
    elif shape == "flame":
        rows = ["  #   ", "  ##  ", " ###  ", " #### ", "######", " #### "]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "#":
                    lit.add((x, y))
    return lit


def sigil(stone, rune, shape):
    """A round stone tablet carved with a glowing rune."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    c = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - c, y - c)
            if d < 7.2:
                f = 1.05 - d / 18 + (0.08 if (x * 3 + y * 5) % 7 == 0 else 0)
                col = shade(stone, f)
                if 6.0 < d:
                    col = shade(stone, 0.65)
                img.putpixel((x, y), col)
    for (x, y) in rune_mask(shape):
        img.putpixel((5 + x, 5 + y), rune + (255,))
    # A glow halo around the rune.
    for y in range(16):
        for x in range(16):
            p = img.getpixel((x, y))
            if p[3] and p[:3] != rune and math.hypot(x - c, y - c) < 4.8:
                near = any(img.getpixel((x + dx, y + dy))[:3] == rune for dx in (-1, 0, 1) for dy in (-1, 0, 1)
                           if 0 <= x + dx < 16 and 0 <= y + dy < 16)
                if near:
                    img.putpixel((x, y), tuple((p[i] + rune[i]) // 2 for i in range(3)) + (255,))
    return img


def crown():
    """A crown of dark abyssal metal set with pearls and a glowing teal heart-stone."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    metal = (40, 70, 86)
    edge = (110, 200, 210)
    for y in range(8, 13):
        for x in range(2, 14):
            img.putpixel((x, y), shade(metal, 1.0 - (y - 8) * 0.05))
    for x in range(2, 14):
        img.putpixel((x, 8), edge + (255,))
        img.putpixel((x, 12), shade(metal, 0.7))
    # Points of the crown, each tipped with a pearl.
    for px, height in ((2, 4), (5, 3), (7, 1), (10, 3), (13, 4)):
        for y in range(8 - height, 8):
            img.putpixel((px, y), shade(metal, 1.1))
            if px + 1 < 14 and px != 13:
                img.putpixel((px + 1, y), shade(metal, 0.85))
        img.putpixel((px, 8 - height - 1), (240, 236, 244, 255))
    # The heart-stone.
    for (x, y) in ((7, 10), (8, 10), (7, 9), (8, 9)):
        img.putpixel((x, y), (60, 255, 220, 255))
    img.putpixel((7, 9), (200, 255, 245, 255))
    return img


def altar_textures():
    side = Image.new("RGBA", (16, 16), CLEAR)
    top = Image.new("RGBA", (16, 16), CLEAR)
    base = (40, 48, 56)
    rune = (60, 200, 220)
    for y in range(16):
        for x in range(16):
            n = ((x * 7 + y * 13) % 5) * 0.03
            side.putpixel((x, y), shade(base, 0.9 + n - (0.15 if y in (0, 15) else 0)))
            top.putpixel((x, y), shade(base, 1.0 + n))
    for y in range(4, 12):
        side.putpixel((7, y), rune + (255,))
        side.putpixel((8, y), shade(rune, 0.7))
    for x in range(4, 12):
        side.putpixel((x, 6), shade(rune, 0.8))
    c = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - c, y - c)
            if abs(d - 5.2) < 0.55 or abs(d - 2.2) < 0.5:
                top.putpixel((x, y), rune + (255,))
    return side, top


def write(assets):
    items_tex = os.path.join(assets, "textures", "item")
    block_tex = os.path.join(assets, "textures", "block")
    os.makedirs(items_tex, exist_ok=True)
    for sid, (_, stone, rune, shape) in SIGILS.items():
        sigil(stone, rune, shape).save(os.path.join(items_tex, sid + ".png"))
        _json(os.path.join(assets, "models", "item", sid + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"naturesmod:item/{sid}"}})
        _json(os.path.join(assets, "items", sid + ".json"), {"model": {"type": "minecraft:model", "model": f"naturesmod:item/{sid}"}})
    crown().save(os.path.join(items_tex, "crown_of_the_deep.png"))
    _json(os.path.join(assets, "models", "item", "crown_of_the_deep.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": "naturesmod:item/crown_of_the_deep"}})
    _json(os.path.join(assets, "items", "crown_of_the_deep.json"),
          {"model": {"type": "minecraft:model", "model": "naturesmod:item/crown_of_the_deep"}})

    side, top = altar_textures()
    side.save(os.path.join(block_tex, "abyssal_altar_side.png"))
    top.save(os.path.join(block_tex, "abyssal_altar_top.png"))
    faces = lambda: {f: {"texture": "#top" if f in ("up", "down") else "#side"} for f in ("north", "south", "east", "west", "up", "down")}
    _json(os.path.join(assets, "models", "block", "abyssal_altar.json"), {
        "textures": {"particle": "naturesmod:block/abyssal_altar_side", "side": "naturesmod:block/abyssal_altar_side",
                     "top": "naturesmod:block/abyssal_altar_top"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 4, 16], "faces": faces()},
            {"from": [3, 4, 3], "to": [13, 10, 13], "faces": faces()},
            {"from": [1, 10, 1], "to": [15, 13, 15], "faces": faces()},
        ],
    })
    corners = {"tide": (1, 1), "deep": (11, 1), "bone": (1, 11), "ember": (11, 11)}
    multipart = [{"apply": {"model": "naturesmod:block/abyssal_altar"}}]
    for socket, (x, z) in corners.items():
        sid = f"{socket}_sigil"
        model = f"naturesmod:block/abyssal_altar_{socket}"
        _json(os.path.join(assets, "models", "block", f"abyssal_altar_{socket}.json"), {
            "textures": {"particle": f"naturesmod:item/{sid}", "gem": f"naturesmod:item/{sid}"},
            "elements": [{"from": [x, 13, z], "to": [x + 4, 16, z + 4], "shade": False,
                          "faces": {f: {"uv": [6, 6, 10, 10], "texture": "#gem"} for f in ("north", "south", "east", "west", "up")}}],
        })
        multipart.append({"when": {socket: "true"}, "apply": {"model": model}})
    _json(os.path.join(assets, "blockstates", "abyssal_altar.json"), {"multipart": multipart})
    _json(os.path.join(assets, "items", "abyssal_altar.json"), {"model": {"type": "minecraft:model", "model": "naturesmod:block/abyssal_altar"}})


def lang():
    out = dict(LANG)
    out.update({f"item.naturesmod.{sid}": name for sid, (name, _, _, _) in SIGILS.items()})
    return out
