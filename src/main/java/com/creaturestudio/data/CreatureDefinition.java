package com.creaturestudio.data;

import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Definicion completa de una criatura creada por el usuario: modelo + estadisticas + comportamiento.
 * Se guarda como JSON en la carpeta del mundo y se sincroniza con todos los clientes.
 */
public class CreatureDefinition {
    public static final int MAX_PARTS = 128;
    public static final int NAME_MAX = 32;

    public String id = "";
    public String name = "Nueva criatura";
    public String owner = "";
    public int revision = 0;

    // --- Estadisticas ---
    public float scale = 1f;
    public float health = 20f;
    public float damage = 3f;
    public float speed = 0.25f;
    public float armor = 0f;
    public float knockbackResistance = 0f;
    public float followRange = 24f;
    public int xp = 3;

    // --- Comportamiento ---
    public Behavior behavior = Behavior.PASSIVE;
    public Movement movement = Movement.WALK;
    public RangedType ranged = RangedType.NONE;
    public HitEffect hitEffect = HitEffect.NONE;
    public SoundPreset sound = SoundPreset.COW;
    public boolean fireImmune = false;
    public boolean burnsInSun = false;
    public String temptItem = "";

    // --- Botin ---
    public String dropItem = "";
    public int dropMin = 0;
    public int dropMax = 1;

    // --- Aparicion natural ---
    public boolean naturalSpawn = false;
    public SpawnTime spawnTime = SpawnTime.ANY;
    public String spawnBiomes = "";
    public int spawnChance = 10;
    public int spawnMax = 4;

    // --- Huevo ---
    public int eggColor1 = 0x7A5230;
    public int eggColor2 = 0xF2D16B;

    // --- Modelo ---
    public List<CreaturePart> parts = new ArrayList<>();

    public CreatureDefinition() {
    }

    public CreatureDefinition copy() {
        return CreatureJson.fromJson(CreatureJson.toJson(this));
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static boolean isValidId(String id) {
        return id != null && !id.isEmpty() && id.length() <= 40 && id.matches("[a-z0-9_]+");
    }

    public boolean isHostile() {
        return behavior == Behavior.HOSTILE;
    }

    /**
     * Caja envolvente del modelo en reposo, en pixeles: {minX, minY, minZ, maxX, maxY, maxZ}.
     * Tiene en cuenta las rotaciones y la jerarquia.
     */
    public float[] bounds() {
        return bounds(false);
    }

    /** @param ignoreWings si es true no cuenta las alas (para que la caja de colision no sea enorme). */
    public float[] bounds(boolean ignoreWings) {
        if (parts.isEmpty()) {
            return new float[]{-4, 0, -4, 4, 8, 4};
        }
        boolean allWings = true;
        for (CreaturePart p : parts) {
            if (p.anim != PartAnim.WING_A && p.anim != PartAnim.WING_B) {
                allWings = false;
                break;
            }
        }
        boolean skipWings = ignoreWings && !allWings;
        Matrix4f[] mats = PoseSolver.solve(parts, null, null);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        Vector3f v = new Vector3f();
        for (int i = 0; i < parts.size(); i++) {
            CreaturePart p = parts.get(i);
            if (skipWings && (p.anim == PartAnim.WING_A || p.anim == PartAnim.WING_B)) {
                continue;
            }
            for (int c = 0; c < 8; c++) {
                float cx = (c & 1) == 0 ? p.x : p.x + p.w;
                float cy = (c & 2) == 0 ? p.y : p.y + p.h;
                float cz = (c & 4) == 0 ? p.z : p.z + p.d;
                mats[i].transformPosition(cx, cy, cz, v);
                minX = Math.min(minX, v.x);
                minY = Math.min(minY, v.y);
                minZ = Math.min(minZ, v.z);
                maxX = Math.max(maxX, v.x);
                maxY = Math.max(maxY, v.y);
                maxZ = Math.max(maxZ, v.z);
            }
        }
        return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /** Ancho y alto de la caja de colision en bloques. */
    public float[] hitbox() {
        float[] b = bounds(true);
        // Como en vanilla, la base es cuadrada: se usa algo intermedio entre lo ancho y lo largo
        // para que una criatura alargada (un perro) no tenga una caja enorme.
        float sizeX = b[3] - b[0];
        float sizeZ = b[5] - b[2];
        float width = Math.max(Math.min(sizeX, sizeZ), (sizeX + sizeZ) * 0.5f * 0.8f) / 16f * scale;
        float height = Math.max(b[4], 1f) / 16f * scale;
        return new float[]{clamp(width, 0.2f, 6f), clamp(height, 0.2f, 10f)};
    }

    /** Desplaza todas las piezas para que el modelo quede centrado en X/Z y apoyado en el suelo. */
    public void centerOnGround() {
        if (parts.isEmpty()) {
            return;
        }
        float[] b = bounds();
        float dx = -(b[0] + b[3]) / 2f;
        float dz = -(b[2] + b[5]) / 2f;
        float dy = -b[1];
        dx = Math.round(dx * 4f) / 4f;
        dy = Math.round(dy * 4f) / 4f;
        dz = Math.round(dz * 4f) / 4f;
        for (CreaturePart p : parts) {
            p.translate(dx, dy, dz);
        }
    }

    /** Limpia y limita todos los valores (se usa en el servidor antes de guardar). */
    public void sanitize() {
        name = cleanText(name, NAME_MAX);
        if (name.isEmpty()) {
            name = "Criatura";
        }
        if (!isValidId(id)) {
            id = "";
        }
        if (owner == null) {
            owner = "";
        }
        scale = clamp(scale, 0.1f, 8f);
        health = clamp(health, 1f, 1024f);
        damage = clamp(damage, 0f, 200f);
        speed = clamp(speed, 0.01f, 1.5f);
        armor = clamp(armor, 0f, 30f);
        knockbackResistance = clamp(knockbackResistance, 0f, 1f);
        followRange = clamp(followRange, 4f, 64f);
        xp = clampInt(xp, 0, 1000);

        if (behavior == null) behavior = Behavior.PASSIVE;
        if (movement == null) movement = Movement.WALK;
        if (ranged == null) ranged = RangedType.NONE;
        if (hitEffect == null) hitEffect = HitEffect.NONE;
        if (sound == null) sound = SoundPreset.NONE;
        if (spawnTime == null) spawnTime = SpawnTime.ANY;

        temptItem = cleanResource(temptItem);
        dropItem = cleanResource(dropItem);
        dropMin = clampInt(dropMin, 0, 64);
        dropMax = clampInt(dropMax, dropMin, 64);

        spawnBiomes = cleanBiomes(spawnBiomes);
        spawnChance = clampInt(spawnChance, 1, 100);
        spawnMax = clampInt(spawnMax, 1, 32);

        eggColor1 &= 0xFFFFFF;
        eggColor2 &= 0xFFFFFF;

        if (parts == null) {
            parts = new ArrayList<>();
        }
        parts.removeIf(Objects::isNull);
        while (parts.size() > MAX_PARTS) {
            parts.remove(parts.size() - 1);
        }
        int n = parts.size();
        for (int i = 0; i < n; i++) {
            CreaturePart p = parts.get(i);
            p.name = cleanText(p.name, 24);
            if (p.name.isEmpty()) {
                p.name = "cubo";
            }
            if (p.parent < -1 || p.parent >= n || p.parent == i) {
                p.parent = -1;
            }
            p.x = clamp(p.x, -512f, 512f);
            p.y = clamp(p.y, -512f, 512f);
            p.z = clamp(p.z, -512f, 512f);
            p.w = clamp(p.w, 0f, 256f);
            p.h = clamp(p.h, 0f, 256f);
            p.d = clamp(p.d, 0f, 256f);
            p.px = clamp(p.px, -512f, 512f);
            p.py = clamp(p.py, -512f, 512f);
            p.pz = clamp(p.pz, -512f, 512f);
            p.rx = clamp(p.rx, -360f, 360f);
            p.ry = clamp(p.ry, -360f, 360f);
            p.rz = clamp(p.rz, -360f, 360f);
            p.color &= 0xFFFFFF;
            p.material = cleanMaterial(p.material);
            if (p.anim == null) {
                p.anim = PartAnim.NONE;
            }
            p.animAmount = clamp(p.animAmount, 0f, 5f);
            p.animSpeed = clamp(p.animSpeed, 0f, 5f);
        }
    }

    // ---------------------------------------------------------------------------------------

    public static float clamp(float v, float min, float max) {
        if (!(v >= min)) {
            return min; // tambien atrapa NaN
        }
        return Math.min(v, max);
    }

    public static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static String cleanText(String s, int max) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < max; i++) {
            char c = s.charAt(i);
            if (c >= 32 && c != '\u00A7' && c != 127) {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /** Devuelve un identificador "espacio:ruta" valido o "" si no lo es. */
    public static String cleanResource(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return "";
        }
        if (!s.contains(":")) {
            s = "minecraft:" + s;
        }
        return ResourceLocation.tryParse(s) == null ? "" : s;
    }

    /** Normaliza una textura: "stone" -> "minecraft:block/stone". */
    public static String cleanMaterial(String s) {
        if (s == null) {
            return MaterialIds.SOLID;
        }
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return MaterialIds.SOLID;
        }
        String ns = "minecraft";
        String path = s;
        int colon = s.indexOf(':');
        if (colon >= 0) {
            ns = s.substring(0, colon);
            path = s.substring(colon + 1);
        }
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }
        if (!path.contains("/")) {
            path = "block/" + path;
        }
        String full = ns + ":" + path;
        return full.length() > 128 || ResourceLocation.tryParse(full) == null ? MaterialIds.SOLID : full;
    }

    /** Lista de biomas o etiquetas separados por comas: "minecraft:plains, #minecraft:is_forest". */
    public static String cleanBiomes(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String raw : s.split(",")) {
            String t = raw.trim().toLowerCase(Locale.ROOT);
            boolean tag = t.startsWith("#");
            if (tag) {
                t = t.substring(1);
            }
            String clean = cleanResource(t);
            if (clean.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(tag ? "#" : "").append(clean);
        }
        String out = sb.toString();
        return out.length() > 512 ? out.substring(0, 512) : out;
    }
}
