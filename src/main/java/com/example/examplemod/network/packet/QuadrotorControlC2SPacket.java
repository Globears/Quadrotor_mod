package com.example.examplemod.network.packet;

import com.example.examplemod.autopilot.ControlCommand;
import com.example.examplemod.autopilot.MotorState;
import com.example.examplemod.entity.custom.QuadrotorEntity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import org.joml.Vector3f;


public class QuadrotorControlC2SPacket {
    //该数据包传输玩家的控制指令，要控制的无人机，还可能有四个电机的状态（调试用）

    //要控制的无人机id
    private final int quadrotorId;

    //指令
    private final ControlCommand command;

    //由指令发布的电机状态
    private final MotorState motorState;
    

    // 常规构造器
    public QuadrotorControlC2SPacket(MotorState motorState, ControlCommand command, int id) {
        this.motorState = motorState;
        this.command = command;
        this.quadrotorId = id;
    }
    
    // 重置构造器
    public QuadrotorControlC2SPacket() {
        this.motorState = new MotorState();
        this.command = new ControlCommand();
        this.quadrotorId = 0;
    }

    //数据包在传输时先编码成字节流
    public static void encode(QuadrotorControlC2SPacket packet, FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.motorState.motor1);
        buffer.writeFloat(packet.motorState.motor2);
        buffer.writeFloat(packet.motorState.motor3);
        buffer.writeFloat(packet.motorState.motor4);

        buffer.writeFloat(packet.command.referenceThrottle);
        buffer.writeFloat(packet.command.referenceYaw);
        buffer.writeFloat(packet.command.referencePitch);
        buffer.writeFloat(packet.command.referenceRoll);

        buffer.writeInt(packet.quadrotorId);
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

        int quadrotorId = buffer.readInt();

        MotorState motorState = new MotorState();
        motorState.motor1 = motor1;
        motorState.motor2 = motor2;
        motorState.motor3 = motor3;
        motorState.motor4 = motor4;
        ControlCommand command = new ControlCommand();
        command.referenceThrottle = referenceThrottle;
        command.referenceYaw = referenceYawSpeed;
        command.referencePitch = referencePitch;
        command.referenceRoll = referenceRoll;
        return new QuadrotorControlC2SPacket(motorState, command, quadrotorId);
    }

    public static void handle(QuadrotorControlC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() == null) return;
            
            Player player = ctx.getSender();

            Entity quad = player.level().getEntity(packet.quadrotorId);

            if(!(quad instanceof QuadrotorEntity)){
                player.sendSystemMessage(Component.literal("控制时出现异常"));
            }
            
            if(quad instanceof QuadrotorEntity){
                QuadrotorEntity quadrotor = (QuadrotorEntity)quad;
                quadrotor.setCommand(packet.command);

                //调试用
            }
            
        });
        ctx.setPacketHandled(true);
    }
    

    

}