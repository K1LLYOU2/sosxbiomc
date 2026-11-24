
package com.k.sosxbiomc.client.rendering.mutations;

import com.k.sosxbiomc.SOSxBiomc;
import com.k.sosxbiomc.client.model.tumor_model;

import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.world.entity.EntityType;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = SOSxBiomc.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class tumor_render_layer_registration {

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event
    ) {
        event.registerLayerDefinition(
                tumor_model.LAYER_LOCATION,
                tumor_model::createBodyLayer
        );
    }

    @SubscribeEvent
    public static void addLayers(
            EntityRenderersEvent.AddLayers event
    ) {
        CowRenderer cowRenderer =
                (CowRenderer) event.getRenderer(EntityType.COW);

        tumor_model<net.minecraft.world.entity.animal.Cow> tumorModel =
                new tumor_model<>(
                        event.getEntityModels().bakeLayer(
                                tumor_model.LAYER_LOCATION
                        )
                );

        cowRenderer.addLayer(
                new tumor_render_layer(
                        cowRenderer,
                        tumorModel
                )
        );
    }
}
