package com.example.examplemod.sound;

import com.example.examplemod.ExampleMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = 
        DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ExampleMod.MODID);

    public static final RegistryObject<SoundEvent> Rotor = 
        SOUNDS.register("rotor", () ->
            SoundEvent.createVariableRangeEvent(
                new ResourceLocation("examplemod", "rotor")
            )
        );

    public static void register(IEventBus eventBus){
        SOUNDS.register(eventBus);
    }
}
