package com.garam.regiondifficulty.network;

import com.garam.regiondifficulty.client.ClientDifficultyCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C 包 —— 携带完整的区域难度分解数据。
 */
public class DifficultyDataPacket {
    public final float overallMult;
    public final String dimId;
    public final float dimMult;
    public final String biomeId;
    public final float biomeMult;
    public final float depthMult;
    public final float structMult;
    public final int playerY;

    public DifficultyDataPacket(float overallMult, String dimId, float dimMult,
                                String biomeId, float biomeMult,
                                float depthMult, float structMult, int playerY) {
        this.overallMult = overallMult;
        this.dimId = dimId;
        this.dimMult = dimMult;
        this.biomeId = biomeId;
        this.biomeMult = biomeMult;
        this.depthMult = depthMult;
        this.structMult = structMult;
        this.playerY = playerY;
    }

    public static void encode(DifficultyDataPacket packet, FriendlyByteBuf buf) {
        buf.writeFloat(packet.overallMult);
        buf.writeUtf(packet.dimId);
        buf.writeFloat(packet.dimMult);
        buf.writeUtf(packet.biomeId);
        buf.writeFloat(packet.biomeMult);
        buf.writeFloat(packet.depthMult);
        buf.writeFloat(packet.structMult);
        buf.writeInt(packet.playerY);
    }

    public static DifficultyDataPacket decode(FriendlyByteBuf buf) {
        return new DifficultyDataPacket(
                buf.readFloat(), buf.readUtf(), buf.readFloat(),
                buf.readUtf(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readInt()
        );
    }

    public static void handle(DifficultyDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientDifficultyCache.update(packet));
        ctx.get().setPacketHandled(true);
    }
}
