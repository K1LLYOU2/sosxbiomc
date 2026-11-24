
package com.k.sosxbiomc.pit;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class pit {

    public static final int MAX_INTENSITY = 10;

    private final UUID id;
    private final LongSet blocks;
    private final LongSet infectedFlesh = new LongOpenHashSet();

    private final ArrayDeque<Long> spreadFrontier = new ArrayDeque<>();
    private final LongSet queuedSources = new LongOpenHashSet();

    private final Map<ResourceLocation, Integer> resources = new HashMap<>();

    private int intensity;

    private double centerX;
    private double centerY;
    private double centerZ;

    private double maxRadiusSquared;

    private long nextSpreadAttemptTick = Long.MIN_VALUE;

    private Runnable onChange = () -> {};
    private BiConsumer<pit, Integer> onIntensityChange = (changed, old) -> {};

    pit(UUID id, LongSet blocks) {
        this.id = id;
        this.blocks = new LongOpenHashSet(blocks);

        calculateCenter();

        for (long block : this.blocks) {
            enqueueSpreadSource(block);
        }

        updateMaxRadius();
    }

    void setChangeListener(Runnable listener) {
        this.onChange = listener;
    }

    void setIntensityListener(BiConsumer<pit, Integer> listener) {
        this.onIntensityChange = listener;
    }

    public UUID getId() {
        return id;
    }

    public LongSet getBlocks() {
        return LongSets.unmodifiable(blocks);
    }

    public int getSize() {
        return blocks.size();
    }

    // ---------- infected flesh ----------

    public LongSet getInfectedFleshBlocks() {
        return LongSets.unmodifiable(infectedFlesh);
    }

    public void addInfectedFleshBlock(long position) {
        if (infectedFlesh.add(position)) {
            enqueueSpreadSource(position);
            updateRadius(position);
            onChange.run();
        }
    }

    public void addAllInfectedFleshBlocks(LongSet positions) {
        if (positions == null || positions.isEmpty()) return;

        boolean changed = infectedFlesh.addAll(positions);

        if (changed) {
            for (long position : positions) {
                enqueueSpreadSource(position);
                updateRadius(position);
            }

            onChange.run();
        }
    }

    public void removeInfectedFleshBlock(long position) {
        if (infectedFlesh.remove(position)) {
            onChange.run();
        }
    }

    // ---------- spherical spreading ----------

    private void calculateCenter() {
        if (blocks.isEmpty()) return;

        double x = 0;
        double y = 0;
        double z = 0;

        for (long block : blocks) {
            BlockPos pos = BlockPos.of(block);

            x += pos.getX();
            y += pos.getY();
            z += pos.getZ();
        }

        double count = blocks.size();

        centerX = x / count;
        centerY = y / count;
        centerZ = z / count;
    }

    private double distanceSquared(long position) {
        BlockPos pos = BlockPos.of(position);

        double dx = pos.getX() - centerX;
        double dy = pos.getY() - centerY;
        double dz = pos.getZ() - centerZ;

        return dx * dx + dy * dy + dz * dz;
    }

    private void updateRadius(long position) {
        maxRadiusSquared = Math.max(
                maxRadiusSquared,
                distanceSquared(position)
        );
    }

    private void updateMaxRadius() {
        maxRadiusSquared = 0;

        for (long block : blocks) {
            updateRadius(block);
        }

        for (long block : infectedFlesh) {
            updateRadius(block);
        }
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public double getMaxRadiusSquared() {
        return maxRadiusSquared;
    }

    public double getDistanceSquared(long position) {
        return distanceSquared(position);
    }

    public boolean tryBeginSpreadAttempt(long gameTime) {
        if (gameTime < nextSpreadAttemptTick) {
            return false;
        }

        nextSpreadAttemptTick = gameTime + 10;
        return true;
    }

    // ---------- spread frontier ----------

    private void enqueueSpreadSource(long position) {
        if (queuedSources.add(position)) {
            spreadFrontier.addLast(position);
        }
    }

    public Long pollSpreadSource() {
        Long position = spreadFrontier.pollFirst();

        if (position != null) {
            queuedSources.remove(position);
        }

        return position;
    }

    public void requeueSpreadSource(long position) {
        enqueueSpreadSource(position);
    }

    /**
     * Returns the current number of sources in the frontier.
     */
    public int getSpreadFrontierSize() {
        return spreadFrontier.size();
    }

    /**
     * Returns the frontier in its current rotation order.
     */
    public long[] getSpreadFrontierSnapshot() {
        long[] frontier = new long[spreadFrontier.size()];
        int index = 0;

        for (long position : spreadFrontier) {
            frontier[index++] = position;
        }

        return frontier;
    }

    /**
     * Restores the center and frontier from saved state.
     */
    public void restoreSpreadState(
            double savedCenterX,
            double savedCenterY,
            double savedCenterZ,
            long[] savedFrontier
    ) {
        centerX = savedCenterX;
        centerY = savedCenterY;
        centerZ = savedCenterZ;

        spreadFrontier.clear();
        queuedSources.clear();

        if (savedFrontier != null) {
            for (long position : savedFrontier) {
                if (blocks.contains(position) || infectedFlesh.contains(position)) {
                    enqueueSpreadSource(position);
                }
            }
        }

        // Compatibility fallback for missing or empty frontier data.
        if (spreadFrontier.isEmpty()) {
            for (long block : blocks) {
                enqueueSpreadSource(block);
            }

            for (long block : infectedFlesh) {
                enqueueSpreadSource(block);
            }
        }

        updateMaxRadius();
    }

    /**
     * Transfers the original center and spreading frontier
     * when the manager rebuilds this Pit.
     *
     * Preserves the old frontier's rotation order while removing
     * sources that do not belong to this surviving component.
     */
    public void inheritSpreadState(pit old) {
        if (old == null) return;

        centerX = old.centerX;
        centerY = old.centerY;
        centerZ = old.centerZ;

        infectedFlesh.addAll(old.infectedFlesh);

        spreadFrontier.clear();
        queuedSources.clear();

        // Preserve the old queue's current order first.
        for (long position : old.spreadFrontier) {
            if (blocks.contains(position) || infectedFlesh.contains(position)) {
                enqueueSpreadSource(position);
            }
        }

        // Append any new or previously unqueued Blood Ether blocks.
        for (long block : blocks) {
            enqueueSpreadSource(block);
        }

        // Append any new or previously unqueued infected Flesh.
        for (long block : infectedFlesh) {
            enqueueSpreadSource(block);
        }

        updateMaxRadius();
        onChange.run();
    }

    // ---------- intensity ----------

    public int getIntensity() {
        return intensity;
    }

    public void setIntensity(int value) {
        int clamped = clampIntensity(value);

        if (clamped == intensity) return;

        int old = intensity;
        intensity = clamped;

        onChange.run();
        onIntensityChange.accept(this, old);
    }

    public void addIntensity(int delta) {
        setIntensity((int) Math.max(
                0L,
                Math.min(MAX_INTENSITY, (long) intensity + delta)
        ));
    }

    public boolean isMaxIntensity() {
        return intensity >= MAX_INTENSITY;
    }

    private static int clampIntensity(int value) {
        return Math.max(0, Math.min(MAX_INTENSITY, value));
    }

    // ---------- resources ----------

    public int getResource(ResourceLocation type) {
        if (type == null) return 0;
        return resources.getOrDefault(type, 0);
    }

    public Map<ResourceLocation, Integer> getResources() {
        return Collections.unmodifiableMap(resources);
    }

    public void addResource(ResourceLocation type, int amount) {
        if (type == null || amount <= 0) return;

        int current = resources.getOrDefault(type, 0);
        long combined = (long) current + amount;

        resources.put(type, (int) Math.min(Integer.MAX_VALUE, combined));
        onChange.run();
    }

    public boolean removeResource(ResourceLocation type, int amount) {
        if (type == null) return false;

        int have = getResource(type);

        if (amount <= 0 || have < amount) return false;

        if (have == amount) {
            resources.remove(type);
        } else {
            resources.put(type, have - amount);
        }

        onChange.run();
        return true;
    }

    // ---------- persistence ----------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putUUID("Id", id);
        tag.putInt("Intensity", intensity);

        tag.putLongArray("Blocks", blocks.toLongArray());
        tag.putLongArray("InfectedFlesh", infectedFlesh.toLongArray());

        tag.putDouble("CenterX", centerX);
        tag.putDouble("CenterY", centerY);
        tag.putDouble("CenterZ", centerZ);

        // Array order preserves the frontier's current rotation.
        tag.putLongArray("SpreadFrontier", getSpreadFrontierSnapshot());

        ListTag list = new ListTag();

        resources.forEach((type, count) -> {
            CompoundTag entry = new CompoundTag();

            entry.putString("Type", type.toString());
            entry.putInt("Count", count);

            list.add(entry);
        });

        tag.put("Resources", list);

        return tag;
    }

    public static pit load(CompoundTag tag) {
        pit loaded = new pit(
                tag.getUUID("Id"),
                new LongOpenHashSet(tag.getLongArray("Blocks"))
        );

        loaded.intensity = clampIntensity(tag.getInt("Intensity"));

        loaded.infectedFlesh.addAll(
                new LongOpenHashSet(tag.getLongArray("InfectedFlesh"))
        );

        double savedCenterX = loaded.centerX;
        double savedCenterY = loaded.centerY;
        double savedCenterZ = loaded.centerZ;

        if (tag.contains("CenterX") && tag.contains("CenterY")
                && tag.contains("CenterZ")) {

            savedCenterX = tag.getDouble("CenterX");
            savedCenterY = tag.getDouble("CenterY");
            savedCenterZ = tag.getDouble("CenterZ");
        }

        loaded.restoreSpreadState(
                savedCenterX,
                savedCenterY,
                savedCenterZ,
                tag.getLongArray("SpreadFrontier")
        );

        ListTag list = tag.getList("Resources", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            ResourceLocation type = ResourceLocation.tryParse(
                    entry.getString("Type")
            );

            int count = entry.getInt("Count");

            if (type != null && count > 0) {
                loaded.resources.put(type, count);
            }
        }

        return loaded;
    }
}
