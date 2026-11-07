package com.creaturestudio.data;

import java.util.List;
import java.util.function.Supplier;

/** Modelos de partida para el editor. Frente = -Z, izquierda de la criatura = +X. */
public final class Templates {

    public record Template(String key, Supplier<CreatureDefinition> factory) {
        public CreatureDefinition create() {
            return factory.get();
        }
    }

    public static final List<Template> ALL = List.of(
            new Template("empty", Templates::empty),
            new Template("quadruped", Templates::quadruped),
            new Template("biped", Templates::biped),
            new Template("spider", Templates::spider),
            new Template("bird", Templates::bird),
            new Template("slime", Templates::slime),
            new Template("snake", Templates::snake),
            new Template("golem", Templates::golem),
            new Template("dragon", Templates::dragon)
    );

    private Templates() {
    }

    /** Pequeno ayudante para encadenar piezas y sus copias espejadas. */
    private static final class Builder {
        final CreatureDefinition def = new CreatureDefinition();

        int add(CreaturePart p) {
            def.parts.add(p);
            return def.parts.size() - 1;
        }

        /** Anade la pieza y su reflejo en X; devuelve el indice de la original. */
        int addMirrored(CreaturePart p) {
            int i = add(p);
            add(p.mirroredX());
            return i;
        }
    }

    public static CreatureDefinition empty() {
        Builder b = new Builder();
        b.add(CreaturePart.box("cuerpo", -4, 0, -4, 8, 8, 8).textured(MaterialIds.SMOOTH).colored(0xE6E6E6));
        b.def.sound = SoundPreset.NONE;
        return b.def;
    }

    public static CreatureDefinition quadruped() {
        Builder b = new Builder();
        int fur = 0x8B5A2B;
        int body = b.add(CreaturePart.box("cuerpo", -4, 8, -7, 8, 7, 14).pivotAt(0, 11.5f, 0)
                .textured(MaterialIds.FUR).colored(fur));
        int head = b.add(CreaturePart.box("cabeza", -3.5f, 10, -13, 7, 7, 7).pivotAt(0, 13, -7)
                .textured(MaterialIds.FUR).colored(fur).animated(PartAnim.HEAD).childOf(body));
        b.add(CreaturePart.box("hocico", -2, 10, -16, 4, 3, 3).textured(MaterialIds.SMOOTH)
                .colored(0xC89B6D).childOf(head));
        b.add(CreaturePart.box("nariz", -1, 12, -16.4f, 2, 1, 0.5f).colored(0x2B1D14).childOf(head));
        b.addMirrored(CreaturePart.box("ojo izquierdo", 1, 14, -13.4f, 2, 2, 0.5f)
                .textured(MaterialIds.EYE).stretched().childOf(head));
        b.addMirrored(CreaturePart.box("oreja izquierda", 1.5f, 17, -10, 2, 3, 1).pivotAt(2.5f, 17, -9.5f)
                .rotated(0, 0, -10).textured(MaterialIds.FUR).colored(0x6E4522).childOf(head));
        b.addMirrored(CreaturePart.box("pata delantera izquierda", 1, 0, -6, 3, 8, 3).pivotAt(2.5f, 8, -4.5f)
                .textured(MaterialIds.FUR).colored(fur).animated(PartAnim.LEG_A).childOf(body));
        b.addMirrored(CreaturePart.box("pata trasera izquierda", 1, 0, 3, 3, 8, 3).pivotAt(2.5f, 8, 4.5f)
                .textured(MaterialIds.FUR).colored(fur).animated(PartAnim.LEG_B).childOf(body));
        b.add(CreaturePart.box("cola", -1, 12, 7, 2, 2, 7).pivotAt(0, 13, 7).rotated(-25, 0, 0)
                .textured(MaterialIds.FUR).colored(0x6E4522).animated(PartAnim.TAIL).childOf(body));
        CreatureDefinition d = b.def;
        d.behavior = Behavior.NEUTRAL;
        d.sound = SoundPreset.WOLF;
        d.health = 20;
        d.damage = 4;
        d.speed = 0.3f;
        d.eggColor1 = fur;
        d.eggColor2 = 0xC89B6D;
        return d;
    }

    public static CreatureDefinition biped() {
        Builder b = new Builder();
        int skin = 0xD8A47F;
        int body = b.add(CreaturePart.box("cuerpo", -4, 12, -2, 8, 12, 4).pivotAt(0, 12, 0)
                .textured(MaterialIds.SMOOTH).colored(0x3A6EA5));
        int head = b.add(CreaturePart.box("cabeza", -4, 24, -4, 8, 8, 8).pivotAt(0, 24, 0)
                .textured(MaterialIds.SMOOTH).colored(skin).animated(PartAnim.HEAD).childOf(body));
        b.addMirrored(CreaturePart.box("ojo izquierdo", 1, 27, -4.5f, 2, 2, 0.5f)
                .textured(MaterialIds.EYE).stretched().childOf(head));
        b.add(CreaturePart.box("boca", -2, 25, -4.3f, 4, 1, 0.3f).colored(0x5A2E2E).childOf(head));
        b.add(CreaturePart.box("pelo", -4.25f, 30, -4.25f, 8.5f, 2.5f, 8.5f).textured(MaterialIds.FUR)
                .colored(0x4A2F1B).childOf(head));
        b.addMirrored(CreaturePart.box("brazo izquierdo", 4, 12, -2, 4, 12, 4).pivotAt(6, 22, 0)
                .textured(MaterialIds.SMOOTH).colored(skin).animated(PartAnim.ARM_A).childOf(body));
        b.addMirrored(CreaturePart.box("pierna izquierda", 0, 0, -2, 4, 12, 4).pivotAt(2, 12, 0)
                .textured(MaterialIds.SMOOTH).colored(0x2F3B52).animated(PartAnim.LEG_A).childOf(body));
        CreatureDefinition d = b.def;
        d.behavior = Behavior.NEUTRAL;
        d.sound = SoundPreset.VILLAGER;
        d.eggColor1 = 0x3A6EA5;
        d.eggColor2 = skin;
        return d;
    }

    public static CreatureDefinition spider() {
        Builder b = new Builder();
        int dark = 0x2E2424;
        int body = b.add(CreaturePart.box("cuerpo", -4, 5, -5, 8, 6, 8).pivotAt(0, 8, -1)
                .textured(MaterialIds.SMOOTH).colored(0x3B2F2F));
        b.add(CreaturePart.box("abdomen", -5, 4, 3, 10, 9, 11).pivotAt(0, 8, 3)
                .textured(MaterialIds.SPOTS).colored(0x5A463D).animated(PartAnim.BOB, 0.5f).childOf(body));
        int head = b.add(CreaturePart.box("cabeza", -3, 6, -9, 6, 5, 4).pivotAt(0, 8, -5)
                .textured(MaterialIds.SMOOTH).colored(dark).animated(PartAnim.HEAD).childOf(body));
        b.addMirrored(CreaturePart.box("ojo izquierdo", 0.5f, 8.5f, -9.3f, 1.5f, 1.5f, 0.4f)
                .colored(0xFF2B2B).glowing().childOf(head));
        b.addMirrored(CreaturePart.box("ojo pequeno izquierdo", 1.9f, 9.9f, -9.2f, 1, 1, 0.3f)
                .colored(0xFF2B2B).glowing().childOf(head));
        float[] spread = {30, 10, -10, -30};
        for (int k = 0; k < 4; k++) {
            float z = -4 + k * 2.5f;
            PartAnim anim = (k % 2 == 0) ? PartAnim.SIDE_LEG_A : PartAnim.SIDE_LEG_B;
            b.addMirrored(CreaturePart.box("pata " + (k + 1) + " izquierda", 4, 7, z, 14, 1.5f, 1.5f)
                    .pivotAt(4, 7.75f, z + 0.75f).rotated(0, spread[k], -30)
                    .textured(MaterialIds.SMOOTH).colored(dark).animated(anim).childOf(body));
        }
        CreatureDefinition d = b.def;
        d.behavior = Behavior.HOSTILE;
        d.sound = SoundPreset.SPIDER;
        d.hitEffect = HitEffect.POISON;
        d.health = 16;
        d.damage = 3;
        d.speed = 0.3f;
        d.eggColor1 = 0x3B2F2F;
        d.eggColor2 = 0xC4261D;
        return d;
    }

    public static CreatureDefinition bird() {
        Builder b = new Builder();
        int blue = 0x3D7FD1;
        int body = b.add(CreaturePart.box("cuerpo", -3, 5, -4, 6, 6, 9).pivotAt(0, 8, 0)
                .textured(MaterialIds.FEATHERS).colored(blue));
        b.add(CreaturePart.box("pecho", -2.5f, 4.8f, -3.5f, 5, 1, 7).textured(MaterialIds.BELLY)
                .colored(0xF2E3B3).childOf(body));
        int head = b.add(CreaturePart.box("cabeza", -2.5f, 9, -8, 5, 5, 5).pivotAt(0, 10, -4)
                .textured(MaterialIds.FEATHERS).colored(blue).animated(PartAnim.HEAD).childOf(body));
        b.add(CreaturePart.box("pico", -1, 10, -10, 2, 1.5f, 2).colored(0xF2A93B).childOf(head));
        b.addMirrored(CreaturePart.box("ojo izquierdo", 2.5f, 11.5f, -7.5f, 0.4f, 1.5f, 1.5f)
                .textured(MaterialIds.EYE_CUTE).stretched().childOf(head));
        b.addMirrored(CreaturePart.box("ala izquierda", 3, 9.5f, -3, 10, 1, 7).pivotAt(3, 10, 0.5f)
                .textured(MaterialIds.FEATHERS).colored(0x2F66B0).animated(PartAnim.WING_A).childOf(body));
        b.add(CreaturePart.box("cola", -2, 8, 5, 4, 1, 6).pivotAt(0, 8.5f, 5).rotated(15, 0, 0)
                .textured(MaterialIds.FEATHERS).colored(0x2F66B0).animated(PartAnim.TAIL, 0.5f).childOf(body));
        b.addMirrored(CreaturePart.box("pata izquierda", 1, 0, -0.5f, 1, 5, 1).pivotAt(1.5f, 5, 0)
                .colored(0xF2A93B).animated(PartAnim.LEG_A).childOf(body));
        CreatureDefinition d = b.def;
        d.movement = Movement.FLY;
        d.behavior = Behavior.PASSIVE;
        d.sound = SoundPreset.PARROT;
        d.health = 8;
        d.speed = 0.25f;
        d.eggColor1 = blue;
        d.eggColor2 = 0xF2A93B;
        return d;
    }

    public static CreatureDefinition slime() {
        Builder b = new Builder();
        int body = b.add(CreaturePart.box("cuerpo", -6, 0, -6, 12, 12, 12).pivotAt(0, 0, 0)
                .textured(MaterialIds.SMOOTH).colored(0x5FCB4A).animated(PartAnim.BOB, 2f));
        b.addMirrored(CreaturePart.box("ojo izquierdo", 1.5f, 6, -6.5f, 3, 3, 0.5f)
                .textured(MaterialIds.EYE_CUTE).stretched().childOf(body));
        b.add(CreaturePart.box("boca", -2, 3, -6.3f, 4, 1.5f, 0.3f).textured(MaterialIds.MOUTH)
                .stretched().childOf(body));
        CreatureDefinition d = b.def;
        d.behavior = Behavior.NEUTRAL;
        d.sound = SoundPreset.SLIME;
        d.health = 16;
        d.damage = 2;
        d.speed = 0.2f;
        d.eggColor1 = 0x5FCB4A;
        d.eggColor2 = 0x2E7D32;
        return d;
    }

    public static CreatureDefinition snake() {
        Builder b = new Builder();
        int green = 0x4E9A3A;
        int seg1 = b.add(CreaturePart.box("cuerpo 1", -2, 0, -6, 4, 3, 5).pivotAt(0, 1.5f, -1)
                .textured(MaterialIds.SCALES).colored(green).animated(PartAnim.SWAY, 2f));
        int head = b.add(CreaturePart.box("cabeza", -2.5f, 0, -11, 5, 3.5f, 5).pivotAt(0, 1.75f, -6)
                .textured(MaterialIds.SCALES).colored(green).animated(PartAnim.HEAD).childOf(seg1));
        b.addMirrored(CreaturePart.box("ojo izquierdo", 0.8f, 3.5f, -10.5f, 1.2f, 0.4f, 1.2f)
                .textured(MaterialIds.EYE_SLIT).stretched().colored(0xF2D33B).childOf(head));
        b.add(CreaturePart.box("lengua", -0.5f, 0.8f, -13, 1, 0.2f, 2).colored(0xD12E2E).childOf(head));
        int seg2 = b.add(CreaturePart.box("cuerpo 2", -1.8f, 0, -1, 3.6f, 2.8f, 5).pivotAt(0, 1.4f, -1)
                .textured(MaterialIds.SCALES).colored(green).animated(PartAnim.TAIL, 0.6f).childOf(seg1));
        int seg3 = b.add(CreaturePart.box("cuerpo 3", -1.6f, 0, 4, 3.2f, 2.6f, 5).pivotAt(0, 1.3f, 4)
                .textured(MaterialIds.SCALES).colored(green).animated(PartAnim.TAIL, 0.6f).childOf(seg2));
        int seg4 = b.add(CreaturePart.box("cuerpo 4", -1.3f, 0, 9, 2.6f, 2.2f, 5).pivotAt(0, 1.1f, 9)
                .textured(MaterialIds.SCALES).colored(green).animated(PartAnim.TAIL, 0.6f).childOf(seg3));
        b.add(CreaturePart.box("punta", -1, 0, 14, 2, 1.6f, 6).pivotAt(0, 0.8f, 14)
                .textured(MaterialIds.SCALES).colored(0x3C7A2C).animated(PartAnim.TAIL, 0.8f).childOf(seg4));
        CreatureDefinition d = b.def;
        d.behavior = Behavior.NEUTRAL;
        d.sound = SoundPreset.SPIDER;
        d.hitEffect = HitEffect.POISON;
        d.health = 12;
        d.damage = 3;
        d.speed = 0.28f;
        d.eggColor1 = green;
        d.eggColor2 = 0xF2D33B;
        return d;
    }

    public static CreatureDefinition golem() {
        Builder b = new Builder();
        int stone = 0x8A8A8A;
        int body = b.add(CreaturePart.box("cuerpo", -9, 14, -5, 18, 14, 10).pivotAt(0, 14, 0)
                .textured(MaterialIds.ROCK).colored(stone).animated(PartAnim.SWAY));
        b.add(CreaturePart.box("musgo", -9.3f, 20, -5.3f, 7, 5, 0.5f).textured("minecraft:block/moss_block")
                .childOf(body));
        int head = b.add(CreaturePart.box("cabeza", -4, 28, -7, 8, 8, 8).pivotAt(0, 28, -2)
                .textured(MaterialIds.ROCK).colored(stone).animated(PartAnim.HEAD).childOf(body));
        b.addMirrored(CreaturePart.box("ojo izquierdo", 1, 31, -7.4f, 2, 1.5f, 0.5f)
                .colored(0x4FE3FF).glowing().childOf(head));
        b.addMirrored(CreaturePart.box("brazo izquierdo", 9, 2, -3, 5, 24, 6).pivotAt(11.5f, 26, 0)
                .textured(MaterialIds.ROCK).colored(0x777777).animated(PartAnim.ARM_A).childOf(body));
        b.addMirrored(CreaturePart.box("pierna izquierda", 1.5f, 0, -3, 6, 14, 6).pivotAt(4.5f, 14, 0)
                .textured(MaterialIds.ROCK).colored(0x777777).animated(PartAnim.LEG_A).childOf(body));
        CreatureDefinition d = b.def;
        d.behavior = Behavior.NEUTRAL;
        d.sound = SoundPreset.IRON_GOLEM;
        d.health = 100;
        d.damage = 12;
        d.speed = 0.22f;
        d.knockbackResistance = 1f;
        d.armor = 6;
        d.eggColor1 = stone;
        d.eggColor2 = 0x4FE3FF;
        return d;
    }

    public static CreatureDefinition dragon() {
        Builder b = new Builder();
        int red = 0xB3261E;
        int dark = 0x7A1A14;
        int bone = 0xE8DCC0;
        int body = b.add(CreaturePart.box("cuerpo", -5, 10, -9, 10, 9, 18).pivotAt(0, 14, 0)
                .textured(MaterialIds.SCALES).colored(red));
        b.add(CreaturePart.box("vientre", -4, 9.6f, -8, 8, 1, 16).textured(MaterialIds.BELLY)
                .colored(0xE8B04B).childOf(body));
        b.add(CreaturePart.box("pinchos", -0.5f, 19, -6, 1, 2.5f, 12).colored(bone).childOf(body));
        int neck = b.add(CreaturePart.box("cuello", -2.5f, 15, -15, 5, 5, 7).pivotAt(0, 17, -9)
                .rotated(20, 0, 0).textured(MaterialIds.SCALES).colored(red).childOf(body));
        int head = b.add(CreaturePart.box("cabeza", -3.5f, 17, -22, 7, 6, 8).pivotAt(0, 19, -15)
                .rotated(-20, 0, 0).textured(MaterialIds.SCALES).colored(red).animated(PartAnim.HEAD)
                .childOf(neck));
        b.add(CreaturePart.box("hocico", -2.5f, 17, -27, 5, 4, 5).textured(MaterialIds.SCALES)
                .colored(red).childOf(head));
        b.add(CreaturePart.box("mandibula", -2.3f, 16, -26.5f, 4.6f, 1.2f, 5).pivotAt(0, 16.6f, -22)
                .colored(dark).animated(PartAnim.JAW).childOf(head));
        b.addMirrored(CreaturePart.box("ojo izquierdo", 3.5f, 20, -20.5f, 0.5f, 1.5f, 2)
                .textured(MaterialIds.EYE_SLIT).stretched().colored(0xFFD23F).glowing().childOf(head));
        b.addMirrored(CreaturePart.box("cuerno izquierdo", 1.5f, 23, -17, 1.5f, 4, 1.5f)
                .pivotAt(2.25f, 23, -16.25f).rotated(30, 0, 0).colored(bone).childOf(head));
        b.addMirrored(CreaturePart.box("ala izquierda", 5, 17, -6, 18, 1, 12).pivotAt(5, 17.5f, 0)
                .textured(MaterialIds.SMOOTH).colored(dark).animated(PartAnim.WING_A).childOf(body));
        b.addMirrored(CreaturePart.box("pata delantera izquierda", 2.5f, 0, -8, 3.5f, 10, 3.5f)
                .pivotAt(4.25f, 10, -6.25f).textured(MaterialIds.SCALES).colored(red)
                .animated(PartAnim.LEG_A).childOf(body));
        b.addMirrored(CreaturePart.box("pata trasera izquierda", 2.5f, 0, 4, 4, 10, 4)
                .pivotAt(4.5f, 10, 6).textured(MaterialIds.SCALES).colored(red)
                .animated(PartAnim.LEG_B).childOf(body));
        int tail1 = b.add(CreaturePart.box("cola 1", -2, 12, 9, 4, 4, 9).pivotAt(0, 14, 9)
                .textured(MaterialIds.SCALES).colored(red).animated(PartAnim.TAIL, 0.6f).childOf(body));
        int tail2 = b.add(CreaturePart.box("cola 2", -1.5f, 12.5f, 18, 3, 3, 8).pivotAt(0, 14, 18)
                .textured(MaterialIds.SCALES).colored(red).animated(PartAnim.TAIL, 0.6f).childOf(tail1));
        b.add(CreaturePart.box("punta de la cola", -1, 13, 26, 2, 2, 6).pivotAt(0, 14, 26)
                .textured(MaterialIds.SCALES).colored(dark).animated(PartAnim.TAIL, 0.8f).childOf(tail2));
        CreatureDefinition d = b.def;
        d.movement = Movement.FLY;
        d.behavior = Behavior.NEUTRAL;
        RangedPresets.apply(d, RangedType.FIREBALL);
        d.projStrength = 8f;
        d.rangedRange = 20f;
        d.rangedMeleeRange = 4f;
        d.sound = SoundPreset.DRAGON;
        d.fireImmune = true;
        d.health = 120;
        d.damage = 10;
        d.speed = 0.3f;
        d.armor = 8;
        d.scale = 1.5f;
        d.xp = 50;
        d.eggColor1 = red;
        d.eggColor2 = 0xFFD23F;
        return d;
    }
}
