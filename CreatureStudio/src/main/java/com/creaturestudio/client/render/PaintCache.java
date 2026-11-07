package com.creaturestudio.client.render;

import com.creaturestudio.CreatureStudio;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureStore;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Texturas de pintura de las criaturas del mundo (una por criatura, se rehace al cambiar). */
public final class PaintCache {
    private static final Map<String, Entry> CACHE = new HashMap<>();

    private static final class Entry {
        final PaintAtlas atlas;
        @Nullable
        CreatureDefinition def;
        int revision = Integer.MIN_VALUE;
        boolean painted;

        Entry(PaintAtlas atlas) {
            this.atlas = atlas;
        }
    }

    private PaintCache() {
    }

    /** Textura de la pintura de la criatura, o null si no tiene nada pintado. */
    @Nullable
    public static PaintAtlas get(@Nullable CreatureDefinition def) {
        if (def == null || !CreatureDefinition.isValidId(def.id)) {
            return null;
        }
        Entry e = CACHE.get(def.id);
        if (e == null) {
            e = new Entry(new PaintAtlas(ResourceLocation.fromNamespaceAndPath(CreatureStudio.MODID, "paint/" + def.id)));
            CACHE.put(def.id, e);
        }
        if (e.def != def || e.revision != def.revision) {
            e.def = def;
            e.revision = def.revision;
            e.painted = e.atlas.rebuild(def);
        }
        return e.painted ? e.atlas : null;
    }

    /** Libera las texturas de criaturas que ya no existen. */
    public static void prune() {
        Iterator<Map.Entry<String, Entry>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> entry = it.next();
            if (CreatureStore.CLIENT.get(entry.getKey()) == null) {
                entry.getValue().atlas.close();
                it.remove();
            }
        }
    }

    public static void clear() {
        for (Entry e : CACHE.values()) {
            e.atlas.close();
        }
        CACHE.clear();
    }
}
