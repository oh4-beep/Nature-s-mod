package com.naturesmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.LongSet;

import com.naturesmod.NaturesMod;
import com.naturesmod.block.ModBlocks;
import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.SelfPlacing;
import com.naturesmod.worldgen.WaterWorks;
import com.naturesmod.worldgen.WorldGenUtil;
import com.naturesmod.worldgen.wild.WildOverworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The landmarks and the life of the wild Overworld: lavender planted in rows across whole valleys,
 * volcanoes with lava in their craters, fields of steam vents, terraced hot springs, brine pools
 * crusted with salt, the stone trees of the petrified forests, the craters of fallen meteors with
 * their iron hearts, giant glowcaps in the forests and the caves, spires and crusts of crystal in
 * the deep caverns, and dry-stone walls wandering over the moors.
 */
public record WildFeature(Style style) implements SelfPlacing {
	public static final MapCodec<WildFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Style.CODEC.fieldOf("style").forGetter(WildFeature::style)
	).apply(instance, WildFeature::new));

	private static final ResourceKey<Biome> LAVENDER_FIELDS = WildOverworld.biome("lavender_fields");
	private static final ResourceKey<LootTable> METEOR_LOOT = ResourceKey.create(Registries.LOOT_TABLE, NaturesMod.id("chests/land/meteor"));

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 1);
		boolean placed = switch (style) {
			case LAVENDER_FIELD -> lavenderField(level, random, origin);
			case VOLCANO -> volcano(level, window, random, origin);
			case STEAM_VENTS -> steamVents(level, window, random, origin);
			case HOT_SPRING -> hotSpring(level, window, random, origin);
			case BRINE_POOL -> brinePool(level, window, random, origin);
			case PETRIFIED_TREE -> petrifiedTree(level, window, random, origin);
			case METEOR_CRATER -> meteorCrater(level, window, random, origin);
			case GIANT_GLOWCAP -> giantGlowcap(level, window, random,
					new BlockPos(origin.getX(), WorldGenUtil.groundY(level, origin.getX(), origin.getZ()) + 1, origin.getZ()));
			case GROTTO_GLOWCAP -> giantGlowcap(level, window, random, origin);
			case CRYSTAL_SPIRE -> crystalSpire(level, window, random, origin);
			case CRYSTAL_GROWTH -> crystalGrowth(level, window, random, origin);
			case STONE_WALL -> stoneWall(level, window, random, origin);
		};
		if (placed) {
			FeatureStats.placed("wild_" + style.getSerializedName(), origin);
		}
		return placed;
	}

	// --- helpers -------------------------------------------------------------------------

	private static boolean clearOfStructures(WorldGenLevel level, BlockPos origin, int reach) {
		for (int dx = -reach; dx <= reach; dx += reach) {
			for (int dz = -reach; dz <= reach; dz += reach) {
				if (!WorldGenUtil.isStructureFree(level, origin.offset(dx, 0, dz))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Open air, or a plant, leaves or snow that may be built over. */
	private static boolean open(BlockState state) {
		return state.isAir() || WorldGenUtil.isClearable(state) || state.is(BlockTags.LEAVES) || state.is(Blocks.SNOW);
	}

	private static boolean solid(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && state.getFluidState().isEmpty() && state.isFaceSturdy(level, pos, Direction.UP);
	}

	private static int maxRadius(WorldGenUtil.Window window, int x, int z) {
		return Math.min(Math.min(x - window.minX(), window.maxX() - x), Math.min(z - window.minZ(), window.maxZ() - z));
	}

	/** Puts a block, first clearing the upper half of a tall plant that stood there. */
	private static void put(WorldGenLevel level, BlockPos pos, BlockState state) {
		BlockState old = level.getBlockState(pos);
		if (old.getBlock() instanceof DoublePlantBlock && old.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			BlockPos above = pos.above();
			if (level.getBlockState(above).is(old.getBlock())) {
				level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		level.setBlock(pos, state, 2);
	}

	private static BlockState pillar(Block block, Direction.Axis axis) {
		return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	private static BlockState cluster(Block block, Direction facing, boolean wet) {
		BlockState state = block.defaultBlockState().setValue(AmethystClusterBlock.FACING, facing);
		return state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, wet) : state;
	}

	private static long mix(long seed, long a, long b) {
		long h = seed ^ a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL;
		h = (h ^ h >>> 33) * 0xFF51AFD7ED558CCDL;
		h = (h ^ h >>> 33) * 0xC4CEB9FE1A85EC53L;
		return h ^ h >>> 33;
	}

	private static double hash01(long seed, int x, int z) {
		return (mix(seed, x, z) >>> 11) * 0x1.0p-53;
	}

	/** Smooth value noise in [-1, 1], cells of the given size, the same in every chunk. */
	private static double blob(long seed, int x, int z, double cell) {
		double fx = x / cell;
		double fz = z / cell;
		int x0 = Mth.floor(fx);
		int z0 = Mth.floor(fz);
		double tx = fx - x0;
		double tz = fz - z0;
		tx = tx * tx * (3 - 2 * tx);
		tz = tz * tz * (3 - 2 * tz);
		double top = Mth.lerp(tx, hash01(seed, x0, z0), hash01(seed, x0 + 1, z0));
		double bottom = Mth.lerp(tx, hash01(seed, x0, z0 + 1), hash01(seed, x0 + 1, z0 + 1));
		return Mth.lerp(tz, top, bottom) * 2.0 - 1.0;
	}

	// --- lavender -------------------------------------------------------------------------

	/**
	 * Lavender across a whole chunk of the lavender fields. Some valleys are planted in long
	 * straight rows (the same rows from one chunk to the next), others are wild drifts of it.
	 */
	private boolean lavenderField(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int minX = origin.getX() & ~15;
		int minZ = origin.getZ() & ~15;
		long seed = level.getSeed();
		long region = mix(seed, Math.floorDiv(minX, 96), Math.floorDiv(minZ, 96));
		boolean rows = (region & 3) != 0;
		boolean alongX = (region & 4) == 0;
		int spacing = 3 + (int) (region >>> 3 & 1);
		BlockState lavender = ModBlocks.LAVENDER.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int planted = 0;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = minX + dx;
				int z = minZ + dz;
				int ground = WorldGenUtil.groundY(level, x, z);
				BlockState soil = level.getBlockState(pos.set(x, ground, z));
				if (!soil.is(Blocks.GRASS_BLOCK) && !soil.is(Blocks.DIRT) && !soil.is(Blocks.COARSE_DIRT)) {
					continue;
				}
				pos.setY(ground + 1);
				BlockState here = level.getBlockState(pos);
				if (!here.isAir() && !(WorldGenUtil.isClearable(here) && here.getFluidState().isEmpty())) {
					continue;
				}
				if (!level.getBiome(pos).is(LAVENDER_FIELDS)) {
					continue;
				}
				boolean plant;
				if (rows) {
					int across = alongX ? z : x;
					plant = Math.floorMod(across, spacing) != 0 && random.nextInt(16) != 0;
				} else {
					plant = blob(seed, x, z, 7.0) > -0.05 && random.nextInt(5) != 0;
				}
				if (plant) {
					put(level, pos, lavender);
					planted++;
				}
			}
		}
		return planted > 0;
	}

	// --- volcanoes and steam ---------------------------------------------------------------

	/** A volcanic cone of basalt, blackstone and tuff, with a lake of lava in its crater and streaks of magma down its flanks. */
	private boolean volcano(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int ox = origin.getX();
		int oz = origin.getZ();
		int radius = Math.min(9 + random.nextInt(6), maxRadius(window, ox, oz) - 1);
		if (radius < 7 || !clearOfStructures(level, origin, radius)) {
			return false;
		}
		int base = WorldGenUtil.groundY(level, ox, oz);
		if (!level.getFluidState(new BlockPos(ox, base + 1, oz)).isEmpty()) {
			return false;
		}
		int height = (int) Math.round(radius * 0.9) + random.nextInt(3);
		double crater = radius * 0.3;
		int lava = base + height - 2;
		double[] streaks = new double[2 + random.nextInt(3)];
		for (int i = 0; i < streaks.length; i++) {
			streaks[i] = random.nextDouble() * Math.PI * 2;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > radius) {
					continue;
				}
				int x = ox + dx;
				int z = oz + dz;
				int ground = WorldGenUtil.groundY(level, x, z);
				if (ground < base - 8) {
					continue;
				}
				int top = d < crater ? base + height - 3 - (int) Math.round((crater - d) * 0.8)
						: base + (int) Math.round(height * Math.pow(1.0 - (d - crater) / (radius - crater), 1.3));
				double angle = Math.atan2(dz, dx);
				boolean streak = false;
				for (double s : streaks) {
					streak |= Math.abs(Mth.wrapDegrees(Math.toDegrees(angle - s))) < 7.0;
				}
				for (int y = Math.max(ground + 1, base - 8); y <= top; y++) {
					pos.set(x, y, z);
					BlockState at = level.getBlockState(pos);
					if (!open(at) || !at.getFluidState().isEmpty()) {
						continue;
					}
					level.setBlock(pos, rock(random, d, crater, y == top, streak && y == top), 2);
				}
				if (d < crater) {
					for (int y = Math.max(top + 1, ground + 1); y <= lava; y++) {
						level.setBlock(pos.set(x, y, z), Blocks.LAVA.defaultBlockState(), 2);
					}
				}
			}
		}
		// Steam rising off the rim.
		for (int k = 0; k < 2; k++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int x = ox + (int) Math.round(Math.cos(angle) * (crater + 1.5));
			int z = oz + (int) Math.round(Math.sin(angle) * (crater + 1.5));
			int y = WorldGenUtil.groundY(level, x, z);
			if (level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
				level.setBlock(pos.set(x, y, z), ModBlocks.STEAM_VENT.defaultBlockState(), 2);
			}
		}
		return true;
	}

	private static BlockState rock(RandomSource random, double d, double crater, boolean surface, boolean streak) {
		if (streak) {
			return Blocks.MAGMA_BLOCK.defaultBlockState();
		}
		if (d < crater + 2.0) {
			int roll = random.nextInt(10);
			return roll < 3 ? Blocks.MAGMA_BLOCK.defaultBlockState() : roll < 7 ? Blocks.BLACKSTONE.defaultBlockState()
					: pillar(Blocks.BASALT, Direction.Axis.Y);
		}
		int roll = random.nextInt(20);
		if (surface && roll < 7) {
			return ModBlocks.ASH_BLOCK.defaultBlockState();
		}
		if (roll < 12) {
			return pillar(Blocks.BASALT, Direction.Axis.Y);
		}
		if (roll < 17) {
			return Blocks.BLACKSTONE.defaultBlockState();
		}
		return roll < 19 ? Blocks.TUFF.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
	}

	/** A field of steam vents in a crust of minerals. */
	private boolean steamVents(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int base = WorldGenUtil.groundY(level, origin.getX(), origin.getZ());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int vents = 0;
		int count = 2 + random.nextInt(4);
		for (int k = 0; k < count; k++) {
			int x = origin.getX() + random.nextInt(9) - 4;
			int z = origin.getZ() + random.nextInt(9) - 4;
			if (!window.containsRadius(x, z, 2)) {
				continue;
			}
			int ground = WorldGenUtil.groundY(level, x, z);
			if (Math.abs(ground - base) > 2 || !WorldGenUtil.isTerrain(level.getBlockState(pos.set(x, ground, z)))) {
				continue;
			}
			BlockState above = level.getBlockState(pos.set(x, ground + 1, z));
			if (!above.getFluidState().isEmpty() || !open(above)) {
				continue;
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			level.setBlock(pos.set(x, ground, z), ModBlocks.STEAM_VENT.defaultBlockState(), 2);
			vents++;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (dx * dx + dz * dz > 5 || dx == 0 && dz == 0 || random.nextInt(3) == 0) {
						continue;
					}
					int crustY = WorldGenUtil.groundY(level, x + dx, z + dz);
					BlockState under = level.getBlockState(pos.set(x + dx, crustY, z + dz));
					if (WorldGenUtil.isTerrain(under) && !under.is(ModBlocks.STEAM_VENT)) {
						int roll = random.nextInt(6);
						level.setBlock(pos, roll < 2 ? Blocks.TUFF.defaultBlockState() : roll < 4 ? Blocks.CALCITE.defaultBlockState()
								: pillar(Blocks.BASALT, Direction.Axis.Y), 2);
					}
				}
			}
		}
		return vents > 0;
	}

	// --- springs and pools -----------------------------------------------------------------

	private static boolean flat(WorldGenLevel level, int x, int z, int radius, int tolerance) {
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		for (int k = 0; k < 8; k++) {
			double angle = Math.PI * 2 * k / 8;
			int y = WorldGenUtil.groundY(level, x + (int) Math.round(Math.cos(angle) * radius), z + (int) Math.round(Math.sin(angle) * radius));
			low = Math.min(low, y);
			high = Math.max(high, y);
		}
		int center = WorldGenUtil.groundY(level, x, z);
		return high - low <= tolerance && Math.abs(center - low) <= tolerance;
	}

	/** Terraced pools of steaming water rimmed with calcite, stepping down a hillside. */
	private boolean hotSpring(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int ox = origin.getX();
		int oz = origin.getZ();
		if (!window.containsRadius(ox, oz, 12) || !flat(level, ox, oz, 5, 3) || !clearOfStructures(level, origin, 8)) {
			return false;
		}
		int base = WorldGenUtil.groundY(level, ox, oz);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		if (!level.getFluidState(pos.set(ox, base + 1, oz)).isEmpty()) {
			return false;
		}
		double r1 = 2.8 + random.nextDouble() * 1.6;
		if (!terrace(level, window, random, ox, oz, r1, base, 2)) {
			return false;
		}
		// A second pool a step lower, on the downhill side, where the first one spills over.
		Direction down = null;
		int lowest = base;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int x = ox + side.getStepX() * (int) (r1 + 4);
			int z = oz + side.getStepZ() * (int) (r1 + 4);
			int y = WorldGenUtil.groundY(level, x, z);
			if (y < lowest && y >= base - 4) {
				lowest = y;
				down = side;
			}
		}
		if (down != null) {
			double r2 = 2.0 + random.nextDouble() * 1.2;
			int x = ox + down.getStepX() * (int) Math.round(r1 + r2 + 1.5);
			int z = oz + down.getStepZ() * (int) Math.round(r1 + r2 + 1.5);
			if (window.containsRadius(x, z, 5)) {
				terrace(level, window, random, x, z, r2, WorldGenUtil.groundY(level, x, z), 1);
			}
		}
		return true;
	}

	/** One pool of a hot spring: warm water over a calcite floor, a raised calcite lip, vents bubbling at the bottom. */
	private static boolean terrace(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, int cx, int cz, double radius, int surface,
			int depth) {
		LongSet cells = WaterWorks.pool(level, window, random, cx, cz, radius, radius * (0.8 + random.nextDouble() * 0.4), surface, depth, true);
		if (cells.isEmpty()) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int vents = 0;
		for (long packed : cells) {
			int x = BlockPos.getX(packed);
			int z = BlockPos.getZ(packed);
			int y = surface;
			while (y > surface - 6 && !level.getFluidState(pos.set(x, y, z)).isEmpty()) {
				y--;
			}
			boolean vent = vents < 2 && random.nextInt(6) == 0;
			level.setBlock(pos.set(x, y, z), vent ? ModBlocks.STEAM_VENT.defaultBlockState()
					: random.nextInt(4) == 0 ? Blocks.DRIPSTONE_BLOCK.defaultBlockState() : Blocks.CALCITE.defaultBlockState(), 2);
			if (vent) {
				vents++;
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				int nx = x + side.getStepX();
				int nz = z + side.getStepZ();
				if (cells.contains(BlockPos.asLong(nx, 0, nz)) || !window.contains(nx, nz)) {
					continue;
				}
				BlockState rim = level.getBlockState(pos.set(nx, surface, nz));
				if (rim.getFluidState().isEmpty()) {
					level.setBlock(pos, Blocks.CALCITE.defaultBlockState(), 2);
					BlockState lip = level.getBlockState(pos.set(nx, surface + 1, nz));
					if (lip.isAir() || WorldGenUtil.isClearable(lip) && lip.getFluidState().isEmpty()) {
						level.setBlock(pos, Blocks.CALCITE.defaultBlockState(), 2);
					}
				}
			}
		}
		return true;
	}

	/** A shallow pool of brine on the salt flats, its bed and rim crusted white and grown over with salt crystals. */
	private boolean brinePool(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int ox = origin.getX();
		int oz = origin.getZ();
		if (!window.containsRadius(ox, oz, 10) || !flat(level, ox, oz, 5, 2)) {
			return false;
		}
		int base = WorldGenUtil.groundY(level, ox, oz);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		if (!level.getFluidState(pos.set(ox, base + 1, oz)).isEmpty()) {
			return false;
		}
		double rx = 3.0 + random.nextDouble() * 4.0;
		double rz = 3.0 + random.nextDouble() * 4.0;
		LongSet cells = WaterWorks.pool(level, window, random, ox, oz, rx, rz, base, 1 + random.nextInt(2), true);
		if (cells.isEmpty()) {
			return false;
		}
		BlockState salt = ModBlocks.SALT_BLOCK.defaultBlockState();
		for (long packed : cells) {
			int x = BlockPos.getX(packed);
			int z = BlockPos.getZ(packed);
			int y = base;
			while (y > base - 4 && !level.getFluidState(pos.set(x, y, z)).isEmpty()) {
				y--;
			}
			level.setBlock(pos.set(x, y, z), salt, 2);
			if (y < base - 1 && random.nextInt(5) == 0) {
				level.setBlock(pos.set(x, y + 1, z), cluster(ModBlocks.SALT_CRYSTAL, Direction.UP, true), 2);
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				int nx = x + side.getStepX();
				int nz = z + side.getStepZ();
				if (cells.contains(BlockPos.asLong(nx, 0, nz)) || !window.contains(nx, nz)) {
					continue;
				}
				BlockState rim = level.getBlockState(pos.set(nx, base, nz));
				if (rim.getFluidState().isEmpty() && !rim.isAir()) {
					level.setBlock(pos, salt, 2);
					if (random.nextInt(3) == 0 && level.getBlockState(pos.set(nx, base + 1, nz)).isAir()) {
						level.setBlock(pos, cluster(ModBlocks.SALT_CRYSTAL, Direction.UP, false), 2);
					}
				}
			}
		}
		return true;
	}

	// --- the petrified forest ---------------------------------------------------------------

	/** A tree turned to stone: a standing trunk broken off short, or a long fallen one, with crystal in its cracks. */
	private boolean petrifiedTree(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int ox = origin.getX();
		int oz = origin.getZ();
		if (!window.containsRadius(ox, oz, 9)) {
			return false;
		}
		int base = WorldGenUtil.groundY(level, ox, oz);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		if (!WorldGenUtil.isTerrain(level.getBlockState(pos.set(ox, base, oz))) || !level.getFluidState(pos.set(ox, base + 1, oz)).isEmpty()) {
			return false;
		}
		Block[] crystals = {ModBlocks.CITRINE_CRYSTAL_CLUSTER, ModBlocks.AZURE_CRYSTAL_CLUSTER, Blocks.AMETHYST_CLUSTER};
		if (random.nextInt(5) < 3) {
			// Standing: a short thick stump of a trunk, its top broken off.
			int height = 3 + random.nextInt(6);
			double radius = random.nextInt(3) == 0 ? 1.2 : 0.5;
			BlockState upright = pillar(ModBlocks.PETRIFIED_LOG, Direction.Axis.Y);
			for (int i = 0; i < height; i++) {
				int r = (int) Math.ceil(radius);
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (dx * dx + dz * dz > radius * radius + 0.3 || i >= height - 2 && random.nextInt(3) == 0) {
							continue;
						}
						BlockState at = level.getBlockState(pos.set(ox + dx, base + 1 + i, oz + dz));
						if (open(at) && at.getFluidState().isEmpty()) {
							level.setBlock(pos, upright, 2);
						}
					}
				}
			}
			// Stubs of branches, and crystal grown into the cracks.
			for (Direction side : Direction.Plane.HORIZONTAL) {
				int reach = (int) Math.ceil(radius) + 1;
				if (random.nextInt(3) == 0 && height > 4) {
					int y = base + 2 + random.nextInt(height - 3);
					BlockState stub = pillar(ModBlocks.PETRIFIED_LOG, side.getAxis());
					for (int s = reach; s < reach + 1 + random.nextInt(2); s++) {
						if (open(level.getBlockState(pos.set(ox + side.getStepX() * s, y, oz + side.getStepZ() * s)))) {
							level.setBlock(pos, stub, 2);
						}
					}
				} else if (random.nextInt(3) == 0) {
					int y = base + 1 + random.nextInt(height);
					if (level.getBlockState(pos.set(ox + side.getStepX() * reach, y, oz + side.getStepZ() * reach)).isAir()
							&& level.getBlockState(new BlockPos(ox + side.getStepX() * (reach - 1), y, oz + side.getStepZ() * (reach - 1)))
									.is(ModBlocks.PETRIFIED_LOG)) {
						level.setBlock(pos, cluster(crystals[random.nextInt(crystals.length)], side, false), 2);
					}
				}
			}
			return true;
		}
		// Fallen: a long trunk lying along the ground, half sunk into it.
		Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		int length = 6 + random.nextInt(8);
		BlockState lying = pillar(ModBlocks.PETRIFIED_LOG, along.getAxis());
		int laid = 0;
		for (int s = 0; s < length; s++) {
			int x = ox + along.getStepX() * s;
			int z = oz + along.getStepZ() * s;
			if (!window.contains(x, z)) {
				break;
			}
			int ground = WorldGenUtil.groundY(level, x, z);
			if (ground > base + 1 || ground < base - 1) {
				break;
			}
			BlockState at = level.getBlockState(pos.set(x, base + 1, z));
			if (!open(at) || !at.getFluidState().isEmpty()) {
				break;
			}
			level.setBlock(pos, lying, 2);
			laid++;
			if (random.nextInt(5) == 0) {
				Direction side = along.getClockWise();
				if (random.nextBoolean()) {
					side = side.getOpposite();
				}
				if (level.getBlockState(pos.set(x + side.getStepX(), base + 1, z + side.getStepZ())).isAir()) {
					level.setBlock(pos, cluster(crystals[random.nextInt(crystals.length)], side, false), 2);
				}
			}
		}
		return laid >= 3;
	}

	// --- fallen stars --------------------------------------------------------------------------

	/** The crater of a fallen meteor: a scorched bowl in a ring of thrown-up rubble, the meteorite at its heart. */
	private boolean meteorCrater(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int ox = origin.getX();
		int oz = origin.getZ();
		int radius = Math.min(5 + random.nextInt(6), maxRadius(window, ox, oz) - 4);
		if (radius < 4 || !clearOfStructures(level, origin, radius + 2)) {
			return false;
		}
		int base = WorldGenUtil.groundY(level, ox, oz);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// No craters in lakes, and none blasted through the middle of a forest.
		for (int k = 0; k < 12; k++) {
			double angle = Math.PI * 2 * k / 12;
			int x = ox + (int) Math.round(Math.cos(angle) * radius);
			int z = oz + (int) Math.round(Math.sin(angle) * radius);
			int ground = WorldGenUtil.groundY(level, x, z);
			if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty() || Math.abs(ground - base) > 5) {
				return false;
			}
			for (int y = ground + 1; y < ground + 12; y++) {
				if (level.getBlockState(pos.set(x, y, z)).is(BlockTags.LOGS)) {
					return false;
				}
			}
		}
		double depth = Math.max(3.0, radius * 0.55);
		for (int dx = -radius - 3; dx <= radius + 3; dx++) {
			for (int dz = -radius - 3; dz <= radius + 3; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				int x = ox + dx;
				int z = oz + dz;
				int ground = WorldGenUtil.groundY(level, x, z);
				if (d <= radius) {
					int floor = base - (int) Math.round(depth * (1.0 - (d / radius) * (d / radius)));
					for (int y = ground + 2; y > floor; y--) {
						BlockState at = level.getBlockState(pos.set(x, y, z));
						if ((WorldGenUtil.isTerrain(at) || open(at)) && at.getFluidState().isEmpty()) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
						}
					}
					BlockState scorched = switch (random.nextInt(6)) {
						case 0 -> Blocks.MAGMA_BLOCK.defaultBlockState();
						case 1, 2 -> Blocks.GRAVEL.defaultBlockState();
						case 3 -> Blocks.TUFF.defaultBlockState();
						default -> Blocks.COARSE_DIRT.defaultBlockState();
					};
					if (d > radius * 0.6 && scorched.is(Blocks.MAGMA_BLOCK)) {
						scorched = Blocks.COBBLESTONE.defaultBlockState();
					}
					level.setBlock(pos.set(x, floor, z), scorched, 2);
				} else if (d <= radius + 3) {
					// The rim: rubble thrown up highest just outside the edge.
					int rise = (int) Math.round(2.4 * (1.0 - Math.abs(d - radius - 1.0) / 2.0));
					for (int y = ground + 1; y <= ground + rise; y++) {
						BlockState at = level.getBlockState(pos.set(x, y, z));
						if (open(at) && at.getFluidState().isEmpty()) {
							int roll = random.nextInt(5);
							level.setBlock(pos, roll < 2 ? Blocks.GRAVEL.defaultBlockState() : roll < 4 ? Blocks.COARSE_DIRT.defaultBlockState()
									: Blocks.COBBLESTONE.defaultBlockState(), 2);
						}
					}
				}
			}
		}
		// The meteorite itself, half buried, with veins of iron and a lump of something hot.
		int heart = base - (int) Math.round(depth) + 1;
		double size = 1.4 + random.nextDouble() * 1.2;
		int s = (int) Math.ceil(size);
		for (int dx = -s; dx <= s; dx++) {
			for (int dy = -s; dy <= s; dy++) {
				for (int dz = -s; dz <= s; dz++) {
					if (dx * dx + dy * dy * 1.4 + dz * dz > size * size) {
						continue;
					}
					int roll = random.nextInt(12);
					BlockState rock = roll < 7 ? ModBlocks.METEORITE.defaultBlockState() : roll < 9 ? Blocks.RAW_IRON_BLOCK.defaultBlockState()
							: roll < 11 ? Blocks.IRON_ORE.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
					level.setBlock(pos.set(ox + dx, heart + dy, oz + dz), rock, 2);
				}
			}
		}
		// Now and then, whatever the star carried is still there, sealed under it.
		if (random.nextInt(4) == 0) {
			BlockPos chest = new BlockPos(ox, heart - s - 1, oz);
			level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
			RandomizableContainer.setBlockEntityLootTable(level, random, chest, METEOR_LOOT);
		}
		return true;
	}

	// --- glowcaps -------------------------------------------------------------------------------

	/**
	 * A giant glowcap standing on the ground at its foot (under the forest's canopy, or on the floor of a
	 * grotto): a pale stem under a wide dome of glowing blue cap, with little glowcaps round it.
	 */
	private boolean giantGlowcap(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos foot) {
		BlockState below = level.getBlockState(foot.below());
		if (!below.isFaceSturdy(level, foot.below(), Direction.UP) || !level.getFluidState(foot).isEmpty()) {
			return false;
		}
		int capRadius = 3 + random.nextInt(3);
		if (!window.containsRadius(foot.getX(), foot.getZ(), capRadius + 2)) {
			return false;
		}
		int wanted = 5 + random.nextInt(7);
		int room = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (room < wanted + 3 && open(level.getBlockState(pos.set(foot.getX(), foot.getY() + room, foot.getZ())))) {
			room++;
		}
		int height = Math.min(wanted, room - 3);
		if (height < 3) {
			return false;
		}
		double angle = random.nextDouble() * Math.PI * 2;
		double lean = random.nextDouble() * 0.12;
		int topX = foot.getX();
		int topZ = foot.getZ();
		BlockState stem = Blocks.MUSHROOM_STEM.defaultBlockState();
		for (int i = 0; i < height; i++) {
			topX = foot.getX() + (int) Math.round(Math.cos(angle) * lean * i * i / height);
			topZ = foot.getZ() + (int) Math.round(Math.sin(angle) * lean * i * i / height);
			if (open(level.getBlockState(pos.set(topX, foot.getY() + i, topZ)))) {
				level.setBlock(pos, stem, 2);
			}
		}
		int capY = foot.getY() + height;
		BlockState cap = ModBlocks.GLOWCAP_BLOCK.defaultBlockState();
		for (int dx = -capRadius; dx <= capRadius; dx++) {
			for (int dz = -capRadius; dz <= capRadius; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz) / capRadius;
				for (int dy = -1; dy <= 2; dy++) {
					boolean inCap = switch (dy) {
						case -1 -> d > 0.78 && d <= 1.0 && random.nextInt(3) != 0;
						case 0 -> d <= 1.0;
						case 1 -> d <= 0.78;
						default -> d <= 0.42;
					};
					if (inCap && open(level.getBlockState(pos.set(topX + dx, capY + dy, topZ + dz)))) {
						level.setBlock(pos, cap, 2);
					}
				}
			}
		}
		// Little glowcaps round the foot.
		BlockState small = ModBlocks.GLOWCAP.defaultBlockState();
		for (int k = 0; k < 6; k++) {
			int x = foot.getX() + random.nextInt(7) - 3;
			int z = foot.getZ() + random.nextInt(7) - 3;
			for (int dy = 2; dy >= -2; dy--) {
				pos.set(x, foot.getY() + dy, z);
				if (level.getBlockState(pos).isAir() && solid(level, pos.below())) {
					level.setBlock(pos, small, 2);
					break;
				}
			}
		}
		return true;
	}

	// --- crystal caverns ------------------------------------------------------------------------

	private static Block[] crystalPair(RandomSource random) {
		int roll = random.nextInt(10);
		if (roll < 4) {
			return new Block[] {ModBlocks.AZURE_CRYSTAL, ModBlocks.AZURE_CRYSTAL_CLUSTER};
		}
		if (roll < 7) {
			return new Block[] {ModBlocks.CITRINE_CRYSTAL, ModBlocks.CITRINE_CRYSTAL_CLUSTER};
		}
		return new Block[] {Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_CLUSTER};
	}

	/** A spire of crystal rising from a cave floor, or hanging from its roof, tipped with a cluster. */
	private boolean crystalSpire(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin).isAir()) {
			return false;
		}
		Direction grow;
		if (solid(level, origin.below())) {
			grow = Direction.UP;
		} else if (level.getBlockState(origin.above()).isFaceSturdy(level, origin.above(), Direction.DOWN)) {
			grow = Direction.DOWN;
		} else {
			return false;
		}
		if (!window.containsRadius(origin.getX(), origin.getZ(), 4)) {
			return false;
		}
		Block[] pair = crystalPair(random);
		BlockState crystal = pair[0].defaultBlockState();
		int height = 3 + random.nextInt(7);
		double radius = 0.7 + random.nextDouble() * 1.5;
		double tiltX = (random.nextDouble() - 0.5) * 0.4;
		double tiltZ = (random.nextDouble() - 0.5) * 0.4;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int tipX = origin.getX();
		int tipY = origin.getY();
		int tipZ = origin.getZ();
		int built = 0;
		for (int i = 0; i < height; i++) {
			double r = radius * (1.0 - i / (double) height);
			int y = origin.getY() + grow.getStepY() * i;
			double cx = origin.getX() + 0.5 + tiltX * i;
			double cz = origin.getZ() + 0.5 + tiltZ * i;
			if (!level.getBlockState(pos.set(Mth.floor(cx), y, Mth.floor(cz))).isAir()) {
				break;
			}
			int ir = (int) Math.ceil(r);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double ox = Mth.floor(cx) + dx + 0.5 - cx;
					double oz = Mth.floor(cz) + dz + 0.5 - cz;
					if (ox * ox + oz * oz > r * r && !(dx == 0 && dz == 0)) {
						continue;
					}
					if (level.getBlockState(pos.set(Mth.floor(cx) + dx, y, Mth.floor(cz) + dz)).isAir()) {
						level.setBlock(pos, crystal, 2);
					}
				}
			}
			tipX = Mth.floor(cx);
			tipY = y;
			tipZ = Mth.floor(cz);
			built++;
		}
		if (built == 0) {
			return false;
		}
		pos.set(tipX, tipY + grow.getStepY(), tipZ);
		if (level.getBlockState(pos).isAir()) {
			level.setBlock(pos, cluster(pair[1], grow, false), 2);
		}
		return true;
	}

	/** Crystal clusters crusting the walls, floor and roof of a cave around a spot, a few of their rocks turned to crystal. */
	private boolean crystalGrowth(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin).isAir() || !window.containsRadius(origin.getX(), origin.getZ(), 6)) {
			return false;
		}
		Block[] pair = crystalPair(random);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos support = new BlockPos.MutableBlockPos();
		int grown = 0;
		for (int k = 0; k < 24; k++) {
			pos.set(origin.getX() + random.nextInt(9) - 4, origin.getY() + random.nextInt(7) - 3, origin.getZ() + random.nextInt(9) - 4);
			if (!level.getBlockState(pos).isAir()) {
				continue;
			}
			for (Direction facing : Direction.values()) {
				support.setWithOffset(pos, facing.getOpposite());
				BlockState rock = level.getBlockState(support);
				if (!rock.isFaceSturdy(level, support, facing) || !rock.is(BlockTags.BASE_STONE_OVERWORLD)) {
					continue;
				}
				level.setBlock(pos, cluster(pair[1], facing, false), 2);
				if (random.nextInt(4) == 0) {
					level.setBlock(support, pair[0].defaultBlockState(), 2);
				}
				grown++;
				break;
			}
		}
		return grown > 0;
	}

	// --- the moors ------------------------------------------------------------------------------

	/** An old dry-stone wall wandering over the moor, mossy and gapped, following the lie of the land. */
	private boolean stoneWall(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		double x = origin.getX() + 0.5;
		double z = origin.getZ() + 0.5;
		double angle = random.nextDouble() * Math.PI * 2;
		int length = 12 + random.nextInt(20);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int lastX = Mth.floor(x);
		int lastZ = Mth.floor(z);
		int laid = 0;
		for (int s = 0; s < length; s++) {
			angle += (random.nextDouble() - 0.5) * 0.35;
			x += Math.cos(angle);
			z += Math.sin(angle);
			int bx = Mth.floor(x);
			int bz = Mth.floor(z);
			if (!window.contains(bx, bz)) {
				break;
			}
			if (random.nextInt(14) == 0) {
				// A gap where a gate once was, or the stones have tumbled.
				lastX = bx;
				lastZ = bz;
				continue;
			}
			laid += wallColumn(level, random, pos, bx, bz);
			if (bx != lastX && bz != lastZ) {
				// Close the corner so the wall never goes diagonal.
				laid += wallColumn(level, random, pos, lastX, bz);
			}
			lastX = bx;
			lastZ = bz;
		}
		return laid >= 6;
	}

	private static int wallColumn(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int x, int z) {
		int ground = WorldGenUtil.groundY(level, x, z);
		if (!level.getFluidState(pos.set(x, ground + 1, z)).isEmpty() || !WorldGenUtil.isTerrain(level.getBlockState(pos.set(x, ground, z)))) {
			return 0;
		}
		int height = random.nextInt(4) == 0 ? 2 : 1;
		int laid = 0;
		for (int y = ground + 1; y <= ground + height; y++) {
			BlockState at = level.getBlockState(pos.set(x, y, z));
			if (!open(at) || !at.getFluidState().isEmpty()) {
				break;
			}
			int roll = random.nextInt(10);
			put(level, pos, roll < 4 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : roll < 8 ? Blocks.COBBLESTONE.defaultBlockState()
					: Blocks.STONE.defaultBlockState());
			laid++;
		}
		return laid;
	}

	public enum Style implements StringRepresentable {
		LAVENDER_FIELD("lavender_field"),
		VOLCANO("volcano"),
		STEAM_VENTS("steam_vents"),
		HOT_SPRING("hot_spring"),
		BRINE_POOL("brine_pool"),
		PETRIFIED_TREE("petrified_tree"),
		METEOR_CRATER("meteor_crater"),
		GIANT_GLOWCAP("giant_glowcap"),
		GROTTO_GLOWCAP("grotto_glowcap"),
		CRYSTAL_SPIRE("crystal_spire"),
		CRYSTAL_GROWTH("crystal_growth"),
		STONE_WALL("stone_wall");

		public static final Codec<Style> CODEC = StringRepresentable.fromEnum(Style::values);
		private final String name;

		Style(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}
}
