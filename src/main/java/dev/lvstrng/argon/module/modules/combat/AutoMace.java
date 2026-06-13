package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.MinMaxSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import dev.lvstrng.argon.utils.MathUtils;
import dev.lvstrng.argon.utils.MouseSimulation;
import dev.lvstrng.argon.utils.TimerUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public final class AutoMace extends Module implements TickListener {
    private final NumberSetting densityThreshold = new NumberSetting(EncryptedString.of("Density Threshold"), 0, 20, 3, 0.5)
            .setDescription(EncryptedString.of("Minimum falling strength before swapping and smashing"));
    private final NumberSetting velocityThreshold = new NumberSetting(EncryptedString.of("Fall Velocity"), 0.01, 2, 0.18, 0.01)
            .setDescription(EncryptedString.of("Required downward velocity for optimal mace timing"));
    private final MinMaxSetting hitDelay = new MinMaxSetting(EncryptedString.of("Timing Delay"), 0, 150, 1, 8, 32)
            .setDescription(EncryptedString.of("Humanized delay window before the smash"));
    private final BooleanSetting autoStunSlam = new BooleanSetting(EncryptedString.of("Auto Stun Slam"), true)
            .setDescription(EncryptedString.of("Axe hit into mace 1-tick combo"));
    private final BooleanSetting smartSwapBack = new BooleanSetting(EncryptedString.of("Smart Swap Back"), true)
            .setDescription(EncryptedString.of("Swaps back after landing or after the hit"));
    private final BooleanSetting breachOnGround = new BooleanSetting(EncryptedString.of("Breach Swap Ground"), true)
            .setDescription(EncryptedString.of("Allows swapping only when grounded for breach-style pressure"));
    private final BooleanSetting silentSwap = new BooleanSetting(EncryptedString.of("Silent Swap"), false)
            .setDescription(EncryptedString.of("Uses selected-slot packets instead of visible hotbar changes"));
    private final BooleanSetting inputSimulation = new BooleanSetting(EncryptedString.of("Input Simulation"), true)
            .setDescription(EncryptedString.of("Simulates a short left-click before attacking"));

    private final TimerUtils timer = new TimerUtils();
    private int previousSlot = -1;
    private long nextSwingDelay;
    private boolean queuedSlam;

    public AutoMace() {
        super(EncryptedString.of("Auto Mace"), EncryptedString.of("Times mace swaps, stun slams and swap-backs"), -1, Category.COMBAT);
        addSettings(densityThreshold, velocityThreshold, hitDelay, autoStunSlam, smartSwapBack, breachOnGround, silentSwap, inputSimulation);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        restoreSlot();
        reset();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)) {
            if (mc.player.isOnGround()) restoreSlot();
            return;
        }

        boolean falling = mc.player.fallDistance >= densityThreshold.getValueFloat()
                && mc.player.getVelocity().y <= -velocityThreshold.getValueFloat();
        boolean axePrimer = autoStunSlam.getValue() && mc.player.getMainHandStack().getItem() instanceof AxeItem;
        boolean groundBreach = breachOnGround.getValue() && mc.player.isOnGround();

        if (!falling && !axePrimer && !groundBreach) {
            if (smartSwapBack.getValue() && mc.player.isOnGround()) restoreSlot();
            return;
        }

        int maceSlot = findMaceSlot();
        if (maceSlot == -1) return;

        if (previousSlot == -1) previousSlot = mc.player.getInventory().selectedSlot;

        if (axePrimer && !queuedSlam) {
            attack(target);
            queuedSlam = true;
            nextSwingDelay = MathUtils.randomInt(hitDelay.getMinInt(), hitDelay.getMaxInt());
            timer.reset();
            return;
        }

        if (queuedSlam && !timer.hasReached(nextSwingDelay)) return;

        swap(maceSlot);
        attack(target);
        queuedSlam = false;

        if (smartSwapBack.getValue()) restoreSlot();
    }

    private void attack(LivingEntity target) {
        if (inputSimulation.getValue()) MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_LEFT, MathUtils.randomInt(18, 45));
        WorldUtils.hitEntity(target, true);
    }

    private void swap(int slot) {
        if (slot < 0 || slot > 8 || mc.player.getInventory().selectedSlot == slot) return;
        if (silentSwap.getValue()) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        else InventoryUtils.setInvSlot(slot);
    }

    private void restoreSlot() {
        if (previousSlot != -1) swap(previousSlot);
        previousSlot = -1;
        queuedSlam = false;
    }

    private void reset() {
        previousSlot = -1;
        nextSwingDelay = 0;
        queuedSlam = false;
        timer.reset();
    }

    private int findMaceSlot() {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem() instanceof MaceItem) return i;
        return -1;
    }
}
