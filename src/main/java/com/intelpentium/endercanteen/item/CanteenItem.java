package com.intelpentium.endercanteen.item;

import com.intelpentium.endercanteen.EnderCanteenConfig;
import com.intelpentium.endercanteen.blockentity.FluidTapBlockEntity;
import com.intelpentium.endercanteen.compat.ThirstCompat;
import com.intelpentium.endercanteen.network.StopDrinkingPacket;
import com.intelpentium.endercanteen.registry.ModDataComponents;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CanteenItem extends dev.ghen.thirst.foundation.common.item.DrinkableItem {

    public CanteenItem(Properties properties) {
        super(properties);
    }

    // -------------------------------------------------------------------------
    // Config helpers
    // -------------------------------------------------------------------------

    public static int drinkMb() {
        return EnderCanteenConfig.DRINK_AMOUNT_MB.get();
    }

    public static int calcThirst(int drainedMb) {
        return Math.max(1, (int) Math.round(
                drainedMb * EnderCanteenConfig.THIRST_PER_250MB.get() / 250.0));
    }

    public static int calcQuenched(int drainedMb) {
        return Math.max(0, (int) Math.round(
                drainedMb * EnderCanteenConfig.QUENCHED_PER_250MB.get() / 250.0));
    }

    // -------------------------------------------------------------------------
    // Thirst Was Taken – RegisterThirstValueEvent
    // -------------------------------------------------------------------------

    /**
     * Registers the canteen as a drink with the correct thirst/quenched values
     * so ThirstWasTaken's HUD overlay (and AppleSkin thirst preview) works.
     * Called from EnderCanteen on the NeoForge event bus.
     */
    public static void onRegisterThirstValue(dev.ghen.thirst.foundation.common.event.RegisterThirstValueEvent event) {
        // Register in VALID_DRINKS for the AppleSkin/ThirstWasTaken thirst-preview icons.
        // ThirstWasTaken's PlayerThirstManager.drink(LivingEntityUseItemEvent.Finish) checks
        // 'instanceof DrinkableItem → return' BEFORE calling IThirst.drink() – so registering
        // here does NOT cause a double drink. Our finishUsingItem is the sole thirst source.
        // NOTE: event.addDrink() is @Deprecated with empty body – write to the map directly.
        dev.ghen.thirst.api.ThirstHelper.VALID_DRINKS.put(
                com.intelpentium.endercanteen.registry.ModItems.CANTEEN.get(),
                new Number[]{calcThirst(drinkMb()), calcQuenched(drinkMb())}
        );
    }

    // -------------------------------------------------------------------------
    // Linking: Shift + right-click a fluid block
    // -------------------------------------------------------------------------

    @Override
    public @NotNull InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;

        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();

        IFluidHandler handler = getHandlerAt(level, pos);
        if (handler == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.endercanteen.canteen.no_handler"), true);
            }
            return InteractionResult.FAIL;
        }

        stack.set(ModDataComponents.LINKED_POS.get(), GlobalPos.of(level.dimension(), pos));

        if (!level.isClientSide) {
            player.displayClientMessage(
                    Component.translatable("item.endercanteen.canteen.linked",
                            pos.getX(), pos.getY(), pos.getZ()), true);
            player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.5f);
        }
        return InteractionResult.SUCCESS;
    }

    // -------------------------------------------------------------------------
    // Drinking: right-click with linked canteen
    // -------------------------------------------------------------------------

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);

        GlobalPos linkedPos = stack.get(ModDataComponents.LINKED_POS.get());
        if (linkedPos == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.endercanteen.canteen.not_linked"), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        // Server-side: authoritative check (supports cross-dimension via server.getLevel()).
        // On fail, we send a StopDrinkingPacket to the client so it cancels the animation.
        // Client-side: always start the animation optimistically; the server will stop it
        // immediately via packet if the tank is empty/unreachable.
        if (!level.isClientSide) {
            Level targetLevel = getTargetLevel(level, linkedPos);
            if (targetLevel == null || !targetLevel.isLoaded(linkedPos.pos())) {
                player.displayClientMessage(
                        Component.translatable("item.endercanteen.canteen.out_of_range"), true);
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) sendStopPacket(sp);
                return InteractionResultHolder.fail(stack);
            }
            IFluidHandler handler = getHandlerAt(targetLevel, linkedPos.pos());
            if (handler == null || findWaterStack(handler, drinkMb(), FluidAction.SIMULATE) == null) {
                player.displayClientMessage(
                        Component.translatable("item.endercanteen.canteen.no_water"), true);
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) sendStopPacket(sp);
                return InteractionResultHolder.fail(stack);
            }
            // RF check: if enabled, require enough energy for at least one thirst point
            if (EnderCanteenConfig.RF_ENABLED.get()) {
                int costPerPoint = EnderCanteenConfig.RF_COST_PER_THIRST_POINT.get();
                if (costPerPoint > 0) {
                    CanteenEnergyStorage energy = new CanteenEnergyStorage(stack);
                    if (energy.getEnergyStored() < costPerPoint) {
                        player.displayClientMessage(
                                Component.translatable("item.endercanteen.canteen.no_rf"), true);
                        if (player instanceof net.minecraft.server.level.ServerPlayer sp) sendStopPacket(sp);
                        return InteractionResultHolder.fail(stack);
                    }
                }
            }
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity) {
        if (!(entity instanceof net.minecraft.server.level.ServerPlayer player)) return stack;

        GlobalPos linkedPos = stack.get(ModDataComponents.LINKED_POS.get());
        if (linkedPos == null) return stack;

        // Cross-dimension support: look up the target level on the server
        Level targetLevel = getTargetLevel(level, linkedPos);
        if (targetLevel == null || !targetLevel.isLoaded(linkedPos.pos())) {
            player.displayClientMessage(
                    Component.translatable("item.endercanteen.canteen.out_of_range"), true);
            sendStopPacket(player);
            return stack;
        }

        IFluidHandler handler = getHandlerAt(targetLevel, linkedPos.pos());
        if (handler == null) {
            sendStopPacket(player);
            return stack;
        }

        FluidStack drained = findWaterStack(handler, drinkMb(), FluidAction.EXECUTE);
        if (drained == null || drained.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("item.endercanteen.canteen.no_water"), true);
            sendStopPacket(player);
            return stack;
        }

        int thirst   = calcThirst(drained.getAmount());
        int quenched = calcQuenched(drained.getAmount());

        // RF cost: consume RF proportional to thirst + quenched points restored
        if (EnderCanteenConfig.RF_ENABLED.get()) {
            int costPerPoint = EnderCanteenConfig.RF_COST_PER_THIRST_POINT.get();
            if (costPerPoint > 0) {
                int totalPoints = thirst + quenched;
                int totalCost = totalPoints * costPerPoint;
                CanteenEnergyStorage energy = new CanteenEnergyStorage(stack);
                // Extract what we can; if not enough energy, abort the drink
                int extracted = energy.extractEnergy(totalCost, true);
                if (extracted < costPerPoint) {
                    // Not enough RF for even one point – reject
                    player.displayClientMessage(
                            Component.translatable("item.endercanteen.canteen.no_rf"), true);
                    // Refund the fluid
                    handler.fill(drained, FluidAction.EXECUTE);
                    sendStopPacket(player);
                    return stack;
                }
                // Recalculate how many points we can afford
                int affordablePoints = extracted / costPerPoint;
                if (affordablePoints < thirst + quenched) {
                    // Partially limit – scale thirst and quenched to affordable amount
                    // Simple approach: reduce quenched first, then thirst
                    int newTotal = affordablePoints;
                    quenched = Math.min(quenched, newTotal);
                    thirst = Math.min(thirst, newTotal - quenched);
                    int actualCost = (thirst + quenched) * costPerPoint;
                    energy.extractEnergy(actualCost, false);
                } else {
                    energy.extractEnergy(totalCost, false);
                }
            }
        }

        ThirstCompat.addThirst(player, thirst, quenched, drained);

        player.playSound(SoundEvents.GENERIC_DRINK,
                1.0f, 1.0f + (float)(Math.random() * 0.4 - 0.2));
        return stack;
    }

    // -------------------------------------------------------------------------
    // RF durability bar
    // -------------------------------------------------------------------------

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        if (!EnderCanteenConfig.RF_ENABLED.get()) return false;
        CanteenEnergyStorage energy = new CanteenEnergyStorage(stack);
        // Always show bar so the player knows the charge level (hide only when full)
        return energy.getMaxEnergyStored() > 0;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        CanteenEnergyStorage energy = new CanteenEnergyStorage(stack);
        int max = energy.getMaxEnergyStored();
        if (max <= 0) return 0;
        // getBarWidth must return 0-13 (vanilla uses 13 as full width)
        return Math.round(13.0f * energy.getEnergyStored() / max);
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        CanteenEnergyStorage energy = new CanteenEnergyStorage(stack);
        int max = energy.getMaxEnergyStored();
        float ratio = max > 0 ? (float) energy.getEnergyStored() / max : 0f;
        // Interpolate: red (0xFF0000) → yellow (0xFFFF00) → cyan (0x00FFFF)
        int r, g, b;
        if (ratio < 0.5f) {
            float t = ratio * 2f;
            r = 255;
            g = Math.round(255 * t);
            b = 0;
        } else {
            float t = (ratio - 0.5f) * 2f;
            r = Math.round(255 * (1f - t));
            g = 255;
            b = Math.round(255 * t);
        }
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return 32;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.DRINK;
    }

    // -------------------------------------------------------------------------
    // Tooltip
    // -------------------------------------------------------------------------

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext ctx,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        GlobalPos pos = stack.get(ModDataComponents.LINKED_POS.get());
        if (pos != null) {
            Level level = ctx.level();
            boolean sameDimension = level != null && level.dimension().equals(pos.dimension());

            if (sameDimension) {
                tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_linked",
                        pos.pos().getX(), pos.pos().getY(), pos.pos().getZ()));
            } else {
                tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_linked_dim",
                        pos.pos().getX(), pos.pos().getY(), pos.pos().getZ(),
                        pos.dimension().location().toString()));
                tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_cross_dim"));
            }

            // Show fluid info only when chunk is loaded in the same dimension (client-side)
            if (level != null && sameDimension && level.isLoaded(pos.pos())) {
                IFluidHandler handler = getHandlerAt(level, pos.pos());
                if (handler != null) {
                    int totalWater = 0;
                    int totalCapacity = 0;
                    Integer purity = null;
                    for (int i = 0; i < handler.getTanks(); i++) {
                        FluidStack content = handler.getFluidInTank(i);
                        if (!content.isEmpty() && content.getFluid().defaultFluidState().is(FluidTags.WATER)) {
                            totalWater += content.getAmount();
                            if (purity == null) {
                                Integer p = content.get(dev.ghen.thirst.content.registry.ThirstComponent.PURITY);
                                purity = (p != null) ? p : 3;
                            }
                        }
                        totalCapacity += handler.getTankCapacity(i);
                    }
                    if (totalCapacity > 0) {
                        tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_fluid",
                                totalWater, totalCapacity));
                    }
                    if (purity != null) {
                        tooltip.add(Component.translatable(
                                "item.endercanteen.canteen.tooltip_purity." + purity));
                    }
                }
            }
        } else {
            tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_unlinked"));
        }
        tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_drink_amount", drinkMb()));

        // RF / Energy tooltip
        if (EnderCanteenConfig.RF_ENABLED.get()) {
            CanteenEnergyStorage energy = new CanteenEnergyStorage(stack);
            int stored = energy.getEnergyStored();
            int capacity = energy.getMaxEnergyStored();
            tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_rf",
                    stored, capacity));
            int costPerPoint = EnderCanteenConfig.RF_COST_PER_THIRST_POINT.get();
            if (costPerPoint > 0) {
                int drinkPoints = calcThirst(drinkMb()) + calcQuenched(drinkMb());
                int drinkCost = drinkPoints * costPerPoint;
                tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_rf_cost",
                        drinkCost, costPerPoint));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a StopDrinkingPacket to the client to cancel the drinking animation.
     * Called server-side whenever the drink is rejected (tank empty / unreachable).
     */
    private static void sendStopPacket(net.minecraft.server.level.ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new StopDrinkingPacket());
    }

    /**
     * Resolves the target Level for a GlobalPos.
     * Works cross-dimensionally on the logical server via MinecraftServer.getLevel().
     * Returns null on the client side for non-current dimensions, or if the dimension is unknown.
     */
    @Nullable
    private static Level getTargetLevel(Level currentLevel, GlobalPos linkedPos) {
        if (currentLevel.dimension().equals(linkedPos.dimension())) {
            return currentLevel;
        }
        if (currentLevel.isClientSide) return null;
        net.minecraft.server.MinecraftServer server = currentLevel.getServer();
        if (server == null) return null;
        return server.getLevel(linkedPos.dimension());
    }

    @Nullable
    private static FluidStack findWaterStack(IFluidHandler handler, int mb, FluidAction action) {
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack content = handler.getFluidInTank(i);
            if (content.isEmpty()) continue;
            if (!content.getFluid().defaultFluidState().is(FluidTags.WATER)) continue;
            FluidStack request = content.copyWithAmount(Math.min(mb, content.getAmount()));
            FluidStack result = handler.drain(request, action);
            if (!result.isEmpty()) return result;
        }
        return null;
    }

    @Nullable
    private static IFluidHandler getHandlerAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FluidTapBlockEntity tap) return tap.findAdjacentHandler();

        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler != null) return handler;

        for (Direction dir : Direction.values()) {
            handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, dir);
            if (handler != null) return handler;
        }
        return null;
    }
}
