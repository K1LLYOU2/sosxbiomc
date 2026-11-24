package com.k.sosxbiomc.pit;

import com.github.elenterius.biomancy.init.ModBlocks;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class pitactions {

    private static final int BLOCK_INFECT_THRESHOLD = 15;

    private static final Direction[] DIRECTIONS = Direction.values();

    public static void tick(pit target, ServerLevel level) {
        if (target == null || level == null) return;

        if (!target.tryBeginSpreadAttempt(level.getGameTime())) {
            return;
        }

        block_infect(level, target);
    }

    /**
     * Attempts to infect one block using the entire rotating frontier.
     * Candidate blocks are prioritized by their distance from
     * the Pit's original three-dimensional center.
     */
    public static boolean block_infect(ServerLevel level, pit target) {
        if (level == null || target == null) {
            return false;
        }

        int pressure = pitresources.getPressure(target);

        if (pressure <= BLOCK_INFECT_THRESHOLD) {
            return false;
        }

        long bestPosition = 0;
        int bestCost = 0;
        double bestScore = Double.MAX_VALUE;

        boolean foundCandidate = false;

        LongSet checkedCandidates = new LongOpenHashSet();

        int checkedSources = 0;
        int sourceLimit = target.getSpreadFrontierSize();

        while (checkedSources < sourceLimit) {
            Long sourceValue = target.pollSpreadSource();

            if (sourceValue == null) {
                break;
            }

            long source = sourceValue;
            checkedSources++;

            BlockPos origin = BlockPos.of(source);

            if (!level.hasChunkAt(origin)) {
                target.requeueSpreadSource(source);
                continue;
            }

            boolean isOriginalPit = target.getBlocks().contains(source);
            boolean isInfectedFlesh =
                    target.getInfectedFleshBlocks().contains(source);

            if (isInfectedFlesh
                    && !level.getBlockState(origin).is(ModBlocks.FLESH.get())) {

                target.removeInfectedFleshBlock(source);
                continue;
            }

            if (!isOriginalPit && !isInfectedFlesh) {
                continue;
            }

            for (Direction direction : DIRECTIONS) {
                BlockPos candidate = origin.relative(direction);

                if (level.isOutsideBuildHeight(candidate)) {
                    continue;
                }

                if (!level.hasChunkAt(candidate)) {
                    continue;
                }

                long candidateLong = candidate.asLong();

                if (!checkedCandidates.add(candidateLong)) {
                    continue;
                }

                BlockState state = level.getBlockState(candidate);

                int cost = infectability.getInfectionCost(state);

                // Skip expensive blocks and continue searching around them.
                if (cost <= 0 || cost > pressure) {
                    continue;
                }

                double distanceSquared =
                        target.getDistanceSquared(candidateLong);

                // Prefer candidates closest to the Pit's center.
                double score = distanceSquared;

                // Tiny deterministic jitter to break ties.
                score += ((candidateLong * 0x9E3779B97F4A7C15L) >>> 48)
                        / 65536.0 * 0.25;

                if (!foundCandidate || score < bestScore) {
                    foundCandidate = true;
                    bestScore = score;
                    bestPosition = candidateLong;
                    bestCost = cost;
                }
            }

            // Rotate this source to the back of the frontier.
            target.requeueSpreadSource(source);
        }

        if (!foundCandidate) {
            return false;
        }

        BlockPos selected = BlockPos.of(bestPosition);

        boolean converted = level.setBlock(
                selected,
                ModBlocks.FLESH.get().defaultBlockState(),
                3
        );

        if (!converted) {
            return false;
        }

        target.addInfectedFleshBlock(bestPosition);

        pitresources.spendPressure(target, bestCost);

        return true;
    }
}