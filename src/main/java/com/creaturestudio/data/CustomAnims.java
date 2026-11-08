package com.creaturestudio.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Animaciones propias hechas con poses (pestana Animar, "Mis animaciones"). Aqui esta, por accion, cuantas poses
 * tiene y su velocidad; los valores de cada pose estan en cada pieza ({@link CreaturePart#moves}), asi viajan con
 * ella al copiarla, espejarla, borrarla o cambiarla de orden.
 */
public final class CustomAnims {
    public static final int MAX_POSES = 6;
    /** Numeros por pose y pieza: giro x, y, z (grados) y desplazamiento x, y, z (pixeles). */
    public static final int VALUES = 6;
    public static final float MAX_ANGLE = 360f;
    public static final float MAX_OFFSET = 64f;
    public static final float MIN_SPEED = 0.1f;
    public static final float MAX_SPEED = 5f;

    /** Poses de cada accion (0 = no tiene animacion propia: usa las automaticas). */
    public final int[] poses = new int[AnimAction.COUNT];
    /** Velocidad de cada accion (1 = normal). */
    public final float[] speed = new float[AnimAction.COUNT];

    public CustomAnims() {
        Arrays.fill(speed, 1f);
    }

    public int poses(AnimAction a) {
        return poses[a.ordinal()];
    }

    public float speed(AnimAction a) {
        return speed[a.ordinal()];
    }

    public boolean has(AnimAction a) {
        return poses[a.ordinal()] > 0;
    }

    /** true si alguna accion tiene animacion propia. */
    public boolean any() {
        for (int p : poses) {
            if (p > 0) {
                return true;
            }
        }
        return false;
    }

    public void sanitize(List<CreaturePart> parts) {
        for (int a = 0; a < AnimAction.COUNT; a++) {
            poses[a] = Math.max(0, Math.min(MAX_POSES, poses[a]));
            float s = speed[a];
            speed[a] = Float.isNaN(s) ? 1f : Math.max(MIN_SPEED, Math.min(MAX_SPEED, s));
        }
        for (CreaturePart p : parts) {
            sanitizePart(p);
        }
    }

    private void sanitizePart(CreaturePart p) {
        if (p.moves == null) {
            return;
        }
        float[][] fixed = new float[AnimAction.COUNT][];
        boolean any = false;
        for (int a = 0; a < AnimAction.COUNT && a < p.moves.length; a++) {
            float[] v = p.moves[a];
            if (v == null || poses[a] == 0) {
                continue;
            }
            float[] out = new float[MAX_POSES * VALUES];
            boolean used = false;
            for (int i = 0; i < out.length && i < v.length; i++) {
                float limit = i % VALUES < 3 ? MAX_ANGLE : MAX_OFFSET;
                float x = v[i];
                out[i] = Float.isNaN(x) ? 0f : Math.max(-limit, Math.min(limit, x));
                used |= out[i] != 0f;
            }
            if (used) {
                fixed[a] = out;
                any = true;
            }
        }
        p.moves = any ? fixed : null;
    }

    // ---------------------------------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------------------------------

    /** {"walk": {"poses": 2, "speed": 1.0}, ...} (solo las acciones que tienen animacion). */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        for (AnimAction a : AnimAction.ALL) {
            if (!has(a)) {
                continue;
            }
            JsonObject j = new JsonObject();
            j.addProperty("poses", poses(a));
            j.addProperty("speed", round(speed(a)));
            o.add(a.key(), j);
        }
        return o;
    }

    public static CustomAnims fromJson(@Nullable JsonObject o) {
        CustomAnims c = new CustomAnims();
        if (o == null) {
            return c;
        }
        for (AnimAction a : AnimAction.ALL) {
            JsonElement e = o.get(a.key());
            if (e == null || !e.isJsonObject()) {
                continue;
            }
            JsonObject j = e.getAsJsonObject();
            try {
                if (j.has("poses")) {
                    c.poses[a.ordinal()] = j.get("poses").getAsInt();
                }
                if (j.has("speed")) {
                    c.speed[a.ordinal()] = j.get("speed").getAsFloat();
                }
            } catch (RuntimeException ignored) {
                // valor raro: se queda el de por defecto
            }
        }
        return c;
    }

    /** Poses de una pieza: {"walk": [6 numeros por pose...], ...}, o null si no se mueve en ninguna. */
    @Nullable
    public JsonObject partToJson(CreaturePart p) {
        if (p.moves == null) {
            return null;
        }
        JsonObject o = new JsonObject();
        for (AnimAction a : AnimAction.ALL) {
            int n = poses(a);
            if (n == 0 || !p.movesIn(a, n)) {
                continue;
            }
            float[] v = p.moves[a.ordinal()];
            JsonArray arr = new JsonArray();
            for (int i = 0; i < n * VALUES; i++) {
                arr.add(round(v[i]));
            }
            o.add(a.key(), arr);
        }
        return o.size() == 0 ? null : o;
    }

    public static void partFromJson(CreaturePart p, @Nullable JsonObject o) {
        p.moves = null;
        if (o == null) {
            return;
        }
        for (AnimAction a : AnimAction.ALL) {
            JsonElement e = o.get(a.key());
            if (e == null || !e.isJsonArray()) {
                continue;
            }
            JsonArray arr = e.getAsJsonArray();
            for (int i = 0; i < arr.size() && i < MAX_POSES * VALUES; i++) {
                try {
                    float v = arr.get(i).getAsFloat();
                    if (v != 0f) {
                        p.setMove(a, i / VALUES, i % VALUES, v);
                    }
                } catch (RuntimeException ignored) {
                    // numero raro: se queda en cero
                }
            }
        }
    }

    private static float round(float v) {
        return Math.round(v * 100f) / 100f;
    }

    // ---------------------------------------------------------------------------------------
    // Editar (los usa el editor)
    // ---------------------------------------------------------------------------------------

    /** Crea la animacion de una accion con sus poses de inicio (todas en cero: la postura normal). */
    public static void create(CreatureDefinition def, AnimAction a) {
        clear(def, a);
        def.customAnims.poses[a.ordinal()] = a.startPoses();
        def.customAnims.speed[a.ordinal()] = 1f;
    }

    /** Quita la animacion propia de una accion (vuelve a usar las automaticas). */
    public static void clear(CreatureDefinition def, AnimAction a) {
        def.customAnims.poses[a.ordinal()] = 0;
        for (CreaturePart p : def.parts) {
            p.clearMoves(a);
        }
    }

    /**
     * Pone una pose nueva justo despues de "after" (copia de ella si copy, o en cero) en todas las piezas.
     * Devuelve su numero, o -1 si ya hay el maximo.
     */
    public static int insertPose(CreatureDefinition def, AnimAction a, int after, boolean copy) {
        int n = def.customAnims.poses(a);
        if (n >= MAX_POSES) {
            return -1;
        }
        int at = Math.max(0, Math.min(n, after + 1));
        for (CreaturePart p : def.parts) {
            float[] v = p.moves == null ? null : p.moves[a.ordinal()];
            if (v == null) {
                continue;
            }
            System.arraycopy(v, at * VALUES, v, (at + 1) * VALUES, (MAX_POSES - at - 1) * VALUES);
            for (int k = 0; k < VALUES; k++) {
                v[at * VALUES + k] = copy && after >= 0 && after < n ? v[after * VALUES + k] : 0f;
            }
        }
        def.customAnims.poses[a.ordinal()] = n + 1;
        return at;
    }

    /** Borra una pose (en todas las piezas). Si era la unica, la animacion desaparece. */
    public static void removePose(CreatureDefinition def, AnimAction a, int pose) {
        int n = def.customAnims.poses(a);
        if (pose < 0 || pose >= n) {
            return;
        }
        if (n == 1) {
            clear(def, a);
            return;
        }
        for (CreaturePart p : def.parts) {
            float[] v = p.moves == null ? null : p.moves[a.ordinal()];
            if (v == null) {
                continue;
            }
            System.arraycopy(v, (pose + 1) * VALUES, v, pose * VALUES, (MAX_POSES - pose - 1) * VALUES);
            Arrays.fill(v, (MAX_POSES - 1) * VALUES, MAX_POSES * VALUES, 0f);
        }
        def.customAnims.poses[a.ordinal()] = n - 1;
    }

    /** true si en esa pose ninguna pieza esta movida. */
    public static boolean poseEmpty(CreatureDefinition def, AnimAction a, int pose) {
        for (CreaturePart p : def.parts) {
            for (int k = 0; k < VALUES; k++) {
                if (p.move(a, pose, k) != 0f) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * La pose contraria de "from" (la izquierda hace lo que hacia la derecha y al reves; lo del centro se refleja):
     * con una pose y su contraria sale un paso completo. Va en la pose siguiente si esta vacia; si no, se anade una
     * nueva despues de "from". Devuelve donde quedo, o -1 si no cabe.
     */
    public static int addOpposite(CreatureDefinition def, AnimAction a, int from) {
        int n = def.customAnims.poses(a);
        if (from < 0 || from >= n) {
            return -1;
        }
        int to = from + 1;
        if (to >= n || !poseEmpty(def, a, to)) {
            to = insertPose(def, a, from, false);
            if (to < 0) {
                return -1;
            }
        }
        writeOpposite(def, a, from, to);
        return to;
    }

    /** Escribe en la pose "to" la contraria de la pose "from". */
    public static void writeOpposite(CreatureDefinition def, AnimAction a, int from, int to) {
        int n = def.parts.size();
        float[][] out = new float[n][VALUES];
        for (int i = 0; i < n; i++) {
            int src = mirrorPartner(def, i);
            CreaturePart s = def.parts.get(src >= 0 ? src : i);
            for (int k = 0; k < VALUES; k++) {
                out[i][k] = s.move(a, from, k) * MIRROR[k];
            }
        }
        for (int i = 0; i < n; i++) {
            CreaturePart p = def.parts.get(i);
            for (int k = 0; k < VALUES; k++) {
                p.setMove(a, to, k, out[i][k]);
            }
        }
    }

    /** Signo de cada valor al reflejar en X: el giro en X y los desplazamientos en Y/Z no cambian. */
    public static final float[] MIRROR = {1f, -1f, -1f, -1f, 1f, 1f};

    /**
     * La pieza del otro lado: la que se llama igual cambiando izquierda por derecha o, si no hay, la que esta en el
     * sitio reflejado con el mismo tamano. -1 si esta en el centro o no tiene pareja.
     */
    public static int mirrorPartner(CreatureDefinition def, int i) {
        if (i < 0 || i >= def.parts.size()) {
            return -1;
        }
        int named = BodyParts.partner(def, i);
        if (named >= 0) {
            return named;
        }
        CreaturePart p = def.parts.get(i);
        float cx = p.x + p.w / 2f;
        if (Math.abs(cx) < 0.25f) {
            return -1;
        }
        int best = -1;
        float bestErr = 1.01f;
        for (int j = 0; j < def.parts.size(); j++) {
            if (j == i) {
                continue;
            }
            CreaturePart q = def.parts.get(j);
            float err = Math.abs(q.x + q.w / 2f + cx) + Math.abs(q.y - p.y) + Math.abs(q.z - p.z)
                    + Math.abs(q.w - p.w) + Math.abs(q.h - p.h) + Math.abs(q.d - p.d);
            if (err < bestErr) {
                bestErr = err;
                best = j;
            }
        }
        return best;
    }
}
