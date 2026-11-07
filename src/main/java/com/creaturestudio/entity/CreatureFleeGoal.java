package com.creaturestudio.entity;

import com.creaturestudio.data.CreatureDefinition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Miedo (como el creeper con los gatos): huye de los mobs elegidos cuando los ve cerca.
 * Hace lo mismo que AvoidEntityGoal, pero usa siempre la navegacion actual del mob: la criatura
 * la cambia al aplicarse (caminar, volar, trepar) y AvoidEntityGoal se queda con la primera.
 */
class CreatureFleeGoal extends Goal {
    private static final double WALK = 1.0;
    private static final double SPRINT = 1.3;

    private final CustomCreature mob;
    @Nullable
    private LivingEntity toAvoid;
    @Nullable
    private Path path;

    CreatureFleeGoal(CustomCreature mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        CreatureDefinition d = mob.def();
        if (d == null || !d.abilities.fear) {
            return false;
        }
        double r = d.abilities.fearRange;
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class,
                mob.getBoundingBox().inflate(r, 3.0, r), e -> e.isAlive() && !e.isSpectator() && mob.fears(e))) {
            double dist = mob.distanceToSqr(e);
            if (dist < best && mob.hasLineOfSight(e)) {
                best = dist;
                nearest = e;
            }
        }
        if (nearest == null) {
            return false;
        }
        Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, nearest.position());
        if (away == null || nearest.distanceToSqr(away.x, away.y, away.z) < nearest.distanceToSqr(mob)) {
            return false;
        }
        Path p = mob.getNavigation().createPath(away.x, away.y, away.z, 0);
        if (p == null) {
            return false;
        }
        this.path = p;
        this.toAvoid = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(this.path, WALK);
    }

    @Override
    public void stop() {
        this.toAvoid = null;
        this.path = null;
    }

    @Override
    public void tick() {
        if (this.toAvoid != null) {
            mob.getNavigation().setSpeedModifier(mob.distanceToSqr(this.toAvoid) < 49.0 ? SPRINT : WALK);
        }
    }
}
