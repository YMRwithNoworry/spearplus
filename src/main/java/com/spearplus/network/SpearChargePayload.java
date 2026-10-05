package com.spearplus.network;

import com.spearplus.SpearPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: the raw "shift + use key" state for the throw charge, sent on every edge.
 *
 * <p>The charge cannot rely on the vanilla use-item state. In practice the client's
 * {@code LocalPlayer#isUsingItem} toggles on and off every tick while the key is held, so
 * {@code getTicksUsingItem()} never accumulates past 1 and the release never reaches the throw
 * threshold. This payload carries the input itself instead, which is stable.
 *
 * <p>The vanilla use action is still left running on the client: it is what produces the vanilla
 * spear arm pose and the in-hand charge animation.
 */
public record SpearChargePayload(boolean charging) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SpearChargePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(SpearPlus.MOD_ID, "spear_charge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpearChargePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, SpearChargePayload::charging, SpearChargePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

