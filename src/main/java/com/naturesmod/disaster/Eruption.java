package com.naturesmod.disaster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import com.naturesmod.block.ModBlocks;
import com.naturesmod.tree.TreeFeller;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A volcano erupting, rated on the volcanic explosivity index. First the unrest: tremors under the
 * mountain and steam from its summit. Then it blows: a blast heard far off (the sound arriving well
 * after the flash), trees around the summit laid flat, a crater torn open and filled with lava. An
 * eruption column rises hundreds of blocks and spreads into an umbrella; ash falls downwind and
 * settles in layers; lightning flickers in the plume; glowing bombs fly out of the vent and land in
 * fire; lava overflows the crater and runs down the steepest way, its crust cooling to basalt and
 * blackstone. In the great eruptions (index 4 and up) the column collapses in pyroclastic flows,
 * avalanches of searing gas and ash that race down the flanks, burning all they reach and charring
 * the forests. Then it wanes to steam.
 */
public final class Eruption extends Disaster {
	/** Volcanic bombs fly under these, on the server and the clients alike. */
	public static final double GRAVITY = 0.05;
	public static final double DRAG = 0.995;

	private final int vei;
	private final BlockPos vent;
	private final int unrest;
	private final int plinian;
	private final double columnHeight;
	private final Vec3 wind;
	private final double ashReach;
	private final int lavaCap;
	private final int flowCount;
	/** When, into the eruption, each pulse of the collapsing column sends a pyroclastic flow down. */
	private final int[] flowTimes;
	private final int crater;
	private final List<Bomb> bombs = new ArrayList<>();
	private final List<Flow> flows = new ArrayList<>();
	private final List<LavaFlow> lavaFlows = new ArrayList<>();
	private final Deque<Cooling> cooling = new ArrayDeque<>();
	private int lavaPlaced;
	private int flowsLaunched;
	private boolean blown;
	private int nextTremor = 20;

	private static final class Bomb {
		Vec3 pos;
		Vec3 velocity;
		final double size;
		int age;

		Bomb(Vec3 pos, Vec3 velocity, double size) {
			this.pos = pos;
			this.velocity = velocity;
			this.size = size;
		}
	}

	/** A pyroclastic flow: its front, racing downhill. */
	private static final class Flow {
		double x;
		double y;
		double z;
		double dirX;
		double dirZ;
		double travelled;
		final double length;
		final double width;
		final double speed;

		Flow(double x, double y, double z, double angle, double length, double width, double speed) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.dirX = Math.cos(angle);
			this.dirZ = Math.sin(angle);
			this.length = length;
			this.width = width;
			this.speed = speed;
		}
	}

	/** The head of a lava flow, feeling its way down. */
	private static final class LavaFlow {
		BlockPos head;
		int steps;
		final int length;
		int wait;
		int dx;
		int dz;

		LavaFlow(BlockPos head, int length, int dx, int dz) {
			this.head = head;
			this.length = length;
			this.dx = dx;
			this.dz = dz;
		}
	}

	private record Cooling(BlockPos pos, int at) {
	}

	Eruption(ServerLevel level, Vec3 at, float intensity, boolean natural) {
		super(DisasterType.ERUPTION, level, at, intensity, natural, 0);
		vei = Math.round(this.intensity);
		vent = summit(BlockPos.containing(at));
		x = vent.getX() + 0.5;
		y = vent.getY() + 1;
		z = vent.getZ() + 0.5;
		unrest = 500 + 100 * vei;
		plinian = 1200 + 500 * (vei - 2);
		duration = unrest + plinian + 900;
		columnHeight = Math.min(600.0, 40.0 * Math.pow(vei, 1.6));
		double windAngle = random.nextDouble() * Math.PI * 2.0;
		wind = new Vec3(Math.cos(windAngle), 0.0, Math.sin(windAngle));
		ashReach = 40.0 + 40.0 * vei;
		lavaCap = 40 + 30 * vei;
		flowCount = vei >= 4 ? vei - 2 : 0;
		flowTimes = new int[flowCount];
		for (int i = 0; i < flowCount; i++) {
			flowTimes[i] = (int) (plinian * (0.55 + 0.35 * (i + 0.2 + random.nextDouble() * 0.6) / flowCount));
		}
		crater = 3 + vei;
	}

	/** The highest ground within forty blocks: the summit of the mountain. */
	private BlockPos summit(BlockPos around) {
		BlockPos best = around;
		int bestY = Integer.MIN_VALUE;
		for (int dx = -40; dx <= 40; dx += 3) {
			for (int dz = -40; dz <= 40; dz += 3) {
				int bx = around.getX() + dx;
				int bz = around.getZ() + dz;
				if (!loaded(bx, bz)) {
					continue;
				}
				int top = terrain(bx, bz);
				// Close to where it was asked for, all else being equal.
				int score = top * 4 - (Math.abs(dx) + Math.abs(dz)) / 6;
				if (score > bestY) {
					bestY = score;
					best = new BlockPos(bx, top, bz);
				}
			}
		}
		return best;
	}

	private int phase() {
		return age < unrest ? 0 : age < unrest + plinian ? 1 : 2;
	}

	@Override
	public float radius() {
		return (float) ashReach;
	}

	@Override
	public Vec3 direction() {
		return wind;
	}

	@Override
	public float strength() {
		return switch (phase()) {
			case 0 -> 0.2F;
			case 1 -> 1.0F;
			default -> (float) Math.max(0.0, 1.0 - (age - unrest - plinian) / 900.0);
		};
	}

	@Override
	public float[] extra() {
		double rising = phase() == 1 ? Math.min(1.0, (age - unrest) / 400.0) : phase() == 2 ? strength() : 0.0;
		float[] out = new float[9 + flows.size() * 3];
		out[0] = vei;
		out[1] = phase();
		out[2] = vent.getY();
		out[3] = (float) (columnHeight * rising);
		out[4] = (float) wind.x;
		out[5] = (float) wind.z;
		out[6] = (float) ashReach;
		out[7] = crater;
		out[8] = flows.size();
		for (int i = 0; i < flows.size(); i++) {
			Flow flow = flows.get(i);
			out[9 + i * 3] = (float) flow.x;
			out[10 + i * 3] = (float) flow.y;
			out[11 + i * 3] = (float) flow.z;
		}
		return out;
	}

	@Override
	protected void begin() {
		warn(1200, player -> Component.translatable("naturesmod.warning.eruption", (int) distance2d(player, x, z),
				compass(player.getX(), player.getZ(), x, z)));
	}

	@Override
	protected void tick() {
		int phase = phase();
		if (phase == 0 && age >= nextTremor) {
			// Magma forcing its way up: swarms of small quakes under the mountain.
			Disasters.add(new Earthquake(level, new Vec3(x + random.nextGaussian() * 8, y, z + random.nextGaussian() * 8),
					(float) (2.8 + random.nextDouble() * 1.2), natural, true).quietly());
			nextTremor = age + 80 + random.nextInt(140);
		}
		if (phase == 1) {
			if (!blown) {
				blown = true;
				blast();
			}
			erupt();
		}
		tickBombs();
		tickFlows();
		tickLava();
	}

	// --- the blast ---------------------------------------------------------------------------

	private void blast() {
		DisasterNet.send(level, new DisasterNet.Event(DisasterNet.Event.BLAST, x, y, z, new float[] {vei}), 0);
		level.explode(null, x, y + 2, z, 4.0F + vei, Level.ExplosionInteraction.NONE);
		double reach = 30.0 + vei * 12.0;
		for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(vent).inflate(reach))) {
			if (immune(entity)) {
				continue;
			}
			Vec3 away = entity.position().subtract(x, y - 4, z);
			double d = Math.max(1.0, away.length());
			Vec3 push = away.normalize().scale(Math.max(0.0, 1.0 - d / reach) * 2.5);
			shove(entity, push.x, Math.max(0.4, push.y), push.z);
		}
		if (griefing()) {
			openCrater();
			for (int i = 0; i < 160 * vei; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				double d = crater + 4 + random.nextDouble() * reach * 1.6;
				int bx = (int) Math.floor(x + Math.cos(angle) * d);
				int bz = (int) Math.floor(z + Math.sin(angle) * d);
				if (!loaded(bx, bz) || random.nextDouble() > 1.3 - d / (reach * 1.6)) {
					continue;
				}
				BlockPos top = new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1, bz);
				if (TreeFeller.isLog(level.getBlockState(top))) {
					fellTree(top, new Vec3(Math.cos(angle), 0.0, Math.sin(angle)), 0, 4 + vei * 2);
				}
			}
		}
	}

	/** Blows the summit open into a crater and fills it with lava. */
	private void openCrater() {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -crater; dx <= crater; dx++) {
			for (int dz = -crater; dz <= crater; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > crater) {
					continue;
				}
				int bx = vent.getX() + dx;
				int bz = vent.getZ() + dz;
				if (!loaded(bx, bz)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
				int floor = vent.getY() - (int) Math.round(crater * 0.6 * (1.0 - (d / crater) * (d / crater)));
				for (int by = top; by > floor; by--) {
					if (!level.getBlockState(pos.set(bx, by, bz)).is(Blocks.BEDROCK)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				if (d < crater - 1.5 && floor > level.getMinY() + 2) {
					level.setBlock(pos.set(bx, floor, bz), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
					lavaPlaced++;
				}
			}
		}
	}

	// --- the eruption ------------------------------------------------------------------------

	private void erupt() {
		int since = age - unrest;
		double vigour = Math.min(1.0, since / 200.0);
		// Bombs out of the vent.
		if (random.nextDouble() < 0.25 * vigour * vei / 4.0) {
			throwBomb();
		}
		// Lava over the rim and down the flanks.
		if (griefing() && lavaPlaced < lavaCap && random.nextInt(Math.max(40, 260 - vei * 30)) == 0 && lavaFlows.size() < 2 + vei / 2) {
			startLavaFlow();
		}
		// Ash on the wind.
		if (griefing()) {
			for (int i = 0; i < 6 + 4 * vei; i++) {
				ashfall();
			}
		}
		// Lightning in the plume.
		if (random.nextInt(Math.max(30, 140 - vei * 15)) == 0) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double d = 8.0 + random.nextDouble() * 30.0;
			lightning(x + Math.cos(angle) * d + wind.x * 20, z + Math.sin(angle) * d + wind.z * 20);
		}
		// The column collapses: pyroclastic flows.
		if (flowsLaunched < flowCount && since >= flowTimes[flowsLaunched]) {
			launchFlow();
		}
	}

	private void throwBomb() {
		double angle = random.nextDouble() * Math.PI * 2.0;
		double elevation = Math.toRadians(50.0 + random.nextDouble() * 35.0);
		double speed = 1.0 + random.nextDouble() * (1.0 + vei * 0.3);
		Vec3 velocity = new Vec3(Math.cos(angle) * Math.cos(elevation) * speed, Math.sin(elevation) * speed, Math.sin(angle) * Math.cos(elevation) * speed);
		Vec3 start = new Vec3(x, y + 2, z);
		double size = 0.5 + random.nextDouble() * (0.5 + vei * 0.15);
		bombs.add(new Bomb(start, velocity, size));
		DisasterNet.send(level, new DisasterNet.Event(DisasterNet.Event.BOMB, start.x, start.y, start.z, new float[] {(float) velocity.x,
				(float) velocity.y, (float) velocity.z, (float) size}), 900);
	}

	private void tickBombs() {
		for (Iterator<Bomb> it = bombs.iterator(); it.hasNext(); ) {
			Bomb bomb = it.next();
			bomb.pos = bomb.pos.add(bomb.velocity);
			bomb.velocity = bomb.velocity.scale(DRAG).add(0.0, -GRAVITY, 0.0);
			bomb.age++;
			BlockPos at = BlockPos.containing(bomb.pos);
			if (bomb.age > 600 || at.getY() < level.getMinY()) {
				it.remove();
				continue;
			}
			if (!loaded(at.getX(), at.getZ())) {
				continue;
			}
			BlockState state = level.getBlockState(at);
			if (bomb.velocity.y < 0 && !state.isAir() && !state.canBeReplaced()) {
				it.remove();
				land(bomb, at.above());
			}
		}
	}

	private void land(Bomb bomb, BlockPos at) {
		DisasterNet.send(level, new DisasterNet.Event(DisasterNet.Event.BOMB_LANDS, at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
				new float[] {(float) bomb.size}), 600);
		level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, (float) (0.6 + bomb.size), Level.ExplosionInteraction.NONE);
		if (!griefing()) {
			return;
		}
		BlockPos below = at.below();
		if (!level.getBlockState(below).is(Blocks.BEDROCK)) {
			level.setBlock(below, random.nextBoolean() ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_ALL);
		}
		for (int i = 0; i < 4; i++) {
			BlockPos fire = at.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
			if (level.getBlockState(fire).isAir() && BaseFireBlock.canBePlacedAt(level, fire, Direction.UP)) {
				level.setBlock(fire, BaseFireBlock.getState(level, fire), Block.UPDATE_ALL);
			}
		}
	}

	/** Lays ash somewhere downwind: thick near the vent, a dusting far off. */
	private void ashfall() {
		double u = -0.15 + random.nextDouble() * 1.15;
		double v = (random.nextDouble() * 2.0 - 1.0) * (0.15 + Math.max(0.0, u) * 0.35);
		double px = -wind.z;
		double pz = wind.x;
		double ax = x + (wind.x * u + px * v) * ashReach;
		double az = z + (wind.z * u + pz * v) * ashReach;
		double distance = Math.sqrt((ax - x) * (ax - x) + (az - z) * (az - z));
		double weight = 1.0 - distance / ashReach;
		if (weight <= 0.0 || random.nextDouble() > weight) {
			return;
		}
		int bx = (int) Math.floor(ax);
		int bz = (int) Math.floor(az);
		if (!loaded(bx, bz)) {
			return;
		}
		ash(bx, bz, 1 + (int) (weight * 4.0));
	}

	/** Adds a layer of ash to a column, up to `most` layers deep. */
	private void ash(int bx, int bz, int most) {
		BlockPos top = new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz);
		BlockState there = level.getBlockState(top);
		BlockState under = level.getBlockState(top.below());
		// Ash layers don't block motion, so the heightmap stops on one already lying there.
		if (there.is(ModBlocks.ASH_LAYER)) {
			int layers = there.getValue(SnowLayerBlock.LAYERS);
			if (layers < Math.min(8, most)) {
				level.setBlock(top, there.setValue(SnowLayerBlock.LAYERS, layers + 1), Block.UPDATE_ALL);
			}
			return;
		}
		if ((there.isAir() || there.canBeReplaced() && !there.is(ModBlocks.ASH_LAYER)) && there.getFluidState().isEmpty()
				&& under.isFaceSturdy(level, top.below(), Direction.UP) && under.getFluidState().isEmpty()) {
			level.setBlock(top, ModBlocks.ASH_LAYER.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	// --- lava --------------------------------------------------------------------------------

	private void startLavaFlow() {
		double angle = random.nextDouble() * Math.PI * 2.0;
		int bx = (int) Math.floor(x + Math.cos(angle) * (crater + 1));
		int bz = (int) Math.floor(z + Math.sin(angle) * (crater + 1));
		if (!loaded(bx, bz)) {
			return;
		}
		BlockPos head = new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz);
		lavaFlows.add(new LavaFlow(head, 40 + vei * 25, (int) Math.signum(Math.round(Math.cos(angle))), (int) Math.signum(Math.round(Math.sin(angle)))));
	}

	private void tickLava() {
		for (Iterator<LavaFlow> it = lavaFlows.iterator(); it.hasNext(); ) {
			LavaFlow flow = it.next();
			if (--flow.wait > 0) {
				continue;
			}
			flow.wait = 6 + random.nextInt(8);
			if (lavaPlaced >= lavaCap || flow.steps >= flow.length || !griefing()) {
				it.remove();
				continue;
			}
			pour(flow.head);
			if (random.nextInt(3) == 0) {
				// The flow spreads a little to the side as it goes.
				pour(flow.head.offset(flow.dz, 0, -flow.dx));
			}
			BlockPos next = downhill(flow);
			if (next == null) {
				it.remove();
				continue;
			}
			flow.dx = next.getX() - flow.head.getX();
			flow.dz = next.getZ() - flow.head.getZ();
			flow.head = next;
			flow.steps++;
		}
		while (!cooling.isEmpty() && cooling.peekFirst().at() <= age) {
			BlockPos pos = cooling.pollFirst().pos();
			if (level.getBlockState(pos).is(Blocks.LAVA) && level.getFluidState(pos).isSource()) {
				int roll = random.nextInt(10);
				level.setBlock(pos, roll < 5 ? Blocks.BASALT.defaultBlockState() : roll < 8 ? Blocks.BLACKSTONE.defaultBlockState()
						: Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	private void pour(BlockPos at) {
		if (!loaded(at.getX(), at.getZ())) {
			return;
		}
		BlockPos top = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
		BlockState there = level.getBlockState(top);
		if (!level.getFluidState(top.below()).isEmpty()) {
			// Lava into water: it quenches to stone with a hiss.
			level.setBlock(top.below(), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
			return;
		}
		if (there.isAir() || there.canBeReplaced()) {
			level.setBlock(top, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
			lavaPlaced++;
			cooling.addLast(new Cooling(top, age + 400 + random.nextInt(400)));
		}
	}

	/** The lowest of the ground around the head of a flow, ahead of it more than behind. */
	private BlockPos downhill(LavaFlow flow) {
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		int here = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, flow.head.getX(), flow.head.getZ());
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0 || dx == -flow.dx && dz == -flow.dz) {
					continue;
				}
				int bx = flow.head.getX() + dx;
				int bz = flow.head.getZ() + dz;
				if (!loaded(bx, bz)) {
					continue;
				}
				int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
				double score = h - (dx == flow.dx && dz == flow.dz ? 0.6 : 0.0) + random.nextDouble() * 0.5;
				if (h <= here + 1 && score < bestScore) {
					bestScore = score;
					best = new BlockPos(bx, h, bz);
				}
			}
		}
		return best;
	}

	// --- pyroclastic flows -------------------------------------------------------------------

	private void launchFlow() {
		if (flowsLaunched == 0) {
			warnFlows();
		}
		flowsLaunched++;
		double angle = random.nextDouble() * Math.PI * 2.0;
		flows.add(new Flow(x + Math.cos(angle) * crater, y, z + Math.sin(angle) * crater, angle, 80.0 + vei * 30.0 + random.nextDouble() * 40.0,
				10.0 + vei * 2.0, 0.9 + random.nextDouble() * 0.5));
	}

	private void tickFlows() {
		for (Iterator<Flow> it = flows.iterator(); it.hasNext(); ) {
			Flow flow = it.next();
			steer(flow);
			flow.x += flow.dirX * flow.speed;
			flow.z += flow.dirZ * flow.speed;
			flow.travelled += flow.speed;
			int bx = (int) Math.floor(flow.x);
			int bz = (int) Math.floor(flow.z);
			if (loaded(bx, bz)) {
				flow.y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
			}
			sear(flow);
			if (flow.travelled > flow.length) {
				it.remove();
			}
		}
	}

	/** Gravity steers a flow down the steepest way, its momentum keeping it on course over small rises. */
	private void steer(Flow flow) {
		double bestAngle = 0.0;
		double best = Double.MAX_VALUE;
		double heading = Math.atan2(flow.dirZ, flow.dirX);
		for (double turn : new double[] {0.0, -0.5, 0.5, -1.0, 1.0}) {
			double angle = heading + turn;
			int bx = (int) Math.floor(flow.x + Math.cos(angle) * 6.0);
			int bz = (int) Math.floor(flow.z + Math.sin(angle) * 6.0);
			if (!loaded(bx, bz)) {
				continue;
			}
			double h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) + Math.abs(turn) * 1.5;
			if (h < best) {
				best = h;
				bestAngle = angle;
			}
		}
		double angle = heading + (bestAngle - heading) * 0.35;
		flow.dirX = Math.cos(angle);
		flow.dirZ = Math.sin(angle);
	}

	/** What a flow reaches burns: the living, the forests, the grass; and it leaves ash behind. */
	private void sear(Flow flow) {
		double half = flow.width / 2.0;
		if (age % 5 == 0) {
			AABB box = new AABB(flow.x - half, flow.y - 4, flow.z - half, flow.x + half, flow.y + 14, flow.z + half);
			for (Entity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> !immune(e))) {
				hurt(entity, DisasterDamage.PYROCLASTIC, 4.0F + vei);
				entity.igniteForSeconds(8.0F);
			}
		}
		if (!griefing()) {
			return;
		}
		for (int i = 0; i < 14; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = half * Math.sqrt(random.nextDouble());
			int bx = (int) Math.floor(flow.x + Math.cos(angle) * r);
			int bz = (int) Math.floor(flow.z + Math.sin(angle) * r);
			if (loaded(bx, bz)) {
				charColumn(bx, bz);
				if (random.nextInt(3) == 0) {
					ash(bx, bz, 2 + random.nextInt(2));
				}
			}
		}
	}

	/** Burns a column black: leaves gone, wood charred, grass scorched, plants and snow vanished. */
	void charColumn(int bx, int bz) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int by = top; by > top - 20 && by > level.getMinY(); by--) {
			BlockState state = level.getBlockState(pos.set(bx, by, bz));
			if (state.isAir()) {
				continue;
			}
			if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof VegetationBlock || state.is(Blocks.SNOW) || state.canBeReplaced()
					&& state.getFluidState().isEmpty()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			} else if (state.is(BlockTags.LOGS)) {
				BlockState charred = ModBlocks.CHARRED_LOG.defaultBlockState();
				if (state.hasProperty(RotatedPillarBlock.AXIS)) {
					charred = charred.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
				}
				level.setBlock(pos, charred, Block.UPDATE_ALL);
			} else if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
				level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
				return;
			} else if (!state.getFluidState().isEmpty() || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("ice")) {
				return;
			} else if (!state.ignitedByLava()) {
				return;
			}
		}
	}

	public BlockPos vent() {
		return vent;
	}

	/** How far it has got: the ticks of unrest and of the eruption proper (for tests). */
	public int unrestTicks() {
		return unrest;
	}

	public int eruptionTicks() {
		return plinian;
	}

	public int flowsLaunched() {
		return flowsLaunched;
	}

	public int lavaPlaced() {
		return lavaPlaced;
	}

	/** Players in the way get a warning when the flows start down the mountain. */
	private void warnFlows() {
		warn(ashReach, player -> Component.translatable("naturesmod.warning.pyroclastic"));
	}
}
