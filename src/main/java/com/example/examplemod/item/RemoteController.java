package com.example.examplemod.item;

import com.example.examplemod.network.ModNetwork;
import com.example.examplemod.network.packet.QuadrotorFPVRequestC2SPacket;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Level;

public class RemoteController extends Item {
    public RemoteController() {
        super(new Item.Properties());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        if (world.isClientSide()) {
            // 客户端执行：进行射线检测获取实体ID并发送请求给服务器
            HitResult result = player.pick(20.0D, 0.0F, false);
            if (player.isShiftKeyDown()) {
                // 持Shift右键：退出FPV
                ModNetwork.CHANNEL.sendToServer(new QuadrotorFPVRequestC2SPacket(-2));
            } else if (result != null && result.getType() == HitResult.Type.ENTITY) {
                EntityHitResult er = (EntityHitResult) result;
                int id = er.getEntity().getId();
                ModNetwork.CHANNEL.sendToServer(new QuadrotorFPVRequestC2SPacket(id));
            } else {
                // 发送 -1 表示自动选择绑定的无人机或寻找附近的绑定设备
                ModNetwork.CHANNEL.sendToServer(new QuadrotorFPVRequestC2SPacket(-1));
            }
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    // Helper: ensure the ItemStack has a persistent UUID to identify this remote instance
    public static java.util.UUID getOrCreateRemoteId(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID("RemoteId")) {
            tag.putUUID("RemoteId", java.util.UUID.randomUUID());
        }
        return tag.getUUID("RemoteId");
    }
}
