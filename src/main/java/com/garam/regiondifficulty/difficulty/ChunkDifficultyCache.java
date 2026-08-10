package com.garam.regiondifficulty.difficulty;

import com.garam.regiondifficulty.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for the non-depth portion of the regional difficulty multiplier.
 *
 * <p>Structure lookups are expensive because they iterate over ALL configured structures
 * for each query. This cache stores the (dim × biome × struct) partial result keyed by
 * chunk (dimension + chunkX + chunkZ), with TTL-based expiry.</p>
 *
 * <p>Depth is NOT cached because Y varies per query within a chunk, but depth
 * calculation is a simple lerp that is O(1).</p>
 */
public class ChunkDifficultyCache {

    /** Compact cache key: dimension + chunk XZ + Y-section (4-bit shift to bin by 16 blocks). */
    private record CacheKey(String dimId, int chunkX, int chunkZ, int ySection) {}

    /** Cached entry: non-depth partial multiplier + expiry tick. */
    private static class CacheEntry {
        final float partialMultiplier;
        final long expiryTick;

        CacheEntry(float partialMultiplier, long expiryTick) {
            this.partialMultiplier = partialMultiplier;
            this.expiryTick = expiryTick;
        }
    }

    private final ConcurrentHashMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    private volatile int ttlTicks;
    private volatile long lastCleanupTick = 0L;
    private static final long CLEANUP_INTERVAL = 1200L; // 60 seconds at 20 TPS

    /**
     * Updates the TTL from config. Called on config reload.
     * Also invalidates all entries when TTL changes.
     */
    public void configure(int ttlTicks) {
        if (this.ttlTicks != ttlTicks) {
            this.ttlTicks = ttlTicks;
            invalidateAll();
        }
    }

    /**
     * Get the cached partial multiplier (dim × biome × struct), or compute and cache it.
     *
     * @param level       the server level
     * @param pos         the query position (used for chunk coords and biome lookup)
     * @param multipliers the current config snapshot
     * @return partial multiplier (dim × biome × struct), excluding depth
     */
    public float getOrCompute(ServerLevelAccessor level, BlockPos pos,
                               DifficultyMultipliers multipliers) {
        if (!Config.cacheEnabled || ttlTicks <= 0) {
            return computeNonDepth(level, pos, multipliers);
        }

        long currentTick = level.getLevel().getGameTime();
        CacheKey key = new CacheKey(
                level.getLevel().dimension().location().toString(),
                pos.getX() >> 4,
                pos.getZ() >> 4,
                pos.getY() >> 4   // 3D biomes: bin by 16-block vertical sections
        );

        CacheEntry entry = cache.get(key);
        if (entry != null && currentTick < entry.expiryTick) {
            return entry.partialMultiplier;
        }

        // Cache miss or expired — compute and store
        float partial = computeNonDepth(level, pos, multipliers);
        long expiry = currentTick + ttlTicks;
        cache.put(key, new CacheEntry(partial, expiry));

        // Lazy cleanup: sweep expired entries periodically
        if (currentTick - lastCleanupTick > CLEANUP_INTERVAL) {
            cleanup(currentTick);
            lastCleanupTick = currentTick;
        }

        return partial;
    }

    /**
     * Compute the non-depth portion: dim × biome × struct.
     */
    @SuppressWarnings("deprecation")
    private static float computeNonDepth(ServerLevelAccessor level, BlockPos pos,
                                          DifficultyMultipliers multipliers) {
        float dimMult = multipliers.getDimensionMultiplier(level.getLevel().dimension());
        float biomeMult = multipliers.getDefaultMultiplier();
        float structMult = 1.0F;

        // Biome
        var biomeKey = level.getBiome(pos).unwrapKey();
        if (biomeKey.isPresent()) {
            biomeMult = multipliers.getBiomeMultiplier(biomeKey.get());
        }

        // Structure — iterate configured structures and find max multiplier at this position
        for (Map.Entry<String, Float> entry : multipliers.getAllStructureEntries()) {
            String key = entry.getKey();
            String[] idParts = key.split(":", 2);
            if (idParts.length != 2) continue;

            var structureKey = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.STRUCTURE,
                    new net.minecraft.resources.ResourceLocation(idParts[0], idParts[1]));

            var start = level.getLevel().structureManager()
                    .getStructureWithPieceAt(pos, structureKey);

            if (start.isValid() && entry.getValue() > structMult) {
                structMult = entry.getValue();
            }
        }

        return dimMult * biomeMult * structMult;
    }

    /**
     * Remove all entries whose expiry tick has passed.
     */
    public void cleanup(long currentTick) {
        Iterator<Map.Entry<CacheKey, CacheEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<CacheKey, CacheEntry> entry = it.next();
            if (currentTick >= entry.getValue().expiryTick) {
                it.remove();
            }
        }
    }

    /**
     * Clear the entire cache. Call on config reload or mod disable.
     */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * Returns the number of currently cached entries (for debugging).
     */
    public int size() {
        return cache.size();
    }
}
