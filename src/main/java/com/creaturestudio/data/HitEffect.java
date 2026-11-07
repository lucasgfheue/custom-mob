package com.creaturestudio.data;

import java.util.Locale;

/** Efecto que aplica la criatura al golpear cuerpo a cuerpo o con su proyectil. */
public enum HitEffect {
    NONE,
    POISON,
    WITHER,
    SLOWNESS,
    WEAKNESS,
    FIRE,
    LEVITATION,
    BLINDNESS,
    NAUSEA,
    HUNGER,
    GLOWING,
    DARKNESS,
    MINING_FATIGUE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
