package dev.lvstrng.argon.utils;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Item;
import org.lwjgl.glfw.GLFW;

import static dev.lvstrng.argon.Argon.mc;

public final class AdvancedInputSimulator {
    private AdvancedInputSimulator() {}

    public static void leftClick(int minPressMs, int maxPressMs) {
        MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT, MathUtils.randomInt(minPressMs, maxPressMs));
    }

    public static void rightClick(int minPressMs, int maxPressMs) {
        MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT, MathUtils.randomInt(minPressMs, maxPressMs));
    }

    public static boolean selectHotbarSlot(int slot, Item fallback) {
        if (mc.player == null) return false;
        int clamped = Math.max(0, Math.min(8, slot));
        if (mc.player.getInventory().getStack(clamped).isOf(fallback)) {
            InventoryUtils.setInvSlot(clamped);
            return true;
        }
        return InventoryUtils.selectItemFromHotbar(fallback);
    }

    public static void setKey(KeyBinding key, boolean pressed) {
        if (key != null) key.setPressed(pressed);
    }

    public static void tapKey(KeyBinding key, int pressMs) {
        if (key == null) return;
        Thread.ofVirtual().start(() -> {
            try {
                key.setPressed(true);
                Thread.sleep(Math.max(1, pressMs));
            } catch (InterruptedException ignored) {
            } finally {
                key.setPressed(false);
            }
        });
    }
}
