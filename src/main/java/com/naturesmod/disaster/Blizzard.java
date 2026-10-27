package com.naturesmod.disaster;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.naturesmod.NaturesConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A blizzard: a gale full of snow, hundreds of blocks across and lasting minutes, and inside it a whiteout
 * where you can't see a few blocks. It brings its own cold, whatever the land it comes over: within its
 * reach the snow piles up fast (less of it on hot ground), drifting several blocks deep in the lee of walls,
 * cliffs and trunks and filling hollows (with powder snow in the drifts of the worst storms, which one sinks
 * into), settling on roofs and on the crowns of trees; still water freezes over, and torches, fires and
 * campfires out in the open are blown out. The wind veers a little as it goes and comes in gusts, now and
 * then a squall far stronger than the gale. It blows dropped things away and leans on the animals; on a
 * player it leans from the player's own game (see the client's effects of it), so walking into it is a
 * struggle. Out in it without warm clothes (leather) or a fire close by you chill and then freeze through,
 * and in the strongest storms the cold wounds as well; a roof over your head keeps the worst of it off.
 *
 * <p>The work is bounded every tick: so many columns of land looked at about each player out in it (and
 * about its heart, where no one is), and so many blocks changed in all, however many players there are;
 * the things about the players are looked for every few ticks, so many at most.
 */
public final class Blizzard extends Disaster {
	/** At most this many blocks changed a tick, all players together. */
	private static final int MAX_CHANGES = 300;
	/** At most this many columns looked at a tick, all players together. */
	private static final int MAX_COLUMNS = 640;
	/** How far about each player the snow is laid where it is seen to pile up (only a little falls further off). */
	private static final double NEAR = 64.0;
	/** Things other than players looked at about each player, every few ticks. */
	private static final int MAX_NEARBY = 64;
	/** Over how many blocks its edge thins out to nothing. */
	private static final double EDGE = 60.0;
	/** How fast the whole storm drifts with its wind, in blocks a tick. */
	private static final double DRIFT = 0.025;

	private final double reach;
	private final double visibility;
	private final double flakeSpeed;
	private double heading;
	private double veer;
	private Vec3 wind;
	private double gust = 1.0;
	private double gustTarget = 1.0;
	private int gustLeft;
	/** Players already told they are freezing. */
	private final Set<UUID> warned = new HashSet<>();
	/** What has had its share of the wind this round (two players close together find the same things). */
	private final Set<Entity> handled = new HashSet<>();
	/** Columns that may still be looked at this tick. */
	private int columnsLeft;
	private int snowLaid;
	private int iceFormed;
	private int lightsOut;

	/**
	 * The snow on a column: where the next snow would go, how much lies there already (in layers, eight to a
	 * block), how many blocks of it are powder snow, the height it starts at and what it lies on.
	 */
	private record Pack(BlockPos surface, int depth, int powder, int ground, BlockState base) {
	}

	Blizzard(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.BLIZZARD, level, at, intensity, natural, (int) (3600 + 1200 * DisasterType.BLIZZARD.clamp(intensity)));
		heading = random.nextDouble() * Math.PI * 2.0;
		wind = new Vec3(Math.cos(heading), 0.0, Math.sin(heading));
		reach = 250.0 + 30.0 * this.intensity;
		// A few blocks in the worst of them: some nine at level 4, four at level 5.
		visibility = Math.max(3.5, 28.0 - 4.8 * this.intensity);
		// How fast the snow flies past, in blocks a tick: a gale.
		flakeSpeed = 0.45 + 0.13 * this.intensity;
		y = ground((int) Math.floor(x), (int) Math.floor(z)) + 1;
	}

	@Override
	public float radius() {
		return (float) reach;
	}

	@Override
	public Vec3 direction() {
		return wind;
	}

	@Override
	public float strength() {
		float in = Math.min(1.0F, age / 200.0F);
		float out = Math.min(1.0F, (duration - age) / 300.0F);
		return Math.max(0.0F, Math.min(in, out));
	}

	@Override
	public float[] extra() {
		return new float[] {(float) visibility, (float) flakeSpeed, (float) gust};
	}

	// --- for the tests ------------------------------------------------------------------------

	/** How far one can see in it at its height, in blocks (less in the gusts). */
	public double visibility() {
		return visibility;
	}

	/** How hard the wind blows now against its usual: about 0.7 in a lull to 1.7 in a squall. */
	public double gust() {
		return gust;
	}

	/** Layers of snow it has laid so far (a block of snow is eight). */
	public int snowLaid() {
		return snowLaid;
	}

	/** Blocks of still water it has frozen over so far. */
	public int iceFormed() {
		return iceFormed;
	}

	/** Torches, fires and campfires out in the open it has blown out so far. */
	public int lightsOut() {
		return lightsOut;
	}

	/** Whether the cold gets at something: out under the sky, able to freeze (no leather on) and with no fire near. */
	public boolean exposed(LivingEntity entity) {
		return entity.canFreeze() && !sheltered(entity) && !nearHeat(entity.blockPosition());
	}

	/**
	 * How deep the snow lies on a column, in layers: a block of snow or of powder snow is eight. Snow lying on
	 * the crowns of trees counts too.
	 */
	public static int snowDepth(ServerLevel level, BlockPos column) {
		Pack pack = pack(level, column.getX(), column.getZ());
		return pack == null ? 0 : pack.depth();
	}

	// --- the storm ----------------------------------------------------------------------------

	@Override
	protected void begin() {
		BlockPos heart = BlockPos.containing(x, y, z);
		if (!natural && !level.isRaining() && level.dimension() == Level.OVERWORLD && cold(heart)) {
			// Where it is cold enough the game's own snow falls with it; anywhere else the whiteout hides the sky anyway,
			// and rain falling through it would be wrong.
			level.getServer().setWeatherParameters(0, duration + 600, true, false);
		}
		warn(reach + 150.0, player -> Component.translatable("naturesmod.warning.blizzard"));
	}

	@Override
	protected void tick() {
		blow();
		// The storm drifts with its wind.
		x += wind.x * DRIFT;
		z += wind.z * DRIFT;
		float strength = strength();
		if (strength <= 0.0F) {
			return;
		}
		if (age % 5 == 0) {
			chill(strength);
		}
		if (griefing()) {
			snowfall(strength);
		}
	}

	/** The wind veers a little as the storm goes on, and comes in gusts: every so often a squall far stronger than the gale. */
	private void blow() {
		veer = Math.max(-0.0004, Math.min(0.0004, veer * 0.995 + random.nextGaussian() * 0.00002));
		heading += veer;
		wind = new Vec3(Math.cos(heading), 0.0, Math.sin(heading));
		if (--gustLeft <= 0) {
			gustLeft = 40 + random.nextInt(120);
			gustTarget = random.nextInt(5) == 0 ? 1.4 + random.nextDouble() * 0.3 : 0.7 + random.nextDouble() * 0.5;
		}
		gust += (gustTarget - gust) * 0.05;
	}

	private boolean cold(BlockPos pos) {
		return level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
	}

	// --- the cold and the wind ----------------------------------------------------------------

	/** The cold and the wind on the players out in the storm and on everything about them (every five ticks). */
	private void chill(float strength) {
		handled.clear();
		for (ServerPlayer player : level.players()) {
			double d = distance2d(player, x, z);
			if (player.isSpectator() || d > reach) {
				continue;
			}
			float force = strength * (float) Math.min(1.0, (reach - d) / EDGE);
			if (!immune(player)) {
				freeze(player, force);
			}
			int looked = 0;
			for (Entity entity : level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(24.0), e -> e.isAlive() && !(e instanceof Player))) {
				if (looked++ >= MAX_NEARBY) {
					break;
				}
				if (!handled.add(entity) || sheltered(entity)) {
					continue;
				}
				if (entity instanceof ItemEntity item) {
					// Dropped things skitter off downwind, hopping as they go.
					double push = 0.1 * gust * force;
					shove(item, wind.x * push, 0.03 * force, wind.z * push);
				} else if (entity instanceof LivingEntity living) {
					double push = 0.035 * gust * force;
					shove(living, wind.x * push, 0.0, wind.z * push);
					if (age % 20 == 0) {
						numb(living, force);
					}
				}
			}
		}
		handled.clear();
	}

	/**
	 * Out in the storm and not dressed for it (leather keeps the cold out), nor by a fire, a player chills, five
	 * ticks' worth at a time: frozen through in some ten seconds of a strong storm, and then hurt by it (the
	 * game's own freezing, and in a storm of level 4 or 5 the blizzard's cold besides). By a fire one thaws.
	 */
	private void freeze(ServerPlayer player, float force) {
		if (!player.canFreeze() || sheltered(player)) {
			return;
		}
		int frozen = player.getTicksFrozen();
		int full = player.getTicksRequiredToFreeze();
		if (nearHeat(player.blockPosition())) {
			if (frozen > 0) {
				player.setTicksFrozen(Math.max(0, frozen - 10));
			}
			return;
		}
		// The game thaws two ticks' worth a tick (out of powder snow): the storm outdoes that.
		int chill = 10 + Math.round((0.5F + 0.75F * intensity) * force * (float) (0.6 + 0.4 * gust));
		int now = Math.min(full + 40, frozen + chill);
		if (now > frozen) {
			player.setTicksFrozen(now);
		}
		if (now >= full / 2 && NaturesConfig.get().disasterWarnings && warned.add(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("naturesmod.warning.blizzard_freezing").withStyle(ChatFormatting.GOLD));
		}
		if (now >= full && intensity >= 3.5F && age % 60 == 0) {
			hurt(player, DisasterDamage.BLIZZARD, intensity >= 4.5F ? 2.0F : 1.0F);
		}
	}

	/**
	 * Animals and monsters out in it chill too, twenty ticks' worth at a time: they slow and shiver, but only a
	 * storm of level 5 freezes them through (and so, slowly, to death).
	 */
	private void numb(LivingEntity entity, float force) {
		if (!entity.canFreeze()) {
			return;
		}
		int full = entity.getTicksRequiredToFreeze();
		int most = intensity >= 4.5F ? full + 40 : full - 1;
		int frozen = entity.getTicksFrozen();
		if (frozen >= most) {
			return;
		}
		int chill = 40 + Math.round(4.0F * (0.5F + 0.75F * intensity) * force);
		entity.setTicksFrozen(Math.min(most, frozen + chill));
	}

	private boolean nearHeat(BlockPos at) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -1; dy <= 2; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					BlockState state = level.getBlockState(pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz));
					if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)
							|| state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// --- the snow -----------------------------------------------------------------------------

	/**
	 * The snow coming down: so many columns a tick about each player out in the storm, and about its heart if no
	 * one is there, within a few dozen blocks (where it is seen to pile up); a few more anywhere in its reach.
	 */
	private void snowfall(float strength) {
		int per = Math.round((24.0F + 16.0F * intensity) * strength * (float) (0.7 + 0.3 * gust));
		int changes = MAX_CHANGES;
		columnsLeft = MAX_COLUMNS;
		boolean heartSeen = false;
		double far = reach + NEAR;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || changes <= 0) {
				continue;
			}
			double dx = player.getX() - x;
			double dz = player.getZ() - z;
			double d2 = dx * dx + dz * dz;
			if (d2 > far * far) {
				continue;
			}
			if (d2 < 40.0 * 40.0) {
				heartSeen = true;
			}
			changes = fall(player.getX(), player.getZ(), NEAR, per, changes);
		}
		if (!heartSeen) {
			changes = fall(x, z, NEAR, per, changes);
		}
		fall(x, z, reach, Math.round(10.0F * strength), changes);
	}

	/** Snow on `samples` columns about a place, no more than `changes` blocks changed; returns how many changes are left. */
	private int fall(double cx, double cz, double spread, int samples, int changes) {
		// A few patches of ground a tick, each a chunk across, not columns strewn all about: every chunk the snow lands
		// in has to be drawn again by the players' games, and a weak computer can only redraw so many a tick.
		int patchX = 0;
		int patchZ = 0;
		for (int i = 0; i < samples && changes > 0 && columnsLeft > 0; i++) {
			columnsLeft--;
			if (i % 16 == 0) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				double r = spread * Math.sqrt(random.nextDouble());
				patchX = Math.floorDiv((int) Math.floor(cx + Math.cos(angle) * r), 16) * 16;
				patchZ = Math.floorDiv((int) Math.floor(cz + Math.sin(angle) * r), 16) * 16;
			}
			int bx = patchX + random.nextInt(16);
			int bz = patchZ + random.nextInt(16);
			double dx = bx + 0.5 - x;
			double dz = bz + 0.5 - z;
			double from = Math.sqrt(dx * dx + dz * dz);
			// Thinning out toward its edge.
			if (from > reach || from > reach - EDGE && random.nextDouble() * EDGE > reach - from) {
				continue;
			}
			if (drop(bx, bz)) {
				changes--;
			}
		}
		return changes;
	}

	/**
	 * A little more snow on one column, wherever it would lie: on the ground, a roof or the crown of a tree, deeper
	 * where something breaks the wind or the column lies in a hollow. Or its water freezes over, or a light out in
	 * the open there goes out. True if a block changed.
	 */
	private boolean drop(int bx, int bz) {
		if (!loaded(bx, bz)) {
			return false;
		}
		Pack pack = pack(level, bx, bz);
		if (pack == null) {
			return false;
		}
		BlockPos at = pack.surface();
		BlockPos below = at.below();
		BlockState there = level.getBlockState(at);
		BlockState under = level.getBlockState(below);
		if (there.is(Blocks.FIRE) || there.is(Blocks.SOUL_FIRE)) {
			if (random.nextDouble() < 0.4 * gust) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				lightsOut++;
				return true;
			}
			return false;
		}
		if (there.is(Blocks.TORCH) || there.is(Blocks.WALL_TORCH)) {
			if (random.nextDouble() < 0.2 * gust) {
				level.destroyBlock(at, true);
				lightsOut++;
				return true;
			}
			return false;
		}
		if (under.getBlock() instanceof CampfireBlock && under.getValue(CampfireBlock.LIT)) {
			if (random.nextDouble() < 0.1 * gust) {
				level.setBlock(below, under.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
				lightsOut++;
				return true;
			}
			return false;
		}
		// Still water freezes over (and stays frozen: nothing here thaws it but a torch's warmth).
		if (pack.depth() == 0 && under.is(Blocks.WATER) && level.getFluidState(below).isSource()) {
			level.setBlock(below, Blocks.ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
			iceFormed++;
			return true;
		}
		// Hot ground gets half as much, and it lies half as deep.
		boolean hot = level.getBiome(at).value().getBaseTemperature() >= 1.5F;
		if (hot && random.nextBoolean()) {
			return false;
		}
		float scale = hot ? 0.5F : 1.0F;
		int open = Math.max(2, Math.round(8.0F * (0.5F + 0.3F * intensity) * scale));
		int deepest = Math.max(open, Math.round(8.0F * (1.2F + 0.85F * intensity) * scale));
		int depth = pack.depth();
		double top = pack.ground() + depth / 8.0;
		// A drift or a hollow only where the snow would lie deeper than on open ground: a column the snow about it
		// has merely got ahead of (a block of it formed next door first) is neither.
		double plain = Math.max(top, pack.ground() + open / 8.0);
		int cap = open;
		int add = 1;
		boolean drift = false;
		if (pack.base().is(BlockTags.LEAVES) && depth < 8) {
			// On the crowns of trees, a cap of snow.
			cap = hot ? 1 : 3;
		} else {
			double bank = shelter(bx, bz, pack.ground());
			if (!(bank > plain + 0.1)) {
				bank = hollow(bx, bz, plain);
			}
			if (bank > plain + 0.1) {
				cap = Math.max(open, Math.min(deepest, (int) Math.floor((bank - pack.ground()) * 8.0)));
				add = 2;
				drift = true;
			}
		}
		if (depth >= cap) {
			return false;
		}
		add = Math.max(1, Math.min(add, cap - depth));
		if (there.is(Blocks.SNOW)) {
			int had = there.getValue(SnowLayerBlock.LAYERS);
			int layers = had + add;
			BlockState next;
			if (layers < 8) {
				next = there.setValue(SnowLayerBlock.LAYERS, layers);
			} else if (occupied(at)) {
				// Something stands in it: no block of snow forms about it, to wall it in or smother it.
				if (had >= 8) {
					return false;
				}
				next = there.setValue(SnowLayerBlock.LAYERS, 8);
			} else {
				next = fullBlock(pack, drift);
			}
			level.setBlock(at, next, Block.UPDATE_CLIENTS);
			snowLaid += add;
			return true;
		}
		if (!(there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty())) {
			return false;
		}
		if (under.is(Blocks.POWDER_SNOW)) {
			// Nothing settles on powder snow in layers: the drift grows a block at a time.
			if (random.nextInt(8) >= add || occupied(at)) {
				return false;
			}
			level.setBlock(at, fullBlock(pack, drift), Block.UPDATE_CLIENTS);
			snowLaid += 8;
			return true;
		}
		BlockState layer = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, add);
		if (!layer.canSurvive(level, at)) {
			return false;
		}
		level.setBlock(at, layer, Block.UPDATE_CLIENTS);
		snowLaid += add;
		return true;
	}

	/** Snow grown to a full block: in the drifts and hollows of a storm of level 4 or 5 now and then powder snow, to sink into. */
	private BlockState fullBlock(Pack pack, boolean drift) {
		int most = intensity >= 4.5F ? 2 : intensity >= 3.5F ? 1 : 0;
		if (drift && pack.powder() < most && !pack.base().is(BlockTags.LEAVES) && random.nextBoolean()) {
			return Blocks.POWDER_SNOW.defaultBlockState();
		}
		return Blocks.SNOW_BLOCK.defaultBlockState();
	}

	/** Whether anything living stands in a block's room (looked at only where a block of snow would form). */
	private boolean occupied(BlockPos pos) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty();
	}

	/**
	 * How high snow may drift on a column for what breaks the wind there: up to a block under the top of a wall or
	 * a cliff upwind, a block lower for every block further off; a short tail behind a tree's trunk; or banked up
	 * against a wall just downwind. Not the crowns of trees: the wind goes through those.
	 */
	private double shelter(int bx, int bz, int ground) {
		double best = Double.NEGATIVE_INFINITY;
		for (int d = 1; d <= 6; d++) {
			int ux = (int) Math.floor(bx + 0.5 - wind.x * d);
			int uz = (int) Math.floor(bz + 0.5 - wind.z * d);
			if (!loaded(ux, uz)) {
				break;
			}
			double h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ux, uz) - d;
			if (h > best && h > ground) {
				if (level.getBlockState(new BlockPos(ux, ground, uz)).is(BlockTags.LOGS)) {
					h = Math.min(h, ground + 2.0 - 0.5 * d);
				}
				best = Math.max(best, h);
			}
		}
		int fx = (int) Math.floor(bx + 0.5 + wind.x * 1.2);
		int fz = (int) Math.floor(bz + 0.5 + wind.z * 1.2);
		if ((fx != bx || fz != bz) && loaded(fx, fz) && !level.getBlockState(new BlockPos(fx, ground, fz)).is(BlockTags.LOGS)) {
			best = Math.max(best, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fx, fz) - 1.5);
		}
		return best;
	}

	/**
	 * A hollow: three or four sides of a column standing higher than `top` (the snow in it, or as deep as it would
	 * lie on open ground, whichever is higher): a ditch, a pit. Snow fills it up to the lowest of them.
	 */
	private double hollow(int bx, int bz, double top) {
		int higher = 0;
		double rim = Double.MAX_VALUE;
		for (int side = 0; side < 4; side++) {
			int nx = bx + (side == 0 ? 1 : side == 1 ? -1 : 0);
			int nz = bz + (side == 2 ? 1 : side == 3 ? -1 : 0);
			if (!loaded(nx, nz)) {
				return Double.NEGATIVE_INFINITY;
			}
			int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz);
			if (h > top + 0.1) {
				higher++;
				rim = Math.min(rim, h);
			}
		}
		return higher >= 3 ? rim : Double.NEGATIVE_INFINITY;
	}

	/**
	 * The snow on a column as it lies: snow layers and powder snow don't count for the heightmap (it is not solid to
	 * it), blocks of snow do. Null where there is no room for any.
	 */
	private static Pack pack(ServerLevel level, int bx, int bz) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		if (top <= level.getMinY() + 1 || top >= level.getMaxY()) {
			return null;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, top, bz);
		BlockState there = level.getBlockState(pos);
		int blocks = 0;
		int powder = 0;
		// Up through powder snow to the top of it.
		while (there.is(Blocks.POWDER_SNOW) && powder < 8 && pos.getY() < level.getMaxY() - 1) {
			powder++;
			blocks++;
			pos.move(0, 1, 0);
			there = level.getBlockState(pos);
		}
		int surface = pos.getY();
		int layers = there.is(Blocks.SNOW) ? there.getValue(SnowLayerBlock.LAYERS) : 0;
		// Down through the snow already lying there to what it lies on.
		pos.set(bx, top - 1, bz);
		BlockState base = level.getBlockState(pos);
		int down = 0;
		while ((base.is(Blocks.SNOW_BLOCK) || base.is(Blocks.POWDER_SNOW)) && down < 12 && pos.getY() > level.getMinY() + 1) {
			if (base.is(Blocks.POWDER_SNOW)) {
				powder++;
			}
			blocks++;
			down++;
			pos.move(0, -1, 0);
			base = level.getBlockState(pos);
		}
		return new Pack(new BlockPos(bx, surface, bz), blocks * 8 + layers, powder, pos.getY() + 1, base);
	}
}
