package com.example.locatelegacy.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**生成 config/LocateLegacyStructures.json*/
public final class StructureConfigManager {

    private static final String FILE_NAME = "LocateLegacyStructures.json";

    private static volatile boolean inited = false;
    private static File configFile;
    private static File gameDir;

    private static final List<StructureDefinition> ENTRIES = new ArrayList<StructureDefinition>();

    private StructureConfigManager() {}

    public static void init(File mcConfigDir) {
        if (inited) return;
        inited = true;

        try {
            if (mcConfigDir == null) return;
            if (!mcConfigDir.exists()) mcConfigDir.mkdirs();

            gameDir = mcConfigDir.getParentFile();
            configFile = new File(mcConfigDir, FILE_NAME);

            if (!configFile.exists()) {
                writeDefault(configFile);
            }

            reload();

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void reload() {
        if (configFile == null) return;

        ENTRIES.clear();

        FileReader fr = null;
        try {
            JsonObject root = null;
            if (gameDir != null) {
                com.google.gson.JsonElement ov = ResourcePackOverrides
                    .tryLoadOverrideJson(gameDir, "config/" + FILE_NAME, FILE_NAME);
                if (ov != null && ov.isJsonObject()) root = ov.getAsJsonObject();
            }

            fr = new FileReader(configFile);
            JsonObject localRoot = new JsonParser().parse(fr)
                .getAsJsonObject();
            if (root == null) {
                root = localRoot;
            } else {
                if (localRoot.has("structures") && localRoot.get("structures")
                    .isJsonArray()) {
                    if (!root.has("structures") || !root.get("structures")
                        .isJsonArray()) {
                        root.add("structures", localRoot.get("structures"));
                    } else {
                        com.google.gson.JsonArray a = root.getAsJsonArray("structures");
                        com.google.gson.JsonArray b = localRoot.getAsJsonArray("structures");
                        for (com.google.gson.JsonElement el : b) a.add(el);
                    }
                }
            }

            JsonArray arr = root.has("structures") ? root.getAsJsonArray("structures") : new JsonArray();
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                StructureDefinition entry = parseEntry(e.getAsJsonObject());
                if (entry != null && entry.isValid()) {
                    if (entry.mod != null) entry.mod = entry.mod.trim()
                        .toLowerCase();
                    if (entry.id != null) entry.id = entry.id.trim()
                        .toLowerCase();
                    ENTRIES.add(entry);
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            try {
                if (fr != null) fr.close();
            } catch (Throwable ignored) {}
        }
    }

    public static List<StructureDefinition> getAllEntries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static List<StructureDefinition> getEntriesForDim(int dimId) {
        if (ENTRIES.isEmpty()) return Collections.emptyList();
        List<StructureDefinition> out = new ArrayList<StructureDefinition>();
        for (StructureDefinition e : ENTRIES) {
            if (e != null && e.dim == dimId) out.add(e);
        }
        return out;
    }

    public static StructureDefinition findById(String fullIdLower) {
        if (fullIdLower == null) return null;
        String want = fullIdLower.trim()
            .toLowerCase();
        if (want.isEmpty() || want.indexOf(':') < 0) return null;

        for (StructureDefinition e : ENTRIES) {
            if (e == null) continue;
            if (want.equals(e.fullId())) return e;
        }
        return null;
    }

    public static StructureDefinition get(String fullIdLower) {
        return findById(fullIdLower);
    }

    private static StructureDefinition parseEntry(JsonObject obj) {
        try {
            StructureDefinition e = new StructureDefinition();

            e.name = getAsString(obj, "name", "");
            e.id = getAsString(obj, "id", "");
            e.mod = getAsString(obj, "mod", "");
            e.mapGen = getAsString(obj, "mapGen", "");
            e.biomeName = getAsString(obj, "biomeName", "");
            e.dim = getAsInt(obj, "dim", 0);

            e.biomeAll = false;
            e.biomeIdWhitelist.clear();
            e.biomeIdBlacklist.clear();

            if (obj.has("biomeId")) {
                applyBiomeRule(e, obj.get("biomeId"));
            }

            if (obj.has("biomeIdWhitelist")) {
                applyIntList(e.biomeIdWhitelist, obj.get("biomeIdWhitelist"));
            }
            if (obj.has("biomeIdBlacklist")) {
                applyIntList(e.biomeIdBlacklist, obj.get("biomeIdBlacklist"));
            }

            return e;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private static void applyBiomeRule(StructureDefinition e, JsonElement biomeEl) {
        if (biomeEl == null || biomeEl.isJsonNull()) return;

        if (biomeEl.isJsonPrimitive()) {
            try {
                String s = biomeEl.getAsString();
                if (s != null && "all".equalsIgnoreCase(s.trim())) {
                    e.biomeAll = true;
                    return;
                }
            } catch (Throwable ignored) {}

            try {
                e.biomeIdWhitelist.add(Integer.valueOf(biomeEl.getAsInt()));
            } catch (Throwable ignored) {}
            return;
        }

        if (biomeEl.isJsonArray()) {
            applyIntList(e.biomeIdWhitelist, biomeEl);
            return;
        }

        if (biomeEl.isJsonObject()) {
            JsonObject o = biomeEl.getAsJsonObject();
            if (o.has("all") && o.get("all")
                .isJsonPrimitive()) {
                try {
                    e.biomeAll = o.get("all")
                        .getAsBoolean();
                } catch (Throwable ignored) {}
            }
            if (o.has("whitelist")) {
                applyIntList(e.biomeIdWhitelist, o.get("whitelist"));
            }
            if (o.has("blacklist")) {
                applyIntList(e.biomeIdBlacklist, o.get("blacklist"));
            }
        }
    }

    private static void applyIntList(java.util.List<Integer> out, JsonElement el) {
        if (el == null || el.isJsonNull()) return;
        if (el.isJsonArray()) {
            for (JsonElement x : el.getAsJsonArray()) {
                if (x != null && x.isJsonPrimitive()) {
                    try {
                        out.add(Integer.valueOf(x.getAsInt()));
                    } catch (Throwable ignored) {}
                }
            }
        } else if (el.isJsonPrimitive()) {
            try {
                out.add(Integer.valueOf(el.getAsInt()));
            } catch (Throwable ignored) {}
        }
    }

    private static String getAsString(JsonObject obj, String key, String def) {
        if (obj == null || key == null) return def;
        try {
            if (!obj.has(key)) return def;
            JsonElement e = obj.get(key);
            return e != null ? e.getAsString() : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static int getAsInt(JsonObject obj, String key, int def) {
        if (obj == null || key == null) return def;
        try {
            if (!obj.has(key)) return def;
            JsonElement e = obj.get(key);
            return e != null ? e.getAsInt() : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static void writeDefault(File f) {
        FileWriter fw = null;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("structures", new JsonArray());

            Gson gson = new GsonBuilder().setPrettyPrinting()
                .create();

            fw = new FileWriter(f);
            fw.write(gson.toJson(root));
            fw.flush();
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            try {
                if (fw != null) fw.close();
            } catch (Throwable ignored) {}
        }
    }
}
