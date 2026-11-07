package com.creaturestudio.client;

import com.creaturestudio.CreatureStudio;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreaturePart;
import com.creaturestudio.data.MaterialIds;
import com.creaturestudio.data.PaintLayout;
import com.creaturestudio.data.PartAnim;
import com.creaturestudio.data.PoseSolver;
import com.creaturestudio.data.SkinLayout;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Los modelos de las entidades de Minecraft, convertidos a piezas del editor (forma, articulaciones,
 * giros y la textura original como pintura). Solo cliente.
 *
 * Minecraft usa Y hacia abajo y el suelo en Y=24; el editor usa Y hacia arriba y el suelo en 0,
 * asi que y' = 24 - y y los giros en X y Z cambian de signo.
 */
public final class VanillaModels {
    /** Un modelo de la lista: id = "minecraft:cow#main". */
    public record Entry(String id, ModelLayerLocation location, String model, String layer, Component name,
                        String searchText) {
    }

    @Nullable
    private static List<Entry> entries;
    private static final Map<String, Optional<CreatureDefinition>> CACHE = new HashMap<>();
    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static final Map<String, Method> METHODS = new HashMap<>();

    private VanillaModels() {
    }

    /** Todos los modelos conocidos (sin las capas de armadura), ordenados por nombre. */
    public static List<Entry> entries() {
        if (entries == null) {
            List<Entry> list = new ArrayList<>();
            try {
                ModelLayers.getKnownLocations().forEach(location -> {
                    Entry e = entry(location);
                    if (e != null) {
                        list.add(e);
                    }
                });
            } catch (RuntimeException | LinkageError e) {
                CreatureStudio.LOGGER.warn("No se pudo leer la lista de modelos de Minecraft: {}", e.toString());
            }
            list.sort(Comparator.comparing(e -> e.name().getString().toLowerCase(Locale.ROOT)));
            entries = list;
        }
        return entries;
    }

    @Nullable
    private static Entry entry(ModelLayerLocation location) {
        String id = location.toString();
        int hash = id.indexOf('#');
        String model = hash >= 0 ? id.substring(0, hash) : id;
        String layer = hash >= 0 ? id.substring(hash + 1) : "main";
        if (layer.contains("armor")) {
            return null; // copias del cuerpo humanoide para la armadura
        }
        ResourceLocation rl = ResourceLocation.tryParse(model);
        Component base = Component.literal(rl == null ? model : rl.getPath());
        if (rl != null) {
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(rl);
            if (type.isPresent()) {
                base = type.get().getDescription();
            }
        }
        Component name = layer.equals("main") ? base : Component.empty().append(base).append(" (" + layer + ")");
        String search = (name.getString() + " " + id).toLowerCase(Locale.ROOT);
        return new Entry(id, location, model, layer, name, search);
    }

    /** Modelos cuyo nombre o id contiene el texto (sin distinguir mayusculas). */
    public static List<Entry> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return entries();
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries()) {
            if (e.searchText().contains(q)) {
                out.add(e);
            }
        }
        return out;
    }

    /** El modelo convertido (se guarda en cache), o null si no se pudo convertir. No modificar el resultado. */
    @Nullable
    public static CreatureDefinition load(Entry e) {
        Optional<CreatureDefinition> cached = CACHE.get(e.id());
        if (cached == null) {
            cached = Optional.ofNullable(convert(e));
            CACHE.put(e.id(), cached);
        }
        return cached.orElse(null);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    // ---------------------------------------------------------------------------------------
    // Conversion
    // ---------------------------------------------------------------------------------------

    @Nullable
    private static CreatureDefinition convert(Entry e) {
        try {
            ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(e.location());
            Converter c = new Converter();
            ResourceLocation texture = findTexture(e.model(), e.layer());
            if (texture != null) {
                c.loadTexture(texture);
            }
            c.walk(root, "root", -1, 0f, 0f, 0f);
            if (c.parts.isEmpty()) {
                return null;
            }
            CreatureDefinition def = new CreatureDefinition();
            def.parts = c.parts;
            c.assignUv(def);
            def.name = e.name().getString();
            assignAnimations(def.parts, e.model());
            def.centerOnGround();
            return def;
        } catch (Exception | LinkageError ex) {
            CreatureStudio.LOGGER.warn("No se pudo convertir el modelo {}: {}", e.id(), ex.toString());
            return null;
        }
    }

    private static final class Converter {
        final List<CreaturePart> parts = new ArrayList<>();
        /** UV de Minecraft (0..1) del centro de cada texel de la pintura de cada pieza: {u[], v[]} o null. */
        final List<float[][]> samples = new ArrayList<>();
        @Nullable
        int[] tex;
        int texW;
        int texH;
        /** Tamano de la textura del modelo (en texels), deducido de los cubos. */
        float modelW;
        float modelH;

        void loadTexture(ResourceLocation location) {
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            Optional<Resource> res = rm.getResource(location);
            if (res.isEmpty()) {
                return;
            }
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                int w = img.getWidth();
                int h = img.getHeight();
                int[] px = new int[w * h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int abgr = img.getPixelRGBA(x, y);
                        px[y * w + x] = (abgr & 0xFF00FF00) | ((abgr >> 16) & 0xFF) | ((abgr & 0xFF) << 16);
                    }
                }
                tex = px;
                texW = w;
                texH = h;
            } catch (Exception ex) {
                CreatureStudio.LOGGER.warn("No se pudo leer la textura {}: {}", location, ex.toString());
            }
        }

        /** Recorre una pieza de Minecraft y sus hijas. (ax, ay, az) = posicion de la pieza padre. */
        void walk(ModelPart part, String name, int parent, float ax, float ay, float az)
                throws ReflectiveOperationException {
            if (parts.size() >= CreatureDefinition.MAX_PARTS) {
                return;
            }
            float px = ax + part.x;
            float py = ay + part.y;
            float pz = az + part.z;
            boolean rotated = part.xRot != 0f || part.yRot != 0f || part.zRot != 0f;
            List<?> cubes = (List<?>) get(part, "cubes");
            Map<?, ?> children = (Map<?, ?>) get(part, "children");
            int first = -1;
            int count = 0;
            for (Object cube : cubes) {
                if (parts.size() >= CreatureDefinition.MAX_PARTS) {
                    break;
                }
                String cubeName = count == 0 ? name : name + "_" + (count + 1);
                int index = addCube(cube, cubeName, part, px, py, pz, first < 0 ? parent : first, first < 0);
                if (first < 0) {
                    first = index;
                }
                count++;
            }
            boolean animated = animFor(name, false) != PartAnim.NONE;
            if (first < 0 && (rotated || animated) && !children.isEmpty() && parts.size() < CreatureDefinition.MAX_PARTS) {
                // Grupo sin cubos pero con giro o que Minecraft anima (la cabeza del lobo, por ejemplo): hace falta
                // una pieza vacia que lo lleve para que sus hijas giren con el.
                CreaturePart g = CreaturePart.box(name, px, 24f - py, pz, 0f, 0f, 0f);
                g.pivotAt(px, 24f - py, pz);
                setRotation(g, part);
                g.parent = parent;
                g.material = MaterialIds.CLEAR;
                parts.add(g);
                samples.add(null);
                first = parts.size() - 1;
            }
            int node = first >= 0 ? first : parent;
            for (Map.Entry<?, ?> child : children.entrySet()) {
                String childName = String.valueOf(child.getKey());
                if (hidden(childName, children)) {
                    continue;
                }
                walk((ModelPart) child.getValue(), childName, node, px, py, pz);
            }
        }

        private int addCube(Object cube, String name, ModelPart part, float px, float py, float pz, int parent,
                            boolean carriesRotation) throws ReflectiveOperationException {
            Object[] polygons = (Object[]) get(cube, "polygons");
            float mnx = Float.MAX_VALUE, mny = Float.MAX_VALUE, mnz = Float.MAX_VALUE;
            float mxx = -Float.MAX_VALUE, mxy = -Float.MAX_VALUE, mxz = -Float.MAX_VALUE;
            for (Object poly : polygons) {
                for (Object v : (Object[]) get(poly, "vertices")) {
                    Vector3f pos = (Vector3f) get(v, "pos");
                    mnx = Math.min(mnx, pos.x);
                    mny = Math.min(mny, pos.y);
                    mnz = Math.min(mnz, pos.z);
                    mxx = Math.max(mxx, pos.x);
                    mxy = Math.max(mxy, pos.y);
                    mxz = Math.max(mxz, pos.z);
                }
            }
            if (mnx > mxx) {
                mnx = mxx = mny = mxy = mnz = mxz = 0f;
            }
            CreaturePart p = CreaturePart.box(name, px + mnx, 24f - (py + mxy), pz + mnz,
                    mxx - mnx, mxy - mny, mxz - mnz);
            p.pivotAt(px, 24f - py, pz);
            if (carriesRotation) {
                setRotation(p, part);
            }
            p.parent = parent;
            p.color = 0xFFFFFF;
            float[][] uv = paint(p, cube, polygons, px, py, pz);
            if (tex != null) {
                // Con textura: lo transparente (capas exteriores como la chaqueta) queda invisible.
                p.material = MaterialIds.CLEAR;
                if (!p.hasPaint()) {
                    p.clearPaint();
                }
            } else {
                p.clearPaint();
                p.material = MaterialIds.SMOOTH;
                p.color = 0xC8C8C8;
            }
            parts.add(p);
            samples.add(uv);
            return parts.size() - 1;
        }

        /**
         * Copia la textura de Minecraft (si hay) en la pintura de la pieza, cara por cara, y devuelve la UV de cada
         * texel para poder aplicar despues otras skins con el mismo formato.
         */
        private float[][] paint(CreaturePart p, Object cube, Object[] polygons, float px, float py, float pz)
                throws ReflectiveOperationException {
            int[] t = tex;
            float dimX = number(cube, "maxX") - number(cube, "minX");
            float dimY = number(cube, "maxY") - number(cube, "minY");
            float dimZ = number(cube, "maxZ") - number(cube, "minZ");
            p.pw = dim(dimX);
            p.ph = dim(dimY);
            p.pd = dim(dimZ);
            p.uvW = uvDim(dimX);
            p.uvH = uvDim(dimY);
            p.uvD = uvDim(dimZ);
            p.paint = new int[PaintLayout.imageW(p.pw, p.pd) * PaintLayout.imageH(p.ph, p.pd)];
            float[] us = new float[p.paint.length];
            float[] vs = new float[p.paint.length];
            java.util.Arrays.fill(us, Float.NaN);
            int imageW = PaintLayout.imageW(p.pw, p.pd);
            for (Object poly : polygons) {
                Object[] verts = (Object[]) get(poly, "vertices");
                if (verts.length < 4) {
                    continue;
                }
                float[][] pos = new float[4][];
                float[][] uv = new float[4][];
                for (int k = 0; k < 4; k++) {
                    Vector3f v = (Vector3f) get(verts[k], "pos");
                    pos[k] = new float[]{v.x, v.y, v.z};
                    uv[k] = new float[]{number(verts[k], "u"), number(verts[k], "v")};
                }
                float[] e1 = sub(pos[1], pos[0]);
                float[] e3 = sub(pos[3], pos[0]);
                float l1 = dot(e1, e1);
                float l3 = dot(e3, e3);
                if (l1 < 1.0e-6f || l3 < 1.0e-6f) {
                    continue;
                }
                int face = faceOf(cross(e1, sub(pos[2], pos[0])));
                if (face < 0) {
                    continue;
                }
                learnTextureSize(face, uv, dimX, dimY, dimZ);
                int[] region = PaintLayout.region(face, p.pw, p.ph, p.pd);
                int fw = PaintLayout.faceW(face, p.pw, p.pd);
                int fh = PaintLayout.faceH(face, p.ph, p.pd);
                float[] fr = PaintLayout.frame(face, p.x, p.y, p.z, p.x + p.w, p.y + p.h, p.z + p.d);
                for (int j = 0; j < fh; j++) {
                    float tt = (j + 0.5f) / fh;
                    for (int i = 0; i < fw; i++) {
                        float ss = (i + 0.5f) / fw;
                        // Punto de la cara (editor) -> coordenadas locales de Minecraft
                        float qx = fr[0] + fr[3] * ss + fr[6] * tt - px;
                        float qy = (24f - (fr[1] + fr[4] * ss + fr[7] * tt)) - py;
                        float qz = fr[2] + fr[5] * ss + fr[8] * tt - pz;
                        float[] q = {qx - pos[0][0], qy - pos[0][1], qz - pos[0][2]};
                        float a = dot(q, e1) / l1;
                        float b = dot(q, e3) / l3;
                        float u = uv[0][0] + a * (uv[1][0] - uv[0][0]) + b * (uv[3][0] - uv[0][0]);
                        float v = uv[0][1] + a * (uv[1][1] - uv[0][1]) + b * (uv[3][1] - uv[0][1]);
                        int k = (region[1] + j) * imageW + region[0] + i;
                        us[k] = u;
                        vs[k] = v;
                        if (t == null) {
                            continue;
                        }
                        int tx = Math.max(0, Math.min(texW - 1, (int) Math.floor(u * texW)));
                        int ty = Math.max(0, Math.min(texH - 1, (int) Math.floor(v * texH)));
                        int c = t[ty * texW + tx];
                        if ((c >>> 24) >= 128) {
                            p.setTexel(face, i, j, 0xFF000000 | (c & 0xFFFFFF));
                        }
                    }
                }
            }
            return new float[][]{us, vs};
        }

        /** El tamano de la textura del modelo sale de cuanto ocupa una cara en UV frente a su tamano en pixeles. */
        private void learnTextureSize(int face, float[][] uv, float dimX, float dimY, float dimZ) {
            if (modelW > 0f && modelH > 0f) {
                return;
            }
            float u0 = Math.min(Math.min(uv[0][0], uv[1][0]), Math.min(uv[2][0], uv[3][0]));
            float u1 = Math.max(Math.max(uv[0][0], uv[1][0]), Math.max(uv[2][0], uv[3][0]));
            float v0 = Math.min(Math.min(uv[0][1], uv[1][1]), Math.min(uv[2][1], uv[3][1]));
            float v1 = Math.max(Math.max(uv[0][1], uv[1][1]), Math.max(uv[2][1], uv[3][1]));
            boolean side = face == PaintLayout.LEFT || face == PaintLayout.RIGHT;
            boolean cap = face == PaintLayout.TOP || face == PaintLayout.BOTTOM;
            float du = side ? dimZ : dimX;
            float dv = cap ? dimZ : dimY;
            if (modelW <= 0f && du >= 1f && u1 - u0 > 1.0e-5f) {
                modelW = Math.round(du / (u1 - u0));
            }
            if (modelH <= 0f && dv >= 1f && v1 - v0 > 1.0e-5f) {
                modelH = Math.round(dv / (v1 - v0));
            }
        }

        /**
         * Da a cada pieza su sitio en la skin (esquina y si va reflejada), comprobando texel a texel que coincide con
         * como lo pinta Minecraft. Las piezas que no encajan se quedan sin sitio.
         */
        void assignUv(CreatureDefinition def) {
            if (modelW < 8f || modelH < 8f) {
                return;
            }
            int tw = (int) modelW;
            int th = (int) modelH;
            boolean any = false;
            for (int n = 0; n < parts.size(); n++) {
                float[][] uv = samples.get(n);
                if (uv == null) {
                    continue;
                }
                CreaturePart p = parts.get(n);
                if (fitUv(p, uv, tw, th, false) || fitUv(p, uv, tw, th, true)) {
                    any = true;
                } else {
                    p.uvU = -1;
                    p.uvV = -1;
                }
            }
            if (any) {
                def.texW = tw;
                def.texH = th;
            }
        }

        private boolean fitUv(CreaturePart p, float[][] uv, int tw, int th, boolean mirror) {
            p.uvMirror = mirror;
            int imageW = PaintLayout.imageW(p.pw, p.pd);
            int total = 0;
            int good = 0;
            boolean placed = false;
            for (int f = 0; f < PaintLayout.FACES; f++) {
                int[] r = PaintLayout.region(f, p.pw, p.ph, p.pd);
                for (int j = 0; j < r[3]; j++) {
                    for (int i = 0; i < r[2]; i++) {
                        int k = (r[1] + j) * imageW + r[0] + i;
                        if (Float.isNaN(uv[0][k])) {
                            continue;
                        }
                        int sx = (int) Math.floor(uv[0][k] * tw + 1.0e-3f);
                        int sy = (int) Math.floor(uv[1][k] * th + 1.0e-3f);
                        if (!placed) {
                            // la esquina sale del primer texel: skinTexel = (uvU + ..., uvV + ...)
                            p.uvU = 0;
                            p.uvV = 0;
                            int[] rel = SkinLayout.skinTexel(p, f, i, j);
                            if (rel == null) {
                                continue;
                            }
                            p.uvU = sx - rel[0];
                            p.uvV = sy - rel[1];
                            placed = true;
                        }
                        int[] e = SkinLayout.skinTexel(p, f, i, j);
                        total++;
                        if (e != null && e[0] == sx && e[1] == sy) {
                            good++;
                        }
                    }
                }
            }
            return placed && p.uvU >= 0 && p.uvV >= 0 && total > 0 && good >= total * 0.97f;
        }
    }

    /** Cara del editor segun la normal en el espacio de Minecraft (Y hacia abajo). */
    private static int faceOf(float[] n) {
        float ax = Math.abs(n[0]);
        float ay = Math.abs(n[1]);
        float az = Math.abs(n[2]);
        if (ax < 1.0e-6f && ay < 1.0e-6f && az < 1.0e-6f) {
            return -1;
        }
        if (ax >= ay && ax >= az) {
            return n[0] > 0 ? PaintLayout.LEFT : PaintLayout.RIGHT;
        }
        if (ay >= az) {
            return n[1] < 0 ? PaintLayout.TOP : PaintLayout.BOTTOM;
        }
        return n[2] > 0 ? PaintLayout.BACK : PaintLayout.FRONT;
    }

    private static void setRotation(CreaturePart p, ModelPart part) {
        float toDeg = (float) (180.0 / Math.PI);
        p.rx = -part.xRot * toDeg;
        p.ry = part.yRot * toDeg;
        p.rz = -part.zRot * toDeg;
    }

    private static int dim(float size) {
        return Math.max(1, Math.min(PaintLayout.MAX_DIM, Math.round(size)));
    }

    private static int uvDim(float size) {
        return Math.max(0, Math.min(PaintLayout.MAX_DIM, Math.round(size)));
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /**
     * Animaciones del editor segun los nombres de las piezas de Minecraft y su jerarquia. Antes se cuelgan las piezas
     * que en el juego siguen a otra (ver FOLLOWS) y luego cada movimiento va solo en la pieza de mas arriba de su
     * cadena: en el caballo gira "head_parts" (cuello, cabeza, hocico y crin juntos), no solo "head".
     */
    static void assignAnimations(List<CreaturePart> parts) {
        assignAnimations(parts, "");
    }

    /** @param model id del modelo (p. ej. "minecraft:cod"): los peces no giran la cabeza por separado del cuerpo. */
    static void assignAnimations(List<CreaturePart> parts, String model) {
        ResourceLocation id = ResourceLocation.tryParse(model);
        String path = id == null ? model : id.getPath();
        boolean fish = path.equals("cod") || path.equals("salmon") || path.startsWith("pufferfish")
                || path.startsWith("tropical_fish") || path.equals("tadpole");
        boolean spider = false;
        for (CreaturePart p : parts) {
            if (p.name.contains("middle") && p.name.contains("leg")) {
                spider = true;
                break;
            }
        }
        attachFollowers(parts);
        PartAnim[] raw = new PartAnim[parts.size()];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = animFor(parts.get(i).name, spider);
        }
        int[] parent = PoseSolver.safeParents(parts);
        for (int i = 0; i < raw.length; i++) {
            PartAnim a = raw[i];
            if (a != PartAnim.NONE) {
                int steps = 0;
                for (int up = parent[i]; up >= 0 && steps <= raw.length; up = parent[up], steps++) {
                    if (family(raw[up]) == family(a)) {
                        a = PartAnim.NONE; // ya se mueve con su antecesora (la cabeza de dentro, la punta del ala...)
                        break;
                    }
                }
            }
            if (fish && a == PartAnim.HEAD) {
                a = PartAnim.NONE;
            }
            parts.get(i).anim = a;
        }
    }

    /** Cuelga cada pieza de FOLLOWS de su guia si estan al mismo nivel (sin cambiar como se ven). */
    static void attachFollowers(List<CreaturePart> parts) {
        for (int i = 0; i < parts.size(); i++) {
            String leaderName = FOLLOWS.get(parts.get(i).name);
            if (leaderName == null) {
                continue;
            }
            int leader = -1;
            for (int j = 0; j < parts.size(); j++) {
                if (j != i && parts.get(j).name.equals(leaderName)) {
                    leader = j;
                    break;
                }
            }
            if (leader < 0 || parts.get(leader).parent != parts.get(i).parent
                    || PoseSolver.isDescendant(parts, leader, i)) {
                continue; // ya esta colgada de ella (o de algo suyo), o no tiene a quien seguir
            }
            PoseSolver.reparentKeepingPose(parts, i, leader);
        }
    }

    /** Tipo de movimiento sin lado (las dos alas, las cuatro patas...). */
    private static int family(PartAnim a) {
        return switch (a) {
            case NONE -> -1;
            case WING_A, WING_B -> 1;
            case LEG_A, LEG_B -> 2;
            case ARM_A, ARM_B -> 3;
            case SIDE_LEG_A, SIDE_LEG_B -> 4;
            default -> 10 + a.ordinal();
        };
    }

    static PartAnim animFor(String name, boolean spider) {
        if (spider) {
            switch (name) {
                case "right_hind_leg", "left_middle_hind_leg", "right_middle_front_leg", "left_front_leg" -> {
                    return PartAnim.SIDE_LEG_A;
                }
                case "left_hind_leg", "right_middle_hind_leg", "left_middle_front_leg", "right_front_leg" -> {
                    return PartAnim.SIDE_LEG_B;
                }
                default -> {
                }
            }
        }
        switch (name) {
            case "head", "head_parts", "real_head" -> {
                return PartAnim.HEAD;
            }
            case "right_leg", "right_hind_leg", "left_front_leg" -> {
                return PartAnim.LEG_A;
            }
            case "left_leg", "left_hind_leg", "right_front_leg" -> {
                return PartAnim.LEG_B;
            }
            case "right_arm" -> {
                return PartAnim.ARM_A;
            }
            case "left_arm" -> {
                return PartAnim.ARM_B;
            }
            case "jaw", "mouth", "lower_jaw", "lower_beak" -> {
                return PartAnim.JAW;
            }
            default -> {
            }
        }
        if (name.endsWith("_head")) {
            return PartAnim.HEAD; // las tres cabezas del Wither
        }
        if (name.startsWith("tail") || name.equals("real_tail")) {
            return PartAnim.TAIL; // tail, tail0, tail1, tail_fin...
        }
        if (name.startsWith("left_wing")) {
            return PartAnim.WING_A; // left_wing, left_wing_base (fantasma)...
        }
        if (name.startsWith("right_wing")) {
            return PartAnim.WING_B;
        }
        return PartAnim.NONE;
    }

    /**
     * Piezas que Minecraft solo ensena en casos especiales: la capa y las orejas de deadmau5 del jugador, la silla, las
     * riendas y los cofres de caballos, burros, mulas, llamas y camellos.
     */
    private static final Set<String> HIDDEN = Set.of("cloak", "ear", "saddle", "head_saddle", "mouth_saddle_wrap",
            "left_saddle_mouth", "right_saddle_mouth", "left_saddle_line", "right_saddle_line", "bridle", "reins",
            "left_chest", "right_chest");

    /** true si la pieza no se importa (ver HIDDEN; tambien las piezas de cria y los brazos sueltos de los illagers). */
    static boolean hidden(String name, Map<?, ?> siblings) {
        if (HIDDEN.contains(name) || name.contains("baby")) {
            return true; // las patas de potro van encima de las de adulto
        }
        // Los illagers tienen los brazos cruzados y ademas los brazos sueltos (Minecraft solo ensena unos)
        return (name.equals("left_arm") || name.equals("right_arm")) && siblings.containsKey("arms");
    }

    /**
     * Piezas que en Minecraft no cuelgan de otra pero se mueven copiandola (el sombrero copia a la cabeza, las mangas
     * a los brazos, el pico de la gallina a su cabeza...): al importarlas se cuelgan de ella para que la sigan.
     */
    private static final Map<String, String> FOLLOWS = Map.ofEntries(
            Map.entry("hat", "head"), Map.entry("jacket", "body"),
            Map.entry("left_sleeve", "left_arm"), Map.entry("right_sleeve", "right_arm"),
            Map.entry("left_pants", "left_leg"), Map.entry("right_pants", "right_leg"),
            Map.entry("beak", "head"), Map.entry("red_thing", "head"), Map.entry("nose", "head"),
            Map.entry("left_ear", "head"), Map.entry("right_ear", "head"), Map.entry("upper_mouth", "head"),
            Map.entry("tail2", "tail1"));

    /** Texturas conocidas (textures/entity/...) de mobs cuyo nombre no coincide con el del modelo. */
    private static final Map<String, String> KNOWN = Map.ofEntries(
            Map.entry("player", "player/wide/steve"), Map.entry("player_slim", "player/slim/alex"),
            Map.entry("ender_dragon", "enderdragon/dragon"), Map.entry("elder_guardian", "guardian_elder"),
            Map.entry("magma_cube", "slime/magmacube"), Map.entry("mooshroom", "cow/red_mooshroom"),
            Map.entry("horse", "horse/horse_white"), Map.entry("donkey", "horse/donkey"), Map.entry("mule", "horse/mule"),
            Map.entry("skeleton_horse", "horse/horse_skeleton"), Map.entry("zombie_horse", "horse/horse_zombie"),
            Map.entry("cat", "cat/tabby"), Map.entry("ocelot", "cat/ocelot"), Map.entry("llama", "llama/creamy"),
            Map.entry("trader_llama", "llama/creamy"), Map.entry("parrot", "parrot/parrot_red_blue"),
            Map.entry("rabbit", "rabbit/brown"), Map.entry("axolotl", "axolotl/axolotl_lucy"),
            Map.entry("frog", "frog/temperate_frog"), Map.entry("turtle", "turtle/big_sea_turtle"),
            Map.entry("polar_bear", "bear/polarbear"), Map.entry("armor_stand", "armorstand/wood"),
            Map.entry("vex", "illager/vex"), Map.entry("evoker", "illager/evoker"), Map.entry("vindicator", "illager/vindicator"),
            Map.entry("pillager", "illager/pillager"), Map.entry("illusioner", "illager/illusioner"),
            Map.entry("ravager", "illager/ravager"), Map.entry("zoglin", "hoglin/zoglin"), Map.entry("drowned", "zombie/drowned"),
            Map.entry("husk", "zombie/husk"), Map.entry("stray", "skeleton/stray"), Map.entry("bogged", "skeleton/bogged"),
            Map.entry("wither_skeleton", "skeleton/wither_skeleton"), Map.entry("cod", "fish/cod"),
            Map.entry("salmon", "fish/salmon"), Map.entry("pufferfish_big", "fish/pufferfish"),
            Map.entry("pufferfish_medium", "fish/pufferfish"), Map.entry("pufferfish_small", "fish/pufferfish"),
            Map.entry("tropical_fish_large", "fish/tropical_b"), Map.entry("tropical_fish_small", "fish/tropical_a"),
            Map.entry("minecart", "minecart"), Map.entry("chest_minecart", "minecart"), Map.entry("furnace_minecart", "minecart"),
            Map.entry("hopper_minecart", "minecart"), Map.entry("tnt_minecart", "minecart"),
            Map.entry("spawner_minecart", "minecart"), Map.entry("command_block_minecart", "minecart"),
            Map.entry("glow_squid", "squid/glow_squid"), Map.entry("cave_spider", "spider/cave_spider"),
            Map.entry("zombified_piglin", "piglin/zombified_piglin"), Map.entry("piglin_brute", "piglin/piglin_brute"),
            Map.entry("evoker_fangs", "illager/evoker_fangs"), Map.entry("giant", "zombie/zombie"),
            Map.entry("snow_golem", "snow_golem"), Map.entry("wither_skull", "wither/wither"),
            Map.entry("llama_spit", "llama/spit"), Map.entry("leash_knot", "lead_knot"),
            Map.entry("shulker_bullet", "shulker/spark"), Map.entry("chest", "chest/normal"),
            Map.entry("double_chest_left", "chest/normal_left"), Map.entry("double_chest_right", "chest/normal_right"));

    /** Palabras de texturas que no son la principal (ojos, brillos, sillas...). */
    private static final String[] EXTRA_WORDS = {"eyes", "overlay", "glow", "saddle", "armor", "collar", "angry", "tame",
            "decor", "charging", "invulnerable", "shooting", "emissive", "markings", "outer", "baby", "crackiness",
            "pattern", "spots", "heart", "pivots", "bloom"};

    /** Busca la textura probable del modelo (las entidades suelen usar textures/entity/<nombre>...). */
    @Nullable
    static ResourceLocation findTexture(String model, String layer) {
        ResourceLocation rl = ResourceLocation.tryParse(model);
        if (rl == null) {
            return null;
        }
        String ns = rl.getNamespace();
        String path = rl.getPath();
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        boolean main = layer.equals("main");
        String known = KNOWN.get(path);
        List<String> candidates = new ArrayList<>();
        if (!main) {
            if (known != null) {
                candidates.add(known + "_" + layer);
                candidates.add(known + "_" + layer + "_layer");
            }
            candidates.add(path + "/" + name + "_" + layer);
            candidates.add(path + "_" + layer);
            candidates.add(path + "/" + layer);
        } else {
            if (known != null) {
                candidates.add(known);
            }
            candidates.add(path + "/" + name);
            candidates.add(path);
            for (String word : name.split("_")) {
                candidates.add(word + "/" + name);
            }
        }
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        for (String c : candidates) {
            ResourceLocation tex = ResourceLocation.tryParse(ns + ":textures/entity/" + c + ".png");
            if (tex != null && rm.getResource(tex).isPresent()) {
                return tex;
            }
        }
        // Ultimo recurso: la imagen que mejor encaje dentro de la carpeta del mob (p. ej. variantes de color).
        Set<String> folders = new LinkedHashSet<>();
        folders.add(path);
        if (known != null && known.contains("/")) {
            folders.add(known.substring(0, known.lastIndexOf('/')));
        }
        for (String word : name.split("_")) {
            folders.add(word);
        }
        ResourceLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        for (String folder : folders) {
            String base = "textures/entity/" + folder;
            Map<ResourceLocation, Resource> found;
            try {
                found = rm.listResources(base, loc -> loc.getPath().endsWith(".png"));
            } catch (RuntimeException ex) {
                continue;
            }
            for (ResourceLocation file : found.keySet()) {
                if (!file.getNamespace().equals(ns) || file.getPath().length() <= base.length() + 1) {
                    continue;
                }
                int score = score(file.getPath().substring(base.length() + 1), name, layer);
                if (score > bestScore) {
                    bestScore = score;
                    best = file;
                }
            }
        }
        return bestScore > -40 ? best : null;
    }

    /** Puntuacion de una imagen de la carpeta del mob (mayor = mas probable que sea su textura). */
    static int score(String file, String name, String layer) {
        String f = file.toLowerCase(Locale.ROOT);
        String fileName = f.substring(f.lastIndexOf('/') + 1).replace(".png", "");
        boolean main = layer.equals("main");
        int score = 0;
        if (fileName.equals(name)) {
            score += main ? 100 : 50;
        } else if (fileName.startsWith(name)) {
            score += 40;
        } else if (fileName.contains(name) || name.contains(fileName)) {
            score += 20;
        }
        boolean extra = false;
        for (String w : EXTRA_WORDS) {
            if (fileName.contains(w)) {
                extra = true;
                break;
            }
        }
        if (main) {
            // la textura principal no suele ser la de los ojos, brillos... ni estar en una subcarpeta
            score -= (extra ? 60 : 0) + (f.contains("/") ? 30 : 0);
        } else if (f.contains(layer)) {
            score += 80;
        } else if (extra) {
            score += 60;
        }
        return score - f.length() / 8;
    }

    // ---------------------------------------------------------------------------------------
    // Reflexion (los detalles internos de los modelos no son publicos)
    // ---------------------------------------------------------------------------------------

    private static float number(Object target, String name) throws ReflectiveOperationException {
        return ((Number) get(target, name)).floatValue();
    }

    /** Lee un campo (aunque sea privado) o, si no existe, llama al metodo sin argumentos con ese nombre. */
    private static Object get(Object target, String name) throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        String key = type.getName() + "#" + name;
        Field field = FIELDS.get(key);
        if (field == null && !FIELDS.containsKey(key)) {
            for (Class<?> k = type; k != null && field == null; k = k.getSuperclass()) {
                try {
                    field = k.getDeclaredField(name);
                    field.setAccessible(true);
                } catch (NoSuchFieldException ignored) {
                    // probar en la clase padre
                }
            }
            FIELDS.put(key, field);
        }
        if (field != null) {
            return field.get(target);
        }
        Method method = METHODS.get(key);
        if (method == null) {
            method = type.getMethod(name);
            method.setAccessible(true);
            METHODS.put(key, method);
        }
        return method.invoke(target);
    }
}
