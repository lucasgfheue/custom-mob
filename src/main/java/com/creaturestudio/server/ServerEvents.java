package com.creaturestudio.server;

import com.creaturestudio.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Eventos del lado del servidor (bus de juego). */
public final class ServerEvents {

    private ServerEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerTick);
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        CreatureFiles.load(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        CreatureFiles.unload();
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModNetwork.syncAll(player);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        NaturalSpawner.tick(event.getServer());
        RespawnData.tick(event.getServer());
    }
}
