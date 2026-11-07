package com.creaturestudio.client.screen;

import com.creaturestudio.client.render.AnimState;
import com.creaturestudio.client.render.CreatureModelRenderer;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreaturePart;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Vista 3D del modelo dentro de una pantalla: girar arrastrando, mover con el boton derecho,
 * acercar con la rueda y seleccionar piezas con un clic (lanzando un rayo contra los cubos).
 */
public class Preview3D {
    public int x, y, w, h;
    public float yaw = 0.6f;
    public float pitch = 0.35f;
    public float zoom = 4f;
    public float panX, panY;
    private final float[] center = {0f, 8f, 0f};
    private float radius = 16f;

    public void setArea(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = Math.max(1, w);
        this.h = Math.max(1, h);
    }

    public boolean contains(double mx, double my) {
        return Ui.inside(mx, my, x, y, w, h);
    }

    /** Encuadra el modelo completo. */
    public void fit(CreatureDefinition def) {
        float[] b = def.bounds();
        center[0] = (b[0] + b[3]) / 2f;
        center[1] = (b[1] + b[4]) / 2f;
        center[2] = (b[2] + b[5]) / 2f;
        float extent = Math.max(8f, Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2])));
        radius = extent;
        zoom = Math.max(0.3f, Math.min(w, h) * 0.62f / extent);
        panX = 0f;
        panY = 0f;
        yaw = 0.6f;
        pitch = 0.35f;
    }

    public void rotate(double dx, double dy) {
        yaw += (float) dx * 0.012f;
        pitch = Mth.clamp(pitch + (float) dy * 0.012f, -1.55f, 1.55f);
    }

    public void pan(double dx, double dy) {
        panX += (float) dx;
        panY += (float) dy;
    }

    public void zoomBy(double amount) {
        zoom = Mth.clamp(zoom * (float) (1.0 + 0.12 * amount), 0.2f, 80f);
    }

    private float depth() {
        return Math.min(200f + zoom * radius * 1.6f, 8000f);
    }

    private void apply(PoseStack ps) {
        ps.translate(x + w / 2f + panX, y + h / 2f + panY, depth());
        ps.scale(zoom, -zoom, zoom);
        ps.mulPose(Axis.XP.rotation(pitch));
        ps.mulPose(Axis.YP.rotation(yaw));
        ps.scale(1f, 1f, -1f);
        ps.translate(-center[0], -center[1], -center[2]);
    }

    /** La misma transformacion que {@link #apply} pero como matriz JOML (para seleccionar piezas). */
    private Matrix4f matrix() {
        return new Matrix4f()
                .translate(x + w / 2f + panX, y + h / 2f + panY, depth())
                .scale(zoom, -zoom, zoom)
                .rotateX(pitch)
                .rotateY(yaw)
                .scale(1f, 1f, -1f)
                .translate(-center[0], -center[1], -center[2]);
    }

    public void render(GuiGraphics g, CreatureDefinition def, AnimState anim, int selected, boolean grid) {
        g.enableScissor(x, y, x + w, y + h);
        PoseStack ps = g.pose();
        ps.pushPose();
        apply(ps);
        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = ps.last();
        Matrix4f[] mats = CreatureModelRenderer.pose(def, anim);
        CreatureModelRenderer.renderPosed(def, mats, pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        float thickness = 1.4f / zoom;
        if (grid) {
            CreatureModelRenderer.renderGrid(pose, vc, thickness);
        }
        if (selected >= 0) {
            CreatureModelRenderer.renderSelection(def, mats, pose, vc, selected, thickness);
        }
        g.flush();
        ps.popPose();
        g.disableScissor();
        Lighting.setupFor3DItems();
        // Limpiar profundidad para que los botones dibujados despues queden por encima del modelo.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /** Indice de la pieza bajo el raton o -1. */
    public int pick(CreatureDefinition def, AnimState anim, double mx, double my) {
        if (!contains(mx, my) || def.parts.isEmpty()) {
            return -1;
        }
        Matrix4f view = matrix();
        Matrix4f[] mats = CreatureModelRenderer.pose(def, anim);
        Matrix4f full = new Matrix4f();
        Matrix4f inverse = new Matrix4f();
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        float best = Float.MAX_VALUE;
        int bestIndex = -1;
        for (int i = 0; i < def.parts.size(); i++) {
            view.mul(mats[i], full);
            full.invert(inverse);
            inverse.transformPosition((float) mx, (float) my, 20000f, a);
            inverse.transformPosition((float) mx, (float) my, -20000f, b);
            CreaturePart p = def.parts.get(i);
            float t = rayBox(a, b, p.x, p.y, p.z, p.x + p.w, p.y + p.h, p.z + p.d);
            if (t >= 0f && t < best) {
                best = t;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static float rayBox(Vector3f a, Vector3f b, float x0, float y0, float z0, float x1, float y1, float z1) {
        float[] o = {a.x, a.y, a.z};
        float[] d = {b.x - a.x, b.y - a.y, b.z - a.z};
        float[] lo = {x0, y0, z0};
        float[] hi = {x1, y1, z1};
        float tMin = 0f;
        float tMax = 1f;
        for (int k = 0; k < 3; k++) {
            float l = lo[k];
            float u = hi[k];
            if (u - l < 0.3f) { // las piezas muy finas se agrandan un poco para poder hacer clic
                float c = (l + u) / 2f;
                l = c - 0.15f;
                u = c + 0.15f;
            }
            if (Math.abs(d[k]) < 1.0e-9f) {
                if (o[k] < l || o[k] > u) {
                    return -1f;
                }
            } else {
                float t1 = (l - o[k]) / d[k];
                float t2 = (u - o[k]) / d[k];
                if (t1 > t2) {
                    float tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) {
                    return -1f;
                }
            }
        }
        return tMin;
    }
}
