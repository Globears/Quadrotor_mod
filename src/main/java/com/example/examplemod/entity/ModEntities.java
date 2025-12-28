package com.example.examplemod.entity;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.custom.QuadrotorEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ExampleMod.MODID);

    public static void register(IEventBus eventBus){
        ENTITIES.register(eventBus);
    }

    public static final RegistryObject<EntityType<QuadrotorEntity>> QUADROTOR =
        ENTITIES.register("quadrotor", () ->
            EntityType.Builder.<QuadrotorEntity>of(QuadrotorEntity::new, MobCategory.MISC)
                // Slightly taller so player can more easily right-click the entity
                .sized(0.9f, 0.9f)
                .build(new ResourceLocation(ExampleMod.MODID, "quadrotor").toString()));

    
}
