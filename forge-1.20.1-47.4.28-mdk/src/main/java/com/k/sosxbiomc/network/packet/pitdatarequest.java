
package com.k.sosxbiomc.network.packet;

import com.k.sosxbiomc.item.third_eye_item;
import com.k.sosxbiomc.mutation.mutationdata;
import com.k.sosxbiomc.network.guinetwork;
import com.k.sosxbiomc.pit.pit;
import com.k.sosxbiomc.pit.pitmanager;
import com.k.sosxbiomc.pit.pitresources;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import top.theillusivec4.curios.api.CuriosApi;

import java.util.Optional;
import java.util.function.Supplier;

public class pitdatarequest {

    private final boolean entityTarget;
    private final BlockPos targetPos;
    private final int entityId;

    public pitdatarequest(BlockPos targetPos) {
        this.entityTarget = false;
        this.targetPos = targetPos;
        this.entityId = -1;
    }

    public pitdatarequest(int entityId) {
        this.entityTarget = true;
        this.targetPos = BlockPos.ZERO;
        this.entityId = entityId;
    }

    public static void encode(
            pitdatarequest message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBoolean(message.entityTarget);

        if (message.entityTarget) {
            buffer.writeVarInt(message.entityId);
        } else {
            buffer.writeBlockPos(message.targetPos);
        }
    }

    public static pitdatarequest decode(FriendlyByteBuf buffer) {

        boolean entityTarget = buffer.readBoolean();

        if (entityTarget) {
            return new pitdatarequest(buffer.readVarInt());
        }

        return new pitdatarequest(buffer.readBlockPos());
    }

    public static void handle(
            pitdatarequest message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {

            ServerPlayer player = context.getSender();

            if (player == null) {
                return;
            }

            boolean hasThirdEye = CuriosApi.getCuriosInventory(player)
                    .resolve()
                    .map(handler -> handler.findCurios(
                            stack -> stack.getItem() instanceof third_eye_item
                    ).stream().anyMatch(result ->
                            "head".equals(result.slotContext().identifier())
                    ))
                    .orElse(false);

            if (!hasThirdEye) {
                sendInvalidResponse(player);
                return;
            }

            ServerLevel level = player.serverLevel();

            if (message.entityTarget) {

                Entity entity = level.getEntity(message.entityId);

                if (!(entity instanceof Cow cow)
                        || !mutationdata.isInfected(cow)
                        || !isValidEntityTarget(player, level, cow)) {
                    sendInvalidResponse(player);
                    return;
                }

                double bec = mutationdata.getBEC(cow);
                int stage = mutationdata.getStage(cow);
                double progress = mutationdata.getProgress(cow);

                double secondsRemaining;

                if (stage >= 1) {
                    secondsRemaining = 0.0;
                } else {
                    double progressPerSecond =
                            Math.pow(bec, 1.15) / 100.0;

                    if (progressPerSecond <= 0.0) {
                        secondsRemaining = Double.POSITIVE_INFINITY;
                    } else {
                        secondsRemaining =
                                Math.max(0.0,
                                        mutationdata.STAGE_1_THRESHOLD - progress)
                                        / progressPerSecond;
                    }
                }

                guinetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new pitdataresponse(
                                true,
                                bec,
                                stage,
                                secondsRemaining
                        )
                );

                return;
            }

            if (!isValidBlockTarget(player, level, message.targetPos)) {
                sendInvalidResponse(player);
                return;
            }

            Optional<pit> target =
                    pitmanager.get(level).getPitAt(message.targetPos);

            if (target.isEmpty()) {
                sendInvalidResponse(player);
                return;
            }

            pit targetPit = target.get();

            int size = targetPit.getSize();
            int pressure = pitresources.getPressure(targetPit);
            int intensity = targetPit.getIntensity();

            double generation =
                    pitresources.getPressureGrowth(intensity);

            guinetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new pitdataresponse(
                            true,
                            size,
                            pressure,
                            intensity,
                            generation
                    )
            );
        });

        context.setPacketHandled(true);
    }

    private static boolean isValidBlockTarget(
            ServerPlayer player,
            ServerLevel level,
            BlockPos targetPos
    ) {
        if (!level.hasChunkAt(targetPos)) {
            return false;
        }

        double reach = player.getAttributeValue(
                ForgeMod.BLOCK_REACH.get()
        );

        if (reach <= 0.0) {
            return false;
        }

        var start = player.getEyePosition();
        var end = start.add(
                player.getViewVector(1.0F).scale(reach)
        );

        BlockHitResult hit = level.clip(
                new ClipContext(
                        start,
                        end,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.SOURCE_ONLY,
                        player
                )
        );

        return hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(targetPos);
    }

    private static boolean isValidEntityTarget(
            ServerPlayer player,
            ServerLevel level,
            Cow target
    ) {
        if (!target.isAlive() || target.level() != level) {
            return false;
        }

        double reach = player.getAttributeValue(
                ForgeMod.ENTITY_REACH.get()
        );

        if (reach <= 0.0) {
            return false;
        }

        var start = player.getEyePosition();
        var end = start.add(
                player.getViewVector(1.0F).scale(reach)
        );

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                start,
                end,
                player.getBoundingBox()
                        .expandTowards(end.subtract(start))
                        .inflate(1.0D),
                entity -> entity.isPickable(),
                reach * reach
        );

        if (entityHit == null || entityHit.getEntity() != target) {
            return false;
        }

        // Prevent inspecting a cow through a solid block.
        double entityDistance = start.distanceToSqr(
                entityHit.getLocation()
        );

        double blockReach = player.getAttributeValue(
                ForgeMod.BLOCK_REACH.get()
        );

        var blockEnd = start.add(
                player.getViewVector(1.0F).scale(blockReach)
        );

        BlockHitResult blockHit = level.clip(
                new ClipContext(
                        start,
                        blockEnd,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.SOURCE_ONLY,
                        player
                )
        );

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            double blockDistance = start.distanceToSqr(
                    blockHit.getLocation()
            );

            if (blockDistance < entityDistance) {
                return false;
            }
        }

        return true;
    }

    private static void sendInvalidResponse(ServerPlayer player) {
        guinetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new pitdataresponse(false, 0, 0, 0, 0.0)
        );
    }
}
