"""Summarises Java Flight Recorder CPU samples: the methods that take the most time on the game's
threads (server, render, worldgen workers), counted where they run (self) and anywhere on the
stack (total). Usage: python3 hotspots.py <recording.jfr>..."""

import collections
import subprocess
import sys

GAME_THREADS = ("Server thread", "Render thread", "Worker-Main", "Server-Worker", "IO-Worker", "Chunk")


def samples(path):
    """(thread, [(full type name, method)...] from the top of the stack) for every sample on a game thread.

    Reads `jfr print` as text, a line at a time: a long recording printed whole (as JSON, say) runs to
    gigabytes and takes the runner down with it."""
    proc = subprocess.Popen(["jfr", "print", "--stack-depth", "64", "--events", "jdk.ExecutionSample", path],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    thread, frames, in_stack = "", [], False
    for line in proc.stdout:
        line = line.strip()
        if line.startswith("jdk.ExecutionSample"):
            thread, frames, in_stack = "", [], False
        elif line.startswith("sampledThread = "):
            # sampledThread = "Server thread" (javaThreadId = 33)
            quoted = line.split('"')
            thread = quoted[1] if len(quoted) > 2 else ""
        elif line.startswith("stackTrace = ["):
            in_stack = True
        elif in_stack:
            if line.startswith("]"):
                in_stack = False
            elif line and line != "...":
                # net.minecraft.world.level.levelgen.NoiseChunk.fillSlice(boolean, int)  line: 123
                typ, _, method = line.split("(")[0].rpartition(".")
                frames.append((typ, method))
        elif line == "}" and thread.startswith(GAME_THREADS):
            yield thread, frames
    if proc.wait() != 0:
        print(f"[profile] {path}: jfr print failed with code {proc.returncode}")


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
