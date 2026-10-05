package com.spearplus.client;

import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;

/**
 * The vanilla thrown-item render state plus the two angles needed to point the spear along its
 * flight direction. The item itself is still resolved exactly the way the vanilla thrown-item
 * renderer does it, so no new assets are involved.
 */
public class ThrownSpearRenderState extends ThrownItemRenderState {
    public float yRot;
    public float xRot;
}
