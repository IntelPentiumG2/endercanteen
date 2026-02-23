package com.intelpentium.endercanteen.registry;

import com.intelpentium.endercanteen.EnderCanteen;
import com.intelpentium.endercanteen.item.CanteenItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EnderCanteen.MODID);

    public static final DeferredItem<CanteenItem> CANTEEN = ITEMS.register("canteen",
            () -> new CanteenItem(new net.minecraft.world.item.Item.Properties().stacksTo(1)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}

