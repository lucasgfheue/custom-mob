package com.creaturestudio.entity;

import com.creaturestudio.data.CreatureDefinition;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

/**
 * Busca jugadores a los que atacar dentro del alcance de vision de la criatura. La version "a traves de paredes"
 * tambien encuentra a los que no ve, hasta su propio alcance. Una vez elegido, lo persigue hasta la distancia de
 * persecucion.
 */
public class CreatureTargetGoal extends NearestAttackableTargetGoal<Player> {
    private final boolean throughWalls;

    public CreatureTargetGoal(CustomCreature mob, boolean throughWalls) {
        super(mob, Player.class, !throughWalls);
        this.throughWalls = throughWalls;
    }

    @Override
    protected double getFollowDistance() {
        // tambien se llama desde el constructor de la clase padre (cuando throughWalls aun es false)
        CreatureDefinition d = this.mob instanceof CustomCreature c ? c.def() : null;
        if (d == null) {
            return super.getFollowDistance();
        }
        // distancia a la que deja de perseguir: la de persecucion, pero nunca menos de la de vision
        // (si no, veria a alguien y lo soltaria al momento)
        double sight = this.throughWalls ? Math.max(d.visionRange, d.wallRange) : d.visionRange;
        return Math.max(sight, d.followRange);
    }

    @Override
    protected void findTarget() {
        CreatureDefinition d = this.mob instanceof CustomCreature c ? c.def() : null;
        double vision = d == null ? super.getFollowDistance() : d.visionRange;
        this.targetConditions = TargetingConditions.forCombat().range(vision);
        super.findTarget();
        if (this.target == null && this.throughWalls && d != null) {
            this.targetConditions = TargetingConditions.forCombat().range(d.wallRange).ignoreLineOfSight();
            super.findTarget();
        }
    }
}
