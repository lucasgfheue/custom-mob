package com.creaturestudio.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Facciones (pestana Facciones): a quien ataca una criatura nada mas verlo (enemigos), a quien no ataca nunca
 * (aliados) y su faccion, sin nada de Minecraft para poder probarlo.
 *
 * <p>Cada entrada de las listas es una de estas:</p>
 * <ul>
 *     <li>un tipo de entidad: {@code minecraft:zombie}, {@code minecraft:player}...</li>
 *     <li>una etiqueta de tipos de entidad: {@code #minecraft:undead}, {@code #minecraft:raiders}...</li>
 *     <li>un grupo: {@code @monsters}, {@code @animals}, {@code @villagers}, {@code @golems}, {@code @creatures}
 *     (las de Creature Studio) o {@code @all} (todos los mobs y jugadores)</li>
 *     <li>las criaturas de una faccion: {@code faction:Orcos}</li>
 *     <li>una criatura de Creature Studio: {@code creature:<id>}</li>
 * </ul>
 */
public final class Factions {
    public static final int MAX_LIST = 24;
    public static final int NAME_MAX = 32;
    public static final String PLAYER = "minecraft:player";
    public static final String FACTION = "faction:";
    public static final String CREATURE = "creature:";
    public static final String[] GROUPS = {"@monsters", "@animals", "@villagers", "@golems", "@creatures", "@all"};

    private Factions() {
    }

    /** Lo que hace falta saber de una entidad para ver si esta en una lista. */
    public interface Subject {
        /** Su tipo, "minecraft:zombie". */
        String type();

        /** Si su tipo esta en la etiqueta ("minecraft:undead", sin #). */
        boolean inTag(String tag);

        /** Si esta en el grupo ("@monsters"...). */
        boolean inGroup(String group);

        /** Su id si es una criatura de Creature Studio ("" si no). */
        String creatureId();

        /** Su faccion si es una criatura de Creature Studio ("" si no tiene). */
        String faction();
    }

    /** true si la entidad esta en la lista. */
    public static boolean matchesAny(List<String> list, Subject s) {
        for (String e : list) {
            if (matches(e, s)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(String entry, Subject s) {
        if (entry == null || entry.isEmpty()) {
            return false;
        }
        if (entry.startsWith(FACTION)) {
            return sameFaction(entry.substring(FACTION.length()), s.faction());
        }
        if (entry.startsWith(CREATURE)) {
            String id = s.creatureId();
            return !id.isEmpty() && id.equals(entry.substring(CREATURE.length()));
        }
        if (entry.startsWith("@")) {
            return s.inGroup(entry);
        }
        if (entry.startsWith("#")) {
            return s.inTag(entry.substring(1));
        }
        return entry.equals(s.type());
    }

    /** Dos facciones son la misma si tienen nombre y se escriben igual (sin contar mayusculas). */
    public static boolean sameFaction(String a, String b) {
        return a != null && b != null && !a.isEmpty() && a.trim().equalsIgnoreCase(b.trim());
    }

    /** Nombre de faccion limpio (sin espacios de mas ni simbolos de formato, como mucho 32 letras). */
    public static String cleanName(String s) {
        String t = CreatureDefinition.cleanText(s, NAME_MAX * 2).replaceAll("\\s+", " ").trim();
        return t.length() > NAME_MAX ? t.substring(0, NAME_MAX).trim() : t;
    }

    /** Una entrada limpia ("Zombie" -> "minecraft:zombie"); "" si no vale. */
    public static String cleanEntry(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        String low = s.toLowerCase(Locale.ROOT);
        if (low.startsWith(FACTION)) {
            String name = cleanName(s.substring(FACTION.length()));
            return name.isEmpty() ? "" : FACTION + name;
        }
        if (low.startsWith(CREATURE)) {
            String id = low.substring(CREATURE.length()).trim();
            return CreatureDefinition.isValidId(id) ? CREATURE + id : "";
        }
        if (low.startsWith("@")) {
            for (String g : GROUPS) {
                if (g.equals(low)) {
                    return g;
                }
            }
            return "";
        }
        if (low.startsWith("#")) {
            String tag = CreatureDefinition.cleanResource(low.substring(1));
            return tag.isEmpty() ? "" : "#" + tag;
        }
        return CreatureDefinition.cleanResource(low);
    }

    /** La lista limpia: sin vacios ni repetidos (las facciones sin contar mayusculas) y como mucho {@code max}. */
    public static List<String> cleanList(List<String> in, int max) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (in != null) {
            for (String raw : in) {
                String c = cleanEntry(raw);
                if (!c.isEmpty() && out.size() < max && seen.add(c.toLowerCase(Locale.ROOT))) {
                    out.add(c);
                }
            }
        }
        return out;
    }
}
