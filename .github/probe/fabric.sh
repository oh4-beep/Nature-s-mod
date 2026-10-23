#!/usr/bin/env bash
# Development aid: prints the public API of selected Fabric API classes (javap) after the build has fetched them.
set -u
cd "$(dirname "$0")/../.."
JARS=$(find "$HOME/.gradle/caches" .gradle -name 'fabric-*.jar' ! -name '*sources*' 2>/dev/null | sort -u)
mkdir -p /tmp/fab
for jar in $JARS; do
	for cls in $(unzip -Z1 "$jar" 2>/dev/null | grep -E '^net/fabricmc/fabric/api/(command/v2|networking/v1|client/networking/v1|particle/v1|client/particle/v1|event/lifecycle/v1|client/event/lifecycle/v1|client/rendering/v1)/[A-Za-z$]+\.class$' | grep -v -E 'Impl|Mixin|\$[0-9]'); do
		name="${cls%.class}"
		echo "===== $name ($(basename "$jar"))"
		javap -cp "$jar" "${name//\//.}" 2>&1 | head -60
	done
done
