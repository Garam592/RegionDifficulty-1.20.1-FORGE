package com.garam.regiondifficulty.item;

import com.garam.regiondifficulty.RegionDifficulty;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS=
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RegionDifficulty.MODID);
    public static final RegistryObject<CreativeModeTab> TEST_TAB=
            CREATIVE_MODE_TABS.register("test_tab",()-> CreativeModeTab.builder().icon(()->
        new ItemStack(ModItems.EX_ITEM.get())).title(Component.translatable("itemGroup.region_difficulty_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModItems.EX_ITEM.get());
                    }).build());
    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
