package com.spearplus.entity;

import com.spearplus.ModEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The spear that leaves the player's hand after a full charge.
 *
 * <p>Flight is completely ballistic-free: no gravity, no drag, one straight line along the launch
 * vector. Collision is resolved on the server only, and every living entity whose hitbox is crossed
 * by the swept segment takes the damage once; the projectile is not stopped by the first victim.
 * The first block hit consumes the projectile and leaves a pickable spear behind.
 */
public class ThrownSpear extends Projectile implements ItemSupplier {
    /** 1.5x the stack's attack-damage attribute value, as specified. */
    public static final float DAMAGE_MULTIPLIER = 1.5F;
    /** Launch speed in blocks per tick. */
    public static final float LAUNCH_SPEED = 2.5F;
    /** Hard cap on lifetime so a missed shot cannot fly forever. */
    private static final int MAX_LIFETIME_TICKS = 200;
    private static final double HITBOX_MARGIN = 0.3D;

    private static final EntityDataAccessor<ItemStack> DATA_ITEM_STACK =
            SynchedEntityData.defineId(ThrownSpear.class, EntityDataSerializers.ITEM_STACK);

    private boolean consumed;

    public ThrownSpear(EntityType<? extends ThrownSpear> type, Level level) {
        super(type, level);
    }

    public ThrownSpear(Level level, LivingEntity owner, ItemStack spear) {
        super(ModEntities.THROWN_SPEAR.get(), level);
        this.setOwner(owner);
        this.setItem(spear);
        this.setPos(owner.getX(), owner.getEyeY() - 0.1D, owner.getZ());
    }

    public void setItem(ItemStack stack) {
        this.entityData.set(DATA_ITEM_STACK, stack.copyWithCount(1));
    }

    @Override
    public ItemStack getItem() {
        return this.entityData.get(DATA_ITEM_STACK);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM_STACK, ItemStack.EMPTY);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Item", ItemStack.CODEC, this.getItem());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setItem(input.<ItemStack>read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.consumed) {
            return;
        }
        if (!this.level().isClientSide() && this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
            return;
        }

        Vec3 movement = this.getDeltaMovement();
        if (movement.lengthSqr() < 1.0E-7D) {
            return;
        }

        Vec3 from = this.position();
        Vec3 to = from.add(movement);

        // The world geometry clips the flight path first; entity hits past that point do not happen.
        BlockHitResult blockHit = this.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double blockDistance = blockHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE
                : blockHit.getLocation().distanceToSqr(from);

        List<EntityHitResult> hits = this.collectEntityHits(from, to, blockDistance);

        // Move first so every victim is resolved from the same, final position of this tick.
        this.setPos(to.x, to.y, to.z);

        for (EntityHitResult hit : hits) {
            if (this.consumed) {
                break;
            }
            this.hitEntity(hit.getEntity());
        }

        if (blockHit.getType() != HitResult.Type.MISS) {
            this.onHitBlock(blockHit);
            return;
        }

        this.setDeltaMovement(movement);
    }

    /** Every entity hitbox crossed by {@code from -> to}, nearest first, bounded by the block hit. */
    private List<EntityHitResult> collectEntityHits(Vec3 from, Vec3 to, double blockDistance) {
        AABB sweep = new AABB(from, to).inflate(HITBOX_MARGIN);
        List<EntityHitResult> hits = new ArrayList<>();

        for (Entity candidate : this.level().getEntitiesOfClass(Entity.class, sweep, this::canHitEntity)) {
            AABB box = candidate.getBoundingBox().inflate(HITBOX_MARGIN);
            Optional<Vec3> clip = box.clip(from, to);
            if (clip.isEmpty()) {
                continue;
            }
            double distance = clip.get().distanceToSqr(from);
            if (distance <= blockDistance) {
                hits.add(new EntityHitResult(candidate, clip.get()));
            }
        }

        hits.sort(Comparator.comparingDouble(hit -> hit.getLocation().distanceToSqr(from)));
        return hits;
    }

    @Override
    protected boolean canHitEntity(Entity candidate) {
        if (candidate == this || candidate.isSpectator() || !candidate.isAlive() || !candidate.canBeHitByProjectile()) {
            return false;
        }
        Entity owner = this.getOwner();
        if (owner == null) {
            return true;
        }
        if (candidate == owner || candidate.isPassengerOfSameVehicle(owner)) {
            return false;
        }
        if (candidate instanceof Player target && owner instanceof Player shooter) {
            return shooter.canHarmPlayer(target);
        }
        return true;
    }

    private void hitEntity(Entity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        float damage = this.computeDamage();
        Entity owner = this.getOwner();
        DamageSource source = this.getItem().getDamageSource(owner instanceof LivingEntity living ? living : null);

        if (target.hurtServer(serverLevel, source, damage)) {
            if (target instanceof LivingEntity livingTarget) {
                livingTarget.setLastHurtByMob(owner instanceof LivingEntity living ? living : null);
                if (owner instanceof LivingEntity living) {
                    living.setLastHurtMob(livingTarget);
                }
            }
            // Fire aspect / knockback style post-hit enchantment effects from the thrown spear.
            net.minecraft.world.item.enchantment.EnchantmentHelper.doPostAttackEffectsWithItemSource(
                    serverLevel, target, source, this.getItem());
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.consumed = true;
            // Same recovery rule as the vanilla trident: the spear survives the impact on the ground.
            ItemStack drop = this.getItem().copyWithCount(1);
            if (!drop.isEmpty()) {
                this.spawnAtLocation(serverLevel, drop, 0.1F);
            }
        }
        this.discard();
    }

    /**
     * The spear's own attack-damage attribute value, taken from the thrown stack at the moment of
     * the hit. Enchantments that add to the attack-damage attribute are already part of that value.
     */
    private float computeDamage() {
        ItemStack spear = this.getItem();
        ItemAttributeModifiers modifiers =
                spear.getComponents().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double base = modifiers.compute(Attributes.ATTACK_DAMAGE, 0.0D, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        return (float) (base * DAMAGE_MULTIPLIER);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 4096.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public @Nullable ItemStack getWeaponItem() {
        return this.getItem();
    }
}
