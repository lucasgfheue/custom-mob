package com.creaturestudio.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Partes del cuerpo ya hechas para el modo facil. Cada una se coloca sola respecto al cuerpo y a la
 * cabeza que ya tenga la criatura; si falta el cuerpo, se crea uno.
 * Coordenadas del editor: Y hacia arriba, suelo en 0, frente hacia -Z e izquierda de la criatura en +X.
 */
public final class BodyParts {

    public enum Kind {
        BODY_BIPED("body_biped"),
        BODY_QUADRUPED("body_quadruped"),
        HEAD("head"),
        EYES("eyes"),
        EARS("ears"),
        HORNS("horns"),
        SNOUT("snout"),
        ANTENNAE("antennae"),
        ARMS("arms"),
        LEGS("legs"),
        SPIDER_LEGS("spider_legs"),
        TAIL("tail"),
        WINGS("wings"),
        FINS("fins");

        public final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    /** Resultado de {@link #add}: primera pieza nueva (o la que ya existia) y si se creo algo. */
    public record Result(int index, boolean created) {
    }

    private BodyParts() {
    }

    /**
     * Anade la parte del cuerpo. Devuelve null si no caben mas piezas.
     * Si la criatura ya tiene cuerpo (o cabeza) y se pide otro, devuelve el que ya hay sin crear nada.
     */
    /** Lo que hay que subir el modelo para que quepan las patas (se aplica al final). */
    private static final ThreadLocal<float[]> LIFT = ThreadLocal.withInitial(() -> new float[1]);

    public static Result add(CreatureDefinition def, Kind kind, int color) {
        float[] lift = LIFT.get();
        lift[0] = 0f;
        if (def.parts.size() + maxNew(kind) > CreatureDefinition.MAX_PARTS) {
            return null;
        }
        List<CreaturePart> added = new ArrayList<>();
        int base = def.parts.size();
        int[] body = {body(def)};
        switch (kind) {
            case BODY_BIPED, BODY_QUADRUPED -> {
                if (body[0] >= 0) {
                    return new Result(body[0], false);
                }
                added.add(bodyPart(kind == Kind.BODY_QUADRUPED, color));
            }
            case HEAD -> {
                int head = head(def);
                if (head >= 0) {
                    return new Result(head, false);
                }
                headWithEyes(def, body[0], color, base, added);
            }
            case EYES, EARS, HORNS, SNOUT, ANTENNAE -> {
                int target = head(def);
                if (target < 0) {
                    // sin cabeza: primero se pone una (con ojos) encima o delante del cuerpo
                    headWithEyes(def, body[0], color, base, added);
                    target = base;
                    if (kind == Kind.EYES) {
                        break;
                    }
                }
                CreaturePart h = target < base ? def.parts.get(target) : added.get(target - base);
                switch (kind) {
                    case EYES -> eyes(h, target, added);
                    case EARS -> pair(added, h, target, "oreja", Math.max(1.5f, h.w * 0.25f), Math.max(2f, h.h * 0.4f), 1f,
                            h.x + h.w - Math.max(1.5f, h.w * 0.25f) - 0.5f, h.x + 0.5f, h.y + h.h, h.z + h.d * 0.5f - 0.5f,
                            color, MaterialIds.SMOOTH, 0f, PartAnim.NONE, PartAnim.NONE, true);
                    case HORNS -> pair(added, h, target, "cuerno", 1.5f, Math.max(3f, h.h * 0.5f), 1.5f,
                            h.x + h.w - 2.5f, h.x + 1f, h.y + h.h - 0.5f, h.z + 1f,
                            0xE8DCC0, MaterialIds.SMOOTH, 20f, PartAnim.NONE, PartAnim.NONE, true);
                    case ANTENNAE -> {
                        int first = added.size();
                        pair(added, h, target, "antena", 1f, 6f, 1f, h.x + h.w * 0.7f - 0.5f, h.x + h.w * 0.3f - 0.5f,
                                h.y + h.h, h.z + 1f, color, MaterialIds.SMOOTH, 25f, PartAnim.NONE, PartAnim.NONE, true);
                        added.get(first).rx = -15f;
                        added.get(first + 1).rx = -15f;
                    }
                    default -> { // SNOUT
                        float w = Math.max(2f, h.w * 0.5f);
                        float hh = Math.max(1.5f, h.h * 0.35f);
                        float d = Math.max(2f, h.d * 0.3f);
                        added.add(part("hocico", cx(h) - w / 2f, h.y + 0.5f, h.z - d, w, hh, d,
                                cx(h), h.y + 0.5f, h.z, target, color, MaterialIds.SMOOTH, PartAnim.NONE));
                    }
                }
            }
            case ARMS, LEGS, SPIDER_LEGS, TAIL, WINGS, FINS -> {
                if (body[0] < 0) {
                    boolean four = kind != Kind.ARMS && kind != Kind.LEGS;
                    added.add(bodyPart(four, color));
                    body[0] = base;
                }
                limbs(def, kind, body[0], color, base, added);
            }
        }
        if (added.isEmpty()) {
            return new Result(body[0], false);
        }
        int existing = find(def, added.get(0).name);
        if (existing >= 0) {
            // ya lo tiene (p. ej. pulsar dos veces "Piernas"): se selecciona lo que hay en vez de duplicarlo
            return new Result(existing, false);
        }
        if (lift[0] != 0f) {
            for (CreaturePart p : def.parts) {
                p.translate(0f, lift[0], 0f);
            }
        }
        def.parts.addAll(added);
        return new Result(base, true);
    }

    /** Como mucho, cuantas piezas anade cada parte (para no pasar del limite). */
    private static int maxNew(Kind kind) {
        return switch (kind) {
            case BODY_BIPED, BODY_QUADRUPED -> 1;
            case HEAD -> 3;
            case EYES, EARS, HORNS, ANTENNAE -> 5;
            case SNOUT -> 4;
            case SPIDER_LEGS -> 9;
            case LEGS -> 5;
            default -> 3; // brazos, cola, alas, aletas (+ cuerpo)
        };
    }

    // ---------------------------------------------------------------------------------------

    private static CreaturePart bodyPart(boolean fourLegs, int color) {
        return fourLegs
                ? part("cuerpo", -4f, 8f, -7f, 8f, 7f, 14f, 0f, 11.5f, 0f, -1, color, MaterialIds.SMOOTH, PartAnim.NONE)
                : part("cuerpo", -4f, 12f, -2f, 8f, 12f, 4f, 0f, 18f, 0f, -1, color, MaterialIds.SMOOTH, PartAnim.NONE);
    }

    private static void headWithEyes(CreatureDefinition def, int bodyIndex, int color, int base, List<CreaturePart> added) {
        CreaturePart head;
        if (bodyIndex >= 0) {
            CreaturePart b = bodyIndex < base ? def.parts.get(bodyIndex) : added.get(bodyIndex - base);
            float s = Math.max(5f, Math.min(8f, b.w));
            if (horizontal(b)) {
                float hy = b.y + b.h - s * 0.6f;
                head = part("cabeza", cx(b) - s / 2f, hy, b.z - s + 1f, s, s, s, cx(b), hy + s * 0.4f, b.z,
                        bodyIndex, color, MaterialIds.SMOOTH, PartAnim.HEAD);
            } else {
                float hy = b.y + b.h;
                head = part("cabeza", cx(b) - s / 2f, hy, cz(b) - s / 2f, s, s, s, cx(b), hy, cz(b),
                        bodyIndex, color, MaterialIds.SMOOTH, PartAnim.HEAD);
            }
        } else {
            head = part("cabeza", -4f, 0f, -4f, 8f, 8f, 8f, 0f, 0f, 0f, -1, color, MaterialIds.SMOOTH, PartAnim.HEAD);
        }
        int index = base + added.size();
        added.add(head);
        eyes(head, index, added);
    }

    private static void eyes(CreaturePart h, int parent, List<CreaturePart> out) {
        float e = Math.max(1f, Math.min(2f, h.w / 4f));
        float y = h.y + h.h * 0.55f;
        float z = h.z - 0.5f;
        float left = h.x + h.w * 0.72f - e / 2f;
        float right = h.x + h.w * 0.28f - e / 2f;
        for (int k = 0; k < 2; k++) {
            float x = k == 0 ? left : right;
            CreaturePart eye = part(k == 0 ? "ojo izquierdo" : "ojo derecho", x, y, z, e, e, 0.5f,
                    x + e / 2f, y + e / 2f, h.z, parent, 0xFFFFFF, MaterialIds.EYE, PartAnim.NONE);
            eye.stretch = true;
            out.add(eye);
        }
    }

    /**
     * Dos piezas iguales a cada lado (izquierda en +X, derecha en -X). tilt: grados que se abren hacia fuera
     * (giro en Z); pivote en la base de cada pieza.
     */
    private static void pair(List<CreaturePart> out, CreaturePart anchor, int parent, String name, float w, float h,
                             float d, float leftX, float rightX, float y, float z, int color, String material,
                             float tilt, PartAnim leftAnim, PartAnim rightAnim, boolean feminine) {
        String l = feminine ? " izquierda" : " izquierdo";
        String r = feminine ? " derecha" : " derecho";
        CreaturePart a = part(name + l, leftX, y, z, w, h, d, leftX + w / 2f, y, z + d / 2f, parent, color, material, leftAnim);
        a.rz = -tilt;
        CreaturePart b = part(name + r, rightX, y, z, w, h, d, rightX + w / 2f, y, z + d / 2f, parent, color, material, rightAnim);
        b.rz = tilt;
        out.add(a);
        out.add(b);
    }

    private static void limbs(CreatureDefinition def, Kind kind, int bi, int color, int base, List<CreaturePart> added) {
        CreaturePart b = bi < base ? def.parts.get(bi) : added.get(bi - base);
        switch (kind) {
            case LEGS -> {
                boolean four = horizontal(b);
                float need = four ? 8f : 12f;
                if (b.y < 2f) {
                    raise(def, added, need - b.y);
                }
                float legH = b.y + pendingLift(def, b);
                if (four) {
                    float lw = Math.max(2f, Math.min(4f, b.w * 0.375f));
                    float front = b.z + 1f;
                    float back = b.z + b.d - lw - 1f;
                    float left = b.x + b.w - lw - 0.5f;
                    float right = b.x + 0.5f;
                    added.add(leg("pata delantera izquierda", left, front, lw, legH, bi, color, PartAnim.LEG_A));
                    added.add(leg("pata delantera derecha", right, front, lw, legH, bi, color, PartAnim.LEG_B));
                    added.add(leg("pata trasera izquierda", left, back, lw, legH, bi, color, PartAnim.LEG_B));
                    added.add(leg("pata trasera derecha", right, back, lw, legH, bi, color, PartAnim.LEG_A));
                } else {
                    float lw = Math.max(2f, Math.min(4f, b.w / 2f));
                    float z = cz(b) - lw / 2f;
                    added.add(leg("pierna izquierda", cx(b), z, lw, legH, bi, color, PartAnim.LEG_B));
                    added.add(leg("pierna derecha", cx(b) - lw, z, lw, legH, bi, color, PartAnim.LEG_A));
                }
            }
            case ARMS -> {
                float aw = Math.max(2f, Math.min(4f, b.w / 2f));
                float ah = Math.max(4f, Math.min(12f, b.h));
                float ad = Math.max(2f, Math.min(4f, b.d));
                float top = b.y + b.h;
                float z = horizontal(b) ? b.z + 1f : cz(b) - ad / 2f;
                CreaturePart left = part("brazo izquierdo", b.x + b.w, top - ah, z, aw, ah, ad,
                        b.x + b.w + aw / 2f, top - 2f, z + ad / 2f, bi, color, MaterialIds.SMOOTH, PartAnim.ARM_B);
                CreaturePart right = part("brazo derecho", b.x - aw, top - ah, z, aw, ah, ad,
                        b.x - aw / 2f, top - 2f, z + ad / 2f, bi, color, MaterialIds.SMOOTH, PartAnim.ARM_A);
                added.add(left);
                added.add(right);
            }
            case SPIDER_LEGS -> {
                if (b.y < 4f) {
                    raise(def, added, 4f - b.y);
                }
                float len = Math.max(6f, b.w * 1.2f);
                float t = 1.5f;
                float y = b.y + pendingLift(def, b) + b.h * 0.4f;
                for (int k = 0; k < 4; k++) {
                    float z = b.z + b.d * (0.15f + 0.7f * k / 3f) - t / 2f;
                    PartAnim a = k % 2 == 0 ? PartAnim.SIDE_LEG_A : PartAnim.SIDE_LEG_B;
                    PartAnim o = k % 2 == 0 ? PartAnim.SIDE_LEG_B : PartAnim.SIDE_LEG_A;
                    CreaturePart left = part("pata " + (k + 1) + " izquierda", b.x + b.w, y, z, len, t, t,
                            b.x + b.w, y + t / 2f, z + t / 2f, bi, color, MaterialIds.SMOOTH, a);
                    left.rz = -35f;
                    CreaturePart right = part("pata " + (k + 1) + " derecha", b.x - len, y, z, len, t, t,
                            b.x, y + t / 2f, z + t / 2f, bi, color, MaterialIds.SMOOTH, o);
                    right.rz = 35f;
                    added.add(left);
                    added.add(right);
                }
            }
            case TAIL -> {
                boolean four = horizontal(b);
                float tw = Math.max(1.5f, Math.min(3f, b.w / 4f));
                float td = Math.max(5f, four ? b.d * 0.6f : 8f);
                float y = four ? b.y + b.h - tw - 1f : b.y + 1f;
                float z = b.z + b.d;
                CreaturePart tail = part("cola", cx(b) - tw / 2f, y, z, tw, tw, td, cx(b), y + tw / 2f, z,
                        bi, color, MaterialIds.SMOOTH, PartAnim.TAIL);
                tail.rx = -25f;
                added.add(tail);
            }
            case WINGS -> {
                float ww = Math.max(8f, b.w * 1.4f);
                float wd = Math.max(5f, Math.min(12f, b.d * 0.8f));
                float y = b.y + b.h - 1f;
                float z = cz(b) - wd / 2f;
                added.add(part("ala izquierda", b.x + b.w, y, z, ww, 1f, wd, b.x + b.w, y + 0.5f, cz(b),
                        bi, color, MaterialIds.FEATHERS, PartAnim.WING_A));
                added.add(part("ala derecha", b.x - ww, y, z, ww, 1f, wd, b.x, y + 0.5f, cz(b),
                        bi, color, MaterialIds.FEATHERS, PartAnim.WING_B));
            }
            default -> { // FINS
                float fh = Math.max(3f, b.h * 0.5f);
                float fd = Math.max(4f, b.d * 0.4f);
                float y = b.y + b.h * 0.2f;
                float z = cz(b) - fd / 2f;
                CreaturePart left = part("aleta izquierda", b.x + b.w, y, z, 1f, fh, fd, b.x + b.w, y + fh, cz(b),
                        bi, color, MaterialIds.SCALES, PartAnim.WING_A);
                left.rz = -30f;
                CreaturePart right = part("aleta derecha", b.x - 1f, y, z, 1f, fh, fd, b.x, y + fh, cz(b),
                        bi, color, MaterialIds.SCALES, PartAnim.WING_B);
                right.rz = 30f;
                added.add(left);
                added.add(right);
            }
        }
    }

    /** Lo que todavia falta por subir a una pieza que ya tenia la criatura (las nuevas ya estan subidas). */
    private static float pendingLift(CreatureDefinition def, CreaturePart b) {
        return def.parts.contains(b) ? LIFT.get()[0] : 0f;
    }

    private static CreaturePart leg(String name, float x, float z, float w, float h, int parent, int color, PartAnim anim) {
        return part(name, x, 0f, z, w, h, w, x + w / 2f, h, z + w / 2f, parent, color, MaterialIds.SMOOTH, anim);
    }

    /**
     * Sube el modelo para que quepan las patas debajo del cuerpo: las piezas nuevas se suben ya y las que ya
     * tenia la criatura al final, cuando se sabe que se anade algo.
     */
    private static void raise(CreatureDefinition def, List<CreaturePart> added, float dy) {
        for (CreaturePart p : added) {
            p.translate(0f, dy, 0f);
        }
        LIFT.get()[0] += dy;
    }

    private static CreaturePart part(String name, float x, float y, float z, float w, float h, float d,
                                     float px, float py, float pz, int parent, int color, String material,
                                     PartAnim anim) {
        CreaturePart p = CreaturePart.box(name, x, y, z, w, h, d);
        p.pivotAt(px, py, pz);
        p.parent = parent;
        p.color = color & 0xFFFFFF;
        p.material = material;
        p.anim = anim;
        return p;
    }

    private static boolean horizontal(CreaturePart b) {
        return b.d > b.h;
    }

    private static float cx(CreaturePart b) {
        return b.x + b.w / 2f;
    }

    private static float cz(CreaturePart b) {
        return b.z + b.d / 2f;
    }

    // ---------------------------------------------------------------------------------------

    /** El cuerpo: la pieza llamada cuerpo/body/torso o, si no hay, la mas grande. -1 si no hay piezas. */
    public static int body(CreatureDefinition def) {
        int named = find(def, "cuerpo", "body", "torso");
        if (named >= 0) {
            return named;
        }
        int best = -1;
        float bestV = 0f;
        for (int i = 0; i < def.parts.size(); i++) {
            CreaturePart p = def.parts.get(i);
            float v = p.w * p.h * p.d;
            if (v > bestV && !isHead(p)) {
                bestV = v;
                best = i;
            }
        }
        return best;
    }

    public static int head(CreatureDefinition def) {
        return find(def, "cabeza", "head");
    }

    private static boolean isHead(CreaturePart p) {
        String n = p.name.toLowerCase(Locale.ROOT);
        return n.equals("cabeza") || n.equals("head");
    }

    private static int find(CreatureDefinition def, String... names) {
        for (String name : names) {
            for (int i = 0; i < def.parts.size(); i++) {
                if (def.parts.get(i).name.trim().toLowerCase(Locale.ROOT).equals(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** La pieza del otro lado (mismo nombre con izquierda/derecha cambiadas), o -1. */
    public static int partner(CreatureDefinition def, int index) {
        if (index < 0 || index >= def.parts.size()) {
            return -1;
        }
        String name = def.parts.get(index).name;
        String other = CreaturePart.mirrorName(name);
        if (other.equals(name) || other.endsWith("(espejo)")) {
            return -1;
        }
        for (int i = 0; i < def.parts.size(); i++) {
            if (i != index && def.parts.get(i).name.equals(other)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Escala la pieza y todas las que cuelgan de ella alrededor de su pivote (los valores se redondean a 0,25).
     * Las medidas quedan entre 0,25 y 256.
     */
    public static void scaleGroup(CreatureDefinition def, int root, float sx, float sy, float sz) {
        if (root < 0 || root >= def.parts.size()) {
            return;
        }
        CreaturePart r = def.parts.get(root);
        float ox = r.px;
        float oy = r.py;
        float oz = r.pz;
        for (int i = 0; i < def.parts.size(); i++) {
            if (i != root && !PoseSolver.isDescendant(def.parts, i, root)) {
                continue;
            }
            CreaturePart p = def.parts.get(i);
            float ow = p.w;
            float oh = p.h;
            float od = p.d;
            p.x = snap(ox + (p.x - ox) * sx);
            p.y = snap(oy + (p.y - oy) * sy);
            p.z = snap(oz + (p.z - oz) * sz);
            p.w = size(p.w * sx, ow);
            p.h = size(p.h * sy, oh);
            p.d = size(p.d * sz, od);
            p.px = snap(ox + (p.px - ox) * sx);
            p.py = snap(oy + (p.py - oy) * sy);
            p.pz = snap(oz + (p.pz - oz) * sz);
            p.onResized(ow, oh, od);
        }
    }

    /** Punto mas bajo del modelo en reposo. */
    public static float lowest(CreatureDefinition def) {
        return def.parts.isEmpty() ? 0f : def.bounds()[1];
    }

    /**
     * Vuelve a apoyar el modelo en el suelo: si antes estaba apoyado (lowestBefore = 0) o ahora se hunde por
     * debajo, se sube o se baja entero para que el punto mas bajo quede en 0.
     */
    public static void ground(CreatureDefinition def, float lowestBefore) {
        if (def.parts.isEmpty()) {
            return;
        }
        float now = lowest(def);
        boolean wasGrounded = Math.abs(lowestBefore) < 0.05f;
        if (!wasGrounded && now >= 0f) {
            return;
        }
        float dy = snap(-now);
        if (dy != 0f) {
            for (CreaturePart p : def.parts) {
                p.translate(0f, dy, 0f);
            }
        }
    }

    private static float snap(float v) {
        return Math.round(v * 4f) / 4f;
    }

    /** Las piezas planas (0) siguen planas; el resto, entre 0,25 y 256. */
    private static float size(float v, float old) {
        if (old <= 0f) {
            return 0f;
        }
        return Math.max(0.25f, Math.min(256f, snap(v)));
    }
}
