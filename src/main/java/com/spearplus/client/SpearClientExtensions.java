package com.spearplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.spearplus.SpearThrowHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jspecify.annotations.Nullable;

/**
 * Gives the spear the vanilla trident's charge pose while a throw charge is running: the arm is
 * cocked back over the shoulder instead of raising the spear forward.
 *
 * <p>Nothing here touches the spear outside a charge, so a plain right-click keeps the untouched
 * vanilla raise. The pose is only ever applied to the local player, because the charge state only
 * exists on the client that started it (see {@link SpearThrowHandler}).
 *
 * <p>Registered for the vanilla spear items in {@link SpearPlusClient#registerClientExtensions}.
 */
public final class SpearClientExtensions implements IClientItemExtensions {
    public static final SpearClientExtensions INSTANCE = new SpearClientExtensions();

    private SpearClientExtensions() {
    }

    /** Third person: swap the spear's own arm pose for the trident's cocked-back one. */
    @Override
    public HumanoidModel.@Nullable ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return isChargingLocalPlayer(entity, hand) ? HumanoidModel.ArmPose.THROW_TRIDENT : null;
    }

    /**
     * First person: the same cocked-back pose the vanilla trident uses. Returning {@code true} tells
     * the renderer that this transform replaced the vanilla use animation for this hand.
     */
    @Override
    public boolean applyForgeHandTransform(
            PoseStack poseStack,
            PlayerRenderState playerState,
            HumanoidArm arm,
            ItemStack stack,
            float partialTick,
            float equipProcess,
            float swingProcess) {
        AvatarRenderState avatar = playerState.avatarRenderState;
        if (avatar == null || !SpearThrowClient.isCharging()
                || !avatar.isUsingItem || avatar.useItemHand.asArm(avatar.mainArm) != arm) {
            return false;
        }

        // Numbers copied from the vanilla trident charge (FirstPersonHandsAndItemsRenderer): base
        // hand position, then the arm pulled back and the weapon cocked over the shoulder.
        int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(invert * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
        poseStack.translate(invert * -0.5F, 0.7F, 0.1F);
        poseStack.rotateDegrees(Axis.XP, -55.0F);
        poseStack.rotateDegrees(Axis.YP, invert * 35.3F);
        poseStack.rotateDegrees(Axis.ZP, invert * -9.785F);

        // Pull back over the charge, with the trident's slight shake once the arm is cocked.
        float power = Mth.clamp(avatar.ticksUsingItem / SpearThrowHandler.FULL_CHARGE_TICKS, 0.0F, 1.0F);
        if (power > 0.1F) {
            float shake = Mth.sin((avatar.ticksUsingItem - 0.1F) * 1.3F) * (power - 0.1F);
            poseStack.translate(0.0F, shake * 0.004F, 0.0F);
        }

        poseStack.translate(0.0F, 0.0F, power * 0.2F);
        poseStack.scale(1.0F, 1.0F, 1.0F + power * 0.2F);
        poseStack.rotateDegrees(Axis.YN, invert * 45.0F);
        return true;
    }

    /**
     * The charge state is a single client-wide flag, so it may only be applied to the player that
     * owns it; every other player keeps the pose the vanilla renderer picks for them.
     */
    private static boolean isChargingLocalPlayer(LivingEntity entity, InteractionHand hand) {
        return SpearThrowClient.isCharging()
                && entity == Minecraft.getInstance().player
                && entity.isUsingItem()
                && entity.getUsedItemHand() == hand;
    }
}
