package com.spearplus.client;

import com.spearplus.SpearPlus;
import com.spearplus.SpearThrowHandler;
import com.spearplus.network.SpearChargePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Client half of the throw input.
 *
 * <p>The charge is driven from the raw use key rather than from the vanilla item-use state, because
 * the charge cancels the vanilla use action on purpose. Two edges are reported to the server:
 * "started" while shift + the use key are held with a spear, and "stopped" as soon as any of those
 * conditions breaks.
 */
@EventBusSubscriber(modid = SpearPlus.MOD_ID, value = Dist.CLIENT)
public final class SpearThrowClient {
    private static boolean charging;

    private SpearThrowClient() {
    }

    /**
     * Swallows the vanilla use action for shift + right-click with a spear, on the client as well as
     * on the server, so the vanilla spear raise is never started for this input.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !player.isShiftKeyDown()) {
            return;
        }
        if (!SpearThrowHandler.isThrowableSpear(event.getItemStack())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        boolean holding = player != null
                && minecraft.gui.screen() == null
                && isUseKeyDown(minecraft.options.keyUse)
                && player.isShiftKeyDown()
                && SpearThrowHandler.isThrowableSpear(player.getItemInHand(InteractionHand.MAIN_HAND));

        if (holding == charging) {
            return;
        }
        charging = holding;

        if (player != null && player.connection != null) {
            player.connection.send(new SpearChargePayload(holding));
        }
    }

    private static boolean isUseKeyDown(KeyMapping keyUse) {
        return keyUse.isDown();
    }
}
