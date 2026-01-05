package com.example.examplemod.network.packet;

import java.util.function.Supplier;

import com.example.examplemod.client.ClientEvents;
import com.example.examplemod.client.FpvManager;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class QuadrotorFPVResponseS2CPacket {
    private final int entityId;
    private final boolean start;

    public QuadrotorFPVResponseS2CPacket(int entityId, boolean start) {
        this.entityId = entityId;
        this.start = start;
    }

    public static void encode(QuadrotorFPVResponseS2CPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.entityId);
        buf.writeBoolean(pkt.start);
    }

    public static QuadrotorFPVResponseS2CPacket decode(FriendlyByteBuf buf) {
        return new QuadrotorFPVResponseS2CPacket(buf.readInt(), buf.readBoolean());
    }

    public static void handle(QuadrotorFPVResponseS2CPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // 客户端处理：切换摄像机到指定实体
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().player == null) return;
            if (pkt.start) {
                if (Minecraft.getInstance().level != null) {
                    if (Minecraft.getInstance().level.getEntity(pkt.entityId) != null) {
                        Minecraft.getInstance().setCameraEntity(Minecraft.getInstance().level.getEntity(pkt.entityId));
                        // 标记客户端 FPV 状态
                        ClientEvents.setFPV(true, pkt.entityId);
                        FpvManager.setFPV(true);
                    }
                }
            } else {
                // 停止 FPV：将摄像机切回玩家
                Minecraft.getInstance().setCameraEntity(Minecraft.getInstance().player);
                ClientEvents.setFPV(false, -1);
                FpvManager.setFPV(false);
            }
        });
        ctx.setPacketHandled(true);
    }
}
