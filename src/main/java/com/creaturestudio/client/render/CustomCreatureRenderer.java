package com.creaturestudio.client.render;

import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.entity.CustomCreature;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Renderizador de las criaturas personalizadas (usa el atlas de bloques como textura). */
public class CustomCreatureRenderer extends MobRenderer<CustomCreature, CreatureEntityModel> {

    public CustomCreatureRenderer(EntityRendererProvider.Context context) {
        super(context, new CreatureEntityModel(), 0.5f);
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
}
