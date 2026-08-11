package com.garam.regiondifficulty.combat;

/**
 * 区域难度伤害缩放的纯计算工具类。
 *
 * <p>公式：{@code newDamage = baseDamage * clamp(1 + (multiplier - 1) * intensity, min, max)}</p>
 */
public final class DamageScaler {

    private DamageScaler() {} // 工具类

    /**
     * 根据区域难度倍率缩放伤害值。
     *
     * @param baseAmount 护甲减伤后的基础伤害值
     * @param regionMult 区域难度倍率（1.0 = 不变）
     * @param intensity  配置中的强度权重（0.0 = 不缩放，1.0 = 满缩放）
     * @param clampMin   允许的最小缩放系数
     * @param clampMax   允许的最大缩放系数
     * @return 缩放后的伤害值
     */
    public static float scale(float baseAmount, float regionMult,
                               double intensity, double clampMin, double clampMax) {
        if (baseAmount <= 0.0F) return baseAmount;
        if (intensity <= 0.0) return baseAmount;
        if (Math.abs(regionMult - 1.0F) < 0.0001F) return baseAmount;

        double scaleFactor = 1.0 + (regionMult - 1.0) * intensity;
        scaleFactor = Math.max(clampMin, Math.min(clampMax, scaleFactor));

        return (float) (baseAmount * scaleFactor);
    }
}
