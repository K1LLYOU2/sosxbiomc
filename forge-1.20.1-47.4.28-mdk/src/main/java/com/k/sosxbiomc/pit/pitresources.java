
package com.k.sosxbiomc.pit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class pitresources {

    public static final ResourceLocation PIT_PRESSURE =
            new ResourceLocation("sosxbiomc", "pit_pressure");

    private static final long UPDATE_INTERVAL = 100L;
    private static final double TICKS_PER_SECOND = 20.0;

    private static final double BASE_GROWTH = 4.0;
    private static final double INTENSITY_MULTIPLIER = 1.3;
    private static final double INTENSITY_ADDITION = 1.5;

    private static final Map<UUID, Long> lastUpdateTimes =
            new HashMap<>();

    private static final Map<UUID, Double> pressureRemainders =
            new HashMap<>();

    public static double getPressureGrowth(int intensity) {
        int x = Math.max(0, Math.min(pit.MAX_INTENSITY, intensity));

        return BASE_GROWTH
                + Math.pow(INTENSITY_MULTIPLIER, x)
                + INTENSITY_ADDITION * x;
    }

    /**
     * Updates pressure when at least 100 ticks have elapsed.
     * Uses actual elapsed time to account for server lag.
     */
    public static void tick(pit target, ServerLevel level) {
        if (target == null || level == null) {
            return;
        }

        UUID id = target.getId();
        long currentTime = level.getGameTime();

        Long lastUpdate = lastUpdateTimes.get(id);

        // First update establishes the starting timestamp.
        if (lastUpdate == null) {
            lastUpdateTimes.put(id, currentTime);
            return;
        }

        long elapsedTicks = currentTime - lastUpdate;

        if (elapsedTicks < UPDATE_INTERVAL) {
            return;
        }

        lastUpdateTimes.put(id, currentTime);

        double elapsedSeconds = elapsedTicks / TICKS_PER_SECOND;
        double growth = getPressureGrowth(target.getIntensity());

        double accumulated =
                pressureRemainders.getOrDefault(id, 0.0);

        accumulated += growth * elapsedSeconds;

        int wholePressure = (int) accumulated;

        if (wholePressure > 0) {
            target.addResource(PIT_PRESSURE, wholePressure);
            accumulated -= wholePressure;
        }

        pressureRemainders.put(id, accumulated);
    }

    public static int getPressure(pit target) {
        return target == null
                ? 0
                : target.getResource(PIT_PRESSURE);
    }

    public static boolean spendPressure(pit target, int amount) {
        if (target == null || amount <= 0) {
            return false;
        }

        return target.removeResource(PIT_PRESSURE, amount);
    }

    public static void forgetPit(UUID id) {
        if (id == null) {
            return;
        }

        lastUpdateTimes.remove(id);
        pressureRemainders.remove(id);
    }
}
