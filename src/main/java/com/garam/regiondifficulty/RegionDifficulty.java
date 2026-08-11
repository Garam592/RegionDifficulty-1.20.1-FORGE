package com.garam.regiondifficulty;

import com.garam.regiondifficulty.item.ModCreativeModeTabs;
import com.garam.regiondifficulty.item.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

// 此处的值应与 META-INF/mods.toml 文件中的条目匹配
@Mod(RegionDifficulty.MODID)
public class RegionDifficulty
{
    // 在一个公共位置定义 mod id，方便所有地方引用
    public static final String MODID = "region_difficulty";
    // 直接引用一个 slf4j 日志记录器
    private static final Logger LOGGER = LogUtils.getLogger();

    public RegionDifficulty(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // 注册 commonSetup 方法用于模组加载
        modEventBus.addListener(this::commonSetup);
        ModItems.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        // 将自身注册到服务器和其他我们感兴趣的游戏事件中
        MinecraftForge.EVENT_BUS.register(this);

        // 将物品注册到创造模式物品栏标签页
        modEventBus.addListener(this::addCreative);

        // 注册本模组的 ForgeConfigSpec，以便 Forge 为我们创建和加载配置文件
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // 一些通用设置代码
        LOGGER.info("来自通用设置——你好");

        if (Config.logDirtBlock)
            LOGGER.info("泥土方块 >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);
    }

    // 将示例方块物品添加到建筑方块标签页
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
//        if(event.getTabKey()== CreativeModeTabs.COMBAT){
//            event.accept(ModItems.EX_ITEM);
//        }
    }

    // 你可以使用 SubscribeEvent 并让事件总线自动发现要调用的方法
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // 服务器启动时执行一些操作
        LOGGER.info("来自服务器启动——你好");
    }

    // 你可以使用 EventBusSubscriber 自动注册类中所有标注了 @SubscribeEvent 的静态方法
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // 一些客户端设置代码
            LOGGER.info("来自客户端设置——你好");
            LOGGER.info("Minecraft 名称 >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
