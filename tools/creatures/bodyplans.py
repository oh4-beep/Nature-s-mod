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
    head = part("head", (0, 0, -body_len / 2), cubes=[
        cube("head", (-w / 2, -h / 2 + 0.5, -head_len), (w, h - 1, head_len)),
        cube("snout", (-w / 2 + 0.5, -h / 2 + 1.5, -head_len - 1), (w - 1, h - 3, 1)),
    ], children=head_children + [part("jaw", (0, h / 2 - 1.5, -0.5), cubes=[cube("jaw", (-w / 2 + 0.25, 0, -head_len), (w - 0.5, 1, head_len))])])
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
    body = part("body", (0, y0, 0), cubes=[cube("body", (-w / 2, -h / 2, -body_len / 2), (w, h, body_len))], children=[head, tail] + fins)
    return {"kind": "fish", "root": [body], "length": body_len + head_len + tail_len + 5}


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
    ], children=[
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
        cubes.append(cube("dorsal", (0, -size / 2 - 1.5, 0), (0, 1.5, seg_len)))
        children = [chain] if chain else []
        if i == n - 1:
            children.append(part("tail_fin", (0, 0, seg_len), cubes=[cube("glow_tip" if v.get("glow_tail") else "tail_fin", (0, -2, 0), (0, 4, 3))]))
        chain = part(f"segment{i}", (0, 0, 0 if i == 0 else seg_len), cubes=cubes, children=children)
    head = part("head", (0, 0, 0), cubes=head_cubes, children=head_children)
    body = part("body", (0, y0, -n * 5 / 2), cubes=[], children=[head, chain])
    return {"kind": "long", "root": [body], "length": n * 5 + 5 + 3, "segments": n}


def shark(v):
    """Sharks. Options: hammer, snout (goblin), bulky (greenland), whip (ghost shark), big_eyes."""
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
    body = part("body", (0, y0, 0), cubes=[cube("body", (-w / 2, -h / 2, -body_len / 2), (w, h, body_len))], children=[
        part("head", (0, 0, -body_len / 2), cubes=head_cubes, children=head_children),
        part("tail", (0, -0.5, body_len / 2), cubes=[cube("tail", (-w / 4, -h / 4, 0), (w / 2, h / 2, 8))],
             children=[part("tail_fin", (0, 0, 8), (0.5, 0, 0), cubes=tail_fin)]),
        part("dorsal", (0, -h / 2, -2), (-0.6, 0, 0), cubes=[cube("dorsal", (0, -6, 0), (0, 6, 4))]),
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


PLANS = {"fish": fish, "deep": deep, "long": long, "shark": shark, "ray": ray, "jelly": jelly, "squid": squid, "seahorse": seahorse}


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
