package com.naturesmod.disaster;

import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a wind can tear away: the gust (metres a second) each block gives way to, after the damage
 * indicators of the Enhanced Fujita scale. Leaves and plants go first, then glass and the flimsy
 * fittings of a building (doors, fences, wool), then its wooden walls and roof and the trees (EF3),
 * then rubble stone and the like (EF4), and only the worst tornadoes bring down dressed masonry:
 * bricks, stone bricks, polished and cut stone, concrete (EF5). The ground itself is only scoured.
 */
final class WindDamage {
	static final double NEVER = Double.MAX_VALUE;
	/** Dressed stone and the like, by name: what only an EF5 brings down. */
	private static final String[] MASONRY = {"brick", "tiles", "polished", "smooth_", "chiseled", "cut_", "quartz", "prismarine", "purpur",
			"concrete"};
	/** The light fittings of a building, by name: what goes soon after the glass (if it isn't iron or stone). */
	private static final String[] FLIMSY = {"fence", "door", "sign", "ladder", "carpet", "banner", "bed", "torch", "lantern", "wool",
			"scaffolding", "button", "pressure_plate", "lever", "rail", "pot"};

	private WindDamage() {
	}

	static double threshold(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir()) {
			return 0.0;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F || state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)) {
			return NEVER;
		}
		if (state.is(BlockTags.LEAVES)) {
			return 32.0;
		}
		Block block = state.getBlock();
		if (state.canBeReplaced() || block instanceof VegetationBlock || state.is(BlockTags.FLOWERS) || state.is(WorldGenUtil.SAPLINGS)) {
			return 26.0;
		}
		String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (name.contains("glass") || state.is(Blocks.ICE)) {
			return 36.0;
		}
		if (named(name, MASONRY) && !name.contains("powder")) {
			return 86.0 + 3.0 * Math.sqrt(hardness);
		}
		if (hardness <= 3.0F && named(name, FLIMSY)) {
			return 42.0;
		}
		double resistance = Math.min(block.getExplosionResistance(), 20.0);
		return 30.0 + 14.0 * Math.sqrt(hardness) + 2.5 * resistance;
	}

	private static boolean named(String name, String[] parts) {
		for (String part : parts) {
			if (name.contains(part)) {
				return true;
			}
		}
		return false;
	}

	/** The land itself, which a tornado scours but doesn't carry off whole. */
	static boolean isGround(BlockState state) {
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND)
				|| state.is(BlockTags.TERRACOTTA) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SNOW_BLOCK)
				|| state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE);
	}

	/** Breaks apart rather than flying off whole: leaves, plants, glass. */
	static boolean shatters(BlockState state) {
		String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return state.is(BlockTags.LEAVES) || state.canBeReplaced() || state.getBlock() instanceof VegetationBlock || name.contains("glass")
				|| name.contains("pane") || state.is(Blocks.ICE);
	}
}
