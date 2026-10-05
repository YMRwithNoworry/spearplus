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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Turns "hold shift + right-click with a vanilla spear" into a charge, and a full charge into a
 * throw. Everything here is server authoritative.
 *
 * <p>The vanilla spear keeps its own use action untouched. A plain right-click is never intercepted,
 * so it still performs the vanilla spear raise; only the shift + right-click combination is taken
 * over, and for that input the vanilla use action is cancelled outright so the raise never plays
 * and the two behaviours cannot fight each other.
 *
 * <p>Because the vanilla use action is cancelled, the vanilla item-use state is never entered. The
 * charge therefore has its own lifecycle: the client reports the press/release edges through
 * {@link com.spearplus.network.SpearChargePayload}, and the server times the charge, validates that
 * the player is still sneaking and still holding a spear, and only then spawns the projectile.
 */
public final class SpearThrowHandler {
    /** Ticks of charging required before the release throws. Matches the vanilla bow. */
    public static final int CHARGE_TICKS = 20;

    /** Server tick at which each player's charge started, keyed by player UUID. */
    private static final Map<UUID, Integer> CHARGE_START = new HashMap<>();

    private SpearThrowHandler() {
    }

    /**
     * Claims the shift + right-click combination for a held spear and swallows the vanilla use
     * action for that one input, on both the client and the server, so the vanilla spear raise and
     * the throw never fight each other.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntity().isShiftKeyDown() || !isThrowableSpear(event.getItemStack())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * Starts a charge when the client reports the shift + right-click press.
     *
     * <p>The client keeps re-sending the press edge for as long as the keys are held, so this is
     * deliberately idempotent: an already running charge keeps its original start tick, which is
     * what makes the charge actually accumulate instead of restarting every tick.
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
     * Safety net for a charge that was never explicitly released: as soon as the player can no
     * longer charge (let go of shift, swapped the spear away, died), the pending charge is dropped
     * instead of being left stuck.
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

    /** The charge is only valid while the player is alive, sneaking and holding a spear. */
    private static boolean canCharge(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator()
                && player.isShiftKeyDown()
                && isThrowableSpear(player.getMainHandItem());
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
