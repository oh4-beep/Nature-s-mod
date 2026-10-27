"""Assets and data of the natural disasters: the textures of their particles (billowing cloud,
debris, sparks, snow), the volcanic ash that settles in layers, the ways they kill, and their words
(names, warnings, reports, command feedback, death messages). Their sounds are made by sounds.py.
"""

import json
import math
import os
import random

import numpy as np
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
    "hurricane": "Hurricane",
    "tsunami": "Tsunami",
    "sinkhole": "Sinkhole",
    "avalanche": "Avalanche",
    "lightning_storm": "Lightning storm",
    "acid_rain": "Acid rain",
}

DAMAGE = {
    "tornado": ("%1$s was swept away by a tornado", "%1$s was swept away by a tornado while fighting %2$s"),
    "earthquake": ("%1$s was crushed in an earthquake", "%1$s was crushed in an earthquake while fighting %2$s"),
    "meteor": ("%1$s was struck by a meteorite", "%1$s was struck by a meteorite while fighting %2$s"),
    "pyroclastic": ("%1$s was engulfed by a pyroclastic flow", "%1$s was engulfed by a pyroclastic flow while fleeing %2$s"),
    "hail": ("%1$s was battered to death by hail", "%1$s was battered to death by hail while fighting %2$s"),
    "hurricane": ("%1$s was blown away by a hurricane", "%1$s was blown away by a hurricane while fighting %2$s"),
    "tsunami": ("%1$s was swept away by a tsunami", "%1$s was swept away by a tsunami while fighting %2$s"),
    "sinkhole": ("%1$s was swallowed by a sinkhole", "%1$s was swallowed by a sinkhole while fighting %2$s"),
    "avalanche": ("%1$s was buried by an avalanche", "%1$s was buried by an avalanche while fleeing %2$s"),
    "acid_rain": ("%1$s was eaten away by acid rain", "%1$s was eaten away by acid rain while fighting %2$s"),
    "sandstorm": ("%1$s was sandblasted to death in a sandstorm", "%1$s was sandblasted to death in a sandstorm while fighting %2$s"),
    "blizzard": ("%1$s froze to death in a blizzard", "%1$s froze to death in a blizzard while fighting %2$s"),
}


# --- particle textures -----------------------------------------------------------------------

def fractal(size, rng, octaves=5, base=4):
    """Fractal value noise in [0, 1]: smooth billows with finer detail on top, tileable or not."""
    total = np.zeros((size, size))
    weight = 0.0
    amplitude = 1.0
    cells = base
    for _ in range(octaves):
        grid = rng.random((cells + 1, cells + 1))
        t = np.linspace(0, cells, size, endpoint=False)
        i = t.astype(int)
        f = t - i
        f = f * f * (3 - 2 * f)
        rows = grid[i][:, i] * (1 - f)[None, :] + grid[i][:, i + 1] * f[None, :]
        rows2 = grid[i + 1][:, i] * (1 - f)[None, :] + grid[i + 1][:, i + 1] * f[None, :]
        total += (rows * (1 - f)[:, None] + rows2 * f[:, None]) * amplitude
        weight += amplitude
        amplitude *= 0.5
        cells *= 2
    return total / weight


def cloud(seed):
    """A soft, lumpy billow of cloud, big enough not to show its pixels close up: white, lit from above and
    shaded beneath, its edge broken into smaller billows that thin out to nothing."""
    size = 128
    rng = np.random.default_rng(seed)
    noise = fractal(size, rng)
    detail = fractal(size, rng, octaves=4, base=8)
    y, x = np.mgrid[0:size, 0:size]
    dx = (x + 0.5) / size * 2 - 1
    dy = (y + 0.5) / size * 2 - 1
    r = np.sqrt(dx * dx + dy * dy)
    # The edge wanders in and out with the billows.
    edge = 0.62 + 0.38 * noise
    density = np.clip(1.0 - r / edge, 0.0, 1.0)
    density = density ** 0.75 * (0.7 + 0.6 * detail)
    alpha = np.clip(density * 1.5, 0.0, 1.0)
    alpha = alpha * alpha * (3 - 2 * alpha)
    # Light from above: the top of each billow bright, the underside grey.
    light = 0.80 + 0.20 * (1 - (dy + 1) / 2) + 0.12 * (detail - 0.5) + 0.08 * (noise - 0.5)
    v = np.clip(light * 255, 0, 255).astype(np.uint8)
    a = np.clip(alpha * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(np.dstack([v, v, v, a]), "RGBA")


def streak(seed):
    """A thin bright line fading out at both ends: a shooting star's trail, turned on screen to lie along its path."""
    size = 64
    y, x = np.mgrid[0:size, 0:size]
    across = (y + 0.5 - size / 2) / (1.2 + seed * 0.5)
    along = (x + 0.5) / size * 2 - 1
    alpha = np.exp(-across * across) * np.clip(1.0 - np.abs(along) ** 1.6, 0.0, 1.0)
    a = np.clip(alpha * 255, 0, 255).astype(np.uint8)
    w = np.full((size, size), 255, np.uint8)
    return Image.fromarray(np.dstack([w, w, w, a]), "RGBA")


def soft_spark(seed, size=32):
    """A point of light: white at its heart, its glow falling away smoothly."""
    y, x = np.mgrid[0:size, 0:size]
    dx = (x + 0.5) / size * 2 - 1
    dy = (y + 0.5) / size * 2 - 1
    r = np.sqrt(dx * dx + dy * dy)
    alpha = np.clip(1.0 - r, 0.0, 1.0) ** (1.4 + seed * 0.6)
    a = np.clip(alpha * 255, 0, 255).astype(np.uint8)
    w = np.full((size, size), 255, np.uint8)
    return Image.fromarray(np.dstack([w, w, w, a]), "RGBA")


def soft_flake(seed, size=16):
    """A flake of driven snow: a soft dot."""
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:size, 0:size]
    dx = (x + 0.5) / size * 2 - 1 + rng.uniform(-0.1, 0.1)
    dy = (y + 0.5) / size * 2 - 1 + rng.uniform(-0.1, 0.1)
    r = np.sqrt(dx * dx + dy * dy)
    alpha = np.clip(1.0 - r * 1.1, 0.0, 1.0) ** 0.7
    a = np.clip(alpha * 255, 0, 255).astype(np.uint8)
    w = np.full((size, size), 255, np.uint8)
    return Image.fromarray(np.dstack([w, w, w, a]), "RGBA")


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


def particles(assets):
    folder = os.path.join(assets, "textures", "particle")
    os.makedirs(folder, exist_ok=True)
    kinds = {
        "cloud": [cloud(7100 + i) for i in range(6)],
        "debris": [debris(7200 + i) for i in range(6)],
        "spark": [soft_spark(i) for i in range(2)],
        "flake": [soft_flake(7300 + i) for i in range(2)],
        "streak": [streak(i) for i in range(2)],
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
    # Dug up, it gives back a layer of ash.
    pools = [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "naturesmod:ash_layer"}], "condition": {"type": "minecraft:survives_explosion"}}]
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
        "naturesmod.warning.acid_rain": "⚠ Acid rain! It burns skin, withers leaves and kills crops. Get under a roof, or at least put on a helmet",
        "naturesmod.warning.acid_rain_burning": "⚠ The acid rain is burning your skin! Get under a roof, or put on a helmet",
        "naturesmod.warning.acid_rain_coming": "⚠ Acid rain is falling %s of you, drifting %s. Get yourself and your animals under a roof",
        "naturesmod.warning.acid_rain_helmet": "⚠ The acid rain is eating through your helmet! Get under a roof",
        "naturesmod.warning.avalanche": "⚠ AVALANCHE! The slope %s blocks %s of you has let go. Get out of its path, to the side or up onto high ground!",
        "naturesmod.warning.avalanche_buried": "⚠ You are buried in the snow! Dig your way up, or wade out of the powder snow",
        "naturesmod.warning.ball_lightning": "⚠ Ball lightning! Keep clear of the glowing orb: it zaps anything that comes close",
        "naturesmod.warning.blizzard_freezing": "⚠ You are freezing! Get out of the wind: under a roof, by a fire, or into leather clothes",
        "naturesmod.warning.eruption_ash": "⚠ The volcano has erupted! Its ash is drifting %s: get under a roof and stay off the mountain!",
        "naturesmod.warning.eruption_imminent": "⚠ The volcano is about to blow! Get off the mountain and as far from it as you can, now!",
        "naturesmod.warning.hurricane": "⚠ HURRICANE WARNING: a Category %s hurricane is %s blocks %s of you, moving %s. Board up and get indoors, away from windows!",
        "naturesmod.warning.hurricane_eye": "⚠ You are in the eye of the hurricane. The calm won't last: the other side of the eyewall is coming, its wind from the other way!",
        "naturesmod.warning.hurricane_eyewall": "⚠ The hurricane's eyewall is %s blocks %s of you: its worst winds are almost here. Get under a roof, now!",
        "naturesmod.warning.hurricane_landfall": "⚠ The Category %s hurricane has made landfall %s blocks %s of you, moving %s. Stay indoors until it has passed!",
        "naturesmod.warning.landslide": "⚠ LANDSLIDE! The hillside %s blocks %s of you is giving way. Get out of its path, to the side or up onto high ground!",
        "naturesmod.warning.landslide_buried": "⚠ You are buried in the rubble! Dig your way out",
        "naturesmod.warning.lightning_charge": "⚠ The air around you is crackling and your hair stands on end: lightning is about to strike here. Move away, now!",
        "naturesmod.warning.lightning_storm": "⚠ Severe thunderstorm overhead: lightning is striking all around. Get indoors, off open ground and hilltops, and away from tall trees",
        "naturesmod.warning.lightning_storm_coming": "⚠ Severe thunderstorm warning: a lightning storm %s blocks %s of you is moving %s. Get indoors before it arrives",
        "naturesmod.warning.meteor_great": "⚠ A huge fireball is coming down %s blocks %s of you. Get away from it and take cover, now!",
        "naturesmod.warning.sandstorm_blasting": "⚠ The haboob is almost on you: get indoors! Out in it the blasting sand flays bare skin, so wear a helmet",
        "naturesmod.warning.sandstorm_near": "⚠ The wall of dust is almost on you: get indoors and stay there until it has passed",
        "naturesmod.warning.sinkhole": "⚠ The ground is cracking and sinking %s blocks %s of you: a sinkhole is opening. Keep clear of it!",
        "naturesmod.warning.sinkhole_under": "⚠ The ground under your feet is cracking and sinking: a sinkhole is opening. Run!",
        "naturesmod.warning.tornado_near": "⚠ The tornado is almost on you! Get below ground, or run at right angles to its path",
        "naturesmod.warning.tsunami": "⚠ TSUNAMI WARNING: the sea is drawing back from the shore. A wave %s blocks high is coming in from the %s. Get to high ground, now!",
        "naturesmod.warning.tsunami_backwash": "⚠ The water is rushing back out to sea. Keep away from the shore until it is over",
        "naturesmod.warning.tsunami_flood": "⚠ FLASH FLOOD: a wall of water %s blocks high is rushing in from the %s. Get to high ground, now!",
        "naturesmod.warning.tsunami_near": "⚠ The wave is almost on you! Climb as high as you can, now!",
        "naturesmod.warning.wildfire_near": "⚠ The wildfire is almost on you, running %s with the wind. Get out of its way across the wind, not ahead of it!",
    })
    for direction in ("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"):
        out[f"naturesmod.compass.{direction}"] = direction
    for level, word in (("weak", "weak"), ("light", "light"), ("moderate", "moderate"), ("strong", "strong"), ("very_strong", "very strong"),
                        ("severe", "severe"), ("violent", "violent"), ("extreme", "extreme")):
        out[f"naturesmod.shaking.{level}"] = word
    return out
