package com.creaturestudio.data;

import java.util.Locale;

/** Forma de desplazarse de la criatura. */
public enum Movement {
    WALK,
    FLY;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
