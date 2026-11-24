
package com.k.sosxbiomc.mutation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

public class mutationdata {

    private static final String DATA_KEY = "sosxbiomc_mutation";

    public static final double MAX_BEC = 100.0;
    public static final double BEC_PER_DOSE = 5.0;
    public static final double PASSIVE_BEC_GROWTH = 1.1;

    public static final int BEC_GROWTH_INTERVAL = 800;
    public static final double STAGE_1_THRESHOLD = 100.0;

    public enum Status {
        UNINFECTED,
        MUTATING,
        COMPLETE
    }

    private mutationdata() {
    }

    private static CompoundTag getData(LivingEntity entity) {
        CompoundTag persistentData = entity.getPersistentData();

        if (!persistentData.contains(DATA_KEY)) {
            persistentData.put(DATA_KEY, new CompoundTag());
        }

        return persistentData.getCompound(DATA_KEY);
    }

    public static boolean isInfected(LivingEntity entity) {
        return getData(entity).getBoolean("infected");
    }

    public static Status getStatus(LivingEntity entity) {
        CompoundTag data = getData(entity);

        if (!data.getBoolean("infected")) {
            return Status.UNINFECTED;
        }

        try {
            return Status.valueOf(data.getString("status"));
        } catch (IllegalArgumentException exception) {
            return Status.MUTATING;
        }
    }

    public static void setStatus(LivingEntity entity, Status status) {
        getData(entity).putString("status", status.name());
    }

    public static double getBEC(LivingEntity entity) {
        return getData(entity).getDouble("bec");
    }

    public static void setBEC(LivingEntity entity, double amount) {
        double clamped = Math.max(0.0, Math.min(MAX_BEC, amount));
        getData(entity).putDouble("bec", clamped);
    }

    public static double addBEC(LivingEntity entity, double amount) {
        double oldBEC = getBEC(entity);
        double newBEC = Math.min(MAX_BEC, oldBEC + Math.max(0.0, amount));

        setBEC(entity, newBEC);

        return newBEC - oldBEC;
    }

    public static double inject(LivingEntity entity) {
        CompoundTag data = getData(entity);

        if (!data.getBoolean("infected")) {
            long currentTick = entity.level().getGameTime();

            data.putBoolean("infected", true);
            data.putString("status", Status.MUTATING.name());
            data.putDouble("bec", 0.0);
            data.putDouble("progress", 0.0);
            data.putInt("stage", 0);

            data.putLong("last_bec_growth_tick", currentTick);
            data.putLong("last_progress_tick", currentTick);
        }

        return addBEC(entity, BEC_PER_DOSE);
    }

    public static double getProgress(LivingEntity entity) {
        return getData(entity).getDouble("progress");
    }

    public static void setProgress(LivingEntity entity, double progress) {
        getData(entity).putDouble("progress", Math.max(0.0, progress));
    }

    public static void addProgress(LivingEntity entity, double amount) {
        setProgress(entity, getProgress(entity) + Math.max(0.0, amount));
    }

    public static int getStage(LivingEntity entity) {
        return getData(entity).getInt("stage");
    }

    public static void setStage(LivingEntity entity, int stage) {
        CompoundTag data = getData(entity);
        int newStage = Math.max(0, stage);

        data.putInt("stage", newStage);

        if (newStage >= 1) {
            data.putString("status", Status.COMPLETE.name());
        } else if (data.getBoolean("infected")) {
            data.putString("status", Status.MUTATING.name());
        }
    }

    public static long getLastBECGrowthTick(LivingEntity entity) {
        return getData(entity).getLong("last_bec_growth_tick");
    }

    public static void setLastBECGrowthTick(LivingEntity entity, long tick) {
        getData(entity).putLong("last_bec_growth_tick", tick);
    }

    public static long getLastProgressTick(LivingEntity entity) {
        return getData(entity).getLong("last_progress_tick");
    }

    public static void setLastProgressTick(LivingEntity entity, long tick) {
        getData(entity).putLong("last_progress_tick", tick);
    }
}
