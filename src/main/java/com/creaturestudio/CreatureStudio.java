package com.creaturestudio;

import com.creaturestudio.client.ClientSetup;
import com.creaturestudio.entity.CustomCreature;
import com.creaturestudio.network.ModNetwork;
import com.creaturestudio.registry.ModRegistry;
import com.creaturestudio.server.ServerEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

@Mod(CreatureStudio.MODID)
public class CreatureStudio {
    public static final String MODID = "creaturestudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreatureStudio(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        modBus.addListener(CreatureStudio::onAttributes);
        modBus.addListener(CreatureStudio::onCreativeTabs);
        modBus.addListener(ModNetwork::register);
        ServerEvents.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModRegistry.CUSTOM_CREATURE.get(), CustomCreature.createAttributes().build());
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistry.STUDIO_ITEM.get());
        }
    }
}
