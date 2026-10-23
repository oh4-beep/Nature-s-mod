package com.naturesmod.worldgen.wild;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The Overworld's biomes, gone wild: vanilla's own biome source picks a biome as always, and then
 * a patch of it may turn into one of Nature's Mod's. Each new biome has its own noise field laid
 * over the world; where the field runs high over one of the biomes it grows out of, the new biome
 * takes that spot. A field covers a set share of its biomes, so lavender fields take about a third
 * of the plains, salt flats a third of the deserts. Caves have fields of their own, in three
 * dimensions and only between set depths, so crystal caverns open up deep under any land.
 *
 * <p>The fields are seeded from the world seed: every world has its new biomes in new places.
 */
public final class WildBiomeSource extends BiomeSource {
	public static final MapCodec<WildBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BiomeSource.CODEC.fieldOf("base").forGetter(source -> source.base),
			Variant.CODEC.listOf().fieldOf("variants").forGetter(source -> source.variants)
	).apply(instance, WildBiomeSource::new));

	/** How many points of each field are sampled to find the level it must clear to cover its share. */
	private static final int CALIBRATION_SAMPLES = 2048;

	private final BiomeSource base;
	private final List<Variant> variants;
	/** For each vanilla biome met so far, which variants may take its place, in order of precedence. */
	private final Map<Holder<Biome>, int[]> candidates = new ConcurrentHashMap<>();
	/** The noise fields of each world seed met so far. */
	private final Map<Long, Fields> fields = new ConcurrentHashMap<>();

	public WildBiomeSource(BiomeSource base, List<Variant> variants) {
		this.base = base;
		this.variants = List.copyOf(variants);
	}

	/** A new biome, the biomes it grows out of and how much of them it takes. */
	public record Variant(Holder<Biome> biome, HolderSet<Biome> replaces, int salt, double scale, double coverage, int minY, int maxY,
			double verticalScale, float minTemperature, float maxTemperature) {
		public static final Codec<Variant> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Biome.CODEC.fieldOf("biome").forGetter(Variant::biome),
				Biome.LIST_CODEC.fieldOf("replaces").forGetter(Variant::replaces),
				Codec.INT.fieldOf("salt").forGetter(Variant::salt),
				Codec.doubleRange(8.0, 20000.0).fieldOf("scale").forGetter(Variant::scale),
				Codec.doubleRange(0.0, 1.0).fieldOf("coverage").forGetter(Variant::coverage),
				Codec.INT.optionalFieldOf("min_y", Integer.MIN_VALUE).forGetter(Variant::minY),
				Codec.INT.optionalFieldOf("max_y", Integer.MAX_VALUE).forGetter(Variant::maxY),
				Codec.doubleRange(0.0, 20000.0).optionalFieldOf("vertical_scale", 0.0).forGetter(Variant::verticalScale),
				Codec.FLOAT.optionalFieldOf("min_temperature", -2.0F).forGetter(Variant::minTemperature),
				Codec.FLOAT.optionalFieldOf("max_temperature", 2.0F).forGetter(Variant::maxTemperature)
		).apply(instance, Variant::new));

		boolean caves() {
			return verticalScale > 0.0;
		}

		boolean picky() {
			return minTemperature > -2.0F || maxTemperature < 2.0F;
		}
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return Stream.concat(base.possibleBiomes().stream(), variants.stream().map(Variant::biome));
	}

	@Override
	public BiomeResolver createResolver(Climate.Sampler sampler) {
		return wild(base.createResolver(sampler), sampler);
	}

	@Override
	public BiomeResolver createResolverForChunk(Climate.Sampler sampler, int minQuartX, int minQuartY, int minQuartZ, int quartSizeX,
			int quartSizeY, int quartSizeZ) {
		// Vanilla samples the climate of the whole chunk in one go; the new biomes only look at the result.
		return wild(base.createResolverForChunk(sampler, minQuartX, minQuartY, minQuartZ, quartSizeX, quartSizeY, quartSizeZ), sampler);
	}

	@Override
	public void addDebugInfo(List<String> result, BlockPos feetPos, Climate.Sampler sampler) {
		base.addDebugInfo(result, feetPos, sampler);
	}

	private BiomeResolver wild(BiomeResolver vanilla, Climate.Sampler sampler) {
		Fields fields = this.fields.computeIfAbsent(WorldSeed.of(sampler), Fields::new);
		return (x, y, z) -> pick(vanilla.getNoiseBiome(x, y, z), x, y, z, sampler, fields);
	}

	private Holder<Biome> pick(Holder<Biome> biome, int x, int y, int z, Climate.Sampler sampler, Fields fields) {
		int[] options = candidates.get(biome);
		if (options == null) {
			options = candidates.computeIfAbsent(biome, this::candidatesFor);
		}
		if (options.length == 0) {
			return biome;
		}
		int blockY = QuartPos.toBlock(y);
		for (int i : options) {
			Variant variant = variants.get(i);
			if (blockY < variant.minY() || blockY > variant.maxY() || fields.value(i, x, y, z) < fields.threshold(i)) {
				continue;
			}
			if (variant.picky()) {
				long temperature = sampler.sample(x, y, z).temperature();
				if (temperature < Climate.quantizeCoord(variant.minTemperature()) || temperature > Climate.quantizeCoord(variant.maxTemperature())) {
					continue;
				}
			}
			return variant.biome();
		}
		return biome;
	}

	/** The variants that grow out of a biome, by key: holders of one biome need not be the same object. */
	private int[] candidatesFor(Holder<Biome> biome) {
		ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
		if (key == null) {
			return new int[0];
		}
		List<Integer> found = new ArrayList<>();
		for (int i = 0; i < variants.size(); i++) {
			for (Holder<Biome> replaced : variants.get(i).replaces()) {
				if (replaced.is(key)) {
					found.add(i);
					break;
				}
			}
		}
		return found.stream().mapToInt(Integer::intValue).toArray();
	}

	/** The noise fields of one world, and the level each must clear to cover its share of its biomes. */
	private final class Fields {
		private final SimplexNoise[] noises;
		private final double[] thresholds;

		Fields(long seed) {
			int count = variants.size();
			noises = new SimplexNoise[count];
			thresholds = new double[count];
			for (int i = 0; i < count; i++) {
				Variant variant = variants.get(i);
				// Variants of one biome share a salt, and so a field: the biome spreads across all its hosts alike.
				noises[i] = new SimplexNoise(RandomSource.create(seed ^ (0x5DEECE66DL * (variant.salt() + 1L))));
				thresholds[i] = calibrate(i, variant);
			}
		}

		private double calibrate(int i, Variant variant) {
			RandomSource random = RandomSource.create(1_000_003L * (variant.salt() + 7L));
			int low = variant.minY() == Integer.MIN_VALUE ? -64 : variant.minY();
			int high = variant.maxY() == Integer.MAX_VALUE ? 320 : variant.maxY();
			double[] samples = new double[CALIBRATION_SAMPLES];
			for (int k = 0; k < samples.length; k++) {
				int x = QuartPos.fromBlock(random.nextInt(60000) - 30000);
				int z = QuartPos.fromBlock(random.nextInt(60000) - 30000);
				int y = QuartPos.fromBlock(low + random.nextInt(Math.max(1, high - low + 1)));
				samples[k] = value(i, variant, x, y, z);
			}
			Arrays.sort(samples);
			int index = (int) Math.round((1.0 - variant.coverage()) * (samples.length - 1));
			return samples[Math.max(0, Math.min(samples.length - 1, index))];
		}

		double threshold(int i) {
			return thresholds[i];
		}

		double value(int i, int x, int y, int z) {
			return value(i, variants.get(i), x, y, z);
		}

		private double value(int i, Variant variant, int x, int y, int z) {
			SimplexNoise noise = noises[i];
			double nx = QuartPos.toBlock(x) / variant.scale();
			double nz = QuartPos.toBlock(z) / variant.scale();
			if (variant.caves()) {
				double ny = QuartPos.toBlock(y) / variant.verticalScale();
				return noise.get(nx, ny, nz) * 0.75 + noise.get(nx * 2.7 + 50.0, ny * 2.7, nz * 2.7 - 50.0) * 0.25;
			}
			// A second, finer octave frays the edges so the patches don't look like blobs.
			return noise.get(nx, nz) * 0.75 + noise.get(nx * 2.7 + 50.0, nz * 2.7 - 50.0) * 0.25;
		}
	}

	/** The seed of the world a climate sampler belongs to. */
	private static final class WorldSeed {
		private WorldSeed() {
		}

		static long of(Climate.Sampler sampler) {
			try {
				// Fabric's biome API hands every climate sampler the seed of its world.
				return ((net.fabricmc.fabric.impl.biome.MultiNoiseSamplerHooks) (Object) sampler).fabric_getSeed();
			} catch (Throwable missing) {
				return sampled(sampler);
			}
		}

		/** Without the hook: a hash of the climate at a few far-off spots, which only the seed decides. */
		private static long sampled(Climate.Sampler sampler) {
			long hash = 0x2545F4914F6CDD1DL;
			for (int i = 1; i <= 3; i++) {
				Climate.TargetPoint point = sampler.sample(QuartPos.fromBlock(1913 * i), 16, QuartPos.fromBlock(-7177 * i));
				for (long value : new long[] {point.temperature(), point.humidity(), point.continentalness(), point.erosion(), point.weirdness()}) {
					hash = (hash ^ value) * 0x9E3779B97F4A7C15L;
					hash ^= hash >>> 29;
				}
			}
			return hash;
		}
	}

	/** The new biomes a source can turn out, for tests and tools. */
	public Set<Holder<Biome>> wildBiomes() {
		Set<Holder<Biome>> out = new HashSet<>();
		for (Variant variant : variants) {
			out.add(variant.biome());
		}
		return out;
	}
}
