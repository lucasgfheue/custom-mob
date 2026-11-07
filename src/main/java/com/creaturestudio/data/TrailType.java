package com.creaturestudio.data;

import java.util.Locale;

/** Estela de particulas que deja el proyectil (el cliente elige la particula de Minecraft). */
public enum TrailType {
    NONE,
    SMOKE,
    LARGE_SMOKE,
    FLAME,
    SOUL_FLAME,
    PORTAL,
    LIGHTNING,
    CRIT,
    MAGIC_CRIT,
    WITCH,
    ENCHANT,
    HEART,
    SNOWFLAKE,
    END_ROD,
    CLOUD,
    NOTE,
    DRAGON_BREATH,
    GLOW,
    ASH,
    CHERRY,
    LAVA,
    SPIT;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
