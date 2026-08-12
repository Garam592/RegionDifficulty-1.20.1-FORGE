package com.garam.regiondifficulty.network;

import com.garam.regiondifficulty.difficulty.DifficultyCalculator;
import com.garam.regiondifficulty.difficulty.DifficultyMultipliers;
import com.garam.regiondifficulty.event.DifficultyEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * C2S 空包 —— 客户端请求服务端计算当前位置的区域难度。
 */
public class RequestDifficultyPacket {

    public RequestDifficultyPacket() {}

    public static void encode(RequestDifficultyPacket packet, FriendlyByteBuf buf) {
        // 空载荷
    }

    public static RequestDifficultyPacket decode(FriendlyByteBuf buf) {
        return new RequestDifficultyPacket();
    }

    public static void handle(RequestDifficultyPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition();
            DifficultyMultipliers multipliers = DifficultyEventHandler.getMultipliersSnapshot();
            if (multipliers == null) return;

            float overall = DifficultyCalculator.calculateMultiplier(level, pos, multipliers);

            ResourceKey<Level> dimKey = level.dimension();
            float dimMult = multipliers.getDimensionMultiplier(dimKey);

            Optional<ResourceKey<Biome>> biomeKey = level.getBiome(pos).unwrapKey();
            String biomeId = biomeKey.map(k -> k.location().toString()).orElse("unknown");
            float biomeMult = biomeKey.map(multipliers::getBiomeMultiplier)
                    .orElse(multipliers.getDefaultMultiplier());

            float depthMult = multipliers.getDepthMultiplier(dimKey, pos.getY());

            // 结构倍率 = overall / (dim * biome * depth)，避免重复遍历结构
            float nonStruct = dimMult * biomeMult * depthMult;
            float structMult = nonStruct > 0.001F ? overall / nonStruct : 1.0F;

            DifficultyDataPacket response = new DifficultyDataPacket(
                    overall, dimKey.location().toString(), dimMult,
                    biomeId, biomeMult, depthMult, structMult, pos.getY()
            );
            NetworkHandler.sendToPlayer(response, player);
        });
        ctx.get().setPacketHandled(true);
    }
}
