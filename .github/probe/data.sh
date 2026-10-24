#!/usr/bin/env bash
# Development aid: prints samples of the vanilla data pack of the targeted Minecraft version, to match its formats.
set -u
cd "$(dirname "$0")/../.."
mkdir -p /tmp/vanilla
for jar in $(find "$HOME/.gradle/caches" .gradle -name '*.jar' 2>/dev/null | grep -i minecraft | grep -v sources | sort -u); do
	if unzip -Z1 "$jar" 2>/dev/null | grep -q '^data/minecraft/worldgen/'; then
		echo "DATA JAR: $jar"
		unzip -q -o "$jar" 'data/*' -d /tmp/vanilla
		break
	fi
done
cd /tmp/vanilla/data/minecraft
echo "===== FOLDERS"
find . -maxdepth 2 -type d | sort
for f in worldgen/configured_feature/oak.json worldgen/configured_feature/ore_iron.json worldgen/configured_feature/disk_sand.json \
	worldgen/configured_feature/trees_plains.json worldgen/configured_feature/patch_grass.json worldgen/configured_feature/lake_lava.json \
	worldgen/configured_feature/glow_lichen.json worldgen/configured_feature/spring_water.json worldgen/configured_feature/flower_default.json \
	worldgen/placed_feature/trees_plains.json worldgen/placed_feature/oak_checked.json worldgen/placed_feature/ore_iron_upper.json \
	loot_table/blocks/snow.json loot_table/blocks/oak_leaves.json loot_table/blocks/sea_pickle.json loot_table/blocks/stone.json \
	recipe/oak_planks.json recipe/furnace.json recipe/iron_ingot_from_smelting_iron_ore.json \
	advancement/story/root.json advancement/story/mine_stone.json damage_type/in_fire.json dimension_type/overworld.json dimension_type/the_nether.json \
	tags/block/saplings.json tags/block/flowers.json tags/block/mineable/shovel.json \
	worldgen/structure_set/villages.json worldgen/structure/desert_pyramid.json worldgen/structure/ruined_portal.json \
	worldgen/density_function/overworld/continents.json worldgen/noise/continentalness.json; do
	echo "===== $f"
	if [ -f "$f" ]; then head -c 2500 "$f"; echo; else echo "(missing)"; fi
done
echo "===== worldgen/biome/plains.json"
head -c 5000 worldgen/biome/plains.json; echo
echo "===== worldgen/noise_settings/overworld.json (head)"
head -c 3000 worldgen/noise_settings/overworld.json; echo
echo "===== feature folders"
ls worldgen | head -40
ls worldgen/configured_feature 2>/dev/null | head -400 | tr '\n' ' '; echo
ls worldgen/feature 2>/dev/null | head -50 | tr '\n' ' '; echo
