"""Assets of the Void Wyrm's call: the star fragment, the Star Altar and the four weapons of the
stars, with their names, tooltips, recipe, loot and tags. The weapons are the lost weapons of the
Abyss re-forged in starmetal: the same shapes, painted in the colours of the void.
"""

import json
import os

from PIL import Image

WEAPONS = {
    # id: (name, strike, power, the Abyss weapon whose shape it shares)
    "voidblade": ("Voidblade", "Strike: cuts the target loose from the ground (levitation)",
                  "Use: Blink - step through the void to wherever you look, up to 24 blocks", "tidecleaver"),
    "starcaller": ("Starcaller", "Strike: lights up the target; hits flying foes harder",
                   "Use: Starfall - a shower of stars on whatever you look at", "sovereign_staff"),
    "gravity_hammer": ("Gravity Hammer", "Strike: knocks the target up into the air",
                       "Use: Singularity - drags everything within 10 blocks to you, left floating", "pressure_maul"),
    "skyrider_glaive": ("Skyrider Glaive", "Strike: hits far harder when you strike from above",
                        "Use: Skyleap - leap high into the air and glide down", "abyssal_scythe"),
}

LANG = {
    "item.naturesmod.star_fragment": "Star Fragment",
    "block.naturesmod.star_altar": "Star Altar",
    "message.naturesmod.star_altar.fragment": "The fragment blazes into the altar. (%s/4)",
    "message.naturesmod.star_altar.status": "The altar holds %s of its four star fragments.",
    "message.naturesmod.star_altar.not_end": "The stars only answer out in the End.",
    "message.naturesmod.star_altar.waking": "The altar is blazing. Something is coming down out of the dark...",
    "message.naturesmod.star_altar.awakening": "Four stars burn in the altar. Far above, in the void, something vast turns towards you...",
    "message.naturesmod.star_altar.risen": "The Void Wyrm descends!",
    "message.naturesmod.void_wyrm.enraged": "The Void Wyrm screams, and begins to blink across the sky!",
    "message.naturesmod.void_wyrm.slain": "The Void Wyrm has fallen! The stars are quiet again.",
}


def _json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def void_tint(img):
    """Repaints a texture in the void's colours: its light and shade kept, its hue turned to purple."""
    out = img.convert("RGBA").copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            v = (r * 0.3 + g * 0.5 + b * 0.2) / 255.0
            px[x, y] = (int(40 + 200 * v), int(10 + 120 * v ** 1.6), int(70 + 185 * v), a)
    return out


def star_fragment():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    core, glow, edge = (255, 250, 220, 255), (240, 170, 255, 255), (150, 80, 220, 255)
    for i in range(-6, 7):
        d = abs(i)
        c = core if d <= 1 else glow if d <= 4 else edge
        img.putpixel((8 + i, 8), c)
        img.putpixel((8, 8 + i), c)
    for i in range(-3, 4):
        d = abs(i)
        img.putpixel((8 + i, 8 + i), core if d <= 1 else glow)
        img.putpixel((8 + i, 8 - i), core if d <= 1 else glow)
    return img


def write(assets, data):
    tex = os.path.join(assets, "textures", "item")
    os.makedirs(tex, exist_ok=True)
    star_fragment().save(os.path.join(tex, "star_fragment.png"))
    _json(os.path.join(assets, "models", "item", "star_fragment.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": "naturesmod:item/star_fragment"}})
    _json(os.path.join(assets, "items", "star_fragment.json"), {"model": {"type": "minecraft:model", "model": "naturesmod:item/star_fragment"}})
    for wid, (_, _, _, shape) in WEAPONS.items():
        void_tint(Image.open(os.path.join(tex, f"{shape}.png"))).save(os.path.join(tex, f"{wid}.png"))
        _json(os.path.join(assets, "models", "item", f"{wid}.json"),
              {"parent": "minecraft:item/handheld", "textures": {"layer0": f"naturesmod:item/{wid}"}})
        _json(os.path.join(assets, "items", f"{wid}.json"), {"model": {"type": "minecraft:model", "model": f"naturesmod:item/{wid}"}})
    # The Star Altar: starmetal on top, voidstone round its sides.
    _json(os.path.join(assets, "models", "block", "star_altar.json"), {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"top": "naturesmod:block/starmetal_block", "side": "naturesmod:block/voidstone_bricks",
                     "bottom": "naturesmod:block/polished_voidstone"}})
    _json(os.path.join(assets, "blockstates", "star_altar.json"),
          {"variants": {f"fragments={n}": {"model": "naturesmod:block/star_altar"} for n in range(5)}})
    _json(os.path.join(assets, "items", "star_altar.json"), {"model": {"type": "minecraft:model", "model": "naturesmod:block/star_altar"}})
    ns = os.path.join(data, "naturesmod")
    _json(os.path.join(ns, "loot_table", "blocks", "star_altar.json"), {
        "type": "minecraft:block", "random_sequence": "naturesmod:blocks/star_altar",
        "pools": [{"rolls": 1, "bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": "naturesmod:star_altar"}]}]})
    _json(os.path.join(ns, "recipe", "star_altar.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": [" V ", "SCS", "OOO"],
        "key": {"V": "naturesmod:void_crystal", "S": "naturesmod:starmetal_ingot", "C": "minecraft:end_crystal", "O": "minecraft:obsidian"},
        "result": {"id": "naturesmod:star_altar", "count": 1}})
    _json(os.path.join(ns, "tags", "item", "repairs_end_weapons.json"), {"values": ["naturesmod:starmetal_ingot"]})


def lang():
    out = dict(LANG)
    for wid, (name, strike, power, _) in WEAPONS.items():
        out[f"item.naturesmod.{wid}"] = name
        out[f"item.naturesmod.{wid}.strike"] = strike
        out[f"item.naturesmod.{wid}.power"] = power
    return out
