# Nature's Mod

A Fabric mod for **Minecraft Java 26.3** that rebuilds the Overworld into a wilder, more realistic world:
towering mountain ranges, rivers that run down from the peaks over waterfalls, misty plunge pools,
gaping cave mouths, ancient giant trees, and trees that actually topple when you chop them.

## What it changes

### Terrain
- **A taller world.** The Overworld goes up to **y = 575** (vanilla stops at 319) so mountains have room to grow.
- **Colossal mountains.** Great ranges tower two to three times higher than vanilla, with summits around y = 400-480
  and sharper, jagged peaks that pierce the clouds, while foothills still roll gently and lowlands stay lowlands.
- **Larger landscapes.** Continents, mountain ranges and river valleys are ~40% larger and biomes 25% larger, so the
  land reads as broad, believable scenery instead of patchwork, and the mountains stay majestic instead of spiky.
- **Deeper oceans.**
- **Dramatic cave openings.** Natural cave entrances are larger and more common.
- **A real snow line.** Every peak above roughly y = 290 is capped with snow, with bare rock and ice on steep faces.

### Water
- **Mountain rivers.** Rivers rise from springs high in the mountains and follow the land downhill for hundreds of
  blocks, carving gorges (and the occasional tunnel) through ridges, stepping down in cascades and plunging over
  waterfalls until they reach the sea or a lake.
- **Cliff waterfalls.** Streams run to the edge of cliffs and pour over into plunge pools. In freezing places they
  are frozen into icefalls instead.
- **Cascading pools.** Chains of natural terraced pools stepping down hillsides, each spilling into the next.
- **Alpine tarns.** Clear lakes cradled in high mountain hollows, ringed with gravel and boulders.
- **Glaciers.** Tongues of ice pour from snowy basins high on the great peaks down into the valleys below: snowfields
  up top, bare ice lower down, deep blue ice in the walls of the crevasses that crack open where the ice runs
  steep, rubble moraines along the flanks, and a meltwater lake at the snout.
- **Cenotes and sinkholes.** Collapsed shafts in jungles and savannas, opening into caverns with turquoise pools
  and vines trailing all the way down.

### Caves
- **Grand cave mouths.** Huge arched openings into hillsides: mossy floors near the light, vines and glow berries
  hanging from the arch, dripstone deeper inside, and sometimes a spring flowing out of the cave.

### Landmarks
- **Natural arches.** Banded terracotta arches in the badlands, sandstone arches in the desert, stone arches in the
  highlands and sea arches on rocky beaches.
- **Hoodoos.** Clusters of striped rock spires with heavy cap stones rising out of the badlands.
- **Sea stacks.** Weathered rock pillars standing in the surf off the coast, grass on their crowns.
- **Hanging gardens.** Curtains of vines spill down cliff faces in jungles, swamps and forests, with moss creeping
  over the brink.

### Forests
- **Giant trees.** Ancient oaks and dark oaks with flared roots and sprawling limbs, autumn giants in orange, red and
  yellow, golden birches, towering redwoods in old-growth taigas, weeping willows along swamps and riverbanks,
  bottle-trunked baobabs in the savanna, and bleached dead snags.
- **Fallen logs.** Long trunks of trees that fell years ago, lying beside their stumps under moss, mushrooms and
  shelf fungi.
- **Richer forest floors.** Leaf litter, bushes, ferns, boulders and fireflies near water.

### Trees that fall
- **Chop through the trunk and the tree topples.** Everything above the cut swings down around the stump and lands
  on the ground as a fallen tree, crown and all. Big trees need their whole trunk cut through, just like in reality,
  and the stump stays behind. Don't stand where it falls.
- Sneak while chopping to take a single log the vanilla way.
- **Lightning** can split a tree and knock it over, and during **thunderstorms** the wind occasionally brings down a
  tree near you.

### Atmosphere
- Waterfalls are wreathed in **mist and spray** at their foot and **roar** louder the higher they are.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer for Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and the Nature's Mod jar into your `mods` folder.
3. Create a **new world**. Existing worlds keep their old terrain; only newly generated chunks use the new terrain,
   which leaves visible seams.

The mod is needed on the server for world generation and tree felling, and on the client for the waterfall effects.

## Configuration

`config/naturesmod.json` is created on first launch:

| Option | Default | What it does |
| --- | --- | --- |
| `treeFelling` | `true` | Chopping through a natural tree's trunk makes it topple. |
| `fellingRequiresAxe` | `true` | Only fell trees when using an axe. |
| `sneakToChopNormally` | `true` | Sneak to break a single log. |
| `fellInCreative` | `false` | Also fell trees in creative mode. |
| `fallingTreesHurt` | `true` | Falling logs hurt whatever they land on. |
| `logsPerExtraDurability` | `8` | Extra axe wear: one durability per this many felled logs (0 = none). |
| `maxTreeLogs` | `2500` | Largest tree, in logs, that can be felled at once. |
| `lightningFellsTrees` | `true` | Lightning strikes can fell trees. |
| `stormWindthrow` | `true` | Thunderstorms occasionally blow over trees near players. |
| `windthrowMinutes` | `6` | Average minutes of thunderstorm between wind-thrown trees per player. |
| `waterfallMist` | `true` | Mist and spray at the foot of waterfalls. |
| `mistDensity` | `1.0` | Amount of waterfall mist. |
| `waterfallSounds` | `true` | Waterfalls roar. |

Only natural trees fall: a group of logs must carry natural (non-player-placed) leaves, so log cabins and other
builds are never knocked over.

## Building

Requires JDK 25.

```sh
./gradlew build          # the mod jar ends up in build/libs/
./gradlew runClient      # play with the mod in a development client
./gradlew runSmokeTest   # headless world generation check that prints a terrain report
```

The smoke test boots a dedicated server, generates the spawn area plus the nearest example of a dozen landmark
biomes (peaks, jungle, badlands...), prints statistics, ASCII relief maps and feature counts, renders a top-down
map and a bird's-eye view of every region to `run/smoketest/maps/`, fells a tree, and exits. CI runs it on every
push (see `.github/workflows/build.yml`). It needs an accepted `eula.txt` in `run/smoketest/`.

### World generation data

The terrain and feature JSON in `src/main/resources/data` is generated by `tools/worldgen/generate.py` from the
vanilla data pack of the targeted Minecraft version, so it always matches the game's exact data format:

```sh
git clone --depth 1 --branch 26.3-data https://github.com/misode/mcmeta vanilla
python3 tools/worldgen/generate.py --vanilla vanilla/data
```

Tweak the terrain in `tools/worldgen/terrain.py` (world height, mountain stretch, biome scale, cave entrances, snow
line) and the features in `tools/worldgen/features.py`, then re-run the script. Which biomes get which feature is
set in `ModBiomeModifications.java`.

## License

MIT
