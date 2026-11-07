package com.creaturestudio.client.render;

import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.MaterialIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Texturas disponibles para las piezas. Todas salen del atlas de bloques. */
public final class Materials {

    public record Preset(String id, String key) {
    }

    public static final List<Preset> PRESETS = List.of(
            new Preset(MaterialIds.SOLID, "solid"),
            new Preset(MaterialIds.SMOOTH, "smooth"),
            new Preset(MaterialIds.FUR, "fur"),
            new Preset(MaterialIds.SCALES, "scales"),
            new Preset(MaterialIds.SPOTS, "spots"),
            new Preset(MaterialIds.STRIPES, "stripes"),
            new Preset(MaterialIds.FEATHERS, "feathers"),
            new Preset(MaterialIds.ROCK, "rock"),
            new Preset(MaterialIds.PLATES, "plates"),
            new Preset(MaterialIds.BELLY, "belly"),
            new Preset(MaterialIds.EYE, "eye"),
            new Preset(MaterialIds.EYE_SLIT, "eye_slit"),
            new Preset(MaterialIds.EYE_CUTE, "eye_cute"),
            new Preset(MaterialIds.MOUTH, "mouth"),
            new Preset(MaterialIds.TEETH, "teeth"),
            new Preset(MaterialIds.CLEAR, "clear"),
            new Preset("minecraft:block/white_wool", "wool"),
            new Preset("minecraft:block/white_concrete", "concrete"),
            new Preset("minecraft:block/white_concrete_powder", "concrete_powder"),
            new Preset("minecraft:block/white_terracotta", "terracotta"),
            new Preset("minecraft:block/stone", "stone"),
            new Preset("minecraft:block/cobblestone", "cobblestone"),
            new Preset("minecraft:block/deepslate", "deepslate"),
            new Preset("minecraft:block/oak_leaves", "leaves"),
            new Preset("minecraft:block/grass_block_top", "grass"),
            new Preset("minecraft:block/moss_block", "moss"),
            new Preset("minecraft:block/oak_planks", "planks"),
            new Preset("minecraft:block/oak_log", "bark"),
            new Preset("minecraft:block/sand", "sand"),
            new Preset("minecraft:block/snow", "snow"),
            new Preset("minecraft:block/ice", "ice"),
            new Preset("minecraft:block/bone_block_side", "bone"),
            new Preset("minecraft:block/red_mushroom_block", "mushroom"),
            new Preset("minecraft:block/prismarine", "prismarine"),
            new Preset("minecraft:block/netherrack", "netherrack"),
            new Preset("minecraft:block/magma", "magma"),
            new Preset("minecraft:block/obsidian", "obsidian"),
            new Preset("minecraft:block/crying_obsidian", "crying_obsidian"),
            new Preset("minecraft:block/end_stone", "end_stone"),
            new Preset("minecraft:block/purpur_block", "purpur"),
            new Preset("minecraft:block/gold_block", "gold"),
            new Preset("minecraft:block/iron_block", "iron"),
            new Preset("minecraft:block/diamond_block", "diamond"),
            new Preset("minecraft:block/emerald_block", "emerald"),
            new Preset("minecraft:block/copper_block", "copper"),
            new Preset("minecraft:block/amethyst_block", "amethyst"),
            new Preset("minecraft:block/slime_block", "slime"),
            new Preset("minecraft:block/honey_block_side", "honey"),
            new Preset("minecraft:block/sponge", "sponge"),
            new Preset("minecraft:block/glowstone", "glowstone"),
            new Preset("minecraft:block/sea_lantern", "sea_lantern"),
            new Preset("minecraft:block/shroomlight", "shroomlight"),
            new Preset("minecraft:block/sculk", "sculk"),
            new Preset("minecraft:block/pumpkin_side", "pumpkin"),
            new Preset("minecraft:block/melon_side", "melon"),
            new Preset("minecraft:block/hay_block_side", "hay"),
            new Preset("minecraft:block/bricks", "bricks"),
            new Preset("minecraft:block/mud", "mud"),
            new Preset("minecraft:block/water_still", "water"),
            new Preset("minecraft:block/lava_still", "lava"),
            new Preset("minecraft:block/nether_portal", "portal"),
            new Preset("minecraft:block/glass", "glass")
    );

    private static final ResourceLocation FALLBACK = ResourceLocation.parse(MaterialIds.SOLID);
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();

    private Materials() {
    }

    public static ResourceLocation location(String material) {
        String key = material == null ? "" : material;
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (CACHE.size() > 2048) {
            CACHE.clear();
        }
        ResourceLocation rl = ResourceLocation.tryParse(CreatureDefinition.cleanMaterial(key));
        if (rl == null) {
            rl = FALLBACK;
        }
        CACHE.put(key, rl);
        return rl;
    }

    public static Function<ResourceLocation, TextureAtlasSprite> atlas() {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
    }

    public static TextureAtlasSprite sprite(Function<ResourceLocation, TextureAtlasSprite> atlas, String material) {
        return atlas.apply(location(material));
    }

    public static TextureAtlasSprite sprite(String material) {
        return sprite(atlas(), material);
    }

    /** true si la textura existe en el atlas (si no, Minecraft usaria la textura de "falta textura"). */
    public static boolean exists(String material) {
        return !sprite(material).contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    public static Component displayName(String material) {
        String normalized = CreatureDefinition.cleanMaterial(material);
        for (Preset p : PRESETS) {
            if (p.id().equals(normalized)) {
                return Component.translatable("material.creaturestudio." + p.key());
            }
        }
        return Component.literal(normalized);
    }
}
