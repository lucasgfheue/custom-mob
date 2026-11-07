package com.creaturestudio.client;

import com.creaturestudio.data.CreatureDefinition;
import com.creaturestudio.data.CreatureJson;
import net.minecraft.Util;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Exportar e importar criaturas como archivos JSON en .minecraft/creaturestudio/ */
public final class ClientFiles {

    private ClientFiles() {
    }

    public static Path folder() {
        Path dir = FMLPaths.GAMEDIR.get().resolve("creaturestudio");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    public static Path export(CreatureDefinition def) throws IOException {
        Path dir = folder();
        String base = safeName(def.name);
        Path target = dir.resolve(base + ".json");
        int n = 2;
        while (Files.exists(target)) {
            target = dir.resolve(base + "_" + n++ + ".json");
        }
        CreatureDefinition copy = def.copy();
        copy.owner = "";
        Files.writeString(target, CreatureJson.write(copy, true), StandardCharsets.UTF_8);
        return target;
    }

    public static List<Path> list() {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder(), "*.json")) {
            for (Path p : stream) {
                out.add(p);
            }
        } catch (IOException ignored) {
        }
        out.sort((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()));
        return out;
    }

    /** Lee un archivo y lo prepara como criatura nueva (con identificador nuevo). */
    public static CreatureDefinition load(Path file) throws IOException {
        CreatureDefinition def;
        try {
            def = CreatureJson.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage(), e);
        }
        def.id = CreatureDefinition.newId();
        def.owner = "";
        def.revision = 0;
        def.sanitize();
        if (def.id.isEmpty()) {
            def.id = CreatureDefinition.newId();
        }
        return def;
    }

    public static void openFolder() {
        Util.getPlatform().openFile(folder().toFile());
    }

    private static String safeName(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('_');
            }
            if (sb.length() >= 40) {
                break;
            }
        }
        return sb.length() == 0 ? "criatura" : sb.toString();
    }
}
