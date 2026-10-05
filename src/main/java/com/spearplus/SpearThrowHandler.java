package com.spearplus;

import com.spearplus.entity.ThrownSpear;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Turns "hold shift + right-click with a vanilla spear" into a charge, and a full charge into a
 * throw.
 *
 * <p>The charge is timed by the server from the raw input reported by
 * {@link com.spearplus.network.SpearChargePayload}, not from the vanilla use-item state. That state
 * proved unusable in practice: the client's {@code LocalPlayer#isUsingItem} toggles every tick while
 * the key is held, so {@code getTicksUsingItem()} never accumulates and the release never reaches
 * the throw threshold.
 *
 * <p>The vanilla use action is still allowed to run, because it is what produces the vanilla spear
 * arm pose and in-hand charge animation. Two things are layered on top:
 * <ul>
 *   <li>the vanilla kinetic-weapon melee damage is suppressed while a charge is running, so charging
 *       does not also stab whatever is in front of the player;</li>
 *   <li>releasing a charge that ran long enough throws the spear.</li>
 * </ul>
 *
 * <p>A plain right-click is never intercepted, so the vanilla spear raise and the throw cannot fight
 * each other: the raise is the same use action, just not converted into a throw without shift.
 */
public final class SpearThrowHandler {
    /** Ticks of charging required before the release throws. Matches the vanilla bow. */
    public static final int CHARGE_TICKS = 20;

    /** Server tick at which each player's charge started, keyed by player UUID. */
    private static final Map<UUID, Integer> CHARGE_START = new HashMap<>();

    private SpearThrowHandler() {
    }

    /** The charge only counts while the player is alive, sneaking and holding a spear. */
    private static boolean canCharge(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator()
                && player.isShiftKeyDown()
                && isThrowableSpear(player.getMainHandItem());
    }

    /**
     * Starts a charge when the client reports the shift + right-click press.
     *
     * <p>Idempotent on purpose: the client re-sends the press edge for as long as the keys are held,
     * and an already running charge must keep its original start tick or it would never fill up.
     */
    public static void startCharge(ServerPlayer player) {
        if (!canCharge(player) || CHARGE_START.containsKey(player.getUUID())) {
            return;
        }
        CHARGE_START.put(player.getUUID(), player.level().getServer().getTickCount());
    }

    /** Releases the charge; a charge held long enough throws, anything shorter does nothing. */
    public static void stopCharge(ServerPlayer player) {
        Integer start = CHARGE_START.remove(player.getUUID());
        if (start == null) {
            return;
        }
        int held = player.level().getServer().getTickCount() - start;
        if (held >= CHARGE_TICKS) {
            throwSpear(player, InteractionHand.MAIN_HAND);
        }
    }

    /**
     * Suppresses the vanilla kinetic-weapon damage while a throw charge is running, so the charge
     * does not double as a melee stab. Cancelling this event also skips {@code ItemStack#onUseTick},
     * which is where the kinetic damage is applied on the server.
     */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (CHARGE_START.containsKey(player.getUUID()) && isThrowableSpear(event.getItem())) {
            event.setCanceled(true);
        }
    }

    /**
     * Safety net for a charge that was never explicitly released (the player stopped sneaking, swapped
     * the spear away, died, or the release edge was lost): drop it instead of leaving it stuck.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (CHARGE_START.containsKey(player.getUUID()) && !canCharge(player)) {
            CHARGE_START.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        CHARGE_START.remove(event.getEntity().getUUID());
    }

    private static void throwSpear(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canCharge(player) || !isThrowableSpear(stack) || !(player.level() instanceof ServerLevel serverLevel)) {
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

