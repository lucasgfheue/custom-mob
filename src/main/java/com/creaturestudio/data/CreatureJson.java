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

        JsonObject drop = new JsonObject();
        drop.addProperty("item", d.dropItem);
        drop.addProperty("min", d.dropMin);
        drop.addProperty("max", d.dropMax);
        o.add("drop", drop);

        JsonObject spawn = new JsonObject();
        spawn.addProperty("natural", d.naturalSpawn);
        spawn.addProperty("time", d.spawnTime.name());
        spawn.addProperty("biomes", d.spawnBiomes);
        spawn.addProperty("chance", d.spawnChance);
        spawn.addProperty("max", d.spawnMax);
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

        JsonObject drop = obj(o, "drop");
        if (drop != null) {
            d.dropItem = str(drop, "item", "");
            d.dropMin = integer(drop, "min", d.dropMin);
            d.dropMax = integer(drop, "max", d.dropMax);
        }

        JsonObject spawn = obj(o, "spawn");
        if (spawn != null) {
            d.naturalSpawn = bool(spawn, "natural", false);
            d.spawnTime = enumValue(spawn, "time", SpawnTime.class, d.spawnTime);
            d.spawnBiomes = str(spawn, "biomes", "");
            d.spawnChance = integer(spawn, "chance", d.spawnChance);
            d.spawnMax = integer(spawn, "max", d.spawnMax);
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
