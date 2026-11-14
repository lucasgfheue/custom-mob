package com.creaturestudio.entity;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.function.BooleanSupplier;

/**
 * Envuelve un objetivo de IA y solo lo deja actuar cuando se cumple la condicion.
 * Asi una sola clase de entidad puede comportarse de forma distinta segun su definicion.
 */
public class ConditionalGoal extends Goal {
    private final Goal inner;
    private final BooleanSupplier condition;
    /** Condicion para seguir una vez empezado (puede ser mas suave que la de empezar). */
    private final BooleanSupplier keepGoing;

    public ConditionalGoal(Goal inner, BooleanSupplier condition) {
        this(inner, condition, condition);
    }

    public ConditionalGoal(Goal inner, BooleanSupplier condition, BooleanSupplier keepGoing) {
        this.inner = inner;
        this.condition = condition;
        this.keepGoing = keepGoing;
        this.setFlags(inner.getFlags());
    }

    @Override
    public boolean canUse() {
        return condition.getAsBoolean() && inner.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return keepGoing.getAsBoolean() && inner.canContinueToUse();
    }

    @Override
    public boolean isInterruptable() {
        return inner.isInterruptable();
    }

    @Override
    public void start() {
        inner.start();
    }

    @Override
    public void stop() {
        inner.stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return inner.requiresUpdateEveryTick();
    }

    @Override
    public void tick() {
        inner.tick();
    }
}
