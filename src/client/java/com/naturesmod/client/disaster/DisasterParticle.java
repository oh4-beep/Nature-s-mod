package com.naturesmod.client.disaster;

import com.naturesmod.disaster.DisasterParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * The particle the disasters are drawn with: a soft billow of cloud or dust, a speck of debris, a
 * glowing spark or a flake of snow, set up for its job with a few calls and then added. It either
 * drifts (with its own velocity, drag and gravity) or turns about the axis of a vortex, following the
 * vortex as it moves: that is how a tornado's funnel spins and climbs as it crosses the land.
 */
public final class DisasterParticle extends SingleQuadParticle {
	static SpriteSet cloudSprites;
	static SpriteSet debrisSprites;
	static SpriteSet sparkSprites;
	static SpriteSet flakeSprites;

	/** The axis a particle turns about, at a height. */
	interface Axis {
		boolean alive();

		double x(double height);

		double z(double height);
	}

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
	private float startSize;
	private float growth;
	private float spin;
	private boolean glow;

	private DisasterParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
		super(level, x, y, z, sprite);
		this.hasPhysics = false;
		this.friction = 1.0F;
		this.gravity = 0.0F;
		this.xd = 0.0;
		this.yd = 0.0;
		this.zd = 0.0;
		this.alpha = 0.0F;
		this.startSize = this.quadSize;
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
		this.quadSize = size;
		this.startSize = size;
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

	/** Turns about an axis: starting `angle` round it at `radius`, turning `angular` radians a tick, rising `climb` and moving out `outward` blocks a tick. */
	DisasterParticle vortex(Axis axis, double angle, double radius, double angular, double climb, double outward) {
		this.axis = axis;
		this.angle = angle;
		this.orbit = radius;
		this.angular = angular;
		this.climb = climb;
		this.outward = outward;
		this.x = axis.x(this.y) + Math.cos(angle) * radius;
		this.z = axis.z(this.y) + Math.sin(angle) * radius;
		this.xo = this.x;
		this.zo = this.z;
		return this;
	}

	void spawn() {
		Fx.spawned++;
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
				this.y += this.climb;
				this.x = this.axis.x(this.y) + Math.cos(this.angle) * this.orbit;
				this.z = this.axis.z(this.y) + Math.sin(this.angle) * this.orbit;
			}
		} else {
			this.x += this.xd;
			this.y += this.yd;
			this.z += this.zd;
			this.xd *= this.drag;
			this.yd = this.yd * this.drag - this.fall;
			this.zd *= this.drag;
		}
		this.roll += this.spin;
		float t = (float) this.age / this.lifetime;
		float fade = t < this.fadeIn ? t / this.fadeIn : t > 1.0F - this.fadeOut ? (1.0F - t) / this.fadeOut : 1.0F;
		this.alpha = this.peakAlpha * Mth.clamp(fade, 0.0F, 1.0F);
		this.quadSize = this.startSize * (1.0F + this.growth * t);
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return this.glow ? 0xF000F0 : super.getLightCoords(partialTick);
	}
}
