package com.example.examplemod.network.packet;

import java.util.UUID;
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
    private final boolean start;

    public QuadrotorFPVRequestC2SPacket(int entityId, boolean start) {
        this.entityId = entityId;
        this.start = start;
    }

    public static void encode(QuadrotorFPVRequestC2SPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.entityId);
        buf.writeBoolean(pkt.start);
    }

    public static QuadrotorFPVRequestC2SPacket decode(FriendlyByteBuf buf) {
        return new QuadrotorFPVRequestC2SPacket(buf.readInt(), buf.readBoolean());
    }

    public static void handle(QuadrotorFPVRequestC2SPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            // 停止FPV
            if (!pkt.start){
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new QuadrotorFPVResponseS2CPacket(-1, false));
                sender.sendSystemMessage(Component.literal("Quadrotor: 退出 FPV"));
                QuadrotorEntity quad = (QuadrotorEntity)sender.level().getEntity(pkt.entityId);
                quad.setPilotUUID(null);
            }

            // 开始FPV
            if (pkt.start) {
                
                Entity e = sender.level().getEntity(pkt.entityId);
                if(e instanceof QuadrotorEntity){
                    QuadrotorEntity quadrotor = (QuadrotorEntity)e;
                    quadrotor.setPilotUUID(sender.getUUID());
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new QuadrotorFPVResponseS2CPacket(quadrotor.getId(), true));
                }
            }

        });
        ctx.setPacketHandled(true);
    }
}
