package com.example.examplemod.entity.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.custom.QuadrotorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class QuadrotorRenderer extends EntityRenderer<QuadrotorEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(ExampleMod.MODID, "textures/entity/quadrotor.png");
    private final QuadrotorModel2<QuadrotorEntity> model;

    public QuadrotorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new QuadrotorModel2<>(context.bakeLayer(QuadrotorModel2.LAYER_LOCATION));
    }

    @Override
    public void render(QuadrotorEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        

        // Translate so the model sits at entity feet as intended by the model (bb_main was offset to y=24)
        // Apply entity orientation (yaw/pitch) and roll so the visual model matches physics
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-(float)Math.toDegrees(entity.getYawAngle())));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float)Math.toDegrees(entity.getPitchAngle())));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float)Math.toDegrees(entity.getRollAngle())));
        VertexConsumer vb = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        model.renderToBuffer(poseStack, vb, packedLight, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(QuadrotorEntity entity) {
        return TEXTURE;
    }
}