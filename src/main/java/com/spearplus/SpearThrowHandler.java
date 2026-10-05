package com.spearplus;

import com.spearplus.entity.ThrownSpear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Turns "hold right-click with a vanilla spear" into a charge, and a full charge release into a
 * throw. Everything here is server authoritative: the client only keeps the vanilla use animation
 * and the vanilla input handling.
 *
 * <p>The vanilla spear keeps its own use action ({@code Item.use} starts the kinetic-weapon charge),
 * so this class adds behaviour instead of replacing it. The spear is only ever removed from the
 * inventory when a projectile was actually launched.
 */
public final class SpearThrowHandler {
    /** Ticks of charging required before the release throws. Matches the vanilla bow. */
    public static final int CHARGE_TICKS = 20;

    private SpearThrowHandler() {
    }

    @SubscribeEvent
    public static void onStopUsing(LivingEntityUseItemEvent.Stop event) {
        LivingEntity entity = event.getEntity();
        ItemStack stack = event.getItem();

        if (!isThrowableSpear(stack)) {
            return;
        }
        if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (entity.getTicksUsingItem() < CHARGE_TICKS) {
            // Released too early: the spear simply stays where it was, untouched and uncooled.
            return;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        InteractionHand hand = event.getHand();
        if (player.getItemInHand(hand) != stack) {
            return;
        }

        // The stack is captured before it is taken out of the inventory: the projectile carries the
        // exact item (enchantments and attributes included) and its damage is read from it later.
        ItemStack projectileStack = stack.copyWithCount(1);

        ThrownSpear spear = new ThrownSpear(serverLevel, player, projectileStack);
        spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, ThrownSpear.LAUNCH_SPEED, 0.0F);
        spear.setDeltaMovement(spear.getDeltaMovement().scale(1.0D));
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
