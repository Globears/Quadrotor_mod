package com.example.examplemod.item;


import com.example.examplemod.ExampleMod;
import com.example.examplemod.item.custom.Cheese;
import com.example.examplemod.item.custom.QuadrotorSpawn;
import com.example.examplemod.item.custom.RemoteController;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = 
        DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    public static final RegistryObject<Item> Cheese = ITEMS.register("cheese", 
        () -> {
            return new Cheese();
        });

    public static final RegistryObject<Item> RemoteController = ITEMS.register("remote_controller",
        () -> {
            return new RemoteController();
        });

    public static final RegistryObject<Item> DRONE_SPAWN_ITEM = ITEMS.register(
        "quadrotor_spawn", 
        QuadrotorSpawn::new 
    );

    public static void register(IEventBus eventBus){
        ITEMS.register(eventBus);
    }
}
