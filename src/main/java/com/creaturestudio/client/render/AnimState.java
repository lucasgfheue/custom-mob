package com.creaturestudio.client.render;

/** Valores de animacion de un fotograma (iguales a los que Minecraft pasa a setupAnim). */
public record AnimState(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                        float headPitch, float attackTime, boolean flying) {
    public static final AnimState REST = new AnimState(0f, 0f, 0f, 0f, 0f, 0f, false);
}
