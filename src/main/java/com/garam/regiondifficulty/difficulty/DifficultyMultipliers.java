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
 * Immutable snapshot of config-derived multiplier tables.
 * Rebuilt from Config values on config load and reload.
 */
@SuppressWarnings("removal")
public class DifficultyMultipliers {

    private final Map<String, Float> dimensionMultipliers;
    private final Map<String, Float> biomeMultipliers;
    private final Map<String, Float> structureMultipliers;
    private final float defaultMultiplier;
    private final float depthBaseY;
    private final float depthMaxY;
    private final float depthMaxMultiplier;
    private final float depthMinMultiplier;

    private DifficultyMultipliers(Map<String, Float> dimensionMultipliers,
                                  Map<String, Float> biomeMultipliers,
                                  Map<String, Float> structureMultipliers,
                                  float defaultMultiplier,
                                  float depthBaseY, float depthMaxY,
                                  float depthMaxMultiplier, float depthMinMultiplier) {
        this.dimensionMultipliers = Collections.unmodifiableMap(dimensionMultipliers);
        this.biomeMultipliers = Collections.unmodifiableMap(biomeMultipliers);
        this.structureMultipliers = Collections.unmodifiableMap(structureMultipliers);
        this.defaultMultiplier = defaultMultiplier;
        this.depthBaseY = depthBaseY;
        this.depthMaxY = depthMaxY;
        this.depthMaxMultiplier = depthMaxMultiplier;
        this.depthMinMultiplier = depthMinMultiplier;
    }

    // ========== Factory ==========

    /**
     * Build a DifficultyMultipliers instance from the current Config values.
     */
    public static DifficultyMultipliers fromConfig() {
        return new DifficultyMultipliers(
                parseEntryList(Config.dimensionMultipliers),
                parseEntryList(Config.biomeMultipliers),
                parseEntryList(Config.structureMultipliers),
                Config.defaultMultiplier,
                Config.depthBaseY,
                Config.depthMaxY,
                Config.depthMaxMultiplier,
                Config.depthMinMultiplier
        );
    }

    /**
     * Parse a list of "namespace:path=value" strings into a String->Float map.
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
                    // Skip malformed entries
                }
            }
        }
        return map;
    }

    // ========== Accessors ==========

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
     * Returns the set of all configured structure entries for iteration.
     */
    public Set<Map.Entry<String, Float>> getAllStructureEntries() {
        return structureMultipliers.entrySet();
    }

    public float getDefaultMultiplier() {
        return defaultMultiplier;
    }

    /**
     * Computes the depth multiplier from a Y coordinate using linear interpolation.
     * <ul>
     *   <li>Above baseY (surface) → minMultiplier</li>
     *   <li>Below maxY (deepest) → maxMultiplier</li>
     *   <li>Between → linear interpolation</li>
     * </ul>
     */
    public float getDepthMultiplier(double y) {
        if (y >= depthBaseY) return depthMinMultiplier;
        if (y <= depthMaxY) return depthMaxMultiplier;
        // Linear interpolation between baseY and maxY
        float t = (float) ((depthBaseY - y) / (depthBaseY - depthMaxY));
        return depthMinMultiplier + (depthMaxMultiplier - depthMinMultiplier) * t;
    }
}
