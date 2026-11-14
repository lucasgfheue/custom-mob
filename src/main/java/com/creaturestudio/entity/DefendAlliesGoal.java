package com.creaturestudio.entity;

import com.creaturestudio.data.CreatureDefinition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Facciones: "Defiende a sus aliados". Si alguien acaba de atacar a un aliado cercano (uno de su faccion, de su
 * especie o de su lista de aliados, tambien jugadores), va a por el, como el golem de hierro con los aldeanos.
 */
public class DefendAlliesGoal extends TargetGoal {
    private static final double RANGE = 16.0;

    private final CustomCreature creature;
    @Nullable
    private LivingEntity attacker;
    private int nextLook;

    public DefendAlliesGoal(CustomCreature mob) {
        super(mob, false);
        this.creature = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        CreatureDefinition d = creature.def();
        if (d == null || !d.defendAllies || (d.faction.isEmpty() && d.allies.isEmpty())
                || creature.tickCount < nextLook) {
            return false;
        }
        nextLook = creature.tickCount + 10;
        attacker = null;
        double best = Double.MAX_VALUE;
        TargetingConditions help = TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();
        for (LivingEntity ally : creature.level().getEntitiesOfClass(LivingEntity.class,
                creature.getBoundingBox().inflate(RANGE, 8.0, RANGE))) {
            if (ally == creature || !ally.isAlive() || !creature.isAllyOf(ally)) {
                continue;
            }
            LivingEntity a = ally.getLastHurtByMob(); // (se olvida solo a los 5 segundos)
            if (a == null || a == creature || !a.isAlive() || creature.isAllyOf(a) || !this.canAttack(a, help)) {
                continue;
            }
            double dist = creature.distanceToSqr(a);
            if (dist < best) {
                best = dist;
                attacker = a;
            }
        }
        return attacker != null;
    }

    @Override
    public void start() {
        creature.setTarget(attacker);
        this.targetMob = attacker;
        super.start();
    }
}
