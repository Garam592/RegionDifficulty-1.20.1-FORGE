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
 * 根据区域难度乘数控制哪些生物类型可以生成。
 *
 * <p>挂钩{@link MobSpawnEvent.PositionCheck}，当生成位置的区域难度乘数
 * 超出该生物类型的配置范围时，拒绝生成。</p>
 *
 * <p>这是一个实验性功能，默认禁用
 * ({@code regionalDifficulty.spawnControl.enabled = false})。</p>
 */
@Mod.EventBusSubscriber(modid = "region_difficulty", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpawnControlHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 线程安全的缓存规则快照，在配置加载/重载时刷新。 */
    private static volatile SpawnGateRules cachedRules = null;

    /**
     * 强制从当前Config值刷新缓存的规则。
     */
    public static synchronized void refreshRules() {
        cachedRules = SpawnGateRules.fromConfig();
        LOGGER.debug("SpawnControlHandler: 已重载规则（{}条）", cachedRules.size());
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

        // 仅限制自然生成；放行刷怪笼、结构和命令生成的生物
        // （PositionCheck对所有生成类型均会触发）
        if (!(event.getEntity() instanceof Mob)) return;

        SpawnGateRules rules = getRules();

        // 快速路径：defaultAllow=true且无规则时 → 跳过
        if (Config.spawnControlDefaultAllow && rules.size() == 0) return;

        // 计算预计生成位置的区域难度乘数
        ServerLevelAccessor serverLevel = event.getLevel();
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(
                event.getX(), event.getY(), event.getZ());

        DifficultyMultipliers multipliers = DifficultyEventHandler.getMultipliersSnapshot();
        if (multipliers == null) return;

        float multiplier = DifficultyCalculator.calculateMultiplier(
                serverLevel, pos, multipliers);

        if (!rules.isSpawnAllowed(event.getEntity().getType(), multiplier)) {
            event.setResult(Event.Result.DENY);
            LOGGER.debug("SpawnControlHandler: 拒绝 {} 在 {} 处生成（乘数={}）",
                    event.getEntity().getType().getDescriptionId(), pos, multiplier);
        }
    }
}
