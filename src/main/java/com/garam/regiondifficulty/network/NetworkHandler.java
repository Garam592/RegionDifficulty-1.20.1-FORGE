package com.garam.regiondifficulty.network;

import com.garam.regiondifficulty.RegionDifficulty;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 模组网络通道 —— 注册和处理 difficulty indicator 的 C2S/S2C 包。
 */
public final class NetworkHandler {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RegionDifficulty.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private NetworkHandler() {}

    public static void register() {
        int idx = 0;
        CHANNEL.messageBuilder(RequestDifficultyPacket.class, idx++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestDifficultyPacket::encode)
                .decoder(RequestDifficultyPacket::decode)
                .consumerNetworkThread(RequestDifficultyPacket::handle)
                .add();
        CHANNEL.messageBuilder(DifficultyDataPacket.class, idx++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DifficultyDataPacket::encode)
                .decoder(DifficultyDataPacket::decode)
                .consumerNetworkThread(DifficultyDataPacket::handle)
                .add();
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
