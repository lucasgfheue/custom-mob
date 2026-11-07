package com.creaturestudio.data;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Calcula la matriz de transformacion (en pixeles del modelo) de cada pieza teniendo en cuenta
 * la jerarquia padre/hijo, el pivote, la rotacion y los desplazamientos extra de la animacion.
 */
public final class PoseSolver {
    public static final float DEG = (float) (Math.PI / 180.0);

    private PoseSolver() {
    }

    /** Indices de padre validos y sin ciclos. */
    public static int[] safeParents(List<CreaturePart> parts) {
        int n = parts.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            int p = parts.get(i).parent;
            parent[i] = (p < 0 || p >= n || p == i) ? -1 : p;
        }
        for (int i = 0; i < n; i++) {
            int cur = parent[i];
            int steps = 0;
            while (cur >= 0 && steps <= n) {
                if (cur == i) {
                    parent[i] = -1; // rompe el ciclo
                    break;
                }
                cur = parent[cur];
                steps++;
            }
        }
        return parent;
    }

    /**
     * @param extraRot rotaciones extra en radianes (3 por pieza) o null
     * @param extraOff desplazamientos extra en pixeles (3 por pieza) o null
     */
    public static Matrix4f[] solve(List<CreaturePart> parts, @Nullable float[] extraRot, @Nullable float[] extraOff) {
        int n = parts.size();
        int[] parent = safeParents(parts);
        Matrix4f[] out = new Matrix4f[n];
        for (int i = 0; i < n; i++) {
            compute(i, parts, parent, extraRot, extraOff, out);
        }
        return out;
    }

    private static Matrix4f compute(int i, List<CreaturePart> parts, int[] parent,
                                    @Nullable float[] extraRot, @Nullable float[] extraOff, Matrix4f[] out) {
        if (out[i] != null) {
            return out[i];
        }
        Matrix4f m = parent[i] >= 0
                ? new Matrix4f(compute(parent[i], parts, parent, extraRot, extraOff, out))
                : new Matrix4f();
        CreaturePart p = parts.get(i);
        float rx = p.rx * DEG;
        float ry = p.ry * DEG;
        float rz = p.rz * DEG;
        float ox = 0f, oy = 0f, oz = 0f;
        if (extraRot != null) {
            rx += extraRot[i * 3];
            ry += extraRot[i * 3 + 1];
            rz += extraRot[i * 3 + 2];
        }
        if (extraOff != null) {
            ox = extraOff[i * 3];
            oy = extraOff[i * 3 + 1];
            oz = extraOff[i * 3 + 2];
        }
        m.translate(p.px + ox, p.py + oy, p.pz + oz);
        if (rx != 0f || ry != 0f || rz != 0f) {
            m.rotate(new Quaternionf().rotationZYX(rz, ry, rx));
        }
        m.translate(-p.px, -p.py, -p.pz);
        out[i] = m;
        return m;
    }

    /** true si {@code candidate} es descendiente de {@code ancestor}. */
    public static boolean isDescendant(List<CreaturePart> parts, int candidate, int ancestor) {
        int[] parent = safeParents(parts);
        int cur = candidate >= 0 && candidate < parent.length ? parent[candidate] : -1;
        int steps = 0;
        while (cur >= 0 && steps <= parent.length) {
            if (cur == ancestor) {
                return true;
            }
            cur = parent[cur];
            steps++;
        }
        return false;
    }
}
