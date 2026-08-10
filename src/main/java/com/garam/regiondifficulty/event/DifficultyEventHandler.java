package com.garam.regiondifficulty.event;

import com.garam.regiondifficulty.Config;
import com.garam.regiondifficulty.attribute.AttributeApplier;
import com.garam.regiondifficulty.difficulty.DifficultyCalculator;
import com.garam.regiondifficulty.difficulty.DifficultyMultipliers;
import com.garam.regiondifficulty.difficulty.RegionalDifficulty;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Intercepts mob spawn finalization to replace the vanilla DifficultyInstance
 * with a regionally-enhanced one that accounts for biome, structure, dimension, and depth.
 */
@Mod.EventBusSubscriber(modid = "region_difficulty", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DifficultyEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Thread-safe cached multiplier table, refreshed on config load/reload. */
    private static volatile DifficultyMultipliers cachedMultipliers = null;

    /**
     * Force a refresh of the cached multiplier table from current Config values.
     * Called from the mod constructor on config load/reload events.
     */
    public static synchronized void refreshMultipliers() {
        cachedMultipliers = DifficultyMultipliers.fromConfig();
        DifficultyCalculator.refreshCache();
        AttributeApplier.refreshExcludedMobs();
        SpawnControlHandler.refreshRules();
    }

    /**
     * Lazily initializes and returns the current multiplier table snapshot.
     * Public so that other handlers (CombatScalingHandler, SpawnControlHandler)
     * can use the same cached instance.
     */
    public static DifficultyMultipliers getMultipliersSnapshot() {
        DifficultyMultipliers m = cachedMultipliers;
        if (m == null) {
            synchronized (DifficultyEventHandler.class) {
                m = cachedMultipliers;
                if (m == null) {
                    m = DifficultyMultipliers.fromConfig();
                    cachedMultipliers = m;
                }
            }
        }
        return m;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!Config.enableRegionalDifficulty) return;

        DifficultyInstance original = event.getDifficulty();
        if (original == null) return;
        // No scaling needed for peaceful or near-peaceful difficulty
        if (original.getEffectiveDifficulty() <= 0.0F) return;

        DifficultyMultipliers multipliers = getMultipliersSnapshot();
        BlockPos pos = event.getEntity().blockPosition();

        float multiplier = DifficultyCalculator.calculateMultiplier(
                event.getLevel(), pos, multipliers);

        // Log every spawn for debugging — check logs to verify the pipeline works
        LOGGER.debug("Spawn: {} at {} -> mult={} (eff={} -> {})",
                event.getEntity().getName().getString(), pos,
                String.format("%.2f", multiplier),
                String.format("%.2f", original.getEffectiveDifficulty()),
                String.format("%.2f", original.getEffectiveDifficulty() * multiplier));

        // Skip if no meaningful change
        if (Math.abs(multiplier - 1.0F) < 0.0001F) return;

        // Get world parameters for the formula reconstruction
        Difficulty difficulty = original.getDifficulty();
        Level level = event.getLevel().getLevel();
        long dayTime = level.getDayTime();
        float moonPhase = level.getMoonBrightness();

        DifficultyInstance enhanced = RegionalDifficulty.create(
                original, multiplier, difficulty, dayTime, moonPhase);
        event.setDifficulty(enhanced);

        // Layer B: Apply region-scaled attribute modifiers (health, damage, speed, etc.)
        if (Config.spawnAttributesEnabled && event.getEntity() instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) event.getEntity();
            // Skip players — only scale mob attributes
            if (!(living instanceof Player)) {
                AttributeApplier.apply(living, multiplier);
                if (LOGGER.isDebugEnabled()) {
                    var atkInst = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    String atkStr = atkInst != null ? String.format("%.1f", atkInst.getValue()) : "N/A";
                    LOGGER.debug("  -> Attrs applied: maxHealth={}, atk={}",
                            String.format("%.1f", living.getMaxHealth()), atkStr);
                }
            }
        }
    }
}
