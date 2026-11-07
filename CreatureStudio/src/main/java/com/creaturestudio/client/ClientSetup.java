package com.creaturestudio.client;

import com.creaturestudio.client.render.CustomCreatureRenderer;
import com.creaturestudio.client.render.PaintCache;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.item.CreatureEggItem;
import com.creaturestudio.registry.ModRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Registro de todo lo que solo existe en el cliente. */
public final class ClientSetup {
    /** Se crea al registrarla (como recomienda NeoForge), no al cargar la clase. */
    @Nullable
    private static KeyMapping openStudio;

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::registerRenderers);
        modBus.addListener(ClientSetup::registerKeys);
        modBus.addListener(ClientSetup::registerItemColors);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientSetup::onLoggingOut);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.CUSTOM_CREATURE.get(), CustomCreatureRenderer::new);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        openStudio = new KeyMapping("key.creaturestudio.open",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.creaturestudio");
        event.register(openStudio);
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(ClientSetup::eggColor, ModRegistry.CREATURE_EGG.get());
    }

    private static int eggColor(ItemStack stack, int tintIndex) {
        CreatureDefinition d = CreatureStore.CLIENT.get(CreatureEggItem.getCreatureId(stack));
        int rgb;
        if (d == null) {
            rgb = tintIndex == 0 ? 0x8A8A8A : 0x5A5A5A;
        } else {
            rgb = tintIndex == 0 ? d.eggColor1 : d.eggColor2;
        }
        return 0xFF000000 | rgb;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        KeyMapping key = openStudio;
        if (key == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (key.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                ClientAccess.openLibrary();
            }
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CreatureStore.CLIENT.clear();
        PaintCache.clear();
        VanillaModels.clearCache();
    }
}
