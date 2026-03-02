package com.example.locatelegacy.config;

import java.util.ArrayList;
import java.util.List;

/**
 * config/LocateLegacyStructures.json 结构定义。
 **/
public class StructureDefinition {

    /** 展示名（e.g Lich Tower） */
    public String name = "";

    /** 结构ID（e.g lich_tower） */
    public String id = "";

    /** modID（e.g twilightforest） */
    public String mod = "";

    /** Dim（e.g 7、-1） */
    public int dim = 0;

    /**
     * MapGen
     * 主世界村庄 net.minecraft.world.gen.structure.MapGenVillage
     * 暮色森林 twilightforest.world.MapGenTFMajorFeature
     */
    public String mapGen = "";

    /** 群系名（e.g Ocean） 这个纯展示用，其实不用填 */
    public String biomeName = "";

    /**
     * - biomeAll=true：允许全部群系（可配合 biomeIdBlacklist 排除）
     * - biomeIdWhitelist 非空：只允许这些群系
     * - biomeIdBlacklist 非空：允许全部（或 whitelist 结果）后再排除
     */
    public boolean biomeAll = false;

    /** 群系白名单，可多 */
    public List<Integer> biomeIdWhitelist = new ArrayList<Integer>();

    /** 群系黑名单，可多 */
    public List<Integer> biomeIdBlacklist = new ArrayList<Integer>();

    public boolean isValid() {
        return id != null && !id.trim()
            .isEmpty()
            && mapGen != null
            && !mapGen.trim()
                .isEmpty();
    }

    public String fullId() {
        String rawId = (id == null) ? "" : id.trim();
        if (rawId.contains(":")) {
            return rawId.toLowerCase();
        }

        String ns = (mod == null || mod.trim()
            .isEmpty()) ? "minecraft" : mod.trim();
        return (ns + ":" + rawId).toLowerCase();
    }
}
