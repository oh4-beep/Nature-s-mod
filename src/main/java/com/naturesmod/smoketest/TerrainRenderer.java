package com.naturesmod.smoketest;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Renders generated terrain to images for the smoke test: a shaded top-down map and an oblique
 * "bird's eye" view. Images are written to {@code maps/} and also printed base64-encoded so they
 * can be recovered from CI logs.
 */
final class TerrainRenderer {
	private final ServerLevel level;
	private final int size;
	private final int minX;
	private final int minZ;
	private final int[] heights;
	private final int[] colors;

	TerrainRenderer(ServerLevel level, int minX, int minZ, int size, int[] heights) {
		this.level = level;
		this.minX = minX;
		this.minZ = minZ;
		this.size = size;
		this.heights = heights;
		this.colors = new int[size * size];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				int y = heights[x * size + z];
				BlockState state = level.getBlockState(pos.set(minX + x, y, minZ + z));
				// Look through the canopy so tree crowns show their leaf colour.
				BlockState above = topVisible(pos.set(minX + x, y, minZ + z));
				colors[x * size + z] = colorOf(above != null ? above : state, pos);
			}
		}
	}

	private BlockState topVisible(BlockPos.MutableBlockPos pos) {
		int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 1;
		BlockState state = level.getBlockState(pos.setY(top));
		return state.isAir() ? null : state;
	}

	private int colorOf(BlockState state, BlockPos pos) {
		if (!state.getFluidState().isEmpty()) {
			return 0x3A6FD8;
		}
		if (state.is(BlockTags.LEAVES)) {
			MapColor color = state.getMapColor(level, pos);
			return color.col == 0 ? 0x3C7A2A : darken(color.col, 0.85);
		}
		MapColor color = state.getMapColor(level, pos);
		return color.col == 0 ? 0x808080 : color.col;
	}

	private int height(int x, int z) {
		x = Math.max(0, Math.min(size - 1, x));
		z = Math.max(0, Math.min(size - 1, z));
		return heights[x * size + z];
	}

	/** Lambert shading with light from the north-west. */
	private double shade(int x, int z) {
		double dx = (height(x + 1, z) - height(x - 1, z)) / 2.0;
		double dz = (height(x, z + 1) - height(x, z - 1)) / 2.0;
		double nx = -dx;
		double ny = 1.0;
		double nz = -dz;
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		double lx = -0.5;
		double ly = 0.75;
		double lz = -0.45;
		double llen = Math.sqrt(lx * lx + ly * ly + lz * lz);
		double dot = (nx * lx + ny * ly + nz * lz) / (len * llen);
		return Math.max(0.35, Math.min(1.25, 0.25 + dot));
	}

	BufferedImage topDown() {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				image.setRGB(x, z, darken(colors[x * size + z], shade(x, z)));
			}
		}
		return image;
	}

	/**
	 * Ice thickness from above: how many blocks of snow and ice lie on top of each column. Snow
	 * cover is a block or two thick, glaciers far more; columns with blue ice are tinted cyan.
	 */
	BufferedImage iceMap() {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				int y = heights[x * size + z];
				int thickness = 0;
				boolean blue = false;
				for (int dy = 0; dy < 40; dy++) {
					BlockState state = level.getBlockState(pos.set(minX + x, y - dy, minZ + z));
					if (state.is(Blocks.BLUE_ICE)) {
						blue = true;
					} else if (!state.is(Blocks.PACKED_ICE) && !state.is(Blocks.SNOW_BLOCK)
							&& !state.is(Blocks.ICE) && !state.isAir()) {
						break;
					}
					thickness++;
				}
				int color;
				if (thickness == 0) {
					color = darken(colors[x * size + z], 0.45);
				} else {
					double t = Math.min(1, thickness / 14.0);
					color = mix(0xF4F8FF, blue ? 0x1E6FD9 : 0x5B6FD0, t);
				}
				image.setRGB(x, z, darken(color, shade(x, z)));
			}
		}
		return image;
	}

	/** A bird's-eye view looking north, drawn back to front. */
	BufferedImage oblique() {
		int minH = Integer.MAX_VALUE;
		int maxH = Integer.MIN_VALUE;
		for (int h : heights) {
			minH = Math.min(minH, h);
			maxH = Math.max(maxH, h);
		}
		int floor = Math.max(minH - 8, level.getMinY());
		double rowStep = 1.6;
		double heightStep = 0.9;
		int scale = 3;
		int width = size * scale;
		int imageHeight = (int) (size * rowStep + (maxH - floor) * heightStep) + 40;
		BufferedImage image = new BufferedImage(width, imageHeight, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < imageHeight; y++) {
			int sky = mix(0x9CC7FF, 0xDDEBFF, y / (double) imageHeight);
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, sky);
			}
		}
		double base = 20 + (maxH - floor) * heightStep;
		for (int z = 0; z < size; z++) {
			for (int x = 0; x < size; x++) {
				int h = heights[x * size + z];
				int top = (int) Math.round(base + z * rowStep - (h - floor) * heightStep);
				int bottom = (int) Math.round(base + z * rowStep + rowStep + 1);
				int color = darken(colors[x * size + z], shade(x, z));
				int side = darken(color, 0.7);
				for (int sx = x * scale; sx < x * scale + scale; sx++) {
					for (int sy = Math.max(0, top); sy < Math.min(imageHeight, bottom); sy++) {
						image.setRGB(sx, sy, sy - top < 2 ? color : side);
					}
				}
			}
		}
		return image;
	}

	static void emit(String name, BufferedImage image) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			ImageIO.write(image, "png", bytes);
			emit(name + ".png", bytes.toByteArray());
		} catch (IOException e) {
			System.out.println("[map-error " + name + "] " + e);
		}
	}

	/** Writes a file to {@code maps/} and prints it base64-encoded so it can be recovered from CI logs. */
	static void emit(String fileName, byte[] bytes) {
		try {
			Path dir = Path.of("maps");
			Files.createDirectories(dir);
			Files.write(dir.resolve(fileName), bytes);
		} catch (IOException e) {
			System.out.println("[map-error " + fileName + "] " + e);
		}
		String encoded = Base64.getEncoder().encodeToString(bytes);
		StringBuilder out = new StringBuilder();
		out.append("[map-begin ").append(fileName).append("]\n");
		for (int i = 0; i < encoded.length(); i += 3000) {
			out.append("[map] ").append(encoded, i, Math.min(encoded.length(), i + 3000)).append('\n');
		}
		out.append("[map-end ").append(fileName).append("]");
		System.out.println(out);
	}

	private static int darken(int rgb, double factor) {
		int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * factor);
		int g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * factor);
		int b = (int) Math.min(255, (rgb & 0xFF) * factor);
		return (r << 16) | (g << 8) | b;
	}

	private static int mix(int a, int b, double t) {
		int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
