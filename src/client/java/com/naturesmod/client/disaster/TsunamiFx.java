package com.naturesmod.client.disaster;

import java.util.HashSet;
import java.util.Set;

import com.naturesmod.disaster.DisasterSounds;
import com.naturesmod.disaster.Tsunami;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Draws a tsunami. Before it comes the sea draws back from the shore, foam streaming out to sea over
 * the water, and a roar is heard building out at sea. Then it is seen coming: far off a pale line along
 * the horizon, nearer a long low swell rising as it nears the land into a wall of water, blue-green and
 * dark at its foot, lighter up its face, its crest curling over it in white foam and throwing spray
 * forward; it comes ashore with a boom and runs on up the land, churning white about its foot, with
 * wreckage tumbling in its face. Close to it the ground trembles and the air is misty with its spray.
 * Inside it the world is a few blocks of dark green-blue water, bubbles and wreckage rushing past and
 * everything shaking; behind its face the flood churns on inland, murky. After it has run its course
 * the water drains back to the sea, streaming over the ground. A flood wave over the land is the brown
 * of the mud it carries.
 *
 * <p>Few particles, and big ones: the wave's face is a handful of billows a tick along the stretch of
 * its front facing the viewer, fewer the further off it is; the spray, the foam and the wreckage only
 * near it, and what streams past the viewer (bubbles, the churning flood, the backwash) a modest number
 * about the eye, thinned with the rest on a slow computer.
 */
final class TsunamiFx {
	/** How far from the nearest point of its front it is drawn and heard at all. */
	private static final double FAR = 900.0;
	/** The sea: dark at the foot of the wave, lighter up its face, and the white of its foam. */
	private static final float[] SEA_DEEP = {0.1F, 0.3F, 0.36F};
	private static final float[] SEA_FACE = {0.24F, 0.5F, 0.56F};
	private static final float[] SEA_FOAM = {0.88F, 0.94F, 0.96F};
	/** A flood over the land, brown with mud. */
	private static final float[] MUD_DEEP = {0.3F, 0.26F, 0.18F};
	private static final float[] MUD_FACE = {0.46F, 0.4F, 0.3F};
	private static final float[] MUD_FOAM = {0.78F, 0.74F, 0.64F};
	/** The tsunamis whose landfall this client has heard (those gone, or of another world, are dropped). */
	private static final Set<ClientDisaster> LANDED = new HashSet<>();

	private TsunamiFx() {
	}

	static void tick(ClientDisaster d) {
		double length = Math.sqrt(d.dirX * d.dirX + d.dirZ * d.dirZ);
		if (!(length > 0.5)) {
			return;
		}
		double dx = d.dirX / length;
		double dz = d.dirZ / length;
		int phase = Math.round(d.extra(Tsunami.PHASE));
		boolean moving = phase == Tsunami.AT_SEA || phase == Tsunami.ASHORE;
		double speed = Math.max(0.0, Math.min(3.0, finite(d.extra(Tsunami.SPEED), 0.0F)));
		// Where its front is now: where it was when the server last said, and on since at the speed it was going.
		double since = Math.max(0.0, Math.min(4.0, d.age - finite(d.extra(Tsunami.SENT_AT), d.age)));
		double full = Math.max(1.0, finite(d.extra(Tsunami.FULL), 1.0F));
		double landfall = finite(d.extra(Tsunami.LANDFALL), 0.0F);
		double reach = Math.max(landfall, finite(d.extra(Tsunami.REACH), 0.0F));
		double front = Math.min(reach, finite(d.extra(Tsunami.FRONT), 0.0F) + (moving ? speed * since : 0.0));
		boolean inland = d.extra(Tsunami.INLAND) > 0.5F;
		double base = finite(d.extra(Tsunami.BASE), (float) d.y);
		double halfWidth = Math.max(8.0, finite(d.extra(Tsunami.HALF_WIDTH), 8.0F));
		double crest = Math.max(1.0, finite(d.extra(Tsunami.CREST), 3.0F));
		double flood = Math.max(crest + 1.0, finite(d.extra(Tsunami.FLOOD), 20.0F));
		double ex = Fx.eye.x - d.x;
		double ez = Fx.eye.z - d.z;
		double along = ex * dx + ez * dz;
		double across = -ex * dz + ez * dx;
		Shape wave = new Shape(d, dx, dz, front, speed, full, landfall, reach, inland, base, halfWidth, crest, flood);
		if (phase == Tsunami.BACKWASH) {
			backwash(wave, along, across, Math.max(0.0, Math.min(1.0, finite(d.extra(Tsunami.DRAINED), 1.0F))));
			return;
		}
		double h = Tsunami.waveHeight(front, full, landfall, reach, inland);
		double nearAcross = Math.max(-halfWidth, Math.min(halfWidth, across));
		double gapAlong = along - front;
		double gap = Math.sqrt(gapAlong * gapAlong + (across - nearAcross) * (across - nearAcross));
		if (!(gap < FAR)) {
			return;
		}
		double behind = front - along;
		boolean beside = Math.abs(across) <= halfWidth;
		double top = base + h;
		double floor = land(Fx.eye.x, Fx.eye.z, Fx.eye.y - 1.6);
		// Inside its face, or in the flood behind it (as the server has it: none where the land stands above the water).
		boolean inFace = beside && behind >= -1.0 && behind <= crest && top > floor && Fx.eye.y < top + 0.3;
		double floodTop = beside && behind > crest && behind <= flood
				? base + Tsunami.floodHeight(behind, Tsunami.waveHeight(along, full, landfall, reach, inland), crest, flood) : Double.NEGATIVE_INFINITY;
		boolean inFlood = !inFace && floodTop > floor && Fx.eye.y < floodTop;
		sound(d, gap, behind, beside, inFace);
		if (!inland && phase == Tsunami.ASHORE) {
			comeAshore(wave, nearAcross);
		}
		float[] dark = inland ? MUD_DEEP : SEA_DEEP;
		float[] murk = inland ? MUD_FACE : SEA_FACE;
		if (inFace) {
			DisasterClient.fog(0.97F, dark[0], dark[1], dark[2], 6.0F);
			CameraShake.shake(0.1 + 0.02 * d.intensity);
			tumble(wave);
		} else if (inFlood) {
			DisasterClient.fog(0.85F, murk[0], murk[1], murk[2], 14.0F);
		} else if (behind < 0.0 && gap < 120.0) {
			// Its spray hangs in the air before it, and the ground shakes as it comes.
			float mist = (float) (1.0 - gap / 120.0);
			DisasterClient.fog(0.3F * mist * mist * d.strength, 0.72F, 0.8F, 0.84F, (float) Math.max(40.0, gap * 1.5));
			CameraShake.shake(0.035 * mist * Math.min(1.0, h / 12.0));
		}
		if (h > 0.5) {
			face(wave, nearAcross, gap, h);
			spray(wave, nearAcross, gap, h);
			foot(wave, nearAcross, gap, h);
			if (gap < 90.0 && phase == Tsunami.ASHORE) {
				wreckage(wave, nearAcross, gap, h);
			}
		}
		if (beside && behind > crest - 4.0 && behind < flood + 10.0 && phase != Tsunami.DRAWING_BACK) {
			churn(wave);
		}
		if (!inland && phase != Tsunami.ASHORE && beside && behind < -20.0 && along > landfall - 60.0 && along < landfall + 40.0) {
			drawback(wave, d.strength);
		}
	}

	/** The wave as this client has it this tick: its way, its front, its height and the water it rides on. */
	private record Shape(ClientDisaster d, double dx, double dz, double front, double speed, double full, double landfall, double reach, boolean inland,
			double base, double halfWidth, double crest, double flood) {
		double x(double along, double across) {
			return d.x + dx * along - dz * across;
		}

		double z(double along, double across) {
			return d.z + dz * along + dx * across;
		}

		/** How high it stands (above its base) with its front `along` blocks along its way. */
		double height(double along) {
			return Tsunami.waveHeight(along, full, landfall, reach, inland);
		}
	}

	private static float finite(float value, float otherwise) {
		return Float.isFinite(value) ? value : otherwise;
	}

	/**
	 * The top of the land at (x, z) as this client has it: the first free block over it, water and roofs counting.
	 * Under the crowns of trees it isn't known from here, nor where this client hasn't the land loaded: `otherwise`.
	 */
	private static double land(double x, double z, double otherwise) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int top = Fx.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		if (top <= Fx.level.getMinY() + 1) {
			return otherwise;
		}
		if (Fx.level.getBlockState(new BlockPos(bx, top - 1, bz)).is(BlockTags.LEAVES)) {
			return otherwise;
		}
		return top;
	}

	/** The colour of the water `f` of the way up the wave (0 its foot, 1 its crest): dark below, foam at the top. */
	private static float[] water(double f, boolean inland) {
		float[] deep = inland ? MUD_DEEP : SEA_DEEP;
		float[] face = inland ? MUD_FACE : SEA_FACE;
		float[] foam = inland ? MUD_FOAM : SEA_FOAM;
		if (f < 0.75) {
			return mix(deep, face, f / 0.75);
		}
		return mix(face, foam, Math.min(1.0, (f - 0.75) / 0.25));
	}

	private static float[] mix(float[] a, float[] b, double t) {
		float k = (float) Math.max(0.0, Math.min(1.0, t));
		return new float[] {a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
	}

	/** No more than the particles left to spend this tick, all disasters together. */
	private static int share(int count) {
		return Math.max(0, Math.min(count, Fx.cap - Fx.spawned));
	}

	/** Its roar, all round: building as it comes, loudest in it, and dying away behind it. */
	private static void sound(ClientDisaster d, double gap, double behind, boolean beside, boolean inFace) {
		double near = Math.max(0.0, 1.0 - gap / FAR);
		float roar = (float) (Math.pow(near, 1.5) * Math.max(0.3F, d.strength));
		if (inFace) {
			roar = 1.0F;
		} else if (beside && behind > 0.0) {
			roar = Math.max(roar, (float) (0.85 * Math.max(0.0, 1.0 - behind / 160.0)));
		}
		DisasterAudio.loop("tsunami" + d.id, DisasterSounds.TSUNAMI_ROAR, false, Math.min(1.0F, roar), 0, 0, 0);
	}

	/** The boom of it coming ashore, heard (and felt) once, from the stretch of the shore nearest the viewer. */
	private static void comeAshore(Shape wave, double nearAcross) {
		if (!LANDED.isEmpty()) {
			LANDED.removeIf(known -> DisasterClient.get(known.id) != known);
		}
		if (!LANDED.add(wave.d())) {
			return;
		}
		double lx = wave.x(wave.landfall(), nearAcross);
		double lz = wave.z(wave.landfall(), nearAcross);
		double distance = Fx.distance(lx, wave.base(), lz);
		if (distance < 700.0) {
			DisasterAudio.distant(DisasterSounds.QUAKE_JOLT, lx, wave.base(), lz, 1.0F, 0.55F);
			CameraShake.jolt(0.07 * (1.0 - distance / 700.0));
		}
	}

	/**
	 * The face of the wave: big billows of water along the stretch of its front nearest the viewer, spread the wider
	 * the further off it is, from the ground (or the sea) up to its crest, which leans forward over the face; dark at
	 * its foot, lighter up it, white along the crest. They go with the wave.
	 */
	private static void face(Shape wave, double nearAcross, double gap, double h) {
		int count = Fx.budget(10, gap, FAR);
		if (count <= 0) {
			return;
		}
		double spread = Math.min(wave.halfWidth(), 0.5 * gap + 24.0);
		double top = wave.base() + h;
		// Bigger the further off: each looks much the same size from where it is seen.
		double size = Math.max(3.0, Math.min(10.0, h * 0.45)) * (1.0 + Math.min(3.0, gap / 220.0));
		double lean = Math.min(4.0, h * 0.22);
		double speed = wave.speed();
		for (int i = 0; i < count; i++) {
			double c = nearAcross + Fx.random.nextGaussian() * spread;
			if (c < -wave.halfWidth() || c > wave.halfWidth()) {
				continue;
			}
			double a = wave.front() + Fx.range(-1.2, 0.6);
			double px = wave.x(a, c);
			double pz = wave.z(a, c);
			double floor = Math.max(wave.base() - 12.0, land(px, pz, wave.base() - 1.0));
			if (floor > top - 0.8) {
				// The land stands above the wave here.
				continue;
			}
			double f = Math.pow(Fx.random.nextDouble(), 0.7);
			double curl = f * f * lean;
			float[] tone = water(f, wave.inland());
			Fx.cloud(px + wave.dx() * curl, floor + f * (top - floor), pz + wave.dz() * curl).velocity(wave.dx() * speed, 0.01 + 0.05 * f, wave.dz() * speed)
					.drag(1.0).size((float) (size * Fx.range(0.8, 1.2) * (0.8 + 0.4 * f))).color(tone[0], tone[1], tone[2]).alpha(f > 0.8 ? 0.92F : 0.85F)
					.life(16 + Fx.random.nextInt(14)).grow(0.25F).fade(0.2F, 0.35F).spin((float) Fx.range(-0.02, 0.02)).spawn();
		}
	}

	/** Spray thrown forward and up off its crest. */
	private static void spray(Shape wave, double nearAcross, double gap, double h) {
		int count = Fx.budget(4, gap, 320.0);
		double top = wave.base() + h;
		double lean = Math.min(4.0, h * 0.22);
		float[] foam = wave.inland() ? MUD_FOAM : SEA_FOAM;
		for (int i = 0; i < count; i++) {
			double c = nearAcross + Fx.random.nextGaussian() * (0.4 * gap + 16.0);
			if (c < -wave.halfWidth() || c > wave.halfWidth()) {
				continue;
			}
			double a = wave.front() + lean + Fx.range(-0.5, 1.5);
			double px = wave.x(a, c);
			double pz = wave.z(a, c);
			if (land(px, pz, wave.base() - 1.0) > top - 0.8) {
				continue;
			}
			double throwOut = wave.speed() + Fx.range(0.1, 0.35);
			Fx.cloud(px, top + Fx.range(-0.8, 0.6), pz).velocity(wave.dx() * throwOut, Fx.range(0.08, 0.22), wave.dz() * throwOut).drag(0.97).fall(0.012)
					.size((float) Fx.range(1.2, 2.6)).color(foam[0], foam[1], foam[2]).alpha(0.6F).life(18 + Fx.random.nextInt(12)).grow(0.6F)
					.fade(0.1F, 0.5F).spawn();
		}
	}

	/** White water churning about its foot, racing out before it. */
	private static void foot(Shape wave, double nearAcross, double gap, double h) {
		int count = Fx.budget(3, gap, 320.0);
		double top = wave.base() + h;
		float[] foam = wave.inland() ? MUD_FOAM : SEA_FOAM;
		for (int i = 0; i < count; i++) {
			double c = nearAcross + Fx.random.nextGaussian() * (0.4 * gap + 16.0);
			if (c < -wave.halfWidth() || c > wave.halfWidth()) {
				continue;
			}
			double a = wave.front() + Fx.range(-0.5, 2.5);
			double px = wave.x(a, c);
			double pz = wave.z(a, c);
			double floor = Math.max(wave.base() - 12.0, land(px, pz, wave.base() - 1.0));
			if (floor > top - 0.8) {
				continue;
			}
			double rush = wave.speed() * 1.15;
			Fx.cloud(px, floor + Fx.range(0.0, 1.5), pz).velocity(wave.dx() * rush, 0.03, wave.dz() * rush).drag(0.99).size((float) Fx.range(2.5, 5.0))
					.color(foam[0], foam[1], foam[2]).alpha(0.7F).life(14 + Fx.random.nextInt(9)).grow(0.5F).fade(0.15F, 0.4F).spawn();
		}
	}

	/** Wreckage tumbling in its face: leaves, branches, splinters. Only close by. */
	private static void wreckage(Shape wave, double nearAcross, double gap, double h) {
		int count = Fx.budget(3, gap, 90.0);
		double top = wave.base() + h;
		for (int i = 0; i < count; i++) {
			double c = nearAcross + Fx.random.nextGaussian() * 16.0;
			if (c < -wave.halfWidth() || c > wave.halfWidth()) {
				continue;
			}
			double a = wave.front() + Fx.range(-1.0, 0.5);
			double px = wave.x(a, c);
			double pz = wave.z(a, c);
			double floor = Math.max(wave.base() - 12.0, land(px, pz, wave.base() - 1.0));
			if (floor > top - 1.0) {
				continue;
			}
			DisasterParticle p = Fx.debris(px, floor + Fx.range(0.2, 0.6) * (top - floor), pz);
			if (Fx.random.nextInt(3) == 0) {
				float leaf = (float) Fx.range(0.3, 0.5);
				p.color(leaf * 0.55F, leaf, leaf * 0.35F);
			} else {
				float wood = (float) Fx.range(0.28, 0.42);
				p.color(wood, wood * 0.8F, wood * 0.6F);
			}
			p.velocity(wave.dx() * wave.speed() + Fx.range(-0.06, 0.06), Fx.range(-0.05, 0.12), wave.dz() * wave.speed() + Fx.range(-0.06, 0.06))
					.drag(0.99).fall(0.01).size((float) Fx.range(0.15, 0.35)).alpha(1.0F).life(15 + Fx.random.nextInt(10)).fade(0.05F, 0.2F)
					.spin((float) Fx.range(-0.4, 0.4)).spawn();
		}
	}

	/** Inside its face: bubbles and wreckage rushing past the eye. */
	private static void tumble(Shape wave) {
		float[] foam = wave.inland() ? MUD_FOAM : SEA_FOAM;
		double rush = wave.speed() + 0.2;
		int bubbles = share(Fx.round(6.0 * Fx.density));
		for (int i = 0; i < bubbles; i++) {
			Fx.cloud(Fx.eye.x + Fx.range(-4.0, 4.0) - wave.dx() * 2.0, Fx.eye.y + Fx.range(-2.5, 2.0), Fx.eye.z + Fx.range(-4.0, 4.0) - wave.dz() * 2.0)
					.velocity(wave.dx() * rush + Fx.range(-0.05, 0.05), Fx.range(0.04, 0.14), wave.dz() * rush + Fx.range(-0.05, 0.05)).drag(0.98)
					.size((float) Fx.range(0.5, 1.3)).color(foam[0], foam[1], foam[2]).alpha(0.5F).life(10 + Fx.random.nextInt(8)).fade(0.1F, 0.4F).spawn();
		}
		int specks = share(Fx.round(3.0 * Fx.density));
		for (int i = 0; i < specks; i++) {
			float wood = (float) Fx.range(0.25, 0.4);
			Fx.debris(Fx.eye.x + Fx.range(-3.0, 3.0) - wave.dx() * 3.0, Fx.eye.y + Fx.range(-2.0, 1.5), Fx.eye.z + Fx.range(-3.0, 3.0) - wave.dz() * 3.0)
					.velocity(wave.dx() * rush * 1.3, Fx.range(-0.05, 0.08), wave.dz() * rush * 1.3).drag(0.99).size((float) Fx.range(0.1, 0.25))
					.color(wood, wood * 0.85F, wood * 0.65F).alpha(1.0F).life(8 + Fx.random.nextInt(6)).spin((float) Fx.range(-0.5, 0.5)).spawn();
		}
	}

	/** The flood behind its face about the viewer: murky water churning on inland over the ground, lower and slower the further behind. */
	private static void churn(Shape wave) {
		int count = share(Fx.round(4.0 * Fx.density));
		for (int i = 0; i < count; i++) {
			double px = Fx.eye.x + Fx.range(-14.0, 14.0);
			double pz = Fx.eye.z + Fx.range(-14.0, 14.0);
			double ex = px - wave.d().x;
			double ez = pz - wave.d().z;
			double a = ex * wave.dx() + ez * wave.dz();
			double c = -ex * wave.dz() + ez * wave.dx();
			double behind = wave.front() - a;
			if (Math.abs(c) > wave.halfWidth() || behind <= wave.crest() || behind > wave.flood()) {
				continue;
			}
			double floor = land(px, pz, Double.NaN);
			double top = wave.base() + Tsunami.floodHeight(behind, wave.height(a), wave.crest(), wave.flood());
			if (!(top > floor + 0.3)) {
				continue;
			}
			double k = (behind - wave.crest()) / Math.max(1.0, wave.flood() - wave.crest());
			double current = wave.speed() * 0.65 * (1.0 - 0.7 * k);
			double f = Fx.random.nextDouble();
			float[] tone = water(0.2 + 0.6 * f, wave.inland());
			Fx.cloud(px, floor + f * (top - floor), pz).velocity(wave.dx() * current, Fx.range(0.0, 0.03), wave.dz() * current).drag(1.0)
					.size((float) Fx.range(2.0, 4.5)).color(tone[0], tone[1], tone[2]).alpha(0.4F).life(20 + Fx.random.nextInt(15)).grow(0.3F)
					.fade(0.2F, 0.4F).spawn();
		}
	}

	/** The sea drawing back from the shore before the wave: foam streaking out to sea over the water about the viewer. */
	private static void drawback(Shape wave, float strength) {
		int count = share(Fx.round(2.0 * Fx.density * strength));
		for (int i = 0; i < count; i++) {
			double px = Fx.eye.x + Fx.range(-18.0, 18.0);
			double pz = Fx.eye.z + Fx.range(-18.0, 18.0);
			int bx = (int) Math.floor(px);
			int bz = (int) Math.floor(pz);
			int top = Fx.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
			if (top <= Fx.level.getMinY() + 1 || Fx.level.getBlockState(new BlockPos(bx, top - 1, bz)).getFluidState().isEmpty()) {
				continue;
			}
			Fx.cloud(px, top + 0.05, pz).velocity(-wave.dx() * 0.12, 0.0, -wave.dz() * 0.12).drag(1.0).size((float) Fx.range(0.8, 1.6))
					.color(SEA_FOAM[0], SEA_FOAM[1], SEA_FOAM[2]).alpha(0.55F).life(30 + Fx.random.nextInt(12)).fade(0.2F, 0.4F).spawn();
		}
	}

	/** The water draining back to the sea: streaming over the ground about the viewer, if the wave got here; its roar dying away. */
	private static void backwash(Shape wave, double along, double across, double drained) {
		ClientDisaster d = wave.d();
		float left = (float) (1.0 - drained);
		double outAlong = along < wave.landfall() - 40.0 ? wave.landfall() - 40.0 - along : Math.max(0.0, along - wave.reach() - 2.0);
		double outAcross = Math.max(0.0, Math.abs(across) - wave.halfWidth());
		double gap = Math.sqrt(outAlong * outAlong + outAcross * outAcross);
		boolean zone = gap <= 0.0;
		float heard = zone ? 0.6F : (float) (0.3 * Math.max(0.0, 1.0 - gap / 400.0));
		DisasterAudio.loop("tsunami" + d.id, DisasterSounds.TSUNAMI_ROAR, false, heard * left * left, 0, 0, 0);
		if (!zone) {
			return;
		}
		double floor = land(Fx.eye.x, Fx.eye.z, Fx.eye.y - 1.6);
		if (floor >= wave.base() + wave.height(along)) {
			// The wave never got up here.
			return;
		}
		double depth = Tsunami.backwashDepth(wave.full(), drained);
		double v = Tsunami.backwashSpeed(d.intensity, drained);
		float[] murk = wave.inland() ? MUD_FACE : SEA_FACE;
		if (Fx.eye.y < floor + depth) {
			DisasterClient.fog(0.6F * left, murk[0], murk[1], murk[2], 18.0F);
		}
		if (Fx.eye.y - floor > depth + 6.0 || !(v > 0.01)) {
			return;
		}
		int count = share(Fx.round(7.0 * Fx.density * left));
		for (int i = 0; i < count; i++) {
			double px = Fx.eye.x + Fx.range(-12.0, 12.0) + wave.dx() * 6.0;
			double pz = Fx.eye.z + Fx.range(-12.0, 12.0) + wave.dz() * 6.0;
			double ground = land(px, pz, Double.NaN);
			if (!(ground < wave.base() + wave.height(along))) {
				continue;
			}
			double rush = v * Fx.range(1.2, 1.8);
			float[] tone = water(Fx.random.nextBoolean() ? 0.95 : 0.5, wave.inland());
			Fx.cloud(px, ground + Fx.range(0.1, Math.max(0.2, Math.min(depth, 1.2))), pz).velocity(-wave.dx() * rush, 0.0, -wave.dz() * rush).drag(1.0)
					.size((float) Fx.range(1.4, 3.0)).color(tone[0], tone[1], tone[2]).alpha(0.5F).life(16 + Fx.random.nextInt(10)).grow(0.4F)
					.fade(0.15F, 0.4F).spawn();
		}
	}
}
