package com.naturesmod.worldgen.deep;

import com.naturesmod.NaturesMod;
import com.naturesmod.creature.ModCreatures;
import com.naturesmod.item.AbyssWeapon;
import com.naturesmod.item.ModItems;
import com.naturesmod.worldgen.WorldGenUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Places the blocks of one treasure of the deep inside one chunk. Structures are drawn in local
 * coordinates: x to the right, y up from the sea floor (y = 0 is the ground the structure stands
 * on), z forward; the builder turns them to the structure's rotation. Every chunk the structure
 * touches draws all of it and keeps only its own blocks, so choices come either from
 * {@link #rand} (a hash of the position) or from {@link #layout}, a random sequence that every
 * chunk replays from the start.
 */
public final class DeepBuilder {
	static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final Block[] HEADS = {Blocks.SKELETON_SKULL, Blocks.WITHER_SKELETON_SKULL, Blocks.ZOMBIE_HEAD, Blocks.CREEPER_HEAD,
			Blocks.PIGLIN_HEAD, Blocks.PLAYER_HEAD, Blocks.SKELETON_SKULL, Blocks.WITHER_SKELETON_SKULL};

	private final WorldGenLevel level;
	private final BoundingBox box;
	private final int cx;
	private final int cy;
	private final int cz;
	private final Rotation rotation;
	private final long seed;
	/** Where the loot tables of the chests are: the treasures of the floor, or the poorer highland ruins. */
	private final String lootFolder;
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	/** For layout choices: every chunk replays the same sequence. */
	final RandomSource layout;

	DeepBuilder(WorldGenLevel level, BoundingBox box, int cx, int cy, int cz, Rotation rotation, long seed, String lootFolder) {
		this.level = level;
		this.lootFolder = lootFolder;
		this.box = box;
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		this.rotation = rotation;
		this.seed = seed;
		this.layout = RandomSource.create(seed);
	}

	static BlockState s(Block block) {
		return block.defaultBlockState();
	}

	/** A vanilla block by id: the dyed and copper families are reached this way rather than by field. */
	static Block vanilla(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
	}

	/** Chooses the block for each position of a shape. */
	@FunctionalInterface
	interface Paint {
		BlockState at(int x, int y, int z);
	}

	static Paint p(BlockState state) {
		return (x, y, z) -> state;
	}

	/** Old stonework: mostly the first block, now and then one of the others. */
	Paint worn(BlockState main, BlockState... wear) {
		return (x, y, z) -> worn(x, y, z, main, wear);
	}

	/** An even mixture of blocks. */
	Paint mix(BlockState... options) {
		return (x, y, z) -> options[(int) (rand(x, y, z, 407) * options.length)];
	}

	/** A paint with holes in it, where the ruin has crumbled away. */
	Paint ruin(Paint paint, double crumble) {
		return (x, y, z) -> crumbled(x, y, z, crumble) ? WATER : paint.at(x, y, z);
	}

	/** Bands of one block every {@code every} courses of another. */
	static Paint banded(BlockState main, BlockState band, int every) {
		return (x, y, z) -> Math.floorMod(y, every) == 0 ? band : main;
	}

	// --- coordinates ---------------------------------------------------------------------------

	private int worldX(int x, int z) {
		return switch (rotation) {
			case NONE -> cx + x;
			case CLOCKWISE_90 -> cx - z;
			case CLOCKWISE_180 -> cx - x;
			case COUNTERCLOCKWISE_90 -> cx + z;
		};
	}

	private int worldZ(int x, int z) {
		return switch (rotation) {
			case NONE -> cz + z;
			case CLOCKWISE_90 -> cz + x;
			case CLOCKWISE_180 -> cz - z;
			case COUNTERCLOCKWISE_90 -> cz - x;
		};
	}

	BlockPos at(int x, int y, int z) {
		return new BlockPos(worldX(x, z), cy + y, worldZ(x, z));
	}

	/** Whether a column of the structure lies in the chunk being built. */
	boolean column(int x, int z) {
		int wx = worldX(x, z);
		int wz = worldZ(x, z);
		return wx >= box.minX() && wx <= box.maxX() && wz >= box.minZ() && wz <= box.maxZ();
	}

	BlockState get(int x, int y, int z) {
		pos.set(worldX(x, z), cy + y, worldZ(x, z));
		if (!box.isInside(pos)) {
			return WATER;
		}
		return level.getBlockState(pos);
	}

	/** Solid ground: anything but water, air and the plants that grow in the water. */
	boolean solid(int x, int y, int z) {
		BlockState state = get(x, y, z);
		return !state.isAir() && state.getFluidState().isEmpty();
	}

	// --- placing blocks ----------------------------------------------------------------------

	/** Sets a block, turned to the structure's rotation and waterlogged when it replaces water. */
	void set(int x, int y, int z, BlockState state) {
		int wy = cy + y;
		if (wy <= level.getMinY() || wy >= level.getMaxY() - 1) {
			return;
		}
		pos.set(worldX(x, z), wy, worldZ(x, z));
		if (!box.isInside(pos)) {
			return;
		}
		state = state.rotate(rotation);
		if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
			state = state.setValue(BlockStateProperties.WATERLOGGED, WorldGenUtil.isWater(level.getBlockState(pos)));
		}
		level.setBlock(pos, state, 2);
	}

	void set(int x, int y, int z, Block block) {
		set(x, y, z, block.defaultBlockState());
	}

	void set(int x, int y, int z, Paint paint) {
		set(x, y, z, paint.at(x, y, z));
	}

	void fill(int x0, int y0, int z0, int x1, int y1, int z1, Paint paint) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				if (!column(x, z)) {
					continue;
				}
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
					set(x, y, z, paint.at(x, y, z));
				}
			}
		}
	}

	void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		fill(x0, y0, z0, x1, y1, z1, p(state));
	}

	void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		fill(x0, y0, z0, x1, y1, z1, p(block.defaultBlockState()));
	}

	/** Clears a box to open water. */
	void water(int x0, int y0, int z0, int x1, int y1, int z1) {
		fill(x0, y0, z0, x1, y1, z1, WATER);
	}

	/** A closed room: walls, floor and roof with water inside. */
	void shell(int x0, int y0, int z0, int x1, int y1, int z1, Paint wall) {
		fill(x0, y0, z0, x1, y1, z1, wall);
		water(Math.min(x0, x1) + 1, Math.min(y0, y1) + 1, Math.min(z0, z1) + 1, Math.max(x0, x1) - 1, Math.max(y0, y1) - 1, Math.max(z0, z1) - 1);
	}

	void shell(int x0, int y0, int z0, int x1, int y1, int z1, BlockState wall) {
		shell(x0, y0, z0, x1, y1, z1, p(wall));
	}

	void walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockState wall) {
		walls(x0, y0, z0, x1, y1, z1, p(wall));
	}

	/** Four walls round a box, without floor or roof. */
	void walls(int x0, int y0, int z0, int x1, int y1, int z1, Paint wall) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		for (int x = ax; x <= bx; x++) {
			for (int z = az; z <= bz; z++) {
				if ((x == ax || x == bx || z == az || z == bz) && column(x, z)) {
					for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
						set(x, y, z, wall.at(x, y, z));
					}
				}
			}
		}
	}

	/** Fills down from y until it meets solid ground, so nothing floats over a dip. */
	void foundation(int x, int y, int z, BlockState state) {
		for (int i = 0; i < 40; i++) {
			if (i > 0 && solid(x, y - i, z)) {
				return;
			}
			set(x, y - i, z, state);
		}
	}

	/**
	 * Levels a site: the ground at y = 0 over a rectangle, built up from the solid ground beneath
	 * and cleared to open water for {@code clear} blocks above.
	 */
	void pad(int x0, int z0, int x1, int z1, BlockState ground, BlockState base, int clear) {
		pad(x0, z0, x1, z1, p(ground), base, clear);
	}

	void pad(int x0, int z0, int x1, int z1, Paint ground, BlockState base, int clear) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				if (column(x, z)) {
					padColumn(x, z, ground, base, clear);
				}
			}
		}
	}

	void padRound(int x0, int z0, double radius, BlockState ground, BlockState base, int clear) {
		padRound(x0, z0, radius, p(ground), base, clear);
	}

	/** Levels a round site; see {@link #pad}. */
	void padRound(int x0, int z0, double radius, Paint ground, BlockState base, int clear) {
		int r = (int) Math.ceil(radius);
		for (int x = x0 - r; x <= x0 + r; x++) {
			for (int z = z0 - r; z <= z0 + r; z++) {
				if ((x - x0) * (x - x0) + (z - z0) * (z - z0) <= radius * radius && column(x, z)) {
					padColumn(x, z, ground, base, clear);
				}
			}
		}
	}

	private void padColumn(int x, int z, Paint ground, BlockState base, int clear) {
		foundation(x, -1, z, base);
		set(x, 0, z, ground.at(x, 0, z));
		for (int y = 1; y <= clear; y++) {
			set(x, y, z, WATER);
		}
	}

	/** The sea floor near a column: the top of the solid ground within a few blocks of y = 0. */
	int ground(int x, int z) {
		for (int y = 8; y >= -12; y--) {
			if (solid(x, y, z)) {
				return y;
			}
		}
		return -12;
	}

	void pillar(int x, int z, int y0, int y1, BlockState state) {
		pillar(x, z, y0, y1, p(state));
	}

	void pillar(int x, int z, int y0, int y1, Paint paint) {
		if (!column(x, z)) {
			return;
		}
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			set(x, y, z, paint.at(x, y, z));
		}
	}

	void disc(int x0, int y, int z0, double radius, BlockState state) {
		disc(x0, y, z0, radius, p(state));
	}

	/** A flat disc of blocks. */
	void disc(int x0, int y, int z0, double radius, Paint paint) {
		int r = (int) Math.ceil(radius);
		for (int x = x0 - r; x <= x0 + r; x++) {
			for (int z = z0 - r; z <= z0 + r; z++) {
				if ((x - x0) * (x - x0) + (z - z0) * (z - z0) <= radius * radius && column(x, z)) {
					set(x, y, z, paint.at(x, y, z));
				}
			}
		}
	}

	void cylinder(int x0, int z0, double radius, int y0, int y1, BlockState state, boolean hollow) {
		cylinder(x0, z0, radius, y0, y1, p(state), hollow);
	}

	/** An upright cylinder, solid or just its wall. */
	void cylinder(int x0, int z0, double radius, int y0, int y1, Paint paint, boolean hollow) {
		int r = (int) Math.ceil(radius);
		for (int x = x0 - r; x <= x0 + r; x++) {
			for (int z = z0 - r; z <= z0 + r; z++) {
				double d = Math.sqrt((x - x0) * (x - x0) + (z - z0) * (z - z0));
				if (d > radius || hollow && d <= radius - 1.0 || !column(x, z)) {
					continue;
				}
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
					set(x, y, z, paint.at(x, y, z));
				}
			}
		}
	}

	void sphere(int x0, int y0, int z0, double radius, BlockState state, boolean hollow, int fromDy) {
		ellipsoid(x0, y0, z0, radius, radius, radius, p(state), hollow, fromDy);
	}

	/** A ball, solid or a shell one block thick, cut off below {@code fromDy}. */
	void sphere(int x0, int y0, int z0, double radius, Paint paint, boolean hollow, int fromDy) {
		ellipsoid(x0, y0, z0, radius, radius, radius, paint, hollow, fromDy);
	}

	/** An ellipsoid with the given radii, solid or a shell, cut off below {@code fromDy}. */
	void ellipsoid(int x0, int y0, int z0, double rx, double ry, double rz, Paint paint, boolean hollow, int fromDy) {
		int ix = (int) Math.ceil(rx);
		int iy = (int) Math.ceil(ry);
		int iz = (int) Math.ceil(rz);
		for (int x = x0 - ix; x <= x0 + ix; x++) {
			for (int z = z0 - iz; z <= z0 + iz; z++) {
				if (!column(x, z)) {
					continue;
				}
				for (int dy = Math.max(-iy, fromDy); dy <= iy; dy++) {
					double fx = (x - x0) / rx;
					double fy = dy / ry;
					double fz = (z - z0) / rz;
					double d = fx * fx + fy * fy + fz * fz;
					if (d > 1.0) {
						continue;
					}
					if (hollow) {
						// Inside the shell when one block further out along every axis is still inside.
						double gx = (Math.abs(x - x0) + 1.0) / rx;
						double gy = (Math.abs(dy) + 1.0) / ry;
						double gz = (Math.abs(z - z0) + 1.0) / rz;
						if (gx * gx + fy * fy + fz * fz <= 1.0 && fx * fx + gy * gy + fz * fz <= 1.0 && fx * fx + fy * fy + gz * gz <= 1.0) {
							continue;
						}
					}
					set(x, y0 + dy, z, paint.at(x, y0 + dy, z));
				}
			}
		}
	}

	void line(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		line(x0, y0, z0, x1, y1, z1, p(state));
	}

	/** A straight line of blocks from one point to another. */
	void line(int x0, int y0, int z0, int x1, int y1, int z1, Paint paint) {
		int steps = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
		for (int i = 0; i <= steps; i++) {
			double t = steps == 0 ? 0.0 : i / (double) steps;
			int x = (int) Math.round(x0 + (x1 - x0) * t);
			int y = (int) Math.round(y0 + (y1 - y0) * t);
			int z = (int) Math.round(z0 + (z1 - z0) * t);
			set(x, y, z, paint.at(x, y, z));
		}
	}

	// --- block states --------------------------------------------------------------------------

	static BlockState stairs(Block block, Direction facing, boolean upsideDown) {
		return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				.setValue(BlockStateProperties.HALF, upsideDown ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slab(Block block, boolean top) {
		return block.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState facing(Block block, Direction facing) {
		BlockState state = block.defaultBlockState();
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && facing.getAxis().isHorizontal()) {
			return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		}
		if (state.hasProperty(BlockStateProperties.FACING)) {
			return state.setValue(BlockStateProperties.FACING, facing);
		}
		return state;
	}

	static BlockState axis(Block block, Direction.Axis axis) {
		return block.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	/** Bars, panes or fences joined along a line: east-west when {@code alongX}, else north-south. */
	static BlockState joined(Block block, boolean alongX) {
		return block.defaultBlockState().setValue(BlockStateProperties.EAST, alongX).setValue(BlockStateProperties.WEST, alongX)
				.setValue(BlockStateProperties.NORTH, !alongX).setValue(BlockStateProperties.SOUTH, !alongX);
	}

	static BlockState lantern(boolean soul, boolean hanging) {
		return (soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(BlockStateProperties.HANGING, hanging);
	}

	static BlockState pickles(int count) {
		return Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, count);
	}

	static BlockState lit(Block block) {
		return block.defaultBlockState().setValue(BlockStateProperties.LIT, true);
	}

	/** A mob head standing on the ground, of a kind and turn that depend on the position. */
	BlockState head(int x, int y, int z) {
		Block head = HEADS[(int) (rand(x, y, z, 401) * HEADS.length)];
		return head.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, (int) (rand(x, y, z, 402) * 16));
	}

	static BlockState head(Block head, int turn) {
		return head.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, turn & 15);
	}

	/** A bed whose foot is at (x, y, z) with its head towards {@code facing}. */
	void bed(int x, int y, int z, Direction facing, Block bed) {
		BlockState state = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		set(x, y, z, state.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		set(x + facing.getStepX(), y, z + facing.getStepZ(), state.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
	}

	/** A lamp post: a post of {@code post} with a lantern or light on top. */
	void lampPost(int x, int z, int y0, int height, BlockState post, BlockState light) {
		for (int y = y0; y < y0 + height; y++) {
			set(x, y, z, post);
		}
		set(x, y0 + height, z, light);
	}

	/** A pike with a mob head stuck on it. */
	void pike(int x, int z, int y0, int height, Block pole) {
		for (int y = y0; y < y0 + height; y++) {
			set(x, y, z, pole);
		}
		set(x, y0 + height, z, head(x, y0 + height, z));
	}

	/** A pile of something precious, highest in the middle. */
	void hoard(int x0, int y0, int z0, double radius, BlockState... treasure) {
		int r = (int) Math.ceil(radius);
		for (int x = x0 - r; x <= x0 + r; x++) {
			for (int z = z0 - r; z <= z0 + r; z++) {
				double d = Math.sqrt((x - x0) * (x - x0) + (z - z0) * (z - z0));
				if (d > radius || !column(x, z)) {
					continue;
				}
				int height = (int) Math.round((radius - d) * 0.8 + rand(x, y0, z, 403) * 0.8);
				for (int h = 0; h < height; h++) {
					set(x, y0 + h, z, treasure[(int) (rand(x, y0 + h, z, 404) * treasure.length)]);
				}
			}
		}
	}

	/**
	 * The frame of an active conduit around (x, y, z): three crossed rings of prismarine and sea
	 * lanterns, 42 blocks in all, which is the most a conduit can use. Its power reaches 96 blocks.
	 */
	void conduit(int x0, int y0, int z0) {
		for (int i = -2; i <= 2; i++) {
			for (int j = -2; j <= 2; j++) {
				for (int k = -2; k <= 2; k++) {
					int ai = Math.abs(i);
					int aj = Math.abs(j);
					int ak = Math.abs(k);
					boolean frame = (ai > 1 || aj > 1 || ak > 1)
							&& (i == 0 && (aj == 2 || ak == 2) || j == 0 && (ai == 2 || ak == 2) || k == 0 && (ai == 2 || aj == 2));
					if (frame) {
						set(x0 + i, y0 + j, z0 + k, (i + j + k) % 2 == 0 ? s(Blocks.SEA_LANTERN) : s(Blocks.PRISMARINE_BRICKS));
					} else if (ai <= 1 && aj <= 1 && ak <= 1) {
						set(x0 + i, y0 + j, z0 + k, WATER);
					}
				}
			}
		}
		set(x0, y0, z0, Blocks.CONDUIT);
	}

	// --- containers and spawners -------------------------------------------------------------

	ResourceKey<LootTable> loot(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, NaturesMod.id(lootFolder + name));
	}

	void chest(int x, int y, int z, Direction facing, String loot) {
		container(x, y, z, facing(Blocks.CHEST, facing), loot);
	}

	void barrel(int x, int y, int z, Direction facing, String loot) {
		container(x, y, z, facing(Blocks.BARREL, facing), loot);
	}

	private void container(int x, int y, int z, BlockState state, String loot) {
		BlockPos at = at(x, y, z);
		if (!box.isInside(at)) {
			return;
		}
		set(x, y, z, state);
		RandomizableContainer.setBlockEntityLootTable(level, RandomSource.create(seed ^ at.asLong()), at, loot(loot));
	}

	void spawner(int x, int y, int z, EntityType<?> type) {
		BlockPos at = at(x, y, z);
		if (type == null || !box.isInside(at)) {
			return;
		}
		set(x, y, z, Blocks.SPAWNER);
		if (level.getBlockEntity(at) instanceof SpawnerBlockEntity spawner) {
			spawner.setEntityId(type, RandomSource.create(seed ^ at.asLong()));
		}
	}

	/** A spawner of one of the creatures of the deep. */
	void spawner(int x, int y, int z, String creature) {
		spawner(x, y, z, ModCreatures.type(creature));
	}

	/** An item frame hung in the water at (x, y, z), facing {@code facing} (away from its wall). */
	void frame(int x, int y, int z, Direction facing, ItemStack item) {
		BlockPos at = at(x, y, z);
		if (!box.isInside(at)) {
			return;
		}
		ItemFrame frame = new ItemFrame(level.getLevel(), at, rotation.rotate(facing));
		frame.setItem(item, false);
		level.addFreshEntity(frame);
	}

	void frame(int x, int y, int z, Direction facing, Item item) {
		frame(x, y, z, facing, new ItemStack(item));
	}

	static ItemStack weapon(AbyssWeapon.Kind kind) {
		Item item = ModItems.WEAPONS.get(kind);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	/** An armor stand wearing what it is given, head first. */
	void stand(int x, int y, int z, Direction facing, ItemStack... armor) {
		BlockPos at = at(x, y, z);
		if (!box.isInside(at)) {
			return;
		}
		ArmorStand stand = EntityTypes.ARMOR_STAND.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (stand == null) {
			return;
		}
		stand.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, rotation.rotate(facing).toYRot(), 0.0F);
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
		for (int i = 0; i < armor.length && i < slots.length; i++) {
			stand.setItemSlot(slots[i], armor[i]);
		}
		level.addFreshEntity(stand);
	}

	/** A guardian that keeps the place, waiting for whoever comes for its treasure. */
	void keeper(int x, int y, int z, String creature) {
		BlockPos at = at(x, y, z);
		EntityType<? extends Mob> type = ModCreatures.type(creature);
		if (type == null || !box.isInside(at)) {
			return;
		}
		Mob keeper = type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (keeper == null) {
			return;
		}
		keeper.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, layout.nextFloat() * 360.0F, 0.0F);
		keeper.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
		keeper.setPersistenceRequired();
		level.addFreshEntityWithPassengers(keeper);
	}

	// --- chance -------------------------------------------------------------------------------

	/** A random number in [0, 1) that depends only on the position (in the structure) and the salt. */
	double rand(int x, int y, int z, int salt) {
		long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ salt * 0x27D4EB2F165667C5L;
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	boolean chance(int x, int y, int z, int salt, double p) {
		return rand(x, y, z, salt) < p;
	}

	@SafeVarargs
	final <T> T pick(int x, int y, int z, int salt, T... options) {
		return options[(int) (rand(x, y, z, salt) * options.length)];
	}

	/** Old stonework: mostly the first block, now and then one of the others. */
	BlockState worn(int x, int y, int z, BlockState main, BlockState... wear) {
		double r = rand(x, y, z, 405);
		if (r < 0.7 || wear.length == 0) {
			return main;
		}
		return wear[(int) ((r - 0.7) / 0.3 * wear.length)];
	}

	/** A ruin is never whole: with this chance a block has crumbled away to water. */
	boolean crumbled(int x, int y, int z, double p) {
		return rand(x, y, z, 406) < p;
	}
}
