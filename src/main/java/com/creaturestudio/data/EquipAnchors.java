package com.creaturestudio.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Donde se pone el equipo en un modelo cualquiera: la cabeza (casco), el cuerpo (pechera), los brazos (mangas de la
 * pechera y objetos en las manos) y las patas (pantalones y botas). Se buscan por la animacion de cada pieza (la
 * cabeza mira, los brazos y las patas se balancean) y por su nombre.
 */
public final class EquipAnchors {
    public final int head;
    public final int body;
    /** Brazo derecho (ARM_A) e izquierdo (ARM_B), o -1. */
    public final int rightArm;
    public final int leftArm;
    public final int[] legs;
    /** true si la pata esta en el lado izquierdo de la criatura (+X). */
    public final boolean[] legLeft;

    private EquipAnchors(int head, int body, int rightArm, int leftArm, int[] legs, boolean[] legLeft) {
        this.head = head;
        this.body = body;
        this.rightArm = rightArm;
        this.leftArm = leftArm;
        this.legs = legs;
        this.legLeft = legLeft;
    }

    public static EquipAnchors find(CreatureDefinition d) {
        List<CreaturePart> parts = d.parts;
        int[] parent = PoseSolver.safeParents(parts);
        int headRoot = root(parts, parent, PartAnim.HEAD);
        if (headRoot < 0) {
            headRoot = BodyParts.head(d);
        }
        int head = headRoot >= 0 ? bestBox(parts, parent, headRoot, "head", "cabeza") : -1;
        int body = BodyParts.body(d);
        if (body == head) {
            body = -1;
        }
        int rightArm = root(parts, parent, PartAnim.ARM_A);
        int leftArm = root(parts, parent, PartAnim.ARM_B);
        rightArm = rightArm >= 0 ? bestBox(parts, parent, rightArm) : -1;
        leftArm = leftArm >= 0 ? bestBox(parts, parent, leftArm) : -1;
        List<Integer> legs = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            PartAnim a = parts.get(i).anim;
            boolean leg = a == PartAnim.LEG_A || a == PartAnim.LEG_B || a == PartAnim.SIDE_LEG_A
                    || a == PartAnim.SIDE_LEG_B;
            if (leg && !hasAncestorLeg(parts, parent, i)) {
                int box = bestBox(parts, parent, i);
                if (box >= 0) {
                    legs.add(box);
                }
            }
        }
        int[] legIdx = new int[legs.size()];
        boolean[] left = new boolean[legs.size()];
        for (int k = 0; k < legIdx.length; k++) {
            legIdx[k] = legs.get(k);
            CreaturePart p = parts.get(legIdx[k]);
            left[k] = p.x + p.w / 2f > 0f;
        }
        return new EquipAnchors(head, body, rightArm, leftArm, legIdx, left);
    }

    /** La primera pieza con esa animacion que no cuelga de otra con la misma. */
    private static int root(List<CreaturePart> parts, int[] parent, PartAnim anim) {
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).anim != anim) {
                continue;
            }
            boolean nested = false;
            int steps = 0;
            for (int up = parent[i]; up >= 0 && steps <= parts.size(); up = parent[up], steps++) {
                if (parts.get(up).anim == anim) {
                    nested = true;
                    break;
                }
            }
            if (!nested) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasAncestorLeg(List<CreaturePart> parts, int[] parent, int i) {
        int steps = 0;
        for (int up = parent[i]; up >= 0 && steps <= parts.size(); up = parent[up], steps++) {
            PartAnim a = parts.get(up).anim;
            if (a == PartAnim.LEG_A || a == PartAnim.LEG_B || a == PartAnim.SIDE_LEG_A || a == PartAnim.SIDE_LEG_B) {
                return true;
            }
        }
        return false;
    }

    private static float volume(CreaturePart p) {
        return p.w * p.h * Math.max(p.d, 0.01f);
    }

    /**
     * La pieza con caja de la rama que empieza en root: la que tenga uno de los nombres preferidos, si no root (si
     * tiene tamano), y si no la mas grande de sus hijas.
     */
    static int bestBox(List<CreaturePart> parts, int[] parent, int root, String... prefer) {
        int best = -1;
        float bestV = 0f;
        for (int i = 0; i < parts.size(); i++) {
            if (i != root && !descends(parent, i, root)) {
                continue;
            }
            CreaturePart p = parts.get(i);
            if (volume(p) <= 0f) {
                continue;
            }
            String name = p.name.trim().toLowerCase(Locale.ROOT);
            for (String want : prefer) {
                if (name.equals(want)) {
                    return i;
                }
            }
            float v = i == root ? Float.MAX_VALUE : volume(p);
            if (v > bestV) {
                bestV = v;
                best = i;
            }
        }
        return best;
    }

    private static boolean descends(int[] parent, int i, int ancestor) {
        int steps = 0;
        for (int up = parent[i]; up >= 0 && steps <= parent.length; up = parent[up], steps++) {
            if (up == ancestor) {
                return true;
            }
        }
        return false;
    }
}
