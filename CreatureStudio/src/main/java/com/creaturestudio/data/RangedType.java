package com.creaturestudio.data;

import java.util.Locale;

/** Proyectil que dispara la criatura (si no es NONE, ataca a distancia en vez de cuerpo a cuerpo). */
public enum RangedType {
    NONE,
    ARROW,
    SNOWBALL,
    FIREBALL,
    SPIT;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
