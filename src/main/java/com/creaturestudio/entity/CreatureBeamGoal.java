package com.creaturestudio.entity;

import com.creaturestudio.data.AnimAction;
import com.creaturestudio.data.CreatureDefinition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Guardian: se queda quieta apuntando con un rayo que se carga y luego hace dano (si no pierde de vista al objetivo). */
class CreatureBeamGoal extends Goal {
    private static final int COOLDOWN = 40;
    private final CustomCreature mob;
    private int ticks;
    private boolean fired;

    CreatureBeamGoal(CustomCreature mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        CreatureDefinition d = mob.def();
        LivingEntity t = mob.getTarget();
        if (d == null || !d.abilities.beam || mob.beamCooldown > 0 || t == null || !t.isAlive()
                || !mob.canFightNow()) {
            return false;
        }
        float r = d.abilities.beamRange;
        return mob.distanceToSqr(t) <= r * r && mob.getSensing().hasLineOfSight(t);
    }

    @Override
    public boolean canContinueToUse() {
        CreatureDefinition d = mob.def();
        LivingEntity t = mob.getTarget();
        if (fired || d == null || t == null || !t.isAlive()) {
            return false;
        }
        float r = d.abilities.beamRange * 1.25f;
        return mob.distanceToSqr(t) <= r * r && mob.getSensing().hasLineOfSight(t);
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.fired = false;
        LivingEntity t = mob.getTarget();
        mob.getNavigation().stop();
        if (t != null) {
            mob.setBeamTarget(t.getId());
            mob.getLookControl().setLookAt(t, 90.0f, 90.0f);
        }
        mob.playSoundId("entity.guardian.attack", 1.0f, 1.0f);
        mob.playAction(AnimAction.ABILITY);
    }

    @Override
    public void stop() {
        mob.setBeamTarget(0);
        mob.beamCooldown = COOLDOWN;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        CreatureDefinition d = mob.def();
        LivingEntity t = mob.getTarget();
        if (d == null || t == null) {
            return;
        }
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(t, 90.0f, 90.0f);
        if (++ticks >= d.abilities.beamCharge) {
            fired = true;
            float before = CustomCreature.lifeOf(t);
            if (t.hurt(mob.damageSources().indirectMagic(mob, mob), d.abilities.beamDamage)) {
                mob.stealLife(t, before);
            }
        }
    }
}
