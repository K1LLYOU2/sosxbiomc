
package com.k.sosxbiomc;

import com.k.sosxbiomc.registry.moditems;
import com.k.sosxbiomc.network.guinetwork;
import com.k.sosxbiomc.mutation.mutationhandler;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SOSxBiomc.MOD_ID)
public class SOSxBiomc {

    public static final String MOD_ID = "sosxbiomc";

    public static final Logger LOGGER = LogUtils.getLogger();

    public SOSxBiomc() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        moditems.register(modEventBus);

        guinetwork.register();

        MinecraftForge.EVENT_BUS.register(new mutationhandler());
    }
}
