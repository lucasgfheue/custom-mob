package com.creaturestudio.client.render;

import com.creaturestudio.data.AnimAction;
import com.creaturestudio.data.AnimPlayer;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreaturePart;
import com.creaturestudio.data.MaterialIds;
import com.creaturestudio.data.PaintLayout;
import com.creaturestudio.data.PartAnim;
import com.creaturestudio.data.PoseSolver;
import com.creaturestudio.data.Voxels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Dibuja el modelo de una criatura cubo a cubo. Las coordenadas de entrada estan en pixeles del modelo
 * (Y hacia arriba); quien llama prepara la matriz raiz (mundo o vista previa del editor).
 */
public final class CreatureModelRenderer {
    private static final float PI = (float) Math.PI;
    private static final float DEG = PoseSolver.DEG;

    // Vectores temporales: el dibujado siempre ocurre en el hilo de render.
    private static final Vector3f POS = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();

    private CreatureModelRenderer() {
    }

    // ---------------------------------------------------------------------------------------
    // Animacion
    // ---------------------------------------------------------------------------------------

    /** Matrices de cada pieza para el estado de animacion dado. */
    public static Matrix4f[] pose(CreatureDefinition def, AnimState s) {
        List<CreaturePart> parts = def.parts;
        int n = parts.size();
        float[] rot = new float[n * 3];
        float[] off = new float[n * 3];
        if (s.pinAction() >= 0 && s.pinAction() < AnimAction.COUNT) {
            AnimPlayer.pin(def, AnimAction.ALL[s.pinAction()], s.pinPose(), rot, off);
            return PoseSolver.solve(parts, rot, off);
        }
        for (int i = 0; i < n; i++) {
            animate(parts.get(i), s, rot, off, i * 3);
        }
        AnimPlayer.apply(def, rot, off, s.limbSwing(), s.limbSwingAmount(), s.ageInTicks(), s.flying(),
                s.actionAges());
        return PoseSolver.solve(parts, rot, off);
    }

    private static void animate(CreaturePart p, AnimState s, float[] rot, float[] off, int k) {
        if (p.anim == null || p.anim == PartAnim.NONE) {
            return;
        }
        float amt = p.animAmount;
        float spd = Math.max(p.animSpeed, 0f);
        float swing = s.limbSwing() * 0.6662f * spd;
        float sa = Math.min(s.limbSwingAmount(), 1.5f);
        float t = s.ageInTicks() * spd;
        float attack = Mth.sin(s.attackTime() * PI);
        float side = (p.x + p.w / 2f) >= 0f ? 1f : -1f;
        switch (p.anim) {
            case HEAD -> {
                rot[k] += -s.headPitch() * DEG * amt;
                rot[k + 1] += s.netHeadYaw() * DEG * amt;
            }
            case LEG_A -> rot[k] += Mth.cos(swing) * 1.2f * sa * amt;
            case LEG_B -> rot[k] += Mth.cos(swing + PI) * 1.2f * sa * amt;
            case ARM_A -> rot[k] += (Mth.cos(swing + PI) * 0.9f * sa + attack * 1.4f) * amt;
            case ARM_B -> rot[k] += (Mth.cos(swing) * 0.9f * sa + attack * 1.4f) * amt;
            case SIDE_LEG_A -> {
                rot[k + 1] += Mth.cos(swing) * 0.5f * sa * amt;
                rot[k + 2] += side * Math.max(0f, Mth.sin(swing)) * 0.3f * sa * amt;
            }
            case SIDE_LEG_B -> {
                rot[k + 1] += Mth.cos(swing + PI) * 0.5f * sa * amt;
                rot[k + 2] += side * Math.max(0f, Mth.sin(swing + PI)) * 0.3f * sa * amt;
            }
            case WING_A -> rot[k + 2] += flap(s, t) * amt;
            case WING_B -> rot[k + 2] -= flap(s, t) * amt;
            case TAIL -> rot[k + 1] += (Mth.sin(t * 0.25f) * 0.35f + Mth.cos(swing) * 0.3f * sa) * amt;
            case BOB -> off[k + 1] += Mth.sin(t * 0.15f) * 0.6f * amt;
            case JAW -> rot[k] -= (Math.max(0f, Mth.sin(t * 0.1f)) * 0.08f + attack * 0.6f) * amt;
            case SPIN -> rot[k + 1] += t * 0.1f * amt;
            case SWAY -> rot[k + 2] += (Mth.cos(swing) * 0.1f * sa + Mth.sin(t * 0.05f) * 0.03f) * amt;
            default -> {
            }
        }
    }

    private static float flap(AnimState s, float t) {
        return s.flying() ? Mth.sin(t * 1.3f) * 0.8f : Mth.sin(t * 0.2f) * 0.12f;
    }

    // ---------------------------------------------------------------------------------------
    // Dibujado
    // ---------------------------------------------------------------------------------------

    public static void render(CreatureDefinition def, PoseStack.Pose root, VertexConsumer vc, int light, int overlay,
                              int tint, AnimState anim) {
        if (def.parts.isEmpty()) {
            return;
        }
        renderPosed(def, pose(def, anim), root, vc, light, overlay, tint);
    }

    public static void renderPosed(CreatureDefinition def, Matrix4f[] mats, PoseStack.Pose root, VertexConsumer vc,
                                   int light, int overlay, int tint) {
        Function<ResourceLocation, TextureAtlasSprite> atlas = Materials.atlas();
        Matrix4f m = new Matrix4f();
        Matrix3f rotation = new Matrix3f();
        Matrix3f normal = new Matrix3f();
        List<CreaturePart> parts = def.parts;
        for (int i = 0; i < parts.size() && i < mats.length; i++) {
            CreaturePart p = parts.get(i);
            root.pose().mul(mats[i], m);
            mats[i].get3x3(rotation);
            root.normal().mul(rotation, normal);
            TextureAtlasSprite sprite = Materials.sprite(atlas, p.material);
            int color = multiply(0xFF000000 | p.color, tint);
            int lt = p.glow ? LightTexture.FULL_BRIGHT : light;
            drawBox(vc, m, normal, p.x, p.y, p.z, p.x + p.w, p.y + p.h, p.z + p.d, sprite, p.stretch, color,
                    lt, overlay);
            if (p.voxels != null && p.voxels.length >= 2) {
                drawVoxels(vc, m, normal, p, Materials.sprite(atlas, MaterialIds.SOLID), tint, lt, overlay);
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Minibloques
    // ---------------------------------------------------------------------------------------

    /** Caras que se ven de cada lista de minibloques (las listas no cambian nunca: cada cambio crea otra). */
    private static final Map<int[], byte[]> EXPOSED = new WeakHashMap<>();

    private static byte[] exposed(int[] voxels) {
        byte[] mask = EXPOSED.get(voxels);
        if (mask == null) {
            mask = Voxels.exposed(voxels);
            EXPOSED.put(voxels, mask);
        }
        return mask;
    }

    /** Dibuja los minibloques de una pieza, cada uno de su tamano (solo las caras que no tapa otro minibloque). */
    private static void drawVoxels(VertexConsumer vc, Matrix4f m, Matrix3f nm, CreaturePart p, TextureAtlasSprite s,
                                   int tint, int light, int overlay) {
        int[] v = p.voxels;
        byte[] mask = exposed(v);
        for (int i = 0; i < mask.length; i++) {
            int faces = mask[i];
            if (faces == 0) {
                continue;
            }
            int pos = v[i * 2];
            int color = multiply(0xFF000000 | (v[i * 2 + 1] & 0xFFFFFF), tint);
            float k = Voxels.pixels(Voxels.sizeOf(v[i * 2 + 1]));
            float x0 = p.x + Voxels.px(pos);
            float y0 = p.y + Voxels.py(pos);
            float z0 = p.z + Voxels.pz(pos);
            float x1 = x0 + k;
            float y1 = y0 + k;
            float z1 = z0 + k;
            if ((faces & 1) != 0) { // frente (-Z)
                face(vc, m, nm, x0, y1, z0, 1, 0, 0, 0, -1, 0, k, k, 0, 0, -1, s, true, color, light, overlay);
            }
            if ((faces & 2) != 0) { // atras (+Z)
                face(vc, m, nm, x1, y1, z1, -1, 0, 0, 0, -1, 0, k, k, 0, 0, 1, s, true, color, light, overlay);
            }
            if ((faces & 4) != 0) { // +X
                face(vc, m, nm, x1, y1, z0, 0, 0, 1, 0, -1, 0, k, k, 1, 0, 0, s, true, color, light, overlay);
            }
            if ((faces & 8) != 0) { // -X
                face(vc, m, nm, x0, y1, z1, 0, 0, -1, 0, -1, 0, k, k, -1, 0, 0, s, true, color, light, overlay);
            }
            if ((faces & 16) != 0) { // arriba
                face(vc, m, nm, x0, y1, z1, 1, 0, 0, 0, 0, -1, k, k, 0, 1, 0, s, true, color, light, overlay);
            }
            if ((faces & 32) != 0) { // abajo
                face(vc, m, nm, x0, y0, z0, 1, 0, 0, 0, 0, 1, k, k, 0, -1, 0, s, true, color, light, overlay);
            }
        }
    }

    /**
     * Contorno de un hueco de minibloque de un tamano (para ver donde se pondra o cual se borrara) en el espacio de
     * la pieza.
     */
    public static void renderVoxelCursor(Matrix4f partMat, PoseStack.Pose root, VertexConsumer vc, CreaturePart p,
                                         int pos, int size, int color, float thickness) {
        Matrix4f m = new Matrix4f();
        root.pose().mul(partMat, m);
        Matrix3f rotation = new Matrix3f();
        partMat.get3x3(rotation);
        Matrix3f normal = new Matrix3f();
        root.normal().mul(rotation, normal);
        TextureAtlasSprite solid = Materials.sprite(MaterialIds.SOLID);
        float h = thickness / 2f;
        float k = Voxels.pixels(size);
        float x0 = p.x + Voxels.px(pos);
        float y0 = p.y + Voxels.py(pos);
        float z0 = p.z + Voxels.pz(pos);
        float[] xs = {x0, x0 + k};
        float[] ys = {y0, y0 + k};
        float[] zs = {z0, z0 + k};
        for (float y : ys) {
            for (float z : zs) {
                bar(vc, m, normal, solid, xs[0] - h, y - h, z - h, xs[1] + h, y + h, z + h, color);
            }
        }
        for (float x : xs) {
            for (float z : zs) {
                bar(vc, m, normal, solid, x - h, ys[0] - h, z - h, x + h, ys[1] + h, z + h, color);
            }
        }
        for (float x : xs) {
            for (float y : ys) {
                bar(vc, m, normal, solid, x - h, y - h, zs[0] - h, x + h, y + h, zs[1] + h, color);
            }
        }
    }

    /** Separacion de la pintura respecto a la cara (en pixeles) para que no parpadee con la textura de debajo. */
    private static final float PAINT_OFFSET = 0.05f;

    /**
     * Dibuja la pintura a mano encima del modelo (con la textura de {@link PaintAtlas}).
     * Debe usar las mismas matrices que el dibujado normal.
     */
    public static void renderPaint(CreatureDefinition def, Matrix4f[] mats, PoseStack.Pose root, VertexConsumer vc,
                                   int light, int overlay, int tint, PaintAtlas atlas) {
        Matrix4f m = new Matrix4f();
        Matrix3f rotation = new Matrix3f();
        Matrix3f normal = new Matrix3f();
        float aw = atlas.width();
        float ah = atlas.height();
        List<CreaturePart> parts = def.parts;
        for (int i = 0; i < parts.size() && i < mats.length; i++) {
            if (!atlas.has(i)) {
                continue;
            }
            CreaturePart p = parts.get(i);
            root.pose().mul(mats[i], m);
            mats[i].get3x3(rotation);
            root.normal().mul(rotation, normal);
            int lt = p.glow ? LightTexture.FULL_BRIGHT : light;
            float e = PAINT_OFFSET;
            float x0 = p.x - e;
            float y0 = p.y - e;
            float z0 = p.z - e;
            float x1 = p.x + p.w + e;
            float y1 = p.y + p.h + e;
            float z1 = p.z + p.d + e;
            for (int f = 0; f < PaintLayout.FACES; f++) {
                boolean side = f == PaintLayout.LEFT || f == PaintLayout.RIGHT;
                boolean cap = f == PaintLayout.TOP || f == PaintLayout.BOTTOM;
                float a = side ? p.d : p.w;
                float b = cap ? p.d : p.h;
                if (a <= 0f || b <= 0f) {
                    continue;
                }
                int[] r = PaintLayout.region(f, p.pw, p.ph, p.pd);
                float u0 = (atlas.slotX(i) + r[0]) / aw;
                float u1 = (atlas.slotX(i) + r[0] + r[2]) / aw;
                float v0 = (atlas.slotY(i) + r[1]) / ah;
                float v1 = (atlas.slotY(i) + r[1] + r[3]) / ah;
                float[] fr = PaintLayout.frame(f, x0, y0, z0, x1, y1, z1);
                Vector3f n = normal.transform(fr[9], fr[10], fr[11], NORMAL);
                if (n.lengthSquared() > 1.0e-12f) {
                    n.normalize();
                }
                float nx = n.x();
                float ny = n.y();
                float nz = n.z();
                vertex(vc, m, fr[0], fr[1], fr[2], tint, u0, v0, overlay, lt, nx, ny, nz);
                vertex(vc, m, fr[0] + fr[6], fr[1] + fr[7], fr[2] + fr[8], tint, u0, v1, overlay, lt, nx, ny, nz);
                vertex(vc, m, fr[0] + fr[3] + fr[6], fr[1] + fr[4] + fr[7], fr[2] + fr[5] + fr[8], tint, u1, v1,
                        overlay, lt, nx, ny, nz);
                vertex(vc, m, fr[0] + fr[3], fr[1] + fr[4], fr[2] + fr[5], tint, u1, v0, overlay, lt, nx, ny, nz);
            }
        }
    }

    /**
     * Caja con una textura de piel de Minecraft (como la de las armaduras, 64x32): la zona de caja que empieza en
     * (u, v) con tamano bw x bh x bd se estira sobre la caja dada. "mirror" la refleja como las piezas izquierdas
     * de Minecraft. La caja esta en el espacio de la pieza; partMat es su matriz animada.
     */
    public static void renderSkinBox(Matrix4f partMat, PoseStack.Pose root, VertexConsumer vc,
                                     float x0, float y0, float z0, float x1, float y1, float z1,
                                     int u, int v, int bw, int bh, int bd, boolean mirror, float texW, float texH,
                                     int color, int light, int overlay) {
        Matrix4f m = new Matrix4f();
        root.pose().mul(partMat, m);
        Matrix3f rotation = new Matrix3f();
        partMat.get3x3(rotation);
        Matrix3f normal = new Matrix3f();
        root.normal().mul(rotation, normal);
        for (int f = 0; f < PaintLayout.FACES; f++) {
            boolean side = f == PaintLayout.LEFT || f == PaintLayout.RIGHT;
            boolean cap = f == PaintLayout.TOP || f == PaintLayout.BOTTOM;
            float a = side ? z1 - z0 : x1 - x0;
            float b = cap ? z1 - z0 : y1 - y0;
            if (a <= 0f || b <= 0f) {
                continue;
            }
            int src = mirror ? PaintLayout.mirrored(f) : f;
            int[] r = PaintLayout.region(src, bw, bh, bd);
            float u0 = (u + r[0]) / texW;
            float u1 = (u + r[0] + r[2]) / texW;
            float v0 = (v + r[1]) / texH;
            float v1 = (v + r[1] + r[3]) / texH;
            if (mirror) {
                float t = u0;
                u0 = u1;
                u1 = t;
            }
            if (f == PaintLayout.BOTTOM) {
                float t = v0;
                v0 = v1;
                v1 = t;
            }
            float[] fr = PaintLayout.frame(f, x0, y0, z0, x1, y1, z1);
            Vector3f n = normal.transform(fr[9], fr[10], fr[11], NORMAL);
            if (n.lengthSquared() > 1.0e-12f) {
                n.normalize();
            }
            float nx = n.x();
            float ny = n.y();
            float nz = n.z();
            vertex(vc, m, fr[0], fr[1], fr[2], color, u0, v0, overlay, light, nx, ny, nz);
            vertex(vc, m, fr[0] + fr[6], fr[1] + fr[7], fr[2] + fr[8], color, u0, v1, overlay, light, nx, ny, nz);
            vertex(vc, m, fr[0] + fr[3] + fr[6], fr[1] + fr[4] + fr[7], fr[2] + fr[5] + fr[8], color, u1, v1,
                    overlay, light, nx, ny, nz);
            vertex(vc, m, fr[0] + fr[3], fr[1] + fr[4], fr[2] + fr[5], color, u1, v0, overlay, light, nx, ny, nz);
        }
    }

    /** Cubo morado que se ve mientras llega la definicion desde el servidor. */
    public static void renderPlaceholder(PoseStack.Pose root, VertexConsumer vc, int light, int overlay) {
        TextureAtlasSprite sprite = Materials.sprite(MaterialIds.SOLID);
        drawBox(vc, root.pose(), root.normal(), -3, 0, -3, 3, 6, 3, sprite, false, 0xFFB040D0, light, overlay);
    }

    /** Contorno amarillo de la pieza seleccionada y punto rojo en su pivote. */
    public static void renderSelection(CreatureDefinition def, Matrix4f[] mats, PoseStack.Pose root, VertexConsumer vc,
                                       int index, float thickness) {
        if (index < 0 || index >= def.parts.size() || index >= mats.length) {
            return;
        }
        CreaturePart p = def.parts.get(index);
        Matrix4f m = new Matrix4f();
        root.pose().mul(mats[index], m);
        Matrix3f rotation = new Matrix3f();
        mats[index].get3x3(rotation);
        Matrix3f normal = new Matrix3f();
        root.normal().mul(rotation, normal);
        TextureAtlasSprite solid = Materials.sprite(MaterialIds.SOLID);
        int yellow = 0xFFFFD84A;
        float h = thickness / 2f;
        float[] xs = {p.x, p.x + p.w};
        float[] ys = {p.y, p.y + p.h};
        float[] zs = {p.z, p.z + p.d};
        for (float y : ys) {
            for (float z : zs) {
                bar(vc, m, normal, solid, xs[0] - h, y - h, z - h, xs[1] + h, y + h, z + h, yellow);
            }
        }
        for (float x : xs) {
            for (float z : zs) {
                bar(vc, m, normal, solid, x - h, ys[0] - h, z - h, x + h, ys[1] + h, z + h, yellow);
            }
        }
        for (float x : xs) {
            for (float y : ys) {
                bar(vc, m, normal, solid, x - h, y - h, zs[0] - h, x + h, y + h, zs[1] + h, yellow);
            }
        }
        float s = thickness * 1.8f;
        bar(vc, m, normal, solid, p.px - s, p.py - s, p.pz - s, p.px + s, p.py + s, p.pz + s, 0xFFFF3B3B);
    }

    /** Rejilla del suelo (cada 8 pixeles), eje X en rojo y flecha azul hacia el frente (-Z). */
    public static void renderGrid(PoseStack.Pose root, VertexConsumer vc, float thickness) {
        TextureAtlasSprite solid = Materials.sprite(MaterialIds.SOLID);
        Matrix4f m = root.pose();
        Matrix3f n = root.normal();
        float h = thickness / 2f;
        int extent = 32;
        for (int i = -extent; i <= extent; i += 8) {
            int color = i == 0 ? 0xFF6A6F7A : (i % 16 == 0 ? 0xFF4A4E57 : 0xFF383B42);
            bar(vc, m, n, solid, -extent, -h, i - h, extent, 0f, i + h, color);
            bar(vc, m, n, solid, i - h, -h, -extent, i + h, 0f, extent, color);
        }
        bar(vc, m, n, solid, 0f, 0f, -h, 12f, h * 2f, h, 0xFFE05252);
        bar(vc, m, n, solid, -h, 0f, -16f, h, h * 2f, 0f, 0xFF4F8CFF);
        bar(vc, m, n, solid, -2f, 0f, -16f - h, 2f, h * 2f, -16f + h, 0xFF4F8CFF);
    }

    private static void bar(VertexConsumer vc, Matrix4f m, Matrix3f n, TextureAtlasSprite s,
                            float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        drawBox(vc, m, n, x0, y0, z0, x1, y1, z1, s, true, color, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    /**
     * Dibuja un cubo. Las caras de mas de 16 pixeles repiten la textura (salvo si stretch es true).
     * Si el cubo es plano (grosor 0) solo se dibuja una de las dos caras superpuestas.
     */
    public static void drawBox(VertexConsumer vc, Matrix4f m, Matrix3f nm,
                               float x0, float y0, float z0, float x1, float y1, float z1,
                               TextureAtlasSprite s, boolean stretch, int color, int light, int overlay) {
        float w = x1 - x0;
        float h = y1 - y0;
        float d = z1 - z0;
        // Frente (-Z)
        face(vc, m, nm, x0, y1, z0, 1, 0, 0, 0, -1, 0, w, h, 0, 0, -1, s, stretch, color, light, overlay);
        // Atras (+Z)
        if (d > 0f) {
            face(vc, m, nm, x1, y1, z1, -1, 0, 0, 0, -1, 0, w, h, 0, 0, 1, s, stretch, color, light, overlay);
        }
        // +X
        face(vc, m, nm, x1, y1, z0, 0, 0, 1, 0, -1, 0, d, h, 1, 0, 0, s, stretch, color, light, overlay);
        // -X
        if (w > 0f) {
            face(vc, m, nm, x0, y1, z1, 0, 0, -1, 0, -1, 0, d, h, -1, 0, 0, s, stretch, color, light, overlay);
        }
        // Arriba
        face(vc, m, nm, x0, y1, z1, 1, 0, 0, 0, 0, -1, w, d, 0, 1, 0, s, stretch, color, light, overlay);
        // Abajo
        if (h > 0f) {
            face(vc, m, nm, x0, y0, z0, 1, 0, 0, 0, 0, 1, w, d, 0, -1, 0, s, stretch, color, light, overlay);
        }
    }

    private static void face(VertexConsumer vc, Matrix4f m, Matrix3f nm,
                             float ox, float oy, float oz,
                             float ux, float uy, float uz,
                             float vx, float vy, float vz,
                             float lenU, float lenV,
                             float nx, float ny, float nz,
                             TextureAtlasSprite s, boolean stretch, int color, int light, int overlay) {
        if (lenU <= 0f || lenV <= 0f) {
            return;
        }
        Vector3f n = nm.transform(nx, ny, nz, NORMAL);
        if (n.lengthSquared() > 1.0e-12f) {
            n.normalize();
        }
        float nX = n.x();
        float nY = n.y();
        float nZ = n.z();
        float u0 = s.getU0();
        float u1 = s.getU1();
        float v0 = s.getV0();
        float v1 = s.getV1();
        if (stretch) {
            quad(vc, m, ox, oy, oz, ux, uy, uz, vx, vy, vz, 0f, lenU, 0f, lenV, u0, u1, v0, v1,
                    nX, nY, nZ, color, light, overlay);
            return;
        }
        float du = u1 - u0;
        float dv = v1 - v0;
        for (float a = 0f; a < lenU - 1.0e-4f; a += 16f) {
            float a1 = Math.min(a + 16f, lenU);
            float ub = u0 + du * ((a1 - a) / 16f);
            for (float b = 0f; b < lenV - 1.0e-4f; b += 16f) {
                float b1 = Math.min(b + 16f, lenV);
                float vb = v0 + dv * ((b1 - b) / 16f);
                quad(vc, m, ox, oy, oz, ux, uy, uz, vx, vy, vz, a, a1, b, b1, u0, ub, v0, vb,
                        nX, nY, nZ, color, light, overlay);
            }
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float ox, float oy, float oz,
                             float ux, float uy, float uz,
                             float vx, float vy, float vz,
                             float a0, float a1, float b0, float b1,
                             float ua, float ub, float va, float vb,
                             float nx, float ny, float nz, int color, int light, int overlay) {
        vertex(vc, m, ox + ux * a0 + vx * b0, oy + uy * a0 + vy * b0, oz + uz * a0 + vz * b0,
                color, ua, va, overlay, light, nx, ny, nz);
        vertex(vc, m, ox + ux * a0 + vx * b1, oy + uy * a0 + vy * b1, oz + uz * a0 + vz * b1,
                color, ua, vb, overlay, light, nx, ny, nz);
        vertex(vc, m, ox + ux * a1 + vx * b1, oy + uy * a1 + vy * b1, oz + uz * a1 + vz * b1,
                color, ub, vb, overlay, light, nx, ny, nz);
        vertex(vc, m, ox + ux * a1 + vx * b0, oy + uy * a1 + vy * b0, oz + uz * a1 + vz * b0,
                color, ub, va, overlay, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int color,
                               float u, float v, int overlay, int light, float nx, float ny, float nz) {
        Vector3f p = m.transformPosition(x, y, z, POS);
        vc.addVertex(p.x(), p.y(), p.z(), color, u, v, overlay, light, nx, ny, nz);
    }

    /** Multiplica dos colores ARGB canal a canal. */
    public static int multiply(int a, int b) {
        int alpha = ((a >>> 24) * (b >>> 24)) / 255;
        int red = (((a >> 16) & 255) * ((b >> 16) & 255)) / 255;
        int green = (((a >> 8) & 255) * ((b >> 8) & 255)) / 255;
        int blue = ((a & 255) * (b & 255)) / 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
