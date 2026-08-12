package com.garam.regiondifficulty.difficulty;

import com.garam.regiondifficulty.Config;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单个维度的深度曲线参数。
 */
record DepthCurve(float baseY, float maxY, float maxMultiplier, float minMultiplier) {}

/**
 * 从配置派生的倍率表的不可变快照。
 * 在配置加载和重载时根据 Config 值重新构建。
 */
@SuppressWarnings("removal")
public class DifficultyMultipliers {

    private final Map<String, Float> dimensionMultipliers;
    private final Map<String, Float> biomeMultipliers;
    private final Map<String, Float> structureMultipliers;
    private final Map<String, DepthCurve> depthOverrides;
    private final float defaultMultiplier;
    private final float depthBaseY;
    private final float depthMaxY;
    private final float depthMaxMultiplier;
    private final float depthMinMultiplier;

    private DifficultyMultipliers(Map<String, Float> dimensionMultipliers,
                                  Map<String, Float> biomeMultipliers,
                                  Map<String, Float> structureMultipliers,
                                  Map<String, DepthCurve> depthOverrides,
                                  float defaultMultiplier,
                                  float depthBaseY, float depthMaxY,
                                  float depthMaxMultiplier, float depthMinMultiplier) {
        this.dimensionMultipliers = Collections.unmodifiableMap(dimensionMultipliers);
        this.biomeMultipliers = Collections.unmodifiableMap(biomeMultipliers);
        this.structureMultipliers = Collections.unmodifiableMap(structureMultipliers);
        this.depthOverrides = Collections.unmodifiableMap(depthOverrides);
        this.defaultMultiplier = defaultMultiplier;
        this.depthBaseY = depthBaseY;
        this.depthMaxY = depthMaxY;
        this.depthMaxMultiplier = depthMaxMultiplier;
        this.depthMinMultiplier = depthMinMultiplier;
    }

    // ========== 工厂方法 ==========

    /**
     * 根据当前 Config 值构建一个 DifficultyMultipliers 实例。
     */
    public static DifficultyMultipliers fromConfig() {
        return new DifficultyMultipliers(
                parseEntryList(Config.dimensionMultipliers),
                parseEntryList(Config.biomeMultipliers),
                parseEntryList(Config.structureMultipliers),
                parseDepthOverrides(Config.depthOverrides),
                Config.defaultMultiplier,
                Config.depthBaseY,
                Config.depthMaxY,
                Config.depthMaxMultiplier,
                Config.depthMinMultiplier
        );
    }

    /**
     * 将"命名空间:路径=值"格式的字符串列表解析为 String->Float 映射。
     */
    @SuppressWarnings("deprecation")
    private static Map<String, Float> parseEntryList(List<? extends String> entries) {
        Map<String, Float> map = new HashMap<>();
        if (entries == null) return map;
        for (String entry : entries) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) {
                try {
                    String[] idParts = parts[0].split(":", 2);
                    if (idParts.length != 2) continue;
                    String key = new ResourceLocation(idParts[0], idParts[1]).toString();
                    float value = Float.parseFloat(parts[1]);
                    if (Float.isFinite(value)) {
                        map.put(key, value);
                    }
                } catch (Exception ignored) {
                    // 跳过格式错误的条目
                }
            }
        }
        return map;
    }

    /**
     * 将"命名空间:路径=baseY,maxY,maxMultiplier,minMultiplier"格式的字符串列表
     * 解析为 String->DepthCurve 映射。
     */
    @SuppressWarnings("deprecation")
    private static Map<String, DepthCurve> parseDepthOverrides(List<? extends String> entries) {
        Map<String, DepthCurve> map = new HashMap<>();
        if (entries == null) return map;
        for (String entry : entries) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) continue;
            try {
                String[] idParts = parts[0].split(":", 2);
                if (idParts.length != 2) continue;
                String key = new ResourceLocation(idParts[0], idParts[1]).toString();
                String[] values = parts[1].split(",", 4);
                if (values.length != 4) continue;
                float baseY = Float.parseFloat(values[0].trim());
                float maxY = Float.parseFloat(values[1].trim());
                float maxMult = Float.parseFloat(values[2].trim());
                float minMult = Float.parseFloat(values[3].trim());
                if (Float.isFinite(baseY) && Float.isFinite(maxY)
                        && Float.isFinite(maxMult) && Float.isFinite(minMult)) {
                    map.put(key, new DepthCurve(baseY, maxY, maxMult, minMult));
                }
            } catch (Exception ignored) {
                // 跳过格式错误的条目
            }
        }
        return map;
    }

    // ========== 访问器 ==========

    public float getDimensionMultiplier(ResourceKey<Level> dimensionKey) {
        return dimensionMultipliers.getOrDefault(dimensionKey.location().toString(), defaultMultiplier);
    }

    public float getBiomeMultiplier(ResourceKey<Biome> biomeKey) {
        return biomeMultipliers.getOrDefault(biomeKey.location().toString(), defaultMultiplier);
    }

    public float getStructureMultiplier(ResourceKey<Structure> structureKey) {
        return structureMultipliers.getOrDefault(structureKey.location().toString(), 1.0F);
    }

    /**
     * 返回所有已配置结构条目的集合，用于迭代。
     */
    public Set<Map.Entry<String, Float>> getAllStructureEntries() {
        return structureMultipliers.entrySet();
    }

    public float getDefaultMultiplier() {
        return defaultMultiplier;
    }

    /**
     * 使用线性插值根据 Y 坐标和维度计算深度倍率。
     * 如果该维度有配置的覆盖，则使用维度专属的曲线参数；
     * 否则回退到全局默认深度参数。
     * <ul>
     *   <li>在 baseY（地表）以上 → minMultiplier</li>
     *   <li>在 maxY（最深点）以下 → maxMultiplier</li>
     *   <li>介于两者之间 → 线性插值</li>
     * </ul>
     */
    public float getDepthMultiplier(ResourceKey<Level> dimension, double y) {
        String dimKey = dimension.location().toString();
        DepthCurve curve = depthOverrides.get(dimKey);
        if (curve != null) {
            return computeDepthMultiplier(curve.baseY(), curve.maxY(),
                    curve.maxMultiplier(), curve.minMultiplier(), y);
        }
        return computeDepthMultiplier(depthBaseY, depthMaxY,
                depthMaxMultiplier, depthMinMultiplier, y);
    }

    /**
     * 根据给定的深度曲线参数计算倍率。
     */
    private static float computeDepthMultiplier(float baseY, float maxY,
                                                 float maxMultiplier, float minMultiplier,
                                                 double y) {
        if (y >= baseY) return minMultiplier;
        if (y <= maxY) return maxMultiplier;
        // 在 baseY 和 maxY 之间进行线性插值
        float t = (float) ((baseY - y) / (baseY - maxY));
        return minMultiplier + (maxMultiplier - minMultiplier) * t;
    }
}
