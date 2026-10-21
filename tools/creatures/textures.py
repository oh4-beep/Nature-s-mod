"""Paints the creature textures procedurally from the species colours and pattern.

Every cube face is painted in "creature space": s runs along the body from the snout (0) to
the tail (1) and t from the back (0) to the belly (1), so patterns line up across the
head, body and tail cubes.
"""

import colorsys
import math
import random

from PIL import Image

import bodyplans


def rgb(hexstr):
    hexstr = hexstr.lstrip("#")
    return tuple(int(hexstr[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


class Painter:
    def __init__(self, species, model):
        self.sp = species
        self.model = model
        self.col = {k: rgb(v) for k, v in species["colors"].items()}
        self.rng = random.Random(species["id"])
        self.noise_seed = sum(ord(ch) * (i + 1) for i, ch in enumerate(species["id"]))
        self.glow = set(species["glow"])
        self.translucent = species["translucent"]
        self.extent = self._extent()
        self.spots = [(self.rng.random(), self.rng.random()) for _ in range(40)]

    # --- geometry ------------------------------------------------------------------------------
    def _extent(self):
        zs, ys = [], []

        def walk(parts, off):
            for p in parts:
                o = tuple(off[i] + p["pivot"][i] for i in range(3))
                for c in p["cubes"]:
                    if c["role"] in ("body", "head", "tail", "snout", "mantle", "bell"):
                        zs.extend([o[2] + c["origin"][2], o[2] + c["origin"][2] + c["size"][2]])
                        ys.extend([o[1] + c["origin"][1], o[1] + c["origin"][1] + c["size"][1]])
                    c["abs"] = o
                walk(p["children"], o)
        walk(self.model["root"], (0, 0, 0))
        if not zs:
            return (0, 1, 0, 1)
        return (min(zs), max(zs), min(ys), max(ys))

    def st(self, c, z, y):
        z0, z1, y0, y1 = self.extent
        az = c["abs"][2] + z
        ay = c["abs"][1] + y
        return (az - z0) / max(1e-6, z1 - z0), (ay - y0) / max(1e-6, y1 - y0)

    def noise(self, x, y, scale=3.0):
        # Cheap value noise.
        def h(i, j):
            n = (i * 374761393 + j * 668265263 + self.noise_seed * 1442695041) & 0xFFFFFFFF
            n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
            return (n & 0xFFFF) / 65535.0
        x /= scale
        y /= scale
        xi, yi = math.floor(x), math.floor(y)
        xf, yf = x - xi, y - yi
        a, b, cc, d = h(xi, yi), h(xi + 1, yi), h(xi, yi + 1), h(xi + 1, yi + 1)
        ux, uy = xf * xf * (3 - 2 * xf), yf * yf * (3 - 2 * yf)
        return a + (b - a) * ux + (cc - a) * uy + (a - b - cc + d) * ux * uy

    # --- colours ---------------------------------------------------------------------------------
    def body_colour(self, s, t, px, py):
        col = self.col
        if t < 0.45:
            base = mix(col["back"], col["side"], t / 0.45)
        else:
            base = mix(col["side"], col["belly"], (t - 0.45) / 0.4)
        p = self.sp["pattern"]
        acc = col["accent"]
        if p == "rainbow":
            if 0.42 < t < 0.58:
                base = mix(base, acc, 0.75)
            if t < 0.5 and self.noise(px, py, 1.3) > 0.78:
                base = shade(col["back"], 0.55)
        elif p == "spots":
            for sx, sy in self.spots:
                if (s - sx) ** 2 * 9 + (t - sy * 0.7) ** 2 * 9 < 0.004:
                    base = acc
        elif p == "speckles":
            if self.noise(px, py, 1.0) > 0.8:
                base = shade(base, 0.6)
        elif p == "mottled" or p == "rough":
            n = self.noise(px, py, 2.2)
            if n > 0.62:
                base = mix(base, acc, 0.6 if p == "mottled" else 0.3)
            elif n < 0.3:
                base = shade(base, 0.82)
        elif p == "scales":
            if (px + py) % 2 == 0:
                base = shade(base, 0.9)
        elif p == "bars":
            if t < 0.75 and math.sin(s * math.pi * 10) > 0.55:
                base = mix(base, acc, 0.7)
        elif p == "clown":
            for c0 in (0.18, 0.47, 0.75):
                d = abs(s - c0)
                if d < 0.06:
                    base = (250, 250, 250)
                elif d < 0.085:
                    base = (20, 20, 20)
        elif p == "tang":
            if 0.15 < s < 0.75 and 0.15 < t < 0.55 and math.sin(s * 9 + t * 4) > 0.3:
                base = acc
            if s > 0.88:
                base = (240, 210, 40)
        elif p == "eye_band":
            if 0.06 < s < 0.16:
                base = acc
            if (s - 0.82) ** 2 + (t - 0.35) ** 2 < 0.005:
                base = acc
        elif p == "angel":
            base = mix(col["back"], col["belly"], t * 1.1)
            if (px + 2 * py) % 5 == 0:
                base = mix(base, col["side"], 0.6)
        elif p == "zebra":
            if math.sin(s * math.pi * 18 + t * 2) > 0.1:
                base = mix(base, acc, 0.85)
        elif p == "wavy":
            if t < 0.45 and math.sin(s * 40 + math.sin(t * 20) * 2) > 0.5:
                base = acc
        elif p == "countershade":
            base = col["back"] if t < 0.55 + 0.05 * math.sin(s * 20) else col["belly"]
        elif p == "scutes":
            if (abs(t - 0.1) < 0.06 or abs(t - 0.55) < 0.05) and int(s * 30) % 3 == 0:
                base = acc
        elif p == "finlets":
            if s > 0.7 and (t < 0.08 or t > 0.92) and int(s * 40) % 2 == 0:
                base = acc
        elif p == "manta":
            if t < 0.5 and 0.2 < s < 0.45 and self.noise(px, py, 2) > 0.55:
                base = acc
        elif p == "rings":
            if int(t * 14) % 2 == 0:
                base = shade(base, 0.8)
        # Subtle grain everywhere so flat colours don't look plastic.
        grain = 0.94 + 0.12 * self.noise(px * 1.7, py * 1.3, 1.0)
        return shade(base, grain)

    def fin_colour(self, a, b, px, py, edge):
        col = self.col
        base = col["fin"]
        if int(a * 8) % 2 == 0:
            base = shade(base, 0.85)
        p = self.sp["pattern"]
        if p == "tips" and edge > 0.7:
            base = col["accent"]
        elif p == "clown" and edge > 0.8:
            base = (20, 20, 20)
        elif p == "zebra":
            if math.sin(a * 20) > 0:
                base = col["accent"]
        elif p == "angel" and edge > 0.75:
            base = col["back"]
        elif p == "tang":
            base = (240, 210, 40) if edge > 0.7 else col["back"]
        return base

    # --- painting --------------------------------------------------------------------------------
    def paint(self, image, glow_image):
        for c in bodyplans.iter_cubes(self.model["root"]):
            self.paint_cube(c, image, glow_image)

    def faces(self, c):
        u, v = c["uv"]
        w, h, d = (s for s in c["size"])
        W, H, D = (math.ceil(x - 1e-6) for x in (w, h, d))
        return {
            "top": (u + D, v, W, D),
            "bottom": (u + D + W, v, W, D),
            "west": (u, v + D, D, H),
            "north": (u + D, v + D, W, H),
            "east": (u + D + W, v + D, D, H),
            "south": (u + 2 * D + W, v + D, W, H),
        }

    def paint_cube(self, c, img, glow):
        role = c["role"]
        w, h, d = c["size"]
        ox, oy, oz = c["origin"]
        for face, (fx, fy, fw, fh) in self.faces(c).items():
            for i in range(fw):
                for j in range(fh):
                    px, py = fx + i, fy + j
                    if px >= img.width or py >= img.height:
                        continue
                    a = (i + 0.5) / max(1, fw)
                    b = (j + 0.5) / max(1, fh)
                    # Local position on the cube for this face pixel.
                    if face == "west":
                        lz, ly = oz + d * (1 - a), oy + h * b
                    elif face == "east":
                        lz, ly = oz + d * a, oy + h * b
                    elif face == "top":
                        lz, ly = oz + d * (1 - b), oy
                    elif face == "bottom":
                        lz, ly = oz + d * b, oy + h
                    elif face == "north":
                        lz, ly = oz, oy + h * b
                    else:
                        lz, ly = oz + d, oy + h * b
                    s, t = self.st(c, lz, ly)
                    colour, alpha, glowing = self.pixel(role, face, s, t, a, b, px, py, c)
                    if alpha <= 0:
                        continue
                    img.putpixel((px, py), colour + (alpha,))
                    if glowing:
                        glow.putpixel((px, py), glowing + (255,))

    def pixel(self, role, face, s, t, a, b, px, py, c):
        """Returns (rgb, alpha, glow rgb or None) for one texture pixel."""
        col = self.col
        sp = self.sp
        alpha = 255
        glowing = None
        side = face in ("west", "east")
        # Fraction from the front (snout) of this face, for eye placement.
        front = (1 - a) if face == "west" else a if face == "east" else 0.5
        if role in ("body", "tail", "mantle", "head", "snout", "wing"):
            if face == "top":
                t_eff = 0.0
            elif face == "bottom":
                t_eff = 1.0
            else:
                t_eff = t
            colour = self.body_colour(s, t_eff, px, py)
            if face in ("north", "south") and role in ("body", "tail", "mantle"):
                colour = shade(colour, 0.9)
            if role == "head" and side:
                eye_t = 0.3 if sp["plan"] != "shark" else 0.4
                big = "big_eyes" in sp["variant"] or sp["plan"] in ("squid",)
                if front > 0.55 and abs(b - eye_t) < (0.18 if big else 0.12) and abs(front - 0.8) < (0.2 if big else 0.13):
                    colour = col["eye"]
                    if "eyes" in self.glow:
                        glowing = col["eye"]
                    elif abs(front - 0.85) < 0.06 and b < eye_t:
                        colour = (230, 230, 230)
            if "photophores" in self.glow and role in ("body", "tail", "head") and (side or face == "bottom"):
                if (face == "bottom" and int(a * 6) % 2 == 0 and int(b * 10) % 3 == 0) or (side and 0.7 < t < 0.88 and int(s * 30) % 3 == 0):
                    colour = col["accent"]
                    glowing = col["accent"]
            if "stripes" in self.glow and role in ("body", "tail", "head") and side:
                if abs(t - 0.35) < 0.05 and int(s * 24) % 4 != 0:
                    colour = col["accent"]
                    glowing = col["accent"]
            if "spots" in self.glow and self.noise(px, py, 1.1) > 0.86:
                colour = col["accent"]
                glowing = col["accent"]
            if self.translucent:
                alpha = 170 if role != "wing" else 190
            if role == "mantle" and sp["pattern"] == "spots" and self.noise(px, py, 1.4) > 0.72:
                colour = shade(colour, 0.7)
            return colour, alpha, glowing
        if role in ("fin", "dorsal", "tail_fin", "spines", "glow_tip"):
            # Planes: fade/fork their outer edges.
            edge = 0.0
            if role == "tail_fin" and side:
                edge = abs(b - 0.5) * 2
                along = a if face == "east" else 1 - a
                fork = 0.35 if sp["plan"] in ("fish", "shark") else 0.0
                if along > 0.55 and abs(b - 0.5) < fork * (along - 0.55) / 0.45:
                    return (0, 0, 0), 0, None
            elif role == "dorsal" and side:
                edge = 1 - b
                along = a if face == "east" else 1 - a
                if b < 0.6 * (1 - along) ** 1.5 and sp["plan"] != "long":
                    return (0, 0, 0), 0, None
            elif side:
                edge = b
            else:
                edge = max(abs(a - 0.5), abs(b - 0.5)) * 2
            colour = self.fin_colour(a if side else b, b, px, py, edge)
            if role == "spines":
                colour = col["accent"] if int((a if side else b) * 14) % 2 == 0 else col["side"]
                if int(a * 7) % 2 == 1 and b > 0.3:
                    return (0, 0, 0), 0, None
            if role == "glow_tip":
                colour = col["accent"]
                glowing = col["accent"]
            alpha = 160 if self.translucent else 255
            return colour, alpha, glowing
        if role == "jaw":
            colour = shade(mix(col["side"], col["belly"], 0.6), 0.95)
            if face == "top":
                colour = (150, 40, 40)
            return colour, 170 if self.translucent else 255, None
        if role == "teeth":
            if int((a if side else b) * 10) % 2 == 0 and (b if side else a) > 0.3:
                return (235, 230, 215), 255, None
            return (0, 0, 0), 0, None
        if role in ("barbel", "stalk", "bill", "beak"):
            colour = shade(col["fin"] if role == "barbel" else col["back"], 0.9 + 0.1 * self.noise(px, py, 1))
            if role == "beak":
                colour = mix(col["side"], (230, 230, 210), 0.5)
            return colour, 255, None
        if role == "lure":
            colour = col["accent"]
            return colour, 255, colour if "lure" in self.glow else None
        if role == "dome":
            colour = mix((200, 220, 230), col["accent"], 0.15)
            if side and abs(a - 0.5) < 0.2 and b > 0.5:
                colour = col["accent"]
                return colour, 255, colour if "eyes" in self.glow else None
            return colour, 255, None
        if role == "eye_bulge":
            colour = shade(col["side"], 0.9)
            if side and abs(a - 0.5) < 0.25:
                colour = col["eye"]
                return colour, 255, colour if "eyes" in self.glow else None
            return colour, 255, None
        if role in ("bell", "rim", "inner"):
            colour = col["side"]
            if role == "rim":
                colour = col["fin"]
            if role == "inner":
                colour = col["accent"]
            if self.sp["pattern"] == "moon" and face == "top":
                r = math.hypot(a - 0.5, b - 0.5)
                ang = math.atan2(b - 0.5, a - 0.5)
                if abs(r - 0.22) < 0.07 and abs(math.sin(ang * 2)) > 0.5:
                    colour = col["accent"]
            glow_rgb = None
            if "combs" in self.glow and role == "bell" and side or ("combs" in self.glow and face in ("north", "south") and role == "bell"):
                if int(a * 8) % 2 == 0:
                    hue = (b + a * 0.3) % 1.0
                    r, g, bb = colorsys.hsv_to_rgb(hue, 0.6, 1.0)
                    glow_rgb = (int(r * 255), int(g * 255), int(bb * 255))
                    colour = glow_rgb
            if "rims" in self.glow and role == "rim":
                glow_rgb = col["accent"]
            alpha = {"bell": 140, "rim": 170, "inner": 200}[role]
            return colour, alpha, glow_rgb
        if role in ("tentacle", "arm"):
            colour = col["fin"] if role == "tentacle" else col["accent"] if self.sp["plan"] == "jelly" else col["side"]
            if self.sp["plan"] == "squid":
                colour = shade(mix(col["side"], col["belly"], b * 0.5), 0.95)
                if face == "bottom" and int(b * 10 + a * 3) % 3 == 0:
                    colour = col["belly"]
                return colour, 255, None
            alpha = 150 if self.translucent else 255
            if self.sp["id"] == "lions_mane_jelly":
                colour = mix(col["fin"], col["accent"], b)
            return colour, alpha, None
        return col["side"], 255, None


def build(species):
    plan = bodyplans.PLANS[species["plan"]]
    model = plan(species["variant"])
    tex_w, tex_h = bodyplans.layout(model)
    model["tex"] = (tex_w, tex_h)
    painter = Painter(species, model)
    image = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    glow = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    painter.paint(image, glow)
    has_glow = glow.getbbox() is not None
    return model, image, glow if has_glow else None


# --- side view preview ----------------------------------------------------------------------------

def preview(model, image, scale=6):
    """Draws a flat side view (looking at the creature's right side) for quick checks."""
    cubes = bodyplans.iter_cubes(model["root"])
    zs = [c["abs"][2] + c["origin"][2] for c in cubes] + [c["abs"][2] + c["origin"][2] + c["size"][2] for c in cubes]
    ys = [c["abs"][1] + c["origin"][1] for c in cubes] + [c["abs"][1] + c["origin"][1] + c["size"][1] for c in cubes]
    z0, z1, y0, y1 = min(zs), max(zs), min(ys), max(ys)
    W = int((z1 - z0) * scale) + 2
    H = int((y1 - y0) * scale) + 2
    out = Image.new("RGBA", (W, H), (30, 60, 110, 255))
    # Paint far-to-near: sort by x (west side faces the viewer).
    for c in sorted(cubes, key=lambda c: -(c["abs"][0] + c["origin"][0])):
        u, v = c["uv"]
        w, h, d = c["size"]
        D, Hh = math.ceil(d - 1e-6), math.ceil(h - 1e-6)
        if D == 0 or Hh == 0:
            continue
        cz = c["abs"][2] + c["origin"][2]
        cy = c["abs"][1] + c["origin"][1]
        for i in range(int(d * scale)):
            for j in range(int(h * scale)):
                a = i / (d * scale)
                b = j / (h * scale)
                tx = u + int((1 - a) * D)  # west face: head at the right edge
                ty = v + D + int(b * Hh)
                tx = min(tx, u + D - 1)
                ty = min(ty, v + D + Hh - 1)
                if tx >= image.width or ty >= image.height:
                    continue
                p = image.getpixel((tx, ty))
                if p[3] < 30:
                    continue
                X = int((cz - z0) * scale) + i
                Y = int((cy - y0) * scale) + j
                if 0 <= X < W and 0 <= Y < H:
                    bg = out.getpixel((X, Y))
                    al = p[3] / 255
                    out.putpixel((X, Y), tuple(int(p[k] * al + bg[k] * (1 - al)) for k in range(3)) + (255,))
    return out
