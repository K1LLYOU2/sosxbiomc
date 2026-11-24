package com.k.sosxbiomc.pit;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class infectability {

    private static final int NOT_INFECTABLE = -1;

    // Individual block infection costs
    private static final Map<Block, Integer> DEFAULT_COSTS =
            new HashMap<>();

    // Tag-based infection costs
    private static final Map<TagKey<Block>, Integer> TAG_COSTS =
            new HashMap<>();

    static {
    // Individual block defaults
    registerDefault(Blocks.DIRT, 7);
    registerDefault(Blocks.GRASS_BLOCK, 5);
    registerDefault(Blocks.STONE, 20);

    // Minecraft tag defaults
    registerTagCost(createTag("minecraft", "dirt"), 7);
    registerTagCost(createTag("minecraft", "stone_ore_replaceables"), 20);
    }

    private static TagKey<Block> createTag(String namespace, String name) {
        return TagKey.create(
                Registries.BLOCK,
                new ResourceLocation(namespace, name)
        );
    }

    private static void registerDefault(Block block, int cost) {
        if (block != null && cost > 0) {
            DEFAULT_COSTS.put(block, cost);
        }
    }

    private static void registerTagCost(TagKey<Block> tag, int cost) {
        if (tag != null && cost > 0) {
            TAG_COSTS.put(tag, cost);
        }
    }

    /**
     * Gets the infection cost of a block state.
     *
     * Individual block costs take priority over tag costs.
     * If multiple tags match, the highest cost wins.
     *
     * Returns -1 if the block is not infectable.
     */
    public static int getInfectionCost(BlockState state) {
        if (state == null) {
            return NOT_INFECTABLE;
        }

        Block block = state.getBlock();

        // Individual block override
        Integer blockCost = DEFAULT_COSTS.get(block);

        if (blockCost != null) {
            return blockCost;
        }

        // Tag-based costs
        int highestCost = NOT_INFECTABLE;

        for (Map.Entry<TagKey<Block>, Integer> entry : TAG_COSTS.entrySet()) {
            if (state.is(entry.getKey())) {
                highestCost = Math.max(highestCost, entry.getValue());
            }
        }

        return highestCost;
    }

    public static boolean isInfectable(BlockState state) {
        return getInfectionCost(state) > 0;
    }

    public static Map<Block, Integer> getDefaultCosts() {
        return Collections.unmodifiableMap(DEFAULT_COSTS);
    }

    public static Map<TagKey<Block>, Integer> getTagCosts() {
        return Collections.unmodifiableMap(TAG_COSTS);
    }
}