package com.example.examplemod.network.packet;

import java.util.function.Supplier;

import com.example.examplemod.entity.custom.QuadrotorEntity;
import com.example.examplemod.item.ModItems;
import com.example.examplemod.item.RemoteController;
import com.example.examplemod.network.ModNetwork;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

            // 从玩家手中获取遥控器 UUID（优先主手）
            final java.util.UUID remoteId;
            ItemStack main = sender.getMainHandItem();
            ItemStack off = sender.getOffhandItem();
            if (main.getItem() == ModItems.RemoteController.get()) {
                remoteId = RemoteController.getOrCreateRemoteId(main);
            } else if (off.getItem() == ModItems.RemoteController.get()) {
                remoteId = RemoteController.getOrCreateRemoteId(off);
            }else{
                remoteId = null;
            }

            if (remoteId == null) {
                sender.sendSystemMessage(Component.literal("Quadrotor: 未检测到遥控器，请手持遥控器并右键绑定"));
                return;
            }

            if (e instanceof QuadrotorEntity) {
                QuadrotorEntity quad = (QuadrotorEntity)e;

                // 必须是已绑定给该遥控器或者未绑定（我们允许自动绑定）
                if (quad.getController() == null) {
                    quad.bindController(remoteId);
                    sender.sendSystemMessage(Component.literal("Quadrotor: 已绑定给该遥控器并进入 FPV"));
                } else if (!quad.getController().equals(remoteId)) {
                    sender.sendSystemMessage(Component.literal("Quadrotor: 已被其他遥控器绑定"));
                    return;
                } else {
                    sender.sendSystemMessage(Component.literal("Quadrotor: 进入 FPV"));
                }

                // 发送 S2C 指令给该玩家，通知客户端进入 FPV （只发给请求者）
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new QuadrotorFPVResponseS2CPacket(quad.getId(), true));

            } else {
                // 未指定或非目标实体：尝试在附近找到遥控器绑定的无人机并切换
                QuadrotorEntity found = sender.level().getEntitiesOfClass(QuadrotorEntity.class, sender.getBoundingBox().inflate(32.0D),
                    entity -> remoteId.equals(entity.getController())).stream().findFirst().orElse(null);

                if (found != null) {
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new QuadrotorFPVResponseS2CPacket(found.getId(), true));
                    sender.sendSystemMessage(Component.literal("Quadrotor: 进入附近的绑定无人机 FPV"));
                } else {
                    sender.sendSystemMessage(Component.literal("Quadrotor: 没有找到你遥控器绑定的无人机"));
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
