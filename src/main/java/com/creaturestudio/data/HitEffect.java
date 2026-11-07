package com.creaturestudio.data;

import java.util.Locale;

/** Efecto que aplica la criatura al golpear cuerpo a cuerpo. */
public enum HitEffect {
    NONE,
    POISON,
    WITHER,
    SLOWNESS,
    WEAKNESS,
    FIRE,
    LEVITATION,
    BLINDNESS,
    NAUSEA;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
