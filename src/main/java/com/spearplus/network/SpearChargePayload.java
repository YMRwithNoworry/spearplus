package com.spearplus.network;

import com.spearplus.SpearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: the player started or stopped the shift + right-click throw charge.
 *
 * <p>The charge has to be tracked explicitly rather than through the vanilla item-use state. The
 * charge deliberately cancels the vanilla spear use action (so the vanilla spear raise never plays),
 * which also means {@code LivingEntity#isUsingItem} never becomes true and cannot be used to tell
 * when the button was released. These two packets carry that edge information instead.
 */
public record SpearChargePayload(boolean charging) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SpearChargePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SpearPlus.MOD_ID, "spear_charge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpearChargePayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.BOOL,
                    SpearChargePayload::charging,
                    SpearChargePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
