package com.creaturestudio.data;

import java.util.Locale;

/**
 * Ataque a distancia de la criatura: NONE = solo cuerpo a cuerpo. Los demas valores son plantillas que rellenan
 * las opciones del proyectil (ver {@link RangedPresets}); luego se pueden cambiar una a una.
 */
public enum RangedType {
    NONE,
    ARROW,
    SNOWBALL,
    FIREBALL,
    SPIT,
    TRIDENT,
    AXE,
    BOMB,
    MAGIC;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
