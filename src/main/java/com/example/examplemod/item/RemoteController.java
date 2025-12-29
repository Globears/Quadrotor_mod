package com.example.examplemod.item;

import java.util.UUID;

import com.example.examplemod.network.ModNetwork;
import com.example.examplemod.network.packet.QuadrotorFPVRequestC2SPacket;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class RemoteController extends Item {

    public RemoteController() {
        super(new Item.Properties());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        // 我们在该函数中处理遥控的开始与结束
        // 玩家拿着遥控器右键时，如果遥控器存储了无人机实体的实体id，那么就切换过去并开始遥控

        if (player.level().isClientSide()) {
            // 如果是按shift右键，则退出fpv
            if(player.isShiftKeyDown()){
                ModNetwork.CHANNEL.sendToServer(new QuadrotorFPVRequestC2SPacket(-2));
                return InteractionResultHolder.success(player.getItemInHand(hand));
            }
            
            // 如果是正常右键，则进入绑定的无人机的fpv
            ItemStack remoteController = player.getMainHandItem();
            int quadrotorId = getPairedQuadrotorId(remoteController);
            ModNetwork.CHANNEL.sendToServer(new QuadrotorFPVRequestC2SPacket(quadrotorId));
            
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }


        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    // Helper: ensure the ItemStack has a persistent UUID to identify this remote instance
    public static UUID getOrCreateRemoteId(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID("RemoteId")) {
            tag.putUUID("RemoteId", java.util.UUID.randomUUID());
        }
        return tag.getUUID("RemoteId");
    }


    // TODO:实体id不能保持控制权，世界重启后id会重新分配，我们还希望实体的物品（刷怪蛋）也能保存其唯一标识
    public static int getPairedQuadrotorId(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return tag.getInt("QuadrotorId");
    }

    public static void setPairedQuadrotorId(ItemStack stack, int entityId){
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("QuadrotorId", entityId);
    }

    // TODO:我们希望灵敏度配置保存在遥控器物品里
}
