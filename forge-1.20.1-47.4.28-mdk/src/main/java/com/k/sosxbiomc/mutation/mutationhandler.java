
package com.k.sosxbiomc.mutation;

import net.minecraft.world.entity.animal.Cow;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.k.sosxbiomc.mutation.mutation_sync_handler;

public class mutationhandler {

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {

        // Server-side only.
        if (event.getEntity().level().isClientSide) {
            return;
        }

        // Cows only for the pre-alpha.
        if (!(event.getEntity() instanceof Cow cow)) {
            return;
        }

        if (!mutationdata.isInfected(cow)) {
            return;
        }

        long currentTick = cow.level().getGameTime();

        // PASSIVE BEC GROWTH
        long lastBECGrowth = mutationdata.getLastBECGrowthTick(cow);
        long elapsedBEC = currentTick - lastBECGrowth;

        if (elapsedBEC >= mutationdata.BEC_GROWTH_INTERVAL) {

            long intervals = elapsedBEC / mutationdata.BEC_GROWTH_INTERVAL;

            mutationdata.addBEC(
                    cow,
                    mutationdata.PASSIVE_BEC_GROWTH * intervals
            );

            mutationdata.setLastBECGrowthTick(
                    cow,
                    currentTick - (elapsedBEC % mutationdata.BEC_GROWTH_INTERVAL)
            );
        }

        // Stop mutation progression after Stage 1.
        if (mutationdata.getStage(cow) >= 1) {
            mutationdata.setLastProgressTick(cow, currentTick);
            return;
        }

        // MUTATION PROGRESSION
        double bec = mutationdata.getBEC(cow);

        double progressPerSecond = Math.pow(bec, 1.15) / 100.0;
        double progressThisTick = progressPerSecond / 20.0;

        mutationdata.addProgress(cow, progressThisTick);
        mutationdata.setLastProgressTick(cow, currentTick);

        // STAGE 1
        if (mutationdata.getProgress(cow) >= mutationdata.STAGE_1_THRESHOLD) {

            mutationdata.setProgress(
                    cow,
                    mutationdata.STAGE_1_THRESHOLD
            );

            mutationdata.setStage(cow, 1);
            mutation_sync_handler.syncStage(cow);
        }
    }
}
