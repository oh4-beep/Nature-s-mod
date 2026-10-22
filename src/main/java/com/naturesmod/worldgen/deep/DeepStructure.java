package com.naturesmod.worldgen.deep;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import com.naturesmod.NaturesMod;
import com.naturesmod.worldgen.FeatureStats;
import com.naturesmod.worldgen.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * The forgotten riches on the floor of the Abyss: ruins, vaults and wonders, each of many kinds.
 * They are found at the very bottom, below the last of the guardians, on ground that is fairly
 * level; poorer ruins also stand on the summits and shoulders of the seamounts far above.
 */
public abstract class DeepStructure extends Structure {
	/** Only the true bottom of the Abyss: below the guardians, above the bedrock of the trenches. */
	public static final int MAX_FLOOR = 150;
	private static final int MIN_FLOOR = 18;
	/** The great structures of the Abyss, and how many chunks around them they keep clear. */
	private static final String[] GREAT_STRUCTURES = {"sunken_temple", "bathysphere", "leviathan_lair", "vent_shrine", "drowned_sanctum"};
	private static final int[] KEEP_CLEAR = {3, 1, 3, 2, 4};
	private final DeepKind.Tier tier;
	private final int minFloor;
	private final int maxFloor;

	protected DeepStructure(StructureSettings settings, DeepKind.Tier tier) {
		this(settings, tier, MIN_FLOOR, MAX_FLOOR);
	}

	protected DeepStructure(StructureSettings settings, DeepKind.Tier tier, int minFloor, int maxFloor) {
		super(settings);
		this.tier = tier;
		this.minFloor = minFloor;
		this.maxFloor = maxFloor;
	}

	/** Ruins up on the seamounts keep their chests' riches modest: the fortunes are at the bottom. */
	protected boolean highland() {
		return false;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		int floor = floor(context, x, z);
		if (floor > maxFloor || floor < minFloor) {
			return Optional.empty();
		}
		int r = tier.flatRadius();
		int low = floor;
		int high = floor;
		for (int[] offset : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}, {r, r}, {-r, -r}, {r, -r}, {-r, r}}) {
			int h = floor(context, x + offset[0], z + offset[1]);
			low = Math.min(low, h);
			high = Math.max(high, h);
		}
		if (high - low > tier.maxSlope()) {
			return Optional.empty();
		}
		if (nearGreatStructure(context)) {
			return Optional.empty();
		}
		WorldgenRandom random = context.random();
		DeepKind kind = DeepKind.pick(tier, random);
		Rotation rotation = Rotation.getRandom(random);
		long seed = random.nextLong();
		BlockPos center = new BlockPos(x, floor, z);
		boolean highland = highland();
		FeatureStats.placed((highland ? "deep/highland/" : "deep/") + kind.id(), center);
		return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new DeepPiece(kind, center, rotation, seed, highland))));
	}

	/**
	 * The great structures of the Abyss keep the floor around them to themselves: no treasure is
	 * built where one of them may stand within a few chunks, or it could wall up a temple's rift.
	 */
	private static boolean nearGreatStructure(GenerationContext context) {
		int chunkX = context.chunkPos().getMinBlockX() >> 4;
		int chunkZ = context.chunkPos().getMinBlockZ() >> 4;
		HolderGetter<StructureSet> sets = context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
		for (int i = 0; i < GREAT_STRUCTURES.length; i++) {
			Optional<StructureSet> set = sets.get(ResourceKey.create(Registries.STRUCTURE_SET, NaturesMod.id(GREAT_STRUCTURES[i])))
					.map(Holder::value);
			if (set.isEmpty() || !(set.get().placement() instanceof RandomSpreadStructurePlacement spread)) {
				continue;
			}
			int clear = KEEP_CLEAR[i];
			// The structure's own chunk in this region and the regions around: one of them is the nearest.
			for (int dx = -clear; dx <= clear; dx += clear) {
				for (int dz = -clear; dz <= clear; dz += clear) {
					ChunkPos potential = spread.getPotentialStructureChunk(context.seed(), chunkX + dx, chunkZ + dz);
					if (Math.abs((potential.getMinBlockX() >> 4) - chunkX) <= clear && Math.abs((potential.getMinBlockZ() >> 4) - chunkZ) <= clear) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Y of the top block of the sea floor. */
	private static int floor(GenerationContext context, int x, int z) {
		return context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState()) - 1;
	}

	@Override
	public StructureType<?> type() {
		return switch (tier) {
			case RUIN -> ModStructures.DEEP_RUIN;
			case VAULT -> ModStructures.DEEP_VAULT;
			case WONDER -> ModStructures.DEEP_WONDER;
		};
	}

	public static final class Ruin extends DeepStructure {
		public static final MapCodec<Ruin> CODEC = simpleCodec(Ruin::new);

		public Ruin(StructureSettings settings) {
			super(settings, DeepKind.Tier.RUIN);
		}
	}

	public static final class Vault extends DeepStructure {
		public static final MapCodec<Vault> CODEC = simpleCodec(Vault::new);

		public Vault(StructureSettings settings) {
			super(settings, DeepKind.Tier.VAULT);
		}
	}

	/** A ruin on a seamount, anywhere between the floor and the surface. */
	public static final class Highland extends DeepStructure {
		public static final MapCodec<Highland> CODEC = simpleCodec(Highland::new);

		public Highland(StructureSettings settings) {
			super(settings, DeepKind.Tier.RUIN, MAX_FLOOR + 1, 1030);
		}

		@Override
		protected boolean highland() {
			return true;
		}

		@Override
		public StructureType<?> type() {
			return ModStructures.DEEP_HIGHLAND;
		}
	}

	public static final class Wonder extends DeepStructure {
		public static final MapCodec<Wonder> CODEC = simpleCodec(Wonder::new);

		public Wonder(StructureSettings settings) {
			super(settings, DeepKind.Tier.WONDER);
		}
	}
}
