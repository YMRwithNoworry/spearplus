package com.spearplus.client;

import com.spearplus.SpearPlus;
import com.spearplus.SpearThrowHandler;
import com.spearplus.network.SpearChargePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client half of the throw input: reports the raw "shift + use key held with a spear" state to the
 * server, on every edge.
 *
 * <p>The vanilla use action is deliberately left alone. It runs on the client and is what produces
 * the vanilla spear arm pose and the in-hand charge animation, so cancelling it would take the
 * animation away again.
 */
@EventBusSubscriber(modid = SpearPlus.MOD_ID, value = Dist.CLIENT)
public final class SpearThrowClient {
    private static boolean charging;

    private SpearThrowClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        boolean holding = player != null
                && minecraft.gui.screen() == null
                && minecraft.options.keyUse.isDown()
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
}

