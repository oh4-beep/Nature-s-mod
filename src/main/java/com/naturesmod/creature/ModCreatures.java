package com.naturesmod.creature;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import com.naturesmod.NaturesMod;

/**
 * Registers the creatures of the sea, of the End and of the Overworld's lands and skies: their entity types, attributes,
 * spawn rules and where they live.
 */
public final class ModCreatures {
	private static final Map<EntityType<?>, SpeciesData> SPECIES = new IdentityHashMap<>();
	private static final Map<String, EntityType<? extends Mob>> TYPES = new LinkedHashMap<>();
	private static final Map<String, Item> BUCKETS = new LinkedHashMap<>();

	private ModCreatures() {
	}

	public static void register() {
		for (SpeciesData species : CreatureSpecies.ALL) {
			EntityType<? extends Mob> type = switch (species.kind()) {
				case SMALL -> register(species, SmallFish::new);
				case SCHOOL -> register(species, SchoolingFish::new);
				case LARGE -> register(species, LargeSwimmer::new);
				case JELLY -> register(species, Jellyfish::new);
				case FLYER -> species.behavior() == Behavior.BOSS ? register(species, VoidWyrm::new)
						: "skyray".equals(species.id()) ? register(species, Skyray::new) : register(species, EndFlyer::new);
				case WALKER -> species.rideable() ? register(species, Mount::new) : register(species, EndWalker::new);
			};
			// The End's creatures are brought out by the End's own life spawner (see EndLifeSpawner); the rest spawn as
			// animals and monsters do.
			if (species.inWater() || species.overworld()) {
				addSpawns(species, type);
			}
		}
	}

	private static <T extends Mob> EntityType<T> register(SpeciesData species, EntityType.EntityFactory<T> factory) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, NaturesMod.id(species.id()));
		EntityType.Builder<T> builder = EntityType.Builder.of(factory, species.mobCategory())
				.sized(species.width(), species.height())
				.eyeHeight(species.height() * 0.5F)
				.clientTrackingRange(species.scale() >= 2.0F ? 10 : 6);
		if (species.rideable()) {
			builder.passengerAttachments(species.seat());
		}
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
		SPECIES.put(type, species);
		TYPES.put(species.id(), type);
		FabricDefaultAttributeRegistry.register(type, attributes(species));
		if (species.inWater()) {
			SpawnPlacements.register(type, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					(entityType, level, reason, pos, random) -> canSpawn(species, level, pos, random));
		} else if (species.overworld()) {
			SpawnPlacements.register(type, species.walks() ? SpawnPlacementTypes.ON_GROUND : SpawnPlacementTypes.NO_RESTRICTIONS,
					Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (entityType, level, reason, pos, random) -> canSpawnOnLand(species, level, reason, pos, random));
		} else {
			SpawnPlacements.register(type, species.walks() ? SpawnPlacementTypes.ON_GROUND : SpawnPlacementTypes.NO_RESTRICTIONS,
					Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (entityType, level, reason, pos, random) -> true);
		}
		return type;
	}

	private static AttributeSupplier.Builder attributes(SpeciesData species) {
		double speed = switch (species.kind()) {
			// Plain fish swim with the vanilla fish controls, the big ones with the smooth ones.
			case SMALL, SCHOOL -> 0.7 * species.speed();
			case LARGE -> 0.6 * species.speed();
			case JELLY -> species.speed();
			// On foot: a little slower than a zombie at speed 1.
			case FLYER, WALKER -> 0.22 * species.speed();
		};
		AttributeSupplier.Builder builder = Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, species.health())
				.add(Attributes.MOVEMENT_SPEED, speed)
				.add(Attributes.ATTACK_DAMAGE, Math.max(1.0, species.attack()))
				.add(Attributes.FOLLOW_RANGE, species.behavior() == Behavior.BOSS ? 64.0 : species.behavior().isHostile() ? 32.0 : 16.0);
		if (species.flies()) {
			builder.add(Attributes.FLYING_SPEED, (species.isJelly() ? 0.06 : 0.11) * species.speed());
		}
		if (species.behavior() == Behavior.BOSS) {
			builder.add(Attributes.ARMOR, 12.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
		} else if (species.scale() >= 1.5F) {
			builder.add(Attributes.KNOCKBACK_RESISTANCE, Math.min(0.8, species.scale() * 0.25));
		}
		if (species.walks() && species.width() >= 1.2F) {
			// The big beasts stride up a block without a jump, with or without a rider.
			builder.add(Attributes.STEP_HEIGHT, 1.0);
		}
		if (species.walks() && species.attack() >= 10.0) {
			// A charging rhino or a gorilla's fist sends you flying.
			builder.add(Attributes.ATTACK_KNOCKBACK, 1.5);
		}
		return builder;
	}

	/** Each species keeps to its part of the water column. */
	static boolean canSpawn(SpeciesData species, LevelAccessor level, BlockPos pos, RandomSource random) {
		if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getBlockState(pos.above()).is(Blocks.WATER)) {
			return false;
		}
		int seaLevel = level.getSeaLevel();
		return switch (species.depth()) {
			case "surface" -> pos.getY() >= seaLevel - 13 && pos.getY() <= seaLevel;
			case "floor" -> nearFloor(level, pos, 4);
			case "deep" -> pos.getY() < seaLevel - 24;
			default -> true;
		};
	}

	/**
	 * The Overworld's beasts and birds: out under the open sky, on the ground (or over it) and never in water. Its
	 * hunters only come out in the dark, as monsters do; the rest in daylight, as animals do.
	 */
	static boolean canSpawnOnLand(SpeciesData species, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return true;
		}
		if (!level.canSeeSky(pos) || !level.getFluidState(pos).isEmpty()) {
			return false;
		}
		if (species.mobCategory() == MobCategory.MONSTER) {
			return level.getDifficulty() != Difficulty.PEACEFUL && Monster.isDarkEnoughToSpawn(level, pos, random);
		}
		return species.flies() || level.getRawBrightness(pos, 0) > 8;
	}

	private static boolean nearFloor(LevelAccessor level, BlockPos pos, int reach) {
		BlockPos.MutableBlockPos cursor = pos.mutable();
		for (int i = 0; i < reach; i++) {
			cursor.move(0, -1, 0);
			if (!level.getFluidState(cursor).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** Natural spawns in every biome the species lives in: the Overworld's seas and lands, and the Abyss. */
	private static void addSpawns(SpeciesData species, EntityType<?> type) {
		List<ResourceKey<Biome>> biomes = new ArrayList<>();
		for (String habitat : species.habitats()) {
			int colon = habitat.indexOf(':');
			Identifier id = colon < 0 ? Identifier.withDefaultNamespace(habitat)
					: Identifier.fromNamespaceAndPath(habitat.substring(0, colon), habitat.substring(colon + 1));
			biomes.add(ResourceKey.create(Registries.BIOME, id));
		}
		if (!biomes.isEmpty()) {
			BiomeModifications.addSpawn(BiomeSelectors.includeByKey(biomes), species.mobCategory(), type,
					species.weight(), species.groupMin(), species.groupMax());
		}
	}

	public static SpeciesData species(EntityType<?> type) {
		SpeciesData species = SPECIES.get(type);
		if (species == null) {
			throw new IllegalStateException("Not a Nature's Mod sea creature: " + type);
		}
		return species;
	}

	public static EntityType<? extends Mob> type(String id) {
		return TYPES.get(id);
	}

	public static Map<String, EntityType<? extends Mob>> types() {
		return TYPES;
	}

	public static void registerBucket(String species, Item bucket) {
		BUCKETS.put(species, bucket);
	}

	public static ItemStack bucket(SpeciesData species) {
		Item bucket = BUCKETS.get(species.id());
		return new ItemStack(bucket != null ? bucket : Items.WATER_BUCKET);
	}
}
