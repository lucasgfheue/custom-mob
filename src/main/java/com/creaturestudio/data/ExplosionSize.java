package com.creaturestudio.data;

import java.util.Locale;

/** Explosion del proyectil al chocar (la potencia es la de Level.explode: un creeper es 3). */
public enum ExplosionSize {
    NONE(0f),
    SMALL(1.2f),
    MEDIUM(2.2f),
    BIG(3.5f);

    public final float power;

    ExplosionSize(float power) {
        this.power = power;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
