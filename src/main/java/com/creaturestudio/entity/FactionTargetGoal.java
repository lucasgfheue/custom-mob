package com.creaturestudio.entity;

import com.creaturestudio.data.CreatureDefinition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import java.util.function.Predicate;

/**
 * Facciones: busca a sus enemigos (los de la lista "A quien ataca") dentro de su alcance de vision, igual que
 * {@link CreatureTargetGoal} con los jugadores. La version "a traves de paredes" tambien encuentra a los que no ve.
 * Nunca elige a un aliado (lo descarta {@link CustomCreature#isEnemy}).
 */
public class FactionTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private final boolean throughWalls;

    public FactionTargetGoal(CustomCreature mob, boolean throughWalls) {
        super(mob, LivingEntity.class, !throughWalls);
        this.throughWalls = throughWalls;
    }

    @Override
    protected double getFollowDistance() {
        // tambien se llama desde el constructor de la clase padre (cuando throughWalls aun es false)
        CreatureDefinition d = this.mob instanceof CustomCreature c ? c.def() : null;
        if (d == null) {
            return super.getFollowDistance();
        }
        double sight = this.throughWalls ? Math.max(d.visionRange, d.wallRange) : d.visionRange;
        return Math.max(sight, d.followRange);
    }

    @Override
    protected void findTarget() {
        if (!(this.mob instanceof CustomCreature creature)) {
            return;
        }
        CreatureDefinition d = creature.def();
        Predicate<LivingEntity> enemy = creature::isEnemy;
        double vision = d == null ? super.getFollowDistance() : d.visionRange;
        this.targetConditions = TargetingConditions.forCombat().range(vision).selector(enemy);
        super.findTarget();
        if (this.target == null && this.throughWalls && d != null) {
            this.targetConditions = TargetingConditions.forCombat().range(d.wallRange).ignoreLineOfSight().selector(enemy);
            super.findTarget();
        }
    }
}
