package com.spearplus;

import com.spearplus.entity.ThrownSpear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Turns "hold shift + right-click with a vanilla spear" into a charge, and a full charge into a
 * throw.
 *
 * <p>The charge deliberately rides on the vanilla item-use state instead of a private timer:
 * {@code LivingEntity#isUsingItem()} and {@code getTicksUsingItem()} are what drive the vanilla
 * spear arm animation and the vanilla in-hand charge animation
 * ({@code SpearAnimations} reads {@code HumanoidRenderState#ticksUsingItem}), and they are what the
 * client already sends back to the server through the vanilla release packet. Re-implementing that
 * timing by hand produced a wrong arm pose and an extra input payload for no benefit.
 *
 * <p>What this class adds on top:
 * <ul>
 *   <li>the kinetic-weapon melee damage is suppressed while a throw charge is running, so charging
 *       does not also stab whatever is in front of the player;</li>
 *   <li>the charge only counts while the player is sneaking and holding a spear;</li>
 *   <li>releasing a full charge throws the spear.</li>
 * </ul>
 *
 * <p>A plain right-click is never intercepted, so the vanilla spear raise and the throw cannot
 * fight each other: the raise is exactly the same use action, just not converted into a throw when
 * the player is not sneaking.
 */
public final class SpearThrowHandler {
    /** Ticks of charging required before the release throws. Matches the vanilla bow. */
    public static final int CHARGE_TICKS = 20;

    private SpearThrowHandler() {
    }

    /**
     * A throw charge is running: the spear is being used and the player is sneaking.
     *
     * <p>Both halves matter. Sneaking alone would also match a player who is just standing still with
     * the spear raised, and using alone would match the plain vanilla raise.
     */
    private static boolean isCharging(LivingEntity entity) {
        return entity.isShiftKeyDown()
                && entity.isUsingItem()
                && isThrowableSpear(entity.getUseItem());
    }

    /**
     * Suppresses the vanilla kinetic-weapon damage while the player is charging a throw, so the
     * charge does not double as a melee stab. Cancelling this event also skips
     * {@code ItemStack#onUseTick}, which is where the kinetic damage is applied on the server.
     */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }
        if (isCharging(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** A released charge that ran long enough throws the spear. */
    @SubscribeEvent
    public static void onStopUsing(LivingEntityUseItemEvent.Stop event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        if (!isThrowableSpear(event.getItem()) || !entity.isShiftKeyDown()) {
            return;
        }
        if (entity.getTicksUsingItem() < CHARGE_TICKS) {
            // Released too early: the spear simply stays where it was, untouched and uncooled.
            return;
        }
        throwSpear(player, event.getHand());
    }

    private static void throwSpear(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isThrowableSpear(stack) || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // The stack is captured before it leaves the inventory: the projectile carries the exact
        // item (enchantments and attributes included) and its damage is read from it later.
        ItemStack projectileStack = stack.copyWithCount(1);

        ThrownSpear spear = new ThrownSpear(serverLevel, player, projectileStack);
        spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, ThrownSpear.LAUNCH_SPEED, 0.0F);
        serverLevel.addFreshEntity(spear);

        stack.shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** True for the vanilla spears only: the {@code minecraft:spears} item tag, nothing else. */
    public static boolean isThrowableSpear(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.SPEARS);
    }
}
