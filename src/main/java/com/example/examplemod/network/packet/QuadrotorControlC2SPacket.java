package com.example.examplemod.network.packet;

import java.util.Optional;
import java.util.UUID;

import com.example.examplemod.autopilot.ControlCommand;
import com.example.examplemod.autopilot.MotorState;
import com.example.examplemod.entity.custom.QuadrotorEntity;
import com.example.examplemod.item.ModItems;
import com.example.examplemod.item.RemoteController;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class QuadrotorControlC2SPacket {
    //该数据包应该传输玩家的操作指令（以遥控器举例来说，可能包含四个连续量）

    //指令
    private final ControlCommand command;

    //由指令发布的电机状态
    private final MotorState motorState;
    
    // 玩家UUID，用于验证控制权
    private final UUID playerUUID;
    

    // 常规构造器
    public QuadrotorControlC2SPacket(MotorState motorState, ControlCommand command, UUID playerUUID) {
        this.motorState = motorState;
        this.command = command;
        this.playerUUID = playerUUID;
    }
    
    // 重置构造器
    public QuadrotorControlC2SPacket(UUID playerUUID) {
        this.motorState = new MotorState();
        this.command = new ControlCommand();
        this.playerUUID = playerUUID;
    }

    //数据包在传输时先编码成字节流
    public static void encode(QuadrotorControlC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.motorState.motor1);
        buffer.writeFloat(packet.motorState.motor2);
        buffer.writeFloat(packet.motorState.motor3);
        buffer.writeFloat(packet.motorState.motor4);

        buffer.writeFloat(packet.command.referenceThrottle);
        buffer.writeFloat(packet.command.referenceYawSpeed);
        buffer.writeFloat(packet.command.referencePitch);
        buffer.writeFloat(packet.command.referenceRoll);

        buffer.writeUUID(packet.playerUUID);
    }

    //收到数据包后，解码到变量里
    public static QuadrotorControlC2SPacket decode(FriendlyByteBuf buffer) {

        float motor1 = buffer.readFloat();
        float motor2 = buffer.readFloat();
        float motor3 = buffer.readFloat();
        float motor4 = buffer.readFloat();

        float referenceThrottle = buffer.readFloat();
        float referenceYawSpeed = buffer.readFloat();
        float referencePitch = buffer.readFloat();
        float referenceRoll = buffer.readFloat();

        UUID playerUUID = buffer.readUUID();

        MotorState motorState = new MotorState();
        motorState.motor1 = motor1;
        motorState.motor2 = motor2;
        motorState.motor3 = motor3;
        motorState.motor4 = motor4;
        ControlCommand command = new ControlCommand();
        command.referenceThrottle = referenceThrottle;
        command.referenceYawSpeed = referenceYawSpeed;
        command.referencePitch = referencePitch;
        command.referenceRoll = referenceRoll;
        return new QuadrotorControlC2SPacket(motorState, command, playerUUID);
    }

    public static void handle(QuadrotorControlC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() == null) return;
            
            Player player = ctx.getSender();

            // 从玩家手中获取遥控器 UUID（优先主手）
            final java.util.UUID remoteId;
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            if (main.getItem() == ModItems.RemoteController.get()) {
                remoteId = RemoteController.getOrCreateRemoteId(main);
            } else if (off.getItem() == ModItems.RemoteController.get()) {
                remoteId = RemoteController.getOrCreateRemoteId(off);
            }else{
                remoteId = null;
            }

            if (remoteId == null) {
                player.sendSystemMessage(Component.literal("未检测到遥控器，请手持遥控器发送指令。"));
                return;
            }

            // 查找与该遥控器 UUID 配对的无人机
            Optional<QuadrotorEntity> opt = player.level()
                .getEntitiesOfClass(QuadrotorEntity.class, player.getBoundingBox().inflate(32.0D),
                    entity -> remoteId.equals(entity.getController()))
                .stream()
                .findFirst();

            
            if (opt.isPresent()) {
                QuadrotorEntity quadrotor = opt.get();
                
                // 设置新的推力值
                quadrotor.setMotorState(packet.motorState);
                //向无人机发送命令
                quadrotor.setCommand(packet.command);
                
            } else {
                // 没有找到控制的无人机
                player.sendSystemMessage(Component.literal("未找到你的遥控器绑定的无人机。右键无人机与遥控器配对。"));
                
                // 可选：在玩家周围寻找未绑定的无人机
                Optional<QuadrotorEntity> unbound = player.level()
                    .getEntitiesOfClass(QuadrotorEntity.class, player.getBoundingBox().inflate(16.0D),
                        entity -> entity.getController() == null)
                    .stream()
                    .findFirst();
                    
                if (unbound.isPresent()) {
                    player.sendSystemMessage(Component.literal("提示: 附近有未绑定的无人机，右键点击可与遥控器配对。"));
                }
            }
        });
        ctx.setPacketHandled(true);
    }
    

    

}