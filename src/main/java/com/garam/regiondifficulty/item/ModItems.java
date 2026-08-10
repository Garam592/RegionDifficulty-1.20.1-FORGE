package com.garam.regiondifficulty.item;

import com.garam.regiondifficulty.RegionDifficulty;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS=
            DeferredRegister.create(ForgeRegistries.ITEMS, RegionDifficulty.MODID);
    public static final RegistryObject<Item> EX_ITEM= ITEMS.register("ex_item",
            () ->new Item(new Item.Properties()));
    public static void register(IEventBus eventBus){
        ITEMS.register(eventBus);
    }
}
