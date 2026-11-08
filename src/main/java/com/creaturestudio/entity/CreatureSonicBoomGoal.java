package com.creaturestudio.entity;

import com.creaturestudio.data.AnimAction;
import com.creaturestudio.data.CreatureDefinition;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Warden: carga un grito sonico y lo suelta en linea recta (atraviesa las paredes y los escudos). */
class CreatureSonicBoomGoal extends Goal {
    private static final int CHARGE = 34;
    private final CustomCreature mob;
    private int ticks;
    private boolean fired;

    CreatureSonicBoomGoal(CustomCreature mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        CreatureDefinition d = mob.def();
        LivingEntity t = mob.getTarget();
        if (d == null || !d.abilities.sonicBoom || mob.sonicCooldown > 0 || t == null || !t.isAlive()
                || !mob.canFightNow()) {
            return false;
        }
        float r = d.abilities.sonicRange;
        return mob.distanceToSqr(t) <= r * r;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = mob.getTarget();
        return !fired && t != null && t.isAlive() && ticks <= CHARGE;
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.fired = false;
        mob.getNavigation().stop();
        mob.playSoundId("entity.warden.sonic_charge", 3.0f, 1.0f);
        mob.playAction(AnimAction.ABILITY);
    }

    @Override
    public void stop() {
        CreatureDefinition d = mob.def();
        mob.sonicCooldown = fired && d != null ? d.abilities.sonicCooldown * 20 : 20;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        CreatureDefinition d = mob.def();
        if (t == null || d == null) {
            return;
        }
        mob.getLookControl().setLookAt(t, 30.0f, 30.0f);
        if (++ticks < CHARGE || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        fired = true;
        Vec3 from = mob.position().add(0.0, mob.getBbHeight() * 0.6, 0.0);
        Vec3 diff = t.getEyePosition().subtract(from);
        Vec3 dir = diff.normalize();
        int steps = Mth.floor(diff.length()) + 7;
        for (int i = 1; i < steps; i++) {
            Vec3 p = from.add(dir.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        mob.playSoundId("entity.warden.sonic_boom", 3.0f, 1.0f);
        if (t.hurt(mob.damageSources().sonicBoom(mob), d.abilities.sonicDamage)) {
            double resist = 1.0 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            t.push(dir.x * 2.5 * resist, dir.y * 0.5 * resist, dir.z * 2.5 * resist);
            t.hurtMarked = true;
        }
    }
}
