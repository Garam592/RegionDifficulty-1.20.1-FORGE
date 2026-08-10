package com.garam.regiondifficulty.event;

import com.garam.regiondifficulty.Config;
import com.garam.regiondifficulty.combat.DamageScaler;
import com.garam.regiondifficulty.difficulty.DifficultyCalculator;
import com.garam.regiondifficulty.difficulty.DifficultyMultipliers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Runtime combat damage scaling based on regional difficulty.
 *
 * <p>Hooks {@link LivingHurtEvent} at LOW priority (post-armor calculation) to scale
 * the final damage amount based on the regional difficulty multiplier at the relevant
 * entity's position.</p>
 *
 * <p>Two directions (independently configurable):</p>
 * <ul>
 *   <li><b>Damage TO player</b>: scaled by the attacker's regional difficulty.
 *       Monsters from hard regions hit harder.</li>
 *   <li><b>Damage BY player</b>: scaled by the target's regional difficulty.
 *       Monsters in hard regions are tougher (reduced player damage).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "region_difficulty", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CombatScalingHandler {

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!Config.combatScalingEnabled) return;
        if (event.getAmount() <= 0.0F) return;

        // Damage TO player: scale by attacker's regional difficulty
        if (Config.combatToPlayerEnabled && event.getEntity() instanceof Player) {
            if (event.getSource().getEntity() instanceof LivingEntity) {
                LivingEntity attacker = (LivingEntity) event.getSource().getEntity();
                if (!(attacker instanceof Player)) {
                    float multiplier = computeMultiplierAt(attacker);
                    float scaled = DamageScaler.scale(
                            event.getAmount(), multiplier,
                            Config.combatToPlayerIntensity,
                            Config.combatToPlayerClampMin,
                            Config.combatToPlayerClampMax
                    );
                    event.setAmount(scaled);
                    return;
                }
            }
        }

        // Damage BY player: scale by target's regional difficulty
        if (Config.combatByPlayerEnabled
                && event.getSource().getEntity() instanceof Player
                && event.getEntity() instanceof LivingEntity) {
            LivingEntity victim = (LivingEntity) event.getEntity();
            if (!(victim instanceof Player)) {
                float multiplier = computeMultiplierAt(victim);
                float scaled = DamageScaler.scale(
                        event.getAmount(), multiplier,
                        Config.combatByPlayerIntensity,
                        Config.combatByPlayerClampMin,
                        Config.combatByPlayerClampMax
                );
                event.setAmount(scaled);
            }
        }
    }

    /**
     * Compute the regional difficulty multiplier at the entity's current position.
     */
    private static float computeMultiplierAt(LivingEntity entity) {
        if (!(entity.level() instanceof net.minecraft.world.level.ServerLevelAccessor serverLevel)) {
            return 1.0F;
        }

        DifficultyMultipliers multipliers = DifficultyEventHandler.getMultipliersSnapshot();
        if (multipliers == null) return 1.0F;

        return DifficultyCalculator.calculateMultiplier(
                serverLevel, entity.blockPosition(), multipliers);
    }
}
