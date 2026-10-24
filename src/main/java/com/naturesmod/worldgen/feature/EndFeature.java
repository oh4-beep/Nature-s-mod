package com.naturesmod.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.naturesmod.block.ModBlocks;
import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.SelfPlacing;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * The life and the landmarks of the new End: chorus trees hung with glowing bulbs, spikes of void
 * crystal, the craters of fallen stars, obsidian spires and shards floating over them, still black
 * pools, springy bounce blooms, updraft vents, void vines dangling from the undersides of the
 * lands, and little gardens of ender grass and glowing blooms.
 */
public record EndFeature(Style style) implements SelfPlacing {
	public static final MapCodec<EndFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Style.CODEC.fieldOf("style").forGetter(EndFeature::style)
	).apply(instance, EndFeature::new));

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		WorldGenUtil.Window window = WorldGenUtil.Window.around(origin, 1);
		// The structures of the End are built after these features: keep out of their way.
		if (style != Style.CRYSTAL_CLUSTERS && !clearOfStructures(level, origin, 8)) {
			return false;
		}
		boolean placed = switch (style) {
			case CHORUS_TREE -> chorusTree(level, window, random, origin);
			case CRYSTAL_SPIKES -> crystalSpikes(level, window, random, origin);
			case METEOR -> meteor(level, window, random, origin);
			case SPIRE -> spire(level, window, random, origin);
			case SHARDS -> shards(level, window, random, origin);
			case VOID_POOL -> voidPool(level, window, random, origin);
			case BOUNCE_BLOOMS -> bounceBlooms(level, window, random, origin);
			case UPDRAFT_VENTS -> updraftVents(level, window, random, origin);
			case HANGING_VINES -> hangingVines(level, window, random, origin);
			case GARDEN -> garden(level, window, random, origin);
			case CRYSTAL_CLUSTERS -> crystalClusters(level, window, random, origin);
		};
		if (placed) {
			FeatureStats.placed("end_" + style.getSerializedName(), origin);
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

	private static boolean solid(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced();
	}

	/** Open air, or a little plant that may be cleared. */
	private static boolean open(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
	}

	/** Ground a plant or a building may stand on. */
	private static boolean ground(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isFaceSturdy(level, pos, Direction.UP);
	}

	/** Y of the first open block above the ground in a column, searched around a height. */
	private static int surface(WorldGenLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y + 6, z);
		for (int i = 0; i < 16 && pos.getY() > level.getMinY() + 1; i++) {
			if (open(level, pos) && solid(level, pos.below())) {
				return pos.getY();
			}
			pos.move(Direction.DOWN);
		}
		return Integer.MIN_VALUE;
	}

	private static BlockState pillar(Block block, Direction.Axis axis) {
		return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	private static void setIfOpen(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (open(level, pos)) {
			level.setBlock(pos, state, 2);
		}
	}

	// --- chorus trees --------------------------------------------------------------------

	/** A tall, crooked chorus tree: a trunk that splits into limbs, each ending in glowing bulbs. */
	private boolean chorusTree(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 10) || !ground(level, origin.below()) || !open(level, origin)) {
			return false;
		}
		int height = 8 + random.nextInt(10);
		double x = origin.getX() + 0.5;
		double z = origin.getZ() + 0.5;
		double leanX = (random.nextDouble() - 0.5) * 0.25;
		double leanZ = (random.nextDouble() - 0.5) * 0.25;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState trunk = pillar(ModBlocks.CHORUS_LOG, Direction.Axis.Y);
		for (int i = 0; i < height; i++) {
			pos.set(Mth.floor(x), origin.getY() + i, Mth.floor(z));
			if (!open(level, pos) && !level.getBlockState(pos).is(ModBlocks.CHORUS_LOG)) {
				height = i;
				break;
			}
			level.setBlock(pos, trunk, 2);
			if (i > height / 3) {
				x += leanX;
				z += leanZ;
			}
		}
		if (height < 5) {
			return true;
		}
		BlockPos top = new BlockPos(Mth.floor(x), origin.getY() + height - 1, Mth.floor(z));
		bulbs(level, random, top.above(), 1);
		int limbs = 2 + random.nextInt(3);
		for (int l = 0; l < limbs; l++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int length = 3 + random.nextInt(5);
			int start = height / 2 + random.nextInt(Math.max(1, height / 2));
			double bx = x;
			double by = origin.getY() + start;
			double bz = z;
			for (int s = 0; s < length; s++) {
				bx += Math.cos(angle);
				bz += Math.sin(angle);
				by += 0.6 + random.nextDouble() * 0.3;
				pos.set(Mth.floor(bx), Mth.floor(by), Mth.floor(bz));
				if (!window.contains(pos) || !open(level, pos) && !level.getBlockState(pos).is(ModBlocks.CHORUS_LOG)) {
					break;
				}
				Direction.Axis axis = Math.abs(Math.cos(angle)) > Math.abs(Math.sin(angle)) ? Direction.Axis.X : Direction.Axis.Z;
				level.setBlock(pos, pillar(ModBlocks.CHORUS_LOG, s % 2 == 0 ? axis : Direction.Axis.Y), 2);
				// Now and then a bulb hangs under the limb.
				if (random.nextInt(4) == 0) {
					setIfOpen(level, pos.below(), ModBlocks.CHORUS_BULB.defaultBlockState());
				}
			}
			bulbs(level, random, pos.immutable().above(), random.nextInt(2));
		}
		// Chorus flowers sprout around its roots.
		for (int i = 0; i < 3; i++) {
			BlockPos sprout = origin.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
			int y = surface(level, sprout.getX(), sprout.getY(), sprout.getZ());
			if (y != Integer.MIN_VALUE && window.contains(sprout) && level.getBlockState(new BlockPos(sprout.getX(), y - 1, sprout.getZ())).is(ModBlocks.END_MOSS)) {
				level.setBlock(new BlockPos(sprout.getX(), y, sprout.getZ()), ModBlocks.VOID_BLOOM.defaultBlockState(), 2);
			}
		}
		return true;
	}

	/** A cluster of glowing chorus bulbs: one block, or a little crown of them. */
	private static void bulbs(WorldGenLevel level, RandomSource random, BlockPos at, int size) {
		BlockState bulb = ModBlocks.CHORUS_BULB.defaultBlockState();
		setIfOpen(level, at, bulb);
		if (size > 0) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				if (random.nextInt(3) != 0) {
					setIfOpen(level, at.relative(direction), bulb);
				}
			}
			if (random.nextBoolean()) {
				setIfOpen(level, at.above(), bulb);
			}
		}
	}

	// --- void crystal -------------------------------------------------------------------

	/** A burst of great void crystal spikes leaning out from one root, ringed with clusters. */
	private boolean crystalSpikes(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 12) || !ground(level, origin.below())) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int spikes = 3 + random.nextInt(5);
		for (int s = 0; s < spikes; s++) {
			double length = (s == 0 ? 10 : 5) + random.nextInt(s == 0 ? 9 : 7);
			double radius = (s == 0 ? 1.9 : 1.1) + random.nextDouble() * 0.7;
			double dx = s == 0 ? (random.nextDouble() - 0.5) * 0.3 : (random.nextDouble() - 0.5) * 1.1;
			double dz = s == 0 ? (random.nextDouble() - 0.5) * 0.3 : (random.nextDouble() - 0.5) * 1.1;
			double norm = Math.sqrt(dx * dx + 1.0 + dz * dz);
			double ux = dx / norm;
			double uy = 1.0 / norm;
			double uz = dz / norm;
			double sx = origin.getX() + 0.5 + random.nextInt(5) - 2;
			double sz = origin.getZ() + 0.5 + random.nextInt(5) - 2;
			double sy = origin.getY() - 2;
			for (double t = 0; t <= length; t += 0.5) {
				double r = radius * (1.0 - t / length) + 0.3;
				double cx = sx + ux * t;
				double cy = sy + uy * t;
				double cz = sz + uz * t;
				int ir = (int) Math.ceil(r);
				for (int ox = -ir; ox <= ir; ox++) {
					for (int oy = -ir; oy <= ir; oy++) {
						for (int oz = -ir; oz <= ir; oz++) {
							if (ox * ox + oy * oy + oz * oz > r * r) {
								continue;
							}
							pos.set(Mth.floor(cx) + ox, Mth.floor(cy) + oy, Mth.floor(cz) + oz);
							if (window.contains(pos) && (open(level, pos) || level.getBlockState(pos).is(Blocks.END_STONE)
									|| level.getBlockState(pos).is(ModBlocks.END_MOSS))) {
								level.setBlock(pos, (random.nextInt(9) == 0 ? Blocks.CALCITE : ModBlocks.VOID_CRYSTAL).defaultBlockState(), 2);
							}
						}
					}
				}
			}
		}
		crystalClusters(level, window, random, origin);
		return true;
	}

	/** Void crystal clusters scattered over the ground. */
	private boolean crystalClusters(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int placed = 0;
		for (int i = 0; i < 10; i++) {
			int x = origin.getX() + random.nextInt(11) - 5;
			int z = origin.getZ() + random.nextInt(11) - 5;
			if (!window.contains(x, z)) {
				continue;
			}
			int y = surface(level, x, origin.getY(), z);
			if (y == Integer.MIN_VALUE) {
				continue;
			}
			BlockPos at = new BlockPos(x, y, z);
			if (ground(level, at.below())) {
				level.setBlock(at, ModBlocks.VOID_CRYSTAL_CLUSTER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), 2);
				placed++;
			}
		}
		return placed > 0;
	}

	// --- fallen stars --------------------------------------------------------------------

	/** The crater of a fallen star: a bowl blasted into the ground, the meteorite still lying in it. */
	private boolean meteor(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int radius = 5 + random.nextInt(5);
		if (!window.containsRadius(origin.getX(), origin.getZ(), radius + 3) || !ground(level, origin.below())) {
			return false;
		}
		int depth = 2 + radius / 2;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int floor = origin.getY() - depth;
		for (int dx = -radius - 2; dx <= radius + 2; dx++) {
			for (int dz = -radius - 2; dz <= radius + 2; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				int top = surface(level, x, origin.getY(), z);
				if (top == Integer.MIN_VALUE) {
					continue;
				}
				if (d <= radius) {
					// The bowl: deepest in the middle, scorched and littered with stardust.
					int bowl = origin.getY() - (int) Math.round(depth * (1.0 - (d / radius) * (d / radius)));
					for (int y = bowl; y < top + 3; y++) {
						pos.set(x, y, z);
						if (!level.getBlockState(pos).isAir()) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
						}
					}
					pos.set(x, bowl - 1, z);
					if (solid(level, pos)) {
						level.setBlock(pos, (random.nextInt(3) == 0 ? ModBlocks.METEORITE : random.nextBoolean() ? Blocks.BLACKSTONE
								: ModBlocks.STARDUST).defaultBlockState(), 2);
					}
				} else if (d <= radius + 2 && random.nextInt(3) != 0) {
					// The rim, thrown up around the bowl.
					pos.set(x, top, z);
					if (open(level, pos)) {
						level.setBlock(pos, (random.nextBoolean() ? ModBlocks.STARDUST : Blocks.END_STONE).defaultBlockState(), 2);
					}
				}
			}
		}
		// The star itself: a lump of meteorite veined with starmetal.
		double r = 1.6 + random.nextDouble() * 1.2;
		int ir = (int) Math.ceil(r);
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dy = -ir; dy <= ir; dy++) {
				for (int dz = -ir; dz <= ir; dz++) {
					if (dx * dx + dy * dy + dz * dz > r * r) {
						continue;
					}
					pos.set(origin.getX() + dx, floor + 1 + dy, origin.getZ() + dz);
					Block block = random.nextInt(4) == 0 ? ModBlocks.STARMETAL_ORE : ModBlocks.METEORITE;
					if (dx == 0 && dz == 0 && dy == 0) {
						block = ModBlocks.STARMETAL_BLOCK;
					}
					level.setBlock(pos, block.defaultBlockState(), 2);
				}
			}
		}
		return true;
	}

	// --- spires and shards -------------------------------------------------------------------

	/** A needle of obsidian streaked with crying obsidian; now and then a dormant crystal on top. */
	private boolean spire(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 7) || !ground(level, origin.below())) {
			return false;
		}
		int height = 18 + random.nextInt(28);
		double base = 2.4 + random.nextDouble() * 2.0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		double lean = (random.nextDouble() - 0.5) * 0.15;
		int topY = origin.getY();
		for (int dy = -3; dy < height; dy++) {
			double t = Math.max(0, dy) / (double) height;
			double r = base * (1.0 - t) + 0.45;
			int ir = (int) Math.ceil(r);
			int ox = (int) Math.round(lean * dy);
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					if (dx * dx + dz * dz > r * r) {
						continue;
					}
					pos.set(origin.getX() + dx + ox, origin.getY() + dy, origin.getZ() + dz);
					Block block = Math.floorMod(dy + (dx * 3 + dz) % 4, 9) == 0 ? Blocks.CRYING_OBSIDIAN
							: dy % 11 == 5 ? ModBlocks.VOIDSTONE : Blocks.OBSIDIAN;
					level.setBlock(pos, block.defaultBlockState(), 2);
					topY = Math.max(topY, pos.getY());
				}
			}
		}
		if (random.nextInt(4) == 0) {
			BlockPos crown = new BlockPos(origin.getX() + (int) Math.round(lean * height), topY + 1, origin.getZ());
			level.setBlock(crown, Blocks.BEDROCK.defaultBlockState(), 2);
			EndCrystal crystal = new EndCrystal(level.getLevel(), crown.getX() + 0.5, crown.getY() + 1, crown.getZ() + 0.5);
			crystal.setShowBottom(true);
			level.addFreshEntity(crystal);
		}
		return true;
	}

	/** Shards of obsidian and voidstone hanging in the air over the ground. */
	private boolean shards(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 12)) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int count = 2 + random.nextInt(4);
		for (int i = 0; i < count; i++) {
			int cx = origin.getX() + random.nextInt(17) - 8;
			int cz = origin.getZ() + random.nextInt(17) - 8;
			int cy = origin.getY() + 4 + random.nextInt(12);
			double r = 1.0 + random.nextDouble() * 1.6;
			int ir = (int) Math.ceil(r);
			Block block = random.nextInt(3) == 0 ? ModBlocks.VOIDSTONE : Blocks.OBSIDIAN;
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dy = -ir - 2; dy <= ir; dy++) {
					for (int dz = -ir; dz <= ir; dz++) {
						// Pointed at the bottom, like a shard torn out of the ground.
						double rr = dy < 0 ? r * (1.0 + dy / (double) (ir + 2)) : r;
						if (dx * dx + dz * dz + Math.max(0, dy) * Math.max(0, dy) > rr * rr) {
							continue;
						}
						pos.set(cx + dx, cy + dy, cz + dz);
						if (window.contains(pos) && open(level, pos)) {
							level.setBlock(pos, (random.nextInt(7) == 0 ? Blocks.CRYING_OBSIDIAN : block).defaultBlockState(), 2);
						}
					}
				}
			}
		}
		return true;
	}

	// --- pools ---------------------------------------------------------------------------

	/** A still, black pool sunk into the moss, lined with voidstone, a few lights deep in it. */
	private boolean voidPool(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int radius = 3 + random.nextInt(4);
		if (!window.containsRadius(origin.getX(), origin.getZ(), radius + 2) || !ground(level, origin.below())) {
			return false;
		}
		int water = origin.getY() - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// The ground all round must be at least as high as the water, or the pool would spill.
		for (int dx = -radius - 1; dx <= radius + 1; dx++) {
			for (int dz = -radius - 1; dz <= radius + 1; dz++) {
				if (dx * dx + dz * dz > (radius + 1) * (radius + 1)) {
					continue;
				}
				for (int dy = 0; dy <= 3; dy++) {
					pos.set(origin.getX() + dx, water - dy, origin.getZ() + dz);
					if (level.getBlockState(pos).isAir()) {
						return false;
					}
				}
			}
		}
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > radius) {
					continue;
				}
				int deep = 1 + (int) Math.round((1.0 - d / radius) * 2.5);
				for (int dy = 0; dy < deep; dy++) {
					pos.set(origin.getX() + dx, water - dy, origin.getZ() + dz);
					level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
				}
				pos.set(origin.getX() + dx, water - deep, origin.getZ() + dz);
				level.setBlock(pos, ModBlocks.VOIDSTONE.defaultBlockState(), 2);
				pos.set(origin.getX() + dx, water + 1, origin.getZ() + dz);
				if (!level.getBlockState(pos).isAir() && level.getBlockState(pos).canBeReplaced()) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
				if (deep >= 2 && random.nextInt(9) == 0) {
					pos.set(origin.getX() + dx, water - deep + 1, origin.getZ() + dz);
					level.setBlock(pos, Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 1 + random.nextInt(4)), 2);
				} else if (random.nextInt(10) == 0) {
					pos.set(origin.getX() + dx, water + 1, origin.getZ() + dz);
					level.setBlock(pos, Blocks.LILY_PAD.defaultBlockState(), 2);
				}
			}
		}
		// Glowing blooms on the banks.
		for (int i = 0; i < 6; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int x = origin.getX() + (int) Math.round(Math.cos(angle) * (radius + 1));
			int z = origin.getZ() + (int) Math.round(Math.sin(angle) * (radius + 1));
			int y = surface(level, x, origin.getY(), z);
			if (y != Integer.MIN_VALUE) {
				BlockPos bank = new BlockPos(x, y, z);
				if (ground(level, bank.below()) && level.getBlockState(bank.below()).getFluidState().isEmpty()) {
					level.setBlock(bank, ModBlocks.VOID_BLOOM.defaultBlockState(), 2);
				}
			}
		}
		return true;
	}

	// --- bounce blooms and updraft vents ------------------------------------------------------

	/** A little stand of bounce blooms: springy glowing caps on pale stems. */
	private boolean bounceBlooms(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 10)) {
			return false;
		}
		int placed = 0;
		int blooms = 1 + random.nextInt(4);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int b = 0; b < blooms; b++) {
			int x = origin.getX() + (b == 0 ? 0 : random.nextInt(11) - 5);
			int z = origin.getZ() + (b == 0 ? 0 : random.nextInt(11) - 5);
			int y = surface(level, x, origin.getY(), z);
			if (y == Integer.MIN_VALUE || !ground(level, new BlockPos(x, y - 1, z))) {
				continue;
			}
			int stem = random.nextInt(5);
			int cap = stem == 0 ? 1 : 1 + random.nextInt(2);
			for (int i = 0; i < stem; i++) {
				pos.set(x, y + i, z);
				if (!open(level, pos)) {
					break;
				}
				level.setBlock(pos, pillar(ModBlocks.BOUNCE_STEM, Direction.Axis.Y), 2);
			}
			for (int dx = -cap; dx <= cap; dx++) {
				for (int dz = -cap; dz <= cap; dz++) {
					if (Math.abs(dx) == cap && Math.abs(dz) == cap && cap > 1) {
						continue;
					}
					pos.set(x + dx, y + stem, z + dz);
					if (window.contains(pos)) {
						setIfOpen(level, pos.immutable(), ModBlocks.BOUNCE_BLOOM.defaultBlockState());
					}
				}
			}
			placed++;
		}
		return placed > 0;
	}

	/** One to three updraft vents set into the ground, ringed with polished voidstone. */
	private boolean updraftVents(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 6)) {
			return false;
		}
		int vents = 1 + random.nextInt(3);
		int placed = 0;
		for (int v = 0; v < vents; v++) {
			int x = origin.getX() + (v == 0 ? 0 : random.nextInt(7) - 3);
			int z = origin.getZ() + (v == 0 ? 0 : random.nextInt(7) - 3);
			int y = surface(level, x, origin.getY(), z);
			if (y == Integer.MIN_VALUE) {
				continue;
			}
			BlockPos vent = new BlockPos(x, y - 1, z);
			if (!solid(level, vent)) {
				continue;
			}
			level.setBlock(vent, ModBlocks.UPDRAFT_VENT.defaultBlockState(), 2);
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos ring = vent.relative(direction);
				if (solid(level, ring) && !level.getBlockState(ring).is(ModBlocks.UPDRAFT_VENT)) {
					level.setBlock(ring, ModBlocks.POLISHED_VOIDSTONE.defaultBlockState(), 2);
				}
			}
			// Clear anything growing over it.
			for (int i = 1; i <= 3; i++) {
				BlockPos above = vent.above(i);
				if (!level.getBlockState(above).isAir() && level.getBlockState(above).canBeReplaced()) {
					level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
				}
			}
			placed++;
		}
		return placed > 0;
	}

	// --- vines and gardens -------------------------------------------------------------------

	/** Strands of glowing void vine hanging from the underside of the land overhead. */
	private boolean hangingVines(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		if (!window.containsRadius(origin.getX(), origin.getZ(), 4) || !level.getBlockState(origin).isAir()) {
			return false;
		}
		BlockPos.MutableBlockPos pos = origin.mutable();
		int ceiling = Integer.MIN_VALUE;
		for (int i = 0; i < 24 && pos.getY() < level.getMaxY() - 2; i++) {
			pos.move(Direction.UP);
			if (solid(level, pos)) {
				ceiling = pos.getY();
				break;
			}
		}
		if (ceiling == Integer.MIN_VALUE) {
			return false;
		}
		int placed = 0;
		BlockState vine = ModBlocks.VOID_VINES.defaultBlockState();
		for (int s = 0; s < 6; s++) {
			int x = origin.getX() + random.nextInt(7) - 3;
			int z = origin.getZ() + random.nextInt(7) - 3;
			pos.set(x, ceiling + 2, z);
			int found = Integer.MIN_VALUE;
			for (int i = 0; i < 6; i++) {
				pos.move(Direction.DOWN);
				if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN)) {
					found = pos.getY();
					break;
				}
			}
			if (found == Integer.MIN_VALUE) {
				continue;
			}
			int length = 2 + random.nextInt(9);
			for (int i = 0; i < length; i++) {
				pos.set(x, found - i, z);
				if (!level.getBlockState(pos).isAir()) {
					break;
				}
				level.setBlock(pos, vine, 2);
				placed++;
			}
		}
		return placed > 0;
	}

	/** Ender grass and a few void blooms on the moss. */
	private boolean garden(WorldGenLevel level, WorldGenUtil.Window window, RandomSource random, BlockPos origin) {
		int placed = 0;
		for (int i = 0; i < 24; i++) {
			int x = origin.getX() + random.nextInt(9) - 4;
			int z = origin.getZ() + random.nextInt(9) - 4;
			if (!window.contains(x, z)) {
				continue;
			}
			int y = surface(level, x, origin.getY(), z);
			if (y == Integer.MIN_VALUE) {
				continue;
			}
			BlockPos at = new BlockPos(x, y, z);
			BlockState below = level.getBlockState(at.below());
			if (!below.is(ModBlocks.END_MOSS) && !below.is(Blocks.END_STONE) && !below.is(ModBlocks.STARDUST)) {
				continue;
			}
			if (!level.getBlockState(at).isAir()) {
				continue;
			}
			level.setBlock(at, (random.nextInt(6) == 0 ? ModBlocks.VOID_BLOOM : ModBlocks.ENDER_GRASS).defaultBlockState(), 2);
			placed++;
		}
		return placed > 0;
	}

	public enum Style implements StringRepresentable {
		CHORUS_TREE("chorus_tree"),
		CRYSTAL_SPIKES("crystal_spikes"),
		METEOR("meteor"),
		SPIRE("spire"),
		SHARDS("shards"),
		VOID_POOL("void_pool"),
		BOUNCE_BLOOMS("bounce_blooms"),
		UPDRAFT_VENTS("updraft_vents"),
		HANGING_VINES("hanging_vines"),
		GARDEN("garden"),
		CRYSTAL_CLUSTERS("crystal_clusters");

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
