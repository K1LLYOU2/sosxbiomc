
package com.k.sosxbiomc.pit;

import com.k.sosxbiomc.SOSxBiomc;

import net.minecraft.server.level.ServerLevel;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = SOSxBiomc.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class pittickhandler {

    private static final long UPDATE_INTERVAL = 100L;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel level)) {
            return;
        }

        if (level.getGameTime() % UPDATE_INTERVAL != 0) {
            return;
        }

        pitmanager.get(level).tick(level);
    }
}
