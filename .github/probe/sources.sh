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


# Each line: Name.java (whole file), Name.java|regex (matching lines, 3 lines of context after),
# or Name.java@start-end (a line range).
while IFS= read -r line; do
	[ -z "$line" ] && continue
	name="${line%%[|@]*}"
	filter=""
	range=""
	[[ "$line" == *"|"* ]] && filter="${line#*|}"
	[[ "$line" == *"@"* ]] && range="${line#*@}"
	for file in $(find /tmp/src -name "$name" | sort); do
		echo "===== FILE ${file#/tmp/src/} ${filter:+(filter: $filter)}${range:+(lines $range)}"
		if [ -n "$filter" ]; then
			grep -nE -A3 "$filter" "$file" | head -240
		elif [ -n "$range" ]; then
			sed -n "${range%-*},${range#*-}p" "$file" | cat -n
		else
			cat -n "$file" | head -700
		fi
	done
done < .github/probe/sources.txt
