package com.creaturestudio.client.render;

import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.entity.CustomCreature;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** Modelo de entidad que delega el dibujado en {@link CreatureModelRenderer}. */
public class CreatureEntityModel extends EntityModel<CustomCreature> {
    @Nullable
    private CreatureDefinition def;
    private AnimState anim = AnimState.REST;
    /** Pose del ultimo dibujado (la usa la capa de pintura justo despues, para la misma entidad). */
    @Nullable
    private Matrix4f[] lastMats;
    @Nullable
    private CreatureDefinition lastDef;

    public CreatureEntityModel() {
        super(RenderType::entityCutoutNoCull);
    }

    @Override
    public void setupAnim(CustomCreature entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.def = entity.def();
        this.lastMats = null;
        this.lastDef = null;
        boolean flying = entity.isFlyer() && !entity.onGround();
        this.anim = new AnimState(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, this.attackTime, flying);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        poseStack.pushPose();
        toModelSpace(poseStack);
        CreatureDefinition d = this.def;
        if (d != null && !d.parts.isEmpty()) {
            Matrix4f[] mats = CreatureModelRenderer.pose(d, this.anim);
            this.lastMats = mats;
            this.lastDef = d;
            CreatureModelRenderer.renderPosed(d, mats, poseStack.last(), buffer, packedLight, packedOverlay, color);
        } else {
            CreatureModelRenderer.renderPlaceholder(poseStack.last(), buffer, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    /** Dibuja la pintura a mano con la misma pose que el ultimo {@link #renderToBuffer}. */
    public void renderPaint(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                            CreatureDefinition d, PaintAtlas atlas) {
        Matrix4f[] mats = this.lastMats;
        if (mats == null || this.lastDef != d) {
            return;
        }
        poseStack.pushPose();
        toModelSpace(poseStack);
        CreatureModelRenderer.renderPaint(d, mats, poseStack.last(), buffer, packedLight, packedOverlay, -1, atlas);
        poseStack.popPose();
    }

    /** Pose del ultimo dibujado de esta definicion (para las capas de pintura y de equipo), o null. */
    @Nullable
    public Matrix4f[] lastPose(@Nullable CreatureDefinition d) {
        return d != null && this.lastDef == d ? this.lastMats : null;
    }

    /**
     * LivingEntityRenderer deja el espacio "al reves" (Y hacia abajo y 1,5 bloques arriba);
     * aqui se vuelve a un espacio en pixeles con Y hacia arriba y el suelo en 0.
     */
    static void toModelSpace(PoseStack poseStack) {
        poseStack.translate(0.0f, 1.501f, 0.0f);
        poseStack.scale(1.0f / 16.0f, -1.0f / 16.0f, 1.0f / 16.0f);
    }
}
