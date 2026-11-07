package com.creaturestudio.data;

import java.util.Locale;

/** Que hace la criatura con las puertas: nada, abrirlas (como un aldeano) o romperlas (como un zombi). */
public enum DoorMode {
    NONE,
    OPEN,
    BREAK;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
