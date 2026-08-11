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
 * 拦截生物生成完成事件，用区域增强的DifficultyInstance替换原版的DifficultyInstance，
 * 该增强实例会考虑生物群系、结构、维度和深度等因素。
 */
@Mod.EventBusSubscriber(modid = "region_difficulty", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DifficultyEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 线程安全的缓存乘数表，在配置加载/重载时刷新。 */
    private static volatile DifficultyMultipliers cachedMultipliers = null;

    /**
     * 强制从当前Config值刷新缓存的乘数表。
     * 在模组构造函数中由配置加载/重载事件调用。
     */
    public static synchronized void refreshMultipliers() {
        cachedMultipliers = DifficultyMultipliers.fromConfig();
        DifficultyCalculator.refreshCache();
        AttributeApplier.refreshExcludedMobs();
        SpawnControlHandler.refreshRules();
    }

    /**
     * 延迟初始化并返回当前的乘数表快照。
     * 设为public以便其他处理器（CombatScalingHandler、SpawnControlHandler）
     * 可以使用同一个缓存实例。
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
        // 和平或接近和平难度无需缩放
        if (original.getEffectiveDifficulty() <= 0.0F) return;

        DifficultyMultipliers multipliers = getMultipliersSnapshot();
        BlockPos pos = event.getEntity().blockPosition();

        float multiplier = DifficultyCalculator.calculateMultiplier(
                event.getLevel(), pos, multipliers);

        // 记录每次生成用于调试 —— 检查日志以验证流程是否正常工作
        LOGGER.debug("生成: {} 位置: {} -> 乘数={} (有效难度={} -> {})",
                event.getEntity().getName().getString(), pos,
                String.format("%.2f", multiplier),
                String.format("%.2f", original.getEffectiveDifficulty()),
                String.format("%.2f", original.getEffectiveDifficulty() * multiplier));

        // 无显著变化则跳过
        if (Math.abs(multiplier - 1.0F) < 0.0001F) return;

        // 获取世界参数用于公式重构
        Difficulty difficulty = original.getDifficulty();
        Level level = event.getLevel().getLevel();
        long dayTime = level.getDayTime();
        float moonPhase = level.getMoonBrightness();

        DifficultyInstance enhanced = RegionalDifficulty.create(
                original, multiplier, difficulty, dayTime, moonPhase);
        event.setDifficulty(enhanced);

        // 层级B：应用区域缩放的属性修改器（生命值、伤害、速度等）
        if (Config.spawnAttributesEnabled && event.getEntity() instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) event.getEntity();
            // 跳过玩家 —— 仅缩放生物属性
            if (!(living instanceof Player)) {
                AttributeApplier.apply(living, multiplier);
                if (LOGGER.isDebugEnabled()) {
                    var atkInst = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    String atkStr = atkInst != null ? String.format("%.1f", atkInst.getValue()) : "N/A";
                    LOGGER.debug("  -> 属性已应用: 最大生命值={}, 攻击力={}",
                            String.format("%.1f", living.getMaxHealth()), atkStr);
                }
            }
        }
    }
}
