package com.creaturestudio.client.render;

import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreaturePart;
import com.creaturestudio.data.EquipAnchors;
import com.creaturestudio.entity.CustomCreature;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Equipo de las criaturas: la armadura se estira sobre la cabeza, el cuerpo, los brazos y las patas (como en los
 * mobs de Minecraft pero con la forma de cada criatura), los objetos de las manos van al final de los brazos y un
 * objeto que no sea casco (una calabaza, una cabeza...) se pone encima de la cabeza.
 */
final class EquipmentLayer extends RenderLayer<CustomCreature, CreatureEntityModel> {
    private static final float TEX_W = 64f;
    private static final float TEX_H = 32f;

    private final ItemInHandRenderer hands;

    EquipmentLayer(RenderLayerParent<CustomCreature, CreatureEntityModel> parent, ItemInHandRenderer hands) {
        super(parent);
        this.hands = hands;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, CustomCreature entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        CreatureDefinition d = entity.def();
        Matrix4f[] mats = getParentModel().lastPose(d);
        if (d == null || mats == null || entity.isInvisible()) {
            return;
        }
        EquipAnchors a = EquipAnchors.find(d);
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0f);
        renderArmor(poseStack, buffer, packedLight, overlay, entity, d, mats, a);
        renderHand(poseStack, buffer, packedLight, entity, d, mats, a.rightArm, entity.getMainHandItem(), false);
        renderHand(poseStack, buffer, packedLight, entity, d, mats, a.leftArm, entity.getOffhandItem(), true);
        ItemStack headItem = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (!headItem.isEmpty() && !(headItem.getItem() instanceof ArmorItem)) {
            renderHeadItem(poseStack, buffer, packedLight, entity, d, mats, a.head, headItem);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Armadura
    // ---------------------------------------------------------------------------------------

    private void renderArmor(PoseStack ps, MultiBufferSource buffer, int light, int overlay, CustomCreature entity,
                             CreatureDefinition d, Matrix4f[] mats, EquipAnchors a) {
        ps.pushPose();
        CreatureEntityModel.toModelSpace(ps);
        PoseStack.Pose root = ps.last();
        ItemStack helmet = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (isArmor(helmet, EquipmentSlot.HEAD) && a.head >= 0) {
            drawArmor(root, buffer, light, overlay, helmet, false, (vc, color) -> {
                box(vc, root, d, mats, a.head, 1.0f, 0, 0, 8, 8, 8, false, color, light, overlay);
                box(vc, root, d, mats, a.head, 1.5f, 32, 0, 8, 8, 8, false, color, light, overlay);
            });
        }
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (isArmor(chest, EquipmentSlot.CHEST)) {
            drawArmor(root, buffer, light, overlay, chest, false, (vc, color) -> {
                box(vc, root, d, mats, a.body, 1.0f, 16, 16, 8, 12, 4, false, color, light, overlay);
                box(vc, root, d, mats, a.rightArm, 1.0f, 40, 16, 4, 12, 4, false, color, light, overlay);
                box(vc, root, d, mats, a.leftArm, 1.0f, 40, 16, 4, 12, 4, true, color, light, overlay);
            });
        }
        ItemStack legs = entity.getItemBySlot(EquipmentSlot.LEGS);
        if (isArmor(legs, EquipmentSlot.LEGS)) {
            drawArmor(root, buffer, light, overlay, legs, true, (vc, color) -> {
                box(vc, root, d, mats, a.body, 0.5f, 16, 16, 8, 12, 4, false, color, light, overlay);
                for (int k = 0; k < a.legs.length; k++) {
                    box(vc, root, d, mats, a.legs[k], 0.5f, 0, 16, 4, 12, 4, a.legLeft[k], color, light, overlay);
                }
            });
        }
        ItemStack feet = entity.getItemBySlot(EquipmentSlot.FEET);
        if (isArmor(feet, EquipmentSlot.FEET)) {
            drawArmor(root, buffer, light, overlay, feet, false, (vc, color) -> {
                for (int k = 0; k < a.legs.length; k++) {
                    box(vc, root, d, mats, a.legs[k], 1.0f, 0, 16, 4, 12, 4, a.legLeft[k], color, light, overlay);
                }
            });
        }
        ps.popPose();
    }

    private interface Boxes {
        void draw(VertexConsumer vc, int color);
    }

    private static boolean isArmor(ItemStack stack, EquipmentSlot slot) {
        return !stack.isEmpty() && stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == slot;
    }

    /** Dibuja las cajas con cada capa de textura del material (y el brillo si esta encantada). */
    private static void drawArmor(PoseStack.Pose root, MultiBufferSource buffer, int light, int overlay,
                                  ItemStack stack, boolean inner, Boxes boxes) {
        ArmorItem item = (ArmorItem) stack.getItem();
        ArmorMaterial material = item.getMaterial().value();
        int dye = stack.is(ItemTags.DYEABLE) ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, -6265536)) : -1;
        for (ArmorMaterial.Layer layer : material.layers()) {
            ResourceLocation texture = layer.texture(inner);
            int color = layer.dyeable() ? dye : -1;
            boxes.draw(buffer.getBuffer(RenderType.armorCutoutNoCull(texture)), color);
        }
        if (stack.hasFoil()) {
            boxes.draw(buffer.getBuffer(RenderType.armorEntityGlint()), -1);
        }
    }

    private static void box(VertexConsumer vc, PoseStack.Pose root, CreatureDefinition d, Matrix4f[] mats, int index,
                            float grow, int u, int v, int bw, int bh, int bd, boolean mirror, int color, int light,
                            int overlay) {
        if (index < 0 || index >= d.parts.size() || index >= mats.length) {
            return;
        }
        CreaturePart p = d.parts.get(index);
        CreatureModelRenderer.renderSkinBox(mats[index], root, vc, p.x - grow, p.y - grow, p.z - grow,
                p.x + p.w + grow, p.y + p.h + grow, p.z + p.d + grow, u, v, bw, bh, bd, mirror, TEX_W, TEX_H,
                color, light, overlay);
    }

    // ---------------------------------------------------------------------------------------
    // Manos y cabeza
    // ---------------------------------------------------------------------------------------

    /** Lleva la pose a la pieza (espacio del modelo, en pixeles) y luego a un punto de su caja. */
    private static void toPart(PoseStack ps, Matrix4f partMat, float x, float y, float z) {
        CreatureEntityModel.toModelSpace(ps);
        PoseStack.Pose last = ps.last();
        last.pose().mul(partMat);
        Matrix3f rotation = new Matrix3f();
        partMat.get3x3(rotation);
        last.normal().mul(rotation);
        ps.translate(x, y, z);
        // de pixeles con Y hacia arriba a bloques con Y hacia abajo, como el espacio de los modelos de Minecraft
        ps.scale(16.0f, -16.0f, 16.0f);
    }

    private void renderHand(PoseStack ps, MultiBufferSource buffer, int light, CustomCreature entity,
                            CreatureDefinition d, Matrix4f[] mats, int arm, ItemStack stack, boolean left) {
        if (stack.isEmpty() || arm < 0 || arm >= d.parts.size() || arm >= mats.length) {
            return;
        }
        CreaturePart p = d.parts.get(arm);
        ps.pushPose();
        // la mano es el centro de la cara de abajo del brazo
        toPart(ps, mats[arm], p.x + p.w / 2f, p.y, p.z + p.d / 2f);
        ps.mulPose(Axis.XP.rotationDegrees(-90.0f));
        ps.mulPose(Axis.YP.rotationDegrees(180.0f));
        ps.translate((left ? -1.0f : 1.0f) / 16.0f, 0.125f, 0.0f);
        this.hands.renderItem(entity, stack,
                left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                left, ps, buffer, light);
        ps.popPose();
    }

    private void renderHeadItem(PoseStack ps, MultiBufferSource buffer, int light, CustomCreature entity,
                                CreatureDefinition d, Matrix4f[] mats, int head, ItemStack stack) {
        if (head < 0 || head >= d.parts.size() || head >= mats.length) {
            return;
        }
        CreaturePart p = d.parts.get(head);
        ps.pushPose();
        toPart(ps, mats[head], p.x + p.w / 2f, p.y, p.z + p.d / 2f);
        float k = Math.max(p.w, Math.max(p.h, p.d)) / 8.0f;
        ps.scale(k, k, k);
        // igual que CustomHeadLayer de Minecraft, para una cabeza de 8 pixeles
        ps.translate(0.0f, -0.25f, 0.0f);
        ps.mulPose(Axis.YP.rotationDegrees(180.0f));
        ps.scale(0.625f, -0.625f, -0.625f);
        this.hands.renderItem(entity, stack, ItemDisplayContext.HEAD, false, ps, buffer, light);
        ps.popPose();
    }
}
