package com.k.sosxbiomc.pit;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

import net.mcreator.sonsofsins.init.SonsOfSinsModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.FluidState;

public class pitdetection {

    public static final int MAX_PIT_SIZE = 4096;

    private static final Direction[] DIRECTIONS = Direction.values();

    public enum status {
        NOT_A_PIT,
        COMPLETE,
        TOO_LARGE,
        UNLOADED_BOUNDARY
    }

    private static boolean isBloodSource(FluidState fluid) {
        return fluid.isSource()
                && fluid.getType() == SonsOfSinsModFluids.BLOOD.get();
    }

    public static boolean isPitBlock(LevelReader level, BlockPos pos) {
        return level.hasChunkAt(pos)
                && isBloodSource(level.getFluidState(pos));
    }

    /**
     * Six-directional flood fill. Server thread only.
     * Non-COMPLETE results never contain a partial block set.
     */
    public static detectionresult floodFill(LevelReader level, BlockPos start) {

        if (!isPitBlock(level, start)) {
            return new detectionresult(status.NOT_A_PIT, LongSets.EMPTY_SET);
        }

        LongSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();

        long startLong = start.asLong();
        visited.add(startLong);
        queue.enqueue(startLong);

        MutableBlockPos current = new MutableBlockPos();
        MutableBlockPos neighbor = new MutableBlockPos();

        while (!queue.isEmpty()) {

            current.set(queue.dequeueLong());

            for (Direction direction : DIRECTIONS) {

                neighbor.setWithOffset(current, direction);

                if (level.isOutsideBuildHeight(neighbor)) {
                    continue;
                }

                long neighborLong = neighbor.asLong();

                if (visited.contains(neighborLong)) {
                    continue;
                }

                if (!level.hasChunkAt(neighbor)) {
                    return new detectionresult(
                            status.UNLOADED_BOUNDARY,
                            LongSets.EMPTY_SET
                    );
                }

                if (!isBloodSource(level.getFluidState(neighbor))) {
                    continue;
                }

                if (visited.size() >= MAX_PIT_SIZE) {
                    return new detectionresult(
                            status.TOO_LARGE,
                            LongSets.EMPTY_SET
                    );
                }

                visited.add(neighborLong);
                queue.enqueue(neighborLong);
            }
        }

        return new detectionresult(
                status.COMPLETE,
                LongSets.unmodifiable(visited)
        );
    }

    public static class detectionresult {

        private final status state;
        private final LongSet blocks;

        private detectionresult(status state, LongSet blocks) {
            this.state = state;
            this.blocks = blocks;
        }

        public status getStatus() {
            return state;
        }

        public boolean isComplete() {
            return state == status.COMPLETE;
        }

        public LongSet getBlocks() {
            return blocks;
        }

        public int getSize() {
            return blocks.size();
        }
    }
}