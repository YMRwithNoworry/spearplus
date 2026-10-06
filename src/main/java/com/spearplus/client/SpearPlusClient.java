package com.spearplus.client;

import com.spearplus.ModEntities;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-only wiring: the thrown spear renderer and the spear charge pose.
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

    /**
     * Wires the trident-style charge pose onto the vanilla spears.
     *
     * <p>The items are listed one by one because this event runs while {@code Minecraft} is still
     * being constructed, before item tags exist, so {@code minecraft:spears} cannot be resolved here.
     * Spears added by other mods keep the vanilla pose; they still throw normally.
     */
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(SpearClientExtensions.INSTANCE,
                Items.WOODEN_SPEAR,
                Items.STONE_SPEAR,
                Items.COPPER_SPEAR,
                Items.IRON_SPEAR,
                Items.GOLDEN_SPEAR,
                Items.DIAMOND_SPEAR,
                Items.NETHERITE_SPEAR);
    }
}

