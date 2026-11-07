package com.creaturestudio.network;

import com.creaturestudio.CreatureStudio;
import com.creaturestudio.client.ClientAccess;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.data.DefCodec;
import com.creaturestudio.server.CreatureService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Registro de paquetes y manejadores. Los manejadores "ToClient" solo se ejecutan en el cliente,
 * por eso pueden llamar a {@link ClientAccess} sin romper los servidores dedicados.
 */
public final class ModNetwork {
    /** Los paquetes hacia el servidor deben pesar menos de 32 KiB: se envian trozos de este tamano. */
    public static final int MAX_UPLOAD_BYTES = 30000;
    /** Como mucho 64 trozos por criatura (unos 1,9 MB comprimidos). */
    public static final int MAX_CHUNKS = 64;
    public static final int MAX_TOTAL_UPLOAD = MAX_UPLOAD_BYTES * MAX_CHUNKS;

    /** Envios a medio recibir, uno por jugador (los manejadores corren en el hilo del servidor). */
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

    private static final class Upload {
        final int transfer;
        final byte[][] chunks;
        int received;

        Upload(int transfer, int total) {
            this.transfer = transfer;
            this.chunks = new byte[total][];
        }
    }

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(Payloads.SaveCreature.TYPE, Payloads.SaveCreature.STREAM_CODEC, ModNetwork::onSave);
        registrar.playToServer(Payloads.DeleteCreature.TYPE, Payloads.DeleteCreature.STREAM_CODEC, ModNetwork::onDelete);
        registrar.playToServer(Payloads.GiveEgg.TYPE, Payloads.GiveEgg.STREAM_CODEC, ModNetwork::onGiveEgg);
        registrar.playToServer(Payloads.Summon.TYPE, Payloads.Summon.STREAM_CODEC, ModNetwork::onSummon);

        registrar.playToClient(Payloads.SyncCreature.TYPE, Payloads.SyncCreature.STREAM_CODEC, ModNetwork::onSync);
        registrar.playToClient(Payloads.RemoveCreature.TYPE, Payloads.RemoveCreature.STREAM_CODEC, ModNetwork::onRemove);
        registrar.playToClient(Payloads.ClearCreatures.TYPE, Payloads.ClearCreatures.STREAM_CODEC, ModNetwork::onClear);
        registrar.playToClient(Payloads.Notice.TYPE, Payloads.Notice.STREAM_CODEC, ModNetwork::onNotice);
    }

    // ---------------------------------------------------------------------------------------
    // Servidor
    // ---------------------------------------------------------------------------------------

    private static void onSave(Payloads.SaveCreature payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        int total = payload.total();
        int index = payload.index();
        if (payload.data().length > 32000 || total < 1 || total > MAX_CHUNKS || index < 0 || index >= total) {
            UPLOADS.remove(player.getUUID());
            notice(player, "message.creaturestudio.too_big", "", false);
            return;
        }
        Upload upload = UPLOADS.get(player.getUUID());
        if (upload == null || upload.transfer != payload.transfer() || upload.chunks.length != total) {
            upload = new Upload(payload.transfer(), total);
            UPLOADS.put(player.getUUID(), upload);
        }
        if (upload.chunks[index] == null) {
            upload.chunks[index] = payload.data();
            upload.received++;
        }
        if (upload.received < total) {
            return;
        }
        UPLOADS.remove(player.getUUID());
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] chunk : upload.chunks) {
            joined.writeBytes(chunk);
        }
        CreatureDefinition def;
        try {
            def = DefCodec.decode(joined.toByteArray());
        } catch (IOException e) {
            CreatureStudio.LOGGER.warn("Definicion invalida recibida de {}: {}", player.getName().getString(), e.getMessage());
            notice(player, "message.creaturestudio.invalid", "", false);
            return;
        }
        CreatureService.save(player, def);
    }

    private static void onDelete(Payloads.DeleteCreature payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CreatureService.delete(player, payload.id());
        }
    }

    private static void onGiveEgg(Payloads.GiveEgg payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CreatureService.giveEgg(player, payload.id());
        }
    }

    private static void onSummon(Payloads.Summon payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CreatureService.summon(player, payload.id());
        }
    }

    // ---------------------------------------------------------------------------------------
    // Cliente
    // ---------------------------------------------------------------------------------------

    private static void onSync(Payloads.SyncCreature payload, IPayloadContext context) {
        try {
            CreatureDefinition def = DefCodec.decode(payload.data());
            if (CreatureDefinition.isValidId(def.id)) {
                CreatureStore.CLIENT.put(def);
            }
        } catch (IOException e) {
            CreatureStudio.LOGGER.warn("No se pudo leer una criatura enviada por el servidor: {}", e.getMessage());
        }
        ClientAccess.onCreaturesChanged();
    }

    private static void onRemove(Payloads.RemoveCreature payload, IPayloadContext context) {
        CreatureStore.CLIENT.remove(payload.id());
        ClientAccess.onCreaturesChanged();
    }

    private static void onClear(Payloads.ClearCreatures payload, IPayloadContext context) {
        CreatureStore.CLIENT.clear();
        CreatureStore.CLIENT.setLoaded(true);
        ClientAccess.onCreaturesChanged();
    }

    private static void onNotice(Payloads.Notice payload, IPayloadContext context) {
        ClientAccess.notice(payload.key(), payload.arg(), payload.success());
    }

    // ---------------------------------------------------------------------------------------
    // Envio
    // ---------------------------------------------------------------------------------------

    /** Envia una criatura al servidor (desde el cliente), partida en trozos si hace falta. */
    public static void sendSave(byte[] data) {
        int total = Math.max(1, (data.length + MAX_UPLOAD_BYTES - 1) / MAX_UPLOAD_BYTES);
        int transfer = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        for (int i = 0; i < total; i++) {
            int from = i * MAX_UPLOAD_BYTES;
            int to = Math.min(data.length, from + MAX_UPLOAD_BYTES);
            byte[] chunk = new byte[to - from];
            System.arraycopy(data, from, chunk, 0, chunk.length);
            PacketDistributor.sendToServer(new Payloads.SaveCreature(transfer, i, total, chunk));
        }
    }

    public static void notice(ServerPlayer player, String key, String arg, boolean success) {
        PacketDistributor.sendToPlayer(player, new Payloads.Notice(key, arg == null ? "" : arg, success));
    }

    public static void syncAll(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Payloads.ClearCreatures(true));
        for (CreatureDefinition def : CreatureStore.SERVER.all()) {
            PacketDistributor.sendToPlayer(player, new Payloads.SyncCreature(DefCodec.encode(def)));
        }
    }

    public static void broadcast(CreatureDefinition def) {
        PacketDistributor.sendToAllPlayers(new Payloads.SyncCreature(DefCodec.encode(def)));
    }

    public static void broadcastRemoval(String id) {
        PacketDistributor.sendToAllPlayers(new Payloads.RemoveCreature(id));
    }
}
