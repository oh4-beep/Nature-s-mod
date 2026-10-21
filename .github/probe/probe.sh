#!/usr/bin/env bash
# Development aid: prints public/protected signatures of the Minecraft classes the
# mod builds against, so API changes between Minecraft versions are easy to spot.
set -u
cd "$(dirname "$0")/../.."

CP=""
while IFS= read -r jar; do
	if unzip -Z1 "$jar" 2>/dev/null | grep -q '^net/minecraft/'; then
		echo "MC JAR: $jar"
		CP="$CP:$jar"
	fi
done < <(find "$HOME/.gradle/caches" .gradle -name '*.jar' -size +5M 2>/dev/null | grep -iv sources | sort -u)
CP="${CP#:}"

ALL=$(for j in ${CP//:/ }; do unzip -Z1 "$j"; done | grep '\.class$' | sort -u)

echo "===== CLASS LOCATIONS ====="
while IFS= read -r name; do
	[ -z "$name" ] && continue
	echo "$ALL" | grep -E "/${name}(\\\$[A-Za-z0-9_]+)?\.class$" | grep -v '\$[0-9]' | head -8
done < .github/probe/names.txt

echo "===== SIGNATURES ====="
while IFS= read -r line; do
	[ -z "$line" ] && continue
	cls="${line%%|*}"
	filter=""
	[[ "$line" == *"|"* ]] && filter="${line#*|}"
	echo "----- $cls ${filter:+(filter: $filter)}"
	if [ -n "$filter" ]; then
		javap -cp "$CP" -protected "$cls" 2>&1 | grep -E "$filter" | head -120
	else
		javap -cp "$CP" -protected "$cls" 2>&1 | head -220
	fi
done < .github/probe/classes.txt
