package com.example.examplemod.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("examplemod", "main"),
        () -> PROTOCOL,
        PROTOCOL::equals,
        PROTOCOL::equals
    );

    private static int id = 0;
    public static int nextId() { return id++; }

    public static void register() {
        CHANNEL.registerMessage(nextId(), com.example.examplemod.network.packet.QuadrotorControlC2SPacket.class,
            com.example.examplemod.network.packet.QuadrotorControlC2SPacket::encode,
            com.example.examplemod.network.packet.QuadrotorControlC2SPacket::decode,
            com.example.examplemod.network.packet.QuadrotorControlC2SPacket::handle);

        // FPV 请求（客户端 -> 服务器）
        CHANNEL.registerMessage(nextId(), com.example.examplemod.network.packet.QuadrotorFPVRequestC2SPacket.class,
            com.example.examplemod.network.packet.QuadrotorFPVRequestC2SPacket::encode,
            com.example.examplemod.network.packet.QuadrotorFPVRequestC2SPacket::decode,
            com.example.examplemod.network.packet.QuadrotorFPVRequestC2SPacket::handle);

        // FPV 响应（服务器 -> 客户端）
        CHANNEL.registerMessage(nextId(), com.example.examplemod.network.packet.QuadrotorFPVResponseS2CPacket.class,
            com.example.examplemod.network.packet.QuadrotorFPVResponseS2CPacket::encode,
            com.example.examplemod.network.packet.QuadrotorFPVResponseS2CPacket::decode,
            com.example.examplemod.network.packet.QuadrotorFPVResponseS2CPacket::handle);
    }
}
