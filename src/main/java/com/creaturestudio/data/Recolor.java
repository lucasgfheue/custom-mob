package com.creaturestudio.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Pintar toda la criatura" (pestana Pintar): cambia sus colores a otro conservando los tonos, asi el rojo pasa a azul
 * y el rojo claro a azul claro. Cambia el color de las piezas, lo pintado a mano y los minibloques. El blanco, el negro
 * y los grises (ojos, dientes...) se quedan igual, salvo que el color que se cambia sea uno de ellos.
 *
 * <p>Se trabaja siempre desde una foto de los colores de antes ({@link Snapshot}), asi se puede ir probando colores
 * sin que se vayan acumulando errores.</p>
 */
public final class Recolor {
    /** Cuantos colores de la criatura se ensenan para elegir. */
    public static final int MAX_COLORS = 8;
    /** Diferencia entre el canal mayor y el menor (de 0 a 1) por debajo de la cual un color es blanco, negro o gris. */
    static final float NEUTRAL = 0.1f;
    /** Diferencia de tono (grados) que cuenta del todo como "ese color" y a partir de la cual ya no cuenta. */
    static final float SAME_HUE = 30f;
    static final float OTHER_HUE = 50f;

    private Recolor() {
    }

    // ---------------------------------------------------------------------------------------
    // Foto de los colores
    // ---------------------------------------------------------------------------------------

    /** Los colores de cada pieza en un momento dado. */
    public static final class Snapshot {
        private final int[] colors;
        private final int[][] paint;
        private final int[][] voxels;

        private Snapshot(int n) {
            colors = new int[n];
            paint = new int[n][];
            voxels = new int[n][];
        }

        /** true si la criatura sigue teniendo las mismas piezas (con su pintura y sus minibloques del mismo tamano). */
        public boolean fits(CreatureDefinition d) {
            if (d.parts.size() != colors.length) {
                return false;
            }
            for (int i = 0; i < colors.length; i++) {
                CreaturePart p = d.parts.get(i);
                int[] pp = p.validPaint() ? p.paint : null;
                if (len(pp) != len(paint[i]) || len(p.voxels) != len(voxels[i])) {
                    return false;
                }
            }
            return true;
        }

        private static int len(int[] a) {
            return a == null ? -1 : a.length;
        }
    }

    public static Snapshot snapshot(CreatureDefinition d) {
        Snapshot s = new Snapshot(d.parts.size());
        for (int i = 0; i < d.parts.size(); i++) {
            CreaturePart p = d.parts.get(i);
            s.colors[i] = p.color & 0xFFFFFF;
            s.paint[i] = p.validPaint() ? p.paint.clone() : null;
            s.voxels[i] = p.voxels == null ? null : p.voxels.clone();
        }
        return s;
    }

    /**
     * Pone en la criatura los colores de la foto cambiados para que {@code from} pase a ser {@code to}.
     *
     * @param onlyThat true = solo ese color y sus tonos; false = toda la criatura (menos blancos, negros y grises)
     */
    public static void apply(CreatureDefinition d, Snapshot s, int from, int to, boolean onlyThat) {
        Mapper m = new Mapper(from, to, onlyThat);
        int n = Math.min(d.parts.size(), s.colors.length);
        for (int i = 0; i < n; i++) {
            CreaturePart p = d.parts.get(i);
            p.color = m.map(s.colors[i]);
            int[] paint = s.paint[i];
            if (paint != null && p.validPaint() && p.paint.length == paint.length) {
                int[] out = new int[paint.length];
                for (int k = 0; k < paint.length; k++) {
                    int c = paint[k];
                    out[k] = (c >>> 24) == 0 ? c : (c & 0xFF000000) | m.map(c);
                }
                p.paint = out;
            }
            int[] vox = s.voxels[i];
            if (vox != null && p.voxels != null && p.voxels.length == vox.length) {
                int[] out = vox.clone();
                for (int k = 1; k < out.length; k += 2) {
                    out[k] = (out[k] & 0xFF000000) | m.map(out[k]);
                }
                p.voxels = out;
            }
        }
    }

    /** Un color cambiado (para las pruebas; {@link #apply} usa el mismo calculo). */
    public static int map(int rgb, int from, int to, boolean onlyThat) {
        return new Mapper(from, to, onlyThat).map(rgb);
    }

    /** true si el color es blanco, negro o gris. */
    public static boolean neutral(int rgb) {
        return chroma(rgb) < NEUTRAL;
    }

    // ---------------------------------------------------------------------------------------
    // El cambio de un color
    // ---------------------------------------------------------------------------------------

    private static final class Mapper {
        private final float hb;
        private final float sb;
        private final float yb;
        private final float ht;
        private final float st;
        private final float lt;
        private final float yt;
        private final boolean baseNeutral;
        private final boolean onlyThat;
        private final boolean same;
        private final Map<Integer, Integer> cache = new HashMap<>();

        Mapper(int from, int to, boolean onlyThat) {
            float[] f = hsl(from);
            float[] t = hsl(to);
            this.hb = f[0];
            this.sb = f[1];
            this.yb = luma(from);
            this.ht = t[0];
            this.st = t[1];
            this.lt = t[2];
            this.yt = luma(to);
            this.baseNeutral = neutral(from);
            this.onlyThat = onlyThat;
            this.same = (from & 0xFFFFFF) == (to & 0xFFFFFF);
        }

        int map(int rgb) {
            int key = rgb & 0xFFFFFF;
            if (same) {
                return key; // "como estaba": exactamente los colores de antes
            }
            Integer known = cache.get(key);
            if (known != null) {
                return known;
            }
            int out = compute(key);
            cache.put(key, out);
            return out;
        }

        private int compute(int rgb) {
            float[] c = hsl(rgb);
            boolean neutral = neutral(rgb);
            float weight;
            float hue;
            float sat;
            if (baseNeutral) {
                // se cambia un gris: los grises toman el color nuevo (los muy claros y muy oscuros casi no cambian)
                if (onlyThat) {
                    if (!neutral) {
                        return rgb;
                    }
                    weight = clamp01(1f - (Math.abs(luma(rgb) - yb) - 0.25f) / 0.25f);
                } else {
                    weight = 1f;
                }
                hue = ht;
                sat = neutral ? st : c[1];
            } else {
                if (neutral) {
                    return rgb;
                }
                float delta = wrap(c[0] - hb);
                float far = Math.abs(delta);
                // los tonos de ese color conservan su diferencia; los demas toman el tono nuevo
                float keep = far <= SAME_HUE ? 1f : clamp01(1f - (far - SAME_HUE) / (OTHER_HUE - SAME_HUE));
                if (onlyThat) {
                    weight = keep;
                    if (weight <= 0f) {
                        return rgb;
                    }
                    hue = ht + delta;
                } else {
                    weight = 1f;
                    hue = ht + delta * keep;
                }
                sat = mapSat(c[1], sb, st);
            }
            // lo claro sigue claro: se conserva lo claro que se ve cada color (no solo su "claridad" HSL, que da
            // igual de claros al rojo y al amarillo)
            int out = withLuma(hue, sat, mapLight(luma(rgb), yb, yt), lt);
            return weight >= 0.999f ? out : lerp(rgb, out, weight);
        }
    }

    /** Saturacion: la del color de antes pasa a ser la del nuevo y las demas se mueven en proporcion. */
    static float mapSat(float s, float from, float to) {
        if (from <= 1.0e-4f) {
            return to;
        }
        if (to <= from || s <= from) {
            return Math.min(1f, s * to / from);
        }
        return to + (s - from) * (1f - to) / Math.max(1.0e-4f, 1f - from);
    }

    /** Claridad: la del color de antes pasa a ser la del nuevo; el negro y el blanco no se mueven. */
    static float mapLight(float l, float from, float to) {
        if (l <= from) {
            return from <= 1.0e-4f ? to : l * to / from;
        }
        return from >= 1f - 1.0e-4f ? to : to + (l - from) * (1f - to) / (1f - from);
    }

    /** Lo claro que se ve un color (0 = negro, 1 = blanco). */
    static float luma(int rgb) {
        return (0.299f * ((rgb >> 16) & 255) + 0.587f * ((rgb >> 8) & 255) + 0.114f * (rgb & 255)) / 255f;
    }

    /** El color con ese tono y saturacion que se ve asi de claro (luma). guess: claridad HSL si sale justo. */
    static int withLuma(float h, float s, float y, float guess) {
        float lo = 0f;
        float hi = 1f;
        float l = guess;
        if (Math.abs(lumaF(h, s, l) - y) > 1.0e-4f) {
            for (int i = 0; i < 24; i++) {
                l = (lo + hi) * 0.5f;
                if (lumaF(h, s, l) < y) {
                    lo = l;
                } else {
                    hi = l;
                }
            }
            l = (lo + hi) * 0.5f;
        }
        return rgbOf(h, s, l);
    }

    private static float lumaF(float h, float s, float l) {
        float[] c = rgbF(h, s, l);
        return 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2];
    }

    // ---------------------------------------------------------------------------------------
    // Colores principales
    // ---------------------------------------------------------------------------------------

    /** Los colores que mas se ven en la criatura (uno por familia de tonos), del que mas al que menos. */
    public static int[] mainColors(CreatureDefinition d) {
        Map<Integer, Double> weight = new HashMap<>();
        for (CreaturePart p : d.parts) {
            double area = 2.0 * (p.w * p.h + p.w * p.d + p.h * p.d);
            if (area > 0.0) {
                double base = MaterialIds.CLEAR.equals(p.material) ? 0.0 : area;
                if (p.validPaint()) {
                    int faces = 2 * (p.pw * p.ph + p.pw * p.pd + p.ph * p.pd);
                    if (faces > 0) {
                        double per = area / faces;
                        int painted = 0;
                        for (int c : p.paint) {
                            if ((c >>> 24) != 0) {
                                painted++;
                                weight.merge(c & 0xFFFFFF, per, Double::sum);
                            }
                        }
                        base *= Math.max(0.0, 1.0 - (double) painted / faces);
                    }
                }
                if (base > 0.0) {
                    weight.merge(p.color & 0xFFFFFF, base, Double::sum);
                }
            }
            if (p.voxels != null) {
                for (int k = 0; k + 1 < p.voxels.length; k += 2) {
                    float side = Voxels.pixels(Voxels.sizeOf(p.voxels[k + 1]));
                    weight.merge(p.voxels[k + 1] & 0xFFFFFF, 3.0 * side * side, Double::sum);
                }
            }
        }
        if (weight.isEmpty()) {
            return new int[0];
        }
        List<Map.Entry<Integer, Double>> colors = new ArrayList<>(weight.entrySet());
        colors.sort((a, b) -> {
            int c = Double.compare(b.getValue(), a.getValue());
            return c != 0 ? c : Integer.compare(a.getKey(), b.getKey());
        });
        // familias: los colores por tono y los grises por claridad (oscuros, medios y claros)
        List<double[]> families = new ArrayList<>(); // {color, hue, grupo de grises o -1, peso}
        for (Map.Entry<Integer, Double> e : colors) {
            int rgb = e.getKey();
            float[] c = hsl(rgb);
            int band = neutral(rgb) ? (c[2] < 0.3f ? 0 : c[2] < 0.7f ? 1 : 2) : -1;
            double[] home = null;
            for (double[] f : families) {
                boolean same = band >= 0 ? f[2] == band : f[2] < 0 && Math.abs(wrap(c[0] - (float) f[1])) <= SAME_HUE;
                if (same) {
                    home = f;
                    break;
                }
            }
            if (home == null) {
                families.add(new double[]{rgb, c[0], band, e.getValue()});
            } else {
                home[3] += e.getValue();
            }
        }
        families.sort((a, b) -> Double.compare(b[3], a[3]));
        List<Integer> out = new ArrayList<>();
        for (double[] f : families) {
            if (out.size() >= MAX_COLORS) {
                break;
            }
            out.add((int) f[0]);
        }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = out.get(i);
        }
        return r;
    }

    // ---------------------------------------------------------------------------------------
    // Cuentas de color (HSL: tono 0-360, saturacion y claridad 0-1)
    // ---------------------------------------------------------------------------------------

    static float chroma(int rgb) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        return (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) / 255f;
    }

    static float[] hsl(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f;
        float g = ((rgb >> 8) & 255) / 255f;
        float b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f;
        float h = 0f;
        float s = 0f;
        float d = max - min;
        if (d > 1.0e-6f) {
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) {
                h = (g - b) / d + (g < b ? 6f : 0f);
            } else if (max == g) {
                h = (b - r) / d + 2f;
            } else {
                h = (r - g) / d + 4f;
            }
            h *= 60f;
        }
        return new float[]{h, s, l};
    }

    static int rgbOf(float h, float s, float l) {
        float[] c = rgbF(h, s, l);
        return (clamp255(Math.round(c[0] * 255f)) << 16) | (clamp255(Math.round(c[1] * 255f)) << 8)
                | clamp255(Math.round(c[2] * 255f));
    }

    /** HSL a RGB (de 0 a 1, sin redondear). */
    private static float[] rgbF(float h, float s, float l) {
        h = ((h % 360f) + 360f) % 360f;
        s = clamp01(s);
        l = clamp01(l);
        if (s <= 1.0e-6f) {
            return new float[]{l, l, l};
        }
        float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
        float p = 2f * l - q;
        float t = h / 360f;
        return new float[]{hueToRgb(p, q, t + 1f / 3f), hueToRgb(p, q, t), hueToRgb(p, q, t - 1f / 3f)};
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0f) {
            t += 1f;
        }
        if (t > 1f) {
            t -= 1f;
        }
        if (t < 1f / 6f) {
            return p + (q - p) * 6f * t;
        }
        if (t < 0.5f) {
            return q;
        }
        if (t < 2f / 3f) {
            return p + (q - p) * (2f / 3f - t) * 6f;
        }
        return p;
    }

    /** Diferencia de tono entre -180 y 180 grados. */
    static float wrap(float deg) {
        float d = deg % 360f;
        if (d > 180f) {
            d -= 360f;
        } else if (d <= -180f) {
            d += 360f;
        }
        return d;
    }

    private static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(bl);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }
}
