package com.creaturestudio.data;

import java.util.Locale;

/**
 * Conjuntos de sonidos de criaturas vanilla que se pueden reutilizar.
 * Se guardan como identificadores de texto para que la clase sea segura en ambos lados.
 */
public enum SoundPreset {
    NONE("", "", ""),
    COW("entity.cow.ambient", "entity.cow.hurt", "entity.cow.death"),
    PIG("entity.pig.ambient", "entity.pig.hurt", "entity.pig.death"),
    SHEEP("entity.sheep.ambient", "entity.sheep.hurt", "entity.sheep.death"),
    CHICKEN("entity.chicken.ambient", "entity.chicken.hurt", "entity.chicken.death"),
    WOLF("entity.wolf.ambient", "entity.wolf.hurt", "entity.wolf.death"),
    CAT("entity.cat.ambient", "entity.cat.hurt", "entity.cat.death"),
    FOX("entity.fox.ambient", "entity.fox.hurt", "entity.fox.death"),
    HORSE("entity.horse.ambient", "entity.horse.hurt", "entity.horse.death"),
    FROG("entity.frog.ambient", "entity.frog.hurt", "entity.frog.death"),
    PARROT("entity.parrot.ambient", "entity.parrot.hurt", "entity.parrot.death"),
    BAT("entity.bat.ambient", "entity.bat.hurt", "entity.bat.death"),
    POLAR_BEAR("entity.polar_bear.ambient", "entity.polar_bear.hurt", "entity.polar_bear.death"),
    VILLAGER("entity.villager.ambient", "entity.villager.hurt", "entity.villager.death"),
    IRON_GOLEM("", "entity.iron_golem.hurt", "entity.iron_golem.death"),
    ZOMBIE("entity.zombie.ambient", "entity.zombie.hurt", "entity.zombie.death"),
    SKELETON("entity.skeleton.ambient", "entity.skeleton.hurt", "entity.skeleton.death"),
    SPIDER("entity.spider.ambient", "entity.spider.hurt", "entity.spider.death"),
    CREEPER("", "entity.creeper.hurt", "entity.creeper.death"),
    ENDERMAN("entity.enderman.ambient", "entity.enderman.hurt", "entity.enderman.death"),
    SLIME("entity.slime.squish", "entity.slime.hurt", "entity.slime.death"),
    BLAZE("entity.blaze.ambient", "entity.blaze.hurt", "entity.blaze.death"),
    GHAST("entity.ghast.ambient", "entity.ghast.hurt", "entity.ghast.death"),
    PHANTOM("entity.phantom.ambient", "entity.phantom.hurt", "entity.phantom.death"),
    RAVAGER("entity.ravager.ambient", "entity.ravager.hurt", "entity.ravager.death"),
    WARDEN("entity.warden.ambient", "entity.warden.hurt", "entity.warden.death"),
    DRAGON("entity.ender_dragon.growl", "entity.ender_dragon.hurt", "entity.ender_dragon.death");

    public final String ambient;
    public final String hurt;
    public final String death;

    SoundPreset(String ambient, String hurt, String death) {
        this.ambient = ambient;
        this.hurt = hurt;
        this.death = death;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
