package com.garam.regiondifficulty.client.hud;

import com.garam.regiondifficulty.client.ClientDifficultyCache;
import com.garam.regiondifficulty.item.DifficultyIndicatorItem;
import com.garam.regiondifficulty.network.DifficultyDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

/**
 * 区域难度 HUD 叠加层 —— 当手持 DifficultyIndicatorItem 时渲染。
 */
public final class DifficultyHudOverlay {

    private static final int BG_COLOR = 0x80000000;
    private static final int GREEN  = 0x55FF55;
    private static final int YELLOW = 0xFFFF55;
    private static final int RED    = 0xFF5555;
    private static final int GRAY   = 0xAAAAAA;
    private static final int WHITE  = 0xFFFFFF;

    private DifficultyHudOverlay() {}

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 检查是否手持指示器
        if (!isHoldingIndicator(mc.player.getMainHandItem())
                && !isHoldingIndicator(mc.player.getOffhandItem())) {
            return;
        }

        DifficultyDataPacket data = ClientDifficultyCache.get();
        long now = mc.level.getGameTime();

        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = screenW - 170;
        int y = 45;

        if (data == null || ClientDifficultyCache.isStale(now)) {
            drawLine(graphics, x, y, Component.translatable("hud.region_difficulty.no_data"), GRAY);
            return;
        }

        // 颜色编码
        int color = data.overallMult < 1.0F ? GREEN
                  : data.overallMult <= 2.0F ? YELLOW : RED;

        String overallStr = String.format("%.2f", data.overallMult);

        // 半透明背景
        int lineHeight = 10;
        int lines = data.structMult > 1.01F ? 6 : 5;
        int bgW = 155;
        int bgH = lines * lineHeight + 8;
        graphics.fill(x - 4, y - 4, x + bgW, y + bgH, BG_COLOR);

        // 标题行
        graphics.drawString(mc.font,
                Component.translatable("hud.region_difficulty.overall").append(": " + overallStr),
                x, y, color, true);
        y += lineHeight + 2;

        // 分项
        drawLine(graphics, x, y, dimLine(data), WHITE); y += lineHeight;
        drawLine(graphics, x, y, biomeLine(data), WHITE); y += lineHeight;
        drawLine(graphics, x, y, depthLine(data), WHITE); y += lineHeight;
        if (data.structMult > 1.01F) {
            drawLine(graphics, x, y, structLine(data), WHITE); y += lineHeight;
        }
        drawLine(graphics, x, y, Component.literal("综合倍率: " + String.format("%.2f", data.overallMult)), color);
    }

    private static boolean isHoldingIndicator(ItemStack stack) {
        return stack.getItem() instanceof DifficultyIndicatorItem;
    }

    private static void drawLine(GuiGraphics g, int x, int y, Component text, int color) {
        g.drawString(Minecraft.getInstance().font, text, x, y, color, true);
    }

    private static Component dimLine(DifficultyDataPacket d) {
        String shortName = shortenDim(d.dimId);
        return Component.literal("维度(" + shortName + "): x" + fmt(d.dimMult));
    }

    private static Component biomeLine(DifficultyDataPacket d) {
        String shortName = shorten(d.biomeId);
        return Component.literal("生物群系(" + shortName + "): x" + fmt(d.biomeMult));
    }

    private static Component depthLine(DifficultyDataPacket d) {
        return Component.literal("深度(Y=" + d.playerY + "): x" + fmt(d.depthMult));
    }

    private static Component structLine(DifficultyDataPacket d) {
        return Component.literal("结构: x" + fmt(d.structMult));
    }

    private static String fmt(float v) {
        return String.format("%.2f", v);
    }

    private static String shorten(String id) {
        int colon = id.lastIndexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static String shortenDim(String id) {
        // 去掉 minecraft: 前缀
        String s = shorten(id);
        // 翻译常见维度名
        return switch (s) {
            case "overworld" -> "主世界";
            case "the_nether" -> "下界";
            case "the_end" -> "末地";
            default -> s;
        };
    }
}
