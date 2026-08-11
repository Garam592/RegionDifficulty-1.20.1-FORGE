package com.garam.regiondifficulty.spawn;

import com.garam.regiondifficulty.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析并存储生成门控规则：哪些生物类型在哪些区域难度倍率范围内允许生成。
 *
 * <p>配置格式：{@code "namespace:path=min,max"}，其中 min 和/或 max 可以为空。
 * 示例：</p>
 * <ul>
 *   <li>{@code "minecraft:wither_skeleton=2.0,"} — 需要倍率 ≥ 2.0</li>
 *   <li>{@code "minecraft:bat=,0.5"} — 仅在倍率 ≤ 0.5 时允许</li>
 *   <li>{@code "minecraft:creeper=1.5,3.0"} — 需要 1.5 ≤ 倍率 ≤ 3.0</li>
 * </ul>
 */
@SuppressWarnings("deprecation")
public class SpawnGateRules {

    /** 数值范围规则：[minMultiplier, maxMultiplier] 闭区间。 */
    public record SpawnRule(float minMultiplier, float maxMultiplier) {
        public SpawnRule {
            if (minMultiplier < 0.0F) minMultiplier = 0.0F;
            if (maxMultiplier < 0.0F) maxMultiplier = Float.MAX_VALUE;
            if (maxMultiplier < minMultiplier) maxMultiplier = minMultiplier;
        }

        public boolean allows(float multiplier) {
            return multiplier >= minMultiplier && multiplier <= maxMultiplier;
        }
    }

    private final Map<EntityType<?>, SpawnRule> rules;
    private final boolean defaultAllow;

    private SpawnGateRules(Map<EntityType<?>, SpawnRule> rules, boolean defaultAllow) {
        this.rules = Collections.unmodifiableMap(rules);
        this.defaultAllow = defaultAllow;
    }

    /**
     * 根据当前配置值构建 SpawnGateRules 实例。
     */
    public static SpawnGateRules fromConfig() {
        Map<EntityType<?>, SpawnRule> map = new HashMap<>();
        List<? extends String> entries = Config.spawnControlRules;
        if (entries != null) {
            for (String entry : entries) {
                parseEntry(entry, map);
            }
        }
        return new SpawnGateRules(map, Config.spawnControlDefaultAllow);
    }

    /**
     * 检查某个生物类型在给定的区域倍率下是否允许生成。
     */
    public boolean isSpawnAllowed(EntityType<?> type, float multiplier) {
        SpawnRule rule = rules.get(type);
        if (rule != null) {
            return rule.allows(multiplier);
        }
        return defaultAllow;
    }

    /**
     * 解析单条 "namespace:path=min,max" 条目并将其添加到映射中。
     */
    private static void parseEntry(String entry, Map<EntityType<?>, SpawnRule> map) {
        String[] parts = entry.split("=", 2);
        if (parts.length != 2) return;

        String[] idParts = parts[0].split(":", 2);
        if (idParts.length != 2) return;

        ResourceLocation id;
        try {
            id = new ResourceLocation(idParts[0], idParts[1]);
        } catch (Exception e) {
            return;
        }

        String range = parts[1].trim();
        if (range.isEmpty()) return;

        String[] rangeParts = range.split(",", 2);

        float min = 0.0F;
        float max = Float.MAX_VALUE;

        if (rangeParts.length >= 1 && !rangeParts[0].isEmpty()) {
            try {
                min = Float.parseFloat(rangeParts[0]);
            } catch (NumberFormatException e) {
                return;
            }
        }
        if (rangeParts.length == 2 && !rangeParts[1].isEmpty()) {
            try {
                max = Float.parseFloat(rangeParts[1]);
            } catch (NumberFormatException e) {
                return;
            }
        }

        var typeOpt = EntityType.byString(id.toString());
        if (typeOpt.isPresent()) {
            map.put(typeOpt.get(), new SpawnRule(min, max));
        }
    }

    /**
     * 当前已加载的规则数量（用于调试）。
     */
    public int size() {
        return rules.size();
    }
}
