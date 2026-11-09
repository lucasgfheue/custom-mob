package com.creaturestudio.data;

import java.util.function.IntUnaryOperator;

/**
 * Busca la altura a la que puede aparecer una criatura en una columna del mundo. No usa nada de Minecraft (el mundo
 * llega por {@link Column}) para poder probarla sin el juego.
 */
public final class SpawnSearch {
    /** No hay sitio en esta columna. */
    public static final int NONE = Integer.MIN_VALUE;

    private SpawnSearch() {
    }

    /** Lo que hay en cada altura de una columna. */
    public interface Column {
        /** El bloque tiene forma de choque (se puede estar encima). */
        boolean solid(int y);

        /** Se puede estar dentro: sin forma de choque y sin lava (el agua vale). */
        boolean passable(int y);

        boolean water(int y);

        /** Se ve el cielo desde ahi. */
        boolean sky(int y);
    }

    /**
     * Altura para un lugar sin estructuras.
     *
     * @param top     primera altura libre encima del terreno (o del agua), como el mapa de alturas
     * @param minY    altura minima del mundo
     * @param ceiling el mundo tiene techo (Nether): no hay superficie, se busca en los huecos
     * @param flyer   la criatura vuela
     * @param height  alto de la criatura en bloques (redondeado hacia arriba)
     * @param random  random.applyAsInt(n) da un numero de 0 a n-1
     */
    public static int find(SpawnPlace place, Column c, int top, int minY, boolean ceiling, boolean flyer, int height,
                           IntUnaryOperator random) {
        height = Math.max(1, height);
        SpawnPlace p = place;
        if (p == SpawnPlace.SKY && !flyer) {
            p = SpawnPlace.SURFACE;
        }
        if (ceiling && (p == SpawnPlace.SURFACE || p == SpawnPlace.SKY)) {
            p = SpawnPlace.UNDERGROUND;
        }
        return switch (p) {
            case SURFACE -> surface(c, top, flyer, height, random);
            case SKY -> sky(c, top, height, random);
            case UNDERGROUND -> underground(c, top, minY, ceiling, flyer, height, random);
            case WATER -> water(c, top, minY, random);
        };
    }

    private static int surface(Column c, int top, boolean flyer, int height, IntUnaryOperator random) {
        int y = flyer ? top + 2 + random.applyAsInt(6) : top;
        if (c.water(y) || c.water(y - 1) || !fits(c, y, height)) {
            return NONE;
        }
        if (!flyer && !c.solid(y - 1)) {
            return NONE;
        }
        return y;
    }

    private static int sky(Column c, int top, int height, IntUnaryOperator random) {
        int y = top + 8 + random.applyAsInt(16);
        return fits(c, y, height) && !c.water(y) ? y : NONE;
    }

    private static int underground(Column c, int top, int minY, boolean ceiling, boolean flyer, int height,
                                   IntUnaryOperator random) {
        // en el Nether el mapa de alturas da el techo de piedra base: se busca por debajo
        int maxY = ceiling ? top - 3 : top - 6;
        int low = minY + 1;
        if (maxY - low < 2) {
            return NONE;
        }
        int start = low + random.applyAsInt(maxY - low);
        for (int y = start; y >= Math.max(low, start - 32); y--) {
            if (c.water(y) || !fits(c, y, height)) {
                continue;
            }
            if (!flyer && !c.solid(y - 1)) {
                continue;
            }
            if (!ceiling && c.sky(y)) {
                continue;
            }
            return y;
        }
        return NONE;
    }

    private static int water(Column c, int top, int minY, IntUnaryOperator random) {
        int surface = top - 1;
        if (!c.water(surface)) {
            return NONE;
        }
        int depth = 0;
        while (surface - depth > minY && c.water(surface - depth) && depth < 64) {
            depth++;
        }
        return surface - random.applyAsInt(depth);
    }

    /**
     * Altura dentro de la caja de una pieza de estructura (de arriba abajo, empezando a una altura al azar para no
     * aparecer siempre en el piso de arriba). Vale aire o agua, no lava.
     */
    public static int findInBox(Column c, int minY, int maxY, boolean flyer, int height, IntUnaryOperator random) {
        height = Math.max(1, height);
        int span = maxY - minY + 1;
        if (span <= 0) {
            return NONE;
        }
        int start = minY + random.applyAsInt(span);
        for (int i = 0; i < span; i++) {
            int y = start - i;
            if (y < minY) {
                y += span;
            }
            if (!fits(c, y, height)) {
                continue;
            }
            if (!flyer && !c.solid(y - 1)) {
                continue;
            }
            return y;
        }
        return NONE;
    }

    private static boolean fits(Column c, int y, int height) {
        for (int i = 0; i < height; i++) {
            if (!c.passable(y + i)) {
                return false;
            }
        }
        return true;
    }
}
