package com.spearplus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Spear Plus - adds a charge-and-throw attack to the vanilla spears.
 *
 * <p>Nothing about the vanilla spear items themselves is replaced: they keep their ids, their melee
 * behaviour, their enchantments and their attributes. The throw is layered on top through the
 * NeoForge use-item events and one extra projectile entity.
 */
@Mod(SpearPlus.MOD_ID)
public final class SpearPlus {
    public static final String MOD_ID = "spearplus";

    public SpearPlus(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.register(SpearThrowHandler.class);
    }
}
