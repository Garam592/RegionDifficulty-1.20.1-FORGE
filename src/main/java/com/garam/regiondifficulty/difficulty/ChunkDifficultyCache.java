package com.garam.regiondifficulty.difficulty;

import com.garam.regiondifficulty.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区域难度乘数非深度部分的线程安全缓存。
 *
 * <p>结构查找开销很大，因为每次查询都需要遍历所有配置的结构。
 * 此缓存以区块（维度 + chunkX + chunkZ）为键，存储（维度 × 生物群系 × 结构）部分结果，
 * 并采用基于TTL的过期机制。</p>
 *
 * <p>深度不会被缓存，因为在同一区块内Y坐标随查询而变化，
 * 但深度计算是简单的线性插值，时间复杂度为O(1)。</p>
 */
public class ChunkDifficultyCache {

    /** 紧凑的缓存键：维度 + 区块XZ + Y分段（右移4位，按16格分段）。 */
    private record CacheKey(String dimId, int chunkX, int chunkZ, int ySection) {}

    /** 缓存条目：非深度部分乘数 + 过期时间刻。 */
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
    private static final long CLEANUP_INTERVAL = 1200L; // 60秒（20 TPS下）

    /**
     * 从配置更新TTL。在配置重载时调用。
     * 当TTL发生变化时也会使所有条目失效。
     */
    public void configure(int ttlTicks) {
        if (this.ttlTicks != ttlTicks) {
            this.ttlTicks = ttlTicks;
            invalidateAll();
        }
    }

    /**
     * 获取缓存的（维度 × 生物群系 × 结构）部分乘数，或在未命中时计算并缓存。
     *
     * @param level       服务端世界
     * @param pos         查询位置（用于获取区块坐标和生物群系查找）
     * @param multipliers 当前配置快照
     * @return 部分乘数（维度 × 生物群系 × 结构），不包含深度
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
                pos.getY() >> 4   // 3D生物群系：按16格垂直分段
        );

        CacheEntry entry = cache.get(key);
        if (entry != null && currentTick < entry.expiryTick) {
            return entry.partialMultiplier;
        }

        // 缓存未命中或已过期 —— 计算并存储
        float partial = computeNonDepth(level, pos, multipliers);
        long expiry = currentTick + ttlTicks;
        cache.put(key, new CacheEntry(partial, expiry));

        // 延迟清理：定期清除过期条目
        if (currentTick - lastCleanupTick > CLEANUP_INTERVAL) {
            cleanup(currentTick);
            lastCleanupTick = currentTick;
        }

        return partial;
    }

    /**
     * 计算非深度部分：维度 × 生物群系 × 结构。
     */
    @SuppressWarnings("deprecation")
    private static float computeNonDepth(ServerLevelAccessor level, BlockPos pos,
                                          DifficultyMultipliers multipliers) {
        float dimMult = multipliers.getDimensionMultiplier(level.getLevel().dimension());
        float biomeMult = multipliers.getDefaultMultiplier();
        float structMult = 1.0F;

        // 生物群系
        var biomeKey = level.getBiome(pos).unwrapKey();
        if (biomeKey.isPresent()) {
            biomeMult = multipliers.getBiomeMultiplier(biomeKey.get());
        }

        // 结构 —— 遍历配置的结构并找到此位置的最大乘数
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
     * 移除所有已过期的条目。
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
     * 清空整个缓存。在配置重载或模组禁用时调用。
     */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * 返回当前缓存的条目数量（用于调试）。
     */
    public int size() {
        return cache.size();
    }
}
