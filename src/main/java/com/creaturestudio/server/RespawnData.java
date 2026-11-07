package com.creaturestudio.server;

import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.data.SpawnTime;
import com.creaturestudio.entity.CustomCreature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Criaturas muertas que tienen que volver: se guardan en el mundo (una lista por dimension) para que no se pierdan
 * al cerrar el juego. Cada segundo se mira si ya les toca (tiempo, dia o noche y zona cargada).
 */
public final class RespawnData extends SavedData {
    private static final String NAME = "creaturestudio_respawns";
    private static final int MAX_ENTRIES = 4096;

    /** Una criatura esperando a volver. */
    public record Entry(String creature, double x, double y, double z, float yaw, long due, int used,
                        boolean persistent, double homeX, double homeY, double homeZ) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public RespawnData() {
    }

    private static final SavedData.Factory<RespawnData> FACTORY =
            new SavedData.Factory<>(RespawnData::new, RespawnData::load, null);

    public static RespawnData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public void add(Entry entry) {
        if (this.entries.size() < MAX_ENTRIES) {
            this.entries.add(entry);
            this.setDirty();
        }
    }

    /** Cada segundo, en todas las dimensiones. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || !CreatureStore.SERVER.isLoaded()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            RespawnData data = get(level);
            if (!data.entries.isEmpty()) {
                data.process(level);
            }
        }
    }

    private void process(ServerLevel level) {
        long now = level.getGameTime();
        boolean changed = false;
        Iterator<Entry> it = this.entries.iterator();
        List<Entry> ready = new ArrayList<>();
        while (it.hasNext()) {
            Entry e = it.next();
            CreatureDefinition d = CreatureStore.SERVER.get(e.creature());
            if (d == null || !d.respawn) {
                it.remove(); // la criatura se borro o ya no reaparece
                changed = true;
                continue;
            }
            if (now < e.due() || !rightTime(level, d.respawnTime)
                    || !level.isPositionEntityTicking(BlockPos.containing(e.x(), e.y(), e.z()))) {
                continue;
            }
            it.remove();
            changed = true;
            ready.add(e);
        }
        for (Entry e : ready) {
            CreatureDefinition d = CreatureStore.SERVER.get(e.creature());
            if (d == null) {
                continue;
            }
            CustomCreature c = CustomCreature.spawn(level, d, e.x(), e.y(), e.z(), e.yaw(), e.persistent());
            if (c != null) {
                c.setRespawnInfo(e.used(), e.homeX(), e.homeY(), e.homeZ());
                level.sendParticles(ParticleTypes.POOF, e.x(), e.y() + c.getBbHeight() / 2, e.z(), 16,
                        c.getBbWidth() / 2, c.getBbHeight() / 3, c.getBbWidth() / 2, 0.02);
            }
        }
        if (changed) {
            this.setDirty();
        }
    }

    private static boolean rightTime(ServerLevel level, SpawnTime time) {
        if (time == SpawnTime.ANY || level.dimensionType().hasFixedTime()) {
            return true; // en el Nether o el End no hay dia ni noche
        }
        return time == SpawnTime.DAY ? level.isDay() : level.isNight();
    }

    // ---------------------------------------------------------------------------------------
    // Guardado
    // ---------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : this.entries) {
            CompoundTag t = new CompoundTag();
            t.putString("Creature", e.creature());
            t.putDouble("X", e.x());
            t.putDouble("Y", e.y());
            t.putDouble("Z", e.z());
            t.putFloat("Yaw", e.yaw());
            t.putLong("Due", e.due());
            t.putInt("Used", e.used());
            t.putBoolean("Persistent", e.persistent());
            t.putDouble("HomeX", e.homeX());
            t.putDouble("HomeY", e.homeY());
            t.putDouble("HomeZ", e.homeZ());
            list.add(t);
        }
        tag.put("Entries", list);
        return tag;
    }

    private static RespawnData load(CompoundTag tag, HolderLookup.Provider registries) {
        RespawnData data = new RespawnData();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && data.entries.size() < MAX_ENTRIES; i++) {
            CompoundTag t = list.getCompound(i);
            data.entries.add(new Entry(t.getString("Creature"), t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"),
                    t.getFloat("Yaw"), t.getLong("Due"), t.getInt("Used"), t.getBoolean("Persistent"),
                    t.getDouble("HomeX"), t.getDouble("HomeY"), t.getDouble("HomeZ")));
        }
        return data;
    }
}
