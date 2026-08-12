package com.garam.regiondifficulty.client;

import com.garam.regiondifficulty.network.DifficultyDataPacket;

/**
 * 客户端缓存 —— 存储服务端发来的最新难度数据。
 */
public final class ClientDifficultyCache {

    private static volatile DifficultyDataPacket data;
    private static long lastUpdateTick = -1;

    private ClientDifficultyCache() {}

    public static void update(DifficultyDataPacket packet) {
        data = packet;
        lastUpdateTick = currentClientTick();
    }

    public static DifficultyDataPacket get() {
        return data;
    }

    public static boolean isStale(long currentTick) {
        return data == null || (currentTick - lastUpdateTick) > 40;
    }

    public static void invalidate() {
        data = null;
        lastUpdateTick = -1;
    }

    private static long currentClientTick() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : -1;
    }
}
