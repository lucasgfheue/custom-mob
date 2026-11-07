package com.creaturestudio.client.screen;

import com.creaturestudio.client.render.AnimState;
import com.creaturestudio.client.render.CreatureModelRenderer;
import com.creaturestudio.client.render.PaintAtlas;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreaturePart;
import com.creaturestudio.data.PaintLayout;
import com.creaturestudio.data.Voxels;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.Set;

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

    /** Contorno de un hueco de minibloque que se dibuja encima del modelo (pieza -1 = ninguno). */
    public int cursorPart = -1;
    public int cursorPos;
    public int cursorColor;

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

    /**
     * Acerca o aleja hacia el punto de la pantalla (mx, my), como en los programas de CAD: lo que esta debajo
     * del raton se queda debajo del raton.
     */
    public void zoomAt(double amount, double mx, double my) {
        float old = zoom;
        zoomBy(amount);
        if (old <= 0f || zoom == old) {
            return;
        }
        float k = zoom / old;
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        panX = (float) ((mx - cx) * (1f - k) + k * panX);
        panY = (float) ((my - cy) * (1f - k) + k * panY);
        // que el modelo no se pierda del todo fuera de la vista
        float limit = Math.max(w, h) * 4f + zoom * radius * 2f;
        panX = Mth.clamp(panX, -limit, limit);
        panY = Mth.clamp(panY, -limit, limit);
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

    /** Matriz del modelo a la pantalla (la vista no tiene perspectiva). */
    public Matrix4f viewMatrix() {
        return matrix();
    }

    /**
     * Proyecta a la pantalla un punto de una pieza (coordenadas del modelo en reposo): {x, y, profundidad}.
     * Una profundidad mayor esta mas cerca de la camara.
     */
    public static float[] project(Matrix4f view, Matrix4f part, float x, float y, float z) {
        Vector3f v = part.transformPosition(x, y, z, new Vector3f());
        view.transformPosition(v);
        return new float[]{v.x, v.y, v.z};
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
        render(g, def, anim, selected, grid, null);
    }

    public void render(GuiGraphics g, CreatureDefinition def, AnimState anim, int selected, boolean grid,
                       @Nullable PaintAtlas paint) {
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
        if (paint != null) {
            VertexConsumer paintVc = buffers.getBuffer(RenderType.entityCutoutNoCull(paint.location()));
            CreatureModelRenderer.renderPaint(def, mats, pose, paintVc, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, -1, paint);
            vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        }
        float thickness = 1.4f / zoom;
        if (grid) {
            CreatureModelRenderer.renderGrid(pose, vc, thickness);
        }
        if (selected >= 0) {
            CreatureModelRenderer.renderSelection(def, mats, pose, vc, selected, thickness);
        }
        if (cursorPart >= 0 && cursorPart < def.parts.size() && cursorPart < mats.length) {
            CreatureModelRenderer.renderVoxelCursor(mats[cursorPart], pose, vc, def.parts.get(cursorPart), cursorPos,
                    cursorColor, thickness);
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
        Hit hit = pickHit(def, CreatureModelRenderer.pose(def, anim), mx, my);
        return hit == null ? -1 : hit.part();
    }

    /**
     * Lo que hay bajo el raton: pieza, cara (ver {@link PaintLayout}), punto en coordenadas de la pieza y, si es un
     * minibloque, su posicion empaquetada ({@link Voxels#pack}); voxel = -1 si es la caja de la pieza.
     */
    public record Hit(int part, int face, float x, float y, float z, int voxel) {
        public Hit(int part, int face, float x, float y, float z) {
            this(part, face, x, y, z, -1);
        }

        public boolean isVoxel() {
            return voxel >= 0;
        }
    }

    /** Clave de un minibloque para {@link #pickHit(CreatureDefinition, Matrix4f[], double, double, Set, boolean)}. */
    public static long voxelKey(int part, int pos) {
        return ((long) part << 32) | (pos & 0xFFFFFFFFL);
    }

    @Nullable
    public Hit pickHit(CreatureDefinition def, Matrix4f[] mats, double mx, double my) {
        return pickHit(def, mats, mx, my, null, false);
    }

    /**
     * @param ignore     minibloques que no cuentan (los puestos en el trazo actual, para pintar una capa)
     * @param voxelsOnly solo minibloques (para la goma)
     */
    @Nullable
    public Hit pickHit(CreatureDefinition def, Matrix4f[] mats, double mx, double my, @Nullable Set<Long> ignore,
                       boolean voxelsOnly) {
        if (!contains(mx, my) || def.parts.isEmpty()) {
            return null;
        }
        Matrix4f view = matrix();
        Matrix4f full = new Matrix4f();
        Matrix4f inverse = new Matrix4f();
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        float best = Float.MAX_VALUE;
        Hit bestHit = null;
        for (int i = 0; i < def.parts.size() && i < mats.length; i++) {
            view.mul(mats[i], full);
            full.invert(inverse);
            inverse.transformPosition((float) mx, (float) my, 20000f, a);
            inverse.transformPosition((float) mx, (float) my, -20000f, b);
            CreaturePart p = def.parts.get(i);
            float t = voxelsOnly ? -1f : rayBox(a, b, p.x, p.y, p.z, p.x + p.w, p.y + p.h, p.z + p.d);
            if (t >= 0f && t < best) {
                best = t;
                float hx = a.x + (b.x - a.x) * t;
                float hy = a.y + (b.y - a.y) * t;
                float hz = a.z + (b.z - a.z) * t;
                int face = PaintLayout.faceAt(hx, hy, hz, p.x, p.y, p.z, p.x + p.w, p.y + p.h, p.z + p.d);
                bestHit = new Hit(i, face, hx, hy, hz);
            }
            int[] vox = p.voxels;
            if (vox == null) {
                continue;
            }
            for (int k = 0; k + 1 < vox.length; k += 2) {
                int pos = vox[k];
                if (ignore != null && ignore.contains(voxelKey(i, pos))) {
                    continue;
                }
                float x0 = p.x + Voxels.x(pos);
                float y0 = p.y + Voxels.y(pos);
                float z0 = p.z + Voxels.z(pos);
                float tv = rayBox(a, b, x0, y0, z0, x0 + 1f, y0 + 1f, z0 + 1f);
                if (tv >= 0f && tv < best) {
                    best = tv;
                    float hx = a.x + (b.x - a.x) * tv;
                    float hy = a.y + (b.y - a.y) * tv;
                    float hz = a.z + (b.z - a.z) * tv;
                    int face = PaintLayout.faceAt(hx, hy, hz, x0, y0, z0, x0 + 1f, y0 + 1f, z0 + 1f);
                    bestHit = new Hit(i, face, hx, hy, hz, pos);
                }
            }
        }
        return bestHit;
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
