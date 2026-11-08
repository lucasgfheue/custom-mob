package com.creaturestudio.client.render;

import com.creaturestudio.data.AnimAction;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.entity.CustomCreature;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Renderizador de las criaturas personalizadas (usa el atlas de bloques como textura). */
public class CustomCreatureRenderer extends MobRenderer<CustomCreature, CreatureEntityModel> {
    private static final ResourceLocation BEAM_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/guardian_beam.png");
    private static final RenderType BEAM = RenderType.entityCutoutNoCull(BEAM_TEXTURE);

    public CustomCreatureRenderer(EntityRendererProvider.Context context) {
        super(context, new CreatureEntityModel(), 0.5f);
        this.addLayer(new PaintLayer(this));
        this.addLayer(new EquipmentLayer(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(CustomCreature entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    /** Con una animacion propia de morir, el cuerpo no se tumba de lado como en Minecraft: hace la animacion. */
    @Override
    protected float getFlipDegrees(CustomCreature entity) {
        CreatureDefinition d = entity.def();
        if (d != null && d.customAnims.has(AnimAction.DEATH)) {
            return 0f;
        }
        return super.getFlipDegrees(entity);
    }

    @Override
    protected void scale(CustomCreature entity, PoseStack poseStack, float partialTickTime) {
        CreatureDefinition d = entity.def();
        if (d != null) {
            poseStack.scale(d.scale, d.scale, d.scale);
        }
        // Creeper: se hincha antes de explotar (igual que el de Minecraft)
        float f = entity.getSwelling(partialTickTime);
        if (f > 0f) {
            float wobble = 1.0f + Mth.sin(f * 100.0f) * f * 0.01f;
            f = Mth.clamp(f, 0.0f, 1.0f);
            f *= f;
            f *= f;
            float wide = (1.0f + f * 0.4f) * wobble;
            float tall = (1.0f + f * 0.1f) / wobble;
            poseStack.scale(wide, tall, wide);
        }
    }

    /** Creeper: parpadea en blanco justo antes de explotar. */
    @Override
    protected float getWhiteOverlayProgress(CustomCreature entity, float partialTicks) {
        return flash(entity, partialTicks);
    }

    static float flash(CustomCreature entity, float partialTicks) {
        float f = entity.getSwelling(partialTicks);
        return (int) (f * 10.0f) % 2 == 0 ? 0.0f : Mth.clamp(f, 0.5f, 1.0f);
    }

    /** Calamar luminoso: se ve con luz aunque este a oscuras. */
    @Override
    protected int getBlockLightLevel(CustomCreature entity, BlockPos pos) {
        CreatureDefinition d = entity.def();
        return d != null && d.abilities.glows ? 15 : super.getBlockLightLevel(entity, pos);
    }

    @Override
    protected float getShadowRadius(CustomCreature entity) {
        return Mth.clamp(entity.getBbWidth() * 0.6f, 0.2f, 3.0f);
    }

    @Override
    public void render(CustomCreature entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        int id = entity.getBeamTarget();
        if (id != 0) {
            Entity target = entity.level().getEntity(id);
            if (target != null) {
                renderBeam(entity, target, partialTick, poseStack, buffer);
            }
        }
    }

    private static Vec3 lerpPos(Entity e, double yOffset, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, e.xOld, e.getX()), Mth.lerp(partialTick, e.yOld, e.getY()) + yOffset,
                Mth.lerp(partialTick, e.zOld, e.getZ()));
    }

    /** Rayo del guardian: va del ojo de la criatura al objetivo y cambia de color al cargarse. */
    private static void renderBeam(CustomCreature entity, Entity target, float partialTick, PoseStack poseStack,
                                   MultiBufferSource buffer) {
        float eye = entity.getEyeHeight();
        Vec3 to = lerpPos(target, target.getBbHeight() * 0.5, partialTick);
        Vec3 from = lerpPos(entity, eye, partialTick);
        Vec3 diff = to.subtract(from);
        float length = (float) (diff.length() + 1.0);
        if (length < 1.01f) {
            return;
        }
        Vec3 dir = diff.normalize();
        float pitch = (float) Math.acos(dir.y);
        float yaw = (float) Math.atan2(dir.z, dir.x);
        float charge = entity.getBeamProgress(partialTick);
        float time = entity.getBeamTime(partialTick);
        poseStack.pushPose();
        poseStack.translate(0.0f, eye, 0.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(((float) Math.PI / 2f - yaw) * (180f / (float) Math.PI)));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch * (180f / (float) Math.PI)));
        float c2 = charge * charge;
        int red = 64 + (int) (c2 * 191.0f);
        int green = 32 + (int) (c2 * 191.0f);
        int blue = 128 - (int) (c2 * 64.0f);
        int color = 0xFF000000 | red << 16 | green << 8 | blue;
        float spin = time * 0.05f * -1.5f;
        float scroll = time * 0.5f % 1.0f;
        float v0 = -1.0f + scroll;
        float v1 = length * 2.5f + v0;
        float r = 0.2f;
        float x1 = Mth.cos(spin + (float) Math.PI) * r;
        float z1 = Mth.sin(spin + (float) Math.PI) * r;
        float x2 = Mth.cos(spin) * r;
        float z2 = Mth.sin(spin) * r;
        float x3 = Mth.cos(spin + (float) Math.PI / 2f) * r;
        float z3 = Mth.sin(spin + (float) Math.PI / 2f) * r;
        float x4 = Mth.cos(spin + (float) Math.PI * 1.5f) * r;
        float z4 = Mth.sin(spin + (float) Math.PI * 1.5f) * r;
        VertexConsumer vc = buffer.getBuffer(BEAM);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        // dos laminas cruzadas a lo largo del rayo
        vertex(vc, m, n, x1, length, z1, color, 0.4999f, v1);
        vertex(vc, m, n, x1, 0.0f, z1, color, 0.4999f, v0);
        vertex(vc, m, n, x2, 0.0f, z2, color, 0.0f, v0);
        vertex(vc, m, n, x2, length, z2, color, 0.0f, v1);
        vertex(vc, m, n, x3, length, z3, color, 0.4999f, v1);
        vertex(vc, m, n, x3, 0.0f, z3, color, 0.4999f, v0);
        vertex(vc, m, n, x4, 0.0f, z4, color, 0.0f, v0);
        vertex(vc, m, n, x4, length, z4, color, 0.0f, v1);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, int color,
                               float u, float v) {
        Vector3f p = m.transformPosition(x, y, z, new Vector3f());
        Vector3f nn = n.transform(0.0f, 1.0f, 0.0f, new Vector3f());
        vc.addVertex(p.x(), p.y(), p.z(), color, u, v, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT,
                nn.x(), nn.y(), nn.z());
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
                    LivingEntityRenderer.getOverlayCoords(entity, flash(entity, partialTick)), d, atlas);
        }
    }
}
