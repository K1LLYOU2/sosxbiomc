
package com.k.sosxbiomc.client.rendering.mutations;

import com.k.sosxbiomc.client.model.tumor_model;
import com.k.sosxbiomc.mutation.mutationdata;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.CowModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cow;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class tumor_render_layer extends RenderLayer<Cow, CowModel<Cow>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(
                    "sosxbiomc",
                    "textures/model_textures/tumor_texture.png"
            );

    private static final Vector3f TUMOR_OUT =
            new Vector3f(0, -1, 0);

    private final tumor_model<Cow> tumorModel;

    private List<surface_sampler.Face> surfaceFaces;

    public tumor_render_layer(
            RenderLayerParent<Cow, CowModel<Cow>> parent,
            tumor_model<Cow> tumorModel
    ) {
        super(parent);
        this.tumorModel = tumorModel;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            Cow cow,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (mutationdata.getStage(cow) < 1) {
            return;
        }

        if (surfaceFaces == null) {
            surfaceFaces = surface_sampler.buildFaces(
                    List.of(getParentModel().body)
            );
        }

        if (surfaceFaces.isEmpty()) {
            return;
        }

        UUID uuid = cow.getUUID();

        long seed = uuid.getMostSignificantBits()
                ^ uuid.getLeastSignificantBits();

        Random random = new Random(seed);

        int tumorCount = 2 + random.nextInt(4);

        VertexConsumer vertexConsumer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(TEXTURE)
        );

        tumorModel.setupAnim(
                cow,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch
        );

        for (int i = 0; i < tumorCount; i++) {

            surface_sampler.Placement placement =
                    surface_sampler.sample(
                            surfaceFaces,
                            random,
                            -1F
                    );

            if (placement == null) {
                continue;
            }

            float roll = random.nextFloat() * 360.0F;
            float scale = 0.75F + random.nextFloat() * 0.4F;

            poseStack.pushPose();

            Vector3f position = placement.position();
            Vector3f normal = placement.normal();

            // Apply the body part's pivot and rotation.
            placement.part().translateAndRotate(poseStack);

            // Move to the sampled surface point.
            poseStack.translate(
                    position.x / 16.0F,
                    position.y / 16.0F,
                    position.z / 16.0F
            );

            // Rotate the tumor's outward direction toward the surface normal.
            Quaternionf rotation = new Quaternionf()
                    .rotationTo(TUMOR_OUT, normal);

            poseStack.mulPose(rotation);

            // Randomly rotate around the tumor's outward axis.
            poseStack.mulPose(
                    Axis.YP.rotationDegrees(roll)
            );

            poseStack.scale(scale, scale, scale);

            // Cancel the tumor model's existing root pivot.
            poseStack.translate(
                    1.0F / 16.0F,
                    -21.3F / 16.0F,
                    -1.0F / 16.0F
            );

            tumorModel.renderToBuffer(
                    poseStack,
                    vertexConsumer,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );

            poseStack.popPose();
        }
    }
}
