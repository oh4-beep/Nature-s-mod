package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The particle the disasters are drawn with: a soft billow of cloud or dust, a speck of debris, a
 * glowing spark, a flake of snow or the streak of a shooting star, set up for its job with a few calls
 * and then added. It either drifts (with its own velocity, drag and gravity) or turns about the axis of
 * a vortex, following the vortex as it moves: that is how a tornado's funnel spins and climbs as it
 * crosses the land.
 *
 * <p>Disasters are seen from far off, further than the world is drawn: a tornado on the horizon, the
 * column of an eruption, the wall of a sandstorm. The game's fog swallows anything beyond its view
 * distance, so a particle further out than that is drawn nearer along the same line of sight, made
 * smaller in proportion: on screen it is the same, and the fog leaves it be. A little haze is laid
 * over it instead, more the further it really is.
 */
public final class DisasterParticle extends SingleQuadParticle {
	static SpriteSet cloudSprites;
	static SpriteSet debrisSprites;
	static SpriteSet sparkSprites;
	static SpriteSet flakeSprites;
	static SpriteSet streakSprites;

	/** The axis a particle turns about, at a height. */
	interface Axis {
		boolean alive();

		double x(double height);

		double z(double height);
	}

	/** Where it really is (its x, y and z are where it is drawn). */
	private double px;
	private double py;
	private double pz;
	private Axis axis;
	private double angle;
	private double orbit;
	private double angular;
	private double climb;
	private double outward;
	private double drag = 0.98;
	private double fall;
	private float peakAlpha = 1.0F;
	private float fadeIn = 0.12F;
	private float fadeOut = 0.35F;
	private float size;
	private float growth;
	private float spin;
	private boolean glow;
	/** Bounces off the ground (hailstones), so many times before it lies still. */
	private int bounces = -1;
	/** Drawn nearer than it is, past the view distance. */
	private boolean far;
	private float haze = 1.0F;

	private DisasterParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
		super(level, x, y, z, sprite);
		this.hasPhysics = false;
		this.friction = 1.0F;
		this.gravity = 0.0F;
		this.xd = 0.0;
		this.yd = 0.0;
		this.zd = 0.0;
		this.alpha = 0.0F;
		this.px = x;
		this.py = y;
		this.pz = z;
		this.size = this.quadSize;
		this.lifetime = 40;
	}

	static void registerProviders() {
		ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
		registry.register(DisasterParticles.CLOUD, sprites -> {
			cloudSprites = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> create(level, sprites, x, y, z).velocity(xd, yd, zd).size(1.5F).life(60);
		});
		registry.register(DisasterParticles.DEBRIS, sprites -> {
			debrisSprites = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> create(level, sprites, x, y, z).velocity(xd, yd, zd).size(0.3F).fall(0.04).life(60);
		});
		registry.register(DisasterParticles.SPARK, sprites -> {
			sparkSprites = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> create(level, sprites, x, y, z).velocity(xd, yd, zd).size(0.3F).glow().life(20);
		});
		registry.register(DisasterParticles.FLAKE, sprites -> {
			flakeSprites = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> create(level, sprites, x, y, z).velocity(xd, yd, zd).size(0.1F).life(30);
		});
		registry.register(DisasterParticles.STREAK, sprites -> {
			streakSprites = sprites;
			return (options, level, x, y, z, xd, yd, zd, random) -> create(level, sprites, x, y, z).velocity(xd, yd, zd).size(2.0F).glow().life(4);
		});
	}

	private static DisasterParticle create(ClientLevel level, SpriteSet sprites, double x, double y, double z) {
		return new DisasterParticle(level, x, y, z, sprites.get(level.getRandom()));
	}

	/** A particle of a kind (null before the textures have loaded, or with no world). */
	static DisasterParticle of(SpriteSet sprites, double x, double y, double z) {
		ClientLevel level = Minecraft.getInstance().level;
		return sprites == null || level == null ? null : create(level, sprites, x, y, z);
	}

	DisasterParticle velocity(double vx, double vy, double vz) {
		this.xd = vx;
		this.yd = vy;
		this.zd = vz;
		return this;
	}

	DisasterParticle size(float size) {
		this.size = size;
		return this;
	}

	DisasterParticle color(float r, float g, float b) {
		setColor(r, g, b);
		return this;
	}

	DisasterParticle gray(float shade) {
		setColor(shade, shade, shade);
		return this;
	}

	DisasterParticle alpha(float alpha) {
		this.peakAlpha = alpha;
		return this;
	}

	DisasterParticle life(int ticks) {
		this.lifetime = Math.max(1, ticks);
		return this;
	}

	DisasterParticle fade(float in, float out) {
		this.fadeIn = in;
		this.fadeOut = out;
		return this;
	}

	DisasterParticle drag(double drag) {
		this.drag = drag;
		return this;
	}

	DisasterParticle fall(double gravity) {
		this.fall = gravity;
		return this;
	}

	DisasterParticle grow(float growth) {
		this.growth = growth;
		return this;
	}

	DisasterParticle spin(float spin) {
		this.spin = spin;
		this.roll = this.random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		return this;
	}

	DisasterParticle glow() {
		this.glow = true;
		return this;
	}

	DisasterParticle bounce() {
		this.bounces = 0;
		return this;
	}

	/** Turns it on screen so that its texture's length lies along `direction`, as the viewer sees it: for streaks. */
	DisasterParticle along(Vec3 direction) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			double yaw = Math.toRadians(player.getYRot());
			double pitch = Math.toRadians(player.getXRot());
			// The viewer's right and up.
			double rx = -Math.cos(yaw);
			double rz = -Math.sin(yaw);
			double fx = -Math.sin(yaw) * Math.cos(pitch);
			double fy = -Math.sin(pitch);
			double fz = Math.cos(yaw) * Math.cos(pitch);
			double ux = -rz * fy;
			double uy = rz * fx - rx * fz;
			double uz = rx * fy;
			double sx = direction.x * rx + direction.z * rz;
			double sy = direction.x * ux + direction.y * uy + direction.z * uz;
			this.roll = (float) Math.atan2(sy, sx);
			this.oRoll = this.roll;
		}
		return this;
	}

	/** Turns about an axis: starting `angle` round it at `radius`, turning `angular` radians a tick, rising `climb` and moving out `outward` blocks a tick. */
	DisasterParticle vortex(Axis axis, double angle, double radius, double angular, double climb, double outward) {
		this.axis = axis;
		this.angle = angle;
		this.orbit = radius;
		this.angular = angular;
		this.climb = climb;
		this.outward = outward;
		this.px = axis.x(this.py) + Math.cos(angle) * radius;
		this.pz = axis.z(this.py) + Math.sin(angle) * radius;
		return this;
	}

	void spawn() {
		if (Fx.spawned >= Fx.cap * 2) {
			// Over budget: whatever wanted it shows a little less this tick.
			return;
		}
		Fx.spawned++;
		place();
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		Minecraft.getInstance().particleEngine.add(this);
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		this.oRoll = this.roll;
		if (this.age++ >= this.lifetime) {
			this.remove();
			return;
		}
		if (this.axis != null) {
			if (!this.axis.alive()) {
				this.axis = null;
				this.lifetime = Math.min(this.lifetime, this.age + 10);
			} else {
				this.angle += this.angular;
				this.orbit = Math.max(0.2, this.orbit + this.outward);
				this.py += this.climb;
				this.px = this.axis.x(this.py) + Math.cos(this.angle) * this.orbit;
				this.pz = this.axis.z(this.py) + Math.sin(this.angle) * this.orbit;
			}
		} else {
			this.px += this.xd;
			this.py += this.yd;
			this.pz += this.zd;
			this.xd *= this.drag;
			this.yd = this.yd * this.drag - this.fall;
			this.zd *= this.drag;
			if (this.bounces >= 0 && this.yd < 0.0) {
				land();
			}
		}
		this.roll += this.spin;
		float t = (float) this.age / this.lifetime;
		float fade = t < this.fadeIn ? t / this.fadeIn : t > 1.0F - this.fadeOut ? (1.0F - t) / this.fadeOut : 1.0F;
		place();
		this.alpha = this.peakAlpha * Mth.clamp(fade, 0.0F, 1.0F) * this.haze;
	}

	/** Hitting the ground it bounces, a little less each time, then lies there a moment. */
	private void land() {
		BlockPos at = BlockPos.containing(this.px, this.py, this.pz);
		BlockState state = this.level.getBlockState(at);
		VoxelShape shape = state.getCollisionShape(this.level, at);
		boolean wet = !state.getFluidState().isEmpty();
		if (shape.isEmpty() && !wet) {
			return;
		}
		if (wet || this.bounces >= 2) {
			// Into the water, or come to rest.
			this.py = wet ? this.py : at.getY() + shape.max(Direction.Axis.Y) + 0.02;
			this.xd = 0.0;
			this.yd = 0.0;
			this.zd = 0.0;
			this.fall = 0.0;
			this.bounces = -1;
			this.lifetime = Math.min(this.lifetime, this.age + (wet ? 1 : 8));
			return;
		}
		this.py = at.getY() + shape.max(Direction.Axis.Y) + 0.02;
		this.yd = -this.yd * (0.28 + this.random.nextFloat() * 0.12);
		this.xd = this.xd * 0.5 + (this.random.nextFloat() - 0.5) * 0.08;
		this.zd = this.zd * 0.5 + (this.random.nextFloat() - 0.5) * 0.08;
		this.bounces++;
	}

	/** Where it is drawn, and how big: where it is, or nearer in proportion if it is past the view distance. */
	private void place() {
		float t = (float) this.age / this.lifetime;
		float drawn = this.size * (1.0F + this.growth * t);
		LocalPlayer player = Minecraft.getInstance().player;
		double limit = Fx.viewLimit;
		if (player != null && limit > 0.0) {
			Vec3 eye = player.getEyePosition();
			double dx = this.px - eye.x;
			double dy = this.py - eye.y;
			double dz = this.pz - eye.z;
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d > limit) {
				double k = limit / d;
				this.x = eye.x + dx * k;
				this.y = eye.y + dy * k;
				this.z = eye.z + dz * k;
				this.quadSize = (float) (drawn * k);
				this.far = true;
				// The air between: further off, fainter.
				this.haze = (float) Math.max(0.55, 1.0 - (d - limit) / (limit * 12.0));
				return;
			}
		}
		this.x = this.px;
		this.y = this.py;
		this.z = this.pz;
		this.quadSize = drawn;
		this.far = false;
		this.haze = 1.0F;
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		if (this.glow) {
			return 0xF000F0;
		}
		// Far off it is out in the open sky (and maybe where the world isn't loaded): lit by the sky.
		return this.far ? 0xF00000 : super.getLightCoords(partialTick);
	}
}
