package com.example.examplemod.network.packet;

import java.util.function.Supplier;

import com.example.examplemod.entity.custom.QuadrotorEntity;
import com.example.examplemod.network.ModNetwork;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;

public class QuadrotorFPVRequestC2SPacket {
    private final int entityId;

    public QuadrotorFPVRequestC2SPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(QuadrotorFPVRequestC2SPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.entityId);
    }

    public static QuadrotorFPVRequestC2SPacket decode(FriendlyByteBuf buf) {
        return new QuadrotorFPVRequestC2SPacket(buf.readInt());
    }

    public static void handle(QuadrotorFPVRequestC2SPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            // 特殊值 -2: 停止 FPV
            if (pkt.entityId == -2) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new QuadrotorFPVResponseS2CPacket(-1, false));
                sender.sendSystemMessage(Component.literal("Quadrotor: 退出 FPV"));
                ctx.setPacketHandled(true);
                return;
            }

            // 找到目标实体（如果有）
            Entity e = sender.level().getEntity(pkt.entityId);

            if (e instanceof QuadrotorEntity) {
                QuadrotorEntity quad = (QuadrotorEntity)e;
                // 发送 S2C 指令给该玩家，通知客户端进入 FPV （只发给请求者）
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new QuadrotorFPVResponseS2CPacket(quad.getId(), true));

            } 
        });
        ctx.setPacketHandled(true);
    }
}
