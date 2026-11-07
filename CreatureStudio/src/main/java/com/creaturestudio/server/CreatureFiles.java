package com.creaturestudio.server;

import com.creaturestudio.CreatureStudio;
import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureJson;
import com.creaturestudio.data.CreatureStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Guarda cada criatura como un archivo JSON en <mundo>/creaturestudio/. */
public final class CreatureFiles {
    @Nullable
    private static Path dir;

    private CreatureFiles() {
    }

    public static void load(MinecraftServer server) {
        CreatureStore.SERVER.clear();
        dir = server.getWorldPath(LevelResource.ROOT).resolve("creaturestudio");
        int count = 0;
        try {
            Files.createDirectories(dir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path file : stream) {
                    try {
                        CreatureDefinition d = CreatureJson.read(Files.readString(file, StandardCharsets.UTF_8));
                        String fileName = file.getFileName().toString();
                        String fileId = fileName.substring(0, fileName.length() - ".json".length());
                        if (!CreatureDefinition.isValidId(d.id)) {
                            d.id = CreatureDefinition.isValidId(fileId) ? fileId : CreatureDefinition.newId();
                        }
                        d.sanitize();
                        if (d.id.isEmpty()) {
                            continue;
                        }
                        if (d.revision <= 0) {
                            d.revision = 1;
                        }
                        CreatureStore.SERVER.put(d);
                        count++;
                    } catch (Exception e) {
                        CreatureStudio.LOGGER.warn("Creature Studio: no se pudo leer {}: {}", file, e.toString());
                    }
                }
            }
        } catch (IOException e) {
            CreatureStudio.LOGGER.error("Creature Studio: no se pudo abrir la carpeta {}", dir, e);
        }
        CreatureStore.SERVER.setLoaded(true);
        CreatureStudio.LOGGER.info("Creature Studio: {} criaturas cargadas desde {}", count, dir);
    }

    public static void unload() {
        dir = null;
        CreatureStore.SERVER.clear();
    }

    public static void save(CreatureDefinition d) {
        Path folder = dir;
        if (folder == null) {
            return;
        }
        try {
            Files.createDirectories(folder);
            Path target = folder.resolve(d.id + ".json");
            Path tmp = folder.resolve(d.id + ".json.tmp");
            Files.writeString(tmp, CreatureJson.write(d, true), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            CreatureStudio.LOGGER.error("Creature Studio: no se pudo guardar {}", d.id, e);
        }
    }

    public static void delete(String id) {
        Path folder = dir;
        if (folder == null || !CreatureDefinition.isValidId(id)) {
            return;
        }
        try {
            Files.deleteIfExists(folder.resolve(id + ".json"));
        } catch (IOException e) {
            CreatureStudio.LOGGER.error("Creature Studio: no se pudo borrar {}", id, e);
        }
    }
}
