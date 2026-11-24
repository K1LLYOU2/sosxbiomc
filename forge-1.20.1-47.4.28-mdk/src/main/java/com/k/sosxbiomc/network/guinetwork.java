
package com.k.sosxbiomc.network;

import com.k.sosxbiomc.SOSxBiomc;
import com.k.sosxbiomc.network.packet.pitdatarequest;
import com.k.sosxbiomc.network.packet.pitdataresponse;
import com.k.sosxbiomc.network.packet.mutationstagesync;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class guinetwork {

    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL =
            NetworkRegistry.newSimpleChannel(
                    new ResourceLocation(SOSxBiomc.MOD_ID, "main"),
                    () -> PROTOCOL_VERSION,
                    PROTOCOL_VERSION::equals,
                    PROTOCOL_VERSION::equals
            );

    private static int packetId = 0;

    public static void register() {

        CHANNEL.registerMessage(
                packetId++,
                pitdatarequest.class,
                pitdatarequest::encode,
                pitdatarequest::decode,
                pitdatarequest::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                pitdataresponse.class,
                pitdataresponse::encode,
                pitdataresponse::decode,
                pitdataresponse::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                mutationstagesync.class,
                mutationstagesync::encode,
                mutationstagesync::decode,
                mutationstagesync::handle
        );
    }
}
