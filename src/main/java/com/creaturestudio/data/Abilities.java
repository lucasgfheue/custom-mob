package com.creaturestudio.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * Habilidades especiales copiadas de los mobs de Minecraft (pestana Habilidades del editor). Todas empiezan
 * apagadas; cada una tiene sus propios ajustes. Los tiempos van en ticks (20 = 1 segundo) o en segundos,
 * segun indique cada campo.
 */
public class Abilities {
    // Creeper: explota cuando alguien se acerca
    public boolean explode;
    public ExplosionSize explodeSize = ExplosionSize.MEDIUM;
    /** Distancia a la que empieza a hacer "sss" (bloques). */
    public float explodeRange = 3f;
    /** Mecha: ticks desde que empieza a hincharse hasta que explota. */
    public int explodeFuse = 30;
    /** true = desaparece al explotar (como el creeper); false = sobrevive y puede volver a explotar. */
    public boolean explodeDies = true;
    /** Si sobrevive: segundos hasta que puede volver a explotar. */
    public int explodeCooldown = 5;

    // Warden: no ve, pero oye
    public boolean blind;
    public float hearRange = 16f;
    // Warden: grito sonico (atraviesa paredes)
    public boolean sonicBoom;
    public float sonicDamage = 10f;
    public float sonicRange = 15f;
    /** Segundos entre gritos. */
    public int sonicCooldown = 5;

    // Enderman: se teletransporta
    public boolean teleport;
    public float teleportRange = 16f;
    /** Esquiva los proyectiles teletransportandose. */
    public boolean teleportDodge = true;
    /** Se acerca a su objetivo teletransportandose. */
    public boolean teleportChase = true;
    // Enderman: se enoja si la miras a los ojos
    public boolean stareAnger;

    // Guardian: rayo
    public boolean beam;
    public float beamDamage = 6f;
    public float beamRange = 15f;
    /** Ticks que carga el rayo antes de hacer dano. */
    public int beamCharge = 60;
    // Guardian: espinas (dana a quien la golpea)
    public boolean thorns;
    public float thornsDamage = 2f;

    // Guardian anciano: aura que pone un efecto a los jugadores cercanos
    public boolean aura;
    public HitEffect auraEffect = HitEffect.MINING_FATIGUE;
    public float auraRange = 8f;
    /** Segundos entre pulsos. */
    public int auraInterval = 10;
    /** Segundos que dura el efecto. */
    public int auraDuration = 10;

    // Slime: se divide al morir
    public boolean split;
    public int splitCount = 2;
    /** Cuantas veces se pueden dividir los pedazos (1 = solo la primera). */
    public int splitGenerations = 1;

    // Arana: trepa paredes y salta sobre su objetivo
    public boolean climb;
    public boolean leap;

    // Golem de hierro: lanza por los aires al golpear
    public boolean launch;
    public float launchPower = 1f;

    // Bruja: se cura con pociones
    public boolean selfHeal;
    public float selfHealAmount = 6f;
    /** Segundos entre pociones. */
    public int selfHealCooldown = 15;

    // Invocador: invoca ayudantes
    public boolean summon;
    /** Mob de Minecraft (p. ej. minecraft:vex) o el id de otra criatura propia. */
    public String summonEntity = "minecraft:vex";
    public int summonCount = 2;
    public int summonMax = 4;
    /** Segundos entre invocaciones. */
    public int summonCooldown = 20;

    // Devastador: embestida
    public boolean charge;
    public float chargeDamage = 6f;
    /** Bloques por tick durante la carrera. */
    public float chargeSpeed = 1f;
    /** Segundos entre embestidas. */
    public int chargeCooldown = 5;

    // Lobo: manada (las de su especie la defienden)
    public boolean pack;
    public float packRange = 16f;

    // Calamar: suelta tinta al recibir dano
    public boolean ink;

    // Breeze: rebota los proyectiles
    public boolean deflect;
    /** Probabilidad (%) de devolver cada proyectil. */
    public int deflectChance = 100;

    // Gallina: cae despacio y pone huevos (u otro objeto)
    public boolean slowFall;
    public boolean layItems;
    public String layItem = "minecraft:egg";
    /** Segundos (como minimo) entre objeto y objeto. */
    public int layInterval = 300;

    // Vaca: se puede ordenar (da un objeto si la tocas con otro)
    public boolean milkable;
    public String milkWith = "minecraft:bucket";
    public String milkGives = "minecraft:milk_bucket";
    /** true = el objeto de la mano se cambia por el resultado (cubo -> cubo de leche). */
    public boolean milkUsesItem = true;
    /** Segundos de espera entre una vez y otra (0 = sin espera). */
    public int milkCooldown = 0;

    // Zombi / aldeano: puertas
    public DoorMode doors = DoorMode.NONE;

    // Golem de nieve: deja un rastro
    public boolean trail;
    public TrailBlock trailBlock = TrailBlock.SNOW;

    // Calamar luminoso: brilla en la oscuridad
    public boolean glows;

    // Zorro: recoge objetos del suelo
    public boolean pickUp;

    // Creeper: le teme a otro mob (como a los gatos)
    public boolean fear;
    public String fearEntity = "minecraft:cat";
    public float fearRange = 8f;

    public Abilities copy() {
        Abilities c = new Abilities();
        c.explode = explode;
        c.explodeSize = explodeSize;
        c.explodeRange = explodeRange;
        c.explodeFuse = explodeFuse;
        c.explodeDies = explodeDies;
        c.explodeCooldown = explodeCooldown;
        c.blind = blind;
        c.hearRange = hearRange;
        c.sonicBoom = sonicBoom;
        c.sonicDamage = sonicDamage;
        c.sonicRange = sonicRange;
        c.sonicCooldown = sonicCooldown;
        c.teleport = teleport;
        c.teleportRange = teleportRange;
        c.teleportDodge = teleportDodge;
        c.teleportChase = teleportChase;
        c.stareAnger = stareAnger;
        c.beam = beam;
        c.beamDamage = beamDamage;
        c.beamRange = beamRange;
        c.beamCharge = beamCharge;
        c.thorns = thorns;
        c.thornsDamage = thornsDamage;
        c.aura = aura;
        c.auraEffect = auraEffect;
        c.auraRange = auraRange;
        c.auraInterval = auraInterval;
        c.auraDuration = auraDuration;
        c.split = split;
        c.splitCount = splitCount;
        c.splitGenerations = splitGenerations;
        c.climb = climb;
        c.leap = leap;
        c.launch = launch;
        c.launchPower = launchPower;
        c.selfHeal = selfHeal;
        c.selfHealAmount = selfHealAmount;
        c.selfHealCooldown = selfHealCooldown;
        c.summon = summon;
        c.summonEntity = summonEntity;
        c.summonCount = summonCount;
        c.summonMax = summonMax;
        c.summonCooldown = summonCooldown;
        c.charge = charge;
        c.chargeDamage = chargeDamage;
        c.chargeSpeed = chargeSpeed;
        c.chargeCooldown = chargeCooldown;
        c.pack = pack;
        c.packRange = packRange;
        c.ink = ink;
        c.deflect = deflect;
        c.deflectChance = deflectChance;
        c.slowFall = slowFall;
        c.layItems = layItems;
        c.layItem = layItem;
        c.layInterval = layInterval;
        c.milkable = milkable;
        c.milkWith = milkWith;
        c.milkGives = milkGives;
        c.milkUsesItem = milkUsesItem;
        c.milkCooldown = milkCooldown;
        c.doors = doors;
        c.trail = trail;
        c.trailBlock = trailBlock;
        c.glows = glows;
        c.pickUp = pickUp;
        c.fear = fear;
        c.fearEntity = fearEntity;
        c.fearRange = fearRange;
        return c;
    }

    /** Cuantas habilidades estan encendidas. */
    public int activeCount() {
        boolean[] all = {explode, blind, sonicBoom, teleport, stareAnger, beam, thorns, aura, split, climb, leap,
                launch, selfHeal, summon, charge, pack, ink, deflect, slowFall, layItems, milkable,
                doors != DoorMode.NONE, trail, glows, pickUp, fear};
        int n = 0;
        for (boolean b : all) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    public void sanitize() {
        if (explodeSize == null || explodeSize == ExplosionSize.NONE) {
            explodeSize = ExplosionSize.MEDIUM;
        }
        explodeRange = clamp(explodeRange, 1f, 16f, 3f);
        explodeFuse = clamp(explodeFuse, 5, 200);
        explodeCooldown = clamp(explodeCooldown, 1, 120);
        hearRange = clamp(hearRange, 2f, 64f, 16f);
        sonicDamage = clamp(sonicDamage, 0f, 200f, 10f);
        sonicRange = clamp(sonicRange, 2f, 48f, 15f);
        sonicCooldown = clamp(sonicCooldown, 1, 120);
        teleportRange = clamp(teleportRange, 2f, 64f, 16f);
        beamDamage = clamp(beamDamage, 0f, 200f, 6f);
        beamRange = clamp(beamRange, 2f, 48f, 15f);
        beamCharge = clamp(beamCharge, 10, 200);
        thornsDamage = clamp(thornsDamage, 0f, 100f, 2f);
        if (auraEffect == null || auraEffect == HitEffect.NONE) {
            auraEffect = HitEffect.MINING_FATIGUE;
        }
        auraRange = clamp(auraRange, 2f, 48f, 8f);
        auraInterval = clamp(auraInterval, 1, 600);
        auraDuration = clamp(auraDuration, 1, 600);
        splitCount = clamp(splitCount, 2, 4);
        splitGenerations = clamp(splitGenerations, 1, 3);
        launchPower = clamp(launchPower, 0.1f, 5f, 1f);
        selfHealAmount = clamp(selfHealAmount, 1f, 1024f, 6f);
        selfHealCooldown = clamp(selfHealCooldown, 1, 600);
        summonEntity = cleanId(summonEntity, "minecraft:vex");
        summonCount = clamp(summonCount, 1, 6);
        summonMax = clamp(summonMax, 1, 16);
        summonCooldown = clamp(summonCooldown, 1, 600);
        chargeDamage = clamp(chargeDamage, 0f, 200f, 6f);
        chargeSpeed = clamp(chargeSpeed, 0.3f, 3f, 1f);
        chargeCooldown = clamp(chargeCooldown, 1, 120);
        packRange = clamp(packRange, 2f, 64f, 16f);
        deflectChance = clamp(deflectChance, 1, 100);
        layItem = CreatureDefinition.cleanResource(layItem);
        layInterval = clamp(layInterval, 5, 3600);
        milkWith = CreatureDefinition.cleanResource(milkWith);
        milkGives = CreatureDefinition.cleanResource(milkGives);
        milkCooldown = clamp(milkCooldown, 0, 3600);
        if (doors == null) {
            doors = DoorMode.NONE;
        }
        if (trailBlock == null) {
            trailBlock = TrailBlock.SNOW;
        }
        fearEntity = cleanId(fearEntity, "minecraft:cat");
        fearRange = clamp(fearRange, 2f, 32f, 8f);
    }

    /**
     * Id de un mob: "minecraft:vex" (o de otro mod), o sin ":" para el id de una criatura propia o un mob de
     * Minecraft escrito sin "minecraft:" (al usarlo se busca primero entre las criaturas propias).
     */
    public static String cleanId(String s, String fallback) {
        if (s == null) {
            return fallback;
        }
        String t = s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        if (t.isEmpty()) {
            return fallback;
        }
        if (!t.contains(":")) {
            return t.matches("[a-z0-9_]{1,40}") ? t : fallback;
        }
        String clean = CreatureDefinition.cleanResource(t);
        return clean.isEmpty() ? fallback : clean;
    }

    private static float clamp(float v, float min, float max, float fallback) {
        if (Float.isNaN(v)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, v));
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------------------------------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------------------------------

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        JsonObject e = on(explode);
        e.addProperty("size", explodeSize.key());
        e.addProperty("range", explodeRange);
        e.addProperty("fuse", explodeFuse);
        e.addProperty("dies", explodeDies);
        e.addProperty("cooldown", explodeCooldown);
        o.add("explode", e);
        JsonObject h = on(blind);
        h.addProperty("range", hearRange);
        o.add("hearing", h);
        JsonObject sb = on(sonicBoom);
        sb.addProperty("damage", sonicDamage);
        sb.addProperty("range", sonicRange);
        sb.addProperty("cooldown", sonicCooldown);
        o.add("sonicBoom", sb);
        JsonObject tp = on(teleport);
        tp.addProperty("range", teleportRange);
        tp.addProperty("dodge", teleportDodge);
        tp.addProperty("chase", teleportChase);
        o.add("teleport", tp);
        o.addProperty("stareAnger", stareAnger);
        JsonObject b = on(beam);
        b.addProperty("damage", beamDamage);
        b.addProperty("range", beamRange);
        b.addProperty("charge", beamCharge);
        o.add("beam", b);
        JsonObject th = on(thorns);
        th.addProperty("damage", thornsDamage);
        o.add("thorns", th);
        JsonObject a = on(aura);
        a.addProperty("effect", auraEffect.key());
        a.addProperty("range", auraRange);
        a.addProperty("interval", auraInterval);
        a.addProperty("duration", auraDuration);
        o.add("aura", a);
        JsonObject sp = on(split);
        sp.addProperty("count", splitCount);
        sp.addProperty("generations", splitGenerations);
        o.add("split", sp);
        o.addProperty("climb", climb);
        o.addProperty("leap", leap);
        JsonObject l = on(launch);
        l.addProperty("power", launchPower);
        o.add("launch", l);
        JsonObject sh = on(selfHeal);
        sh.addProperty("amount", selfHealAmount);
        sh.addProperty("cooldown", selfHealCooldown);
        o.add("selfHeal", sh);
        JsonObject su = on(summon);
        su.addProperty("entity", summonEntity);
        su.addProperty("count", summonCount);
        su.addProperty("max", summonMax);
        su.addProperty("cooldown", summonCooldown);
        o.add("summon", su);
        JsonObject c = on(charge);
        c.addProperty("damage", chargeDamage);
        c.addProperty("speed", chargeSpeed);
        c.addProperty("cooldown", chargeCooldown);
        o.add("charge", c);
        JsonObject p = on(pack);
        p.addProperty("range", packRange);
        o.add("pack", p);
        o.addProperty("ink", ink);
        JsonObject d = on(deflect);
        d.addProperty("chance", deflectChance);
        o.add("deflect", d);
        o.addProperty("slowFall", slowFall);
        JsonObject ly = on(layItems);
        ly.addProperty("item", layItem);
        ly.addProperty("interval", layInterval);
        o.add("lay", ly);
        JsonObject m = on(milkable);
        m.addProperty("with", milkWith);
        m.addProperty("gives", milkGives);
        m.addProperty("usesItem", milkUsesItem);
        m.addProperty("cooldown", milkCooldown);
        o.add("milk", m);
        o.addProperty("doors", doors.key());
        JsonObject tr = on(trail);
        tr.addProperty("block", trailBlock.key());
        o.add("trail", tr);
        o.addProperty("glows", glows);
        o.addProperty("pickUp", pickUp);
        JsonObject f = on(fear);
        f.addProperty("entity", fearEntity);
        f.addProperty("range", fearRange);
        o.add("fear", f);
        return o;
    }

    private static JsonObject on(boolean value) {
        JsonObject o = new JsonObject();
        o.addProperty("on", value);
        return o;
    }

    /** Lee las habilidades; lo que falte o no se entienda se queda con su valor por defecto. */
    public static Abilities fromJson(JsonObject o) {
        Abilities r = new Abilities();
        JsonObject e = obj(o, "explode");
        r.explode = bool(e, "on", false);
        r.explodeSize = enumValue(ExplosionSize.class, str(e, "size", ""), r.explodeSize);
        r.explodeRange = num(e, "range", r.explodeRange);
        r.explodeFuse = (int) num(e, "fuse", r.explodeFuse);
        r.explodeDies = bool(e, "dies", r.explodeDies);
        r.explodeCooldown = (int) num(e, "cooldown", r.explodeCooldown);
        JsonObject h = obj(o, "hearing");
        r.blind = bool(h, "on", false);
        r.hearRange = num(h, "range", r.hearRange);
        JsonObject sb = obj(o, "sonicBoom");
        r.sonicBoom = bool(sb, "on", false);
        r.sonicDamage = num(sb, "damage", r.sonicDamage);
        r.sonicRange = num(sb, "range", r.sonicRange);
        r.sonicCooldown = (int) num(sb, "cooldown", r.sonicCooldown);
        JsonObject tp = obj(o, "teleport");
        r.teleport = bool(tp, "on", false);
        r.teleportRange = num(tp, "range", r.teleportRange);
        r.teleportDodge = bool(tp, "dodge", r.teleportDodge);
        r.teleportChase = bool(tp, "chase", r.teleportChase);
        r.stareAnger = bool(o, "stareAnger", false);
        JsonObject b = obj(o, "beam");
        r.beam = bool(b, "on", false);
        r.beamDamage = num(b, "damage", r.beamDamage);
        r.beamRange = num(b, "range", r.beamRange);
        r.beamCharge = (int) num(b, "charge", r.beamCharge);
        JsonObject th = obj(o, "thorns");
        r.thorns = bool(th, "on", false);
        r.thornsDamage = num(th, "damage", r.thornsDamage);
        JsonObject a = obj(o, "aura");
        r.aura = bool(a, "on", false);
        r.auraEffect = enumValue(HitEffect.class, str(a, "effect", ""), r.auraEffect);
        r.auraRange = num(a, "range", r.auraRange);
        r.auraInterval = (int) num(a, "interval", r.auraInterval);
        r.auraDuration = (int) num(a, "duration", r.auraDuration);
        JsonObject sp = obj(o, "split");
        r.split = bool(sp, "on", false);
        r.splitCount = (int) num(sp, "count", r.splitCount);
        r.splitGenerations = (int) num(sp, "generations", r.splitGenerations);
        r.climb = bool(o, "climb", false);
        r.leap = bool(o, "leap", false);
        JsonObject l = obj(o, "launch");
        r.launch = bool(l, "on", false);
        r.launchPower = num(l, "power", r.launchPower);
        JsonObject sh = obj(o, "selfHeal");
        r.selfHeal = bool(sh, "on", false);
        r.selfHealAmount = num(sh, "amount", r.selfHealAmount);
        r.selfHealCooldown = (int) num(sh, "cooldown", r.selfHealCooldown);
        JsonObject su = obj(o, "summon");
        r.summon = bool(su, "on", false);
        r.summonEntity = str(su, "entity", r.summonEntity);
        r.summonCount = (int) num(su, "count", r.summonCount);
        r.summonMax = (int) num(su, "max", r.summonMax);
        r.summonCooldown = (int) num(su, "cooldown", r.summonCooldown);
        JsonObject c = obj(o, "charge");
        r.charge = bool(c, "on", false);
        r.chargeDamage = num(c, "damage", r.chargeDamage);
        r.chargeSpeed = num(c, "speed", r.chargeSpeed);
        r.chargeCooldown = (int) num(c, "cooldown", r.chargeCooldown);
        JsonObject p = obj(o, "pack");
        r.pack = bool(p, "on", false);
        r.packRange = num(p, "range", r.packRange);
        r.ink = bool(o, "ink", false);
        JsonObject d = obj(o, "deflect");
        r.deflect = bool(d, "on", false);
        r.deflectChance = (int) num(d, "chance", r.deflectChance);
        r.slowFall = bool(o, "slowFall", false);
        JsonObject ly = obj(o, "lay");
        r.layItems = bool(ly, "on", false);
        r.layItem = str(ly, "item", r.layItem);
        r.layInterval = (int) num(ly, "interval", r.layInterval);
        JsonObject m = obj(o, "milk");
        r.milkable = bool(m, "on", false);
        r.milkWith = str(m, "with", r.milkWith);
        r.milkGives = str(m, "gives", r.milkGives);
        r.milkUsesItem = bool(m, "usesItem", r.milkUsesItem);
        r.milkCooldown = (int) num(m, "cooldown", r.milkCooldown);
        r.doors = enumValue(DoorMode.class, str(o, "doors", ""), DoorMode.NONE);
        JsonObject tr = obj(o, "trail");
        r.trail = bool(tr, "on", false);
        r.trailBlock = enumValue(TrailBlock.class, str(tr, "block", ""), r.trailBlock);
        r.glows = bool(o, "glows", false);
        r.pickUp = bool(o, "pickUp", false);
        JsonObject f = obj(o, "fear");
        r.fear = bool(f, "on", false);
        r.fearEntity = str(f, "entity", r.fearEntity);
        r.fearRange = num(f, "range", r.fearRange);
        return r;
    }

    private static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static float num(JsonObject o, String key, float fallback) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsFloat() : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        try {
            JsonElement e = o.get(key);
            return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String key, E fallback) {
        for (E v : type.getEnumConstants()) {
            if (v.name().equalsIgnoreCase(key)) {
                return v;
            }
        }
        return fallback;
    }
}
