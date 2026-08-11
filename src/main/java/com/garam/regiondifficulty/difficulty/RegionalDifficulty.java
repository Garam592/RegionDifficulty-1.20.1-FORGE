package com.garam.regiondifficulty.difficulty;

import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;

/**
 * 用于创建具有修改后有效难度的 {@link DifficultyInstance} 的工具类，
 * 无需使用 Access Transformer。
 *
 * <p>技术原理：由于 {@code DifficultyInstance} 是不可变的，其有效难度由
 * {@code (Difficulty, dayTime, chunkInhabitedTime, moonPhase)} 计算得出，我们逆向推导
 * 原版公式，找到能产生目标有效难度的 {@code chunkInhabitedTime}，
 * 然后用该值构造一个新的实例。</p>
 */
public final class RegionalDifficulty {

    private RegionalDifficulty() {} // 工具类

    /** 原版公式中使用的最大区块驻留时间（3,600,000 刻 = 50 游戏日）。 */
    private static final float MAX_CHUNK_TIME = 3_600_000.0F;

    /** 原版全局时间偏移常量。 */
    private static final float TIME_OFFSET = -72_000.0F;

    /** 原版全局时间最大值常量。 */
    private static final float MAX_TIME_FACTOR = 1_440_000.0F;

    /**
     * 创建一个 DifficultyInstance，其有效难度等于
     * {@code original.getEffectiveDifficulty() * multiplier}。
     *
     * @param original   本次生成的原版 DifficultyInstance
     * @param multiplier 综合区域难度倍率
     * @param difficulty 世界难度设置
     * @param dayTime    当前世界日间时间（来自 {@code ServerLevel.getDayTime()}）
     * @param moonPhase  当前月相亮度（来自 {@code Level.getMoonBrightness()}）
     * @return 具有调整后有效难度的新 DifficultyInstance
     */
    public static DifficultyInstance create(DifficultyInstance original,
                                            float multiplier,
                                            Difficulty difficulty,
                                            long dayTime,
                                            float moonPhase) {
        if (difficulty == Difficulty.PEACEFUL || multiplier <= 0.0F) {
            return original;
        }

        float targetEffective = original.getEffectiveDifficulty() * multiplier;
        // 限制在安全范围内
        targetEffective = Math.max(0.0F, Math.min(10.0F, targetEffective));

        // 计算原版公式中的固定分量
        float f1 = Mth.clamp((dayTime + TIME_OFFSET) / MAX_TIME_FACTOR, 0.0F, 1.0F) * 0.25F;
        float hardFactor = difficulty == Difficulty.HARD ? 1.0F : 0.75F;
        float moonFactor = Mth.clamp(moonPhase * 0.25F, 0.0F, f1);

        // 由：targetEffective = diffId * (0.75 + f1 + chunkFactor + moonFactor)
        // （EASY 难度下 local*0.5）
        // 因此：chunkFactor = targetEffective / diffId - 0.75 - f1 - moonFactor
        float localTarget = targetEffective / (float) difficulty.getId() - 0.75F - f1;

        // 撤销 EASY 难度的折半处理
        if (difficulty == Difficulty.EASY) {
            localTarget /= 0.5F;
        }

        float chunkFactor = localTarget - moonFactor;

        // 将 chunkFactor 转换为 chunkInhabitedTime。
        // chunkFactor = clamp(chunkTime / MAX_CHUNK_TIME, 0, 1) * hardFactor
        // 因此：normalised = chunkFactor / hardFactor，然后 chunkTime = normalised * MAX_CHUNK_TIME
        float normalised = Mth.clamp(chunkFactor / hardFactor, 0.0F, 1.0F);
        long newChunkTime = (long) (normalised * MAX_CHUNK_TIME);

        return new DifficultyInstance(difficulty, dayTime, newChunkTime, moonPhase);
    }
}
