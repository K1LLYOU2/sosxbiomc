
package com.k.sosxbiomc.network.packet;

import com.k.sosxbiomc.client.gui.third_eye_gui;

import net.minecraft.network.FriendlyByteBuf;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class pitdataresponse {

    private final boolean valid;
    private final boolean mutationTarget;

    // Pit information
    private final int size;
    private final int pressure;
    private final int intensity;
    private final double generation;

    // Mutation information
    private final double bec;
    private final int stage;
    private final double secondsRemaining;

    // Pit response
    public pitdataresponse(
            boolean valid,
            int size,
            int pressure,
            int intensity,
            double generation
    ) {
        this.valid = valid;
        this.mutationTarget = false;

        this.size = size;
        this.pressure = pressure;
        this.intensity = intensity;
        this.generation = generation;

        this.bec = 0.0;
        this.stage = 0;
        this.secondsRemaining = 0.0;
    }

    // Mutation response
    public pitdataresponse(
            boolean valid,
            double bec,
            int stage,
            double secondsRemaining
    ) {
        this.valid = valid;
        this.mutationTarget = true;

        this.size = 0;
        this.pressure = 0;
        this.intensity = 0;
        this.generation = 0.0;

        this.bec = bec;
        this.stage = stage;
        this.secondsRemaining = secondsRemaining;
    }

    public static void encode(
            pitdataresponse message,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBoolean(message.valid);
        buffer.writeBoolean(message.mutationTarget);

        buffer.writeInt(message.size);
        buffer.writeInt(message.pressure);
        buffer.writeInt(message.intensity);
        buffer.writeDouble(message.generation);

        buffer.writeDouble(message.bec);
        buffer.writeInt(message.stage);
        buffer.writeDouble(message.secondsRemaining);
    }

    public static pitdataresponse decode(FriendlyByteBuf buffer) {

        boolean valid = buffer.readBoolean();
        boolean mutationTarget = buffer.readBoolean();

        int size = buffer.readInt();
        int pressure = buffer.readInt();
        int intensity = buffer.readInt();
        double generation = buffer.readDouble();

        double bec = buffer.readDouble();
        int stage = buffer.readInt();
        double secondsRemaining = buffer.readDouble();

        if (mutationTarget) {
            return new pitdataresponse(
                    valid,
                    bec,
                    stage,
                    secondsRemaining
            );
        }

        return new pitdataresponse(
                valid,
                size,
                pressure,
                intensity,
                generation
        );
    }

    public static void handle(
            pitdataresponse message,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT,
                        () -> () -> {

                            if (!message.valid) {
                                third_eye_gui.clearPitData();
                                return;
                            }

                            if (message.mutationTarget) {
                                third_eye_gui.updateMutationData(
                                        message.bec,
                                        message.stage,
                                        message.secondsRemaining
                                );
                            } else {
                                third_eye_gui.updatePitData(
                                        message.size,
                                        message.pressure,
                                        message.intensity,
                                        message.generation
                                );
                            }
                        }
                )
        );

        context.setPacketHandled(true);
    }
}
