package com.creaturestudio.data;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reproduce las animaciones propias ({@link CustomAnims}) encima de las automaticas de cada pieza.
 * <ul>
 *   <li>Bucles (quieta, caminar, volar): pasan de pose en pose con una curva suave y vuelven a empezar. Quieta y
 *   caminar se mezclan segun lo rapido que va la criatura; volar las sustituye en el aire.</li>
 *   <li>De una vez (atacar, disparar, habilidad, dano, morir): salen de la postura que tenia la pieza, pasan por las
 *   poses y vuelven a ella (morir se queda en la ultima).</li>
 * </ul>
 * Las piezas que se mueven en una animacion propia dejan de usar su animacion automatica mientras esta se
 * reproduce, salvo la cabeza que mira, que sigue mirando. Las piezas que no se mueven siguen como siempre.
 */
public final class AnimPlayer {
    private static final float DEG = PoseSolver.DEG;
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    /** Ritmo de los pasos de Minecraft (cos(limbSwing * 0,6662) en sus modelos). */
    public static final float STEP = 0.6662f;
    private static final AnimAction[] LOOPS = {AnimAction.IDLE, AnimAction.WALK, AnimAction.FLY};
    private static final AnimAction[] ONCE = {AnimAction.ATTACK, AnimAction.SHOOT, AnimAction.ABILITY,
            AnimAction.HURT, AnimAction.DEATH};

    private AnimPlayer() {
    }

    /** Ticks que dura una animacion de una vez (morir no pasa del segundo que tarda en desaparecer el cuerpo). */
    public static float durationTicks(CustomAnims ca, AnimAction a) {
        float ticks = 20f * a.seconds() / Math.max(CustomAnims.MIN_SPEED, ca.speed(a));
        return a == AnimAction.DEATH ? Math.min(ticks, 20f) : ticks;
    }

    /**
     * Mezcla las animaciones propias con lo que ya hay en rot (radianes) y off (pixeles), 3 numeros por pieza.
     *
     * @param ages ticks desde que empezo cada accion de una vez (indice = {@link AnimAction#ordinal()}); negativo o
     *             mayor que su duracion = no esta pasando. Puede ser null.
     */
    public static void apply(CreatureDefinition def, float[] rot, float[] off, float limbSwing, float limbSwingAmount,
                             float ageTicks, boolean flying, @Nullable float[] ages) {
        CustomAnims ca = def.customAnims;
        if (ca == null || !ca.any()) {
            return;
        }
        List<CreaturePart> parts = def.parts;
        int n = Math.min(parts.size(), Math.min(rot.length, off.length) / 3);
        float walk = clamp(limbSwingAmount * 2f, 0f, 1f);
        float[] weight = new float[LOOPS.length];
        float[] phase = new float[LOOPS.length];
        if (flying && ca.has(AnimAction.FLY)) {
            weight[2] = 1f;
        } else {
            weight[0] = ca.has(AnimAction.IDLE) ? 1f - walk : 0f;
            weight[1] = ca.has(AnimAction.WALK) ? walk : 0f;
        }
        phase[0] = ageTicks / (20f * AnimAction.IDLE.seconds() / speed(ca, AnimAction.IDLE));
        phase[1] = limbSwing * STEP / TWO_PI * speed(ca, AnimAction.WALK);
        phase[2] = ageTicks / (20f * AnimAction.FLY.seconds() / speed(ca, AnimAction.FLY));

        for (int i = 0; i < n; i++) {
            CreaturePart p = parts.get(i);
            if (p.moves == null) {
                continue;
            }
            float used = 0f;
            float[] acc = new float[CustomAnims.VALUES];
            for (int l = 0; l < LOOPS.length; l++) {
                AnimAction a = LOOPS[l];
                int count = ca.poses(a);
                if (weight[l] <= 0f || count == 0 || !p.movesIn(a, count)) {
                    continue;
                }
                float[] v = p.moves[a.ordinal()];
                used += weight[l];
                for (int k = 0; k < CustomAnims.VALUES; k++) {
                    acc[k] += weight[l] * loopValue(v, count, phase[l], k);
                }
            }
            if (used > 0f) {
                float keep = p.anim == PartAnim.HEAD ? 1f : 1f - Math.min(used, 1f);
                for (int k = 0; k < 3; k++) {
                    rot[i * 3 + k] = rot[i * 3 + k] * keep + acc[k] * DEG;
                    off[i * 3 + k] = off[i * 3 + k] * keep + acc[3 + k];
                }
            }
        }

        if (ages == null) {
            return;
        }
        for (AnimAction a : ONCE) {
            int count = ca.poses(a);
            float age = a.ordinal() < ages.length ? ages[a.ordinal()] : -1f;
            if (count == 0 || age < 0f || Float.isNaN(age)) {
                continue;
            }
            float dur = durationTicks(ca, a);
            boolean hold = a == AnimAction.DEATH;
            if (!hold && age >= dur) {
                continue;
            }
            float t = Math.min(1f, age / dur);
            for (int i = 0; i < n; i++) {
                CreaturePart p = parts.get(i);
                if (p.moves == null || !p.movesIn(a, count)) {
                    continue;
                }
                float[] v = p.moves[a.ordinal()];
                for (int k = 0; k < 3; k++) {
                    rot[i * 3 + k] = onceValue(v, count, t, rot[i * 3 + k], k, DEG, hold);
                    off[i * 3 + k] = onceValue(v, count, t, off[i * 3 + k], 3 + k, 1f, hold);
                }
            }
        }
    }

    /** Solo la postura normal mas una pose (para editarla en el editor): sin animaciones automaticas. */
    public static void pin(CreatureDefinition def, AnimAction a, int pose, float[] rot, float[] off) {
        java.util.Arrays.fill(rot, 0f);
        java.util.Arrays.fill(off, 0f);
        List<CreaturePart> parts = def.parts;
        int n = Math.min(parts.size(), Math.min(rot.length, off.length) / 3);
        for (int i = 0; i < n; i++) {
            CreaturePart p = parts.get(i);
            for (int k = 0; k < 3; k++) {
                rot[i * 3 + k] = p.move(a, pose, k) * DEG;
                off[i * 3 + k] = p.move(a, pose, 3 + k);
            }
        }
    }

    private static float speed(CustomAnims ca, AnimAction a) {
        return Math.max(CustomAnims.MIN_SPEED, ca.speed(a));
    }

    // ---------------------------------------------------------------------------------------
    // Curvas
    // ---------------------------------------------------------------------------------------

    /** Valor k de un bucle de "count" poses en una fase (1 = una vuelta entera). */
    static float loopValue(float[] v, int count, float phase, int k) {
        int stride = CustomAnims.VALUES;
        if (count <= 1) {
            return v[k];
        }
        float u = (phase - (float) Math.floor(phase)) * count;
        int s = Math.min((int) u, count - 1);
        float f = u - s;
        float p0 = v[((s - 1 + count) % count) * stride + k];
        float p1 = v[s * stride + k];
        float p2 = v[((s + 1) % count) * stride + k];
        float p3 = v[((s + 2) % count) * stride + k];
        return catmullRom(p0, p1, p2, p3, f);
    }

    /**
     * Valor k de una animacion de una vez en t (0 a 1): la postura de partida (base), las poses y otra vez la base
     * (o, si hold, se queda en la ultima pose). scale pasa los valores guardados a las unidades de base.
     */
    static float onceValue(float[] v, int count, float t, float base, int k, float scale, boolean hold) {
        int keys = count + (hold ? 1 : 2);
        int segs = keys - 1;
        float u = clamp(t, 0f, 1f) * segs;
        int s = Math.min((int) u, segs - 1);
        float f = u - s;
        return catmullRom(key(v, count, s - 1, base, k, scale, keys, hold), key(v, count, s, base, k, scale, keys, hold),
                key(v, count, s + 1, base, k, scale, keys, hold), key(v, count, s + 2, base, k, scale, keys, hold), f);
    }

    private static float key(float[] v, int count, int j, float base, int k, float scale, int keys, boolean hold) {
        j = Math.max(0, Math.min(keys - 1, j));
        if (j == 0 || (!hold && j == keys - 1)) {
            return base;
        }
        return v[(Math.min(j, count) - 1) * CustomAnims.VALUES + k] * scale;
    }

    /** Curva que pasa por p1 (f=0) y p2 (f=1) siguiendo la direccion de sus vecinos. */
    static float catmullRom(float p0, float p1, float p2, float p3, float f) {
        float f2 = f * f;
        float f3 = f2 * f;
        return 0.5f * (2f * p1 + (p2 - p0) * f + (2f * p0 - 5f * p1 + 4f * p2 - p3) * f2
                + (3f * p1 - p0 - 3f * p2 + p3) * f3);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
