package com.creaturestudio.client.render;

import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.entity.CustomCreature;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;

/** Modelo de entidad que delega el dibujado en {@link CreatureModelRenderer}. */
public class CreatureEntityModel extends EntityModel<CustomCreature> {
    @Nullable
    private CreatureDefinition def;
    private AnimState anim = AnimState.REST;

    public CreatureEntityModel() {
        super(RenderType::entityCutoutNoCull);
    }

    @Override
    public void setupAnim(CustomCreature entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.def = entity.def();
        boolean flying = entity.isFlyer() && !entity.onGround();
        this.anim = new AnimState(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, this.attackTime, flying);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        poseStack.pushPose();
        // LivingEntityRenderer deja el espacio "al reves" (Y hacia abajo y 1.5 bloques arriba);
        // aqui se vuelve a un espacio en pixeles con Y hacia arriba y el suelo en 0.
        poseStack.translate(0.0f, 1.501f, 0.0f);
        poseStack.scale(1.0f / 16.0f, -1.0f / 16.0f, 1.0f / 16.0f);
        CreatureDefinition d = this.def;
        if (d != null && !d.parts.isEmpty()) {
            CreatureModelRenderer.render(d, poseStack.last(), buffer, packedLight, packedOverlay, color, this.anim);
        } else {
            CreatureModelRenderer.renderPlaceholder(poseStack.last(), buffer, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }
}
