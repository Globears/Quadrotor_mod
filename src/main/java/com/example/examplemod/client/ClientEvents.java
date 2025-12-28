package com.example.examplemod.client;

import org.lwjgl.glfw.GLFW;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.autopilot.ControlCommand;
import com.example.examplemod.autopilot.MotorState;
import com.example.examplemod.entity.custom.QuadrotorEntity;
import com.example.examplemod.network.ModNetwork;
import com.example.examplemod.network.packet.QuadrotorControlC2SPacket;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;

import org.lwjgl.system.MemoryStack;
import java.nio.DoubleBuffer;
import com.example.examplemod.item.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    // 保存上一次发送的指令以免重复发送
    private static MotorState old_motorState = new MotorState();
    private static ControlCommand old_command = new ControlCommand();

    // 鼠标与遥控器控制状态（使用像素位移读取原始光标速度）
    private static boolean cursorInit = false;
    private static double lastCursorX = 0.0;
    private static double lastCursorY = 0.0;
    private static final float MOUSE_SENSITIVITY = 0.05f; // 映射系数（可调）
    private static final float MOUSE_PIXELS_TO_RAD = 0.01f; // 像素位移 -> 弧度（需调参）
    private static final double MOUSE_PIXELS_DEADZONE = 1.0; // 小于该像素变化视为未移动

    // 遥控器持有状态（进入时锁定玩家视角）
    private static boolean holdingRemotePrev = false;
    private static float lockedPitch = 0.0f;
    private static float lockedYaw = 0.0f;

    // FPV 状态
    private static boolean fpvActive = false;
    private static int fpvEntityId = -1;

    public static void setFPV(boolean active, int entityId) {
        fpvActive = active;
        fpvEntityId = entityId;
        // 重置鼠标初始状态以免突变
        cursorInit = false;
        // 退出 FPV 时重置旧输入以避免向服务端发送脏指令
        if (!active) {
            old_command = new ControlCommand();
            old_motorState = new MotorState();
        }
    }


    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        MotorState motorState = new MotorState();
        ControlCommand command = new ControlCommand();

        

        // 添加直接的电机指令（便于测试刚体动力学）
        motorState.motor1 = KeyMappings.MOTOR_1.isDown() ? 1f : 0.0f;
        motorState.motor2 = KeyMappings.MOTOR_2.isDown() ? 1f : 0.0f;
        motorState.motor3 = KeyMappings.MOTOR_3.isDown() ? 1f : 0.0f;
        motorState.motor4 = KeyMappings.MOTOR_4.isDown() ? 1f : 0.0f;

        // 添加用户直观操作的指令（优先使用鼠标移动）
        //鼠标的前后移动控制俯仰角，左右移动控制滚转角
        command.referenceThrottle = 0.0f;
        command.referenceYawSpeed = 0.0f;
        command.referencePitch = 0.0f;
        command.referenceRoll = 0.0f;

        if (KeyMappings.THROTTLE.isDown()) {
            command.referenceThrottle = 1.0f;
        }

        // 使用鼠标移动进行姿态控制（FPV 情况下）
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer xbuf = stack.mallocDouble(1);
            DoubleBuffer ybuf = stack.mallocDouble(1);
            long win = mc.getWindow().getWindow();
            GLFW.glfwGetCursorPos(win, xbuf, ybuf);
            double cx = xbuf.get(0);
            double cy = ybuf.get(0);

            if (!cursorInit) {
                lastCursorX = cx;
                lastCursorY = cy;
                cursorInit = true;
            }

            double dx = cx - lastCursorX;
            double dy = cy - lastCursorY;

            //lastCursorX = cx;
            //lastCursorY = cy;

            if (Math.abs(dx) < MOUSE_PIXELS_DEADZONE) dx = 0.0;
            if (Math.abs(dy) < MOUSE_PIXELS_DEADZONE) dy = 0.0;

            // 映射到参考俯仰/滚转/偏航速度
            command.referencePitch = (float)(-dy * MOUSE_PIXELS_TO_RAD * MOUSE_SENSITIVITY);
            command.referenceRoll = (float)(dx * MOUSE_PIXELS_TO_RAD * MOUSE_SENSITIVITY);
            command.referenceYawSpeed = (float)(dx * MOUSE_PIXELS_TO_RAD * MOUSE_SENSITIVITY * 0.5);
        }

        // 检查是否手持遥控器（主手或副手）
        boolean holdingRemote = mc.player.getMainHandItem().getItem() == com.example.examplemod.item.ModItems.RemoteController.get()
                || mc.player.getOffhandItem().getItem() == com.example.examplemod.item.ModItems.RemoteController.get();

        // 仅在 FPV 激活且手持遥控器时发送控制包
        if (!fpvActive || !holdingRemote) {
            return;
        }

        

        
        
        

        //判断当前输入和上次输入是否相同，相同则不发送
        if (motorState.motor1 == old_motorState.motor1 &&
            motorState.motor2 == old_motorState.motor2 &&
            motorState.motor3 == old_motorState.motor3 &&
            motorState.motor4 == old_motorState.motor4 &&
            command.referenceThrottle == old_command.referenceThrottle &&
            command.referenceYawSpeed == old_command.referenceYawSpeed &&
            command.referencePitch == old_command.referencePitch &&
            command.referenceRoll == old_command.referenceRoll)
            return; // 输入未变化，跳过发送
        

        // 更新旧状态
        old_motorState = motorState;
        old_command = command;

        ModNetwork.CHANNEL.sendToServer(new QuadrotorControlC2SPacket(motorState, command, mc.player.getUUID()));
        
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (!fpvActive) return;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera.getEntity() instanceof QuadrotorEntity quad && quad.getId() == fpvEntityId) {
            // QuadrotorEntity 存的是弧度，Renderer 使用度数 => 转换为度
            float rollDeg = (float) Math.toDegrees(quad.getRollAngle());
            float pitchDeg = (float) Math.toDegrees(quad.getPitchAngle());
            float yawDeg = (float) Math.toDegrees(quad.getYawAngle());
            // 可选：对 roll 限幅或平滑（见下）
            event.setYaw(yawDeg);
            event.setPitch(pitchDeg);
            event.setRoll(rollDeg);

            //TODO:对三个角度进行平滑
            

        }
    }
}