package com.k.sosxbiomc.client.rendering;

import com.k.sosxbiomc.SOSxBiomc;
import com.k.sosxbiomc.registry.moditems;

import net.minecraft.world.item.ItemStack;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = SOSxBiomc.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class syringeitemcolor {

    private static final int BLOOD_COLOR = 0xFF703B4F;

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {

        event.register(
                syringeitemcolor::getColor,
                moditems.EMPTY_SYRINGE.get()
        );
    }

    private static int getColor(ItemStack stack, int tintIndex) {

        // Only tint the liquid layer.
        if (tintIndex != 1) {
            return 0xFFFFFFFF;
        }

        // Only apply blood coloring when the syringe contains fluid.
        boolean hasFluid = FluidUtil.getFluidHandler(stack)
                .map(handler -> !handler.getFluidInTank(0).isEmpty())
                .orElse(false);

        return hasFluid ? BLOOD_COLOR : 0xFFFFFFFF;
    }
}