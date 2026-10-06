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
 * <p>Sent from {@link ClientTickEvent.Pre} rather than {@code Post} so that the charge reaches the
 * server before the vanilla use packet the same tick is about to send. The server uses that head
 * start to refuse starting the vanilla spear use while a charge is running, which is what keeps the
 * kinetic-weapon stab out of a throw charge.
 *
 * <p>The vanilla use action itself is deliberately left alone. It runs on the client and is what
 * produces the vanilla spear arm pose and the in-hand charge animation, so cancelling it would take
 * the animation away again.
 */
@EventBusSubscriber(modid = SpearPlus.MOD_ID, value = Dist.CLIENT)
public final class SpearThrowClient {
    private static boolean charging;

    private SpearThrowClient() {
    }

    /** True while the local player is holding a throw charge. Drives the cocked-back charge pose. */
    public static boolean isCharging() {
        return charging;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
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

