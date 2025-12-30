package com.example.examplemod.client;

import org.lwjgl.glfw.GLFW;

import com.example.examplemod.ExampleMod;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeyMappings {
    // motor1
    public static final KeyMapping MOTOR_1 = new KeyMapping("key.examplemod.motor_1", GLFW.GLFW_KEY_Z, "key.category.examplemod");
    // motor2
    public static final KeyMapping MOTOR_2 = new KeyMapping("key.examplemod.motor_2", GLFW.GLFW_KEY_X, "key.category.examplemod");
    // motor3
    public static final KeyMapping MOTOR_3 = new KeyMapping("key.examplemod.motor_3", GLFW.GLFW_KEY_C, "key.category.examplemod");
    //motor4
    public static final KeyMapping MOTOR_4 = new KeyMapping("key.examplemod.motor_4", GLFW.GLFW_KEY_V, "key.category.examplemod");

    //向机体x正方向移动
    public static final KeyMapping PITCH_UP = new KeyMapping("key.examplemod.move_x_pos", GLFW.GLFW_KEY_U, "key.category.examplemod");
    //向机体x负方向移动
    public static final KeyMapping PITCH_DOWN = new KeyMapping("key.examplemod.move_x_neg", GLFW.GLFW_KEY_H, "key.category.examplemod");
    //向机体z正方向移动
    public static final KeyMapping ROLL_LEFT = new KeyMapping("key.examplemod.move_z_pos", GLFW.GLFW_KEY_J, "key.category.examplemod");
    //向机体z负方向移动
    public static final KeyMapping ROLL_RIGHT = new KeyMapping("key.examplemod.move_z_neg", GLFW.GLFW_KEY_K, "key.category.examplemod");
    //油门
    public static final KeyMapping THROTTLE = new KeyMapping("key.examplemod.throttle", GLFW.GLFW_KEY_UP, "key.category.examplemod");
    //油门减
    public static final KeyMapping THROTTLE_N = new KeyMapping("key.examplemod.throttle_n", GLFW.GLFW_KEY_DOWN, "key.category.examplemod");
    //右偏航
    public static final KeyMapping YAW_RIGHT = new KeyMapping("key.examplemod.yaw_right", GLFW.GLFW_KEY_RIGHT, "key.category.examplemod");
    //左偏航
    public static final KeyMapping YAW_LEFT = new KeyMapping("key.examplemod.yaw_left", GLFW.GLFW_KEY_LEFT, "key.category.examplemod");
    



    @SubscribeEvent
    public static void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(com.example.examplemod.client.KeyMappings.MOTOR_1);
        event.register(com.example.examplemod.client.KeyMappings.MOTOR_2);
        event.register(com.example.examplemod.client.KeyMappings.MOTOR_3);
        event.register(com.example.examplemod.client.KeyMappings.MOTOR_4);
        
        event.register(com.example.examplemod.client.KeyMappings.PITCH_UP);
        event.register(com.example.examplemod.client.KeyMappings.PITCH_DOWN);
        event.register(com.example.examplemod.client.KeyMappings.ROLL_LEFT);
        event.register(com.example.examplemod.client.KeyMappings.ROLL_RIGHT);
        event.register(com.example.examplemod.client.KeyMappings.THROTTLE);
        event.register(com.example.examplemod.client.KeyMappings.THROTTLE_N);
        event.register(com.example.examplemod.client.KeyMappings.YAW_RIGHT);
        event.register(com.example.examplemod.client.KeyMappings.YAW_LEFT);
    }

}
