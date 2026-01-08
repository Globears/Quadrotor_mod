package com.example.examplemod.client;

import org.joml.Math;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.custom.QuadrotorEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class FpvHudRenderer {

    private static ResourceLocation len = new ResourceLocation(ExampleMod.MODID, "textures/gui/len.png");

    private static float rollAngle = 0;
    private static float pitchAngle = 0;

    public static void setRoll(float roll){
        rollAngle = roll;
    }

    public static void setPitch(float pitch){
        pitchAngle = pitch;
    }

    @SubscribeEvent
    public static void renderFpvHud(RenderGuiOverlayEvent.Post event){
        if(!FpvManager.isFpvActive()){
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        QuadrotorEntity quadrotor = (QuadrotorEntity)camera.getEntity();

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        GuiGraphics gui = event.getGuiGraphics();

        // gui.drawString(
        //     mc.font,
        //     "hello from ui",
        //     centerX, centerY,
        //     0xFFAA00, // 橙色文字
        //     false
        // );

        float speed = quadrotor.getVelocity().length();
        String speedText = "Speed: " + String.valueOf(speed);
        gui.drawString(
            mc.font,
            speedText,
            0, centerY,
            0xFFFFFF, // 白色文字
            true
        );


        gui.pose().pushPose();
        
		RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 0.1f);

        // 3. 姿态变换：平移→旋转（核心旋转逻辑）
        // 3.1 平移渲染原点到纹理中心（关键！否则旋转会以屏幕原点为中心）
        gui.pose().translate(centerX, centerY - 20, 0);
        // 3.2 旋转：顺时针12° → 转为弧度，负数表示顺时针（MC默认正数逆时针）
        gui.pose().mulPose(Axis.ZP.rotation(-rollAngle));
        // 3.3 平移回纹理左上角（此时绘制坐标以中心为原点，所以要偏移-宽/2、-高/2）
        gui.pose().translate(-64 / 2.0f, -64 / 2.0f, 0);

        gui.blit(len, 0, 0, 0, 0, 64, 64, 64, 64);

		RenderSystem.disableBlend();
		RenderSystem.setShaderColor(1, 1, 1, 1);

        gui.pose().popPose();

    }


}
