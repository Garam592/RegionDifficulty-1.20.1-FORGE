package com.garam.regiondifficulty.spawn;

import com.garam.regiondifficulty.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses and stores spawn-gating rules: which mob types are allowed in which
 * regional difficulty multiplier ranges.
 *
 * <p>Config format: {@code "namespace:path=min,max"} where min and/or max can be empty.
 * Examples:</p>
 * <ul>
 *   <li>{@code "minecraft:wither_skeleton=2.0,"} — requires multiplier ≥ 2.0</li>
 *   <li>{@code "minecraft:bat=,0.5"} — only allowed at multiplier ≤ 0.5</li>
 *   <li>{@code "minecraft:creeper=1.5,3.0"} — requires 1.5 ≤ multiplier ≤ 3.0</li>
 * </ul>
 */
@SuppressWarnings("deprecation")
public class SpawnGateRules {

    /** A numeric range rule: [minMultiplier, maxMultiplier] inclusive. */
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
     * Build a SpawnGateRules instance from the current Config values.
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
     * Check whether a mob type is allowed to spawn at the given regional multiplier.
     */
    public boolean isSpawnAllowed(EntityType<?> type, float multiplier) {
        SpawnRule rule = rules.get(type);
        if (rule != null) {
            return rule.allows(multiplier);
        }
        return defaultAllow;
    }

    /**
     * Parse a single "namespace:path=min,max" entry and add it to the map.
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
     * Number of rules currently loaded (for debugging).
     */
    public int size() {
        return rules.size();
    }
}
