package com.intelpentium.endercanteen.compat;

import com.intelpentium.endercanteen.EnderCanteenConfig;
import dev.ghen.thirst.content.registry.ThirstComponent;
import dev.ghen.thirst.foundation.common.capability.IThirst;
import dev.ghen.thirst.foundation.common.capability.ModAttachment;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
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
    public void addThirst(Player player, int baseThirst, int baseQuenched, @Nullable FluidStack fluid) {
        int purity = getPurity(fluid);

        // Scale thirst and quenched by purity level
        // baseThirst/baseQuenched are already scaled by mB (from CanteenItem.calcThirst/calcQuenched)
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

        // Apply purity effects (mirrors what MixinPlayer does for items)
        applyPurityEffects(player, purity);
    }

    @Override
    public boolean needsDrink(Player player) {
        IThirst data = player.getData(ModAttachment.PLAYER_THIRST.get());
        return data.getThirst() < 20;
    }

    /**
     * Reads the purity from the FluidStack's ThirstComponent.PURITY DataComponent.
     * Falls back to 3 (purified) if no component is set (plain water).
     */
    private static int getPurity(@Nullable FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) return 2;
        Integer purity = fluid.get(ThirstComponent.PURITY);
        // Default purity when no component is present = plain water = 2 (purified)
        return purity != null ? purity : 2;
    }

    /**
     * Applies status effects matching the given purity level.
     *
     * <ul>
     *   <li>purity 0 (dirty): Nausea 8s + Hunger 13s</li>
     *   <li>purity 1 (slightly dirty): Nausea 8s</li>
     *   <li>purity 2+ : no effects</li>
     * </ul>
     */
    public static void applyPurityEffects(Player player, FluidStack fluid) {
        applyPurityEffects(player, getPurity(fluid));
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

