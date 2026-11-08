package com.creaturestudio.data;

import java.util.Locale;

/**
 * Acciones que pueden tener una animacion propia hecha con poses (pestana Animar, "Mis animaciones").
 * Las de bucle se repiten mientras dura el estado; las demas se reproducen una vez cuando pasa algo:
 * salen de la postura que tenia la criatura, pasan por las poses y vuelven (morir se queda en la ultima).
 */
public enum AnimAction {
    /** Quieta (se mezcla con caminar segun lo rapido que va). */
    IDLE(true, 2.0f, 2),
    /** Caminando: va al ritmo de los pasos, no del reloj. */
    WALK(true, 0.5f, 2),
    /** Volando (solo las voladoras, en el aire): sustituye a quieta y caminar. */
    FLY(true, 0.6f, 2),
    /** Al golpear cuerpo a cuerpo. */
    ATTACK(false, 0.5f, 2),
    /** Al disparar. */
    SHOOT(false, 0.5f, 2),
    /** Al usar una habilidad: grito sonico, rayo, embestida, invocar o curarse. */
    ABILITY(false, 1.0f, 2),
    /** Al recibir dano. */
    HURT(false, 0.5f, 1),
    /** Al morir (en Minecraft el cuerpo desaparece al segundo). */
    DEATH(false, 1.0f, 1);

    public static final AnimAction[] ALL = values();
    public static final int COUNT = ALL.length;

    private final boolean loop;
    private final float seconds;
    private final int startPoses;

    AnimAction(boolean loop, float seconds, int startPoses) {
        this.loop = loop;
        this.seconds = seconds;
        this.startPoses = startPoses;
    }

    /** true = se repite mientras dura (quieta, caminar, volar). */
    public boolean loop() {
        return loop;
    }

    /** Segundos que dura a velocidad 1 (una vuelta del bucle o la animacion entera). */
    public float seconds() {
        return seconds;
    }

    /** Poses con las que empieza al crearla. */
    public int startPoses() {
        return startPoses;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AnimAction byKey(String key) {
        for (AnimAction a : ALL) {
            if (a.key().equals(key)) {
                return a;
            }
        }
        return null;
    }
}
