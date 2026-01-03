package com.example.examplemod.item.custom;

import com.example.examplemod.entity.ModEntities;
import com.example.examplemod.entity.custom.QuadrotorEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class QuadrotorSpawn extends Item{
    public QuadrotorSpawn(){
        super(new Properties().stacksTo(64));
    }

    // 右键放置实体的核心逻辑（适配非Mob实体）
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        // 客户端仅做视觉反馈，实体生成逻辑必须在服务端执行
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // 计算实体生成位置（点击的方块中心 + 朝向偏移，避免卡进方块）
        BlockPos clickPos = context.getClickedPos();
        Direction clickFace = context.getClickedFace();
        Vec3 spawnPos = Vec3.atCenterOf(clickPos)
                .add(clickFace.getStepX() * 0.5D, clickFace.getStepY() * 0.5D, clickFace.getStepZ() * 0.5D);

        // 生成无人机实体（非Mob实体专用逻辑）
        EntityType<QuadrotorEntity> quadrotorType = ModEntities.QUADROTOR.get();
        QuadrotorEntity quadrotor = quadrotorType.create(level); // 非Mob实体用create()生成
        if (quadrotor != null) {
            // 设置实体位置和旋转（和玩家视角一致）
            quadrotor.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            quadrotor.setYRot(context.getRotation()); // 朝向和玩家右键时的视角一致
            quadrotor.setXRot(0.0F);

            // 关键：直接添加实体到世界（非Mob无需检查Mob生成规则）
            level.addFreshEntity(quadrotor);

            // 非创造模式消耗物品
            if (!context.getPlayer().isCreative()) {
                context.getItemInHand().shrink(1);
            }

            return InteractionResult.CONSUME; // 标记为“物品已使用”
        }

        return InteractionResult.FAIL;
    }

}
