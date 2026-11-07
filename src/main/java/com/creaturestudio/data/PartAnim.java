package com.creaturestudio.data;

import java.util.Locale;

/**
 * Animaciones predefinidas que se pueden asignar a cada pieza del modelo.
 * Las variantes A/B estan desfasadas media vuelta para que las extremidades se alternen.
 */
public enum PartAnim {
    NONE,
    HEAD,        // mira hacia donde mira la criatura (yaw + pitch)
    LEG_A,       // pierna: balanceo adelante/atras al caminar
    LEG_B,
    ARM_A,       // brazo: balanceo al caminar + golpe al atacar
    ARM_B,
    SIDE_LEG_A,  // pata lateral (arana/insecto): barrido horizontal
    SIDE_LEG_B,
    WING_A,      // ala del lado +X
    WING_B,      // ala del lado -X
    TAIL,        // cola: meneo lateral
    BOB,         // sube y baja suavemente (respiracion / slime)
    JAW,         // mandibula: se abre al atacar
    SPIN,        // gira sin parar sobre su eje Y
    SWAY;        // balanceo lateral del cuerpo al caminar

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Animacion equivalente para la copia espejada (eje X) de una pieza. */
    public PartAnim mirrored() {
        return switch (this) {
            case LEG_A -> LEG_B;
            case LEG_B -> LEG_A;
            case ARM_A -> ARM_B;
            case ARM_B -> ARM_A;
            case WING_A -> WING_B;
            case WING_B -> WING_A;
            // SIDE_LEG ya invierte su direccion al espejar la rotacion Y,
            // asi que mantener la letra produce una marcha alternada.
            default -> this;
        };
    }

    public PartAnim next() {
        PartAnim[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public PartAnim previous() {
        PartAnim[] v = values();
        return v[(ordinal() + v.length - 1) % v.length];
    }
}
