
package com.k.sosxbiomc.network.packet;

import com.k.sosxbiomc.mutation.mutationdata;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class mutationstagesync {

    private final int entityId;
    private final int stage;

    public mutationstagesync(int entityId, int stage) {
        this.entityId = entityId;
        this.stage = stage;
    }

    public static void encode(
            mutationstagesync message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeVarInt(message.entityId);
        buffer.writeVarInt(message.stage);
    }

    public static mutationstagesync decode(FriendlyByteBuf buffer) {
        return new mutationstagesync(
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    public static void handle(
            mutationstagesync message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT,
                        () -> () -> {
                            Minecraft minecraft = Minecraft.getInstance();

                            if (minecraft.level == null) {
                                return;
                            }

                            Entity entity = minecraft.level.getEntity(
                                    message.entityId
                            );

                            if (entity instanceof Cow cow) {
                                mutationdata.setStage(cow, message.stage);
                            }
                        }
                )
        );

        context.setPacketHandled(true);
    }
}
