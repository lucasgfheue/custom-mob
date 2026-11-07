package com.creaturestudio.server;

import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.entity.CustomCreature;
import com.creaturestudio.item.CreatureEggItem;
import com.creaturestudio.network.ModNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Logica del servidor para las acciones que piden los jugadores desde el editor. */
public final class CreatureService {
    public static final int MAX_CREATURES = 500;

    private CreatureService() {
    }

    public static boolean canEdit(ServerPlayer player, CreatureDefinition existing) {
        if (player.hasPermissions(2)) {
            return true;
        }
        MinecraftServer server = player.getServer();
        if (server != null && server.isSingleplayerOwner(player.getGameProfile())) {
            return true;
        }
        return !existing.owner.isEmpty() && existing.owner.equals(player.getUUID().toString());
    }

    public static void save(ServerPlayer player, CreatureDefinition incoming) {
        incoming.sanitize();
        if (incoming.id.isEmpty()) {
            incoming.id = CreatureDefinition.newId();
        }
        CreatureDefinition existing = CreatureStore.SERVER.get(incoming.id);
        if (existing != null) {
            if (!canEdit(player, existing)) {
                ModNetwork.notice(player, "message.creaturestudio.no_permission", existing.name, false);
                return;
            }
            incoming.owner = existing.owner;
            incoming.revision = existing.revision + 1;
        } else {
            if (CreatureStore.SERVER.size() >= MAX_CREATURES) {
                ModNetwork.notice(player, "message.creaturestudio.too_many", String.valueOf(MAX_CREATURES), false);
                return;
            }
            incoming.owner = player.getUUID().toString();
            incoming.revision = 1;
        }
        CreatureStore.SERVER.put(incoming);
        CreatureFiles.save(incoming);
        ModNetwork.broadcast(incoming);
        ModNetwork.notice(player, "message.creaturestudio.saved", incoming.name, true);
    }

    public static void delete(ServerPlayer player, String id) {
        CreatureDefinition existing = CreatureStore.SERVER.get(id);
        if (existing == null) {
            ModNetwork.notice(player, "message.creaturestudio.unknown_creature", "", false);
            return;
        }
        if (!canEdit(player, existing)) {
            ModNetwork.notice(player, "message.creaturestudio.no_permission", existing.name, false);
            return;
        }
        CreatureStore.SERVER.remove(id);
        CreatureFiles.delete(id);
        ModNetwork.broadcastRemoval(id);
        ModNetwork.notice(player, "message.creaturestudio.deleted", existing.name, true);
    }

    public static void giveEgg(ServerPlayer player, String id) {
        CreatureDefinition d = CreatureStore.SERVER.get(id);
        if (d == null) {
            ModNetwork.notice(player, "message.creaturestudio.unknown_creature", "", false);
            return;
        }
        if (!player.getAbilities().instabuild && !consumeOne(player, Items.EGG)) {
            ModNetwork.notice(player, "message.creaturestudio.need_egg", "", false);
            return;
        }
        ItemStack egg = CreatureEggItem.create(d.id, d);
        if (!player.getInventory().add(egg)) {
            player.drop(egg, false);
        }
        ModNetwork.notice(player, "message.creaturestudio.egg_given", d.name, true);
    }

    public static void summon(ServerPlayer player, String id) {
        if (!player.getAbilities().instabuild && !player.hasPermissions(2)) {
            ModNetwork.notice(player, "message.creaturestudio.need_creative", "", false);
            return;
        }
        CreatureDefinition d = CreatureStore.SERVER.get(id);
        if (d == null) {
            ModNetwork.notice(player, "message.creaturestudio.unknown_creature", "", false);
            return;
        }
        Vec3 look = player.getLookAngle();
        double distance = 2.5 + d.hitbox()[0];
        double x = player.getX() + look.x * distance;
        double z = player.getZ() + look.z * distance;
        float yaw = Mth.wrapDegrees(player.getYRot() + 180.0f);
        CustomCreature.spawn(player.serverLevel(), d, x, player.getY(), z, yaw, true);
        ModNetwork.notice(player, "message.creaturestudio.summoned", d.name, true);
    }

    private static boolean consumeOne(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }
}
