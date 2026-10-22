"""Summarises Java Flight Recorder CPU samples: the methods that take the most time on the game's
threads (server, render, worldgen workers), counted where they run (self) and anywhere on the
stack (total). Usage: python3 hotspots.py <recording.jfr>..."""

import collections
import json
import subprocess
import sys

GAME_THREADS = ("Server thread", "Render thread", "Worker-Main", "Server-Worker", "IO-Worker", "Chunk")


def samples(path):
    """(thread, [(full type name, method)...] from the top of the stack) for every sample on a game thread."""
    out = subprocess.run(["jfr", "print", "--json", "--events", "jdk.ExecutionSample", path], capture_output=True, text=True)
    if out.returncode != 0:
        print(f"[profile] {path}: {out.stderr.strip()[:200]}")
        return
    for event in json.loads(out.stdout)["recording"]["events"]:
        thread = (event["values"].get("sampledThread") or {}).get("javaName") or ""
        if thread.startswith(GAME_THREADS):
            frames = (event["values"].get("stackTrace") or {}).get("frames") or []
            yield thread, [(f["method"]["type"]["name"], f["method"]["name"]) for f in frames]


def short(frame):
    return f"{frame[0].split('.')[-1]}.{frame[1]}"


def main():
    self_time, total, mod_total, threads = (collections.Counter() for _ in range(4))
    count = 0
    for path in sys.argv[1:]:
        for thread, stack in samples(path):
            count += 1
            threads[thread.split(" #")[0].rstrip("0123456789-")] += 1
            if stack:
                self_time[short(stack[0])] += 1
            total.update({short(f) for f in stack})
            mod_total.update({short(f) for f in stack if "naturesmod" in f[0]})
    print(f"[profile] {count} samples; by thread: {dict(threads.most_common(8))}")
    for title, counter, n in (("self time (where the CPU actually is)", self_time, 40), ("total time, the mod's own methods", mod_total, 40),
                              ("total time, everything", total, 60)):
        print(f"[profile] --- {title} ---")
        for name, hits in counter.most_common(n):
            print(f"[profile] {100.0 * hits / max(1, count):5.1f}%  {name}")


if __name__ == "__main__":
    main()
