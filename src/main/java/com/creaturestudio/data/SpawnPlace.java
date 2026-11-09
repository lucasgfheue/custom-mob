package com.creaturestudio.data;

import java.util.Locale;

/** Donde busca sitio la aparicion natural (si no se eligieron estructuras). */
public enum SpawnPlace {
    /** Encima del terreno, a cielo abierto (las que vuelan, un poco por encima). */
    SURFACE,
    /** Bajo tierra: cuevas y huecos sin cielo encima (en el Nether, cualquier hueco). */
    UNDERGROUND,
    /** Dentro del agua: rios, lagos y mares. */
    WATER,
    /** En el aire, bastante por encima del suelo (solo las que vuelan; las demas, en la superficie). */
    SKY;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SpawnPlace byKey(String key, SpawnPlace fallback) {
        for (SpawnPlace p : values()) {
            if (p.key().equalsIgnoreCase(key) || p.name().equalsIgnoreCase(key)) {
                return p;
            }
        }
        return fallback;
    }
}
