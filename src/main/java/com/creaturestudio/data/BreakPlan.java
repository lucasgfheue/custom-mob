package com.creaturestudio.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Las cuentas de "romper bloques" (pestana Atributos), sin nada de Minecraft para poder probarlas: que casillas le
 * cierran el paso, cuales rompe de un golpe, cuanto tarda y que bloques deja romper la lista.
 */
public final class BreakPlan {
    /** Dureza de la piedra: un bloque asi tarda justo el tiempo elegido. */
    public static final float STONE = 1.5f;
    /** Con "los duros tardan mas": lo minimo y lo maximo que se multiplica el tiempo (tierra 1/3, obsidiana 8). */
    public static final float MIN_FACTOR = 0.25f;
    public static final float MAX_FACTOR = 8f;
    public static final int MAX_COUNT = 64;
    public static final float MAX_SECONDS = 60f;

    private BreakPlan() {
    }

    /** Si en esa casilla hay algo que pueda romper. */
    @FunctionalInterface
    public interface Solid {
        boolean test(int x, int y, int z);
    }

    /** Numero unico para una casilla (como BlockPos.asLong). */
    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    /** Ticks que tarda en romper un bloque con esa dureza (0 = al instante). */
    public static int ticks(float seconds, boolean byHardness, float hardness) {
        if (!(seconds > 0f)) {
            return 0;
        }
        float factor = 1f;
        if (byHardness) {
            factor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, Math.max(0f, hardness) / STONE));
        }
        return Math.max(1, Math.round(seconds * 20f * factor));
    }

    /**
     * Las casillas que toca la caja (en bloques) estirada {@code dist} bloques hacia (dx, dz), entre las alturas fromY y
     * toY (las dos incluidas), de la mas cercana al centro de la caja a la mas lejana.
     */
    public static List<int[]> ahead(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                    double dx, double dz, double dist, int fromY, int toY) {
        double len = Math.sqrt(dx * dx + dz * dz);
        double ux = len > 1.0e-6 ? dx / len * dist : 0.0;
        double uz = len > 1.0e-6 ? dz / len * dist : 0.0;
        double x0 = minX + Math.min(0.0, ux);
        double x1 = maxX + Math.max(0.0, ux);
        double z0 = minZ + Math.min(0.0, uz);
        double z1 = maxZ + Math.max(0.0, uz);
        int bx0 = floor(x0 + 1.0e-6);
        int bx1 = floor(x1 - 1.0e-6);
        int bz0 = floor(z0 + 1.0e-6);
        int bz1 = floor(z1 - 1.0e-6);
        double cx = (minX + maxX) * 0.5;
        double cy = (minY + maxY) * 0.5;
        double cz = (minZ + maxZ) * 0.5;
        List<int[]> out = new ArrayList<>();
        for (int y = fromY; y <= toY; y++) {
            for (int x = bx0; x <= bx1; x++) {
                for (int z = bz0; z <= bz1; z++) {
                    out.add(new int[]{x, y, z});
                }
            }
        }
        out.sort(Comparator.comparingDouble((int[] p) -> dist2(p, cx, cy, cz))
                .thenComparingInt(p -> -p[1]).thenComparingInt(p -> p[0]).thenComparingInt(p -> p[2]));
        return out;
    }

    /**
     * Los bloques que rompe de un golpe: el primero (x, y, z) y hasta count - 1 mas de alrededor que pueda romper. Van
     * antes los de {@code preferred} (los que le cierran el paso) y despues los mas cercanos al primero; nunca los que
     * estan por debajo de minY (el suelo que pisa).
     */
    public static List<int[]> cluster(int x, int y, int z, int count, int minY, Set<Long> preferred, Solid ok) {
        List<int[]> out = new ArrayList<>();
        out.add(new int[]{x, y, z});
        int want = Math.max(1, Math.min(MAX_COUNT, count));
        if (want == 1) {
            return out;
        }
        int r = 1;
        while ((2 * r + 1) * (2 * r + 1) * (2 * r + 1) < want * 3 && r < 5) {
            r++;
        }
        List<long[]> found = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    int px = x + dx;
                    int py = y + dy;
                    int pz = z + dz;
                    if (py < minY || !ok.test(px, py, pz)) {
                        continue;
                    }
                    long pref = preferred.contains(key(px, py, pz)) ? 0 : 1;
                    found.add(new long[]{pref, (long) dx * dx + (long) dy * dy + (long) dz * dz, -py, px, pz, py});
                }
            }
        }
        found.sort((a, b) -> {
            for (int i = 0; i < 5; i++) {
                int c = Long.compare(a[i], b[i]);
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        });
        for (int i = 0; i < found.size() && out.size() < want; i++) {
            long[] f = found.get(i);
            out.add(new int[]{(int) f[3], (int) f[5], (int) f[4]});
        }
        return out;
    }

    /** true si la lista deja romper ese bloque: por su id o por una etiqueta (#...) que lo incluya. */
    public static boolean listed(List<String> list, String blockId, Predicate<String> inTag) {
        for (String e : list) {
            if (e.startsWith("#") ? inTag.test(e.substring(1)) : e.equals(blockId)) {
                return true;
            }
        }
        return false;
    }

    /** Un bloque o una etiqueta de la lista limpios ("Dirt" -> "minecraft:dirt"); "" si no vale. */
    public static String cleanEntry(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim().toLowerCase(Locale.ROOT);
        boolean tag = t.startsWith("#");
        if (tag) {
            t = t.substring(1).trim();
        }
        String clean = CreatureDefinition.cleanResource(t);
        return clean.isEmpty() ? "" : (tag ? "#" : "") + clean;
    }

    /** La lista limpia: sin vacios ni repetidos y como mucho {@code max}. */
    public static List<String> cleanList(List<String> in, int max) {
        Set<String> out = new LinkedHashSet<>();
        if (in != null) {
            for (String s : in) {
                String c = cleanEntry(s);
                if (!c.isEmpty() && out.size() < max) {
                    out.add(c);
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static double dist2(int[] p, double cx, double cy, double cz) {
        double dx = p[0] + 0.5 - cx;
        double dy = p[1] + 0.5 - cy;
        double dz = p[2] + 0.5 - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
