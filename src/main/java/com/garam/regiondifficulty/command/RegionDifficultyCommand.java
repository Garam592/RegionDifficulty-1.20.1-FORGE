package com.garam.regiondifficulty.command;

import com.garam.regiondifficulty.Config;
import com.garam.regiondifficulty.difficulty.DifficultyCalculator;
import com.garam.regiondifficulty.difficulty.DifficultyMultipliers;
import com.garam.regiondifficulty.event.DifficultyEventHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * 调试命令：{@code /regiondifficulty check} 打印执行玩家所在位置的区域难度倍率，
 * 并按各个因子细分展示。
 */
@Mod.EventBusSubscriber(modid = "region_difficulty")
public class RegionDifficultyCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("regiondifficulty")
                        .requires(src -> src.hasPermission(2)) // 操作员权限等级 2
                        .then(Commands.literal("check")
                                .executes(RegionDifficultyCommand::executeCheck))
                        .then(Commands.literal("reload")
                                .executes(RegionDifficultyCommand::executeReload))
        );
    }

    @SuppressWarnings("deprecation")
    private static int executeCheck(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getLevel() instanceof ServerLevel)) {
            source.sendFailure(Component.literal("此命令只能在服务器维度中使用。"));
            return 0;
        }
        ServerLevel serverLevel = (ServerLevel) source.getLevel();

        BlockPos pos = BlockPos.containing(source.getPosition());
        DifficultyMultipliers multipliers = DifficultyEventHandler.getMultipliersSnapshot();

        if (multipliers == null) {
            source.sendFailure(Component.literal("难度倍率尚未加载。"));
            return 0;
        }

        // 完整倍率
        float fullMult = DifficultyCalculator.calculateMultiplier(serverLevel, pos, multipliers);

        // 各因子细分
        ResourceKey<Level> dimKey = serverLevel.dimension();
        float dimMult = multipliers.getDimensionMultiplier(dimKey);

        Optional<ResourceKey<Biome>> biomeKey = serverLevel.getBiome(pos).unwrapKey();
        float biomeMult = biomeKey.map(multipliers::getBiomeMultiplier)
                .orElse(multipliers.getDefaultMultiplier());

        float depthMult = multipliers.getDepthMultiplier(pos.getY());

        // 结构倍率 — 单独计算，因为缓存会将它们合并
        float structMult = getStructureMultiplier(serverLevel, pos, multipliers);

        String posStr = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        String dimStr = dimKey.location().toString();
        String biomeStr = biomeKey.map(k -> k.location().toString()).orElse("unknown");
        String dimMultStr = fmt(dimMult);
        String biomeMultStr = fmt(biomeMult);
        String structMultStr = fmt(structMult);
        String depthMultStr = fmt(depthMult);
        String fullMultStr = fmt(fullMult);
        String configStr = "全局=" + (Config.enableRegionalDifficulty ? "开" : "关")
                + " 生成属性=" + (Config.spawnAttributesEnabled ? "开" : "关")
                + " 战斗=" + (Config.combatScalingEnabled ? "开" : "关")
                + " 生成控制=" + (Config.spawnControlEnabled ? "开" : "关");
        String cacheStr = "缓存: " + (Config.cacheEnabled ? "开" : "关")
                + " ttl=" + Config.cacheTtlTicks + "t";

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§6===== 区域难度检查 ====="), false);
        source.sendSuccess(() -> Component.literal("§e位置: §f" + posStr), false);
        source.sendSuccess(() -> Component.literal("§e维度: §f" + dimStr + " §7(倍率: " + dimMultStr + ")"), false);
        source.sendSuccess(() -> Component.literal("§e生物群系: §f" + biomeStr + " §7(倍率: " + biomeMultStr + ")"), false);
        source.sendSuccess(() -> Component.literal("§e结构: §7(倍率: " + structMultStr + ")"), false);
        source.sendSuccess(() -> Component.literal("§e深度 (Y=" + pos.getY() + "): §7(倍率: " + depthMultStr + ")"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§a>>> 综合倍率: §6§l" + fullMultStr), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§7" + configStr), false);
        source.sendSuccess(() -> Component.literal("§7" + cacheStr), false);

        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        DifficultyEventHandler.refreshMultipliers();
        source.sendSuccess(() -> Component.literal("§a区域难度倍率已从配置重新加载。"), false);
        return 1;
    }

    @SuppressWarnings("deprecation")
    private static float getStructureMultiplier(ServerLevel level, BlockPos pos,
                                                 DifficultyMultipliers multipliers) {
        float best = 1.0F;
        for (var entry : multipliers.getAllStructureEntries()) {
            String key = entry.getKey();
            String[] parts = key.split(":", 2);
            if (parts.length != 2) continue;
            var sk = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.STRUCTURE,
                    new net.minecraft.resources.ResourceLocation(parts[0], parts[1]));
            var start = level.structureManager().getStructureWithPieceAt(pos, sk);
            if (start.isValid() && entry.getValue() > best) {
                best = entry.getValue();
            }
        }
        return best;
    }

    private static String fmt(float value) {
        return String.format("%.2f", value);
    }
}
