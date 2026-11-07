package com.creaturestudio.entity;

import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.IndirectFire;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Ataque a distancia configurable: alcance, espera entre disparos (minima y maxima), rafagas, quedarse quieto
 * apuntando o disparar sin dejar de moverse, y disparo en arco segun la opcion de "disparo indirecto".
 */
public class CreatureRangedGoal extends Goal {
    private final CustomCreature mob;
    @Nullable
    private LivingEntity target;
    private int cooldown = -1;
    private int seeTime;
    private int burstLeft;
    private int burstTimer;
    private boolean burstArc;
    private int repath;

    public CreatureRangedGoal(CustomCreature mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = this.mob.getTarget();
        CreatureDefinition d = this.mob.def();
        if (t == null || !t.isAlive() || d == null) {
            return false;
        }
        // Si esta muy cerca ataca cuerpo a cuerpo (lo hace el otro objetivo de IA)
        if (d.rangedMeleeRange > 0f && this.mob.distanceTo(t) <= d.rangedMeleeRange) {
            return false;
        }
        this.target = t;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = this.mob.getTarget();
        CreatureDefinition d = this.mob.def();
        if (t == null || !t.isAlive() || d == null) {
            return false;
        }
        return d.rangedMeleeRange <= 0f || this.mob.distanceTo(t) > d.rangedMeleeRange - 0.5f;
    }

    @Override
    public void start() {
        this.cooldown = -1;
        this.seeTime = 0;
        this.burstLeft = 0;
        this.repath = 0;
        this.mob.setAggressive(true);
    }

    @Override
    public void stop() {
        this.target = null;
        this.seeTime = 0;
        this.burstLeft = 0;
        this.cooldown = -1;
        this.mob.setAggressive(false);
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        CreatureDefinition d = this.mob.def();
        LivingEntity t = this.target;
        if (d == null || t == null) {
            return;
        }
        double dist = this.mob.distanceTo(t);
        boolean sees = this.mob.getSensing().hasLineOfSight(t);
        this.seeTime = sees ? Math.min(this.seeTime + 1, 100) : 0;
        boolean inRange = dist <= d.rangedRange;
        boolean blind = d.projGravity && (d.indirect == IndirectFire.YES || d.indirect == IndirectFire.HIDDEN);
        boolean canShoot = inRange && (sees || blind);

        // Moverse: quieto apuntando, o acercandose mientras dispara
        boolean stand = canShoot && d.aimWhileShooting && (this.seeTime >= 5 || !sees);
        if (stand) {
            this.mob.getNavigation().stop();
        } else if (--this.repath <= 0) {
            this.repath = 10;
            if (!canShoot || dist > d.rangedRange * 0.4) {
                this.mob.getNavigation().moveTo(t, 1.0);
            } else {
                this.mob.getNavigation().stop();
            }
        }
        this.mob.getLookControl().setLookAt(t, 30.0f, 30.0f);

        // Disparar: rafagas y espera entre rafagas
        if (this.burstLeft > 0) {
            if (--this.burstTimer <= 0) {
                this.mob.shootVolley(t, this.burstArc);
                this.burstLeft--;
                this.burstTimer = d.burstRate;
                if (this.burstLeft == 0) {
                    this.cooldown = delay(d);
                }
            }
            return;
        }
        if (this.cooldown < 0) {
            this.cooldown = Math.max(5, delay(d) / 2);
        }
        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }
        if (canShoot && (!sees || this.seeTime >= 3)) {
            this.burstArc = useArc(d, dist, sees);
            this.mob.shootVolley(t, this.burstArc);
            this.burstLeft = d.burstCount - 1;
            this.burstTimer = d.burstRate;
            if (this.burstLeft <= 0) {
                this.burstLeft = 0;
                this.cooldown = delay(d);
            }
        }
    }

    /** Si el siguiente disparo va en arco (solo los proyectiles con gravedad pueden). */
    static boolean useArc(CreatureDefinition d, double dist, boolean sees) {
        if (!d.projGravity) {
            return false;
        }
        return switch (d.indirect) {
            case YES -> true;
            case DISTANT -> dist > d.rangedRange * 0.5;
            case HIDDEN -> !sees;
            default -> false;
        };
    }

    private int delay(CreatureDefinition d) {
        int min = Math.max(1, d.rangedMinDelay);
        int max = Math.max(min, d.rangedMaxDelay);
        return min + this.mob.getRandom().nextInt(max - min + 1);
    }
}
