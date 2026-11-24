
package com.k.sosxbiomc.client.gui;

import com.k.sosxbiomc.SOSxBiomc;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = SOSxBiomc.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public class third_eye_gui {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(
                    "sosxbiomc",
                    "textures/gui/third_eye_gui.png"
            );

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 192;

    private static final int TEXT_COLOR = 0xFFF4D6C5;

    private enum DisplayMode {
        NONE,
        PIT,
        MUTATION
    }

    private static DisplayMode displayMode = DisplayMode.NONE;

    // Pit information
    private static int pitSize;
    private static int pressure;
    private static int intensity;
    private static double generation;

    // Mutation information
    private static double bec;
    private static int stage;
    private static double secondsRemaining;

    public static void updatePitData(
            int size,
            int currentPressure,
            int currentIntensity,
            double currentGeneration
    ) {
        pitSize = size;
        pressure = currentPressure;
        intensity = currentIntensity;
        generation = currentGeneration;

        displayMode = DisplayMode.PIT;
    }

    public static void updateMutationData(
            double currentBEC,
            int currentStage,
            double currentSecondsRemaining
    ) {
        bec = currentBEC;
        stage = currentStage;
        secondsRemaining = currentSecondsRemaining;

        displayMode = DisplayMode.MUTATION;
    }

    public static void clearPitData() {
        displayMode = DisplayMode.NONE;

        pitSize = 0;
        pressure = 0;
        intensity = 0;
        generation = 0.0;

        bec = 0.0;
        stage = 0;
        secondsRemaining = 0.0;
    }

    private static String formatTime(double seconds) {
        if (Double.isNaN(seconds) || seconds == Double.POSITIVE_INFINITY) {
            return "PAUSED";
        }

        if (seconds <= 0.0) {
            return "COMPLETE";
        }

        long totalSeconds = (long) Math.ceil(seconds);
        long minutes = totalSeconds / 60;
        long remainingSeconds = totalSeconds % 60;

        return String.format(
                "%02d:%02d",
                minutes,
                remainingSeconds
        );
    }

    @SubscribeEvent
    public static void renderOverlay(RenderGuiOverlayEvent.Post event) {

        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (displayMode == DisplayMode.NONE
                || minecraft.player == null
                || minecraft.level == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        int left = minecraft.getWindow().getGuiScaledWidth() / 2 + 16;
        int top = minecraft.getWindow().getGuiScaledHeight() / 2 + 12;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        guiGraphics.blit(
                TEXTURE,
                left,
                top,
                0,
                0,
                GUI_WIDTH,
                GUI_HEIGHT,
                GUI_WIDTH,
                GUI_HEIGHT
        );

        int textX = left + 32;
        int textY = top + 26;
        int lineSpacing = 13;

        if (displayMode == DisplayMode.PIT) {

            guiGraphics.drawString(
                    minecraft.font,
                    "PIT SIZE: " + pitSize,
                    textX,
                    textY,
                    TEXT_COLOR,
                    false
            );

            guiGraphics.drawString(
                    minecraft.font,
                    "PRESSURE: " + pressure,
                    textX,
                    textY + lineSpacing,
                    TEXT_COLOR,
                    false
            );

            guiGraphics.drawString(
                    minecraft.font,
                    "INTENSITY: " + intensity,
                    textX,
                    textY + lineSpacing * 2,
                    TEXT_COLOR,
                    false
            );

            guiGraphics.drawString(
                    minecraft.font,
                    "GEN: +" + String.format("%.1f", generation) + "/s",
                    textX,
                    textY + lineSpacing * 3,
                    TEXT_COLOR,
                    false
            );
        }

        else if (displayMode == DisplayMode.MUTATION) {

            guiGraphics.drawString(
                    minecraft.font,
                    "BLOOD ETHER CONCENTRATION: "
                            + String.format("%.1f", bec) + " / 100",
                    textX,
                    textY,
                    TEXT_COLOR,
                    false
            );

            guiGraphics.drawString(
                    minecraft.font,
                    "STAGE: " + stage,
                    textX,
                    textY + lineSpacing,
                    TEXT_COLOR,
                    false
            );

            guiGraphics.drawString(
                    minecraft.font,
                    "NEXT STAGE: " + formatTime(secondsRemaining),
                    textX,
                    textY + lineSpacing * 2,
                    TEXT_COLOR,
                    false
            );
        }
    }
}
