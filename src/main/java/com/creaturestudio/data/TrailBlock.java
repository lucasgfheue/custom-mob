package com.creaturestudio.data;

import java.util.Locale;

/** Bloque que va dejando la criatura al caminar (como el golem de nieve). */
public enum TrailBlock {
    SNOW,
    FIRE,
    COBWEB,
    FLOWERS;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
