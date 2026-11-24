
package com.k.sosxbiomc.mutation;

import com.k.sosxbiomc.network.guinetwork;
import com.k.sosxbiomc.network.packet.mutationstagesync;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Cow;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import com.k.sosxbiomc.SOSxBiomc;

@Mod.EventBusSubscriber(
        modid = SOSxBiomc.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class mutation_sync_handler {

    // Sends the current stage to everyone tracking the cow, including its owner.
    public static void syncStage(Cow cow) {

        if (cow.level().isClientSide) {
            return;
        }

        guinetwork.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> cow),
                new mutationstagesync(
                        cow.getId(),
                        mutationdata.getStage(cow)
                )
        );
    }

    // Sends the stage to a player who starts tracking an already-infected cow.
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!(event.getTarget() instanceof Cow cow)) {
            return;
        }

        if (!mutationdata.isInfected(cow)) {
            return;
        }

        guinetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new mutationstagesync(
                        cow.getId(),
                        mutationdata.getStage(cow)
                )
        );
    }
}
