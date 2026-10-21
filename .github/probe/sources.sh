#!/usr/bin/env bash
# Development aid: decompiles Minecraft and prints selected vanilla classes (or the lines of them
# matching a pattern) so the mod can follow the exact API of the targeted version.
set -u
cd "$(dirname "$0")/../.."

JARS=$(find "$HOME/.gradle/caches" .gradle -name '*sources.jar' 2>/dev/null | grep -i minecraft | sort -u)
echo "SOURCE JARS:"
echo "$JARS"
mkdir -p /tmp/src
for jar in $JARS; do
	unzip -q -o "$jar" -d /tmp/src
done

echo "===== PACKAGES ====="
find /tmp/src/net/minecraft -name '*.java' | sed 's#/tmp/src/##' | grep -E 'entity/animal|client/model/|renderer/entity/|item/equipment|world/item/[A-Z]' | sort | head -900

while IFS= read -r line; do
	[ -z "$line" ] && continue
	name="${line%%|*}"
	filter=""
	[[ "$line" == *"|"* ]] && filter="${line#*|}"
	for file in $(find /tmp/src -name "$name" | sort); do
		echo "===== FILE ${file#/tmp/src/} ${filter:+(filter: $filter)}"
		if [ -n "$filter" ]; then
			grep -nE "$filter" "$file" | head -160
		else
			cat -n "$file" | head -700
		fi
	done
done < .github/probe/sources.txt
