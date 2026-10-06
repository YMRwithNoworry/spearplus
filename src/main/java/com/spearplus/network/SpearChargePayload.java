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
 * <p>The charge cannot be timed from the vanilla use-item state, because the server deliberately
 * never enters that state while a charge is running — refusing to start it is how the vanilla
 * kinetic-weapon stab is kept out of a throw. The state only exists on the client, where it drives
 * the vanilla spear raise/sway animation. This payload carries the input itself instead, which is
 * stable and authoritative on the side that actually throws.
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

