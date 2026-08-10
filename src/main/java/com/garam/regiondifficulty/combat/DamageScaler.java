package com.garam.regiondifficulty.combat;

/**
 * Pure computation utility for region-difficulty damage scaling.
 *
 * <p>Formula: {@code newDamage = baseDamage * clamp(1 + (multiplier - 1) * intensity, min, max)}</p>
 */
public final class DamageScaler {

    private DamageScaler() {} // utility class

    /**
     * Scale a damage amount by a regional difficulty multiplier.
     *
     * @param baseAmount the post-armor damage amount
     * @param regionMult the regional difficulty multiplier (1.0 = neutral)
     * @param intensity  config intensity weight (0.0 = no scaling, 1.0 = full)
     * @param clampMin   minimum allowed scale factor
     * @param clampMax   maximum allowed scale factor
     * @return the scaled damage amount
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
