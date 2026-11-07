package com.creaturestudio.entity;

import com.creaturestudio.data.CreatureDefinition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/** Creeper: cuando alguien se le acerca se queda quieta y se hincha hasta explotar (si se aleja, se deshincha). */
class CreatureSwellGoal extends Goal {
    private final CustomCreature mob;
    @Nullable
    private LivingEntity trigger;

    CreatureSwellGoal(CustomCreature mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        CreatureDefinition d = mob.def();
        if (d == null || !d.abilities.explode || mob.explodeCooldown > 0) {
            return false;
        }
        return mob.getSwellDir() > 0 || mob.swellTrigger(d) != null;
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
        CreatureDefinition d = mob.def();
        this.trigger = d == null ? null : mob.swellTrigger(d);
    }

    @Override
    public void stop() {
        this.trigger = null;
        mob.setSwellDir(-1);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        CreatureDefinition d = mob.def();
        if (d == null) {
            mob.setSwellDir(-1);
            return;
        }
        float r = d.abilities.explodeRange + 4f;
        LivingEntity t = this.trigger;
        if (t == null || !t.isAlive() || mob.distanceToSqr(t) > r * r || !mob.getSensing().hasLineOfSight(t)) {
            mob.setSwellDir(-1);
            this.trigger = mob.swellTrigger(d);
        } else {
            mob.setSwellDir(1);
            mob.getLookControl().setLookAt(t, 30.0f, 30.0f);
        }
    }
}
