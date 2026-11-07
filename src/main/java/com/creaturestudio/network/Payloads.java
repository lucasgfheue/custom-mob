package com.creaturestudio.network;

import com.creaturestudio.CreatureStudio;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Todos los paquetes del mod. C2S = cliente a servidor, S2C = servidor a cliente. */
public final class Payloads {

    private Payloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreatureStudio.MODID, path));
    }

    /** Guardar (crear o actualizar) una criatura. Contiene el JSON comprimido. */
    public record SaveCreature(byte[] data) implements CustomPacketPayload {
        public static final Type<SaveCreature> TYPE = typeOf("save_creature");
        public static final StreamCodec<ByteBuf, SaveCreature> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BYTE_ARRAY, SaveCreature::data, SaveCreature::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DeleteCreature(String id) implements CustomPacketPayload {
        public static final Type<DeleteCreature> TYPE = typeOf("delete_creature");
        public static final StreamCodec<ByteBuf, DeleteCreature> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, DeleteCreature::id, DeleteCreature::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record GiveEgg(String id) implements CustomPacketPayload {
        public static final Type<GiveEgg> TYPE = typeOf("give_egg");
        public static final StreamCodec<ByteBuf, GiveEgg> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, GiveEgg::id, GiveEgg::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Summon(String id) implements CustomPacketPayload {
        public static final Type<Summon> TYPE = typeOf("summon");
        public static final StreamCodec<ByteBuf, Summon> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Summon::id, Summon::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Una definicion completa enviada a los clientes. */
    public record SyncCreature(byte[] data) implements CustomPacketPayload {
        public static final Type<SyncCreature> TYPE = typeOf("sync_creature");
        public static final StreamCodec<ByteBuf, SyncCreature> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BYTE_ARRAY, SyncCreature::data, SyncCreature::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RemoveCreature(String id) implements CustomPacketPayload {
        public static final Type<RemoveCreature> TYPE = typeOf("remove_creature");
        public static final StreamCodec<ByteBuf, RemoveCreature> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RemoveCreature::id, RemoveCreature::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Borra la cache del cliente antes de una sincronizacion completa. */
    public record ClearCreatures(boolean full) implements CustomPacketPayload {
        public static final Type<ClearCreatures> TYPE = typeOf("clear_creatures");
        public static final StreamCodec<ByteBuf, ClearCreatures> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, ClearCreatures::full, ClearCreatures::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Mensaje para mostrar en la pantalla abierta (o en el chat). */
    public record Notice(String key, String arg, boolean success) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = typeOf("notice");
        public static final StreamCodec<ByteBuf, Notice> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Notice::key,
                ByteBufCodecs.STRING_UTF8, Notice::arg,
                ByteBufCodecs.BOOL, Notice::success,
                Notice::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
