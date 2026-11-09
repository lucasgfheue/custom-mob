package com.creaturestudio.data;

import java.util.Locale;

/**
 * Estructuras de Minecraft en las que puede aparecer una criatura. Cada una es un id de estructura o una etiqueta
 * (#) que agrupa sus variantes (por ejemplo, las aldeas de cada bioma).
 */
public enum SpawnStructure {
    VILLAGE("#minecraft:village"),
    PILLAGER_OUTPOST("minecraft:pillager_outpost"),
    MANSION("minecraft:mansion"),
    DESERT_PYRAMID("minecraft:desert_pyramid"),
    JUNGLE_PYRAMID("minecraft:jungle_pyramid"),
    SWAMP_HUT("minecraft:swamp_hut"),
    IGLOO("minecraft:igloo"),
    TRAIL_RUINS("minecraft:trail_ruins"),
    RUINED_PORTAL("#minecraft:ruined_portal"),
    MINESHAFT("#minecraft:mineshaft"),
    STRONGHOLD("minecraft:stronghold"),
    TRIAL_CHAMBERS("minecraft:trial_chambers"),
    ANCIENT_CITY("minecraft:ancient_city"),
    MONUMENT("minecraft:monument"),
    OCEAN_RUIN("#minecraft:ocean_ruin"),
    SHIPWRECK("#minecraft:shipwreck"),
    FORTRESS("minecraft:fortress"),
    BASTION("minecraft:bastion_remnant"),
    END_CITY("minecraft:end_city");

    /** Id de la estructura, o de la etiqueta si empieza por #. */
    public final String target;

    SpawnStructure(String target) {
        this.target = target;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SpawnStructure byKey(String key) {
        for (SpawnStructure s : values()) {
            if (s.key().equalsIgnoreCase(key) || s.name().equalsIgnoreCase(key)) {
                return s;
            }
        }
        return null;
    }
}
