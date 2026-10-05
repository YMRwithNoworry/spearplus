package com.spearplus;

import com.spearplus.entity.ThrownSpear;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry entries owned by this mod. Only one: the thrown spear projectile. */
public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(SpearPlus.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownSpear>> THROWN_SPEAR =
            ENTITY_TYPES.registerEntityType(
                    "thrown_spear",
                    ThrownSpear::new,
                    MobCategory.MISC,
                    builder -> builder
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .noSummon());

    private ModEntities() {
    }
}
