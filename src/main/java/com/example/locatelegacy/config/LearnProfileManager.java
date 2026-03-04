package com.example.locatelegacy.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.locatelegacy.locate.StructureLocator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class LearnProfileManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final String FILE_NAME = "LocateLegacyLearnTemp.json";

    private static final Map<String, LearnEntry> ENTRIES = new HashMap<String, LearnEntry>();
    private static File learnFile;

    private LearnProfileManager() {}

    public static synchronized void init(File mcConfigDir) {
        if (mcConfigDir == null) return;
        if (!mcConfigDir.exists()) mcConfigDir.mkdirs();
        learnFile = new File(mcConfigDir, FILE_NAME);
        load();
    }

    public static synchronized LearnSummary recordLearn(StructureLocator.DebugLearnResult r) {
        if (r == null || r.fullId == null
            || r.fullId.trim()
                .isEmpty())
            return null;

        String key = keyOf(r.fullId, r.dim);
        LearnEntry e = ENTRIES.get(key);
        if (e == null) {
            e = new LearnEntry();
            e.fullId = r.fullId.toLowerCase();
            e.dim = r.dim;
            e.mapGen = r.mapGenClass == null ? "" : r.mapGenClass;
            ENTRIES.put(key, e);
        }

        e.samples++;
        if ((e.mapGen == null || e.mapGen.length() == 0) && r.mapGenClass != null) {
            e.mapGen = r.mapGenClass;
        }

        if (r.occupiedChunkDiameter != null) {
            int d = r.occupiedChunkDiameter.intValue();
            Integer c = e.diameterCounts.get(Integer.valueOf(d));
            e.diameterCounts.put(Integer.valueOf(d), Integer.valueOf(c == null ? 1 : c.intValue() + 1));
        }

        if (r.heightMinY != null) {
            e.heightMinY = e.heightMinY == null ? r.heightMinY
                : Integer.valueOf(Math.min(e.heightMinY.intValue(), r.heightMinY.intValue()));
        }
        if (r.heightMaxY != null) {
            e.heightMaxY = e.heightMaxY == null ? r.heightMaxY
                : Integer.valueOf(Math.max(e.heightMaxY.intValue(), r.heightMaxY.intValue()));
        }
        if (r.heightSampleCount > 0) {
            e.heightSampleCount += r.heightSampleCount;
            e.heightSampleSum += r.heightSampleSum;
        }

        if (r.biomeNames != null) {
            for (String name : r.biomeNames) {
                if (name != null) {
                    String n = name.trim().toLowerCase();
                    if (n.length() > 0) e.biomeWhitelist.add(n);
                }
            }
        }

        save();
        return toSummary(e);
    }

    public static synchronized boolean clear() {
        ENTRIES.clear();
        if (learnFile == null) return true;
        if (!learnFile.exists()) return true;
        return learnFile.delete();
    }

    private static String keyOf(String fullId, int dim) {
        return fullId.toLowerCase() + "|" + dim;
    }

    private static void load() {
        ENTRIES.clear();
        if (learnFile == null || !learnFile.exists()) return;

        FileReader fr = null;
        try {
            fr = new FileReader(learnFile);
            JsonElement rootEl = new JsonParser().parse(fr);
            if (rootEl == null || !rootEl.isJsonObject()) return;
            JsonObject root = rootEl.getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("entries");
            if (arr == null) return;

            for (JsonElement el : arr) {
                if (el == null || !el.isJsonObject()) continue;
                LearnEntry e = fromJson(el.getAsJsonObject());
                if (e == null || e.fullId == null) continue;
                ENTRIES.put(keyOf(e.fullId, e.dim), e);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            try {
                if (fr != null) fr.close();
            } catch (Throwable ignored) {}
        }
    }

    private static void save() {
        if (learnFile == null) return;

        FileWriter fw = null;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            JsonArray arr = new JsonArray();
            for (LearnEntry e : ENTRIES.values()) {
                arr.add(toJson(e));
            }
            root.add("entries", arr);

            fw = new FileWriter(learnFile);
            fw.write(GSON.toJson(root));
            fw.flush();
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            try {
                if (fw != null) fw.close();
            } catch (Throwable ignored) {}
        }
    }

    private static LearnEntry fromJson(JsonObject o) {
        try {
            LearnEntry e = new LearnEntry();
            e.fullId = getAsString(o, "fullId", "").toLowerCase();
            e.dim = getAsInt(o, "dim", 0);
            e.mapGen = getAsString(o, "mapGen", "");
            e.samples = getAsInt(o, "samples", 0);

            if (o.has("heightMinY")) e.heightMinY = Integer.valueOf(getAsInt(o, "heightMinY", 0));
            if (o.has("heightMaxY")) e.heightMaxY = Integer.valueOf(getAsInt(o, "heightMaxY", 0));
            e.heightSampleSum = getAsLong(o, "heightSampleSum", 0L);
            e.heightSampleCount = getAsInt(o, "heightSampleCount", 0);

            if (o.has("diameterCounts") && o.get("diameterCounts")
                .isJsonObject()) {
                JsonObject d = o.getAsJsonObject("diameterCounts");
                for (Map.Entry<String, JsonElement> it : d.entrySet()) {
                    try {
                        int key = Integer.parseInt(it.getKey());
                        int val = it.getValue()
                            .getAsInt();
                        e.diameterCounts.put(Integer.valueOf(key), Integer.valueOf(val));
                    } catch (Throwable ignored) {}
                }
            }

            if (o.has("biomeWhitelist") && o.get("biomeWhitelist")
                .isJsonArray()) {
                for (JsonElement be : o.getAsJsonArray("biomeWhitelist")) {
                    try {
                        if (be.isJsonPrimitive()) {
                            String n = be.getAsString();
                            if (n != null) {
                                n = n.trim().toLowerCase();
                                if (n.length() > 0) e.biomeWhitelist.add(n);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }

            return e;
        } catch (Throwable t) {
            return null;
        }
    }

    private static JsonObject toJson(LearnEntry e) {
        JsonObject o = new JsonObject();
        o.addProperty("fullId", e.fullId);
        o.addProperty("dim", e.dim);
        o.addProperty("mapGen", e.mapGen == null ? "" : e.mapGen);
        o.addProperty("samples", e.samples);
        if (e.heightMinY != null) o.addProperty("heightMinY", e.heightMinY.intValue());
        if (e.heightMaxY != null) o.addProperty("heightMaxY", e.heightMaxY.intValue());
        o.addProperty("heightSampleSum", e.heightSampleSum);
        o.addProperty("heightSampleCount", e.heightSampleCount);

        JsonObject d = new JsonObject();
        for (Map.Entry<Integer, Integer> it : e.diameterCounts.entrySet()) {
            d.addProperty(String.valueOf(it.getKey()), it.getValue());
        }
        o.add("diameterCounts", d);

        JsonArray b = new JsonArray();
        List<String> list = new ArrayList<String>(e.biomeWhitelist);
        Collections.sort(list);
        for (String name : list) b.add(new com.google.gson.JsonPrimitive(name));
        o.add("biomeWhitelist", b);
        return o;
    }

    private static LearnSummary toSummary(LearnEntry e) {
        LearnSummary s = new LearnSummary();
        s.fullId = e.fullId;
        s.dim = e.dim;
        s.mapGen = e.mapGen;
        s.samples = e.samples;
        s.heightMinY = e.heightMinY;
        s.heightMaxY = e.heightMaxY;
        s.heightAvgY = e.heightSampleCount > 0 ? (double) e.heightSampleSum / (double) e.heightSampleCount : null;
        s.biomeWhitelist = new ArrayList<String>(e.biomeWhitelist);
        Collections.sort(s.biomeWhitelist);
        s.occupiedChunkDiameter = pickDiameter(e.diameterCounts);
        s.occupiedChunkDiameterMin = findDiameterMin(e.diameterCounts);
        s.occupiedChunkDiameterMax = findDiameterMax(e.diameterCounts);
        s.copyJson = buildSummaryJson(s);
        return s;
    }

    private static Integer pickDiameter(Map<Integer, Integer> counts) {
        if (counts == null || counts.isEmpty()) return null;
        int bestKey = 0;
        int bestCount = Integer.MIN_VALUE;
        for (Map.Entry<Integer, Integer> it : counts.entrySet()) {
            int d = it.getKey()
                .intValue();
            int c = it.getValue()
                .intValue();
            if (c > bestCount || (c == bestCount && d < bestKey)) {
                bestCount = c;
                bestKey = d;
            }
        }
        return Integer.valueOf(bestKey);
    }

    private static Integer findDiameterMin(Map<Integer, Integer> counts) {
        if (counts == null || counts.isEmpty()) return null;
        int v = Integer.MAX_VALUE;
        for (Integer k : counts.keySet()) {
            if (k != null && k.intValue() < v) v = k.intValue();
        }
        return v == Integer.MAX_VALUE ? null : Integer.valueOf(v);
    }

    private static Integer findDiameterMax(Map<Integer, Integer> counts) {
        if (counts == null || counts.isEmpty()) return null;
        int v = Integer.MIN_VALUE;
        for (Integer k : counts.keySet()) {
            if (k != null && k.intValue() > v) v = k.intValue();
        }
        return v == Integer.MIN_VALUE ? null : Integer.valueOf(v);
    }

    private static String buildSummaryJson(LearnSummary s) {
        String mod = "minecraft";
        String id = s.fullId;
        int p = s.fullId.indexOf(':');
        if (p > 0) {
            mod = s.fullId.substring(0, p);
            id = s.fullId.substring(p + 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"")
            .append(id)
            .append("\",\"mod\":\"")
            .append(mod)
            .append("\",\"mapGen\":\"")
            .append(s.mapGen == null ? "" : s.mapGen)
            .append("\",\"dim\":")
            .append(s.dim);

        if (s.biomeWhitelist != null && !s.biomeWhitelist.isEmpty()) {
            sb.append(",\"biomeNameWhitelist\":[");
            for (int i = 0; i < s.biomeWhitelist.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(s.biomeWhitelist.get(i))).append("\"");
            }
            sb.append("]");
        } else {
            sb.append(",\"biomeName\":\"all\"");
        }

        sb.append(",\"filters\":{\"strict\":true");
        if (s.heightMinY != null || s.heightMaxY != null) {
            int minY = s.heightMinY != null ? s.heightMinY.intValue() : 0;
            int maxY = s.heightMaxY != null ? s.heightMaxY.intValue() : 255;
            minY = Math.max(1, minY - 8);
            maxY = Math.min(255, maxY + 8);
            sb.append(",\"heightRange\":{\"minY\":")
                .append(minY);
            sb.append(",\"maxY\":")
                .append(maxY);
            sb.append(",\"unknownPolicy\":\"pass\"}");
        }
        if (s.occupiedChunkDiameterMin != null || s.occupiedChunkDiameterMax != null) {
            int min = s.occupiedChunkDiameterMin != null ? s.occupiedChunkDiameterMin.intValue() : 1;
            int max = s.occupiedChunkDiameterMax != null ? s.occupiedChunkDiameterMax.intValue() : min;
            sb.append(",\"occupiedChunkDiameter\":{\"min\":")
                .append(min);
            sb.append(",\"max\":")
                .append(max);
            sb.append(",\"unknownPolicy\":\"pass\"}");
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String getAsString(JsonObject obj, String key, String def) {
        if (obj == null || key == null || !obj.has(key)) return def;
        try {
            return obj.get(key)
                .getAsString();
        } catch (Throwable t) {
            return def;
        }
    }

    private static int getAsInt(JsonObject obj, String key, int def) {
        if (obj == null || key == null || !obj.has(key)) return def;
        try {
            return obj.get(key)
                .getAsInt();
        } catch (Throwable t) {
            return def;
        }
    }

    private static long getAsLong(JsonObject obj, String key, long def) {
        if (obj == null || key == null || !obj.has(key)) return def;
        try {
            return obj.get(key)
                .getAsLong();
        } catch (Throwable t) {
            return def;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class LearnEntry {

        String fullId;
        int dim;
        String mapGen;
        int samples;
        Integer heightMinY;
        Integer heightMaxY;
        long heightSampleSum;
        int heightSampleCount;
        final Map<Integer, Integer> diameterCounts = new HashMap<Integer, Integer>();
        final Set<String> biomeWhitelist = new LinkedHashSet<String>();
    }

    public static final class LearnSummary {

        public String fullId;
        public int dim;
        public String mapGen;
        public int samples;
        public Integer heightMinY;
        public Integer heightMaxY;
        public Double heightAvgY;
        public Integer occupiedChunkDiameter;
        public Integer occupiedChunkDiameterMin;
        public Integer occupiedChunkDiameterMax;
        public List<String> biomeWhitelist;
        public String copyJson;
    }
}
