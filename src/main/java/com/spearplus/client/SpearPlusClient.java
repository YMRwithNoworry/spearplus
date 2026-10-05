package com.spearplus.client;

import com.spearplus.ModEntities;
import com.spearplus.SpearPlus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only wiring: one renderer, no assets. */
@EventBusSubscriber(modid = SpearPlus.MOD_ID, value = Dist.CLIENT)
public final class SpearPlusClient {
    private SpearPlusClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.THROWN_SPEAR.get(), ThrownSpearRenderer::new);
    }
}
