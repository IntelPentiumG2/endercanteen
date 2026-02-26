package com.intelpentium.endercanteen.dispenser;

import com.intelpentium.endercanteen.EnderCanteenConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.CauldronFluidContent;
import org.jetbrains.annotations.NotNull;

/**
 * Registers custom dispenser behaviours so that:
 * <ul>
 *   <li>A <b>Water Bucket</b> dispensed at an empty or partially filled cauldron fills
 *       it completely (all levels at once) and returns an empty bucket – matching vanilla
 *       bucket behaviour.</li>
 *   <li>An <b>Empty Bucket</b> dispensed at a water cauldron empties it completely and
 *       returns a water bucket.</li>
 * </ul>
 *
 * <p>All non-cauldron targets fall through to the <em>original</em> vanilla behavior that
 * was registered before this mod loaded, so existing interactions (e.g. placing/scooping
 * water/lava source blocks) continue to work unchanged.
 *
 * <p>Register by calling {@link #register()} during {@code FMLCommonSetupEvent}.
 */
public class CauldronDispenserBehavior {

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers the dispenser behaviours for {@link Items#WATER_BUCKET} and
     * {@link Items#BUCKET}.  Call once during common setup (enqueued via
     * {@code event.enqueueWork(...)}).
     */
    public static void register() {
        if (!EnderCanteenConfig.DISPENSER_CAULDRON_INTERACTION.get()) return;

        // Capture the existing (vanilla) behaviors BEFORE overwriting them so they
        // can be used as fallbacks for non-cauldron targets.
        DispenseItemBehavior vanillaWaterBucket = DispenserBlock.DISPENSER_REGISTRY.get(Items.WATER_BUCKET);
        DispenseItemBehavior vanillaEmptyBucket = DispenserBlock.DISPENSER_REGISTRY.get(Items.BUCKET);

        // Fall back to DefaultDispenseItemBehavior if vanilla somehow didn't register one.
        if (vanillaWaterBucket == null) vanillaWaterBucket = new DefaultDispenseItemBehavior();
        if (vanillaEmptyBucket  == null) vanillaEmptyBucket  = new DefaultDispenseItemBehavior();

        DispenserBlock.registerBehavior(Items.WATER_BUCKET, new FillCauldronBehavior(vanillaWaterBucket));
        DispenserBlock.registerBehavior(Items.BUCKET,       new DrainCauldronBehavior(vanillaEmptyBucket));
    }

    // -------------------------------------------------------------------------
    // Fill behaviour – Water Bucket → Cauldron
    // -------------------------------------------------------------------------

    /**
     * Dispenses a water bucket at the target block.
     * <ul>
     *   <li>If the target is an empty or partially filled water cauldron, it fills it
     *       completely (all levels) and returns an empty bucket – identical to vanilla
     *       hand-use behaviour.</li>
     *   <li>If the cauldron is already full, the item is dispensed normally (vanilla ejection).</li>
     *   <li>For all other target blocks the vanilla dispense logic is used as fallback.</li>
     * </ul>
     */
    private static class FillCauldronBehavior implements DispenseItemBehavior {

        private final DispenseItemBehavior fallback;

        FillCauldronBehavior(DispenseItemBehavior fallback) {
            this.fallback = fallback;
        }

        @Override
        @SuppressWarnings("resource") // ServerLevel is not meant to be closed here
        public @NotNull ItemStack dispense(@NotNull BlockSource source, @NotNull ItemStack stack) {
            Level level = source.level();
            Direction facing = source.state().getValue(DispenserBlock.FACING);
            BlockPos targetPos = source.pos().relative(facing);
            BlockState targetState = level.getBlockState(targetPos);

            CauldronFluidContent content = CauldronFluidContent.getForBlock(targetState.getBlock());

            // Target is an empty cauldron – fill completely.
            if (targetState.is(Blocks.CAULDRON)) {
                level.setBlockAndUpdate(targetPos,
                        Blocks.WATER_CAULDRON.defaultBlockState()
                                .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
                return new ItemStack(Items.BUCKET);
            }

            // Target already has water cauldron content – fill to max if not already full.
            if (content != null && content.fluid.isSame(net.minecraft.world.level.material.Fluids.WATER)) {
                int current = content.currentLevel(targetState);
                if (current < content.maxLevel) {
                    // Set to max level, preserving all other BlockState properties (e.g. BLOCK_PURITY).
                    if (content.levelProperty != null) {
                        level.setBlockAndUpdate(targetPos,
                                targetState.setValue(content.levelProperty, content.maxLevel));
                    } else {
                        level.setBlockAndUpdate(targetPos, content.block.defaultBlockState());
                    }
                    return new ItemStack(Items.BUCKET);
                }
                // Cauldron is already full → fall through to vanilla (eject the bucket).
            }

            // Fallback: delegate to the original vanilla behavior (places water source block etc.).
            return fallback.dispense(source, stack);
        }
    }

    // -------------------------------------------------------------------------
    // Drain behaviour – Empty Bucket → Water Cauldron
    // -------------------------------------------------------------------------

    /**
     * Dispenses an empty bucket at the target block.
     * <ul>
     *   <li>If the target is a water cauldron with any water in it, empties it completely
     *       and returns a water bucket – identical to vanilla hand-use behaviour.</li>
     *   <li>For all other target blocks the vanilla dispense logic is used as fallback.</li>
     * </ul>
     */
    private static class DrainCauldronBehavior implements DispenseItemBehavior {

        private final DispenseItemBehavior fallback;

        DrainCauldronBehavior(DispenseItemBehavior fallback) {
            this.fallback = fallback;
        }

        @Override
        @SuppressWarnings("resource") // ServerLevel is not meant to be closed here
        public @NotNull ItemStack dispense(@NotNull BlockSource source, @NotNull ItemStack stack) {
            Level level = source.level();
            Direction facing = source.state().getValue(DispenserBlock.FACING);
            BlockPos targetPos = source.pos().relative(facing);
            BlockState targetState = level.getBlockState(targetPos);

            CauldronFluidContent content = CauldronFluidContent.getForBlock(targetState.getBlock());

            if (content != null && content.fluid.isSame(net.minecraft.world.level.material.Fluids.WATER)) {
                int current = content.currentLevel(targetState);
                if (current > 0) {
                    // Empty the cauldron completely, like using a bucket by hand.
                    level.setBlockAndUpdate(targetPos, Blocks.CAULDRON.defaultBlockState());
                    return new ItemStack(Items.WATER_BUCKET);
                }
            }

            // Fallback: delegate to the original vanilla behavior (scoops water/lava source blocks etc.).
            return fallback.dispense(source, stack);
        }
    }
}


















