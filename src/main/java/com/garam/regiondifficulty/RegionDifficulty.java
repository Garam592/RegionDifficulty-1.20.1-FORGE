package com.garam.regiondifficulty;

import com.garam.regiondifficulty.client.ClientDifficultyCache;
import com.garam.regiondifficulty.client.hud.DifficultyHudOverlay;
import com.garam.regiondifficulty.item.DifficultyIndicatorItem;
import com.garam.regiondifficulty.item.ModCreativeModeTabs;
import com.garam.regiondifficulty.item.ModItems;
import com.garam.regiondifficulty.network.NetworkHandler;
import com.garam.regiondifficulty.network.RequestDifficultyPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
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

        // 注册网络通道（C2S/S2C）
        NetworkHandler.register();
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

    }

    // 你可以使用 SubscribeEvent 并让事件总线自动发现要调用的方法
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {

    }

    // 你可以使用 EventBusSubscriber 自动注册类中所有标注了 @SubscribeEvent 的静态方法
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {

        }
    }

    /**
     * 客户端 FORGE 事件 —— 难度指示器 HUD 渲染和定时请求。
     */
    @Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientForgeEvents
    {
        private static String lastDim = "";

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;

            boolean holding = mc.player.getMainHandItem().getItem() instanceof DifficultyIndicatorItem
                           || mc.player.getOffhandItem().getItem() instanceof DifficultyIndicatorItem;

            if (!holding) {
                ClientDifficultyCache.invalidate();
                lastDim = "";
                return;
            }

            // 维度变化时立即请求
            String curDim = mc.player.level().dimension().location().toString();
            boolean dimChanged = !curDim.equals(lastDim);
            if (dimChanged) {
                lastDim = curDim;
            }

            // 每 20 tick 或维度变化时发送请求
            if (dimChanged || mc.player.tickCount % 20 == 0) {
                NetworkHandler.sendToServer(new RequestDifficultyPacket());
            }
        }

        @SubscribeEvent
        public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
            // 仅在 CHAT_PANEL 类型渲染一次，避免每帧多次绘制
            if (event.getOverlay() != VanillaGuiOverlay.CHAT_PANEL.type()) return;
            DifficultyHudOverlay.render(event.getGuiGraphics(), event.getPartialTick());
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientDifficultyCache.invalidate();
            lastDim = "";
        }
    }
}
