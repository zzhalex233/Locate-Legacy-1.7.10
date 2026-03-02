package com.example.locatelegacy.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * utility for loading override .json from biomepack.
 *
 * a biomepack is a zip placed in .minecraft/resourcepacks/ and named as "*-biomepack.zip".
 *
 * respect Minecraft's selected resource packs in .minecraft/options.txt: ps总算在BETA7改掉了
 * resourcePacks:[...]
 * - Only selected "*-biomepack.zip" will be considered.
 * - If none selected / options missing, fallback to scanning all biomepacks (老版本逻辑)
 *
 * Supported paths inside zip:
 * - config/LocateLegacyBiomeList.json
 * - config/LocateLegacyStructures.json
 * - LocateLegacyBiomeList.json
 * - LocateLegacyStructures.json
 */
public final class ResourcePackOverrides {

    private ResourcePackOverrides() {}

    public static JsonElement tryLoadOverrideJson(File gameDir, String... candidatePathsInZip) {
        try {
            if (gameDir == null) return null;

            File rpDir = new File(gameDir, "resourcepacks");
            if (!rpDir.isDirectory()) return null;

            List<File> packs = getSelectedBiomePacks(gameDir, rpDir);

            if (packs.isEmpty()) {
                packs = scanAllBiomePacks(rpDir);
            }

            if (packs.isEmpty()) return null;

            Collections.sort(packs, new Comparator<File>() {

                @Override
                public int compare(File a, File b) {
                    return a.getName()
                        .compareToIgnoreCase(b.getName());
                }
            });

            for (int i = packs.size() - 1; i >= 0; i--) {
                File pack = packs.get(i);
                ZipFile zf = null;
                try {
                    zf = new ZipFile(pack);

                    ZipEntry hit = null;
                    for (String p : candidatePathsInZip) {
                        if (p == null || p.length() == 0) continue;
                        ZipEntry e = zf.getEntry(p);
                        if (e != null && !e.isDirectory()) {
                            hit = e;
                            break;
                        }
                    }
                    if (hit == null) continue;

                    InputStream in = zf.getInputStream(hit);
                    BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                    try {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line)
                                .append('\n');
                        }
                        return new JsonParser().parse(sb.toString());
                    } finally {
                        try {
                            br.close();
                        } catch (Throwable ignored) {}
                    }

                } finally {
                    try {
                        if (zf != null) zf.close();
                    } catch (Throwable ignored) {}
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    /** 扫描所有以 "-biomepack.zip" 结尾的资源包 */
    private static List<File> scanAllBiomePacks(File rpDir) {
        List<File> packs = new ArrayList<File>();
        File[] files = rpDir.listFiles();
        if (files == null || files.length == 0) return packs;

        for (File f : files) {
            if (f == null || !f.isFile()) continue;
            String n = f.getName()
                .toLowerCase();
            if (n.endsWith("-biomepack.zip")) packs.add(f);
        }
        return packs;
    }

    private static List<File> getSelectedBiomePacks(File gameDir, File rpDir) {
        List<File> out = new ArrayList<File>();

        BufferedReader br = null;
        try {
            File opt = new File(gameDir, "options.txt");
            if (!opt.isFile()) return out;

            br = new BufferedReader(new InputStreamReader(new FileInputStream(opt), "UTF-8"));
            String line;
            String rpLine = null;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("resourcePacks:")) {
                    rpLine = line;
                    break;
                }
            }
            if (rpLine == null) return out;

            int idx = rpLine.indexOf(':');
            if (idx < 0) return out;

            String raw = rpLine.substring(idx + 1)
                .trim();
            if (raw.length() == 0) return out;

            JsonArray arr = tryParseJsonArray(raw);
            if (arr == null) return out;

            for (int i = 0; i < arr.size(); i++) {
                JsonElement el = arr.get(i);
                if (el == null || !el.isJsonPrimitive()) continue;

                String s;
                try {
                    s = el.getAsString();
                } catch (Throwable t) {
                    continue;
                }
                if (s == null) continue;
                s = s.trim();
                if (s.length() == 0) continue;

                if (s.startsWith("file/")) s = s.substring("file/".length());

                String lower = s.toLowerCase();
                if (!lower.endsWith("-biomepack.zip")) continue;

                File f = new File(rpDir, s);
                if (f.isFile()) {
                    out.add(f);
                }
            }

        } catch (Throwable t) {} finally {
            try {
                if (br != null) br.close();
            } catch (Throwable ignored) {}
        }

        return out;
    }

    private static JsonArray tryParseJsonArray(String raw) {
        try {
            JsonElement e = new JsonParser().parse(raw);
            if (e != null && e.isJsonArray()) return e.getAsJsonArray();
        } catch (Throwable ignored) {}

        try {
            int a = raw.indexOf('[');
            int b = raw.lastIndexOf(']');
            if (a >= 0 && b > a) {
                String cut = raw.substring(a, b + 1);
                JsonElement e2 = new JsonParser().parse(cut);
                if (e2 != null && e2.isJsonArray()) return e2.getAsJsonArray();
            }
        } catch (Throwable ignored) {}

        return null;
    }
}
