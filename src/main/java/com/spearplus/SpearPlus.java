package com.spearplus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Spear Plus - adds a charge-and-throw attack to the vanilla spears.
 *
 * <p>Nothing about the vanilla spear items themselves is replaced: they keep their ids, their melee
 * behaviour, their enchantments and their attributes. The throw is layered on top through NeoForge
 * events and one extra projectile entity.
 *
 * <p>Mod-bus listeners (client renderers) are registered explicitly here. In NeoForge 26.3
 * {@code @EventBusSubscriber} no longer has a {@code bus} attribute and always targets the game bus,
 * so it must not be used for mod-bus events such as
 * {@code EntityRenderersEvent.RegisterRenderers}.
 */
@Mod(SpearPlus.MOD_ID)
public final class SpearPlus {
    public static final String MOD_ID = "spearplus";

    public SpearPlus(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.register(SpearThrowHandler.class);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.addListener(com.spearplus.client.SpearPlusClient::registerRenderers);
        }
    }
}
