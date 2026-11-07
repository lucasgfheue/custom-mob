package com.creaturestudio.entity;

import com.creaturestudio.data.HitEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** Efectos que dejan los golpes y los proyectiles de las criaturas. */
public final class HitEffects {
    private HitEffects() {
    }

    public static void apply(HitEffect effect, LivingEntity target, @Nullable Entity source) {
        switch (effect) {
            case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), source);
            case WITHER -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0), source);
            case SLOWNESS -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1), source);
            case WEAKNESS -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0), source);
            case FIRE -> target.igniteForSeconds(4.0f);
            case LEVITATION -> target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0), source);
            case BLINDNESS -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0), source);
            case NAUSEA -> target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0), source);
            case HUNGER -> target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0), source);
            case GLOWING -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), source);
            case DARKNESS -> target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0), source);
            default -> {
            }
        }
    }
}
