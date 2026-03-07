package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.ItemUseListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.*;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public final class AnchorMacro extends Module implements TickListener, ItemUseListener {

    // --- Feature toggles ---
    private final BooleanSetting placer = new BooleanSetting(
            EncryptedString.of("Placer"), false)
            .setDescription(EncryptedString.of("Places respawn anchors from your hotbar"));

    private final BooleanSetting charger = new BooleanSetting(
            EncryptedString.of("Charger"), true)
            .setDescription(EncryptedString.of("Charges an uncharged anchor with glowstone"));

    private final BooleanSetting exploder = new BooleanSetting(
            EncryptedString.of("Exploder"), true)
            .setDescription(EncryptedString.of("Detonates a charged anchor"));

    // --- Timings ---
    private final MinMaxSetting swapDelay = new MinMaxSetting(
            EncryptedString.of("Swap Delay"), 0, 20, 1, 2, 4)
            .setDescription(EncryptedString.of("Ticks to wait before swapping items"));

    private final MinMaxSetting clickDelay = new MinMaxSetting(
            EncryptedString.of("Click Delay"), 0, 20, 1, 2, 4)
            .setDescription(EncryptedString.of("Ticks to wait between right-click actions"));

    // --- Safety & misc ---
    private final BooleanSetting safeExplode = new BooleanSetting(
            EncryptedString.of("Safe Explode"), true)
            .setDescription(EncryptedString.of("Sneaks before exploding so you don't take full damage"));

    private final BooleanSetting useGate = new BooleanSetting(
            EncryptedString.of("Use Gate"), true)
            .setDescription(EncryptedString.of("Cancels if you're using a bow, shield, or eating"));

    private final BooleanSetting clickSimulation = new BooleanSetting(
            EncryptedString.of("Click Simulation"), false)
            .setDescription(EncryptedString.of("Simulates real mouse click for CPS counters"));

    private final NumberSetting explodeSlot = new NumberSetting(
            EncryptedString.of("Explode Slot"), 1, 9, 9, 1)
            .setDescription(EncryptedString.of("Hotbar slot to switch to before exploding (1-9)"));

    // Ticks since last action for each sub-function
    private int placerSwapTimer  = 0;
    private int chargerSwapTimer = 0;
    private int exploderSwapTimer = 0;
    private int clickTimer       = 0;

    // Sneak state for safe explode
    private boolean sneaking   = false;
    private int sneakTimer     = 0;

    public AnchorMacro() {
        super(EncryptedString.of("Anchor Macro"),
                EncryptedString.of("Automates placing, charging, and exploding respawn anchors"),
                -1,
                Category.COMBAT);
        addSettings(placer, charger, exploder,
                swapDelay, clickDelay,
                safeExplode, useGate, clickSimulation, explodeSlot);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(ItemUseListener.class, this);
        resetTimers();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(ItemUseListener.class, this);
        // FIX: guard null player before releasing sneak key
        if (sneaking && mc.player != null) {
            mc.options.sneakKey.setPressed(false);
            sneaking = false;
        }
        resetTimers();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        // FIX: guard against null networkHandler (causes crash on disconnect)
        if (mc.getNetworkHandler() == null) return;

        // Release sneak after short hold
        if (sneaking) {
            sneakTimer++;
            if (sneakTimer >= 4) {
                mc.options.sneakKey.setPressed(false);
                sneaking = false;
                sneakTimer = 0;
            }
        }

        // Gate: only act when holding RMB
        if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS)
            return;

        // Gate: don't interfere with blocking/eating/bows
        if (useGate.getValue()) {
            UseAction action = mc.player.getMainHandStack().getUseAction();
            if (action == UseAction.BLOCK || action == UseAction.SPEAR
                    || action == UseAction.BOW   || action == UseAction.CROSSBOW
                    || action == UseAction.EAT   || action == UseAction.DRINK) return;
        }

        boolean hasAnchor    = InventoryUtils.hasItemInHotbar(i -> i == Items.RESPAWN_ANCHOR);
        boolean hasGlowstone = InventoryUtils.hasItemInHotbar(i -> i == Items.GLOWSTONE);

        if (!hasAnchor || !hasGlowstone) {
            tickTimers();
            return;
        }

        HitResult hit = WorldUtils.getHitResult(4.5);
        // FIX: null-check hit before pattern matching - causes NPE when not looking at anything
        if (hit == null) {
            tickTimers();
            return;
        }

        // --- PLACER ---
        if (placer.getValue()) {
            if (placerSwapTimer  >= MathUtils.randomInt(swapDelay.getMinInt(),  swapDelay.getMaxInt())
                    && clickTimer >= MathUtils.randomInt(clickDelay.getMinInt(), clickDelay.getMaxInt())) {

                if (InventoryUtils.selectItemFromHotbar(Items.RESPAWN_ANCHOR)) {
                    mc.options.useKey.setPressed(false);
                    if (clickSimulation.getValue())
                        MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

                    if (hit instanceof BlockHitResult blockHit)
                        WorldUtils.placeBlock(blockHit, true);

                    placerSwapTimer = 0;
                    clickTimer      = 0;
                }
            }
        }

        // --- CHARGER ---
        if (charger.getValue() && hit instanceof BlockHitResult blockHit) {
            if (BlockUtils.isBlock(blockHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                    && BlockUtils.isAnchorNotCharged(blockHit.getBlockPos())) {

                if (chargerSwapTimer >= MathUtils.randomInt(swapDelay.getMinInt(), swapDelay.getMaxInt())
                        && clickTimer >= MathUtils.randomInt(clickDelay.getMinInt(), clickDelay.getMaxInt())
                        && InventoryUtils.selectItemFromHotbar(Items.GLOWSTONE)) {

                    mc.options.useKey.setPressed(false);
                    if (clickSimulation.getValue())
                        MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

                    WorldUtils.placeBlock(blockHit, true);
                    chargerSwapTimer = 0;
                    clickTimer       = 0;
                }
            }
        }

        // --- EXPLODER ---
        if (exploder.getValue() && hit instanceof BlockHitResult blockHit) {
            if (BlockUtils.isBlock(blockHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                    && BlockUtils.isAnchorCharged(blockHit.getBlockPos())) {

                if (exploderSwapTimer >= MathUtils.randomInt(swapDelay.getMinInt(), swapDelay.getMaxInt())
                        && clickTimer >= MathUtils.randomInt(clickDelay.getMinInt(), clickDelay.getMaxInt())) {

                    // FIX: clamp slot to valid range 0-8 to prevent ArrayIndexOutOfBoundsException
                    int slot = Math.max(0, Math.min(8, explodeSlot.getValueInt() - 1));
                    InventoryUtils.setInvSlot(slot);

                    if (safeExplode.getValue()) {
                        mc.options.sneakKey.setPressed(true);
                        sneaking   = true;
                        sneakTimer = 0;
                    }

                    mc.options.useKey.setPressed(false);
                    if (clickSimulation.getValue())
                        MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

                    // FIX: guard networkHandler before sending packet
                    if (mc.getNetworkHandler() != null)
                        mc.getNetworkHandler().sendPacket(
                                new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, blockHit, 0));

                    exploderSwapTimer = 0;
                    clickTimer        = 0;
                }
            }
        }

        tickTimers();
    }

    @Override
    public void onItemUse(ItemUseListener.ItemUseEvent event) {
        if (mc.player == null || mc.world == null) return;

        // Safe-explode sneak: intercept glowstone use on a charged anchor
        if (safeExplode.getValue()
                && mc.player.isHolding(Items.GLOWSTONE)
                && mc.crosshairTarget instanceof BlockHitResult blockHit
                && BlockUtils.isBlock(blockHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                && BlockUtils.isAnchorCharged(blockHit.getBlockPos())) {
            mc.options.sneakKey.setPressed(true);
            sneaking   = true;
            sneakTimer = 0;
        }
    }

    // -------------------------------------------------------------------------

    private void tickTimers() {
        placerSwapTimer++;
        chargerSwapTimer++;
        exploderSwapTimer++;
        clickTimer++;
        // FIX: sneakTimer is managed only in onTick() — removed duplicate increment that caused double-speed sneak release
    }

    private void resetTimers() {
        placerSwapTimer   = 0;
        chargerSwapTimer  = 0;
        exploderSwapTimer = 0;
        clickTimer        = 0;
        sneakTimer        = 0;
        sneaking          = false;
    }
}
