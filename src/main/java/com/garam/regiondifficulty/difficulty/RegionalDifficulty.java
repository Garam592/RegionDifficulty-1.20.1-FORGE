package com.garam.regiondifficulty.difficulty;

import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;

/**
 * Utility that creates a new {@link DifficultyInstance} with a modified effective difficulty,
 * without needing an Access Transformer.
 *
 * <p>The technique: since {@code DifficultyInstance} is immutable and its effective difficulty
 * is computed from {@code (Difficulty, dayTime, chunkInhabitedTime, moonPhase)}, we reverse the
 * vanilla formula to find the {@code chunkInhabitedTime} that would produce the target effective
 * difficulty, then construct a new instance with that value.</p>
 */
public final class RegionalDifficulty {

    private RegionalDifficulty() {} // utility class

    /** Max chunk inhabited time used in vanilla formula (3,600,000 ticks = 50 in-game days). */
    private static final float MAX_CHUNK_TIME = 3_600_000.0F;

    /** The vanilla global-time offset constant. */
    private static final float TIME_OFFSET = -72_000.0F;

    /** The vanilla global-time max constant. */
    private static final float MAX_TIME_FACTOR = 1_440_000.0F;

    /**
     * Creates a DifficultyInstance whose effective difficulty equals
     * {@code original.getEffectiveDifficulty() * multiplier}.
     *
     * @param original   the vanilla DifficultyInstance for this spawn
     * @param multiplier the combined regional difficulty multiplier
     * @param difficulty the world difficulty setting
     * @param dayTime    the current world day time (from {@code ServerLevel.getDayTime()})
     * @param moonPhase  the current moon brightness (from {@code Level.getMoonBrightness()})
     * @return a new DifficultyInstance with adjusted effective difficulty
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
        // Clamp to safe range
        targetEffective = Math.max(0.0F, Math.min(10.0F, targetEffective));

        // Calculate the fixed components of the vanilla formula
        float f1 = Mth.clamp((dayTime + TIME_OFFSET) / MAX_TIME_FACTOR, 0.0F, 1.0F) * 0.25F;
        float hardFactor = difficulty == Difficulty.HARD ? 1.0F : 0.75F;
        float moonFactor = Mth.clamp(moonPhase * 0.25F, 0.0F, f1);

        // From: targetEffective = diffId * (0.75 + f1 + chunkFactor + moonFactor)
        // (with EASY having local*0.5)
        // So: chunkFactor = targetEffective / diffId - 0.75 - f1 - moonFactor
        float localTarget = targetEffective / (float) difficulty.getId() - 0.75F - f1;

        // Undo the EASY halving
        if (difficulty == Difficulty.EASY) {
            localTarget /= 0.5F;
        }

        float chunkFactor = localTarget - moonFactor;

        // Convert chunkFactor to chunkInhabitedTime.
        // chunkFactor = clamp(chunkTime / MAX_CHUNK_TIME, 0, 1) * hardFactor
        // So: normalised = chunkFactor / hardFactor, then chunkTime = normalised * MAX_CHUNK_TIME
        float normalised = Mth.clamp(chunkFactor / hardFactor, 0.0F, 1.0F);
        long newChunkTime = (long) (normalised * MAX_CHUNK_TIME);

        return new DifficultyInstance(difficulty, dayTime, newChunkTime, moonPhase);
    }
}
