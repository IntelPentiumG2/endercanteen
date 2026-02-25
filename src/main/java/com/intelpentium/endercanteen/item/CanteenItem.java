package com.intelpentium.endercanteen.item;

import com.intelpentium.endercanteen.EnderCanteenConfig;
import com.intelpentium.endercanteen.blockentity.FluidTapBlockEntity;
import com.intelpentium.endercanteen.compat.ThirstCompat;
import com.intelpentium.endercanteen.network.StopDrinkingPacket;
import com.intelpentium.endercanteen.registry.ModDataComponents;
import dev.ghen.thirst.api.ThirstHelper;
import dev.ghen.thirst.content.registry.ThirstComponent;
import dev.ghen.thirst.foundation.common.event.RegisterThirstValueEvent;
import dev.ghen.thirst.foundation.common.item.DrinkableItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.fluids.CauldronFluidContent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CanteenItem extends DrinkableItem {

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
    @SuppressWarnings("unused") // event parameter required by NeoForge event bus signature
    public static void onRegisterThirstValue(RegisterThirstValueEvent event) {
        // Register in VALID_DRINKS for the AppleSkin/ThirstWasTaken thirst-preview icons.
        // ThirstWasTaken's PlayerThirstManager.drink(LivingEntityUseItemEvent.Finish) checks
        // 'instanceof DrinkableItem → return' BEFORE calling IThirst.drink() – so registering
        // here does NOT cause a double drink. Our finishUsingItem is the sole thirst source.
        // NOTE: event.addDrink() is @Deprecated with empty body – write to the map directly.
        ThirstHelper.VALID_DRINKS.put(
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

        // Client-side: start the animation optimistically; the server will cancel it via
        // StopDrinkingPacket if the tank turns out to be empty or unreachable.
        if (level.isClientSide) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        // Server-side authoritative pre-checks --------------------------------
        if (player instanceof ServerPlayer sp) {
            Level targetLevel = getTargetLevel(level, linkedPos);
            if (targetLevel == null || !targetLevel.isLoaded(linkedPos.pos())) {
                player.displayClientMessage(
                        Component.translatable("item.endercanteen.canteen.out_of_range"), true);
                sendStopPacket(sp);
                return InteractionResultHolder.fail(stack);
            }
            IFluidHandler handler = getHandlerAt(targetLevel, linkedPos.pos());
            if (handler == null || findWaterStack(handler, drinkMb(), FluidAction.SIMULATE, targetLevel, linkedPos.pos()) == null) {
                player.displayClientMessage(
                        Component.translatable("item.endercanteen.canteen.no_water"), true);
                sendStopPacket(sp);
                return InteractionResultHolder.fail(stack);
            }
            if (!hasEnoughRf(stack)) {
                player.displayClientMessage(
                        Component.translatable("item.endercanteen.canteen.no_rf"), true);
                sendStopPacket(sp);
                return InteractionResultHolder.fail(stack);
            }
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return stack;

        GlobalPos linkedPos = stack.get(ModDataComponents.LINKED_POS.get());
        if (linkedPos == null) return stack;

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

        FluidStack drained = findWaterStack(handler, drinkMb(), FluidAction.EXECUTE, targetLevel, linkedPos.pos());
        if (drained == null || drained.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("item.endercanteen.canteen.no_water"), true);
            sendStopPacket(player);
            return stack;
        }

        int effectiveMb = Math.min(drained.getAmount(), drinkMb());
        int thirst   = calcThirst(effectiveMb);
        int quenched = calcQuenched(effectiveMb);

        int[] adjusted = applyRfCost(stack, handler, drained, thirst, quenched);
        if (adjusted == null) {
            // Not enough RF – fluid already refunded inside applyRfCost
            player.displayClientMessage(
                    Component.translatable("item.endercanteen.canteen.no_rf"), true);
            sendStopPacket(player);
            return stack;
        }
        thirst   = adjusted[0];
        quenched = adjusted[1];

        ThirstCompat.addThirst(player, thirst, quenched, drained, targetLevel, linkedPos.pos());
        player.playSound(SoundEvents.GENERIC_DRINK, 1.0f, 1.0f + (float)(Math.random() * 0.4 - 0.2));
        return stack;
    }

    // -------------------------------------------------------------------------
    // RF helpers
    // -------------------------------------------------------------------------

    /** Returns true when the canteen has enough RF for at least one thirst point (or RF is disabled). */
    private static boolean hasEnoughRf(ItemStack stack) {
        if (!EnderCanteenConfig.RF_ENABLED.get()) return true;
        int costPerPoint = EnderCanteenConfig.RF_COST_PER_THIRST_POINT.get();
        if (costPerPoint <= 0) return true;
        return new CanteenEnergyStorage(stack).getEnergyStored() >= costPerPoint;
    }

    /**
     * Consumes RF for the drink. Returns the adjusted [thirst, quenched] array, or
     * {@code null} if there is not enough RF (in which case the fluid is refunded into
     * {@code handler}).
     */
    private static int @Nullable [] applyRfCost(ItemStack stack, IFluidHandler handler,
                                     FluidStack drained, int thirst, int quenched) {
        if (!EnderCanteenConfig.RF_ENABLED.get()) return new int[]{thirst, quenched};
        int costPerPoint = EnderCanteenConfig.RF_COST_PER_THIRST_POINT.get();
        if (costPerPoint <= 0) return new int[]{thirst, quenched};

        CanteenEnergyStorage energy = new CanteenEnergyStorage(stack);
        int totalCost = (thirst + quenched) * costPerPoint;
        int extracted = energy.extractEnergy(totalCost, true); // simulate

        if (extracted < costPerPoint) {
            handler.fill(drained, FluidAction.EXECUTE); // refund fluid
            return null;
        }

        int affordablePoints = extracted / costPerPoint;
        int totalPoints = thirst + quenched;
        if (affordablePoints < totalPoints) {
            // Scale down: reduce quenched first, then thirst
            quenched = Math.min(quenched, affordablePoints);
            thirst   = Math.min(thirst,   affordablePoints - quenched);
        }
        energy.extractEnergy((thirst + quenched) * costPerPoint, false); // commit
        return new int[]{thirst, quenched};
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
        if (pos == null) {
            tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_unlinked"));
            tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_drink_amount", drinkMb()));
            return;
        }

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

        if (level != null && sameDimension && level.isLoaded(pos.pos())) {
            appendFluidTooltip(tooltip, level, pos.pos());
        }

        tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_drink_amount", drinkMb()));
        appendRfTooltip(stack, tooltip);
    }

    private static void appendFluidTooltip(List<Component> tooltip, Level level, BlockPos pos) {
        IFluidHandler handler = getHandlerAt(level, pos);
        if (handler == null) return;

        int totalWater = 0;
        int totalCapacity = 0;
        Integer purity = null;

        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack content = handler.getFluidInTank(i);
            totalCapacity += handler.getTankCapacity(i);
            if (content.isEmpty() || !content.getFluid().defaultFluidState().is(FluidTags.WATER)) continue;

            totalWater += content.getAmount();
            if (purity != null) continue;

            Integer p = content.get(ThirstComponent.PURITY);
            if (p != null) {
                purity = p;
            } else {
                // No FluidStack tag → read BLOCK_PURITY from BlockState (cauldron etc.)
                int bp = dev.ghen.thirst.content.purity.WaterPurity.getBlockPurity(level.getBlockState(pos));
                purity = (bp >= 0) ? bp : 2;
            }
        }

        if (totalCapacity > 0) {
            tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_fluid",
                    totalWater, totalCapacity));
        }
        if (purity != null) {
            tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_purity." + purity));
        }
    }

    private static void appendRfTooltip(ItemStack stack, List<Component> tooltip) {
        if (!EnderCanteenConfig.RF_ENABLED.get()) return;

        CanteenEnergyStorage energy = new CanteenEnergyStorage(stack);
        tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_rf",
                energy.getEnergyStored(), energy.getMaxEnergyStored()));

        int costPerPoint = EnderCanteenConfig.RF_COST_PER_THIRST_POINT.get();
        if (costPerPoint <= 0) return;

        int drinkCost = (calcThirst(drinkMb()) + calcQuenched(drinkMb())) * costPerPoint;
        tooltip.add(Component.translatable("item.endercanteen.canteen.tooltip_rf_cost",
                drinkCost, costPerPoint));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a StopDrinkingPacket to the client to cancel the drinking animation.
     * Called server-side whenever the drink is rejected (tank empty / unreachable).
     */
    private static void sendStopPacket(ServerPlayer player) {
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
        MinecraftServer server = currentLevel.getServer();
        if (server == null) return null;
        return server.getLevel(linkedPos.dimension());
    }

    /**
     * Drains water from the handler, returning the drained FluidStack (capped at mb).
     *
     * <p>For blocks backed by {@link CauldronFluidContent} (vanilla/modded cauldrons) we
     * manipulate the BlockState directly instead of going through {@code CauldronWrapper}.
     * {@code CauldronWrapper.updateLevel()} calls {@code block.defaultBlockState()} and then
     * sets only the level property – silently dropping any extra BlockState properties added
     * by mods (e.g. ThirstWasTaken's BLOCK_PURITY), which empties the cauldron completely.
     *
     * <p>For all other handlers the standard {@code drain(int, FluidAction)} path is used,
     * with a doubling-probe SIMULATE fallback for handlers that use coarse drain increments.
     */
    @Nullable
    private static FluidStack findWaterStack(IFluidHandler handler, int mb, FluidAction action,
                                             @Nullable Level level, @Nullable BlockPos pos) {
        // --- Cauldron fast-path: manipulate BlockState directly ---
        if (level != null && pos != null) {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            CauldronFluidContent cauldron = CauldronFluidContent.getForBlock(state.getBlock());
            if (cauldron != null && cauldron.fluid.defaultFluidState().is(FluidTags.WATER)) {
                int currentLevel = cauldron.currentLevel(state);
                if (currentLevel <= 0) return null;

                int oneLevelMb = cauldron.totalAmount / cauldron.maxLevel;
                if (oneLevelMb <= 0) return null;

                if (action.execute()) {
                    int newLevel = currentLevel - 1;
                    net.minecraft.world.level.block.state.BlockState newState;
                    if (newLevel == 0) {
                        newState = net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState();
                    } else if (cauldron.levelProperty != null) {
                        newState = state.setValue(cauldron.levelProperty, newLevel); // preserves BLOCK_PURITY and all other properties
                    } else {
                        return null; // no level property – cannot partially drain
                    }
                    level.setBlockAndUpdate(pos, newState);
                }
                return new FluidStack(cauldron.fluid, Math.min(oneLevelMb, mb));
            }
        }

        // --- Normal handler path ---
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack content = handler.getFluidInTank(i);
            if (content.isEmpty()) continue;
            if (!content.getFluid().defaultFluidState().is(FluidTags.WATER)) continue;

            // Try exactly mb mB first (works for fine-grained handlers like Create tanks).
            FluidStack result = handler.drain(mb, action);
            if (!result.isEmpty() && result.getFluid().defaultFluidState().is(FluidTags.WATER)) {
                return result;
            }

            // Coarse-increment fallback: find the minimum drainable amount via doubling
            // SIMULATE probes. Probe up to capacity*2 so we don't miss increments equal
            // to capacity (probe jumps 512 → 1024 while capacity = 1000).
            int capacity = handler.getTankCapacity(i);
            int probe = 1;
            int minIncrement = 0;
            while (probe <= (long) capacity * 2) {
                FluidStack probeResult = handler.drain(probe, FluidAction.SIMULATE);
                if (!probeResult.isEmpty() && probeResult.getFluid().defaultFluidState().is(FluidTags.WATER)) {
                    minIncrement = probeResult.getAmount();
                    break;
                }
                probe = (probe >= Integer.MAX_VALUE / 2) ? Integer.MAX_VALUE : probe * 2;
            }
            if (minIncrement <= 0) continue;

            if (action.execute()) {
                FluidStack drained = handler.drain(minIncrement, FluidAction.EXECUTE);
                if (!drained.isEmpty() && drained.getFluid().defaultFluidState().is(FluidTags.WATER)) {
                    return drained.copyWithAmount(Math.min(drained.getAmount(), mb));
                }
            } else {
                FluidStack sim = handler.drain(minIncrement, FluidAction.SIMULATE);
                if (!sim.isEmpty() && sim.getFluid().defaultFluidState().is(FluidTags.WATER)) {
                    return sim.copyWithAmount(Math.min(sim.getAmount(), mb));
                }
            }
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
