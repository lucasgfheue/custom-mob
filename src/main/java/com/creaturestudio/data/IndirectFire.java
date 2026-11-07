package com.creaturestudio.data;

import java.util.Locale;

/** Disparo en arco (como un mortero) en vez de en linea recta. */
public enum IndirectFire {
    NO,       // siempre recto
    YES,      // siempre en arco (y puede disparar por encima de muros)
    DISTANT,  // en arco cuando el objetivo esta lejos
    HIDDEN;   // en arco cuando el objetivo se esconde detras de algo

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
