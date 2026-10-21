#!/usr/bin/env python3
"""Generates block assets and language entries for Nature's Mod's places: the Abyssal Rift
block (an animated swirling vortex) and the names and messages of the Abyss.

Usage: python3 tools/assets/generate.py
"""

import json
import math
import os

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

    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(lang_path, encoding="utf-8") as fh:
        lang = json.load(fh)
    lang.update(LANG)
    write_json(lang_path, dict(sorted(lang.items())))
    print(f"wrote rift assets and {len(LANG)} language entries")


if __name__ == "__main__":
    main()
