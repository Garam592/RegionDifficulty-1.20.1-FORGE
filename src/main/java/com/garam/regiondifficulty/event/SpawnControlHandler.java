package com.garam.regiondifficulty.event;

import com.garam.regiondifficulty.Config;
import com.garam.regiondifficulty.difficulty.DifficultyCalculator;
import com.garam.regiondifficulty.difficulty.DifficultyMultipliers;
import com.garam.regiondifficulty.spawn.SpawnGateRules;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Controls which mob types can spawn based on regional difficulty multiplier.
 *
 * <p>Hooks {@link MobSpawnEvent.PositionCheck} to deny spawns when the regional
 * difficulty multiplier at the spawn position falls outside the configured range
 * for that mob type.</p>
 *
 * <p>This is an experimental feature, disabled by default
 * ({@code regionalDifficulty.spawnControl.enabled = false}).</p>
 */
@Mod.EventBusSubscriber(modid = "region_difficulty", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpawnControlHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Thread-safe cached rules snapshot, refreshed on config load/reload. */
    private static volatile SpawnGateRules cachedRules = null;

    /**
     * Force a refresh of the cached rules from current Config values.
     */
    public static synchronized void refreshRules() {
        cachedRules = SpawnGateRules.fromConfig();
        LOGGER.debug("SpawnControlHandler: reloaded rules ({} entries)", cachedRules.size());
    }

    private static SpawnGateRules getRules() {
        SpawnGateRules r = cachedRules;
        if (r == null) {
            synchronized (SpawnControlHandler.class) {
                r = cachedRules;
                if (r == null) {
                    r = SpawnGateRules.fromConfig();
                    cachedRules = r;
                }
            }
        }
        return r;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!Config.spawnControlEnabled) return;

        // Only gate natural spawns; let spawners, structures, and commands through
        // (PositionCheck fires for all spawn types)
        if (!(event.getEntity() instanceof Mob)) return;

        SpawnGateRules rules = getRules();

        // Fast path: defaultAllow=true with no rules → skip
        if (Config.spawnControlDefaultAllow && rules.size() == 0) return;

        // Compute regional multiplier at the attempted spawn position
        ServerLevelAccessor serverLevel = event.getLevel();
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(
                event.getX(), event.getY(), event.getZ());

        DifficultyMultipliers multipliers = DifficultyEventHandler.getMultipliersSnapshot();
        if (multipliers == null) return;

        float multiplier = DifficultyCalculator.calculateMultiplier(
                serverLevel, pos, multipliers);

        if (!rules.isSpawnAllowed(event.getEntity().getType(), multiplier)) {
            event.setResult(Event.Result.DENY);
            LOGGER.debug("SpawnControlHandler: denied spawn of {} at {} (mult={})",
                    event.getEntity().getType().getDescriptionId(), pos, multiplier);
        }
    }
}
