package com.intelpentium.endercanteen.compat;

import com.intelpentium.endercanteen.EnderCanteenConfig;
import dev.ghen.thirst.content.purity.WaterPurity;
import dev.ghen.thirst.content.registry.ThirstComponent;
import dev.ghen.thirst.foundation.common.capability.IThirst;
import dev.ghen.thirst.foundation.common.capability.ModAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Direct integration with "Thirst Was Taken" (dev.ghen.thirst).
 *
 * <p>Purity scale (ThirstComponent.PURITY on FluidStack):</p>
 * <ul>
 *   <li>0 – dirty        → Nausea (config, default 8s) + Hunger (config, default 13s), no quench, + Math.max(1, baseThirst / 2) thirst</li>
 *   <li>1 – slightly dirty → Nausea (config, default 8s), low quench, + baseThirst thirst</li>
 *   <li>2 – acceptable   → no effects, normal quench, + baseThirst thirst</li>
 *   <li>3 – purified     → no effects, full quench, + baseThirst thirst</li>
 * </ul>
 */
public class ThirstWasTakenProvider implements IThirstProvider {

    /** The mod ID used by Thirst Was Taken. */
    private static final String MOD_ID = "thirst";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    @Override
    public void addThirst(Player player, int baseThirst, int baseQuenched, @Nullable FluidStack fluid,
                          @Nullable Level level, @Nullable BlockPos sourcePos) {
        int purity = getPurity(fluid, level, sourcePos);

        int thirst;
        int quenched;
        switch (purity) {
            case 0 -> { thirst = Math.max(1, baseThirst / 2); quenched = 0; }  // dirty
            case 1 -> { thirst = baseThirst; quenched = baseQuenched / 2; }     // slightly dirty
            default -> { thirst = baseThirst; quenched = baseQuenched; }        // acceptable (2) or purified (3)
        }

        IThirst data = player.getData(ModAttachment.PLAYER_THIRST.get());
        data.drink(thirst, quenched);
        data.updateThirstData(player);

        applyPurityEffects(player, purity);
    }

    /**
     * Reads the purity for a drink using the following priority:
     * <ol>
     *   <li>ThirstComponent.PURITY DataComponent on the FluidStack (e.g. water stored in a
     *       Create tank that preserved purity tags).</li>
     *   <li>WaterPurity.BLOCK_PURITY BlockState property at the source position
     *       (set by ThirstWasTaken's MixinLayeredCauldronBlock on cauldrons).</li>
     *   <li>Default: 2 (acceptable) – matches ThirstWasTaken's default for untagged water.</li>
     * </ol>
     */
    public static int getPurity(@Nullable FluidStack fluid, @Nullable Level level, @Nullable BlockPos sourcePos) {
        // 1. FluidStack tag takes priority
        if (fluid != null && !fluid.isEmpty()) {
            Integer p = fluid.get(ThirstComponent.PURITY);
            if (p != null) return p;
        }

        // 2. Read BLOCK_PURITY property from the source block's BlockState (cauldron etc.)
        //    WaterPurity.getBlockPurity(BlockState) returns (BLOCK_PURITY value - 1), or -1
        //    if the block has no such property.
        if (level != null && sourcePos != null) {
            int blockPurity = WaterPurity.getBlockPurity(level.getBlockState(sourcePos));
            if (blockPurity >= 0) return blockPurity;
        }

        // 3. Fallback: acceptable
        return 2;
    }

    private static void applyPurityEffects(Player player, int purity) {
        int nauseaTicks = EnderCanteenConfig.NAUSEA_DURATION_SECONDS.get() * 20;
        int hungerTicks = EnderCanteenConfig.HUNGER_DURATION_SECONDS.get() * 20;
        switch (purity) {
            case 0 -> {
                // dirty water: nausea + hunger
                if (nauseaTicks > 0)
                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, nauseaTicks, 0, false, true));
                if (hungerTicks > 0)
                    player.addEffect(new MobEffectInstance(MobEffects.HUNGER,    hungerTicks, 0, false, true));
            }
            case 1 -> {
                // slightly dirty: nausea only
                if (nauseaTicks > 0)
                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, nauseaTicks, 0, false, true));
            }
            // purity 2 (acceptable) and 3 (purified): no negative effects
        }
    }
}
