package com.creaturestudio.data;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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

    /**
     * Cuelga la pieza {@code child} de {@code newParent} (-1 = de ninguna) sin que cambie como se ve en reposo: se
     * recalcula su giro, se mueve su pivote y se desplaza su rama entera lo necesario. Asi, a partir de ahora la pieza
     * sigue a su nuevo padre cuando este se mueve o se anima. No hace nada si se crearia un ciclo.
     */
    public static void reparentKeepingPose(List<CreaturePart> parts, int child, int newParent) {
        int n = parts.size();
        if (child < 0 || child >= n || newParent >= n || newParent == child
                || (newParent >= 0 && isDescendant(parts, newParent, child))) {
            return;
        }
        Matrix4f[] rest = solve(parts, null, null);
        Matrix4f local = new Matrix4f();
        if (newParent >= 0) {
            Matrix4f inverse = new Matrix4f(rest[newParent]).invert(new Matrix4f());
            inverse.mul(rest[child], local);
        } else {
            local = new Matrix4f(rest[child]);
        }
        CreaturePart c = parts.get(child);
        // Giro: las columnas de la matriz son los ejes X, Y y Z ya girados.
        Vector3f o = local.transformPosition(0f, 0f, 0f, new Vector3f());
        Vector3f ex = local.transformPosition(1f, 0f, 0f, new Vector3f());
        Vector3f ey = local.transformPosition(0f, 1f, 0f, new Vector3f());
        Vector3f ez = local.transformPosition(0f, 0f, 1f, new Vector3f());
        float r00 = ex.x - o.x;
        float r10 = ex.y - o.y;
        float r20 = ex.z - o.z;
        float r01 = ey.x - o.x;
        float r11 = ey.y - o.y;
        float r21 = ey.z - o.z;
        float r22 = ez.z - o.z;
        float[] angles = eulerZYX(r00, r10, r20, r01, r11, r21, r22);
        Vector3f pivot = local.transformPosition(c.px, c.py, c.pz, new Vector3f());
        float dx = pivot.x - c.px;
        float dy = pivot.y - c.py;
        float dz = pivot.z - c.pz;
        int[] parent = safeParents(parts);
        for (int i = 0; i < n; i++) {
            if (i == child || isDescendantOf(parent, i, child)) {
                parts.get(i).translate(dx, dy, dz);
            }
        }
        c.rx = angles[0];
        c.ry = angles[1];
        c.rz = angles[2];
        c.parent = newParent;
    }

    /**
     * Angulos en grados {x, y, z} tales que Rz(z) * Ry(y) * Rx(x) es la matriz de giro dada (por filas:
     * r00 r01 / r10 r11 / r20 r21 r22, lo que hace falta de ella).
     */
    static float[] eulerZYX(float r00, float r10, float r20, float r01, float r11, float r21, float r22) {
        double sy = Math.max(-1.0, Math.min(1.0, -r20));
        double y = Math.asin(sy);
        double x;
        double z;
        if (Math.abs(sy) < 0.99999) {
            x = Math.atan2(r21, r22);
            z = Math.atan2(r10, r00);
        } else {
            // bloqueo del cardan: basta con un solo giro alrededor de X o Z
            x = 0.0;
            z = Math.atan2(-r01, r11);
        }
        return new float[]{clean(Math.toDegrees(x)), clean(Math.toDegrees(y)), clean(Math.toDegrees(z))};
    }

    /** Quita el ruido de coma flotante (89.99998 -> 90). */
    private static float clean(double degrees) {
        double r = Math.rint(degrees * 1000.0) / 1000.0;
        return (float) (Math.abs(r) < 1.0e-3 ? 0.0 : r);
    }

    private static boolean isDescendantOf(int[] parent, int candidate, int ancestor) {
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
