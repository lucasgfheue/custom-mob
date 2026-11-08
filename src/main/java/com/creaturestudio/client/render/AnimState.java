package com.creaturestudio.client.render;

import org.jetbrains.annotations.Nullable;

/**
 * Valores de animacion de un fotograma (iguales a los que Minecraft pasa a setupAnim), mas lo que necesitan las
 * animaciones propias.
 *
 * @param actionAges ticks desde que empezo cada accion de una vez (atacar, disparar...; indice = orden de
 *                   AnimAction), negativo si no esta pasando; null = ninguna
 * @param pinAction  editor: muestra solo la postura normal mas la pose pinPose de esta accion (-1 = no)
 */
public record AnimState(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                        float headPitch, float attackTime, boolean flying, @Nullable float[] actionAges,
                        int pinAction, int pinPose) {
    public static final AnimState REST = new AnimState(0f, 0f, 0f, 0f, 0f, 0f, false);

    public AnimState(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                     float attackTime, boolean flying) {
        this(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, attackTime, flying, null, -1, -1);
    }

    /** La postura normal mas una pose de una animacion propia (para editarla). */
    public static AnimState pinned(int action, int pose) {
        return new AnimState(0f, 0f, 0f, 0f, 0f, 0f, false, null, action, pose);
    }

    public AnimState withAges(@Nullable float[] ages) {
        return new AnimState(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, attackTime, flying, ages,
                pinAction, pinPose);
    }
}
