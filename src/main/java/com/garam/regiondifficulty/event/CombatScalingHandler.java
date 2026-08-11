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
 * 基于区域难度的运行时战斗伤害缩放。
 *
 * <p>在{@link LivingHurtEvent}的LOW优先级（护甲计算之后）挂钩，
 * 根据相关实体所在位置的区域难度乘数来缩放最终伤害值。</p>
 *
 * <p>两个方向（可独立配置）：</p>
 * <ul>
 *   <li><b>对玩家造成的伤害</b>：按攻击者所在区域的难度进行缩放。
 *       高难度区域的怪物造成更高伤害。</li>
 *   <li><b>玩家造成的伤害</b>：按目标所在区域的难度进行缩放。
 *       高难度区域的怪物更耐打（降低玩家造成的伤害）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "region_difficulty", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CombatScalingHandler {

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!Config.combatScalingEnabled) return;
        if (event.getAmount() <= 0.0F) return;

        // 对玩家造成的伤害：按攻击者所在区域的难度进行缩放
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

        // 玩家造成的伤害：按目标所在区域的难度进行缩放
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
     * 计算实体当前位置的区域难度乘数。
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
