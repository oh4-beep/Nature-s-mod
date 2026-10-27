#!/usr/bin/env python3
"""Synthesizes the sounds of the natural disasters. Nothing is recorded: every sound is noise, impulses
and tones shaped the way the real thing sounds, after how it is made. A tornado's roar is the low,
broad rumble of air and debris with the whistle of the vortex wandering over it; the boom of an
impact or an eruption dies away the way thunder does, its high notes first; a far-off blast comes
back off the hills as echoes. Writes mono Ogg Vorbis files (Minecraft only places mono sounds in the
world) under assets/naturesmod/sounds/disaster/, and their sound events into sounds.json.

    python3 tools/assets/sounds.py

Needs numpy and an ffmpeg with libvorbis. The noise is seeded, so the files come out the same each time.
"""

import json
import os
import subprocess

import numpy as np

SR = 44100
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(REPO, "src", "main", "resources", "assets", "naturesmod")
OUT = os.path.join(ASSETS, "sounds", "disaster")


# --- building blocks -------------------------------------------------------------------------

def rng(seed):
    return np.random.default_rng(seed)


def times(seconds):
    return np.arange(int(seconds * SR)) / SR


def wander(seconds, rate, seed, lo=0.0, hi=1.0):
    """A smooth random curve, about `rate` turns a second between lo and hi: a function of time (s)."""
    r = rng(seed)
    count = int(seconds * rate) + 4
    xs = np.linspace(-1.0 / rate, seconds + 1.0 / rate, count)
    ys = r.uniform(lo, hi, count)

    def curve(t):
        t = np.asarray(t, dtype=float)
        i = np.clip(np.searchsorted(xs, t) - 1, 0, count - 2)
        u = (t - xs[i]) / (xs[i + 1] - xs[i])
        u = (1 - np.cos(np.pi * np.clip(u, 0, 1))) / 2
        return ys[i] * (1 - u) + ys[i + 1] * u

    return curve


def spectral_noise(seconds, shape, seed, n_fft=4096, hop=1024):
    """Noise whose amplitude spectrum at time t is shape(t, f): a frame of random phases at a time,
    overlapped and added, so the spectrum can change over time (a wandering whistle, a gust)."""
    r = rng(seed)
    n = int(seconds * SR)
    freqs = np.fft.rfftfreq(n_fft, 1.0 / SR)
    window = np.hanning(n_fft)
    frames = n // hop + n_fft // hop + 1
    out = np.zeros(frames * hop + n_fft)
    for i in range(frames):
        t = (i * hop - n_fft / 2) / SR
        spectrum = shape(max(t, 0.0), freqs) * np.exp(1j * r.uniform(0, 2 * np.pi, freqs.size))
        out[i * hop:i * hop + n_fft] += np.fft.irfft(spectrum, n_fft) * window
    return out[n_fft // 2:n_fft // 2 + n]


def peak(f, centre, width):
    """A resonance: a bump in the spectrum around `centre` Hz, `width` its relative width."""
    return np.exp(-0.5 * (np.log(np.maximum(f, 1.0) / centre) / width) ** 2)


def tilt(f, corner, power):
    """1 below `corner` Hz, then falling (power < 0) or rising (power > 0) as f ** power."""
    return np.where(f < corner, 1.0, (np.maximum(f, 1.0) / corner) ** power)


def highpass(f, corner, order=2):
    return 1.0 / np.sqrt(1.0 + (corner / np.maximum(f, 1e-3)) ** (2 * order))


def lowpass(f, corner, order=2):
    return 1.0 / np.sqrt(1.0 + (np.maximum(f, 0.0) / corner) ** (2 * order))


def filtered(x, response):
    """x through a filter whose gain at f Hz is response(f)."""
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    return np.fft.irfft(np.fft.rfft(x) * response(f), len(x))


def convolve(x, kernel):
    n = len(x) + len(kernel) - 1
    size = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(kernel, size), size)[:n]


def place(out, at, event, gain=1.0):
    """Adds `event` into `out` from sample `at`, clipped to the end."""
    at = int(at)
    if at >= len(out):
        return
    end = min(len(out), at + len(event))
    out[at:end] += gain * event[:end - at]


def burst(seconds, low, high, decay, seed, attack=0.001):
    """A short burst of noise between low and high Hz dying away over `decay` seconds: a crack, a thud, a pop."""
    t = times(seconds)
    noise = filtered(rng(seed).standard_normal(t.size), lambda f: highpass(f, low) * lowpass(f, high))
    return noise * np.minimum(t / attack, 1.0) * np.exp(-t / decay)


def poisson(seconds, rate, seed):
    """Random event times, `rate` a second on average (rate may be a function of time)."""
    r = rng(seed)
    out = []
    t = 0.0
    top = max(rate(s) for s in np.linspace(0, seconds, 64)) if callable(rate) else rate
    while True:
        t += r.exponential(1.0 / top)
        if t >= seconds:
            return out
        if not callable(rate) or r.uniform() < rate(t) / top:
            out.append(t)


def band_decay(seconds, bands, seed):
    """The tail of a blast: noise split into bands, each dying away at its own rate (the high notes
    first, as in thunder); `bands` holds (low Hz, high Hz, gain, decay seconds)."""
    r = rng(seed)
    t = times(seconds)
    out = np.zeros(t.size)
    for low, high, gain, decay in bands:
        noise = filtered(r.standard_normal(t.size), lambda f: highpass(f, low, 3) * lowpass(f, high, 3))
        noise /= np.sqrt(np.mean(noise ** 2)) + 1e-9
        out += gain * noise * np.exp(-t / decay)
    return out


def reverb(x, seconds, seed, wet=0.5, damping=4000.0, echoes=()):
    """Out in the open: a diffuse tail that grows duller as it dies, and echoes off far hills
    (delay seconds, gain), each duller than the sound itself."""
    r = rng(seed)
    t = times(seconds)
    tail = np.zeros(t.size)
    for low, high, decay in ((20, 250, seconds / 3.5), (250, 1200, seconds / 5), (1200, damping, seconds / 9)):
        noise = filtered(r.standard_normal(t.size), lambda f: highpass(f, low) * lowpass(f, high))
        tail += noise * np.exp(-t / decay)
    tail *= np.minimum(t / 0.02, 1.0)
    tail /= np.sqrt(np.sum(tail ** 2)) + 1e-9
    out = np.concatenate([x, np.zeros(t.size)])
    diffuse = np.zeros(out.size)
    wet_part = convolve(x, tail)[:out.size]
    diffuse[:wet_part.size] = wet_part
    out = out * (1 - wet * 0.5) + diffuse * wet * 3.0
    for delay, gain in echoes:
        echo = filtered(x, lambda f: lowpass(f, 900.0))
        place(out, delay * SR, echo, gain)
    return out


def looped(x, fade_seconds):
    """Makes a loop out of x: its last `fade` blended into its first, so it runs on without a seam."""
    fade = int(fade_seconds * SR)
    n = len(x) - fade
    w = np.linspace(0, np.pi / 2, fade)
    out = x[:n].copy()
    out[:fade] = x[:fade] * np.sin(w) + x[n:] * np.cos(w)
    return out


def limited(x, ceiling=2.1):
    """x with its loudest peaks held softly under `ceiling` times its loudness (rms), the rest left as it is:
    a loop full of crashes and bangs levelled by its loudness would otherwise clip on them (and Vorbis
    overshoots a little on sharp peaks, so they want some room)."""
    x = x / (np.sqrt(np.mean(x ** 2)) + 1e-9)
    knee = 0.8 * ceiling
    over = np.abs(x) > knee
    x[over] = np.sign(x[over]) * (knee + (ceiling - knee) * np.tanh((np.abs(x[over]) - knee) / (ceiling - knee)))
    return x


def finish(x, level, loop=False):
    """Levels a sound: loops by loudness, one-shots by their peak, softly limited either way."""
    x = x - np.mean(x)
    if loop:
        x = x / (np.sqrt(np.mean(x ** 2)) + 1e-9) * level
    else:
        x = x / (np.max(np.abs(x)) + 1e-9) * level
    x = np.tanh(x * 1.2) / np.tanh(1.2)
    if not loop:
        fade = int(0.01 * SR)
        x[-fade:] *= np.linspace(1, 0, fade)
    return x


def write(name, x):
    os.makedirs(OUT, exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
    path = os.path.join(OUT, name + ".ogg")
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "-",
                    "-c:a", "libvorbis", "-q:a", "5", path], input=pcm, check=True)
    return path


# --- the sounds ------------------------------------------------------------------------------

def tornado_roar(seed=1):
    """The freight train: a deep, broad roar of wind and debris, surging, with the vortex whistling over it."""
    seconds = 12.0
    surge = wander(seconds, 0.35, seed + 1, 0.6, 1.0)
    whistle = [wander(seconds, 0.22, seed + 10 + k, 480, 1350) for k in range(3)]
    gust = wander(seconds, 0.5, seed + 2, 0.0, 1.0)

    def shape(t, f):
        roar = tilt(f, 70, -1.05) * highpass(f, 28, 3) * lowpass(f, 5000, 2)
        roar *= 1.0 + 1.6 * peak(f, 95, 0.55)
        jet = 0.22 * peak(f, 900, 0.9)
        whistles = sum(0.05 * peak(f, w(t), 0.03) for w in whistle) * gust(t) ** 2
        return surge(t) * (roar + jet) + whistles

    x = spectral_noise(seconds, shape, seed)
    x /= np.sqrt(np.mean(x ** 2))
    # Debris: things hitting things inside the vortex, and timber snapping.
    for k, at in enumerate(poisson(seconds, 4.0, seed + 3)):
        hit = burst(0.25, 120, rng(seed + 100 + k).uniform(600, 2500), rng(seed + 200 + k).uniform(0.02, 0.07), seed + 300 + k)
        place(x, at * SR, hit / (np.max(np.abs(hit)) + 1e-9), rng(seed + 400 + k).uniform(0.15, 0.55))
    for k, at in enumerate(poisson(seconds, 0.6, seed + 4)):
        snap = burst(0.4, 900, 5000, 0.035, seed + 500 + k, attack=0.0005)
        place(x, at * SR, snap / (np.max(np.abs(snap)) + 1e-9), rng(seed + 600 + k).uniform(0.3, 0.7))
    return finish(looped(x, 1.5), 0.32, loop=True)


def wind_howl(seed=20, low=260, high=720, hiss=0.0, gusty=0.6):
    """Strong wind: broad noise surging in gusts, brighter as it blows harder, howling in the corners."""
    seconds = 14.0
    gust = wander(seconds, 0.22, seed + 1, 1.0 - gusty, 1.0)
    howls = [wander(seconds, 0.18, seed + 10 + k, low, high) for k in range(2)]

    def shape(t, f):
        g = gust(t)
        base = tilt(f, 180, -0.85 + 0.35 * (g - 0.7)) * highpass(f, 40, 2) * lowpass(f, 9000)
        howl = sum(0.07 * peak(f, h(t) * (0.85 + 0.3 * g), 0.025) for h in howls) * g ** 3
        snow = hiss * peak(f, 4500, 0.6)
        return g * base + howl + g * snow

    x = spectral_noise(seconds, shape, seed)
    return finish(looped(x, 2.0), 0.3, loop=True)


def sandstorm_wind(seed=40):
    """A haboob: a heavy roar under a hiss of sand, the grains ticking on everything."""
    seconds = 14.0
    gust = wander(seconds, 0.3, seed + 1, 0.55, 1.0)

    def shape(t, f):
        g = gust(t)
        roar = tilt(f, 120, -0.9) * highpass(f, 35, 2)
        sand = 0.22 * peak(f, 3200, 0.7) * (0.6 + 0.6 * g)
        return g * roar + sand

    x = spectral_noise(seconds, shape, seed)
    x /= np.sqrt(np.mean(x ** 2))
    # Grains: a dense patter of tiny clicks, thicker in the gusts.
    grains = np.zeros_like(x)
    tick = burst(0.004, 2500, 12000, 0.0006, seed + 2, attack=0.0001)
    for k, at in enumerate(poisson(seconds, lambda s: 900 * gust(s) ** 2, seed + 3)):
        place(grains, at * SR, tick, rng(seed + 10000 + k).uniform(-1, 1))
    x += 0.35 * grains / (np.sqrt(np.mean(grains ** 2)) + 1e-9)
    return finish(looped(x, 2.0), 0.3, loop=True)


def quake_rumble(seed=60):
    """The ground shaking: a deep, rolling rumble, rattling and the creak of strained timber."""
    seconds = 10.0
    roll = wander(seconds, 0.8, seed + 1, 0.45, 1.0)

    def shape(t, f):
        sub = peak(f, 48, 0.45) * highpass(f, 22, 3)
        growl = 0.35 * peak(f, 140, 0.5)
        return roll(t) * (sub + growl) * lowpass(f, 1500)

    x = spectral_noise(seconds, shape, seed, n_fft=8192, hop=2048)
    x /= np.sqrt(np.mean(x ** 2))
    for k, at in enumerate(poisson(seconds, lambda s: 7 * roll(s), seed + 2)):
        rattle = burst(0.12, 300, rng(seed + 100 + k).uniform(1500, 4000), 0.012, seed + 200 + k)
        place(x, at * SR, rattle / (np.max(np.abs(rattle)) + 1e-9), rng(seed + 300 + k).uniform(0.05, 0.22))
    for k, at in enumerate(poisson(seconds, 0.9, seed + 3)):
        crack = burst(0.6, 120, 1600, 0.12, seed + 400 + k, attack=0.002)
        place(x, at * SR, crack / (np.max(np.abs(crack)) + 1e-9), rng(seed + 500 + k).uniform(0.2, 0.5))
    for k, at in enumerate(poisson(seconds, 0.35, seed + 4)):
        place(x, at * SR, creak(rng(seed + 600 + k).uniform(0.4, 0.9), seed + 700 + k), 0.25)
    return finish(looped(x, 1.5), 0.33, loop=True)


def creak(seconds, seed):
    """Wood under strain: stick and slip, a train of little ticks ringing in the grain."""
    r = rng(seed)
    t = times(seconds)
    out = np.zeros(t.size)
    rate = wander(seconds, 3.0, seed, 35, 110)
    ring_f = r.uniform(500, 1100)
    ring = np.sin(2 * np.pi * ring_f * times(0.03)) * np.exp(-times(0.03) / 0.004)
    at = 0.0
    while at < seconds:
        envelope = np.sin(np.pi * at / seconds) ** 0.7
        place(out, at * SR, ring, envelope * r.uniform(0.6, 1.0))
        at += 1.0 / rate(at) * r.uniform(0.85, 1.15)
    return out / (np.max(np.abs(out)) + 1e-9)


def quake_jolt(seed):
    """The first wave arriving: a heavy thud through the ground, everything jumping, then the rumble."""
    seconds = 4.0
    t = times(seconds)
    sweep = np.sin(2 * np.pi * np.cumsum(70 - 40 * np.minimum(t / 0.5, 1.0)) / SR) * np.exp(-t / 0.7) * np.minimum(t / 0.006, 1)
    tail = band_decay(seconds, [(20, 70, 1.0, 1.4), (70, 200, 0.6, 0.8), (200, 900, 0.3, 0.35), (900, 4000, 0.12, 0.12)], seed)
    x = 1.4 * sweep + 0.5 * tail
    for k, at in enumerate(poisson(1.6, lambda s: 22 * np.exp(-s / 0.6), seed + 1)):
        rattle = burst(0.12, 300, rng(seed + 100 + k).uniform(1500, 4500), 0.015, seed + 200 + k)
        place(x, at * SR, rattle / (np.max(np.abs(rattle)) + 1e-9), rng(seed + 300 + k).uniform(0.05, 0.25))
    return finish(x, 0.95)


def blast(seed, seconds=8.0, crack=1.0, low=1.0, echoes=((0.7, 0.35), (1.6, 0.2))):
    """A great explosion: the crack, the chest-deep boom dying away high notes first, and the hills answering."""
    t = times(seconds)
    shock = burst(seconds, 30, 12000, 0.03, seed, attack=0.0003) * crack
    shock /= np.max(np.abs(shock)) + 1e-9
    thump = np.sin(2 * np.pi * np.cumsum(48 - 26 * np.minimum(t / 1.2, 1.0)) / SR) * np.exp(-t / 1.1) * np.minimum(t / 0.004, 1)
    tail = band_decay(seconds, [(18, 60, 1.0, 2.6), (60, 200, 0.8, 1.6), (200, 800, 0.5, 0.7), (800, 3000, 0.3, 0.28),
                                (3000, 12000, 0.2, 0.08)], seed + 1)
    tail /= np.max(np.abs(tail)) + 1e-9
    x = 0.9 * shock + 1.1 * low * thump + 0.9 * tail
    return reverb(x, 3.5, seed + 2, wet=0.35, echoes=echoes)


def eruption_blast(seed):
    return finish(blast(seed, crack=0.8, low=1.3, echoes=((0.9, 0.4), (2.1, 0.25), (3.4, 0.12))), 0.97)


def meteor_impact(seed):
    """An impact: a sharper crack than a volcano's, then rocks raining back down all around."""
    x = blast(seed, crack=1.3, low=1.0)
    for k, at in enumerate(poisson(4.0, lambda s: 26 * np.exp(-s / 1.4) if s > 0.7 else 0.0, seed + 5)):
        heavy = rng(seed + 100 + k).uniform() < 0.3
        thud = burst(0.3, 60 if heavy else 200, 900 if heavy else 3500, 0.06 if heavy else 0.02, seed + 200 + k)
        place(x, at * SR, thud / (np.max(np.abs(thud)) + 1e-9), rng(seed + 300 + k).uniform(0.04, 0.16))
    return finish(x, 0.97)


def sonic_boom(seed):
    """A meteor bursting high overhead: the double crack of an N-wave, rolling away over the land."""
    seconds = 2.0
    t = times(seconds)
    length = rng(seed).uniform(0.12, 0.22)
    n_wave = np.where(t < length, 1.0 - 2.0 * t / length, 0.0)
    n_wave = filtered(n_wave, lambda f: lowpass(f, 700, 2) * highpass(f, 12, 1))
    x = reverb(n_wave, 3.0, seed + 1, wet=0.45, damping=2500, echoes=((0.8, 0.3), (1.9, 0.18)))
    return finish(x, 0.95)


def meteor_whoosh(seed):
    """A fireball tearing past: a roar falling in pitch as it passes (Doppler), crackling as it breaks up."""
    seconds = 4.5
    t = times(seconds)
    passing = 2.8
    # The pitch of a passing source: high while it closes, low once it is past.
    closing = np.tanh((passing - t) / 0.45)
    centre = 1100 * (1 + 0.65 * closing)
    loud = np.exp(-((t - passing) / np.where(t < passing, 1.2, 0.5)) ** 2)

    def shape(s, f):
        i = min(int(s * SR), t.size - 1)
        return loud[i] * (peak(f, centre[i], 0.35) + 0.6 * tilt(f, 80, -1.0) * highpass(f, 30) * lowpass(f, 1200))

    x = spectral_noise(seconds, shape, seed)
    x /= np.max(np.abs(x)) + 1e-9
    for k, at in enumerate(poisson(seconds, lambda s: 30 * np.exp(-((s - passing) / 0.9) ** 2), seed + 1)):
        spark = burst(0.05, 1500, 9000, 0.004, seed + 100 + k, attack=0.0002)
        place(x, at * SR, spark / (np.max(np.abs(spark)) + 1e-9), rng(seed + 200 + k).uniform(0.05, 0.3))
    return finish(x, 0.9)


def eruption_roar(seed=120):
    """The vent: a jet of gas and ash roaring without end, surging with each burst, lava burbling below."""
    seconds = 12.0
    chuff = wander(seconds, 1.4, seed + 1, 0.55, 1.0)

    def shape(t, f):
        return chuff(t) * (tilt(f, 90, -0.7) * highpass(f, 25, 3) * lowpass(f, 6000) * (1 + 0.8 * peak(f, 60, 0.5)))

    x = spectral_noise(seconds, shape, seed, n_fft=8192, hop=2048)
    x /= np.sqrt(np.mean(x ** 2))
    for k, at in enumerate(poisson(seconds, 3.0, seed + 2)):
        r = rng(seed + 100 + k)
        length = r.uniform(0.08, 0.2)
        s = times(length)
        f0 = r.uniform(40, 110)
        bloop = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 1.5 * s / length)) / SR) * np.sin(np.pi * s / length) ** 2
        place(x, at * SR, bloop, r.uniform(0.2, 0.5))
    return finish(looped(x, 1.5), 0.33, loop=True)


def fire_roar(seed=140):
    """A forest burning: the deep draught of the flames under a storm of crackles and the snap of splitting wood."""
    seconds = 12.0
    flicker = wander(seconds, 2.5, seed + 1, 0.6, 1.0)
    slow = wander(seconds, 0.2, seed + 2, 0.7, 1.0)

    def shape(t, f):
        roar = peak(f, 110, 0.7) * highpass(f, 30, 2)
        hiss = 0.03 * peak(f, 6000, 0.5)
        return flicker(t) * slow(t) * roar + hiss

    x = spectral_noise(seconds, shape, seed)
    x /= np.sqrt(np.mean(x ** 2))
    crackles = np.zeros_like(x)
    for k, at in enumerate(poisson(seconds, lambda s: 45 * slow(s), seed + 3)):
        r = rng(seed + 1000 + k)
        pop = burst(0.03, 700, 9000, r.uniform(0.002, 0.008), seed + 2000 + k, attack=0.0001)
        place(crackles, at * SR, pop / (np.max(np.abs(pop)) + 1e-9), r.pareto(2.5) * r.choice([-1, 1]) * 0.25)
    for k, at in enumerate(poisson(seconds, 0.5, seed + 4)):
        snap = burst(0.2, 400, 6000, 0.04, seed + 3000 + k, attack=0.0003)
        place(crackles, at * SR, snap / (np.max(np.abs(snap)) + 1e-9), 1.2)
    x += 1.4 * crackles / (np.sqrt(np.mean(crackles ** 2)) + 1e-9)
    return finish(looped(x, 1.0), 0.3, loop=True)


def siren(seed=160):
    """An outdoor warning siren some way off: two rotor tones winding up, holding while the head turns, winding down."""
    seconds = 17.0
    t = times(seconds)
    up = 1 - np.exp(-t / 1.4)
    down = np.where(t > 10.0, np.exp(-(t - 10.0) / 2.2), 1.0)
    f0 = 95 + 345 * up * down + 3 * np.sin(2 * np.pi * 0.7 * t)
    x = np.zeros(t.size)
    for ratio, gain in ((1.0, 1.0), (1.25, 0.7)):
        phase = 2 * np.pi * np.cumsum(f0 * ratio) / SR
        for n in range(1, 11):
            x += gain / n ** 1.3 * np.sin(n * phase)
    x *= 0.5 + 0.5 * np.cos(2 * np.pi * t / 7.0 + 1.0) ** 2
    x *= np.minimum(t / 0.3, 1.0) * np.minimum((seconds - t) / 1.5, 1.0)
    x = filtered(x, lambda f: lowpass(f, 1600, 2))
    x = reverb(x, 3.5, seed, wet=0.6, damping=2000, echoes=((0.45, 0.35), (1.3, 0.2)))
    return finish(x, 0.85)


def hail(seed=180):
    """A hailstorm: a hard rattle of ice on the ground, sharper cracks off stone and roofs, the odd big stone
    thudding down, under the hiss of the rain that comes with it."""
    seconds = 12.0
    surge = wander(seconds, 0.4, seed + 1, 0.6, 1.0)
    x = np.zeros(int(seconds * SR))
    for k, at in enumerate(poisson(seconds, lambda s: 260 * surge(s), seed + 2)):
        r = rng(seed + 1000 + k)
        if r.uniform() < 0.75:
            hit = burst(0.03, 2500, 10000, r.uniform(0.002, 0.007), seed + 2000 + k, attack=0.0001)
            gain = r.uniform(0.1, 0.5)
        else:
            hit = burst(0.08, 500, 3000, r.uniform(0.008, 0.025), seed + 2000 + k, attack=0.0002)
            gain = r.uniform(0.25, 0.8)
        place(x, at * SR, hit / (np.max(np.abs(hit)) + 1e-9), gain * r.choice([-1, 1]))
    x /= np.sqrt(np.mean(x ** 2)) + 1e-9

    def rain(t, f):
        return surge(t) * highpass(f, 500, 2) * lowpass(f, 8000, 2)

    hiss = spectral_noise(seconds, rain, seed + 3)
    x += 0.35 * hiss / (np.sqrt(np.mean(hiss ** 2)) + 1e-9)
    x = reverb(x, 1.0, seed + 4, wet=0.25, damping=6000)
    return finish(looped(x, 1.0), 0.24, loop=True)


def hurricane_wind(seed=240):
    """A hurricane: a deep roar of wind that never lets up, gusts swelling out of it and moaning round
    every corner, the rain driven through it lashing harder with each gust, and loose things banging."""
    seconds = 11.5
    gust = wander(seconds, 0.3, seed + 1, 0.45, 1.0)
    howls = [wander(seconds, 0.16, seed + 10 + k, low, high) for k, (low, high) in enumerate(((170, 360), (300, 620), (520, 950)))]

    def shape(t, f):
        g = gust(t)
        roar = tilt(f, 120, -1.0 + 0.4 * (g - 0.7)) * highpass(f, 24, 3) * lowpass(f, 7000) * (1.0 + 0.8 * peak(f, 65, 0.6))
        howl = sum(0.11 * peak(f, h(t) * (0.85 + 0.3 * g), 0.03) for h in howls) * g ** 3
        rain = 0.05 * highpass(f, 900, 2) * lowpass(f, 9000, 2) * (0.4 + 0.8 * g)
        return (0.55 + 0.45 * g) * roar + howl + rain

    x = spectral_noise(seconds, shape, seed)
    x /= np.sqrt(np.mean(x ** 2))
    # Things banging in the wind: a shutter, a sheet of roofing, branches, most of all in the gusts.
    for k, at in enumerate(poisson(seconds, lambda s: 2.2 * gust(s) ** 2, seed + 2)):
        r = rng(seed + 100 + k)
        bang = burst(0.2, 120, r.uniform(900, 2600), r.uniform(0.015, 0.05), seed + 200 + k, attack=0.0005)
        place(x, at * SR, bang / (np.max(np.abs(bang)) + 1e-9), r.uniform(0.1, 0.35))
    return finish(limited(looped(x, 1.5)), 0.3, loop=True)


def tsunami_roar(seed=260):
    """A tsunami coming ashore: a wall of water breaking over and over with a thunder like surf a hundred
    times over, the rush and hiss of the white water churning over everything, and under it a deep roll."""
    seconds = 11.5
    surge = wander(seconds, 0.45, seed + 1, 0.55, 1.0)
    roll = wander(seconds, 0.9, seed + 2, 0.6, 1.0)

    def shape(t, f):
        s = surge(t)
        rumble = 1.5 * roll(t) * peak(f, 45, 0.5) * highpass(f, 20, 3)
        rush = tilt(f, 250, -0.75) * highpass(f, 60, 2) * lowpass(f, 9000, 2) * (1.0 + 0.6 * peak(f, 900, 0.8))
        foam = 0.08 * peak(f, 4500, 0.6)
        return s * (rumble + rush + foam)

    x = spectral_noise(seconds, shape, seed, n_fft=8192, hop=2048)
    x /= np.sqrt(np.mean(x ** 2))
    # The breakers: each a heavy, soft-edged thud with the white water hissing away after it.
    crashes = np.zeros_like(x)
    for k, at in enumerate(poisson(seconds, 0.8, seed + 3)):
        r = rng(seed + 100 + k)
        crash = band_decay(1.6, [(25, 90, 1.0, 0.7), (90, 400, 0.8, 0.45), (400, 2000, 0.6, 0.3), (2000, 9000, 0.35, 0.22)], seed + 200 + k)
        crash *= np.minimum(times(1.6) / r.uniform(0.03, 0.08), 1.0)
        place(crashes, at * SR, crash / (np.max(np.abs(crash)) + 1e-9), r.uniform(0.5, 1.0))
    x += 0.8 * crashes / (np.sqrt(np.mean(crashes ** 2)) + 1e-9)
    # Churning water: air dragged under and bubbling back up, each bubble a little rising chirp.
    for k, at in enumerate(poisson(seconds, lambda s: 45 * surge(s), seed + 4)):
        r = rng(seed + 1000 + k)
        length = r.uniform(0.02, 0.06)
        s = times(length)
        bubble = np.sin(2 * np.pi * np.cumsum(r.uniform(300, 1300) * (1 + 0.5 * s / length)) / SR) * np.exp(-s / (length / 3))
        place(x, at * SR, bubble * np.minimum(s / 0.001, 1.0), r.uniform(0.05, 0.2))
    return finish(limited(looped(x, 1.5)), 0.29, loop=True)


def avalanche_roar(seed=280):
    """An avalanche: a low roar rolling on like thunder that does not stop, the ground drumming under it,
    over it the hiss of snow and ice racing past, and the thump of great blocks tumbling in it."""
    seconds = 11.5
    roll = wander(seconds, 0.7, seed + 1, 0.55, 1.0)
    surge = wander(seconds, 0.25, seed + 2, 0.7, 1.0)

    def shape(t, f):
        s = surge(t)
        rumble = (1.4 * peak(f, 50, 0.5) + 0.5 * peak(f, 160, 0.6)) * highpass(f, 20, 3)
        roar = 0.6 * tilt(f, 110, -0.9) * highpass(f, 40, 2) * lowpass(f, 4000)
        hiss = 0.07 * peak(f, 5000, 0.55)
        return roll(t) * s * rumble + s * roar + (0.5 + 0.5 * s) * hiss

    x = spectral_noise(seconds, shape, seed, n_fft=8192, hop=2048)
    x /= np.sqrt(np.mean(x ** 2))
    for k, at in enumerate(poisson(seconds, lambda s: 3.0 * roll(s), seed + 3)):
        r = rng(seed + 100 + k)
        thump = burst(0.4, 40, r.uniform(300, 900), r.uniform(0.06, 0.15), seed + 200 + k, attack=0.004)
        place(x, at * SR, thump / (np.max(np.abs(thump)) + 1e-9), r.uniform(0.2, 0.5))
    # Ice and crust breaking up as it goes.
    for k, at in enumerate(poisson(seconds, 1.6, seed + 4)):
        r = rng(seed + 400 + k)
        crunch = burst(0.1, 800, 6000, r.uniform(0.008, 0.02), seed + 500 + k, attack=0.0004)
        place(x, at * SR, crunch / (np.max(np.abs(crunch)) + 1e-9), r.uniform(0.08, 0.22))
    return finish(limited(looped(x, 1.5)), 0.31, loop=True)


def sinkhole_collapse(seed):
    """The ground giving way: the rock cracking faster and faster, then the crumbling rush of it breaking
    up, the deep thud of it all landing far below, and earth and stones pouring in after it, the hole
    booming hollow."""
    seconds = 4.6
    t = times(seconds)
    fall = rng(seed).uniform(1.1, 1.6)
    x = np.zeros(t.size)
    # The groan of the rock under strain, swelling until it goes, then rumbling away.
    groan = filtered(rng(seed + 1).standard_normal(t.size), lambda f: peak(f, 90, 0.5) * highpass(f, 25, 3))
    groan /= np.max(np.abs(groan)) + 1e-9
    x += 0.35 * groan * np.minimum(t / 0.05, 1.0) * np.where(t < fall, 0.3 + 0.7 * (t / fall) ** 2, np.exp(-(t - fall) / 1.4))
    # Cracks: the rock splitting, the first one sharp, then faster and faster.
    for k, at in enumerate([0.01] + poisson(fall, lambda s: 6 + 30 * (s / fall) ** 2, seed + 2)):
        r = rng(seed + 100 + k)
        crack = burst(0.25, r.uniform(150, 400), r.uniform(2500, 7000), r.uniform(0.01, 0.04), seed + 200 + k, attack=0.0004)
        place(x, at * SR, crack / (np.max(np.abs(crack)) + 1e-9), (0.8 if k == 0 else r.uniform(0.3, 0.8)) * (0.6 + 0.4 * at / fall))
    # The thud of it landing: the ground jumping, and the boom dying away high notes first.
    s = times(seconds - fall)
    sweep = np.sin(2 * np.pi * np.cumsum(65 - 37 * np.minimum(s / 0.4, 1.0)) / SR) * np.exp(-s / 0.5) * np.minimum(s / 0.005, 1)
    tail = band_decay(seconds - fall, [(20, 80, 1.0, 1.2), (80, 250, 0.7, 0.7), (250, 1000, 0.35, 0.35), (1000, 4000, 0.15, 0.12)],
                      seed + 3)
    tail /= np.max(np.abs(tail)) + 1e-9
    crunch = burst(seconds - fall, 120, 2500, 0.12, seed + 4, attack=0.002)
    crunch /= np.max(np.abs(crunch)) + 1e-9
    place(x, fall * SR, sweep + 0.8 * tail + 0.6 * crunch)
    # Crumbling: clods and grit by the hundred as it breaks up, then the odd big stone after it.
    for k, at in enumerate(poisson(seconds, lambda q: 140 * np.exp(-(q - fall) / 0.9) if q > fall - 0.15 else 0.0, seed + 5)):
        r = rng(seed + 1000 + k)
        clod = burst(0.08, r.uniform(200, 600), r.uniform(1500, 5000), r.uniform(0.005, 0.02), seed + 2000 + k)
        place(x, at * SR, clod / (np.max(np.abs(clod)) + 1e-9), r.uniform(0.1, 0.35))
    for k, at in enumerate(poisson(seconds, lambda q: 6 * np.exp(-(q - fall) / 1.2) if q > fall else 0.0, seed + 6)):
        r = rng(seed + 4000 + k)
        stone = burst(0.3, 50, r.uniform(600, 1200), r.uniform(0.04, 0.08), seed + 5000 + k, attack=0.002)
        place(x, at * SR, stone / (np.max(np.abs(stone)) + 1e-9), r.uniform(0.25, 0.5))
    # Earth pouring in over the edge.
    pour = filtered(rng(seed + 7).standard_normal(t.size), lambda f: highpass(f, 400, 2) * lowpass(f, 4000, 2))
    pour /= np.sqrt(np.mean(pour ** 2)) + 1e-9
    x += 0.12 * pour * np.where(t < fall, 0.0, np.minimum((t - fall) / 0.3, 1.0) * np.exp(-(t - fall) / 2.0))
    x = reverb(x, 1.0, seed + 8, wet=0.3, damping=3000, echoes=((0.18, 0.2),))
    return finish(x, 0.9)


def acid_rain_hiss(seed=300):
    """Acid rain: a steady downpour, and everywhere it lands the fizz and sizzle of something being eaten
    away, crackling like fat in a pan."""
    seconds = 10.0
    surge = wander(seconds, 0.35, seed + 1, 0.7, 1.0)
    flutter = wander(seconds, 6.0, seed + 2, 0.5, 1.0)

    def rain(t, f):
        return surge(t) * highpass(f, 350, 2) * lowpass(f, 7000, 2) * (1.0 + 0.5 * peak(f, 1800, 0.7))

    x = spectral_noise(seconds, rain, seed)
    x /= np.sqrt(np.mean(x ** 2))
    # Drops: a patter on leaves and stone.
    r = rng(seed + 3)
    drops = np.zeros_like(x)
    patter = [burst(0.03, 800, 6000, r.uniform(0.002, 0.006), seed + 10 + k, attack=0.0002) for k in range(8)]
    for at in poisson(seconds, lambda s: 220 * surge(s), seed + 4):
        drop = patter[r.integers(len(patter))]
        place(drops, at * SR, drop / (np.max(np.abs(drop)) + 1e-9), r.uniform(0.1, 0.6) * r.choice([-1, 1]))
    x += 0.4 * drops / (np.sqrt(np.mean(drops ** 2)) + 1e-9)
    # The sizzle: a dense crackle of tiny pops, now thicker, now thinner, over a fizzing hiss.
    pops = np.zeros_like(x)
    clicks = [burst(0.006, 2000, 10000, r.uniform(0.0003, 0.0012), seed + 30 + k, attack=0.00005) for k in range(8)]
    for at in poisson(seconds, lambda s: 1400 * flutter(s) * surge(s), seed + 5):
        click = clicks[r.integers(len(clicks))]
        place(pops, at * SR, click / (np.max(np.abs(click)) + 1e-9), min(r.pareto(3.0), 3.0) * r.choice([-1, 1]))
    x += 0.6 * pops / (np.sqrt(np.mean(pops ** 2)) + 1e-9)

    def fizz(t, f):
        return flutter(t) * peak(f, 6000, 0.35)

    hiss = spectral_noise(seconds, fizz, seed + 6)
    x += 0.3 * hiss / (np.sqrt(np.mean(hiss ** 2)) + 1e-9)
    return finish(limited(looped(x, 1.0)), 0.22, loop=True)


# --- the sound events ------------------------------------------------------------------------

# event: (files [(name, synthesizer)], subtitle, streamed, attenuation distance or None for sounds heard all around)
EVENTS = {
    "disaster.tornado.roar": ([("tornado_roar", tornado_roar)], "Tornado roars", True, 380),
    "disaster.tornado.siren": ([("siren", siren)], "Warning siren wails", True, None),
    "disaster.wind.howl": ([("wind_howl", wind_howl)], "Wind howls", True, 320),
    "disaster.blizzard.wind": ([("blizzard_wind", lambda: wind_howl(30, 650, 1700, hiss=0.12, gusty=0.75))], "Blizzard howls", True, None),
    "disaster.sandstorm.wind": ([("sandstorm_wind", sandstorm_wind)], "Sandstorm roars", True, None),
    "disaster.quake.rumble": ([("quake_rumble", quake_rumble)], "Ground rumbles", True, None),
    "disaster.quake.jolt": ([(f"quake_jolt{k + 1}", lambda k=k: quake_jolt(80 + 10 * k)) for k in range(3)], "Ground jolts", False, None),
    "disaster.eruption.blast": ([(f"eruption_blast{k + 1}", lambda k=k: eruption_blast(100 + 10 * k)) for k in range(3)], "Volcano erupts",
                                False, 2400),
    "disaster.eruption.roar": ([("eruption_roar", eruption_roar)], "Volcano roars", True, 700),
    "disaster.meteor.whoosh": ([(f"meteor_whoosh{k + 1}", lambda k=k: meteor_whoosh(170 + 10 * k)) for k in range(2)], "Meteor screams overhead",
                               False, 320),
    "disaster.meteor.impact": ([(f"meteor_impact{k + 1}", lambda k=k: meteor_impact(200 + 10 * k)) for k in range(3)], "Meteor strikes", False,
                               1600),
    "disaster.meteor.boom": ([(f"sonic_boom{k + 1}", lambda k=k: sonic_boom(230 + 10 * k)) for k in range(2)], "Sonic boom", False, 2400),
    "disaster.fire.roar": ([("fire_roar", fire_roar)], "Wildfire roars", True, 140),
    "disaster.hail": ([("hail", hail)], "Hail clatters", True, None),
    "disaster.hurricane.wind": ([("hurricane_wind", hurricane_wind)], "Hurricane howls", True, None),
    "disaster.tsunami.roar": ([("tsunami_roar", tsunami_roar)], "Tsunami roars", True, None),
    "disaster.avalanche.roar": ([("avalanche_roar", avalanche_roar)], "Avalanche roars", True, 600),
    "disaster.sinkhole.collapse": ([(f"sinkhole_collapse{k + 1}", lambda k=k: sinkhole_collapse(320 + 10 * k)) for k in range(3)],
                                   "Ground collapses", False, 240),
    "disaster.acid_rain.hiss": ([("acid_rain_hiss", acid_rain_hiss)], "Acid rain sizzles", True, None),
}


def sound_entries(names, stream, attenuation):
    out = []
    for name in names:
        entry = {"name": f"naturesmod:disaster/{name}"}
        if stream:
            entry["stream"] = True
        if attenuation:
            entry["attenuation_distance"] = attenuation
        out.append(entry)
    return out


def sounds_json():
    return {event: {"sounds": sound_entries([n for n, _ in files], stream, attenuation), "subtitle": f"subtitles.naturesmod.{event}"}
            for event, (files, _, stream, attenuation) in EVENTS.items()}


def lang():
    return {f"subtitles.naturesmod.{event}": subtitle for event, (_, subtitle, _, _) in EVENTS.items()}


def build():
    for files, _, _, _ in EVENTS.values():
        for name, synthesize in files:
            write(name, synthesize())
    with open(os.path.join(ASSETS, "sounds.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(sounds_json(), f, indent=2)
        f.write("\n")
    return EVENTS


if __name__ == "__main__":
    events = build()
    total = sum(os.path.getsize(os.path.join(OUT, n)) for n in os.listdir(OUT))
    print(f"wrote {len(os.listdir(OUT))} sounds ({total // 1024} KiB) for {len(events)} events")
