package com.naturesmod.creature;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Goals and target rules shared by the sea creatures. */
final class CreatureGoals {
	private CreatureGoals() {
	}

	/**
	 * Divers worth attacking: players in the water within {@code reach} blocks, or further away
	 * when they are wounded. Sharks smell blood.
	 */
	static TargetingConditions.Selector diver(Mob hunter, double reach, boolean smellsBlood) {
		return (target, level) -> {
			if (!(target instanceof Player) || level.getDifficulty() == Difficulty.PEACEFUL || !target.isInWater()) {
				return false;
			}
			double distance = hunter.distanceToSqr(target);
			if (distance < reach * reach) {
				return true;
			}
			return smellsBlood && target.getHealth() < target.getMaxHealth() * 0.6F;
		};
	}

	/** Fish small enough to be eaten, for a predator that is hungry. */
	static TargetingConditions.Selector prey(Mob hunter, double reach, java.util.function.BooleanSupplier hungry) {
		return (target, level) -> {
			if (!(target instanceof AbstractFish) || !hungry.getAsBoolean() || target.getType() == hunter.getType()) {
				return false;
			}
			return target.getBbWidth() * 1.5F < hunter.getBbWidth() && hunter.distanceToSqr(target) < reach * reach;
		};
	}

	/** Anything bleeding in the water: what draws a piranha pack. */
	static TargetingConditions.Selector bleeding(Mob hunter) {
		return (target, level) -> {
			if (target == null || !target.isInWater() || target.getType() == hunter.getType() || target instanceof AbstractSchoolingFish) {
				return false;
			}
			if (target instanceof Player && level.getDifficulty() == Difficulty.PEACEFUL) {
				return false;
			}
			return target.getHealth() < target.getMaxHealth() * 0.75F || hunter.distanceToSqr(target) < 9.0;
		};
	}

	/** Free swimming, sticking close to the sea floor. */
	static final class BottomSwimGoal extends RandomSwimmingGoal {
		BottomSwimGoal(PathfinderMob mob, double speedModifier, int interval) {
			super(mob, speedModifier, interval);
		}

		@Override
		protected Vec3 getPosition() {
			Vec3 target = super.getPosition();
			if (target == null) {
				return null;
			}
			Level level = this.mob.level();
			BlockPos.MutableBlockPos pos = BlockPos.containing(target).mutable();
			for (int i = 0; i < 24; i++) {
				BlockPos below = pos.below();
				if (!level.getFluidState(below).is(FluidTags.WATER) || !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
					break;
				}
				pos.set(below);
			}
			return Vec3.atBottomCenterOf(pos).add(0.0, 0.25, 0.0);
		}
	}

	/** Random swimming that leaves the steering of followers to their school's leader. */
	static final class SchoolSwimGoal extends RandomSwimmingGoal {
		private final AbstractSchoolingFish fish;

		SchoolSwimGoal(AbstractSchoolingFish fish) {
			super(fish, 1.0, 40);
			this.fish = fish;
		}

		@Override
		public boolean canUse() {
			return !this.fish.isFollower() && super.canUse();
		}
	}

	/** An ambush predator slinks back to its hole when the hunt is over. */
	static final class LurkGoal extends Goal {
		private final LargeSwimmer mob;

		LurkGoal(LargeSwimmer mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			BlockPos home = this.mob.home();
			return home != null && this.mob.getTarget() == null && this.mob.distanceToSqr(Vec3.atCenterOf(home)) > 6.0;
		}

		@Override
		public void start() {
			BlockPos home = this.mob.home();
			this.mob.getNavigation().moveTo(home.getX() + 0.5, home.getY() + 0.5, home.getZ() + 0.5, 0.8);
		}

		@Override
		public boolean canContinueToUse() {
			return this.mob.getTarget() == null && !this.mob.getNavigation().isDone();
		}
	}

	static boolean isHurt(LivingEntity entity) {
		return entity.getHealth() < entity.getMaxHealth();
	}
}
