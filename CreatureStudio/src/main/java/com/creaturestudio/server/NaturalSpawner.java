package com.creaturestudio.server;

import com.creaturestudio.data.Behavior;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.data.Movement;
import com.creaturestudio.data.SpawnTime;
import com.creaturestudio.entity.CustomCreature;
import com.creaturestudio.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Aparicion natural sencilla: cada 5 segundos, por cada jugador y criatura con la opcion activada,
 * hay una probabilidad de que aparezca una cerca (sin superar el maximo configurado).
 */
public final class NaturalSpawner {
    private static final int INTERVAL_TICKS = 100;

    private NaturalSpawner() {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL_TICKS != 0 || !CreatureStore.SERVER.isLoaded()) {
            return;
        }
        List<CreatureDefinition> spawnable = new ArrayList<>();
        for (CreatureDefinition d : CreatureStore.SERVER.all()) {
            if (d.naturalSpawn && !d.parts.isEmpty()) {
                spawnable.add(d);
            }
        }
        if (spawnable.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) {
                    continue;
                }
                for (CreatureDefinition d : spawnable) {
                    trySpawnNear(level, player, d);
                }
            }
        }
    }

    private static void trySpawnNear(ServerLevel level, ServerPlayer player, CreatureDefinition d) {
        RandomSource random = level.getRandom();
        if (random.nextInt(100) >= d.spawnChance) {
            return;
        }
        if (d.behavior == Behavior.HOSTILE && level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        if (d.spawnTime == SpawnTime.DAY && !level.isDay()) {
            return;
        }
        if (d.spawnTime == SpawnTime.NIGHT && level.isDay()) {
            return;
        }
        int nearby = level.getEntitiesOfClass(CustomCreature.class, player.getBoundingBox().inflate(64.0),
                e -> d.id.equals(e.getCreatureId())).size();
        if (nearby >= d.spawnMax) {
            return;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = 20 + random.nextInt(24);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, player.getBlockY(), z);
            if (!level.isLoaded(column)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (d.movement == Movement.FLY) {
                y += 2 + random.nextInt(6);
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.below()).isEmpty()) {
                continue;
            }
            if (d.movement == Movement.WALK
                    && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                continue;
            }
            if (!biomeMatches(level, pos, d.spawnBiomes)) {
                return; // el bioma de alrededor no cambia mucho, no insistir
            }
            CustomCreature creature = ModRegistry.CUSTOM_CREATURE.get().create(level);
            if (creature == null) {
                return;
            }
            creature.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0f, 0.0f);
            creature.setCreatureId(d.id);
            if (!level.noCollision(creature)) {
                creature.discard();
                continue;
            }
            level.addFreshEntity(creature);
            return;
        }
    }

    private static boolean biomeMatches(ServerLevel level, BlockPos pos, String biomes) {
        if (biomes == null || biomes.isEmpty()) {
            return true;
        }
        Holder<Biome> biome = level.getBiome(pos);
        for (String entry : biomes.split(",")) {
            String t = entry.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(t.substring(1));
                if (tag != null && biome.is(TagKey.create(Registries.BIOME, tag))) {
                    return true;
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(t);
                if (id != null && biome.is(id)) {
                    return true;
                }
            }
        }
        return false;
    }
}
