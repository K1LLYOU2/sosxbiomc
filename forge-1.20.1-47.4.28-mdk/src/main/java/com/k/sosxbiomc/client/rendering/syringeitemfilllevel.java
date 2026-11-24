package com.k.sosxbiomc.client.rendering;

import com.k.sosxbiomc.SOSxBiomc;
import com.k.sosxbiomc.registry.moditems;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(
        modid = SOSxBiomc.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class syringeitemfilllevel {

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {

        event.enqueueWork(() -> ItemProperties.register(
                moditems.EMPTY_SYRINGE.get(),
                new ResourceLocation(SOSxBiomc.MOD_ID, "fill_level"),
                (stack, level, entity, seed) -> getFillLevel(stack)
        ));
    }

    private static float getFillLevel(ItemStack stack) {

        int amount = FluidUtil.getFluidHandler(stack)
                .map(handler -> handler.getFluidInTank(0).getAmount())
                .orElse(0);

        if (amount <= 0) return 0.0F;
        if (amount <= 25) return 0.25F;
        if (amount <= 50) return 0.50F;
        if (amount <= 75) return 0.75F;

        return 1.0F;
    }
}