package com.creaturestudio.client;

import com.creaturestudio.CreatureStudio;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreaturePart;
import com.creaturestudio.data.MaterialIds;
import com.creaturestudio.data.PaintLayout;
import com.creaturestudio.data.PartAnim;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
            def.name = e.name().getString();
            assignAnimations(def.parts);
            def.centerOnGround();
            return def;
        } catch (Exception | LinkageError ex) {
            CreatureStudio.LOGGER.warn("No se pudo convertir el modelo {}: {}", e.id(), ex.toString());
            return null;
        }
    }

    private static final class Converter {
        final List<CreaturePart> parts = new ArrayList<>();
        @Nullable
        int[] tex;
        int texW;
        int texH;

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
            if (first < 0 && rotated && !children.isEmpty() && parts.size() < CreatureDefinition.MAX_PARTS) {
                // Grupo sin cubos pero con giro: hace falta una pieza vacia que lo lleve.
                CreaturePart g = CreaturePart.box(name, px, 24f - py, pz, 0f, 0f, 0f);
                g.pivotAt(px, 24f - py, pz);
                setRotation(g, part);
                g.parent = parent;
                g.material = MaterialIds.CLEAR;
                parts.add(g);
                first = parts.size() - 1;
            }
            int node = first >= 0 ? first : parent;
            for (Map.Entry<?, ?> child : children.entrySet()) {
                walk((ModelPart) child.getValue(), String.valueOf(child.getKey()), node, px, py, pz);
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
            if (tex != null) {
                p.material = MaterialIds.CLEAR;
                paint(p, cube, polygons, px, py, pz);
                if (!p.hasPaint()) {
                    p.clearPaint();
                    p.material = MaterialIds.SMOOTH;
                    p.color = 0xC8C8C8;
                }
            } else {
                p.material = MaterialIds.SMOOTH;
                p.color = 0xC8C8C8;
            }
            parts.add(p);
            return parts.size() - 1;
        }

        /** Copia la textura de Minecraft en la pintura de la pieza, cara por cara. */
        private void paint(CreaturePart p, Object cube, Object[] polygons, float px, float py, float pz)
                throws ReflectiveOperationException {
            int[] t = tex;
            if (t == null) {
                return;
            }
            float dimX = number(cube, "maxX") - number(cube, "minX");
            float dimY = number(cube, "maxY") - number(cube, "minY");
            float dimZ = number(cube, "maxZ") - number(cube, "minZ");
            p.pw = dim(dimX);
            p.ph = dim(dimY);
            p.pd = dim(dimZ);
            p.paint = new int[PaintLayout.imageW(p.pw, p.pd) * PaintLayout.imageH(p.ph, p.pd)];
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
                        int tx = Math.max(0, Math.min(texW - 1, (int) Math.floor(u * texW)));
                        int ty = Math.max(0, Math.min(texH - 1, (int) Math.floor(v * texH)));
                        int c = t[ty * texW + tx];
                        if ((c >>> 24) >= 128) {
                            p.setTexel(face, i, j, 0xFF000000 | (c & 0xFFFFFF));
                        }
                    }
                }
            }
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

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Animaciones del editor segun el nombre de la pieza de Minecraft. */
    static void assignAnimations(List<CreaturePart> parts) {
        boolean spider = false;
        for (CreaturePart p : parts) {
            if (p.name.contains("middle") && p.name.contains("leg")) {
                spider = true;
                break;
            }
        }
        for (CreaturePart p : parts) {
            p.anim = animFor(p.name, spider);
        }
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
        return switch (name) {
            case "head" -> PartAnim.HEAD;
            case "right_leg", "right_hind_leg", "left_front_leg" -> PartAnim.LEG_A;
            case "left_leg", "left_hind_leg", "right_front_leg" -> PartAnim.LEG_B;
            case "right_arm" -> PartAnim.ARM_A;
            case "left_arm" -> PartAnim.ARM_B;
            case "tail" -> PartAnim.TAIL;
            case "left_wing" -> PartAnim.WING_A;
            case "right_wing" -> PartAnim.WING_B;
            case "jaw" -> PartAnim.JAW;
            default -> PartAnim.NONE;
        };
    }

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
        List<String> candidates = new ArrayList<>();
        if (path.equals("player")) {
            candidates.add("player/wide/steve");
        } else if (path.equals("player_slim")) {
            candidates.add("player/slim/alex");
        }
        if (!layer.equals("main")) {
            candidates.add(path + "/" + name + "_" + layer);
            candidates.add(path + "_" + layer);
        }
        candidates.add(path + "/" + name);
        candidates.add(path);
        for (String word : name.split("_")) {
            candidates.add(word + "/" + name);
        }
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        for (String c : candidates) {
            ResourceLocation tex = ResourceLocation.tryParse(ns + ":textures/entity/" + c + ".png");
            if (tex != null && rm.getResource(tex).isPresent()) {
                return tex;
            }
        }
        return null;
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
