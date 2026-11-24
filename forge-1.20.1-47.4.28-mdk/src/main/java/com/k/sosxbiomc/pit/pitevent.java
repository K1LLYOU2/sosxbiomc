package com.k.sosxbiomc.pit;

import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pit lifecycle events, posted on MinecraftForge.EVENT_BUS (server thread).
 * Not cancelable: the change has already been committed when these fire.
 *
 * Pit objects passed in events are live. Store the UUID, not the object,
 * and call pitmanager#getPit later.
 */
public abstract class pitevent extends Event {

    private final ServerLevel level;

    private pitevent(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel getLevel() {
        return level;
    }

    /** A new pit formed with no ancestry. */
    public static class created extends pitevent {
        private final pit created;

        created(ServerLevel level, pit created) {
            super(level);
            this.created = created;
        }

        public pit getPit() {
            return created;
        }
    }

    /**
     * A pit no longer exists (drained or dissolved below the minimum size).
     * Snapshots are provided so listeners can react, e.g. drop the stock.
     */
    public static class destroyed extends pitevent {
        private final UUID pitId;
        private final LongSet lastBlocks;
        private final Map<ResourceLocation, Integer> lastResources;

        destroyed(ServerLevel level, UUID pitId, LongSet lastBlocks,
                  Map<ResourceLocation, Integer> lastResources) {
            super(level);
            this.pitId = pitId;
            this.lastBlocks = lastBlocks;
            this.lastResources = Collections.unmodifiableMap(lastResources);
        }

        public UUID getPitId() {
            return pitId;
        }

        public LongSet getLastBlocks() {
            return lastBlocks;
        }

        public Map<ResourceLocation, Integer> getLastResources() {
            return lastResources;
        }
    }

    /** Several pits became one. Absorbed pits no longer exist. */
    public static class merged extends pitevent {
        private final pit survivor;
        private final Set<UUID> absorbedIds;

        merged(ServerLevel level, pit survivor, Set<UUID> absorbedIds) {
            super(level);
            this.survivor = survivor;
            this.absorbedIds = Collections.unmodifiableSet(absorbedIds);
        }

        public pit getSurvivor() {
            return survivor;
        }

        public Set<UUID> getAbsorbedIds() {
            return absorbedIds;
        }
    }

    /**
     * One pit became several. The piece whose id equals getOriginId() kept
     * the original identity (if any piece did).
     */
    public static class split extends pitevent {
        private final UUID originId;
        private final List<pit> pieces;

        split(ServerLevel level, UUID originId, List<pit> pieces) {
            super(level);
            this.originId = originId;
            this.pieces = Collections.unmodifiableList(pieces);
        }

        public UUID getOriginId() {
            return originId;
        }

        public List<pit> getPieces() {
            return pieces;
        }
    }

    /** Same pit identity, different block set. */
    public static class resized extends pitevent {
        private final pit resized;
        private final int oldSize;
        private final int newSize;

        resized(ServerLevel level, pit resized, int oldSize, int newSize) {
            super(level);
            this.resized = resized;
            this.oldSize = oldSize;
            this.newSize = newSize;
        }

        public pit getPit() {
            return resized;
        }

        public int getOldSize() {
            return oldSize;
        }

        public int getNewSize() {
            return newSize;
        }
    }

    /** A registered pit's intensity changed. */
    public static class intensitychanged extends pitevent {
        private final pit changed;
        private final int oldIntensity;
        private final int newIntensity;

        intensitychanged(ServerLevel level, pit changed, int oldIntensity, int newIntensity) {
            super(level);
            this.changed = changed;
            this.oldIntensity = oldIntensity;
            this.newIntensity = newIntensity;
        }

        public pit getPit() {
            return changed;
        }

        public int getOldIntensity() {
            return oldIntensity;
        }

        public int getNewIntensity() {
            return newIntensity;
        }
    }
}