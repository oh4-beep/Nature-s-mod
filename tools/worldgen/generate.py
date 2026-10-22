#!/usr/bin/env python3
"""Generates the Nature's Mod data pack files (terrain + world features).

Usage:
    python3 tools/worldgen/generate.py --vanilla /path/to/vanilla/data

--vanilla points at the `data` directory of the vanilla data pack for the targeted
Minecraft version. One easy way to get it is the `<version>-data` tag of
https://github.com/misode/mcmeta, e.g.:

    git clone --depth 1 --branch 26.3-data https://github.com/misode/mcmeta vanilla
    python3 tools/worldgen/generate.py --vanilla vanilla/data

Every generated file is written below src/main/resources/data. Re-run the script
after tweaking a parameter in terrain.py or features.py.
"""

import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import abyss  # noqa: E402
import advancements  # noqa: E402
import deep  # noqa: E402
import end  # noqa: E402
import features  # noqa: E402
import nether  # noqa: E402
import ocean  # noqa: E402
import terrain  # noqa: E402

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


class Vanilla:
    def __init__(self, root):
        self.root = root

    def read(self, rel):
        with open(os.path.join(self.root, rel), encoding="utf-8") as f:
            return json.load(f)


# The biomes that share a chunk generator. Minecraft sorts their features into one order per
# decoration step and refuses to generate the dimension if two biomes disagree on it.
DIMENSION_BIOMES = {
    "the Nether": ["minecraft:nether_wastes", "minecraft:soul_sand_valley", "minecraft:crimson_forest", "minecraft:warped_forest",
                   "minecraft:basalt_deltas", "naturesmod:ember_wastes", "naturesmod:glowstone_grotto", "naturesmod:obsidian_spires",
                   "naturesmod:brimstone_fields", "naturesmod:flesh_caverns"],
    "the End": ["minecraft:the_end", "minecraft:end_highlands", "minecraft:end_midlands", "minecraft:end_barrens",
                "minecraft:small_end_islands"] + [f"naturesmod:{b}" for b in end.BIOMES],
    "the Abyss": ["naturesmod:twilight_waters", "naturesmod:midnight_zone", "naturesmod:abyssal_plains", "naturesmod:glowing_reef",
                  "naturesmod:hydrothermal_vents", "naturesmod:bone_fields"],
}


def check_feature_order(files, vanilla):
    for dimension, biomes in DIMENSION_BIOMES.items():
        steps = {}
        for biome in biomes:
            namespace, name = biome.split(":")
            rel = f"{namespace}/worldgen/biome/{name}.json"
            # A vanilla biome the mod reworks is checked as the mod writes it.
            data = files[rel] if rel in files else vanilla.read(f"minecraft/worldgen/biome/{name}.json")
            for step, placed in enumerate(data["features"]):
                edges = steps.setdefault(step, {})
                for before, after in zip(placed, placed[1:]):
                    edges.setdefault(before, set()).add((after, biome))
        for step, edges in steps.items():
            # Depth-first search for a feature that has to come after itself.
            state = {}

            def visit(feature, path):
                state[feature] = "open"
                for after, biome in edges.get(feature, ()):
                    if state.get(after) == "open":
                        loop = path[path.index(after):] + [after] if after in path else [feature, after]
                        raise SystemExit(f"feature order cycle in {dimension}, step {step} ({biome}): {' -> '.join(loop)}")
                    if after not in state:
                        visit(after, path + [after])
                state[feature] = "done"

            for feature in list(edges):
                if feature not in state:
                    visit(feature, [feature])


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--vanilla", required=True, help="vanilla data pack `data` directory")
    parser.add_argument("--out", default=os.path.join(REPO, "src", "main", "resources", "data"))
    args = parser.parse_args()

    vanilla = Vanilla(args.vanilla)
    files = {}
    files.update(terrain.build(vanilla))
    files.update(features.build(vanilla))
    files.update(abyss.build(vanilla))
    files.update(deep.build(vanilla))
    files.update(nether.build(vanilla))
    files.update(end.build(vanilla))
    files.update(ocean.build())
    buckets_dir = os.path.join(REPO, "src", "main", "resources", "assets", "naturesmod", "items")
    buckets = sorted(f"naturesmod:{name[:-5]}" for name in os.listdir(buckets_dir) if name.endswith("_bucket.json"))
    files.update(advancements.build(buckets))
    check_feature_order(files, vanilla)

    for rel, data in sorted(files.items()):
        path = os.path.join(args.out, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
    print(f"wrote {len(files)} files to {os.path.relpath(args.out, REPO)}")


if __name__ == "__main__":
    main()
