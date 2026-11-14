package com.creaturestudio.entity;

import com.creaturestudio.data.AnimAction;
import com.creaturestudio.data.CreatureDefinition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Come para curarse (pestana Habilidades): cuando le queda poca vida se para y se come la comida que lleva encima, o va
 * a por la que vea tirada cerca. Tarda como un jugador (1,6 segundos), con sus migas y su sonido, y se cura el doble de
 * lo que llena esa comida (como el lobo: un filete cura 16, ocho corazones).
 */
public class CreatureEatGoal extends Goal {
    private static final int EAT_TICKS = 32;
    private static final double SEARCH = 12.0;
    /** Ticks como mucho yendo a por una comida del suelo. */
    private static final int MAX_WALK = 200;

    private final CustomCreature mob;
    /** Comida del suelo a la que va (null = se come la suya). */
    @Nullable
    private ItemEntity food;
    private ItemStack eating = ItemStack.EMPTY;
    private ItemStack savedHand = ItemStack.EMPTY;
    /** Ticks que lleva comiendo (-1 = no esta comiendo). */
    private int eatTicks = -1;
    private int walkTicks;
    private int nextTry;

    public CreatureEatGoal(CustomCreature mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        CreatureDefinition d = mob.def();
        if (d == null || !d.abilities.eat || mob.tickCount < nextTry || !hungry(d)) {
            return false;
        }
        food = null;
        if (mob.foodLeft(d) > 0 && !CustomCreature.stackOf(d.abilities.eatItem).isEmpty()) {
            return true;
        }
        if (d.abilities.eatGround && mob.level() instanceof ServerLevel level
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            food = findFood();
            if (food != null) {
                return true;
            }
        }
        nextTry = mob.tickCount + 40; // no hay comida: vuelve a mirar dentro de 2 segundos
        return false;
    }

    private boolean hungry(CreatureDefinition d) {
        return mob.getHealth() < mob.getMaxHealth() && mob.getHealth() <= d.abilities.eatHearts * 2f;
    }

    @Override
    public void start() {
        walkTicks = 0;
        eatTicks = -1;
        CreatureDefinition d = mob.def();
        if (food != null) {
            mob.getNavigation().moveTo(food, 1.2);
        } else if (d != null) {
            mob.useFood();
            beginEating(CustomCreature.stackOf(d.abilities.eatItem));
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (!mob.isAlive()) {
            return false;
        }
        if (eatTicks >= 0) {
            return true; // si ya ha empezado a comer, termina
        }
        CreatureDefinition d = mob.def();
        return d != null && d.abilities.eat && hungry(d) && food != null && food.isAlive()
                && !food.getItem().isEmpty() && walkTicks < MAX_WALK;
    }

    @Override
    public void tick() {
        if (eatTicks >= 0) {
            tickEating();
            return;
        }
        if (food == null) {
            return;
        }
        walkTicks++;
        mob.getLookControl().setLookAt(food, 30.0f, 30.0f);
        if (walkTicks % 10 == 0) {
            mob.getNavigation().moveTo(food, 1.2);
        }
        double reach = mob.getBbWidth() * 0.5 + 1.2;
        if (mob.distanceToSqr(food) <= reach * reach) {
            ItemStack all = food.getItem();
            mob.take(food, 1);
            ItemStack one = all.split(1);
            if (all.isEmpty()) {
                food.discard();
            } else {
                food.setItem(all);
            }
            food = null;
            beginEating(one);
        }
    }

    @Override
    public void stop() {
        restoreHand();
        eatTicks = -1;
        food = null;
    }

    // ---------------------------------------------------------------------------------------

    @Nullable
    private ItemEntity findFood() {
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity e : mob.level().getEntitiesOfClass(ItemEntity.class,
                mob.getBoundingBox().inflate(SEARCH, 4.0, SEARCH))) {
            if (!e.isAlive() || e.hasPickUpDelay() || e.getItem().isEmpty() || e.getItem().get(DataComponents.FOOD) == null) {
                continue;
            }
            double dist = mob.distanceToSqr(e);
            if (dist < bestDist && mob.getSensing().hasLineOfSight(e)) {
                best = e;
                bestDist = dist;
            }
        }
        return best;
    }

    /** Se pone la comida en la mano y empieza a comer. */
    private void beginEating(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        eating = stack.copyWithCount(1);
        savedHand = mob.getItemBySlot(EquipmentSlot.MAINHAND).copy();
        mob.setItemSlot(EquipmentSlot.MAINHAND, eating.copy());
        mob.getNavigation().stop();
        eatTicks = 0;
        mob.playAction(AnimAction.ABILITY);
    }

    private void tickEating() {
        mob.getNavigation().stop();
        eatTicks++;
        if (eatTicks % 4 == 0) {
            crumbs();
        }
        if (eatTicks >= EAT_TICKS) {
            FoodProperties props = eating.get(DataComponents.FOOD);
            int nutrition = props != null ? Math.max(1, props.nutrition()) : 1;
            mob.heal(2f * nutrition);
            mob.playSoundId("entity.player.burp", 0.5f, mob.getRandom().nextFloat() * 0.1f + 0.9f);
            if (mob.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 2,
                        mob.getBbWidth() * 0.3, 0.2, mob.getBbWidth() * 0.3, 0.0);
            }
            eatTicks = -1;
            restoreHand();
            nextTry = mob.tickCount + 20;
        }
    }

    /** Migas y sonido de comer, delante de la boca. */
    private void crumbs() {
        if (mob.level() instanceof ServerLevel level && !eating.isEmpty()) {
            Vec3 look = mob.getViewVector(1.0f);
            double out = mob.getBbWidth() * 0.5 + 0.15;
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, eating), mob.getX() + look.x * out,
                    mob.getEyeY() - 0.15, mob.getZ() + look.z * out, 5, 0.08, 0.08, 0.08, 0.05);
        }
        mob.playSoundId("entity.generic.eat", 0.5f + 0.5f * mob.getRandom().nextInt(2),
                (mob.getRandom().nextFloat() - mob.getRandom().nextFloat()) * 0.2f + 1.0f);
    }

    private void restoreHand() {
        if (!eating.isEmpty()) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, savedHand);
        }
        eating = ItemStack.EMPTY;
        savedHand = ItemStack.EMPTY;
    }
}
