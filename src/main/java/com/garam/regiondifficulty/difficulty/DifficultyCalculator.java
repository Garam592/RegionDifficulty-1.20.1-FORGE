package com.garam.regiondifficulty.difficulty;

import com.garam.regiondifficulty.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import org.slf4j.Logger;

/**
 * Central calculator that combines biome, structure, dimension, and depth
 * multipliers into a single difficulty modifier for a given spawn context.
 */
@SuppressWarnings("removal")
public class DifficultyCalculator {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Thread-safe chunk-level cache for the non-depth portion of the multiplier. */
    private static final ChunkDifficultyCache CACHE = new ChunkDifficultyCache();

    /**
     * Refresh the cache configuration from current Config values.
     * Called from DifficultyEventHandler.refreshMultipliers() on config load/reload.
     */
    public static void refreshCache() {
        CACHE.configure(Config.cacheTtlTicks);
    }

    /**
     * Invalidate the entire cache. Call on explicit mod disable or world unload.
     */
    public static void invalidateCache() {
        CACHE.invalidateAll();
    }

    /**
     * Compute the combined difficulty multiplier for a mob spawn event.
     *
     * @param level       the server level (or world-gen region)
     * @param pos         the spawn position
     * @param multipliers the current config snapshot
     * @return a float multiplier (1.0 = no change, >1.0 = harder, <1.0 = easier)
     */
    public static float calculateMultiplier(ServerLevelAccessor level,
                                            BlockPos pos,
                                            DifficultyMultipliers multipliers) {
        // Cache holds dim × biome × struct. Depth is cheap (lerp) and varies by Y.
        float nonDepth = CACHE.getOrCompute(level, pos, multipliers);
        float depthMult = multipliers.getDepthMultiplier(pos.getY());
        float combined = nonDepth * depthMult;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("RegionalDifficulty at {}: nonDepth={}, depth={} => {}",
                    pos, nonDepth, depthMult, combined);
        }

        return combined;
    }
}
