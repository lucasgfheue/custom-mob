package com.creaturestudio.data;

import java.util.Locale;

/** Momento del dia en el que puede aparecer de forma natural. */
public enum SpawnTime {
    ANY,
    DAY,
    NIGHT;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
