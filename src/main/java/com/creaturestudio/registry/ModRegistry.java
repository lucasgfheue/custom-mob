package com.creaturestudio.registry;

import com.creaturestudio.CreatureStudio;
import com.creaturestudio.block.CreatureStudioBlock;
import com.creaturestudio.entity.CreatureProjectile;
import com.creaturestudio.entity.CustomCreature;
import com.creaturestudio.item.CreatureEggItem;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatureStudio.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatureStudio.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CreatureStudio.MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreatureStudio.MODID);

    /** Identificador de la criatura guardado dentro del huevo. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> CREATURE_ID =
            COMPONENTS.register("creature_id", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    public static final DeferredBlock<CreatureStudioBlock> STUDIO_BLOCK = BLOCKS.register("creature_studio",
            () -> new CreatureStudioBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f)
                    .sound(SoundType.WOOD)));

    public static final DeferredItem<BlockItem> STUDIO_ITEM = ITEMS.register("creature_studio",
            () -> new BlockItem(STUDIO_BLOCK.get(), new Item.Properties()));

    public static final DeferredItem<CreatureEggItem> CREATURE_EGG = ITEMS.register("creature_egg",
            () -> new CreatureEggItem(new Item.Properties()));

    public static final DeferredHolder<EntityType<?>, EntityType<CustomCreature>> CUSTOM_CREATURE =
            ENTITIES.register("custom_creature", () -> EntityType.Builder.<CustomCreature>of(CustomCreature::new, MobCategory.CREATURE)
                    .sized(0.8f, 1.0f)
                    .clientTrackingRange(10)
                    .build("custom_creature"));

    /** Proyectil configurable que disparan las criaturas. */
    public static final DeferredHolder<EntityType<?>, EntityType<CreatureProjectile>> PROJECTILE =
            ENTITIES.register("projectile", () -> EntityType.Builder.<CreatureProjectile>of(CreatureProjectile::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("projectile"));

    private ModRegistry() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
    }
}
