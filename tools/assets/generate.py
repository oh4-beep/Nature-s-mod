#!/usr/bin/env python3
"""Generates block assets and language entries for Nature's Mod's places: the Abyssal Rift
block (an animated swirling vortex) and the names and messages of the Abyss.

Usage: python3 tools/assets/generate.py
"""

import json
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "worldgen"))
import advancements  # noqa: E402

from PIL import Image

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "naturesmod")

LANG = {
    "block.naturesmod.abyssal_rift": "Abyssal Rift",
    "biome.naturesmod.twilight_waters": "Twilight Waters",
    "biome.naturesmod.midnight_zone": "Midnight Zone",
    "biome.naturesmod.abyssal_plains": "Abyssal Plains",
    "biome.naturesmod.glowing_reef": "Glowing Reef",
    "biome.naturesmod.hydrothermal_vents": "Hydrothermal Vents",
    "biome.naturesmod.bone_fields": "Bone Fields",
    "death.attack.naturesmod.pressure": "%1$s was crushed by the weight of the deep",
    "death.attack.naturesmod.pressure.player": "%1$s was crushed by the weight of the deep while fleeing %2$s",
    "message.naturesmod.pressure.deep": "The water presses in on you. You need a full Tidewalker suit or stronger to dive this deep.",
    "message.naturesmod.pressure.crush": "The crushing pressure of the abyss! Only a full Abyssal suit or a conduit's power can withstand it.",
    "message.naturesmod.pressure.overlay": "The pressure is crushing you!",
    "filled_map.naturesmod.abyssal_rift": "Map to an Abyssal Rift",
    "block.naturesmod.nether_geyser": "Nether Geyser",
    "biome.naturesmod.ember_wastes": "Ember Wastes",
    "biome.naturesmod.glowstone_grotto": "Glowstone Grotto",
    "biome.naturesmod.obsidian_spires": "Obsidian Spires",
}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def rift_texture(frames=32, size=16):
    """A whirlpool of deep blue light spiralling into a black eye."""
    sheet = Image.new("RGBA", (size, size * frames))
    for f in range(frames):
        turn = 2 * math.pi * f / frames
        for y in range(size):
            for x in range(size):
                dx = (x + 0.5 - size / 2) / (size / 2)
                dy = (y + 0.5 - size / 2) / (size / 2)
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                # Two spiral arms winding inwards, turning with the frames.
                arm = 0.5 + 0.5 * math.cos(2 * (a + turn) + 6.0 * r)
                glow = arm * (1.0 - min(1.0, r) * 0.55) + 0.25 * math.exp(-((r - 0.55) ** 2) * 30)
                eye = max(0.0, 1.0 - r * 3.2)
                red = int(10 + 30 * glow)
                green = int(40 + 150 * glow)
                blue = int(90 + 165 * glow)
                red, green, blue = (int(c * (1 - eye)) for c in (red, green, blue))
                alpha = int(150 + 90 * glow + 15 * eye)
                sheet.putpixel((x, y + f * size), (red, green, blue, min(255, alpha)))
    return sheet


def geyser_textures(size=16):
    """Cracked dark basalt; the top has a glowing vent, the sides glowing seams."""
    import random
    rng = random.Random(1907)
    def rock(x, y):
        n = rng.random()
        base = 38 + int(22 * n)
        return (base, base - 4, base - 2, 255)
    top = Image.new("RGBA", (size, size))
    side = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            top.putpixel((x, y), rock(x, y))
            side.putpixel((x, y), rock(x, y))
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            # The vent: a hot core fading out through orange into the rock.
            if d < 2.2:
                top.putpixel((x, y), (255, 230 - int(40 * d), 120 - int(40 * d), 255))
            elif d < 3.6:
                t = (d - 2.2) / 1.4
                top.putpixel((x, y), (int(240 - 140 * t), int(110 - 80 * t), int(20), 255))
            # Radial cracks glowing ember-red.
            angle = math.atan2(y - c, x - c)
            if 3.6 <= d < 7.5 and abs(math.sin(angle * 2.5 + 0.4)) < 0.12:
                top.putpixel((x, y), (200, 60, 10, 255))
    for x in range(size):
        # Two wandering seams of magma up the sides.
        for seam in (4, 11):
            y = int(seam + 1.6 * math.sin(x * 0.7 + seam))
            if 0 <= y < size:
                side.putpixel((x, y), (215, 80, 15, 255))
    return top, side


def main():
    rift = rift_texture()
    path = os.path.join(ASSETS, "textures", "block", "abyssal_rift.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    rift.save(path)
    write_json(path + ".mcmeta", {"animation": {"frametime": 2, "interpolate": True}})
    write_json(os.path.join(ASSETS, "blockstates", "abyssal_rift.json"), {"variants": {"": {"model": "naturesmod:block/abyssal_rift"}}})
    write_json(os.path.join(ASSETS, "models", "block", "abyssal_rift.json"), {
        "textures": {"particle": "naturesmod:block/abyssal_rift", "rift": "naturesmod:block/abyssal_rift"},
        "elements": [
            {"from": [0, 6, 0], "to": [16, 6, 16], "shade": False,
             "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#rift"}, "down": {"uv": [0, 0, 16, 16], "texture": "#rift"}}},
            {"from": [0, 10, 0], "to": [16, 10, 16], "shade": False,
             "faces": {"up": {"uv": [16, 16, 0, 0], "texture": "#rift"}, "down": {"uv": [16, 16, 0, 0], "texture": "#rift"}}},
        ],
    })

    top, side = geyser_textures()
    top.save(os.path.join(ASSETS, "textures", "block", "nether_geyser_top.png"))
    side.save(os.path.join(ASSETS, "textures", "block", "nether_geyser_side.png"))
    write_json(os.path.join(ASSETS, "blockstates", "nether_geyser.json"), {"variants": {"": {"model": "naturesmod:block/nether_geyser"}}})
    write_json(os.path.join(ASSETS, "models", "block", "nether_geyser.json"), {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"top": "naturesmod:block/nether_geyser_top", "side": "naturesmod:block/nether_geyser_side",
                     "bottom": "minecraft:block/basalt_top"},
    })
    write_json(os.path.join(ASSETS, "items", "nether_geyser.json"), {"model": {"type": "minecraft:model", "model": "naturesmod:block/nether_geyser"}})

    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(lang_path, encoding="utf-8") as fh:
        lang = json.load(fh)
    lang.update(LANG)
    lang.update(advancements.lang())
    write_json(lang_path, dict(sorted(lang.items())))
    print(f"wrote rift assets and {len(LANG)} language entries")


if __name__ == "__main__":
    main()
