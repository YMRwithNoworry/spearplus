package com.spearplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.spearplus.entity.ThrownSpear;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

/**
 * Renders the thrown spear with the vanilla spear model and texture - no new assets.
 *
 * <p>The display context is {@link ItemDisplayContext#NONE} on purpose. The vanilla spear item model
 * selects the flat inventory sprite for {@code gui}, {@code ground}, {@code fixed} and
 * {@code on_shelf}, and only falls back to the 3D {@code <material>_spear_in_hand} model for the
 * remaining contexts. Rendering the flat sprite in the world made the spear look like a billboard
 * flipping around as it turned; {@code NONE} resolves the 3D in-hand model instead.
 *
 * <p>The pose mirrors {@code ThrownTridentRenderer} exactly (camera orientation, then Y by
 * {@code yRot - 90}, then Z by {@code xRot + 90}), because the spear's in-hand model is authored on
 * the same axis as the trident's model.
 */
public class ThrownSpearRenderer extends EntityRenderer<ThrownSpear, ThrownSpearRenderState> {
    private static final float SCALE = 1.5F;

    private final ItemModelResolver itemModelResolver;

    public ThrownSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = 0.25F;
    }

    @Override
    public void submit(ThrownSpearRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotate(camera.orientation);
        poseStack.rotate(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        poseStack.rotate(Axis.ZP.rotationDegrees(state.xRot + 90.0F));
        poseStack.scale(SCALE, SCALE, SCALE);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    protected AABB getBoundingBoxForCulling(ThrownSpear entity, float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(1.5D);
    }

    @Override
    public ThrownSpearRenderState createRenderState() {
        return new ThrownSpearRenderState();
    }

    @Override
    public void extractRenderState(ThrownSpear entity, ThrownSpearRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
        this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
    }
}
