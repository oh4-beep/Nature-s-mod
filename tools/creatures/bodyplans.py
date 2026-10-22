"""Box-model geometry for the sea creatures.

A body plan is a tree of parts. Each part has a pivot and rotation (relative to its parent),
and cubes in the part's local space. Coordinates follow Minecraft's model space: x to the
creature's left, y downwards (y=24 is the ground under the entity) and z backwards, so the
head points to -z. Each cube carries a "role" that tells the texture painter how to colour
it. Texture coordinates are laid out automatically by `layout()`.

Variants switch optional parts on or off and stretch the shape so one plan covers several
species (a hammerhead is a shark with a hammer head).
"""

import math

PI = math.pi


def cube(role, origin, size, inflate=0.0, mirror=False):
    return {"role": role, "origin": origin, "size": size, "inflate": inflate, "mirror": mirror}


def part(name, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=(), children=()):
    return {"name": name, "pivot": pivot, "rot": rot, "cubes": list(cubes), "children": list(children)}


# --- plans -----------------------------------------------------------------------------------

def fish(v):
    """A streamlined fish: trout, tuna, carp... Options: deep, long, bill, barbels, lobes, legs, dome."""
    h = 5 + (2 if v.get("deep") else 0)          # body height
    w = 3
    body_len = 7 + v.get("stretch", 0)
    head_len = 3
    y0 = 24 - h / 2 - 1.5                         # body centre line
    head_children = []
    if v.get("bill"):
        head_children.append(part("bill", (0, 0.5, -head_len), cubes=[cube("bill", (-0.5, -0.5, -10), (1, 1, 10))]))
    if v.get("beak"):
        head_children.append(part("beak", (0, 0.5, -head_len), cubes=[cube("beak", (-1, -1, -1.5), (2, 2.5, 1.5))]))
    if v.get("barbels"):
        head_children.append(part("left_barbel", (1.5, 1.5, -head_len + 0.5), (0.3, 0.6, 0),
                                  cubes=[cube("barbel", (0, 0, -4), (0, 1, 4))]))
        head_children.append(part("right_barbel", (-1.5, 1.5, -head_len + 0.5), (0.3, -0.6, 0),
                                  cubes=[cube("barbel", (0, 0, -4), (0, 1, 4))]))
    if v.get("dome"):
        head_children.append(part("dome", (0, -h / 2 + 0.5, -head_len + 0.5),
                                  cubes=[cube("dome", (-1.5, -3, 0), (3, 3, 3), inflate=0.1)]))
    if v.get("lure"):
        head_children.append(part("lure", (0, -h / 2 + 0.5, -head_len + 1), (-0.5, 0, 0),
                                  cubes=[cube("stalk", (0, -6, -0.5), (0, 6, 1))],
                                  children=[part("bulb", (0, -6, 0), cubes=[cube("lure", (-1, -2, -1), (2, 2, 2))])]))
    if v.get("big_maw"):
        # A gaping maw: a deep, long lower jaw lined with fangs.
        jaw = part("jaw", (0, h / 2 - 1.5, -0.5), (0.25, 0, 0), cubes=[cube("jaw", (-w / 2 - 0.25, 0, -head_len - 2), (w + 0.5, 2, head_len + 2))],
                   children=[part("teeth", (0, 0, -head_len - 2), cubes=[cube("teeth", (-w / 2, -2, 0), (w, 2, 0))]),
                             part("side_teeth", (0, 0, -head_len), cubes=[cube("teeth", (-w / 2 - 0.2, -1.5, 0), (0, 1.5, head_len + 1))])])
        head_children.append(part("upper_teeth", (0, h / 2 - 1.5, -head_len - 0.5), cubes=[cube("teeth", (-w / 2 + 0.25, 0, 0), (w - 0.5, 1.5, 0))]))
    else:
        jaw = part("jaw", (0, h / 2 - 1.5, -0.5), cubes=[cube("jaw", (-w / 2 + 0.25, 0, -head_len), (w - 0.5, 1, head_len))])
    head = part("head", (0, 0, -body_len / 2), cubes=[
        cube("head", (-w / 2, -h / 2 + 0.5, -head_len), (w, h - 1, head_len)),
        cube("snout", (-w / 2 + 0.5, -h / 2 + 1.5, -head_len - 1), (w - 1, h - 3, 1)),
    ], children=head_children + [jaw])
    tail_len = 4 + v.get("stretch", 0) / 2
    tail = part("tail", (0, 0, body_len / 2), cubes=[cube("tail", (-w / 2 + 0.5, -h / 2 + 1.5, 0), (w - 1, h - 3, tail_len))],
                children=[part("tail_fin", (0, 0, tail_len), cubes=[cube("tail_fin", (0, -h / 2 - 1, 0), (0, h + 2, 5 if v.get("lobes") else 4))])])
    fins = [
        part("dorsal", (0, -h / 2, -1), cubes=[cube("dorsal", (0, -3 - (2 if v.get("sail") else 0) - (1 if v.get("deep") else 0), 0),
                                                     (0, 3 + (2 if v.get("sail") else 0) + (1 if v.get("deep") else 0), 5 if v.get("sail") else 4))]),
        part("anal_fin", (0, h / 2, 1), cubes=[cube("fin", (0, 0, 0), (0, 2 + (1 if v.get("deep") else 0), 3))]),
        part("left_fin", (w / 2, 1, -body_len / 2 + 1), (0, 0.4, 0.7), cubes=[cube("fin", (0, 0, 0), (3, 0, 2 if not v.get("lobes") else 3))]),
        part("right_fin", (-w / 2, 1, -body_len / 2 + 1), (0, -0.4, -0.7), cubes=[cube("fin", (-3, 0, 0), (3, 0, 2 if not v.get("lobes") else 3), mirror=True)]),
    ]
    if v.get("legs"):
        for i, (x, z) in enumerate([(1.2, -1), (-1.2, -1), (0, 3)]):
            fins.append(part(f"leg{i}", (x, h / 2, z), (0.15, 0, 0), cubes=[cube("barbel", (0, 0, 0), (0, 9, 1))]))
    if v.get("spines"):
        fins.append(part("left_spines", (w / 2, 0, -1), (0, 0, 0.9), cubes=[cube("spines", (0, -3, -2), (6, 0, 7))]))
        fins.append(part("right_spines", (-w / 2, 0, -1), (0, 0, -0.9), cubes=[cube("spines", (-6, -3, -2), (6, 0, 7), mirror=True)]))
    body_cubes = [cube("body", (-w / 2, -h / 2, -body_len / 2), (w, h, body_len))] + mutations(v, w, h, body_len)
    body = part("body", (0, y0, 0), cubes=body_cubes, children=[head, tail] + fins)
    return {"kind": "fish", "root": [body], "length": body_len + head_len + tail_len + 5}


def mutations(v, w, h, body_len):
    """Extra cubes on the body of a mutated creature: eyes all over it, or ribs showing through."""
    cubes = []
    if v.get("eyes_many"):
        for i, (z, y) in enumerate([(-0.3, -0.3), (0.05, 0.15), (0.3, -0.2), (-0.1, 0.35), (0.42, 0.25)]):
            size = 1.4 if i % 2 == 0 else 1.0
            for side in (-1, 1):
                x = side * (w / 2) - (size if side < 0 else 0) + side * 0.05
                cubes.append(cube("eye_bulge", (x, y * h - size / 2, z * body_len - size / 2), (size, size, size)))
    if v.get("ribs"):
        for side in (-1, 1):
            cubes.append(cube("ribs", (side * (w / 2 + 0.3), -h / 2 + 0.5, -body_len / 2 + 1), (0, h - 1, body_len - 2)))
    return cubes


def deep(v):
    """A tall, thin reef fish: angelfish, tang, butterflyfish, piranha. Options: tall_fins, round."""
    h = 9 if not v.get("round") else 12
    body_len = 6 if not v.get("round") else 8
    y0 = 24 - h / 2 - 1
    tall = v.get("tall_fins")
    body = part("body", (0, y0, 0), cubes=[
        cube("body", (-1, -h / 2, -body_len / 2), (2, h, body_len)),
        cube("head", (-1, -h / 2 + 1.5, -body_len / 2 - 2), (2, h - 3, 2)),
        cube("snout", (-0.5, -0.5, -body_len / 2 - 3), (1, 2, 1)),
    ] + mutations(v, 2, h, body_len), children=[
        part("jaw", (0, 1.5, -body_len / 2 - 2), cubes=[cube("jaw", (-0.75, 0, -1), (1.5, 0.5, 1))]),
        part("dorsal", (0, -h / 2, -body_len / 2 + 1), (0.2, 0, 0), cubes=[cube("dorsal", (0, -3 - (5 if tall else 0), 0), (0, 3 + (5 if tall else 0), body_len))]),
        part("anal_fin", (0, h / 2, -body_len / 2 + 2), (-0.2, 0, 0), cubes=[cube("fin", (0, 0, 0), (0, 2 + (5 if tall else 0), body_len - 1))]),
        part("tail", (0, 0, body_len / 2), cubes=[cube("tail", (-0.5, -1.5, 0), (1, 3, 2))],
             children=[part("tail_fin", (0, 0, 2), cubes=[cube("tail_fin", (0, -h / 2 + (0 if not v.get("round") else 3), 0), (0, h - (0 if not v.get("round") else 6), 3))])]),
        part("left_fin", (1, 1, -1), (0, 0.5, 0.5), cubes=[cube("fin", (0, 0, 0), (2, 0, 2))]),
        part("right_fin", (-1, 1, -1), (0, -0.5, -0.5), cubes=[cube("fin", (-2, 0, 0), (2, 0, 2), mirror=True)]),
    ] + ([part("left_spines", (1, -1, -1), (0, 0, 0.8), cubes=[cube("spines", (0, -3, -2), (6, 0, 7))]),
          part("right_spines", (-1, -1, -1), (0, 0, -0.8), cubes=[cube("spines", (-6, -3, -2), (6, 0, 7), mirror=True)])] if v.get("spines") else []))
    return {"kind": "fish", "root": [body], "length": body_len + 6}


def long(v):
    """Eels and serpents: a chain of segments. Options: segments, thick, fangs, frill, jaw, glow_tail, stiff."""
    n = v.get("segments", 5)
    t = 3 + v.get("thick", 0)
    y0 = 24 - t / 2 - 1
    jaw_len = 4 + (2 if v.get("big_jaw") else 0)
    head_cubes = [cube("head", (-t / 2, -t / 2, -4), (t, t - 1, 4)), cube("snout", (-t / 2 + 0.5, -t / 2 + 0.5, -5), (t - 1, t - 2, 1))]
    head_children = [part("jaw", (0, t / 2 - 1, -0.5), cubes=[cube("jaw", (-t / 2 + 0.25, 0, -jaw_len), (t - 0.5, 1 + (1 if v.get("big_jaw") else 0), jaw_len))])]
    if v.get("fangs"):
        head_children.append(part("fangs", (0, t / 2 - 1, -4), cubes=[cube("teeth", (-1, -1, -0.5), (2, 2, 0))]))
    if v.get("barbel"):
        head_children.append(part("barbel", (0, t / 2, -3), (0.6, 0, 0), cubes=[cube("barbel", (0, 0, 0), (0, 5, 1))],
                                  children=[part("barbel_tip", (0, 5, 0), cubes=[cube("lure", (-0.5, 0, -0.5), (1, 1, 1))])]))
    if v.get("frill"):
        head_children.append(part("frill", (0, 0, -0.5), cubes=[cube("fin", (-t / 2 - 1.5, -t / 2 - 1, 0), (t + 3, t + 2, 0))]))
    chain = None
    for i in reversed(range(n)):
        size = t - i * (0.25 if not v.get("taper") else 0.4)
        size = max(1.2, size)
        seg_len = 5
        cubes = [cube("body" if i < n - 1 else "tail", (-size / 2, -size / 2, 0), (size, size, seg_len))]
        if not v.get("smooth"):
            cubes.append(cube("dorsal", (0, -size / 2 - 1.5, 0), (0, 1.5, seg_len)))
        children = [chain] if chain else []
        if i == n - 1:
            children.append(part("tail_fin", (0, 0, seg_len), cubes=[cube("glow_tip" if v.get("glow_tail") else "tail_fin", (0, -2, 0), (0, 4, 3))]))
        chain = part(f"segment{i}", (0, 0, 0 if i == 0 else seg_len), cubes=cubes, children=children)
    if v.get("twin_head"):
        # Two heads on one neck, splayed apart.
        head = part("head", (-t * 0.6, 0, 0), (0, 0.35, 0), cubes=head_cubes, children=head_children)
        twin = part("twin_head", (t * 0.6, 0, 0), (0, -0.35, 0), cubes=[dict(c) for c in head_cubes],
                    children=[part("twin_jaw", (0, t / 2 - 1, -0.5), (0.3, 0, 0),
                                   cubes=[cube("jaw", (-t / 2 + 0.25, 0, -jaw_len), (t - 0.5, 1, jaw_len))])])
        body = part("body", (0, y0, -n * 5 / 2), cubes=[], children=[head, twin, chain])
    else:
        head = part("head", (0, 0, 0), cubes=head_cubes, children=head_children)
        body = part("body", (0, y0, -n * 5 / 2), cubes=[], children=[head, chain])
    return {"kind": "long", "root": [body], "length": n * 5 + 5 + 3, "segments": n}


def shark(v):
    """Sharks. Options: hammer, snout (goblin), bulky (greenland), whip (ghost shark), big_eyes, whale (flat flukes)."""
    w, h = (8, 7) if v.get("bulky") else (6, 6)
    body_len = 12
    y0 = 24 - h / 2 - 1
    head_cubes = [cube("head", (-w / 2 + 0.5, -h / 2 + 0.5, -5), (w - 1, h - 1, 5))]
    head_children = [part("jaw", (0, h / 2 - 1.5, -1), cubes=[cube("jaw", (-w / 2 + 1, 0, -4), (w - 2, 1.5, 4))],
                          children=[part("teeth", (0, 0, -4), cubes=[cube("teeth", (-w / 2 + 1, -0.5, 0), (w - 2, 1, 0))])])]
    if v.get("hammer"):
        head_cubes.append(cube("head", (-7, -h / 2 + 1, -6), (14, 2, 3)))
    else:
        head_cubes.append(cube("snout", (-w / 2 + 1.5, -h / 2 + 1, -7 - (5 if v.get("snout") else 0)), (w - 3, h - 3, 2 + (5 if v.get("snout") else 0))))
    if v.get("big_eyes"):
        head_cubes.append(cube("eye_bulge", (-w / 2, -h / 2 + 1, -4), (w, 2, 2), inflate=0.2))
    tail_fin = [cube("tail_fin", (0, -7, 0), (0, 7, 5)), cube("tail_fin", (0, 0, 0), (0, 4, 3))]
    if v.get("whip"):
        tail_fin = [cube("tail_fin", (0, -1, 0), (0, 2, 12))]
    if v.get("whale"):
        # A whale's flukes lie flat, and it has only a little hump for a dorsal fin.
        tail_fin = [cube("tail_fin", (-7, 0, 0), (14, 0, 5))]
    body = part("body", (0, y0, 0), cubes=[cube("body", (-w / 2, -h / 2, -body_len / 2), (w, h, body_len))] + mutations(v, w, h, body_len), children=[
        part("head", (0, 0, -body_len / 2), cubes=head_cubes, children=head_children),
        part("tail", (0, -0.5, body_len / 2), cubes=[cube("tail", (-w / 4, -h / 4, 0), (w / 2, h / 2, 8))],
             children=[part("tail_fin", (0, 0, 8), (0.5, 0, 0), cubes=tail_fin)]),
        part("dorsal", (0, -h / 2, -2 if not v.get("whale") else 3), (-0.6, 0, 0),
             cubes=[cube("dorsal", (0, -6 if not v.get("whale") else -2, 0), (0, 6 if not v.get("whale") else 2, 4))]),
        part("left_fin", (w / 2, h / 2 - 1, -3), (0.3, 0.3, 0.5), cubes=[cube("fin", (0, 0, 0), (7, 0, 4))]),
        part("right_fin", (-w / 2, h / 2 - 1, -3), (0.3, -0.3, -0.5), cubes=[cube("fin", (-7, 0, 0), (7, 0, 4), mirror=True)]),
        part("anal_fin", (0, h / 2, 4), (0.5, 0, 0), cubes=[cube("fin", (0, 0, 0), (0, 2, 3))]),
    ])
    return {"kind": "fish", "root": [body], "length": body_len + 7 + 8 + 5}


def ray(v):
    """Rays and flatfish. Options: manta (cephalic fins, short tail), flat (flounder: oval, no tail)."""
    flat = v.get("flat")
    y0 = 23
    span = 6 if flat else 9
    body = part("body", (0, y0, 0), cubes=[cube("body", (-3, -1, -5), (6, 2, 10)), cube("head", (-2, -1.5, -7), (4, 2, 2))], children=[
        part("left_wing", (3, 0, 0), cubes=[cube("wing", (0, -0.5, -4), (span, 1, 8))]),
        part("right_wing", (-3, 0, 0), cubes=[cube("wing", (-span, -0.5, -4), (span, 1, 8), mirror=True)]),
    ] + ([] if flat else [part("tail", (0, 0, 5), cubes=[cube("tail", (-0.5, -0.5, 0), (1, 1, 6 if v.get("manta") else 14))])])
      + ([part("left_horn", (1.5, 0, -7), (0.3, 0, 0), cubes=[cube("fin", (0, -0.5, -3), (1, 1, 3))]),
          part("right_horn", (-1.5, 0, -7), (0.3, 0, 0), cubes=[cube("fin", (-1, -0.5, -3), (1, 1, 3))])] if v.get("manta") else []))
    return {"kind": "ray", "root": [body], "length": 14}


def jelly(v):
    """Jellyfish. Options: tentacles, length, box, oval (comb jelly), big."""
    n = v.get("tentacles", 8)
    length = v.get("length", 10)
    size = 10 if v.get("big") else 8
    box = v.get("box")
    oval = v.get("oval")
    y0 = 12
    bell = [cube("bell", (-size / 2, -size / 2 - (3 if oval else 0), -size / 2), (size, size - 2 + (6 if oval else 0), size))]
    if not oval:
        bell.append(cube("rim", (-size / 2 - 0.5, size / 2 - 2, -size / 2 - 0.5), (size + 1, 1, size + 1)))
        bell.append(cube("inner", (-2, -2, -2), (4, 5, 4)))
    tentacles = []
    for i in range(0 if oval else n):
        a = 2 * PI * i / n
        r = size / 2 - 1 if not box else size / 2 - 0.5
        x, z = (math.cos(a) * r, math.sin(a) * r) if not box else ([1, 1, -1, -1][i % 4] * r, [1, -1, 1, -1][i % 4] * r)
        tentacles.append(part(f"tentacle{i}", (x, size / 2 - 1, z), (0, -a, 0),
                              cubes=[cube("tentacle", (-0.5, 0, 0), (1, length, 0))]))
    if not oval:
        for i in range(4):
            a = 2 * PI * i / 4 + PI / 4
            tentacles.append(part(f"arm{i}", (math.cos(a) * 1.5, size / 2 - 1, math.sin(a) * 1.5), (0, -a, 0),
                                  cubes=[cube("arm", (-1, 0, 0), (2, length * 0.6, 0))]))
    body = part("body", (0, y0, 0), cubes=bell, children=tentacles)
    return {"kind": "jelly", "root": [body], "length": size, "tentacles": n}


def squid(v):
    """Squids and octopuses. Options: arms, tentacle_len, cape (vampire squid), dumbo (ear fins, round)."""
    dumbo = v.get("dumbo")
    mantle_len = 6 if dumbo else 14
    mw = 7 if dumbo else 6
    y0 = 18 if not dumbo else 17
    arms = []
    n = v.get("arms", 8)
    for i in range(n):
        a = 2 * PI * i / n
        x, y = math.cos(a) * mw / 3, math.sin(a) * mw / 3
        arms.append(part(f"arm{i}", (x, y, -2 if not dumbo else 0), (0 if not dumbo else 1.2, 0, 0),
                         cubes=[cube("arm", (-0.5, -0.5, -8 if not dumbo else 0), (1, 1, 8) if not dumbo else (1, 6, 1))]))
    if v.get("tentacle_len"):
        for i, x in enumerate((-1, 1)):
            arms.append(part(f"tentacle{i}", (x, 0.5, -2), cubes=[cube("arm", (-0.5, -0.5, -v["tentacle_len"]), (1, 1, v["tentacle_len"]))],
                             children=[part(f"club{i}", (0, 0, -v["tentacle_len"]), cubes=[cube("arm", (-1, -1, -3), (2, 2, 3))])]))
    head_cubes = [cube("head", (-mw / 2 + 0.5, -mw / 2 + 0.5, -3), (mw - 1, mw - 1, 3))]
    mantle = [cube("mantle", (-mw / 2, -mw / 2, 0), (mw, mw, mantle_len))]
    fins = []
    if dumbo:
        fins = [part("left_fin", (mw / 2, -1, 2), cubes=[cube("wing", (0, -1, -1), (3, 0, 3))]),
                part("right_fin", (-mw / 2, -1, 2), cubes=[cube("wing", (-3, -1, -1), (3, 0, 3), mirror=True)])]
    else:
        fins = [part("left_fin", (mw / 2, 0, mantle_len - 4), cubes=[cube("wing", (0, 0, 0), (3, 0, 4))]),
                part("right_fin", (-mw / 2, 0, mantle_len - 4), cubes=[cube("wing", (-3, 0, 0), (3, 0, 4), mirror=True)])]
    if v.get("cape"):
        fins.append(part("cape", (0, 0, -3), cubes=[cube("wing", (-5, -5, -6), (10, 10, 0))]))
    body = part("body", (0, y0, 0), cubes=mantle, children=[part("head", (0, 0, 0), cubes=head_cubes, children=arms)] + fins)
    if dumbo:
        body["rot"] = (-PI / 2, 0, 0)
    return {"kind": "squid", "root": [body], "length": mantle_len + 3 + 8, "arms": n}


def seahorse(v):
    y0 = 14
    body = part("body", (0, y0, 0), cubes=[cube("body", (-1.5, -4, -1.5), (3, 8, 3))], children=[
        part("head", (0, -4, 0), (0.3, 0, 0), cubes=[cube("head", (-1.5, -3, -1.5), (3, 3, 3)), cube("snout", (-0.5, -2, -5), (1, 1, 3.5))]),
        part("dorsal", (0, -1, 1.5), cubes=[cube("dorsal", (0, -2, 0), (0, 4, 2))]),
        part("tail", (0, 4, 0), (0.3, 0, 0), cubes=[cube("tail", (-1, 0, -1), (2, 4, 2))],
             children=[part("tail_fin", (0, 4, 0), (0.9, 0, 0), cubes=[cube("tail", (-0.5, 0, -0.5), (1, 3, 1))])]),
    ])
    return {"kind": "upright", "root": [body], "length": 12}


# --- the land creatures of the End ----------------------------------------------------------------

def beetle(v):
    """Six-legged armoured things: a shell over a low body, mandibles and feelers. Options: horn."""
    leg_len = 6
    splay = 0.75
    reach = math.cos(splay) * leg_len
    y0 = 24 - reach - 1.0
    head_children = [
        part("left_mandible", (1.2, 0.8, -3), (0, -0.35, 0), cubes=[cube("jaw", (-0.5, 0, -2.5), (1, 1, 2.5))]),
        part("right_mandible", (-1.2, 0.8, -3), (0, 0.35, 0), cubes=[cube("jaw", (-0.5, 0, -2.5), (1, 1, 2.5), mirror=True)]),
        part("left_antenna", (1, -1.5, -2.5), (-0.7, -0.4, 0), cubes=[cube("barbel", (0, -5, -0.5), (0, 5, 1))]),
        part("right_antenna", (-1, -1.5, -2.5), (-0.7, 0.4, 0), cubes=[cube("barbel", (0, -5, -0.5), (0, 5, 1))]),
    ]
    if v.get("horn"):
        head_children.append(part("horn", (0, -1.5, -2), (-0.6, 0, 0), cubes=[cube("crystal", (-0.5, -5, -0.5), (1, 5, 1))]))
    if v.get("pincers"):
        for side in (1, -1):
            head_children.append(part("left_pincer" if side > 0 else "right_pincer", (side * 2.2, 0.5, -2.5), (0, -side * 0.5, 0),
                                      cubes=[cube("shell", (-0.75, -0.75, -4), (1.5, 1.5, 4)), cube("shell", (-1.25, -1, -7), (2.5, 2, 3))]))
    head = part("head", (0, -0.5, -4.5), cubes=[cube("head", (-2, -1.5, -3), (4, 3, 3))], children=head_children)
    legs = []
    for z in (-2.5, 0.5, 3.5):
        for side in (1, -1):
            # Each leg reaches out sideways and down to the ground.
            legs.append(part(f"leg{len(legs)}", (side * 2.8, 1.2, z), (0, 0, -side * splay),
                             cubes=[cube("leg", (-0.5, 0, -0.5), (1, leg_len, 1), mirror=side < 0)]))
    body_children = [head] + legs
    if v.get("stinger"):
        sting = part("tail_tip", (0, 0, 5), (1.3, 0, 0), cubes=[cube("shell", (-0.75, -0.75, 0), (1.5, 1.5, 4)),
                                                              cube("teeth", (-0.5, -0.5, 4), (1, 1, 2))])
        body_children.append(part("tail", (0, -2, 4.5), (0.9, 0, 0), cubes=[cube("shell", (-1, -1, 0), (2, 2, 5))], children=[sting]))
    body = part("body", (0, y0, 0), cubes=[cube("shell", (-3.5, -4, -4.5), (7, 4, 9)), cube("body", (-3, 0, -4), (6, 2, 8))],
                children=body_children)
    return {"kind": "insect", "root": [body], "length": 15}


def golem(v):
    """Hulking two-legged things of stone and crystal, arms hanging to their knees."""
    leg_h = 11
    hip = 24 - leg_h
    crystals = []
    for i, (x, z, lean) in enumerate([(-3.5, 1.5, -0.35), (3.0, 2.0, 0.3), (0.0, 2.5, 0.0), (-1.0, 0.0, -0.15), (2.0, -1.0, 0.2)]):
        crystals.append(part(f"crystal{i}", (x, 0.5, z), (0.4, 0, lean), cubes=[cube("crystal", (-1, -5 - i % 3, -1), (2, 5 + i % 3, 2))]))
    body = part("body", (0, hip - 10, 0), cubes=[cube("body", (-6, 0, -3.5), (12, 10, 7))], children=[
        part("head", (0, 0.5, -1.5), cubes=[cube("head", (-3, -7, -3.5), (6, 7, 6))]),
        part("left_arm", (6, 1.5, 0), cubes=[cube("limb", (0, -1.5, -2), (4, 16, 4))]),
        part("right_arm", (-6, 1.5, 0), cubes=[cube("limb", (-4, -1.5, -2), (4, 16, 4), mirror=True)]),
    ] + crystals)
    left_leg = part("left_leg", (2.8, hip, 0), cubes=[cube("leg", (-2, 0, -2), (4, leg_h, 4))])
    right_leg = part("right_leg", (-2.8, hip, 0), cubes=[cube("leg", (-2, 0, -2), (4, leg_h, 4), mirror=True)])
    return {"kind": "biped", "root": [body, left_leg, right_leg], "length": 12}


def stalker(v):
    """Tall prowlers striding on four stilts, a long neck carrying the head high."""
    leg_h = 15
    top = 24 - leg_h
    body = part("body", (0, top - 3, 0), cubes=[cube("body", (-3, -2, -6), (6, 5, 12))], children=[
        part("head", (0, -1, -5.5), (0.45, 0, 0), cubes=[cube("body", (-1.5, -7, -1.5), (3, 8, 3)), cube("head", (-2, -10, -5), (4, 4, 6))],
             children=[part("jaw", (0, -6.4, -3), cubes=[cube("jaw", (-1.5, 0, -2.5), (3, 1, 3))])]),
        part("tail", (0, -1, 6), (-0.35, 0, 0), cubes=[cube("tail", (-0.5, -0.5, 0), (1, 1, 9))]),
    ])
    legs = [part(name, (x, top, z), cubes=[cube("leg", (-1, 0, -1), (2, leg_h, 2), mirror=x < 0)])
            for name, x, z in [("left_front_leg", 2.2, -4), ("right_front_leg", -2.2, -4), ("left_hind_leg", 2.2, 4),
                               ("right_hind_leg", -2.2, 4)]]
    return {"kind": "quadruped", "root": [body] + legs, "length": 18}


def hopper(v):
    """Small round hopping critters with long ears."""
    body = part("body", (0, 19, 0), cubes=[cube("body", (-3, -4, -3), (6, 6, 6)), cube("head", (-2.5, -3, -4), (5, 4, 1))], children=[
        part("left_ear", (1.5, -4, -1), (-0.2, 0, -0.25), cubes=[cube("ear", (-0.5, -6, -1), (1, 6, 2))]),
        part("right_ear", (-1.5, -4, -1), (-0.2, 0, 0.25), cubes=[cube("ear", (-0.5, -6, -1), (1, 6, 2), mirror=True)]),
        part("tail", (0, -1, 3), cubes=[cube("tail", (-1, -1, 0), (2, 2, 2))]),
    ])
    feet = [part("left_foot", (1.8, 22, 0), cubes=[cube("leg", (-1, 0, -1.5), (2, 2, 3))]),
            part("right_foot", (-1.8, 22, 0), cubes=[cube("leg", (-1, 0, -1.5), (2, 2, 3), mirror=True)])]
    return {"kind": "hopper", "root": [body] + feet, "length": 8}


# --- the beasts and birds of the Overworld ----------------------------------------------------------

def mammal(v):
    """Four-legged beasts, from capybaras to elephants and giraffes. Options: body (w, h, l), leg, leg_w, hind (shorter
    hind legs), neck, neck_w, neck_pitch, head (w, h, l), head_pitch, snout (w, h, l), ears (small/round/long/big/
    elephant), horns (rhino/bull/ossicone), antlers (deer/moose), tusks (up/forward), trunk, mane (head/neck/shaggy),
    hump, tail (thin/tuft/bushy/stub/long), tail_len."""
    bw, bh, bl = v.get("body", (6, 6, 12))
    leg = v.get("leg", 8)
    lw = v.get("leg_w", 2)
    hind = v.get("hind", leg)
    hip = 24 - leg
    hw, hh, hl = v.get("head", (4, 4, 6))
    neck = v.get("neck", 0)
    pitch = v.get("neck_pitch", 0.4)

    # The head, with whatever grows on it.
    head_cubes = [cube("head", (-hw / 2, -hh / 2, -hl), (hw, hh, hl))]
    if v.get("snout"):
        sw, sh, sl = v["snout"]
        head_cubes.append(cube("snout", (-sw / 2, hh / 2 - sh, -hl - sl), (sw, sh, sl)))
    snout_len = v["snout"][2] if v.get("snout") else 0
    head_children = []
    ears = v.get("ears")
    for side in (1, -1) if ears else ():
        name = "left_ear" if side > 0 else "right_ear"
        if ears == "elephant":
            head_children.append(part(name, (side * hw / 2, -hh / 2 + 1, -hl * 0.6), (0, side * 0.45, 0),
                                      cubes=[cube("ear", (0 if side > 0 else -1, 0, 0), (1, 10, 8), mirror=side < 0)]))
        else:
            ew, eh, ed = {"small": (1, 2, 1), "round": (2, 2, 1), "long": (1, 4, 2), "big": (1, 3, 2)}[ears]
            head_children.append(part(name, (side * (hw / 2 - 0.5), -hh / 2, -hl * 0.3), (0, 0, side * 0.35),
                                      cubes=[cube("ear", (-ew / 2, -eh, -ed / 2), (ew, eh, ed), mirror=side < 0)]))
    horns = v.get("horns")
    if horns == "rhino":
        head_children.append(part("horn", (0, hh / 2 - (v["snout"][1] if v.get("snout") else 2), -hl - snout_len + 1.5), (-0.35, 0, 0),
                                  cubes=[cube("horn", (-1, -5, -1), (2, 5, 2))]))
        head_children.append(part("second_horn", (0, -hh / 2, -hl * 0.75), (-0.2, 0, 0), cubes=[cube("horn", (-0.5, -2, -0.5), (1, 2, 1))]))
    elif horns == "bull":
        for side in (1, -1):
            head_children.append(part("left_horn" if side > 0 else "right_horn", (side * hw / 2, -hh / 2 + 1, -hl * 0.7), (0, 0, 0),
                                      cubes=[cube("horn", (0 if side > 0 else -3, -0.5, -0.5), (3, 1, 1)),
                                             cube("horn", (2.2 if side > 0 else -3.2, -3, -0.5), (1, 3, 1))]))
    elif horns == "ossicone":
        for side in (1, -1):
            head_children.append(part("left_horn" if side > 0 else "right_horn", (side * 1, -hh / 2, -hl * 0.35),
                                      cubes=[cube("horn", (-0.5, -3, -0.5), (1, 3, 1))]))
    antlers = v.get("antlers")
    for side in (1, -1) if antlers else ():
        name = "left_horn" if side > 0 else "right_horn"
        if antlers == "deer":
            head_children.append(part(name, (side * 1.2, -hh / 2, -hl * 0.35), (0.25, 0, side * 0.45),
                                      cubes=[cube("horn", (-0.5, -8, -0.5), (1, 8, 1)), cube("horn", (-0.5, -5, -3), (1, 1, 3)),
                                             cube("horn", (-0.5, -8, -2.5), (1, 1, 2.5))]))
        else:
            # A moose's broad palms.
            x0 = 0 if side > 0 else -8
            head_children.append(part(name, (side * hw / 2, -hh / 2 + 0.5, -hl * 0.4), (0, 0, -side * 0.75),
                                      cubes=[cube("horn", (0 if side > 0 else -3, -1, -0.5), (3, 1, 1)),
                                             cube("horn", (x0, -2, -3), (8, 1, 5))]))
    if v.get("tusks"):
        up = v["tusks"] == "up"
        for side in (1, -1):
            head_children.append(part("left_tusk" if side > 0 else "right_tusk", (side * (hw / 2 - 1), hh / 2 - 1, -hl - snout_len + 1),
                                      (-0.9 if not up else 0.3, 0, 0),
                                      cubes=[cube("tusk", (-0.5, -3 if up else 0, -0.5), (1, 3, 1) if up else (1, 6, 1))]))
    if v.get("trunk"):
        head_children.append(part("trunk", (0, hh / 2 - 2, -hl), (-0.12, 0, 0), cubes=[cube("snout", (-1.5, 0, -1.5), (3, v["trunk"], 3))]))
    mane = v.get("mane")
    if mane == "head":
        head_cubes.append(cube("mane", (-hw / 2 - 2, -hh / 2 - 2, -2.5), (hw + 4, hh + 4, 4)))

    # The neck, if it has one; the head rides on it or right on the front of the body.
    if neck:
        nw = v.get("neck_w", 3)
        neck_cubes = [cube("body", (-nw / 2, -neck, -nw / 2), (nw, neck, nw))]
        if mane == "neck":
            neck_cubes.append(cube("mane", (-0.5, -neck - 1, nw / 2 - 0.5), (1, neck + 1, 2)))
        head = part("head", (0, -neck + 1, 0), (-pitch + v.get("head_pitch", 0.3), 0, 0), cubes=head_cubes, children=head_children)
        front = part("neck", (0, -bh / 2 + 1.5, -bl / 2 + 1.5), (pitch, 0, 0), cubes=neck_cubes, children=[head])
    else:
        front = part("head", (0, -bh / 2 + hh / 2 - 1 + v.get("head_drop", 0), -bl / 2 + 1), (v.get("head_pitch", 0.0), 0, 0),
                     cubes=head_cubes, children=head_children)

    body_cubes = [cube("body", (-bw / 2, -bh / 2, -bl / 2), (bw, bh, bl))]
    if v.get("hump"):
        body_cubes.append(cube("body", (-bw * 0.35, -bh / 2 - 3, -bl / 2 + 1), (bw * 0.7, 3, bl * 0.35)))
    if mane == "shaggy":
        body_cubes.append(cube("mane", (-bw / 2 - 1, -bh / 2 - 1.5, -bl / 2 - 1), (bw + 2, bh + 2.5, bl * 0.45)))

    tail = v.get("tail", "thin")
    tl = v.get("tail_len", 8)
    body_children = [front]
    if tail:
        cubes, droop = {
            "thin": ([cube("tail", (-0.5, 0, 0), (1, 1, tl))], -0.9),
            "tuft": ([cube("tail", (-0.5, 0, 0), (1, 1, tl)), cube("mane", (-1, -0.5, tl - 1), (2, 2, 3))], -1.0),
            "bushy": ([cube("tail", (-1.5, -1, 0), (3, 3, tl))], -0.6),
            "stub": ([cube("tail", (-1, 0, 0), (2, 2, 2))], -0.3),
            "long": ([cube("tail", (-0.75, 0, 0), (1.5, 1.5, tl))], -0.45),
        }[tail]
        body_children.append(part("tail", (0, -bh / 2 + 1, bl / 2), (droop, 0, 0), cubes=cubes))
    slope = -math.atan2(leg - hind, bl) if hind != leg else 0.0
    body = part("body", (0, hip - bh / 2, 0), (slope, 0, 0), cubes=body_cubes, children=body_children)

    fx = bw / 2 - lw / 2 - 0.2
    fz = -bl / 2 + lw / 2 + 1
    hz = bl / 2 - lw / 2 - 1
    legs = []
    for name, x, z, height in [("left_front_leg", fx, fz, leg), ("right_front_leg", -fx, fz, leg),
                               ("left_hind_leg", fx, hz, hind), ("right_hind_leg", -fx, hz, hind)]:
        legs.append(part(name, (x, 24 - height, z), cubes=[cube("leg", (-lw / 2, 0, -lw / 2), (lw, height, lw), mirror=x < 0)]))
    return {"kind": "quadruped", "root": [body] + legs, "length": bl + hl + snout_len + (2 if neck else 0), "seat": leg + bh}


def primate(v):
    """Apes and monkeys: long arms, a round head with a muzzle, and maybe a long curling tail. Options: knuckles (a
    gorilla's knuckle-walk and bulk), tail."""
    big = v.get("knuckles", False)
    leg_h = 7 if big else 6
    hip = 24 - leg_h
    bw, bh, bd = (11, 11, 7) if big else (5, 7, 4)
    lean = 0.95 if big else 0.6
    arm_len = 15 if big else 9
    aw = 4 if big else 2
    hw, hh, hl = (8, 7, 7) if big else (5, 5, 4)
    head_cubes = [cube("head", (-hw / 2, -hh, -hl / 2), (hw, hh, hl)),
                  cube("snout", (-hw * 0.35, -hh * 0.45, -hl / 2 - 1.5), (hw * 0.7, hh * 0.45, 1.5))]
    if big:
        head_cubes.append(cube("head", (-hw / 2, -hh - 1, -hl / 2 - 0.5), (hw, 2, 2)))
    head_children = [part("left_ear", (hw / 2, -hh * 0.6, 0), cubes=[cube("ear", (0, -1, -0.5), (1, 2, 1))]),
                     part("right_ear", (-hw / 2, -hh * 0.6, 0), cubes=[cube("ear", (-1, -1, -0.5), (1, 2, 1), mirror=True)])]
    children = [part("head", (0, -bh + 0.5, -bd * 0.2), (-lean, 0, 0), cubes=head_cubes, children=head_children)]
    for side in (1, -1):
        children.append(part("left_arm" if side > 0 else "right_arm", (side * (bw / 2 + aw / 2), -bh + 1.5, 0), (-lean, 0, 0),
                             cubes=[cube("limb", (-aw / 2, 0, -aw / 2), (aw, arm_len, aw), mirror=side < 0)]))
    if v.get("tail"):
        tip = part("tail_tip", (0, 0, 9), (1.0, 0, 0), cubes=[cube("tail", (-0.5, -0.5, 0), (1, 1, 5))])
        children.append(part("tail", (0, -1, bd / 2), (0.7, 0, 0), cubes=[cube("tail", (-0.5, -0.5, 0), (1, 1, 9))], children=[tip]))
    body = part("body", (0, hip, 0.5), (lean, 0, 0), cubes=[cube("body", (-bw / 2, -bh, -bd / 2), (bw, bh, bd))], children=children)
    lw = 4 if big else 2
    legs = [part(name, (x, hip, 1.0), cubes=[cube("leg", (-lw / 2, 0, -lw / 2), (lw, leg_h, lw), mirror=x < 0)])
            for name, x in (("left_leg", bw * 0.3), ("right_leg", -bw * 0.3))]
    return {"kind": "biped", "root": [body] + legs, "length": 10}


def bird(v):
    """Birds that walk: ostriches, flamingos and penguins. Options: body (w, h, l), leg, neck, neck_pitch, beak, penguin."""
    if v.get("penguin"):
        body = part("body", (0, 22, 0), cubes=[cube("body", (-4, -11, -3.5), (8, 11, 7))], children=[
            part("head", (0, -11, -0.5), cubes=[cube("head", (-3, -5, -3), (6, 5, 6)), cube("beak", (-1, -3, -5), (2, 1.5, 2))]),
            part("left_wing", (4, -10, 0), (0, 0, -0.15), cubes=[cube("wing", (0, 0, -1.5), (1, 8, 3))]),
            part("right_wing", (-4, -10, 0), (0, 0, 0.15), cubes=[cube("wing", (-1, 0, -1.5), (1, 8, 3), mirror=True)]),
            part("tail", (0, -1, 3.5), (-0.6, 0, 0), cubes=[cube("tail", (-1.5, 0, 0), (3, 1, 2))]),
        ])
        feet = [part(name, (x, 22, 0), cubes=[cube("leg", (-1, 0, -2.5), (2, 2, 3), mirror=x < 0)]) for name, x in (("left_leg", 2), ("right_leg", -2))]
        return {"kind": "biped", "root": [body] + feet, "length": 8}
    bw, bh, bl = v.get("body", (6, 6, 9))
    leg = v.get("leg", 10)
    neck = v.get("neck", 8)
    pitch = v.get("neck_pitch", 0.15)
    hip = 24 - leg
    beak = v.get("beak", (1, 1, 2))
    head = part("head", (0, -neck, 0), (-pitch + v.get("head_pitch", 0.0), 0, 0),
                cubes=[cube("head", (-1.5, -2.5, -3), (3, 3, 4)), cube("beak", (-beak[0] / 2, -1.5, -3 - beak[2]), beak)])
    body = part("body", (0, hip - bh / 2, 0), cubes=[cube("body", (-bw / 2, -bh / 2, -bl / 2), (bw, bh, bl)),
                                                      cube("tail", (-bw / 2 + 1, -bh / 2 - 1, bl / 2 - 1), (bw - 2, 3, 3))], children=[
        part("neck", (0, -bh / 2 + 1, -bl / 2 + 1), (pitch, 0, 0), cubes=[cube("body", (-1, -neck, -1), (2, neck, 2))], children=[head]),
        part("left_wing", (bw / 2, -bh / 2 + 1, -bl / 2 + 1.5), cubes=[cube("wing", (0, 0, 0), (1, bh - 1, bl - 2))]),
        part("right_wing", (-bw / 2, -bh / 2 + 1, -bl / 2 + 1.5), cubes=[cube("wing", (-1, 0, 0), (1, bh - 1, bl - 2), mirror=True)]),
    ])
    legs = [part(name, (x, hip, 0.5), cubes=[cube("leg", (-0.5, 0, -0.5), (1, leg, 1)), cube("leg", (-1, leg - 1, -2.5), (2, 1, 3))])
            for name, x in (("left_leg", 1.5), ("right_leg", -1.5))]
    return {"kind": "biped", "root": [body] + legs, "length": bl + 4, "seat": leg + bh}


def flybird(v):
    """Birds of the open sky: eagles, vultures, owls and toucans. Options: body (w, h, l), span, beak (w, h, l), owl."""
    bw, bh, bl = v.get("body", (5, 5, 9))
    span = v.get("span", 12)
    beak = v.get("beak", (2, 2, 2))
    owl = v.get("owl", False)
    hs = (6, 6, 5) if owl else (4, 4, 4)
    head_children = []
    if owl:
        head_children += [part("left_ear", (2, -6, -1), (0, 0, 0.3), cubes=[cube("ear", (-0.5, -2, -0.5), (1, 2, 1))]),
                          part("right_ear", (-2, -6, -1), (0, 0, -0.3), cubes=[cube("ear", (-0.5, -2, -0.5), (1, 2, 1), mirror=True)])]
    head = part("head", (0, -bh / 2 + 1, -bl / 2 + 1), cubes=[cube("head", (-hs[0] / 2, -hs[1] + 1, -hs[2]), hs),
                                                             cube("beak", (-beak[0] / 2, -1.5, -hs[2] - beak[2]), beak)],
                children=head_children)
    body = part("body", (0, 18, 0), cubes=[cube("body", (-bw / 2, -bh / 2, -bl / 2), (bw, bh, bl)),
                                           cube("leg", (-1.5, bh / 2, 0), (1, 2, 1)), cube("leg", (0.5, bh / 2, 0), (1, 2, 1))], children=[
        head,
        part("left_wing", (bw / 2, -bh / 2 + 1, -bl / 2 + 2), cubes=[cube("wing", (0, 0, 0), (span, 1, bl - 2))]),
        part("right_wing", (-bw / 2, -bh / 2 + 1, -bl / 2 + 2), cubes=[cube("wing", (-span, 0, 0), (span, 1, bl - 2), mirror=True)]),
        part("tail", (0, -bh / 2 + 1, bl / 2), cubes=[cube("tail", (-2, 0, 0), (4, 1, 5))]),
    ])
    return {"kind": "bird", "root": [body], "length": bl + 4}


def reptile(v):
    """Low, long reptiles on sprawling legs: crocodiles and their kin. Options: body (w, h, l), head (w, h, l), tail."""
    bw, bh, bl = v.get("body", (7, 4, 14))
    hw, hh, hl = v.get("head", (5, 3, 10))
    leg_len, splay = 4, 0.8
    hip = 24 - leg_len * math.cos(splay)
    tl = v.get("tail", 10)
    head = part("head", (0, -bh / 2 + 1, -bl / 2), cubes=[cube("head", (-hw / 2, -hh / 2, -hl), (hw, hh, hl))], children=[
        part("jaw", (0, hh / 2, -0.5), cubes=[cube("jaw", (-hw / 2 + 0.25, 0, -hl + 0.5), (hw - 0.5, 1, hl - 0.5))]),
    ])
    tip = part("tail_fin", (0, 0.5, tl), cubes=[cube("tail", (-1, 0, 0), (2, 2, tl))])
    tail = part("tail", (0, -bh / 2 + 1, bl / 2), (-0.06, 0, 0), cubes=[cube("tail", (-2, 0, 0), (4, 3, tl))], children=[tip])
    body = part("body", (0, hip - bh / 2, 0), cubes=[cube("body", (-bw / 2, -bh / 2, -bl / 2), (bw, bh, bl)),
                                                      cube("spines", (-bw / 2 + 1, -bh / 2 - 1, -bl / 2 + 1), (bw - 2, 1, bl - 2))],
                children=[head, tail])
    legs = []
    for name, x, z in [("left_front_leg", bw / 2, -bl / 2 + 2), ("right_front_leg", -bw / 2, -bl / 2 + 2),
                       ("left_hind_leg", bw / 2, bl / 2 - 2), ("right_hind_leg", -bw / 2, bl / 2 - 2)]:
        legs.append(part(name, (x, hip, z), (0, 0, -splay if x > 0 else splay),
                         cubes=[cube("leg", (-1, 0, -1), (2, leg_len, 2), mirror=x < 0)]))
    return {"kind": "quadruped", "root": [body] + legs, "length": bl + hl + tl}


PLANS = {"fish": fish, "deep": deep, "long": long, "shark": shark, "ray": ray, "jelly": jelly, "squid": squid, "seahorse": seahorse,
         "beetle": beetle, "golem": golem, "stalker": stalker, "hopper": hopper, "mammal": mammal, "primate": primate, "bird": bird,
         "flybird": flybird, "reptile": reptile}
# The body plans of creatures that live on land or in the air.
LAND_PLANS = {"beetle", "golem", "stalker", "hopper", "mammal", "primate", "bird", "flybird", "reptile"}
# Land creatures with their eyes on the front of the face; the rest have them on the sides of the head.
FRONT_EYED = {"beetle", "golem", "stalker", "hopper", "primate"}


# --- texture layout -----------------------------------------------------------------------------

def iter_cubes(parts, out=None):
    out = [] if out is None else out
    for p in parts:
        for c in p["cubes"]:
            out.append(c)
        iter_cubes(p["children"], out)
    return out


def net_size(c):
    w, h, d = (math.ceil(s - 1e-6) if s > 0 else 0 for s in c["size"])
    return 2 * d + 2 * w, d + h, (w, h, d)


def layout(model):
    """Assigns texture offsets to every cube (shelf packing). Returns (tex_w, tex_h)."""
    cubes = iter_cubes(model["root"])
    order = sorted(cubes, key=lambda c: -net_size(c)[1])
    for tex_w in (32, 64, 128, 256):
        x = y = shelf = 0
        ok = True
        for c in order:
            nw, nh, _ = net_size(c)
            nw, nh = max(nw, 1), max(nh, 1)
            if nw > tex_w:
                ok = False
                break
            if x + nw > tex_w:
                x, y, shelf = 0, y + shelf, 0
            c["uv"] = (x, y)
            x += nw
            shelf = max(shelf, nh)
        height = y + shelf
        if ok and height <= tex_w:
            tex_h = 32
            while tex_h < height:
                tex_h *= 2
            return tex_w, tex_h
    raise ValueError("model too big for a 256x256 texture")
