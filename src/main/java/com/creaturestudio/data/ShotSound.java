package com.creaturestudio.data;

import java.util.Locale;

/** Sonidos para el ataque a distancia: al disparar, al dar a alguien y al chocar con el suelo. */
public enum ShotSound {
    NONE(""),
    BOW("entity.skeleton.shoot"),
    CROSSBOW("item.crossbow.shoot"),
    SNOWBALL("entity.snowball.throw"),
    EGG("entity.egg.throw"),
    TRIDENT("item.trident.throw"),
    BLAZE("entity.blaze.shoot"),
    GHAST("entity.ghast.shoot"),
    FIRE_CHARGE("item.firecharge.use"),
    SPIT("entity.llama.spit"),
    SHULKER("entity.shulker.shoot"),
    WITHER("entity.wither.shoot"),
    DRAGON("entity.ender_dragon.shoot"),
    SPELL("entity.evoker.cast_spell"),
    BREEZE("entity.breeze.shoot"),
    WIND("entity.wind_charge.throw"),
    FIREWORK("entity.firework_rocket.launch"),
    ENDER_PEARL("entity.ender_pearl.throw"),
    ARROW_HIT("entity.arrow.hit"),
    ARROW_HIT_PLAYER("entity.arrow.hit_player"),
    TRIDENT_HIT("item.trident.hit"),
    TRIDENT_GROUND("item.trident.hit_ground"),
    GLASS("block.glass.break"),
    STONE("block.stone.hit"),
    WOOD("block.wood.hit"),
    SLIME("entity.slime.squish"),
    EXPLOSION("entity.generic.explode"),
    FIZZ("block.fire.extinguish"),
    AMETHYST("block.amethyst_block.hit"),
    BELL("block.bell.use"),
    ANVIL("block.anvil.land"),
    ORB("entity.experience_orb.pickup"),
    HURT("entity.player.hurt"),
    SPLASH("entity.generic.splash"),
    THUNDER("entity.lightning_bolt.impact"),
    TELEPORT("entity.enderman.teleport"),
    WIND_BURST("entity.wind_charge.wind_burst");

    /** Identificador del sonido de Minecraft (sin "minecraft:"), vacio = ninguno. */
    public final String id;

    ShotSound(String id) {
        this.id = id;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
