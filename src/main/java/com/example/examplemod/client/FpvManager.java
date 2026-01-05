package com.example.examplemod.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FpvManager {

    private static boolean isFpvActive = false;

    public static boolean isFpvActive(){
        return isFpvActive;
    }

    public static void setFPV(boolean isFpv){
        isFpvActive = isFpv;
    }

}
