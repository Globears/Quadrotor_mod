package com.example.examplemod.item;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;


public class Cheese extends Item{
    private static FoodProperties food = (new FoodProperties.Builder()
        .saturationMod(10)
        .alwaysEat()
        .build()
        );

    

    public Cheese(){
        super(new Item.Properties().food(food));
    }
}