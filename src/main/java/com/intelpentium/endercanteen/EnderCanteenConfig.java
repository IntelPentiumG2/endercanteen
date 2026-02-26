package com.intelpentium.endercanteen;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.ModConfigSpec;

public class EnderCanteenConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue DRINK_AMOUNT_MB;
    public static final ModConfigSpec.IntValue THIRST_PER_250MB;
    public static final ModConfigSpec.IntValue QUENCHED_PER_250MB;
    public static final ModConfigSpec.IntValue NAUSEA_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue HUNGER_DURATION_SECONDS;

    // RF / Energy
    public static final ModConfigSpec.BooleanValue RF_ENABLED;
    public static final ModConfigSpec.IntValue RF_CAPACITY;
    public static final ModConfigSpec.IntValue RF_COST_PER_THIRST_POINT;

    // Dispenser
    public static final ModConfigSpec.BooleanValue DISPENSER_CAULDRON_INTERACTION;

    // Cauldron drinking
    public static final ModConfigSpec.BooleanValue CAULDRON_DRAIN_FULL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Ender Canteen configuration").push("general");

        DRINK_AMOUNT_MB = builder
                .comment("Amount of fluid (in mB) consumed from the linked tank per drink. Default: 500")
                .defineInRange("drinkAmountMb", 500, 1, 100_000);

        THIRST_PER_250MB = builder
                .comment("Thirst points restored per 250 mB of water consumed. Default: 2")
                .defineInRange("thirstPer250mb", 2, 0, 20);

        QUENCHED_PER_250MB = builder
                .comment("Quench (saturation) points restored per 250 mB of water consumed. Default: 2")
                .defineInRange("quenchedPer250mb", 2, 0, 20);

        builder.pop();
        builder.comment("Effect durations when drinking dirty water (in seconds)").push("effects");

        NAUSEA_DURATION_SECONDS = builder
                .comment("Duration of the Nausea effect when drinking dirty (purity 0) or slightly dirty (purity 1) water, in seconds. Default: 8")
                .defineInRange("nauseaDurationSeconds", 8, 0, 300);

        HUNGER_DURATION_SECONDS = builder
                .comment("Duration of the Hunger effect when drinking dirty (purity 0) water, in seconds. Default: 13")
                .defineInRange("hungerDurationSeconds", 13, 0, 300);

        builder.pop();

        builder.comment("RF/FE energy settings for the Canteen").push("energy");

        RF_ENABLED = builder
                .comment("If true, drinking requires RF/FE energy stored in the Canteen. Default: true")
                .define("rfEnabled", true);

        RF_CAPACITY = builder
                .comment("Maximum RF/FE the Canteen can store. Default: 100000")
                .defineInRange("rfCapacity", 100_000, 1, 10_000_000);

        RF_COST_PER_THIRST_POINT = builder
                .comment("RF/FE consumed per restored thirst+quench point combined. Default: 1000")
                .defineInRange("rfCostPerThirstPoint", 1_000, 0, 1_000_000);

        builder.pop();

        builder.comment("Cauldron drinking settings").push("cauldron");

        CAULDRON_DRAIN_FULL = builder
                .comment("If true, drinking from a linked cauldron drains it completely instead of one level at a time. Default: true")
                .define("cauldronDrainFull", true);

        builder.pop();

        builder.comment("Dispenser behaviour settings").push("dispenser");

        DISPENSER_CAULDRON_INTERACTION = builder
                .comment("If true, dispensers can fill cauldrons with water buckets and drain them with empty buckets. Default: true")
                .define("dispenserCauldronInteraction", true);

        builder.pop();
        SPEC = builder.build();
    }

    public static void register(ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, SPEC);
    }
}
