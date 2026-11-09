package com.creaturestudio.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.Base64;
import java.util.Locale;

/** Conversion de definiciones a JSON y viceversa (tolerante a campos que falten). */
public final class CreatureJson {
    public static final int FORMAT = 1;
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private CreatureJson() {
    }

    public static String write(CreatureDefinition d, boolean pretty) {
        return (pretty ? PRETTY : COMPACT).toJson(toJson(d));
    }

    public static CreatureDefinition read(String json) {
        JsonElement e = JsonParser.parseString(json);
        if (!e.isJsonObject()) {
            throw new JsonParseException("Se esperaba un objeto JSON");
        }
        return fromJson(e.getAsJsonObject());
    }

    public static JsonObject toJson(CreatureDefinition d) {
        JsonObject o = new JsonObject();
        o.addProperty("format", FORMAT);
        o.addProperty("id", d.id);
        o.addProperty("name", d.name);
        o.addProperty("owner", d.owner);
        o.addProperty("revision", d.revision);

        o.addProperty("scale", d.scale);
        o.addProperty("health", d.health);
        o.addProperty("damage", d.damage);
        o.addProperty("speed", d.speed);
        o.addProperty("armor", d.armor);
        o.addProperty("knockbackResistance", d.knockbackResistance);
        o.addProperty("followRange", d.followRange);
        o.addProperty("xp", d.xp);

        o.addProperty("behavior", d.behavior.name());
        o.addProperty("movement", d.movement.name());
        o.addProperty("ranged", d.ranged.name());
        o.addProperty("hitEffect", d.hitEffect.name());
        o.addProperty("sound", d.sound.name());
        o.addProperty("fireImmune", d.fireImmune);
        o.addProperty("burnsInSun", d.burnsInSun);
        o.addProperty("temptItem", d.temptItem);

        JsonObject respawn = new JsonObject();
        respawn.addProperty("enabled", d.respawn);
        respawn.addProperty("time", d.respawnTime.name());
        respawn.addProperty("delay", d.respawnDelay);
        respawn.addProperty("atHome", d.respawnAtHome);
        respawn.addProperty("limit", d.respawnLimit);
        o.add("respawn", respawn);

        JsonObject melee = new JsonObject();
        melee.addProperty("reach", d.meleeReach);
        melee.addProperty("interval", d.meleeInterval);
        melee.addProperty("knockback", d.meleeKnockback);
        o.add("melee", melee);

        JsonObject ranged = new JsonObject();
        ranged.addProperty("accuracy", d.rangedAccuracy);
        ranged.addProperty("shots", d.shotCount);
        ranged.addProperty("range", d.rangedRange);
        ranged.addProperty("meleeRange", d.rangedMeleeRange);
        ranged.addProperty("minDelay", d.rangedMinDelay);
        ranged.addProperty("maxDelay", d.rangedMaxDelay);
        ranged.addProperty("burst", d.burstCount);
        ranged.addProperty("burstRate", d.burstRate);
        ranged.addProperty("fireSound", d.fireSound.name());
        ranged.addProperty("hitSound", d.hitSound.name());
        ranged.addProperty("groundSound", d.groundSound.name());
        ranged.addProperty("aim", d.aimWhileShooting);
        ranged.addProperty("indirect", d.indirect.name());
        o.add("rangedAttack", ranged);

        JsonObject proj = new JsonObject();
        proj.addProperty("item", d.projItem);
        proj.addProperty("strength", d.projStrength);
        proj.addProperty("size", d.projSize);
        proj.addProperty("speed", d.projSpeed);
        proj.addProperty("gravity", d.projGravity);
        proj.addProperty("accelerate", d.projAccelerate);
        proj.addProperty("explode", d.projExplode.name());
        proj.addProperty("effect", d.projEffect.name());
        proj.addProperty("trail", d.projTrail.name());
        proj.addProperty("glows", d.projGlows);
        proj.addProperty("knockback", d.projKnockback);
        proj.addProperty("3d", d.proj3D);
        proj.addProperty("spins", d.projSpins);
        proj.addProperty("sticks", d.projSticks);
        o.add("projectile", proj);

        JsonObject resist = new JsonObject();
        resist.addProperty("arrow", d.resistArrow);
        resist.addProperty("melee", d.resistMelee);
        resist.addProperty("explosion", d.resistExplosion);
        o.add("resist", resist);

        JsonObject regen = new JsonObject();
        regen.addProperty("health", d.healthRegen);
        regen.addProperty("combat", d.combatRegen);
        o.add("regen", regen);

        JsonObject vision = new JsonObject();
        vision.addProperty("range", d.visionRange);
        vision.addProperty("throughWalls", d.seeThroughWalls);
        vision.addProperty("wallRange", d.wallRange);
        o.add("vision", vision);

        JsonObject flags = new JsonObject();
        flags.addProperty("canDrown", d.canDrown);
        flags.addProperty("noFallDamage", d.noFallDamage);
        flags.addProperty("cobweb", d.cobwebAffected);
        flags.addProperty("potionImmune", d.potionImmune);
        o.add("flags", flags);

        JsonObject equip = new JsonObject();
        equip.addProperty("head", d.equipHead);
        equip.addProperty("chest", d.equipChest);
        equip.addProperty("legs", d.equipLegs);
        equip.addProperty("feet", d.equipFeet);
        equip.addProperty("mainHand", d.equipMainHand);
        equip.addProperty("offHand", d.equipOffHand);
        equip.addProperty("shield", d.equipShield);
        equip.addProperty("dropChance", d.equipDropChance);
        o.add("equipment", equip);
        o.add("abilities", d.abilities.toJson());
        if (d.customAnims.any()) {
            o.add("customAnims", d.customAnims.toJson());
        }

        JsonArray drops = new JsonArray();
        for (CreatureDrop drop : d.drops) {
            JsonObject j = new JsonObject();
            j.addProperty("item", drop.item);
            j.addProperty("min", drop.min);
            j.addProperty("max", drop.max);
            j.addProperty("chance", drop.chance);
            drops.add(j);
        }
        o.add("drops", drops);

        JsonObject spawn = new JsonObject();
        spawn.addProperty("natural", d.naturalSpawn);
        spawn.addProperty("time", d.spawnTime.name());
        spawn.addProperty("biomes", d.spawnBiomes);
        spawn.addProperty("chance", d.spawnChance);
        spawn.addProperty("max", d.spawnMax);
        spawn.addProperty("place", d.spawnPlace.key());
        JsonArray structures = new JsonArray();
        for (SpawnStructure st : d.spawnStructures) {
            structures.add(st.key());
        }
        spawn.add("structures", structures);
        spawn.addProperty("structureIds", d.spawnStructureIds);
        o.add("spawn", spawn);

        JsonArray egg = new JsonArray();
        egg.add(hex(d.eggColor1));
        egg.add(hex(d.eggColor2));
        o.add("eggColors", egg);

        JsonArray parts = new JsonArray();
        for (CreaturePart p : d.parts) {
            JsonObject j = new JsonObject();
            j.addProperty("name", p.name);
            j.addProperty("parent", p.parent);
            j.add("from", vec(p.x, p.y, p.z));
            j.add("size", vec(p.w, p.h, p.d));
            j.add("pivot", vec(p.px, p.py, p.pz));
            j.add("rotation", vec(p.rx, p.ry, p.rz));
            j.addProperty("color", hex(p.color));
            j.addProperty("material", p.material);
            j.addProperty("stretch", p.stretch);
            j.addProperty("glow", p.glow);
            j.addProperty("animation", p.anim.name());
            j.addProperty("animAmount", p.animAmount);
            j.addProperty("animSpeed", p.animSpeed);
            if (p.hasUv()) {
                JsonArray uv = new JsonArray();
                uv.add(p.uvU);
                uv.add(p.uvV);
                uv.add(p.uvMirror ? 1 : 0);
                uv.add(p.uvW);
                uv.add(p.uvH);
                uv.add(p.uvD);
                j.add("uv", uv);
            }
            if (p.validPaint()) {
                JsonArray size = new JsonArray();
                size.add(p.pw);
                size.add(p.ph);
                size.add(p.pd);
                j.add("paintSize", size);
                j.addProperty("paint", encodePaint(p.paint));
            }
            if (Voxels.count(p.voxels) > 0) {
                // sin tamanos, como antes (lo siguen abriendo versiones anteriores); con tamanos, en "voxels2"
                if (Voxels.legacy(p.voxels)) {
                    j.addProperty("voxels", Voxels.encodeLegacy(p.voxels));
                } else {
                    j.addProperty("voxels2", Voxels.encode(p.voxels));
                }
            }
            JsonObject moves = d.customAnims.partToJson(p);
            if (moves != null) {
                j.add("moves", moves);
            }
            parts.add(j);
        }
        o.add("parts", parts);
        if (d.texW > 0 && d.texH > 0) {
            JsonArray tex = new JsonArray();
            tex.add(d.texW);
            tex.add(d.texH);
            o.add("textureSize", tex);
        }
        return o;
    }

    public static CreatureDefinition fromJson(JsonObject o) {
        CreatureDefinition d = new CreatureDefinition();
        d.id = str(o, "id", "");
        d.name = str(o, "name", d.name);
        d.owner = str(o, "owner", "");
        d.revision = integer(o, "revision", 0);

        d.scale = num(o, "scale", d.scale);
        d.health = num(o, "health", d.health);
        d.damage = num(o, "damage", d.damage);
        d.speed = num(o, "speed", d.speed);
        d.armor = num(o, "armor", d.armor);
        d.knockbackResistance = num(o, "knockbackResistance", d.knockbackResistance);
        d.followRange = num(o, "followRange", d.followRange);
        d.xp = integer(o, "xp", d.xp);

        d.behavior = enumValue(o, "behavior", Behavior.class, d.behavior);
        d.movement = enumValue(o, "movement", Movement.class, d.movement);
        d.ranged = enumValue(o, "ranged", RangedType.class, d.ranged);
        d.hitEffect = enumValue(o, "hitEffect", HitEffect.class, d.hitEffect);
        d.sound = enumValue(o, "sound", SoundPreset.class, d.sound);
        d.fireImmune = bool(o, "fireImmune", false);
        d.burnsInSun = bool(o, "burnsInSun", false);
        d.temptItem = str(o, "temptItem", "");

        // Version anterior: un solo objeto de botin
        JsonObject drop = obj(o, "drop");
        if (drop != null && !o.has("drops")) {
            String item = str(drop, "item", "");
            int max = integer(drop, "max", 1);
            if (!item.isBlank() && max > 0) {
                d.drops.add(new CreatureDrop(item, integer(drop, "min", 0), max, 100));
            }
        }
        if (o.has("drops") && o.get("drops").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("drops")) {
                if (e.isJsonObject() && d.drops.size() < CreatureDefinition.MAX_DROPS) {
                    JsonObject j = e.getAsJsonObject();
                    d.drops.add(new CreatureDrop(str(j, "item", ""), integer(j, "min", 1), integer(j, "max", 1),
                            integer(j, "chance", 100)));
                }
            }
        }

        JsonObject respawn = obj(o, "respawn");
        if (respawn != null) {
            d.respawn = bool(respawn, "enabled", false);
            d.respawnTime = enumValue(respawn, "time", SpawnTime.class, d.respawnTime);
            d.respawnDelay = integer(respawn, "delay", d.respawnDelay);
            d.respawnAtHome = bool(respawn, "atHome", false);
            d.respawnLimit = integer(respawn, "limit", 0);
        }

        JsonObject melee = obj(o, "melee");
        if (melee != null) {
            d.meleeReach = num(melee, "reach", d.meleeReach);
            d.meleeInterval = integer(melee, "interval", d.meleeInterval);
            d.meleeKnockback = num(melee, "knockback", d.meleeKnockback);
        }

        JsonObject ranged = obj(o, "rangedAttack");
        JsonObject proj = obj(o, "projectile");
        if (proj == null) {
            // Criatura de una version anterior: el proyectil sale de su tipo de ataque
            RangedPresets.apply(d, d.ranged);
        }
        if (ranged != null) {
            d.rangedAccuracy = integer(ranged, "accuracy", d.rangedAccuracy);
            d.shotCount = integer(ranged, "shots", d.shotCount);
            d.rangedRange = num(ranged, "range", d.rangedRange);
            d.rangedMeleeRange = num(ranged, "meleeRange", d.rangedMeleeRange);
            d.rangedMinDelay = integer(ranged, "minDelay", d.rangedMinDelay);
            d.rangedMaxDelay = integer(ranged, "maxDelay", d.rangedMaxDelay);
            d.burstCount = integer(ranged, "burst", d.burstCount);
            d.burstRate = integer(ranged, "burstRate", d.burstRate);
            d.fireSound = enumValue(ranged, "fireSound", ShotSound.class, d.fireSound);
            d.hitSound = enumValue(ranged, "hitSound", ShotSound.class, d.hitSound);
            d.groundSound = enumValue(ranged, "groundSound", ShotSound.class, d.groundSound);
            d.aimWhileShooting = bool(ranged, "aim", true);
            d.indirect = enumValue(ranged, "indirect", IndirectFire.class, d.indirect);
        }
        if (proj != null) {
            d.projItem = str(proj, "item", d.projItem);
            d.projStrength = num(proj, "strength", d.projStrength);
            d.projSize = integer(proj, "size", d.projSize);
            d.projSpeed = integer(proj, "speed", d.projSpeed);
            d.projGravity = bool(proj, "gravity", d.projGravity);
            d.projAccelerate = bool(proj, "accelerate", d.projAccelerate);
            d.projExplode = enumValue(proj, "explode", ExplosionSize.class, d.projExplode);
            d.projEffect = enumValue(proj, "effect", HitEffect.class, d.projEffect);
            d.projTrail = enumValue(proj, "trail", TrailType.class, d.projTrail);
            d.projGlows = bool(proj, "glows", d.projGlows);
            d.projKnockback = num(proj, "knockback", d.projKnockback);
            d.proj3D = bool(proj, "3d", d.proj3D);
            d.projSpins = bool(proj, "spins", d.projSpins);
            d.projSticks = bool(proj, "sticks", d.projSticks);
        }

        JsonObject resist = obj(o, "resist");
        if (resist != null) {
            d.resistArrow = integer(resist, "arrow", 0);
            d.resistMelee = integer(resist, "melee", 0);
            d.resistExplosion = integer(resist, "explosion", 0);
        }

        JsonObject regen = obj(o, "regen");
        if (regen != null) {
            d.healthRegen = num(regen, "health", 0f);
            d.combatRegen = num(regen, "combat", 0f);
        }

        JsonObject vision = obj(o, "vision");
        if (vision != null) {
            d.visionRange = num(vision, "range", d.visionRange);
            d.seeThroughWalls = bool(vision, "throughWalls", false);
            d.wallRange = num(vision, "wallRange", d.wallRange);
        } else {
            d.visionRange = d.followRange; // antes la vision era el rango de seguimiento
        }

        JsonObject flags = obj(o, "flags");
        if (flags != null) {
            d.canDrown = bool(flags, "canDrown", true);
            d.noFallDamage = bool(flags, "noFallDamage", false);
            d.cobwebAffected = bool(flags, "cobweb", true);
            d.potionImmune = bool(flags, "potionImmune", false);
        }

        JsonObject abilities = obj(o, "abilities");
        d.abilities = abilities != null ? Abilities.fromJson(abilities) : new Abilities();
        d.customAnims = CustomAnims.fromJson(obj(o, "customAnims"));

        JsonObject equip = obj(o, "equipment");
        if (equip != null) {
            d.equipHead = str(equip, "head", "");
            d.equipChest = str(equip, "chest", "");
            d.equipLegs = str(equip, "legs", "");
            d.equipFeet = str(equip, "feet", "");
            d.equipMainHand = str(equip, "mainHand", "");
            d.equipOffHand = str(equip, "offHand", "");
            d.equipShield = str(equip, "shield", "");
            d.equipDropChance = integer(equip, "dropChance", 0);
        }

        JsonObject spawn = obj(o, "spawn");
        if (spawn != null) {
            d.naturalSpawn = bool(spawn, "natural", false);
            d.spawnTime = enumValue(spawn, "time", SpawnTime.class, d.spawnTime);
            d.spawnBiomes = str(spawn, "biomes", "");
            d.spawnChance = integer(spawn, "chance", d.spawnChance);
            d.spawnMax = integer(spawn, "max", d.spawnMax);
            d.spawnPlace = SpawnPlace.byKey(str(spawn, "place", ""), SpawnPlace.SURFACE);
            d.spawnStructures = java.util.EnumSet.noneOf(SpawnStructure.class);
            if (spawn.has("structures") && spawn.get("structures").isJsonArray()) {
                for (JsonElement e : spawn.getAsJsonArray("structures")) {
                    if (e.isJsonPrimitive()) {
                        SpawnStructure st = SpawnStructure.byKey(e.getAsString());
                        if (st != null) {
                            d.spawnStructures.add(st);
                        }
                    }
                }
            }
            d.spawnStructureIds = str(spawn, "structureIds", "");
        }

        if (o.has("eggColors") && o.get("eggColors").isJsonArray()) {
            JsonArray egg = o.getAsJsonArray("eggColors");
            if (egg.size() > 0) d.eggColor1 = parseColor(egg.get(0), d.eggColor1);
            if (egg.size() > 1) d.eggColor2 = parseColor(egg.get(1), d.eggColor2);
        }

        if (o.has("textureSize") && o.get("textureSize").isJsonArray() && o.getAsJsonArray("textureSize").size() == 2) {
            try {
                d.texW = o.getAsJsonArray("textureSize").get(0).getAsInt();
                d.texH = o.getAsJsonArray("textureSize").get(1).getAsInt();
            } catch (RuntimeException e) {
                d.texW = 0;
                d.texH = 0;
            }
        }

        d.parts.clear();
        if (o.has("parts") && o.get("parts").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("parts")) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject j = e.getAsJsonObject();
                CreaturePart p = new CreaturePart();
                p.name = str(j, "name", p.name);
                p.parent = integer(j, "parent", -1);
                float[] from = vec(j, "from", new float[]{p.x, p.y, p.z});
                float[] size = vec(j, "size", new float[]{p.w, p.h, p.d});
                float[] pivot = vec(j, "pivot", new float[]{p.px, p.py, p.pz});
                float[] rot = vec(j, "rotation", new float[]{0, 0, 0});
                p.x = from[0];
                p.y = from[1];
                p.z = from[2];
                p.w = size[0];
                p.h = size[1];
                p.d = size[2];
                p.px = pivot[0];
                p.py = pivot[1];
                p.pz = pivot[2];
                p.rx = rot[0];
                p.ry = rot[1];
                p.rz = rot[2];
                p.color = j.has("color") ? parseColor(j.get("color"), p.color) : p.color;
                p.material = str(j, "material", p.material);
                p.stretch = bool(j, "stretch", false);
                p.glow = bool(j, "glow", false);
                p.anim = enumValue(j, "animation", PartAnim.class, PartAnim.NONE);
                p.animAmount = num(j, "animAmount", 1f);
                p.animSpeed = num(j, "animSpeed", 1f);
                readPaint(j, p);
                readUv(j, p);
                if (j.has("voxels2") && j.get("voxels2").isJsonPrimitive()) {
                    p.voxels = Voxels.decode(j.get("voxels2").getAsString());
                } else if (j.has("voxels") && j.get("voxels").isJsonPrimitive()) {
                    p.voxels = Voxels.decodeLegacy(j.get("voxels").getAsString());
                }
                CustomAnims.partFromJson(p, obj(j, "moves"));
                d.parts.add(p);
            }
        }
        return d;
    }

    // ---------------------------------------------------------------------------------------
    // Pintura: RGBA en Base64
    // ---------------------------------------------------------------------------------------

    static String encodePaint(int[] argb) {
        byte[] bytes = new byte[argb.length * 4];
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            bytes[i * 4] = (byte) (c >> 16);
            bytes[i * 4 + 1] = (byte) (c >> 8);
            bytes[i * 4 + 2] = (byte) c;
            bytes[i * 4 + 3] = (byte) (c >>> 24);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    static int[] decodePaint(String text) {
        byte[] bytes = Base64.getDecoder().decode(text);
        int[] out = new int[bytes.length / 4];
        for (int i = 0; i < out.length; i++) {
            out[i] = (bytes[i * 4 + 3] & 255) << 24 | (bytes[i * 4] & 255) << 16
                    | (bytes[i * 4 + 1] & 255) << 8 | (bytes[i * 4 + 2] & 255);
        }
        return out;
    }

    private static void readUv(JsonObject j, CreaturePart p) {
        try {
            if (j.has("uv") && j.get("uv").isJsonArray() && j.getAsJsonArray("uv").size() == 6) {
                JsonArray uv = j.getAsJsonArray("uv");
                p.uvU = uv.get(0).getAsInt();
                p.uvV = uv.get(1).getAsInt();
                p.uvMirror = uv.get(2).getAsInt() != 0;
                p.uvW = uv.get(3).getAsInt();
                p.uvH = uv.get(4).getAsInt();
                p.uvD = uv.get(5).getAsInt();
            }
        } catch (RuntimeException e) {
            p.uvU = -1;
            p.uvV = -1;
        }
    }

    private static void readPaint(JsonObject j, CreaturePart p) {
        if (!j.has("paint") || !j.has("paintSize") || !j.get("paintSize").isJsonArray()) {
            return;
        }
        try {
            JsonArray size = j.getAsJsonArray("paintSize");
            if (size.size() != 3) {
                return;
            }
            p.pw = size.get(0).getAsInt();
            p.ph = size.get(1).getAsInt();
            p.pd = size.get(2).getAsInt();
            p.paint = decodePaint(j.get("paint").getAsString());
            if (!p.validPaint()) {
                p.clearPaint();
            }
        } catch (RuntimeException e) {
            p.clearPaint();
        }
    }

    // ---------------------------------------------------------------------------------------

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Acepta "#RRGGBB", "RRGGBB", "0xRRGGBB" o un numero. Devuelve -1 si no es valido. */
    public static int parseHex(String s) {
        if (s == null) {
            return -1;
        }
        String t = s.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        } else if (t.startsWith("0x") || t.startsWith("0X")) {
            t = t.substring(2);
        }
        if (t.length() == 3) {
            t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
        }
        if (t.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(t, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int parseColor(JsonElement e, int def) {
        try {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                return e.getAsInt() & 0xFFFFFF;
            }
            int c = parseHex(e.getAsString());
            return c < 0 ? def : c;
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private static JsonArray vec(float a, float b, float c) {
        JsonArray arr = new JsonArray();
        arr.add(round(a));
        arr.add(round(b));
        arr.add(round(c));
        return arr;
    }

    private static float round(float v) {
        return Math.round(v * 1000f) / 1000f;
    }

    private static float[] vec(JsonObject o, String key, float[] def) {
        try {
            if (o.has(key) && o.get(key).isJsonArray()) {
                JsonArray arr = o.getAsJsonArray(key);
                float[] out = def.clone();
                for (int i = 0; i < Math.min(3, arr.size()); i++) {
                    out[i] = arr.get(i).getAsFloat();
                }
                return out;
            }
        } catch (RuntimeException ignored) {
        }
        return def;
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }

    private static String str(JsonObject o, String key, String def) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static float num(JsonObject o, String key, float def) {
        try {
            return o.has(key) ? o.get(key).getAsFloat() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static int integer(JsonObject o, String key, int def) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        try {
            return o.has(key) ? o.get(key).getAsBoolean() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static <E extends Enum<E>> E enumValue(JsonObject o, String key, Class<E> type, E def) {
        try {
            if (o.has(key)) {
                return Enum.valueOf(type, o.get(key).getAsString().trim().toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException ignored) {
        }
        return def;
    }
}
