package com.naturesmod.worldgen;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.naturesmod.NaturesMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Two density functions the reworked End is shaped with: how far a spot lies from a point, and a function read at one
 * fixed height, which together give the distance from the middle of the world across the land. The game's own set has
 * neither in this version.
 */
public final class ModDensityFunctions {
	private ModDensityFunctions() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, NaturesMod.id("distance_to_point"), DistanceToPoint.CODEC.codec());
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, NaturesMod.id("slice"), Slice.CODEC.codec());
	}

	/** The straight-line distance in blocks from a point. */
	public record DistanceToPoint(double x, double y, double z) implements DensityFunction.SimpleFunction {
		private static final MapCodec<DistanceToPoint> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.STRING.optionalFieldOf("metric", "euclidean").forGetter(f -> "euclidean"),
				Codec.DOUBLE.listOf(3, 3).fieldOf("point").forGetter(f -> List.of(f.x, f.y, f.z))
		).apply(instance, (metric, point) -> new DistanceToPoint(point.get(0), point.get(1), point.get(2))));
		public static final KeyDispatchDataCodec<DistanceToPoint> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

		@Override
		public double compute(DensityFunction.FunctionContext context) {
			double dx = context.blockX() - x;
			double dy = context.blockY() - y;
			double dz = context.blockZ() - z;
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		@Override
		public double minValue() {
			return 0.0;
		}

		@Override
		public double maxValue() {
			return 1.0E8;
		}

		@Override
		public KeyDispatchDataCodec<? extends DensityFunction> codec() {
			return CODEC;
		}
	}

	/** A function read at one height, so it is the same all the way up a column. */
	public record Slice(DensityFunction input, int coordinate) implements DensityFunction {
		private static final MapCodec<Slice> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				DensityFunction.CODEC.fieldOf("input").forGetter(Slice::input),
				Codec.STRING.optionalFieldOf("axis", "y").forGetter(f -> "y"),
				Codec.INT.fieldOf("coordinate").forGetter(Slice::coordinate)
		).apply(instance, (input, axis, coordinate) -> new Slice(input, coordinate)));
		public static final KeyDispatchDataCodec<Slice> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

		@Override
		public double compute(DensityFunction.FunctionContext context) {
			return input.compute(new DensityFunction.SinglePointContext(context.blockX(), coordinate, context.blockZ()));
		}

		@Override
		public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
			contextProvider.fillAllDirectly(output, this);
		}

		@Override
		public DensityFunction mapChildren(DensityFunction.Visitor visitor) {
			return new Slice(visitor.apply(input), coordinate);
		}

		@Override
		public double minValue() {
			return input.minValue();
		}

		@Override
		public double maxValue() {
			return input.maxValue();
		}

		@Override
		public KeyDispatchDataCodec<? extends DensityFunction> codec() {
			return CODEC;
		}
	}
}
