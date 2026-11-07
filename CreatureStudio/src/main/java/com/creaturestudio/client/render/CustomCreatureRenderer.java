package com.creaturestudio.client.render;

import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.entity.CustomCreature;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Renderizador de las criaturas personalizadas (usa el atlas de bloques como textura). */
public class CustomCreatureRenderer extends MobRenderer<CustomCreature, CreatureEntityModel> {

    public CustomCreatureRenderer(EntityRendererProvider.Context context) {
        super(context, new CreatureEntityModel(), 0.5f);
        this.addLayer(new PaintLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(CustomCreature entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    protected void scale(CustomCreature entity, PoseStack poseStack, float partialTickTime) {
        CreatureDefinition d = entity.def();
        if (d != null) {
            poseStack.scale(d.scale, d.scale, d.scale);
        }
    }

    @Override
    protected float getShadowRadius(CustomCreature entity) {
        return Mth.clamp(entity.getBbWidth() * 0.6f, 0.2f, 3.0f);
    }

    /** Capa con la pintura a mano (su propia textura dinamica). */
    private static final class PaintLayer extends RenderLayer<CustomCreature, CreatureEntityModel> {
        PaintLayer(RenderLayerParent<CustomCreature, CreatureEntityModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, CustomCreature entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            CreatureDefinition d = entity.def();
            PaintAtlas atlas = PaintCache.get(d);
            if (d == null || atlas == null || entity.isInvisible()) {
                return;
            }
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(atlas.location()));
            getParentModel().renderPaint(poseStack, vc, packedLight,
                    LivingEntityRenderer.getOverlayCoords(entity, 0.0f), d, atlas);
        }
    }
}
