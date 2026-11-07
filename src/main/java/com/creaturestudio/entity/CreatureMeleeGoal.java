package com.creaturestudio.entity;

import com.creaturestudio.data.CreatureDefinition;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Ataque cuerpo a cuerpo con la velocidad de golpe de la definicion (el alcance lo da la criatura). */
public class CreatureMeleeGoal extends MeleeAttackGoal {
    private final CustomCreature creature;
    private int cooldown;

    public CreatureMeleeGoal(CustomCreature creature, double speed) {
        super(creature, speed, true);
        this.creature = creature;
    }

    @Override
    public void start() {
        super.start();
        this.cooldown = 0;
    }

    @Override
    public void tick() {
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        super.tick();
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target) {
        if (this.cooldown > 0 || !this.mob.isWithinMeleeAttackRange(target)
                || !this.mob.getSensing().hasLineOfSight(target)) {
            return;
        }
        CreatureDefinition d = this.creature.def();
        this.cooldown = d == null ? 20 : d.meleeInterval;
        this.creature.lowerShield(12);
        this.mob.swing(InteractionHand.MAIN_HAND);
        this.mob.doHurtTarget(target);
    }
}
