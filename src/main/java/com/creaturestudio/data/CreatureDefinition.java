package com.creaturestudio.data;

import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
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
    /** Maximo de texels pintados a mano por criatura (sumando todas las piezas). */
    public static final int MAX_PAINT_TEXELS = 160_000;

    /** Tamano de la textura (skin) en la que estan las piezas con {@code uvU >= 0}; 0 si no hay. */
    public int texW;
    public int texH;
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

    // --- Reaparicion ---
    public boolean respawn = false;
    public SpawnTime respawnTime = SpawnTime.ANY;
    /** Segundos desde que muere hasta que vuelve. */
    public int respawnDelay = 30;
    /** true = vuelve donde aparecio por primera vez; false = donde murio. */
    public boolean respawnAtHome = false;
    /** Cuantas veces puede volver (0 = siempre). */
    public int respawnLimit = 0;

    // --- Cuerpo a cuerpo (la fuerza es damage y el efecto hitEffect) ---
    /** Alcance del golpe en bloques, desde el borde de su cuerpo. */
    public float meleeReach = 0.8f;
    /** Ticks entre golpe y golpe (20 = un segundo). */
    public int meleeInterval = 20;
    public float meleeKnockback = 0f;

    // --- A distancia (el tipo es ranged) ---
    public int rangedAccuracy = 60;
    public int shotCount = 1;
    public float rangedRange = 15f;
    /** Si el objetivo esta mas cerca que esto, ataca cuerpo a cuerpo (0 = nunca). */
    public float rangedMeleeRange = 0f;
    public int rangedMinDelay = 20;
    public int rangedMaxDelay = 40;
    public int burstCount = 1;
    public int burstRate = 5;
    public ShotSound fireSound = ShotSound.BOW;
    public ShotSound hitSound = ShotSound.ARROW_HIT;
    public ShotSound groundSound = ShotSound.ARROW_HIT;
    /** Se queda quieto apuntando mientras dispara (si no, dispara sin dejar de moverse). */
    public boolean aimWhileShooting = true;
    public IndirectFire indirect = IndirectFire.NO;

    // --- Proyectil ---
    public String projItem = "minecraft:arrow";
    public float projStrength = 4f;
    /** Tamano: 5 = normal, 10 = el doble. */
    public int projSize = 5;
    /** Velocidad: 10 = la de una bola de nieve. */
    public int projSpeed = 10;
    public boolean projGravity = true;
    public boolean projAccelerate = false;
    public ExplosionSize projExplode = ExplosionSize.NONE;
    public HitEffect projEffect = HitEffect.NONE;
    public TrailType projTrail = TrailType.NONE;
    public boolean projGlows = false;
    public float projKnockback = 0f;
    /** true = objeto en 3D orientado hacia donde vuela; false = dibujo plano que mira a la camara. */
    public boolean proj3D = true;
    public boolean projSpins = false;
    public boolean projSticks = true;

    // --- Resistencias en % (la del empuje es knockbackResistance, de 0 a 1) ---
    public int resistArrow = 0;
    public int resistMelee = 0;
    public int resistExplosion = 0;

    // --- Regeneracion (vida por segundo) ---
    public float healthRegen = 0f;
    public float combatRegen = 0f;

    // --- Alcance (la persecucion es followRange) ---
    public float visionRange = 24f;
    public boolean seeThroughWalls = false;
    public float wallRange = 24f;

    // --- Otros ---
    public boolean canDrown = true;
    public boolean noFallDamage = false;
    public boolean cobwebAffected = true;
    public boolean potionImmune = false;

    // --- Inventario ---
    public static final int MAX_DROPS = 10;
    public String equipHead = "";
    public String equipChest = "";
    public String equipLegs = "";
    public String equipFeet = "";
    public String equipMainHand = "";
    public String equipOffHand = "";
    public String equipShield = "";
    /** Probabilidad (%) de soltar cada pieza del equipo al morir. */
    public int equipDropChance = 0;
    /** Botin: hasta {@link #MAX_DROPS} objetos distintos, cada uno con su probabilidad. */
    public List<CreatureDrop> drops = new ArrayList<>();

    // --- Habilidades (pestana Habilidades) ---
    public Abilities abilities = new Abilities();

    // --- Animaciones propias (pestana Animar; las poses estan en cada pieza) ---
    public CustomAnims customAnims = new CustomAnims();

    // --- Aparicion natural ---
    public boolean naturalSpawn = false;
    public SpawnTime spawnTime = SpawnTime.ANY;
    public String spawnBiomes = "";
    public int spawnChance = 10;
    public int spawnMax = 4;
    /** Donde busca sitio (si no hay estructuras elegidas). */
    public SpawnPlace spawnPlace = SpawnPlace.SURFACE;
    /** Si no esta vacio, solo aparece dentro de estas estructuras (o de las de {@link #spawnStructureIds}). */
    public EnumSet<SpawnStructure> spawnStructures = EnumSet.noneOf(SpawnStructure.class);
    /** Otras estructuras (de otros mods), separadas por comas; las etiquetas empiezan por #. */
    public String spawnStructureIds = "";

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
            // Caja de la pieza y, para encuadrar la vista (no para la caja de colision), sus minibloques
            float bx0 = p.x;
            float by0 = p.y;
            float bz0 = p.z;
            float bx1 = p.x + p.w;
            float by1 = p.y + p.h;
            float bz1 = p.z + p.d;
            if (!ignoreWings && Voxels.count(p.voxels) > 0) {
                for (int k = 0; k + 1 < p.voxels.length; k += 2) {
                    int pos = p.voxels[k];
                    bx0 = Math.min(bx0, p.x + Voxels.x(pos));
                    by0 = Math.min(by0, p.y + Voxels.y(pos));
                    bz0 = Math.min(bz0, p.z + Voxels.z(pos));
                    bx1 = Math.max(bx1, p.x + Voxels.x(pos) + 1);
                    by1 = Math.max(by1, p.y + Voxels.y(pos) + 1);
                    bz1 = Math.max(bz1, p.z + Voxels.z(pos) + 1);
                }
            }
            for (int c = 0; c < 8; c++) {
                float cx = (c & 1) == 0 ? bx0 : bx1;
                float cy = (c & 2) == 0 ? by0 : by1;
                float cz = (c & 4) == 0 ? bz0 : bz1;
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
        followRange = clamp(followRange, 4f, 128f);
        xp = clampInt(xp, 0, 1000);

        if (behavior == null) behavior = Behavior.PASSIVE;
        if (movement == null) movement = Movement.WALK;
        if (ranged == null) ranged = RangedType.NONE;
        if (hitEffect == null) hitEffect = HitEffect.NONE;
        if (sound == null) sound = SoundPreset.NONE;
        if (spawnTime == null) spawnTime = SpawnTime.ANY;

        temptItem = cleanResource(temptItem);

        if (respawnTime == null) respawnTime = SpawnTime.ANY;
        respawnDelay = clampInt(respawnDelay, 1, 3600);
        respawnLimit = clampInt(respawnLimit, 0, 1000);

        meleeReach = clamp(meleeReach, 0f, 8f);
        meleeInterval = clampInt(meleeInterval, 4, 200);
        meleeKnockback = clamp(meleeKnockback, 0f, 10f);

        rangedAccuracy = clampInt(rangedAccuracy, 0, 100);
        shotCount = clampInt(shotCount, 1, 10);
        rangedRange = clamp(rangedRange, 2f, 64f);
        rangedMeleeRange = clamp(rangedMeleeRange, 0f, 16f);
        rangedMinDelay = clampInt(rangedMinDelay, 1, 400);
        rangedMaxDelay = clampInt(rangedMaxDelay, rangedMinDelay, 400);
        burstCount = clampInt(burstCount, 1, 20);
        burstRate = clampInt(burstRate, 1, 100);
        if (fireSound == null) fireSound = ShotSound.NONE;
        if (hitSound == null) hitSound = ShotSound.NONE;
        if (groundSound == null) groundSound = ShotSound.NONE;
        if (indirect == null) indirect = IndirectFire.NO;

        projItem = cleanResource(projItem);
        projStrength = clamp(projStrength, 0f, 200f);
        projSize = clampInt(projSize, 1, 20);
        projSpeed = clampInt(projSpeed, 1, 50);
        if (projExplode == null) projExplode = ExplosionSize.NONE;
        if (projEffect == null) projEffect = HitEffect.NONE;
        if (projTrail == null) projTrail = TrailType.NONE;
        projKnockback = clamp(projKnockback, 0f, 10f);

        resistArrow = clampInt(resistArrow, 0, 100);
        resistMelee = clampInt(resistMelee, 0, 100);
        resistExplosion = clampInt(resistExplosion, 0, 100);
        healthRegen = clamp(healthRegen, 0f, 100f);
        combatRegen = clamp(combatRegen, 0f, 100f);
        visionRange = clamp(visionRange, 1f, 128f);
        wallRange = clamp(wallRange, 1f, 128f);

        equipHead = cleanResource(equipHead);
        equipChest = cleanResource(equipChest);
        equipLegs = cleanResource(equipLegs);
        equipFeet = cleanResource(equipFeet);
        equipMainHand = cleanResource(equipMainHand);
        equipOffHand = cleanResource(equipOffHand);
        equipShield = cleanResource(equipShield);
        equipDropChance = clampInt(equipDropChance, 0, 100);
        if (drops == null) {
            drops = new ArrayList<>();
        }
        drops.removeIf(Objects::isNull);
        for (CreatureDrop drop : drops) {
            drop.item = cleanResource(drop.item);
            drop.min = clampInt(drop.min, 0, 64);
            drop.max = clampInt(drop.max, drop.min, 64);
            drop.chance = clampInt(drop.chance, 1, 100);
        }
        while (drops.size() > MAX_DROPS) {
            drops.remove(drops.size() - 1);
        }

        spawnBiomes = cleanBiomes(spawnBiomes);
        if (spawnPlace == null) spawnPlace = SpawnPlace.SURFACE;
        if (spawnStructures == null) spawnStructures = EnumSet.noneOf(SpawnStructure.class);
        spawnStructureIds = cleanBiomes(spawnStructureIds);
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
        sanitizePaint();
        int voxelBudget = Voxels.MAX_TOTAL;
        for (CreaturePart p : parts) {
            p.voxels = Voxels.clean(p.voxels, voxelBudget);
            voxelBudget -= Voxels.count(p.voxels);
        }
        if (abilities == null) {
            abilities = new Abilities();
        }
        abilities.sanitize();
        if (customAnims == null) {
            customAnims = new CustomAnims();
        }
        customAnims.sanitize(parts);
        if (texW < 8 || texH < 8 || texW > 4096 || texH > 4096) {
            texW = 0;
            texH = 0;
        }
        for (CreaturePart p : parts) {
            if (texW == 0 || !p.hasUv() || p.uvU >= texW || p.uvV >= texH) {
                p.clearUv();
            }
        }
    }

    /** Pintura: alfa 0 o 255 (como las texturas de recorte), sin capas vacias y con un limite total. */
    private void sanitizePaint() {
        int budget = MAX_PAINT_TEXELS;
        for (CreaturePart p : parts) {
            if (!p.validPaint()) {
                p.clearPaint();
                continue;
            }
            boolean any = false;
            for (int i = 0; i < p.paint.length; i++) {
                int c = p.paint[i];
                if ((c >>> 24) < 128) {
                    p.paint[i] = 0;
                } else {
                    p.paint[i] = 0xFF000000 | (c & 0xFFFFFF);
                    any = true;
                }
            }
            if (!any || p.paint.length > budget) {
                p.clearPaint();
            } else {
                budget -= p.paint.length;
            }
        }
    }

    /** true si alguna pieza tiene pintura a mano. */
    public boolean hasPaint() {
        for (CreaturePart p : parts) {
            if (p.hasPaint()) {
                return true;
            }
        }
        return false;
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
        // "Diamond Sword" -> "minecraft:diamond_sword"
        s = s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
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
    /** true si solo aparece dentro de estructuras. */
    public boolean spawnsInStructures() {
        return !spawnStructures.isEmpty() || !spawnStructureIds.isEmpty();
    }

    /** Todos los ids y etiquetas (#) de estructuras en los que puede aparecer. */
    public List<String> structureTargets() {
        List<String> out = new ArrayList<>();
        for (SpawnStructure st : spawnStructures) {
            out.add(st.target);
        }
        if (!spawnStructureIds.isEmpty()) {
            for (String t : spawnStructureIds.split(",")) {
                if (!t.isBlank() && !out.contains(t.trim())) {
                    out.add(t.trim());
                }
            }
        }
        return out;
    }

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
