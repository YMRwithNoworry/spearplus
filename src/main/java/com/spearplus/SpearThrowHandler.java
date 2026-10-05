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
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Turns "hold shift + right-click with a vanilla spear" into a charge, and a full charge into a
 * throw. Everything here is server authoritative: the client only sends its normal input.
 *
 * <p>The vanilla spear keeps its own use action untouched. A plain right-click is never intercepted,
 * so it still performs the vanilla spear raise; only the shift + right-click combination is taken
 * over by this class, which is why the two behaviours cannot interfere with each other.
 *
 * <p>The vanilla spear is only ever removed from the inventory when a projectile was actually
 * launched.
 */
public final class SpearThrowHandler {
    /** Ticks of charging required before the release throws. Matches the vanilla bow. */
    public static final int CHARGE_TICKS = 20;

    /** Players currently charging a throw, keyed by player UUID. */
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    private SpearThrowHandler() {
    }

    private record Charge(InteractionHand hand, int ticks) {
    }

    /**
     * Claims the shift + right-click combination for a held spear and swallows the vanilla use
     * action for that one input, so the vanilla spear raise and the throw never fight each other.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) {
            return;
        }
        if (!isThrowableSpear(event.getItemStack())) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        CHARGES.put(player.getUUID(), new Charge(event.getHand(), 0));
    }

    /** Advances the charge and releases the throw once the use key or shift is let go. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Charge charge = CHARGES.get(player.getUUID());
        if (charge == null) {
            return;
        }

        // Letting go of either shift or the use key ends the charge; a full charge throws.
        boolean released = !player.isShiftKeyDown()
                || !player.isUsingItem()
                || player.getTicksUsingItem() < charge.ticks();

        CHARGES.remove(player.getUUID());
        if (released) {
            if (charge.ticks() >= CHARGE_TICKS) {
                throwSpear(player, charge.hand());
            }
            return;
        }

        CHARGES.put(player.getUUID(), new Charge(charge.hand(), charge.ticks() + 1));
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
    private static boolean isThrowableSpear(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.SPEARS);
    }
}
