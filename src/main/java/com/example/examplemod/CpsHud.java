package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;

@Mod.EventBusSubscriber(modid = STapMod.MODID, value = Dist.CLIENT)
public class CpsHud {

    private static final boolean SHOW_RIGHT = true;     // показывать и ПКМ: "CPS: 7 | 3"
    private static final int X = 5;                     // отступ слева
    private static final int Y = 5;                     // отступ сверху
    private static final int COLOR = 0xFFFFFFFF;        // цвет текста (ARGB, первые FF обязательны)
    private static final int BACKGROUND = 0x90000000;   // плашка (0 = без плашки)

    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();
    private static boolean leftWasDown = false;
    private static boolean rightWasDown = false;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        long now = System.currentTimeMillis();

        boolean leftDown = mc.screen == null && mc.options.keyAttack.isDown();
        boolean rightDown = mc.screen == null && mc.options.keyUse.isDown();

        if (leftDown && !leftWasDown) LEFT.addLast(now);
        if (rightDown && !rightWasDown) RIGHT.addLast(now);
        leftWasDown = leftDown;
        rightWasDown = rightDown;

        purge(LEFT, now);
        purge(RIGHT, now);

        String text = SHOW_RIGHT
                ? "CPS: " + LEFT.size() + " | " + RIGHT.size()
                : "CPS: " + LEFT.size();

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;

        if (BACKGROUND != 0) {
            graphics.fill(X - 2, Y - 2, X + font.width(text) + 2, Y + font.lineHeight + 1, BACKGROUND);
        }
        graphics.drawString(font, text, X, Y, COLOR, true);
    }

    // убираем клики старше 1 секунды
    private static void purge(Deque<Long> clicks, long now) {
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
            clicks.pollFirst();
        }
    }
}
