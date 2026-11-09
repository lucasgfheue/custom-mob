package com.creaturestudio.server;

import com.creaturestudio.data.Behavior;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureStore;
import com.creaturestudio.data.Movement;
import com.creaturestudio.data.SpawnPlace;
import com.creaturestudio.data.SpawnSearch;
import com.creaturestudio.data.SpawnTime;
import com.creaturestudio.entity.CustomCreature;
import com.creaturestudio.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Aparicion natural sencilla: cada 5 segundos, por cada jugador y criatura con la opcion activada, hay una
 * probabilidad de que aparezca una cerca (sin superar el maximo configurado), en el lugar elegido (superficie, bajo
 * tierra, agua o cielo) o dentro de las estructuras elegidas.
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
        if (d.spawnsInStructures()) {
            trySpawnInStructure(level, player, d, random);
        } else {
            trySpawnInPlace(level, player, d, random);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Lugar: superficie, bajo tierra, agua o cielo
    // ---------------------------------------------------------------------------------------

    private static void trySpawnInPlace(ServerLevel level, ServerPlayer player, CreatureDefinition d,
                                        RandomSource random) {
        boolean flyer = d.movement == Movement.FLY;
        boolean ceiling = level.dimensionType().hasCeiling();
        boolean surfaceLike = !ceiling && (d.spawnPlace == SpawnPlace.SURFACE || d.spawnPlace == SpawnPlace.SKY);
        int height = creatureHeight(d);
        for (int attempt = 0; attempt < 4; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = 20 + random.nextInt(24);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = SpawnSearch.find(d.spawnPlace, column(level, x, z), top, level.getMinBuildHeight(), ceiling,
                    flyer, height, random::nextInt);
            if (y == SpawnSearch.NONE) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (!biomeMatches(level, pos, d.spawnBiomes)) {
                if (surfaceLike) {
                    return; // en la superficie el bioma de alrededor no cambia mucho, no insistir
                }
                continue; // bajo tierra y en el agua cambia con la altura y la zona
            }
            if (spawnAt(level, d, x, y, z, random)) {
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Estructuras: aldeas, minas, fortalezas...
    // ---------------------------------------------------------------------------------------

    private static void trySpawnInStructure(ServerLevel level, ServerPlayer player, CreatureDefinition d,
                                            RandomSource random) {
        Predicate<Structure> matches = structureMatcher(level, d.structureTargets());
        boolean flyer = d.movement == Movement.FLY;
        int height = creatureHeight(d);
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        for (int attempt = 0; attempt < 6; attempt++) {
            // un chunk cercano: cada chunk sabe que estructuras lo tocan
            int cx = (px >> 4) + random.nextInt(7) - 3;
            int cz = (pz >> 4) + random.nextInt(7) - 3;
            if (!level.hasChunk(cx, cz)) {
                continue;
            }
            List<StructureStart> starts = level.structureManager().startsForStructure(new ChunkPos(cx, cz), matches);
            if (starts.isEmpty()) {
                continue;
            }
            StructureStart start = starts.get(random.nextInt(starts.size()));
            if (!start.isValid()) {
                continue;
            }
            // una pieza cercana al jugador (las minas y fortalezas son enormes)
            List<BoundingBox> near = new ArrayList<>();
            for (StructurePiece piece : start.getPieces()) {
                BoundingBox b = piece.getBoundingBox();
                int bx = (b.minX() + b.maxX()) / 2;
                int bz = (b.minZ() + b.maxZ()) / 2;
                long dx = bx - px;
                long dz = bz - pz;
                if (dx * dx + dz * dz <= 56L * 56L) {
                    near.add(b);
                }
            }
            if (near.isEmpty()) {
                continue;
            }
            BoundingBox box = near.get(random.nextInt(near.size()));
            int x = box.minX() + random.nextInt(box.maxX() - box.minX() + 1);
            int z = box.minZ() + random.nextInt(box.maxZ() - box.minZ() + 1);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = SpawnSearch.findInBox(column(level, x, z), box.minY(), box.maxY(), flyer, height, random::nextInt);
            if (y == SpawnSearch.NONE) {
                continue;
            }
            // ni encima del jugador ni muy lejos de el
            double dist = player.distanceToSqr(x + 0.5, y, z + 0.5);
            if (dist < 10.0 * 10.0 || dist > 72.0 * 72.0) {
                continue;
            }
            if (!biomeMatches(level, new BlockPos(x, y, z), d.spawnBiomes)) {
                continue;
            }
            if (spawnAt(level, d, x, y, z, random)) {
                return;
            }
        }
    }

    /** Comprueba si una estructura es una de las elegidas (por id o por etiqueta). */
    private static Predicate<Structure> structureMatcher(ServerLevel level, List<String> targets) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<ResourceLocation> ids = new ArrayList<>();
        List<TagKey<Structure>> tags = new ArrayList<>();
        for (String t : targets) {
            if (t.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(t.substring(1));
                if (tag != null) {
                    tags.add(TagKey.create(Registries.STRUCTURE, tag));
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(t);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return structure -> {
            ResourceLocation id = registry.getKey(structure);
            if (id != null && ids.contains(id)) {
                return true;
            }
            if (!tags.isEmpty()) {
                Holder<Structure> holder = registry.wrapAsHolder(structure);
                for (TagKey<Structure> tag : tags) {
                    if (holder.is(tag)) {
                        return true;
                    }
                }
            }
            return false;
        };
    }

    // ---------------------------------------------------------------------------------------
    // Comun
    // ---------------------------------------------------------------------------------------

    /** La columna (x, z) del mundo vista por {@link SpawnSearch}. */
    private static SpawnSearch.Column column(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return new SpawnSearch.Column() {
            @Override
            public boolean solid(int y) {
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                return !state.getCollisionShape(level, pos).isEmpty();
            }

            @Override
            public boolean passable(int y) {
                pos.set(x, y, z);
                if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                    return false;
                }
                BlockState state = level.getBlockState(pos);
                return state.getCollisionShape(level, pos).isEmpty() && !state.getFluidState().is(FluidTags.LAVA);
            }

            @Override
            public boolean water(int y) {
                pos.set(x, y, z);
                return level.getFluidState(pos).is(FluidTags.WATER);
            }

            @Override
            public boolean sky(int y) {
                pos.set(x, y, z);
                return level.canSeeSky(pos);
            }
        };
    }

    /** Alto de la criatura en bloques, redondeado hacia arriba (para buscar huecos donde quepa). */
    private static int creatureHeight(CreatureDefinition d) {
        float[] hb = d.hitbox();
        return Math.max(1, Math.min(8, Mth.ceil(hb[1])));
    }

    private static boolean spawnAt(ServerLevel level, CreatureDefinition d, int x, int y, int z, RandomSource random) {
        CustomCreature creature = ModRegistry.CUSTOM_CREATURE.get().create(level);
        if (creature == null) {
            return false;
        }
        creature.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0f, 0.0f);
        creature.setCreatureId(d.id);
        if (!level.noCollision(creature)) {
            creature.discard();
            return false;
        }
        level.addFreshEntity(creature);
        return true;
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
