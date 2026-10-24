"""Development aid: prints the shape of a data pack, one line per folder and JSON key path.

A dict that names its kind (by "type", "condition", "function" or "predicate_type") starts its path afresh with that
kind, so the lines show which fields each kind of thing takes wherever it appears. Comparing the output for the vanilla data with the output for the
mod's data shows the fields and kinds the mod uses that the targeted game version does not know.

Usage: python3 schema.py DATA_DIR [NAMESPACE...]
"""
import json
import pathlib
import sys

DISPATCH = ("type", "condition", "function", "predicate_type")
# Maps keyed by registry names, whose keys are data rather than fields.
FREE_KEYS = ("properties", "Properties", "enchantments", "components", "key", "criteria", "spawn_costs")


def leaf(value):
    if isinstance(value, bool):
        return "bool"
    if isinstance(value, (int, float)):
        return "num"
    if isinstance(value, str):
        return "tag" if value.startswith("#") else "str"
    return "null"


def walk(node, path, out, parent_key=""):
    if isinstance(node, dict):
        out.add(path + "{}")
        kind = ""
        for key in DISPATCH:
            if isinstance(node.get(key), str):
                kind = "<%s=%s>" % (key, node[key])
                break
        if kind:
            # A kind takes the same fields wherever it appears, so its path starts afresh.
            path = kind
            out.add(path + "{}")
        for key, value in node.items():
            name = "*" if parent_key in FREE_KEYS and not kind else key
            walk(value, path + "." + name, out, key)
    elif isinstance(node, list):
        out.add(path + "[]")
        for value in node:
            walk(value, path + "[]", out, parent_key)
    else:
        out.add(path + ":" + leaf(node))


def main():
    root = pathlib.Path(sys.argv[1])
    namespaces = sys.argv[2:] or [p.name for p in root.iterdir() if p.is_dir()]
    lines = set()
    for namespace in namespaces:
        base = root / namespace
        for f in sorted(base.rglob("*.json")):
            rel = f.relative_to(base).parts
            if rel[0] == "tags":
                continue
            folder = "/".join(rel[:2]) if rel[0] in ("worldgen", "loot_table") and len(rel) > 2 else rel[0]
            try:
                data = json.loads(f.read_text())
            except ValueError as e:
                lines.add("%s BROKEN %s" % (folder, e))
                continue
            found = set()
            walk(data, "", found)
            for path in found:
                lines.add(folder + " " + path)
    for line in sorted(lines):
        print(line)


main()
