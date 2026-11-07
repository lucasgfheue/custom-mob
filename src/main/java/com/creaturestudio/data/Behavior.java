package com.creaturestudio.data;

import java.util.Locale;

/** Actitud de la criatura hacia los jugadores. */
public enum Behavior {
    PASSIVE,  // nunca ataca, huye al ser golpeada
    NEUTRAL,  // ataca solo si la golpean
    HOSTILE;  // ataca a los jugadores que ve

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
