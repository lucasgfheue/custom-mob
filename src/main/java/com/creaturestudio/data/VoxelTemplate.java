package com.creaturestudio.data;

import java.util.Arrays;
import java.util.Locale;

/**
 * Accesorios hechos de minibloques que se ponen sobre una pieza con un clic (casco, corona, cuernos...).
 * Se construyen alrededor de la caja de la pieza: x de 0 a W-1, y de 0 a H-1, z de 0 a D-1; el frente es -Z.
 */
public enum VoxelTemplate {
    HELMET,
    CROWN,
    HORNS,
    HALO,
    BEARD,
    CHESTPLATE,
    BELT,
    CAPE,
    SHOULDERS,
    BOOTS;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Minibloques del accesorio para esa pieza, del color dado. */
    public int[] build(CreaturePart p, int rgb) {
        int w = Math.max(1, Math.round(p.w));
        int h = Math.max(1, Math.round(p.h));
        int d = Math.max(1, Math.round(p.d));
        Builder b = new Builder(rgb);
        switch (this) {
            case HELMET -> {
                // Casco: cubre la parte de arriba y los lados, con un hueco para la cara
                int y0 = Math.max(0, h - Math.max(3, (int) Math.ceil(h * 0.6)));
                for (int x = -1; x <= w; x++) {
                    for (int y = y0; y <= h; y++) {
                        for (int z = -1; z <= d; z++) {
                            boolean shell = x == -1 || x == w || y == h || z == -1 || z == d;
                            if (!shell) {
                                continue;
                            }
                            boolean face = z == -1 && x >= 1 && x <= w - 2 && y < h - 2 && y != h;
                            if (!face) {
                                b.add(x, y, z);
                            }
                        }
                    }
                }
            }
            case CROWN -> {
                // Corona: un aro sobre la cabeza con puntas
                for (int x = 0; x < w; x++) {
                    for (int z = 0; z < d; z++) {
                        boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                        if (!edge) {
                            continue;
                        }
                        b.add(x, h, z);
                        // puntas alternas, iguales a los dos lados
                        if ((Math.min(x, w - 1 - x) + Math.min(z, d - 1 - z)) % 2 == 0) {
                            b.add(x, h + 1, z);
                        }
                        boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                        if (corner) {
                            b.add(x, h + 1, z);
                            b.add(x, h + 2, z);
                        }
                    }
                }
            }
            case HORNS -> {
                // Cuernos: dos, que suben y se abren hacia fuera
                int z = Math.max(0, d / 2 - 1);
                for (int side = 0; side < 2; side++) {
                    int x = side == 0 ? 0 : w - 1;
                    int out = side == 0 ? -1 : 1;
                    b.add(x, h, z);
                    b.add(x, h + 1, z);
                    b.add(x + out, h + 2, z);
                    b.add(x + out, h + 3, z);
                    b.add(x + 2 * out, h + 4, z);
                    b.add(x + 2 * out, h + 4, z + 1);
                }
            }
            case HALO -> {
                // Aureola: un aro flotando encima de la cabeza
                int x0 = w > 4 ? 1 : 0;
                int x1 = w > 4 ? w - 2 : w - 1;
                int z0 = d > 4 ? 1 : 0;
                int z1 = d > 4 ? d - 2 : d - 1;
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        if (x == x0 || x == x1 || z == z0 || z == z1) {
                            b.add(x, h + 2, z);
                        }
                    }
                }
            }
            case BEARD -> {
                // Barba: delante de la boca y colgando por debajo de la barbilla
                int x0 = w > 2 ? 1 : 0;
                int x1 = w > 2 ? w - 2 : w - 1;
                for (int x = x0; x <= x1; x++) {
                    b.add(x, 0, -1);
                    b.add(x, 1, -1);
                    b.add(x, -1, -1);
                    b.add(x, -1, 0);
                }
                for (int x = Math.min(x0 + 1, x1); x <= Math.max(x1 - 1, x0); x++) {
                    b.add(x, -2, -1);
                }
            }
            case CHESTPLATE -> {
                // Pechera: rodea el cuerpo por los cuatro lados
                for (int x = -1; x <= w; x++) {
                    for (int y = 0; y < h; y++) {
                        for (int z = -1; z <= d; z++) {
                            if (x == -1 || x == w || z == -1 || z == d) {
                                b.add(x, y, z);
                            }
                        }
                    }
                }
            }
            case BELT -> {
                // Cinturon con hebilla
                for (int x = -1; x <= w; x++) {
                    for (int z = -1; z <= d; z++) {
                        if (x == -1 || x == w || z == -1 || z == d) {
                            b.add(x, 0, z);
                        }
                    }
                }
                int mid = w / 2;
                b.add(mid - 1, 0, -2);
                b.add(mid, 0, -2);
                b.add(mid - 1, 1, -2);
                b.add(mid, 1, -2);
            }
            case CAPE -> {
                // Capa: una lamina por detras que cae mas abajo del cuerpo
                int bottom = -Math.round(h * 0.8f);
                for (int x = 0; x < w; x++) {
                    for (int y = bottom; y < h; y++) {
                        b.add(x, y, d);
                    }
                }
            }
            case SHOULDERS -> {
                // Hombrera: encima del brazo y bajando por el lado de fuera
                boolean left = p.x + p.w / 2f > 0f; // +X es la izquierda de la criatura
                int outer = left ? w : -1;
                for (int x = -1; x <= w; x++) {
                    for (int z = -1; z <= d; z++) {
                        b.add(x, h, z);
                    }
                }
                for (int y = Math.max(0, h - 3); y < h; y++) {
                    for (int z = -1; z <= d; z++) {
                        b.add(outer, y, z);
                    }
                }
            }
            case BOOTS -> {
                // Botas: rodean la parte de abajo de la pierna, con puntera
                int top = Math.min(h, 3);
                for (int x = -1; x <= w; x++) {
                    for (int y = 0; y < top; y++) {
                        for (int z = -1; z <= d; z++) {
                            if (x == -1 || x == w || z == -1 || z == d) {
                                b.add(x, y, z);
                            }
                        }
                    }
                }
                for (int x = 0; x < w; x++) {
                    b.add(x, 0, -2);
                }
            }
            default -> {
            }
        }
        return b.result();
    }

    /** Acumula minibloques sin repetir y descarta los que salen del rango guardable. */
    private static final class Builder {
        private final int rgb;
        private int[] out = new int[64];
        private int n;

        Builder(int rgb) {
            this.rgb = rgb & 0xFFFFFF;
        }

        void add(int x, int y, int z) {
            if (!Voxels.inRange(x, y, z)) {
                return;
            }
            int pos = Voxels.pack(x, y, z);
            for (int i = 0; i < n; i += 2) {
                if (out[i] == pos) {
                    return;
                }
            }
            if (n + 2 > out.length) {
                out = Arrays.copyOf(out, out.length * 2);
            }
            out[n++] = pos;
            out[n++] = rgb;
        }

        int[] result() {
            return Arrays.copyOf(out, n);
        }
    }
}
