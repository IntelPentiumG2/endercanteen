package com.intelpentium.endercanteen;

import com.intelpentium.endercanteen.compat.ThirstCompat;
import com.intelpentium.endercanteen.compat.ThirstWasTakenProvider;
import com.intelpentium.endercanteen.item.CanteenEnergyStorage;
import com.intelpentium.endercanteen.item.CanteenItem;
import com.intelpentium.endercanteen.network.StopDrinkingPacket;
import com.intelpentium.endercanteen.registry.ModBlockEntities;
import com.intelpentium.endercanteen.registry.ModBlocks;
import com.intelpentium.endercanteen.registry.ModDataComponents;
import com.intelpentium.endercanteen.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(EnderCanteen.MODID)
public class EnderCanteen {

    public static final String MODID = "endercanteen";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredItem<BlockItem> FLUID_TAP_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem("fluid_tap", ModBlocks.FLUID_TAP);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CANTEEN_TAB =
            CREATIVE_MODE_TABS.register("canteen_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.endercanteen"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.CANTEEN.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ModItems.CANTEEN.get());
                        output.accept(FLUID_TAP_ITEM.get());
                    })
                    .build());

    public EnderCanteen(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModDataComponents.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register config
        EnderCanteenConfig.register(modContainer);

        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        if (ThirstWasTakenProvider.isModLoaded()) {
            NeoForge.EVENT_BUS.addListener(CanteenItem::onRegisterThirstValue);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ThirstCompat.init();
        LOGGER.info("[EnderCanteen] Initialised. Thirst Was Taken present: {}",
                ThirstWasTakenProvider.isModLoaded());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                StopDrinkingPacket.TYPE,
                StopDrinkingPacket.CODEC,
                StopDrinkingPacket::handle
        );
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.FLUID_TAP.get(),
                (be, side) -> be.findAdjacentHandler()
        );

        // RF / Energy capability for the Canteen item
        event.registerItem(
                Capabilities.EnergyStorage.ITEM,
                (stack, ctx) -> new CanteenEnergyStorage(stack),
                ModItems.CANTEEN.get()
        );
    }
}
