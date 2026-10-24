#!/usr/bin/env bash
# Development aid: prints the vanilla data pack (but its structure templates) and the game sources of the given
# packages as base64 lines, to unpack and work against locally. Runs after data.sh and sources.sh have unpacked them.
set -u
if [ -d /tmp/vanilla/data/minecraft ]; then
	tar czf /tmp/vanilla-data.tgz -C /tmp/vanilla/data/minecraft --exclude=./structure .
	echo "DATA TGZ: $(stat -c %s /tmp/vanilla-data.tgz) bytes"
	base64 -w 4000 /tmp/vanilla-data.tgz | sed 's/^/B64DATA|/'
fi
if [ -d /tmp/src ] && [ $# -gt 0 ]; then
	(cd /tmp/src && tar czf /tmp/sources.tgz "$@")
	echo "SOURCES TGZ: $(stat -c %s /tmp/sources.tgz) bytes"
	base64 -w 4000 /tmp/sources.tgz | sed 's/^/B64SRC|/'
fi
