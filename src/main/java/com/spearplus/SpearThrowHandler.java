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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Turns "hold shift + right-click with a vanilla spear" into a charge, and a full charge into a
 * throw.
 *
 * <p>Two rules keep this working without ever fighting the vanilla use-item state, which is the one
 * and only driver of the vanilla spear arm pose and in-hand charge animation:
 *
 * <ul>
 *   <li>the charge is timed by the server from the raw input reported by
 *       {@link com.spearplus.network.SpearChargePayload}, never from the vanilla use state;</li>
 *   <li>the server refuses to <b>start</b> the vanilla use for a charging player, which is what
 *       keeps the vanilla kinetic-weapon stab out of a throw charge. The client still starts that
 *       use locally — {@code MultiPlayerGameMode#useItem} calls {@code ItemStack#use} as a
 *       prediction — and that local state is what plays the raise and the sway.</li>
 * </ul>
 *
 * <p>Suppressing the stab by cancelling {@code LivingEntityUseItemEvent.Tick} instead is <b>not</b>
 * an option: the NeoForge hook turns a cancelled tick into a use duration of {@code -1}, so
 * {@code LivingEntity#updateUsingItem} immediately calls {@code completeUsingItem()}. The server
 * then clears the use flag on every single tick, the client's use state is torn down with it, and
 * the animation restarts from tick 0 forever — which is exactly the "arm snapping between two poses"
 * look this class used to produce.
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

    /** True while this player has a throw charge running. */
    public static boolean isCharging(ServerPlayer player) {
        return CHARGE_START.containsKey(player.getUUID());
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
     * Stops the vanilla spear use from starting on the server while the player is charging a throw,
     * so the charge cannot double as a melee stab. Cancelling here — before {@code Item#use} — leaves
     * no use state, no use sound and no inventory resync behind.
     *
     * <p>Only the server is intercepted. The client has to keep starting the use locally, because
     * that prediction is what the spear raise/sway animation reads.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isThrowableSpear(event.getItemStack())) {
            return;
        }
        // The charge is normally known already: the client sends it from ClientTickEvent.Pre, ahead of
        // the use packet of the same tick. The sneak check is the fallback for the tick a player presses
        // shift and right-click together.
        if (isCharging(player) || player.isShiftKeyDown()) {
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
        if (isCharging(player) && !canCharge(player)) {
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
