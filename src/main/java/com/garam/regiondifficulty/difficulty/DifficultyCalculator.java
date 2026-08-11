package com.garam.regiondifficulty.difficulty;

import com.garam.regiondifficulty.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import org.slf4j.Logger;

/**
 * 中央计算器，将生物群系、结构、维度和深度倍率综合为
 * 适用于给定生成上下文的单一难度修正值。
 */
@SuppressWarnings("removal")
public class DifficultyCalculator {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 用于倍率中非深度部分的线程安全区块级缓存。 */
    private static final ChunkDifficultyCache CACHE = new ChunkDifficultyCache();

    /**
     * 根据当前 Config 值刷新缓存配置。
     * 在配置加载/重载时由 DifficultyEventHandler.refreshMultipliers() 调用。
     */
    public static void refreshCache() {
        CACHE.configure(Config.cacheTtlTicks);
    }

    /**
     * 使整个缓存失效。在显式禁用模组或卸载世界时调用。
     */
    public static void invalidateCache() {
        CACHE.invalidateAll();
    }

    /**
     * 计算生物生成事件对应的综合难度倍率。
     *
     * @param level       服务器世界（或世界生成区域）
     * @param pos         生成位置
     * @param multipliers 当前配置快照
     * @return 浮点倍率（1.0 = 不变，>1.0 = 更难，<1.0 = 更简单）
     */
    public static float calculateMultiplier(ServerLevelAccessor level,
                                            BlockPos pos,
                                            DifficultyMultipliers multipliers) {
        // 缓存保存 维度 × 生物群系 × 结构。深度计算代价低（线性插值），且随Y坐标变化。
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
