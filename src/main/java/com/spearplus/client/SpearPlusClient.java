package com.spearplus.client;

import com.spearplus.ModEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only renderer wiring.
 *
 * <p>Registered explicitly from the mod constructor (mod event bus). {@code @EventBusSubscriber}
 * cannot be used here: in NeoForge 26.3 that annotation has no {@code bus} attribute and always
 * targets the game bus, so a {@code RegisterRenderers} listener would silently never run and the
 * thrown spear would have no renderer.
 */
public final class SpearPlusClient {
    private SpearPlusClient() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.THROWN_SPEAR.get(), ThrownSpearRenderer::new);
    }
}

