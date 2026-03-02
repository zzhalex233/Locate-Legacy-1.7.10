package com.example.locatelegacy.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class BiomeListManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private static File configDir;
    private static File mcGameDir;
    private static File biomeListFile;

    private static final Map<Integer, LinkedHashMap<Integer, String>> DIM_TO_BIOMES = new HashMap<Integer, LinkedHashMap<Integer, String>>();

    private static long lastSaveMs = 0L;
    private static volatile boolean dirty = false;

    private BiomeListManager() {}

    public static void init(File mcConfigDir) {
        configDir = mcConfigDir;
        if (configDir == null) {
            System.out.println("[LocateLegacy] WARN: config dir is null, biome list will not be persisted.");
            return;
        }
        if (!configDir.exists()) configDir.mkdirs();

        mcGameDir = configDir.getParentFile();
        biomeListFile = new File(configDir, "LocateLegacyBiomeList.json");

        reload();
    }

    public static synchronized void reload() {
        if (configDir == null) return;

        try {
            JsonElement override = ResourcePackOverrides.tryLoadOverrideJson(
                mcGameDir,
                "config/LocateLegacyBiomeList.json",
                "LocateLegacyBiomeList.json",
                "locatelegacybiome/biomeList.json");// 路径

            if (override != null && override.isJsonObject()) {
                loadFromJsonObject(override.getAsJsonObject());
                return;
            }

            if (!biomeListFile.exists()) {
                writeEmptyTemplate();
            }

            Reader r = null;
            try {
                r = new FileReader(biomeListFile);
                JsonElement local = new JsonParser().parse(r);
                if (local != null && local.isJsonObject()) {
                    loadFromJsonObject(local.getAsJsonObject());
                } else {
                    writeEmptyTemplate();
                    DIM_TO_BIOMES.clear();
                }
            } finally {
                if (r != null) try {
                    r.close();
                } catch (Throwable ignored) {}
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void writeEmptyTemplate() {
        try {
            JsonObject root = new JsonObject();
            root.add("dimensions", new JsonArray());

            FileWriter fw = new FileWriter(biomeListFile);
            try {
                fw.write(GSON.toJson(root));
            } finally {
                fw.close();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void loadFromJsonObject(JsonObject root) {
        DIM_TO_BIOMES.clear();

        JsonArray dims = root.getAsJsonArray("dimensions");
        if (dims == null) return;

        for (int i = 0; i < dims.size(); i++) {
            JsonElement de = dims.get(i);
            if (!de.isJsonObject()) continue;

            JsonObject dobj = de.getAsJsonObject();
            if (!dobj.has("dimId")) continue;

            int dimId = safeInt(dobj.get("dimId"), 0);

            LinkedHashMap<Integer, String> biomes = new LinkedHashMap<Integer, String>();
            JsonArray arr = dobj.getAsJsonArray("biomes");
            if (arr != null) {
                for (int j = 0; j < arr.size(); j++) {
                    JsonElement be = arr.get(j);
                    if (!be.isJsonObject()) continue;

                    JsonObject bobj = be.getAsJsonObject();
                    if (!bobj.has("biomeId")) continue;

                    int biomeId = safeInt(bobj.get("biomeId"), -1);
                    if (biomeId < 0) continue;

                    String biomeName = bobj.has("biomeName") ? safeStr(bobj.get("biomeName")) : "";
                    if (!biomes.containsKey(biomeId)) {
                        biomes.put(biomeId, biomeName);
                    }
                }
            }

            DIM_TO_BIOMES.put(dimId, biomes);
        }
    }

    private static int safeInt(JsonElement e, int def) {
        try {
            return e.getAsInt();
        } catch (Throwable t) {
            return def;
        }
    }

    private static String safeStr(JsonElement e) {
        try {
            return e.getAsString();
        } catch (Throwable t) {
            return "";
        }
    }

    public static void recordBiome(int dimId, int biomeId, String biomeName) {
        if (configDir == null) return;

        LinkedHashMap<Integer, String> biomes = DIM_TO_BIOMES.get(dimId);
        if (biomes == null) {
            biomes = new LinkedHashMap<Integer, String>();
            DIM_TO_BIOMES.put(dimId, biomes);
        }

        if (biomes.containsKey(biomeId)) {
            String old = biomes.get(biomeId);
            if ((old == null || old.length() == 0) && biomeName != null) {
                biomes.put(biomeId, biomeName);
                requestSave();
            }
            return;
        }

        biomes.put(biomeId, biomeName == null ? "" : biomeName);
        requestSave();
    }

    public static List<String> getBiomeNamesForDim(int dimId) {
        LinkedHashMap<Integer, String> biomes = DIM_TO_BIOMES.get(dimId);
        if (biomes == null || biomes.isEmpty()) return new ArrayList<String>();

        ArrayList<String> out = new ArrayList<String>();
        for (String n : biomes.values()) {
            if (n != null && n.length() > 0) out.add(n);
        }
        return out;
    }

    public static void tickSave() {
        if (!dirty) return;
        long now = System.currentTimeMillis();
        if (now - lastSaveMs < 1500L) return;
        saveNow();
    }

    private static synchronized void requestSave() {
        dirty = true;
    }

    private static synchronized void saveNow() {
        if (configDir == null) return;
        if (!dirty) return;

        try {
            JsonObject root = new JsonObject();
            JsonArray dims = new JsonArray();

            for (Map.Entry<Integer, LinkedHashMap<Integer, String>> e : DIM_TO_BIOMES.entrySet()) {
                JsonObject dobj = new JsonObject();
                dobj.addProperty("dimId", e.getKey());

                JsonArray biomes = new JsonArray();
                for (Map.Entry<Integer, String> b : e.getValue()
                    .entrySet()) {
                    JsonObject bobj = new JsonObject();
                    bobj.addProperty("biomeId", b.getKey());
                    bobj.addProperty("biomeName", b.getValue() == null ? "" : b.getValue());
                    biomes.add(bobj);
                }
                dobj.add("biomes", biomes);
                dims.add(dobj);
            }

            root.add("dimensions", dims);

            FileWriter fw = new FileWriter(biomeListFile);
            try {
                fw.write(GSON.toJson(root));
            } finally {
                fw.close();
            }

            dirty = false;
            lastSaveMs = System.currentTimeMillis();

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
