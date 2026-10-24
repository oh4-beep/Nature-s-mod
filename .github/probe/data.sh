#!/usr/bin/env bash
# Development aid: prints the shape of the vanilla data pack of the targeted Minecraft version, and some of its files
# in full, to match the mod's data to its formats.
set -u
cd "$(dirname "$0")/../.."
probe="$PWD/.github/probe"
mkdir -p /tmp/vanilla
for jar in $(find "$HOME/.gradle/caches" .gradle -name '*.jar' 2>/dev/null | grep -i minecraft | grep -v sources | sort -u); do
	if unzip -Z1 "$jar" 2>/dev/null | grep -q '^data/minecraft/worldgen/'; then
		echo "DATA JAR: $jar"
		unzip -q -o "$jar" 'data/*' -d /tmp/vanilla
		break
	fi
done
echo "===== SCHEMA"
python3 "$probe/schema.py" /tmp/vanilla/data minecraft | sed 's/^/S| /'
cd /tmp/vanilla/data/minecraft
for f in $(cat "$probe/files262.txt"); do
	echo "===== $f"
	if [ -f "$f" ]; then cat "$f"; echo; else echo "(missing)"; ls "$(dirname "$f")" 2>/dev/null | head -400 | tr '\n' ' '; echo; fi
done
echo "===== placed features"
ls worldgen/placed_feature | tr '\n' ' '; echo
