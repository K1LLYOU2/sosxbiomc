
package com.k.sosxbiomc.client.gui;

import com.k.sosxbiomc.SOSxBiomc;
import com.k.sosxbiomc.network.guinetwork;
import com.k.sosxbiomc.network.packet.pitdatarequest;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import net.minecraft.world.entity.projectile.ProjectileUtil;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = SOSxBiomc.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public class third_eye_target_handler {

    private static final int REQUEST_INTERVAL = 5;

    private static BlockPos lastTargetPos;
    private static Integer lastTargetEntityId;
    private static int lastRequestTick = -1;

    private static void clearTarget() {
        lastTargetPos = null;
        lastTargetEntityId = null;
        lastRequestTick = -1;
        third_eye_gui.clearPitData();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.level == null) {
            clearTarget();
            return;
        }

        if (minecraft.screen != null) {
            clearTarget();
            return;
        }

        var player = minecraft.player;

        double entityReach = player.getAttributeValue(
                ForgeMod.ENTITY_REACH.get()
        );

        double blockReach = player.getAttributeValue(
                ForgeMod.BLOCK_REACH.get()
        );

        var start = player.getEyePosition();

        // Check the block ray first.
        var blockEnd = start.add(
                player.getViewVector(1.0F).scale(blockReach)
        );

        BlockHitResult blockHit = minecraft.level.clip(
                new ClipContext(
                        start,
                        blockEnd,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.SOURCE_ONLY,
                        player
                )
        );

        // Check for a cow along the entity reach.
        var entityEnd = start.add(
                player.getViewVector(1.0F).scale(entityReach)
        );

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                start,
                entityEnd,
                player.getBoundingBox()
                        .expandTowards(entityEnd.subtract(start))
                        .inflate(1.0D),
                entity -> entity instanceof Cow && entity.isPickable(),
                entityReach * entityReach
        );

        // Prefer the cow if it is closer than the block.
        if (entityHit != null) {

            Entity target = entityHit.getEntity();

            double entityDistance = start.distanceToSqr(
                    entityHit.getLocation()
            );

            double blockDistance = blockHit.getType() == HitResult.Type.BLOCK
                    ? start.distanceToSqr(blockHit.getLocation())
                    : Double.MAX_VALUE;

            if (entityDistance <= blockDistance) {

                int entityId = target.getId();
                int currentTick = player.tickCount;

                boolean targetChanged =
                        lastTargetEntityId == null
                                || lastTargetEntityId != entityId;

                boolean intervalElapsed =
                        currentTick - lastRequestTick >= REQUEST_INTERVAL;

                if (targetChanged || intervalElapsed) {

                    guinetwork.CHANNEL.sendToServer(
                            new pitdatarequest(entityId)
                    );

                    lastTargetEntityId = entityId;
                    lastTargetPos = null;
                    lastRequestTick = currentTick;
                }

                return;
            }
        }

        // Otherwise inspect the block, preserving Pit functionality.
        if (blockHit.getType() == HitResult.Type.BLOCK) {

            BlockPos targetPos = blockHit.getBlockPos();
            int currentTick = player.tickCount;

            boolean targetChanged =
                    lastTargetPos == null
                            || !targetPos.equals(lastTargetPos);

            boolean intervalElapsed =
                    currentTick - lastRequestTick >= REQUEST_INTERVAL;

            if (targetChanged || intervalElapsed) {

                guinetwork.CHANNEL.sendToServer(
                        new pitdatarequest(targetPos)
                );

                lastTargetPos = targetPos;
                lastTargetEntityId = null;
                lastRequestTick = currentTick;
            }

            return;
        }

        clearTarget();
    }
}
