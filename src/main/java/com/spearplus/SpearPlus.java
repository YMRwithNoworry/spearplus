package com.spearplus;

import com.spearplus.network.SpearChargePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Spear Plus - adds a charge-and-throw attack to the vanilla spears.
 *
 * <p>Nothing about the vanilla spear items themselves is replaced: they keep their ids, their melee
 * behaviour, their enchantments and their attributes. The throw is layered on top through NeoForge
 * events, one small client-to-server input payload and one extra projectile entity.
 *
 * <p>Mod-bus listeners (payload registration, client renderers) are registered explicitly here. In
 * NeoForge 26.3 {@code @EventBusSubscriber} no longer has a {@code bus} attribute and always targets
 * the game bus, so it must not be used for mod-bus events such as
 * {@code EntityRenderersEvent.RegisterRenderers}.
 */
@Mod(SpearPlus.MOD_ID)
public final class SpearPlus {
    public static final String MOD_ID = "spearplus";

    public SpearPlus(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(SpearPlus::registerPayloads);
        NeoForge.EVENT_BUS.register(SpearThrowHandler.class);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.addListener(com.spearplus.client.SpearPlusClient::registerRenderers);
            modEventBus.addListener(com.spearplus.client.SpearPlusClient::registerClientExtensions);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                SpearChargePayload.TYPE,
                SpearChargePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (payload.charging()) {
                            SpearThrowHandler.startCharge(player);
                        } else {
                            SpearThrowHandler.stopCharge(player);
                        }
                    }
                }));
    }
}

