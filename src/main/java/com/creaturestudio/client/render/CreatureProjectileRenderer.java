package com.creaturestudio.client.render;

import com.creaturestudio.entity.CreatureProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Dibuja el proyectil de las criaturas con el objeto que lleva: en 3D apunta hacia donde vuela (y puede dar vueltas
 * como un hacha lanzada); en 2D es un dibujo plano que siempre mira a la camara, como una bola de nieve.
 */
public class CreatureProjectileRenderer extends EntityRenderer<CreatureProjectile> {
    private final ItemRenderer itemRenderer;

    public CreatureProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0f;
    }

    @Override
    public void render(CreatureProjectile entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || (entity.tickCount < 2
                && this.entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25)) {
            return;
        }
        float scale = entity.visualScale();
        int light = entity.glows() ? LightTexture.FULL_BRIGHT : packedLight;
        poseStack.pushPose();
        poseStack.translate(0.0f, entity.getBbHeight() / 2f, 0.0f);
        if (entity.is3D()) {
            // Igual que las flechas: el eje X queda apuntando hacia donde vuela
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
            if (entity.spins() && !entity.isStuck()) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(-(entity.tickCount + partialTick) * 36.0f));
            }
            // El dibujo de los objetos (flechas, espadas, tridentes...) va en diagonal hacia arriba a la derecha
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0f));
            poseStack.scale(scale, scale, scale);
            poseStack.translate(0.0f, -0.125f, 0.0f);
        } else {
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        }
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, poseStack,
                buffer, entity.level(), entity.getId());
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CreatureProjectile entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
