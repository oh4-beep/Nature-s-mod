"""Assets and data of the natural disasters: the textures of their particles (billowing cloud,
debris, sparks, snow), the volcanic ash that settles in layers, the ways they kill, and their words
(names, warnings, reports, command feedback, death messages). Their sounds are made by sounds.py.
"""

import json
import math
import os
import random

from PIL import Image

from hell import _json

DISASTERS = {
    "tornado": "Tornado",
    "earthquake": "Earthquake",
    "meteor_shower": "Meteor shower",
    "eruption": "Volcanic eruption",
    "wildfire": "Wildfire",
    "sandstorm": "Sandstorm",
    "blizzard": "Blizzard",
}

DAMAGE = {
    "tornado": ("%1$s was swept away by a tornado", "%1$s was swept away by a tornado while fighting %2$s"),
    "earthquake": ("%1$s was crushed in an earthquake", "%1$s was crushed in an earthquake while fighting %2$s"),
    "meteor": ("%1$s was struck by a meteorite", "%1$s was struck by a meteorite while fighting %2$s"),
    "pyroclastic": ("%1$s was engulfed by a pyroclastic flow", "%1$s was engulfed by a pyroclastic flow while fleeing %2$s"),
}


# --- particle textures -----------------------------------------------------------------------

def smooth_noise(size, cells, rng):
    """Value noise on a size x size grid, a random value every size/cells pixels, smoothly blended."""
    grid = [[rng.random() for _ in range(cells + 1)] for _ in range(cells + 1)]
    out = [[0.0] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            gx = x / size * cells
            gy = y / size * cells
            ix, iy = int(gx), int(gy)
            fx, fy = gx - ix, gy - iy
            fx = fx * fx * (3 - 2 * fx)
            fy = fy * fy * (3 - 2 * fy)
            top = grid[iy][ix] * (1 - fx) + grid[iy][ix + 1] * fx
            bottom = grid[iy + 1][ix] * (1 - fx) + grid[iy + 1][ix + 1] * fx
            out[y][x] = top * (1 - fy) + bottom * fy
    return out


def cloud(seed):
    """A soft, lumpy billow of cloud: white, lit from above and shaded beneath, fading out at its edges."""
    size = 32
    rng = random.Random(seed)
    coarse = smooth_noise(size, 3, rng)
    fine = smooth_noise(size, 7, rng)
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            dx = (x + 0.5) / size * 2 - 1
            dy = (y + 0.5) / size * 2 - 1
            r = math.sqrt(dx * dx + dy * dy)
            lump = coarse[y][x] * 0.65 + fine[y][x] * 0.35
            density = max(0.0, 1.0 - r * (1.05 - 0.45 * lump)) ** 0.9
            alpha = min(1.0, density * (0.75 + 0.5 * lump) * 1.6)
            light = 0.78 + 0.22 * (1 - (dy + 1) / 2) + 0.08 * (fine[y][x] - 0.5)
            v = int(max(0, min(255, 255 * light)))
            img.putpixel((x, y), (v, v, v, int(255 * max(0.0, min(1.0, alpha)))))
    return img


def debris(seed):
    """A chunk of something torn loose: a jagged little shard, light enough to tint."""
    size = 8
    rng = random.Random(seed)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    points = []
    for k in range(rng.randint(5, 7)):
        angle = k / 6 * math.tau + rng.uniform(-0.4, 0.4)
        radius = rng.uniform(1.6, 3.6)
        points.append((3.5 + math.cos(angle) * radius, 3.5 + math.sin(angle) * radius))
    for y in range(size):
        for x in range(size):
            if inside((x + 0.5, y + 0.5), points):
                v = int(150 + rng.random() * 90 - (y / size) * 40)
                img.putpixel((x, y), (v, v, v, 255))
    return img


def inside(point, polygon):
    x, y = point
    hit = False
    for (x1, y1), (x2, y2) in zip(polygon, polygon[1:] + polygon[:1]):
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1 + 1e-9) + x1:
            hit = not hit
    return hit


def spark(seed, size=8):
    """A point of light: white at its heart, its glow falling away."""
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            dx = (x + 0.5) / size * 2 - 1
            dy = (y + 0.5) / size * 2 - 1
            r = math.sqrt(dx * dx + dy * dy)
            alpha = max(0.0, 1.0 - r) ** (1.4 + seed * 0.3)
            img.putpixel((x, y), (255, 255, 255, int(255 * alpha)))
    return img


def flake(seed):
    """A flake of driven snow: a soft dot with a glint."""
    size = 8
    rng = random.Random(seed)
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            dx = (x + 0.5) / size * 2 - 1
            dy = (y + 0.5) / size * 2 - 1
            r = math.sqrt(dx * dx + dy * dy)
            alpha = max(0.0, 1.0 - r * 1.15) ** 0.8 * (0.85 + 0.15 * rng.random())
            img.putpixel((x, y), (255, 255, 255, int(255 * alpha)))
    return img


def particles(assets):
    folder = os.path.join(assets, "textures", "particle")
    os.makedirs(folder, exist_ok=True)
    kinds = {
        "cloud": [cloud(7100 + i) for i in range(6)],
        "debris": [debris(7200 + i) for i in range(6)],
        "spark": [spark(i) for i in range(2)],
        "flake": [flake(7300 + i) for i in range(2)],
    }
    for kind, images in kinds.items():
        names = []
        for i, img in enumerate(images):
            img.save(os.path.join(folder, f"{kind}_{i}.png"))
            names.append(f"naturesmod:{kind}_{i}")
        _json(os.path.join(assets, "particles", kind + ".json"), {"textures": names})


# --- volcanic ash ----------------------------------------------------------------------------

def ash_layer(assets, data_root):
    texture = "naturesmod:block/ash_block"
    models = os.path.join(assets, "models", "block")
    variants = {}
    for layers in range(1, 9):
        name = f"ash_layer_height{layers * 2}" if layers < 8 else "ash_layer_block"
        if layers < 8:
            h = layers * 2
            faces = {
                "down": {"uv": [0, 0, 16, 16], "texture": "#texture", "cullface": "down"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#texture"},
                "north": {"uv": [0, 16 - h, 16, 16], "texture": "#texture", "cullface": "north"},
                "south": {"uv": [0, 16 - h, 16, 16], "texture": "#texture", "cullface": "south"},
                "west": {"uv": [0, 16 - h, 16, 16], "texture": "#texture", "cullface": "west"},
                "east": {"uv": [0, 16 - h, 16, 16], "texture": "#texture", "cullface": "east"},
            }
            _json(os.path.join(models, name + ".json"), {
                "textures": {"particle": texture, "texture": texture},
                "elements": [{"from": [0, 0, 0], "to": [16, h, 16], "faces": faces}],
            })
        else:
            _json(os.path.join(models, name + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": texture}})
        variants[f"layers={layers}"] = {"model": f"naturesmod:block/{name}"}
    _json(os.path.join(assets, "blockstates", "ash_layer.json"), {"variants": variants})
    _json(os.path.join(assets, "items", "ash_layer.json"), {"model": {"type": "minecraft:model", "model": "naturesmod:block/ash_layer_height2"}})
    merge_tag(os.path.join(data_root, "minecraft", "tags", "block", "mineable", "shovel.json"), ["naturesmod:ash_layer"])
    # Dug up, it gives back as many layers as it was deep.
    pools = [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "naturesmod:ash_layer",
                                       "modifier": [{"type": "minecraft:set_count", "count": layers}]}],
              "condition": {"type": "minecraft:block_state_property", "block": "naturesmod:ash_layer", "properties": {"layers": str(layers)}}}
             for layers in range(1, 9)]
    _json(os.path.join(data_root, "naturesmod", "loot_table", "blocks", "ash_layer.json"), {"type": "minecraft:block", "pools": pools})


def merge_tag(path, values):
    existing = []
    if os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            existing = json.load(fh).get("values", [])
    existing += [v for v in values if v not in existing]
    _json(path, {"replace": False, "values": existing})


# --- how they kill ---------------------------------------------------------------------------

def damage(data_root):
    for name in DAMAGE:
        _json(os.path.join(data_root, "naturesmod", "damage_type", name + ".json"),
              {"exhaustion": 0.1, "message_id": f"naturesmod.{name}", "scaling": "never"})
    tags = os.path.join(data_root, "minecraft", "tags", "damage_type")
    merge_tag(os.path.join(tags, "is_explosion.json"), ["naturesmod:meteor"])
    merge_tag(os.path.join(tags, "is_fire.json"), ["naturesmod:pyroclastic"])


def write(assets, data_root):
    particles(assets)
    ash_layer(assets, data_root)
    damage(data_root)


def lang():
    out = {f"disaster.naturesmod.{key}": name for key, name in DISASTERS.items()}
    for name, (death, death_player) in DAMAGE.items():
        out[f"death.attack.naturesmod.{name}"] = death
        out[f"death.attack.naturesmod.{name}.player"] = death_player
    out["block.naturesmod.ash_layer"] = "Volcanic Ash"
    out.update({
        "commands.naturesmod.disaster.started": "A %s (%s) is beginning %s blocks %s of you",
        "commands.naturesmod.disaster.started_here": "A %s (%s) is beginning here",
        "commands.naturesmod.disaster.stopped": "Ended %s disasters",
        "commands.naturesmod.disaster.none": "No disasters under way here",
        "naturesmod.warning.tornado": "⚠ TORNADO WARNING: an %s tornado has touched down %s blocks %s of you, moving %s. Take shelter "
                                      "underground or in a sturdy building, now!",
        "naturesmod.warning.quake": "⚠ Earthquake early warning: strong shaking expected in %s seconds. Drop, cover and hold on!",
        "naturesmod.report.quake": "Earthquake of magnitude %s, %s blocks %s of you: %s shaking",
        "naturesmod.warning.meteors": "☄ A meteor shower is lighting up the sky. Some of them may reach the ground",
        "naturesmod.warning.eruption": "⚠ The volcano %s blocks %s of you is stirring: tremors and steam. Keep away from it and its ash cloud!",
        "naturesmod.warning.pyroclastic": "⚠ The eruption column is collapsing: pyroclastic flows are racing down the mountain!",
        "naturesmod.warning.wildfire": "⚠ Wildfire %s blocks %s of you, spreading %s with the wind",
        "naturesmod.warning.sandstorm": "⚠ A wall of dust is rolling in from the %s. Get under cover",
        "naturesmod.warning.blizzard": "⚠ Blizzard warning: whiteout and dangerous cold. Find shelter, a fire, or warm clothes",
    })
    for direction in ("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"):
        out[f"naturesmod.compass.{direction}"] = direction
    for level, word in (("weak", "weak"), ("light", "light"), ("moderate", "moderate"), ("strong", "strong"), ("very_strong", "very strong"),
                        ("severe", "severe"), ("violent", "violent"), ("extreme", "extreme")):
        out[f"naturesmod.shaking.{level}"] = word
    return out
