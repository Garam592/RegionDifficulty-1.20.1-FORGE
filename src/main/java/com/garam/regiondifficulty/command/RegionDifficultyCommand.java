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
 * Debug command: {@code /regiondifficulty check} prints the regional difficulty
 * multiplier at the executing player's position, with a per-factor breakdown.
 */
@Mod.EventBusSubscriber(modid = "region_difficulty")
public class RegionDifficultyCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("regiondifficulty")
                        .requires(src -> src.hasPermission(2)) // operator level 2
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
            source.sendFailure(Component.literal("This command can only be used on a server level."));
            return 0;
        }
        ServerLevel serverLevel = (ServerLevel) source.getLevel();

        BlockPos pos = BlockPos.containing(source.getPosition());
        DifficultyMultipliers multipliers = DifficultyEventHandler.getMultipliersSnapshot();

        if (multipliers == null) {
            source.sendFailure(Component.literal("Difficulty multipliers not loaded yet."));
            return 0;
        }

        // Full multiplier
        float fullMult = DifficultyCalculator.calculateMultiplier(serverLevel, pos, multipliers);

        // Per-factor breakdown
        ResourceKey<Level> dimKey = serverLevel.dimension();
        float dimMult = multipliers.getDimensionMultiplier(dimKey);

        Optional<ResourceKey<Biome>> biomeKey = serverLevel.getBiome(pos).unwrapKey();
        float biomeMult = biomeKey.map(multipliers::getBiomeMultiplier)
                .orElse(multipliers.getDefaultMultiplier());

        float depthMult = multipliers.getDepthMultiplier(pos.getY());

        // Structure multiplier — compute separately since cache merges it
        float structMult = getStructureMultiplier(serverLevel, pos, multipliers);

        String posStr = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        String dimStr = dimKey.location().toString();
        String biomeStr = biomeKey.map(k -> k.location().toString()).orElse("unknown");
        String dimMultStr = fmt(dimMult);
        String biomeMultStr = fmt(biomeMult);
        String structMultStr = fmt(structMult);
        String depthMultStr = fmt(depthMult);
        String fullMultStr = fmt(fullMult);
        String configStr = "global=" + (Config.enableRegionalDifficulty ? "ON" : "OFF")
                + " spawnAttr=" + (Config.spawnAttributesEnabled ? "ON" : "OFF")
                + " combat=" + (Config.combatScalingEnabled ? "ON" : "OFF")
                + " spawnCtl=" + (Config.spawnControlEnabled ? "ON" : "OFF");
        String cacheStr = "Cache: " + (Config.cacheEnabled ? "ON" : "OFF")
                + " ttl=" + Config.cacheTtlTicks + "t";

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§6===== Region Difficulty Check ====="), false);
        source.sendSuccess(() -> Component.literal("§ePosition: §f" + posStr), false);
        source.sendSuccess(() -> Component.literal("§eDimension: §f" + dimStr + " §7(mult: " + dimMultStr + ")"), false);
        source.sendSuccess(() -> Component.literal("§eBiome: §f" + biomeStr + " §7(mult: " + biomeMultStr + ")"), false);
        source.sendSuccess(() -> Component.literal("§eStructure: §7(mult: " + structMultStr + ")"), false);
        source.sendSuccess(() -> Component.literal("§eDepth (Y=" + pos.getY() + "): §7(mult: " + depthMultStr + ")"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§a>>> Combined Multiplier: §6§l" + fullMultStr), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§7" + configStr), false);
        source.sendSuccess(() -> Component.literal("§7" + cacheStr), false);

        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        DifficultyEventHandler.refreshMultipliers();
        source.sendSuccess(() -> Component.literal("§aRegional difficulty multipliers reloaded from config."), false);
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
