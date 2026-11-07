package com.creaturestudio.data;

/** Valores tipicos del proyectil para cada tipo de ataque a distancia. */
public final class RangedPresets {
    private RangedPresets() {
    }

    /** Pone el tipo de ataque y rellena las opciones del proyectil y sus sonidos. */
    public static void apply(CreatureDefinition d, RangedType type) {
        d.ranged = type;
        switch (type) {
            case ARROW -> set(d, "minecraft:arrow", 4f, 5, 11, true, false, ExplosionSize.NONE, HitEffect.NONE,
                    TrailType.NONE, false, true, false, true, ShotSound.BOW, ShotSound.ARROW_HIT, ShotSound.ARROW_HIT);
            case SNOWBALL -> set(d, "minecraft:snowball", 1f, 5, 10, true, false, ExplosionSize.NONE, HitEffect.SLOWNESS,
                    TrailType.NONE, false, false, false, false, ShotSound.SNOWBALL, ShotSound.NONE, ShotSound.NONE);
            case FIREBALL -> set(d, "minecraft:fire_charge", 5f, 5, 7, false, true, ExplosionSize.NONE, HitEffect.FIRE,
                    TrailType.SMOKE, true, false, false, false, ShotSound.BLAZE, ShotSound.NONE, ShotSound.FIZZ);
            case SPIT -> set(d, "minecraft:slime_ball", 1f, 3, 10, true, false, ExplosionSize.NONE, HitEffect.NONE,
                    TrailType.SPIT, false, false, false, false, ShotSound.SPIT, ShotSound.SLIME, ShotSound.SLIME);
            case TRIDENT -> set(d, "minecraft:trident", 8f, 6, 16, true, false, ExplosionSize.NONE, HitEffect.NONE,
                    TrailType.NONE, false, true, false, true, ShotSound.TRIDENT, ShotSound.TRIDENT_HIT,
                    ShotSound.TRIDENT_GROUND);
            case AXE -> set(d, "minecraft:iron_axe", 6f, 6, 9, true, false, ExplosionSize.NONE, HitEffect.NONE,
                    TrailType.NONE, false, true, true, true, ShotSound.TRIDENT, ShotSound.WOOD, ShotSound.WOOD);
            case BOMB -> set(d, "minecraft:tnt", 2f, 6, 7, true, false, ExplosionSize.SMALL, HitEffect.NONE,
                    TrailType.SMOKE, false, true, true, false, ShotSound.SNOWBALL, ShotSound.NONE, ShotSound.NONE);
            case MAGIC -> set(d, "minecraft:amethyst_shard", 5f, 5, 12, false, false, ExplosionSize.NONE,
                    HitEffect.WEAKNESS, TrailType.WITCH, true, false, false, false, ShotSound.SPELL,
                    ShotSound.AMETHYST, ShotSound.AMETHYST);
            default -> {
            }
        }
    }

    private static void set(CreatureDefinition d, String item, float strength, int size, int speed, boolean gravity,
                            boolean accelerate, ExplosionSize explode, HitEffect effect, TrailType trail,
                            boolean glows, boolean threeD, boolean spins, boolean sticks,
                            ShotSound fire, ShotSound hit, ShotSound ground) {
        d.projItem = item;
        d.projStrength = strength;
        d.projSize = size;
        d.projSpeed = speed;
        d.projGravity = gravity;
        d.projAccelerate = accelerate;
        d.projExplode = explode;
        d.projEffect = effect;
        d.projTrail = trail;
        d.projGlows = glows;
        d.projKnockback = 0f;
        d.proj3D = threeD;
        d.projSpins = spins;
        d.projSticks = sticks;
        d.fireSound = fire;
        d.hitSound = hit;
        d.groundSound = ground;
    }
}
