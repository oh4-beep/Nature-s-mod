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
import features  # noqa: E402
import nether  # noqa: E402
import terrain  # noqa: E402

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


class Vanilla:
    def __init__(self, root):
        self.root = root

    def read(self, rel):
        with open(os.path.join(self.root, rel), encoding="utf-8") as f:
            return json.load(f)


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
    files.update(nether.build(vanilla))

    for rel, data in sorted(files.items()):
        path = os.path.join(args.out, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
    print(f"wrote {len(files)} files to {os.path.relpath(args.out, REPO)}")


if __name__ == "__main__":
    main()
