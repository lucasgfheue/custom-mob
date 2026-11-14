package com.creaturestudio.entity;

import com.creaturestudio.data.AnimAction;
import com.creaturestudio.data.CreatureDefinition;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Devastador: corre en linea recta hacia su objetivo; si lo alcanza, le hace dano y lo lanza lejos. */
class CreatureChargeGoal extends Goal {
    private static final int MAX_TICKS = 25;
    private final CustomCreature mob;
    private Vec3 dir = Vec3.ZERO;
    private int ticks;
    private boolean hit;

    CreatureChargeGoal(CustomCreature mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        CreatureDefinition d = mob.def();
        LivingEntity t = mob.getTarget();
        if (d == null || !d.abilities.charge || mob.chargeCooldown > 0 || t == null || !t.isAlive()
                || !mob.onGround() || !mob.canFightNow()) {
            return false;
        }
        double dist = mob.distanceTo(t);
        return dist > 4.0 && dist < 20.0 && mob.getSensing().hasLineOfSight(t);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = mob.getTarget();
        return !hit && ticks < MAX_TICKS && t != null && t.isAlive() && !(ticks > 3 && mob.horizontalCollision);
    }

    @Override
    public void start() {
        LivingEntity t = mob.getTarget();
        this.ticks = 0;
        this.hit = false;
        if (t == null) {
            return;
        }
        Vec3 flat = new Vec3(t.getX() - mob.getX(), 0.0, t.getZ() - mob.getZ());
        this.dir = flat.lengthSqr() > 1.0e-6 ? flat.normalize() : Vec3.ZERO;
        mob.playAction(AnimAction.ABILITY);
        mob.getNavigation().stop();
        mob.playSoundId("entity.ravager.roar", 1.0f, 1.0f);
    }

    @Override
    public void stop() {
        CreatureDefinition d = mob.def();
        mob.chargeCooldown = d == null ? 100 : d.abilities.chargeCooldown * 20;
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(v.x * 0.2, v.y, v.z * 0.2);
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
        ticks++;
        float speed = d.abilities.chargeSpeed;
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0f;
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.yHeadRot = yaw;
        if (ticks % 2 == 0 && mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.1, mob.getZ(), 2, 0.3, 0.05, 0.3, 0.01);
        }
        if (mob.getBoundingBox().inflate(0.4).intersects(t.getBoundingBox())) {
            hit = true;
            float before = CustomCreature.lifeOf(t);
            if (t.hurt(mob.damageSources().mobAttack(mob), d.abilities.chargeDamage)) {
                mob.stealLife(t, before);
                t.knockback(1.6, -dir.x, -dir.z);
                t.hurtMarked = true;
            }
            mob.playSoundId("entity.ravager.attack", 1.0f, 1.0f);
        }
    }
}
